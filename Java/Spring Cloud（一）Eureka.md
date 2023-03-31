# Spring Cloud（一）Eureka

## 单体应用存在的问题

在传统应用程序中，一般都会将整个的应用程序作为一个单独的可执行文件部署到相应的服务器上执行。一般的应用程序结构可能如下图所示：

<img src="https://s2.loli.net/2022/02/24/5ENxJupQLr1h9qX.png" alt="single-app.png" style="zoom:60%;" />

这种方式的优点很明显，比如：架构简单，服务之间调用逻辑清晰，服务部署方式也比较简单等等。

但是也有一些显而易见的缺点：

- 随着项目的需求变多，整个项目的复杂程度会越来越高，这个时候对代码的微小改动很有可能影响到程序的正常工作
- 由于项目很大，因此编译和部署时也会耗费更多的时间
- 有时只是改动了一个小模块的功能，但是不得不将整个项目再次重新编译并部署，但是这通常都是不必要的
- 不同的模块对于硬件的要求不同，因此很难针对单一的模块进行硬件的水平扩展
- 如果希望对现有的项目进行技术选型，这需要对整个项目进行修改，这将导致工作量剧增

## 微服务的引入

鉴于大型项目中存在的问题，可以考虑将单独的功能模块从单体的应用程序中剥离出来，使得这些单独的功能模块称为一个单独的应用程序服务，这些服务可以单独部署，采用不同的技术选型，拥有自己的数据库等基础中间件。这些剥离出来的应用程序就被称为 “微服务”，相关的结构可能如下图所示：

<img src="https://s2.loli.net/2022/02/24/pHWCXPsGwjlIUBF.png" alt="micro-service.png" style="zoom:67%;" />

微服务的引入解决了上文提到的单体应用存在的问题，但是它也引入了一些新的问题：

- 现在管理这些应用程序服务变得相当复杂，特别是涉及到多个服务之间相互调用的时候
- 数据的一致性很难在多个微服务应用程序的环境中保持
- 定位问题的难度也会随之增加

尽管引入微服务还存在一些其它的问题，但是在当前的环境下，特别是互联网这种流量特别大的应用程序，单体应用程序时绝对无法达到要求的（单个计算机的性能是有限的），因此微服务的架构在现在可以说是一个大势所趋

## Spring Cloud 微服务生态圈

Spring 框架提供了对微服务应用程序的支持，它提供了以下的组件用于支持微服务应用架构：

| 微服务技术栈     | 对应的组件           |
| ---------- | --------------- |
| 服务开发       | Spring MVC      |
| 服务配置       | Config          |
| 服务注册与发现    | Eureka          |
| 服务调用（负载均衡） | Ribbon、Feign    |
| 服务路由（网关）   | Zuul            |
| 服务熔断（熔断器）  | Hystrix         |
| 服务圈链路监控    | Sleuth + Zipkin |
| 服务部署       | Docker          |

这些组件之间的关系如下图所示：

<img src="https://s2.loli.net/2023/03/25/V2sX6ZHbACjUa4d.png" alt="spring_components.png" style="zoom:80%;" />

## 服务治理

前文提到过，将单个的应用程序拆分成多个微服务应用程序，会导致服务之间相互调用的复杂性。比如，如何确定调用的服务是否存在，如何去访问这些服务，这些问题的解决就是 “服务治理”需要解决的问题。

服务治理是微服务应用程序中最为基础的部分，主要的任务是实现各个微服务应用程序之间的自动化注册和发现，因此当某个服务程序希望调用其它模块的服务时，会首先调用 “服务治理”这一模块，以获取要调用的服务的基本信息（如 IP、端口等），然后再进行服务的调用

实际上，在传统的单体应用程序中，也存在诸如 `RMI` 这样的技术来实现服务的远程调用，但是这些调用都是基于固定的 IP 地址来进行调用的，缺少一定的灵活性。

### 服务发现

服务发现分为两种表现方式：客户端—服务发现、服务端—服务发现

