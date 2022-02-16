# Java 集合（二） Map

Map 定义的是键值对的映射关系，一般情况下，都会选择 `HashMap` 作为具体的实现，除了 `HashMap` 之外，另一个使用到的比较多的 `Map` 实现是 `TreeMap`

<br />

## `HashMap`

### 构造函数

`HashMap` 存在四个构造函数，对应的源代码如下所示：

```java
// 设置初始容量和装载因子
public HashMap(int initialCapacity, float loadFactor) {
    if (initialCapacity < 0)
        throw new IllegalArgumentException("Illegal initial capacity: " +
                                           initialCapacity);
    if (initialCapacity > MAXIMUM_CAPACITY)
        initialCapacity = MAXIMUM_CAPACITY;
    if (loadFactor <= 0 || Float.isNaN(loadFactor))
        throw new IllegalArgumentException("Illegal load factor: " +
                                           loadFactor);
    this.loadFactor = loadFactor;
    
    //  tableSizeFor 方法的目的是找到大于等于 initialCapacity 的 2 的整数次幂 
    this.threshold = tableSizeFor(initialCapacity);
}

// 设置初始容量，以及默认的装载因子
public HashMap(int initialCapacity) {
    this(initialCapacity, DEFAULT_LOAD_FACTOR);
}

// 使用频率较高的构造函数，仅仅只是设置装载因此
public HashMap() {
    this.loadFactor = DEFAULT_LOAD_FACTOR; // all other fields defaulted
}

// 从一个 Map 中拷贝一个 Map，这个构造函数不常用
public HashMap(Map<? extends K, ? extends V> m) {
    this.loadFactor = DEFAULT_LOAD_FACTOR;
    putMapEntries(m, false);
}
```

首先，介绍一下一个 `HashMap` 具有的状态量：

```java
// 实际的存储表
transient Node<K,V>[] table;

// 此 HashMap 中包含的键值对节点集合
transient Set<Map.Entry<K,V>> entrySet;

// 当前 HashMap 中的键值对数量
transient int size;

// 该 HashMap 被修改的次数，主要是用于 CAS 等并发场景
transient int modCount;

// 当当前 HashMap 中键值对的数量超过这个值时，将会触发扩容操作
int threshold;

/* 
	负载因子，该字段存在的主要目的是
	确保 put、get 方法的操作的时间复杂度都控制在 O(1) 
*/
final float loadFactor;
```

其次，在 `HashMap` 中定义的一些静态变量：

```java
// 默认的初始容量
static final int DEFAULT_INITIAL_CAPACITY = 1 << 4;

//  HashMap 的最大容量
static final int MAXIMUM_CAPACITY = 1 << 30;

// 默认的装载因子
static final float DEFAULT_LOAD_FACTOR = 0.75f;

// 当表上对应的 List 的元素个数达到这个阈值是，将使用树的结构来替换链式结构
static final int TREEIFY_THRESHOLD = 8;

// 当树中的元素个数小于这个阈值时，转换为使用链式结构来存储
static final int UNTREEIFY_THRESHOLD = 6;

// 当前 HashMap 中总的元素个数大于等于这个阈值时才能转换成以树结构存储
static final int MIN_TREEIFY_CAPACITY = 64;
```

<br />

### 键值对节点的定义

如果是通过链式的方式存储在表中，那么节点的定义如下所示：

```java
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;
    final K key;
    V value;
    Node<K,V> next;

    Node(int hash, K key, V value, Node<K,V> next) {
        this.hash = hash;
        this.key = key;
        this.value = value;
        this.next = next;
    }
    
    // 省略部分 Object 方法和 getter 以及 setter 方法
}
```

如果是通过红黑树的方式存储，那么节点的定义如下所示：

```java
static final class TreeNode<K,V> extends LinkedHashMap.Entry<K,V> {
    TreeNode<K,V> parent;  // red-black tree links
    TreeNode<K,V> left;
    TreeNode<K,V> right;
    TreeNode<K,V> prev;    // needed to unlink next upon deletion
    boolean red;
    TreeNode(int hash, K key, V val, Node<K,V> next) {
        super(hash, key, val, next);
    }
    
    // 省略部分其它的代码
}
```

