# MySQL 基础（四）锁

<br />

## 解决并发事务带来的问题

### 写—写情况

任意一种事务隔离级别都不允许 “脏写” 的发生，因为这样会使得数据混乱。所以，当多个未提交的事务相继对一条记录进行改动时，就需要使得这些事务串行执行，避免 “脏写” 的发生。

为了使得事务的执行是串行化的，需要通过对修改的记录进行加锁，才能保证事务的执行是串行化的。锁的本质是一个内存上的结构

MySQL 中对于 “写—写” 操作的具体处理流程如下：

1. 在初始阶段，没有相关的锁结构和记录相关联，此时的状态如下图所示

    <img src="https://s6.jpg.cm/2021/12/21/LcEm3L.png" style="zoom:80%" />

2. 当一个事务 T1 想要对这条记录进行改动时，首先会检查在内存中是否存在与这条记录相关联的锁记录，如果没有，那么就会在内存中生成一个锁结构与这条记录相关联，这个阶段也被称为 “获取锁/加锁”，此时结构如下图所示:

    <img src="https://s6.jpg.cm/2021/12/21/LcE2Qe.png" style="zoom:80%" />

3. 此时又新来了一个事务 T2 访问了这条记录，T2 也想对这条记录进行修改，但是发现现在这条记录已经有一个对应的锁结构与之关联了，在这种情况下，T2 也会生成一个锁结构与这条记录进行关联，但是 T2 生成的锁结构的 `is_wating` 属性将为 `true` 而不是 `false`，这种情况也被称为 “获取锁失败/加锁失败”，此时的情况如下图所示：

    <img src="https://s6.jpg.cm/2021/12/21/LcmhRH.png" style="zoom:80%" />

4. 在 T1 提交事务之后，就会把生成的锁结构释放掉，然后检测是否还有与该记录关联的锁结构，在当前的上下文环境中，发现还存在 T2 生成的锁结构，因此需要将 T2 的锁结构的 `is_wating` 属性修改为 `false`，使得 T2 继续执行，此时的情况如下图所示：

    <img src="https://s6.jpg.cm/2021/12/21/Lcmj1L.png" style="zoom:80%">

<br />

### 读—写情况

为了避免在 “读—写” 或 “写—读” 情况下发生 “脏读”、“不可重复读”、“幻读” 的情况，有以下两种方案来解决：

1. 读操作使用 MVCC，而写操作则通过加锁的方式来保证写入的可见性
2. 读、写操作都采用加锁的形式

SQL 标准规定 Repeatable Read（可重复读）隔离级别需要避免 “脏读” 和 “不可重复读” 问题的出现，但是 MySQL 对于该级别的实现在很大程度上也避免了 “幻读” 问题的出现

<br />

### 一致性读

在 MySQL 中，对于一致性读的定义如下：

> A read operation that uses **snapshot** information to present query results based on a point in time, regardless of changes performed by other transactions running at the same time. If queried data has been changed by another transaction, the original data is reconstructed based on the contents of the **undo log**. This technique avoids some of the **locking** issues that can reduce **concurrency** by forcing transactions to wait for other transactions to finish.

大致翻译如下：

> 一个读操作，它使用快照信息展示基于某个时间点的查询结果，而不管其它同时运行的事务执行的修改。如果查询的数据已经被其它的事务修改了，那么初始的数据将会基于 undo log 的内容重新构造。这种技术避免了一些使用锁会存在的问题 — 由于使用锁而导致的使得事务不得不等待其它事务执行完成，从而减少了并发量

翻译的不是很准确，但是大概意思就是一致性读是通过类似 MVCC 的方式来实现读操作的

所有普通的 `SELECT` 语句在 Read Committed 和 Repeatable Read 的隔离级别下都是一致性读（MVCC），如：

```sql
SELECT * FROM meeting;
SELECT * FROM person p JOIN address a ON p.address_id = a.id;
```

上面的文档也提到了，一致性读不会对任何记录进行加锁操作，其它事务可以自由地对表中的记录进行修改，从而提高了并发量

<br />

### 锁定读

在使用加锁的方式来解决读写问题时，由于既需要允许 “读—读” 情况不受影响，又要使 “写—写” 或 “读—写” 情况中的操作相互阻塞，所以 MySQL 的提供了两类锁：

- 共享锁（S 锁）

    一个事务如果想要读取一条记录，需要首先获取到该记录的 S 锁

