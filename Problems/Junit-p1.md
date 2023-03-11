# Spring Junit 使用时无法注入 Bean 的问题

在使用 Junit 4 时，使用如下的测试方式来注入 `DataSource` Bean ：

```java
import org.junit.Test;
import org.junit.Before;
import org.apache.shiro.realm.jdbc.JdbcRealm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

@SpringBootTest
public class ShiroTest {
    @Autowired
    DataSource dataSource;
    
    JdbcRealm jdbcRealm = new JdbcRealm();
    
    // 在执行测试之前执行的相关行为
    @Before
    public void addDataSource() {
        jdbcRealm.setDataSource(dataSource);
    }
    
    @Test
    public void test() {
        System.out.println("dataSource={}", dataSource);
    }
}
```

此时注入到 `ShiroTest` 中的 `DataSource` Bean 为 null，这是由于 Junit 默认情况下不会加载 `Spring` 上下文环境，因此此时无法找到在 Spring 中存在的 `DataSource` Bean

<br />

为了解决这个问题，主要有以下两种解决方案：

- 使得 Junit 加载 Spring 上下文，只需加入 `@RunWith` 来加载 Spring 上下文即可，具体代码如下所示：

    ```java
    import org.junit.Test;
    import org.junit.Before;
    import org.junit.runner.RunWith;
    import org.apache.shiro.realm.jdbc.JdbcRealm;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.boot.test.context.SpringBootTest;
    import org.springframework.test.context.junit4.SpringRunner;
    
    import javax.sql.DataSource;
    
    @SpringBootTest
    @RunWith(SpringRunner.class)
    public class ShiroTest {
        @Autowired
        DataSource dataSource;
        
        JdbcRealm jdbcRealm = new JdbcRealm();
        
        @Before
        public void addDataSource() {
            jdbcRealm.setDataSource(dataSource);
        }
        
        @Test
        public void test() {
            System.out.println("dataSource={}", dataSource);
        }
    }
    ```

    通过加入 `SpringRunner`，即可使得 Junit 自动完成 Spring 上下文的加载

- 将 Junit 4 升级到 Junit 5。Junit 5 是对 Junit 4 的一个重大改进，在 Junit 5 中已经将 Spring 上下文的加载整合了，因此不需要显式地添加 `@RunWith` 来加载 Spring 上下文。

    需要注意的是，Junit 5 的 API 相对于 Junit 4 来讲有许多的变动，具体可以参考对应的文档，上文的例子使用 Junit 5 来测试如下所示：

    ```java
    import org.junit.jupiter.api.BeforeEach;
    import org.junit.jupiter.api.Test;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.boot.test.context.SpringBootTest;
    import org.apache.shiro.realm.jdbc.JdbcRealm;
    
    import javax.sql.DataSource;
    
    @SpringBootTest
    public class ShiroTest {
        @Autowired
        DataSource dataSource;
        
        JdbcRealm jdbcRealm = new JdbcRealm();
        
        @BeforeEach
        public void addDataSource() {
            jdbcRealm.setDataSource(dataSource);
        }
        
        @Test
        public void test() {
            System.out.println("dataSource={}", dataSource);
        }
    }
    ```



<br />

现在，在 `ShiroTest` 执行 `test()` 测试，会发现是存在对应的 `DataSource` Bean 的。