<br />

### 添加键值对

对应的 `put` 方法的源代码如下所示：

```java
public V put(K key, V value) {
    return putVal(hash(key), key, value, false, true);
}
```

继续进入 `putVal` 方法，对应的源代码如下所示：

```java
final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
               boolean evict) {
    Node<K,V>[] tab; Node<K,V> p; int n, i;
    // 初始化桶数组 table，HashMap 将 table 的初始化放到这里进行
    if ((tab = table) == null || (n = tab.length) == 0)
        n = (tab = resize()).length;
    // 如果对应的桶中不包含任何节点，那么直接放入这个桶即可
    if ((p = tab[i = (n - 1) & hash]) == null)
        tab[i] = newNode(hash, key, value, null);
    else {
        Node<K,V> e; K k;
        /*
        	如果插入的位置已经存在键值对了，那么判断第一个键值对
        	是否是需要被覆盖的键值对节点，然后再考虑进行覆盖
        */
        if (p.hash == hash &&
            ((k = p.key) == key || (key != null && key.equals(k))))
            e = p;
        /*
        	如果对应桶中的第一个元素的类型为 TreeNode，则说明
        	在这个桶中键值对的存储形式为红黑树，直接调用红黑树的插入方法即可
        */
        else if (p instanceof TreeNode)
            e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
        else {
            // 遍历链表，同时统计此时的链表长度
            for (int binCount = 0; ; ++binCount) {
                /* 
                	如果链表中不包含要覆盖的键值对元素，
                	那么首先将插入的键值对节点放到当前桶对应的链表的末尾
                */
                if ((e = p.next) == null) {
                    p.next = newNode(hash, key, value, null);
                    /* 
                    	如果插入该节点之后该桶对应的元素的数量达到了树化的阈值，
                    	那么将当前桶对应的链表转换为对应的红黑树来存储
                    */
                    if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                        treeifyBin(tab, hash); // 树化操作
                    break;
                }
                // 如果此时该桶对应的链表中存在对应的键值对节点，则直接覆盖
                if (e.hash == hash &&
                    ((k = e.key) == key || (key != null && key.equals(k))))
                    break;
                p = e;
            }
        }
        
        /* 
        	判断插入的简直对的键是否存在于当前 HashMap 中
        */
        if (e != null) {
            V oldValue = e.value;
            // onlyIfAbsent 表示仅在 oldValue 为 null 的情况下更新键值对的值
            if (!onlyIfAbsent || oldValue == null)
                e.value = value;
            afterNodeAccess(e); // 后置处理钩子方法
            return oldValue;
        }
    }
    ++modCount; // 记录修改次数
    // 如果此时总的元素的个数超过了阈值，那么需要进行扩容操作
    if (++size > threshold)
        resize();
    afterNodeInsertion(evict); // 后置处理的钩子方法
    return null;
}
```

<br />

#### 扩容机制

上文中在添加一个键值对元素时，对应的扩容的方法为 `resize()`，具体对应的源代码如下所示：