- 独占锁（X 锁）

    在事务要修改一条记录时，首先需要获取该记录的 X 锁

S 锁和 X 锁的兼容关系如下表所示：

| 兼容性 |  X 锁  |  S锁   |
| :----: | :----: | :----: |
|  X 锁  | 不兼容 | 不兼容 |
|  S 锁  | 不兼容 |  兼容  |

解释如下：

1. 如果事务 T1 获取到了一条记录的 S 锁，并且此时事务 T2 想要获得这条记录的 X 锁，那么 T2 的获取锁的操作将会被阻塞，直到 T1 释放该记录的 S 锁
2. 如果事务 T1 首先获取到了一条记录的 X 锁，那么不管现在别的事务是想获得该记录的 S 锁还是 X 锁，这个获取锁的操作都将被阻塞，直到 T1 释放 X 锁

MySQL 中显式地加锁的语句如下所示：

```sql
-- 对读取的记录加上 S 锁
SELECT .... LOCK IN SHARE MODE;
-- 对读取的记录加上 X 锁
SELECT .... FOR UPDATE

-- 这种加锁的 SQL 不是 SQL 标准中定义的，只能在 MySQL 中使用，并且加锁的方式对于性能有一定的损耗，基于以上两点原因应该尽量避免使用锁来读取记录
```



<br />

### 锁定写

- `DELETE` 操作

    首先在 B+ 树中定位到这条记录的位置，然后获取到这条记录的 X 锁，最后再执行 delete mark

- `INSERT` 操作

    一般情况下，新插入的记录受到隐式锁的保护，不需要在内存中为其生成对应的锁结构

- `UPDATE` 操作

    `UPDATE` 操作分为以下三种情况：

    - 未修改主键值，并且被更新后的记录所占用的存储空间未发生变化

        在这种情况下，首先需要在 B+ 树中定位到这条记录的位置，然后再获取到这条记录的 X 锁，最后在原纪录的位置进行修改操作

    - 未修改主键值，但是更新后的记录所占用的存储空间发生了变化

        首先在 B+ 树中定位到这条记录的位置，然后获得这条记录的 X 锁，之后再将原有的记录直接删除（没有 delete mark 这一步），然后再插入一条新的记录

    - 修改了主键值

        相当于在原记录上执行 `DELETE` 操作，然后再执行一次 `INSERT` 操作，加锁的顺序按照 `DELETE` 和 `INSERT` 的顺序进行 



<br />

## 多粒度锁

在 MySQL 中，主要分为行锁和表锁，对于行锁来说，对于一条记录加行锁，只会影响到当前的记录行，对于其它的记录没有影响，这种锁的粒度比较细；而表锁则是直接对整个表进行加锁，直接影响到整个表中的所有数据，这种锁的粒度较粗。

在实际情况中，如果能够不使用锁，那么就尽量不要使用锁；如果不得已不得不使用锁，那么需要在行锁和表锁之间进行一下权衡，选择粒度合适的锁。

上面介绍的 “锁定读” 和 “锁定写”，使用的都是行锁，分为 S 锁和 X 锁，同样地，表锁也分为共享锁（S 锁）和独占锁（X 锁）

<br />

### InnoDB 中的行级锁

在 InnoDB 中，对于行锁也做了分类，不同类型的行锁的功能也有所不同

- Record Lock（记录锁）

    对应的类型为 `LOCK_REC_NOT_GAP`，这种类型的行锁仅仅只是将一条记录加上锁，这种记录锁也分为 S 锁和 X 锁

- Gap Lock（间隙锁）

    对应的类型为 `LOCK_GAP`，这种锁也被称为 “间隙锁”，该锁的主要功能是锁住指定记录以及前面的间隙，防止在前面的间隙中插入新的数据。

    间隙锁提出的目的仅仅只是为了防止 “幻读” 问题的出现

    使用技巧，间隙锁只能防止锁定的记录之前的间隙不能插入新的数据，如果想要同时保证该记录后面的间隙也不能插入新的数据，回忆一下 Page 中的 `Supremum` 记录，只需要给 `Supremum` 记录也同时加上间隙锁就可以使得整个区间都无法插入新的记录，完全避免了 “幻读” 问题

- Next-Key Lock

    对应的行级锁的类型为 `LOCK_ORDINARY`，本质就是一个 **记录锁 + 间隙锁** 的组合体，既能保护该记录无法被其它的事务修改，也能防止其它的事务将新的记录插入到该记录前面的间隙中

