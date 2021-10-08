# Java 内存模型

## 简介

> Java 内存模型是通过各种操作来定义的，包括对变量的读/写操作，监视器的加锁、解锁操作，以及线程的启动和合并操作。
>
> `JMM` 为程序中所有的操作定义了一个偏序关系，称之为 `Happens-Before`。如果想要保证执行 B 操作的线程看到操作 A 的结果（无论 A 和 B 是否在同一个线程中执行），那么 A 和 B 之间的操作必须满足 `Happens-Before` 的关系。如果两个操作之间缺少 `Happens-Before` 关系，那么 `JVM` 就可以对它们进行任意的重排序。

### Happens-Before 规则

- 程序顺序规则：如果程序中操作 A 在操作 B 之前，那么在线程中操作 A 将在 操作 B 之前执行
- 监视器锁规则：在监视器锁上的解锁操作必须在同一个监视器锁上的加锁操作之前执行（显示锁和内置锁在加锁和解锁等操作上有相同的内存语义）
- volatile 变量规则：对 volatile 变量的写入操作必须在对该变量的读操作之前执行（原子变量与 volatile 变量在读操作和写操作上有着相同的语义）
- 线程启动规则：在线程上对 `Thread.start() ` 的调用必须在该线程中执行任何操作之前执行
- 线程结束规则：线程中的任何操作都必须在其他线程检测到该线程已经结束之前执行，或者从 `Thread.join()` 中成功返回，或者在调用 `Thread.isAlive()` 中返回 `false`
- 中断规则：当一个线程在另一个线程上调用 `interrupt` 时，必须在被中断线程检测到 `interrupt` 之前执行（或者抛出 `InterruptException`，或者调用 `isInterrupted` 和 `interrupted`）
- 终结器规则：对象的构造函数必须在启动该对象的终结器之前执行
- 传递性：如果操作 A 在操作 B 之前执行，并且 B操作在 C操作之前执行，那么操作 A 必须在 操作 C 之前执行



### 锁的内存语义

- 线程 A 释放了一个锁，实质上是线程 A 向接下来将要获取这个锁的某个线程发出了（线程 A 对共享变量所做修改）的消息
- 线程 B 获取了一个锁，实质上是线程 B 接收了之前某个线程发出的（在释放这个锁之前对共享变量所做修改）的消息
- 线程 A 释放锁，随后线程 B 获得了这个锁，这个过程实质上是线程 A 通过主存向线程 B 发送了消息



### `volatile` 内存语义

- 线程 A 写一个 `volatile` 变量，实质上是线程 A 向接下来将要读这个 `volatile` 变量的某个线程发送了对共享变量所做修改的消息
- 线程 B 读取一个 `volatile` 变量，实质上是线程 B 接收了之前某个线程发出的在读这个 `volatile` 变量之前所做修改的消息
- 线程 A 写一个 `volatile` 变量，随后线程 B 读取了这个变量，这个过程实质上是线程 A 通过主存向线程 B 发送了修改这个共享变量的消息



#### `volatile` 内存语义的实现

- 内存屏障

  为了实现 `volatile` 的内存语义，编译器会在生成字节码时，在指令序列中插入内存屏障来禁止特定类型的处理器重排序

  > 内存屏障是一种 barrier 指令类型，它导致 CPU 或编译器对 barrier 指令前后发出的内存操作执行顺序约束。也就是说，在 barrier 之前的内存操作保证在 barrier 之后的操作之前执行

  内存屏障主要分为以下四种：

  - `LoadLoad`内存屏障：对于这样的语句 `load1;LoadLoad;load2`，在 `load2` 及后续读取操作要读取的数据被访问之前，保证 `load1` 要读取的数据被读取完毕
  - `StoreStore`内存屏障：对于这样的语句 `store1;StoreStore;store2`，在 `store2` 及后续的写入操作执行之前，保证 `store1` 中的写入操作对处理器可见
  - `LoadStore`内存屏障：对于这样的语句 `load1;LoadStore;store1`，在 `store1` 及后续写入操作被刷出之前，保证 `load1` 的读取操作要全部完成
  - `StoreLoad`内存屏障：对于这样的语句 `store1;StoreLoad;load1`，在`load1`   及后续的所有读取操作执行之前，保证 `store1` 中的数据写入对于所有处理器可见。这个内存屏障是所有内存屏障中开销最大的，这个屏障是一个万能屏障，兼具其他三种内存屏障的功能

- `Java` 中 `volatile` 的实现

  <img src="https://upload-images.jianshu.io/upload_images/12917134-3676bc3a13760f2c.png?imageMogr2/auto-orient/strip|imageView2/2/w/1200/format/webp">

  - 对每个`volatile` 写操作之前插入一个 `StoreStore` 内存屏障
  - 对每个 `volatile` 写操作之后插入一个`StoreLoad` 内存屏障
  - 对每个 `volatile`读操作之前插入一个 `LoadLoad` 内存屏障
  - 对每个 `volatile` 读操作之后插入一个 `LoadStore` 内存屏障

  

### `final` 关键字的内存语义

- 在构造函数内对一个 `final` 域的写入，与随后把这个被构造对象的引用赋值给一个引用变量，这两个操作之间不能重排序
- 初次读一个包含 `final` 域的对象的引用，与随后初次读这个 `final` 域，这两个操作之间不能重排序



