

## MyBatis 简介

MyBatis 是一款优秀的持久层框架，它支持自定义 SQL、存储过程以及高级映射。MyBatis 免除了几乎所有的 JDBC 代码以及设置参数和获取结果集的工作。MyBatis 可以通过简单的 XML 或注解来配置和映射原始类型、接口和 Java POJO（Plain Old Java Objects，普通老式 Java 对象）为数据库中的记录。<sup>[1]</sup>



## 基本配置

### Mybatis 配置

首先需要先配置 `mybatis` 的配置文件，配置的内容包括 `mybatis` 的基本设置、运行环境和要加载的 `Mapper XML` 映射文件等配置

如下所示：

```xml
<?xml version="1.0" encoding="utf-8" ?>
<!DOCTYPE configuration
        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-config.dtd" >
<configuration>
    <!-- 加载对应的配置文件，将相关的数据库配置信息放入这里面会更好 -->
    <properties resource="mybatis/jdbc.properties" />

    <!-- MyBatis 相关的一些设置 -->
    <settings>
        <!-- 将日志输出到标准输出 -->
        <setting name="logImpl" value="STDOUT_LOGGING"/>
        <!-- 设置字段与列之间的映射关系，可选值为 NONE, PARTIAL, FULL -->
        <setting name="autoMappingBehavior" value="PARTIAL" />
        <!-- 将下划线自动映射为陀峰 -->
        <setting name="mapUnderscoreToCamelCase" value="true"/>
    </settings>

    <!--<typeAliases>
        <package name="vo"/>
    </typeAliases>-->

    <!-- 不同开发环境对应的配置，默认为 dev 的配置 -->
    <environments default="dev">

        <environment id="dev">
            <transactionManager type="JDBC" /> <!-- 使用 JDBC 作为事务管理 -->
            <!-- 数据源的一些配置 -->
            <dataSource type="POOLED" >
                <property name="driver" value="${driver}" />
                <property name="url" value="${url}" />
                <property name="username" value="${username}" />
                <property name="password" value="${password}" />
            </dataSource>
        </environment>

    </environments>

    <databaseIdProvider type="DB_VENDOR" />

    <!-- 要加载的 Mapper XML 映射文件 -->
    <mappers>
        <mapper resource="mybatis/mapper/UserMapper.xml" />
        <mapper resource="mybatis/mapper/MessageMapper.xml" />
    </mappers>
</configuration>
```

完成 `Myabtis` 的这些设置之后，才能正常使用 `Mybatis`



### SqlSession

从 `Myabtis` 配置文件中读取对应的配置来创建对应的 `SqlSession`。`SqlSession` 的创建是由 `SqlSessionFactory` 工厂对象来创建的，由于在整个系统中只需要一个 `SqlSessionFactory`，因此可以使用 “静态内部类” 的方式在系统中加载唯一的 `SqlSessionFactory` 实例。

具体创建如下所示：

```java
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;

public class SqlSessionFactoryUtil {
    /*
    	使用静态内部类的方式创建单例的 SqlSessionFactory
    */
    private static class Holder {
        private final static SqlSessionFactory sqlSessionFactory;

        static {
            InputStream in = null;
            try {
                /*
                	参考 Maven 项目结构，Resources 会从 resource 目录下加载对应的资源
                	通过 getResourceAsStream() 方法读取对应的配置文件，将它读入到输入流中
                */
                in = Resources.getResourceAsStream("mybatis-config.xml");
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            // 使用构建者模式从输入流中创建一个新的 SqlSessionFactory 对象
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(in);
        }

        // 现在，通过该方法获取的 SqlSessionFactory 对象就是单例的了
        public static SqlSessionFactory getSqlSessionFactory() {
            return sqlSessionFactory;
        }
    }
    
    // 通过创建的 SqlSessionFactory 对象创建一个 SqlSession
    public static SqlSession openSqlSession() {
        return Holder.getSqlSessionFactory().openSession();
    }
}
```



### DAO 接口

定义一个数据库访问对象应该有的接口，只需要定义需要的接口操作即可，`Mybatis` 会自动生成相关的数据库访问对象。

一个简单的 DAO 接口如下：

```java
import org.apache.ibatis.annotations.Param;

// 只需要定义方法，别的交给 Mybatis
public interface UserMapper {
    User getUserById(@Param("id") Long userId);
}
```



### Mapper XML 映射文件

对于任何数据库访问框架来讲，每个框架首先要解决的就是 `ORM`，即对象关系映射，通俗地讲就是处理 Java 对象的属性和数据库中表的列的对应关系。相比较于其它的一些数据库访问框架，如 `JPA`、`JDBCTemplate` 等，`Mybatis` 的配置可能要更加复杂一些。

