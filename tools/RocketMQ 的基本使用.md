# RocketMQ 的基本使用

> **RocketMQ**[[1\]](https://zh.wikipedia.org/wiki/Apache_RocketMQ#cite_note-1)是一个分布式消息和流数据平台，具有低延迟、高性能、高可靠性、万亿级容量和灵活的可扩展性。RocketMQ是2012年阿里巴巴开源的第三代分布式消息中间件，2016年11月21日，阿里巴巴向Apache软件基金会捐赠了RocketMQ；第二年2月20日，Apache软件基金会宣布Apache RocketMQ成为顶级项目。

RocketMQ 出现的原因是由于旧有的 ActiveMQ 无法满足业务要求，因此开发的一个能够满足业务需求的消息中间件。RocketMQ 的优点有很多，比如：高性能、高可靠等，但是由于某些原因，RocketMQ 可能并不像别的 MQ 那么的对开发人员友好

<br />

## 安装

首先，在官网上下载对应的源代码文件或者二进制文件：https://dlcdn.apache.org/rocketmq/，建议直接下载二进制文件，如果有特别需要的话，也可以下载对应的源文件，然后使用 `Maven` 构建即可。

据我个人的使用情况来看，下载源文件可能不是一个明智的选择，尽管官方的文档给出的是只要 JDK 1.8 或者以上的版本就可以，但是我在构建的过程中发现 RocketMQ 严重依赖于 JDK 1.8 特有的 API，因此我个人建议直接下载已经构建好的 jar

下载完成或者构建完成之后，应该是可以看到以下几个文件夹的：

![2021-12-25 11-03-10 的屏幕截图.png](https://s2.loli.net/2021/12/25/MIfV5GRBt7pxKiF.png)

其中，`benchmark` 文件夹包括对于 RocketMQ 的性能测试脚本；`bin` 目录下包含了一些启动 RocketMQ 以及配置相关的一些脚本；`conf` 目录下包含了所有的配置文件；`lib` 目录下则包含了所有的构建好的 `jar` 文件



<br />

## 启动 NameServer

如果没有指定配置文件的话，那么默认就是本地的 `9876` 端口，需要自定义相关属性的话，可以在 `conf` 目录下创建一个 `namesrv.properties` 的文件，定义需要的内容：

```properties
# 将 namesrv 的监听端口改为 8848
listenPort=8848
```

然后再运行启动 NamerServer 的脚本，具体如下：

```sh
# 当前的工作目录位于 RocketMQ 的基本工作目录，-c 选项表示加载对应的配置文件
./bin/mqnamesrv -c conf/namesrv.properties
```

此时启动 NameServer 可能会有问题，这个可能是由于 JDK 的版本太高，但是由于 RocketMQ 的默认启动是通过 JDK 1.8 特有的一种方式来启动的，因此可能会出现类似于以下这样的问题：

![2021-12-25 14-38-25 的屏幕截图.png](https://s2.loli.net/2021/12/25/2MOAyv6Y5BclFSa.png)

这是由于自从 JDK 9 开始，`-Djava.ext.dirs` 这个选项已经被废弃了，因此出现了这样的问题

解决方式：

手动编辑 `bin/runserver.sh` 文件，将 `-Djava.ext.dirs` 这个启动选项给去掉，但是这样又会导致一个新的问题，由于 `ext` 的类加载级别是要一般的应用级类加载器级别要高，因此此时是没有办法加载到实际运行的主类的，此时将对应的 `jar` 加入到  JVM 启动时的类中即可解决这个问题。

最终 `bin/runserver.sh` 修改后如下所示：

```shell
# 省略一部分不太重要的内容

[ ! -e "$JAVA_HOME/bin/java" ] && JAVA_HOME=$HOME/jdk/java
[ ! -e "$JAVA_HOME/bin/java" ] && JAVA_HOME=/usr/java
[ ! -e "$JAVA_HOME/bin/java" ] && error_exit "Please set the JAVA_HOME variable in your environment, We need java(x64)!"

export JAVA_HOME
export JAVA="$JAVA_HOME/bin/java"
export BASE_DIR=$(dirname $0)/..

# 这里是一次性将 RocketMQ 中 lib/ 目录下需要的所有的 jar 都放入 JVM 中，这样就能够成功找到对应的启动主类了
export CLASSPATH=.:${BASE_DIR}/lib/*:${BASE_DIR}/conf:${CLASSPATH}

# 中间一大段无需修改

choose_gc_log_directory
choose_gc_options
JAVA_OPT="${JAVA_OPT} -XX:-OmitStackTraceInFastThrow"
JAVA_OPT="${JAVA_OPT} -XX:-UseLargePages"

# -Djava.ext.dirs 这个选项需要被废除，这里直接将其注释掉
#JAVA_OPT="${JAVA_OPT} -Djava.ext.dirs=${JAVA_HOME}/jre/lib/ext:${BASE_DIR}/lib:${JAVA_HOME}/lib/ext"
#JAVA_OPT="${JAVA_OPT} -Xdebug -Xrunjdwp:transport=dt_socket,address=9555,server=y,suspend=n"
JAVA_OPT="${JAVA_OPT} ${JAVA_OPT_EXT}"
JAVA_OPT="${JAVA_OPT} -cp ${CLASSPATH}" # 添加 --classpath

# 这里是真正启动 JVM 的地方
$JAVA ${JAVA_OPT} $@
```

现在，再执行启动选项，就可以成功运行 RocketMQ 的 NameServer 了，启动时看起来像下面这样：

![2021-12-25 14-54-34 的屏幕截图.png](https://s2.loli.net/2021/12/25/4By9AHVMDNkPESw.png)



<br />

## 创建 Broker

首先，创建一个自己的 `Broker` 配置文件，具体内容如下所示：

```properties
# Broker 对应的监听端口
listenPort=12345
# Broker 的节点名
brokerClusterName = DefaultCluster
# Broker 的名字
brokerName = broker-a
# Broker 的 id，0 表示主节点，非 0 表示 从节点
brokerId = 0
# 不是特别重要的属性，表示在什么时候执行删除的操作，这里 04 表示凌晨 4 点
deleteWhen = 04
# 文件的保留时间，这里定义为 48 小时
fileReservedTime = 48
# Broker 所属类型，SYNC_MASTER 表示同步主节点，ASYNC_MASTER 表示异步主节点，SLAVE 表示从节点
brokerRole = SYNC_MASTER
# 刷盘策略，只有两种刷盘策略：SYNC_FLUSH 同步刷盘和 ASYNC_FLUSH 异步刷盘（可能有数据丢失）
flushDiskType = SYNC_FLUSH
```

然后启动 Broker，在 RocketMQ 的安装目录下执行对应的脚本即可，具体如下所示：

```sh
# -c 表示加载对应的配置文件，-n 表示 broker 连接的 NameServer 地址，之前已经将端口改为了 8848
./bin/mqbroker -n 127.0.0.1:8848 -c conf/master-broker.properties
```

如果使用的是高版本的 JDK，那么不出意外的话， 执行上面的内容依旧是会出现问题的（吐槽一句：脚本内容着实有待完善）。将 `./bin/runserver.sh` 按照下面的步骤进行修改：

1. 首先，将 `-Djava.ext.dir` 这个选项去掉
2. 然后移除一些已经废除的 GC 选项。如：`+XX:PrintAdaptiveSizePolicy`、`-XX:+PrintGCDateStamps` 等
3. 修改 GC 的日志打印选项
4. 修改加载的 `CLASSPATH`

在我的电脑上，安装的 JDK 版本为 openJDK-11.02，最终修改后的 `./bin/runserver.sh` 的内容如下所示：

```sh
# 省去一部分不太重要的内容

export JAVA_HOME
export JAVA="$JAVA_HOME/bin/java"
export BASE_DIR=$(dirname $0)/..
# 修改 CLASSPTH，使得能够加载对应的主类
export CLASSPATH=.:${BASE_DIR}/lib/*:${BASE_DIR}/conf:${CLASSPATH}

# 省去一段不太重要的内容。。。

# 默认情况下会设置 最大堆大小为 8 GB，如果是在自己的电脑上，建议将这两个值设置地小一点，否则可能会导致机器崩溃
JAVA_OPT="${JAVA_OPT} -server -Xms2g -Xmx2g"
# 一堆 GC 相关的属性配置，G1 收集器（如果可用的话）是一个很好的选择
JAVA_OPT="${JAVA_OPT} -XX:+UseG1GC -XX:G1HeapRegionSize=16m -XX:G1ReservePercent=25 -XX:InitiatingHeapOccupancyPercent=30 -XX:SoftRefLRUPolicyMSPerMB=0"

# 这部分是需要修改的地方，将 -Xloggc:改成-Xlog:gc: （JDK 11 上如此），然后再删除掉一些废弃的选项，最后如下所示
# JAVA_OPT="${JAVA_OPT} -verbose:gc -Xloggc:${GC_LOG_DIR}/rmq_broker_gc_%p_%t.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCApplicationStoppedTime -XX:+PrintAdaptiveSizePolicy"
JAVA_OPT="${JAVA_OPT} -verbose:gc -Xlog:gc:${GC_LOG_DIR}/rmq_broker_gc_%p_%t.log"

# 这一行整个启动选项都被废弃了
# JAVA_OPT="${JAVA_OPT} -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=5 -XX:GCLogFileSize=30m"

#JAVA_OPT="${JAVA_OPT} -XX:GCLogFileSize=30m"
JAVA_OPT="${JAVA_OPT} -XX:-OmitStackTraceInFastThrow"
JAVA_OPT="${JAVA_OPT} -XX:+AlwaysPreTouch"
JAVA_OPT="${JAVA_OPT} -XX:MaxDirectMemorySize=15g"
JAVA_OPT="${JAVA_OPT} -XX:-UseLargePages -XX:-UseBiasedLocking"

# 注意将 -Djava.ext.dirs 这个启动选项去掉
#JAVA_OPT="${JAVA_OPT} -Djava.ext.dirs=${JAVA_HOME}/jre/lib/ext:${BASE_DIR}/lib:${JAVA_HOME}/lib/ext"
#JAVA_OPT="${JAVA_OPT} -Xdebug -Xrunjdwp:transport=dt_socket,address=9555,server=y,suspend=n"
JAVA_OPT="${JAVA_OPT} ${JAVA_OPT_EXT}"
JAVA_OPT="${JAVA_OPT} -cp ${CLASSPATH}"

# 省略一部分不太重要的内容

```

现在，再次启动自定义的 Broker，启动成功的结果会类似下图：

![2021-12-25 15-59-17 的屏幕截图.png](https://s2.loli.net/2021/12/25/jfc9SOhCtKvWle4.png)

<br />



## 生产者发送消息

在发送和接受消息之前，需要添加 RocketMQ 对应的客户端依赖项：

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-client</artifactId>
    <version>4.9.2</version> <!-- 对应具体的 RocketMQ 的版本 -->
</dependency>
```

- 以同步阻塞的方式发送消息

  ```java
  mport org.apache.rocketmq.client.producer.DefaultMQProducer;
  import org.apache.rocketmq.client.producer.SendResult;
  import org.apache.rocketmq.common.message.Message;
  
  import java.nio.charset.StandardCharsets;
  import java.util.Scanner;
  
  public class SyncProducer {
      public static void main(String[] args) throws Throwable {
          // 生产组为 lxh_producer
          DefaultMQProducer producer = new DefaultMQProducer("lxh_producer");
          // 设置 NameServer
          producer.setNamesrvAddr("127.0.0.1:9876");
          producer.start();
          Scanner sc = new Scanner(System.in);
          String line;
          // 读取客户端输入行，然后再发送
          while ((line = sc.nextLine()).length() > 0) {
              // 聚合成对ing的 Message 
              Message msg = new Message(
                  "TopicTest", // 发送消息到的目的 Topic，消费端可以按照这个进行消息的接收
                  "TagA", // 该消息的 Tag，客户端可以对此进行过滤
                  line.getBytes(StandardCharsets.UTF_8) // 将内容编码成字节流
              );
              // 以同步的方式发送消息，只有在消息发送完成之后才能进行后续的动作
              SendResult result = producer.send(msg);
              System.out.println(result);
          }
          producer.shutdown();
      }
  }
  ```

  

- 以异步的方式发送消息

  ```java
  package org.xhliu.rocketmqexample.producer;
  
  import org.apache.rocketmq.client.producer.DefaultMQProducer;
  import org.apache.rocketmq.client.producer.SendCallback;
  import org.apache.rocketmq.client.producer.SendResult;
  import org.apache.rocketmq.common.message.Message;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  
  import java.nio.charset.StandardCharsets;
  import java.util.Scanner;
  
  public class AsyncProducer {
      static final Logger log = LoggerFactory.getLogger(AsyncProducer.class);
  
      public static void main(String[] args) throws Throwable {
          DefaultMQProducer producer = new DefaultMQProducer("lxh_producer");
          producer.setNamesrvAddr("127.0.0.1:8848");
          producer.start();
  
          Scanner sc = new Scanner(System.in);
          String line;
          while ((line = sc.nextLine()).length() > 0) {
              Message msg = new Message(
                  "TopicTest",
                  "TagA",
                  line.getBytes(StandardCharsets.UTF_8)
              );
              
              // 以异步的方式发送消息，通过注册回调函数的方式来对返回结果进行处理
              producer.send(msg, new SendCallback() {
                  @Override
                  public void onSuccess(SendResult sendResult) {
                      log.info("send result: " + sendResult.toString());
                  }
  
                  @Override
                  public void onException(Throwable throwable) {
                      log.error(throwable.getMessage());
                      throwable.printStackTrace();
                  }
              });
          }
  
          producer.shutdown();
      }
  }
  
  ```

  

- 单向发送消息

  ```java
  import org.apache.rocketmq.client.producer.DefaultMQProducer;
  import org.apache.rocketmq.common.message.Message;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  
  import java.nio.charset.StandardCharsets;
  
  public class OneWayProducer {
      private final static Logger log = LoggerFactory.getLogger(OneWayProducer.class);
  
      public static void main(String[] args)  throws Throwable{
          DefaultMQProducer producer = new DefaultMQProducer("lxh_producer");
          producer.setNamesrvAddr("127.0.0.1:8848");
          producer.start();
  
          for (int i = 0; i < 10; i++) {
              // 创建消息，并指定Topic，Tag和消息体
              Message msg = new Message(
                  "TopicTest" /* Topic */,
                  "TagA" /* Tag */,
                  ("Hello RocketMQ " + i).getBytes(StandardCharsets.UTF_8) /* Message body */
              );
              // 发送单向消息，没有任何返回结果
              producer.sendOneway(msg);
  
          }
          producer.shutdown();
      }
  }
  
  ```

  



<br />

## 消费者消费消息

消息的消费只能是阻塞的，具体的示例代码如下所示：

```java
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class SyncConsumer {
    public static void main(String[] args) throws Throwable {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("lxh_consumer");
        consumer.setNamesrvAddr("127.0.0.1:8848");
        consumer.subscribe(
            "TopicTest",
            "*"
        );

        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(
                List<MessageExt> list,
                ConsumeConcurrentlyContext context
            ) {
                for (MessageExt ext : list) {
                    System.out.println("Get Body: " + new String(ext.getBody(), StandardCharsets.UTF_8));
                }
                System.out.printf("%s Receive New Messages: %s %n", Thread.currentThread().getName(), list);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        consumer.start();
        System.out.println("Consumer Start.....");
    }
}
```





参考：

<sup>[1]</sup> https://github.com/apache/rocketmq/blob/master/docs/cn/RocketMQ_Example.md