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

    以下面的示例为例：

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

参考

<sup>[1]</sup> https://mp.weixin.qq.com/s/9LRFYGquXWpMCeyAonNcMQ