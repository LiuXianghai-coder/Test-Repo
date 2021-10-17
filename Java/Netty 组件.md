# Netty 组件介绍

## BootStrap

Netty 中的 BootStrap 分为两种：一种是客户端的 `BootStrap`；一种是服务端的 `ServerBootStrap`。

- 客户端的 `BootStrap`

  初始化客户端，该 `BootStrap` 只有一个 `EventLoopGroup`，用于连接远程主机

- 服务端的 `ServerBootStrap`

  用于绑定本地端口，一般存在两个 `EventLoopGroup`（一个用于包含单例的 `ServerChannel`，用于接收和分发请求；另一个是包含了所有创建的 Channel，处理服务器接受到的所有来自客户端的连接）。

### 启动流程

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



## Channel

以客户端连接到服务端为例



### 客户端 Channel 的初始化过程

1. 通过 `BootStrap` 实例对象设置对应的 `Channel`的类型，即上文的 `bootStrap.channel(NioSocketChannel.class)` 就是将当前的 `BootStrap` 的 `Channel` 类型设置为 `NioSocketChannel`。这个过程只是指定了 `Channel` 的类型，同时设置了对应的 `ChannelFactory`，并没有真正实例化 `Channel`

2. 通过调用 `Bootstrap`的`connect()`方法完成 `Channel` 的实例化。具体调用链（在 `Boostrap`）：`connect()` ——> `doResolveAndConnect()` ——> `initAndRegister()` ——> `channelFactory.newChannel()` 完成 Channel 的初始化工作

3. 在 `Channel` 的初始化过程中，首先会触发 `NioSocketChannel` 的构造器，使用默认的 `selector` 来创建一个 `NioSocketChannel`，然后依次调用父类的方法进行一系列的初始化操作。