- Insert Intention Lock（插入意向锁）

    对应的行级锁的类型为 `LOCK_INSERT_INTENTION`，事务在等待时也需要在内存中生成一个锁结构，表明如果有事务想在某个间隙中插入新记录，但是现在正处于等待状态。

    以下面的示例为例：<sup>[1]</sup>

    <img src="https://mmbiz.qpic.cn/mmbiz_png/AZHyCoMMOCibAnol8HUF5H7WPuFenoCCicrlyYFGcXnJS5ia1zYIy3epVWrKwyhJsjwvN4mXkVXfR2wKMPed1cIew/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1">

    现在有三个事务 T1、T2、T3要对 no=9 的数据进行修改，此时 T1 获取到了间隙锁，T2、T3准备插入新的记录，但是由于 T1 的间隙锁存在，这两个事务只能等待，直到 T1 释放间隙锁。锁结构中的 type 表示当前事务持有的锁的类型，T1 持有间隙锁因此类型为 gap，T2、T3 表示希望插入记录，因此类型为 “插入意向锁”

    当 T1 提交了事务之后，会释放掉间隙锁，此时 T2、T3之间的操作也不会相互阻塞，它们可以同时获取到 no=9 的记录的插入意向锁，然后执行插入操作。

    事实上插入意向锁并不会阻止别的事务继续获取该记录上任何类型的锁，因此该锁的功能是十分有限的

- 隐式锁

    一般情况下，执行 `INSERT` 语句时不需要在内存中生成锁结构的，但是在某些特殊的情况下，可能会有一些问题，比如：

    一个事务首先插入了一条记录，然后另一个事务执行了如下操作：

    - 立即执行 `SELECT …… LOCK IN SHARED MODE` 语句读取这条记录（获取到该条记录的 S 锁），或者使用 `SELECT …… FOR UPDATE` 语句读取这条记录（获取这条记录的 X 锁），这种情况下将会出现 “脏读” 问题
    - 立刻修改这条记录（获取这条记录的 X 锁），在这种情况下将会出现 “脏写” 问题

    对于这种情况，一般要通过记录的隐藏列 `trx_id` 来解决这个问题，需要将聚簇索引和二级索引中的记录分开来看。

    - 对于聚簇索引

        该类型的记录中会存在一个隐藏的 `trx_id` 列，该隐藏列记录着最后改动该记录的事务 id，在当前事务中新插入一条聚簇索引记录之后，该记录的 `trx_id` 列代表的就是当前事务的事务 id。

        如果此时其它事务此时想要对该记录添加 S 锁或者 X 锁，首先会检查该记录的 `trx_id` 列代表的事务是否是当前的活跃事务，如果不是的话就可以正常读取（该记录对应的事务已经提交）；如果是的话，那么就帮助当前的事务创建一个 X 锁的锁结构，该锁结构的 `is_waiting` 属性为 `false`，然后再为自己创建一个锁结构，该锁结构的 `is_waiting` 属性为 `true`，然后自身进入等待状态

    - 对于二级索引

        二级索引对应的记录本省没有 `trx_id` 这一隐藏列，但是在二级索引页的 `Page Header` 部分有一个 `PAGE_MAX_TRX_ID` 属性，该属性代表对该页做改动的最大的事务 id。

        如果当前 `PAGE_MAX_TRX_ID` 属性值小于当前最小的活跃事务 id，那么说明对页所做的修改的事务都已经提交了，否则就需要在 Page 中定位到对应的二级索引，然后通过回表操作找到它对应的聚簇记录，在重复对聚簇索引的相同的做法

    综上，隐式锁起到了延迟生成锁结构的功能，一般情况下不生成隐式锁，如果发生上文描述的冲突的锁操作，则采用隐式锁结构来保护记录



<br />

### InnoDB 中的表级锁

InnoDB 存储引擎中的表级锁没有太大的用处，除了在某些特殊情况下（如系统奔溃恢复时），大部分情况下都不会对整个表添加表级别的 S 锁或 X 锁。

在对某个表执行 DDL 语句时，其它事务在对这个表并发执行 DML 语句时，会发生阻塞；反之，如果执行 DML 语句时再执行 DDL 语句，也会发生阻塞。这个阻塞是在 server 层使用 **元数据锁**（Metadata Lock）来实现的，也不会使用到 S 锁和 X 锁

