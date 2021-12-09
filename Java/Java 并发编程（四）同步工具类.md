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

闭锁的线程执行情况如下图所示：

<img src="https://s6.jpg.cm/2021/12/08/LdpfTp.png" style="zoom:60%" /><sup>[2]</sup>

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

`CountDownLatch` 是典型 `AQS` 的共享模式的使用

首先查看 `CountDownLatch` 中有关 `AQS` 具体子类的定义：

```java
// CountDownLatch.Sync

/* 
	具体实现的同步类，这是每个同步工具类与 AQS 关联的地方，也是实现同步的关键所在
*/
private static final class Sync extends AbstractQueuedSynchronizer {
    private static final long serialVersionUID = 4982264981922014374L;

    Sync(int count) {
        /* 
        	将 AQS 的 state 视为当前 CountDownLatch 持有的钥匙数，
        	只有所有的钥匙都正确匹配了 Latch 才能打开这个锁，执行后面的内容
        */
        setState(count);
    }
    // 省略部分继承自 AQS 的自定义实现方法，具体可以参见有关 AQS 共享模式的部分
}
```

这部分内容主要是有关于 `AQS` 共享模式的使用，只是 `CountDownLatch` 将 `state` 看做是打开这扇门的钥匙，只有所有的钥匙都匹配了才会打开这个锁，即将 `state` 置为 0 唤醒阻塞队列中的所有节点以继续执行

`CountDownLatch` 关键的两个方法为 `await()` 和 `countDown()`，具体的实现如下：

```java
public class CountDownLatch {
    // 省略一些其它的不太关键的代码	
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    public boolean await(long timeout, TimeUnit unit)
        throws InterruptedException {
        // 带有超时时间的 timeOut
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    public void countDown() {
        // 相当于有一把钥匙已经匹配了
        sync.releaseShared(1);
    }
}
```

`CountDownLatch` 的`await()` 方法主要依赖对于 `tryAcquireShared` 的具体实现，`CountDownLatch` 对此的具体实现如下：

```java
protected int tryAcquireShared(int acquires) {
    // 如果所有的钥匙都已经被匹配了，那么就可以打开这个锁了
    return (getState() == 0) ? 1 : -1; 
}
```

`countDown()` 方法主要依赖于 `tryReleaseShared` 方法的实现，在 `CountDownLatch` 中对此的实现如下：

```java
protected boolean tryReleaseShared(int releases) {
    for (;;) { // 使用永正循环防止 CAS 失败
        int c = getState();
        if (c == 0)
            return false;
        // 已经有一个钥匙配对了
        int nextc = c - 1;
        if (compareAndSetState(c, nextc))
            return nextc == 0;
    }
}
```

上文的介绍只是大概介绍一下有关 `AQS` 具体子类实现的模板方法， 真正核心的部分依旧位于 `AQS` 中，如果不嫌弃的话，可以看看我的这篇：https://www.cnblogs.com/FatalFlower/p/15656009.html



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

我认为这个工具类已经过时了，如果有必须使用 `FutureTask` 来实现的异步任务，那么我会更加倾向于使用 `Reactor` 来实现。出于这个原因，对于这个工具类我不打算做进一步的源码分析

源码分析略过。。。。



<br />

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

同样的，`Semaphore` 也是基于 `AQS` 的。

`acquire`  方法：

```java
/*
	都是基于 AQS 方法的调用，不做过多的介绍
*/

public void acquire() throws InterruptedException {
    sync.acquireSharedInterruptibly(1);
}

public void acquireUninterruptibly() {
    sync.acquireShared(1);
}

public void acquire(int permits) throws InterruptedException {
    if (permits < 0) throw new IllegalArgumentException();
    sync.acquireSharedInterruptibly(permits);
}

public void acquireUninterruptibly(int permits) {
    if (permits < 0) throw new IllegalArgumentException();
    sync.acquireShared(permits);
}
```

`release` 方法：

```java
/*
	都是基于 AQS 父类方法，不做过多的介绍
*/

public void release() {
    sync.releaseShared(1);
}

public void release(int permits) {
    if (permits < 0) throw new IllegalArgumentException();
    sync.releaseShared(permits);
}
```