实例化的具体流程如下图所示：![Netty.png](https://i.loli.net/2021/10/15/RBn9HCc3aq1M6IV.png)

### 服务端 Channel 的初始化过程

服务端的 Channel 初始化与客户端的 Channel 类似，不同的地方在于服务端的 Channel 是绑定到对应的主机和端口，而 客户端的 Channnel 是需要连接到服务器的 Channel。



### 客户端 Channel 的注册过程

在调用 `BootStrap` 的 `bind()` 方法的过程中，会调用到 `initRegister()` 方法，在这个方法中会对 Channel 进行初始化；在调用 `register()` 方法之后，会再次调用 `EventLoopGroup` 对象的 `register(Channel channel)` 方法将 `Channel` 注册到对应的 `EventLoopGroup` 中。具体源代码如下所示：

```java
// 移除了处理异常等不相关的代码
final ChannelFuture initAndRegister() {
    Channel channel = null;
    // 使用对应的 ChannelFactory 对象创建一个 Channel
    channel = channelFactory.newChannel();
    init(channel);

    // 将 Channel 注册到对应的 EventLoopGroup 中
    ChannelFuture regFuture = config().group().register(channel);
    
    return regFuture;
}
```

调用 `EventLoopGroup` 的 `register(Channel channel)` 方法会调用 `AbstractUnsafe` 的 `register(EventLoop eventLoop, ChannelPromise promise)` 方法来进行注册。具体源代码如下所示：

```java
public final void register(EventLoop eventLoop, final ChannelPromise promise) {
    // 省略一部分检查的代码
    AbstractChannel.this.eventLoop = eventLoop;
    
    if (eventLoop.inEventLoop()) {
        register0(promise);
    }
    // 省略一部分代码。。。
}
```

`register0` 源代码如下所示：

```java
private void register0(ChannelPromise promise) {
    // 省略一些无用的代码
    doRegister();
    // 省略一些无用的代码
}
```

由于当前使用的是 `NioSocketChannel`，因此会调用到 `AbstractNioChannel` 的 `doRegister()` 方法，具体的源代码如下所示：

```java
protected void doRegister() throws Exception {
    boolean selected = false;
    for (;;) {
        try {
            /*
            	javaChannel() ：由于这里注册的 Channel 是 NioSocketChannel，因此会得到当前正要注册的 NioSocketChannel
            	通过调用 Channel 的 register() 方法，将这个 Channel 注册到对应的 Selector 中，这里的 selector 对应着 NioEventLoop，即分配到的 EventLoop 对象，到此 Channel 的注册完成
            */
            selectionKey = javaChannel().register(eventLoop().unwrappedSelector(), 0, this);
            return;
        } catch (CancelledKeyException e) {
           // 省略一部分异常捕获的代码
        }
    }
}
```



总的流程图如下所示：![Netty.png](https://i.loli.net/2021/10/15/JZ4q3SePWRmvrzM.png)

### 服务端 Channel 的注册过程

同样地，服务端的 Channel 的注册和客户端的也十分类似，不同的地方在于服务端的 Channel 会注册到“主” `EventLoopGroup` ，而客户端的 Channel 则只是注册到一个普通的 `EventLoopGroup`。



### 客户端 Channel 的连接过程

![Netty.png](https://i.loli.net/2021/10/16/J18grlLjO49AdYS.png)



### 服务端 Channel 接受连接的过程

服务端的 Channel 在启动时会首先创建一个 `NioServerSocketChannel` 并注册到 “主” `EventLoopGroup`，用于创建对应的处理对应的连接请求。处理连接的请求在初始化 `NioServerSocketChannel` 的时候就已经准备好了，初始化服务端的 Channel 的源代码如下所示：

```java
// 该代码位于 ServerBootStrap
@Override
void init(Channel channel) {
    // 获取当前 Channel 的 Pipeline
    ChannelPipeline p = channel.pipeline();
    
    // 这里的 childGroup 是 “从”—EvenLoopGroup，用于真正处理请求
    final EventLoopGroup currentChildGroup = childGroup;
    final ChannelHandler currentChildHandler = childHandler;
    final Entry<ChannelOption<?>, Object>[] currentChildOptions = newOptionsArray(childOptions);
    final Entry<AttributeKey<?>, Object>[] currentChildAttrs = newAttributesArray(childAttrs);

    p.addLast(new ChannelInitializer<Channel>() {
        @Override
        public void initChannel(final Channel ch) {
            final ChannelPipeline pipeline = ch.pipeline();
            ChannelHandler handler = config.handler();
            // 将在 bootStrap 中添加的 ChannelHandler 对象添加到 Pipeline 的末尾
            if (handler != null) {
                pipeline.addLast(handler);
            }

            ch.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    // ServerBootstrapAcceptor 中重写了 channelRead() 方法，每个连接的请求都会首先调用这个方法以读取请求的内容
                    pipeline.addLast(new ServerBootstrapAcceptor(
                        ch, currentChildGroup, currentChildHandler, currentChildOptions, currentChildAttrs));
                }
            });
        }
    });
}
```

`ServerBootstrapAcceptor` 中 `channelRead()` 的源代码如下所示：

```java
@Override
@SuppressWarnings("unchecked")
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    final Channel child = (Channel) msg;
    
    // childHandler 由 ServerBootStrap 对象进行指定
    child.pipeline().addLast(childHandler);
    
    /* 
    	注意这里的 childGroup 对应的是 “从”—EventLoopGroup，由于 “从”—EventLoopGroup 是处理请求的，
    	因此在这里得到的请求（即 msg）会为它新创建一个 Channel 注册到“从”—EventLoopGroup 的某个 EventLoop 中
    	
    	这个请求转发到 “从”—EventLoopGroup 的工作是由 “主”—EventLoopGroup 来完成的
    */
    childGroup.register(child).addListener();
    
    // 省略部分异常检查代码。。。。
}
```

`channelRead()` 的工作已经介绍完了，现在问题是 “主”—`EventLoopGroup` 是如何处理请求的了。这个和使用 NIO 进行网络编程有关系，当绑定当前的一个 `NioServerSocketChannel`时，Netty 的底层会监听对应的事件。当 `NioServerSocketChannel` 的状态为 `SelectionKey.OP_ACCEPT`时，表示当前的 `NioServerSocketChannel` 是可以接收连接请求的，当一个请求到达时，便会执行对应的请求，`NioServerSocketChannel` 执行对应请求的源代码如下所示：

```java
/* 
	因为每个 EventLoop 都是通过单个线程的方式来处理对应的任务的，同样地，NioServerSocketChannel 也是通过 EventLoop 来进行任务处理的
	
	该方法位于 NioEventLoop 中，因为初始化 NioServerSocketChannel 时指定了 NioEventLoop 来处理每个任务
*/
@Override
protected void run() {
    for (;;) {
        // 省略一大段异常处理和其它不是很关键的代码
        processSelectedKeys();
    }
}
```

`processSelectedKeys` 源代码如下所示：

```java
private void processSelectedKeys() {
    if (selectedKeys != null) {
        processSelectedKeysOptimized();
    } else {
        processSelectedKeysPlain(selector.selectedKeys());
    }
}
```

尽管存在一个判断条件，但实际上，由于当前处理的 Channel 是 `NioServerSocketChannel`，最终都会调用 `processSelectedKey` 方法进行进一步的处理。具体的源代码如下所示：

```java
private void processSelectedKey(SelectionKey k, AbstractNioChannel ch) {
    // 省略一大段异常检查的代码
    int readyOps = k.readyOps();
    // 这里就是 NioServerSocketChannel 在可以读取时要进行处理的代码块。当当前的 Channel 是可读的或者是可接收请求是则执行对应的逻辑
    if ((readyOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readyOps == 0) {
        /* 
        	这里的 Unsafe 对象由 NioServerSocketChannel 在实例化时创建，
        	具体由 AbstractNioMessageChannel 通过重写 newSafe() 方法来实现
        	
        	这里的 Unsafe 类为 AbstractNioMessageChannel 的内部类
         */
        unsafe.read();
    }
}
```

`unsafe.read()`方法的源代码：

```java
@Override
public void read() {
    // 核心代码如下，已经省略大部分其它不太重要的代码 
    do {
        /* 
        	doReadMessages 由具体的子类来实现，在这里具体的子类为 NioServerSocketChannel
        	
        	每次调用 doReadMessages 都将创建一个新的 SocketChannel，即一个新的连接放入 readBuf 列表中
        */
        int localRead = doReadMessages(readBuf);
        if (localRead == 0) {
            break;
        }
        if (localRead < 0) {
            closed = true;
            break;
        }

        allocHandle.incMessagesRead(localRead);
    } while (continueReading(allocHandle));
    
    int size = readBuf.size();
    // 将读取到的数据进行相应的处理
    for (int i = 0; i < size; i ++) {
        /*
        	由于只有一个服务线程处理请求数据，因此就不会因为同时有多个请求进行访问而造成数据混乱
        */
        readPending = false;
        pipeline.fireChannelRead(readBuf.get(i));
    }
    
    readBuf.clear();
    allocHandle.readComplete();
    pipeline.fireChannelReadComplete();
}
```

`doReadMessages()` 方法源代码如下所示：

```java
@Override
protected int doReadMessages(List<Object> buf) throws Exception {
    // 根据当前的 NioServerSocketChannel 创建一个新的 SocketChannel
    SocketChannel ch = SocketUtils.accept(javaChannel());
    if (ch != null) {
        // 可以看到，每次读取时都会为当前读取的数据段分配一个新的 NioSocketChannel 来进行相应的任务处理
        buf.add(new NioSocketChannel(this, ch)); // 新分配的 NioSocketChnnel 的
        return 1;
    }
    // 省略部分异常检测代码
    
    return 0;
}
```

至此，`NioServerSocketChannel` 是如何分发请求到 `NioSocketChannel` 就已经非常清楚了

接收请求的具体流程如下所示：

![Netty.png](https://i.loli.net/2021/10/16/Qg2s3dwefNDb68Y.png)



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

### 服务端的 ChannelHandler

由于服务端存在两种类型的 `EventLoopGroup`，一个用于接收和分发请求，一个用于真正处理请求，因此 ChannelHandler 也分为两种，一类是用于接收请求时使用，一类是用于处理请求时使用。

`ServerBootStrap`通过 `handler(ChannelHandler handler)` 方法来指定接收请求是要执行的 `ChannelHandler`，这里的`ChannelHandler`添加是发生在初始化 `Channel` 的过程中（注意是初始化 Channel 而不是实例化 Channel）。具体的源代码如下所示：

```java
@Override
void init(Channel channel) { // 这里的 Channel 是实例化之后的 Channel，即 NioServerSocketChannel
    ChannelPipeline p = channel.pipeline();

    final EventLoopGroup currentChildGroup = childGroup;
    final ChannelHandler currentChildHandler = childHandler;
    final Entry<ChannelOption<?>, Object>[] currentChildOptions = newOptionsArray(childOptions);
    final Entry<AttributeKey<?>, Object>[] currentChildAttrs = newAttributesArray(childAttrs);

    p.addLast(new ChannelInitializer<Channel>() {
        @Override
        public void initChannel(final Channel ch) {
            final ChannelPipeline pipeline = ch.pipeline();
            // 这里的 handler 是通过 ServerBootStrap 调用 handler(...) 来指定的
            ChannelHandler handler = config.handler();
            if (handler != null) {
                pipeline.addLast(handler);
            }

            ch.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    pipeline.addLast(new ServerBootstrapAcceptor(
                        ch, currentChildGroup, currentChildHandler, currentChildOptions, currentChildAttrs));
                }
            });
        }
    });
}
```

初始化之后，服务端 `NioServerSocketChannel` 的 Pipeline 的底层 Handler 结构如下图所示：

![Netty.png](https://i.loli.net/2021/10/17/nLQdc1ibXImK9Ge.png)

当接收到一个新的客户端请求时，会调用 `ServerBootstrapAcceptor.channelRead` 方法，具体源代码如下图所示：

```java
 public void channelRead(ChannelHandlerContext ctx, Object msg) {
     // 这里的 child 是分配到 workGroup 中的一个 Channel，不是服务端处理连接的 NioServerSocketChannel
     final Channel child = (Channel) msg;

     // 这里的 chilHandler 是在 ServerBootStrap 对象中通过 childHandler() 方法设置的
     child.pipeline().addLast(childHandler);
     
     // 省略一部分不重要的代码
 }
```



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

具体的流程如下所示：

![Netty.png](https://i.loli.net/2021/10/15/rCzvEQiGoOqZJRL.png)