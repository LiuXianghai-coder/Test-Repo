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



#### 配置 Bean

现在，在一个能够被 `SpringApplication` 扫描到的包下创建一个配置类

```java
@Configuration // 标记这个类是一个配置类，由于 @Configuration 包含了 @Component ，因此它也是一个组建类
public class OuterController {
    // 在 Spring 中创建的 Bean 都是单例的
    
    @Bean(name = "wheel") // 创建一个名为 wheel 的 bean
    public Wheel wheel() {
        Wheel wheel = new Wheel();
        wheel.setColor("Red");
        wheel.setRadius(30);

        return wheel;
    }
    
    @Bean(name = "car") // 这里创建一个名为 car 的 bean
    public Car car() {
        Car car = new Car();
        car.setNumber(1);
        /* 
        	这里会将名为 wheel 的 bean 注入到 car 中，如果没有将上文 wheel() 方法使用 @Bean 进行标记，
        	那么就会直接调用 whell() 方法而不是注入 Bean
        */
        car.setWheel(wheel());
        return car;
    }
}
```

现在，在 Spring 的容器中就可以发现这两个 Bean，同时可以通过这两个 Bean 的名称来引用这两个 Bean



#### 条件 Bean

当一个 Bean 初始化时需要其它 Bean 时，如果这两个 Bean 的关系是强关联的，那么可以使用条件 Bean 的方式来配置 Bean，当初始化 Bean 时需要的 Bean 不存在，就会初始化失败，可以通过为 Bean 添加 `@ConditionalOnBean` 注解来表明这种关系。

以上文的例子为例：

```java
@Bean(name = "car")
@ConditionalOnBean(name = "wheel") // 当 wheel Bean 不存在时，将不会初始化这个 car Bean
public Car car() {
    Car car = new Car();
    car.setNumber(1);
    car.setWheel(wheel());
    return car;
}
```



#### 自定义配置属性 Bean

类似于 `server.port` 等配置属性，实际上是将配置的属性值设置到通过 `@ConfigurationProperties`  标记的自定义配置类中来实现的，当启动 Spring 应用程序时，将会加载对应的配置属性值到这些 Bean，因此能够获取到配置的属性值。

可以自定义一个实体类，定义相关的属性，添加  `@ConfigurationProperties`  注解来实现自定义需要的配置属性

首先需要需要加入以下依赖项：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <version>${spring.boot.version}</version>
    <optional>true</optional>
</dependency>
```

只有在加入了这个依赖项之后，才能正常使用自定义的配置类



定义一个配置类：

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component // 标记这个类为组件类，这样才能读取配置的属性并放入到 Spring 容器中
@ConfigurationProperties(prefix = "mine") // 开启配置属性，前缀为 mine 的配置的所有定义属性都将注入到这个实体类中
public class MyProperties {
    private String name;

    private String age;
    
    // 省略一部分 setter 和 getter 方法
}
```



在 `resource/META-INF/additional-spring-configuration-metadata.json` 定义这些配置属性的类型和描述，如果没有这个目录和文件，可以手动创建它

看起来像下面这样：

```json
{
  "properties": [
    {
      "name": "mine.name",
      "type": "java.lang.String",
      "description": "Just Properties Name mine.name."
    },
    {
      "name": "mine.age",
      "type": "java.lang.Integer",
      "description": "Just a Properties mine.age."
    }
  ]
}
```



现在，在 `application.yml` 文件中定义自定义的属性：

```yaml
mine: # 这个前缀是之前在配置属性类中定义的，表示这是我们自定义的配置属性
  name: xhliu
  age: 22
```

在定义配置属性之后，有的 IDE 可能会显示一个警告，这是因为 IDE 的缓存数据为例，更新这些缓存数据即可。以 IDEA 为例，出现这样的问题只需要点击 `File ——> Invalidate Caches ——> Invalidate and Restart`



