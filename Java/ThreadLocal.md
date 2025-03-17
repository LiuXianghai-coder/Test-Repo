# ThreadLocal

## 简介

在线程的存活周期中，可能会需要一种绑定线程相关的局部变量的属性（如会话信息、参数信息等），一种可行的方式是对每个调用的方法所对应的参数对象进行封装，以使得参数能够按照对应的顺序进行传递。然而，在大部分的场景下，这种方式是不可行的，因为随着参数的增多，可能会使得方法变得更复杂，并且无法作为一个单独的三方库植入对应的系统。为此，可以通过使用 `ThreadLocal` 来绑定对应的线程变量信息

## 相关使用

一种常见的使用方式便是对于动态数据源的处理，因为需要在实际业务处理的过程中，对当前线程所需要执行的数据源进行切换

如，创建一个如下的 `DataSourceHolder` 对象，表示当前持有的数据源信息：

``` java
public final class DataSourceHolder {

    /**
    	使用一个 ThreadLocal 来存储当前线程持有的动态数据源信息，后续对于数据源的修改只需要修改该 ThreadLocal 
    	对象里持有的数据源信息即可进行判断判断
    */
    private final static ThreadLocal<String> DATASOURCE_HOLDER = new ThreadLocal<>();

    public static String getCurDataSource() {
        return DATASOURCE_HOLDER.get();
    }

    public static void setCurDataSource(String curDataSource) {
        DATASOURCE_HOLDER.set(curDataSource);
    }
}
```

当然，同样需要对进行切换的上下文做一个切入点的拦截处理，也就是 AOP：

``` java
import com.example.demo.transaction.DataSourceHolder;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DataSourceAspect {
    
    /**
    	作为 AOP 的切入点，即对应的 service 下的所有 Bean 进行拦截处理
    */
    @Pointcut("execution(* com.example.demo.service.*.*(..))")
    public void dataSourcePointcut() {
    }

    /**
    	实际拦截的业务处理逻辑
    */
    @Before("com.example.demo.aop.DataSourceAspect.dataSourcePointcut()")
    public void setDataSource(JoinPoint joinPoint) {
        String dataSource = DataSourceHolder.getCurDataSource();
        if (dataSource.equals("mysql")) {
            // 为 MySQL 数据源的特殊处理
        } else {
            // 其它数据源的处理
        }
    }
}
```

在实际使用时，在需要使用时，修改 `DataSourceHolder` 中持有的线程数据源信息即可：

``` java
import com.example.demo.entity.SaleInfo;
import com.example.demo.mapper.SaleInfoMapper;
import com.example.demo.transaction.DataSourceHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;
import tk.mybatis.mapper.util.Sqls;

import javax.annotation.Resource;
import java.util.List;

@Service
@Transactional
public class SaleInfoService {

    private final static Logger log = LoggerFactory.getLogger(SaleInfoService.class);

    @Resource
    private SaleInfoMapper saleInfoMapper;
    
    @Resource
    private UserInfoService userInfoService;

    public void updateSaleInfo() {
        Example example = Example.builder(SaleInfo.class)
                .andWhere(Sqls.custom().andBetween("id", 50001L, 50009L))
                .build();
        DataSourceHolder.setCurDataSource("mysql");
        List<SaleInfo> data = saleInfoMapper.selectByExample(example);
        saleInfoMapper.mysqlUpdateAll(data);

        // 修改数据源为 postgresql，引入 UserInfoService 使得代理生效
        DataSourceHolder.setCurDataSource("postgresql");
        userInfoService.updateUserInfo(data);
    }
}
```

## 源码分析

### 线程的初始化

和设置线程上下文加载器类似，在 `Thread` 线程类中存在 `threadLocals` 和 `inheritableThreadLocals` 两个 `ThreadLocal.ThreadLocalMap` 类型的属性，这两个字段分别表示当前线程持有的线程局部变量和继承自父线程的线程局部变量，对应的源码如下：

``` java
/* ThreadLocal values pertaining to this thread. This map is maintained
 * by the ThreadLocal class. */
ThreadLocal.ThreadLocalMap threadLocals = null;

/*
 * InheritableThreadLocal values pertaining to this thread. This map is
 * maintained by the InheritableThreadLocal class.
 */
ThreadLocal.ThreadLocalMap inheritableThreadLocals = null;
```

在创建线程的时候，只会初始化 `inheritableThreadLocals` 相关的属性：

``` java
private Thread(ThreadGroup g, Runnable target, String name,
                   long stackSize, AccessControlContext acc,
                   boolean inheritThreadLocals) {
    // 省略部分其它源代码

    // 初始化继承自父线程的 ThreadLocal
    if (inheritThreadLocals && parent.inheritableThreadLocals != null)
        this.inheritableThreadLocals =
        ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);

    /* Stash the specified stack size in case the VM cares */
    this.stackSize = stackSize;

    /* Set thread ID */
    this.tid = nextThreadID();
}
```

### `ThreadLocal` 对象的实例化

