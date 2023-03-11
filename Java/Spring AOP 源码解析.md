# Spring AOP 源码解析

AOP 即面向切面编程，在前文已经有所介绍，具体的实现方式有以下三种：

- 静态代理：通过为要执行切面操作的类手动定义一个额外的类来完成功能
- 动态代理：在程序运行时动态地生成代理类来实现切面的具体功能
- `AspectJ`：对 相关的`.class` 文件进行对应的处理，加入对相关的切点织入一些功能代码来完成



在上述三种实现方式中，静态代理的方式是不会被考虑的，因为这会导致系统代码的耦合，因此主要的实现方式就是动态代理和 `AspectJ`

一般来讲，使用 `AspectJ` 来实现 AOP 是最好的解决方案，但是由于 `AspectJ` 无法被 `javac` 编译，需要引入额外的编译插件才能完成，因此在 Spring 中对于 AOP 的实现是通过动态代理的方式来实现的

注意，Spring 中对于对象的管理都是以 Bean 的形式来管理的，因此 Spring AOP 相关的类只有在 Spring  IOC 中才会有意义



首先回忆一下 Spring IOC 中实例化 Bean 的基本流程：

<img src="https://i.loli.net/2021/11/08/JvYUES3pWuco9VB.png" />

分析一下实例化 Bean 的流程，可以简单地猜想一下 Spring AOP 的是否是基于 `BeanPostProcessor` 来实现的？



## 基于 Proxy Bean 的 AOP 

按照代理模式的设计，客户端会直接调用 Proxy 的接口，然后在 Proxy 中由 Proxy 对象完成实际类的接口调用，而在 Proxy 中则定义了一系列的额外操作。这是最简单的 Spring 中最简单的 AOP 实现，下文将以 `org.springframework.aop.framework.ProxyFactoryBean` 为例来分析对应的源代码。

`org.springframework.aop.framework.ProxyFactoryBean` 包含的相关属性：

```java
public class ProxyFactoryBean extends ProxyCreatorSupport
		implements FactoryBean<Object>, BeanClassLoaderAware, BeanFactoryAware {
    // 省略一部分不太重要的代码
    
    /**
    	生成的 Proxy 对象要实现的接口，当访问时将会直接调用 Proxy 的相关实现
    */
    public void setProxyInterfaces(Class<?>[] proxyInterfaces) throws ClassNotFoundException {
		setInterfaces(proxyInterfaces);
	}
    
    /* 
    	设置要代理的接口的具体实现，对应代理模式中的 RealSubject 具体类对象，
    	具体可以参考有关 JDK 动态代理的内容
    */
    public void setTarget(Object target) {
		setTargetSource(new SingletonTargetSource(target));
	}
    
    /**
    	设置拦截器，具体来讲就是对应的切面
    */
    public void setInterceptorNames(String... interceptorNames) {
		this.interceptorNames = interceptorNames;
	}
}
```

`ProxyFactoryBean` 的类结构图：

