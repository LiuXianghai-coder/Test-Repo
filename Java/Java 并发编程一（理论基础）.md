# Java 并发编程一（理论基础）

**与计算机基础相关的线程的知识在此略过**



## 线程安全性

相关的定义如下：

> 当多个线程访问某个类时，不管运行时环境采用何种调度方式或者这些线程将如何交替执行，并且在代码中不需要任何额外的同步或者协同，这个类都能够表现出正确的行为，那么称这个类是线程安全的

常见的线程安全对象：

- 无状态对象

  无状态对象一定是线程安全的，这是因为不存在对于当前对象的任何属性字段的访问；而对于方法的调用将使用一个自有的方法栈，这将使得方法的调用都是线程安全的

  ```java
  import java.util.Random;
  
  // 一个简单的无状态对象，这个对象只有一个实例方法，通过传入的 seed 生成一个随机数
  public class StateSafeClass {
      public long random(long seed) {
          Random random = new Random(seed);
          return random.nextLong();
      }
  }
  ```

- 原子操作对象

  如果对于一个对象的所有属性的访问都是原子操作，那么这个对象在多线程同时访问时将会是 “线程安全” 的

  常见的原子操作对象有：`AtomicInteger`、`AtomicLong`、`AtomicBoolean` 等

  > 当使用多个原子操作对象通过组合的方式来构建 “线程安全” 对象时，只有在单个的原子操作中同时更新所有的状态才能确保所构成的类是 “线程安全” 的



### 加锁机制

可以通过加锁的方式来实现代码块的线程安全性，这是因为在 Java 内存模型中定义了有关于 “锁” 的偏序规则：

> 监视器锁规则：在监视器锁上的解锁操作必须在同一个监视器锁上的加锁操作之前执行（显式锁和内置锁在加锁和解锁等操作上有相同的内存语义）

这是为什么能够通过加锁的方式来实现代码的线程安全性的根本原因



#### 内置锁

内置锁即使用 Java 关键字 `synchronized` 关键字来包围对应的代码块，使得其中的代码块是线程安全的

使用 `synchronized` 关键字的示例如下：

```java
public class SynchronizedExample {
    // 用于获取监视器锁的对象，具体可以查看有关 Java 对象的结构中 MarkWord 的部分
    final Object object = new Object();
    
    // 当前对象的状态
    int cnt = 0;
    
    // 通过 synchronized 关键字来维护当前对象的状态更新的线程安全性
    public void plus() {
        // 这里的 object 相当于锁的钥匙，只有一个线程在同一时刻能获取这个钥匙
        synchronized (object) {
            cnt++;
        }
    }
}
```

Java 中的内置锁是可 “重入” 的，可重入的意思是说，如果一个线程获取了一个内置锁，那么在之后的任意时刻（如果这个线程依旧持有这个锁），这个线程就可以再次获取这个锁而进入对应的代码块。

内置锁设计为可重入是选择的必然，查看以下这个例子：

```java
public class Parent {
    // synchronized 在修饰方法时，获取的 “钥匙” 为 this，即当前对象
    public synchronized void parentDo() {
        // do something.......
    }
}

/* 
	由于 Child 继承了 Parent，如果内置锁不是可重入的话，那么在在 childDo() 
	方法中调用父类的 parentDo() 方法将会导致 “死锁” 
*/
public class Child extends Parent {
    public synchronized void childDo() {
        // child do something.....
        super.parentDo();
    }
}
```

由于不同的 `JVM` 的实现各有不同，因此对于内置锁的可重入的实现也各有不同。一般常见的一种实现方法为：为每个锁关联一个获取计数值和一个所有者线程，当计数值为 0 时，那么认为这个锁没有被任何线程持有；每当这个获取了这个锁的线程再次获取这个锁时，就将对应的计数值 + 1，当退出这个锁时，将计数值 -1，当计数值为 0 时，将会释放这个锁



#### 显式锁

`ReentrantLock` 是一个基于 `AQS` 的对于 `java.util.concurrent.locks.Lock` 的具体实现，与使用 `synchronized` 关键字的内置锁不同的地方在于显式锁提供了一种无条件的、可轮询的、定时的以及可中断的锁获取操作，由于加锁和释放锁的操作都是显式的，因此被称为 “显式锁”

显式锁具有与内置锁相同的互斥性以及可见性（内存语义）



## 对象的共享

### 可见性

依旧是在 `JMM` 中定义的一些偏序关系：