DDL 在执行是会因式地提交当前会话中的事务，这是因为 DDL 语句的执行一般都会在若干个事务中完成，在开启这些特殊事务之前，需要将当前会话中事务提交掉

显式地对表加上锁，首先通过以下的 `SHOW VARIABLE` 语句查看两个比较关键的变量：

```sql
SHOW [GLOBAL | SESSION] VARIABLES
    [LIKE 'pattern' | WHERE expr]
```

查看具体两个关键的变量 `innodb_table_locks` 和 `autocommit` ：

```sql
-- 查看 innodb_table_locks 和 autocommit 是否开启
SHOW VARIABLES WHERE Variable_name = 'innodb_table_locks' OR Variable_name = 'autocommit';
```

应该看到如下的数据：

<img src="https://s6.jpg.cm/2021/12/21/LcKubQ.png" />

两个变量都是 `ON` 的话说明可以使用 InnoDB 的表锁，如果没有打开，可以通过以下的命令手动打开：

```sql
SET variable = expr [, variable = expr] ...

variable: {
    user_var_name
  | param_name
  | local_var_name
  | {GLOBAL | @@GLOBAL.} system_var_name
  | [SESSION | @@SESSION. | @@] system_var_name
}
```

具体的，当前需要打开 `innodb_table_lock` 和 `autocommit`，可以这么做：

```sql
SET GLOBAL innodb_table_locks=1;
SET GLOBAL autocommit=1;
```

显式地加上表锁：

```sql
-- 显式地给 meeting 表加上 S 锁
LOCK TABLES meeting READ;
-- 显式地给 meeting 表机上 X 锁
LOCK TABLES meeting WRITE ;
```



<br />

### InnoDB 中的意向锁

当要对表加上 S 锁时，需要表和表中的记录没有持有 X 锁；当要对表加上 X 锁时，需要表中的记录和表都没有持有 X 锁或 S 锁。表上的锁比较容易判断，但是问题在于如何判断表中的记录是否持有相关的锁？为了解决这个问题，InnoDB 引入了意向锁的概念：

- 意向共享锁（IS 锁）

    Intention Shared Lock：当事务准备在某条记录上加上 S 锁时，首先需要在 **表级别** 上加上一个 IS 锁

- 意向独占锁（IX 锁）

    Intention Exclusive Lock：当事务准备在某条记录上加上 X 锁时，首先需要在 **表级别** 上加上一个 IX 锁

IS 锁和 IX 锁都是表级锁，提出的目的是为了在加上表级别的 S 锁和 X 锁时，能够快速地判断表中的记录是否有被上锁，如果没有 IS 锁和 IX 锁，那么就需要遍历整个表来检测是否有记录持有行级的 S 锁或 X 锁

表级别锁之间的兼容性如下表所示：

| 兼容性 |   X    |   IX   |   S    |   IS   |
| :----: | :----: | :----: | :----: | :----: |
|   X    | 不兼容 | 不兼容 | 不兼容 | 不兼容 |
|   IX   | 不兼容 |  兼容  | 不兼容 |  兼容  |
|   S    | 不兼容 | 不兼容 |  兼容  |  兼容  |
|   IS   | 不兼容 |  兼容  |  兼容  |  兼容  |



<br />

### AUTO-INC 锁

当 MySQL 自动地给 `AUTO_INCREMENT` 类型的字段进行递增赋值的操作时，主要的实现有以下两种方式：

- AUTO-INC 锁

    在执行插入语句时，给当前的表加上一个表级别的 `AUTO-INC` 锁，然后为每条待插入记录的 `AUTO_INCREMENT` 列分配递增的值，在该语句结束之后，再释放 `AUTO-INC` 锁。

    `AUTO-INC` 锁的作用范围只是 **单个插入** 语句，在插入语句执行完成之后，这个锁就被释放了

- 轻量级锁

    在通过 `AUTO_INCREMENT` 获得修饰的列的值时获取这个轻量级锁，就把该轻量级锁释放掉，而不需要等待整个插入语句执行完成之后再释放

InnoDB 通过 `innodb_autoinc_lock_mode` 变量来决定采用的方式，该变量主要有三个值：

- `innodb_autoinc_lock_mode = 0`

    该模式下表示使用 `AUTO_INC` 锁来实现自增

- `innodb_autoinc_lock_mode = 1`

    该模式下表示一律使用轻量级锁的方式来实现 `AUTO_INCREMENT` 列的自增

