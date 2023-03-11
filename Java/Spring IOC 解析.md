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



### 启动过程<a id="startAnalyze"></a>

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

#### 创建 Bean 之前的准备工作

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



#### 获取 `BeanFactory`

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



##### 配置 BeanFactory

`customizeBeanFactory(beanFactory)`

这个方法的主要作用就是配置是否允许 `BeanDefinition` 覆盖、是否允许循环引用

```java
protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
    if (this.allowBeanDefinitionOverriding != null) {
        // 设置是否允许覆盖
        beanFactory.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
    }
    if (this.allowCircularReferences != null) {
        // 设置是否允许循环引用
        beanFactory.setAllowCircularReferences(this.allowCircularReferences);
    }
}
```

在配置文件中定义 Bean 时，如果使用了相同的 id 或 name，默认情况下，由于 `allowBeanDefinitionOverriding` 属性为 `null`，因此会抛出异常，如果在不同的配置文件中定义了 Bean，那么就会发生 Bean 的覆盖



##### 加载 Bean 到 BeanFactory

` loadBeanDefinitions(beanFactory)`

这个方法的主要任务是加载配置文件中的 Bean 到 BeanFactory



首先，需要进行配置文件的读取，具体在 `org.springframework.context.support.AbstractXmlApplicationContext` 中 `loadBeanDefinitions(factory)` 方法中完成：

```java
protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) 
    throws BeansException, IOException {
    // 通过给定的 BeanFactory 实例，创建一个新的 XmlBeanDefinitionReader 实例对象
    XmlBeanDefinitionReader beanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);

    // 加载一些相关的配置
    beanDefinitionReader.setEnvironment(this.getEnvironment());
    beanDefinitionReader.setResourceLoader(this);
    beanDefinitionReader.setEntityResolver(new ResourceEntityResolver(this));

    initBeanDefinitionReader(beanDefinitionReader);
    // 主要的 Bean 加载任务在此处进行
    loadBeanDefinitions(beanDefinitionReader);
}
```



`org.springframework.context.support.AbstractXmlApplicationContext` 的 `loadBeanDefinitions(beanDefinitionReader)`：

```java
protected void loadBeanDefinitions(XmlBeanDefinitionReader reader) 
    throws BeansException, IOException {
    Resource[] configResources = getConfigResources(); // 获取资源的配置信息
    if (configResources != null) {
        reader.loadBeanDefinitions(configResources);
    }
    String[] configLocations = getConfigLocations();
    if (configLocations != null) {
        reader.loadBeanDefinitions(configLocations);
    }
}

public int loadBeanDefinitions(Resource... resources) 
    throws BeanDefinitionStoreException {
    Assert.notNull(resources, "Resource array must not be null");
    int count = 0;
    // 加载每个资源文件，解析每个资源文件中的 Bean
    for (Resource resource : resources) {
        // 当前环境下的方法对应 org.springframework.beans.factory.xml.XmlBeanDefinitionReader 中的方法
        count += loadBeanDefinitions(resource); 
    }
    return count; // 加载的 Bean 的数量
}


```



`org.springframework.beans.factory.xml.XmlBeanDefinitionReader` 的 `loadBeanDefinitions(resource)`：

```java
public int loadBeanDefinitions(Resource resource) throws BeanDefinitionStoreException {
    return loadBeanDefinitions(new EncodedResource(resource));
}

public int loadBeanDefinitions(EncodedResource encodedResource) throws BeanDefinitionStoreException {
    // 省略一部分断言和日志打印代码
    
    // 使用 ThreadLocal 来放配置资源文件
    Set<EncodedResource> currentResources = this.resourcesCurrentlyBeingLoaded.get();

    try (InputStream inputStream = encodedResource.getResource().getInputStream()) {
        InputSource inputSource = new InputSource(inputStream);
        if (encodedResource.getEncoding() != null) {
            inputSource.setEncoding(encodedResource.getEncoding());
        }
        
        // 核心部分在这里
        return doLoadBeanDefinitions(inputSource, encodedResource.getResource());
    }
    
    // 省略一部分异常检查和资源关闭的代码
}

protected int doLoadBeanDefinitions(InputSource inputSource, Resource resource)
    throws BeanDefinitionStoreException {

    try {
        // 这一部分的主要任务是加载 XML 文件，解析成为一个 Document 对象
        Document doc = doLoadDocument(inputSource, resource);

        // 注册 Bean 到 BeanFactory，这个方法的重要部分。。。。
        int count = registerBeanDefinitions(doc, resource);
        // 省略一部分日志打印代码
        return count;
    }
   // 省略一大部分捕获异常的代码
}

public int registerBeanDefinitions(Document doc, Resource resource) 
    throws BeanDefinitionStoreException {
    // 这个对象的功能是使得能够读取 XML 中相关的 Bean 配置信息
    BeanDefinitionDocumentReader documentReader = createBeanDefinitionDocumentReader();
    int countBefore = getRegistry().getBeanDefinitionCount();

    /* 
    	加载 Bean 的核心逻辑部分，当前环境下对应的具体实现类为 
    	DefaultBeanDefinitionDocumentReader
    */
    documentReader.registerBeanDefinitions(doc, createReaderContext(resource));
    return getRegistry().getBeanDefinitionCount() - countBefore;
}
```