#### 写 `final` 域的重排序规则

- 写 `final` 域的重排序规则禁止把 `final` 域的写重排序到构造函数之外，这个规则的实现包含下面两个方面：
  - `JMM` 禁止编译器把 `final` 域的写重排序到构造函数之外
  - 编译器会在 `final` 域的写之后，构造函数的 `return` 之前，插入一个 `StoreStore` 内存屏障。这个屏障禁止处理器把 `final` 域的写重排序到构造函数之外。写 `final` 域的重排序规则可以确保：在对象引用为任意线程可见之前，对象的 `final` 域已经被正确初始化过了，而普通域则不具备这个保障



#### 读 `final` 域的重排序规则

- 在一个线程中，初次读对象引用和初次读该对象包含的 `final` 域，`JMM` 禁止处理器重排序这两个操作（注意，仅仅只是针对处理器）
  - 编译器会在读 `final` 域操作前插入一个 `LoadLoad` 内存屏障
  - 初次读对象引用与初次读该对象包含的 `final` 域，这两个操作之间存在间接依赖关系。由于编译器遵守间接依赖关系，因此编译器也不会重排序这两个操作
  - 大多数处理器也会遵守间接依赖也不会重排序这两个操作，但是少数处理器允许存在间接依赖关系的操作做重排序，这个规则就是针对这些处理器的。
  - 读 `final` 域的重排序规则可以确保：在读一个 `final` 域之前，一定会先读包含这个 `final` 域的引用



#### `final` 域为引用类型

- 对于引用类型，写 `final` 域的重排序规则对编译器和处理器增加了如下约束：在构造函数内对一个 `final` 引用的对象的成员域的写入，与随后在构造函数外把这个被构造对象的引用赋值给一个引用变量，这两个操作之间不能重排序。这一规则确保了其它线程能够读到被正确初始化的 `final` 引用对象的成员域 



## 实际运用

## 单例模式的实现

### 饿汉式单例

- 静态工厂方法实现单例模式

  ```java
  public class Cat {
      // 注意这里使用了 final 关键字修饰 INSTANCE，由于 final 域的内存语义，Cat 的构造函数初始化会在将对象引用给 INSTANCE 之前全部完成，从而使得得到的 INSTANCE 实例是有效的
      private static final Cat INSTANCE = new Cat();
  
      private Cat() {
          // 防止客户端使用反射的方式来再次初始化实例
          if (null != INSTANCE)
              try {
                  throw new IllegalAccessException("只能初始化一次");
              } catch (IllegalAccessException e) {
                  e.printStackTrace();
              }
      }
  
      // 使用静态工厂方法的方式获取实例，具体可以看看 《Effective Java》给出的第一条建议
      public static Cat getInstance() {return INSTANCE;}
  }
  ```

- 枚举类型实现单例模式

  ```java
  public enum Dog {
      // 现在，INSTANCE 就是一个 Dog 的单实例了，由于枚举会在类初始化的时候完成相应的构造，因此它也是线程安全的，同时也是使用饿汉式的方式初始化实例的
      INSTANCE
  }
  ```



### 延迟化单例模式

- 延迟初始化类

  ```java
  public class Mouse {
      private Mouse(){}
      
      /* 参考 JVM 种对于类初始化的几个条件，当访问 static 修饰的字段时，
         如果类没有被初始化，那么首先初始化该类
         
         当调用 Mouse 的 getInstance() 静态工厂方法时，由于访问了 FiledHolder 的静态字段，因此会初始化改类。类的初始化是由 JVM 进行调度的，因此它是线程安全的
         
         注意使用的是内部静态类，它相当于一个与主类处于相同级别的类，因此当 Mouse 类初始化的时候并不会初始化这个静态内部类。
      */
      private static class FiledHolder {
          static final Mouse holder = new Mouse();
      }
  
      public Mouse getInstance() {return FiledHolder.holder;}
  }
  ```

- `DCL`（双重检查锁）

  ```java
  public class Elephant {
      /*
        注意这里使用的 volatile 变量，结合上文的内容，使用 volatile 修饰的字段会在写操作之前添加 StoreStore 等内存屏障以维持 Happens-Before 规则，因此保证了对于类的构造会发生在将这个对象的引用赋值到目标变量之前
      */
      private volatile Elephant instance = null;
  
      public Elephant getInstance() {
          /*
            参见 《Effective Java》 第 83 条，引入局部变量 result 确保 instance 在被初始化的情况下读取一次，这样做可以提高性能
          */
          Elephant result = instance;
          // 第一检查实例对象是否已经被初始化
          if (result == null) {
              // 同步初始化实例化类，避免由于多个线程同时进行初始化而破坏单例
              synchronized (this) {
                  // 再次检查实例是否被初始化过，这是因为当线程进来的时候，可能已经由其它的线程进行初始化了
                  if (instance == null)
                      instance = result = new Elephant();
              }
          }
  
          return result;
      }
  }
  ```

  ​	实际上，一般来讲，正常地使用饿汉式地方式来实现单例是最好的解决方案。但是如果确实需要使用延迟化的加载方式，如果需要使用到静态变量，那么使用延迟化初始化类的方式实现是最好的；如果不得不使用一个对象的字段来表示单例，那么就使用 `DCL` 的方式。