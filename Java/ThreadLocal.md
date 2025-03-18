# ThreadLocal

## 简介

在线程的存活周期中，可能会需要一种绑定线程相关的局部变量的属性（如会话信息、参数信息等），一种可行的方式是对每个调用的方法所对应的参数对象进行封装，以使得参数能够按照对应的顺序进行传递。然而，在大部分的场景下，这种方式是不可行的，因为随着参数的增多，可能会使得方法变得更复杂，并且无法作为一个单独的三方库植入对应的系统。为此，可以通过使用 `ThreadLocal` 来绑定对应的线程变量信息

## 相关使用

一种常见的使用方式便是用于存储会话信息，一般可以通过配置 `javax.servlet.Filter` 来实现，将会话信息存储在一个 `ThreadLocal` 中，使得后续的业务都能够访问到当前的会话信息

``` java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import java.io.IOException;

@Component
public class SessionFilter implements Filter {

    // 存储当前的会话信息，后续的业务处理可以有此直接获取，而无需额外的查询
    public final static ThreadLocal<String> SESSION_LOCAL = new ThreadLocal<>();

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        try {
            SESSION_LOCAL.set(getSessionInfo());
            chain.doFilter(request, response);
        } finally {
            SESSION_LOCAL.remove();
        }
    }
}
```

在实际使用时，在需要使用时，直接获取会话信息即可：

