# Spring WebFlux 简介

基于之前提到的 `Reactor` 的出现，使得编写响应式程序成为可能。为此，Spring 的开发团队决定添加有关 `Reactor` 模型的网络层。这样做的话将会对 Spring MVC 作出许多重大的修改，因此 Spring 的研发团队决定开发一个单独的响应式处理框架，随之，Spring WeFlux 就这么诞生了。

Spring WebFlux 与 Spring MVC 的关系如下：

![image.png](https://i.loli.net/2021/11/28/SQ8PfkCvLhiUupx.png)

Spring WebFlux 的大部分内容都借鉴了 Spring MVC，许多在 Spring MVC 上使用的注解在 Spring WebFlux 上依旧是可用的，但是 Spring WebFlux 会为此作出特定于 `Reactor` 的实现



## 基本使用

### 注解

依旧可以使用在 Spring MVC 中存在的那些注解

```java
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping(path = "/hello")
public class HelloController {
    @GetMapping(path = "") // MVC 相关
    public String hello(Model model) {
        model.addAttribute("name", "xhliu2");
        return "hello";
    }

    @GetMapping(path = "/test") // Rest。。。。
    public @ResponseBody
    Mono<String> test() {
        return Mono.just("xhliu2"); // 每个请求都将创建一个 Mono 流，在对请求作出响应时将会将这些流组合到一起（flatMap），因此整个请求整体来讲将会是非阻塞的
    }
}
```



### RouteFunction

通过 `RouteFunction` 来定义请求处理：

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Configuration
public class RouterController {
    /*
    	通过 RouterFunction 来定义处理逻辑。。。。
    */
    @Bean
    public RouterFunction<ServerResponse> route1() {
        return RouterFunctions.route(
            RequestPredicates.GET("/route1"), // 定义请求方法和路径
            // 使用函数式的方式来处理请求，这是为了结合 Reactor 的最佳处理方式（非阻塞）
            request -> ServerResponse.ok().body(Mono.just("This is a Mono Sample"), String.class)
        );
    }
}
```

如果需要定义多个请求路径，可以额外定义一个 `RouterFunction` 的 Bean，也可以在一个 `RouterFunction` Bean 中定义额外的处理路径和处理逻辑

```java
@Bean
public RouterFunction<ServerResponse> route2() {
    return RouterFunctions.route( // 第一个处理逻辑。。。。
        RequestPredicates.GET("/route2"),
        request -> ServerResponse.ok().body(Mono.just("This is route2"), String.class)
    ).andRoute( // 定义的第二个处理。。。
        RequestPredicates.GET("/route3"),
        request -> ServerResponse.ok().body(Mono.just("This is route3"), String.class)
    );
}
```

也可以通过预先定义的 Bean 的相关的方法，使用函数式编程的方式来处理对应的逻辑：

首先定义一个 Bean，用于定义一些逻辑处理：

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component(value = "handleA")
public class HandlerA {
    private final static Logger log = LoggerFactory.getLogger(HandlerA.class);

    public Mono<ServerResponse> echo(ServerRequest request) {
        log.info("Ready to echo......");

        return ServerResponse.ok().body(Mono.just(request.queryParams().toString()), String.class);
    }
}
```

再定义对应的路径的处理逻辑：

```java
@Bean
public RouterFunction<ServerResponse> route3(@Autowired HandlerA handlerA) { 
    return RouterFunctions.route(
        RequestPredicates.GET("/route4"),
        handlerA::echo
    );
}
```



## 源码解析

### WebFlux 的初始化

1. 根据 `classpath` 来判断当前的 web 应用所属的类型

   ```java
   // org.springframework.boot.SpringApplication。。。
   public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
       this.webApplicationType = WebApplicationType.deduceFromClasspath();
       // 省略一部分不太相关的代码。。。。
   }
   ```

   `deduceFromClasspath()` 方法对应的源代码：

   ```java
   // 该方法位于 org.springframework.boot.WebApplicationType 中
   
   private static final String WEBMVC_INDICATOR_CLASS = "org.springframework.web.servlet.DispatcherServlet";
   
   private static final String WEBFLUX_INDICATOR_CLASS = "org.springframework.web.reactive.DispatcherHandler";
   
   private static final String JERSEY_INDICATOR_CLASS = "org.glassfish.jersey.servlet.ServletContainer";
   
   static WebApplicationType deduceFromClasspath() {
       /*
       	如果当前加载的 Class 中，WEBFLUX_INDICATOR_CLASS 已经被加载并且 WEBMVC_INDICATOR_CLASS 和 JERSEY_INDICATOR_CLASS 都没有被加载的情况下，才会认为当前的 Web 应用的类型是 Reactive 的
       */
       if (ClassUtils.isPresent(WEBFLUX_INDICATOR_CLASS, null) && !ClassUtils.isPresent(WEBMVC_INDICATOR_CLASS, null)
           && !ClassUtils.isPresent(JERSEY_INDICATOR_CLASS, null)) {
           return WebApplicationType.REACTIVE;
       }
       for (String className : SERVLET_INDICATOR_CLASSES) {
           if (!ClassUtils.isPresent(className, null)) {
               return WebApplicationType.NONE;
           }
       }
       return WebApplicationType.SERVLET;
   }
   ```

2. 创建 `Reactive` 应用上下文

   创建应用上下文对应的源代码：

   ```java
   // 该源代码位于 org.springframework.boot.ApplicationContextFactory 中。。。
   // 注意这里的 Lamada 表达式。。。
   ApplicationContextFactory DEFAULT = (webApplicationType) -> {
       try {
           switch (webApplicationType) {
               case SERVLET:
                   return new AnnotationConfigServletWebServerApplicationContext();
               case REACTIVE: // 根据上一步推断出的 Web 应用类型为 Reactive，因此会走这
                   return new AnnotationConfigReactiveWebServerApplicationContext();
               default:
                   return new AnnotationConfigApplicationContext();
           }
       }
       catch (Exception ex) {
           throw new IllegalStateException("Unable create a default ApplicationContext instance, "
                                           + "you may need a custom ApplicationContextFactory", ex);
       }
   };
   ```

   实例化 `AnnotationConfigReactiveWebServerApplicationContext` 对应的源代码：

   ```java
   // 一些基本的 Spring IOC 的内容。。。。具体细节可以查看有关 Spring IOC 部分的内容
   public AnnotationConfigReactiveWebServerApplicationContext() {
       this.reader = new AnnotatedBeanDefinitionReader(this);
       this.scanner = new ClassPathBeanDefinitionScanner(this);
   }
   ```

3. 