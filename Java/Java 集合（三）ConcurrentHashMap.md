# Java 集合（三）ConcurrentHashMap

一般来讲，通常使用的 `HashMap` 不是线程安全的，因为没有任何机制来保证每个操作的原子性。在 `ConcurrentHashMap` 出现之前，可以通过给 `HashMap` 的每个操作加上唯一的互斥锁来保证每个操作的线程安全性，这也是 `HashTable` 的实现方式。但是这种方式很笨拙，并且性能较低，因此出现了 `ConcurrentHashMap` 等一系列的并发工具类来提高性能。

本问将针对 JDK 1.7 和 JDK 1.8 的 `ConcurrentHashMap` 的实现进行解析，尽管现在 JDK 的最新版本都已经到 JDK 17 了，但是在 JDK 1.8 之后的版本并没有特别大的改动，因此本文的分析只到 JDK 1.。之所以引入 JDK 1.7 的实现，主要是由于在 JDK 1.7 和 JDK 1.8 之间的实现有质的改变，因此会将 JDK 1.7 的版本作为一个比较。

本文不会讲述如何使用 `ConcurrentHashMap`，有关具体的使用可以参考相关的 API 文档，或者 《Java 并发编程实战》也是对于多线程编程的学习很有帮助的一本书籍

<br />

## JDK 1.7 版本的实现

JDK 1.7 中对于 `ConcurrentHashMap` 的实现是通过分段锁的方式来实现的，具体如下所示：

<img src="https://s2.loli.net/2022/02/18/cAWmQotOBS5pxus.png" alt="image.png" style="zoom:80%;" />

`ConcurrentHashMap` 会维护一个 `Segment` 数组，这个数组有时也被称为 “分段锁” 数组，在这个数组中，在每个分段锁中的操作都是线程安全的，在不同分段锁元素中，所有的操作都能够并发地执行，从而提高了执行效率。

`Segment` 通过继承 `ReentrantLock` 来实现操作的线程安全性

<br />

常用的一些静态字段定义如下：

```java
static final int DEFAULT_INITIAL_CAPACITY = 16; // 默认初始容量
static final float DEFAULT_LOAD_FACTOR = 0.75f; // 默认负载因子
static final int DEFAULT_CONCURRENCY_LEVEL = 16; // 默认并发级别
static final int MAXIMUM_CAPACITY = 1 << 30; // HashMap 的最大容量，即最大元素数量
static final int MIN_SEGMENT_TABLE_CAPACITY = 2; // 最大的 segment 数量
static final int MAX_SEGMENTS = 1 << 16; // 最小 segment 数量
static final int RETRIES_BEFORE_LOCK = 2; // 遇到锁时的重试次数
```



### 构造函数

`ConcurrentHashMap` 存在四个重载的构造函数，但是无一例外的，这几个构造函数最终都会调用 `ConcurrentHashMap(int, float, int)` 这个构造函数，对应的源代码如下所示：

```java
public ConcurrentHashMap(int initialCapacity,
                         float loadFactor, int concurrencyLevel) {
    if (!(loadFactor > 0) || initialCapacity < 0 || concurrencyLevel <= 0)
        throw new IllegalArgumentException();
    if (concurrencyLevel > MAX_SEGMENTS)
        concurrencyLevel = MAX_SEGMENTS;
    
    int sshift = 0;
    int ssize = 1; // segment 数组长度
    
     // 通过concurrencyLevel计算得出， 计算出一个大于或等于concurrencyLevel的最小的2的N次方值
    while (ssize < concurrencyLevel) {
        ++sshift;
        /* 
        	为了能通过按位与的散列算法来定位segments数组的索引（HashMap基础），
        	必须保证segments数组的长度是2的N次方
        */
        ssize <<= 1; 
    }
    
    this.segmentShift = 32 - sshift; // 段偏移量， 默认 28
    this.segmentMask = ssize - 1; // 段掩码， 默认 15
    
    if (initialCapacity > MAXIMUM_CAPACITY)
        initialCapacity = MAXIMUM_CAPACITY; // map 的初始化容量
    
    /*
    	initialCapacity 是整个 map 的初始大小，
    	这里计算出来的 c 表示在每个 Segment 中能够分配的元素的数量
    */
    int c = initialCapacity / ssize;
    
    if (c * ssize < initialCapacity)
        ++c;
    
     /* 
     	segment内部数组的初始化容量， 默认是 2，
     	这样会使得插入第一个元素的时候不会扩容，只有在插入第二个元素时才进行扩容
     */
    int cap = MIN_SEGMENT_TABLE_CAPACITY;
    while (cap < c)
        cap <<= 1; // 保证 segment 内部数组的长度也是 2的N次方
    
    /*
    	创建 Segment 数组，并实例化数组中的地一个元素 segment[0]
    */
    Segment<K,V> s0 =
        new Segment<K,V>(loadFactor, (int)(cap * loadFactor),
                         (HashEntry<K,V>[])new HashEntry[cap]);
    Segment<K,V>[] ss = (Segment<K,V>[])new Segment[ssize];
    
    // 向 segments[0] 中写入对象
    UNSAFE.putOrderedObject(ss, SBASE, s0); // ordered write of segments[0]
    this.segments = ss;
}
```

