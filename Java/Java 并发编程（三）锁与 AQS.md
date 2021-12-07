# Java 并发编程（三）锁与 AQS

**本文 JDK 对应的版本为 JDK 13**

<br />

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
      // 链接到下一个等待条件的节点（条件队列），或者是特殊值为 SHARED 的节点
      Node nextWaiter;
  }
  ```

最后得到的阻塞队列如下图所示：

<img src="https://s6.jpg.cm/2021/12/06/L7hr7k.png" /><sup>[2]</sup>

注意，这里的阻塞队列**不包含头结点 head**



#### 具体分析

- `acquire(int arg)`

	该方法位于 `java.util.concurrent.locks.AbstractQueuedSynchronizer` 中，具体对应的源代码如下：

	```java
	public final void acquire(int arg) {
	  /*
	  	如果 tryAcquire(arg) 成功了（即尝试获取锁成功了），那么就直接获取到了锁
	  	否则，就需要调用 acquireQueued 方法将这个线程放入到阻塞队列中
	  */
	  if (!tryAcquire(arg) &&
	      // 如果尝试获取锁没有成功，那么久将当前的线程挂起，放入到阻塞队列中
	      acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
	    selfInterrupt();
	}
	```

	`tryAcquire(arg)` 对应的源代码如下：

	```java
	// AbstractQueuedSynchronizer 中定义的。。。
	protected boolean tryAcquire(int arg) { // 在 AbstractQueuedSynchronizer 中定义的模版方法，需要具体的子类来实现
	  throw new UnsupportedOperationException();
	}
	```

	为了简化这个过程，以 `ReentrantLock` 的 `FairSync` 为例查看具体的实现：

	```java
	// ReentrantLock.FairSync。。。
	@ReservedStackAccess
	/*
		尝试直接获取锁,返回值为 boolean,表示是否获取到锁
		返回为 true: 1.没有线程在等待锁 2.重入锁,线程本来就持有锁,因此可以再次获取当前的锁
	*/
	protected final boolean tryAcquire(int acquires) {
	  final Thread current = Thread.currentThread();
	  int c = getState();
	  if (c == 0) { // state 为 0 表示此时没有线程持有锁
	    /*
	    	当前的锁为公平锁(FairSync)，因此即使当前锁是可以获取的，
	    	但是需要首先检查是否已经有别的线程在等待这个锁
	    */
	    if (!hasQueuedPredecessors() &&
	        /*
	        	如果没有线程在等待，那么则尝试使用 CAS 修改状态获取锁，如果成功，则获取到当前的锁
	        	如果使用 CAS 获取锁失败，那么就说明几乎在同一时刻有个线程抢先获取了这个锁
	        */
	        compareAndSetState(0, acquires)) {
	      // 到这里就已经获取到锁了，标记一下当前的锁，表示已经被当前的线程占用了
	      setExclusiveOwnerThread(current);
	      return true;
	    }
	  }
	  /*
	  	如果已经有线程持有了当前的锁，那么首先需要检测一下是不是当前线程持有的锁
	  	如果是当前线程持有的锁，那么就是一个重入锁，需要对 state 变量 +1
	  	否则，当前的锁已经被其它线程持有了，获取失败
	  */
	  else if (current == getExclusiveOwnerThread()) {
	    int nextc = c + acquires;
	    if (nextc < 0)
	      throw new Error("Maximum lock count exceeded");
	    setState(nextc);
	    return true;
	  }
	  return false;
	}
	```

	现在再回到 `acquire` 方法，如果 `trAcquire(arg)` 成功获取到了锁，那么就是成功获取到了锁，直接返回即可；如果 `tryAcquire(arg)` 获取锁失败了，则再执行 `acquireQueued` 方法将当前线程放入到阻塞队列尾部

	在那之前，首先会执行 `acquireQueued` 方法中调用的 `addWaiter(Node.EXCLUSIVE)` 方法，具体的源代码如下：

	```java
	// AbstractQueuedSynchronizer
	
	/*
		这个方法的作用是将当前的线程结合给定的 mode 组合成为一个 Node，以便插入到阻塞队列的末尾
		结合当前的上下文，传入的 mode 为 Node.EXCLUSIVE，即独占锁的模式
	*/
	private Node addWaiter(Node mode) {
	  Node node = new Node(mode);
	
	  for (;;) { // 注意这里的永真循环。。。
	    Node oldTail = tail;
	    /*
	    	如果尾结点不为 null，则使用 CAS 的方式将 node 插入到阻塞队列的尾部
	    */
	    if (oldTail != null) {
	      node.setPrevRelaxed(oldTail); // 设置当前 node 的前驱节点为原先的 tail 节点
	      if (compareAndSetTail(oldTail, node)) { // CAS 的方式设置尾结点
	        oldTail.next = node;
	        return node; // 返回当前的节点 
	      }
	    } else {
	      // 如果当前的阻塞队列为空的话，那么首先需要初始化阻塞队列
	      initializeSyncQueue();
	    }
	  }
	}
	
	// 初始化阻塞队列对应的源代码如下
	private final void initializeSyncQueue() {
	  Node h;
	  // 依旧是使用 CAS 的方式，这里的 h 的初始化为延迟初始化
	  if (HEAD.compareAndSet(this, null, (h = new Node())))
	    tail = h;
	}
	```

	之后就是执行 `acquireQueued` 方法了，对应的源代码如下：

	```java
	// AbstractQueuedSynchronizer
	
	/*
		此时的参数 node 已经经过 addWaiter 的处理，已经被添加到阻塞队列的末尾了
		如果 acquireQueued(addWaiter(Node.EXCLUSIVE), arg) 调用之后返回 true，那么就会执行 acquire(int arg) 方法中的 selfInterrupt() 方法
	
		这个方法是比较关键的部分，是真正处理线程挂起，然后被唤醒去获取锁，都在这个方法中定义
	*/
	final boolean acquireQueued(final Node node, int arg) {
	  boolean interrupted = false;
	  try {
	    for (;;) { // 注意这里的永真循环
	      // predecessor() 返回的是当前 node 节点的前驱节点
	      final Node p = node.predecessor();
	
	      /*
	      	p == head 表示当前的节点虽然已经进入到了阻塞队列，但是是阻塞队列中的第一个元素（阻塞队列不包含 head 节点）
	      	因此当前的节点可以尝试着获取一下锁，这是由于当前的节点是阻塞队列的第一个节点，而 head 节点又是延迟初始化的，在这种情况下是有可能获取到锁的
	      */
	      if (p == head && tryAcquire(arg)) {
	        setHead(node);
	        p.next = null; // help GC
	        return interrupted;
	      }
	
	      /*
	      	如果执行到这个位置，则说明 node 要么就不是队头元素，要么就是尝试获取锁失败
	      */
	      if (shouldParkAfterFailedAcquire(p, node))
	        interrupted |= parkAndCheckInterrupt();
	    }
	  } catch (Throwable t) {
	    cancelAcquire(node);
	    if (interrupted)
	      selfInterrupt();
	    throw t;
	  }
	}
	
	// parkAndCheckInterrupt() 对应的源代码
	/*
		该方法的主要任务是挂起当前线程，使得当前线程在此等待被唤醒
	*/
	private final boolean parkAndCheckInterrupt() {
	  LockSupport.park(this); // 该方法用于挂起当前线程
	  return Thread.interrupted();
	}
	```

	`shouldParkAfterFailedAcquire(p, node)` 对应的源代码如下：

	```java
	// AbstractQueuedSynchronizer
	
	/*
		这个方法的主要任务是判断当前没有抢到锁的线程是否需要阻塞
		第一个参数表示当前节点的前驱节点，第二个参数表示当前线程的节点
	*/
	private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
	  int ws = pred.waitStatus;
	  // 前驱节点正常，则需要阻塞当前线程节点
	  if (ws == Node.SIGNAL)
	    /*
	    * This node has already set status asking a release
	    * to signal it, so it can safely park.
	    */
	    return true;
	  
	  /*
	  	前驱节点的状态值大于 0 表示前驱节点取消了排队
	  	如果当前的节点被阻塞了，唤醒它的为它的前驱节点，因此为了使得能够正常工作，
	  	需要将当前节点的前驱节点设置为一个正常的节点，使得当前的节点能够被正常地唤醒
	  */
	  if (ws > 0) {
	    /*
	    * Predecessor was cancelled. Skip over predecessors and
	    * indicate retry.
	    */
	    do {
	      node.prev = pred = pred.prev;
	    } while (pred.waitStatus > 0);
	    pred.next = node;
	  } else {
	    /*
	    * waitStatus must be 0 or PROPAGATE.  Indicate that we
	    * need a signal, but don't park yet.  Caller will need to
	    * retry to make sure it cannot acquire before parking.
	    */
	    
	    /*
	    	如果不满足以上两个条件，那么当前的 ws 的状态就只能为 0， -2， -3 了
	    	在当前的上下文环境中，ws 的状态为 0，因此这里就是将当前节点的前驱节点的 ws 值设置为 Node.SIGNAL
	    */
	    pred.compareAndSetWaitStatus(ws, Node.SIGNAL);
	  }
	  
	  /*
	  	本次执行到此处会返回 false，而 acquireQueued 中的永真循环将会再次进入这个方法
	  	由于上面的一系列操作，当前节点的前驱节点一定是正常的 Node.SIGNAL，因此会在第一个 if 语句中直接返回 true
	  */
	  return false;
	}
	```

	

- `release(int arg)`

	该方法用于释放当前获取到的锁，对应的具体的源代码如下：

	```java
	// AbstractQueuedSynchronizer
	
	// 释放在独占模式中获取到的锁
	public final boolean release(int arg) {
	  if (tryRelease(arg)) {
	    Node h = head;
	    if (h != null && h.waitStatus != 0)
	      unparkSuccessor(h);
	    return true;
	  }
	  return false;
	}
	```

	`tryRelease(arg)` 对应的源代码如下：

	```java
	// AbstractQueuedSynchronizer
	
	// 很明显，这也是一个模版方法，需要具体子类来定义对应的实现
	protected boolean tryRelease(int arg) {
	  throw new UnsupportedOperationException();
	}
	```

	依旧以 `ReentrantLock` 为例，查看一下 `tryRelease(int arg)` 的具体实现

	```java
	// ReentrantLock.Sync
	
	@ReservedStackAccess
	protected final boolean tryRelease(int releases) {
	  int c = getState() - releases;
	  if (Thread.currentThread() != getExclusiveOwnerThread())
	    throw new IllegalMonitorStateException();
	
	  boolean free = false; // 是否已经完全释放锁的标记
	  
	  // 如果 c > 0，则说明获取的锁是一个重入锁，还没有完全释放
	  if (c == 0) {
	    free = true;
	    setExclusiveOwnerThread(null);
	  }
	  setState(c);
	  return free;
	}
	```

	再回到 `release(int arg)` 方法中，如果是已经完全释放了锁，则执行后面的 `return false` 语句，执行结束。如果没有完全释放锁，那么则会继续执行 `unparkSuccessor(h)` 方法，对应的源代码如下：

	```java
	// AbstractQueuedSynchronizer
	
	// 唤醒后继节点
	private void unparkSuccessor(Node node) {
	  /*
	  * If status is negative (i.e., possibly needing signal) try
	  * to clear in anticipation of signalling.  It is OK if this
	  * fails or if status is changed by waiting thread.
	  */
	  int ws = node.waitStatus;
	  if (ws < 0)
	    node.compareAndSetWaitStatus(ws, 0);
	
	  /*
	  * Thread to unpark is held in successor, which is normally
	  * just the next node.  But if cancelled or apparently null,
	  * traverse backwards from tail to find the actual
	  * non-cancelled successor.
	  */
	  
	  /*
	  	唤醒后继节点，但是可能后继节点取消了等待（即 waitStatus = Node.CANCELLED）
	  	在这种情况下，将会从队尾向前查找，找到最靠近 head 的 waitStatus < 0 的节点
	  */
	  Node s = node.next;
	  if (s == null || s.waitStatus > 0) {
	    s = null;
	    // 从队尾开始向前查找，找到第一个合适的节点
	    for (Node p = tail; p != node && p != null; p = p.prev)
	      if (p.waitStatus <= 0) // 可能排在前面的节点取消的可能性更大
	        s = p;
	  }
	  
	  if (s != null) // 唤醒这个合适的节点对应的线程
	    LockSupport.unpark(s.thread);
	}
	```

	在释放了所有的锁之后，唤醒后继的一个还没有被取消的线程节点，然后唤醒它，唤醒之后的节点将恢复原来在 `parkAndCheckInterrupt()` 中的执行状态

	```java
	private final boolean parkAndCheckInterrupt() {
	  LockSupport.park(this); // 被唤醒后将继续执行后面的代码
	  return Thread.interrupted(); // 此时应当是没有被中断的
	}
	```

	再回到原先的 `acquireQueued(node, arg)` 方法，此时由于 head 已经释放了锁，而当前的 node 节点是距离 head 最近的一个有效的线程节点，因此它能够获取到锁，线程在获取锁之后再继续执行对应的代码逻辑

	

#### `ConditionObject`

`ConditionObject` 一般用于 “生产者—消费者” 的模式中，与基于`Object` 的 `wait()` 和 `notifyAll()` 实现的通信机制十分类似。

对应的 `ConditionObject` 的源代码如下：

```java
public class ConditionObject implements Condition, java.io.Serializable {
  // 条件队列的第一个节点
  private transient Node firstWaiter;
  // 条件队列的最后一个节点
  private transient Node lastWaiter;
}
```

与前文的阻塞队列相对应，条件队列与阻塞队列的对应关系图如下所示：

<img src="https://www.javadoop.com/blogimages/AbstractQueuedSynchronizer-2/aqs2-2.png" style="zoom:65%"><sup>[3]</sup>

具体解释：

1. 条件队列和阻塞队列的节点，都是 Node 的实例对象，因为条件队列的节点是需要转移到阻塞队列中取得
2. `ReentrantLock` 的实例对象可以通过多次调用 `newCondition()` 方法来生成新的 `Condition` 对象（最终由 `AQS` 的具体子类对象生成）。在 `AQS` 中，对于 `Condition` 的具体实现为 `ConditionObject`，这个对象只有两个属性字段：`firstWaiter` 和 `lastWaiter`
3. 每个 `ConditionObject` 都有一个自己的条件队列，线程 1 通过调用 `Condition` 对象的 `await` 方法即可将当前的调用线程包装成为 Node 后加入到条件队列中，然后阻塞在条件队列中，不再继续执行后面的代码
4. 调用 `Condition` 对象的 `signal()` 方法将会触发一次唤醒事件，与 `Object` 的 `notify()` 方法类似。此时唤醒的是条件队列的队头节点，唤醒后会将 `firstWaiter` 的节点移动到阻塞队列的末尾，然后在阻塞队列中等待获取锁，之后获取锁之后才能继续执行

##### `await` 方法

`await` 方法对应的源代码如下：

```java
// AbstractQueuedSynchronizer.ConditionObject
/*
	抛出 InterruptedException 表示这个方法是可以被中断的
	这个方法会被阻塞，直到调用 signal 方法（singnal 和 singnalAll）唤醒或者被中断
*/
public final void await() throws InterruptedException {
    // 按照规范，应该在最开始的位置就首先检测一次中断
    if (Thread.interrupted())
        throw new InterruptedException();
    
    // 将当前的线程封装成 Node，添加到条件队列中 
    Node node = addConditionWaiter();
    
    /* 
    	释放锁，返回值是释放锁之前的 state 值
    	在调用 await 方法之前，当前的线程肯定是持有锁的，在这里需要释放掉当前持有的锁
    */
    int savedState = fullyRelease(node);
    int interruptMode = 0;
    /*
    	isOnSyncQueue(node) 返回 true 表示当前的节点已经从条件队列转移到阻塞队列了
    */
    while (!isOnSyncQueue(node)) {
        /* 
        	如果当前的节点不在阻塞队列中，那么将当前节点中的线程挂起，
        	直到通过调用 Condition 对象的 signal* 方法来唤醒它
        */
        LockSupport.park(this);
        // 线程被中断，因此需要退出当前的循环
        if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
            break;
    }
    
    // 进入阻塞队列之后，等待获取锁
    if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
        interruptMode = REINTERRUPT;
    
    if (node.nextWaiter != null) // clean up if cancelled
        unlinkCancelledWaiters();
    if (interruptMode != 0)
        reportInterruptAfterWait(interruptMode);
}
```

`addConditionWaiter()` 对应的源代码如下：

```java
// AbstractQueuedSynchronizer.ConditionObject