- `innodb_autoinc_lock_mode = 2`

    两种方式混用，当插入的记录的数量确定时采用轻量级锁，记录数量不确定时使用 `AUTO-INC` 锁。具体的插入的记录的数量不确定的情况：INSERT …… SELECT、REPLACE …… SELECT、LOAD DATA 等

<br />

### InnoDB 锁的内存结构

前文已经提到，对于单条记录的修改将会隐式地创建一个锁结构来保护这条记录，防止由于多个事务同时修改造成的一致性错误。当有多个记录要进行修改时，当满足以下几个条件时，可以将这些修改的记录放入到一个锁结构中：

1.  加锁操作时在同一个事务中
2. 需要加锁的记录都在同一个 Page 中
3. 需要加锁的类型是一致的
4. 锁的等待状态是一致的

<br />

锁结构主要由 6 部分组成，分别为：事务信息、索引信息、表锁或行锁信息、type_mode、其它信息和 与 heap_no 对应的比特位，具体结构如下图所示：<sup>[1]</sup>

<img src="https://mmbiz.qpic.cn/mmbiz_png/AZHyCoMMOCicBd8icwKDibjXU5J26eb2qJialluvkkccXHJSXNV5n1P49AkVXkccwRN1icljCqN9VrdI1SibwJYycZSQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1" style="zoom:60%">



具体的解释如下：

- 锁所在的事务信息：一个锁结构对应一个事务，这个字段存储了锁对应的事务信息。这个字段实际上只是一个指针，可以通过它获取到内存中关于该事务的更多信息，如：事务 id 等

- 索引信息：对于行级锁来说，这里记录的就是加锁的记录属于哪个索引

- 表锁/行锁 信息

  对于表锁来讲，这里是用于记录对哪张表进行的加锁操作以及其它的信息；

  对于行锁来讲，内容包括三部分：

  - Space ID：记录所在的表空间 ID
  - Page Number：记录所在的页号
  - n_bits：一条记录对应一个 bit，当对多条记录进行加锁操作时，就会对应多个 bit，此时这个值就是单纯地为了记录有多少个 bit，而具体哪条记录对应哪个 bit，是在 “与 heap_no 对应的比特位” 这块内容中定义的映射关系。为了之后在页面中插入新记录时不至于重新分配锁结构，n_bits 的值一般都会 Page 中记录的数目要多一些

- type_mod：由 32 个 bit 组成，分别为 lock_mod、lock_type、lock_wait 和 rec_lock_type，具体如下图所示：<sup>[1]</sup>

  <img src="https://mmbiz.qpic.cn/mmbiz_png/AZHyCoMMOCicBd8icwKDibjXU5J26eb2qJianE3IcFNbG4x4Y3pBV455eCGPr2kh008iadlayMGXMebyk9DrZuwlryA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1" style="zoom:80%">

- 其它信息：为了更好地管理系统运行过程中生成的各种锁结构，设计了各种哈希表和链表

- 与 heap_no 对应的比特位

  如果是行级锁，会通过这部分的比特位来对应 n_bit 属性的值，在每条记录的头信息中保存一个 heap_no 的属性，这个属性的作用是表示记录在堆中的具体位置。而当前这个字段的目的是将 heap_no 和 n_bits 对应起来

<br />

假设现在开启了一个事务 T1，向 `tb_user` 表（表中已经存在 5 条数据，表空间为 67）中页号为 3 的 Page 中插入一条 number = 15 的数据（number 是主键），并为这个记录加上 S 锁，现在分析一下行级锁结构：

- 由于开启的是事务 T1，所以 “锁所在的事务信息” 指的就是 T1 这个事务

- 由于要直接对 number 这个聚簇索引加锁，因此 “索引信息” 就是聚簇索引

- 由于是行级锁，在当前上下文环境中，Space ID 为 67，Page Number 为 3，n_bits 需要按照下列公式计算

  $n\_bits = (1+floor((n\_recs + LOCK\_PAGE\_BITMAP\_MARGIN)/8))\times8$

  其中，$floor()$ 表示向下取整，$n\_recs$ 表示包含哑记录（`Infimum` 和 `Supuremum`）在内的记录总条数，加上此时 `tb_user` 中已经存在了 5 条记录，因此此时 $n\_recs=7$ ，$LOCK\_PAGE\_BITMAP\_MARGIN$ 可以认为是一个魔数，默认为 64，因此，此时的 $n\_bits=(1+floor((7+64)/8))\times8 = 72$