现在，启动 Spring 应用程序，可以看到对应的 Bean，通过 Spring 上下文对象可以获得这个 Bean

```java
MyProperties myProperties = (MyProperties) context.getBean("myProperties"); // 注意上文提到 Spring 将会创建小写字母开头的命名 Bean
```





## Spring Boot 启动流程

### 加载配置类

#### @SpringBootApplication

该注解包含的注解如下：

```java
// 下面的三个注解是 SpringBoot 的核心注解
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = { @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class) })
public @interface SpringBootApplication
```



#### @SpringBootConfiguration

```java
// 这个注解的主要目的是将 @SpringBootApplication 标记的类标记为一个配置类
@Configuration
public @interface SpringBootConfiguration
```



#### @ComponentScan

```java
// 这个注解的主要作用是表示被这个注解标记的类具有组件扫描的能力
@Repeatable(ComponentScans.class)
public @interface ComponentScan
```

`ComponentScans` 类定义如下：

```java
public @interface ComponentScans {
	ComponentScan[] value(); // 包含要扫描的组件的类路径信息等
}
```



#### @EnableAutoConfiguration

```java
@AutoConfigurationPackage
@Import(AutoConfigurationImportSelector.class)
public @interface EnableAutoConfiguration 
```

##### @AutoConfigurationPackage

```java
@Import(AutoConfigurationPackages.Registrar.class) // 导入 AutoConfigurationPackages.Registrar.class 对象，或者简单理解为一个 Bean
public @interface AutoConfigurationPackage
```

`AutoConfigurationPackages.Registrar` 定义如下：

```java
static class Registrar implements ImportBeanDefinitionRegistrar, DeterminableImports {
    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        /*
        	new PackageImports(metadata).getPackageNames() 会得到当前 SpringApplication 所处的包目录，
        	即 SpringBootApplication 启动时会扫描的包路径
        */
        register(registry, new PackageImports(metadata).getPackageNames().toArray(new String[0]));
    }

    @Override
    public Set<Object> determineImports(AnnotationMetadata metadata) {
        return Collections.singleton(new PackageImports(metadata));
    }

}
```



##### AutoConfigurationImportSelector

```java
public class AutoConfigurationImportSelector implements ...{ // 省略一部分实现的接口
	// 省略一部分代码。。。。
	// 处理自动配置导入的方法
	@Override
    public void process(AnnotationMetadata annotationMetadata, DeferredImportSelector deferredImportSelector) {
        // 省略一部分断言代码。。。。
        AutoConfigurationEntry autoConfigurationEntry = ((AutoConfigurationImportSelector) deferredImportSelector)
            .getAutoConfigurationEntry(annotationMetadata); // 处理配置类，主要是被相关注解修饰的类
        // 省略一部分代码。。。。 
    }
    
    protected AutoConfigurationEntry getAutoConfigurationEntry(AnnotationMetadata annotationMetadata) {
		// 省略一部分代码。。。。
		AnnotationAttributes attributes = getAttributes(annotationMetadata);
		List<String> configurations = getCandidateConfigurations(annotationMetadata, attributes); // 加载候选配置
		// 省略一部分不太重要的代码。。。。
		return new AutoConfigurationEntry(configurations, exclusions);
	}
    
    // 获取候选配置类
    protected List<String> getCandidateConfigurations(AnnotationMetadata metadata, AnnotationAttributes attributes) {
		List<String> configurations = SpringFactoriesLoader.loadFactoryNames(getSpringFactoriesLoaderFactoryClass(),
				getBeanClassLoader());
		Assert.notEmpty(configurations, "No auto configuration classes found in META-INF/spring.factories. If you "
				+ "are using a custom packaging, make sure that file is correct.");
		return configurations;
	}
            
	// 省略一部分代码。。。。
}
```

`SpringFactoriesLoader`：