/*
	将当前线程包装成一个 Node，插入的条件队列末尾
*/
private Node addConditionWaiter() {
    if (!isHeldExclusively())
        throw new IllegalMonitorStateException();
    // 当前 ConditionObject 中条件队列的尾节点
    Node t = lastWaiter;
    // If lastWaiter is cancelled, clean out.
    /*
    	如果尾结点的线程已经被取消了，那么就清除它
    	注意当前节点所处的队列为条件队列，因此每个节点的状态都应该是 Node.CONDITION
    */
    if (t != null && t.waitStatus != Node.CONDITION) {
        // 该方法会从前到后清除所有的不满足条件的节点
        unlinkCancelledWaiters();
        t = lastWaiter;
    }

    // 创建一个新的 Node，当前的 Node 的 waitStatus 为 Node.CONDITION
    Node node = new Node(Node.CONDITION);

    // 处理初始队列为空的情况
    if (t == null)
        firstWaiter = node;
    else
        t.nextWaiter = node;

    lastWaiter = node;
    return node;
}

/*
	清除当前 ConditionObject 的条件队列中所有 waitStatus 不为 CONDITION 的节点
*/
private void unlinkCancelledWaiters() {
    Node t = firstWaiter;
    Node trail = null;
    // 单纯的链表移除节点的操作
    while (t != null) {
        Node next = t.nextWaiter;
        if (t.waitStatus != Node.CONDITION) {
            t.nextWaiter = null;
            if (trail == null)
                firstWaiter = next;
            else
                trail.nextWaiter = next;
            if (next == null)
                lastWaiter = trail;
        }
        else
            trail = t;
        t = next;
    }
}
```

`fullyRelease(node)` 对应的源代码如下：

```java
// AbstractQueuedSynchronizer.ConditionObject
/*
	该方法的主要目的是完全释放当前节点中线程持有的锁
	之所以是完全释放，这是因为锁是可重入的
*/
final int fullyRelease(Node node) {
    try {
        /*
        	由于显式锁是可重入的，因此在调用 await() 时也必须再恢复到原来的状态
        	回忆一下 Node 节点中 state 属性代表的意义，如果 state > 0 表示当前持有的锁的数量
        	获取这个锁的数量，使得在进入阻塞队列中的 Node 能够再恢复到原来的状态
        */
        int savedState = getState();
        if (release(savedState)) // 参见上文有关 release 方法的介绍
            return savedState;
        throw new IllegalMonitorStateException();
    } catch (Throwable t) {
        /* 
        	如果在释放锁的过程中失败了，那么就将这个节点的状态设置为 CANCELLED,
        	在之后的处理中会移除这个节点
        */
        node.waitStatus = Node.CANCELLED;
        throw t;
    }
}
```

`isOnSyncQueue(node)` 对应的源代码：

```java
// AbstractQueuedSynchronizer.ConditionObject

