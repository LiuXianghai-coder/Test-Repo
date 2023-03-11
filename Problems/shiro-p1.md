# Spring 整合 Shiro 的过程中出现的问题（一）

Spring 版本：由 Spring Boot 整合，版本为 2.6.2

Shiro 版本：1.8.0

<br />

## 问题描述

当通过自定义 `ShiroFilterChainDefinition` Bean来过滤相关的请求时，定义的配置类内容如下所示：

```java
package org.xhliu.demo.config;

import org.apache.shiro.spring.config.ShiroAnnotationProcessorConfiguration;
import org.apache.shiro.spring.config.ShiroBeanConfiguration;
import org.apache.shiro.spring.web.config.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    ShiroBeanConfiguration.class,
    ShiroAnnotationProcessorConfiguration.class,
    ShiroWebConfiguration.class,
    ShiroWebFilterConfiguration.class,
    ShiroRequestMappingConfig.class
})
public class ShiroWebConfig {
    @Bean
    public ShiroFilterChainDefinition shiroFilterChainDefinition() {
        DefaultShiroFilterChainDefinition chainDefinition = new DefaultShiroFilterChainDefinition();

        // 进入到 /admin 路径的请求必须具有 'admin' 的角色
        chainDefinition.addPathDefinition("/admin/**", "authc, roles[admin]");

        // 进入到 /docs 路径的用户必须具有读取权限
        chainDefinition.addPathDefinition("/docs/**", "authc, perms[document:read]");

        // 所有 /user 的请求都必须经过认证
        chainDefinition.addPathDefinition("/user", "authc");

        // 对于 /tmp 的请求可以是匿名的（即不需要经过认证）
        chainDefinition.addPathDefinition("/tmp", "anon");

        return chainDefinition;
    }
}

```



遇到如下的问题：

```text
Exception encountered during context initialization - cancelling refresh attempt: org.springframework.beans.factory.support.BeanDefinitionOverrideException: Invalid bean definition with name 'subjectDAO' defined in class path resource [org/apache/shiro/spring/web/config/ShiroWebConfiguration.class]: Cannot register bean definition [Root bean: class [null]; scope=; abstract=false; lazyInit=null; autowireMode=3; dependencyCheck=0; autowireCandidate=true; primary=false; factoryBeanName=org.apache.shiro.spring.web.config.ShiroWebConfiguration; factoryMethodName=subjectDAO; initMethodName=null; destroyMethodName=(inferred); defined in class path resource [org/apache/shiro/spring/web/config/ShiroWebConfiguration.class]] for bean 'subjectDAO': There is already [Root bean: class [null]; scope=; abstract=false; lazyInit=null; autowireMode=3; dependencyCheck=0; autowireCandidate=true; primary=false; factoryBeanName=org.apache.shiro.spring.config.ShiroConfiguration; factoryMethodName=subjectDAO; initMethodName=null; destroyMethodName=(inferred); defined in class path resource [org/apache/shiro/spring/config/ShiroConfiguration.class]] bound.

The bean 'subjectDAO', defined in class path resource [org/apache/shiro/spring/web/config/ShiroWebConfiguration.class], could not be registered. A bean with that name has already been defined in class path resource [org/apache/shiro/spring/config/ShiroConfiguration.class] and overriding is disabled.
```

<br />

## 问题分析

这是由于在 `ShiroWebConfiguration` 配置 Bean 中已经预先定义了 `ShiroFilterChainDefinition` 类型的 Bean，在定义了自定义的 `ShiroFilterChainDefinition` Bean 之后，此时 Spring 容器中已经存在了两个 `ShiroFilterChainDefinition` 类型的 Bean，因此在注入 `ShiroFilterChainDefinition` 类型的 Bean 时不知道注入哪一个具体实现类，从而抛出了一个异常

这个问题一般发生在 Spring 版本较高的情况下

<br />

## 解决方案

通过在 `application.peoperties` 或 `application.yml` 配置文件中将 `spring.main.allow-bean-definition-overriding` 设置为 `true`，即允许 Bean 覆盖，使得自定义的 `ShiroFilterChainDefinition` 覆盖预先定义的 Bean，从而使得自定义的 `ShiroFilterChainDefinition` Bean 生效

具体配置内容如下：

```yaml
spring:
  main:
    allow-bean-definition-overriding: true
```