映射相关的一些配置都放在 `***Mapper.xml` 中，具体的文件名可以自己任意命名，但是一般会保留 `Mapper` 后缀。在创建完成 `***Mapper.xml` 文件后，记得将这个 `XML` 文件添加到 `mybatis-config.xml`（`Mybatis` 配置文件）中的 `<mappers></mappers>`中，因为只有这样 `Mybatis` 才能加载到这个映射文件。

一个 `***Mapper.xml` 文件如下所示：

```xml
<?xml version="1.0" encoding="utf-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd" >
<mapper namespace="mybatis.mapper.UserMapper"><!-- namespace 对应相关的 DAO 接口，Mybatis 会根据这个接口通过代理的方式生成对应的数据访问对象 -->
    <!-- 定义一些列名集合，之后可以通过引用的方式直接添加到对应的位置 -->
    <sql id="allColumn">
        id, name, age
    </sql>

    <!-- 一个简单的查询，这里没有配置 ORM 关系，因此会直接将列名映射为对象的属性名 -->
    <select id="getUserById" parameterType="long" resultType="mybatis.vo.User">
        SELECT 
            <include refid="allColumn" /> <!-- 引入之前的列名集合 -->
        FROM
            tb_user
        WHERE
            id=#{id}
    </select>
</mapper>
```



## 基本使用

为了能够更好地使用 `Mybatis`，在使用之前需要完成上文没有完成的 `ORM` 的映射配置。这里新建一个 `Message` 来演示对应的配置，此时的 `Mapper XML` 配置文件如下所示：

```xml
<?xml version="1.0" encoding="utf-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd" >
<mapper namespace="mybatis.mapper.MessageMapper">
    <!-- 要查询的数据列名集合 -->
    <sql id="allColumns">
        id, msg_id, status, content, deleted, create_time, update_time
    </sql>
    
    <!-- 要插入的列名的集合 -->
    <sql id="insertAllColumns">
        msg_id, status, content, deleted, create_time
    </sql>

    <!-- 定义 ORM 映射关系 property 为对象的属性，column 为对应的数据表的列名 -->
    <resultMap id="messageVoResultMap" type="mybatis.vo.Message">
        <id column="id" property="id"/>
        <result column="msg_id" property="msgId"/>
        <result column="status" property="status"/>
        <result column="content" property="content"/>
        <result column="deleted" property="deleted"/>
        <result column="create_time" property="createTime"/>
        <result column="update_time" property="updateTime"/>
    </resultMap>
</mapper>
```



对应的 `Message` 类如下所示：

```java
public class Message {
    private Long id;
    private String msgId;
    private Integer status; // 消息状态，-1-待发送，0-发送中，1-发送失败 2-已发送
    private String content; // 消息内容
    private Integer deleted;
    private Date createTime;
    private Date updateTime;
    
    // 省略一部分 setter 和 getter 方法。。。。。
}
```



### 查询数据

查询数据是比较简单的操作，有时需要通过传入参数对相关的列进行查找。主要存在以下三种方式传入参数进行查找：

- 使用 `@Param` 注解

  通过在 DAO 接口中对参数加上 `@Param` 注解，即可表示这个参数是一个查询参数，在查询时会将这个查询参数放入 SQL 的查询中（如果会使用到的话）。

  方法如下所示：

  ```java
  Message getMessageById(@Param("id") Long id); // 此时传入的 id 就是一个查询参数, @Param("id") 中的 id 表示会对应上 Mapper XML 文件中的参数 id
  ```

  此时 Mapper XML 配置文件也会处理这个参数，具体的配置如下所示：

  ```xml
  <!-- parameterType 注意与传入的参数相匹配 -->
  <select id="getMessageById" parameterType="Long" resultMap="messageVoResultMap"> <!-- 注意这里的 messageVoResultMap 是上文定义的 ORAM 关系 -->
      SELECT
      	<include refid="allColumns"/>
      FROM
      	tb_message
      WHERE
      	id=#{id} <!-- 这里的 #{id} 就是传入的查询参数 -->
  </select>
  ```

  