`org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader` 的 `registerBeanDefinitions(doc, readContext)` 方法

```java
public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
    this.readerContext = readerContext;
    // 从 XML 根节点开始，解析 XML 中的 Bean 信息
    doRegisterBeanDefinitions(doc.getDocumentElement());
}

protected void doRegisterBeanDefinitions(Element root) {
    /*
    	这个类的主要作用是负责 Bean 的解析
    */
    BeanDefinitionParserDelegate parent = this.delegate;
    this.delegate = createDelegate(getReaderContext(), root, parent);

    if (this.delegate.isDefaultNamespace(root)) {
        /*
        	这一块的主要目的是加载对应的 Profile 的 Bean，
        	如果当前环境配置的 profile 不包含此 profile，那么将会跳过这些 Bean 的解析
        */
        String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
        if (StringUtils.hasText(profileSpec)) {
            String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
                profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
            if (!getReaderContext()
                .getEnvironment()
                .acceptsProfiles(specifiedProfiles)
               ) {
                // 省略一部分日志打印。。。。
                return;
            }
        }
    }

    preProcessXml(root); // 在正式处理 XML 之前的钩子
    // 正式解析 Bean，重点部分
    parseBeanDefinitions(root, this.delegate);
    postProcessXml(root); // 解析 XML 之后的钩子

    this.delegate = parent;
}


protected void parseBeanDefinitions(
    Element root, BeanDefinitionParserDelegate delegate
) {
    /*
    	default namespace 涉及到 <import />、<alias />、<bean />、<beans />
    	其它的属于 custom
    */
    if (delegate.isDefaultNamespace(root)) {
        NodeList nl = root.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node node = nl.item(i);
            if (node instanceof Element) {
                Element ele = (Element) node;
                if (delegate.isDefaultNamespace(ele)) {
                    // 解析 default namespace 下面的几个元素
                    parseDefaultElement(ele, delegate);
                }
                else {
                    // 解析其它的 namespace 元素
                    delegate.parseCustomElement(ele);
                }
            }
        }
    }
    else {
        delegate.parseCustomElement(root);
    }
}

// 解析默认的标签
private void parseDefaultElement(
    Element ele, 
    BeanDefinitionParserDelegate delegate
) {
    // 解析 <import /> 标签
    if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
        importBeanDefinitionResource(ele);
    }
    // 解析 <alias /> 标签
    else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
        processAliasRegistration(ele);
    }
    // 解析 <bean /> 标签
    else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
        processBeanDefinition(ele, delegate);
    }
    // 解析 <beans /> 标签
    else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
        // 由于有的 Beans 可能会有内嵌的 Beans，因此需要递归处理
        doRegisterBeanDefinitions(ele);
    }
}

// 重点查看一下 <bean /> 标签的解析
protected void processBeanDefinition(
    Element ele, 
    BeanDefinitionParserDelegate delegate
) {
    // 将 <bean /> 中的标签提取出来，然后封装到一个 BeanDefinitionHolder 对象中
    BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
    
    if (bdHolder != null) {
        // 如果 Bean 有自定义的属性，则进行相应的解析
        bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
        try {
            // 将 Bean 注册到 BeanFactory 中，注意，这里是没有实例化 Bean 的
            BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
        }
        // 省略一部分异常捕获代码
        
        // 注册完成之后，发送事件
        getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
    }
}
```



具体 `<bean />` 标签 转换为 `BeanDefinitionHolder` 的源代码：

```java
public BeanDefinitionHolder parseBeanDefinitionElement(
    Element ele, @Nullable BeanDefinition containingBean
) {
    // 获取 Bean 的 id 和 name 属性
    String id = ele.getAttribute(ID_ATTRIBUTE);
    String nameAttr = ele.getAttribute(NAME_ATTRIBUTE);

    List<String> aliases = new ArrayList<>(); // 别名
    
    /*
    	将 name 属性的定义按照 "逗号、分号、空格" 的方式进行切分，形成一个别名列表数组
    */
    if (StringUtils.hasLength(nameAttr)) {
        String[] nameArr = StringUtils.tokenizeToStringArray(nameAttr, MULTI_VALUE_ATTRIBUTE_DELIMITERS);
        aliases.addAll(Arrays.asList(nameArr));
    }

    String beanName = id;
    // 如果没有指定 id，那么将使用别名列表的第一个名字作为 beanName
    if (!StringUtils.hasText(beanName) && !aliases.isEmpty()) {
        beanName = aliases.remove(0);
        // 省略一部分日志打印。。。。。
    }

    if (containingBean == null) {
        checkNameUniqueness(beanName, aliases, ele);
    }
    
    /*
    	根据 <bean ....></bean> 的配置创建 BeanDefinition，然后把配置中的信息都设置到实例中
    */
    AbstractBeanDefinition beanDefinition = parseBeanDefinitionElement(ele, beanName, containingBean);
    
    if (beanDefinition != null) {
        // 省略一部分设置 beanName 的代码（在没有设置 id 和 name 的情况下）
        
        String[] aliasesArray = StringUtils.toStringArray(aliases);
        return new BeanDefinitionHolder(beanDefinition, beanName, aliasesArray);
    }

    return null;
}
```



