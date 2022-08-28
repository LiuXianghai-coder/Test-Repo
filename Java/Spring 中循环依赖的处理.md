# Spring 中循环依赖的处理

Spring 提供了十分强大的依赖注入功能，使得我们不再需要手动去管理对象的依赖项。然而，在实际的使用场景中，可能会遇到类似下面的依赖异常：

``` text
Exception encountered during context initialization - cancelling refresh attempt: org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'author' defined in file [/home/lxh/JavaProject/sample/target/classes/com/example/sample/component/Author.class]: Unsatisfied dependency expressed through constructor parameter 0; nested exception is org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'book' defined in file [/home/lxh/JavaProject/sample/target/classes/com/example/sample/component/Book.class]: Unsatisfied dependency expressed through constructor parameter 0; nested exception is org.springframework.beans.factory.BeanCurrentlyInCreationException: Error creating bean with name 'author': Requested bean is currently in creation: Is there an unresolvable circular reference?
```

一般来讲，这种情况的出现都是由于 Bean 类的设计不合理导致的，通常的解决方案都是选择重新设计 Bean 类的组织结构来打破循环依赖。然而，在某些特定的情况下，可能没有办法重新设计这些类组织结构（比如旧有代码的遗留问题等），这种情况有几种特殊的处理方式。但是在介绍这些处理方式之前，让我们先来了解一下 Spring 中对于循环依赖的处理

## 出现原因

回想一下 Spring 中获取 Bean 的流程，详情可以参考：https://javadoop.com/post/spring-ioc，获取 Bean 的大概流程如下（没有代理的情况）：

1. 获取 Bean 类型的实例对象（可以理解为 `new` 关键字创建的对象）
2. 填充 Bean 实例的相关属性（即 `@Resource` 或 `@Autowire` 注解标记的属性）
3. 应用 `BeanFactory` 的后置方法和注册销毁方法

通过分析流程，可以发现问题可能出现在第一步或者第二步的处理过程中。首先，我们分析一下获取 Bean 类型的实力对象的过程，这个过程是在 `org.springframework.beans.factory.support.DefaultSingletonBeanRegistry` 的 `getSingleton` 方法中完成的：

``` java
/**
* singletonObjects 存储已经创建好的 Bean 实例，这在一些博客中也被称为 “一级缓存”
*/
private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

/**
* singletonFactories 存储创建 Bean 的工厂对象，这也被称为 “三级缓存”
*/
private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

/**
* earlySingletonObjects 存储已经创建好但是没有填充相关属性的 Bean，也被称为 “二级缓存”
*/
private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

protected Object getSingleton(String beanName, boolean allowEarlyReference) {
    // Quick check for existing instance without full singleton lock
    Object singletonObject = this.singletonObjects.get(beanName);
    if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
        singletonObject = this.earlySingletonObjects.get(beanName);
        if (singletonObject == null && allowEarlyReference) {
            synchronized (this.singletonObjects) {
                // Consistent creation of early reference within full singleton lock
                singletonObject = this.singletonObjects.get(beanName);
                if (singletonObject == null) {
                    singletonObject = this.earlySingletonObjects.get(beanName);
                    if (singletonObject == null) {
                        ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
                        if (singletonFactory != null) {
                            singletonObject = singletonFactory.getObject();
                            this.earlySingletonObjects.put(beanName, singletonObject);
                            this.singletonFactories.remove(beanName);
                        }
                    }
                }
            }
        }
    }
    return singletonObject;
}
```