- 使用 `java.util.Map` 对象

  使用 `@Param` 的一个有点就是简单，快速。但是缺点也很明显，如果想要传入多个参数，那么就会很麻烦。`Mybatis`提供了使用 `java.util.Map` 来存储参数的方式来传入多个查询参数。

  具体的 DAO 接口如下所示：

  ```java
  Message getMessageByMap(Map<String, Object> map); // 使用 Object 作为 V 可能不是一个很好的想法
  ```

  对应的 Mapper XML 的配置如下所示：

  ```xml
  <!-- 通过 java.util.Map 对象来传入对应的查询参数 -->
  <select id="getMessageByMap" parameterType="java.util.Map" resultMap="messageVoResultMap">
      SELECT
      	<include refid="allColumns"/>
      FROM
      	tb_message
      WHERE
      	id=#{id} AND msg_id=#{msg_id} <!-- 这里的 id 和 msg_id 都是 map 中的 key -->
  </select>
  ```

  

- `使用 Java 实体对象`

  使用 `java.utl.Map` 的方式传递多个参数有时并不是一个很好的主意，因为有时可能会写错 `key`，导致找不到对应的查询参数。为此，可以考虑使用实体对象的方式来传递相关的查询参数。

  对应的 DAO 接口的方法如下所示：

  ```java
  Message getMessageByEntity(Message message); // 使用 Message 实体对象存储查询参数
  ```

  对应的 Mapper XML 配置文件如下所示：

  ```xml
  <!-- mybatis.vo.Message 为自定义的实体对象的全路径名 -->
  <select id="getMessageByEntity" parameterType="mybatis.vo.Message" resultMap="messageVoResultMap">
      SELECT
      	<include refid="allColumns"/>
      FROM
      	tb_message
      WHERE
      	id=#{id} AND msg_id=#{msgId} <!-- 这里的 id 和 msgId 是实体的属性名 -->
  </select>
  ```

  为了能够更加清晰地传入参数，在构造参数实体对象时推荐使用 “构建者” 模式构建对象来设置对应的参数值。

  使用实体存储查询参数也不是一个十分好的方式，缺点在于可存储的查询参数依旧是有限的，但是相比较于使用 `@Param` 的方式传递参数，这种方式还是要好很多。

  

具体的查询示例如下所示：

```java
// 获取 SqlSession 对象
try (SqlSession sqlSession = SqlSessionFactoryUtil.openSqlSession()) {
    // 传入对应的 DAO 接口，得到 MyBatis 生成的数据库访问实体对象
    MessageMapper mapper = sqlSession.getMapper(MessageMapper.class);

    // 使用 @Param 注解的方式传入对应的查询参数
    Message messageByParam = mapper.getMessageById(1L);
    log.info(messageByParam.toString());

    // 使用 java.util.Map 的方式传入对应的查询参数
    Map<String, Object> map = new HashMap<>();
    map.put("id", 2L);
    map.put("msg_id", "msg_1");
    Message messageByMap = mapper.getMessageByMap(map);
    log.info(messageByMap.toString());

    // 使用 “构建者” 模式创建对应的参数实体对象
    Message params = MessageVo.builder()
        .id(1L)
        .msgId("msg_1")
        .build();
    // 通过参数实体对象进行对应的数据查询
    Message messageByEntity = mapper.getMessageByEntity(params);
    log.info(messageByEntity.toString());
} catch (Exception e) {
    log.info("Get Exception: " + e.getLocalizedMessage());
}
```



### 增加数据

与查询类似，每次都需要传入对应数据进行插入，首先在 Mapper XML 文件中定义要插入的数据的列

```xml
<sql id="insertAllColumns">
    msg_id, status, content, deleted, create_time
</sql>
```

再定义插入逻辑

```xml
<!-- 这里使用的传入参数的方式是通过参数实体对象来传递的， 注意定义 keyProperty，这个是主键 -->
<insert id="insert" parameterType="mybatis.vo.Message" keyProperty="id">
        INSERT INTO 
    		tb_message(<include refid="insertAllColumns" />) 
    	VALUES (#{msgId}, #{status}, #{content}, #{deleted}, #{createTime})
</insert>
```

DAO 中定义的添加数据的方法：

```java
int insert(Message message);
```

按照一般的操作执行插入方法，如下所示：

```java
Message message = MessageVo.builder()
    .msgId("msg_3")
    .deleted(0)
    .status(1)
    .content("Three Content")
    .createTime(new Date())
    .build();
int rows = mapper.insert(message);
log.info("insert rows={}", rows);
```

直接这么执行插入方法，最终会发现没有将数据插入到数据库中，这是因为修改数据的操作会触发一个事务，而这个事务是没有被提交的。

解决方案如下：

- 在执行完成之后手动提交事务

  ```java
  // 上文的插入数据的执行内容
  sqlSession.commit(); // 手动提交事务
  ```

  