`BeanDefinition ` 实例的创建：

```java
public AbstractBeanDefinition parseBeanDefinitionElement(
    Element ele, 
    String beanName, 
    @Nullable BeanDefinition containingBean
) {
    // 省略一部分不太重要的代码

    try {
        // 创建 BeanDefinition 实例，设置类信息，这里 Bean 的类在此完成加载
        AbstractBeanDefinition bd = createBeanDefinition(className, parent);
        
        // 设置 BeanDefinition 的一堆属性，即 <bean .../> 中的属性。。。。。
        parseBeanDefinitionAttributes(ele, beanName, containingBean, bd);
        bd.setDescription(DomUtils.getChildElementValueByTagName(ele, DESCRIPTION_ELEMENT));
        
        // 解析 <bean>....</bean> 的子节点属性，解析之后的信息都放到 bd 的属性中
        parseMetaElements(ele, bd);
        parseLookupOverrideSubElements(ele, bd.getMethodOverrides());
        parseReplacedMethodSubElements(ele, bd.getMethodOverrides());

        parseConstructorArgElements(ele, bd);
        parsePropertyElements(ele, bd);
        parseQualifierElements(ele, bd);
        // 解析子节点属性结束。。。。。

        bd.setResource(this.readerContext.getResource());
        bd.setSource(extractSource(ele));

        return bd;
    }
    // 省略一部分异常捕获代码
    finally {
        this.parseState.pop();
    }

    return null;
}
```

经过这个阶段，已经通过 `<bean />` 创建了一个 `BeanDefinitionHolder` 实例



注册 `Bean` 到 `BeanFactory`

对应的源代码如下（位于 `org.springframework.beans.factory.support.BeanDefinitionReaderUtils`）：

```java
public static void registerBeanDefinition(
    BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry)
    throws BeanDefinitionStoreException {

    String beanName = definitionHolder.getBeanName();
    /* 
    	注册这个 Bean，当前环境下对应 	
    	org.springframework.beans.factory.support.DefaultListableBeanFactory 实现类
    */
    registry.registerBeanDefinition(beanName, definitionHolder.getBeanDefinition());

    // 如果这个 Bean 存在别名的话，需要将所有的别名也注册到 BeanFactory 中
    String[] aliases = definitionHolder.getAliases();
    if (aliases != null) {
        for (String alias : aliases) {
            // 具体方法定义于 org.springframework.core.SimpleAliasRegistry
            registry.registerAlias(beanName, alias);
        }
    }
}
```

具体的注册实现：

```java
public void registerBeanDefinition(
    String beanName, 
    BeanDefinition beanDefinition
) throws BeanDefinitionStoreException {
    // 省略一部分不太重要的代码
    
    // 这个 Bean 是否是允许覆盖的。。。。 beanDefinitionMap 为 ConcurrentHashMap
    BeanDefinition existingDefinition = this.beanDefinitionMap.get(beanName);
    if (existingDefinition != null) { // 处理重复 name 的 Bean
        if (!isAllowBeanDefinitionOverriding()) {
            throw new BeanDefinitionOverrideException(
                beanName, beanDefinition, existingDefinition
            );
        }
        else if (existingDefinition.getRole() < beanDefinition.getRole()) {
            // 省略一部分日志输出，表示用框架定义的 Bean 覆盖用户定义的 Bean
        }
        else if (!beanDefinition.equals(existingDefinition)) {
            // 用新的 Bean 覆盖旧的 Bean
        }
        else {
           // 用相同的 Bean 覆盖旧的 Bean
        }
        // 覆盖 Bean
        this.beanDefinitionMap.put(beanName, beanDefinition);
    }
    else {
        /*
        	判断是否已经有别的 Bean 开始初始化了，初始化并不是实例化！！！！
        	
        	有的话就需要加锁避免缓存一致性问题
        */
        if (hasBeanCreationStarted()) {
            synchronized (this.beanDefinitionMap) {
                this.beanDefinitionMap.put(beanName, beanDefinition);
                List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames.size() + 1);
                updatedDefinitions.addAll(this.beanDefinitionNames);
                updatedDefinitions.add(beanName);
                this.beanDefinitionNames = updatedDefinitions;
                removeManualSingletonName(beanName);
            }
        }
        else {
            // 一般会走到这里。。。。
            
            /* 
            	将 BeanDefinition 注册到 beanDefinitionMap 中，
            	beanDefinitionMap 保存了所有的  beanName ——> BeanDefinition 的映射
            */
            this.beanDefinitionMap.put(beanName, beanDefinition);
            // 添加注册的 BeanName
            this.beanDefinitionNames.add(beanName);
            
            /* 
            	如果手动注册的 Bean 中包含这个 Bean，那么移除它
            
            	手动注册的 Bean 是指调用 registerSingleton(name, Object) 注册的 Bean，
            	这些 Bean 一般都是系统属性相关的 Bean，用户可以手动设置这些 Bean，
            	这么做会移除系统中默认的一些 Bean 而使用用户定义的 Bean
            */
            removeManualSingletonName(beanName);
        }
        this.frozenBeanDefinitionNames = null;
    }

    if (existingDefinition != null || containsSingleton(beanName)) {
        resetBeanDefinition(beanName);
    }
    else if (isConfigurationFrozen()) {
        clearByTypeCache();
    }
}
```