```java
// Hint：HashMap 的构造函数会确保初始容量为 0 或 2 的整数次幂
final Node<K,V>[] resize() {
    Node<K,V>[] oldTab = table;
    int oldCap = (oldTab == null) ? 0 : oldTab.length;
    int oldThr = threshold;
    int newCap, newThr = 0;
    
    // 如果旧容量 > 0，说明表已经被初始化过了
    if (oldCap > 0) {
        // 如果旧有容量已经达到了最大值，那么将不再进行扩容
        if (oldCap >= MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return oldTab;
        }
        /* 
        	默认将原有容量扩容到原来的 2 倍，如果扩容后依旧没有大于最大容量的话，
        	那么也会将阈值扩大到原来的两倍
        */
        else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                 oldCap >= DEFAULT_INITIAL_CAPACITY)
            newThr = oldThr << 1; // double threshold
    }
    
    else if (oldThr > 0) // initial capacity was placed in threshold
        /*
        	如果旧的 table 数组的阈值 > 0 但是数组容量不大于 0
        	这种情况对应通过传入 initCapaticy 的构造函数的实例化，这种情况会
        	初始化 HashMap 的阈值，但是实际数组的实例化是放到 put 方法中来实现的
        */
        newCap = oldThr;
    else {               // zero initial threshold signifies using defaults
        // 走到这说明需要执行默认的实例化数组
        newCap = DEFAULT_INITIAL_CAPACITY; // 1 << 4，即 16
        newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY); // 0.75 * (1 << 4)
    }
    
    /*
    	如果不是走的默认实例化数组的分支，那么新数组的阈值可能没有被计算
    	因此在这里需要进一步地判断同时计算新实例化的数组的阈值
    */
    if (newThr == 0) {
        float ft = (float)newCap * loadFactor;
        newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                  (int)ft : Integer.MAX_VALUE);
    }
    
    threshold = newThr; // 更新当前 HashMap 中的阈值属性
    
    @SuppressWarnings({"rawtypes","unchecked"})
    Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap]; // 创建新的桶数组
    table = newTab; // 修改当前 HashMap 的桶数组属性
    
    // 由于桶数组被更新了，需要把原来桶数组中的元素节点复制到新创建的桶数组中
    if (oldTab != null) {
        // 遍历原有的桶数组中的每个桶元素
        for (int j = 0; j < oldCap; ++j) {
            Node<K,V> e;
            // 如果桶中存在元素，那么就将它复制到新创建的桶数组中
            if ((e = oldTab[j]) != null) {
                oldTab[j] = null;
                if (e.next == null)
                    newTab[e.hash & (newCap - 1)] = e;
                else if (e instanceof TreeNode)
                    /* 
                    	如果当前桶中的元素类型为红黑树，那么在复制时需要对红黑树中
                    	的节点进行拆分，并尽可能均匀地分散到每个桶中
                    */
                    ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                else { // preserve order
                    Node<K,V> loHead = null, loTail = null;
                    Node<K,V> hiHead = null, hiTail = null;
                    Node<K,V> next;
                    do {
                        /*
                        	由于 HashMap 保证桶数组的容量是 2 的整数次幂，
                        	因此，可以针对旧有的桶数组容量进行划分（按位与），尽可能均匀地
                        	将当前桶数组中的元素分散到新创建的桶数组中
                        */
                        next = e.next;
                        // 如果当前元素和 oldCap & == 0，表示它充分分散后要到低索引区域
                        if ((e.hash & oldCap) == 0) {
                            if (loTail == null)
                                loHead = e;
                            else
                                loTail.next = e;
                            loTail = e;
                        }
                        else { // 否则这个元素应该到高索引的桶元素位置
                            if (hiTail == null)
                                hiHead = e;
                            else
                                hiTail.next = e;
                            hiTail = e;
                        }
                    } while ((e = next) != null);
                    
                    // 将划分后的链表重新放入到新创建的桶数组中
                    if (loTail != null) {
                        loTail.next = null;
                        newTab[j] = loHead;
                    }
                    if (hiTail != null) {
                        hiTail.next = null;
                        newTab[j + oldCap] = hiHead;
                    }
                }
            }
        }
    }
    
    return newTab;
}
```

<br />

#### 链表的树化

前文提到过，当桶中元素的数量达到 `TREEIFY_THRESHOLD` 阈值时，该桶中存储元素的数据结构将从链表转换为红黑树，具体对应 `treeifyBin(Node<K, V>[], int)` 方法，其中，源代码如下所示：

```java
final void treeifyBin(Node<K,V>[] tab, int hash) {
    int n, index; Node<K,V> e;
    /*
    	当桶数组的长度没有达到最小树化的大小时，会首先考虑扩容而不是直接树化
    */
    if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY)
        resize();
    // 如果对应的桶中不包含元素，那么也不需要进行树化的操作
    else if ((e = tab[index = (n - 1) & hash]) != null) {
        TreeNode<K,V> hd = null, tl = null;
        
        // 遍历当前桶中的链表，将链表元素节点转换为对应的红黑树元素节点
        do {
            TreeNode<K,V> p = replacementTreeNode(e, null);
            if (tl == null)
                hd = p;
            else {
                p.prev = tl;
                tl.next = p;
            }
            tl = p;
        } while ((e = e.next) != null);
        
        /*
        	上面的操作只是完成的节点类型的转换，存储元素的底层数据结构依旧是链表
        	在这里需要将存储元素的数据结构从链表转换为红黑树
        */
        if ((tab[index] = hd) != null)
            hd.treeify(tab);
    }
}
```

