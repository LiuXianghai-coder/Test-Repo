## NIO 简介

​	自 JDK 1.4 以来，引入了一个被称为 NIO（New IO） 的 IO 操作，是标准 IO 一个替代品。Java 的 NIO 提供了一种与传统意义上的 IO 不同的编程模型。有时，NIO 也被称为 No-Blocking IO，这是因为一般情况下 NIO 的 API 都是非阻塞的。然而，使用 No-Blocking IO 并不能很好地表示 NIO 地意思，因为 NIO 有一部分 API 依旧是阻塞的。



### 传统 IO 与 NIO

#### 主要区别

- 传统 IO 是面向字节流的，而 NIO 是面向 Buffer 的
  - 面向字节流：面向字节流意味着一次从一个流中读取一个或者多个字节，要对这些字节流做什么操作取决于自己的实现。由于这些读取的字节流并不能缓存起来，因此在读取字节流的时候只能是对读取到的字节进行相应的操作，不能再次读取之前的字节也不能读取后面的字节。
  - 面向 Buffer：与面向字节流的方式不同，数据被读取到 Buffer 中以便之后再进行处理。由于 Buffer 的存在，对于这个 Buffer 内任意字节的读取都是可行的。同时，由于 Buffer 是位于内存中的，因此数据的处理速度要比一般面向字节流的方式的处理速度更快。
- 传统 IO 是阻塞的，而 NIO 一般是非阻塞的
  - 传统 IO 的各种流都是阻塞的，这意味着，当一个线程调用类似 `read()` 或 `write()` 这样的方法时，整个线程都会是阻塞的。在这个线程被阻塞期间，这个线程不能做其它的任何工作
  - NIO 的各个操作一般都是非阻塞的，这种非阻塞的模式使得一个线程能够当一个 IO 操作完成时再去进行相应的处理，而不是一直等待。

一般模型：

<img src="https://www3.ntu.edu.sg/home/ehchua/programming/java/images/IO_Processes.png" alt="image.png" style="zoom:150%;" />

- Disk Buffer：一个用于读取存储在磁盘上的数据块的 RAM，这个操作是一个十分慢的操作，因为它要调用物理磁盘指针的移动来获取数据
- OS Buffer ：OS 有自己的缓存可以缓存更多的数据，并且可以更加优雅地对数据进行管理。这个缓存也可以在应用程序之间进行共享。
- Application Buffer：应用程序自己的缓存

传统的 IO 只能使用到 Disk Drive Buffer 的缓存，而 NIO 则能够使用 Application Buffer 甚至是 OS Buffer，因此 NIO 对于数据的处理回更加高效。



#### Buffer

NIO 的数据传输是通过继承了 `java.nio.Buffer` 类的被称为 buffers 来实现的。一个 Buffer 与数组有一些相似，但是通过与底层操作系统紧密耦合因此更加有效率一些。一个 Buffer 是一个连续的、线性的存储。和数组一样，一个 Buffer 有固定的大小。

Java 中 NIO 的继承类类对除了 `boolean` 类型外，其它的基本数据类型都有相对应的 buffer 实现。这些实现类的抽象父类 `java.nio.Buffer` 提供了对于所有 buffer 的一般属性，并且定义了一些一般的操作集合。



一个 `Buffer` 一般会有以下几个属性字段：

- `mark`

  > 用于标记上次访问的 `position` 的位置索引，可以通过调用 `mark()` 方法来获取。如果重新设置 `position` 或者 `limit`  的值小于 `mark`，那么就会丢弃之前的 `mark`

- `position`

  >  	表示要读取或者写入的下一个元素的位置索引，这个属性字段的值不能大于 `limit`。可以通过 `position()` 方法来获取到当前的 `position`，或者通过 `position(int newPosition)` 来重新设置 `position`。 

- `limit`

  > ​	表示在 Buffer 中第一个不能被读取或者修改的元素位置索引，`limit` 的大小不能超过 `capacity`。实际上，这就表示当前处理的数据所占用的空间，也就是说，在当前处理的 Buffer 中，有效数据的范围是 [0, limit -1]。
  >
  > ​	可以通过调用 `limit()` 方法来得到当前的 `limit` 位置，也可以通过 `limit(int newLimit)` 方法来重新设置`limit`，但是设置的 `limit` 不能大于 `capacity`  

- `capacity`

  > ​	表示能够存储元素的最大容量大小，这个属性字段不能为空，在创建 Buffer 的时候就必须指定并且在之后不能被修改。可以通过 Buffer 的 `capacity()` 方法来获取该 Buffer 的容量