至此，Bean 的注册就已经完成了



#### 准备 BeanFactory

`prepareBeanFactory(beanFactory)`

```java
// 配置当前 BeanFactory 的相关属性，例如：当前上下文的类加载器、后置处理等
protected void prepareBeanFactory(
    ConfigurableListableBeanFactory beanFactory
) {
    /*
    	设置类加载器，默认为应用上下文类加载器
    */
    beanFactory.setBeanClassLoader(getClassLoader());
    
    if (!shouldIgnoreSpel) {
        beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
    }
    beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

    /*
    	添加一个 BeanPostProcessor，这个类的会在 Bean 实例化之前和之后执行一些相关的操作，
    	具体在这里是执行一个回调，即返回实例化的 Bean
    */
    beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
    
    /* 
    	如果某个 Bean 依赖于以下几个接口的实现类，那么在自动装配的时候忽略它们
    	Spring 会通过其它方式来处理这些依赖
    */
    beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
    beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
    beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
    beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
    beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
    beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
    beanFactory.ignoreDependencyInterface(ApplicationStartupAware.class);
    // end ...........................

    /*
    	为几个特殊的 Bean 赋值，如果有 Bean 依赖了以下几个，会注入这边相应的值
    */
    beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
    beanFactory.registerResolvableDependency(ResourceLoader.class, this);
    beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
    beanFactory.registerResolvableDependency(ApplicationContext.class, this);

    /*
    	这个 BeanPostProcessor 的目的在于在 Bean 实例化之后，如果是 ApplicationListener 的子类，
    	则将它添加到 listerners 列表中，也就是注册事件监听器
    */
    beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));
    
    /*
    	一个特殊的 Bean：loadTimeWeaver，这个是 AspectJ 的概念，可以跳过
    */
    if (!NativeDetector.inNativeImage() && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
        beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
        beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
    }

    // 如果用户没有自定义 “environment” 这个 Bean，那么 Spring 将会注册一个默认的名为 “environment” 的 Bean
    if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
        beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
    }
    // 同样地，也会默认注册一个名为 “systemProperties” 的 Bean
    if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
        beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
    }
    // 同样地，也会默认注册一个名为 “systemEnvironment” 的 Bean
    if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
        beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
    }
    // 同样地，也会默认注册一个名为 “applicationStartup” 的 Bean
    if (!beanFactory.containsLocalBean(APPLICATION_STARTUP_BEAN_NAME)) {
        beanFactory.registerSingleton(APPLICATION_STARTUP_BEAN_NAME, getApplicationStartup());
    }
}
```



#### 实例化所有的 Bean

`finishBeanFactoryInitialization(beanFactory)`，在这个方法中，将会实例化所有的 Bean

在调用这个方法之前，通过之前的 <a href="#startAnalyze">启动分析</a> 中介绍的，在实例化 Bean 之前，会调用 `invokeBeanFactoryPostProcessors(beanFactory)` 实例化所有的 `BeanFactoryPostProcessor` 类型的 Bean 并且调用所有的 `postProcessBeanFactory(beanFactory)` 方法；再通过调用 `registerBeanPostProcessors(beanFactory)` 实例化所有的 `BeanPostProcessor` 类型的 Bean，以及设置这些 Bean 定义的钩子方法，现在，是时候实例化所有的非 lazy-init Bean了



对应的源代码如下：

