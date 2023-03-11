# Java 并发编程（五）读写锁

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

## 基本使用

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

## 源码解析

<br />

### 构造函数

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

### 原理

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

> `ReentrantReadWriteLock` 使用了一个 16 位的状态来表示写入锁的计数，并且使用了另外一个 16 位的状态来表示读锁的计数 

就是说，`state` 变量已经被拆分成了两部分，由于 `state` 是一个 32 位的整数，现在 `state` 的前 16 位用于单独处理“共享模式”，而后 16 位则用于处理 “独占模式”

<br />

### Sync

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

### 读锁

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
            // 缓存当前的线程持有的读锁的数量
            readHolds.set(rh);
          rh.count++;
        }
        
        // 返回一个大于 0 的数，表示已经获取到了读锁
        return 1;
      }
      
      return fullTryAcquireShared(current);
    }
    ```

    如果要走到最后一个 `return` 语句，可能有以下几种情况：

    - `readerShouldBlock()` 返回 `true`，这可能有两种情况：在 `FairSync` 中的 `hasQueuedPredecessors()` 方法返回 `true`，即阻塞队列中存在其它元素在等待锁；在 `NoFairSync` 中的 `apparentlyFirstQueuedIsExclusive()` 方法返回 `true`，即判断阻塞队列中 `head` 的后继节点是否是用来获取写锁的，如果是的话，那么让这个锁先来，避免写锁饥饿
    - 持有写锁的数量超过最大值（2^16 - 1）
    - `CAS` 失败，即存在竞争关系，可能是多个线程争夺一个读锁，或者多个线程争夺一个写锁

    如果是发生了以上几种情况，那么就需要调用 `fullTryAcquireShared` 再次尝试

    <br />

    `fullTryAcquireShared(current)` 方法对应的源代码：

    ```java
    /*
    	引入这个方法的目的是为了减少锁竞争
    */
    final int fullTryAcquireShared(Thread current) {
      HoldCounter rh = null;
      for (;;) { // 永真循环避免由于 CAS 失败直接退出的情况
        int c = getState();
        // 如果其它线程持有了写锁，自然是获取不到锁了，因此需要进入到阻塞队列
        if (exclusiveCount(c) != 0) {
          if (getExclusiveOwnerThread() != current)
            return -1;
          // else we hold the exclusive lock; blocking here
          // would cause deadlock.
        } else if (readerShouldBlock()) {
          // 处理重入
          if (firstReader == current) {
            // assert firstReaderHoldCount > 0;
          } else {
            if (rh == null) {
              rh = cachedHoldCounter;
              if (rh == null || rh.tid != getThreadId(current)) {
                /*
                	cachedHoldCounter 缓存的不是当前的线程，那么到 ThreadLocal 中获取当前线程的 HolderCounter，
                	如果线程从来没有初始化过 ThreadLocal 的值，那么 get() 方法将会执行初始化
                */
                rh = readHolds.get();
                
                /*
                	rh.count == 0 说明上一行代码只是单纯地初始化，那么它依旧是需要去排队的
                */
                if (rh.count == 0)
                  readHolds.remove();
              }
            }
            if (rh.count == 0)
              return -1;
          }
        }
        
        if (sharedCount(c) == MAX_COUNT)
          throw new Error("Maximum lock count exceeded");
        
        if (compareAndSetState(c, c + SHARED_UNIT)) {
          /*
          	如果在这里已经 CAS 成功了，那么久意味着成功获取读锁了，
          	下面要做的就是设置 firstReader 或 cachedHoldCounter
          */
          
          if (sharedCount(c) == 0) {
            firstReader = current;
            firstReaderHoldCount = 1;
          } else if (firstReader == current) {
            firstReaderHoldCount++;
          } else {
            // 设置 cachedHoldCounter 为当前的线程
            if (rh == null)
              rh = cachedHoldCounter;
            if (rh == null || rh.tid != getThreadId(current))
              rh = readHolds.get();
            else if (rh.count == 0)
              readHolds.set(rh);
            rh.count++;
            cachedHoldCounter = rh; // cache for release
          }
          
          return 1; // 大于 0 表示获取到了锁
        }
      }
    }
    ```

    

- 读锁的释放

    `ReadLock` 释放锁的代码如下：

    ```java
    public void unlock() {
      sync.releaseShared(1);
    }
    ```

    这个方法位于 `AQS` 中，具体的定义如下：

    ```java
    // 就是一般的释放共享变量的逻辑，具体的模版方法为 tryReleaseShared，需要子类去具体实现
    public final boolean releaseShared(int arg) {
      if (tryReleaseShared(arg)) {
        doReleaseShared(); // 这里是 AQS 的相关知识，再次不做过多的介绍
        return true;
      }
      return false;
    }
    ```

    `Sync` 中对于 `tryReleaseShared` 的具体实现如下：

    ```java
    protected final boolean tryReleaseShared(int unused) {
      Thread current = Thread.currentThread();
      if (firstReader == current) {
        if (firstReaderHoldCount == 1)
          /*
          	如果 firstReaderHoldCount == 1，那么将 firstReader 置为 null
          	这是为了给后续的线程使用
          */
          firstReader = null;
        else
          firstReaderHoldCount--;
      } else {
        // 判断 cachedHoldCounter 是否缓存的是当前的线程，如果不是的话，那么久需要从 ThreadLocal 中获取
        HoldCounter rh = cachedHoldCounter;
        if (rh == null || rh.tid != getThreadId(current))
          rh = readHolds.get();
        int count = rh.count;
        
        if (count <= 1) {
          // 这一步将 ThreadLocal 移除掉，这是为了避免内存泄露，因此当前线程已经不再持有读锁了
          readHolds.remove();
          if (count <= 0)
            // unlock() 次数太多了
            throw unmatchedUnlockException();
        }
        --rh.count;
      }
      
      for (;;) {
        int c = getState();
        // nextc 是 state 高 16 位 -1 后的值
        int nextc = c - SHARED_UNIT;
        if (compareAndSetState(c, nextc))
          /*
          	如果 nextc == 0，那就是 state 全部 32 位都为 0，即读锁和写锁都已经全部被释放了
          	此时在这里返回 true 的话，其实是帮助唤醒后继节点中获取锁的线程
          */
          return nextc == 0;
      }
    }
    ```

<br />

### 写锁

- 写锁的获取

	写锁是独占锁，因此如果已经有线程获取到了读锁，那么写锁需要进入到阻塞队列中等待

	写锁加锁的源代码：

	```java
	public void lock() {
	  sync.acquire(1); // 标准的 AQS 写法
	}
	```

	重点在于 `Sync` 类对于 `tryAcquire` 的实现，具体的源代码如下：

	```java
	protected final boolean tryAcquire(int acquires) {
	  
	  Thread current = Thread.currentThread();
	  int c = getState();
	  int w = exclusiveCount(c);
	  if (c != 0) {
	    /*
	    	c != 0 && w == 0: 写锁可用，但是有线程持有读锁（也可能是自己持有）
	    	c != 0 && w != 0 && current != getExclusiveOwnerThread(): 其它线程持有写锁
	    	也就是说，只要有读锁或者写锁被占用，这次就不能获取到写锁
	    */
	    if (w == 0 || current != getExclusiveOwnerThread())
	      return false;
	    
	    if (w + exclusiveCount(acquires) > MAX_COUNT)
	      throw new Error("Maximum lock count exceeded");
	    
	    // 这里不需要 CAS，因为能够走到这的只可能是写锁重入
	    setState(c + acquires);
	    return true;
	  }
	  
	  // 如果写锁获取不需要阻塞，那么则执行 CAS，成功则代表获取到了写锁
	  if (writerShouldBlock() ||
	      !compareAndSetState(c, c + acquires))
	    return false;
	  setExclusiveOwnerThread(current);
	  return true;
	}
	```

	

- 写锁的释放

	写锁释放的源代码如下：

	```java
	public void unlock() {
	  sync.release(1); // 标准的 AQS 的使用
	}
	```

	与之密切相关的就是 `Sync` 对于 `tryRelease` 方法的具体实现，具体的实现代码如下所示：

	```java
	// 简单来讲就是将 state 中关于写锁的持有的数量 -1
	protected final boolean tryRelease(int releases) {
	  if (!isHeldExclusively())
	    throw new IllegalMonitorStateException();
	  
	  int nextc = getState() - releases;
	  boolean free = exclusiveCount(nextc) == 0;
	  
	  if (free)
	    setExclusiveOwnerThread(null);
	  setState(nextc);
	  
	  return free;
	}
	```

	

<br/>

### 锁降级

持有写锁的线程，去获取读锁的这个过程被称为锁降级，这样的话，一个线程可能既持有写锁，也持有读锁。但是，是不存在锁升级这个情况的，因为如果一个持有读锁的线程，再去尝试获取写锁，这种情况下就有可能会发生死锁



<br />

参考：

<sup>[1]</sup> https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/ReentrantReadWriteLock.html

<sup>[2]</sup> https://javadoop.com/post/reentrant-read-write-lock

