# MySQL 基础（二）日志

在操作系统和数据库管理系统中，为了提高数据的容灾性，一般都会通过写入相关日志的方式来记录数据的修改，使得系统受到灾难时能够从之前的数据中恢复过来。MySQL 也提供了日志的机制来提高数据的容灾性，主要包括 redo 日志和 undo 日志

<br />

## redo 日志

在 Buffer Pool中修改了页，如果在将 Buffer Pool 中的内容冲洗到磁盘上的这一过程出现了问题，导致内存中的数据失效，那么这个已经提交的事务在数据库中所做的修改就丢失了，这时需要通过 redo 日志来重新提交本次的事务。

<br />

### redo 简单日志类型

- 通用日志类型

  具体的结构如下图所示：
  <img src="https://s2.loli.net/2021/12/19/2nhyvNz6plCI5Sm.png" alt="redo_log.png" style="zoom:150%;" />

  

- 固定长度日志类型

  主要有以下几种：

  MLOG_1BYTE（type = 1）、MLOG_2BYTE（type = 2）、MLOG_4BYTE（type = 4）、MLOG_8BYTE（type = 8）

  具体的结构如下图所示：

  <img src="https://i.loli.net/2021/08/14/t1ksBbZqrLh2M7Y.png" alt="image.png" style="zoom:80%;" />

  

- 不限定长度日志类型

  对应的类型为 MLOG_WRITE_STRING（type = 30），具体的结构如下图所示：

  <img src="https://i.loli.net/2021/08/14/XSjpTJYBD5wRAiz.png" alt="image.png" style="zoom:80%;" />

<br />

### redo 复杂日志类型

复杂日志的结构如下所示：

<img src="https://s2.loli.net/2021/12/19/ilsgcYWQwJb3mje.png" alt="image.png" style="zoom:50%;" />

存在以下几种类型：

- MLOG_REC_INSERT（type = 9）（插入记录，非紧凑型）
- MLOG_COMP_REC_INSERT（type = 38）（插入记录，紧凑型）
- MLOG_COMP_PAGE_CREATE（type = 58）（创建页）
- MLOG_COMP_REC_DELETE（type = 42）（删除记录）
- MLOG_COMP_LIST_START_DELETE（type = 44）（指定开始位置删除）
- MLOG_COMP_LIST_END_DELETE（type = 43）（指定结束位置删除）
- MLOG_ZIP_PAGE_COMPRESS（type = 51）（压缩页）

<br />

### redo 日志组

通过日志组来保证事务的一致性，具体的日志组结构如下图所示：

