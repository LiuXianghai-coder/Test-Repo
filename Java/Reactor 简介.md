# Reactor 简介

官方的介绍如下：

> Reactor is a fully non-blocking reactive programming foundation for the JVM, with efficient demand management (in the form of managing “backpressure”). It integrates directly with the Java 8 functional APIs, notably `CompletableFuture`, `Stream`, and `Duration`. It offers composable asynchronous sequence APIs — `Flux` (for [N] elements) and `Mono` (for [0|1] elements) — and extensively implements the [Reactive Streams](https://www.reactive-streams.org/) specification.

大致翻译如下：

> Reactor 是一个完全非阻塞的 JVM 反应式编程基础，具有高效的需求管理（以管理“背压”的形式）。它直接整合了 Java 8 中的函数式编程的 API，尤其是 `CompletableFuture`、`Stream` 和 `Duration`。它提供了可组合的异步序列 API— `Flux`（用于 N 个元素）和 `Mono`（用于0个或一个元素），并实现了 `Reactive Stream` 规范



## 使用 `Reactor` 的原因

`Reactor Programming` （响应式编程）是一种新的编程范式，可以通过使用类似于函数式编程的方式来构建异步处理管道。它是一个基于事件的模型，其中数据在可用时推送给消费者。

`Reactor Programming`  具有能够有效地利用资源并且增加应用程序为大量客户端提供服务的能力，相比较传统的创建一个单独的线程来处理请求，响应式编程能够降低编写并发代码的难度。

通过围绕完全异步和非阻塞的核心支柱构建，响应式编程是 JDK 中那些功能有限的异步处理 API （`CallBack` 和 `Future` 等）的更好的替代方案



## 关键概念

- Publisher

  数据的发布者，即数据产生的地方

- Subscriber

  数据的订阅者，即数据接受方

  和 `Publisher` 的关系如下：

  <img src="https://i.loli.net/2021/11/21/D34YMIqvaJUHQud.png" alt="image.png" style="zoom:65%;" />

- Processor

  代表一个处理阶段，既是 `Publisher`，也是 `Subscriber`

- Subscription

  代表 `Publisher` 和 `Subscribe`r  的一对一生命周期，只能由一个 `Subscriber` 使用一次



对应的关系图如下：

<img src="https://i.loli.net/2021/07/11/17JdYa2mvnopFcA.png" alt="image.png" style="zoom:75%;" />

首先定义实体类：

```java
public class User {

    public static final User SKYLER = new User("swhite", "Skyler", "White");
    public static final User JESSE = new User("jpinkman", "Jesse", "Pinkman");
    public static final User WALTER = new User("wwhite", "Walter", "White");
    public static final User SAUL = new User("sgoodman", "Saul", "Goodman");

    private final String username;
    private final String firstname;
    private final String lastname;

    public User(String username, String firstname, String lastname) {
        this.username = username;
        this.firstname = firstname;
        this.lastname = lastname;
    }
    
    // 省略一部分 getter 和 Object 相关的代码
}
```

定义一个转换函数

```java
// 这个函数的任务是将 User 的每个字段都转换成为对应的大写形式
Function<User, User> capital = user ->
    new User(
    	user.getUsername().toUpperCase(),
    	user.getFirstname().toUpperCase(),
    	user.getLastname().toUpperCase()
	);
```



## Flux

`Flux`  是 Reactive Stream 中的 `Publisher`，增加了许多可用于生成、转换、编排 Flux 序列的运算符。`Flux` 用于发送 0 个或者多个元素（`onNext()` 事件触发），然后成功结束或者直到出现 error（`onCompelete()` 和 `onError()` 都是终止事件），如果没有终止事件的产生，那么 `Flux` 将会产生无穷无尽的元素

<img src="https://s6.jpg.cm/2021/11/22/IOLvhH.jpg" style="zoom:80%" />

基本使用

- 静态方法

  - 静态工厂方法

    静态方法主要是用于创建元素流，或者通过几种回调类型来生成这些流的元素

    创建 `Flux` 主要有以下几种方法：

    ```java
    // 创建一个空的 Flux
    static <T> Flux<T> empty();
    
    // 解析传入的参数值，将它们组合成一个 Flux
    static <T> Flux<T> just(T... data);
    
    // 从一个 Iterable 对象中创建对应的 Flux
    static <T> Flux<T> fromIterable(Iterable<? extends T> it);
    
    // 创建一个 Exception 元素，这是在 Reactor Stream 中处理异常的方式
    static <T> Flux<T> error(Throwable error);
    
    // 每个一定时间间隔产生一个 Long 类型的元素到 Flux 的流中
    static Flux<Long> interval(Duration period);
    ```

  - `merge`

    方法签名如下：

    ```java
    // 将多个 Publisher 合并为一个 Flux，这样合并的 Flux 的元素组成将是交错（无顺序的）
    static <I> Flux<I> merge(int prefetch, boolean delayError, Publisher<? extends I>... sources)
    ```

  - `concat`

    ```java
    // 将多个 Publisher 合并为一个 Flux，这样得到的 Flux 是有序的
    static <T> Flux<T> concat(Publisher<? extends T>... sources)
    ```

- 实例方法

  - `map`
  
    ```java
    // 函数原型，该方法的主要任务是 Flux 中的每个元素进行通过转换函数进行转换。。。
    public final <V> Flux<V> map(Function<? super T, ? extends V> mapper);
    ```
  
    具体使用：
  
    ```java
    // 将 Flux 中的每个 User 对象执行对应的转换，得到转换之后的一个新的 Flux
    Flux<User> capitalizeMany(Flux<User> flux) {
        return flux.map(capital);
    }
    ```
  
  - `flatMap`
  
    有的转换函数可能是阻塞的，由于 `Reactor` 要求每个操作应当都是非阻塞的，为了解决这个问题，可以将相关的 `map` 操作放入一个 `Mono` 中进行异步处理以达到非阻塞的效果
  
    `Flux` 的 `flatMap` 的函数原型如下：
  
    ```java
    /* 
    	将当前 Flux 中的元素以异步的方式发送到 Publisher 中，然后通过合并的方式将这些 Publisher
    	扁平化为单个的 Flux，这个操作允许 Flux 交错（不按顺序）地进行
    */
    public final <R> Flux<R> flatMap(Function<? super T, ? extends Publisher<? extends R>> mapper);
    ```
  
    具体的过程如下所示：
  
    <img src="https://s6.jpg.cm/2021/11/22/IOYiP2.png" />
  
    
  
    具体示例如下：
  
    ```java
    Flux<User> asyncCapitalizeMany(Flux<User> flux) {
        /* 
        	将 Flux 的每个元素以异步的方式放入到 Mono 中，最后再将这些 Mono 的元素整合到
        	一个 Flux 中
        */
        return flux.flatMap(this::asyncCapitalizeUser);
    }
    
    Mono<User> asyncCapitalizeUser(User u) {
        return Mono.just(
            	new User(
            		u.getUsername().toUpperCase(), 
            		u.getFirstname().toUpperCase(), 
            		u.getLastname().toUpperCase()
        		)
              );
    }
    ```
  



## Mono

`Mono` 和 `FLux` 类似，主要的区别在于 `Mono` 的元素个数只能是 0 个或一个，或者元素是一个异常

<img src="https://s6.jpg.cm/2021/11/22/IOLonL.jpg" style="zoom:80%">

基本使用

- 静态方法

  - 静态工厂方法

    静态工厂方法主要用于创建 `Mono`

    ```java
    // 创建一个空的 Mono
    static <T> Mono<T> empty();
    
    /* 
    	创建一个不会产生任何数据的 Mono，与空的 Mono 不同，
    	这样得到的 Mono 不会产生任何 onComplete 事件
    */
    static <T> Mono<T> never();
    
    // 从单个的元素产生一个 Mono
    static <T> Mono<T> just(T data);
    
    // 产生一个带有异常的 Mono
    static <T> Mono<T> error(Throwable error);
    ```

- 实例方法

  - `map`

    方法原型如下：

    ```java
    // 将元素执行对应的转换。。。
    public final <R> Mono<R> map(Function<? super T, ? extends R> mapper);
    ```

  - `flatMap`

    方法原型如下：

    ```java
    // 将当前的元素放入到一个 Publisher 中，以实现非阻塞的目标
    final <R> Mono<R> flatMap(Function<? super T, ? extends Mono<? extends R>>
    ```

    <img src="https://s6.jpg.cm/2021/11/22/IOs5Kh.png" style="zoom:80%" />

  


## StepVerifier

`Reactor Programming` 和一般的编程不一样，由于它是完全非阻塞的，因此代码的调试将会变得很困难，使用 `StepVerifier` 类能够有效地检测 `Reactor Stream` 是否时按照预期的定义进行的

首先，需要添加 `reactor-test` 相关的依赖

```xml
<!-- 在 Spring Boot 中由 SpringBoot Parent 管理，因此无须添加版本号 -->
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-test</artifactId>
</dependency>
```

基本使用

- 检测预期的元素

  ```java
  // 创建一个 Flux 元素流
  Flux<Long> counter = Flux.interval(Duration.ofMillis(10)).take(5);
  // 检测每一步得到的元素，如果不是预期的元素，则会得到一个断言失败的异常。。。
  StepVerifier.create(counter)
      .expectNext(0L)
      .expectNext(1L)
      .expectNext(2L)
      .expectNext(3L)
      .expectNext(4L)
      .verifyComplete();
  ```

- 断言出现的元素

  ```java
  static class {
      private final String username;
      private final String firstname;
      private final String lastname;
      
      public User(String username, String firstname, String lastname) {
          this.username = username;
          this.firstname = firstname;
          this.lastname = lastname;
      }
      
      // 省略一部分代码
  }
  
  void expectSkylerJesseComplete(Flux<User> flux) {
      StepVerifier.create(flux)
          // 下一个到来的元素应当是 username 为 "swhite" 的 User 对象
          .assertNext(user -> user.getUsername().equals("swhite"))
          // ...............
          .assertNext(user -> user.getUsername().equals("jpinkman"))
          .verifyComplete(); // 此时元素流应当已经结束
  }
  ```

- 检测出现的异常

  ```java
  StepVerifier.create(flux) // 从对应的 Flux 创建元素流
      .expectError(RuntimeException.class) // 下一个到来的元素应当是一个 RuntimeException
      .verify();
  ```

- 虚拟时间检测

  使用 `Flux` 的 `internal()` 方法将会按照一定的时间间隔产生一个元素，如果产生的元素很多，同时每个元素产生的间隔又很大，这个时候就会导致测试时间会变得很长，为了解决这个问题，`StepVerifier` 提供了 `withVirtualTime` 的方法来解决这个问题

  ```java
  Supplier<Flux<Long>> supplier = () -> Flux.interval(Duration.ofSeconds(1)).take(3600L);
  
  StepVerifier.withVirtualTime(supplier)
      .thenAwait(Duration.ofHours(1)) // 使得当前的 Flux 元素流觉得 3600s 已经过去了
      .expectNextCount(3600) // 此时应当已经接受了 3600 个元素
      .verifyComplete(); // 测试结束
  ```



## 背压（backpressure）

前文介绍了有关 `Publisher` 和 `Subscriber` 之间的关系，在关系图中存在一个称为 “backpressure” 的轮子，这个组件的作用用于限制 `Publisher` 向 `Subscriber` 发送数据的速度，使得 `Subscriber` 能够正常地处理数据，不至于由于收到的数据过多无法处理而导致 `Subscriber` 宕机。

这是一种反馈机制，由 `Subscriber` 向 `Publisher` 发送一个 “反馈信息”，表示自己准备处理多少数据，而 `Publisher` 通过这一 “反馈信息” 限制自己的发送数据的速度（具体可以将多余的数据丢弃或放入缓冲区），从而达到一个动态的平衡。

```java
Flux.just(1, 2, 3, 4, 5)
    .log() // 打印相关的记录信息。。。。
    .subscribe(new Subscriber<Integer>() {
        private Subscription subscription;
        private int amt = 0;

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            this.subscription.request(2); // 首先请求两个元素
        }
        
        /* 
        	之后，每获取到两个元素就发送一个 “获取下两个元素” 的反馈信息给 Publisher，
        	以此达到背压的效果
        */
        @Override
        public void onNext(Integer integer) {
            System.out.println("onNext: " + integer);
            amt++;
            if (amt % 2 == 0) this.subscription.request(2);
        }

        @Override
        public void onError(Throwable throwable) {
            // nothing should to do....
        }

        @Override
        public void onComplete() {
            // nothing should to do....
        }
    });
```



## 并发

一般情况下，创建的元素流是在当前的线程下启动的，由于 `Reactor` 是完全非阻塞的，因此如果某个流的操作阻塞的，那么在这种情况下可能就无法看到执行的信息（但实际上确实做了这个任务）。

如果想要将当前流的执行环境放入到另一个线程中，可以考虑使用 `Reactor` 提供的 `Schedulers` 来实现

具体示例如下：

```java
Flux.just(1, 2, 3, 4)
    .log()
    .map(i -> i * 2)
    .subscribeOn(Schedulers.parallel())
    .doOnNext(s -> System.out.println("Current Thread: " + Thread.currentThread().getName())) // 每次获取到元素时打印当前的线程
    .subscribe();

/* 
	由于 JVM 会在不存在非守护线程时退出，而 Reactor 又是完全非阻塞的，因此 Reactor 运行时的线程会被视为一个已完成任务的线程（实际上还没有），在 Main 方法中，直接这么编写将会导致 JVM 提前结束；为了解决这个问题，主要有两种思路：使得当前线程睡眠一会儿使得所有任务有机会执行；开启一个新的非守护线程去执行这个任务（在当前的 Schedule 调度的情况下，会创建一个新的单线程的线程池去执行，因此这么做也是非阻塞的）。
*/
Thread.sleep(1000);
```

得到的输出如下：

<img src="https://s6.jpg.cm/2021/11/24/LGqvnk.png" />

图中的 "parallel-1" 就是 `Flux` 所在的流执行的线程

如果使用创建新的线程的方式来执行，具体示例如下：

```java
// 创建一个
Flux<Integer> flux = Flux.just(1, 2, 3, 4)
    .log()
    .map(i -> i * 2)
    /* 
    	注意，不要使用 Schedulers 来执行调度，这样会使得当前的“父线程”（当前 Flux 的执行线程）
    	为非阻塞的而直接结束
    */
    // .subscribeOn(Schedulers.parallel()) 
    .doOnNext(s -> System.out.println("Current Thread: " + Thread.currentThread().getName()));

/* 
	创建一个名为 "subscribe-Thread" 的线程去执行这个任务，由于 Flux 的元素是在这个新的线程中执行的，
	因此在这个过程中这个线程始终都是存活的，这样就可以避免 JVM 提前退出的问题了
*/
new Thread(flux::subscribe, "subscribe-Thread").start();
```

执行结果如下：

<img src="https://s6.jpg.cm/2021/11/24/LG7LNu.png" />