/*
	判断当前的节点是否是从条件队列中转移到了阻塞队列，并且正在等待被唤醒
*/
final boolean isOnSyncQueue(Node node) {
    /*
    	从条件队列中移动到阻塞队列中时，node 的 waitStatus 将会被设置为 0
    	如果 node 的 waitStatus 依旧为 Node.CONDITION，那么则说明它还在条件队列中
    	如果 node 的前驱节点为 null，那么也一定还在等待队列中（阻塞队列中每个节点都会有前驱节点）
    */
    if (node.waitStatus == Node.CONDITION || node.prev == null)
        return false;

    // 如果 node 都已经存在后继节点了，那么肯定在阻塞队列中了
    if (node.next != null) // If has successor, it must be on queue
        return true;

    /*
    * node.prev can be non-null, but not yet on queue because
    * the CAS to place it on queue can fail. So we have to
    * traverse from tail to make sure it actually made it.  It
    * will always be near the tail in calls to this method, and
    * unless the CAS failed (which is unlikely), it will be
    * there, so we hardly ever traverse much.
    */

    /*
    	由于 CAS 在将条件队列中的节点移动到阻塞队列中时可能会失败，（具体可以查看 AQS 的入队方法）
    	此时当前节点的前驱节点不为 null，为了解决这个问题，
    	需要遍历阻塞队列来确保当前的节点确实是已经进入到了阻塞队列
    */
    return findNodeFromTail(node);
}

