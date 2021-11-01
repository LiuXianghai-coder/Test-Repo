# Spring Boot 简介

`Spring Boot`  的出现使得创建一个单独的应用变得容易，它的出现是为了简化 `Spring`应用程序的开发，它有如下几个特点

- 可以创建一个单独的 Spring 应用
- 内嵌的 `Tomcat`、`Jetty`，因此不再需要将应用打包成为一个 `WAR` 文件去单独部署
- 提供简化的  `starter` 依赖项来简化构建应用程序的配置，这里的 `starter` 依赖项自动包含了这个 `starter` 需要的依赖，因此无需再对这个依赖项进行额外的依赖配置
- 可以自动地配置第三方依赖库
- 提供了生产环境的特征，如指标、健康检查和额外的配置
- 不再需要使用 `XML` 的配置，绝对



## 基本使用

### 启动应用程序

```java
@SpringBootApplication // 这个注解表示是一个 SpringBoot 应用程序
public class BatchProcessingApplication {
    public static void main(String[] args) {
        // 这里是 SpringBoot 应用程序真正启动的地方，启动时会加载当前类的包路径及以下包的所有 Spring 组件类
        SpringApplication.run(BatchProcessingApplication.class, args) // 这个方法的运行会返回一个 Spring 上下文对象
    }
}
```

由于在启动时只会加载被 `@SpringBotApplication` 注解修饰的类的当前路径及子路径下的所有 Spring 组件，因此在这个包外面的组件类便无法被加载到。为了加载能够加载其它的包文件，可以配置 `@SpringBootApplication` 注解中的 `scanBasePackages` 来配置扫描包或者通过 `scanBasePackageClasses` 来手动将外部的组件类配置到 `SpringApplication` 中。

具体如下所示：

```java
@SpringBootApplication(scanBasePackages = "org.xhliu") // 启动时扫描 org.xhliu 包下的所有组件类
@SpringBootApplication(scanBasePackageClasses = {org.xhliu.OuterController.class}) // 启动时将 org.xhliu.OuterController 外部组件类加载到 Spring 容器中
```

以上的扫面组件类的方式加载会创建对应的 `Bean`名称，一般是以小写开头的简短类名。



除了上面的两种扫描的方式加载外部 组件类的方式外，也可以使用 `@Import` 注解的方式来显式地将组件类导入到 `Spring` 容器中，具体实例如下所示：

```java
@Import(value = {org.xhliu.OuterController.class}) // 显式地将外部组件类加载到这个配置类中
@SpringBootApplication
public class BatchProcessingApplication {
    // 省略启动的应用的代码
}
```

这种方式会在在引入时，对应的 `Bean` 名称会是类的全限定名称，与上文的简短类名 `Bean` 有很大不同



### Bean 的配置

首先定义两个实体类 `Car` 和 `Wheel`

`Car`：

```java
public class Car {
    private int number;
    private Wheel wheel;
    
    // 省略一部分 Object 方法和 getter、setter 方法
}
```

`Wheel`：

```java
public class Wheel {
    private int radius;
    private String color;
    
    // 省略一部分 Object 方法和 getter、setter 方法
}
```

现在，在一个能够被 `SpringApplication` 扫描到的包下创建一个配置类

```java
@Configuration // 标记这个类是一个配置类，由于 @Configuration 包含了 @Component ，因此它也是一个组建类
public class OuterController {
    // 在 Spring 中创建的 Bean 都是单例的
    
    @Bean(name = "car") // 这里创建一个名为 car 的 bean
    public Car car() {
        Car car = new Car();
        car.setNumber(1);
        car.setWheel(wheel());
        return car;
    }

    @Bean(name = "wheel") // 创建一个名为 wheel 的 bean
    public Wheel wheel() {
        Wheel wheel = new Wheel();
        wheel.setColor("Red");
        wheel.setRadius(30);

        return wheel;
    }
}
```

