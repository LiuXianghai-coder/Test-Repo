# `Redis` 数据结构

> `Redis`： Remote Dictionary Service（远程字典服务）。主要存储键值对类型的数据，对于键(`key`) 来讲，只能是 `String` 类型的，而对于 值(`value`) 来讲，可以是其它的数据类型。
>
> <img src="https://i.loli.net/2021/09/26/IQraABP7niphvX9.png" alt="1.png" style="zoom:50%;" />



## 基本数据结构

`Redis` 中存在五种常见的数据结构，分别是 `String（字符串）`、`List（列表）`、`Hash（哈希）`、`Set（集合）`、`ZSet（有序集合）`



### `Redis` 对象

一个 `Redis`  对象由以下字段组成：

```c
// server.h —— redis.h

typedef struct redisObject {                                                                                                    
    // 对象的类型，取值范围：REDIS_STRING、REDIS_LIST、REDIS_HASH、REDIS_SET、REDIS_ZSET
    unsigned type:4;
    
    // 对象的编码，取值范围：REDIS_ENCODING_INT、REDIS_ENCODING_EMBSTR、REDIS_ENCODING_RAW、REDIS_ENCODING_HT、REDIS_ENCODING_LINKEDLIST、REDIS_ENCODING_ZIPLIST、REDIS_ENCODING_INTSET、REDIS_ENCODING_SKIPLIST
    unsigned encoding:4; // 每种 type 对应着两个或两个以上的编码
    
    unsigned lru:LRU_BITS; /* LRU time (relative to global lru_clock) or
                              * LFU data (least significant 8 bits frequency
                              * and most significant 16 bits access time). */
    int refcount;
    
    void *ptr;
 } robj;
```

​	基本数据类型就是上文提到的几种数据类型，而这些数据类型之间的编码方式有所不同。这是因为为了适应不同的需求，每个基本数据类型都会有不同的实现方式（可以类比为面向对象设计中的 `接口` 和 `实现类` 的关系）

​	基本类型与编码的对应关系如下所示：

