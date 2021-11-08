# Spring IOC 解析

## Bean 容器的创建

`ApplicationContext` 的类结构：

<img src="https://www.javadoop.com/blogimages/spring-context/1.png" style="zoom:60%" />

通过上图可以看到，具体的 `ApplicationContext` 有 `AnnotationConfigApplicationContext`、`FileSystemXmlApplicationContext`、`ClassPathXmlApplicationContext` 等几种具体的实现，在创建 `IOC` 容器时可以通过这三种 `ApplicationContext` 实例对象来创建。

这三个 `ApplicationContext` 的主要区别如下：

1. `ClassPathXmlApplicationContext`：加载类路径下的 `XML` 配置文件，通过解析 `XML` 文件来加载相关的 `Bean` 属性
2. `FileSystemXmlApplicationContext`：通过自定义 `XML` 文件的路径来加载对应的 `XML` 配置文件，使用方式和 `ClassPathXmlApplicationContext` 一致
3. `AnnotationConfigApplicationContext`：通过对相关的类添加对应的注解来实现 `Bean` 的装载，相比较于使用 `XML` 的配置方式，使用注解的方式更加简单和方便



由于使用 `XML` 配置的方式对应的源代码比较简单，因此首先从 `ClassPathXmlApplicationContext` 类对 `IOC` 容器的初始化进行分析



### `BeanFactory`

`BeanFactory` 是一个基本的 `bean` 容器视图，负责生产和管理 `bean` 实例，这个接口是 `IOC` 的核心部分

相关的类结构图如下所示：

<img src="https://www.javadoop.com/blogimages/spring-context/2.png" style="zoom:60%" />

- `ApplicationContext` 继承了 `ListableBeanFactory`，通过继承 `ListableBeanFactory`，便能够一次获取多个 `bean` 实例（`BeanFactory` 一次只能获取一个 `Bean` 实例）；通过继承 `HierarchicalBeanFactory` ，使得能够在应用中设置 `BeanFactory` 的父子关系
- `AutowireCapableBeanFactory` 用于自动装配 `bean`
- `ConfigurableListableBeanFactory` 继承了 `ListableBeanFactory`、`ListableBeanFactory`、`AutowireCapableBeanFactory` 三个接口



### 启动过程

以 `ClassPathXmlApplicationContext` 创建 `IOC` 为例，具体的实例化方法如下：

```java
public ClassPathXmlApplicationContext(
    String[] configLocations, 
    boolean refresh, 
    @Nullable ApplicationContext parent
) throws BeansException {
    
    super(parent);
    // 设置当前加载的资源文件的路径
    setConfigLocations(configLocations);
    if (refresh) {
        /* 
        	具体创建 IOC 的核心方法，这个方法会销毁原有的 ApplicationContext，
        	然后在执行一次初始化操作
        */
        refresh();
    }
}
```



具体 `refresh()` 方法的源代码：

```java
public void refresh() throws BeansException, IllegalStateException {
    // 同步化创建过程
    synchronized (this.startupShutdownMonitor) {
        StartupStep contextRefresh = 
            this.applicationStartup.start("spring.context.refresh");

        /*
        	创建容器之前的准备工作，具体为：记录当前启动时的时间戳、将当前状态标记为 “活跃” 状态、初始化和验证配置属性、存储在创建 ApplicationContext 之前的应用监听器列表
        */
        prepareRefresh();

        /*
        	这里时比较关键的一步
        	在这里首先会销毁之前的 Bean 工厂并关闭，然后创建一个新的 Bean 工厂，同时将配置文件中定义的 Bean 加载到这个新创建的 Bean 工厂中（此时的 Bean 没有被实例化）
        */
        ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

        /*
        	设置当前上下文的类加载器和一些创建 Bean 工厂之后的后置处理
        */
        prepareBeanFactory(beanFactory);

        try {
            /*
            	提供给子类的扩展点，在整个阶段，所有的 Bean 都已经被加载、注册到 Bean 工厂中了，
            	具体的子类可以在实现时定义一些特定的处理
            */
            postProcessBeanFactory(beanFactory);
            
            // 一个新的处理步骤，简单地标记一下
            StartupStep beanPostProcess = this.
                applicationStartup.start("spring.context.beans.post-process");
            
            /*
            	实例化所有注册到 Bean 工厂的 BeanFactoryPostProcessor 类型的 Bean，
            	并且调用这些 Bean 的 postProcessBeanFactory 方法
            	
            	这里的实例化 Bean 的任务是通过 org.springframework.beans.factory.support.AbstractBeanFactory 来完成的
            */
            invokeBeanFactoryPostProcessors(beanFactory);
            
            /*
            	实例化所有注册到 Bean 工厂的 BeanPostProcessor 类型的 Bean
            	这个类型的 Bean 主要有两个方法：postProcessBeforeInitialization、postProcessAfterInitialization，这两个方法分别在 Bean 实例化之前和之后执行
            	
            	这里的实例化 Bean 的任务也是通过 org.springframework.beans.factory.support.AbstractBeanFactory 来完成的
            	
            */
            registerBeanPostProcessors(beanFactory);
            
            beanPostProcess.end(); // 又标记一个阶段

            /*
            	初始化当前 ApplicationContext 的 MessageSource，这一步主要是需要处理一些国际化的信息
            */
            initMessageSource();

            /*
            	初始化当前 ApplicationContext 的事件广播器
            */
            initApplicationEventMulticaster();

            /*
            	一个模板方法，这个方法的主要目的是在 Bean 真正初始化之前执行一些额外的操作
            	需要具体的子类来实现
            */
            onRefresh();

            /*
            	注册事件监听器，在这里不会实例化 Bean
            */
            registerListeners();

            /*
            	这里是初始化所有 Bean 的地方，也是整个 Bean 装载的核心部分！！！！
            	在这一步中会初始化所有的非 lazy-init Bean
            */
            finishBeanFactoryInitialization(beanFactory);

            /*
            	最后，广播事件，通知 ApplicationContext 初始化完成
            */
            finishRefresh();
        }

        catch (BeansException ex) {
            if (logger.isWarnEnabled()) {
                logger.warn("Exception encountered during context initialization - " +
                            "cancelling refresh attempt: " + ex);
            }

            // Destroy already created singletons to avoid dangling resources.
            destroyBeans();

            // Reset 'active' flag.
            cancelRefresh(ex);

            // Propagate exception to caller.
            throw ex;
        }

        finally {
            // Reset common introspection caches in Spring's core, since we
            // might not ever need metadata for singleton beans anymore...
            resetCommonCaches();
            contextRefresh.end();
        }
    }
}
```