- 客户端的服务发现
  
    客户端通过查询服务注册中心，获取可用服务的实际网络地址（IP和端口），然后客户端通过某种负载均衡算法来选择一个实际的服务实例，将相关的请求发送到该服务实例
  
    如下图所示：
  
  <img src="https://s2.loli.net/2023/03/25/FSnHJziI2tCx8qA.png" alt="spring_service_discover_client.png" style="zoom:80%;" />

- 服务端的服务发现
  
    客户端将请求发送到具有“负载均衡”功能的微服务上，该 “负载均衡”服务首先查询到服务注册中心，找到可用的服务，然后将请求发送到该服务上
  
  <img src="https://s2.loli.net/2023/03/25/SmWw4XkDHUVr9Cj.png" alt="spring_service_discover_server.png" style="zoom:80%;" />

## 实际使用

由于服务治理是相对的，因此至少需要两个微服务应用程序才能够进行服务之间的调用。

### 配置注册中心

对于注册中心，如果是通过 <a href="https://start.spring.io/">spring.io</a> 的方式来创建的项目，那么只需要添加 `eureka-server` 的依赖即可：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
</dependency>
```

然后在 `Spring-Boot` 的启动类上加上 `@EnableEurekaServer` 使得当前的程序成为注册中心。

在 `application.yml` 配置文件中做如下的配置：

```yaml
server:
  port: 8761 # Eureka 注册中心的端口
eureka:
  instance:
    # 设置当前 Eureka 实例的主机名
    hostname: xhliu-eureka-server
  client:
    # 由于当前所在的实例为注册中心，因此不需要向该注册中心注册自己
    register-with-eureka: false
    # 注册中心的职责就是维护服务器实例，不需要去检索服务
    fetch-registry: false
    service-url:
      # 暴露给其它 Eureka 客户端的注册地址，Map 结构
      defaultZone: http://127.0.0.1:8761/eureka/
```

然后启动该 `Spring-Boot` 项目，此时该项目就已经是一个单独的注册中心了。如果此时访问`http://127.0.0.1:8761`，可能会看到类似的界面：

<img src="https://s6.jpg.cm/2022/02/24/LgLDpC.png" alt="spring-components.png" style="zoom:67%;" />

说明已经成功创建了一个 `Eureka` 注册中心

### 注册服务

对于注册到注册中心的应用来讲，需要添加 `eureka-client` 依赖：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

在启动类上加上 `@EnableDiscoveryClient` 注解可以使得当前的服务能够被其它的服务所发现，由于 `Eureka` 默认能够发现应用服务，因此即使不加这个注解，该服务依旧能够被正常发现。

在启动之前，配置当前的应用程序，使得它能够成功注册到对应的注册中心，以及能够被其它的应用服务正常地发现

在 `application.yml` 文件中，做如下的配置：

```yaml
server:
  port: 8001
spring:
  application:
    # 当前应用服务名称
    name: xhliu-producer-1

# 这里需要正确配置注册中心的 URL，否则可能会出现找不到该应用的情况
eureka:
  client:
    service-url:
      defaultZone: http://127.0.0.1:8761/eureka/
```

启动该 `Spring-Boot` 应用程序，然后回到上文的 `Eureka` 的管理界面，可以看到该服务已经注册到 `Eureka` 注册中心了：

<img src="https://s6.jpg.cm/2022/02/24/LgPkcr.png" alt="spring-components.png" style="zoom:67%;" />

或者，访问注册中心的 `/apps` 接口，也可以查看注册到注册中心的服务，在上文的配置中，注册中心的上下文换进为 `/eureka`，因此访问 http://127.0.0.1:8761/eureka/apps 即可查看已经注册的服务，该接口会返回以 `XML` 格式的数据载体，类似下图所示：
<img src="https://s6.jpg.cm/2022/02/24/LgPpiC.png" alt="spring-components.png" style="zoom:67%;"/>

### 服务之间的访问

有了注册中心的存在，现在服务与服务之间的调用变得更加灵活了。