- 设置 `SqlSession` 为自动提交事务

  在 `SqlSessionFactory`  调用 `openSession()`时，指定打开的 `SqlSession` 是自动提交事务的，加入对应的参数即可：

  ```java
  // 省略初始化 SqlSessionFactory 的内容
  public static SqlSession openSqlSession() {
      return Holder.getSqlSessionFactory().openSession(true); // 开启自动提交事务
  }
  ```

提交事务之后，就可以实现数据的真实插入了。



### 修改数据

修改数据也是一个事务，只要注意事务的提交即可。

Mapper XML 的配置如下所示：

```xml
<update id="update" parameterType="mybatis.vo.Message">
    UPDATE tb_message SET content=#{content} WHERE id=#{id}
</update>
```

对应的 DAO 接口如下所示：

```java
int update(Message message); // 更新 Message 对应的数据
```

对应的更新代码如下所示：

```java
Message messageByParam = mapper.getMessageById(2L); // 首先需要查到对应的 Message
messageByParam.setContent("Update Content"); // 更新这个对象的属性
int update = mapper.update(messageByParam); // 再写回数据库，注意这里已经在打开 SqlSession 时默认开启了事务提交，因此这里不需要再手动提交事务
log.info("update rows={}", update);
```



### 删除数据

删除数据与更新数据一致，对应的 Mapper XML 的如下：

```xml
<delete id="delete" parameterType="mybatis.vo.Message">
    DELETE FROM tb_message WHERE id=#{id} <!-- 按照 id 来删除数据 -->
</delete>
```

对应的 DAO 接口的方法如下所示：

```java
int delete(Message message);
```

删除数据的代码如下所示：

```java
Message message = MessageVo.builder().id(4L).build(); // 构建者模式传入对应的参数
int deleteNum = mapper.delete(message);
log.info("delete rows={}", deleteNum);
```



## 级联关系

### 一对一

一个表中的实体对应另一个表中的另一个唯一的实体，如 `Message` 和 `MessageDetail` 所示：

`MessageDetail`

```java
public class MessageDetail {
    private Long id;
    private String msgId;
    private String detailContent;
    private Date createTime;
    private Date updateTime;
}
```

设置 `MessageDetail` 的 Mapper XML 映射文件

```xml
<?xml version="1.0" encoding="utf-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd" >
<mapper namespace="mybatis.mapper.MessageDetailMapper">
    <sql id="allColumns">
        id, msg_id, detail_content, create_time, update_time
    </sql>

    <!-- 由于是一对一的关系，因此只需要将在查询 Message 时指定对应的主键来查询对应的对象即可 -->
    <select id="getMessageByMsgId" parameterType="string" resultType="mybatis.vo.MessageDetail">
        SELECT
            <include refid="allColumns"/>
        FROM
            tb_message_detail
        WHERE
            msg_id = #{msgId}
    </select>
</mapper>
```

`Message`：

```java
public class Message {

    private Long id;
    private String msgId;
    private Integer status; // 消息状态，-1-待发送，0-发送中，1-发送失败 2-已发送
    private String content; // 消息内容
    private Integer deleted;
    private Date createTime;
    private Date updateTime;
    private MessageDetail messageDetail;
    
    // 省略一部分 setter 和 getter 方法
}
```

在 `MessageMapper` 对应的 XML 映射文件中添加对应的级联关系：

```xml
<?xml version="1.0" encoding="utf-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd" >
<mapper namespace="mybatis.mapper.MessageMapper">
    <sql id="allColumns">
        id, msg_id, status, content, deleted, create_time, update_time
    </sql>
    
    <!-- 省略一部分映射配置 -->
   
    <!-- 设置对应的 ORM -->
    <resultMap id="messageAndDetail" type="mybatis.vo.Message">
        <id column="id" property="id"/>
        <result column="msg_id" property="msgId"/>
        <result column="status" property="status"/>
        <result column="content" property="content"/>
        <result column="deleted" property="deleted"/>
        <result column="create_time" property="createTime"/>
        <result column="update_time" property="updateTime"/>
        <!-- 通过 association 关联到 messageDetail 来实现一对一的级联关系 -->
        <association property="messageDetail" column="msg_id"
                     select="mybatis.mapper.MessageDetailMapper.getMessageByMsgId" />
    </resultMap>
    
    <!-- 关联之后，只需要使用一般的方法进行数据操作即可 -->
    <select id="getMessageAndMessageDetail" parameterType="long" resultMap="messageAndDetail">
        SELECT 
            <include refid="allColumns" />
        FROM
            tb_message
        WHERE
            id=#{id}
    </select>
</mapper>
```



### 一对多

和一对一类似，这里以 `User` 和 `UserContact` 为例，一个 `User` 可以有多个 `UserContact`。