```java
public final class SpringFactoriesLoader {
    public static final String FACTORIES_RESOURCE_LOCATION = "META-INF/spring.factories";
    // 省略一部分代码。。。。
    
    public static List<String> loadFactoryNames(Class<?> factoryType, @Nullable ClassLoader classLoader) {
		// 省略一部分不太重要的代码
		return loadSpringFactories(classLoaderToUse).getOrDefault(factoryTypeName, Collections.emptyList());
	}
    
    private static Map<String, List<String>> loadSpringFactories(ClassLoader classLoader) {
		Map<String, List<String>> result = cache.get(classLoader);
		// 省略缓存检查代码。。。。
		result = new HashMap<>();
        /*
        	这里是加载配置类的核心部分，定义了配置类的加载路径为 META-INF/spring.factories
        */
		Enumeration<URL> urls = classLoader.getResources(FACTORIES_RESOURCE_LOCATION); 
        // 省略一部分配置类加载代码、异常检查代码等
		return result;
	}
}
```

配置类加载原理：

1. 查找项目依赖的所有 `jar` 包
2. 遍历每个 `jar` 包下的 `META-INF/spring-factories` 文件（有的 `jar` 包不存在这个文件）
3. 将所有的 `spring.factories` 文件加载到 Spring IOC 容器中
4. 加载满足条件的配置类



### 启动

具体的启动代码如下：

```java
public static ConfigurableApplicationContext run(Class<?>[] primarySources, String[] args) {
    return new SpringApplication(primarySources).run(args);
}
```



#### 创建 `SpringApplication` 对象

对应源代码如下：

```java
// 这里的主要任务是初始化相关的资源
public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
    this.resourceLoader = resourceLoader; // 初始化资源加载器，默认为 null
    Assert.notNull(primarySources, "PrimarySources must not be null");
    /*
    	初始化 primarySource，主要的资源类集合
    */
    this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
   
    /*
    	Web 应用程序的类型，包括 REACTIVE、NONE、SERVLET
    */
    this.webApplicationType = WebApplicationType.deduceFromClasspath();
    
    /*
    	初始化引导器，bootstrapRegistryInitializers 引导应用程序注册
    	
    	从 META-INF/spring.factories 文件中获取 Bootstrapper.class 和 BootstrapRegistryInitializer.class，然后合并到
    	bootstrapRegistryInitializers 中
    */
    this.bootstrapRegistryInitializers = getBootstrapRegistryInitializersFromSpringFactories();
    
    /*
    	List<ApplicationContextInitializer<?>> initializers 应用上下文初始器
    	
    	初始化 ApplicationContextInitializer，这个类同样地使用 getSpringFactoriesInstances 
    	方法从 META-INF/spring.factories 文件中加载而来
    */
    setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
    
    /*
    	List<ApplicationListener<?>> listeners 应用监听器
    	同样地，通过使用 getSpringFactoriesInstances 方法从 META-INF/spring.factories 文件中加载而来
    */
    setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));
    
    /*
    	找到 main 方法所在的类
    */
    this.mainApplicationClass = deduceMainApplicationClass();
}
```

判断 Web 应用程序类型的源代码

```java
// 通过能够加载到的类来判断 Web 应用程序所属的类型
static WebApplicationType deduceFromClasspath() {
    if (ClassUtils.isPresent(WEBFLUX_INDICATOR_CLASS, null) 
        && !ClassUtils.isPresent(WEBMVC_INDICATOR_CLASS, null)
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

获取 `main` 方法所在的主类

```java
// 通过跟踪方法的调用栈，从上往下找到 main 方法坐在的类
private Class<?> deduceMainApplicationClass() {
    try {
        StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace) {
            if ("main".equals(stackTraceElement.getMethodName())) { // 一个运行的程序只会有一个 main 方法
                return Class.forName(stackTraceElement.getClassName());
            }
        }
    }
    catch (ClassNotFoundException ex) {
        // Swallow and continue
    }
    return null;
}
```



#### 调用 `run` 方法

