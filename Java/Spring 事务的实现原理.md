# Spring 事务的实现原理

在执行访问数据库相关的操作中，特别是针对数据的修改操作，由于对于数据的修改可能会出现异常，因此对于整个一组的数据修改实际上都不能算是生效的，在这种情况下，需要使用事务的 “回滚” 来撤销本次执行的操作；而在执行成功之后，需要手动将这一组操作提交给数据库管理系统，使得对于数据的修改能够生效，这种操作在事务中也被称为 “提交”。

有关事务的内容可以参见：<a href="https://zh.m.wikipedia.org/zh-sg/%E6%95%B0%E6%8D%AE%E5%BA%93%E4%BA%8B%E5%8A%A1">数据库事务</a>。在实际的开发过程中，同样需要手动提交或者回滚来处理事务，这是一项十分繁琐的工作，同时，手动提交事务也不便于统一进行管理，因此一般会将事务的处理作为一个<a href="https://en.wikipedia.org/wiki/Aspect_(computer_programming)">切面</a>来进行统一的处理。在一般的场景下，都会使用 Spring 作为开发容器并通过 `@Transactional` 注解来对事务进行统一的管理，本文将对 Spring 中 `@Transactional` 的使用以及实现做简要的概述

## 基本使用

首先查看 `@Transactional` 注解对应的源代码：

``` java
package org.springframework.transaction.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.TransactionDefinition;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Transactional {
    @AliasFor("transactionManager")
	String value() default "";
    
    /**
    * 这个值用于指定相关的事务管理类，可以通过 Bean 的名称来指定
    */
    @AliasFor("value")
	String transactionManager() default ""; 
    
    /**
   	* 用于描述事务的相关属性
    */
    String[] label() default {};
    
    /**
    * 事务的传播类型，默认为支持当前事务，如果当前事务不存在，则创建一个事务
    */
    Propagation propagation() default Propagation.REQUIRED;
    
    /**
    * 事务的隔离级别，默认为 DBMS 默认的事务隔离级别
    */
    Isolation isolation() default Isolation.DEFAULT;
    
    /**
    * 事务的超时时间，默认为 DBMS 的事务超时时间
    */
    int timeout() default TransactionDefinition.TIMEOUT_DEFAULT;
    
    String timeoutString() default "";
    
    /**
    * 如果事务是只读的，那么可以将这个字段设置为 true，以提高系统的性能
    */
    boolean readOnly() default false;
    
    /**
    * 引起事务回滚的异常类，这个属性在自定义异常回滚时可以使用
    */
    Class<? extends Throwable>[] rollbackFor() default {};
    
    String[] rollbackForClassName() default {};
    
    /**
    * 不会导致事务回滚的异常类
    */
    Class<? extends Throwable>[] noRollbackFor() default {};
    
    String[] noRollbackForClassName() default {};
}
```

在使用 `@Transactional` 注解来自动管理事务之前，需要做开启事务管理，定义类似如下的配置 Bean，开启事务管理支持：

``` java
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author lxh
 * @date 2022/7/23-下午9:24
 */
@Configuration
@EnableTransactionManagement // 开启 Spring 的事务管理
public class TransactionalConfig {
    @Bean // 相关的事务管理类	
    public PlatformTransactionManager transactionManager() {
        DataSourceTransactionManager manager = new DataSourceTransactionManager();
        manager.setDataSource(new PooledDataSource("com.mysql.cj.jdbc.Driver",
                "jdbc:mysql://127.0.0.1:3306/lxh", "root", "123456"));
        return manager;
    }
}
```

对于 Spring Boot 类型的项目来讲，在自动配置的过程中已经完成了相关的配置，只需加入对应的 `JDBC` 依赖项来是的自动配置生效即可：

``` xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-jdbc</artifactId>
</dependency>
```

在自动引入 `spring-boot-starter-data*` 的依赖时会包含此依赖项，因此一般情况下都不需要手动引入 `JDBC` 的依赖

当做了上面的一些配置之后，现在就可以使用 Spring 的 `@Transactional` 注解来使得事务生效，例如，对于下面的 `Service` 类，现在 Spring 就会自动生成对应的代理类来实现相关的事务行为：