`Semaphore` 也存在 “公平” 和 “非公平” 的两种具体实现，主要的区别在于 “公平” 的实现会首先检查在阻塞队列中是否已经存在争夺锁的线程节点，除此之外并没有什么大的不同



<br />

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

栅栏的线程的一般执行情况如下图所示：

<img src="https://s6.jpg.cm/2021/12/08/LdpaaL.png" style="zoom:80%"><sup>[2]</sup>

<br />

#### 使用示例

一个经典的使用场景就是用于合并多个已经排序好的数组，每个线程可以采用不同的排序方式对不同区域的数据进行排序，最后再将这些已经排序好的数组归并，使得整个数据都是有序的

具体的实现如下：

```java
// 该实际使用的情况参考自 《Unix 环境高级编程》（第三版）中有关线程中内存屏障的实际使用
public class BarrierExample {
    private final CyclicBarrier barrier;
    private final int[] array;
    private final int[] aux;
    private final Thread[] threads;

    class MergeAction implements Runnable {
        private final int[] idxs;
        private final int unit;

        MergeAction(int[] idxs, int unit) {
            this.idxs = idxs;
            this.unit = unit;

            for (int i = 0; i < idxs.length; ++i)
                idxs[i] = i * unit;
        }

        @Override
        public void run() {
            int idx = 0;
            int n = idxs.length;
            while (idx < array.length) {
                int index = 0;
                int tmp = 0, min = Integer.MAX_VALUE;
                for (int i = 0; i < n; ++i) {
                    if (idxs[i] >= array.length || idxs[i] >= (i + 1) * unit)
                        continue;
                    int j = idxs[i];
                    if (array[j] < min) {
                        index = i;
                        min = array[j];
                        tmp = j;
                    }
                }
                idxs[index]++;
                aux[idx++] = array[tmp];
            }

            System.arraycopy(aux, 0, array, 0, aux.length);
        }
    }

    public BarrierExample(final int size) {
        array = new int[size];
        aux = new int[size];

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < size; ++i)
            array[i] = random.nextInt(0, 2*size);

        int cnt = Runtime.getRuntime().availableProcessors();
        int unit = (int) Math.ceil(size * 1.0 / cnt);
        threads = new Thread[cnt];
        // 闭锁用于统计运行时间
        startGate = new CountDownLatch(1);
        endGate = new CountDownLatch(cnt);

        for (int i = 0; i < cnt; ++i)
            threads[i] = new Thread(
                    new SortThread(
                            i * unit,
                            Math.min(array.length, (i + 1) * unit)
                    ),
                    "Sort-Thread-" + i
            );
        barrier = new CyclicBarrier(cnt, new MergeAction(new int[cnt], unit));
    }

    class SortThread implements Runnable {
        private final int start, end;

        SortThread(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public void run() {
            // 每个线程采用 Java 内置的排序算法进行排序
            Arrays.sort(array, start, end);
            try {
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
        }
    }

    public void start() throws InterruptedException {
        for (Thread thread : threads) thread.start();
    }
}
```

与在 `CPU` 处理频率为 2.4 GH，四个物理核心（八个逻辑核心）的机器上，该程序与使用单线程的性能比较如下图所示：

<img src="https://s6.jpg.cm/2021/12/08/LdepbX.png">

在数据量较大时有较大的性能差异，当数据量达到 2 亿级别的情况下，使用多线程的方式进行排序大约需要耗费 3500 ms，而使用单线程的方式进行排序大概需要 22000 ms，使用多线程的方式使得性能提升了 大约 6.25 倍。

使用闭锁也能完成该功能，栅栏与闭锁的最大区别就是闭锁在打开之后就无法再被使用了，而栅栏在完全打开后依旧可以再次使用

<br />

#### 源码解析

栅栏也是基于 `AQS` 来实现的

首先查看 `CyberBarrier` 对象的相关属性：