具体的流程图如下所示：

<img src="https://i.loli.net/2021/11/08/JvYUES3pWuco9VB.png" alt="IOC-flow.png" style="zoom:120%;" />

### 	

### Bean 的定义

Bean 在 Spring 容器中是以 `BeanDefinition` 对象的形式存在的，也就是说，Bean 在代码层面上是以 `BeanDefinition` 的形式存在的。

具体 `BeanDefinition` 的定义字段如下所示：

```java
public interface BeanDefinition extends AttributeAccessor, BeanMetadataElement {
    /* 
    	范围标识符，默认有以下两种：singleton（单例）、prototype（原型）
    	除此之外，在 Web 扩展中，还存在 request、session、websocket等几种
    */
	String SCOPE_SINGLETON = ConfigurableBeanFactory.SCOPE_SINGLETON;
	String SCOPE_PROTOTYPE = ConfigurableBeanFactory.SCOPE_PROTOTYPE;

    // 不是特别重要的几个字段
	int ROLE_APPLICATION = 0;
	int ROLE_SUPPORT = 1;
	int ROLE_INFRASTRUCTURE = 2;

	/**
	 * 设置父 Bean，这里涉及到 Bean 的继承
	 */
	void setParentName(@Nullable String parentName);

	/**
	 * 获取父 Bean
	 */
	@Nullable
	String getParentName();

	/**
	* 设置 Bean 的 Class 的全限定名，可以通过这个 Class 名来动态生成实例
    */
	void setBeanClassName(@Nullable String beanClassName);

	/**
	* 获取 Bean 的 Class 全限定名
    */
	@Nullable
	String getBeanClassName();

	/**
	* 设置 Bean 的 scope
    */
	void setScope(@Nullable String scope);

	/**
	* 获取当前 Bean 的 scope
    */
	@Nullable
	String getScope();

	/**
	* 设置是否是 lazy-init （懒加载）
    */
	void setLazyInit(boolean lazyInit);
	boolean isLazyInit();

    /*
    	设置该 Bean 依赖的所有的 Bean，注意：这里的依赖不是值属性依赖
    */
	void setDependsOn(@Nullable String... dependsOn);
    
    // 返回该 Bean 的所有依赖
	@Nullable
	String[] getDependsOn();

	/**
	* 设置该 Bean 是否可以注入到其它的 Bean 中，只针对类型注入有效
	* 如果是按照 Bean 的名称来注入的，那么这里将不会对其产生任何影响
    */
	void setAutowireCandidate(boolean autowireCandidate);

	// 该 Bean 是否可以注入到其它 Bean 中
	boolean isAutowireCandidate();

	/**
	* 是否是首先的 Bean，如果在注入时没有指定 Bean 的名字，那么将会有限选择设置了该属性的 Bean
    */
	void setPrimary(boolean primary);
	boolean isPrimary();

	/**
	* 如果该 Bean 是采用工厂方法生成，指定工厂名称
    */
	void setFactoryBeanName(@Nullable String factoryBeanName);
	@Nullable
	String getFactoryBeanName();

	/**
	* 指定工厂类中的工厂方法名称
    */
	void setFactoryMethodName(@Nullable String factoryMethodName);
	@Nullable
	String getFactoryMethodName();

	// 构造器参数
	ConstructorArgumentValues getConstructorArgumentValues();

	/**
	* 如果构造器参数已经定义在这个 Bean 中了，则返回 true
   	*/
	default boolean hasConstructorArgumentValues() {
		return !getConstructorArgumentValues().isEmpty();
	}

	//  Bean 中的属性值
	MutablePropertyValues getPropertyValues();

	/**
	 * 如果在这个 Bean 中存在已经定义的属性值，返回 true
	 */
	default boolean hasPropertyValues() {
		return !getPropertyValues().isEmpty();
	}

	// 设置初始化方法名
	void setInitMethodName(@Nullable String initMethodName);
	@Nullable
	String getInitMethodName();

	/**
	 * 设置销毁 Bean 的方法名
	 */
	void setDestroyMethodName(@Nullable String destroyMethodName);
	@Nullable
	String getDestroyMethodName();

	// 设置 role
	void setRole(int role);
	int getRole();

	// 设置人类可读的关于这个 Bean 的描述
	void setDescription(@Nullable String description);
	@Nullable
	String getDescription();

	/**
	* Return a resolvable type for this bean definition,
	* based on the bean class or other specific metadata.
    */
	ResolvableType getResolvableType();
    
	boolean isSingleton();
	boolean isPrototype();

	// 如果这个 bean 被设置为 abstract，那么将不能被实例化，通常作为父类 bean 用于继承
	boolean isAbstract();

	@Nullable
	String getResourceDescription();

    @Nullable
	BeanDefinition getOriginatingBeanDefinition();
}
```