```java
// 实例化所有的非 lazy-init Bean
protected void finishBeanFactoryInitialization(
    ConfigurableListableBeanFactory beanFactory
) {
    /*
    	加载名为 “conversionService” 的 Bean，这里的 Bean 是实例化的，
    	具体的实例化在 getBean(beanName, Classs) 中完成 
    */
    if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
        beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
        beanFactory.setConversionService(
            beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
    }
    
    // 不是特别重要的代码
    if (!beanFactory.hasEmbeddedValueResolver()) {
        beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
    }
    
    // 加载所有类型为 LoadTimeWeaverAware 的 Bean，然后初始化它们
    String[] weaverAwareNames =	beanFactory
        .getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
    for (String weaverAwareName : weaverAwareNames) {
        getBean(weaverAwareName);
    }

    // Stop using the temporary ClassLoader for type matching.
    beanFactory.setTempClassLoader(null);

    // 由于现在 Bean 要开始实例化了，因此需要暂停加载 Bean 来维护完整性
    beanFactory.freezeConfiguration();
    
    // 开始 Bean 的初始化
    beanFactory.preInstantiateSingletons();
}
```



## Bean 的初始化

上通过文，已经了解到 Bean 的实例化之前需要经历的那些流程，现在具体来分析以下 Bean 的实例化过程。

对于上文的实例化方法 `preInstantiateSingletons()`，之前在获取 `BeanFactory` 的时候介绍过，此时的 `BeanFactory` 的具体类为 `DefaultListableBeanFactory`。

对应的实例化 Bean 的源代码：

`org.springframework.beans.factory.support.DefaultListableBeanFactory`

```java
public void preInstantiateSingletons() throws BeansException {
    // 省略一部分日志输出。。。。
    
    // this.beanDefinitionNames 在获取 BeanFactory 的时候已经加载了所有的 Bean 的 name
    List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);
    
    // 触发所有的非 lazy-init Bean 的实例化操作
    for (String beanName : beanNames) {
        // 合并父 Bean 的相关配置，可以参考 Bean 的继承
        RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
        
        // 抽象的、lazy-init 的 Bean 是不需要初始化的
        if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
            // 处理工厂 Bean
            if (isFactoryBean(beanName)) {
                // 工厂 Bean 的 BeanName 需要加上前缀 '&'
                Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
                if (bean instanceof FactoryBean) {
                    FactoryBean<?> factory = (FactoryBean<?>) bean;
                    boolean isEagerInit;
                    
                    // 工厂 Bean 的特殊处理
                    if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
                        isEagerInit = AccessController.doPrivileged(
                            (PrivilegedAction<Boolean>) ((SmartFactoryBean<?>) factory)::isEagerInit,
                            getAccessControlContext());
                    }
                    else {
                        isEagerInit = (factory instanceof SmartFactoryBean &&
                                       ((SmartFactoryBean<?>) factory).isEagerInit());
                    }
                    if (isEagerInit) {
                        getBean(beanName);
                    }
                }
            }
            else {
                /* 
                	对于普通的 Bean，
                	只需要调用
                	org.springframework.beans.factory.support.AbstractBeanFactory 
                	的 getBean(beanName) 就可以进行初始化了
                */
                getBean(beanName);
            }
        }
    }
    
    // 到此，所有的非 lazy-init Bean 已经完成了实例化

    // 省略 SmartInitializingSingleton 类型 Bean 的回调。。。。
}
```



#### getBean

上文提到的 `getBean` 方法是创建具体 Bean 的方法，具体的源代码如下：

`org.springframework.beans.factory.support.AbstractBeanFactory`：

