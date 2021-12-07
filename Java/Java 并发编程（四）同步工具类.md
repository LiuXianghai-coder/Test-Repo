# Java 并发编程（四）同步工具类

**本文使用的 JDK 版本为 JDK 8**

<br />

## 基本同步工具类

### 闭锁（CountDownLatch）

>   闭锁是一种工具类，可以延迟线程的进度直到其到达终止状态。闭锁的作用相当与一扇门：在闭锁的状态到达之前，这扇门一直是关闭的，没有任何线程能够通过，当到达这扇门之后，这扇门会打开并且允许所有的线程通过。当闭锁到达结束状态之后，将不会再改变状态，因此这扇门将永远保持打开状态。
>
>   闭锁可以用来确保某些活动直到其它活动都完成之后才继续执行，例如：
>
>   - 确保某个计算需要在所有的资源初始化之后才执行
>   - 确保某个服务在其依赖的所有其他服务都启动之后才启动
>   - 等待知道某个操作的所有参与者都就绪再执行

<sup>[1]</sup>

<br />

>   `CountDownLatch` 是一种灵活的闭锁实现，能够适应大多数的场景。
>
>   闭锁状态包括一个计数器，该计数器被初始化为一个正值，表示需要等待的事件数量。`countDown` 方法递减计数器，表示有一个事件已经发生了，而 `awiat` 将会等待计数器达到 0，这表示所有的需要等待的事件都已经发生。
>
>   如果计数器的值非 0，那么 `await` 方法将会一直阻塞，直到计数器为 0，或者等待中的线程中断，或者等待超时

<sup>[1]</sup>

<br />

#### 使用示例

闭锁的一个常用的使用情况是记录多线程程序的运行时间

一般来讲，常规的通过获取时间戳的方式来计算并发程序的运行时间是不准确的，因为多个线程之间很难保证程序的运行顺序，因此也就很难得到比较精确的运行时间。通过引入 `CountDownLatch` 来使得多个线程的起点时间，能够比较精确地得到总的执行时间

具体的示例代码如下：

```java
import java.util.concurrent.CountDownLatch;

// 该代码参考自 《Java 并发编程实战》
public class TestHarness {
  /*
  	通过两个闭锁对象，一个被称作 “开始门”，另一个被称为 “结束门”，每个线程都需要等待 “开始门” 打开之后才能执行对应的任务，
  	在执行完对应的任务之后将 “结束门” 的线程数 -1，表示当前的线程已经执行完成了，最后，当 “结束门” 完全被打开之后，再统计当前
  	的时间戳，即可得到一个比较精确的运行时间
  */
  public long timTask(int nThreads, final Runnable task)
    throws InterruptedException {
    final CountDownLatch starGate   =   new CountDownLatch(1); // 开始计时的闭锁
    final CountDownLatch endGate    =   new CountDownLatch(nThreads); // 任务执行完时的闭锁

    for (int i =0; i < nThreads; ++i) {
      Thread thread = new Thread(() -> {
        try {
          starGate.await(); // 对每个开启的线程，首先阻塞，知道 startGate 打开闭锁，任务正式开始

          try {
            task.run(); 
          } finally {
            // 每执行玩一个任务，endGate 的计数器 -1，当到达 0 时则说明所有任务执行结束
            endGate.countDown();
          }
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      });

      thread.start();
    }

    long start = System.nanoTime();
    starGate.countDown(); // 开启 startGate 闭锁，执行任务
    endGate.await(); // 阻塞当前线程知道所有的任务执行完成
    long end = System.nanoTime();

    return end - start;
  }

  public static class Task implements Runnable {
    @Override
    public void run() {
      System.out.println("Task Running......");
    }
  }

  public static void main(String[] args) throws InterruptedException {
    TestHarness harness = new TestHarness();
    System.out.printf("Take time %d\n nanos", harness.timTask(10, new Task()));
  }
}
```

<br />

#### 源码解析

<br />

### FutureTask

>   事实上，`FutureTask` 也可以用做闭锁，`FutureTask` 表示的计算是通过 Callable 来实现的，相当于一种可以生成计算结果的 `Runnable`，并且处于以下三种状态：
>
>   1. 等待运行（Waiting to run）
>   2. 正在运行（Running）
>   3. 运行完成（Completed），表示计算的所有可能结束方式，包括正常结束、由于取消而结束和由于一场而结束等。当 `FutureTask` 进入完成状态之后，它会永远停止在这个状态上
>
>   `Future.get` 的行为取决于任务的状态，如果任务已经完成，那么 `get` 将会立即返回结果，否则 `get` 将阻塞直到任务进入完成状态，然后返回计算结果或者抛出异常。
>
>   `FutureTask` 在 `Executor` 中表示异步任务，此外还可以用来表示一些时间较长的计算，这些计算可以在使用计算结果之前启动。

<sup>[1]</sup>

<br />

