# Spring 测试

在一般的应用开发过程中，合理的开发工作应当包含对相关功能的单元测试的编写，如何快速编写一个有效的单元测试有时也是一个值得考虑的问题。

## Junit 测试

### 测试环境准备

在 Spring 项目中，可以使用 `Junit` 创建一个 `test` 的应用上下文，并执行对应的单元测试

比如，如果存在如下的服务类 `UserInfoService`：

``` java
import com.example.demo.entity.UserInfo;
import com.example.demo.mapper.UserInfoMapper;
import org.springframework.stereotype.Service;

/**
 *@author lxh
 */
@Service
public class UserInfoService {

    // 用于查询用户信息的 DAO 对象
    @Resource
    private UserInfoMapper userInfoMapper;

    public UserInfo findUserInfo(Long userId) {
        return userInfoMapper.selectByUserId(String.valueOf(userId));
    }
}
```

现在需要对这个 `UserInfoService` 编写相关的单元测试，由于这里的 `UserInfoMapper` 需要访问数据库，因此我们不得不创建一个用于测试的 profile 文件配置测试用例所在的数据库：

```yaml
server:
  port: 0

spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/db_test?allowMultiQueries=true
    username: root
    password: 123456

mybatis:
  configuration:
    cache-enabled: false
    local-cache-scope: statement
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  mapperLocations: mybatis/mapper/*
```

对于一些在开发环境中自定义配置的 `DataSource`，可以通过添加 `@Profile` 注解使得它们不在测试的上下文中生效，否则配置的测试数据库配置上下文可能会不生效：

``` java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author lxh
 */
@Configuration
public class DataSourceConfig {
    
    @Bean(name = "dynamicDataSource")
    @Profile({"default", "prod", "dev"}) // 使得当前的动态数据源只在默认的配置、生产以及开发环境中生效
    public DataSource dynamicDataSource() {
        Map<Object, Object> dataSourceMap = new HashMap<>();
        DataSource mysqlDataSource = mysqlDataSource();
        dataSourceMap.put("mysql", mysqlDataSource); 
        dataSourceMap.put("postgresql", postgresqlDataSource());
        DynamicDataSource dataSource = new DynamicDataSource();
        dataSource.setTargetDataSources(dataSourceMap);
        dataSource.setDefaultTargetDataSource(mysqlDataSource);
        return dataSource;
    }
}
```

而对于测试上下文来说，我们可以重新定义对应的数据库连接信息，使得它只在测试环境中生效：

``` java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author lxh
 */
@Configuration
public class TestDataSourceConfig {
    
    @Bean("dynamicDataSource")
    @Profile("test")
    public DynamicDataSource dynamicDataSource(DataSource dataSource) {
        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("mysql", dataSource);
        // 。。。。。。。 可以将切换的上下文都指向当前 DataSource，或者再自定义测试环境的数据库连接
        DynamicDataSource dynamicDataSource = new TestDynamicDataSource();
        dynamicDataSource.setTargetDataSources(dataSourceMap);
        return dynamicDataSource;
    }
}
```

此外，为了避免测试类影响到其它环境的有关数据，可以考虑使用 `H2` 作为临时的内存数据库，并且在每次执行单元测试时都重新构建数据表以及初始化数据：

``` yaml
spring:
  sql:
    init:
      schema-locations: db_script/schema_h2.sql
      data-locations: db_script/data_h2.sql
      mode: always
  datasource:
    url: jdbc:h2:/data/test
    driver-class-name: org.h2.Driver
    username: sa
    password:
```

此外，为了避免一些多余的自动配置类的加载（或者在当前模块中缺少上层模块的组件），我们可以创建一个针对测试类的 Spring 应用上下文，从而将上下文与实际应用的上下文进行区分：

``` java
import com.example.demo.common.DynamicDataSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 *@author lxh
 */
// 移除不需要的 SpringApplicationAdminJmxAutoConfiguration 自动配置类
@SpringBootApplication(exclude = {SpringApplicationAdminJmxAutoConfiguration.class})
@MapperScan(value = {"com.example.demo.mapper"}, lazyInitialization = "true")
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
```

### 编写单元测试

对于上文提到的 `UserInfoService`，可以创建如下的单元测试类进行测试：

``` java
import com.example.demo.TestApplication;
import com.example.demo.entity.UserInfo;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

/**
 *@author lxh
 */
@RunWith(SpringRunner.class) // 使用 SpringRunner 管理当前测试类周期
@ContextConfiguration(classes = TestApplication.class) // 当前 Spring 应用所处的上下文
public class UserInfoServiceTest {

    @Resource
    private UserInfoService userInfoService;

    @Test
    public void findUserInfoTest() {
        UserInfo userInfo = userInfoService.findUserInfo(2L);
        Assert.assertEquals("xhliu2", userInfo.getName()); // 如果在对应的测试数据库中存在这样的数据，则测试通过
    }
}
```

