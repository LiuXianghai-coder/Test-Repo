# RocketMQ 简介

<br />

## 消息中间件的作用

采用消息中间件的原因：

- 有时并发量并不是一直那么大，只是瞬时间有那么大的流量导致系统在这个时间区间内无法负载这么大的流量，导致系统崩溃。这种情况下，可以采用消息中间件来暂时存储这些消息，在之后的时间区域内再进行处理，这也被成为 “削峰填谷”
- 随着微服务的流行，需要对系统的各个部分进行拆分，以提高系统各个功能个独立性，但是现在有的服务又依赖于其它的服务，这种情况下就需要使用消息中间件来维护这些依赖关系，从而能够完成系统的解藕工作
- 系统性能的需要，一台计算机的处理能力是有限的，通过消息中间件来拆分系统模块能够提高系统的性能
- 可以将一部分已经处理的流量放入消息中间件，可以很好地体现真实的使用场景



<br />

## RocketMQ 的特点

常见的消息中间件有：ActiveMQ、RabbitMQ、Kafka、RocketMQ 等，而 RocketMQ 主要有以下几个特点：

1. 支持事务型消息（消息发送和 DB 操作保持两方的一致性，RabbitMQ 和 Kafka 没有这个特点）
2. 支持结合 RocketMQ 的多个系统之间数据的一致性（多方事务，二方事务是前提）
3. 支持延时消息（RabbitMQ 和 Kafka 不支持）
4. 支持指定次数和时间间隔的失败消息重新发送（Kafka 不支持，RabbitMQ 需要手动确认）
5. 支持 Consumer 端的 tag 过滤，减少不必要的网络传输（RabbitMQ 和 Kafka 均不支持）
6. 支持重复消费（RabbitMQ 不支持，Kafka 支持）



<br />

## RocketMQ 架构

### 技术架构<sup>[1]</sup>