``` java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xhliu.demo.entity.UserInfo;
import org.xhliu.demo.mapper.UserInfoMapper;
import org.xhliu.demo.service.UserService;

import javax.annotation.Resource;

/**
 * @author lxh
 * @date 2022/7/21-下午11:02
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class UserServiceImpl implements UserService {

    @Resource
    private UserInfoMapper userInfoMapper;

    @Override
    public void initUserInfos() {
        UserInfo userInfo = userInfoMapper.selectByUserName("yyf");
        if (userInfo == null) {
            UserInfo valObj = new UserInfo();
            valObj.setUserName("yyf");
            valObj.setUserAge(23);
            valObj.setDescribe("");
            userInfoMapper.insertUserInfo(valObj);
        }
    }
}
```

由于现在的 `@Transactional` 注解加在类上，因此对于当前类的所有 `public` 修饰的方法都会具有相关的事务行为。

值得一提的是，`@Transactional` 注解不仅仅可以添加在类上，而且可以加在接口、类或者类的相关方法上，当 `@Transactional` 在多个地方同时定义时，会按照以下的优先级进行处理（高—>低）：接口（类级别）、父类（类级别）、类（类级别）、接口内方法（方法级别）、父类方法（方法级别）以及本类方法（方法级别）

## 实现原理

根据 `@Transactional` 注解存在的行为，可以简单的推断一下实现的原理，很明显，这是在执行前后添加了对应的逻辑。而在 Java 中实现这样的功能也被成为 `AOP`（面向切面编程），实现 `AOP` 的方式目前就两种主流的方式：AspectJ 和代理。结合 Spring 中对于 AOP 的实现，可以大致推断出实现方式为代理的实现方式

### 相关 Bean 的加载

结合 Spring Boot 自动装载 Bean 的流程可以发现，对于事务的处理都是在 `org.springframework.boot.autoconfigure.jdbc.TransactionAutoConfiguration` 配置中中完成自动装配。比较关键的代码如下：

``` java
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(TransactionManager.class) // 只有当 Bean 容器中存在 TransactionManager 类型的 Bean 时才加载下面的 Bean
// 只有当 Bean 容器中不存在 AbstractTransactionManagementConfiguration 类型的 Bean 时才进行后续 Bean 的加载
@ConditionalOnMissingBean(AbstractTransactionManagementConfiguration.class)
public static class EnableTransactionManagementConfiguration {

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = false)
    @ConditionalOnProperty(prefix = "spring.aop", name = "proxy-target-class", havingValue = "false")
    public static class JdkDynamicAutoProxyConfiguration {

    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @ConditionalOnProperty(prefix = "spring.aop", name = "proxy-target-class", havingValue = "true",
                           matchIfMissing = true)
    public static class CglibAutoProxyConfiguration {

    }

}
```

**Note：**在这里 `TransactionManager` 类型的 Bean 在实际的使用场景中，一般是 `org.springframework.jdbc.support.JdbcTransactionManager`，而 `AbstractTransactionManagementConfiguration` 的具体实现类在一般 `JDBC`  场景下的具体实现类为 `org.springframework.transaction.annotation.ProxyTransactionManagementConfiguration`

在 `ProxyTransactionManagementConfiguration` 配置类中，最为核心的部分是有关切面的定义，具体代码如下：

``` java
@Bean(name = TransactionManagementConfigUtils.TRANSACTION_ADVISOR_BEAN_NAME)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public BeanFactoryTransactionAttributeSourceAdvisor transactionAdvisor(
    TransactionAttributeSource transactionAttributeSource, TransactionInterceptor transactionInterceptor) {

    BeanFactoryTransactionAttributeSourceAdvisor advisor = new BeanFactoryTransactionAttributeSourceAdvisor();
    advisor.setTransactionAttributeSource(transactionAttributeSource);
    advisor.setAdvice(transactionInterceptor);
    if (this.enableTx != null) {
        advisor.setOrder(this.enableTx.<Integer>getNumber("order"));
    }
    return advisor;
}

/**
* 由于注入时会考虑 Bean 的名称，下面的两个 Bean 将会分别注入到上面对应的参数中
*/
@Bean
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public TransactionAttributeSource transactionAttributeSource() {
    return new AnnotationTransactionAttributeSource();
}

@Bean
@Role(BeanDefinition.ROLE_INFRASTRUCTURE) // 对应的拦截器，定义了具体的处理逻辑
public TransactionInterceptor transactionInterceptor(TransactionAttributeSource transactionAttributeSource) {
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionAttributeSource(transactionAttributeSource);
    if (this.txManager != null) {
        interceptor.setTransactionManager(this.txManager);
    }
    return interceptor;
}
```

