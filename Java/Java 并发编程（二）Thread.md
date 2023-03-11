# Java 并发编程（二） （Thread）

## 线程状态

线程一般的状态转换图如下：

![image.png](https://s2.loli.net/2021/12/05/TqeGL8DK7Z6OhPz.png)

在线程生命周期中存在的状态解释如下：

- `New`（初始化）状态

  此时线程刚刚被实例化，可以通过调用 `start()` 方法来启动这个实例化的的线程，使其状态转变成为 `Ready` 状态

- `Runnable` 状态

  `Ready` 状态和 `Running` 状态统称为 `Runnable` 状态

  - `Ready` （就绪）状态

    此时线程已经可以被操作系统调度了，但是此时还没有执行，当被操作系统调度，获得 CPU 的执行权限后，此时线程的状态就转变成为 `Ready` 状态。

  - `Running` 状态

    此时线程获得了 CPU 的时间片，正在执行中

- `Blocked`（阻塞）状态

  此时线程由于某种原因（获取锁、IO 等）无法利用 CPU

- `Wating`（等待）状态

  此时线程由于某些原因，需要等待其它的线程执行完成之后才能继续执行，与阻塞状态不同的地方在与，等待状态是自己**主动地**等待某个操作完成，而阻塞则是由于一些不可控的外在因素**被动地**等待

- `TIMED_WATING`（超时等待）状态

  和等待状态类似，但是与之不同的地方在于超时等待状态会在指定的时间之后自行结束等待

- `Terminated`（终止）状态

  该线程的任务已经完成了，是时候被操作系统回收了



### 等待队列<sup>[2]</sup>

<img src="https://img-blog.csdn.net/20180701221233161?watermark/2/text/aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3BhbmdlMTk5MQ==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70" />

1. 线程 1 正在持有对象的锁，此时线程 5 和线程 6 由于未获得锁而处于阻塞状态；线程 2、线程 3、线程 4 没有去夺取锁，因此处于等待状态，位于等待队列中
2. 线程 1 通过调用 `wait` 方法释放当前持有的锁，进入等待状态，当前线程 1 在释放锁之后进入等待状态中欧你
3. 在同步队列中的线程 5 的位置比线程 6 要靠前，因此线程 5 会首先获取到对象的锁（`synhronized` 是公平锁）
4. 线程 5 通过调用 `notifyAll()` 方法唤醒在等待队列中的所有线程，使得它们进入同步队列
5. 线程 5 执行结束，释放当前对象锁
6. 之后由同步队列中的线程竞争锁，由于操作系统对于线程调度的原因，在这种情况下即使 `synchronized`是公平锁，但是最终是哪个线程获取到当前的对象锁依旧是未知的



## Deamon 线程

Deamon 线程也被称为“守护线程”， 这一类线程的主要任务是给其它的线程提供一些支持性的工作。 `JVM` 在不存在非 Deamon 线程的情况下将会退出，因此 Deamon 线程中的任务不一定会被执行

创建一个 Deamon 线程的方式如下：

```java
Thread thread = new Thread(() ->{});
thread.setDaemon(true); // 将当前的线程设置为 Deamon 线程
```



## 线程的取消与关闭<sup>[1]</sup>

`Java` 没有显式的方式直接去停止一个正在运行的线程，尽管 `Thread` 类存在 `stop` 和 `suspend` 等方法显式地终止线程，但是这些方法都存在严重的缺陷，因此应当避免使用这些方法。

`Java` 提供了一种中断的方式来取消一个线程，这是一种协作机制，即使一个线程向另一个线程发送中断信号，从而停止另一个线程的工作

如果一个线程能够在某个操作正常完成之前就能够将其置入 “完成” 状态，那么这个线程就被称为是 “可取消的”。

取消一个正在运行的线程有以下几种情况：

- 用户取消请求

  使用 `JMX` 或其它显式的取消操作

- 有时间限制的操作

  比如，如果一个服务端程序在 3s 的时间内都没能做出响应，那么就丢弃这个请求

- 错误

  如果由于底层的某些硬件限制，导致出现错误的情况。如：内存已满、磁盘已满等

- 关闭

  当一个程序或者服务关闭之后，必须对正在处理和等待的线程执行对应的操作，使得程序能够正常退出。在这个过程中，某些正在等待的线程可能会被取消

  

### 线程中断

与线程中断相关的方法如下：

```java
public class Thread {
    // 这个方法会中断当前线程
    public void interrupt(){}
    
    // 该静态方法将会清除当前线程的中断状态
    public static boolean interrupted(){}
    
    // 检测当前的线程是否被中断
    public boolean isInterrupted(){}
}
```

线程中断是一种协作机制，线程可以通过这种机制来通知另一个线程，告诉它在合适或者可能的情况下停止当前工作，并转而执行其它的工作。



<br />

> 在 Java 的 API 规范中，并没有将中断与任何取消语义关联起来，但实际上，如果在取消之外的其它操作中使用中断，那么都是不合适的，并且很难支撑起更大的应用



<br />

与一般的设置一个 `boolean` 位来自定义中断不同，自定义的中断在某些情况下可能不能按照预期的工作，这是因为：有个阻塞库的 API 在调用时将会导致自定义的取消操作无法执行，从而使得整个操作一直都是阻塞的。在这种情况下，使用线程中将能够解决这一类问题，因为一般的阻塞库 API 都会检查当前线程是否已经被中断，从而终止某些操作

<br />

线程中断的本质只是设置线程的中断标志，大部分的的阻塞库 API 对于中断的处理如下：**清除当前线程的中断标记**，然后抛出 `InterruptedException`。这是因为：每个新创建的线程都不是在自己的线程环境下运行的，它只能在父线程服务（如线程池）中进行，对于中断的响应应当是通知调用者执行自定义的后续处理，而不是由自己处理，这就是为什么大部分的阻塞库函数都只是抛出 `InterruptedException` 异常的原因

<br />

> 调用 interrupt 并不意味着立即停止目标线程正在执行的工作，而只是传递了请求中断的消息



<br />

在使用静态的 `interrupted()` 方法时要格外小心，因为它会清除当前线程的中断状态。如果在调用 `interrupted()` 时返回了 `true`，那么除非希望屏蔽这个中断，否则应当再次调用 `Thread` 对象的实例方法 `interrupt()` 来恢复线程的中断状态。具体的示例如下：

```java
import java.util.concurrent.*;
// 此代码来自 《Java 并发编程实战》
public class TaskRunnable implements Runnable {
    BlockingQueue<Task> queue;

    public void run() {
        try {
            processTask(queue.take());
        } catch (InterruptedException e) {
            /*
            	由于 BlockingQueue 的 take 方法在响应中断时会清除线程的中断状态，
            	因此在捕获到这个异常时需要再次将当前的线程的中断状态恢复
            */
            Thread.currentThread().interrupt();
        }
    }

    void processTask(Task task) {
    }

    interface Task {
    }
}
```



### 响应中断

一般响应中断主要有以下两种方式：

- 传递异常，使得调用该方法的方法也变成可中断的阻塞方法

  ```java
  BlockingQueue<Task> queue;
  // 抛出 InterruptedException，使得 getNextTask 成为可中断的阻塞方法
  public Task getNextTask() throws InterruptedException {
      return queue.take();
  }
  ```

  

- 恢复中断状态，使得调用栈中的上层代码能够对其进行处理

  如果不想或者无法传递 `InterruptedException`，那么恢复线程的中断状态将是一个可选的方案

<br />

> 只有在实现了中断策略的代码才能屏蔽中断请求，在常规的任务和代码库中都不应该屏蔽中断请求



## 线程的生命周期

### 线程创建

`Java` 中创建一个线程，最终对应的源代码如下

```java
private Thread(
    ThreadGroup g, 
    Runnable target, 
    String name,
    long stackSize, 
    AccessControlContext acc,
    boolean inheritThreadLocals
) {
    // 省略一部分参数检测代码。。。。
    this.name = name;
    // 将当前线程设置为要创建的线程的父线程
    Thread parent = currentThread();

    if (g == null) {
        if (g == null) {
            g = parent.getThreadGroup();
        }
    }
    
    g.checkAccess();
    // 省略一部分不太重要的代码。。。。

    g.addUnstarted();

    this.group = g;
    // 将 Deamon、priority 属性设置为父线程对应的属性
    this.daemon = parent.isDaemon();
    this.priority = parent.getPriority();
    
    // 省略一部分不太重要的代码
    
    this.inheritedAccessControlContext =
        acc != null ? acc : AccessController.getContext();
    this.target = target;
    setPriority(priority);
    // 将父线程相关的 ThrealLocal 对象复制到当前创建的线程
    if (inheritThreadLocals && parent.inheritableThreadLocals != null)
        this.inheritableThreadLocals =
        ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);
    /* Stash the specified stack size in case the VM cares */
    this.stackSize = stackSize;

    // 设置当前要创建的线程的 ID
    this.tid = nextThreadID();
}
```



### 线程启动

新创建的线程只是一个单独的对象，如果需要使得操作系统能够去调度这个线程，那么就需要调用线程对象的 `start()` 方法，将当前线程的状态转换为 `Runnbale` 状态

具体的 `start()` 源代码为 `native` 方法，在此不做深入的分析



### 线程运行

一般正常的运行，在上文已经详细介绍过，在此不做赘述



### 线程终止

可以通过线程中断的方式或者自定义 `boolean` 标志位的方式来终止一个线程

```java
public class ShutDown {
    public static void main(String[] args) {
        Runner one = new Runner();
        Thread countThread = new Thread(one, "Thread One");
        countThread.start();
        countThread.interrupt(); // 使用线程中断的方式来终止当前执行的线程

        Runner two = new Runner();
        countThread = new Thread(two, "Thread Two");
        countThread.start();
        two.cancel(); // 通过自定义 boolean 标志位的方式来终止一个线程
    }

    static class Runner implements Runnable {
        private long i;
        private volatile boolean on = true; // 这个字段必须是 volatile 修饰的

        public void run() {
            while (on && !Thread.currentThread().isInterrupted())
                i++;
        }

        public void cancel() {
            on = false;
        }
    }
}

```



## 线程间通信

### JMM

由 `JMM` 定义的偏序规则，`volatile` 和加锁都可以实现线程之间的通信

`volatile`  修饰的变量保证了变量的可见性和有序性

`synchronized` 和其它的加锁机制保证了线程之间的可见性和排它性



### 等待/通知机制

- 等待方（消费者）

  1. 获取对象的锁
  2. 如果条件不满足，则调用锁对象的 `wait()` 方法
  3. 条件满足则执行对应的逻辑

  伪代码形式如下：

  ```java
  synchronized (lock) {
      while (condition) {
          lock.wait();
      }
      // 对应的处理逻辑
  }
  ```

  

- 通知方（生产者）

  1. 获取对象的锁
  2. 改变原有条件
  3. 唤醒所有在等待队列中的线程

  伪代码的形式如下：

  ```java
  synchronized (lock) {
      // 改变等待方的条件
      lock.notifyAll(); // 避免使用 notify，因为它会随机唤醒一个在等待队列中的线程
  }
  ```



### join 方法

在 JDK 1.7 中的描述如下：<sup>[3]</sup>

> **Waiting for the finalization of a thread**
>
> **In some situations, we will have to wait for the finalization of a thread. For example, we may have a program that will begin initializing the resources it needs before proceeding with the rest of the execution. We can run the initialization tasks as threads and wait for its finalization before continuing with the rest of the program. For this purpose, we can use the join() method of the Thread class. When we call this method using a thread object, it suspends the execution of the calling thread until the object called finishes its execution.**

大概意思：主线程等待子线程的终止。如果在主线程的代码块中，遇到了 `t.join()` ，那么当前执行的线程就需要等待 `t` 执行完成之后才能继续执行。

`join` 方法的本质是通过锁对象的 `wait()` 方法来实现的（即 “等待/通知” 机制），对应的源代码如下：

```java
public final synchronized void join(long millis)
    throws InterruptedException {
    /* 
     isAlive() 用于判断当前的线程是否存活，
     这里的主要目的是避免由于锁对象的虚假唤醒带来的影响
    */
    while (isAlive()) {
        wait(0);
    }
    
    // 省略一部分不太重要的代码
}
```

由于是调用锁对象的 `wait()` 方法，因此 `join()` 方法会释放当前持有的锁



参考：

<sup>[1]</sup> 《Java 并发编程实战》

<sup>[2]</sup> https://blog.csdn.net/pange1991/article/details/53860651

<sup>[3]</sup> https://www.cnblogs.com/duanxz/p/5038471.html