```java
public class CyclicBarrier {
  /*
  	由于 CyberBarrier 是可重入的，因此把每次从开始使用到穿过栅栏当做 “一代” 或者 “一个周期”
  */
  private static class Generation {
    boolean broken = false;
  }

  // 使用可重入锁来实现拦截的功能
  private final ReentrantLock lock = new ReentrantLock();
  
  /* 
  	通过 CyclicBarrier 的栅栏的条件，
  	在 CyclicBarrier 中的条件为所有的线程都已经到达了该栅栏位置
  */
  private final Condition trip = lock.newCondition();

  /*
  	需要到达栅栏的线程数
  */
  private final int parties;

  /*
  	在越过栅栏之前要执行的任务
  */
  private final Runnable barrierCommand;
  
  // 当前所处的 “代”
  private Generation generation = new Generation();

  /*
  	还未到达栅栏的线程的数量，初始数量为 parties，
  	每当一个线程到达栅栏，该数量就 -1
  */
  private int count;
}
```

新的 “一代” 的开启逻辑：

```java
private void nextGeneration() {
  // signal completion of last generation
  /*
  	回忆一下 AQS 的 ConditionObject，调用该方法将会将 ConditionObject 
  	的条件队列中的所有的线程节点移动到阻塞队列
  */
  trip.signalAll();
  
  // set up next generation
  /*
  	重新生成新的一代
  */
  count = parties;
  
  generation = new Generation();
}
```

“打破栅栏” 的逻辑：

```java
private void breakBarrier() {
  // 设置 broken 状态为 true
  generation.broken = true;
  // 重置 count 初始值为 parties
  count = parties;
  /*
  	唤醒所有在 trip 的条件队列中的线程，将它们放入到阻塞队列
  */
  trip.signalAll();
}
```

<br />

`await` 方法：

```java
// 不带超时机制的 await 方法
public int await() throws InterruptedException, BrokenBarrierException {
  try {
    return dowait(false, 0L);
  } catch (TimeoutException toe) {
    throw new Error(toe); // cannot happen
  }
}

// 带超时机制的 await 方法，如果超时则抛出 TimeoutException
public int await(long timeout, TimeUnit unit)
  throws 	InterruptedException,
					BrokenBarrierException,
					TimeoutException {
  return dowait(true, unit.toNanos(timeout));
}
```

这两个方法都调用了 `dowait` 方法，具体查看 `dowait` 方法的逻辑：

```java
// 栅栏的核心逻辑部分

private int dowait(boolean timed, long nanos)
  throws 	InterruptedException, 
					BrokenBarrierException,
					TimeoutException {
  final ReentrantLock lock = this.lock;
  // 首先尝试获取锁
  lock.lock();
  try {
    final Generation g = generation;
    
    // 检查栅栏是否被打破，如果被打破，则抛出 BrokenBarrierException
    if (g.broken)
      throw new BrokenBarrierException();

    // 检查中断状态，如果这个线程已经被中断了，则抛出 InterruptedException
    if (Thread.interrupted()) {
      breakBarrier();
      throw new InterruptedException();
    }
    
    /*
    	index 是这个 dowait 的返回值 
    */
    int index = --count;
    
    /*
    	如果 index 为 0，说明所有的线程都已经到达了栅栏上，准备通过栅栏
    */
    if (index == 0) {  // tripped
      boolean ranAction = false;
      try {
        // 执行在通过栅栏之前要执行的任务（如果定义了）
        final Runnable command = barrierCommand;
        if (command != null)
          command.run();
        /*
        	将 ranAction 设置为 true 表示在执行 command 任务时没有发生异常退出的情况
        */
        ranAction = true;
        /*
        	唤醒所有在 ConditionObject 对象中的线程节点，将它们放入到阻塞队列中
        	然后再生成新的 “一代”
        */
        nextGeneration();
        return 0;
      } finally {
        if (!ranAction)
          /*
          	执行到这里说明在执行 command 任务时发生了异常，在这种情况下需要打破栅栏，
          	唤醒所有的等待线程，设置 broken 为 true，重置 count 为 parties
          */
          breakBarrier();
      }
    }

    // loop until tripped, broken, interrupted, or timed out
    /*
    	如果最后一个线程调用了 await，那么就会直接返回了
    	如果不是最后一个到达栅栏的线程，那么就会执行下面的代码
    */
    for (;;) {
      try {
        /* 
        	如果带有超时机制，调用带有超时的 Condition 的 await 方法，
        	然后等待，直到最后一个线程调用 await
        */
        if (!timed)
          trip.await(); // 注意，Condition 的 await 会释放锁
        else if (nanos > 0L)
          nanos = trip.awaitNanos(nanos);
      } catch (InterruptedException ie) {
        /* 
        	如果执行到这里，说明正在等待的线程在调用 await (Condition 的 await 方法) 
        	的过程中被中断了
        */
        if (g == generation && ! g.broken) {
          // 打破栅栏，然后抛出异常
          breakBarrier();
          throw ie;
        } else {
          /*
          	到这里，说明新的 “一代” 已经产生了，即最后一个线程 await 执行成功，
          	在这种情况下就没有必要抛出异常了，记录这个中断信息即可
          	
          	如果栅栏已经被打破了，那么也不应该抛出 InterruptedException 异常，
          	而是抛出 BrokenBarrierException  异常
          */
          Thread.currentThread().interrupt();
        }
      }
      
      // 检查栅栏是否被打破
      if (g.broken)
        throw new BrokenBarrierException();

      /*
      	如果最后一个线程执行完指定的任务时，会调用 nextGeneration 方法来创建一个新的 “代”
      	然后再释放掉锁，其它线程从 Condition 对象的 await 方法中获取到锁并返回，就会满足下面的条件
      */
      if (g != generation)
        return index;
      
      // 如果存在超时限制，并且已经超时，则抛出 TimeoutException
      if (timed && nanos <= 0L) {
        breakBarrier();
        throw new TimeoutException();
      }
    }
  } finally {
    // 记得一定要在 finally 中释放锁
    lock.unlock();
  }
}
```

