# Spring AOP 简介

—— 本文将简单介绍一下有关 Spring AOP 的概念以及基本的使用



Spring AOP 是 Spring 中对于 AOP 的支持与实现。在 Spring 中，AOP 的实现是通过动态代理的方式来实现的，这是由于 Spring IOC 的存在，对于对象实例的控制更加方便，同时也为了降低开发的难度以及更加切合 Spring  导致的。尽管使用 `AspectJ` 的方式来实现 AOP 是目前公认的最好的解决方案，但是对于 Spring 来说，使用动态代理的方式才是最 “适合” 的。



在 Spring 中配置 AOP 主要有以下几种方式：

- 基于接口的配置  
- 基于配置文件的配置
- 基于 `@Aspect` 的配置



下文将简要介绍这几种配置的方式，为了描述方便，首先定义以下几个类：

- 具体参与业务的实体类 `User` 和 `Order``

  `User` 实体类：

  ```java
  public class User {
      private final String firstName;
  
      private final String lastName;
  
      public User(String firstName, String lastName) {
          this.firstName = firstName;
          this.lastName = lastName;
      }
  
      public String getFirstName() {
          return firstName;
      }
  
      public String getLastName() {
          return lastName;
      }
  
      @Override
      public String toString() {
          return "User{" +
              "firstName='" + firstName + '\'' +
              ", lastName='" + lastName + '\'' +
              '}';
      }
  }
  ```

  

  `Order` 实体类：

  ```java
  public class Order {
      private final String userName;
  
      private final String product;
  
      public Order(String userName, String product) {
          this.userName = userName;
          this.product = product;
      }
  
      // 构建者模式来构建对象
      public static class Builder {
          private String userName;
          private String product;
  
          public Builder userName(String userName) {
              this.userName = userName;
              return this;
          }
  
          public Builder product(String product) {
              this.product = product;
              return this;
          }
  
          public Order build() {
              return new Order(this.userName, this.product);
          }
      }
  
      public String getUserName() {
          return userName;
      }
  
      public String getProduct() {
          return product;
      }
  
      @Override
      public String toString() {
          return "Order{" +
              "userName='" + userName + '\'' +
              ", product='" + product + '\'' +
              '}';
      }
  }
  
  ```

  

- 定义的顶层业务行为的接口 `UserService` 和 `OrderService`：

  `UserService`：

  ```java
  public interface UserService {
      User createUser(String firstName, String lastName);
  
      User queryUser();
  }
  ```

  `OrderService`：

  ```java
  public interface OrderService {
      Order createOrder(String userName, String product);
  
      Order queryOrder(String userName);
  }
  ```



- 简单的业务操作的具体实现类：

  `UserServiceImpl`：

  ```java
  public class UserServiceImpl implements UserService {
      private static User user = null;
  
      public User createUser(String firstName, String lastName) {
          user = new User(firstName, lastName);
          return user;
      }
  
      public User queryUser() {
          return user;
      }
  }
  ```

  

  `OrderServiceImpl`：

  ```java
  public class OrderServiceImpl implements OrderService {
      private static Order order;
  
      public Order createOrder(String userName, String product) {
          order = new Order.Builder()
              .userName(userName).product(product).build();
          return order;
      }
  
      public Order queryOrder(String userName) {
          return order;
      }
  }
  ```



- 定义的两个切点操作

  `LogArgsAdvice`

  ```java
  package org.xhliu.aop.service.impl;
  
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.aop.MethodBeforeAdvice;
  
  import java.lang.reflect.Method;
  import java.util.Arrays;
  
  // 在执行方法之前要执行的操作
  public class LogArgsAdvice implements MethodBeforeAdvice {
      private final static Logger log = LoggerFactory.getLogger(LogArgsAdvice.class);
  
      public void before(Method method, Object[] objects, Object o) {
          log.info("准备执行方法: " + method.getName() + ", 参数列表: " + Arrays.toString(objects));
      }
  }
  
  ```

  `LogResultAdvice` 

  ```java
  package org.xhliu.aop.service.impl;
  
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.aop.AfterReturningAdvice;
  
  import java.lang.reflect.Method;
  
  // 在执行方法之后采取的行为
  public class LogResultAdvice implements AfterReturningAdvice {
      private final static Logger log = LoggerFactory.getLogger(LogArgsAdvice.class);
  
      public void afterReturning(Object o, Method method, Object[] objects, Object o1)
          throws Throwable {
          log.info("方法执行完成之后, 得到的结果: " + o);
      }
  }
  
  ```

  



## 基于接口的配置

尽管基于 XML 配置的方式来配置 Bean 的方式已经过时，但是由于使用配置类的方式来讲解不是那么的很好懂，因为它不能很明显地查看到 Bean 的配置，为了能够更好地说明 AOP 的相关知识，在此依旧使用 XML 配置的方式来做为示例来介绍

首先，创建一个名为 `application.xml` 的 XML 文件放入到 项目的 `resources` 目录下，如果没有可以参考一下 `maven` 的项目结构进行配置。

将前文定义的实现类和 Advice 类放入配置文件中，具体如下所示：

```xml
<?xml version="1.0" encoding="utf-8" ?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
    http://www.springframework.org/schema/beans/spring-beans-3.0.xsd">

	<!-- 注意将具体的包名换成对应的包 -->
    <bean id="orderService" class="org.xhliu.aop.service.impl.OrderServiceImpl" />
    <bean id="userService" class="org.xhliu.aop.service.impl.UserServiceImpl" />

    <bean id="logArgsAdvice" class="org.xhliu.aop.service.impl.LogArgsAdvice" />
    <bean id="logResultAdvice" class="org.xhliu.aop.service.impl.LogResultAdvice" />