### `@Transactional` 注解属性的处理

注解的目的是用于提供相关的元数据信息，这些元数据信息可以保留到运行时。注解本身不具备任何业务逻辑，所有的业务逻辑都需要对应的业务代码进行处理。在当前的 `JDBC`的环境下，对于 `@Transactional` 注解来讲，结合上文的参数 Bean，可以看到 `org.springframework.transaction.annotation.AnnotationTransactionAttributeSource` 的实例化逻辑：

``` java
public AnnotationTransactionAttributeSource() {
    this(true);
}

public AnnotationTransactionAttributeSource(boolean publicMethodsOnly) {
    this.publicMethodsOnly = publicMethodsOnly;
    if (jta12Present || ejb3Present) { // 这两种类型的事务未曾遇到过
        this.annotationParsers = new LinkedHashSet<>(4);
        this.annotationParsers.add(new SpringTransactionAnnotationParser());
        if (jta12Present) {
            this.annotationParsers.add(new JtaTransactionAnnotationParser());
        }
        if (ejb3Present) {
            this.annotationParsers.add(new Ejb3TransactionAnnotationParser());
        }
    }
    else {
        // 因此最后的注解处理类就是 SpringTransactionAnnotationParser
        this.annotationParsers = Collections.singleton(new SpringTransactionAnnotationParser());
    }
}
```

可以看到，最终在 `AnnotationTransactionAttributeSource` 中对于 `@Transacational` 注解的处理是通过 `SpringTransactionAnnotationParser` 来进行解析，查看对应解析 `@Transactional`注解元数据的源码如下：

``` java
@Override
@Nullable
public TransactionAttribute parseTransactionAnnotation(AnnotatedElement element) {
    AnnotationAttributes attributes = AnnotatedElementUtils.findMergedAnnotationAttributes(
        element, Transactional.class, false, false);
    if (attributes != null) {
        // 在这里完成 @Transactional 注解的元数据的解析，其实就是获取注解的方法的返回值而已
        return parseTransactionAnnotation(attributes);
    }
    else {
        return null;
    }
}
```

### 代理对象的实例化

回想一下 Spring 中 Bean 的生命周期，在较低版本的 Spring 中，为了处理循环依赖，对于代理对象的处理在 `org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory` 的 `getEarlyBeanReference` 方法处理对应的依赖项，处理依赖的同时也会创建对应的代理对象实例（对非依赖项将会在初始化 Bean 时对处理逻辑进行代理，但是逻辑是相同的），具体的源码如下：

``` java
protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
    Object exposedObject = bean;
    if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
            exposedObject = bp.getEarlyBeanReference(exposedObject, beanName);
        }
    }
    return exposedObject;
}
```

实际上创建代理的 `SmartInstantiationAwareBeanPostProcessor`  为 `AbstractAutoProxyCreator`，对应的逻辑如下：

``` java
public Object getEarlyBeanReference(Object bean, String beanName) {
    Object cacheKey = getCacheKey(bean.getClass(), beanName);
    this.earlyProxyReferences.put(cacheKey, bean);
    return wrapIfNecessary(bean, beanName, cacheKey); // 对目标类进行封装，得到实际的代理对象
}

protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
    // 省略部分代码

    // 根据当前对象的类型，检查能够适应到的拦截方法，从而创建对应的代理对象
    Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);
    if (specificInterceptors != DO_NOT_PROXY) {
        this.advisedBeans.put(cacheKey, Boolean.TRUE);
        Object proxy = createProxy(
            bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
        this.proxyTypes.put(cacheKey, proxy.getClass());
        return proxy;
    }

    this.advisedBeans.put(cacheKey, Boolean.FALSE);
    return bean;
}
```

### 事务的处理逻辑

前文我们提到过，在 `BeanFactoryTransactionAttributeSourceAdvisor` 切面中定义的拦截器为 `TransactionInterceptor`，因此在创建对应的代理类时会将对应的处理逻辑加入到生成的代理类中，而在 `TransactionInterceptor` 中定义的处理逻辑如下：