```java
public Object getBean(String name) throws BeansException {
    return doGetBean(name, null, null, false);
}

/* 
	getBean 方法经常用来从容器中获取 Bean，
	如果已经实例化了就直接获取，否则就需要首先进行实例化
*/
protected <T> T doGetBean(
    String name, 
    @Nullable Class<T> requiredType, 
    @Nullable Object[] args, b
    oolean typeCheckOnly
) throws BeansException {
    /*
    	首先将 beanName 进行以下转换，
    	具体针对：带有前缀的 beanName 和使用别名的 Bean
    */
    String beanName = transformedBeanName(name);
    
    Object beanInstance; // 具体的实例对象引用句柄
    
    // 检查在 BeanFactory 中是否已经存在了这个 Bean
    Object sharedInstance = getSingleton(beanName);
    
    /*
    	通过 args 参数来表示是否需要创建 Bean，如果传入的 args 不为 null，
    	那么就表示希望去创建 Bean 而不是获取 Bean
    */
    if (sharedInstance != null && args == null) {
        // 省略一部分日志打印代码
        
        /*
        	如果是普通 Bean 的话，直接返回 sharedInstance
        	如果是 FactoryBean 的话，返回创建它的实例对象
        */
        beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, null);
    }

    else {
        /*
        	如果已经创建了该 beanName 对应的 prototype 类型的 Bean，
        	说明很大概率发生了循环引用，此时需要抛出异常来避免这个问题 
        */
        if (isPrototypeCurrentlyInCreation(beanName)) {
            throw new BeanCurrentlyInCreationException(beanName);
        }

        // 检查一下这个 Bean 在 BeanFactory 中是否存在
        BeanFactory parentBeanFactory = getParentBeanFactory();
        
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // 如果当前 BeanFactory 无法找到，那么尝试在父 BeanFactory 中查找
            String nameToLookup = originalBeanName(name);
            if (parentBeanFactory instanceof AbstractBeanFactory) {
                return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
                    nameToLookup, requiredType, args, typeCheckOnly);
            }
            
            else if (args != null) {
                // 返回父 BeanFactory 中的查询结果
                return (T) parentBeanFactory.getBean(nameToLookup, args);
            }
            else if (requiredType != null) {
                // No args -> delegate to standard getBean method.
                return parentBeanFactory.getBean(nameToLookup, requiredType);
            }
            else {
                return (T) parentBeanFactory.getBean(nameToLookup);
            }
        }

        if (!typeCheckOnly) {
            markBeanAsCreated(beanName); // 将当前 BeanName 放入 alreadyCreated 集合中
        }
        
        
        StartupStep beanCreation = this
            .applicationStartup.start("spring.beans.instantiate")
            .tag("beanName", name); // 一个新的步骤，简单记录一下
        
        try {
            // 可以忽略的部分
            if (requiredType != null) {
                beanCreation.tag("beanType", requiredType::toString);
            }
            
            RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
            checkMergedBeanDefinition(mbd, beanName, args);

            /*
            	首先初始化依赖的所有 Bean，这里的依赖指的是 depend-on 中定义的依赖
            */
            String[] dependsOn = mbd.getDependsOn();
            if (dependsOn != null) {
                for (String dep : dependsOn) {
                    // 检查是否存在循环依赖
                    if (isDependent(beanName, dep)) {
                        throw new BeanCreationException(mbd.getResourceDescription(), beanName,
                                                        "Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
                    }
                    
                    // 注册一下依赖关系
                    registerDependentBean(dep, beanName);
                    
                    try {
                        getBean(dep); // 首先初始化被依赖项
                    }
                    // 省略一部分异常捕获代码
                }
            }

            // 如果是 singleton scope，则创建 Singleton 类型的实例
            if (mbd.isSingleton()) {
                sharedInstance = getSingleton(beanName, () -> {
                    try {
                        return createBean(beanName, mbd, args); // 创建 Bean
                    }
                    // 省略一部分异常捕获代码
                });
                beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
            }

            else if (mbd.isPrototype()) { // 如果是 prototype 的，则创建 prorotype 的实例
                Object prototypeInstance = null;
                try {
                    beforePrototypeCreation(beanName);
                    prototypeInstance = createBean(beanName, mbd, args); // 创建 Bean
                }
                finally {
                    afterPrototypeCreation(beanName);
                }
                beanInstance = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
            }

            else {
                // 如果既不是 singleton 也不是 prototype 类型，那么就需要委托给相应的实现类来处理
                String scopeName = mbd.getScope();
                Scope scope = this.scopes.get(scopeName);
                // 省略一部分检测代码
                try {
                    Object scopedInstance = scope.get(beanName, () -> {
                        beforePrototypeCreation(beanName);
                        try {
                            return createBean(beanName, mbd, args); // 创建 Bean
                        }
                        finally {
                            afterPrototypeCreation(beanName);
                        }
                    });
                    beanInstance = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
                }
                // 省略一部分异常捕获代码
            }
        }
        // 省略一部分异常捕获代码
        finally {
            beanCreation.end();
        }
    }
    
    // 返回之前再检查一下得到的 Bean 的类型是否是需要的类型
    return adaptBeanInstance(name, beanInstance, requiredType);
}
```



#### 创建 Bean

`org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory` 的 `createBean(beanName, md, args)` 方法。

```java
protected Object createBean(
    String beanName, 
    RootBeanDefinition mbd, 
    @Nullable Object[] args
)throws BeanCreationException {
    // 省略一部分日志打印代码
    
    RootBeanDefinition mbdToUse = mbd;
    
    // 确保要创建的 Bean 的 Class 已经被加载
    Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
    if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
        mbdToUse = new RootBeanDefinition(mbd);
        mbdToUse.setBeanClass(resolvedClass);
    }
    
    /*
    	准备方法重写：MethodOverrides，
    	对应 XML 配置文件中的 <lookup-method /> 和 <replaced-method />
    */
    try {
        mbdToUse.prepareMethodOverrides();
    }
   // 省略一部分异常捕获代码

    try {
        // 使得 InstantiationAwareBeanPostProcessor 在这一步有机会返回代理
        Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
        if (bean != null) {
            return bean;
        }
    }
    // 省略一部分异常捕获代码

    try {
        // 具体创建 Bean 实例的地方
        Object beanInstance = doCreateBean(beanName, mbdToUse, args);
        
        // 省略一部分日志打印代码
        
        return beanInstance;
    }
    // 省略一部分异常捕获代码
}
```