</beans>

```

现在，按照动态代理的一般流程，需要定义一个代理类来实现一些额外的行为，正好，Spring 中就有好几个这样的类，将一个最简单的 `org.springframework.aop.framework.ProxyFactoryBean` 作为示例演示：

```xml
<!-- 这里的代理 Bean 现在类型也是 UserService -->
<bean id="userServiceProxy" class="org.springframework.aop.framework.ProxyFactoryBean">
    <!-- 要代理的接口 -->
    <property name="proxyInterfaces">
        <list>
            <value>org.xhliu.aop.service.UserService</value>
        </list>
    </property>

    <!-- 要代理的接口的具体实现 -->
    <property name="target" ref="userService" />

    <property name="interceptorNames">
        <!-- 配置拦截器列表，可以配置 advice、advisor、interceptor -->
        <list>
             <!-- 拦截器要采取的行为，<value> 中的值为已经定义的两个 Bean -->
            <value>logArgsAdvice</value>
            <value>logResultAdvice</value>
        </list>
    </property>
</bean>
<!-- 也可以定义 OrderService 的代理类，与之类似，在此省略。。。。。 -->
```



通过加载这个 XML 配置文件，进行调用：

```java
package org.xhliu.aop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.xhliu.aop.service.OrderService;
import org.xhliu.aop.service.UserService;

public class Application {