有关红黑树的内容可以参考：<a href="https://algs4.cs.princeton.edu/home/">《算法（第四版）》</a> “查找”章节中有关红黑树的部分

<br />

这里还有一个值得注意的地方在于红黑树的实现需要节点之间能够进行比较，但是 `HashMap` 在设计之初并没有考虑到这一层，因此，为了能够实现节点之间的比较，`HashMap` 中 `TreeNode`节点之间的比较将会按照下面的顺序进行比较：

1. 首先比较两个 `TreeNode<K, V>` 中 `K` 的 hash 值，如果两者相等则转到第二步
2. 检查 `K` 是否实现了 `java.lang.Comparable<T>` 接口，如果实现了该接口，则调用两个 `K` 的 `compareTo(T)` 方法进行比较
3. 如果通过第二步依旧无法比较出两个节点的大小，则调用 `tieBreakOrder(Object, Object)`（加时赛）方法继续进行比较

以上的比较步骤对应于 `treeify(Node<K, V>[] tab)`，相关的源代码如下所示：

```java
for (TreeNode<K,V> p = root;;) {
    int dir, ph;
    K pk = p.key;
    /*
    	比较两个 TreeNode 的逻辑部分
    */
    if ((ph = p.hash) > h)
        dir = -1;
    else if (ph < h)
        dir = 1;
    else if ((kc == null &&
              (kc = comparableClassFor(k)) == null) ||
             (dir = compareComparables(kc, k, pk)) == 0)
        dir = tieBreakOrder(k, pk); // 这里最终会调用 native 方法进行 hash 值的比较
}
```

<br />

#### 红黑树的拆分

在前文“扩容机制”部分提到过，在将原桶数组元素复制到新桶数组元素的过程中时，如果桶中的元素类型是 `TreeNode`，那么就会将该桶内的红黑树进行分裂。

和链表转红黑树不同，在上文 “链表转红黑树”的这个过程中，转换完成之后每个 `TreeNode` 节点依旧保留着原来链式结构的引用，因此只需要遍历一次节点即可

红黑树的拆分对应 `split(HashMap<K, V>, Node<K, V>[], int, int)`，对应的源代码如下所示：

```java
// 该方法为 TreeNode 节点对象的方法
final void split(HashMap<K,V> map, Node<K,V>[] tab, int index, int bit) {
    TreeNode<K,V> b = this;
    // Relink into lo and hi lists, preserving order
    TreeNode<K,V> loHead = null, loTail = null;
    TreeNode<K,V> hiHead = null, hiTail = null;
    int lc = 0, hc = 0; // 统计分裂后两个链表的节点数
    
    /*
    	注意，TreeNode 节点是保留有 next 节点和 prev 节点的，
    	因此依旧可以当做链表来进行遍历
    */
    for (TreeNode<K,V> e = b, next; e != null; e = next) {
        next = (TreeNode<K,V>)e.next;
        e.next = null;
        /* 
        	和拆分链表节点类似，按照指定的 bit 位来进行划分，
        	将会得到拆分后的两个链表
        */
        if ((e.hash & bit) == 0) {
            if ((e.prev = loTail) == null)
                loHead = e;
            else
                loTail.next = e;
            loTail = e;
            ++lc;
        }
        else {
            if ((e.prev = hiTail) == null)
                hiHead = e;
            else
                hiTail.next = e;
            hiTail = e;
            ++hc;
        }
    }

    if (loHead != null) {
        /* 
        	如果此时分裂后的链表长度达到了非树化的阈值（6），那么将其转换为链表的存储结构
        */
        if (lc <= UNTREEIFY_THRESHOLD)
            tab[index] = loHead.untreeify(map);
        else {
            // 否则的话，依旧保留当前桶内元素以红黑树的数据结构进行存储
            tab[index] = loHead;
            /*
            	hiHead == null 说明当前桶内对应的所有元素经过分裂之后
            	依旧在同一个桶内，而此时的数据结构依旧是红黑树的存储方式，没有发生变化
            	
            	因此只有当 hiHead 存在元素时，才需要再次进行树化的操作
            */
            if (hiHead != null) // (else is already treeified)
                loHead.treeify(tab);
        }
    }
    
    // 和上面的 loHead 链表转换一致
    if (hiHead != null) {
        if (hc <= UNTREEIFY_THRESHOLD)
            tab[index + bit] = hiHead.untreeify(map);
        else {
            tab[index + bit] = hiHead;
            if (loHead != null)
                hiHead.treeify(tab);
        }
    }
}
```

