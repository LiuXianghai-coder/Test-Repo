## Netty 线程模型

### Reactor 线程模型

由于传统 的阻塞 IO 对于响应时间不是很好，因此引入了 `Reactor` 的异步事件模型来提高响应时间。

主要存在以下三种方式：

- 单线程

  ![2021-10-13 21-36-52 的屏幕截图.png](https://i.loli.net/2021/10/13/2xdgu7l3zFQyUS9.png)

  ​	`Reactor` 内部通过 `selector` 来监听连接事件，收到事件之后通过 `dispatcher` 来进行分发。如果是连接建立的事件，则由 `acceptor` 进行处理，`acceptor` 通过接受到连接，创建一个 Handler 来处理后续的事件。

  ​	该模型的缺点：由于是使用单线程的方式来处理每个连接事件，因此无法有效地利用 CPU 提供的资源

- 多线程

  ![2021-10-13 21-37-03 的屏幕截图.png](https://i.loli.net/2021/10/13/ajdsfBhxCvU9p3O.png)

  ​	为了充分利用 CPU 提供的资源，因此使用多线程的方式来对工作集合进行处理。

  ​	在主线程中，`Reactor` 对象通过 `selector` 来监控连接事件，收到事件之后通过 `dispatch`  进行分发，如果是连接事件，则由 `acceptor` 进行处理，`acceptor` 通过接收到连接，创建一个 `Handler` 来处理后续的事件。在这个线程模型中，该 `Handler` 只是负责响应事件，不进行任何业务操作，所有的业务操作都放在线程池中进行处理。

  ​	该模型存在的缺点：在同时接受大量的连接请求时，由于所有的连接请求都是通过单个的 `Reactor` 对象来进行处理的，因此这是很可能会造成连接超时的情况。

- 主从多线程

![2021-10-13 21-37-33 的屏幕截图.png](https://i.loli.net/2021/10/13/rBjq9M2fE7a3c4e.png)

​	由于使用单独的 `Reactor` 来处理连接在处理大量连接是可能会导致连接连接超时的情况，因此在这个线程模型中采用了 “主—从” `Reactor` 的模式来解决多线程模型中出现的问题。现在在这个模型中，存在一个 “主” `Reactor` 对象，它负责将接收到的请求分发到某个 “从” `Reactor` 对象，“从” `Reactor` 对象将会将得到的请求按照 “**多线程模型**” 的方式对任务进行处理。“从” `Reactor` 对象的数量由具体的硬件环境来决定。



### Netty 线程模型

Netty 通过 `NioEventLoopGroup` 来实现上文提及的几种 `Reactor` 模型

- 单线程模型

  单线程模型就是只指定一个线程执行客户端的连接和读取操作，即将所有的请求和任务的处理都放在一个线程中执行，只要将 `NioEventLoopGroup` 中的线程数设置为 1 即可实现单线程模型。

  ```java
  NioEventLoopGroup group = new NioEventLoopGroup(1); // 设置 NioEventLoopGroup 线程数为1，将当前的 Reactor 线程模型设置为单线程模型
  ServerBootstrap bootstrap = new ServerBootstrap();
  bootstrap.group(group)
      .channel(NioServerSocketChannel.class)
      .option(ChannelOption.TCP_NODELAY, true) // 开启 Nagle 算法
      .option(ChannelOption.SO_BACKLOG, 1024) // 最大连接等待队列的长度
      .childHandler(new SimpleChannelInboundHandler<SocketChannel>() {
          @Override
          protected void
              channelRead0(
              ChannelHandlerContext ctx,
              SocketChannel msg
          ) throws Exception {
              /*
              	TODO
              */
          }
      });
  ```

  大致的工作流程：

  <img src="https://p1-jj.byteimg.com/tos-cn-i-t2oaga2asx/gold-user-assets/2019/10/20/16de99192b867bc4~tplv-t2oaga2asx-watermark.awebp" />

- 多线程模型

  多线程模型就是在一个 `Reactor` 对象中对客户端的连接进行处理，然后将业务交给线程池进行处理。代码如下所示：

  ```java
  NioEventLoopGroup group = new NioEventLoopGroup();
  ServerBootstrap bootstrap = new ServerBootstrap();
  bootstrap.group(group)
      .channel(NioServerSocketChannel.class)
      .option(ChannelOption.TCP_NODELAY, true)
      .option(ChannelOption.SO_BACKLOG, 1024)
      .childHandler(new SimpleChannelInboundHandler<SocketChannel>() {
          @Override
          protected void channelRead0( ChannelHandlerContext ctx, SocketChannel msg) throws Exception {
              /*
              	TODO
              */
          }
      });
  ```

  值得注意的是，这种模型的实现原理就是将“主” `Reactor` 对象和 “从” `Reactor` 对象设置为同一个 `NioEventLoopGroup` 对象来实现的

  具体工作流程：

  <img src="https://p1-jj.byteimg.com/tos-cn-i-t2oaga2asx/gold-user-assets/2019/10/20/16de99190b88bf42~tplv-t2oaga2asx-watermark.awebp" />

- 主从多线程模型

  只要将 “主” `Reactor` 对象和 “从” `Reactor` 对象设置为不同的 `NioEventLoopGroup` 即可达到对应的效果。

  具体代码如下所示：

  ```java
  NioEventLoopGroup mainGroup = new NioEventLoopGroup();
  NioEventLoopGroup minorGroup = new NioEventLoopGroup();
  ServerBootstrap bootstrap = new ServerBootstrap();
  bootstrap.group(mainGroup, minorGroup)
      .channel(NioServerSocketChannel.class)
      .option(ChannelOption.TCP_NODELAY, true)
      .option(ChannelOption.SO_BACKLOG, 1024)
      .childHandler(new SimpleChannelInboundHandler<SocketChannel>() {
          @Override
          protected void
              channelRead0(
              ChannelHandlerContext ctx,
              SocketChannel msg
          ) throws Exception {
              /*
              	TODO
               */
          }
      });
  ```

  工作流程如下所示：

  <img src="https://p1-jj.byteimg.com/tos-cn-i-t2oaga2asx/gold-user-assets/2019/10/20/16de99379db7182e~tplv-t2oaga2asx-watermark.awebp" />

  在 Netty 中，“主” `Reactor` 实际上依旧只是随机选择一个线程用于处理客户端的连接。与此同时， `NioServerSocketChannel` 绑定到 `mainGroup`，而 `NioSockerChannel` 绑定到 `minorChannel` 中



参考：

<sup>[1]</sup> https://juejin.cn/post/6844903974298976270

<sup>[2]</sup> http://gee.cs.oswego.edu/dl/cpjslides/nio.pdf