> 程序顺序规则：如果程序中操作 A 在操作 B 之前，那么在线程中操作 A 将在 操作 B 之前执行
>
> 监视器锁规则：在监视器锁上的解锁操作必须在同一个监视器锁上的加锁操作之前执行
>
> volatile 变量规则：对 volatile 变量的写入操作必须在对该变量的读操作之前执行（原子变量与 volatile 变量在读操作和写操作上有着相同的语义）
>
> 线程启动规则：在线程上对 `Thread.start()` 的调用必须在该线程中执行任何操作之前执行
>
> 线程结束规则：线程中的任何操作都必须在其他线程检测到该线程已经结束之前执行，或者从 `Thread.join()` 中成功返回，或者在调用 `Thread.isAlive()` 中返回 `false`
>
> 中断规则：当一个线程在另一个线程上调用 `interrupt` 时，必须在被中断线程检测到 `interrupt` 之前执行（或者抛出 `InterruptException`，或者调用 `isInterrupted` 和 `interrupted`）
>
> 终结器规则：对象的构造函数必须在启动该对象的终结器之前执行
>
> 传递性：如果操作 A 在操作 B 之前执行，并且 B操作在 C操作之前执行，那么操作 A 必须在 操作 C 之前执行

除了上述描述的规则，在一个 Java 程序中，在 `JVM`、处理器以及运行运行时都可能对操作的执行顺序进行一些意想不到的调整，这也被称为 “指令重排序”，要正确使用 `JMM` 定义的相关的偏序规则，才能使得程序按照正常的逻辑运行



### 最低安全性

当线程在没有进行同步的情况下，可能会读取到一个失效值，但是这个失效的值至少也是之前某个线程设置的值，而不是一个随机值。这种保证也被成为 “最低安全性”

在大多数的情况下，“最低安全性” 总是适用的，但是存在这么一个例外：没有使用 `volatile` 修饰的 64 位数值变量（`double` 和 `long`）

`JMM` 要求，变量的读取操作和写入操作都必须是原子操作，但是对于非 `volatile` 修饰的 `long` 和 `double` 变量，`JVM` 允许将 64 位的写操作和读操作分解为两个 32 位的数值的操作，在这种情况下是不满足 “最低安全性” 的

为了解决这个问题，可以通过使用 `volatile` 关键字修饰对应的变量，或者通过加锁的方式来保护对应的变量的状态，这是因为`volatile` 可以保证对一个变量的写入操作发生在其它的线程操作读取它之前；加锁则使得对于变量的操作发生在另一个线程操作这个变量之前，加锁是更加强大的 `volatile` （`JMM` 的偏序关系）



### 对象的发布与逸出

> “对象的发布” 指的是使得对象能够在当前作用域之外的代码中使用，例如，将一个指向该对象的引用保存到其它代码可以访问的地方，或者在一个非私有的方法中返回该引用，或者将引用传递到其它类的方法中。
>
> “对象的逸出” 是指某个不应该发布的对象被发布

以下面的例子为例：

```java
// 一个不安全的发布示例
public class UnsafePublisher {
    private String[] states = new String[] {
        "Apple", "Orange", "Strawberry", "Watermelon"
    };
    
    /* 
    	通过 getStates() 方法就可以获取到内部的 states 属性，调用这个方法的任何客户端都可以直接对 
    	states 的内容进行修改，这种修改是线程非安全的
    */
    public String[] getStates() {return this.states;}
}
```

以上的示例是一个显式的 “移除” 情况，值得一提的还有隐式地发布 `this`  对象（当前实例对象）

```java
public class ThisEscape {
    public ThisEscape(EventSource source) {
        /*
        	在构造函数中发布 EventListener 时，也会隐式地发布 this，
        	因为在内类 EventListener 中包含了对当前 ThisEscape 对象的引用
        	（非静态内部类的实例对象会带有外部的 this 引用，具体可以查看 《Effective Java》有更加详细的说明）
        */
        
        /* 
        	这里存在的问题是在 ThisEscape 对象在构造过程中就已经逸出了，
        	因此外部的调用有可能会访问到未初始化完成的 this 对象
        */
        source.registerListener(new EventListener() {
            public void onEvent(Event e) {
                doSomething(e);
            }
        });
    }

    void doSomething(Event e) {}
    interface EventSource {void registerListener(EventListener e);}
    interface EventListener {void onEvent(Event e);}
    interface Event {}
}
```

一种常见的错误就是在构造函数中启动一个线程，这样会导致这个 `this` 对象被新创建的线程共享，新启动的线程将会看到一个没有构造完全的实例对象！！！

同样，如果在构造函数中调用了一个可改写的实例方法（既不是私有方法，也不是终结方法）都会导致 `this` 对象的逸出

如果想要在构造函数中启动一个线程或者注册事件监听，那么最好的解决方案为将当前对象的构造函数设计为私有的，通过定义一个工厂方法来获取一个新的实例对象，就能够有效地避免这个问题：

```java
public class SafeListener {
    private final EventListener listener;

    private SafeListener() {
        listener = new EventListener() {
            public void onEvent(Event e) {
                doSomething(e);
            }
        };
    }
    
    // 通过这种方式，就不会将构造了一半的实例对象发布到别的地方了
    public static SafeListener newInstance(EventSource source) {
        SafeListener safe = new SafeListener();
        source.registerListener(safe.listener);
        return safe;
    }
    
    // 省略部分接口的定义。。。。
}
```