查看有多少个线程已经到达了栅栏上：

```java
public int getNumberWaiting() {
  final ReentrantLock lock = this.lock;
  lock.lock();
  try {
    return parties - count;
  } finally {
    lock.unlock();
  }
}
```

检查栅栏是否被打破的方法：

```java
public boolean isBroken() {
  final ReentrantLock lock = this.lock;
  lock.lock();
  try {
    return generation.broken;
  } finally {
    lock.unlock();
  }
}
```

总结一下会打破栅栏的几种情况：

-   在执行 `await` 的线程被中断了
-   执行 `command` 任务时出现了异常
-   带有超时限制的 `await` 方法在被唤醒时发现超时了

<br />

最后，重置栅栏的方法：

```java
public void reset() {
  final ReentrantLock lock = this.lock;
  lock.lock();
  try {
    /*
    	简单来讲就是 “打破旧的，创建新的”
    */
    breakBarrier();   // break the current generation
    nextGeneration(); // start a new generation
  } finally {
    lock.unlock();
  }
}
```

<br />

## 读写锁

`JUC` 中关于读写锁的接口定义如下：

```java
// java.util.concurrent.locks.ReadWriteLock
public interface ReadWriteLock {
    // 返回一个读锁
    Lock readLock();
    
    // 返回一个写锁
    Lock writeLock();
}
```

在 `JUC` 中，常用的具体实现为 `ReentrantReadWriteLock`，因此，在这里以 `ReentrantReadWriteLock` 为例来介绍读写锁的相关内容。

<br />

### 基本使用

读写锁的一个常用的使用场景就是对于数据的读取操作，在大部分的业务场景下，发生读的情况要比发生写的概率要高很多。在这种情况，可以针对热点数据进行缓存，从而提高系统的响应性能。

使用示例如下：

```java
// 该代码来源于 JDK 的官方文档，稍作了一点修改
class CachedData {
    Object data;
    volatile boolean cacheValid;
    final DataAccess access;
    final Order order;
    final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

    CachedData(DataAccess access, Order order) {
        this.access = access;
        this.order = order;
    }

    void processCachedData() {
        rwl.readLock().lock();
        if (!cacheValid) { // 当前缓存已经失效了，即已经发生了写事件
            // 在获取写锁之前必须释放读锁，否则会造成死锁
            rwl.readLock().unlock();
            rwl.writeLock().lock();
            try {
                /*
                	重新判断缓存是否是失效的，
                	因为在这个过程中可能已经有其它的线程对这个缓存的数据进行修改了
                */
                if (!cacheValid) {
                    data = access.queryData();
                    cacheValid = true;
                }
                /*
                	获取读锁，在持有写锁的情况下，可以获得读锁，这也被称为 “锁降级”
                */
                rwl.readLock().lock();
            } finally {
                // 释放写锁，此时依旧持有读锁
                rwl.writeLock().unlock();
            }
        }

        try {
            order.useData(data);
        } finally {
            // 注意最后一定要释放锁
            rwl.readLock().unlock();
        }
    }

    interface DataAccess {
        Object queryData();
    }

    interface Order {
        void useData(Object data);
    }
}
```

