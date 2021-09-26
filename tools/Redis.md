### 数据结构

- 字符串 （`String`）
- 哈希（`Hash`）
- 列表（`List`）
- 集合（`Set`）
- 带范围查询的排序集合（`ZSet`）
- 位图（`BitMap`）
- 半径查询和流的地理空间索引



### 对象源代码

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



### Redis 对象类型

 `Redis` 是一个键-值的数据库，对于键来讲，只能之 `String` 类型的，而值可以是多样的。

一般结构如下图所示：

<img src="https://i.loli.net/2021/09/26/IQraABP7niphvX9.png" alt="1.png" style="zoom:67%;" />

 

每个 type 对应的编码如下图所示：

![1.png](https://i.loli.net/2021/09/26/KrnUyAF6SmcfxCo.png)

### 编码与数据结构的实现

| encoding 常量             | 编码对应的底层数据结构              |
| ------------------------- | ----------------------------------- |
| REDIS_ENCODING_INT        | long 类型的整数                     |
| REDIS_ENCODING_EMBSTR     | embstr 编码的动态字符串（短字符串） |
| REDIS_ENCODING_RAW        | 简单动态字符串                      |
| REDIS_ENCODING_ZIPLIST    | 字典（`HashTable`）                 |
| REDIS_ENCODING_LINKEDLIST | 双向链表                            |
| REDIS_ENCODING_INTSET     | 压缩集合                            |
| REDIS_ENCODING_HT         | 整数集合                            |
| REDIS_ENCODING_SKIPLIST   | 跳表和字典                          |



### 数据类型

- `String`

  - 底层存储的是字符数组

  - 对应编码：`REDIS_ENCODING_INT`、`REDIS_ENCODING_EMBSTR`、`REDIS_ENCODING_RAW`

  - 编码对应情况

    1. 当设置值为 `long` 类型表示的整数，此时 `String` 的 encoding 为 `int`。此时的 `Redis` 对象如下图所示：

       <img src="https://i.loli.net/2021/09/26/P1blS52nyrYaAIp.png" alt="image.png" style="zoom:80%;" />

    2. embstr，`buf` 中直接存储对应的字符串内容。当字符串的长度大于某个阈值时，将会从 `embstr` 编码转变为 `raw` 编码

       - 当保存的至的长度小于某个阈值时，使用 `embstr` 作为字符串的存储方式。
       - 当使用 `raw` 的方式编码时会调用两次的内存分配来分别创建 `Redis Object` 和 `sdshdr`
       - 使用 `embstr` 的方式编码时调用一次内存分配函数来创建一个连续的内存空间，因此速度更快
       - `embstr` 的编码方式没有提供修改函数，所以它是只读的。如果对 `embstr` 编码的字符串进行修改，首先会转换成为 `raw` 类型的编码方式，然后再进行修改。

       <img src="https://i.loli.net/2021/09/26/Mi6ESIFHw3xpQAb.png" alt="image.png" style="zoom:80%;" />

    3. 当存储的字符串长度大于某个阈值时，会使用 `raw`  的编码方式进行编码

       <img src="https://i.loli.net/2021/09/26/UueYsdPf9z8Qbvr.png" alt="image.png" style="zoom:80%;" />

       其中，`ptr` 指向的 `sdshdr` 的 `free` 属性表示空闲的元素数量，`len` 表示已经使用的元素数量。

  - `SDS`

    > Simple Dynamic String 简单动态字符串。`Redis` 定义的字符串类型
    >
    > 引入原因：
    >
    > 1）提高访问速度。
    >
    > ​	C 语言计算字符串长度的方式过于耗时
    >
    > 2）避免缓冲区溢出。
    >
    > ​	C 字符串的操作存在缓存溢出的问题，前提是在增加元素之前没有足够的内存分配
    >
    > ​	`SDS` 通过动态地执行空间的扩充，API 会自动地进行空间的扩展 
    >
    > 3）通过预分配内存空间和惰性释放的方式来提高内存分配的速度
    >
    > ​	空间预分配
    >
    > ​		对 `SDS`  进行修改之后，`SDS` 的长度 < 1 M, 这个时候分配的 `len = free`
    >
    > ​		对 `SDS` 进行修改之后，`SDS` 的长度 >= 1 M，这个时候会按照 1 M 的空间去分配
    >
    > 4） C 字符串不能包含空字符，使用的范围较小；`SDS` 可以保存任意的数据

- `List` 类型

  - 常用与消息队列

  - 特点：有序、可重复、插入和删除的速度较快

  - 编码的对应情况

    1. `REDIS_ENCODING_ZIPLIST`, 压缩链表

       <img src="https://i.loli.net/2021/09/27/HvOzpuBJhKDoA1G.png" alt="1.png" style="zoom:80%;" />

    2. `REDIS_ENCODING_LINKEDLIST` 双向链表

       ![1.png](https://i.loli.net/2021/09/27/XTusxmtNzFL8YB2.png)

- `Set` 类型

  编码方式：

  1. `REDIS_ENCODGIN_INTSET`，针对整形集合作为底层实现

     <img src="https://i.loli.net/2021/09/27/eBo45rOAtspqFHJ.png" alt="1.png" style="zoom:80%;" />

  2. `REDIST_ENCODING_HT`

     底层是字典，每个键都是字符串对象，字典项对应的值都为 NULL。

     转换规则

     - 当元素数量大于某个阈值时，将会转变为 `REDIS_ENCODING_HT` 的编码方式
     - 当插入的元素是非数字时，使用 `REDIS_ENCODING_HT` 的编码方式

     <img src="https://i.loli.net/2021/09/27/357ISmTzjJR6aH1.png" alt="1.png" style="zoom:80%;" />

- `ZSET`

  - 编码方式

    1.  `REDIS_ENCODING_ZIPLIST`

    2. `REDIS_ENCODING_SKIPLIST`

       <img src="https://i.loli.net/2021/07/03/hixj3ldFyzgXkM2.png" alt="image.png" style="zoom:150%;" />

- `Hash`

  编码方式：

  1. `REDIS_ENCODING_ZIPLIST`
  2. `REDIS_ENCODING_HT`
     - `Hash` 里的 `key` 和 `value` 长度均小于某个阈值时，使用 `REDIS_ENCODING_ZIPLIST` 编码方式
     - `Hash` 中的键值对数量小于某个阈值时，使用 `REDIS_ENCODING_ZIPLIST`