`doCreateBean`

```java
protected Object doCreateBean(
    String beanName, 
    RootBeanDefinition mbd, 
    @Nullable Object[] args
) throws BeanCreationException {

    // Instantiate the bean.
    BeanWrapper instanceWrapper = null;
    if (mbd.isSingleton()) {
        instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
    }
    
    if (instanceWrapper == null) { 
        // 说明这不是一个 FactoryBean，将在此实例化 Bean
        instanceWrapper = createBeanInstance(beanName, mbd, args);
    }
    
    // 这个 bean 就是定义的实例对象，BeanDefinition 包装了它
    Object bean = instanceWrapper.getWrappedInstance();
    // 获取这个实例对象在 Bean 中的类型
    Class<?> beanType = instanceWrapper.getWrappedClass();
    if (beanType != NullBean.class) {
        mbd.resolvedTargetType = beanType;
    }
    
    // 省略一部分不太重要的代码
    
    // 下面的内容是为了解决循环依赖的问题
    boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
                                      isSingletonCurrentlyInCreation(beanName));
    if (earlySingletonExposure) {
        // 省略一部分日志打印代码
        addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
    }
    // 解决循环依赖结束。。。。。。

    // Initialize the bean instance.
    Object exposedObject = bean;
    try {
        // 这一步负责属性装配，之前只是实例化了对象，但是没有将相关的属性设置到这个实例对象中
        populateBean(beanName, mbd, instanceWrapper);
        
        /*
        	这里会执行实例化 Bean 的各种钩子操作
        	如：init-method、BeanPostProcessor 的方法、InitializingBean 的方法
        */
        exposedObject = initializeBean(beanName, exposedObject, mbd);
    }
    // 省略一部分异常捕获代码

    // 省略一部分不太重要的代码

    return exposedObject;
}
```

在上述代码中，最关键的几个部分分别是：`createBeanInstance`（创建实例对象）、`populateBean`（属性装配）、`initializeBean`（执行初始化 Bean 的各种操作）

- Bean 实例化

  ```java
  protected BeanWrapper createBeanInstance(
      String beanName, 
      RootBeanDefinition mbd, 
      @Nullable Object[] args
  ) {
      // 确保加载了要加载的 Bean 的 Class
      Class<?> beanClass = resolveBeanClass(mbd, beanName);
      
      // 校验一下这个类的访问权限
      if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
          // 省略抛出异常的部分代码
      }
  
      Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
      if (instanceSupplier != null) {
          return obtainFromSupplier(instanceSupplier, beanName);
      }
  
      if (mbd.getFactoryMethodName() != null) {
          // 可以使用工厂方法来完成 Bean 的实例化，则首先采用工厂方法进行实例化
          return instantiateUsingFactoryMethod(beanName, mbd, args);
      }
      
      /*
      	如果不是第一次实例化这个 Bean，在这种情况下，
      	可以从第一次创建中得知实例化的方式（无参构造和构造函数注入）
      */
      boolean resolved = false;
      boolean autowireNecessary = false;
      if (args == null) {
          synchronized (mbd.constructorArgumentLock) {
              if (mbd.resolvedConstructorOrFactoryMethod != null) {
                  resolved = true;
                  autowireNecessary = mbd.constructorArgumentsResolved;
              }
          }
      }
      if (resolved) {
          if (autowireNecessary) {
              // 构造函数的依赖注入
              return autowireConstructor(beanName, mbd, null, null);
          }
          else {
              // 无参构造函数
              return instantiateBean(beanName, mbd);
          }
      }
  
      // 判断是否采用有参构造函数
      Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
      if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
          mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
          // 构造函数依赖注入
          return autowireConstructor(beanName, mbd, ctors, args);
      }
  
      ctors = mbd.getPreferredConstructors();
      if (ctors != null) {
          return autowireConstructor(beanName, mbd, ctors, null);
      }
  
      // 没有指定实例的构造函数，则默认使用无参构造函数
      return instantiateBean(beanName, mbd);
  }
  ```

  以使用无参构造函数的方式实例化 Bean 为例，具体的源代码如下所示：

  ```java
  protected BeanWrapper instantiateBean(String beanName, RootBeanDefinition mbd) {
      try {
          Object beanInstance;
          if (System.getSecurityManager() != null) {
              beanInstance = AccessController.doPrivileged(
                  (PrivilegedAction<Object>) () -> getInstantiationStrategy().instantiate(mbd, beanName, this),
                  getAccessControlContext());
          }
          else {
              // 一般会走这里，具体的实例化 Bean 在此处完成
              beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, this);
          }
          BeanWrapper bw = new BeanWrapperImpl(beanInstance);
          initBeanWrapper(bw);
          return bw;
      }
      // 省略一部分异常捕获代码
  }
  ```

  具体地，当前 `InstantiationStrategy` 的实现类为 `SimpleInstantiationStrategy`，对应的实例化方法的源代码：

  ```java
  public Object instantiate(
      RootBeanDefinition bd, 
      @Nullable String beanName, 
      BeanFactory owner
  ) {
      /*
      	如果不存在方法覆写，那么将使用 Java 的反射进行实例化，否则，将使用 CGLIB
      */
      if (!bd.hasMethodOverrides()) {
          Constructor<?> constructorToUse;
          synchronized (bd.constructorArgumentLock) {
              constructorToUse = (Constructor<?>) bd.resolvedConstructorOrFactoryMethod;
              if (constructorToUse == null) {
                  final Class<?> clazz = bd.getBeanClass();
                  if (clazz.isInterface()) {
                      throw new BeanInstantiationException(clazz, "Specified class is an interface");
                  }
                  try {
                      if (System.getSecurityManager() != null) {
                          constructorToUse = AccessController.doPrivileged(
                              (PrivilegedExceptionAction<Constructor<?>>) clazz::getDeclaredConstructor);
                      }
                      else {
                          constructorToUse = clazz.getDeclaredConstructor();
                      }
                      bd.resolvedConstructorOrFactoryMethod = constructorToUse;
                  }
                 // 省略一部分异常捕获代码
              }
          }
          // 由于不存在方法覆写，因此可以使用反射的方式调用 Bean 的构造函数进行实例化
          return BeanUtils.instantiateClass(constructorToUse);
      }
      else {
          // 存在方法覆写，使用 CGLIB 来完成实例化，需要依赖 CGLIB 生成子类
          return instantiateWithMethodInjection(bd, beanName, owner);
      }
  }
  ```

  至此，Bean 的实例化就已经完成了

  