<br />

### 源码解析

<br />

#### 构造函数

首先，查看 `ReentrantReadWriteLock` 实例对象的属性

```java
public class ReentrantReadWriteLock
    implements ReadWriteLock, java.io.Serializable {
    // ReentrantReadWriteLock 的静态内部类，为读锁
    private final ReentrantReadWriteLock.ReadLock readerLock;
    // ReentrantReadWriteLock 的静态内部类，为写锁
    private final ReentrantReadWriteLock.WriteLock writerLock;

    // 同步工具类，为 AQS 的具体子类
    final Sync sync;
    
    public ReentrantReadWriteLock() {
        this(false);
    }
    
    // 构造函数，初始化 ReentrantReadWriteLock，默认情况选择使用非公平的同步工具 
    public ReentrantReadWriteLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
        readerLock = new ReadLock(this);
        writerLock = new WriteLock(this);
    }
}
```

再查看 `ReadLock` 和 `WriteLock` 的相关定义，首先查看 `ReadLock` 相关的源代码：

```java
public static class ReadLock implements Lock, java.io.Serializable {
    private final Sync sync;

    protected ReadLock(ReentrantReadWriteLock lock) {
        sync = lock.sync; // 注意这里的 sync
    }
    // 省略其它一些不是特别重要的代码
}
```

再查看 `WriteLock` 相关的源代码：

```java
public static class WriteLock implements Lock, java.io.Serializable {
    private final Sync sync;

    protected WriteLock(ReentrantReadWriteLock lock) {
        sync = lock.sync; // 注意这里的 sync
    }
    // 省略其它一些不是特别重要的代码
}
```

可以看到，`ReadLock` 和 `WriteLock` 都使用了同一个 `Sync` 实例对象来维持自身的同步需求，这点很关键

<br />

#### 原理

`ReadLock` 中关于获取锁和释放锁的源代码：

```java
// 获取锁
public void lock() {
    sync.acquireShared(1);
}

public void lockInterruptibly() throws InterruptedException {
    sync.acquireSharedInterruptibly(1);
}

// 释放锁
public void unlock() {
    sync.releaseShared(1);
}
```

`WriteLock` 中关于获取锁和释放锁的源代码：

```java
// 获取锁
public void lock() {
    sync.acquire(1);
}

public void lockInterruptibly() throws InterruptedException {
    sync.acquireInterruptibly(1);
}

// 释放锁
public void unlock() {
    sync.release(1);
}
```

通过对比 `ReadLock` 和 `WriteLock` 中获取锁和释放锁的源代码，很明显，`ReadLock` 是以 “共享模式” 的方式获取和释放锁，而 `WriteLock` 则是通过以独占的方式来获取和释放锁。这两种获取和释放锁的实现都在 `AQS` 中定义，在此不做过多的详细介绍

再结合上文关于 `ReadLock` 和 `WriteLock` 的构造函数，可以发现它们是使用了同一个 `AQS` 子类实例对象，也就是说，在 `ReentrantReadWriteLock` 中的 `AQS` 的具体子类既使用了“共享模式”，也使用了“独占模式”

更一般地来讲，回忆一下 `AQS` 关于 “共享模式” 和 “独占模式”  对于 `state` 变量的使用，“共享模式” 将 `state` 共享，每个线程都能访问 `state`；“独占模式” 下，`state` 被视作是获取到锁的状态，0 表示还没有线程获取该锁，大于 0 则表示线程获取锁的重入次数

为了能够实现 `ReentrantReadWriteLock` 中的两个模式的共用的功能，`ReentrantReadWriteLock` 中 `Sync` 类对 `state` 进行了如下的处理：

> ReentrantReadWriteLock 使用了一个 16 位的状态来表示写入锁的计数，并且使用了另外一个 16 位的状态来表示读锁的计数 

就是说，`state` 变量已经被拆分成了两部分，由于 `state` 是一个 32 位的整数，现在 `state` 的前 16 位用于单独处理“共享模式”，而后 16 位则用于处理 “独占模式”