    private final static Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args ) {
        ApplicationContext context =
            new ClassPathXmlApplicationContext("classpath:application.xml");
        // 注意，由于代理的 Bean 的类型也是 UserService，因此不能通过类型来获取唯一的 Bean 实例
        UserService userService = context.getBean("userServiceProxy", UserService.class);

        log.info(userService.createUser("Xhanghai", "Liu").toString());
        log.info(userService.queryUser().toString());
    }
}
```

运行的结果如下图所示：

![2021-11-13 21-52-48 的屏幕截图.png](https://i.loli.net/2021/11/13/AjFHtvdBViZxgGs.png)

可以看到，我们的 Advice 类已经生效了

除了上文用到的 `org.springframework.aop.framework.ProxyFactoryBean` 代理类之外，在 Spring 中还存在 `org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator `、`org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator` 等一些其它有用的类来生成需要的代理对象。

- `NameMatchMethodPointcutAdvisor`

  这里有一些关于 AOP 的概念，Advisor 是具体的监视器类，Advice 是对监视的位置所采取的一系列行为。了解了这两个概念之后，现在使用 `NameMatchMethodPointcutAdvisor` ，在 XML 中配置如下：

  ```xml
  <bean id="logCreateAdvisor" class="org.springframework.aop.support.NameMatchMethodPointcutAdvisor">
      <!-- advisor 实例内部只会有一个 advice -->
      <property name="advice" ref="logArgsAdvice" />
      <!-- 只有下面 value 中定义的两个方法才会被拦截，可以配置多个，以 , 分隔 -->
      <property name="mappedNames" value="createUser,createOrder" />
  </bean>
  ```

  如果想要能够通过正则表达式的方式来匹配方法，可以考虑使用 `RegexpMethodPointcutAdvisor`

  具体配置如下：

  ```xml
  <!-- 使用正则表达式的方式来匹配来拦截 create.* 的方法 -->
  <bean id="logArgsAdvisor" class="org.springframework.aop.support.RegexpMethodPointcutAdvisor">
      <!-- advisor 实例内部只会有一个 advice -->
      <property name="advice" ref="logArgsAdvice" />
      <!-- 这里表示匹配 org.xhliu.aop.service.impl 包下的所有类的 create 开头的方法 -->
      <property name="pattern" value="org.xhliu.aop.service.impl.*.create.*" />
  </bean>
  ```

  通过定义的 Advisor，不需要再手动获取代理 Bean，Advisor 将会自动完成匹配到的方法的拦截并采取相应的行为（但是依旧依赖于相关的 Bean 来使得它生效，具体可以参考 <a href="#DefaultAdvisorAutoProxyCreator">DefaultAdvisorAutoProxyCreator</a>）

  

- `BeanNameAutoProxyCreator`

  使用 `ProxyFactoryBean` 手动配置代理 Bean 过于繁琐，Spring 便提供了更加方便的 Proxy Bean 来自动创建，具体的示例如下：

  ```xml
  <!-- 这里定义的 Bean 的类型也不是 UserService，这更加像一个切面类 -->
  <bean class="org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator">
      <property name="interceptorNames">
          <!-- 定义的拦截器 Bean 列表，定义相关的行为 -->
          <list>
              <value>logResultAdvice</value>
          </list>
      </property>
  
      <!-- 这里的 value 指的是在 BeanFactory 中的名字（即 BeanName），可以使用正则表达式进行匹配 -->
      <property name="beanNames" value="*Service"/>
  </bean>
  ```

  同样地，这个 Bean 会在方法调用时自动完成拦截而不是需要手动获取代理类

  

- `DefaultAdvisorAutoProxyCreator`<a id="DefaultAdvisorAutoProxyCreator"></a>

  如果上述的配置觉得还是有点繁琐，那么可以使用 `DefaultAdvisorAutoProxyCreator`，具体示例如下：

  ```xml
  <!-- 定义 Advisor 自动代理对象，这个对象会将所有的 Advisor 存入，然后执行每个 Advisor 对应的 Advice -->
  <bean class="org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator" />
  ```

  以上配置会将所有的 Advisor 装载到一个 Bean 中，除了需要自己定义 Advisor 之外，剩下的工作都交给了这个 Bean 来完成



## 基于配置文件的配置

基于配置文件的配置指的是在 XML 中定义 AOP 的相关行为，注意区分这些细节

一个配置实例如下：

```xml
<!-- 通过 XML 的方式来配置织入 Bean -->
<aop:config>
    <!-- 注意，在 XML 中定义全局的 PointCut 时，需要将 <aop:pointcut> 标签放在最顶部 -->

    <!-- 以下方式定义的 PointCut 是 mineAspect 私有的 -->
    <aop:aspect id="mineAspect" ref="mineArgAspect">
        <aop:pointcut id="minePintCut"
                      expression="execution(* org.xhliu.aop.service.impl.UserServiceImpl.*(..))"/>
        <aop:before method="logArgs" pointcut-ref="minePintCut" /> <!-- 引用相关的 PointCut，执行对应的操作 -->
    </aop:aspect>
</aop:config>

<bean name="mineArgAspect" class="org.xhliu.aop.aspect.MineArgAdvice"/>
```

这里涉及到几个概念：Pointcut（切点）一般指的是方法调用时、抛出异常时、返回值时，可以看做是一个程序执行时的一个关键点；Aspect（切面）是在相关切点上定义行为的对象，可以简单理解为一个监视器



`MineArgAdvice` 的类定义如下：

```java
import org.aspectj.lang.JoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class MineArgAdvice {
    private final static Logger log = LoggerFactory.getLogger(MineArgAdvice.class);
    
    // 在织入点之后的行为
    public void logResult(Object result) {
        log.info("Get Result = " + result.toString());
    }
    
    // JointPoint 是织入点，在当前环境下表示的是将要执行的方法
    public void logArgs(JoinPoint joinPoint) {
        log.info("Before method execution, print params = " + Arrays.toString(joinPoint.getArgs()));
    }
}

```

以上的配置就完成了对 `org.xhliu.aop.service.impl.UserServiceImpl` 包下所有类定义的方法的监听，在调用方法时将会执行对应的行为，具体的切点匹配的 `expression` 将会在 <a href="#aspect">基于 @Aspect 注解的配置</a>中进行说明



## 基于 `@Aspect` 注解的配置 <a name="aspect"></a>

`@Aspect`  使用了 `AspectJ` 中的一些概念，但是也是仅仅只是使用了 `AspectJ` 中的一些概念，包括使用到的注解等，但是这两者之间是两个东西

要使用 `@Aspect` 注解，首先得添加对应的 `Aspect` 依赖项，这是因为要使用到相关的注解:

```xml
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjweaver</artifactId>
    <version>1.9.7</version>
