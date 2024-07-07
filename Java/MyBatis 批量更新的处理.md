# MyBatis 批量更新的处理

一般来讲，在使用 MyBatis 进行数据库的访问时，通常会遇到需要更新数据的相关业务，在某些业务场景下，如果需要进行一批次的数据更新，可能性能不是特别理想。本文将简要介绍几种能够高效地处理批量更新数据的实现方式

## 单语句的批量更新

在某些业务场景下，可能更新的到的数据都在同一个表中，关联到的业务也是同一个业务，并且此次更新后的值是一致的，那么在这种情况下，可以通过编写统一的 `update` 语句来加快这个处理过程，如下所示：

``` xml
<update id = "updateBusiness">
    UPDATE SET col1=#{val1}, col2=#{val2} WHERE rel_id = #{relId}
</update>
```

由于这种方式简单，并且效率高，因此这种方式应当是优先被考虑的方式

## 使用 INSERT 代替 UPDATE

### 先删除，再新增

如果上文的更新方式不适合对于需求，可以考虑将现有的数据移除，再插入更新后的数据，由于 `INSERT` 语句支持一次性插入多条数据，从而降低对数据库的访问频率，在某些情况下可以大幅度提升处理性能

**注意**：这种方式的删除和插入应当在同一个事务中进行，否则可能会出现数据异常或者数据丢失的极端情况

如果使用的是通用 `Mapper`，https://github.com/abel533/Mybatis-Spring，那么可以自定义扩展的批量插入实现，从而简化相关的 `XML` 语句，具体实现样例如下：

首先，定义提供 `SQL` 语句的 `Provider`：

``` java
import org.apache.ibatis.mapping.MappedStatement;
import tk.mybatis.mapper.MapperException;
import tk.mybatis.mapper.entity.EntityColumn;
import tk.mybatis.mapper.mapperhelper.EntityHelper;
import tk.mybatis.mapper.mapperhelper.MapperHelper;
import tk.mybatis.mapper.mapperhelper.SelectKeyHelper;
import tk.mybatis.mapper.mapperhelper.SqlHelper;
import tk.mybatis.mapper.provider.base.BaseInsertProvider;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author lxh
 */
public class ExtendsInsertProvider
        extends BaseInsertProvider {
    public ExtendsInsertProvider(Class<?> mapperClass, MapperHelper mapperHelper) {
        super(mapperClass, mapperHelper);
    }

    protected final static String VAR_REGEX = "#\\{(.+)}";

    public String saveAll(MappedStatement ms) {
        Class<?> entityClass = getEntityClass(ms);
        StringBuilder sql = new StringBuilder();
        //获取全部列
        Set<EntityColumn> columnList = EntityHelper.getColumns(entityClass);
        EntityColumn logicDeleteColumn = SqlHelper.getLogicDeleteColumn(entityClass);
        sql.append(SqlHelper.insertIntoTable(entityClass, tableName(entityClass)));
        sql.append(SqlHelper.insertColumns(entityClass, false, false, false));
        sql.append("<trim prefix=\"VALUES \" suffixOverrides=\",\">");
        sql.append("<foreach collection=\"collection\" item=\"item\" separator=\",\" >");
        processKey(sql, entityClass, ms, columnList);
        sql.append("<trim prefix=\"(\" suffix=\")\" suffixOverrides=\",\">");
        for (EntityColumn column : columnList) {
            if (!column.isInsertable()) {
                continue;
            }
            if (logicDeleteColumn != null && logicDeleteColumn == column) {
                sql.append(SqlHelper.getLogicDeletedValue(column, false)).append(",");
                continue;
            }
            String tmp;
            //优先使用传入的属性值,当原属性property!=null时，用原属性
            //自增的情况下,如果默认有值,就会备份到property_cache中,所以这里需要先判断备份的值是否存在
            //其他情况值仍然存在原property中
            if (column.isIdentity()) {
                tmp = SqlHelper.getIfCacheNotNull(column,
                        column.getColumnHolder(null, "_cache", ","));
                sql.append(tmp);
            } else {
                //其他情况值仍然存在原property中
                tmp = SqlHelper.getIfNotNull(column,
                        column.getColumnHolder(null, null, ","), isNotEmpty());
                sql.append(replaceByRegex(tmp, VAR_REGEX, "item.", true));
            }

            //当属性为null时，如果存在主键策略，会自动获取值，如果不存在，则使用null
            //当null的时候，如果不指定jdbcType，oracle可能会报异常，指定VARCHAR不影响其他
            if (column.isIdentity()) {
                tmp = SqlHelper.getIfCacheIsNull(column, column.getColumnHolder() + ",");
                sql.append(replaceByRegex(tmp, "#\\{(.+})", "item.", true));
            } else {
                //当null的时候，如果不指定jdbcType，oracle可能会报异常，指定VARCHAR不影响其他
                tmp = SqlHelper.getIfIsNull(column,
                        column.getColumnHolder(null, null, ","), isNotEmpty());
                sql.append(replaceByRegex(tmp, VAR_REGEX, "item.", true));
            }
        }
        sql.append("</trim>");
        sql.append("</foreach>");
        sql.append("</trim>");
        return sql.toString();
    }

    protected void processKey(StringBuilder sql, Class<?> entityClass,
                              MappedStatement ms, Set<EntityColumn> columnList) {
        //Identity列只能有一个
        boolean hasIdentityKey = false;
        //先处理cache或bind节点
        for (EntityColumn column : columnList) {
            if (column.isIdentity()) {
                //这种情况下,如果原先的字段有值,需要先缓存起来,否则就一定会使用自动增长
                //这是一个bind节点
                String tmp = SqlHelper.getBindCache(column);
                sql.append(replaceByRegex(tmp, "value=\"(.+\")", "item.", true));
                //如果是Identity列，就需要插入selectKey
                //如果已经存在Identity列，抛出异常
                if (hasIdentityKey) {
                    //jdbc类型只需要添加一次
                    if (column.getGenerator() != null && "JDBC".equals(column.getGenerator())) {
                        continue;
                    }
                    throw new MapperException(ms.getId() + "对应的实体类"
                            + entityClass.getName() + "中包含多个MySql的自动增长列,最多只能有一个!");
                }
                //插入selectKey
                SelectKeyHelper.newSelectKeyMappedStatement(ms, column, entityClass, isBEFORE(), getIDENTITY(column));
                hasIdentityKey = true;
            } else if (column.getGenIdClass() != null) {
                sql.append("<bind name=\"").append(column.getColumn())
                        .append("GenIdBind\" value=\"@tk.mybatis.mapper.genid.GenIdUtil@genId(");
                sql.append("item").append(", '").append(column.getProperty()).append("'");
                sql.append(", @").append(column.getGenIdClass().getName()).append("@class");
                sql.append(", '").append(tableName(entityClass)).append("'");
                sql.append(", '").append(column.getColumn()).append("')");
                sql.append("\"/>");
            }
        }
    }

    public static String replaceByRegex(String rawStr, String regex,
                                        String content, boolean et) {
        if (rawStr == null || rawStr.isEmpty()) {
            return rawStr;
        }
        if (!et) {
            return rawStr.replaceAll(regex, content);
        }

        Pattern pat = Pattern.compile(regex);
        Matcher matcher = pat.matcher(rawStr);
        while (matcher.find()) {
            if (matcher.groupCount() >= 1) {
                String group = matcher.group(1);
                rawStr = rawStr.replace(group, content + group);
            }
        }
        return rawStr;
    }
}
```