- type_mode：参考上文的对照表，lock_mode = 2，lock_type=32，lock_wait=0，rec_lock_type=1024，总的结果 `type_mode=1024|32|0|2=1058`

- 其它信息：在此略过

- 与 heap_no 对应的比特位：因为之前已经存在 5 条记录，所以 number = 15 对应的 no_heap = 7，它对应的 bit 位如下图所示：<sup>[1]</sup>

  <img src="https://mmbiz.qpic.cn/mmbiz_png/AZHyCoMMOCicBd8icwKDibjXU5J26eb2qJiasfNicGQccCOOjfZbraspElcWtC6H0ic38gGILYcctiaGfQvzqsTJicdHDQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1" style="zoom:80%">

  此时整个锁结构看起来可能如下所示：<sup>[1]</sup>

  <img src="https://mmbiz.qpic.cn/mmbiz_png/AZHyCoMMOCicBd8icwKDibjXU5J26eb2qJiaviardQdHjiao8eNSUIR3JCE5f5Vl5cYsRW5UGzOHbJ1g5ibYsUB4cW28Q/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1" style="zoom:80%">

  

<br />

## 语句加锁分析

首先，假设现在的表结构和索引结构如下图u所示：<sup>[1]</sup>

<img src="https://mmbiz.qpic.cn/mmbiz_png/AZHyCoMMOCicBd8icwKDibjXU5J26eb2qJiatic8oNqdjWATibEjyq5rH1kVWoTSmhKWvKSMeibhE6N1ZM2Q6Xg1oVyVA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1" style="zoom:80%">

<br />

### 普通的 SELECT

普通的 SELECT 语句在没有使用 serializable 的隔离级别时，都不会进行加锁。对于使用 serializable 的隔离级别，视情况进行加锁。

隔离级别与加锁方式的对应规则如下表所示：

|     隔离级别     | 加锁方式                                                     |                  存在的问题                  |
| :--------------: | :----------------------------------------------------------- | :------------------------------------------: |
| Read Uncommitted | 不加锁，直接记录最新的版本                                   |     可能出现脏读、不可重复读、幻读的问题     |
|  Read Committed  | 不加锁，每次执行 SELECT 时都会生成一个 ReadView，可以避免脏读 |        可能出现不可重复读和幻读的问题        |
| Repeatable Read  | 不加锁，只有在第一次执行 SELECT 时才会生成一个 ReadView      | 可以很大程度上解决幻读问题，但是不能完全解决 |
|   Serializable   | 当 autocommit = 0，即自动提交未打开时，<br/>SELECT 会被转换成 SELECT …… LOCK IN SHARED MODE，<br/>在这种情况下将会给记录加上 S 锁。当 autocommit=1，即自动提交打开时，SELECT 语句不会加锁，只是利用 MVCC 生成一个 ReadView 来读取记录。<br/>这是因为启动了自动提交，意味着一个事务中只包含一条语句，<br/>一个事务只执行一条 SQL 语句自然不会导致 “幻读” |     不会出现脏读、不可重复读和幻读的问题     |

<br />

### 锁定读

对于锁定读的语句，可以归结为以下四种语句：<sup>[1]</sup>

- 语句 1：`SELECT …… LOCK IN SHARED MODE`

    根据隔离级别加上对应的 S 记录锁或 next-key 锁

- 语句 2：`SELECT …… FOR UPDATE `

    根据隔离级别加上 X 记录锁或 next-key 锁

- 语句 3：`UPDATE ……`

    当更新二级索引时，所有被更新的二级索引节点都会加上与 X 记录锁功能相同的隐式锁，其它与 `SELECT …… FOR UPDATE` 类似

    对于隔离级别为 “READ UNCOMMITTED” 和 “READ COMMITTED” 的情况，采用的是一种 **半一致读** 的方式来执行 `UPDATE` 语句

- 语句 4：`DELETE ……`

    与 `UPDATE` 类似，当表中包含二级索引，那么在二级索引记录在被删除之前都需要加上与 X 记录锁功能相同的隐式锁

注意：之所以语句 3 和 语句 4 都算做是锁定读，这是因为在 `UPDATE` 或 `DELETE` 时都需要隐式地查找相应的数据，因此也被视为是一种锁定读

<br />

锁定读的大致流程：

