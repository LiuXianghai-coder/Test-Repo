# Netty 组件介绍

## BootStrap

Netty 中的 BootStrap 分为两种：一种是客户端的 `BootStrap`；一种是服务端的 `ServerBootStrap`。

- 客户端的 `BootStrap`

  初始化客户端，该 `BootStrap` 只有一个 `EventLoopGroup`，用于连接远程主机

- 服务端的 `ServerBootStrap`

  用于绑定本地端口，一般存在两个 `EventLoopGroup`（一个用于包含单例的 `ServerChannel`，用于接收和分发请求；另一个是包含了所有创建的 Channel，处理服务器接受到的所有来自客户端的连接）。

`BootStrap` 的启动流程（以 `ServerBootStrap` 为例）：

![Channel.png](https://i.loli.net/2021/10/14/hOva4TZ7omdAWe1.png)

具体的代码如下所示：

```java
NioEventLoopGroup group = new NioEventLoopGroup(); // 创建一个 NioEventLoopGroup
ServerBootstrap bootstrap = new ServerBootstrap(); 
bootstrap.group(group)
    .channel(NioServerSocketChannel.class) // 设置 Channel 的类型为 NioServerSocketChannel
    .localAddress(new InetSocketAddress(9098)) // 监听本地的 9098 端口
    .option(ChannelOption.TCP_NODELAY, true) // 设置启用 Nagle 算法
    .option(ChannelOption.SO_BACKLOG, 1024) // 设置最大等待连接队列长度
    .childHandler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            // 对当前的 Channel 中的 pieline 添加一系列的 Handler
            ch.pipeline().addLast(new ProtobufDecoder(null));
            ch.pipeline().addLast(new ProtobufEncoder());
        }
    });
```



### Channel 的实例化过程

1. 通过 `ServerBootStrap` 实例对象设置对应的 `Channel`的类型，即上文的 `bootStrap.channel(NioServerSocketChannel.class)` 就是将当前的 `BootStrap` 的 `Channel` 类型设置为 `NioServerSocketChannel`。这个过程只是指定了 `Channel` 的类型，同时设置了对应的 `ChannelFactory`，并没有真正实例化 `Channel`

2. 通过调用抽象父类  `AbstractBootstrap`的`bind()`方法完成 `Channel` 的实例化。具体调用链（在 `AbstractBootstrap`）：`bind()` ——> `doBind()` ——> `initAndRegister()` ——> `channelFactory.newChannel()` 完成 Channel 的初始化工作

3. 在 `Channel` 的初始化过程中，首先会触发 `NioServerSocketChannel` 的构造器，使用默认的 `selector` 来创建一个 `ServerSocketChannel`，然后依次调用父类的方法进行一系列的初始化操作。该 `NioServerSocketChannel` 的继承关系如下图所示：<img src="https://i.loli.net/2021/10/14/59irmSKfx4pU2Xw.png" alt="2021-10-14 21-51-11 的屏幕截图.png" style="zoom:80%;" />

   每个每个阶段的任务如下：

   - `NioServerSocketChannel`：调用 `NioServerSocketChannel` 的静态方法 `newSocket()` 打开一个新的 `ServerSocketChannel`。
   - `AbstractNioMessageChannel`：没有做多余的工作，只是触发父类的构造函数而已
   - `AbstractChannel`：初始化 `AbstractChannel` 中的一些属性：
     - 将 parent 属性设置为 `null`
     - `unSafe` 通过 `newUnsafe()` 方法实例化一个 `unsafe` 对象
     - `pipeline` 通过 `newChannelPipeline()` 方法创建一个新的实例对象。因此，对于每个通过 `BootStrap` 对象的  `bind()` 来绑定监听地址时，都会创建一个新的 `pipeline`，因此不会有干扰
   - `AbstractNioChannel`：
     - `SelectableChannel ch` 被设置为通过 `NioServerSocketChannel`的静态方法 `newSocket()` 创建的 `ServerSocketChannel`
     - `readInterestOp` 被设置为 `SelectionKey.OP_ACCEPT`
     - `SelectableChannel ch` 被设置为非阻塞的

### Channel 的注册过程

在调用 `BootStrap` 的 `bind()` 方法的过程中，会调用到 `initRegister()` 方法，在这个方法中会对 Channel 进行初始化，具体源代码如下所示：

```java
// 移除了处理异常等不相关的代码
final ChannelFuture initAndRegister() {
    Channel channel = null;
    channel = channelFactory.newChannel();
    init(channel);

    ChannelFuture regFuture = config().group().register(channel);
    
    return regFuture;
}
```



## ChannelPipeline

`ChannelPipeline` 是 `ChannelHandler` 链的容器，用于存储一系列的 `ChannelHandler`。

每当一个新的 `Channel` 被创建了，都会建立一个新的 `ChannelPipeline`，并且这个 `ChannelPipeline` 会绑定到 `Channel` 上。具体对应关系如下图所示：

![NIO.png](https://i.loli.net/2021/10/14/Y9NxfiGKqVJaePI.png)

`ChannelPipeline` 是双向的，但是同一时刻只能有一个方向的任务进行，如下图所示：

<img src="http://tutorials.jenkov.com/images/netty/channelpipeline-1.png" style="zoom:80%">

当一个入站事件被触发时，将会按照 `ChannelInboundHandler` 的顺序进行对应的处理。Netty 一般会认为入站的开始位置为 `ChannelPiepeline` 的开始位置，及上图上 `SocketChannel` 为`ChannelPipeline` 的开始位置，因此在添加对应的 `ChannelHandler` 时需要注意这一点。



### ChannelPipeline 的初始化

初始化 `ChannelPipeline` 的源代码如下所示：

```java
/*
	这里的 Channel 是之前初始化的 NioServerSocketChannel
*/
protected DefaultChannelPipeline(Channel channel) {
    this.channel = ObjectUtil.checkNotNull(channel, "channel");
    succeededFuture = new SucceededChannelFuture(channel, null);
    voidPromise =  new VoidChannelPromise(channel, true);

    tail = new TailContext(this); // 上问 ChannelPipeline 示意图中的 head 节点，这是一个哑节点
    head = new HeadContext(this); //  Pipeline 中的尾节点，同样也是一个哑节点

    // 组成一个双向链表，注意上文提到一个 Pipeline 可以是双向的，因此这里的数据结构采用双向链表
    head.next = tail;
    tail.prev = head;
}
```



## ChannelHandler

## EventLoop

### EventLoop 的初始化

`NioEvenLoopGroup`有几个构造器，但是最终都是调用父类 `MultithreadEventLoopGroup` 的构造器

调用父类的 `MultithreadEventLoopGroup` 构造器初始化：

```java
private static final int DEFAULT_EVENT_LOOP_THREADS;

static {
    // 初始化 EventLoop 线程数，这里设置的线程数为处理器的数量 * 2
    DEFAULT_EVENT_LOOP_THREADS = Math.max(1, SystemPropertyUtil.getInt(
        "io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));

    if (logger.isDebugEnabled()) {
        logger.debug("-Dio.netty.eventLoopThreads: {}", DEFAULT_EVENT_LOOP_THREADS);
    }
}

// 确定 EventLoop 的线程数，再交给父类进行构造
protected MultithreadEventLoopGroup(int nThreads, Executor executor, Object... args) {
    super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, executor, args);
}
```

再次调用父类的构造函数进行初始化：

```java
protected MultithreadEventExecutorGroup(int nThreads, 
                                        Executor executor,
                                        EventExecutorChooserFactory chooserFactory, 
                                        Object... args) {
    if (executor == null) {
        executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
    }

    children = new EventExecutor[nThreads];

    for (int i = 0; i < nThreads; i ++) {
        children[i] = newChild(executor, args);
    }

    chooser = chooserFactory.newChooser(children);
}
```

主要任务：

- 创建一个大小为 `nThreads` 的 `EventExecutor` 数组

- 通过调用 `newChild` 来初始化 children 数组的每个元素

- 根据 `nThreads` 的大小，创建不同的 `EventExecutorChooser`，如果 `nThreads` 是 2 的整数幂，则使用 `PowerOfTwoEventExecutorChooser`，否则，使用 `GenericEventExecutorChooser`。它们的功能够一样，都是从 children 数组中选出一个合适的 `EventExecutor` 实例

  源代码如下：

  ```java
  public EventExecutorChooser newChooser(EventExecutor[] executors) {
      if (isPowerOfTwo(executors.length)) {
          return new PowerOfTwoEventExecutorChooser(executors);
      } else {
          return new GenericEventExecutorChooser(executors);
      }
  }
  ```

总的初始化流程：

- EventLoopGroup（实际上是 `MultithreadEventExecutorGroup`）内部维护了一个类型为 `EventExecutor` 的 children 数组，其大小为 `nThreads`，这样就创建了一个线程池
- 在 `MultithreadEventLoopGroup` 中会确定要选择的 `EventLoop` 线程数，默认为可用的处理器大小 * 2
- `MultithreadEventExecutorGroup` 会调用 `newChild` 方法来初始化 children 数组的每个元素