![img](https://github.com/apache/rocketmq/raw/master/docs/cn/image/rocketmq_architecture_1.png)

RocketMQ 架构上主要分为四个部分，对应图中的部分：

- Producer：消息发布的角色，支持分布式集群方式部署。

    Producer 通过 MQ 的负载均衡模块选择相应的 Broker 集群队列进行消息投递，投递的过程支持快速失败并且低延迟

    Producer 与 Name Server 集群中的其中一个节点（随机选择）建立长连接，定期从 Name Server 获取所有 TOPIC 路由信息，并向提供 TOPIC 服务的 Master Broker 建立长连接，并且定时向 Master 发送心跳。Producer 完全无状态，可集群部署。

    Producer 每隔 30s （由 `ClientConfig` 的 `pollNameServerInterval` ）从 NameServer 获取所有 Topic 队列的最新情况，这意味着如果 Broker 不可用，Producer 最多 30s 内能够感知，在此期间发往 Broker 的所有消息都会失败。Producer 每隔 30s（由 `ClientConfig` 中的 `heartBeatBrokerInterval` 决定）向所关联的 Broker 发送心跳，Broker 每隔 10s  扫描存活的连接，如果 Broker 在 2 分钟内没有收到心跳数据，则关闭与 Producer 的连接

- Consumer：消息消费的角色，支持分布式集群方式部署。

    支持两种模式：pull 模式和 push 模式。同时也支持以集群方式和广播的方式进行消费，提供实时消息订阅机制，可以满足大部分的要求

    Consumer 与 Name Server 集群中的其中一个节点（随机选择）建立长连接，定期从 Name Server 获取 TOPIC 的路由信息，并向提供 TOPIC 的 Broker 建立长连接，且定时向 Broker 发送心跳。

    Consumer 既可以从 Master Broker 订阅消息，也可以从 Slave Broker 订阅消息，订阅规则由 Broker 配置决定。

    Consumer 每隔 30s （由 `ClientConfig` 中 `heartBeatBrokerInterval` 决定）向所关联的 Broker 发送心跳，Broker 每隔 10s 扫描所有存活的连接，如果某个连接 2 分钟内没有发送心跳数据，则关闭该连接，并向该 `ConsumerGroup` 的所有 Consumer 发送 通知，Group 内的 Consumer 重新分配额队列，然后继续消费。当 Consumer 得到 Master Broker 宕机的通知后，将会转向 Slave Broker 消费，Slave Broker 不能保证 Master Broker 的所有消息都会同步过来，因此会有少量的消息丢失。但是一旦 Master Broker 恢复过来之后，未同步的消息最终会被消费。

    消费者队列是在消费者连接之后才有的，消费者标识为 {IP}@{ConsumerGroup}{TOPIC}{tag}，任何一个元素不同都被认为是不同的消费端，每个消费端会拥有自己的一份消费队列（默认是 Broker 队列数量 * Broker 数量）

- Broker 

  Broker 包含了以下几个重要的模块

  - Remoting Module：负责整个 Broker 的实体，负责处理来自 clients 的请求
  - Client Manager：负责管理客户端（Producer/Consumer）和维护 Consumer 的 Topic 的订阅信息
  - Store Service：高可用服务，提供 Master Broker 和 Slave Broker 之间的数据同步功能
  - Index Service：根据特定的 Message Key 对投递到 Broker 的消息进行索引服务，以提供消息的快速查询

  具体如下图所示：

  <img src="https://github.com/apache/rocketmq/raw/master/docs/cn/image/rocketmq_architecture_2.png" alt="img" style="zoom:150%;" />

  Broker 通过提供轻量级的 Topic 和 Queue 机制来处理消息存储，它们支持 Push 和 Pull 模型，包含容错机制（2副本或3副本），并且提供强大的峰值填充和按照初始时间顺序积累数以千亿级别的消息的能力。除此之外，Brokers 提供了灾难恢复、丰富的指标统计和报警机制，这些都是传统消息传递系统所缺乏的。

  ​	Broker 分为Master 和 Slave，一个 Master 可以对应多个 Slave，但是一个 Slave 只能对应一个 Master， Master 和 Slave 的对应关系通过指定相同的 Broker Id， Broker Id 为 0 表示该  Broker 为 Master，非 0 表示该 Broker 为 Slave。

  ​	每个 Broker 与 Name Server 集群中的所有节点建立长连接，每隔 30s 注册 TOPIC 信息到所有的 Name Server。Name Server 每隔 10s 扫描所有存活的 Broker，如果 Name Server 超过两分钟没有收到来自 Broker 的心跳，则 Name Server 断开与 Broker 的的连接 

  ​	Broker 负责消息的存储和交付、消息查询、高可用保障等

  

- NameServer： NameServer 是一个简单的 Topic 路由注册中心，支持 Broker 的动态注册与发现。

  该部分的主要有两个功能：一是管理 Broker，NameServer 接受来自 Broker 集群的注册信息并且保存下来作为路由信息的基本数据，提供心跳检测，检查 Broker 是否还存活；二是对路由信息的管理，每个 NameServer 保存关于 Broker 集群的整个路由信息和用于客户端查询的队列信息，这样 Producer 和 Consumer 就可以通过 NameServer 知道整个 Broker 集群的路由信息，从而进行消息的投递和消费。

  NameServer 通常以集群的方式进行部署，各个实例之间相互独立。

  Broker 向每一台 NameServer 注册自己的路由信息，因此每个 NameServer 实例上面都保存着一份完整的路由信息。如果此时某个 NameServer 由于某种原因下线了，Broker 依旧可以向其它的 NameServer 同步其路由信息，Producer 和 Consumer 依旧可以感知 Broker 的路由信息

<br />

### 部署架构<sup>[1]</sup>

如下图所示：

<img src="https://github.com/apache/rocketmq/raw/master/docs/cn/image/rocketmq_architecture_3.png" alt="img" style="zoom:150%;" />

RocketMQ 的网络部署特点：

- NameServer 是一个几乎无状态节点，可以集群部署，节点之间没有任何信息同步
- Broker 部署相对来讲较复杂，Broker 分为 Master 和 Slave，一个 Master 可以对应多个 Slave，但是一个 Slave 只能对应一个 Master，Master 和 Slave 之间的对应关系通过指定相同的 BrokerName 但是不同的 BrokerId 来定义，BrokerId 为  0 则表示这个 Broker 是一个 Master Broker，非 0 则表示是一个 Slave Broker。Master 也可以部署多个，每个 Broker 和 NameServer 集群中的所有节点建立长连接，定时注册 Topic 信息到所有的 NameServer
- Producer 和 NameServer 集群中的一个节点（随机选择）建立长连接，定期从 NameServer 获取 Topic 路由信息，并向提供 Topic 服务的 Master 建立长连接，且定时向 Master 发送心跳。Producer 完全无状态，可以集群部署
- Consumer 和 NameServer 集群中的一个节点（随机选择）建立长连接，定期从 NameServer 获取 Topic 路由信息，并向提供 Topic 服务的 Master、Slave 建立长连接，且定时向 Master、Slave 发送心跳。Consumer 既可以从 Master 订阅消息，也可以从 Slave 订阅消息，消费者在向 Master 拉取消息时，Master 会根据拉取偏移量与最大偏移量的距离（判断是否读老消息），以及从服务器是否可度等因素建议下一次是从 Master 还是 Slave 拉取

<br />

结合部署架构图，启动 RocketMQ 集群工作的流程如下：

1. 启动 NameServer，NameServer 启动之后监听端口，等待 Broker、Producer、Consumer 连接，相当于一个路由控制中心
2. Broker 启动，跟所有的 NameServer 保持长连接，定时发送心跳包。心跳包中包含了当前 Broker 信息（IP + 端口等）以及存储的 Topic 信息。注册成功之后，NameServer 中就有 Topic 和 Broker 的映射关系
3. 收发消息之前，首先创建 Topic，创建 Topic 时需要指定该 Topic 要存储在哪些 Broker 上，也可以在发送消息时自动创建 Topic
4. Producer 发送消息，启动时首先跟 NameServer 集群中的一个建立长连接，并从 NameServer 中获取当前发送的 Topic 存储在哪些 Broker 上，轮询从队列列表中选择一个队列，然后与队列所在的 Broker 建立长连接从而向 Broker 发送消息
5. Consumer 和 Producer 类似，跟其中一台 NameServer 建立长连接，获取当前订阅 Topic 存在于哪些 Broker 上，然后直接和 Broker 建立连接通信，开始消费消息



<br />

## 关键特性与实现原理

<br />

### 关键特性<sup>[2]</sup>

- 顺序消费

  顺序消费指的是消费一类消息时，能够按照发送的顺序来进行消费。例如：如果一个订单产生了三条消息，分别为：订单创建、订单付款、订单完成，消费这类消息时必须按照这个顺序来消费才有意义。RocketMQ 可以严格地保证消息有序

<<<<<<< Updated upstream
  顺序消费分为全局顺序消费和分区顺序消费，全局顺序消费是指某个 Topic 下的所有消息都要保证顺序；部分顺序消费只要保证每一组消息被顺序消费即可

  - 全局顺序消费：对于一个指定的 Topic，所有消息严格按照先进先出的顺序进行发布和消费。适用场景：性能要求不高、所有消息严格按照 FIFO 的原则进行消息发布的消费的场景
  - 分区顺序消费：对于指定的一个 Topic，所有消息根据 sharding key 进行分区。同一个分区内的消息严格按照 FIFO 的顺序进行发布和消费。sharding key 是顺序消息中用来区分不同分区的关键字段，和普通消息的 key 是完全不同的概念。使用场景：性能要求高、以 sharding key 作为分区字段，在同一个区块中严格按照 FIFO 原则进行消息发布和消费的场景
=======

>>>>>>> Stashed changes

- 消息过滤

    RocketMQ 的消费者可以根据 Tag 进行消息过滤，也支持自定义属性过滤。消息过滤在 Broker 端实现，这样做的好处是减少了对于 Consumer 无用的消息传输，但是这样做会导致 Broker 的负担加重，并且实现起来较为复杂

- 消息的可靠性

    在以下几种异常情况下，RocketMQ 都能保证消息极大的可靠性：Broker 非正常关闭、Broker 异常宕机、操作系统宕机、机器断电，但是能够立即恢复断电。具体的可靠性取决于刷盘策略

    当机器的关键设备损坏，在这种情况下在这个单点上的消息就会丢失。RocketMQ 在这种情况下，同于异步复制的方式，可以保证 99% 的消息不丢失；通过同步双写技术可以完全避免单点问题，同步双写会影响到性能，适合对消息可靠性要求极高的应用场景

- 事务消息

    RocketMQ 的事务消息是指应用本地事务和发送消息操作可以被定义到全局事务中，要么同时成功，要么同时失败，RocketMQ 的事务消息提供类似 X/Open XA 的分布式事务功能，通过事务消息能够达到分布式事务的最终一致

- 定时消息

    定时消息（延迟队列）是指消息发送到 broker 之后，不会被立即消费，等待特定的时间投递给真正的 Topic。Broker 的默认配置项 `messageDelayLevel`，默认值为 “1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h” 18 个 level。

    `messageDelayLevel` 是 Broker 的属性，不针对某个 Topic，发送消息时，设置 `delayLevel` 即可：`message.setDelayLevel(level)`。level 存在以下三种情况：

    - $level == 0$：消息为非延迟消息
    - $1 \leq level \leq maxLevel$：消息延迟特定时间，例如 $level == 1$ ，延迟 1s
    - $level > maxLevel$：则 $level == maxLevel$，延迟 2h

    定时消息会存储在名为 `SCHEDULE_TOPIC_XXXX` 的 Topic 中，并根据 `delayTimeLevel` 存入特定的 `queue`，`queue = delayTimeLevel - 1` 表示一个 `queue` 只会存储相同延迟的消息，保证具有相同发送延迟的消息能够顺序消费，Broker 也会调度地消费 `SCHEDULE_TOPIC_XXXX`，将消息写入真实的 Topic

    需要注意的是，定时消息会在第一次写入和调度写入真实 Topic 时都会计数，因此发送数量、TPS 都会变高

- 消息重试

    Consumer 消费消息失败之后，需要提供一种重试机制，使得消息再被消费一次。Consumer 消费消息失败通常有以下几种情况：

    - 由于消息本身的原因，例如反序列化失败，消息数据本身无法被处理，这种错误通常需要跳过这条消息，再消费其它消息。在这种情况下，这种消息即使立刻重试消费，很大概率也不会成功的。因此在这种情况下最好提供一种重试机制，即过了 10 s 之后再重试
    - 由于依赖的下游应用服务不可用，例如 DB 访问的服务不可用，在这种情况下，即使跳过这条消息，消费其它的消息同样也会出现错误。最好的解决方案就是使得应用睡眠 30 s，再消费下一条消息，这样可以降低 Broker 的压力

    RocketMQ 通过对每个消费组都设置一个名为 `%RETRY%+consumerGroup` 的重试队列（该重试队列针对消费组，而不是针对 Topic）用于暂时保存由于各种异常而导致 Consumer 端无法消费的消息。

    考虑到异常恢复起来需要一些时间，会为重试队列设置多个重试级别，每个重试级别都有与之对应的重新投递延时，重试次数越多投递延时就越大。

    RocketMQ对于重试消息的处理是先保存至 Topic 名称为 `SCHEDULE_TOPIC_XXXX` 的延迟队列中，后台定时任务按照对应的时间进行Delay后重新保存至 `%RETRY%+consumerGroup` 的重试队列中。

- 消息重投

    生产者在发送消息时，同步消息失败会重投，异步消息有重试，`oneway` 没有任何保证。消息重投保证消息尽可能发送成功、不丢失，但可能会造成消息重复，消息重复在RocketMQ中是无法避免的问题。

    消息重复在一般情况下不会发生，当出现消息量大、网络抖动，消息重复就会是大概率事件。另外，生产者主动重发、consumer负载变化也会导致重复消息。

    如下方法可以设置消息重试策略：

    - `retryTimesWhenSendFailed`：同步发送失败重投次数，默认为 2，因此生产者会最多尝试发送`retryTimesWhenSendFailed + 1`次。不会选择上次失败的 Broker，尝试向其他 Broker 发送，最大程度保证消息不丢。超过重投次数，抛出异常，由客户端保证消息不丢。当出现 `RemotingException`、`MQClientException` 和部分 `MQBrokerException` 时会重投。
    - `retryTimesWhenSendAsyncFailed`：异步发送失败重试次数，异步重试不会选择其他broker，仅在同一个broker上做重试，不保证消息不丢。
    - `retryAnotherBrokerWhenNotStoreOK`：消息刷盘（主或备）超时或 Slave 不可用（返回状态非`SEND_OK`），是否尝试发送到其他 Broker，默认 `false`

- 流量控制

    生产者流控，因为 Broker 处理能力达到瓶颈；消费者流控，因为消费能力达到瓶颈。

    生产者流控：

    - `commitLog` 文件被锁时间超过 `osPageCacheBusyTimeOutMills` 时，参数默认为 1000ms，返回流控。
    - 如果开启 `transientStorePoolEnable == true`，且 Broker 为异步刷盘的主机，且 `transientStorePool` 中资源不足，拒绝当前 `send` 请求，返回流控。
    - Broker 每隔 10ms 检查 `send` 请求队列头部请求的等待时间，如果超过 `waitTimeMillsInSendQueue`，默认 200ms，拒绝当前 `send` 请求，返回流控。
    - Broker 通过拒绝 `send` 请求方式实现流量控制

    注意，生产者流控，不会尝试消息重投。

    <br />

    消费者流控：

    - 消费者本地缓存消息数超过 `pullThresholdForQueue` 时，默认 1000。
    - 消费者本地缓存消息大小超过 `pullThresholdSizeForQueue` 时，默认 100MB。
    - 消费者本地缓存消息跨度超过 `consumeConcurrentlyMaxSpan` 时，默认 2000。

    消费者流控的结果是降低拉取频率。

- 死信队列

    死信队列用于处理无法被正常消费的消息。当一条消息初次消费失败，消息队列会自动进行消息重试；达到最大重试次数后，若消费依然失败，则表明消费者在正常情况下无法正确地消费该消息，此时，消息队列 不会立刻将消息丢弃，而是将其发送到该消费者对应的特殊队列中。

    RocketMQ 将这种正常情况下无法被消费的消息称为死信消息（Dead-Letter Message），将存储死信消息的特殊队列称为死信队列（Dead-Letter Queue）。在 RocketMQ 中，可以通过使用 console 控制台对死信队列中的消息进行重发来使得消费者实例再次进行消费。

    

<br />

### 实现原理

- 顺序消费

    RocketMQ 从业务的层面来保证消息的顺序，而不仅仅依靠消息系统。

    RocketMQ 通过轮询所有队列的方式来确定消息被发送到那一个队列（负载均衡策略）

    ```java
    // RocketMQ通过MessageQueueSelector中实现的算法来确定消息发送到哪一一个队列列上
    // RocketMQ默认提供了了两种MessageQueueSelector实现:随机/Hash
    // 当然你可以根据业务实现自自己己的MessageQueueSelector来决定消息按照何种策略略发送到消息队列列中
    SendResult sendResult = producer.send(msg, new MessageQueueSelector() {
        @Override
        public MessageQueue select(List<MessageQueue> mqs, Message msg, Object arg) {
            Integer id = (Integer) arg;
            int index = id % mqs.size(); // 对传入的参数 ID 对队列的大小进行取模，因此传入的参数 ID 一致的消息一定在同一队列
            return mqs.get(index);
        }
    }, orderId);
    ```

    获取到路由信息后，根据 `MessageQueueSelector` 实现的算法来选择一个队列。

    ```java
    private SendResult send() {
        // 获取topic路路由信息
        TopicPublishInfo topicPublishInfo =
            this.tryToFindTopicPublishInfo(msg.getTopic());
        if (topicPublishInfo != null && topicPublishInfo.ok()) {
            MessageQueue mq = null;
            // 根据我们的算法,选择一一个发送队列列
            // 这里里里的arg = orderId
            mq = selector.select(topicPublishInfo.getMessageQueueList(), msg, arg);
            if (mq != null) {
                return this.sendKernelImpl(msg, mq, communicationMode, sendCallback, timeout);
            }
        }
    }
    ```

- 消息重复

    消息重复的根本原因：网络不可达

    处理：

    - 消费端处理消息的业务逻辑要保持幂等性
    - 保证每条数据都有唯一编号，且保证消息处理成功与去重表的日志同时出现

- 事务消息

    RocketMQ 处理事务消息分为三个阶段：

    1. 第一阶段发送 Prepared 消息，拿到消息地址
    2. 执行本地事务
    3. 通过第一阶段拿到的地址去访问消息，并修改消息的状态

    <br />

    生产者发送消息源代码：

    ```java
    TransactionCheckListener transactionCheckListener = new
        TransactionCheckListenerImpl();// 构造事务消息的生生产者
    TransactionMQProducer producer = new TransactionMQProducer("groupName");
    // 设置事务决断处理理类
    producer.setTransactionCheckListener(transactionCheckListener);
    // 本地事务的处理理逻辑,相当于示例例中检查Bob账户并扣钱的逻辑
    TransactionExecuterImpl tranExecuter = new TransactionExecuterImpl();
    producer.start();
    // 构造MSG,省略略构造参数
    Message msg = new Message(......);
    // 发送消息
    SendResult sendResult = producer.sendMessageInTransaction(msg, tranExecuter,
                                                              null);
    producer.shutdown();
    ```

    `sendMessageInTransaction`源代码：

    ```java
    public TransactionSendResult sendMessageInTransaction(.....) {
        // 逻辑代码,非非实际代码
        // 1.发送消息
        sendResult = this.send(msg);
        // sendResult.getSendStatus() == SEND_OK
        // 2.如果消息发送成功,处理理与消息关联的本地事务单元
        if(sendResult.getStatus==SEND_OK)
        LocalTransactionState localTransactionState =
        tranExecuter.executeLocalTransactionBranch(msg, arg);
        // 3.结束事务
        this.endTransaction(sendResult, localTransactionState, localException);
    }
    ```

    具体如下所示：

    <img src="https://s6.jpg.cm/2021/12/24/LbDOfe.png" alt="img" style="zoom:60%;" />

<br />

### 消息存储

<br />

#### 消息存储的整体架构

RocketMQ的消息存储是由 `CommitQueue` 和 `CommitLog` 配合完成的，两者的组合关系如下图所示：

<img src="https://s6.jpg.cm/2021/12/24/LbZA3Q.png" alt="img" style="zoom:50%;" /><sup>[4]</sup>

具体地，Producer 在生产消息时，会生成对应的 `ConsumeQueue` 的节点元素，而 Consumer 通过 Topic 读取到对应的 `ConsumeQueue` 中对应的节点，就可以读取到在 `CommitLog` 上的偏移量，从而读取到具体的消息

几个关键的组件介绍如下：<sup>[3]</sup>

- `CommitLog`

    消息主体以及元数据的存储主体，存储Producer端写入的消息主体内容,消息内容不是定长的。单个文件大小默认1G, 文件名长度为20位，左边补零，剩余为起始偏移量，比如00000000000000000000代表了第一个文件，起始偏移量为0，文件大小为1G=1073741824；当第一个文件写满了，第二个文件为00000000001073741824，起始偏移量为1073741824，以此类推。消息主要是顺序写入日志文件，当文件满了，写入下一个文件

    在文件中的具体存储结构如下图所示：<sup>[4]</sup>

    <img src="https://s6.jpg.cm/2021/12/24/LbOb65.png" alt="img" style="zoom:80%;" />

    <br />

- `CommitQueue`

    消息消费队列，引入的目的主要是提高消息消费的性能，由于 RocketMQ 是基于 Topic 的订阅模式，消息消费是针对主题进行的，如果要遍历 `CommitLog` 文件中根据 Topic 检索消息是非常低效的。Consumer 可根据 `ConsumeQueue` 来查找待消费的消息。

    `ConsumeQueue`（逻辑消费队列）作为消费消息的索引，保存了指定Topic下的队列消息在 `CommitLog` 中的起始物理偏移量offset，消息大小size和消息Tag的 `HashCode` 值。

    `ConsumeQueue`文件可以看成是基于 Topic 的 `CommitLog` 索引文件，故 `ConsumeQueue` 文件夹的组织方式如下：`topic/queue/file` 三层组织结构，具体存储路径为：`$HOME/store/consumequeue/{topic}/{queueId}/{fileName}`。同样 `ConsumeQueue` 文件采取定长设计，每一个条目共20个字节，分别为8字节的 `CommitLog` 物理偏移量、4字节的消息长度、8字节 Tag `hashcode`，单个文件由30W个条目组成，可以像数组一样随机访问每一个条目，每个 `ConsumeQueue` 文件大小约 5.72M

    `ConsumeQueue` 的每个元素的组成结构如下图所示：<sup>[4]</sup>

    <img src="https://s6.jpg.cm/2021/12/24/LbOuEC.png" alt="img" style="zoom:80%;" />

    <br />

- `IndexFile`

    `IndexFile`（索引文件）提供了一种可以通过 key 或时间区间来查询消息的方法。Index 文件的存储位置是：`$HOME \store\index$​{fileName}`，文件名 `fileName` 是以创建时的时间戳命名的，固定的单个 `IndexFile` 文件大小约为 400M，一个 `IndexFile` 可以保存 2000W个索引，`IndexFile` 的底层存储设计为在文件系统中实现 `HashMap` 结构，故 RocketMQ 的索引文件其底层实现为 hash 索引。

    <br />

具体情况如下图所示：

<img src="https://github.com/apache/rocketmq/raw/master/docs/cn/image/rocketmq_design_1.png" alt="img" style="zoom:80%;" /><sup>[3]</sup>

<br />

#### 页缓存与内存映射

- 页缓存

    页缓存（`PageCache`）是 OS 对文件的缓存，用于加速对文件的读写。一般来说，程序对文件进行顺序读写的速度几乎接近于内存的读写速度，主要原因就是由于 OS 使用  `PageCache`  机制对读写访问操作进行了性能优化，将一部分的内存用作 `PageCache`

    对于数据的写入，OS 会先写入至 `Cache` 内，随后通过异步的方式由 `pdflush` 内核线程将 `Cache` 内的数据刷盘至物理磁盘上。对于数据的读取，如果一次读取文件时出现未命中 `PageCache` 的情况，OS从物理磁盘上访问读取文件的同时，会顺序对其他相邻块的数据文件进行预读取

    在 RocketMQ 中，`ConsumeQueue` 逻辑消费队列存储的数据较少，并且是顺序读取，在 `PageCache` 机制的预读取作用下，`ConsumeQueue` 文件的读性能几乎接近读内存，即使在有消息堆积情况下也不会影响性能。而对于 `CommitLog` 消息存储的日志数据文件来说，读取消息内容时候会产生较多的随机访问读取，严重影响性能。如果选择合适的系统 IO 调度算法，比如设置调度算法为“Deadline”（此时块存储采用SSD的话），随机读的性能也会有所提升。

- 内存映射

    另外，RocketMQ 主要通过 `MappedByteBuffer` 对文件进行读写操作。其中，利用了 `NIO` 中的 `FileChannel` 模型将磁盘上的物理文件直接映射到用户态的内存地址中（这种 `mmap` 的方式减少了传统 IO 将磁盘文件数据在操作系统内核地址空间的缓冲区和用户应用程序地址空间的缓冲区之间来回进行拷贝的性能开销），将对文件的操作转化为直接对内存地址进行操作，从而极大地提高了文件的读写效率（正因为需要使用内存映射机制，故 RocketMQ 的文件存储都使用定长结构来存储，方便一次将整个文件映射至内存）

<br />

#### 消息刷盘

RocketMQ 提供了以同步或异步的方式来将数据冲刷到磁盘上，当需要可靠的消息传输时，使用同步的方式是一个很好的方式；而当对性能要求比较高，但是允许一定的消息丢失的话，异步的方式会是一个更好的选择。

两者的刷盘方式下图所示：
<img src="https://github.com/apache/rocketmq/raw/master/docs/cn/image/rocketmq_design_2.png" alt="img" style="zoom:100%;" /><sup>[3]</sup>

<br />

### 负载均衡

RocketMQ 中的负载均衡都在 Client 端完成，具体来说的话，主要可以分为 Producer 端发送消息时候的负载均衡和 Consumer 端订阅消息的负载均衡

- Producer 的负载均衡

    Producer 端在发送消息的时候，会先根据 Topic 找到指定的 `TopicPublishInfo`，在获取了`TopicPublishInfo` 路由信息后，RocketMQ 的客户端在默认方式下 `selectOneMessageQueue()` 方法会从`TopicPublishInfo` 中的 `messageQueueList` 中选择一个队列（ `MessageQueue` ）进行发送消息。

    具体的容错策略均在 `MQFaultStrategy` 这个类中定义。这里有一个 `sendLatencyFaultEnable` 开关变量，如果开启，在随机递增取模的基础上，再过滤掉 not available 的 Broker 代理。所谓的 `latencyFaultTolerance`，是指对之前失败的，按一定的时间做退避。例如，如果上次请求的 latency 超过 550Lms，就退避 3000Lms；超过 1000L，就退避 60000L；如果关闭，采用随机递增取模的方式选择一个队列（`MessageQueue`）来发送消息，`latencyFaultTolerance` 机制是实现消息发送高可用的核心关键所在

- Consumer 的负载均衡

    在 RocketMQ 中，Consumer 端的两种消费模式（Push/Pull）**都是基于拉模式**来获取消息的，而在 Push 模式只是对 Pull 模式的一种封装，其本质实现为消息拉取线程在从服务器拉取到一批消息后，然后提交到消息消费线程池后，又“马不停蹄”的继续向服务器再次尝试拉取消息。

    如果未拉取到消息，则延迟一下又继续拉取。

    消费端会通过 `RebalanceService` 线程，每 20s 做一次基于 Topic 下的所有队列负载：

    > 1. 遍历 Consumer 下所有的 Topic，根据 Topic 订阅所有的消息
    > 2. 获取同一 Topic 和 Consume Group 下的所有 Consumer
    > 3. 根据具体的分配策略来分配消费队列，分配的策略包含：平均分配、消费段配置等。





<br />

参考：

<sup>[1]</sup> https://github.com/apache/rocketmq/blob/master/docs/cn/architecture.md

<sup>[2]</sup> https://github.com/apache/rocketmq/blob/master/docs/cn/features.md

<sup>[3]</sup> https://github.com/apache/rocketmq/blob/master/docs/cn/design.md

<sup>[4]</sup> https://zhuanlan.zhihu.com/p/92125985