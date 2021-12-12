# Java 并发编程（七）线程池

<br />

## 任务的创建与执行

在多线程的编程环境中，理想的情况是每个任务之间都存在理想的边界，各个任务之间相互独立，这样才能够享受到并发带来的明显的性能提升，并且，由于每个任务之间如果都是独立的，对于并发的处理也会简单许多。

一般情况下，显式地创建一个线程，启动一个任务会调用以下的构造函数创建一个 `Thread` 对象：

```java
public Thread(Runnable target) {
    // 。。。。。。。
}
```

然后调用 `Thread` 对象的 `start()` 方法启动这个任务：

```java
Thread thread = new Thread(runnable);
thread.start();
```

这样显式地为每个任务创建一个单独的线程来处理存在以下几个问题：

- 创建一个线程有一定的开销，当任务数量十分大的时候，如果依旧是为每个任务创建一个单独的线程来处理，那么将会消耗大量的计算资源（主要是线程的创建和销毁）
- 将会浪费大量的资源，存活的线程会占用一定的内存空间，特别是当存货的线程的数量远大于处理器的核心数时，将会导致大量的空闲线程的存在，占用大量的内存
- 为每个任务创建单独的线程的系统是不稳定的，在 `JVM` 中，对于线程的数量也是有限制的，特别是 `JVM` 线程栈的限制，在一个 32 位的机器上，一般允许最多存在几千到几万个线程，超过这个数量将会导致 `OutOfMemoryError`，遇到这种情况很难再恢复



> 任务是一组逻辑工作单元，而线程则是使任务异步的方式<sup>[1]</sup>

认识到任务和线程之间的关系很重要，这就是引入线程池的原因，因为任务是一组逻辑工作单元，谁来处理它其实任务本身并不关心；线程是具体处理任务的负载，它只是单纯地处理任务，线程本身不知道任务的具体内容。

<br />

## Executor 框架

线程池简化了线程的管理工作，`java.util.concurrent` 提供了一种灵活的线程池实现做为 `Executor` 框架的一部分，在 Java 类库中，任务执行的主要抽象不是 `Thread`，而是 `Executor`<sup>[1]</sup>

`Executor` 接口的定义如下：

```java
public interface Executor {
    void execute(Runnable command); // Runnable 表示任务
}
```

`Executor` 基于 “生产者—消费者” 模式，提交任务的操作相当于生产者（生产待完成的工作单元），执行任务的线程则相当于消费者（执行这些工作单元）。在 Java 程序中，如果要实现一个 “生产者—消费者” 的设计，最简单的方式通常就是使用 `Executor`<sup>[1]</sup>

<br />

### 工作单元

- `Runnable`

  不带返回值的任务，具体的定义如下：

  ```java
  public interface Runnable {
      public abstract void run();
  }
  ```

  

- `Callable`

  由于 `Runnable` 的任务不带有返回值（`void` 在此认为不是返回值），因此如果想要获取任务的执行结果，使用 `Runnable` 是不能做到的，在这种情况下，需要使用 `Callable` 来定义任务，它认为主入口点（即 `call`） 将返回一个值，并可能抛出一个异常

  具体的定义如下：

  ```java
  public interface Callable<V> {
      V call() throws Exception;
  }
  ```



<br />

### Executor 的生命周期

由于 `JVM` 只有在所有（非守护）线程全部终止之后才会退出，因此，如果不能正确地关闭 `Executor`，那么 `JVM` 就无法退出。

`Executor` 是通过异步的方式来执行任务的，因此对于每个任务的执行，在 `Executor` 中是无法立刻看到处理结果的。为了解决这个问题，`ExecutorService` 继承了 `Executor`，添加了一些用于生命周期管理的方法，具体如下：