#### 使用示例

该同步工具类可能过时了，它的本意是提供一种类似于异步的方式来执行对应的任务，但是最终调用 `get()` 方法获取处理结果的这个过程它依旧是阻塞的。如果希望使用更加优秀的异步处理工具，不妨试试 `Reactor`

一般的使用如下：

```java
public class PreLoader {
  private final Supplier<ProductInfo> loadProduct = ProductInfo::new; // 提供 ProductInfo 对象的 Supplier 函数

  private final FutureTask<ProductInfo> futureTask =
    new FutureTask<>(loadProduct::get); // Callable 也是一个 Supplier 类型的函数

  private final Thread thread = new Thread(futureTask);

  public void start() {thread.start();}

  public ProductInfo get()
    throws ExecutionException, InterruptedException {
    try {
      return futureTask.get(); // 在得到处理结果之前阻塞当前线程
    } catch (ExecutionException | InterruptedException e) {
      /* 
      * 值得注意的是，Callable 表示的任务可能抛出未受检查的和经过检查的异常，并且任何代码都有可能抛出一个 Error。
      */
      throw e;
    }
  }

  public static void main(String[] args) throws ExecutionException, InterruptedException {
    for (int i = 0; i < 10; ++i) {
      PreLoader loader = new PreLoader();
      loader.start();
      System.out.println(loader.get().toString());
    }
  }
}
```



<br />

#### 源码解析



### 信号量（Semaphore）

>   计数信号量用于控制同时访问某个特定资源的操作数量，或者同时执行某个特定操作的数量。计数信号量还可以用来实现某种资源池，或者对容器施加边界。
>
>   Semaphore 中管理着一组虚拟的许可（Permit），许可的初始数量可以在构造函数时指定。在执行构造函数的时候可以首先获得许可（如果还存在剩余的许可），并在使用之后再释放许可。如果没有许可可用，那么 `acquire` 方法将一直阻塞，直到有许可或者被中断或超时。`release` 方法将一个许可返回给信号量
>
>   Semaphore 可以用于实现资源池（如数据库连接池）、有界阻塞容器等
>
>   
>
>   Semaphore 不会将许可将线程关联起来，因此在一个线程中得到的许可可以在另一个线程中释放

<sup>[1]</sup> 

<br />

#### 使用示例

可以通过使用信号量来实现一个简单的有界容器，具体的代码如下：

```java
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;

public class BoundHashSet<T> {
  private final Set<T> set;

  private final Semaphore sem;

  public BoundHashSet(int capacity) {
    this.set = Collections.synchronizedSet(new HashSet<>());
    this.sem = new Semaphore(capacity); // 定义的许可数，在这里定义可以有界容器的大小
  }

  public boolean add(T o) throws InterruptedException {
    sem.acquire();
    boolean wasAdded = false;
    try {
      wasAdded = this.set.add(o);;
      return wasAdded;
    } finally {
      if (!wasAdded) // 如果此次添加失败，那么释放该许可
        this.sem.release();
    }
  }

  public boolean remove(Object o) {
    boolean wasRemoved = this.set.remove(o);
    if (wasRemoved)
      this.sem.release();
    return wasRemoved;
  }
}
```



<br />

#### 源码解析



### 栅栏（Barrier）

>   栅栏类似于闭锁，它能阻塞一组线程知道某个事件发生。
>
>   栅栏与闭锁的区别在于，所有的线程必须同时到达栅栏位置，才能继续执行；闭锁一般用于等待事件，而栅栏则用于等待其他线程。

<sup>[1]</sup> 

<br />

>   `CyclicBarrier` 可以使一定数量的参与方反复地在栅栏位置汇集，在并行迭代算法（将一个问题分解为一系列相互独立的子问题）中非常有用。
>
>   `CyclicBarrier` 可以将一个栅栏操作传递给构造函数，这个操作是一个 `Runnable`，当成功通过栅栏时会在一个子线程中执行它，但是在阻塞线程被释放之前是不能被执行的。
>
>   
>
>   当线程到达栅栏时将调用 `await` 方法，这个方法将阻塞直到所有线程都到达栅栏位置；如果所有线程都到了栅栏位置，那么这时栅栏将会打开，此时所有的线程都将被释放，而栅栏将被重置以便下次使用。
>
>   如果对 `await` 的调用超时，或者 `await` 阻塞的线程被中断，那么栅栏就被认为是打破了，所有阻塞的 `await` 调用都将终止并且抛出 `BrokenBarrierException`。
>
>   如果成功地通过栅栏，那么 `await` 将为每个线程返回同一个唯一的到达索引号，可以利用这些索引来选举产生一个领导线程，并在下一次的迭代中由该领导线程执行一些特殊的工作。

<sup>[1]</sup> 

<br />

#### 使用示例





#### 源码解析



## 读写锁





<sup>[1]</sup> 《Java 并发编程实战》

