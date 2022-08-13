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

从 Spring Boot 自动装载 Bean 的流程中可以发现，对于 