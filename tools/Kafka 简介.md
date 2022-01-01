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

<br />

## Java 客户端整合

有时可能希望直接使用 Java 来直接使用 Kafka 进行消息的发送，在那之前，需要添加 Kafka 对于 Java 的客户端依赖项，具体可以到 <a href="https://mvnrepository.com/">Maven 仓库 </a> 查找对应的依赖项：

```xml
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
    <version>${kafka.version}</version> <!-- 选择对应的 kafka 的版本 -->
</dependency>
```

<br />

### 生产者端

最好的做法是将属性配置成为一个单例的值对象，为了简单期间，这里只是做了一下配置，具体内容如下：

```java
// 生产者端的属性配置
public Properties producerProp() {
    Properties properties = new Properties();
    // 设置 Kafka 的 Broker 列表
    properties.put(BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092,127.0.0.1:9093");

    /*
            设置消息的持久化机制参数：
            0 表示不需要等待任何 Broker 的确认；
            1 表示至少等待 Leader 的数据同步成功；
            -1 则表示需要等待所有的 min.insync.replicas 配置的副本的数量都成功写入
         */
    properties.put(ACKS_CONFIG, "1");

    /*
            配置失败重试机制，由于会重新发送消息，因此必须在业务端保证消息的幂等性
         */
    properties.put(RETRIES_CONFIG, 3); // 失败重试 3 次
    properties.put(RECONNECT_BACKOFF_MS_CONFIG, 300); // 每次重试的时间间隔为 300 ms


    /*
            配置缓存相关的信息
         */
    properties.put(BUFFER_MEMORY_CONFIG, 32*1024*1024);  // 设置发送消息的本地缓冲区大小，这里设置为 32 MB
    properties.put(BATCH_SIZE_CONFIG, 16*1024);  // 设置批量发送消息的大小，这里设置为 16 KB
    
    /*
            batch 的等待时间，默认值为 0, 表示消息必须被立即发送，这里设置为 10 表示消息发送之后的 10 ms 内，
            如果 Batch 已经满了，那么这个消息就会随着 Bathh 一起发送出去
      */
    properties.put(LINGER_MS_CONFIG, 10);

    /*
            配置 key 和 value 的序列化实现类
         */
    properties.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    properties.put(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

    // 属性配置完成
    return properties;
}
```

配置完成之后，就可以通过 Java 来向 Kafka 来发送消息了，Kafka 对与 Java 的客户端接口提供了两种发送消息的方式：以同步阻塞的方式发送消息和以异步非阻塞的方式来发送消息，

- 以同步阻塞的方式发送消息：

    ```java
    static final String TOPIC_NAME = "xhliu";
    static final Integer PARTITION_TWO = 1;
    static final Gson gson = new GsonBuilder().create();
    
    void syncSend() {
        Properties producerProp = producerProp(); // 加载配置属性
        Producer<String, String> producer = new KafkaProducer<>(producerProp);
        Message message;
    
        for (int i = 0; i < 5; ++i) {
            // 具体的消息实体
            message = new Message(i, "BLOCK_MSG_" + i);
            
            // 生产者发送消息的记录对象
            ProducerRecord<String, String> record = new ProducerRecord<>(
                TOPIC_NAME, PARTITION_TWO,
                String.valueOf(message.getId()), gson.toJson(message)
            );
    
            try {
                // 发送消息，得到一个 Future，关于这个类具体可以参考 《Java 并发编程实战》
                Future<RecordMetadata> future = producer.send(record);
                // Future 的 get() 方法将会阻塞当前的线程
                RecordMetadata metadata = future.get();
                // 打印发送之后的结果。。。。。
                log.info("[topic]={}, [position]={}, [offset]={}", metadata.topic(),
                         metadata.partition(), metadata.offset());
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    ```

    