![image.png](https://i.loli.net/2021/08/15/H2BimIJdn6abPfk.png)

通过 `MLOG_MULTI_REC_CORD` 判断 redo 日志的组别，在 redo 时会将该字段前的所有 redo 日志视为一个事务中的操作（即再执行事务）。由于一个事务可能会存在多个对数据修改的操作，因此会有多条日志记录，简单的一条 redo 日志无法保证整个事务的**原子性**，必须使用日志组的方式才能实现

针对单条 redo 日志，单独放在一个日志组中可能过于浪费空间，为此，对于单条的 redo 日志，将会从 redo 日志的 “type” 字段中剥离一个位来表示该条日志是否是单条原子性操作，具体地，日志组中的 redo 日志的 ”type“ 字段的结构如下所示：

![image.png](https://i.loli.net/2021/08/15/ySt6BW3pDPoUVJv.png)

flag 位为 1，表示该 redo 日志是一个单条原子性的操作，为 0 则表示一般日志；通过该 flag 位，同样可以保证事务的一致性，因此当该 flag 位为 1 时，它将不属于一个 redo 日志组



<br />

### redo 日志缓冲区

<br />

#### MTR

MTR（Mini—Transaction）：对于底层 Page 的一次**原子访问**的过程被称为一个 Mini—Transaction

由于一个事务可以执行多个 `SQL` 语句，因此一个事务可以包含多个 MTR； MTR 可以包含多条 redo 日志，具体的对应关系如下图所示：

<img src="https://s2.loli.net/2021/12/19/jSa4ftUDb87Kd1E.png" alt="image.png" style="zoom:50%;" />

<br />

#### redo 日志块结构

redo 日志的块结构如下图所示：

<img src="https://s2.loli.net/2021/12/19/seAMZ846zdUjrWx.png" alt="image.png" style="zoom:80%;" />

具体的关于 log block header 中的相关字段的介绍如下：

- `LOG_BLOCK_HDR_NO`：块编号
- `LOG_BLOCK_HDR_DATA_LEN`：在 log block body 中实际存储的数据体的长度
- `LOG_BLOCK_FIRST_REC_GROUP`：对应的 MTR（参见 MTR 中 redo log 的对应关系） 
- `LOG_BLOCK_CHECKPOINT_NO`：。。。。

这是组成 redo log buffer 的基本单位

<br />

#### redo log buffer

和 Page 类似，在将 redo log 写入到磁盘中时，不会直接与磁盘交互，而是首先将 redo log 写入到内存中的 buffer 区，再合适的时间通过后台线程再冲洗到磁盘上

 redo log buffer 的组成如下图所示：

<img src="https://s2.loli.net/2021/12/19/vrlaziAnJqtQS2T.png" alt="image.png" style="zoom:80%;" />

日志的写入过程：当提交事务时，会将事务的 MTR 分解写入，当有多个事务并发地提交时，MTR 的写入顺序将是不确定的

以下图为例，假设现在有两个事务 T1 和 T2，这两个事务分别存在两个 MTR ：mtr_t1_1、mtr_t1_2 和 mtr_t2_1、mtr_t2_2，实际写入情况可能如下图所示：

![image.png](https://s2.loli.net/2021/12/19/sXT4OZom9ILkQBV.png)

值得注意的是，为了保证内存的连续性，写入操作将是顺序的，可以看到，mtr_t1_2 由于内容较多，为了保证内存的顺序性，这会使得mtr_t1_2 会横跨多个 block 进行写入，尽管 mtr_t2_2 有机会在这个过程中写入，但是依旧需要等待来维持顺序写

具体地，一个事务提交时，写入 redo log 的步骤如下：开始事务——> 执行 SQL ——> 产生 redo log ——> redo log 聚集到 MTR 中 ——> 写入到 block ——> 写入到 log buffer

<br />

### 数据冲刷

在满足以下几种条件时，将会执行将 log buffer 中的内容冲刷到磁盘上的操作：

1. log buffer 的可用空间不足 50% 的时候
2. 事务提交
3. 由于后台线程的存在，大约会以每秒一次的频率将 log buffer 中的内容写入到磁盘中
4. 正常关闭服务器时
5. 做 checkpoint 时

MySQL 会将 log buffer 中的内容写入到名称为 ib_logfile* 的文件中，默认情况下，MySQL 会使用使用到两个文件，当第一个文件写满时再写入下一个文件，当最后一个文件写满时，在写回到第一个日志文件，具体的情况如下图所示：

![image.png](https://s2.loli.net/2021/12/19/1PXAkvca36lqKGj.png)

```ini
 # 设置 redo log 文件的最大大小为 10 MB，设置的值应当在 4MB ~ 512GB 这个范围内
 innodb_log_file_size = 10MB
 # 设置 redo log 存储的文件的数量，范围为 2 ~ 100 
 innodb_log_files_in_group=4
```

<br />

### redo log 文件格式

lsn (log sequence number)：用于记录当前总共已经写入到 log buffer 的 redo 日志量，初始值为 8704

redo log 的文件格式如下图所示：

![image.png](https://s2.loli.net/2021/12/19/TvuwVblN6jgdcJL.png)

首先对于 log file header 部分，关键的字段如下：

- `LOG_HEADER_START_LSN`：redo 日志具体内容距离文件开始位置的偏移量，默认为 2048（512*4）

对于 checkpoint 2 部分，关键的是两个字段：

- `LOG_CHECKPOINT_LSN`：checkpoint 在文件中的偏移量
- `LOG_CHECKPOINT_OFFSET`：checkpoint 在日志组中的偏移量

- 



<br />

## undo 日志

redo log 用于处理容灾恢复的操作，主要是为了防止由于受到异常情况导致数据未能真正写入到磁盘而造成的数据丢失的情况，具体一点，就是说当事务提交时，需要有手段来保证数据的一致性。而 undo log 则是为了处理事务处理时出现异常，需要回滚事务的情况

<br />

### INSERT 对应的 undo log

使用 `TRX_UNDO_INSERT_REC` 日志结构，对应的具体结构如下图所示：

![image.png](https://s2.loli.net/2021/12/19/82AcfT9y1CEFNlJ.png)

关键的字段解释如下：

- undo Type：该 undo log 所属的日志类型，在这里为 `TRX_UNDO_INSERT_REC`

- table id ： 在 `information_schema.INNODB_TABLES` 中可以查看对应的 Table Id，该字段值由 MySQL 自动生成

- 主键各列信息：以  `<len, value>`  组成的映射关系，其中 `len` 表示主键字段类型的长度，`value` 表示实际值



<br />

### DELETE 对应的 undo log

删除操作对应的日志结构为 `TRX_UNDO_DEL_MARK_REC`，具体的结构如下图所示：

![image.png](https://s2.loli.net/2021/12/19/yPYDMATG3ueW5d2.png)

关键的字段解释如下：

- info bits：记录头信息比特位（不太重要）
- len of index_col_info：索引列每列的字段长度总和（主键索引、二级索引）
- 索引的各列信息：`pos` 表示在记录中相对于真实记录数据的开始位置，比如，`trx_id` 为 1，`roller_pointer` 为 2

执行删除操作之后对应的 undo log 的内容可能类似下图所示：
![image.png](https://s2.loli.net/2021/12/19/US4ZbYLERmsIWOi.png)

为了保证能够恢复数据，通过 `roll_pointer` 指向对应的删除的记录的插入 undo log

<br />

### UPDATE 对应的 undo log

UPDATE 操作对应的 undo log 日志类型为 `TRX_UNDO_UPD_EXIST_REC`， 对应的结构如下图所示：

![image.png](https://s2.loli.net/2021/12/19/Qy6B2hFi5Uxa81s.png)

更新的 undo log 的具体过程和删除类似