// 对应的源代码。。。。
private boolean findNodeFromTail(Node node) {
    // We check for node first, since it's likely to be at or near tail.
    // tail is known to be non-null, so we could re-order to "save"
    // one null check, but we leave it this way to help the VM.
    
    /*
    	从尾结点开始遍历搜索节点，检查是否在阻塞队列中
    */
    for (Node p = tail;;) {
        if (p == node)
            return true;
        if (p == null)
            return false;
        p = p.prev;
    }
}
```

<br />

##### `signal` 方法

`signal` 方法用于唤醒正在等待的线程，在当前的环境下，`signal` 的主要目的是唤醒在条件队列中线程节点，将它们移动到阻塞队列中

`AQS` 中对于 `signal()` 方法的实现如下：

```java
// AbstractQueuedSynchronizer.ConditionObject
/*
	移动等待了最久的线程，将它从条件队列移动到阻塞队列
*/
public final void signal() {
    // 调用 signal 的线程必须持有当前的独占锁
    if (!isHeldExclusively())
        throw new IllegalMonitorStateException();
    // 一般第一个节点就被视作 “等待最久” 的线程
    Node first = firstWaiter;
    // 真正唤醒线程
    if (first != null)
        doSignal(first);
}

/*
	从前往后查找第一个符合条件的节点（有的线程可能已经被取消或者被中断了）
*/
private void doSignal(Node first) {
    do {
        // 移除第一个节点
        /* 
        	如果移除第一个节点之后条件队列中不再有节点了，那么需要将 lastWaiter 
        	节点也置为 null
        */
        if ( (firstWaiter = first.nextWaiter) == null)
            lastWaiter = null;

        // 移除该节点和队列之间的连接关系
        first.nextWaiter = null;
    } while (!transferForSignal(first) &&
             (first = firstWaiter) != null); // 遍历队列，直到找到第一个满足条件的节点
}