在进行分裂时，如果满足“非树化”的要求，那么还会进一步将每个 `TreeNode` 转换为 `Node`，对应的源代码如下所示：

```java
final Node<K,V> untreeify(HashMap<K,V> map) {
    Node<K,V> hd = null, tl = null;
    for (Node<K,V> q = this; q != null; q = q.next) {
        Node<K,V> p = map.replacementNode(q, null); // 将 TreeNode 转换为 Node
        if (tl == null)
            hd = p;
        else
            tl.next = p;
        tl = p;
    }
    return hd;
}
```

<br />

### 查找键值对

这个方法比较简单，首先对获取要查找的节点的 `Key` 的 `hashCode`，然后定位到对应的桶元素，在桶元素上进行查找即可，对应的源代码如下所示：

```java
public V get(Object key) {
    Node<K,V> e;
    // 首先计算出 key 的 hashCode，定位到桶元素，然后再桶内元素集合中进行查找即可
    return (e = getNode(hash(key), key)) == null ? null : e.value;
}

final Node<K,V> getNode(int hash, Object key) {
    Node<K,V>[] tab; Node<K,V> first, e; int n; K k;
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (first = tab[(n - 1) & hash]) != null) {
        // 首先检查第一个元素是否是需要查找的元素
        if (first.hash == hash && // always check first node
            ((k = first.key) == key || (key != null && key.equals(k))))
            return first;
        
        if ((e = first.next) != null) {
            /*
            	如果这个桶内的元素节点类型为 TreeNode，那么按照红黑树的查找方式进行查找
            */
            if (first instanceof TreeNode)
                return ((TreeNode<K,V>)first).getTreeNode(hash, key);
            
            // 否则的话遍历当前元素所在的链表，直到找到需要的元素
            do {
                if (e.hash == hash &&
                    ((k = e.key) == key || (key != null && key.equals(k))))
                    return e;
            } while ((e = e.next) != null);
        }
    }
    
    return null;
}
```

<br />

### 删除键值对

红黑树的删除操作本身是一个比较复杂的操作，但是如果排除红黑树的删除操作，那么 `HashMap` 的删除操作是比较简单的。

对应 `HashMap` 的 `remove(Object)` 方法，具体的源代码如下所示：

```java
public V remove(Object key) {
    Node<K,V> e;
    // 首先，定位到节点的 key 所在的桶，然后继续在桶内的元素集合中实现删除操作
    return (e = removeNode(hash(key), key, null, false, true)) == null ?
        null : e.value;
}

final Node<K,V> removeNode(int hash, Object key, Object value,
                           boolean matchValue, boolean movable) {
    Node<K,V>[] tab; Node<K,V> p; int n, index;
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (p = tab[index = (n - 1) & hash]) != null) {
        Node<K,V> node = null, e; K k; V v;
        /*
        	如果桶内元素的第一个节点和要查找的 key 相等，那么将 node 指向该节点
        */
        if (p.hash == hash &&
            ((k = p.key) == key || (key != null && key.equals(k))))
            node = p;
        // 否则的话需要遍历链表或者通过红黑树的查找方法进行 Key 的查找
        else if ((e = p.next) != null) {
            if (p instanceof TreeNode)
                // 红黑树的查找操作
                node = ((TreeNode<K,V>)p).getTreeNode(hash, key);
            else {
                // 遍历当前桶对应的链表，查找对应的 key
                do {
                    if (e.hash == hash &&
                        ((k = e.key) == key ||
                         (key != null && key.equals(k)))) {
                        node = e;
                        break;
                    }
                    p = e;
                } while ((e = e.next) != null);
            }
        }
        
        /*
        	node != null 说明存在和 key 对应的元素节点，分不同的情况进行删除即可
        */
        if (node != null && (!matchValue || (v = node.value) == value ||
                             (value != null && value.equals(v)))) {
            if (node instanceof TreeNode)
                // 如果是红黑树的话，调用红黑树的删除方法即可
                ((TreeNode<K,V>)node).removeTreeNode(this, tab, movable);
            else if (node == p)
                /*
                	走到这里的话，说明当前桶内存储元素节点的数据结构为链表，
                	如果要删除的节点为首节点，直接移动首节点即可
                */
                tab[index] = node.next;
            else
                // 否则需要调整前一个节点的后继链接，以达到删除的目的
                p.next = node.next;
            ++modCount;
            --size;
            afterNodeRemoval(node); // 在移除节点之后的钩子方法
            return node;
        }
    }
    return null;
}
```

