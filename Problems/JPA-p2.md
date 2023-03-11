# Spring JPA  could not initialize proxy - no Session

JPA 通过谓词分析的方式来快速地实现数据库访问相关的操作，优点是提高了开发速度，但是如果遇到问题，排查起来会很麻烦

<br />

本次遇到的问题如下图所示：

```text
Exception in thread "main" org.hibernate.LazyInitializationException: could not initialize proxy [org.xhliu.demo.entity.CustomerInfo#1] - no Session
	at org.hibernate.proxy.AbstractLazyInitializer.initialize(AbstractLazyInitializer.java:176)
	at org.hibernate.proxy.AbstractLazyInitializer.getImplementation(AbstractLazyInitializer.java:322)
	at org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor.intercept(ByteBuddyInterceptor.java:45)
	at org.hibernate.proxy.ProxyConfiguration$InterceptorDispatcher.intercept(ProxyConfiguration.java:95)
	at org.xhliu.demo.entity.CustomerInfo$HibernateProxy$zwCU7ed1.toString(Unknown Source)
	at org.xhliu.demo.Application.main(Application.java:29)
```

这种异常一般发生在使用 JPA 执行一对多的连接查询的情况，这是由于在执行查询时，本次一对多的实体类字段属性可能未完成初始化，如 `List`、`Collection` 等，而此时 JPA 的持久化上下文已经被关闭了，因此抛出了 “no session” 这样的异常

<br />

解决方案：

1. 添加全局 JPA 配置 `enable_lazy_load_no_trans`

    <b>本解决方案极度不推荐</b>

    在全局配置文件中添加如下的配置项：

    ```yaml
    spring:
      jpa:
        properties:
          hibernate:
            enable_lazy_load_no_trans: true
    ```

    如果配置了这个属性，那么 JPA 就会为每一个一对多的关联字段分配一个单独的临时 Session，使得这个字段能够通过这个临时会话拿到对应的属性。

    这是一种 ”<a href="https://en.wikipedia.org/wiki/Anti-pattern">反模式</a>“，因为如果这么配置了，当一对多的字段属性变多时，为了维护这些临时 Session 将会给底层的连接池带来很大的压力，严重的话将会明显地影响到性能，如果有别的解决方案尽量不要使用该方案

2. 定义数据拉取策略

    JPA 定义了如下的获取列数据的策略：

    ```java
    public enum FetchType {
        // 该策略表示数据可以被延迟获取
        LAZY,
    
        // 该策略表示必须被尽早地获取
        EAGER
    }
    ```

    对于一对多的集合集合属性字段，使用 `EAGER` 来确保能够即使获取数据，从而解决这个问题，具体使用如下所示：

    ```java
    @Entity
    @Table(name = "user_info")
    public class UserInfo {
         @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "user_id", nullable = false)
        private long userId;
        
        @Basic(fetch = FetchType.LAZY)
        @Column(name = "user_phone")
        private String userPhone;
        
        @OneToMany(fetch = FetchType.EAGER)
        private Set<Feature> holdingFeatures = new HashSet<>();
    
        @OneToMany(fetch = FetchType.EAGER)
        private Set<Book> bookSet = new HashSet<>();
    }
    ```

3. `@NamedEntityGrap` 和 `@EntityGraph`

    具体使用如下所示：

    对于实体 `Item`：

    ```java
    @Entity
    @NamedEntityGraph(
        name = "Item.characteristics",
        attributeNodes = @NamedAttributeNode("characteristics")
    )
    public class Item {
        @Id
        private Long id;
        private String name;
    
        @OneToMany(mappedBy = "item")
        private List<Characteristic> characteristics = new ArrayList<>();
    }
    ```

    对于实体 `Characteristic`：

    ```java
    @Entity
    public class Characteristic {
        @Id
        private Long id;
        private String type;
    
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn
        private Item item;
    }
    ```

    对于 JPA 接口 `ItemRepository`：

    ```java
    public interface ItemRepository extends JpaRepository<Item, Long> {
        @EntityGraph(value = "Item.characteristics")
        Item findByName(String name);
    }
    ```

    对于 JPA 接口 `CharacteristicsRepository`：

    ```java
    public interface CharacteristicsRepository extends JpaRepository<Characteristic, Long> {
        @EntityGraph(attributePaths = {"item"})
        Characteristic findByType(String type);    
    }
    ```

    这样也可以解决由于存在的级联关系导致的  “no session” 的问题

<br />

参考：

<sup>[1]</sup> https://stackoverflow.com/questions/36583185/spring-data-jpa-could-not-initialize-proxy-no-session-with-methods-marke

<sup>[2]</sup> https://www.baeldung.com/spring-data-jpa-named-entity-graphs