``` java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SaleInfoService {

    public void updateSaleInfo() {
        String userInfo = SessionFilter.SESSION_LOCAL.get();
        // 。。。。。其余的部分业务逻辑
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

   - `replaceStaleEntry`

     `replaceStaleEntry` 方法用于直接替换掉当前节点的 `ThreadLocal` 已经被清理的情况，具体的流程如下图所示：

     ![ThreadLocal_replaceStaleEntry.png](https://s2.loli.net/2025/03/17/G7pFf2j6kNs4YQi.png)

     对应的源码如下：

     ``` java
     private void replaceStaleEntry(ThreadLocal<?> key, Object value,
                                    int staleSlot) {
         Entry[] tab = table;
         int len = tab.length;
         Entry e;
     
         // Back up to check for prior stale entry in current run.
         // We clean out whole runs at a time to avoid continual
         // incremental rehashing due to garbage collector freeing
         // up refs in bunches (i.e., whenever the collector runs).
         /*
         	staleSlot 表示本次确定已经被清除的 ThreadLocal 位置索引，最终一定会将该位置替换为本次的 ThreadLocal
         	slotToExpunge 表示需要被清理的 ThreadLocal 位置，如果最终 slotToExpunge != staleSlot，说明需要对其进行清理了
         */
         int slotToExpunge = staleSlot;
         /*
         	这里通过向前探测的方式来寻找  slotToExpunge 的位置，
         	目的是为了判断在未冲突的部分是否也发生了 ThreadLocal 的清理
         */
         for (int i = prevIndex(staleSlot, len);
              (e = tab[i]) != null;
              i = prevIndex(i, len))
             if (e.get() == null)
                 slotToExpunge = i;
     
         // Find either the key or trailing null slot of run, whichever
         // occurs first
         for (int i = nextIndex(staleSlot, len); // 向后以线性探测的方式进行查找
              (e = tab[i]) != null;
              i = nextIndex(i, len)) {
             ThreadLocal<?> k = e.get();
     
             // If we find key, then we need to swap it
             // with the stale entry to maintain hash table order.
             // The newly stale slot, or any other stale slot
             // encountered above it, can then be sent to expungeStaleEntry
             // to remove or rehash all of the other entries in run.
             /*
             	如果在解决冲突的过程中发现与本次的 ThreadLocal 一致，
             	则需要将其替换到 staleSlot 的位置，并对其进行清理
             */
             if (k == key) {
                 e.value = value;
     
                 tab[i] = tab[staleSlot];
                 tab[staleSlot] = e;
     
                 // Start expunge at preceding stale entry if it exists
                 // 如果之前没有找到被清除的位置索引，在替换后原来的位置 i 就成为了需要被清除的索引
                 if (slotToExpunge == staleSlot)
                     slotToExpunge = i;
                 cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                 return;
             }
     
             // If we didn't find stale entry on backward scan, the
             // first stale entry seen while scanning for key is the
             // first still present in the run.
             /* 
             	同样地，如果之前向前无法找到被清除的位置，而当前的位置已经被清除了，
             	则将 slotToExpunge 置为 i，在后续的处理中对其进行清除
             	expungeStaleEntry 也会解决冲突的情况，因此记录第一次出现的位置即可
             */
             if (k == null && slotToExpunge == staleSlot)
                 slotToExpunge = i;
         }
     
         // If key not found, put new entry in stale slot
         /*
         	当前的 table 中不存在与当前 ThreadLocal 对应的 Entry，
         	因此需要新创建 Entry，并替换 staleSlot 位置对应的 Entry
         */
         tab[staleSlot].value = null;
         tab[staleSlot] = new Entry(key, value);
     
         // If there are any other stale entries in run, expunge them
         /*
         	向前探测的过程中发现了被清理的 ThreadLocal，同样需要对其进行清理
         */
         if (slotToExpunge != staleSlot)
             cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
     }
     ```

   - `expungeStaleEntry`

     在 `replaceStaleEntry` 中较为重要的是 `expungeStaleEntry`  方法，它的作用是清除对应位置的 `Entry`，并对冲突的 `Entry` 重新排列，具体的流程如下所示：

     ![ThreadLocal_expungeStaleEntry.png](https://s2.loli.net/2025/03/17/JtRf1TkhCKcnpao.png)

     对应的源码如下：

     ``` java
     private int expungeStaleEntry(int staleSlot) {
         Entry[] tab = table;
         int len = tab.length;
     
         // expunge entry at staleSlot
         // 首先清除 statleSlot 对应的 Entry 信息
         tab[staleSlot].value = null;
         tab[staleSlot] = null;
         size--;
     
         // Rehash until we encounter null
         Entry e;
         int i;
         /*
         	线性探测地处理 staleSlot 位置冲突的 ThreadLocal，并对它们进行清除或调整
         */
         for (i = nextIndex(staleSlot, len);
              (e = tab[i]) != null;
              i = nextIndex(i, len)) {
             ThreadLocal<?> k = e.get();
             if (k == null) {
                 /*
                 	如果当前 Entry 对应的 ThreadLocal 已经被清除了，
                 	则直接清除当前位置的 Entry 信息
                 */
                 e.value = null;
                 tab[i] = null;
                 size--;
             } else {
                 /*
                 	走到这里说明当前的 Entry 的 ThreadLocal 依旧存活，因此
                 	需要对其进行调整
                 */
                 int h = k.threadLocalHashCode & (len - 1);
                 if (h != i) {
                     /*
                     	这里首先将当前的位置设置为 null 是为了在后续的冲突解决中一定能找到本次 Entry 最终的实际位置，
                     	其次，在清除的过程中，有可能之前冲突的部分已经被清理了，需要将该位置的 Entry 移动到之前的被清除位置，相当于
                     	与之前的位置做了一次交换
                     */
                     tab[i] = null;
     
                     // Unlike Knuth 6.4 Algorithm R, we must scan until
                     // null because multiple entries could have been stale.
                     while (tab[h] != null)
                         h = nextIndex(h, len);
                     tab[h] = e; // 交换 i 的 Entry 到 h
                 }
             }
         }
         return i; // 这里的 table[i] 为 null
     }
     ```

   - `cleanSomeSlots`

     `cleanSomeSlots` 的作用是机会性地清除一些 `Entry`，对应的执行流程如下图所示：

     ![ThreadLocal_cleanSomeSlots.png](https://s2.loli.net/2025/03/17/SpxKRiqhzWUw1j3.png)

     对应的源码如下：

     ``` java
     /*
     	注意：这里的 i 对应的 Entry 为 null
     	n 用于控制循环的次数，如果没有 ThreadLocal 被清除，会检查 i 后的 log2n 个位置是否存在被清除的 Entry
     */
     private boolean cleanSomeSlots(int i, int n) {
         boolean removed = false;
         Entry[] tab = table;
         int len = tab.length;
         do {
             i = nextIndex(i, len);
             Entry e = tab[i];
             if (e != null && e.get() == null) {
                 n = len;
                 removed = true;
                 i = expungeStaleEntry(i); // 清除 i 的 Entry，并调整与 i 冲突的 ThreadLocal
             }
         } while ( (n >>>= 1) != 0);
         return removed;
     }
     ```

   - `rehash` 

     `rehash` 方法的目的是为了扩容，但正如上文提到的，`cleanSomeSlots` 只会机会性的清除一些 `Entry`，因此在调用 `rehash` 方法时，`table` 中依旧可能存在 `ThreadLocal` 已经被清除的 `Entry`。因此首先需要对整个数组进行清理操作，再判断是否达到了扩容的阈值

     对应的源码如下所示：

     ``` java
     private void rehash() {
         expungeStaleEntries(); // 对整个数组的元素进行清理操作
     
         // Use lower threshold for doubling to avoid hysteresis
         /*
         	threshold 为数组长度的 2/3，这里的 3/4 就是数组长度的 1/2，为理想的阈值
         */
         if (size >= threshold - threshold / 4)
             resize();
     }
     ```

     值得提一句的是，线性探测法的时间复杂度为 $O(\frac{1}{1-\alpha})$，其中，$\alpha$ 表示数组中实际使用的空间占整体空间的比值，一般认为 $\frac{1}{2}$ 是一个比较理想的状态

     - `expungeStaleEntries`

       `expungeStaleEntries` 用于整个数组的清理操作，对应的源码如下所示：

       ``` java
       private void expungeStaleEntries() {
           Entry[] tab = table;
           int len = tab.length;
           for (int j = 0; j < len; j++) {
               Entry e = tab[j];
               /* 
               	遍历数组的每个 Entry，对可能被清除的 Entry 位置调用 expungeStaleEntry 进行清理
               */
               if (e != null && e.get() == null)
                   expungeStaleEntry(j);
           }
       }
       ```

     - `resize`

       `resize` 方法是实际的扩容操作，具体的做法就是遍历整个 `table`，将 `table` 中的每个未被清除的 `Entry` 按照线性探测的冲突处理策略放入新的 `table` 中，在最后替换原有的 `table` 和阈值信息

       对应的源码如下所示：

       ``` java
       private void resize() {
           Entry[] oldTab = table;
           int oldLen = oldTab.length;
           int newLen = oldLen * 2; // 扩容长度为原来的两倍
           Entry[] newTab = new Entry[newLen];
           int count = 0;
       
           for (Entry e : oldTab) { // 遍历整个 table，将未被清理的 Entry 放入新的 table 中
               if (e != null) {
                   ThreadLocal<?> k = e.get();
                   if (k == null) {
                       e.value = null; // Help the GC
                   } else {
                       int h = k.threadLocalHashCode & (newLen - 1);
                       while (newTab[h] != null)
                           h = nextIndex(h, newLen);
                       newTab[h] = e;
                       count++;
                   }
               }
           }
       
           // 替换原有的 table，size、以及扩容阈值
           setThreshold(newLen);
           size = count;
           table = newTab;
       }
       ```

### `get`方法

`get` 方法相比较 `set` 方法要简单很多，整体的流程如下图所示：

![ThreadLocal_get.png](https://s2.loli.net/2025/03/17/Zh7iHJqDUBV6jGt.png)

具体源码如下：

``` java
public T get() {
    Thread t = Thread.currentThread();
    ThreadLocalMap map = getMap(t);
    // 首先，获取到当前线程关联的 ThreadLocalMap
    if (map != null) {
        // 根据当前 ThreadLocal，定位到对应的 Entry
        ThreadLocalMap.Entry e = map.getEntry(this);
        if (e != null) {
            @SuppressWarnings("unchecked")
            T result = (T)e.value;
            return result;
        }
    }
    // 如果没有与之关联的 Entry，则将初始值与当前 ThreadLocal 绑定到一起，并返回初始值
    return setInitialValue();
}
```

`map.getEntry` 对应的源码如下：

``` java
private Entry getEntry(ThreadLocal<?> key) {
    int i = key.threadLocalHashCode & (table.length - 1);
    Entry e = table[i];
    // 首次定位命中，则返回当前位置的 Entry
    if (e != null && e.get() == key)
        return e;
    else
        // 可能存在冲突，需要线性探测后续的位置
        return getEntryAfterMiss(key, i, e);
}