// AbstractQueuedSynchronizer

/*
	将条件队列中的节点移动到阻塞队列
	返回 true 表示转移成功，false 则表示这个节点在调用 signal 之前就被取消了
*/
final boolean transferForSignal(Node node) {
    /*
    * If cannot change waitStatus, the node has been cancelled.
    */
    /* 
    	CAS 修改当前节点的 waitStatus如果失败，说明该节点所在的线程已经被取消了
    */
    if (!node.compareAndSetWaitStatus(Node.CONDITION, 0))
        return false;

    /*
    * Splice onto queue and try to set waitStatus of predecessor to
    * indicate that thread is (probably) waiting. If cancelled or
    * attempt to set waitStatus fails, wake up to resync (in which
    * case the waitStatus can be transiently and harmlessly wrong).
    */
    /*
    	这里的的 p 是 node 在进入阻塞队列之后的前驱节点
    */
    Node p = enq(node); // 以自旋的方式进入阻塞队列的队尾
    int ws = p.waitStatus;
    /*
    	ws > 0 表示 node 在阻塞队列中的前驱节点取消了等待，直接唤醒 node 对应的线程
    	ws <= 0，那么在进入阻塞队列的时候需要将 node 的前驱节点设置为 SIGNAL，表示前驱节点会唤醒后继节点
    */
    if (ws > 0 || !p.compareAndSetWaitStatus(ws, Node.SIGNAL))
        LockSupport.unpark(node.thread);
    return true;
}
```

在唤醒线程之后，再查看 `await()` 方法中的逻辑：

```java
public final void await() throws InterruptedException {
    int interruptMode = 0;
    while (!isOnSyncQueue(node)) {
        LockSupport.park(this); // 当前线程被挂起
        // 挂起后的后置处理
        if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
            break;
    }
    // ……………………………………………………
}
```

`interruptMode` 可选的值如下：

- `REINTERRUPT`：在 `await` 方法返回的时候，需要重新设置中断状态
- `THROW_IE`：代表 `await` 方法返回的时候,需要抛出 `InterruptedException` 异常
- 0：表示在 `await` 方法调用期间，该线程没有被中断

<br />

线程被唤醒之后的第一步操作是调用 `checkInterruptWhileWaiting(node)` 检查当前的线程是否被中断了，对应的源代码如下：

```java
// AbstractQueuedSynchronizer.ConditionObject
// 返回对应 interruptMode 中的三个值
private int checkInterruptWhileWaiting(Node node) {
    return Thread.interrupted() ?
        (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
    0;
}

// AbstractQueuedSynchronizer
/*
	只有线程被中断的情况下，才会调用此方法
	如果需要的话，将这个已经取消等待的节点转移到阻塞队列
	返回 true ：如果此线程在 signal 调用之前被取消
*/
final boolean transferAfterCancelledWait(Node node) {
    /*
    	CAS 将节点状态设置为 0
    	如果这一步 CAS 成功，则说明是调用 signal 方法之前就已经发生了中断，
    	因为 signal 方法会将条件队列的首个节点的 waitStatus 置为 0 再移动到阻塞队列
    	如果不为 0 则说明要么被取消了，要么还没有调用 signal 进行处理
    */
    if (node.compareAndSetWaitStatus(Node.CONDITION, 0)) {
        enq(node); // 可以看到，即使被中断了，依旧会将这个节点放入到阻塞队列
        return true;
    }
    
    /*
    * If we lost out to a signal(), then we can't proceed
    * until it finishes its enq().  Cancelling during an
    * incomplete transfer is both rare and transient, so just
    * spin.
    */
    /*
    	如果会走到这，那么一定是 CAS 设置 node 的 waitStatus 失败了，
    	即是在调用 signal 之后发生的中断
    	
    	signal 会将节点移动从条件队列移动到阻塞队列，但是可能由于某些原因还没有移动完成，
    	因此在这里通过自旋的方式等待其完成
    */
    while (!isOnSyncQueue(node))
        Thread.yield();
    return false;
}
```

**可以看到，即使发生了中断，依旧会完成将 node 从条件队列转移到阻塞队列**

<br />

唤醒线程后继续向下走，对应的源代码如下：

```java
public final void await() throws InterruptedException {
    // 省略部分代码
    
    /*
    	当 acquireQueued 方法返回 true 时，说明线程已经被中断了
    	如果此时 interruptMode 为 THROW_IE 的话，说明在调用 signal 方法之前就已经被中断了
    	在这种情况下，将 interruptMode 置为 REINTERRUPT，以便之后重新中断
    */
    if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
        interruptMode = REINTERRUPT;
}
```

继续向下执行，对应的源代码：

```java
/* 
	在调用 signal 时会断开当前节点和后继节点之间的连接，
	如果此时后继节点不为 null，说明是被中断的，同样需要断开这个节点在条件队列中的连接
*/
if (node.nextWaiter != null) // clean up if cancelled
    unlinkCancelledWaiters();
// 处理中断
if (interruptMode != 0)
    reportInterruptAfterWait(interruptMode);
```

`reportInterruptAfterWait(interruptMode)` 对应的源代码：

```java
// AbstractQueuedSynchronizer.ConditionObject
// 处理中断
private void reportInterruptAfterWait(int interruptMode)
    throws InterruptedException {
    // 根据 interruptMode 对中断进行不同的处理
    if (interruptMode == THROW_IE)
        throw new InterruptedException();
    else if (interruptMode == REINTERRUPT)
        selfInterrupt();
}
```

<br />

#### 共享模式

##### 获取锁

主要对应 `AQS` 中的 `acquireSharedInterruptibly` 方法，具体的定义如下：

```java
// AbstractQueuedSynchronizer
public final void acquireSharedInterruptibly(int arg)
    throws InterruptedException {
    // 检测当前线程是否被中断
    if (Thread.interrupted())
        throw new InterruptedException();
    // 由子类具体实现的模板方法
    if (tryAcquireShared(arg) < 0)
        doAcquireSharedInterruptibly(arg);
}
```

`doAcquireSharedInterruptibly(arg)` 方法对应的源代码：

```java
// AbstractQueuedSynchronizer
private void doAcquireSharedInterruptibly(int arg)
    throws InterruptedException {
    // 同样地，将当前线程封装，然后加入阻塞队列
    final Node node = addWaiter(Node.SHARED);
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                // 同上，由子类实现的模板方法
                int r = tryAcquireShared(arg);
                if (r >= 0) {
                    setHeadAndPropagate(node, r);
                    p.next = null; // help GC
                    return;
                }
            }
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                throw new InterruptedException();
        }
    } catch (Throwable t) {
        cancelAcquire(node);
        throw t;
    }
}
```

<br/>

##### 释放锁

具体对应 `AQS` 中的 `releaseShared(int arg)` 方法，具体的定义如下：

```java
// AbstractQueuedSynchronizer
public final boolean releaseShared(int arg) {
    // AQS 定义的模板方法，由具体的子类来实现
    if (tryReleaseShared(arg)) {
        doReleaseShared(); // 关键的方法
        return true;
    }
    return false;
}

