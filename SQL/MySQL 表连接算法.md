# MySQL 表连接算法

## <a name="simpleNestedJoin" href="#">简单嵌套循环连接</a>

<img src="https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/3fe424c5b4914b62b6004983e32a8bb2~tplv-k3u1fbpfcp-zoom-in-crop-mark:1512:0:0:0.awebp" alt="image.png" style="zoom:50%;" />

对于 `t1` 表中过滤后的每一条数据，都会立马去 `t2` 表中进行匹配查询，而不会等记录检索完成了再去查询 `t2`

这个过程就像是一个嵌套的循环，连接的表的数量就是循环嵌套的层数，当表中数据量较大或连接的表过多时，这种算法的性能是很差的

## <a name="indexNestedJoin" href="#">索引嵌套循环连接</a>

和 <a href="#simpleNestedJoin">简单嵌套循环连接</a> 中的算法类似，区别在于对 `t2` 表关联的列上加上了索引，使得在 `t2` 表中的匹配查询不再需要全表扫描

`MySQL` 的查询优化器会自动选择合适的连接算法

## 缓存块嵌套循环连接

对于关联表 `t2` 中关联的字段不存在索引，会使得复杂度达到 $O(n^k)$， 其中 $k$为表连接的个数。

为了优化这种情况，`MySQL` 引入了一个名为 "Join Buffer"（连接缓冲池） 的概念。在执行连接查询前首先申请一块固定大小的内存空间，然后将驱动表 `t1` 中过滤后的若干条数据记录放入 "Join Buffer" 中，然后匹配 `t2` 表中关联的数据时，可以一次性与 "Join Buffer" 中的数据进行匹配，减少对 `t2` 的全表扫描次数

> Join Buffer 不会存放驱动表 `t1` 的所有信息，只会存储 `SELECT` 查询列和过滤条件所涉及的列

值得注意的是，在高版本的 `MySQL` 中，使用 <a href="#hashJoin">哈希连接</a> 替换了 "缓存块嵌套循环连接"

## 批量键访问连接

Batched Key Access Joins

由于在关联的过程中，`t2` 中二级索引的排列顺序与 `t1` 中关联列的排列顺序不一致，这就会导致 <a href="#indexNestedJoin">索引嵌套循环连接</a> 中每次走索引的时候都会使用随机 IO 的形式进行访问

在 `MySQL 5.6` 开始，提供了一种 "多范围读（Multi-Range Read MRR）" 的特性，将随机 IO 转换为顺序 IO 的方式进行读取，以提高连接速度

## <a name="hashJoin" href="#">哈希连接</a>

- 哈希表构建阶段

  将 `t1` 表中的连接列计算 `hash` 值，在内存中构建哈希表

- 探测阶段

  遍历被驱动表 `t2`，计算连接字段的 `hash` 值，并在哈希表中进行查找，如果匹配，则放入结果集

内存空间不足的情况：构建哈希表的大小是有 `join_buffer_size` 控制的，如果内存空间不足，则需要将这部分的哈希表作为临时文件存储在磁盘中

具体步骤如下：

- 将 `t1` 表的连接列数据进行分片存储，每个分片中存储对应分片 `hash` 的哈希表
- 将 `t2` 表的连接列按照相同的方式构建文件分片
- 遍历这些文件分片，按照哈希连接的方式进行处理，得到最终的结果集



<hr />

参考：

<sup>[1]</sup> https://juejin.cn/post/7074061373205921828

<sup>[2]</sup> https://cloud.tencent.com/developer/article/1684046

<sup>[3]</sup>https://cloud.tencent.com/developer/article/1699812