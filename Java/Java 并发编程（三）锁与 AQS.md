# Java 并发编程（三）锁与 AQS

由于传统的 `synchronized` 关键字提供的内置锁存在的一些缺点，自 JDK 1.5 开始提供了 `Lock` 接口来提供内置锁不具备的功能。显式锁的出现不是为了替代 `synchronized`提供的内置锁，而是当内置锁的机制不适用时，作为一种可选的高级功能

<br />

## 内置锁与显式锁

内置锁于显式锁的比较如下表：

<table>
   <thead>
      <tr>
         <th>类别</th>
         <th align="center">synchronized</th>
         <th align="center">Lock</th>
      </tr>
   </thead>
   <tbody>
      <tr>
         <td>存在层次</td>
         <td align="center">Java的关键字</td>
         <td align="center">是一个类</td>
      </tr>
      <tr>
         <td>锁的释放</td>
         <td align="center">1、以获取锁的线程执行完同步代码，释放锁 <br/>2、线程执行发生异常，jvm会让线程释放锁</td>
         <td align="center">在finally中必须释放锁，<br/>不然容易造成线程死锁</td>
      </tr>
      <tr>
         <td>锁的获取</td>
         <td align="center">假设A线程获得锁，B线程等待。<br/>如果A线程阻塞，B线程会一直等待</td>
         <td align="center">Lock有多个锁获取的方式</td>
      </tr>
      <tr>
         <td>锁状态</td>
         <td align="center">无法判断</td>
         <td align="center">可以判断</td>
      </tr>
      <tr>
         <td>锁类型</td>
         <td align="center">可重入<br/>不可中断<br/>非公平</td>
         <td align="center">可重入<br/>可判断<br/>可公平（两者皆可）</td>
      </tr>
      <tr>
         <td>性能</td>
         <td align="center">少量同步</td>
         <td align="center">大量同步</td>
      </tr>
   </tbody>
</table>



### 显式锁的基本使用

`Lock` 的定义如下：

```java
public interface Lock {
    // 显式地获取锁
    void lock();
    // 可中断地获取锁，与 lock() 方法的不同之处在于在锁的获取过程可以被中断
    void lockInterruptibly() throws InterruptedException;
    // 以非阻塞的方式获取锁，调用该方法将会立即返回，如果成功获取到锁则返回 true，否则返回 false
    boolean tryLock();
    /* 带时间参数的 tryLock，
       有三种情况：在规定时间内获取到了锁；在规定的时间内线程被中断了；在规定的时间内没有获取到锁
    */
    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;
    // 释放锁
    void unlock();
    /* 
    	获取 “等待/通知” 组件，该组件和当前的线程绑定，当前的线程只有获取到了锁，
    	才能调用该组件的 wait 方法，而调用之后，当前线程将会释放锁
    */
    Condition newCondition();
}
```

常用的 `Lock` 的实现类为 `java.util.concurrent.locks.ReentrantLock`，使用的示例如下：

```java
private final static Lock lock = new ReentrantLock();
static int value = 0;

static class Demo implements Runnable {
    @Override
    public void run() {
        lock.lock();
        try {
            value++;
        } finally { // 一定要讲解锁操作放入到 finally 中，否则有可能会造成死锁
            lock.unlock();
        }
    }
}
```

`ReentrantLock` 是基于 `java.util.concurrent.locks.AbstractQueuedSynchronizer` 的具体子类来实现同步的，这个类也被称为 `AQS`，是 `JUC` 中实现 `Lock` 最为核心的部分



## AQS

### 构建同步类

使用 `AQS` 构建同步类时获取锁和释放锁的标准形式如下：<sup>[1]</sup>

```java
boolean acquire() throws InterruptedException {
    while (当前状态不允许获取操作) {
        if (需要阻塞获取请求) {
            如果当前线程不在队列中，则将其插入队列
            阻塞当前线程
        } else {
            返回失败
        }
    }
    
    可能更新同步器的状态
    如果线程位于队列中，则将其移出队列
    返回成功
}

void release () {
    更新同步器状态
    if (新的状态允许某个阻塞的线程获取成功) {
        解除队列中一个或多个线程的阻塞状态
    }
}
```

> 对于支持独占式的同步器，需要实现一些 `protected` 修饰的方法，包括 `tryAcquire`、`tryRelease`、`isHeldExclusively`等；
>
> 对于支持共享式的同步器，应该实现的方法有 `tryAcquireShared`、`tryReleaseShared` 等
>
> `AQS`的 `acquire`、`acquireShared` 和 `release`、`releaseShared` 等方法都将调用这些方法在子类中带有的前缀 `try` 的版本来判断某个操作能否被执行。
>
> 在同步器的子类中，可以根据其获取操作和释放操作的语义，使用 `getState`、`setState`以及 `compareAndSetState` 来检查和更新状态，并根据返回的状态值来告知基类 “获取” 和 “释放” 同步的操作是否是成功的。

<br />

### 源码解析

`AQS` 的类结构图如下：

<img src="https://s2.loli.net/2021/12/06/bMPDKkj9VA8Hy4m.png" alt="AbstractQueuedSynchronizer.png" style="zoom:50%;" />

#### 类属性分析

- `AQS` 实例对象的属性

  `AQS` 中存在非 `static` 的字段如下（`static` 字段没有分析的必要）：

  ```java
  // 头节点，即当前持有锁的线程
  private transient volatile Node head;
  
  // 阻塞队列的尾结点，每个新的节点进来都会插入到尾部
  private transient volatile Node tail;
  
  /*
  	代表锁的状态，0 表示没有被占用，大于 0 表示有线程持有当前的锁
  	这个值可以大于 1，因为锁是可重入的，每次重入时都会将这个值 +1
  */
  private volatile int state;
  
  /*
  	这个属性继承自 AbstractOwnableSynchronizer，
  	表示当前持有独占锁的线程
  */
  private transient Thread exclusiveOwnerThread;
  ```

- 队列节点对象的属性

  ```java
  static final class Node {
      // 标记当前的节点处于共享模式
      static final Node SHARED = new Node();
      // 表示当前的节点处于独占模式
      static final Node EXCLUSIVE = null;
      // 这个值表示当前节点的线程已经被取消了
      static final int CANCELLED =  1;
      // 表示当前节点的下一个节点需要被唤醒
      static final int SIGNAL    = -1;
      // 表示当前节点在等待一个条件
      static final int CONDITION = -2;
      // 表示下一个 acquireShared 应当无条件地传播
      static final int PROPAGATE = -3;
      
      /* 
      	当前节点的等待状态，取值为上面的 
      	CANCELLED、SIGNAL、CONDITION、PROPAGATE 或者 0
      */
      volatile int waitStatus;
      // 当前节点的前节点
      volatile Node prev;
      // 当前节点的下一个节点
      volatile Node next;
      // 当前节点存储的线程
      volatile Thread thread;
      // 链接到下一个等待条件的节点，或者是特殊值为 SHARED 的节点
      Node nextWaiter;
  }
  ```

最后得到的阻塞队列如下图所示：

<img src="https://s6.jpg.cm/2021/12/06/L7hr7k.png" /><sup>[2]</sup>

注意，这里的阻塞队列**不包含头结点 head**





<br />

参考：

<sup>[1]</sup> 《Java 并发编程实战》

<sup>[2]</sup> https://javadoop.com/post/AbstractQueuedSynchronizer