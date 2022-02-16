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
                        treeifyBin(tab, hash);
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
        newCap = oldThr;
    else {               // zero initial threshold signifies using defaults
        newCap = DEFAULT_INITIAL_CAPACITY;
        newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
    }
    if (newThr == 0) {
        float ft = (float)newCap * loadFactor;
        newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                  (int)ft : Integer.MAX_VALUE);
    }
    threshold = newThr;
    @SuppressWarnings({"rawtypes","unchecked"})
    Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];
    table = newTab;
    if (oldTab != null) {
        for (int j = 0; j < oldCap; ++j) {
            Node<K,V> e;
            if ((e = oldTab[j]) != null) {
                oldTab[j] = null;
                if (e.next == null)
                    newTab[e.hash & (newCap - 1)] = e;
                else if (e instanceof TreeNode)
                    ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                else { // preserve order
                    Node<K,V> loHead = null, loTail = null;
                    Node<K,V> hiHead = null, hiTail = null;
                    Node<K,V> next;
                    do {
                        next = e.next;
                        if ((e.hash & oldCap) == 0) {
                            if (loTail == null)
                                loHead = e;
                            else
                                loTail.next = e;
                            loTail = e;
                        }
                        else {
                            if (hiTail == null)
                                hiHead = e;
                            else
                                hiTail.next = e;
                            hiTail = e;
                        }
                    } while ((e = next) != null);
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

### 查找键值对

<br />

### 删除键值对

<br />

## `TreeMap`