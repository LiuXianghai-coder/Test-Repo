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

- Producer：消息发布的角色，支持分布式集群方式部署。Producer 通过 MQ 的负载均衡模块选择相应的 Broker 集群队列进行消息投递，投递的过程支持快速失败并且低延迟

- Consumer：消息消费的角色，支持分布式集群方式部署。支持两种模式：pull 模式和 push 模式。同时也支持以集群方式和广播的方式进行消费，提供实时消息订阅机制，可以满足大部分的要求

- Broker： Broker 主要负责消息的存储、投递和查询以及服务高可用保证，为了实现这些功能，Broker 包含了以下几个重要的模块

  - Remoting Module：负责整个 Broker 的实体，负责处理来自 clients 的请求
  - Client Manager：负责管理客户端（Producer/Consumer）和维护 Consumer 的 Topic 的订阅信息
  - Store Service：高可用服务，提供 Master Broker 和 Slave Broker 之间的数据同步功能
  - Index Service：根据特定的 Message Key 对投递到 Broker 的消息进行索引服务，以提供消息的快速查询

  具体如下图所示：

  <img src="https://github.com/apache/rocketmq/raw/master/docs/cn/image/rocketmq_architecture_2.png" alt="img" style="zoom:150%;" />

  

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

- 顺序消费

  顺序消费指的是消费一类消息时，能够按照发送的顺序来进行消费。例如：如果一个订单产生了三条消息，分别为：订单创建、订单付款、订单完成，消费这类消息时必须按照这个顺序来消费才有意义。RocketMQ 可以严格地保证消息有序

- 消息过滤

- 事务消息

- 消息存储

- 消息订阅



<br />

参考：

<sup>[1]</sup> https://github.com/apache/rocketmq/blob/master/docs/cn/architecture.md