<br />

### 序列化

实际上，`HashMap` 中的几个状态变量都是通过 `transient` 来修饰的，如下所示：

```java
transient Node<K,V>[] table;
transient Set<Map.Entry<K,V>> entrySet;
transient int size;
```

使用 `treansient` 的目的是为了避免该字段被序列化，但是 `HashMap` 是实现了 `Serializable` 接口的，因此能够实现序列化

`HashMap` 通过重写 `readObject(ObjectInputStream s)` 和 `writeObject(ObjectOutputStream s)` 方法来自定义序列化的实现，之所以这么做的原因，大致有两个：

1. `HashMap` 为了符合 `Map` 接口要求的时间复杂度，因此实际上一个 `table` 中有接近一半的空间是没有被使用的，为了节约空间因此需要自定义序列化的操作
2. 由于 JVM 只是一个规范，因此在不同的 JVM 实现中，对同一个对象计算 `hashCode` 得到的值可能不一样，因此保留这些元素的状态在序列化时是没有意义的 

<br />

## `TreeMap`

`java.util.TreeMap`  也是在实际使用过程中使用的比较多的 `Map` 具体实现类，`TreeMap` 不仅实现了 `java.util.Map` 接口，同时还实现了 `java.util.NavigableMap` 接口从而使得在迭代元素时得到的元素节点是有序的，同时具备搜索特定目标元素节点的相关方法，如 `lowerEntry`、`floorEntry` 等方法

和 `HashMap` 的实现不同，`HashMap` 很大程度上依赖具体元素节点对象的 `hashCode` 和 `equals` 方法才能正常工作。 `TreeMap` 则要求键值对元素节点的 `Key` 必须实现 `java.lang.Comparable` 接口就能够正常工作。

由于 `TreeMap` 是基于红黑树的数据结构来存储相关的键值对元素，因此对于元素节点的插入、删除以及查找等操作的时间复杂度都为 $O(log_2N)$ ，性能略差于 `HashMap`

`TreeMap` 的底层基于红黑树，因此在本文不会做详细的介绍，如果想要了解红黑树，《算法（第四版）》会是一个相当好的选择。我也在此推荐一下我写的关于红黑树的博客：https://www.cnblogs.com/FatalFlower/p/15334566.html

<br />

## 两者的比较

一般情况下，使用 `HashMap` 的情况会比较多，因为 `HashMap` 能够保证操作的时间复杂度都控制在 $O(1)$ ，但是这里有一个前提条件：对象的 `equals` 方法和 `hashCode` 方法必须是有效的，如果你重写了一个值对象的 `equals` 方法，那么必须重写该对象的 `hashCode` 方法，同时保证两者是对应的，这样 `HashMap` 才能正常工作，这有时可能是放弃使用 `HashMap` 的一个原因

`TreeMap` 基于红黑树的数据结构，因此要求节点对象必须实现 `java.lang.Comprable` 接口，或者在构造 `TreeMap` 时传入对应的 `Comparator` 对象以实现节点之间的比较。尽管 `TreeMap` 的每项操作的时间复杂度都是 $O(long_2N)$，但是如果你希望拥有一个有顺序的 `Map`，那么 `TreeMap` 绝对是一个不二之选

<br />

参考：

<sup>[1]</sup> https://segmentfault.com/a/1190000012926722