然后，定义通用的批量插入接口：

``` java
import org.apache.ibatis.annotations.InsertProvider;
import tk.mybatis.mapper.annotation.RegisterMapper;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

/**
 * @author lxh
 */
@RegisterMapper
public interface ExtendsMapper<T>
        extends Mapper<T> {

    @InsertProvider(type = ExtendsInsertProvider.class, method = "dynamicSQL")
    int saveAll(List<? extends T> data);
}
```

最后，只需要当前使用的 `Mapper` 继承这个扩展的 `ExtendsMapper` 即可使用批量插入的功能：

``` java
import com.example.demo.entity.BigColsSchema;

/**
 * @author lxh
 */
public interface BigColsSchemaMapper
        extends ExtendsMapper<BigColsSchema> {
}
```

一般来讲，一次批量处理的数据量在 $500-1000$ 左右是比较合适的，实际处理可以根据相关的配置选择一次插入的合适阈值

除了先删除，再插入的方式外，现在主流的 `RDBMS` 都提供了类似 `INSERT INTO ... ON DUPLICATE KEY UPDATE` 的方式来实现主键重复时需要执行的后续动作

### 使用 DBMS 的特殊 INSERT 语句

大部分主流的关系型数据库，如 `MySQL`、`PostgreSQL` 以及 `Oracle` 等都提供了类似的方式用于处理这样的场景：当本次插入的数据记录已经存在时，应当采取的何种行为。通过这种方式也能够有效的提高批处理更新的效率

以 `MySQL` 为例，在 8.0 及之后的版本，都提供了 `INSERT INTO table_name(col1, col2,....) VALUES(?, ?, ?)  ON DUPLICATE KEY UPDATE col1=?` 方式来处理当记录重复时，针对特殊列的更新处理。为了区分旧有的记录和现有的插入记录，可以将插入的记录进行起别名来做区分，以下面的插入语句为例：

``` sql
-- 插入一条新的记录，并将本次插入得到记录起名为 new, 当 MySQL 识别到这条插入的语句重复时，便会将现有表中对应记录的 simple_id, user_name 更新为本次插入记录行的相关属性值
INSERT INTO user_info(user_id, simple_id, user_name)
VALUES (1, '0x4f', 'xhliu2'), (2, '0x4f4f', 'xhliu2')
    AS new ON DUPLICATE KEY UPDATE simple_id=new.simple_id, user_name=new.user_name
```

同样以通用 `Mapper` 为例，可以为每个实体提取对应的处理公用处理逻辑：