- `address`

  > 只有在直接 Buffer 中才会使用到

这几个字段之间的大小关系为：0 ≤ mark ≤ position ≤ limit ≤ capacity



基本使用：

- 创建一个 buffer

  - 调用 `Buffer` 子类的  `allocate(int capacity)` 方法，可以得到一个容量为 `capacity` 的子类 buffer
  - 调用 `Buffer` 子类的  `wrap(type[] array, int offset, int length)` 或  `wrap(type[] array)` 方法将一个 `byte` 数组转换为 buffer
  - 通过已有的 buffer 创建一个视图，这个会共享已有的 buffer。具体可以查看 `Buffer` 具体子类的 `duplicate()` 方法

- 操作 buffer

  - `mark()`

    > 标记之前的 `position`，使得当前 buffer 对象的 `mark` 字段值更新为当前的 `position`，

  - `reset()`

    > 将当前 buffer 对象的 `position` 置为 `mark`，如果没有之前没有调用 `mark()` 更新 `mark`，那么就会抛出 `InvalidMarkException`。 

  - `clear()`

    > 将 `position` 置为 0，`limit` 置为 `capacity`，同时丢弃 `mark`，为读取数据做准备
    >
    > 这个方法实际上并不会直接擦除 buffer 内的数据，但是在一般情况下清除之后都会直接读取数据来覆盖原有的数据，因此实际效果与擦除了数据的效果是一致的。

  - `flip()`

    > 设置 `limit` 的值为 `position`，同时将 `position` 置为 0，同时丢弃 `mark`，为输出数据做准备。 
    >
    > 一般在调用 `flip()` 方法后会调用 `compact()` 方法来移动缓冲区数据到开始位置

  - `compact()`

    > 将 `position` 到 `limit` 之间的数据复制到 buffer 的开始位置，如果已经定义了 `mark`，那么就丢弃 它

  - `rewind()`

    > 设置 `position` 为 0，同时丢弃 `mark`，为数据读取做准备

    

- 直接 buffer和非直接 buffer

  - 直接 buffer

    - JVM 将会尽最大努力直接在 buffer 上执行 IO 操作，因此，它将试图去避免在每次调用系统 IO 操作之前将缓冲区内容复制到中间缓冲区。所以，直接 buffer 将会更加有效率些。

    - 对于 `ByteBuffer`，可以通过调用 `allocateDirect(int capacity)` 分配一个直接 `ByteBuffer`。对于其他的 buffer（char、short、int 、float、long、double），需要首先创建一个 `ByteBuffer`，然后通过类似于 `asIntBuffer()` 的方法创建一个对应的视图。由于这些基本数据类型是由多个 byte 组成的单元，所以需要通过 `order(ByteOrder order)` 指定 byte 的端顺序（大端—大byte在前、小端—小 byte 在前）。order 参数可以是  `ByteOrder.BIG_ENDIAN`, `ByteOrder.LITTLE_ENDIAN`，或者 `ByteOrder.nativeOrder()`自动得到当前平台的端顺序。

      ```java
      // 使用直接 buffer 的方式分配 200 字节的 buffer
      ByteBuffer buffer = ByteBuffer.allocateDirect(200);
      
      // 将这个 ByteBuffer 转换为对应的 IntBuffer。
      // 一般来讲，除了那些老式的操作系统，如 Solaris、AIX 为大端顺序之外，其它的操作系统都为小端顺序
      IntBuffer intBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
      ```

  - 非直接 buffer

    将 buffer 分配到 JVM 中的堆区中



#### Channel 

一个 Channel 代表一个物理 IO 连接。跟标准 IO 流有点像，但是 Channel 是一个更加依赖平台的流版本。由于channel 跟平台之间的有着紧密联系，因此它能够实现更好的 IO 吞吐量。 channel 分为以下几种：

- `FileChannel`
- `SocketChannel` ：支持非阻塞 TCP Socket 连接
- `DatagramChannel` ：UDP 面向数据报 Socket 的连接



Channel 与 Stream 的区别：

- 可以同时在多个 Channel 上分别进行 读/写 操作；在 Stream 上只能执行一种操作（读或写）
- Channel 的读写操作可以是异步的，但是 Stream 的操作是同步的
- Channel 都是围绕 buffer 来展开操作的



获取 Channel：

> ​	通过 `java.io.FileInputStream`, `java.io.FileOutputStream`, `java.io.RandomAccessFile`, `java.net.Socket`, `java.net.ServerSocket`, `java.net.DatagramSocket`, 和`java.net.MulticastSocket` 等对象的 `getChannel()` 方法可以获取一个 Channel 对象。