private void doReleaseShared() {
    for (;;) {
        Node h = head;
        if (h != null && h != tail) {
            int ws = h.waitStatus;
            /* 
            	每个 Node 在加入到阻塞队列的时候，都会将前驱节点的 waitStatus 
            	设置为 Node.SIGNAL 
            */
            if (ws == Node.SIGNAL) {
                if (!h.compareAndSetWaitStatus(Node.SIGNAL, 0))
                    continue;            // loop to recheck cases
                /*
                	唤醒 head 的后继节点，也就是阻塞队列中的第一个节点
                */
                unparkSuccessor(h);
            }
            else if (ws == 0 &&
                     !h.compareAndSetWaitStatus(0, Node.PROPAGATE))
                continue;                // loop on failed CAS
        }
        if (h == head)                   // loop if head changed
            break;
    }
}
```

再回到 `doAcquireSharedInterruptibly`：

```java
private void doAcquireSharedInterruptibly(int arg)
    throws InterruptedException {
    final Node node = addWaiter(Node.SHARED);
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                /*
                	2. 由于前一个线程持有的锁已经被释放了，当前线程已经被唤醒，继续执行
                */
                int r = tryAcquireShared(arg);
                if (r >= 0) {
                    // 3. 然后会进入这个方法
                    setHeadAndPropagate(node, r);
                    p.next = null; // help GC
                    return;
                }
            }
            if (shouldParkAfterFailedAcquire(p, node) &&
                /*
                	1. 线程在这个方法中被挂起，因此当线程被唤醒时也会从这个方法中返回
                	假设当前的线程没有被中断
                */
                parkAndCheckInterrupt())
                throw new InterruptedException();
        }
    } catch (Throwable t) {
        cancelAcquire(node);
        throw t;
    }
}
```

`setHeadAndPropagate(node, r)` 对应的源代码如下：

```java
// AbstractQueuedSynchronizer
private void setHeadAndPropagate(Node node, int propagate) {
    Node h = head; // Record old head for check below
    setHead(node);
    
    /*
    	唤醒当前 node 节点之后的后继节点
    */
    if (propagate > 0 || h == null || h.waitStatus < 0 ||
        (h = head) == null || h.waitStatus < 0) {
        Node s = node.next;
        if (s == null || s.isShared())
            doReleaseShared(); // 释放当前节点持有的锁
    }
}
```

再回到 `doReleaseShared` 方法：

```java
private void doReleaseShared() {
    for (;;) {
        Node h = head;
        /*
        	h == null 表示当前的阻塞队列为空；h == tail 表示头节点可能是刚刚初始化的头结点
        	或者 h 只是一个普通的线程节点，但是由于它已经被唤醒了，说明阻塞队列中已经没有节点了
        	因此不再需要唤醒后继节点
        */
        if (h != null && h != tail) {
            int ws = h.waitStatus;
            if (ws == Node.SIGNAL) {
                /*
                	在当前上下文环境下 CAS 可能会失败
                */
                if (!h.compareAndSetWaitStatus(Node.SIGNAL, 0))
                    continue;
                
                // CAS 成功则唤醒后继节点
                unparkSuccessor(h);
            }
            else if (ws == 0 &&
                     /*
                     	这里也会对头节点的 waitStatus 修改，因此上面中的 CAS 可能会失败
                     */
                     !h.compareAndSetWaitStatus(0, Node.PROPAGATE))
                continue;
        }
        
        /*
        	如果唤醒的线程已经占领了 head，那么再循环，否则，退出当前循环
        */
        if (h == head)
            break;
    }
}
```





<br />

#### 处理中断

在 `acquireQueued` 方法的执行过程中，对于中断的处理代码如下：

```java
if (shouldParkAfterFailedAcquire(p, node))
    interrupted |= parkAndCheckInterrupt();

