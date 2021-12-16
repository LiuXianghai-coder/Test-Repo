# MySQL 基础（一）Page

存储在磁盘上的数据需要通过 IO 来读取，这是一个比较耗时的操作，为了能够提高访问速度，`MySQL` 引入了 `Page` 的结构作为客户端与数据交互的基本单元。

<br />

## Page 结构

`Page` 的大小默认为 16 kb，由于这个大小可能无法与某些操作系统的页大小相匹配，这种情况下可能会使得对于 `Page` 的写入无法保证原子性（即 `Page` 没有完全写入，这种情况非常危险），为了解决这个问题，可以设置 `Page` 的大小，具体在 `/etc/my.inf` 文件中配置（`windows` 上为 `my.ini` 文件）：

```ini
# 将 Page 的大小设置为 4kb，只有在 MySQL 5.6 之后使用 InnoDB 存储引擎才可以修改
innodb_page_size=4K
```

出于不同的目的，`Page` 也有许多类型，比如，用于存储索引的 `Page` 被称为索引页，存储数据的 `Page` 被称为数据页，存储日志的 `Page` 被称为日志信息页等 

`Page` 的一般结构如下图所示：
<img src="https://i.loli.net/2021/08/14/967EzBwgVUFNafI.png" alt="page.png" style="zoom:80%;" />

具体介绍如下：

- `File Header`：38 字节，用于存储页的通用信息
- `Page Header`：56 字节，用于存储页的专用信息，即页的状态信息
- `Infimum + Supermum`：26 字节，用于存储当前页的最小记录和最大记录
- `User Records`：用于存储在当前页中实际存储的数据
- `Free Space` ：表示当前页可用的存储空间
- `Page Directory`：页目录，存储用户记录的相对位置
- `File Tailer` ：8 字节，这个属性的主要目的是用于检测当前的 `Page` 是否是完整的

<br />

### Row 的结构

当读取数据时，将会从 `Table` 中加载数据到 `Page`，而在表中数据是以 `row` 为基本单位读取到 `Page` 中，即存储在 `User Record` 中的数据是以 `row` 为单位的

`MySQL` 中 `row` 的结构如下图所示（以 `COMPACT` 行格式为例）：

![record.png](https://i.loli.net/2021/08/14/jCWYNhSelJ6GyHA.png)

关键的部分在于记录头信息的组成部分，具体解释如下：

- “预留位 1” 和 “预留位 2”：这两个位置是保留位使得之后的版本可以进行扩展
- `deleted_flag`：当前对应的 `row` 是否被删除，这里只是逻辑上的删除。将这个位置为 1 表示已经被删除，通过这个标记位，可以将所有被删除的 `row` 组合称为一个 “垃圾链表”，同时使得当前 `row` 的空间可以被复用
-  `min_rec_flag`：B+ 树每层非叶子节点中标识最小的目录项记录
- `n_owner`：把一个页划分成若干个组，提高整体的性能。改组中主键最大的 `row` 将会保存该值，表示该组中存在的记录的数量
- `heap_no`：

<br />

## 索引



<br />

## Buffer Pool