## Mockito 测试

### 基本使用

`Junit` 是一个很强大的测试框架，对于其它的测试工具的集成也比较好。在上文中，我们举例了一个查询底层数据库的单元测试，由于这里的数据库连接获取比较容易，因此我们可以实际地访问数据库来获取数据进行测试。然而，在一些场景（如需要 RPC 调用或者数据在不同分区）下，直接创建相关的底层测试框架是很麻烦的，并且这么做的意义也不大，有时可能仅仅只是希望验证当前的功能是否能够正常执行，而并不关心实际的数据是否正确。一种可行的方法是为这些注入数据访问接口的依赖定义一个切面，使得这些数据访问的接口返回一个看起来比较合理的数据，并使得该切面只在测试环境下工作。幸运的是，已经存在这样的现成工具供我们使用了，它就是 `Mockito`

假设我们现在有一个这样的需求场景：用户创建了一个订单，在订单创建时需要同步更新商品的库存信息，由于订单服务和库存服务都是单独的服务接口，并且两者的数据处于不同的分区，因此需要通过 “两阶段提交” 的方式来完成这一操作。为了方便，我们将库存服务的处理设置为 MQ 形式的调用

现在有如下的订单服务类：

``` java
import com.example.springtest.entity.OrderInfo;
import com.example.springtest.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigInteger;
import java.util.UUID;

/**
 *@author lxh
 */
@Service
public class OrderService {

    private final static Logger log = LoggerFactory.getLogger(OrderService.class);

    @Resource
    private OrderMapper orderMapper;

    private GoodsService goodsService;

    @Transactional
    public boolean createOrder(OrderInfo orderInfo) {
        log.info("准备创建订单，对应的订单信息=[{}]", orderInfo);
        int orderId = new BigInteger(UUID.randomUUID().toString()
                .replaceAll("-", "").getBytes())
                .mod(BigInteger.valueOf(Integer.MAX_VALUE - 1))
                .intValue();
        orderInfo.setId(orderId);
        orderInfo.setCreatedTime(new Date());
        orderMapper.insertOrderInfo(orderInfo);

        boolean updated = goodsService.updateGoodsStoreInfo(
                orderInfo.getGoodsId(), orderInfo.getGoodsCnt());// 更新库存信息
        if (!updated) {
            throw new RuntimeException("库存信息更新失败");
        }
        return Boolean.TRUE;
    }
    
    /*
    	注意这里需要使用 setter 的形式进行依赖注入，在某些 Mockito 中基于反射的形式注入
    	可能会在 Spring 代理的情况下无法正常注入
    */
    @Resource
    public void setGoodsService(GoodsService goodsService) {
        this.goodsService = goodsService;
    }
}
```

在这个例子中，我们需要根据商品的库存更新状态来判断是否能够提交订单的创建事务，由于 `GoodsService` 是一个 MQ 调用，因此在测试的过程中需要确保对于 MQ 的调用的成功与失败情况执行对应的单元测试。如果重新配置 MQ ，那么不仅会十分繁琐，而且也达不到相应的测试效果。

为此我们可以使用 `Mocktio` 为 `GoodsService` 的方法调用创建一个代理，使其不用使用真实的向 MQ 发送消息。对应的测试类如下：