1. 快速在 B+ 树中定位到该扫描区间（即 `SELECT` 的查询区间）中的第一条记录，把该记录作为当前记录

2. 根据不同的隔离级别，为当前的记录加上不同类型的锁，具体如下表所示：<sup>[1]</sup>

   <img src="https://mmbiz.qpic.cn/mmbiz_png/AZHyCoMMOCicBd8icwKDibjXU5J26eb2qJiaxqia60iacmMVovK7pxBRdhBianT5YJ4zyFJfxsHyYpwYic82OsLrC6Pjbw/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1" style="zoom:60%">

3. 判断索引条件下推（ICP，Index Condition Pushdown）的条件是否成立，如果符合索引条件下推，则执行步骤 4；否则，获取记录所在的单向链表的下一条记录，并作为新的记录，跳到步骤 2 继续执行

   除此之外，本步骤还会判断当前记录是否符合扫描区间的边界条件，如果超出了扫描边界，则跳过步骤 4 和步骤 5，直接向 server 层返回查询完毕。**注意：步骤 3 不会释放锁**

   ICP 只适用于二级索引，并且只适用于 `SELECT` 语句。他是用来把查询中与被使用索引相关的搜索条件下推到存储引擎中去判断，而不是返回到 server 层再去判断，ICP 只是为了减少回表的次数，也就是减少读取完整的聚簇索引记录的次数，从而减少 IO 的次数

   <br />

   ICP 的官方解释：

   > Index Condition Pushdown (ICP) is an optimization for the case where MySQL retrieves rows from a table using an index. Without ICP, the storage engine traverses the index to locate rows in the base table and returns them to the MySQL server which evaluates the `WHERE` condition for the rows. With ICP enabled, and if parts of the `WHERE` condition can be evaluated by using only columns from the index, the MySQL server pushes this part of the `WHERE` condition down to the storage engine. The storage engine then evaluates the pushed index condition by using the index entry and only if this is satisfied is the row read from the table. ICP can reduce the number of times the storage engine must access the base table and the number of times the MySQL server must access the storage engine.

   大致翻译如下：

   > 索引条件下推（ICP）是针对 MySQL 在使用索引从表中提取数据的情况下所做的优化。如果没有使用 ICP，存储引擎就需要遍历所有的索引用于定位表中记录所在的位置，然后把这些记录返回给 MySQL Server，MySQL Server 再评估这些 WHERE 条件。如果使用了 ICP，并且 WHERE 条件的一部分可以仅使用索引中的列进行评估，那么 MySQL 就会将 WHERE 条件的这部分下推到存储引擎。然后存储引擎使用索引条目评估推送的索引条件，仅当满足该条件时才从表中读取记录。ICP 能够减少存储引擎必须访问基础表的次数以及减少 MySQL Server 访问存储引擎的次数

   使用到了 <a href="https://translate.google.cn/?sl=en&tl=zh-CN&op=translate">Google 翻译</a>，并结合了一些自身的理解。

   值得注意的一点是：InnoDB 的 ICP 只支持二级索引，并且需要访问整个表的所有记录的时候

4. 执行回表操作，获取到对应的聚簇索引记录，并加锁

5. 判断边界条件是否成立，如果还在边界内，则执行步骤 6；否则，如果隔离级别为 Read Uncommitted 或 Read Committed，则需要释放掉加在该记录上面的锁，如果隔离级别为 Repeatable Read 或 Serializable，则不会释放记录上面的锁

6. server 层判断其余搜索条件是否成立，如果不满足搜索条件，也要像步骤 5 中描述的那样，根据不同的隔离级别来确定对当前记录是否加锁或者释放锁

7. 获取当前记录所在的单向链表的下一条记录，并跳到步骤 2

<br />

注意：

- 当隔离级别为 “READ UNCOMMITTED” 或 “READ COMMITTED” 时，如果匹配的模式为 “精准匹配”，那么将不会为扫描区间后面的一条记录加锁，如以下面的 SQL 语句为例

    ```sql
    SELECT * FROM tb_user WHERE name='tom' FOR UPDATE
    ```

- 当隔离级别为 “REPEATABLE READ”  或 “SERIALIZABLE” 时，如果匹配的模式为 “精准匹配“，那么将会为扫描区间后的一条记录加上间隙锁