### 具体分析

- 创建 Bean 之前的准备工作

  对应的源代码如下所示：

  ```java
  protected void prepareRefresh() {
      this.startupDate = System.currentTimeMillis(); // 记录当前的启动时间
      // 设置当前应用上下文的活跃状态，这两个活跃属性都是 AtomicBoolean 类型的
      this.closed.set(false);
      this.active.set(true);
      
      // 省略一部分日志输出代码
  
      /*
      	初始化当前上下文的占位符属性资源
      */
      initPropertySources();
  
      /*
      	验证所有需要的属性是否都是可解析的，即可用的
      */
      getEnvironment().validateRequiredProperties();
  
      /*
      	存储要销毁的 ApplicationContext 的所有监听器
      */
      if (this.earlyApplicationListeners == null) {
          this.earlyApplicationListeners = new LinkedHashSet<>(this.applicationListeners);
      }
      else {
          // Reset local application listeners to pre-refresh state.
          this.applicationListeners.clear();
          this.applicationListeners.addAll(this.earlyApplicationListeners);
      }
  
      // 准备去加载新的事件监听器
      this.earlyApplicationEvents = new LinkedHashSet<>();
  }
  ```

  

- 创建一个新的 `BeanFactory`，并加载 Bean 到 `BeanFactory`

  具体对于当前的情况，调用的是`org.springframework.context.support.AbstractApplicationContext` 的 `obtainFreshBeanFactory()` 方法：

  ```java
  protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
      // 关闭旧的 BeanFactory，创建新的 BeanFactory，加载 Bean 定义，注册 Bean 等
      refreshBeanFactory();
      
      // 返回刚刚创建的 Bean
      return getBeanFactory();
  }
  ```

  具体创建 `BeanFactory` 对应的源代码：

  `org.springframework.context.support.AbstractRefreshableApplicationContext` 的 `refreshBeanFactory()` 方法

  ```java
  protected final void refreshBeanFactory() throws BeansException {
      /*
      	如果 ApplicationContext 已经加载过 BeanFactory 了，那么将销毁所有的 Bean，关闭 BeanFactory
      	注意：应用中允许有多个 BeanFactory,但是针对 ApplicationContext 只能有一个 BeanFactory
      */
      if (hasBeanFactory()) {
          destroyBeans();
          closeBeanFactory();
      }
      try {
          // 初始化当前的 BeanFactory 为 DefaultListableBeanFactory
          DefaultListableBeanFactory beanFactory = createBeanFactory();
          beanFactory.setSerializationId(getId()); // 设置 beanFactory 的序列化 ID
          
          // 设置 BeanFactory 的两个属性：是否允许覆盖、是否允许循环引用
          customizeBeanFactory(beanFactory);
          // 加载 Bean 到 BeanFactory 中，这里的 Bean 是没有被实例化的
          loadBeanDefinitions(beanFactory);
          
          this.beanFactory = beanFactory;
      }
      // 省略一部分一场检查代码
  }
  ```

  在这个过程中，相比较上文提到的 `ApplicationContext` 继承自 `BeanFactory`，在这个过程中更加像是一种组合关系而不是继承关系。现在，在初始化 `ApplicationContext` 中的 `BeanFactory` 属性之后，之后所有的对于 `BeanFactory` 的请求都将通过 `ApplicationContext` 来完成

  

  至于为何选择 `DefaultListableBeanFactory` 作为 `BeanFactory` 的实例对象，可以参考下图所示的类关系：

  <img src="https://www.javadoop.com/blogimages/spring-context/3.png" style="zoom:80%" />

  可以看到，`DefaultListableBeanFactory` 不仅继承了 `ConfigurableListableBeanFactory`，也继承了 `AbstractAutowireCapableBeanFactory`，因此能够做更多的工作

- 



## Bean 的初始化