在每个 `ThreadLocal` 对象中，都会存在一个 `threadLocalHashCode` 属性，这个属性会在实例化时进行初始化，并且对于所有的的 `ThreadLocal` 对象来讲，生成的 `threadLocalHashCode` 都会按照相同的规则进行生成，因此可以得到较好的分散效果，对应的源码如下：

``` java
public class ThreadLocal<T> {
    // 当前生成的 ThreadLocal 对象对应的 hashCode
    private final int threadLocalHashCode = nextHashCode();
    
    private static AtomicInteger nextHashCode =
        new AtomicInteger();
    
    // 一个魔数，用于确保递增的 hashCode 能够取得更好的散列效果
    private static final int HASH_INCREMENT = 0x61c88647;
    
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }
}
```

### `set` 方法

整体的步骤分为以下三个部分：获取当前线程对应的 `threadLocals`、根据当前的 `threadLocals` 判断是否需要实例化、替换或新增当前的 `ThreadLocal` 属性

整体的流程如下：

<img src="https://s2.loli.net/2025/03/11/z3pe6FngX7wEBv2.png" alt="ThreadLocal_init_set.png" style="zoom:80%;" />

1. 当前线程对应的 `threadLocals`

   如前文讲到的，在线程初始化的时候只会对 `inheritableThreadLocals` 进行初始化的处理，因此第一次访问 `threadLocals` 属性时就是 `null`

2. 根据当前的 `threadLocals` 判断是否需要实例化

   如果当前线程是第一次调用 `ThreadLocal` 对象的 `set` 方法，由于 `threadLocals` 为 `null`，因此需要对其进行初始化，初始化对应的源码如下：

   ``` java
   static class ThreadLocalMap {
       /*
       	将当前调用的 ThreadLocal 对象和对应的 Value 绑定为一个 Entry
       */
       static class Entry extends WeakReference<ThreadLocal<?>> {
           Object value;
   
           Entry(ThreadLocal<?> k, Object v) {
               super(k);
               value = v;
           }
       }
       
       private static final int INITIAL_CAPACITY = 16; // 默认的初始容量
       
       private Entry[] table; // 类似 HashMap 中的 Entry，存储实际的 <Key, Value>
       
       private int size = 0;
       
       private int threshold; // 需要进行扩容的阈值
   
       private void setThreshold(int len) {
           threshold = len * 2 / 3;
       }
       
       ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
           table = new Entry[INITIAL_CAPACITY];
           int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
           // 初始化时将本次的 ThreadLocal 和 Value 进行绑定
           table[i] = new Entry(firstKey, firstValue);
           size = 1;
           setThreshold(INITIAL_CAPACITY);
       }
   }
   ```

3. 替换或新增当前的 `ThreadLocal` 属性

   这个阶段是 `set` 方法中比较复杂的部分，主要涉及到 `hash` 冲突、`ThreadLocal` 被清除、清理 `ThreadLocal` 以及扩容的操作，整体的流程如下图所示：

   ![ThreadLocal_map_set.png](https://s2.loli.net/2025/03/11/sEpXGVLI5bKBMDS.png)

   对应的源码如下：

   ``` java
   private void set(ThreadLocal<?> key, Object value) {
   
       // We don't use a fast path as with get() because it is at
       // least as common to use set() to create new entries as
       // it is to replace existing ones, in which case, a fast
       // path would fail more often than not.
   
       /*
       	注意，初始默认的数组大小为 16，之后的每次扩容都会扩大为原来的两倍，因此 table 的长度一定是 2 
       */
       Entry[] tab = table;
       int len = tab.length;
       // 根据 hashCode 定位到当前 table 中对应的索引
       int i = key.threadLocalHashCode & (len-1);
   
       /*
       	如果当前位置存在 hash 冲突，即当前位置存在 ThreadLocal 的占用时，需要对其进行相关处理
       	如果是一个正常的与当前 ThreadLocal 无关的 Enrty，则需要通过 nextIndex 向后寻找下一个索引
       */
       for (Entry e = tab[i];
            e != null;
            e = tab[i = nextIndex(i, len)]) {
           ThreadLocal<?> k = e.get();
   
           /*
           	如果当前位置的 ThreadLocal 与本次调用的 ThreadLocal 一致，
           	则覆盖原有的 value 即可
           */
           if (k == key) {
               e.value = value;
               return;
           }
   
           /*
           	如果当前位置的 ThreadLocal 对象已经被 GC 清理了，
           	那么对这个位置进行替换即可
           */
           if (k == null) {
               replaceStaleEntry(key, value, i);
               return;
           }
           
           // 正常的 ThreadLocal Entry，继续向后寻找下一个索引
       }
   
       tab[i] = new Entry(key, value);
       int sz = ++size;
       /*
       	在添加当前 Entry 后，cleanSomeSlots 会机会性地清理一些已经被 
       	GC 收集的 ThreadLocal 对象
       */
       if (!cleanSomeSlots(i, sz) && sz >= threshold)
           rehash();
   }
   ```

   `replaceStaleEntry` 方法用于直接替换掉当前节点的 `ThreadLocal` 已经被清理的情况，具体的流程如下图所示：

   

### `get`方法

## 相关问题