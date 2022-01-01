# Kafka 的基本使用

Kafka 是一款分布式消息发布和订阅系统，最初的目的是作为一个日志提交系统来使用。现在，也可以作为一般的消息中间件来使用。

<br />

## 组件介绍

相关的组件介绍如下表所示：

| 组件           | 解释                                                         |
| -------------- | ------------------------------------------------------------ |
| Broker         | 实际 Kafka 存储消息的部分                                    |
| Topic          | Kafka 通过 Topic 来对消息进行归类，发布到 Kafka 的每条消息都<br />需要指定一个 Topic，Topic 是一个逻辑上的概念，没有物理结构 |
| Producer       | 向 Kafka 发送消息的角色                                      |
| Consumer       | 从 Kafka 对应的主题中获取消息的角色                          |
| Consumer Group | 每个 Consumer 都只会属于一个 Consumer Group，一个消息可以被多个 <br />Consumer Group 消费，但是只能被 Consumer Group 中的一个 Consumer 消费 |
| Partition      | 物理上的概念，将 Topic 上的数据拆分到多个 Partition，每个 Partition <br />内的消息都是有序的 |



<br />

## 使用前的准备

在使用之前，务必确保已经正确设置了 `JAVA_HOME` 环境变量，Zookeeper 和 Kafka 都依赖 JRE 才能运行

Kafka 的注册中心依赖于 Zookeeper，这点在 Kafka 2.8 之后可能会有所变化，但是出于稳定性地考虑，依旧按照传统的使用 Zookeeper 来实现注册中心的功能。

<br />

### 启动 Zookeeper

ke

有关 Zookeeper 的安装可以参考：https://zookeeper.apache.org/doc/current/zookeeperStarted.html

如果想要直接点的，可以参考我的：https://www.cnblogs.com/FatalFlower/p/15747105.html；如果想要搭建集群，可以参考：https://www.cnblogs.com/ysocean/p/9860529.html

这里为了简单起见，假设 Zookeeper 是通过单节点的方式启动的



<br />

### 安装 Kafka

首先，从 <a href="https://kafka.apache.org/downloads">官网</a> 下载需要的 Kafka 的二进制文件，进入官网后，具体如下：

<img src="https://s6.jpg.cm/2021/12/31/LtnWy8.png" style="zoom:50%" />

点击链接进入下载界面，选择合适的镜像站点进行下载：

<img src="https://s6.jpg.cm/2021/12/31/Ltn3Q8.png" style="zoom:50%" />

这里下载的二进制版本中会包含有关 Zookeeper 的相关文件，Kafka 也提供了对应的启动脚本来启动 Zookeeper，但是为了更好的保持这两者的独立性，在本文中将使用自己的 Zookeeper 而不是 Kafka 内置的

<br />

解压下载的压缩包之后，就需要创建 Kafka 的 Broker 了，这里的 Broker 和 RocketMQ 的 Broker 十分类似，Kafka 的 Broker 存储了有关 Topic 的分区和副本的信息，Producer 和 Consumer 都是通过 Topic 来进行生产和消费的。

创建 Broker 之前首先需要创建对应的配置文件，以 `config` 目录下的 `server.properties`  配置文件为例：

```properties
# 创建的 Broker 的 id，在整个 Kafka 系统中都要保证这个 id 的唯一性，同时，这个 id 只能是非负数
broker.id=0

# Broker 的监听的配置，PLAINTEXT 表示这个 listener 的名称
# 127.0.0.1 表示监听的主机地址，9092 表示监听端口
listeners=PLAINTEXT://127.0.0.1:9092

# 服务端用于接受请求和返回响应使用的线程数
num.network.threads=3

# 服务端用于处理请求的线程数，包括处理 IO 的那部分线程
num.io.threads=8

# 用于发送响应的 buffer 的大小
socket.send.buffer.bytes=102400

# 接受请求的 buffer 的大小
socket.receive.buffer.bytes=102400

# 每次请求的最大大小，用于防止 OutOfMemory Exception
socket.request.max.bytes=104857600

# 该 Broker 的存储目录，存储所有的数据（包括消息、索引等数据）
log.dirs=/tmp/kafka-logs

# 每创建一个 Topic 时默认的分区的数量，在创建 Topic 后可以增加分区的数量，
# 但是不能减少，如果需要减少分区则只能新建一个新的 Topic
num.partitions=1
# 每个日志目录使用的线程数，当 Kafka 启动、关闭、重启时需要使用对应的线程来处理日志片段
# 当一个 Topic 存在多个分区时，适当调大这个属性可以更好地利用计算机并行处理的能力
num.recovery.threads.per.data.dir=1

# 日志的保留时间
log.retention.hours=168
# 每个分区保留的数据的最大总量，超过这个总量的话，则会将前面的一部分数据视为是无效的
log.retention.bytes=1073741824
# 日志片段的最大大小
log.segment.bytes=1073741824

# Zookeeper 的连接地址，注意与开启的 Zookeeper 相匹配
zookeeper.connect=localhost:2181
zookeeper.connection.timeout.ms=18000

group.initial.rebalance.delay.ms=0
```