``` java
// invoke 方法可以说是代理中的熟客了
public Object invoke(MethodInvocation invocation) throws Throwable {
    // Work out the target class: may be {@code null}.
    // The TransactionAttributeSource should be passed the target class
    // as well as the method, which may be from an interface.
    Class<?> targetClass = (invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null);

    // Adapt to TransactionAspectSupport's invokeWithinTransaction...
    return invokeWithinTransaction(invocation.getMethod(), targetClass, new CoroutinesInvocationCallback() {
        @Override
        @Nullable
        public Object proceedWithInvocation() throws Throwable {
            return invocation.proceed();
        }
        @Override
        public Object getTarget() {
            return invocation.getThis();
        }
        @Override
        public Object[] getArguments() {
            return invocation.getArguments();
        }
    });
}
```

继续向下跟踪处理逻辑，可以看到在 `org.springframework.transaction.interceptor.TransactionAspectSupport` 的 `invokeWithinTransaction` 中定义了事务处理的相关逻辑：

``` java
protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
                                         final InvocationCallback invocation) throws Throwable {
    
    TransactionAttributeSource tas = getTransactionAttributeSource();
    final TransactionAttribute txAttr = (tas != null ? tas.getTransactionAttribute(method, targetClass) : null);
    final TransactionManager tm = determineTransactionManager(txAttr);

    // 省略 Reactive 中相关的事务处理逻辑

    PlatformTransactionManager ptm = asPlatformTransactionManager(tm);
    final String joinpointIdentification = methodIdentification(method, targetClass, txAttr);

    // 这里的 ptm 在 JDBC 环境下为 JdbcTransactionManager，因此实际上这里就是对应的事务处理的相关逻辑
    if (txAttr == null || !(ptm instanceof CallbackPreferringPlatformTransactionManager)) {
        /* 
        	根据 @Transactional 中 propagation 属性开启事务（其实只是创建了一个对 TransactionManager 的引用对象而已，实际的
        	事务管理最终都需要通过 TransationManager 对象来进行处理）
        */
        TransactionInfo txInfo = createTransactionIfNecessary(ptm, txAttr, joinpointIdentification);

        Object retVal;
        try {
            retVal = invocation.proceedWithInvocation();
        }
        catch (Throwable ex) {
            // 事务的回滚处理
            completeTransactionAfterThrowing(txInfo, ex);
            throw ex;
        }
        finally {
            cleanupTransactionInfo(txInfo);
        }

        if (retVal != null && vavrPresent && VavrDelegate.isVavrTry(retVal)) {
            // Set rollback-only in case of Vavr failure matching our rollback rules...
            TransactionStatus status = txInfo.getTransactionStatus();
            if (status != null && txAttr != null) {
                retVal = VavrDelegate.evaluateTryFailure(retVal, txAttr, status);
            }
        }

        commitTransactionAfterReturning(txInfo); // 提交事务
        return retVal;
    }
    // 省略部分代码。。。。
}
```

## 实际场景中遇到的一些问题

- 注解作用于方法

  由于 Spring 对于 `@Transactional` 注解的实际事务实现是通过代理的方式来实现的，那么这样做可能会存在以下一些问题：

  - 对于非 `public` 或 `protected` 修饰符修饰的方法，事务是失效的，而对于`protected` 修饰的方法，当使用 JDK 的动态代理时，也会导致失效。这是因为对于代理的实现来讲，不管是何种动态代理的实现，都要至少保证能够访问到对应的方法

  - 对于方法的内部方法调用，那么即使内部方法是通过 `public`修饰符修饰的，但是在此时调用它的方法中依旧是事务失效的。以下图为例：

    ![proxy.png](https://s2.loli.net/2022/08/16/pHD7ZyOv53FBC6o.png)

    由于被代理的方法 `MethodA` 在被代理时，内部调用的 `MethodB` 依旧是原有对象中的 `MethodB`，因此对于 `MethodB` 的代理结果将不会反映到 `MethodA` 中对于 `MethodB` 的调用中

- 事务的回滚失效

  结合对应的源码，可以分析一下造成事务回滚失效的可能原因：事务的代理没有生效？有没有达到触发回滚的条件？如果是代理没有生效，那么就需要检查一下代理的方法是否居于可访问的权限，以及访问的方法确实是被代理的方法。如果是后者的原因，那么可以检查一下对应的 `@Transactional` 属性设置的相关值，如果抛出的异常不是会导致回滚的异常，那么此时的回滚必然是失效的；而更加一般的情况时，大多数人会尝试使用 `try{}catch{}` 来捕获异常，使得代理的逻辑无法接收到异常，从而导致事务回滚失效