// 重点在于 parkAndCheckInterrupt 方法
private final boolean parkAndCheckInterrupt() {
    LockSupport.park(this);
    return Thread.interrupted(); // 该方法会清除中断标记
}
```

在 `acquireQueued` 中，只是单纯地使用一个变量 `interrupted` 来标记是否被中断过，也就是说，在 `acquireQueued` 中，并不会处理中断，**即使当前的线程节点被中断了，它依旧会尝试去获取锁**

具体对于中断的处理由具体的实现来定义，可以忽略这个中断，也可以抛出一个异常

以 `ReentrantLock` 对于 `lockInterruptibly()` 的实现为例，具体的实现代码如下：

```java
// ReentrantLock
public void lockInterruptibly() throws InterruptedException {
    sync.acquireInterruptibly(1); // 该方法为 AQS 中定义的方法
}
```

`AQS` 中对于 `acquireInterruptibly` 方法的定义如下：

```java
// AbstractQueuedSynchronizer
public final void acquireInterruptibly(int arg)
    throws InterruptedException {
    /*
    	在 parkAndCheckInterrupt() 方法中通过 Thread.interrupted() 
    	方法清除了线程的中断标记，因此不会走这
    */
    if (Thread.interrupted())
        throw new InterruptedException();
    // 继续往下走
    if (!tryAcquire(arg))
        doAcquireInterruptibly(arg);
}

