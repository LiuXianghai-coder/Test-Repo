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
            */
            invokeBeanFactoryPostProcessors(beanFactory);
            
            /*
            	实例化所有注册到 Bean 工厂的 BeanPostProcessor 类型的 Bean
            	这个类型的 Bean 主要有两个方法：postProcessBeforeInitialization、postProcessAfterInitialization，这两个方法分别在 Bean 实例化之前和之后执行
            	
            */
            registerBeanPostProcessors(beanFactory);
            
            beanPostProcess.end();

            // Initialize message source for this context.
            initMessageSource();

            // Initialize event multicaster for this context.
            initApplicationEventMulticaster();

            // Initialize other special beans in specific context subclasses.
            onRefresh();

            // Check for listener beans and register them.
            registerListeners();

            // Instantiate all remaining (non-lazy-init) singletons.
            finishBeanFactoryInitialization(beanFactory);

            // Last step: publish corresponding event.
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





## Bean 的初始化