``` java
public String mysqlUpdateAll(MappedStatement ms) {
    Class<?> entityClass = getEntityClass(ms);
    StringBuilder sql = new StringBuilder(saveAll(ms)); // 上文的 saveAll 方法
    sql.append(" AS new ON DUPLICATE KEY UPDATE ");

    sql.append("<trim suffixOverrides=\",\">");
    //获取全部列
    String tableName = tableName(entityClass);
    Set<EntityColumn> columnSet = EntityHelper.getColumns(entityClass);
    //当某个列有主键策略时，不需要考虑他的属性是否为空，因为如果为空，一定会根据主键策略给他生成一个值
    for (EntityColumn column : columnSet) {
        if (!column.isInsertable()) {
            continue;
        }
        if (column.isId()) {
            continue;
        }
        String colName = column.getColumn();
        sql.append(colName).append("=IF(").append("new.").append(colName)
            .append(" IS NULL, ").append(tableName).append(".").append(colName)
            .append(", ").append("new.").append(colName).append(")")
            .append(",");
    }
    sql.append("</trim>");

    return sql.toString();
}
```

这种方式的缺点在于对于记录行的操作会有一些副作用，比如，对于自增的列，每次的插入都会增加这一列的值，但相比上文的先删除再插入，可能从处理方式上来讲，更加 “可靠” 一些

## 一次发送多个 `UPDATE`

一般主流的方式是通过编写一个 `<foreach>` 标签，其中元素为本次需要更新的数据，通过增加访问一次数据库的量来减少数据库的访问频率，这个思路也是当今网络通信处理的通用思路

例如，我们可以编写如下的 `MyBatis` 接口：

``` java
@Mapper
public interface SaleInfoMapper {
    @Update({
            "<script>",
            "<foreach collection=\"data\" item=\"item\" separator=\";\">",
            "UPDATE sale_info SET id=#{item.id}, amount=#{item.amount}, " +
                    "year=#{item.year} WHERE id=#{item.id}",
            "</foreach>",
            "</script>"
    })
    int updateAll(@Param("data") List<? extends SaleInfo> data);
}
```

这样做的前提条件是数据库能够支持一次处理多个语句，对于 `MySQL` 来讲，如果需要开启这样的功能，则需要在连接的 `url` 后加上 `allowMultiQueries=true` 来开启这一功能



## Batch Executor

在常见的业务场景中，造成批量更新性能低下的主要原因在于每次的更新都需要访问一次数据库，上文所描述的方法都是在数据库的角度上减少对数据库的访问频率来提高处理性能。除了这种方式外，`java.sql.Statement` 提供了批处理的形式来对相同的 `SQL` 语句进行特殊的处理，从而减少对数据库的访问频率

`MyBatis` 已经对此进行了封装，我们只需要在创建 `SqlSession` 的时候指定对应的 `ExecutorType` 即可使用批处理模式，具体示例如下：

``` java
import com.example.demo.entity.SaleInfo;
import com.google.common.base.Stopwatch;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author lxh
 */
@SpringBootTest
public class BatchUpdateTest {

    private final static Logger log = LoggerFactory.getLogger(BatchUpdateTest.class);

    @Resource
    private ApplicationContext context;

    private final List<SaleInfo> data = new ArrayList<>();

    // 测试前首先需要加载对应数据
    @BeforeEach
    void loadData() {
        SaleInfoMapper mapper = context.getBean(SaleInfoMapper.class);
        List<SaleInfo> saleInfos = mapper.sampleInfo();
        if (!CollectionUtils.isEmpty(saleInfos)) {
            saleInfos.forEach(v -> v.setAmount(31415926));
            data.addAll(saleInfos);
        }
    }

    // 传统的单次更新处理。。。。。
    @Test
    public void simpleUpdateTest() {
        SaleInfoMapper mapper = context.getBean(SaleInfoMapper.class);
        Stopwatch stopwatch = Stopwatch.createStarted();
        for (SaleInfo saleInfo : data) {
            mapper.update(saleInfo);
        }
        log.info("simpleUpdateTest take {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    // 批量更新的有关处理
    @Test
    public void batchUpdateTest() {
        SqlSessionFactory sqlSessionFactory = context.getBean(SqlSessionFactory.class);
        Stopwatch stopwatch = Stopwatch.createStarted();
        try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
            SaleInfoMapper infoMapper = sqlSession.getMapper(SaleInfoMapper.class);
            List<SaleInfo> aux = new ArrayList<>();
            for (SaleInfo saleInfo : data) {
                aux.add(saleInfo);
                infoMapper.update(saleInfo);
                if (aux.size() >= 500) {
                    sqlSession.flushStatements();
                    aux.clear();
                }
            }
            if (!aux.isEmpty()) sqlSession.flushStatements();
            log.info("batchUpdateTest take {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }
}
```

在这次的测试中，总共有 $3951$ 条测试数据，在我的机器上，使用传统的单次更新进行处理，共计耗时 $15745$ 毫秒，而使用批处理的形式进行处理，共计耗时 $2312$ 毫秒（注意关闭 `autoCommit` ，否则这里的处理没有意义）

<br />

参考：

<sup>[1]</sup> https://dev.mysql.com/doc/refman/8.0/en/insert-on-duplicate.html

<sup>[2]</sup> https://www.baeldung.com/jdbc-batch-processing