#### producer

现在在 `producer` 应用服务中添加如下的 `REST` 接口：

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
public class EurekaProducerController {
    private final static Logger log = LoggerFactory.getLogger(EurekaProducerController.class);

    @Resource
    private DiscoveryClient client; // 服务发现的客户端接口

    @Resource
    private Registration registration; // 用于获取注册信息

    /*
        该接口每次访问都会打印当前服务发现到的服务，同时将其作为返回值返回给调用对象
    */
    @GetMapping(path = "/produce")
    public List<ServiceInstance> produce() {
        List<ServiceInstance> instances = client.getInstances(registration.getServiceId());
        instances.forEach(obj -> log.info("host={} service={}", obj.getHost(), obj.getServiceId()));
        return instances;
    }
}
```

启动两个 `producer`（改变 `application.yml` 的 `sever.port` 再启动），完成这两个 `producer` 的

注册

#### consumer

然后，创建一个 `concumer` 服务程序，用于调用 `producer` 的 `REST` 接口。`consumer` 的 `application.yml` 配置如下所示：

```yaml
server:
  port: 8008
spring:
  application:      
    name: xhliu-consumer-1
eureka:
  client:
    service-url:
      defaultZone: http://127.0.0.1:8761/eureka/
```

由于是 `REST` 接口的调用，因此需要创建一个 `RestTemplate` 类型的 `Bean` 用于访问 `REST` 接口：

```java
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ConsumerBeanConfig {
    @Bean
    @LoadBalanced  // 通过 @LoadBalanced 注解开启负载均衡
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

然后，创建一个 `REST` 接口用于客户端调用：

```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.List;

@RestController
public class ConsumerController {
    @Resource
    private RestTemplate restTemplate;

    @GetMapping(path = "/consumer")
    public List<?> index() {
        return restTemplate.getForEntity(
            /*
                这里的访问地址格式为 http://{服务名}/{REST 接口}
            */
            "http://xhliu-producer-1/produce",
            List.class
        ).getBody();
    }
}
```

然后启动 `consumer` ，将 `concumer` 注册到注册中心中

现在，对于 `consoumer` 的 `/concumer` 的访问，都会首先向注册中心查找对应的服务，再进行相关的调用。注意，这里的负载均衡是在 `consumer` 这里完成的（注意 `@loadblance` 注解）。如果访问该 `concumer` 的 `/consumer` 接口，可能会得到类似下图的输出：

<img src="https://s6.jpg.cm/2022/02/24/LgPCwp.png" alt="spring-components.png" style="zoom:67%;"/>

在注册到注册中心的两个 `producer` 中，由于 `consumer` 负载均衡的存在，具体访问哪个 `producer` 是不确定的

### 注册中心集群的搭建

由于注册中心本身也是一个服务，因此只需要创建另一个注册中心，然后将它注册到现在的注册中心即可。

假设现在创建一个有两个实例的 `Eureka` 注册中心集群，其中一个注册中心为 `master`，一个为 `copied`。

`master` 的 `application.yml` 配置文件内容如下所示：

```yaml
server:
  port: 8040 # Eureka 注册中心的端口

spring:
  application:
    # 同一个集群的应用程序名必须是一样的
    name: xhliu-server

eureka:
  instance:
    # 设置当前 Eureka 实例的主机名
    hostname: xhliu-eureka-master
  client:
    # 由于当前所在的实例为注册中心，因此不需要向该注册中心注册自己
    register-with-eureka: false
    # 注册中心的职责就是维护服务器实例，不需要去检索服务
    fetch-registry: false
    service-url:
      # 将 master 注册到 copied，使得能够被 copied 发现
      defaultZone: http://127.0.0.1:8050/eureka/
```

`copied` 的注册中心的 `application.yml` 配置文件内容如下：

```yaml
server:
  port: 8050 # master 注册中心的端口

spring:
  application:
    # 同一个集群的应用程序名必须是一样的
    name: xhliu-server

eureka:
  instance:
    # 设置当前 Eureka 实例的主机名
    hostname: xhliu-eureka-copied
  client:
    # 由于当前所在的实例为注册中心，因此不需要向该注册中心注册自己
    register-with-eureka: false
    # 注册中心的职责就是维护服务器实例，不需要去检索服务
    fetch-registry: false
    service-url:
      # 将 copied 注册到 master，使得其能够被 master 发现
      defaultZone: http://127.0.0.1:8040/eureka/
```

现在，分别启动这两个注册中心，访问这两个注册中心的 `Eureka` 管理界面，其中，可以看到，`master` 中已经存在 `cpoied` 的备用注册中心了：

<img src="https://s6.jpg.cm/2022/02/24/LgR1L4.png" alt="spring-components.png" style="zoom:67%;"/>

## Eureka 的核心功能

Eureka 主要存在以下的一些核心功能：

- 服务注册功能
  
    这个最基本的注册中心的功能之一，`Client` 端通过 `REST` 请求的方式，向 `Server` 注册自己的服务信息（如 `ip` 和端口号等）。`Server` 收到这些信息则会将它们存储在一个双层 `Map` 结构中，其中，第一层 `Map` 会存储该请求的服务名；第二层存储具体服务的实例名

- 服务续约功能
  
    服务注册之后，`Client` 会默认每隔 $30$ s 来告知 `Server` 自己依旧存活。可以通过修改如下的配置内容来修改这个间隔时间：
  
  ```properties
  eureka.instance.lease-renewal-interval-in-seconds=30
  ```

- 服务同步功能
  
    在 `Eureka` 集群中，`Server` 之间通过相互注册来进行同步，保证注册的服务信息的一致性

- 服务获取功能
  
    `Client` 启动之后，会发送一个 `REST` 请求给 `Server` 端，然后从 `Server` 端获取已经注册的服务列表。`Client` 端会将这些获取到的服务缓存到本地（缓存保留时间默认为 $30$s），同时，处于性能的考虑，`Server` 端也会将这些信息缓存到本地（缓存保留时间默认为 $30$s）。可以通过在配置文件中进行配置：
  
  ```properties
  eureka.client.fetch-registry=true
  eureka.client.registry-fetch-interval-seconds=30
  ```

- 服务调用功能
  
    当 `Client` 需要关闭或者重启时，可以发送 `REST` 请求给 `Server` 端，告知自己将要下线。此时，将该 `Client` 的服务状态设置为 `DOWN`，并将该状态同步给集群中的其它节点

- 服务剔除功能
  
    如果 `Client` 没有发送下线请求给 `Server` ，但是由于某些原因（如网络故障）导致该 `Client` 不能继续提供服务，那么就会触发服务剔除机制。`Eureka Server` 在启动时，会创建一个定时任务，默认每隔 $60$s 从当前的服务列表中包超时 $90$s 没有续约的服务进行剔除
  
    可以通过修改相关的配置来修改这个超时时间：
  
  ```properties
  eureka.instance.lease-expiration-duration-in-seconds=90
  ```

- 自我保护
  
    如果由于网络在一段时间内发生了异常，导致所有的服务都没有续约，那么为了防止 `Server` 端把所有的服务都进行剔除，于是就出现了自我保护机制。
  
    如果在 $15$ 分钟内，出现了低于 $85\%$ 的续约失败比例，那么将会触发 “自我保护”机制，该机制下不会对任何服务进行剔除操作，当网络正常之后，再退出自我保护机制
  
    通过进行如下配置以开启“自我保护”机制：
  
  ```properties
  eureka.server.enable-self-preservation=true
  ```

## Eureka 的 REST API

具体可以参考：https://github.com/Netflix/eureka/wiki/Eureka-REST-operations

<br />

参考：

<sup>[1]</sup> https://mp.weixin.qq.com/s/Xfq5YCaSSc7WgOH6Yqz4zQ

<br />

本文对应的项目地址：https://github.com/LiuXianghai-coder/Spring-Study/tree/master/spring-eureka