</dependency>
```

按照一般的 `AspectJ` 的使用流程，首先需要定义一个切面类，定义一些切点（也可以直接在切面类中定义切点，这样的切点将会是切面类私有的）：

```java
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 从全局的角度来定义一些切点
 *
 * PointCut，相当于一个程序执行点，
 * 一般来讲，一个方法的执行前、执行后；return 语句执行前、执行后；异常抛出前，抛出后 都会被认为是一个 PointCut
 *
 * 具体要在这些执行点进行何种操作，需要配置相应的切面类来定义
 **/
@Aspect // Aspect 表示定义的这个类是一个切面类
public class SystemArchitecture {
    private final static Logger log = LoggerFactory.getLogger(SystemArchitecture.class);

    /*
        使用 @PointCut 注解来定义切点

        匹配切点的方式主要有以下几种（都支持正则表达式匹配）
            execution：用于匹配方法签名，表示执行 **** 方法时
            within：表示当前所在类或者所在包下面的方法
            @annotation：方法上存在的特定注解，如 @PointCut("execution(.*(..)) && @annotation(Subscribe)")
                         就会匹配所有方法参数中带有 @Subscribe 注解的方法
            bean：用于匹配 Bean 的名字
    */

    /**
     * org.xhliu.web 包下的所有类中存在的方法都定义一个 PointCut
     */
    @Pointcut("within(org.xhliu.web.*)")
    public void inWebLayer(){
    }

    /**
     * 为 org.xhliu.service 包下的所有类中存在的方法都定义一个 PointCut
     */
    @Pointcut("within(org.xhliu.service.impl.*)")
    public void inServiceLayer(){
    }

    /**
     * 为 org.xhliu.dao 包下的所有类中存在的方法创建 PointCut
     */
    @Pointcut("within(org.xhliu.dao.*)")
    public void inDaoLayer(){
    }

    /**
     * 使用 '.' 来表示一个包名，'..' 表示当前包以及子包，(..) 则表示匹配方法的任意参数
     *
     * 为 org.xhliu.service 类中存在的方法创建 PointCut
     */
    @Pointcut("within(org.xhliu.service.impl.*)")
    public void businessService(){
    }

    /**
     * 为 org.xhliu.dao 包及子包中的所有类声明的方法创建 PointCut
     */
    @Pointcut("execution(* org.xhliu.dao.*.*(..))")
    public void dataAccessOperation(){
    }
}
```

然后再定义一个切面类，用于定义在切点上采取的行为（行为也被称为 Advice）：

```java
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

@Aspect
public class LogArgsAspect {
    private final static Logger log = LoggerFactory.getLogger(LogArgsAspect.class);

    /**
    * 在 org.xhliu.aop.aspect.SystemArchitecture.businessService() 切点执行前，
    * 首先运行下面定义的方法
    */
    @Before("org.xhliu.aop.aspect.SystemArchitecture.businessService()")
    public void logArgs(JoinPoint joinPoint) { // JointPoint 表示的是织入点，可以获取到执行的上下文信息
        log.info("Before method execution, print params = " + Arrays.toString(joinPoint.getArgs()));
    }
}
```

#### 使得 @Aspect 注解生效

有两种方法使得 `@Aspect` 注解生效

1. 在配置文件中添加相关配置，使得这些切面类生效：

   ```xml
   <!-- 开启 @Aspect 配置 -->
   <aop:aspectj-autoproxy/>
   ```

2. 在配置类中开启

   ```java
   @Configuration
   @EnableAspectJAutoProxy // 开启 @Aspect 配置
   public class AppConfig {
   }
   ```

   

这两种方式都能够开启 `@Aspect` 注解的作用，Spring 会将这些 `@Aspect` 标记的类视为实现 AOP 的配置类（`Aspect` 是一个 Bean，这点使用时需要稍微注意）

将之前定义的切面类作为 Bean 放入到 Spring IOC 容器中，使得这些 `@Aspect` 标注的类能够正常被 Spring IOC 发现：

```xml
<!-- 这是上文中中定义的一个 Aspect 类 -->
<bean name="logArgsAspect" class="org.xhliu.aop.aspect.LogArgsAspect" >
</bean>

<!-- 这里是额外新定义的一个 Aspect 类，主要任务是在方法执行之后执行相关的行为 -->
<bean name="logResultAspect" class="org.xhliu.aop.aspect.LogResultAspect" >
</bean>
```



项目地址：https://github.com/LiuXianghai-coder/Spring-Study/tree/master/spring-aop



参考：

<sup>[1]</sup> https://javadoop.com/post/spring-aop-intro