// 首次未命中的后续处理
private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
    Entry[] tab = table;
    int len = tab.length;

    while (e != null) {
        ThreadLocal<?> k = e.get();
        if (k == key)
            return e; // 冲突后找到的与当前 ThreadLocal 关联的 Entry
        if (k == null)
            /*
            	当前位置的 ThreadLocal 被清除了，需要对这个位置进行清理
            */
            expungeStaleEntry(i);
        else
            // 线性探测下一个元素
            i = nextIndex(i, len);
        e = tab[i];
    }
    return null; // 说明当前 table 中没有与之关联的 Entry
}
```

`setInitialValue` 对应的源码如下：

``` java
private T setInitialValue() {
    /*
    	当前 ThreadLocal 默认的初始值，可以通过 withInitial 的静态方法或者重写 ThreadLocal
    	的 initialValue 来设置默认值
    */
    T value = initialValue();
    
    // 与 set 方法一致
    Thread t = Thread.currentThread();
    ThreadLocalMap map = getMap(t);
    if (map != null) {
        map.set(this, value);
    } else {
        createMap(t, value);
    }
    // set value 结束
    
    if (this instanceof TerminatingThreadLocal) {
        TerminatingThreadLocal.register((TerminatingThreadLocal<?>) this);
    }
    
    // 返回默认的初始值
    return value;
}
```

## 相关问题

1. `ThreadLocal` 是线程安全的吗？

   在 《Java 并发编程实战》一书中提到过 “线程封闭”的概念， `ThreadLocal`本身就是一种 “线程封闭” 的机制，因此它一定是线程安全的

2. `ThreadLocal` 会导致内存泄漏吗？

   在网上很多的博客文章中，提到 `ThreadLocal` 就会想到它的 “内存泄漏” 问题，但实际上，这种情况几乎不可能出现。

   首先，按照一般的说法，发生内存泄漏首先就需要满足以下两个条件：

   - 线程没有消亡，如果是一般的线程池的配置，这一条件就很容易达到
   - 相关的 `ThreadLocal` 只存在弱引用，并且发生了 GC 回收了这部分内存空间。这个条件很难达到，因为一般对于 `ThreadLocal` 的使用都是通过静态变量的方式进行引用，因此一直会存在至少一个强引用导致不会被 GC 回收

   其次，即使同时满足了上述的两个条件，在 `set` 方法和 `get` 方法的过程中我们可以看到，会检测 `Entry` 对应的 `ThreadLocal` 是否被清除，对于已经被清除的 `Entry` 会调用 `expungeStaleEntry` 对其进行清理，也不会发生真正意义上的内存泄漏

   综上所述，`ThreadLocal` 很难发生内存泄漏，即使发生了内存泄漏，`ThreadLocal` 也会自行清理这部分内容，而无需过多地干预这部分内容，但实际使用过程中，还是推荐保持良好的习惯，在使用完后便对其进行移除，如下所示：

   ``` java
   try {
       SESSION_LOCAL.set("xxxx");
   } finally {
       SESSION_LOCAL.remove();
   }
   ```