- 当二级索引无法查找到数据时，并且此时隔离级别为 隔离级别为 “REPEATABLE READ” 或 “SERIALIZABLE” ，如果此时的查找方式为精确查找，那么会为扫描区间的下一条记录加上**间隙锁**；如果不是精确查找，那么会为扫描区间的下一条记录加上一个 **next-key 锁**

- 当隔离级别为 “REPEATABLE READ” 或 “SERIALIZABLE” ，使用聚簇索引，并且扫描区间为左闭区间，如果定位到的第一个聚簇索引记录的 number 值正好与扫描区间中的最小值相同，那么会为该聚簇索引加上 X类型的**记录锁**，参考上面介绍的，在 “REPEATABLE READ” 或 “SERIALIZABLE”  隔离级别下将会为记录加上 next-key 锁，注意这里的不同

- 当隔离级别为 “REPEATABLE READ” 或 “SERIALIZABLE” ，使用自右向左的方式扫描记录，会给匹配到的第一条记录的下一条记录加上 **间隙锁**

<br />

### 半一致性读

**当隔离级别为 “READ UNCOMMITTED” 或 “READ COMMITTED” 时**，执行 `UPDATE` 语句时将会使用半一致性读

半一致性读，有关的介绍如下：<sup>[2]</sup> 

> - 是一种用在 Update 语句中的读操作（一致性读）的优化，是在 RC 事务隔离级别下与一致性读的结合。
> - 当 Update 语句的 where 条件中匹配到的记录已经上锁，会再次去 InnoDB 引擎层读取对应的行记录，判断是否真的需要上锁（第一次需要由 InnoDB 先返回一个最新的已提交版本）。
> - 只在 RC 事务隔离级别下或者是设置了 innodb_locks_unsafe_for_binlog=1 的情况下才会发生。
> - innodb_locks_unsafe_for_binlog 参数在 8.0 版本中已被去除（可见，这是一个可能会导致数据不一致的参数，官方也不建议使用了）。

当 `UPDATE` 语句读取到被其它事务加了 X 锁的记录时， InnoDB 会将该记录的最新版本读取出来，然后判断该版本是否与 `UPDATE` 语句中的满足 `WHERE` 的后继条件。如果不满足，则不对该记录进行加锁，从而跳到下一条记录； 如果满足，则再次读取该记录并对其进行加锁。这样就可以减少在执行 `UPDATE` 的过程中被阻塞的概率



<br />

### INSERT

insert 语句在一般情况下都不需要在内存中生成锁结构，单纯地依靠隐式锁保护插入的记录

在当前事务中插入一条记录之前，首先需要定位当前记录在 B+ 树中的位置。如果该位置的下一条记录已经被添加了间隙锁或者 next-key 锁，那么在当前的记录加上意向锁，然后当前的事务进入到等待状态

在执行 `INSERT` 语句时会生成锁结构的两种特殊情况：

- 重复键

    当插入的记录的主键在表中已经存在了，那么将会出现插入异常的情况。但是在此之前，会对该主键值加上 S 锁。具体地，当隔离级别为 “READ UNCOMMITTED” 或 “READ COMMITTED” 时，会加上 S 型的**记录锁**；当隔离级别为 ”REPEATABLE READ“ 或 ”SERIALIZABLE“ 时，会加上 S型的 next-key 锁

    当与唯一的二级索引重复，在这种情况下，无论什么隔离级别，都会对已经存在的 B+ 树中的那条唯一的二级索引加上 next-key 锁

    在使用 `INSERT...ON DUPLICATE KEY...` 这样的语法来插入记录时，如果遇到主键或者唯一的二级索引列的值重复，会对 B+ 树中已经存在的相同的键的记录加上 X 锁，而不是 S 锁

- 外键检查

    当插入记录的外键在主表中能够找到，那么在插入成功之前，无论当前事务的隔离级别是什么，只需要直接给主表对应的那条记录加上 S 型的记录锁即可

    当插入记录的外键在主表中无法被找到，在这种情况下，就需要对隔离级别进行分类处理

    - 当隔离级别为  “READ UNCOMMITTED” 或 “READ COMMITTED” 时，并不对记录进行加锁
    - 当隔离级别为  ”REPEATABLE READ“ 或 ”SERIALIZABLE“ ，对主表查询不到的那个键附近的记录加上 **间隙锁**



<br />

参考

<sup>[1]</sup> https://mp.weixin.qq.com/s/9LRFYGquXWpMCeyAonNcMQ

<sup>[2]</sup> https://cloud.tencent.com/developer/article/1651628