<br />

#### Sync

核心部分就是分析 `Sync` 的源代码，在这里定义了对 `state` 变量的修改以及获取锁和释放锁的逻辑

首先查看 `Sync` 相关字段属性：

```java
abstract static class Sync extends AbstractQueuedSynchronizer {
    // 这几个字段的作用就是将 state 划分为两部分，前 16 位为共享模式，后 16 位为独占模式
    static final int SHARED_SHIFT   = 16;
    static final int SHARED_UNIT    = (1 << SHARED_SHIFT);
    // -1 的目的值为了得到最大值
    static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1; 
    static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;
    
    // 取 c 的前 16 位, 只需要右移即可
    static int sharedCount(int c)    { return c >>> SHARED_SHIFT; }
    // 取 c 的后 16 位，只要与对应的掩码按位与即可
    static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }

    // 该类的作用是记录每个线程持有的读锁的数量
    static final class HoldCounter {
        // 线程持有的读锁的数量
        int count = 0;
        // 线程的 ID
        final long tid = getThreadId(Thread.currentThread());
    }
    
    // ThreadLocal 的子类
    static final class ThreadLocalHoldCounter
        extends ThreadLocal<HoldCounter> {
        public HoldCounter initialValue() {
            return new HoldCounter();
        }
    }
    
    /*
    	用于记录线程持有的读锁信息
    */
    private transient ThreadLocalHoldCounter readHolds;

    /*
    	用于缓存，用于记录 “最后一个获取读锁” 的线程的读锁的重入次数
    */
    private transient HoldCounter cachedHoldCounter;

    /*
    	第一个获取读锁的线程（并且未释放读锁）
    */
    private transient Thread firstReader = null;
    // 第一个获取读锁的线程持有的读锁的数量（重入次数）
    private transient int firstReaderHoldCount;

    Sync() {
        // 初始化 readHolds
        readHolds = new ThreadLocalHoldCounter();
        // 确保 readHolds 的可见性
        setState(getState()); // ensures visibility of readHolds
    }
}
```

#### 读锁

- 读锁的获取

    再回到 `ReadLock` 部分，获取锁的源代码如下：

    ```java
    public void lock() {
        sync.acquireShared(1);
    }
    
    // 与之对应的 AQS 的代码
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }
    ```

    重点在于 `tryAcquireShared(arg)` 方法，该方法在 `Sync` 中定义：

    ```java
    protected final int tryAcquireShared(int unused) {
        Thread current = Thread.currentThread();
        int c = getState();
        
        /*
        	exclusiveCount(c) != 0 说明当前有线程持有写锁，在这种情况下就不能直接获取读锁
        	但是如果持有写锁的线程时当前线程，那么就可以继续获取读锁
        */
        if (exclusiveCount(c) != 0 &&
            getExclusiveOwnerThread() != current)
            return -1;
        
        int r = sharedCount(c); // 读锁的获取次数
        
        if (!readerShouldBlock() && // 读锁是否需要阻塞
            r < MAX_COUNT && // 判断获取锁的次数是否溢出（2^16 - 1）
            compareAndSetState(c, c + SHARED_UNIT)) { // 将读锁的获取次数 +1
            
            // 此时已经获取到了读锁
            
            // r == 0 说明线程是第一个获取读锁的，或者前面获取读锁的线程都已经释放了读锁
            if (r == 0) {
                firstReader = current;
                firstReaderHoldCount = 1;
            } else if (firstReader == current) { // 是否重入
                firstReaderHoldCount++;
            } else {
                // 更新缓存，即最后一个获取读锁的线程
                HoldCounter rh = cachedHoldCounter;
                if (rh == null || rh.tid != getThreadId(current))
                    cachedHoldCounter = rh = readHolds.get();
                else if (rh.count == 0)
                    // 
                    readHolds.set(rh);
                rh.count++;
            }
            return 1;
        }
        return fullTryAcquireShared(current);
    }
    ```

    

- 读锁的释放

#### 写锁



<br />

<sup>[1]</sup> 《Java 并发编程实战》

<sup>[2]</sup> https://javadoop.com/post/AbstractQueuedSynchronizer-3