`UserContact`：

```java
public class UserContact {

    private Long id;
    private Long userId;
    private Integer contactType;
    private String contactValue;
    private Date createTime;
    private Date updateTime;
    
    // 省略部分 getter 和 setter 方法
}
```

对应的 Mapper XML 配置文件：

```xml
<?xml version="1.0" encoding="utf-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd" >
<mapper namespace="mybatis.mapper.UserContactMapper">
    <sql id="allColumn">
        id, user_id, contact_type, contact_value, create_time, update_time
    </sql>

    <select id="getUserContactByUserId" parameterType="long" resultType="mybatis.vo.UserContact">
        SELECT
            <include refid="allColumn"/>
        FROM
           tb_user_contact
        WHERE
            user_id=#{userId}
    </select>
</mapper>
```



`User`：

```java
public class User {
    private long id;
    private String name;
    public int age;
    private List<UserContact> userContacts;
}
```

修改 `User` 的 Mapper XML 配置文件：

```xml
<?xml version="1.0" encoding="utf-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd" >
<mapper namespace="mybatis.mapper.UserMapper">
    <sql id="allColumn">
        id, name, age
    </sql>

    <resultMap id="userResult" type="mybatis.vo.User">
        <id column="id" property="id"/>
        <result column="name" property="name"/>
        <result column="age" property="age"/>
        <!-- 使用 collection 表示这是一个一对多的级联关系，注意这里的 column 表示的是关联到另一个表的列，但是列名是当前表的列名 -->
        <collection property="userContacts" column="id"
                    select="mybatis.mapper.UserContactMapper.getUserContactByUserId" />
    </resultMap>

    <select id="getUserById" parameterType="long" resultMap="userResult">
        SELECT 
            <include refid="allColumn" />
        FROM
            tb_user
        WHERE
            id=#{id}
    </select>
</mapper>
```

之后按照一般流程的使用即可。



### 多对多

更加复杂的级联关系，每条记录都对应着另一个表中的其它多个列，这种关系也可以转换成为 n 个一对多的关系，再使用上文的 一对多的关系进行处理即可



级联不是一个十分好的策略，一般情况下要避免使用



## MyBatis 缓存

### 一级缓存

`MyBatis` 默认打开的缓存，在多次查询同一条数据时，会自动将结果进行缓存，当之后进行数据的访问时将直接从缓存中获取数据而不会再进行数据库的数据查询。 一级缓存只对应当前的 `SqlSession`，对于不同的 `SqlSession` 对象将会走不同的一级缓存。

```java
// 对于同一个 SqlSession，当第一次查询数据时，会放入到一级缓存中
UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
User user = userMapper.getUserById(1L);
log.info("user={}", user.toString()); // 第一次的查询会直接进行数据库的访问操作

// 第二次查询将会直接从一级缓存中获取数据而不会再进行数据库的访问
User user1 = userMapper.getUserById(1L);
log.info("user={}", user1.toString());
```

一级缓存失效的一种情况

```java
UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
User user = userMapper.getUserById(1L);
log.info("user={}", user.toString());

// 由于新打开了一个 SqlSession，因此从这个 SqlSession 中获取相同的数据依旧会进行数据库的访问
SqlSession sqlSession1 = SqlSessionFactoryUtil.openSqlSession();
UserMapper userMapper1 = sqlSession1.getMapper(UserMapper.class);
User user1 = userMapper1.getUserById(1L);
log.info("user={}", user1.toString());
```



### 二级缓存

需要手动进行开启，在 Mapper XML 映射文件中添加 `<cache />` 标签即可，开启二级缓存需要对应的实体对象实现序列化

添加二级缓存：

```xml
<?xml version="1.0" encoding="utf-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd" >
<mapper namespace="mybatis.mapper.MessageMapper">
    <cache /> <!-- 为当前的 Mapper 开启二级缓存，注意，对应的实体类应当是实现了序列化的 -->
</mapper>
```

**注意：** 如果要使得二级缓存生效，除了需要将对应的实体对象实现序列化之外，还需要在每次执行数据库访问操作之后进行一次事务的提交，无论是否在开启`SqlSession`的时候是否已经默认打开了自动提交



### 自定义缓存

定一个类，实现 `org.apache.ibatis.cache`，然后在 Mapper XML 中引用这个缓存实体对象即可，如：定一个一个 `MineCache` 的实现了 `org.apache.ibatis.cache` 的自定义缓存对象，只需在对应的 Mapper XML 映射文件中添加 `<cache-ref namespace="xxx.xxx.MineCache" />` 即可使用自定义的缓存