- 属性装配

  `populateBean(beanName, mbd, instanceWrapper)`

  ```java
  // 将相关的实例的属性值注入到实例对象的对应属性
  protected void populateBean(
      String beanName, 
      RootBeanDefinition mbd, 
      @Nullable BeanWrapper bw
  ) {
      // 省略一部分参数检测代码
      
      /*
      	Bean 已经被实例化，但是还没有被设置属性值，在这里可以对赋值之前做一些额外的操作
      	InstantiationAwareBeanPostProcessor 类型的实例对象可以在这里对 Bean 进行一些相关的操作
      */
      if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
          for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
              if (!bp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
                  return;
              }
          }
      }
  
      PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);
  
      int resolvedAutowireMode = mbd.getResolvedAutowireMode();
      if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
          MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
          /*
          	如果是通过 BeanName 的方式来实现注入，那么首先根据 BeanName 找到 Bean 并实例化这个 Bean，
          	再注入当前的 Bean 中
          */
          if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
              autowireByName(beanName, mbd, bw, newPvs);
          }
          /*
          	如果是按照 Bean 的类型来注入，要做更多的工作
          */
          if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
              autowireByType(beanName, mbd, bw, newPvs);
          }
          pvs = newPvs;
      }
      
      // 省略一部分不太重要的代码
  
      if (pvs != null) {
          applyPropertyValues(beanName, mbd, bw, pvs); // 将得到的属性值再设置到 Bean 实例中
      }
  }
  ```

  

- `initializeBean`

  `initializeBean(beanName, exposedObject, mbd)`

  位于 `org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory`

  ```java
  protected Object initializeBean(String beanName, Object bean, @Nullable RootBeanDefinition mbd) {
      // 省略一部分代码
      
      /*
      	如果这个 Bean 实现了 BeanNameAware、BeanClassLoaderAware、BeanFactoryAware 接口，
      	则将执行对应的回调方法
      */
      invokeAwareMethods(beanName, bean);
  
      Object wrappedBean = bean;
      if (mbd == null || !mbd.isSynthetic()) {
          /*
          	记得在实例化之前的 registerBeanPostProcessors(beanFactory) 吗？
          	在这里将会执行所有的 BeanPostProcessor 的 postProcessBeforeInitialization(bean, beanName) 方法
          */
          wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
      }
  
      try {
          /*
          	如果对 Bean 定义了 init-method 方法，将会在这里执行
          	如果 Bean 实现了 InitializingBean 接口，那么在这里也会调用 afterPropertiesSet() 方法
           */
          invokeInitMethods(beanName, wrappedBean, mbd);
      }
      // 省略一部分异常捕获代码
      
      if (mbd == null || !mbd.isSynthetic()) {
          /*
          	在这里就会执行所有的 BeanPostProcessor 的 postProcessAfterInitialization(bean, beanName) 方法
          */
          wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
      }
  
      return wrappedBean;
  }
  ```





参考：

<sup>[1]</sup> https://javadoop.com/post/spring-ioc