// doAcquireInterruptibly 方法的定义如下
private void doAcquireInterruptibly(int arg)
    throws InterruptedException {
    final Node node = addWaiter(Node.EXCLUSIVE);
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                return;
            }
            /*
            	关键在这，与不抛出 InterruptedException 的相比，最大的区别就在于对于中断的处理，
            	上文的 acquireQueued 则只是将中断标记返回给调用者而不是显式地抛出一个异常
            */
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                throw new InterruptedException();
        }
    } catch (Throwable t) {
        cancelAcquire(node); // 取消该节点去获取锁的行为
        throw t; // 传递捕获到的异常
    }
}
```

`cancelAcquire(node)` 对应的源代码如下：

```java
// AbstractQueuedSynchronizer
private void cancelAcquire(Node node) {
    // Ignore if node doesn't exist
    if (node == null)
        return;

    node.thread = null;

    // Skip cancelled predecessors
    /*
    	找到符合条件的前驱节点，将不符合条件的前驱节点都清除
    */
    Node pred = node.prev;
    while (pred.waitStatus > 0)
        node.prev = pred = pred.prev;

    // predNext is the apparent node to unsplice. CASes below will
    // fail if not, in which case, we lost race vs another cancel
    // or signal, so no further action is necessary, although with
    // a possibility that a cancelled node may transiently remain
    // reachable.
    Node predNext = pred.next;

    // Can use unconditional write instead of CAS here.
    // After this atomic step, other Nodes can skip past us.
    // Before, we are free of interference from other threads.
    node.waitStatus = Node.CANCELLED;
    
    
    /*
    	一般的链表清除节点工作
    */
    // If we are the tail, remove ourselves.
    if (node == tail && compareAndSetTail(node, pred)) {
        pred.compareAndSetNext(predNext, null);
    } else {
        // If successor needs signal, try to set pred's next-link
        // so it will get one. Otherwise wake it up to propagate.
        int ws;
        if (pred != head &&
            ((ws = pred.waitStatus) == Node.SIGNAL ||
             (ws <= 0 && pred.compareAndSetWaitStatus(ws, Node.SIGNAL))) &&
            pred.thread != null) {
            Node next = node.next;
            if (next != null && next.waitStatus <= 0)
                pred.compareAndSetNext(predNext, next);
        } else {
            unparkSuccessor(node);
        }

        node.next = node; // help GC
    }
}
```

​	

<br />

参考：

<sup>[1]</sup> 《Java 并发编程实战》

<sup>[2]</sup> https://javadoop.com/post/AbstractQueuedSynchronizer

<sup>[3]</sup> https://javadoop.com/post/AbstractQueuedSynchronizer-2