- 以异步的方式发送消息

    以异步的方式发送消息与同步的方式类似，最大的不同是通过异步的方式发送是通过哦注册回调函数来实现的，不会阻塞当前的线程，具体如下所示：

    ```java
    void asyncSend() throws InterruptedException {
        Properties producerProp = producerProp(); // 加载配置属性
        Producer<String, String> producer = new KafkaProducer<>(producerProp);
        Message message;
    
        for (int i = 0; i < 5; ++i) {
            message = new Message(i, "NO_BLOCK_MSG_" + i);
    
            ProducerRecord<String, String> record = new ProducerRecord<>(
                TOPIC_NAME, PARTITION_TWO,
                String.valueOf(message.getId()), gson.toJson(message)
            );
            
            // 注册回调函数来处理发送之后的结果，避免阻塞当前的线程
            producer.send(record, (metadata, e) -> {
                if (e != null) {
                    log.error("异步发送消息失败，", e);
                    return;
                }
    
                if (metadata != null) {
                    log.info("[topic]={}, [position]={}, [offset]={}",
                             metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
        }
    }
    ```

    

<br />

### 消费者端

消费者端的配置如下所示：

```java
public Properties consumerProp() {
    Properties properties = new Properties();

    /*
            配置 Kafka 的 Broker 列表
         */
    properties.put(BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092,127.0.0.1:9093");

    /*
            配置消费组
         */
    properties.put(GROUP_ID_CONFIG, "xhliu-group1");

    /*
            Offset 的重置策略，对于新创建的一个消费组，offset 是不存在的，这里定义了如何对 offset 进行赋值消费
            latest：默认值，只消费自己启动之后发送到主题的消息
            earliest：第一次从头开始消费，之后按照 offset 的记录继续进行消费
         */
    properties.put(AUTO_OFFSET_RESET_CONFIG, "earliest");

    /*
            设置 Consumer 给 Broker 发送心跳的时间间隔
         */
    properties.put(HEARTBEAT_INTERVAL_MS_CONFIG, 1000);

    /*
            如果超过 10s 没有收到消费者的心跳，则将消费者踢出消费组，然后重新 rebalance，将分区分配给其它消费者
         */
    properties.put(SESSION_TIMEOUT_MS_CONFIG, 10*1000);

    /*
            一次 poll 最大拉取的消息的条数，具体需要根据消息的消费速度来设置
         */
    properties.put(MAX_POLL_RECORDS_CONFIG, 500);

    /*
            如果两次 poll 的时间间隔超过了 30s，那么 Kafka 就会认为 Consumer 的消费能力太弱，
            将它踢出消费组，再将分区分配给其它消费组
         */
    properties.put(MAX_POLL_INTERVAL_MS_CONFIG, 30*1000);

    /*
            设置 Key 和 Value 的反序列化实现类
         */
    properties.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    properties.put(VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

    // 配置结束
    return properties;
}
```

对于消费端来讲，由于 Kafka 的消费模型是通过轮询的方式来实现的，因此就不存在所谓的同步和异步获取消息的方式。但是在消费完成消息之后，消费者需要发送一个 Offset 到对应的 Topic，表示这个消息已经被当前的消费者消费了，消费组中下一个消费消息的消费者直接从这这个位置的偏移量开始消费，这种消费之后提交 Offset 有两种方式：自动提交和手动提交

- 自动提交的使用示例如下所示：

    ```java
    void autoCommitOffset() throws InterruptedException {
        Properties properties = consumerProp();
    
        // 设置是否是自动提交，默认为 true
        properties.put(ENABLE_AUTO_COMMIT_CONFIG, "true");
        //  自动提交 offset 的时间间隔
        properties.put(AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
    
        Consumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(Lists.newArrayList(TOPIC_NAME));
    
        // 指定当前的消费者在 TOPIC_NAME 上的 PARTITION_ONE 的分区上进行消费
        //        consumer.assign(Lists.newArrayList(new TopicPartition(TOPIC_NAME, PARTITION_ONE)));
        // 指定 consumer 从头开始消费
        //        consumer.seekToBeginning(Lists.newArrayList(new TopicPartition(TOPIC_NAME, PARTITION_ONE)));
        // 指定分区和 offset 进行消费
        //        consumer.seek(new TopicPartition(TOPIC_NAME, PARTITION_ONE), 10);
    
        ConsumerRecords<String, String> records;
        while (true) {
            /*
                    通过长轮询的方式拉取消息
                 */
            records = consumer.poll(Duration.ofMillis(1000));
    
            for (ConsumerRecord<String, String> record : records) {
                log.info("[topic]={}, [position]={}, [offset]={}, [key]={}, [value]={}",
                         record.topic(),record.partition(), record.offset(), record.key(), record.value());
            }
    
            Thread.sleep(10000);
        }
    }
    ```

    使用自动提交可能会导致消息的丢失，这是因为在消费者在消费消息的过程中，可能由于系统崩溃等原因，使得消费者未能完全消费这条消息，但是自动提交 Offset 的方式又将这条消息标记为了 “已消费”，Kafka 不支持重复消费，因此此时这个消费组就无法再消费这条已经丢失的消息

