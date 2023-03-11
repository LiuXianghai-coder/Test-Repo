# Spring Cloud （三）GateWay

GateWay 即网关，主要的功能是提供一个类似代理的功能，对相关的服务调用进行统一的处理。

## 网关的作用

不存在网关的服务调用，如下图所示：

<img src="https://s2.loli.net/2022/03/07/kjnxcheNgFLuA5z.png" alt="GateWay.png" style="zoom:67%;" />

在这种情况下，由于每个服务之间的 API 都一样，因此客户端进行服务调用的时候就会变得十分复杂；每个服务对于每次的客户端调用都需要进行相同或者类似的处理，增大了系统的冗余；由于每个 Service 使用的编程语言或者协议不一致，因此可能需要对请求进行进一步的适配处理才能够正常调用服务接口

这个问题其实在 《设计模式—可复用面向对象基础》一书中有一个类似的解决方案，通过 Facade（外观）模式，为服务提供一个统一的接口，使得子系统更加容易使用，这个统一的接口就是 GateWay（网关）代表的角色

引入网关之后，服务调用就会变成如下图所示的结构：

<img src="https://s2.loli.net/2022/03/07/rMc9IWO8K2ykxPq.png" alt="GateWay.png" style="zoom:67%;" />

## 网关的应用场景

- 鉴权认证

    客户端身份认证：主要用于判断当前用户是否是一个合法的用户，一般的做法是使用帐号和密码进行验证，对于一些复杂的认证场景，可以通过采用对应的加密算法来实现

    访问权限控制：身份认证和访问权限一般都是互相联系的。当身份认证通过之后，就需要判断当前的用户是否具有权限访问该资源，或者该用户的访问权限是否被限制了

- 灰度发布（金丝雀发布）

    在某些应用场景中，由于软件版本的迭代速度比较快，在这种情况下直接进行版本发布会伴随一定的风险，为了避免这些问题，一般会采用灰度发布的方式实现平滑过度。

    灰度发布时，会将一部分用户流量引入到当前新发布的版本中，假设现在将客户端流量分为了 A、B 两部分，那么 A 将继续使用原来版本的系统，而 B 则会使用较新的版本，通过 B 的反响，在逐步将 A 中的流量移动到新版本中

    由于网关的存在，定义分流规则并进行实际分流将会变得比较简单

## 网关的技术选型

网关最为关键的两部分：请求的转发路由和过滤器，常见的网关的实现方案有：OpenResty，Zuul、GateWay 等

### OpenResty

OpenResty 是由 Nginx 和 Lua 集成的一个高i性能 Web 应用服务器，内部集成了大量的 Lua 库和第三方模块。可以理解为 OpenResty 就是将 Lua 嵌入到了 Nginx 中，在每个 Nginx 的进程中都嵌入了一个 LuaJIT 来执行 Lua 脚本

OpenResty 指令的执行顺序如下所示：

