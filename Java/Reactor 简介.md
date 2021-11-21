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



## Flux

`Flux`  是 Reactive Stream 中的 `Publisher`，增加了许多可用于生成、转换、编排 Flux 序列的运算符。`Flux` 用于发送 0 个或者多个元素（`onNext()` 事件触发），然后成功结束或者直到出现 error（`onCompelete()` 和 `onError()` 都是终止事件），如果没有终止事件的产生，那么 `Flux` 将会产生无穷无尽的元素



### 基本使用

- 静态方法

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

- 实例方法

  实例方法主要是对得到的元素流的元素进行操作，这些操作应当都是非阻塞并且是异步的