![1.png](https://i.loli.net/2021/09/26/KrnUyAF6SmcfxCo.png)

### 字符串

字符串对应的 `Redis` 数据类型是 `REDIS_STRING`，具体的编码有 `REDIS_ENCODIGN_INT`、`REDIS_ENCODIGN_EMBSTR`、`REDIS_ENCODIGN_RAW`

- `REDIS_ENCODIGN_INT`

  - 对应 `long` 类型的整数，当输入的字符串为数字并且在 `long` 类型表示的数字的范围内时，`String` 回使用这种编码格式。

  - 对象此时的具体内容如下图所示：

  <img src="https://i.loli.net/2021/09/26/P1blS52nyrYaAIp.png" alt="image.png" style="zoom:60%;" />

- `REDIS_ENCODIGN_EMBSTR`

  - 当字符串的长度小于某个阈值时，会采用 `embstr` 的编码方式进行编码。这是因为 `embstr` 的编码方式只需要分配一次 `sdshdr` 的空间，因此速度更快。

  - `embstr` 编码的方式是不可变的，因此如果对 `embstr` 编码的字符串进行修改，会首先讲字符串变为 `raw` 的编码方式再进行修改

  - `embstr` 编码的对象的内容存储表示：

    <img src="https://i.loli.net/2021/09/26/Mi6ESIFHw3xpQAb.png" alt="image.png" style="zoom:60%;" />

- `REDIS_ENCODIGN_RAW`

  - 除了上述的两种编码格式之外，其它的 `String` 都是使用 `raw` 的方式进行编码的

  - 具体的对象的存储内容如下图所示

    <img src="https://i.loli.net/2021/09/26/UueYsdPf9z8Qbvr.png" alt="image.png" style="zoom:60%;" />

- `SDS`

  > `SDS`：Simple Dynamic String （简单动态字符串），由 `Redis` 定义的字符串类型。
  >
  > 与 C 的字符串相比，有以下优势：
  >
  > 1. 能够提高访问速度
  >
  >    - C 语言计算字符串时需要再次遍历整个字符串，因此会浪费许多的时间
  >
  > 2. 能够避免缓存区溢出
  >
  >    - C 语言字符串的操作存在缓存区溢出的问题，前提是在增加元素之前没有足够的内存分配。
  >    - `SDS` 通过动态地执行空间地扩充，API 会自动进行空间地扩展
  >
  > 3. 通过预分配内存空间和惰性释放的方式来提高内存的分配速度
  >
  >    - 空间预分配
  >
  >      对 `SDS` 进行修改之后，如果 `SDS` 的长度小于某个阈值，这个时候会分配 `len = free`
  >
  >      对 `SDS` 进行修改之后，如果 `SDS` 的长度大于阈值，这个时候会按照一定的固定大小进行分配
  >
  >    - 惰性释放
  >
  >      再删除一段内容后，当前的存储空间不会立刻释放，从而减少了再次分配空间的时间
  >
  > 4. C 语言保存的数据内容有限；而 `SDS` 可以保存任意类型的内容



### 列表(`List`)

​	列表的编码方式有 `REDIS_ENCODING_ZIPLIST`、`REDIS_ENCODING_LINKEDLIST`

​	特点：有序、可重复、插入和删除的速度较快

- `REDIS_ENCODING_ZIPLIST` （压缩链表）<a name="REDIS_ENCODING_ZIPLIST"></a>

  - 存储结构：

  <img src="https://i.loli.net/2021/09/27/HvOzpuBJhKDoA1G.png" alt="1.png" style="zoom:60%;" />

  `zlbytes`：表示压缩链表的总字节数

  `zltail`：表示距离尾部节点的偏移量

  `zlend`：表示这个压缩链表的长度

  

- `REDIS_ENCODING_LINKEDLIST` （双向链表）<a name="REDIS_ENCODING_LINKEDLIST"></a>

  - 一般意义上的双向链表

  - 具体结构如下所示：

    <img src="https://i.loli.net/2021/09/27/XTusxmtNzFL8YB2.png" alt="1.png" style="zoom:60%;" />

### 集合(`Set`)

​	表示没有重复元素的无序即可，对应的编码方式有：`REDIS_ENCODING_INTSET`、`REDIS_ENCODING_HT`

- `REDIS_ENCODING_INTSET`

  - 针对整形集合作为底层实现
  - 使用整形集合时集合时有序的
  - 具体结构如下所示：

  <img src="https://i.loli.net/2021/09/27/eBo45rOAtspqFHJ.png" alt="1.png" style="zoom:60%;" />

- `REDIS_ENCODING_HT` <a name="REDIS_ENCODING_HT"></a>

  - 底层时字典的存储结构，字典的每个键和 `Redis` 的 `key` 一样都只能时字符串类型的。在处理 `Set` 数据类型时，字典每个键对应的值都是 `NULL`
  - 当达到一定条件时，会从整形集合转变为字典
    - 当插入元素的数量大于某个阈值时，将会从 `REDIS_ENCODING_INTSET` 的编码方式转换为`REDIS_ENCODING_HT` 的编码方式
    - 当插入的元素为非数字时，将会直接使用 ``REDIS_ENCODING_HT` 的编码方式
  - 具体结构如下所示：

  <img src="https://i.loli.net/2021/09/27/357ISmTzjJR6aH1.png" alt="1.png" style="zoom:60%;" />

### 有序集合

有顺序的集合。存在两种编码方式：`REDIS_ENCODING_ZIPLIST`、`REDIS_ENCODING_SKIPLIST`

- `REDIS_ENCODING_ZIPLIST` (`ziplist`)

  参见：<a href="#REDIS_ENCODING_ZIPLIST">REDIS_ENCODING_ZIPLIST</a>

- `REDIS_ENCODING_SKIPLIST`（`zset`）

  - `SkipList`

    > 在基于有序链表的数据结构的基础上，通过在相距随机距离的节点上添加额外的一个索引链接，通过这些索引链接可以提高查找速度。
    >
    > 可以看看：http://zhangtielei.com/posts/blog-redis-skiplist.html
    >
    > 优点：
    >
    > 1. 相比较于于 `B+` 树的数据结构，`SkipList` 可以降低索引的内存使用。
    > 2. 对于经常性地访问一段连续区间的元素，`SkipList` 要比其它平衡树和哈希表更加高效
    > 3. 相比较其它的数据结构，调试起来更加简单

    一个可能的 `SkipList` 结构：

    <img src="http://zhangtielei.com/assets/photos_redis/skiplist/redis_skiplist_example.png" />

  - 内存存储结构

    ```c
    # ZSet 存储结构
    typedef struct zset {
        dict *dict;
        zskiplist *zsl;
    } zset;
    ```

    看起来像下面这样：

    <img src="https://segmentfault.com/img/bVcIpbD/view" />

- 转换规则

  ```bash
  zset-max-ziplist-entries 128 
  zset-max-ziplist-value 64
  ```

  - 当存储的元素个数大于 `zset-max-ziplist-entries` 时，将自动从 `REDIS_ENCODING_ZIPLIST`转换为 `REDIS_ENCODING_SKIPLIST`
  - 当存储的单个数据元素的长度大于 `zset-max-ziplist-value` 时，自动从 `REDIS_ENCODING_ZIPLIST`转换为 `REDIS_ENCODING_SKIPLIST`

### 哈希

存储哈希表的数据对象。编码类型：`REDIS_ENCODING_ZIPLIST`、`REDIS_ENCODING_HT`

- `REDIS_ENCODING_ZIPLIST`

  参见：<a href="#REDIS_ENCODING_ZIPLIST">REDIS_ENCODING_ZIPLIST</a>

- `REDIS_ENCODING_HT`

  参见：<a href="#REDIS_ENCODING_HT">REDIS_ENCODING_HT</a>

- 转换规则

  - `Hash` 中的键值对数量小于某个阈值时，使用 `REDIS_ENCODING_ZIPLIST`
  - `Hash` 里的 `key` 和 `value` 长度均小于某个阈值时，使用 `REDIS_ENCODING_ZIPLIST` 编码方式