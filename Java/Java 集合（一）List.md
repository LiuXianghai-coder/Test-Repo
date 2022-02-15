# Java 集合（一）List

在 Java 中，主要存在以下三种类型的集合：Set、List 和 Map，按照更加粗略的划分，可以分为：Collection 和 Map，这些类型的继承关系如下图所示：

![image.png](https://s2.loli.net/2022/02/15/K82pzXAQcMIlSdZ.png)

- `Collection` 是集合 List、Set、Queue 等最基本的接口
- `Iterator` 即迭代器，可以通过迭代器遍历集合中的数据
- `Map` 是映射关系的基本接口

本文将主要介绍有关 `List` 集合的相关内容，`ArrayList` 和 `LinkedList` 是在实际使用中最常使用的两种 `List`，因此主要介绍这两种类型的 `List`

本文的 JDK 版本为 JDK 1.8

<br />

## ArrayList

具体的使用可以参考 `List` 接口的相关文档，在此不做过多的介绍

 <br />

### 初始化

`ArrayList` 存在三个构造函数，用于初始化 `ArrayList`

- `ArrayList()`

    对应的源代码如下所示：

    ```java
    // 默认的空元素列表
    private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};
    
    // 实际存储元素的数组
    transient Object[] elementData; 
    
    public ArrayList() {
        // 调用次无参构造函数时，首先将元素列表指向空的列表，在之后的扩容操作中再进行进一步的替换
        this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
    }
    ```

- `ArrayList(int)`

    对应的源代码如下所示：

    ```java
    private static final Object[] EMPTY_ELEMENTDATA = {};
    
    public ArrayList(int initialCapacity) {
        if (initialCapacity > 0) {
            this.elementData = new Object[initialCapacity];
        } else if (initialCapacity == 0) {
            this.elementData = EMPTY_ELEMENTDATA;
        } else {
            throw new IllegalArgumentException("Illegal Capacity: "+
                                               initialCapacity);
        }
    }
    ```

- `ArrayList(Collection<? extend E>)`

    对应的源代码：

    ```java
    public ArrayList(Collection<? extends E> c) {
        Object[] a = c.toArray();
        if ((size = a.length) != 0) {
            if (c.getClass() == ArrayList.class) {
                /* 
                	如果添加的集合和 ArrayList 相同，那么直接修改当前的数据列表的引用
                	因此在某些情况下对于该数组的修改会出现一些奇怪的问题，
                	因为对于当前数组元素的修改也会导致原数组元素的改变
                */
                elementData = a;
            } else {
                /*
                	想比较之下，复制一个数组会更加安全，缺点在于执行速度不是那么的好
                */
                elementData = Arrays.copyOf(a, size, Object[].class);
            }
        } else {
            // 如果集合中元素的个数为 0，那么替换为空数组
            elementData = EMPTY_ELEMENTDATA;
        }
    }
    ```

<br />

### 添加元素

添加元素是比较重要的部分，特别是 `ArrayList` 的自动扩容机制

添加一个元素的源代码如下所示：

```java
public boolean add(E e) {
    modCount++; // 记录当前的数组的修改次数
    add(e, elementData, size);
    return true;
}
```

添加时调用重载函数 `add(E, Object[], int)`，对应的源代码如下所示：

```java
private void add(E e, Object[] elementData, int s) {
    if (s == elementData.length)
        elementData = grow(); // 比较关键的地方在这，在这里完成自动扩容
    elementData[s] = e;
    size = s + 1;
}
```

<br />

#### 扩容

`grow()` 扩容的实现的源代码如下所示：

```java
private Object[] grow() {
    return grow(size + 1);
}

private Object[] grow(int minCapacity) {
    int oldCapacity = elementData.length;
    if (oldCapacity > 0 || elementData != DEFAULTCAPACITY_EMPTY_ELEMENTDATA) { // 判断当前爱你的数组元素是否被修改过
        // 首先，计算扩容后的数组的大小
        int newCapacity = ArraysSupport.newLength(oldCapacity,
                                                  minCapacity - oldCapacity, /* minimum growth */
                                                  oldCapacity >> 1           /* preferred growth */);
        // 然后将创建原有的数据元素数组的副本对象，再将数据填入到副本数组中，完成扩容操作
        return elementData = Arrays.copyOf(elementData, newCapacity);
    } else {
        /* 
        	由于没有被修改过，那么直接扩容到目标大小即可，
        	DEFAULT_CAPACITY=10，这是为了提供一个最小的初始容量，以免扩容机制过于频繁造成的性能损失
         */
        return elementData = new Object[Math.max(DEFAULT_CAPACITY, minCapacity)];
    }
}
```

扩容时关键的是在于新的容量的计算，具体的源代码如下所示：

```java
// jdk.internal.util.ArraysSupport
public static int newLength(int oldLength, int minGrowth, int prefGrowth) {
    /* 
    	参照上文中 grow 中传入的参数，最小大小扩容到原来数组大小的 1.5 倍，
    	如果 minGrwoth 大于 oldCapacity，则以 minGrowth 为准
    */
    int prefLength = oldLength + Math.max(minGrowth, prefGrowth); // might overflow
    // 加法操作可能会导致整形变量溢出
    if (0 < prefLength && prefLength <= SOFT_MAX_ARRAY_LENGTH) {
        return prefLength;
    } else {
        // 需要另一种计算扩容大小的方式，默认的扩容方式无法处理
        return hugeLength(oldLength, minGrowth);
    }
}

public static final int SOFT_MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

private static int hugeLength(int oldLength, int minGrowth) {
    int minLength = oldLength + minGrowth;
    if (minLength < 0) { // overflow
        throw new OutOfMemoryError(
            "Required array length " + oldLength + " + " + minGrowth + " is too large");
    } else if (minLength <= SOFT_MAX_ARRAY_LENGTH) {
        return SOFT_MAX_ARRAY_LENGTH;
    } else {
        return minLength;
    }
}
```



<br />

### 移除元素



<br />

## LinkedList