经过此构造函数之后，便得到一个 `Segment` 数组，同时实例化了 `Segment` 数组中的第一个元素。在初始化完成之后，有以下几点需要注意：

- `Segment` 数组在初始化之后就不能在被修改了，因为它是被 `final` 关键字修饰的
- `Segment` 数组会将 `Segment[0]` 中对应的对象实例化，实例化后的 `Segment` 中存储的内容和 `HashMap` 类似，可以这么理解：`Segment` 数组是在原有的 `HashMap` 上做的一层封装，用于保证 `Map` 的操作的线程安全性
- 调用构造函数之后会初始化 `segmentShift`（段偏移量）和 `segmentMask`（掩码），这个在之后调整数据元素时将会用到



### 添加元素

添加元素对应 `put(K, V)` 方法，具体的源代码如下所示：

```java
public V put(K key, V value) {
    Segment<K,V> s;
    if (value == null)
        throw new NullPointerException();
    // 计算 key 的 hash 值
    int hash = hash(key);
    
    /*
    	根据 hash 值找到该元素节点在 segment 数组中的对应位置 j
    	根据上文构造函数中对于 segmentShift 的初始化，segmentShift 默认为 28
    */
    int j = (hash >>> segmentShift) & segmentMask; // 定位到 key 在 segment 数组中的索引位置
    
    /*
    	调用构造函数时只是实例化了 `segments[0] 位置的对象，
    	因此如果访问其它的 segment 元素需要首先对齐进行实例化
    */
    if ((s = (Segment<K,V>)UNSAFE.getObject          // nonvolatile; recheck
         (segments, (j << SSHIFT) + SBASE)) == null) //  in ensureSegment
        s = ensureSegment(j); // 第一次访问segment时，创建segment对象
    
    return s.put(key, hash, value, false); // 委托给特定的段
}
```

注意：`Segment` 数组在构造函数初始化完成之后就无法被修改了（这也是合理的），但是只是实例化了 `Segment` 数组对象的引用，实际 `Segment` 数组中的每个元素（除了第 0 个元素之外）都是没有被实例化的。因此在添加对应的节点元素时，首先要确保所在的 `Segment` 已经被实例化了，这就是 `ensureSegment(int)` 方法所做的事情，具体对应的源代码如下所示：

```java
private Segment<K,V> ensureSegment(int k) {
    final Segment<K,V>[] ss = this.segments;
    long u = (k << SSHIFT) + SBASE; // raw offset
    Segment<K,V> seg;
    if ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u)) == null) {
        /*
        	上文已经介绍过，Segments 数组在构造函数中会实例化第 0 个位置的对象，
        	这里可以看到，其它的 Segment 都是基于第 0 个 Segment 的相关属性来进行
        	属性设置的，因此在构造函数中实例化第 0 个 Segment 是必需的
        */
        Segment<K,V> proto = ss[0]; // use segment 0 as prototype
        int cap = proto.table.length;
        float lf = proto.loadFactor;
        int threshold = (int)(cap * lf);
        /* --------------- 复制属性结束 -------------------------- */
        
        // 初始化 Segment 内部的 entry 数组
        HashEntry<K,V>[] tab = (HashEntry<K,V>[])new HashEntry[cap];
        /*
        	再一次检查该 Segment 是否被实例化了，
        	因为在这个过程中有可能其它的线程也进行对该位置的 Segment 对象的实例化
        */
        if ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u))
            == null) { // recheck
            Segment<K,V> s = new Segment<K,V>(lf, threshold, tab);
            
            /*
            	通过 CAS 的方式不断检查当前的 Segment 对象是否被实例化
            	如果实例化成功，则退出
            */
            while ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u)) // while 循环的目的是给 seg 再次赋值
                   == null) {
                if (UNSAFE.compareAndSwapObject(ss, u, null, seg = s))
                    break;
            }
        }
    }
    return seg;
}
```

<br />

#### Segment 的 put

目前，第一层 `Segment` 数组的 `put` 方法已经大致了解了处理流程，现在需要了解一下在 `Segment` 对象内部是如何完成数据元素的插入操作的。

`Segment` 对象可以简单理解为就是一个 `HashMap` 对象，但是在 JDK 1.7 的实现中，只存在 “数组 + 链表” 的存储方式来存储对应的元素节点。其中，上文 `s.put(...)` 方法对应于 `Segment` 对象中的 `put(K, int, V, boolean)` 方法，具体对应的源代码如下所示：

```java
final V put(K key, int hash, V value, boolean onlyIfAbsent) {
    /*
    	在进行后续的操作之前需要获取当前所在的 segment 持有的独占锁
    */
    HashEntry<K,V> node = tryLock() ? null : scanAndLockForPut(key, hash, value);
    
    V oldValue;
    try {
        HashEntry<K,V>[] tab = table; // segment 对象内部的数组，参考前文对应的结构图
        // 利用 hash 值，找到需要插入的元素数组的下标 index
        int index = (tab.length - 1) & hash;
        HashEntry<K,V> first = entryAt(tab, index); // 获取当前元素链表的头节点
        
        for (HashEntry<K,V> e = first;;) { // 进行元素定位即更新操作
            /*
            	分情况处理当前当前的链表节点
            */
            if (e != null) { // 当前节点所在的数组索引存在链表节点，因此需要进行遍历，将节点插入到尾部
                K k;
                // 根据情况决定是否要覆盖旧的键值对
                if ((k = e.key) == key ||
                    (e.hash == hash && key.equals(k))) { // key 已存在
                    oldValue = e.value;
                    if (!onlyIfAbsent) { // onlyIfAbsent决定是否更新值
                        e.value = value;
                        ++modCount; // 修改次数
                    }
                    break;
                }
                // 继续向后遍历
                e = e.next;
            }
            else {
                /*
                	如果 node  != null，不管它是否是通过并发的方式添加的，现在已经存在链表元素了
                	直接通过“头插法”的方式设置当前节点为链表的头节点即可
                */
                if (node != null)
                    node.setNext(first); // 头插法， node已经在尝试获取锁的时候实例化过了
                else
                    /* 
                    	执行到这里说明当前对应的位置不存在链表元素，因此需要创建一个新的链表元素节点
                    */
                    node = new HashEntry<K,V>(hash, key, value, first); // 头插法， 未实例化过
                
                int c = count + 1;
                
                // 如果当前 Segment 中的元素的数量达到了阈值，那么就需要考虑进行扩容
                if (c > threshold && tab.length < MAXIMUM_CAPACITY)
                    rehash(node); // 扩容， 只对当前segment 扩容， 和 hashmap 扩容 类似
                else
                    /*
                    	由于没有达到阈值，因此只需要将当前位置对应的链表的首节点设置为新插入的头节点即可
                    */
                    setEntryAt(tab, index, node); 
                ++modCount;
                count = c;
                oldValue = null;
                break;
            }
        }
    } finally {
        unlock();
    }
    return oldValue;
}
```

#### 独占锁的获取

在这里重点关注一下对于线程竞争的处理，如果进行当前的 `Segment` 对象的  `put` 操作的线程能够获取到锁 （`tryLock()` 成功）那么就会持有锁进行相应的后续操作。如果没有获取到锁，那么将会由 `scanAndLockForPut(K, int, V)` 方法执行相应的线程同步操作。具体的源代码如下所示：

```java
private HashEntry<K,V> scanAndLockForPut(K key, int hash, V value) {
    HashEntry<K,V> first = entryForHash(this, hash); //定位HashEntry数组位置，获取第一个节点
    HashEntry<K,V> e = first;
    HashEntry<K,V> node = null;
    int retries = -1; // negative while locating node 扫描次数
    
    while (!tryLock()) { // 不断通过tryLock尝试获取锁
        HashEntry<K,V> f; // to recheck first below
        if (retries < 0) {
            if (e == null) {
                if (node == null) // speculatively create node
                    /*
                    	当前元素节点所在位置不存在链表节点，因此需要新建一个
                    */
                    node = new HashEntry<K,V>(hash, key, value, null); 
                retries = 0;
            }
            else if (key.equals(e.key)) // 查到key
                retries = 0;
            else
                e = e.next; // 遍历链表
        }
        /*
        	如果重试次数超过 MAX_SCAN_RETRIES（单核为  多核为 64）依旧没有获取到锁，
        	那么进入到阻塞队列等待锁
        	
        	注意 lock() 方法的调用，前文提到 Segment 继承自 ReentrantLock
        */
        else if (++retries > MAX_SCAN_RETRIES) {
            lock(); // 若还获取不到锁，那么当前线程就被阻塞，这点类似于自旋锁
            break;
        }
        else if ((retries & 1) == 0 && //每间隔一次循环
                 /*
                 	判断是否有其它的线程将元素节点插入到当前的槽位中，如果有其它的线程进行了修改，
                 	那么再走一次 scanAndLockForPut
                 */
                 (f = entryForHash(this, hash)) != first) { // 检查一次first节点是否改变
            e = first = f; // re-traverse if entry changed 首节点有变动，更新first
            retries = -1; // 重新扫描链表
        }
    }
    
    return node;
}
```

#### Segment 的扩容操作

和 `HashMap` 的扩容操作类似，但是有些差别，对应的方法为 `Segment` 的 `rehash(K)` 方法，具体的源代码如下所示：

```java
private void rehash(HashEntry<K,V> node) {
    HashEntry<K,V>[] oldTable = table;
    int oldCapacity = oldTable.length;
    int newCapacity = oldCapacity << 1; // 扩容后默认为原来大小的两倍
    
    threshold = (int)(newCapacity * loadFactor);
    HashEntry<K,V>[] newTable =
        (HashEntry<K,V>[]) new HashEntry[newCapacity];
    
    /*
    	这是一个技术活，由于 Segment 的数组的长度总是 2 的整数次幂，
    	因此得到的掩码会有明显的二进制的划分
    */
    int sizeMask = newCapacity - 1;
    
    /*
    	遍历老数组，将位置为 i 处的链表尽可能均匀地分散到新的创建的元素数组中
    */
    for (int i = 0; i < oldCapacity ; i++) {
        HashEntry<K,V> e = oldTable[i]; // 当前槽位的第一个元素
        // 只有在第一个元素不为 null 的情况下才需要进行元素的再次分配
        if (e != null) {
            HashEntry<K,V> next = e.next;
            /*
            	首先计算当前处理的元素 e 在新数组中的对应位置
            */
            int idx = e.hash & sizeMask;
            
            if (next == null)   //  只有一个元素的话只需处理当前的节点即可
                newTable[idx] = e;
            else { // Reuse consecutive sequence at same slot
                HashEntry<K,V> lastRun = e; // 划分的链表的后半部分的头节点
                int lastIdx = idx; 
                
                /*
                	将当前槽位的链表划分到新创建的数组的不同槽位中
                */
                for (HashEntry<K,V> last = next;
                     last != null;
                     last = last.next) {
                    int k = last.hash & sizeMask;
                    if (k != lastIdx) { // 注意 sizeMask
                        lastIdx = k;
                        lastRun = last;
                    }
                }
                
                newTable[lastIdx] = lastRun;
                // 将  lastRun 节点之间的所有链表元素划分到其它的槽位中
                for (HashEntry<K,V> p = e; p != lastRun; p = p.next) {
                    V v = p.value;
                    int h = p.hash;
                    int k = h & sizeMask;
                    HashEntry<K,V> n = newTable[k];
                    newTable[k] = new HashEntry<K,V>(h, p.key, v, n);
                }
            }
        }
    }
    
    // 将新来的 node 放到新数组中刚刚的 两个链表之一 的 头部
    int nodeIndex = node.hash & sizeMask; // add the new node
    node.setNext(newTable[nodeIndex]);
    newTable[nodeIndex] = node;
    
    table = newTable;
}
```

这里值得一提的是有关划分槽位链表的工作，这项工作是一项十分精细的工作。首先，由于 `Segment` 的元素数组的长度都是 $2$ 的整数次幂，因此每次扩容是一定能够保证每个槽位中链表的元素能够通过掩码进行划分。在 `rehash` 方法中，还使用到了一些其它的技巧（`lastRun` 得到后一部分的链表）。根据统计，使用默认的负载因子，大约只有 $1/6$ 的元素节点需要进行复制

### 获取元素

获取元素对应的是 `ConcurrentHashMap` 的 `get(Object)` 方法。具体有以下几步：

- 计算要获取的 `Key` 的 `hash` 值，首先定位到对应的 `Segment`
- 在 `Segment` 中的元素数组中找到当前 `Key` 对应的元素槽
- 遍历该槽位的链表以查找 `Key` 对应的 `Value`

```java
public V get(Object key) {
    Segment<K,V> s; // manually integrate access methods to reduce overhead
    HashEntry<K,V>[] tab;
    int h = hash(key); // 计算 key 的 hashCode
    
    // 根据 HashCode 定位到 segment
    long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE; 
    if ((s = (Segment<K,V>)UNSAFE.getObjectVolatile(segments, u)) != null &&
        (tab = s.table) != null) {
        
        // 再根据 key 的 hashCode 找到对应槽位的链表，遍历链表以查找对应的 Value
        for (HashEntry<K,V> e = (HashEntry<K,V>) UNSAFE.getObjectVolatile
             (tab, ((long)(((tab.length - 1) & h)) << TSHIFT) + TBASE); // 定位到元素
             e != null; e = e.next) {
            K k;
            if ((k = e.key) == key || (e.hash == h && key.equals(k)))
                return e.value;
        }
    }
    return null;
}
```

## JDK 1.8 版本的实现

与 JDK 1.7 的实现最大的不同在于存储元素的数据结构在超过一定的阈值时可能会发生改变，底层的数据结构由 “数组 + 链表” 转换为 “数组 + 红黑树”。具体的结构示意图如下所示：

![image.png](https://s2.loli.net/2022/02/21/BL8Hk4DK5iXodNc.png)

### 构造函数

主要有以下几个构造函数：

不带参数的构造函数：

```java
public ConcurrentHashMap() {
}
```

带有初始容量的构造函数：

```java
public ConcurrentHashMap(int initialCapacity) {
    if (initialCapacity < 0)
        throw new IllegalArgumentException();
    
    /*
    	将容量设置为 2 的整数次幂
    */
    int cap = ((initialCapacity >= (MAXIMUM_CAPACITY >>> 1)) ?
               MAXIMUM_CAPACITY :
               tableSizeFor(initialCapacity + (initialCapacity >>> 1) + 1));
    this.sizeCtl = cap;
}
```

以及带有负载因子和并发度的构造函数：

```java
public ConcurrentHashMap(int initialCapacity,
                         float loadFactor, int concurrencyLevel) {
    if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0)
        throw new IllegalArgumentException();
    if (initialCapacity < concurrencyLevel)   // Use at least as many bins
        initialCapacity = concurrencyLevel;   // as estimated threads
    long size = (long)(1.0 + (long)initialCapacity / loadFactor);
    int cap = (size >= (long)MAXIMUM_CAPACITY) ?
        MAXIMUM_CAPACITY : tableSizeFor((int)size);
    this.sizeCtl = cap;
}
```

大部分情况下都会使用无參的构造函数。

### 添加元素

对应 `put(K, V)` 方法，源代码如下所示：

```java
public V put(K key, V value) {
    return putVal(key, value, false);
}
```

继续进入 `putVal(K, V, boolean)`，对应的源代码如下所示：

```java
final V putVal(K key, V value, boolean onlyIfAbsent) {
    if (key == null || value == null) throw new NullPointerException(); //1.校验参数是否合法
    // 相当于再一次计算 hashCode
    int hash = spread(key.hashCode());
    
    int binCount = 0; // 用于记录链表的长度
    for (Node<K,V>[] tab = table;;) { //2. 遍历Node
        Node<K,V> f; int n, i, fh;
        /*
        	数组为空，则将当前的数组进行初始化
        */
        if (tab == null || (n = tab.length) == 0)
            tab = initTable();
        /*
        	找到当前 hash 值对应的数组下标，得到第一个节点 f
        */
        else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
            /*
            	CAS对指定位置的节点进行原子操作
            */
            if (casTabAt(tab, i, null,
                         new Node<K,V>(hash, key, value, null))) 
                break;                   // no lock when adding to empty bin
        }
        /*
        	MOVED = -1，如果在扩容的时候可能会出现这种情况
        */
        else if ((fh = f.hash) == MOVED)
            tab = helpTransfer(tab, f);
        else {
            V oldVal = null;
            /*
            	获取数组该位置的头结点的监视器锁
            */
            synchronized (f) {
                if (tabAt(tab, i) == f) {
                    if (fh >= 0) { // 头节点的 hash 值大于 0，说明当前的存储结构为链表
                        binCount = 1; // 该变量用于记录链表的长度
                        
                        // 遍历链表
                        for (Node<K,V> e = f;; ++binCount) {
                            K ek;
                            
                            /*
                            	如果发现了相等的 key，那么进行判断是否需要进行覆盖
                            */
                            if (e.hash == hash &&
                                ((ek = e.key) == key ||
                                 (ek != null && key.equals(ek)))) {
                                oldVal = e.val;
                                if (!onlyIfAbsent)
                                    e.val = value;
                                break;
                            }
                            
                            /*
                            	这里和 JDK 1.7 的插入方式不同，这里使用尾插法的方式插入到链表的末尾
                            */
                            Node<K,V> pred = e;
                            if ((e = e.next) == null) {
                                pred.next = new Node<K,V>(hash, key,
                                                          value, null);
                                break;
                            }
                        }
                    }
                    else if (f instanceof TreeBin) { // TreeBin 对应的存储结构为红黑树
                        Node<K,V> p;
                        binCount = 2;
                        /*
                        	调用红黑树额插入方式插入新的节点
                        */
                        if ((p = ((TreeBin<K,V>)f).putTreeVal(hash, key,
                                                              value)) != null) {
                            oldVal = p.val;
                            if (!onlyIfAbsent)
                                p.val = value;
                        }
                    }
                }
            }
            
            /*
            	这里进行判断，判断当前数组位置的存储结构是否要从链表转换为红黑树
            */
            if (binCount != 0) {
                /*
                	TREEIFY_THRESHOLD = 8，达到这个阈值就进行转换
                */
                if (binCount >= TREEIFY_THRESHOLD)
                    treeifyBin(tab, i);
                if (oldVal != null)
                    return oldVal;
                break;
            }
        }
    }
    addCount(1L, binCount);
    return null;
}
```

#### 初始化数组

在上文见到的源代码中，如果 `table` 为 `null`，或者 `table` 的长度为 $0$，那么需要首先进行数组的初始化，对应 `initTable()` 方法，具体的源代码如下所示：

```java
private final Node<K,V>[] initTable() {
    Node<K,V>[] tab; int sc;
    while ((tab = table) == null || tab.length == 0) {
        if ((sc = sizeCtl) < 0)
            Thread.yield(); // lost initialization race; just spin
        // CAS 一下，将 sizeCtl 设置为 -1，代表抢到了锁
        else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
            try {
                if ((tab = table) == null || tab.length == 0) {
                    /*
                    	DEFAULT_CAPACITY = 16
                    */
                    int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
                    
                    @SuppressWarnings("unchecked")
                    Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                    
                    /*
                    	this.table 被 volatile 修饰，因此对于当前 table 的修改对于其它线程来讲都是可见的
                    */
                    table = tab = nt;
                    sc = n - (n >>> 2);
                }
            } finally {
                /*
                	修改 sizeCtl
                */
                sizeCtl = sc;
            }
            break;
        }
    }
    return tab;
}
```

#### 链表 —> 红黑树

上面 `put` 方法的源代码分析到当 `bintCount` 的数量达到阈值时，会**考虑**将当前的链表转换为对应的红黑树来存储元素节点。具体对应 `treeifBin(Node<K, V>[], int)`，对应的源代码如下所示：

```java
private final void treeifyBin(Node<K,V>[] tab, int index) {
    Node<K,V> b; int n, sc;
    if (tab != null) {
        /*
        	MIN_TREEIFY_CAPACITY = 64，如果当前数组的长度小于 64 时，
        	会优先考虑扩容数组而不是将链表转换为红黑树
        */
        if ((n = tab.length) < MIN_TREEIFY_CAPACITY)
            tryPresize(n << 1);
        else if ((b = tabAt(tab, index)) != null && b.hash >= 0) {
            synchronized (b) {
                if (tabAt(tab, index) == b) {
                    TreeNode<K,V> hd = null, tl = null;
                    
                    /*
                    	遍历链表，建立对应的红黑树
                    */
                    for (Node<K,V> e = b; e != null; e = e.next) {
                        TreeNode<K,V> p =
                            new TreeNode<K,V>(e.hash, e.key, e.val,
                                              null, null);
                        if ((p.prev = tl) == null)
                            hd = p;
                        else
                            tl.next = p;
                        tl = p;
                    }
                    // 将建立好的红黑树放到原来链表的对应位置，替换掉原来的链表
                    setTabAt(tab, index, new TreeBin<K,V>(hd));
                }
            }
        }
    }
}
```



#### 扩容数组

### 获取元素

参考：

<sup>[1]</sup> https://mp.weixin.qq.com/s/EdSEZpKrtPQooaEPKWV5ig