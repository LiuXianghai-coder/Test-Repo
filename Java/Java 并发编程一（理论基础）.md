# Java 并发编程一（理论基础）

与计算机基础相关的线程的知识在此略过



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