![ProxyFactoryBean.png](https://i.loli.net/2021/11/16/GEWeFbxyRvk1NwJ.png)

`ProxyFactoryBean` 只是一个简单的代理对象，并没有使用到类似 `AspectJ` 来侵入代码的方式来修改对应的对象的源代码，只是起到了一个简单的代理的作用。因此在使用时需要手动获取到这个代理对象才能正常使用 AOP 的功能，这种使用方式和使用 Bean 没有太大的差异，因此在这里不做过多的赘述，具体可以参考 Spring IOC 中有关 Bean 的实例化流程



## 基于 Auto Proxy 的 AOP<a id="autoProxy"></a>

以使用的 `org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator` 为例，它的类继承关系如下：

![BeanNameAutoProxyCreator.png](https://i.loli.net/2021/11/16/IUH1ZJMla2mVOPe.png)

可以看到，`BeanNameAutoProxyCreator` 是实现了 `BeanPostProcessor` 接口的类，因此它会在 Bean 实例化之前和实例化之后执行相应的操作。

具体的逻辑定义在 `AbstractAutowireCapableBeanFactory` 中：

```java
// 此时的 Bean 已经是实例化之后的
protected Object initializeBean(String beanName, Object bean, @Nullable RootBeanDefinition mbd) {
    // 省略一部分代码
    Object wrappedBean = bean;
    if (mbd == null || !mbd.isSynthetic()) {
        wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
    }
    
    invokeInitMethods(beanName, wrappedBean, mbd);
    // 省略一部分异常检测代码
    
    if (mbd == null || !mbd.isSynthetic()) {
        wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
    }

    return wrappedBean;
}
```

由于 `BeanNameAutoProxyCreator` 继承自 `AbstractAutoProxyCreator`，而 `AbstractAutoProxyCreator` 对于 `BeanPostProcessor` 的实现如下：

```java
// Bean 初始化（这里的初始化是在 Spring IOC 容器中的初始化，此时的 Bean 已经被实例化了，不要和 JVM 的类初始化弄混淆了）
public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
    Object cacheKey = getCacheKey(beanClass, beanName);

    // 省略一部分不太重要的代码

    TargetSource targetSource = getCustomTargetSource(beanClass, beanName);
    if (targetSource != null) { // 对于当前的 BeanNameAutoProxy 来讲，不会执行到以下部分
        if (StringUtils.hasLength(beanName)) {
            this.targetSourcedBeans.add(beanName);
        }
        Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(beanClass, beanName, targetSource);
        // 关键的地方在这里，这里会自动创建一个代理对象
        Object proxy = createProxy(beanClass, beanName, specificInterceptors, targetSource);
        this.proxyTypes.put(cacheKey, proxy.getClass());
        return proxy;
    }

    return null;
}

public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
    if (bean != null) {
        Object cacheKey = getCacheKey(bean.getClass(), beanName);
        if (this.earlyProxyReferences.remove(cacheKey) != bean) {
            /*
            	这里是最关键的部分，在这里会生成一个动态代理对象
            */
            return wrapIfNecessary(bean, beanName, cacheKey);
        }
    }
    return bean;
}
```

`wrapIfNecessary `  方法依旧定义在 `org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator` 中，具体的源代码如下所示：

```java
protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
    // 省略一部分不太重要的参数检查代码
    
    // 这里会使用到在配置时使用的 “拦截器”，这相当于 Aspect 中的 “Advisor”
    Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);
    if (specificInterceptors != DO_NOT_PROXY) {
        this.advisedBeans.put(cacheKey, Boolean.TRUE);
        // 重点！！！ 这里是核心部分，即创建代理对象
        Object proxy = createProxy(
            bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
        
        this.proxyTypes.put(cacheKey, proxy.getClass());
        return proxy;
    }

    this.advisedBeans.put(cacheKey, Boolean.FALSE);
    return bean;
}
```

`createProxy` 方法对应的源代码：

```java
/*
	该方法位于 org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator 中
*/
protected Object createProxy(
    Class<?> beanClass, 
    @Nullable String beanName,
    @Nullable Object[] specificInterceptors, // 指定的 Advisor 列表
    TargetSource targetSource // 相当于代理模式中的 RealObject
) {
    // 如果可以，那么将 targetSource 暴露给指定的 Bean，即将 RealObject 放入指定的 Bean 中（不是特别重要）
    if (this.beanFactory instanceof ConfigurableListableBeanFactory) {
        AutoProxyUtils.exposeTargetClass((ConfigurableListableBeanFactory) this.beanFactory, beanName, beanClass);
    }

    // 创建一个新的 ProxyFactory 实例对象
    ProxyFactory proxyFactory = new ProxyFactory();
    proxyFactory.copyFrom(this); // 复制相关的字段属性。。。。

    // 对应配置中的 "proxy-target-class" 属性，表示这个 Bean 是否是一个代理对象
    if (proxyFactory.isProxyTargetClass()) {
        if (Proxy.isProxyClass(beanClass)) {
            for (Class<?> ifc : beanClass.getInterfaces()) {
                proxyFactory.addInterface(ifc);
            }
        }
    }
    else {
        if (shouldProxyTargetClass(beanClass, beanName)) {
            proxyFactory.setProxyTargetClass(true);
        }
        else {
            /**
            	如果这个 beanClass 存在一个或多个接口，则将它们放入到 ProxyFactory 对象中
            	否则，将这个 beanClass 的 proxyTargetClass 属性设置为 true
            */
            evaluateProxyInterfaces(beanClass, proxyFactory);
        }
    }

    /** 
    	通过传入的 specificInterceptors 构建 Advisor，具体实现是通过 AdvisorAdapterRegistry 实例对象的
        wrap 方法对其进行包装
   	*/
    Advisor[] advisors = buildAdvisors(beanName, specificInterceptors);
    proxyFactory.addAdvisors(advisors);
    proxyFactory.setTargetSource(targetSource);
    customizeProxyFactory(proxyFactory);

    proxyFactory.setFrozen(this.freezeProxy);
    if (advisorsPreFiltered()) {
        proxyFactory.setPreFiltered(true);
    }

    // Use original ClassLoader if bean class not locally loaded in overriding class loader
    ClassLoader classLoader = getProxyClassLoader();
    if (classLoader instanceof SmartClassLoader && classLoader != beanClass.getClassLoader()) {
        classLoader = ((SmartClassLoader) classLoader).getOriginalClassLoader();
    }
    // 创建具体的 Proxy 对象
    return proxyFactory.getProxy(classLoader);
}
```

具体的 `getProxy(classLoader)` 方法的源代码：

```java
/**
	以下方法均位于 org.springframework.aop.framework.ProxyCreatorSupport 类中
*/
protected final synchronized AopProxy createAopProxy() {
    if (!this.active) {
        activate();
    }
    return getAopProxyFactory().createAopProxy(this);
}

public AopProxyFactory getAopProxyFactory() {
    return this.aopProxyFactory;
}

// 实例化 ProxyFactory 时将会调用父类的构造函数，完成 aopProxyFactory 属性字段的实例化
public ProxyCreatorSupport() {
    this.aopProxyFactory = new DefaultAopProxyFactory();
}
```

`createAopProxy(this)` 方法对应的源代码：

```java
// 该源代码位于 org.springframework.aop.framework.DefaultAopProxyFactory 中
public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
    /* 
    	如果被代理对象是一个实例对象，并且没有实现任何接口，同时也不是一个代理对象，则使用 CGLIB 动态代理；
    	否则，使用 JDK 的动态代理
    */
    if (!NativeDetector.inNativeImage() &&
        (config.isOptimize() || config.isProxyTargetClass() || hasNoUserSuppliedProxyInterfaces(config))) {
        Class<?> targetClass = config.getTargetClass();
        if (targetClass == null) {
            throw new AopConfigException("TargetSource cannot determine target class: " +
                                         "Either an interface or a target is required for proxy creation.");
        }
        if (targetClass.isInterface() || Proxy.isProxyClass(targetClass)) {
            return new JdkDynamicAopProxy(config);
        }
        return new ObjenesisCglibAopProxy(config);
    }
    else {
        return new JdkDynamicAopProxy(config);
    }
}
```



#### `JdkDynamicAopProxy`

鉴于以上的源代码，一般情况下都会选择使用 JDK 的动态代理，参考动态代理的相关内容，使用 JDK 的动态代理时必须要实现 `InvocationHandler` 的接口，`JdkDynamicAopProxy` 对于此方法的实现如下：

```java
public Object invoke(
    Object proxy, 
    Method method, 
    Object[] args
) throws Throwable {
    Object oldProxy = null;
    boolean setProxyContext = false;

    TargetSource targetSource = this.advised.targetSource;
    Object target = null;

    try {
        // 省略一部分代码

        Object retVal;

        // 如果设置了 exposeProxy，那么将 proxy 放入到 ThreadLocal 中
        if (this.advised.exposeProxy) {
            oldProxy = AopContext.setCurrentProxy(proxy);
            setProxyContext = true;
        }

        target = targetSource.getTarget();
        Class<?> targetClass = (target != null ? target.getClass() : null);

        // 创建一个 Chain，包含所有要执行的 Advice（责任链模式？）
        List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);

        if (chain.isEmpty()) { // 责任链是空的，不需要进行执行相关的 Advice 操作
            Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
            retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
        }
        else {
            // 创建一个方法调用对象
            MethodInvocation invocation =
                new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain);
            // 通过解释链对织入点执行对应的 Advice 操作
            retVal = invocation.proceed();
        }

        // 处理方法的返回值
        Class<?> returnType = method.getReturnType();
        if (retVal != null && retVal == target &&
            returnType != Object.class && returnType.isInstance(proxy) &&
            !RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
            
            retVal = proxy;
        }
        else if (retVal == null && returnType != Void.TYPE && returnType.isPrimitive()) {
            throw new AopInvocationException(
                "Null return value from advice does not match primitive return type for: " + method);
        }
        return retVal;
    }
    finally {
        if (target != null && !targetSource.isStatic()) {
            // Must have come from TargetSource.
            targetSource.releaseTarget(target);
        }
        if (setProxyContext) {
            // Restore old proxy.
            AopContext.setCurrentProxy(oldProxy);
        }
    }
}
```



#### `ObjenesisCglibAopProxy` 

使用 CGLIB 动态代理的方式来实现 Spring AOP 的功能

具体的 `getProxy` 方法对应的源代码如下：

```java
@Override
public Object getProxy(@Nullable ClassLoader classLoader) {
    Class<?> rootClass = this.advised.getTargetClass();

    Class<?> proxySuperClass = rootClass;
    if (rootClass.getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)) {
        proxySuperClass = rootClass.getSuperclass();
        Class<?>[] additionalInterfaces = rootClass.getInterfaces();
        for (Class<?> additionalInterface : additionalInterfaces) {
            this.advised.addInterface(additionalInterface);
        }
    }

    // Validate the class, writing log messages as necessary.
    validateClassIfNecessary(proxySuperClass, classLoader);

    // Configure CGLIB Enhancer...
    Enhancer enhancer = createEnhancer();
    if (classLoader != null) {
        enhancer.setClassLoader(classLoader);
        if (classLoader instanceof SmartClassLoader &&
            ((SmartClassLoader) classLoader).isClassReloadable(proxySuperClass)) {
            enhancer.setUseCache(false);
        }
    }
    enhancer.setSuperclass(proxySuperClass); // 通过继承 rootClass 来实现代理
    enhancer.setInterfaces(AopProxyUtils.completeProxiedInterfaces(this.advised));
    enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
    enhancer.setStrategy(new ClassLoaderAwareGeneratorStrategy(classLoader));

    Callback[] callbacks = getCallbacks(rootClass); // 代理对象要采取的行为，即 Advice
    Class<?>[] types = new Class<?>[callbacks.length];
    for (int x = 0; x < types.length; x++) {
        types[x] = callbacks[x].getClass();
    }
    // fixedInterceptorMap only populated at this point, after getCallbacks call above
    enhancer.setCallbackFilter(new ProxyCallbackFilter(
        this.advised.getConfigurationOnlyCopy(), this.fixedInterceptorMap, this.fixedInterceptorOffset));
    enhancer.setCallbackTypes(types);

    // Generate the proxy class and create a proxy instance.
    return createProxyClassAndInstance(enhancer, callbacks);
    
    // 省略一部分异常检查代码
}
```



通过以上两种方式来代理原有的目标 Bean，相当于在原来定义的 Bean 的方法中添加了额外的操作生成了对应的代理对象，而原来定义的 Bean 实例对象则被放入代理对象中。

现在，原来指定的 `BeanName` 将会指向代理类实例而不是原来的 Bean 实例。



## 基于 Advisor 的 AOP

基于 `Advisor` 的 AOP 会首先定义一些 `Advisor` 类型的 Bean，常见的 `Advisor` 具体类的类结构图如下所示：

<img src="https://i.loli.net/2021/11/17/d98IVLecibPCD3G.png" alt="AbstractGenericPointcutAdvisor.png" style="zoom:80%;" />

单独定义 `Advisor` 是不能直接实现 AOP 功能，需要将它们注册到一个 `AutoProxyCreator` 中使之组合来执行对应的 Advice 操作，一般会选择将 `Advisor` 注册到 `DefaultAdvisorAutoProxyCreator` 的具体类中，`DefaultAdvisorAutoProxyCreator` 的类结构如下所示：

![DefaultAdvisorAutoProxyCreator.png](https://i.loli.net/2021/11/17/TuE4MnSfFrsmRDw.png)

从类结构图中可以看到，`DefaultAdvisorAutoProxyCreator` 也是继承自 `AbstractAutoProxyCreator` 类，其中，具体的分析已经在 <a href="#autoProxy">基于 `Auto Proxy` 的 AOP </a> 中已经介绍过了，在此不再做过多的赘述。

实际上，`DefaultAdvisorAutoProxyCreator` 与 `BeanNameAutoProxyCreator` 对于 AOP 的实现基本上是相同的，它们之间的主要区别在于 `BeanNameAutoProxyCreator` 需要自己手动将拦截器 Bean 手动定义到对应的字段属性中，完成 Bean 的装配；而 `DefaultAdvisorAutoProxyCreator` 则会自动将 `BeanFactory` 中的 `Advisor` 类型的 Bean 装载到自己的容器中，相对来讲使用更为简单。它们两者对于 AOP 的实现最终都是通过 `AbstractAutoProxyCreator` 这个抽象父类来创建代理对象来实现的。



## 基于 Aspect 的 AOP

基于 `Aspect` 的 AOP 首先需要进行相关配置以开启 `@Aspect` 注解的功能，主要有两种方式可以开启 `@Aspect` ：一时在 `XML` 配置文件中加入 `<aop:aspectj-autoproxy/>`；二是在一个配置类中加入 `@EnableAspectJAutoProxy` 注解以开启 `@Aspect` 

解析 `<aop:aspectj-autoproxy/>` 对应的源代码：

```java
/**
	具体方法定义于 org.springframework.aop.config.AopNamespaceHandler 类中
*/
public void init() {
    // In 2.0 XSD as well as in 2.5+ XSDs
    registerBeanDefinitionParser("config", new ConfigBeanDefinitionParser());
    // 解析 aspectj-autoproxy 配置的内容
    registerBeanDefinitionParser("aspectj-autoproxy", new AspectJAutoProxyBeanDefinitionParser());
    registerBeanDefinitionDecorator("scoped-proxy", new ScopedProxyBeanDefinitionDecorator());

    // Only in 2.0 XSD: moved to context namespace in 2.5+
    registerBeanDefinitionParser("spring-configured", new SpringConfiguredBeanDefinitionParser());
}
```

具体解析的源码位于 `org.springframework.aop.config.AspectJAutoProxyBeanDefinitionParser`：

```java
public BeanDefinition parse(Element element, ParserContext parserContext) {
    // 核心部分。。。。。
    AopNamespaceUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(parserContext, element);
    extendBeanDefinition(element, parserContext);
    return null;
}
```

`registerAspectJAnnotationAutoProxyCreatorIfNecessary` 对应的源代码：

```java
// 该方法定义于 org.springframework.aop.config.AopNamespaceUtils 中
public static void 
    registerAspectJAnnotationAutoProxyCreatorIfNecessary(
    ParserContext parserContext, Element sourceElement) {
    
    // 核心部分。。。。
    BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(
        parserContext.getRegistry(), parserContext.extractSource(sourceElement));
    
    useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
    registerComponentIfNecessary(beanDefinition, parserContext);
}
```

`AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary` 方法对应的源代码:

```java
// 该方法定义于 org.springframework.aop.config.AopConfigUtils 中
public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(
    BeanDefinitionRegistry registry, @Nullable Object source) {
    
    /**
    	这里会注册一个 AspectJAwareAdvisorAutoProxyCreator 类型的 Bean
    */
    return registerOrEscalateApcAsRequired(AspectJAwareAdvisorAutoProxyCreator.class, registry, source);
}
```

`AspectJAwareAdvisorAutoProxyCreator` 对应的类继承关系如下：

![AspectJAwareAdvisorAutoProxyCreator.png](https://i.loli.net/2021/11/17/u5XndirO4se7PUJ.png)

可以看到，`AspectJAwareAdvisorAutoProxyCreator` 也是继承自 `AbstractAutoProxyCreator` 抽象父类，具体已经在 <a href="#autoProxy">基于 Auto Proxy 的AOP </a> 中详细介绍，在此不做过多的赘述。

`AnnotationAwareAspectJAutoProxyCreator` 是处理有关 `@Aspect` 注解的类，它将添加了 `AspecJ` 注解的 Bean 处理为 Spring 中对应的 `Advisor`，具体处理逻辑在此不做详细介绍



参考：

<sup>[1]</sup> https://javadoop.com/post/spring-aop-source