- 手动提交的示例如下所示：

    ```java
    void manualCommitOffset() throws InterruptedException {
        Properties properties = (Properties) consumerProp.clone();
    
        // 设计提交 Offset 为手动提交，只需要将允许自动提交设置为 false 即可
        properties.put(ENABLE_AUTO_COMMIT_CONFIG, "false");
        Consumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(Lists.newArrayList(TOPIC_NAME));
    
        ConsumerRecords<String, String> records;
        while (true) {
            /*
                    通过长轮询的方式拉取消息
                 */
            records = consumer.poll(Duration.ofMillis(1000));
    
            for (ConsumerRecord<String, String> record : records) {
                log.info("[topic]={}, [position]={}, [offset]={}, [key]={}, [value]={}",
                         record.topic(),record.partition(), record.offset(), record.key(), record.value());
            }
    
            if (records.count() > 0) {
                /*
                        手动同步提交 offset，当前线程会阻塞，知道 offset 提交成功
                     */
                consumer.commitSync();
    
                /*
                        通过异步的方式来完成 offset 的提交
                     */
                /*
                    consumer.commitAsync((offsets, e) -> {
                        log.error("异常 offset={}", gson.toJson(offsets));
                        if (e != null) {
                            log.error("提交 offset 发生异常，", e);
                        }
                    });
                    */
            }
    
            Thread.sleep(2000);
        }
    }
    ```

    

<br />

## 与 Spring Boot 的整合

如果使用的应用程序是使用 Spring Boot 的话，那么使用起来会比较方便，因为Spring Boot 已经将 Kafka 作为了可自动配置的 Bean，只需要加入对应的 `starter` 即可创建 `KafkaTemplate` 的 Bean 来直接向 Kafka 发送消息，在 Spring Boot 项目中添加如下的 `starter`：

```xml
<!-- 添加了这个依赖之后，就不用再天际 Kafka-Client 的依赖了，因为 spring-kafka 会自动引入这些依赖项 -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

在正式使用之前，同样的需要配置对应的 Kafka 的相关信息，以 `application.yml` 为例，生产者和消费者的配置如下：

```yaml
spring:
  kafka:
    producer:
      bootstrap-servers: 127.0.0.1:9092
      acks: 1
      retries: 3
      buffer-memory: 33554432
      # 消息的 key 和 value 的序列化实现类
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      batch-size: 16384
      properties:
        # 提交延时，当 Producer 积累的消息达到 batch-size 或者接收到消息 linger.ms 后，生产者就会将消息提交给 Kafka
        # linger.ms 等于0，表示每当接收到一条消息的时候，就提交给 Kafka，这个时候 batch-size 上面配置的值就无效了
        linger.ms: 10

    consumer:
      bootstrap-servers: 127.0.0.1:9092
      group-id: order
      enable-auto-commit: true # 是否自动提交 offset
      auto-commit-interval: 100 # 提交 offset 的延时，即接受到消息多久之后提交 offset
      # 当 Kafka 中没有初始 offset 或 offset 超出范围时，自动重置 offset
      # earliest 表示第一次从头开始消费，之后再按照 offset 的记录继续消费
      # latest（默认） 表示只消费自己启动之后收到的主题的信息
      auto-offset-reset: earliest
      max-poll-records: 200 # 批量消费的最大消息的数量
      # 消息的 key 和 value 的反序列化实现类，可以自定义来实现
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        # 会话超时时间，如果 Consumer 超过这个时间没有发送心跳，就会出发 rebalance 操作
        session.timeout.ms: 120000
        # Consumer 请求超时时间
        request.timeout.ms: 180000
        # Kafka 会自动完成对象的序列化和反序列化，下面的配置则是表示可以被序列化的对象所在的包，即“可信任的”
        spring:
          json:
            trusted:
              packages: org.xhliu.kafkaexample.vo
    listener:
      ack-mode: record
