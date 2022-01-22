# Spring JPA 在多模块项目中的使用问题（一）

具体的项目结构如下图所示：
<img src="https://s2.loli.net/2022/01/22/rNPbjpzTKmOvQ3y.png" alt="2022-01-22 11-30-34 的屏幕截图.png" style="zoom:150%;" />	

其中，`score-application` 层为应用层，用于定义实际的操作；`score-domain` 表示领域层，用于处理业务之间的关联关系；`score-infrastructure` 为基础设施层，实际对于数据库的访问操作在这一层实现；`score-interfaces` 为接口层，客户端可以直接访问的接口

此时希望在 `score-infrastructure` 中通过 JPA 来完成数据的实际操作，但是在当前的项目环境下，`score-interface`  是实际的具体主类，所有的 Bean 都将在这一层完成注册，然后统一注入到不同的依赖 Bean 中。

在单个模块的项目中，只需要定义对应的实体类，然后定义一个 `Repository` 接口，该接口继承 `JpaRepository<T, ID>` ，然后通过谓词命名法定义相关的接口，Spring JPA 就会自动实现该接口，从而实现访问数据库的功能，具体如下所示：

```java
@Repository
public interface UserJpaRepo extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> getByUserAccount(String userAccount);
    Optional<UserEntity> getByUserId(Long userId);
}
```

这种方式在单模块的项目中没有问题，直接通过 Spring 依赖注入的方式注入该类型的 Bean 即可。但是如果当前的所处环境是多模块的话，出于安全的考虑（代码的侵入），在 `score-interfaces` 层中可能会无法找到这个类型的 Bean，这种情况下需要在 `score-interfaces` 中的 Spring 启动主类中加上 `@EnableJpaRepositories` 来允许另外的模块使用那个 JPA，具体使用如下所示：

```java
// 使得 com.amarsoft 包下的所有 JPA 接口都能被 Spring  JPA 代理生成
@EnableJpaRepositories(basePackages = {"com.amarsoft.*"})
// 扫描所有模块下的 com.amarsoft 包，使得这些包下的 Bean 都能被加载进来
@SpringBootApplication(scanBasePackages = {"com.amarsoft.*"}) 
public class ScoreInterfacesApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScoreInterfacesApplication.class, args);
    }
}
```

如果实体类在 JPA 接口所在的模块，那么应该是能够正常运行的，但是按照 “领域驱动设计” 的架构，实体类应当实在领域层定义的，由于模块之间的隔离性，此时将会出现找不到实体类的问题。解决这个问题的方式也很简单，在启动主类上加上`@EntityScan` 注解使得实体类能够被发现，最终启动类如下所示：

```java
// 由于某些操作可能涉及到事务，因此需要开启 JPA 的事务管理机制
@EnableTransactionManagement
// 使得 com.amarsoft 包及其子包下的实体类能够被 JPA 接口代理所发现
@EntityScan(basePackages = "com.amarsoft.*")
@EnableJpaRepositories(basePackages = {"com.amarsoft.*"})
@SpringBootApplication(scanBasePackages = {"com.amarsoft.*"})
public class ScoreInterfacesApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScoreInterfacesApplication.class, args);
    }
}
```

最终，在项目中即可正常使用 JPA