重点关注以下几个配置属性：`broker.id`、`listeners`、`log.dirs` 和 `zookeeper.connect`，这四个属性是 Kafka 能够成功运行的关键属性

配置之后，执行 `bin` 目录下的脚本文件启动 Kafka 的 Broker：

```sh
# 当前目录位于解压后的 Kafka 的基目录
./bin/kafka-server-start.sh config/server.properties
# 在 Windows 上，需要使用 PowerShell 执行 ./windows/bin/kafka-server-start.cmd 的执行脚本

# 如果不想看到启动时的输出，可以将其启动之后再放入后台运行即可
# 在 Unix 类操作系统上，可以这么做
nohup ./bin/kafka-server-start.sh config/server.properties &
```

现在，Kafka 应当已经启动了

<br />



## 基本使用

具体的使用可以参考：https://kafka.apache.org/quickstart，下文的所有目录都基于解压后的 Kafka 所在的主目录，且是在 Linux 类系统上执行的脚本文件

<br />

### 创建主题

在正式使用之前，首先需要创建一个主题来接收生产者发送的消息

使用如下的脚本即可创建一个名为 “order” 的主题

```bash
# 创建一个名为 order 的 Topic，分区数量为 1，副本数为 1
# 需要注意的是，副本数不能超过 Broker 的数量，否则就会导致无法创建满足条件的副本，而无法正常创建 Topic
./bin/kafka-topics.sh --create --topic order --bootstrap-server 127.0.0.1:9092 --partitions 1 --replication-factor 1
```

查看创建的 order Topic 的信息：

```bash
# 指定 --describe 和对应的 Topic 的名称即可
./bin/kafka-topics.sh --describe --topic order --bootstrap-server 127.0.0.1:9092
```

会看到类似下面的输出：

<img src="https://s6.jpg.cm/2021/12/31/LteQc2.png" style="60%">

<br />

### 发送消息

使用以下脚本进入发送消息的控制台：

```bash
./bin/kafka-console-producer.sh --topic order --bootstrap-server 127.0.0.1:9092
```

然后再控制台输入一些消息，具体如下所示：

<img src="https://s6.jpg.cm/2021/12/31/LteexL.png" style="60%">

每一行都会被看做独立的消息发送到主题中，因此现在如果消费者从头开始消费的话，应该能够收到五条消息

<br />

### 接收消息

- 单个消费者消费

    使用以下脚本即可从对应的主题从头开始消费消息：

    ```bash
    # 使用 --from-brginning 选项表示从该 Topic 的开始位置消费消息，如果没有指定这个选项的话
    # 将只接收当启动 Consumer 之后的发送的消息
    ./bin/kafka-console-consumer.sh --from-beginning --topic order --bootstrap-server 127.0.0.1:9092
    ```

    具体输出如下所示：

    <img src="https://s6.jpg.cm/2021/12/31/Lte8hO.png" style="60%">

    可以看到，确实是收到了之前发送的五条消息

- 多个消费者消费消息

    首先，创建两个消费组分别为 `xhliu-group1` 和 `xhliu-group1`，如下面的命令所示：

    ```bash
    #  在一个终端中执行以下的命令，使用 --consumer-property 指定消费者所属的消费组
    ./bin/kafka-console-consumer.sh --topic order --bootstrap-server 127.0.0.1:9092 --consumer-property group.id=xhliu-group1
    
    # 在另一个新的终端中执行下面的命令，将当前的消费者放入 xhliu-group2 的消费组中
    ./bin/kafka-console-consumer.sh --topic order --bootstrap-server 127.0.0.1:9092 --consumer-property group.id=xhliu-group2
    ```

    注意，由于消费者是按照组的方式来划分的，因此不同的消费组能够消费相同的消息；从单个的消费组的角度来讲，每个消费组中在同一时刻只能有一个消费者去消费主题中的消息，这点要特别注意

- 查看消费组的信息

    执行以下的脚本文件即可查看对应的 Broker 下存在的消费组列表

    ```bash
    # 添加 --list 选项表示列出当前 Broker 下的所有消费组
    ./bin/kafka-consumer-groups.sh --bootstrap-server 127.0.0.1:9092 --list
    ```

    如果想要查看对应的消费组的具体细节信息，可以执行以下的脚本：

    ```bash
    # 添加 --group 选项查看对应的消费组信息，--describe 表示显示详细信息
    ./bin/kafka-consumer-groups.sh --group xhliu-group1 --bootstrap-server 127.0.0.1:9092 --describe 
    ```

    ![2021-12-31 23-14-57 的屏幕截图.png](https://s2.loli.net/2021/12/31/qxPBMaoIOyVz8mY.png)

    其中，比较重要的几个参数为 `CURRENT-OFFSET`（表示消费组已经消费的消息的偏移量）、`LOG-END-OFFSET`（主题对应分区消息的结束偏移量）、`LAG`（表示消费组未消费的消息的数量）

    <br />

参考：

<sup>[1]</sup> https://mp.weixin.qq.com/s/SdR9wey7hUZqYq6K8FyNZg