```java
public interface ExecutorService extends Executor {
    // 终止任务
    /*
    	执行平缓的关闭过程：不再接受新的任务，同时等待已经提交的任务执行完成
    	（包括那些已经提交但是还没有开始执行的任务）
    */
    void shutdown();
    /*
    	以一种粗暴的方式关闭：尝试取消所有运行中的任务，并且不再启动队列中尚未开始的任务
    */
    List<Runnable> shutdownNow();
    boolean isShutdown();
    boolean isTerminated();
    boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException;
    
    // 提交任务
    <T> Future<T> submit(Callable<T> task);
    <T> Future<T> submit(Runnable task, T result);
    Future<?> submit(Runnable task);
    
    // 执行任务
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException;
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                  long timeout, TimeUnit unit)
        throws InterruptedException;
    <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException;
    <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                    long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
}
```

`ExecutorService` 的生命周期有三种状态：运行、关闭、已终止。



<br />

## 线程池

<br />

### 线程池大小

需要具体问题具体分析，一般的推荐的大小如下：

- 对于 CPU 密集型的程序，一般线程池的大小比较小，为 $N_{cpu} + 1$

- 对于 IO 密集型的程序，一般设置为 $2\times N_{cpu}$  是合理的

- 混合型：	需要进行差分

  得到可用的 CPU 核心数：`N = Runtime.getRuntime().availableProcessors()`

  定义以下一些变量：

$$
\begin{align*}
&N_{cpu} = number \; of \; CPU \;(CPU 的总核心数)\\
&U_{cpu} = tagret \; CPU \; utilization\;(0\leq U_{cpu}\leq\quad CPU的使用率)\\
&\frac{W}{C} = ratio\; of \; wait \; time \; to \; compute \; time\; (等待计算的时间所占的比重)
\end{align*}一般来讲，设置线程池最优的大小为：
$$

$$
N_{threads} = N_{cpu} \ast U_{cpu} \ast(1 + \frac{W}{C})
$$

以上只是推荐的大小，具体受到内存、文件句柄、套接字句柄等一系列资源限制的影响。但是线程池大小的上限还是比较容易计算的，只需要计算每个任务对该资源的需求量，然后用该资源的可用总量除以每个任务的需求量，得到的结果就是线程池大小的上限



### ThreadPoolExecutor

<br />

#### 构造函数

`ThreadPoolExecutor` 是常用的 `Executor` 的具体实现，具体的类结构图如下所示：

<img src="https://s2.loli.net/2021/12/12/tC8GEYkVFUzbTS9.png" alt="ThreadPoolExecutor.png" style="zoom:40%;" />

`ThreadPoolExecutor` 的构造函数如下：

```java
public ThreadPoolExecutor(int corePoolSize,
                          int maximumPoolSize,
                          long keepAliveTime,
                          TimeUnit unit,
                          BlockingQueue<Runnable> workQueue,
                          ThreadFactory threadFactory,
                          RejectedExecutionHandler handler) {
    if (corePoolSize < 0 ||
        maximumPoolSize <= 0 ||
        maximumPoolSize < corePoolSize ||
        keepAliveTime < 0)
        throw new IllegalArgumentException();
    if (workQueue == null || threadFactory == null || handler == null)
        throw new NullPointerException();
    this.corePoolSize = corePoolSize;
    this.maximumPoolSize = maximumPoolSize;
    this.workQueue = workQueue;
    this.keepAliveTime = unit.toNanos(keepAliveTime);
    this.threadFactory = threadFactory;
    this.handler = handler;
}
```

主要的几个构造参数解释如下：