``` java
import com.example.springtest.SpringTestApplication;
import com.example.springtest.entity.OrderInfo;
import com.example.springtest.mapper.OrderMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

import javax.annotation.Resource;

/*
    所有对于数据库操作在测试类中都不生效，使得能够多次执行测试用例
 */
@Rollback
/*
    在高版本的 Spring Test 中，已经集成了 Junit，因此可以跳过 @RunWith 和 @ContextConfiguration 的配置
 */
@SpringBootTest(classes = SpringTestApplication.class)
class OrderServiceTest {

    /*
        @Mock 注解表示将创建一个 GoodsService 的方法代理对象，对于具体的方法调用
        的返回结果可以参考 Mockito#when
     */
    @Mock
    GoodsService goodsService;

    @Resource
    /*
        表示这是一个需要注入 Mock 的对象，在 Mockito 初始化时会扫描
        这些注解并完成 @Mock 依赖的注入处理
     */
    @InjectMocks
    OrderService orderService;

    @Resource
    OrderMapper orderMapper;

    @BeforeEach
    public void initMock() {
        try {
            /*
                初始化 Mock 的测试环境，完成 Mock 相关的实例构造以及依赖注入
             */
            MockitoAnnotations.openMocks(this).close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void createOrderSuccess() {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setGoodsId(2);
        orderInfo.setGoodsCnt(10);

        /*
            Mockito.when 会代理当前的方法调用，并返回当前假定的返回结果，
            当然也可以抛出异常等其它操作。这里我们假设每次对于库存服务的处理都是成功的，
            那么我们的订单应当是每次调用都会成功
         */
        Mockito.when(goodsService.updateGoodsStoreInfo(2, 10))
                .thenReturn(Boolean.TRUE);

        Assertions.assertTrue(orderService.createOrder(orderInfo));
        // 订单创建成功，数据库中应当存在订单信息
        Assertions.assertNotNull(orderMapper.queryById(orderInfo.getId()));
    }

    @Test
    void createOrderFailed() {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setGoodsId(2);
        orderInfo.setGoodsCnt(10);

        // 这里我们假设当前的库存更新是失败的，因此订单的创建应当也是失败的
        Mockito.when(goodsService.updateGoodsStoreInfo(2, 10))
                .thenReturn(Boolean.FALSE);

        Assertions.assertThrows(RuntimeException.class,
                () -> orderService.createOrder(orderInfo), "库存信息更新失败"
        );
        // 订单创建失败，数据库中应当不存在订单信息
        Assertions.assertNull(orderMapper.queryById(orderInfo.getId()));
    }

    @Test
    void goodsUpdateException() {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setGoodsId(2);
        orderInfo.setGoodsCnt(10);

        // 这里我们假设当前的库存更新是失败的，因此订单的创建应当也是失败的
        Mockito.when(goodsService.updateGoodsStoreInfo(2, 10))
                        .thenThrow(NullPointerException.class);

        // 由于消息发送过程中抛出了异常，那么在当前创建订单的过程中应当也能捕获
        Assertions.assertThrows(NullPointerException.class,
                () -> orderService.createOrder(orderInfo)
        );
        // 订单创建失败，数据库中应当不存在订单信息
        Assertions.assertNull(orderMapper.queryById(orderInfo.getId()));
    }
}
```

在这个例子中，通过为 `GoodsService` 的相关方法的指定参数的调用设置了固定的返回结果，模拟了在不同场景下 `OrderService`创建订单时应当出现的结果，以此简化了测试用例的编写

### 其它API

一般情况下，使用 Mockito 固定方法调用的输出结果已经够用了，然而，处于严谨的角度考虑，我们必须确定我们预订的方法调用确实已经被调用了（有时预订的方法未被调用却有正确的处理结果），Mocktio 提供了一些额外的 API 来辅助测试

- `Mockito#verify(T mock, VerificationMode mode)`

  该方法的第一个参数 `mock` 为预订的 Mock Bean，第二个参数可以参考 `org.mockito.verification.VerificationMode` 的具体实现，主要是为了验证 Mock Bean 的方法是否按照预期的被调用了

  `Mockito.verify` 提供了对于 Mock Bean 的方法调用的校验，比如，如果希望检查预订 `GoodsService`  的方法确实被调用了，那么可以这么做：

  ``` java
  // 验证预订的 GoodsService 方法调用至少被调用一次
  Mockito.verify(goodsService, Mockito.atLeast(1))
      .updateGoodsStoreInfo(orderInfo.getGoodsId(), orderInfo.getGoodsCnt());
  ```

  对于一些异步调用，比如订单创建完成后需要发送通知给用户，那么可以使用 `org.mockito.Mockito#timeout` 的验证模式来检查在一定时间间隔的方法调用：

  ``` java
  // 测试在 100 ms 内会调用 goodsService 的 sendNotify 方法
  Mockito.verify(goodsService, Mockito.timeout(100))
      .sendNotify(orderInfo.getUserId());
  ```

- `Mockito#spy(T)`

  与 Mockito 初始化时使用的 `Mock#mock(Class<T> classToMock)` 创建的 Mock 对象不同，`spy` 方法会代理已经存在的实例对象为一个新的 Mock 对象，对于非预订的方法调用则会返回实例对象原有的调用结果。

  如果希望使用已经存在的实例对象（如 `Mapper` 对象），但是又希望针对特定的方法和参数返回预订的结果，可以考虑使用 `spy` 来代替 `mock` 创建新的 Mock 对象

<hr />

项目地址：https://github.com/LiuXianghai-coder/Spring-Study/tree/master/spring-test

参考：

<sup>[1]</sup> https://www.tutorialspoint.com/mockito/index.htm