```

<br />

首先，定义消息对象实例对象类，如下所示：

```java
// 按照设计来讲，这里的消息应该是一个值对象，因此应该设计为 “不可变对象”
public class Message {
    private final int id;
    private final String body;

    // 由于 Spring 默认使用 Jackson 的方式来实现 Json 的序列化，因此这里配置 Jackson 使得其能够正常构建对象
    @JsonCreator
    public Message(
            @JsonProperty("id") int id,
            @JsonProperty("body") String body
    ) {
        this.id = id;
        this.body = body;
    }
    // 省略 Getter 方法和 toString() 方法
}
```





### 生产者端

直接使用 `kafkaTemplate` 来发送消息即可

- 以阻塞的方式发送消息

    ```java
    @Resource
    private KafkaTemplate<String, Message> kafkaTemplate;
    
    // Spring 会自动引入 Jackson，因此在这里直接注入即可
    @Resource
    private ObjectMapper mapper;
    
    @GetMapping(path = "blockProducer")
    public String blockProducer() throws Throwable {
        List<Message> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Message message = new Message(i, "BLOCKING_MSG_SPRINGBOOT_" + i);
            ListenableFuture<SendResult<String, Message>> future =
                kafkaTemplate.send(TOPIC_NAME, PARTITION_ONE, String.valueOf(message.getId()), message);
    
            SendResult<String, Message> result = future.get();
            RecordMetadata metadata = result.getRecordMetadata();
            log.info("---BLOCKING_MSG_SPRINGBOOT--- [topic]={}, [partition]={}, [offset]={}",
                     metadata.topic(), metadata.partition(), metadata.offset());
            list.add(message);
        }
    
        return mapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(list);
    }
    ```

    

- 以异步的形式发送消息

    ```java
    @GetMapping(path = "noBlockProducer")
    public String noBlockProducer() throws Throwable {
        List<Message> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Message message = new Message(i, "NO_BLOCKING_MSG_SPRINGBOOT_" + i);
            ListenableFuture<SendResult<String, Message>> future =
                kafkaTemplate.send(TOPIC_NAME, PARTITION_ONE, String.valueOf(message.getId()), message);
    
            future.addCallback(new ListenableFutureCallback<>() {
                @Override
                public void onFailure(Throwable ex) {
                    log.error("消息发送失败! ",  ex);
                }
    
                @Override
                public void onSuccess(SendResult<String, Message> result) {
                    RecordMetadata metadata = result.getRecordMetadata();
                    log.info("---BLOCKING_MSG_SPRINGBOOT--- [topic]={}, [partition]={}, [offset]={}",
                             metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
    
            list.add(message);
        }
    
        return mapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(list);
    }
    ```

<br />

### 消费者端

在配置文件中已经配置了是否需要自动提交 Offset， Spring Boot 通过添加 `@KafkaListener` 来自动实现消费者对于消息的消费，具体使用如下所示：

```java
@KafkaListener(topics = {TOPIC_NAME}, groupId = CONSUMER_GROUP)
public void listenGroup(ConsumerRecord<String, Message> record) {
    log.info("[topic]={}, [position]={}, [offset]={}, [key]={}, [value]={}",
             record.topic(),record.partition(), record.offset(), record.key(), record.value());
    
    // ack.acknowledge(); 手动提交 Offset，需要enable-auto-commit: false才可以
}
```

如果想要配置消费组来消费多个 Topic 消息，可以像下面这么做：

```java
@KafkaListener(
    groupId = "xhliu-group1", // 消费组名称
    concurrency = "3",  // 每个消费组中创建 3 个 Consumer
    topicPartitions = {
        // 针对主题 xhliu，要消费分区 0 和 分区 1
        @TopicPartition(topic = "xhliu", partitions = {"0", "1"}), 
        // 针对主题 order，消费分区 0 和分区1，同时分区 1 从 offset=100 开始消费
        @TopicPartition(
            topic = "order", partitions = {"0"},
            partitionOffsets = @PartitionOffset(partition = "1", initialOffset = "100")
        )
    }
)
public void listenGroupPro(ConsumerRecord<String, Message> record) {
    Message message = record.value();

    log.info("msgId={}, content={}", message.getId(), message.getBody());
}
```

<br />

参考：

<sup>[1]</sup> https://mp.weixin.qq.com/s/SdR9wey7hUZqYq6K8FyNZg