- `corePoolSize`：线程池的基本大小，即线程池的目标大小，在没有任务执行时线程池的大小，只有当工作队列满了的情况下才会创建超出这个数量的线程
- `maximumPoolSize`：线程池的最大大小，表示可以同时活动的线程数量的上限
- `keepAliveTime`：线程的存活时间，如果某个线程的空闲时间超过了这个存活时间，那么将会将这个线程标记为可回收的，并且如果此时线程池中线程的数量已经超过了基本大小，那么就会回收这个线程
- `unit`：线程活动保存时间的单位
- `workQueue`：用于保存等待执行任务的阻塞队列，如 `ArrayBlockingQueue`、`LinkedBlockingQueue`、`SynchronousQueue`、`PriorityBloakingQueue`等（为了避免资源耗尽，尽量选择有界阻塞队列，通过定义包和策略来处理越界的请求）
- `threadFactory`：线程池中创建线程的工厂对象
- `handler`：饱和策略，当线程中的线程的数量已经达到最大大小时，并且此时阻塞队列已满的情况下，对于新来的任务的处理策略，如：`AbortPolicy`、`CallerRunsPolicy`、`DiscardOldestPolicy`、`DiscardPolicy` 或者自定义实现 `RejectedExecutionHandler`

<br />

#### 饱和策略

`JDK` 提供了四种饱和策略用于处理有界队列被填满时再收到请求的情况

- 终止（Abort）策略

  默认的饱和策略，该策略将直接抛出未经检查的 `RejectedExecutionException`，由调用者自行处理这个异常

- 调用者运行（Caller-Runs）策略

  该策略是一种调节机制，使用该策略既不会抛出异常，也不会丢弃任务，而是将某些任务回退给调用者，从而降低新任务的压力。具体地，该策略不会在线程池的某个线程中执行新提交的任务，而是在一个已经调用了 `execute` 的线程中执行该任务

- 丢弃（Discard）策略

  当新的任务无法保存到队列中时，该策略将会悄悄地丢弃这个任务

- 丢弃最旧（Discard-Olds）策略

  该策略将会丢弃下一个将被执行的任务，然后尝试提交任务。如果工作队列是一个优先级队列，那么将会抛弃优先级最高的任务

<br />

#### 扩展 ThreadPoolExecutor

`ThreadPoolExecutor` 提供了几个保留的钩子方法，使得可以在子类中定义一些相关的钩子操作，具体有以下几个方法：

```java
protected void beforeExecute(Thread t, Runnable r) { } // 执行前
protected void afterExecute(Runnable r, Throwable t) { } // 执行后
protected void terminated() { } // 线程池关闭时，一般用于定义释放资源等操作
```

具体的一个示例如下所示，通过继承 `ThreadPoolExecutor`，添加对应的日志打印处理：

```java
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

// 该示例来自 《Java 并发编程实战》
public class TimingThreadPool extends ThreadPoolExecutor {

    public TimingThreadPool() {
        super(1, 1, 0L, TimeUnit.SECONDS, null);
    }

    private final ThreadLocal<Long> startTime = new ThreadLocal<Long>();
    private final Logger log = Logger.getLogger("TimingThreadPool");
    private final AtomicLong numTasks = new AtomicLong();
    private final AtomicLong totalTime = new AtomicLong();
    
    // 执行任务之前打印相关的日志信息，同时记录当前的时间戳
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        log.fine(String.format("Thread %s: start %s", t, r));
        startTime.set(System.nanoTime());
    }
    
    /* 
    	执行任务之后再打印日志信息，由于任务开始之前已经得到了当时的时间戳，
    	因此此时就可以得到该任务的执行时间
    */
    protected void afterExecute(Runnable r, Throwable t) {
        try {
            long endTime = System.nanoTime();
            long taskTime = endTime - startTime.get();
            numTasks.incrementAndGet();
            totalTime.addAndGet(taskTime);
            log.fine(String.format("Thread %s: end %s, time=%dns",
                                   t, r, taskTime));
        } finally {
            super.afterExecute(r, t);
        }
    }

    /*
    	线程池关闭时执行的操作，这里只是打印对应的日志信息
    */
    protected void terminated() {
        try {
            log.info(String.format("Terminated: avg time=%dns",
                                   totalTime.get() / numTasks.get()));
        } finally {
            super.terminated();
        }
    }
}
```



<br />

参考：

<sup>[1]</sup> 《Java 并发编程实战》