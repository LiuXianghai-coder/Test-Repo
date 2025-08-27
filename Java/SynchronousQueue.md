# `SynchronousQueue` 

在 `Java` 的并发编程中，不可绕过的话题便是通过线程池的方式多线程或异步地处理任务，在线程池中，一个核心的参数是有关阻塞队列的选择，当提交的任务数使得核心线程无法足量处理这些任务时，会将这些任务放入阻塞队列中，如果阻塞队列也不能容纳新加入的任务，则会考虑创建超过核心线程数的线程。在实际开发中，根据具体的处理任务选择合适的阻塞队列也是提高系统吞吐量的一个重要选项

一般来讲，对于通用配置的线程池，选择 `LinkedBlockingQueue` 作为线程池的阻塞队列是合适的（因为总是希望这个任务可以被执行）。但在某些场景下，如任务执行时间较短、任务数比较少、希望尽可能快地被执行，选择 `SynchronousQueue` 会是一个比较好的选择

## 实现原理

和 `ReentrantLock` 锁的实现类似，`SynchronousQueue` 也具备公平和非公平的两种实现模式。具体的算法依据 <a href="https://www.cs.rochester.edu/u/scott/papers/2004_DISC_dual_DS.pdf">2004_DISC_dual_DS</a>，在 `SynchronousQueue` 以 `LIFO`（后进先出）`TransferStack` 作为非公平模式的实现，`FIFO`（先进先出）`TransferQueue` 作为公平模式的实现。这两种模式的性能十分接近，区别在于公平模式的实现会提供更高的吞吐量，而非公平模式则会提供更好的线程局部性

对应的构造函数如下：

``` java
public class SynchronousQueue<E> extends AbstractQueue<E>
    implements BlockingQueue<E>, java.io.Serializable {
    
    private transient volatile Transferer<E> transferer;
    
    public SynchronousQueue() {
        this(false); // 默认是非公平模式
    }
    
    public SynchronousQueue(boolean fair) {
        transferer = fair ? new TransferQueue<E>() : new TransferStack<E>();
    }
}
```

和线程池执行任务主要相关的 `BlockingQueue` 接口为 `offer`、`take` 和 `poll` ，具体的实现如下：

``` java
public class SynchronousQueue<E> extends AbstractQueue<E>
    implements BlockingQueue<E>, java.io.Serializable {
    
    private transient volatile Transferer<E> transferer;
    
    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        return transferer.transfer(e, true, 0) != null;
    }
    
    public E take() throws InterruptedException {
        E e = transferer.transfer(null, false, 0);
        if (e != null)
            return e;
        Thread.interrupted();
        throw new InterruptedException();
    }
    
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E e = transferer.transfer(null, true, unit.toNanos(timeout));
        if (e != null || !Thread.interrupted())
            return e;
        throw new InterruptedException();
    }
}
```

可以看到实际的操作最终都是委托给了 `Transferer` 的 `transfer` 方法，后续我们将继续分析 `TransferQueue` 和 `TransferStack` 的相关实现

## LIFO 实现



## FIFO 实现