![OpenResty.png](https://s2.loli.net/2022/03/07/YnOjuiS6eNqhrIl.png)

### Zuul

Zuul 是 Netflix 的一个开源服务网关，它的主要功能是路由转发和请求过滤

Zuul 的核心有一系列的过滤器组成，它定义了 4 中标准类型的过滤器，这些会对应请求的整个生命周期

Zuul 的请求过滤如下：

<img src="https://s2.loli.net/2022/03/07/TPq1p9AMZtQnwUV.png" alt="Zuul.png" style="zoom:80%;" />

各个组件说明如下：

- Pre Filters：前置过滤器，请求被路由之前调用，可以用于处理鉴权、限流等
- Routing Filters：路由过滤器，将请求路由到后端的微服务
- Post Filters：后置过滤器，路由过滤器中远程调用结束后被执行，可以用于统计、监控、日志等
- Error Filters：错误过滤器，任意一个过滤器出现异常或者远程服务调用超时都会被调用

Zuul 1.x 采用的是传统的 TPC（Thread Per Connection）的方式来处理请求，也就是说，对于每一个请求，会为这个请求专门分配一个线程来进行处理，直到这个请求处理完成之后再销毁这个线程，这种方式性能较差

Zuul 本身存在的一些性能问题不适合高并发的场景，因此 Spring Cloud 并没有集成 Zuul 作为默认的网关

### GateWay

Sprin Cloud GateWay 是 Spring 官方团队研发的 API 网关技术，它的目的是取代 Zuul 成为微服务提供一个简单高效的 API 网关。

Spring Cloud GateWay 是基于 Reactor 开发的一套网关，Reactor 通过完全非阻塞的方式保证了性能，并且由于 Reactor 线程模型，对于线程的创建与销毁发生的频率都是相当低的

## 基本使用

由于在最新的版本中 GateWay 已经采用 WebFlux 作为底层服务器处理，因此传统的 Spring Boot Web 是无法被使用的。

要使用 Spring Cloud GateWay，只需要在项目中加入如下的依赖项：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
    <version>3.1.1</version>
</dependency>
```

添加该依赖项会自动引入 WebFlux 的依赖项，因此简化了相关的操作，在 WebFlux 中，传统的 Web 相关的注解依旧是可以使用的，以下面的例子为例，创建一个简单的控制器 Bean：

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimpleController {
    private static final Logger log = LoggerFactory.getLogger(SimpleController.class);

    @GetMapping(path = "/say")
    public String say() {
        log.info("[spring-cloud-gateway-service] say Hello");
        return "[spring-cloud-gateway-service] say Hello\n";
    }
}
```

接下来在 `application.yml`（或者 `aplication.properties`）配置文件中配置网管，具体的示例如下所示：

```yaml
server:
  port: 8000 # spring Web 的相关配置在 WebFlux 中依旧可以使用

spring:
  cloud:
  # 在这里配置具体的网关
    gateway:
      routes:
        - id: path_route # id 表示路由 id，注意在系统中保证这个 id 是唯一的
          uri: http://127.0.0.1:8000 # 该  GateWay 作用的 URL
          filters: # 过滤器，为通过 Predicate 的请求进行进一步的处理，具体可以查看 GatewayFilter
            - name: StripPrefix # 这里是 GateWay 中一个内置 Filter ，每个请求跳过第一个请求前缀
              args:
                parts: 1
          predicates: # 路由条件，根据匹配的结果决定是否执行该请求路由，具体可以查看 RoutePredicateFactory
            - name: Path # 这也是 GateWay 的一个内置 Predicate，表示用于路径匹配
              args:
                xhliu: /gateway/**
```

实际上，`Filter` 和 `Predicate` 在 GateWay 的自动配置类中已经定义了大量的具体实现类对应的 Bean，在装载到对应的容器中时，会去掉冗余的后缀，保留前缀作为对应的键以便于查找，具体可以查看对应的源代码

此时对 http://127.0.0.1:8000/gateway/say 进行访问，可以发现请求会转发到 http://127.0.0.1:8000/say，如下图所示：

![2022-03-08 20-19-37 的屏幕截图.png](https://s2.loli.net/2022/03/08/BL4lPAdsbZWIGO7.png)

### 处理流程

Spring Cloud GateWay 请求的处理过程如下图所示：

<img src="https://s2.loli.net/2022/03/08/dkU25AQy1EzfYiP.png" alt="route.drawio.png" style="zoom:80%;" />

组件说明如下：

- Route（路由）：包含 `Predicate` 和 `Filter`，是网关的基本组件，由自定义 Id、目标 URL、Predicate 集合和 Filter 集合组成
- Predicate：这是 Java 8 中引入的函数式编程的一个基本接口，提供了类似断言的功能，具体来说，就是输入一个参数，`Predicate` 会进行判断是否是满足条件的。如果满足条件即 Predicate 返回 true，则请求会被 Route 进行转发
- Filter（过滤器）：为请求提供前置和后置的过滤，这里的 ”过滤“ 指的是给通过 `Predicate` 的请求进行一些特有的操作，可以类比 Spring AOP 所做的增强功能

### RoutePredicateFactory

在 Spring Cloud GateWay 中，提供了几种 `RoutePredicateFactory` 的初始 Bean，这些 RoutePredicateFactory 按照类型进行划分可以分为以下几类：

- 指定时间规则匹配路由
    - `BeforeRoutePredicateFactory`：只有在指定的时间之前的请求才能通过，否则请求转发失败
    - `AfterRoutePredicateFactory`：只有在指定时间之后的请求才能通过，否则请求转发失败
    - `BetweenRoutePredicateFactory`：只有在指定的时间段的请求才能被转发，否则请求转发失败
- 匹配请求 Cookie 的路由
    - `CookieRoutePredicateFactory`：判断请求携带的 Cookie 是否匹配配置的规则，不满足提哦见则服务转发失败
- 请求头信息匹配规则路由
    - `HeaderRoutePredicateFactory`：只有在请求的请求头包含的信息匹配对应的规则，才进行请求的转发
    - `CloudFoundryRouteServiceRoutePredicateFactory`：// TODO
- 主机信息匹配规则路由
    - `HostRoutePredicateFactory`：每个 Http 请求都会携带一个 Host 字段，这个字段表示请求的服务器的地址，该配置规则通过对 Host 信息进行规则匹配进行路由
- 请求方式的路由规则
    - `MethodRoutePredicateFactory`：只有满足定义的请求方法（`PUT`、`GET`、`POST`等）规则才能进行正常的请求转发
- 请求路进路由规则
    - `PathRoutePredicateFactory`：根据请求的请求路径进行对应的路由规则判断，再进行请求的转发

​	以上是一些常用的规则配置，其它的一些配置目前尚未接触

​	// TODO

### GatewayFilterFactory

`GatewayFilterFactory` 是路由的过滤器（请求增强），`Filter` 分为两种：`Pre` 过滤器和 `Post` 过滤器，`Pre` 过滤器在请求转发之前执行，`Post` 过滤器在请求转发之后，处理结果返回客户端之前执行

GateWay Filter 的实现方式也分为两种：`GateWayFilter`、`GlobalFilter`，其中 `GateWayFilter` 只会应用到单个路由或者一个分组的路由上，而 `GlobalFilter` 则会应用到所有的路由上

- `GateWayFilter`

    - `AddRequestParameterGatewayFilterFactory`：对所有请求添加一个查询参数，前提是该请求必须通过 `Predicate`

    - `AddResponseHeaderGatewayFilterFactory`：在处理结果返回给客户端之前，向 Header 中添加相应的数据

    - `RequestRateLimiterGatewayFilterFactory`：配置请求限流，该过滤器会对访问到当前网关的所有请求进行限流，当没限流时，默认返回响应码 429。`RequestRateLimiterGatewayFilterFactory` 默认提供了 `RedisRateLimiter` 的限流实现，它采用令牌桶算法来实现限流功能

        如果希望使用 `RequestRateLimiterGatewayFilterFactory` 的默认限流，那么需要添加如下的依赖：

        ```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>
        ```

        这是因为当前的运行环境为 WebFlux，因此传统的 spring-boot-starter-data-redis 可能无法适应到当前的运行环境

    - `RetryGatewayFilterFactory`：准许请求重试

- `GlobalFilter`

    `GlobalFilter` 的执行顺序：

    1. 当 GateWay 接收到请求时，`FilteringWebHandler` 处理器会将所有的 `GlobalFilter` 实例以及所有路由上配置的 GateWayFilter实例添加到一条过滤链中
    2. 所有的过滤器将会按照 `@Order` 注解定义的顺序进行排序处理

    Spring Cloud GateWay 内置的集中 `GlobalFilter`：

    - `GatewayMetricsFilter`：网关指标过滤器，提供监视指标，可以结合 Spring Actuactor 对 Spring Boot 应用程序进行监控

### 自定义 Filter

- 自定义 `GateWayFilter`

    如果需要自定义 `GateWayFilter`，通过继承 `AbstractGatewayFilterFactory` 来自定义具体的行为可能是一个比较好的选择，示例代码如下：

    ```java
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.cloud.gateway.filter.GatewayFilter;
    import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
    import org.springframework.stereotype.Component;
    import reactor.core.publisher.Mono;
    
    /*
        需要注意的是自定义的 Filter 的类名必须是以 “GatewayFilterFactory” 结尾，这是因为 Spring Cloud GateWay
        在进行解析是会去掉这个结尾部分，这也是 “约定优于配置” 的一个场景
    */
    @Component // 必须能够被 Spring 容器发现
    public class XhliuGatewayFilterFactory
            extends AbstractGatewayFilterFactory<XhliuGatewayFilterFactory.XhliuConfig> {
    
        private final static Logger log = LoggerFactory.getLogger(XhliuGatewayFilterFactory.class);
    
        public XhliuGatewayFilterFactory() {
            super(XhliuConfig.class);
        }
    
        @Override
        public GatewayFilter apply(XhliuConfig config) {
            return ((exchange, chain) -> {
                log.info("XhliuGateWayFilter [Pre] Filter Request, Config's name={}", config.getName());
                return chain.filter(exchange)
                        .then(Mono.fromRunnable(
                                () -> {
                                    log.info("XhliuGateWayFilter [Post] Response Filter");
                                }
                        ));
            });
        }
    
        /*
            该配置类的主要目的是获取在 yaml 配置文件中配置的属性信息
        */
        static class XhliuConfig {
            private String name;
    
            public String getName() {
                return name;
            }
    
            public void setName(String name) {
                this.name = name;
            }
        }
    }
    ```

- 自定义 `GlobalFilter`

    如果要自定义 `GlobalFilter` 的话，只需要实现 `GlobalFilter` 接口即可，如下所示：

    ```java
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.cloud.gateway.filter.GatewayFilterChain;
    import org.springframework.cloud.gateway.filter.GlobalFilter;
    import org.springframework.core.Ordered;
    import org.springframework.stereotype.Component;
    import org.springframework.web.server.ServerWebExchange;
    import reactor.core.publisher.Mono;
    
    @Component
    public class XhliuGlobalFilter
            implements GlobalFilter, Ordered {
    
        private final Logger log = LoggerFactory.getLogger(XhliuGlobalFilter.class);
    
        @Override
        public Mono<Void> filter(
                ServerWebExchange exchange,
                GatewayFilterChain chain
        ) {
            log.info("XhliuGlobalFilter [Pre] Filter");
            return chain.filter(exchange)
                    .then(Mono.fromRunnable(() -> log.info("XhliuGlobalFilter [Post] Filter")));
        }
    
        /*
             表示该过滤器的执行顺序，值越小，执行优先级越高
        */
        @Override
        public int getOrder() {
            return 0;
        }
    }
    ```

<br />

参考

<sup>[1]</sup> https://mp.weixin.qq.com/s/f17POwoG-VJf-WZJjSuymg