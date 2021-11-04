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
    	这里的实现是通过发现相关的类是否存在来进行判断的
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



核心的方法 **`getSpringFactoriesInstances`**，通过这个方法将 `spring.factories` 中加载需要的对象，具体的源代码如下所示：

```java
private <T> Collection<T> getSpringFactoriesInstances(Class<T> type) {
    return getSpringFactoriesInstances(type, new Class<?>[] {});
}

private <T> Collection<T> getSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes, Object... args) {
    ClassLoader classLoader = getClassLoader(); // 获取当前应用的类加载器
    
    /*
    	SpringFactoriesLoader.loadFactoryNames 方法是 SpringFramework 中的方法，主要的任务是读取 spring.factories 文件中的类的全限定名
    */
    Set<String> names = new LinkedHashSet<>(SpringFactoriesLoader.loadFactoryNames(type, classLoader));
    
    /*
    	通过类的权限定名来实例化这些类
    */
    List<T> instances = createSpringFactoriesInstances(type, parameterTypes, classLoader, args, names);
    
    AnnotationAwareOrderComparator.sort(instances);
    
    return instances;
}
```



`SpringApplication` 的实例化流程如下所示：

![SpringApplication.png](https://i.loli.net/2021/11/03/yPJGRdVM3aergn8.png)



#### 调用 `run` 方法

具体有以下执行流程：

- 创建引导启动器的上下文

  具体源代码如下所示：

  ```java
  // 创建启动引导器的上下文
  private DefaultBootstrapContext createBootstrapContext() {
      DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
  
      /*
      	遍历从创建 SpringApplication 初始化的 bootstrapRegistryInitializers，调用它们的 initialize 方法
      	初始化后的相关内容将会放到 BootstrapContext 对象中
      */
      this.bootstrapRegistryInitializers.forEach((initializer) -> initializer.initialize(bootstrapContext));
  
      // 返回初始化之后的 BootStrapContext
      return bootstrapContext;
  }
  ```

  

- 获取所有的 `SpringApplicationRunListener` 实例对象

  具体源代码如下所示：

  ```java
  // 获取所有的 SpringApplicationRunListener 实例对象
  private SpringApplicationRunListeners getRunListeners(String[] args) {
      Class<?>[] types = new Class<?>[] { SpringApplication.class, String[].class };
  
      return new SpringApplicationRunListeners(
          logger, // logger 为 SpringApplication 类的静态变量，用于打印日志相关的信息
          /** 
          	获取所有的jar包依赖的META-INF/spring.factories的配置文件中有配置了
          	"org.springframework.boot.SpringApplicationRunListener"的内容，并进行实例的初始化
          */
          getSpringFactoriesInstances(SpringApplicationRunListener.class, types, this, args),
          this.applicationStartup // applicationStartup 默认为 DefaultApplicationStartup
      ); 
  }
  ```

- 通过 `SpringApplicationRunListener` 对象的 `starting`  方法启动所有的 `BootStrapContext`

  具体源代码如下所示：

  ```java
  // 启动 BootStrapContext
  void starting(ConfigurableBootstrapContext bootstrapContext, Class<?> mainApplicationClass) {
      doWithListeners(
          "spring.boot.application.starting", 
          (listener) -> listener.starting(bootstrapContext),
          (step) -> {
              if (mainApplicationClass != null) {
                  step.tag("mainApplicationClass", mainApplicationClass.getName());
              }
          });
  }
  
  private void doWithListeners(
      String stepName, 
      Consumer<SpringApplicationRunListener> listenerAction,
      Consumer<StartupStep> stepAction
  ) {
      /*
      	applicationStartup 属性在  getRunListeners 方法调用时设置，默认为 DefaultApplicationStartup
      */
      StartupStep step = this.applicationStartup.start(stepName);
      /*
      	对每个存在的 SpringApplicationRunListener 执行相同的函数处理
      	
      	listeners 属性字段同样在 getRunListeners 方法调用是初始化，
      	从 spring.factories 文件中读取 SpringApplicationRunListener 对应的类并实例化到 listeners 中
      */
      this.listeners.forEach(listenerAction);
  
      if (stepAction != null) {
          stepAction.accept(step); // 接收传入的参数，标记为一个步骤标签
      }
      step.end();
  }
  ```

- 解析命令行参数，将它们解析到 `ApplicationArguments` 对象中

  对应的源代码如下所示：

  ```java
  // 读取在启动 SpringApplication 时传入的参数 args，将这些参数进行解析
  ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
  ```

  `DefaultApplicationArguments`的实例化源代码：

  ```java
  public DefaultApplicationArguments(String... args) {
      Assert.notNull(args, "Args must not be null");
      this.source = new Source(args); // 在实例化 Source 对象时会完成相关参数的解析
      this.args = args;
  }
  ```

  具体的参数解析是通过 `SimpleCommandLineArgsParser` 的 `parse(String ...args)` 方法来完成的，具体源代码如下所示：

  ```java
  public CommandLineArgs parse(String... args) {
      CommandLineArgs commandLineArgs = new CommandLineArgs(); // 用于存储解析后的参数
      for (String arg : args) {
          if (arg.startsWith("--")) { // 以 -- 开头的参数为选项参数
              String optionText = arg.substring(2);
              String optionName;
              String optionValue = null;
              int indexOfEqualsSign = optionText.indexOf('=');
              if (indexOfEqualsSign > -1) {
                  optionName = optionText.substring(0, indexOfEqualsSign);
                  optionValue = optionText.substring(indexOfEqualsSign + 1);
              }
              else {
                  optionName = optionText;
              }
              if (optionName.isEmpty()) {
                  throw new IllegalArgumentException("Invalid argument syntax: " + arg);
              }
              commandLineArgs.addOptionArg(optionName, optionValue);
          }
          else {
              commandLineArgs.addNonOptionArg(arg);
          }
      }
      return commandLineArgs;
  }
  ```

  

- 准备环境

  具体的源代码如下所示：

  ```java
  private ConfigurableEnvironment prepareEnvironment(
      SpringApplicationRunListeners listeners,
      DefaultBootstrapContext bootstrapContext, 
      ApplicationArguments applicationArguments
  ) {
      /*
      	根据当前的 Spring 应用程序的类型，创建不同的配置环境，一般会创建 Spring Web 应用程序，
      	因此创建的配置环境对象为 ApplicationServletEnvironment
      */
      ConfigurableEnvironment environment = getOrCreateEnvironment();
  
      /*
      	通过上一步解析得到的命令行参数，配置 enviroment 对象
      	然而，在一般情况下只是向 enviroment 对象中设置类型转换器
      */
      configureEnvironment(environment, applicationArguments.getSourceArgs());
  
      /*
      	将 "configurationProperties" 插入或者更新到 environment 的 
      	propertySources 的 propertySourceList 集合的第一个index中 
      */
      ConfigurationPropertySources.attach(environment);
  
      /** 
      	遍历调用 SpringApplicationRunListener 的 environmentPrepared(...) 方法 
      	这里在运行时的步骤名为 spring.boot.application.environment-prepared
      **/
      listeners.environmentPrepared(bootstrapContext, environment);
  
      /** 
      	将环境信息设置到 MutablePropertySources propertySources 的最后 
      **/
      DefaultPropertiesPropertySource.moveToEnd(environment);
  
      Assert.state(!environment.containsProperty("spring.main.environment-prefix"),
                   "Environment prefix cannot be set via properties.");
  
      /** 
      	将环境绑定到SpringApplication 
      */
      bindToSpringApplication(environment);
  
      if (!this.isCustomEnvironment) {
          /** 将环境封装为StandardEnvironment */
          environment = new EnvironmentConverter(getClassLoader())
              .convertEnvironmentIfNecessary(environment,
                                             deduceEnvironmentClass());
      }
  
      /** 执行绑定操作 */
      ConfigurationPropertySources.attach(environment);
  
      return environment;
  }
  ```

  

- 配置系统参数

  具体源代码如下所示：

  ```java
  // 配置系统参数 spring.beaninfo.ignore，这个配置表示是否跳过 java BeanInfo 的搜索
  private void configureIgnoreBeanInfo(ConfigurableEnvironment environment) {
      if (System.getProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME) == null) {
          Boolean ignore = environment.getProperty("spring.beaninfo.ignore", Boolean.class, Boolean.TRUE);
          System.setProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME, ignore.toString());
      }
  }
  ```

  

- 打印 `Banner`

  打印 Banner 主要有以下 3 种模式：

  ```java
  enum Mode {
      OFF, // 禁止打印 Banner
      CONSOLE, // 将 Banner 打印到控制台
      LOG // 将 Banner 打印到日志文件
  }
  
  // 具体可以通过创建的 SpringApplication 来设置打印模式
  ```

  获取文本 Banner 的源代码如下所示：

  ```java
  static final String BANNER_LOCATION_PROPERTY = "spring.banner.location"; // 文本 Banner 的位置
  
  static final String DEFAULT_BANNER_LOCATION = "banner.txt"; // 默认加载的 Banner 文件，位于 reources 目录下
  
  private Banner getTextBanner(Environment environment) {
      String location = environment.getProperty(BANNER_LOCATION_PROPERTY, DEFAULT_BANNER_LOCATION);
      Resource resource = this.resourceLoader.getResource(location);
      try {
          if (resource.exists() && !resource.getURL().toExternalForm().contains("liquibase-core")) {
              return new ResourceBanner(resource);
          }
      }
      catch (IOException ex) {
          // Ignore
      }
      return null;
  }
  ```

  因此如果想要自定义打印的 Banner 的话，可以在 `reources` 目录下添加 `banner.txt`，放入需要打印的 Banner 内容即可

  具体的 Banner 生成器可以使用：https://devops.datenkollektiv.de/banner.txt/index.html



分割线——————————————————————————————————————————

上面的部分是初始的准备阶段，下面的部分是和应用上下文相关的部分，主要涉及到 `IOC` 的 `Bean` 装载等

- 创建应用上下文

  对应的源代码如下所示：

  ```java
  ApplicationContextFactory DEFAULT = (webApplicationType) -> {
      try {
          switch (webApplicationType) {
              case SERVLET:
                  // 一般是 Spring Web 应用，因此会选择实例化这个类
                  return new AnnotationConfigServletWebServerApplicationContext();
              case REACTIVE:
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

  - `AnnotationConfigServletWebServerApplicationContext` 对应的实例化源代码：

    ```java
    public AnnotationConfigServletWebServerApplicationContext() {
        this.reader = new AnnotatedBeanDefinitionReader(this);
        this.scanner = new ClassPathBeanDefinitionScanner(this);
    }
    ```

    `AnnotatedBeanDefinitionReader` 对应的源代码

    ```java
    public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, Environment environment) {
        // 省略一部分参数断言代码
        this.registry = registry;
        // 用于处理 @Conditional 注解修饰的 Bean
        this.conditionEvaluator = new ConditionEvaluator(registry, environment, null);
        // 在给定的 BeanDefinitionRegistry 中注册所有跟注解相关联的后置处理器
        AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
    }
    ```

    注册后置处理器的具体源代码：

    ```java
    public static Set<BeanDefinitionHolder> registerAnnotationConfigProcessors(
    			BeanDefinitionRegistry registry, @Nullable Object source) {
        DefaultListableBeanFactory beanFactory = unwrapDefaultListableBeanFactory(registry);
        if (beanFactory != null) {
            if (!(beanFactory.getDependencyComparator() instanceof AnnotationAwareOrderComparator)) {
                beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE); // 依赖注入的 Bean 优先级比较
            }
            if (!(beanFactory.getAutowireCandidateResolver() instanceof ContextAnnotationAutowireCandidateResolver)) {
                beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver()); // @Autowire 注入时候选 Bean 的处理方案
            }
        }
        // 省略一部分添加 BeanDefinitionHolder 的源代码
        
        return beanDefs;
    }
    ```

  

- 准备应用上下文

  对应的源代码如下所示：

  ```java
  private void prepareContext(DefaultBootstrapContext bootstrapContext, ConfigurableApplicationContext context,
                              ConfigurableEnvironment environment, SpringApplicationRunListeners listeners,
                              ApplicationArguments applicationArguments, Banner printedBanner) {
      /** 
      	往IOC容器中，保存环境信息environment 
      */
      context.setEnvironment(environment);
  
      /** 
      	IOC容器的后置处理流程 
     	*/
      postProcessApplicationContext(context);
  
      /** 
      	应用初始化器
      	这里是对在 实例化 SprinApplication 时加载的 ApplicationContextInitializer 执行 initialize 方法
     	*/
      applyInitializers(context);
  
      /** 
      	遍历从 spring.factories 中加载的 SpringApplicationRunListener 的配置类，在上下文对象中执行 contextPrepared(...) 方法
      */
      listeners.contextPrepared(context);
  
      bootstrapContext.close(context);
      if (this.logStartupInfo) {
          logStartupInfo(context.getParent() == null);
          logStartupProfileInfo(context);
      }
      // Add boot specific singleton beans
      ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
      beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
      if (printedBanner != null) {
          beanFactory.registerSingleton("springBootBanner", printedBanner);
      }
      if (beanFactory instanceof DefaultListableBeanFactory) {
          ((DefaultListableBeanFactory) beanFactory).setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
      }
      if (this.lazyInitialization) {
          context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
      }
      // Load the sources
      Set<Object> sources = getAllSources();
      Assert.notEmpty(sources, "Sources must not be empty");
      load(context, sources.toArray(new Object[0]));
  
      /** 
      	遍历调用 SpringApplicationRunListener 的 contextPrepared(...) 方法 
      */
      listeners.contextLoaded(context);
  }
  ```

  

- 刷新应用上下文

  这一部分的源代码对应如下：

  ```java
  private void refreshContext(ConfigurableApplicationContext context) {
      if (this.registerShutdownHook) {
          shutdownHook.registerApplicationContext(context);
      }
      /** Spring IOC核心的初始化过程 */
      refresh(context);
  }
  ```

  

- 在刷新应用上下文之后进行后置处理

- 启动 `SpringBoot` 的应用监听器

- 调用所有的 `CommandRunner` 的 `run `

- 调用 `SpringApplication` 的所有 `running` 方法