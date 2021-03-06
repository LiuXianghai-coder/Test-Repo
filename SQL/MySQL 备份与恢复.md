# MySQL 备份与恢复

在制定备份和恢复策略时，有两个重要的需求值得参考：恢复点目标（PRO）、恢复时间目标（RTO）。它们定义了可以容忍丢失多少数据，以及需要等待多久将数据恢复

## 在线备份和离线备份

如果可以，关闭 MySQL 服务再做备份是最简单、最安全的策略，也是所有备份策略中获取一致性副本最好的策略，而且损坏或者不一致的风险最小。但是这种方案在高负载和高数据量的情况下不是一个很好的策略。

在众多的备份方案中，最大的问题是它们都会使用 `FLUSH TABLES WITH READ LOCK`，这会导致 MySQL 关闭并且锁住所有的表，并刷新查询缓存。这个过程需要的时间不是可预估的，如果全局锁需要等待一个长时间运行的语句完成，或者有许多表，那么时间会更长。除非锁被释放，否则就不能在服务器上更改任何数据，一切操作都会被阻塞和积压（包括 `SELECT` 语句）。尽管如此，这些策略依旧比关闭服务器再备份代价要低。避免使用 `FLUSH ……` 的最好方式就是只使用 "InnoDB" 存储引擎

在规划备份时，有一些与性能相关的因素需要考虑：

- 锁时间：进行备份需要持有锁多长的时间？例如在执行 `FLUSH …………` 时持有的全局锁

- 备份时间：复制备份到目的地需要多久？

- 备份负载：在复制备份到目的地时对服务器性能有多大的影响？

- 恢复时间：把备份镜像从存储位置复制到 MySQL 服务器，重放入二进制日志等操作，需要多长的时间？

最大的权衡是备份时间与备份负载，可以牺牲其中的一个以增强另一个。

## 逻辑备份和物理备份

有两种主要的方法来备份 MySQL 数据：逻辑备份（也就是 “导出”）和直接复制原始文件的物理备份。逻辑备份是将数据包含在一个 MySQL 能够解析的文件中，要么是 SQL，要么是以某个符号分隔的文本。原始文件是指存在于硬盘上的文件

这两种备份方式都各有优缺点：

逻辑备份存在的优点：

- 逻辑备份是一个文本文件，可以使用类似 `sed`、`awk` 这样的文本分析工具查看和操作

- 恢复通常比较简单，只需要将文本文件再导入回 MySQL 即可

- 可以通过网络来备份和恢复，也就是说，可以在不同的主机中进行相同的操作

- 比较灵活，可以通过对应的 SQL 语句来备份指定的内容

- 和存储引擎无关，这是因为这样做只是提取了文本数据，消除了存储引擎之间的差异

- 有助于避免数据损坏，有时存放在内存中的数据在磁盘上已经损坏了（未写入磁盘），在这种情况导出的数据可能是可靠的。

逻辑备份存在的缺点：

- 必须由数据库服务器完成逻辑备份的工作，因此需要更多的 CPU 周期

- 逻辑备份在某些场景下比数据库本身的文件还要大（因为字符编码的原因），当然，也可以考虑将这个文本文件再压缩一下

- 无法保证导出后再还原的数据一定是准确的，比如：浮点数据的表示

- 从逻辑备份中还原需要 MySQL 进行加载和执行 SQL 语句，并转换为存储格式，并重建索引，所有的这一切操作都会很慢

<br />

物理备份存在如下优点：

- 基于文件的物理备份，只需要将需要的文件复制到其它地方即可完成备份，不需要其它额外的工作来生成原始文件

- 物理备份的恢复更加简单

- MySQL 的物理备份非常容易跨平台、操作系统和 MySQL 版本（对于逻辑备份同样如此）

- 从物理备份中恢复更快，因为 MySQL 服务器不需要执行任何 SQL 和重构索引。对于一个很大的 InnoDB 表，如果无法完全加载到内存中，则物理备份的恢复要快得多。

物理备份存在的缺点：

- InnoDB 的原始文件通常比相应的逻辑备份要大得多，这是由于在 InnoDB 表中往往存在许多未使用的空间，而这些空间通常用来做数据存储以外的其它用途（插入缓冲、回滚段等）

- 物理备份并不是总是可以跨平台，有的处理器对于浮点数的处理可能使用不同的标准，有的操作系统对于文件名并不是大小写敏感等

<br />

物理备份通常更加简单高效，但是尽管如此，对于需要长期保存的备份，或者是满足法律合规要求的备份，尽量还是每隔一段时间做一次逻辑备份

一般来讲，不要假定备份（特别是物理备份）是正常的，对于 InnoDB 来说，这意味着需要启动一个 MySQL 实例，执行 InnoDB 的恢复操作，然后执行 `CHECK TABLES`。可以使用 `mysqlcheck` 来对指定的数据库的所有表执行 `CHECK TABLES` 操作

建议混合使用物理备份和逻辑备份的方式来做备份：首先使用物理备份，以此数据启动 MySQL 服务器实例并运行 `mysqlcheck`，然后再周期性地使用 `mysqldump` 执行逻辑备份。这样做可以同时获得两种备份方式的优点，也不会使得服务器在导出时有过度的负担。如果可以，可以利用文件系统的快照，生成一个快照，将该快照复制到另一个服务器上并释放，然后再测试原始文件，再执行逻辑备份

## 备份的内容

恢复的需求决定备份的内容。最简单的策略是只备份数据和表的定义，这是最低要求，在实际的产品环境中一般需要做更多的工作。以下是 MySQL 备份时需要考虑的方向：

- 非显著数据：不要忘记那些容易被忽略的数据，如 binlog 和事务日志等

- 代码：现代的 MySQL 可以自定义函数、触发器和存储过程等，如果不备份这些内容，可能会导致系统异常。这一类数据一般存储在 mysql 数据库中

- 复制配置：如果恢复一个涉及复制关系的服务器，应该备份所有与复制相关的文件，例如二进制文件、中继日志，、日志索引文件和 .info 文件。至少应该包含 `SHOW MASTER STATUS` 或 `SHOW SLAVE STATUS` 的输出。

- 服务器配置：加上要从一个新的系统上恢复数据，如果包含服务器的配置那么将会是一个特别好的体验

- 选定的操作系统文件：对于服务器配置来说，备份中对产品端服务器至关重要的任何外部配置，都十分重要。比如：Unix 类服务器中，用户组的配置、管理脚本以及 sudo 规则等

### 增量备份和差异备份

当数据量十分庞大时，一个常见的策略是做定期的增量或者差异备份。

增量备份：自从任意类型的上一次备份后所有修改做的备份

差异备份：自上次全备份之后所有改变的部分而做的备份

增量备份和差异备份都是部分备份：它们都不包含完整的数据集，因为某些数据肯定是不怎么变化的，如：客户账号。部分备份对减少服务器性能开销、备份时间以及备份空间等都很合适

部分备份有一定的风险，下面的一些建议可以适当减少这些风险：

- 备份二进制日志，可以在每次 `FLUSH LOGS` 之后来开启一个新的二进制日志，这样就只需要备份新的二进制日志

- 不要备份没有改变的表，可以通过 `SHOW TABLE STATUS` 来查看表的修改时间

- 不要备份没有改变的行。如果一个表只做插入，例如记录网页页面点击的表，那么可以增加一个时间戳的列，然后只备份自上次备份之后插入的行

- 某些数据根本需要备份，比如：临时数据、从数据仓库构建的数据

增量备份的缺点包括增加恢复复杂性、额外的风险、以及更长的恢复时间。如果可以做全量数据备份，就尽量做全量数据备份

## 管理和备份二进制日志

### 二进制日志格式

使用 `mysqlbinglog` 来查看对应的二进制日志，可能如下所示：

```textile
# at 235
#220317 10:53:40 server id 1  end_log_pos 632 CRC32 0x410ab8a8  Query   thread_id=12    exec_time=0     error_code=0    Xid = 46
…………………………
```

第一行表示日志文件内的偏移字节值（这里是 $235$）

第二行包含如下几项：

- 事件的日期和时间，MySQL 会使用它们来产生 `SET TIMSTAMP` 语句

- 员服务器的服务器 ID，对于防止复制之间无限循环和其它问题是非常有必要的

- end_log_pos，下一个事件的偏移字节值，该值对一个多语句事务中的大部分事件都是不正确的。在此类事务的执行过程中，MySQL 的主库会复制事件到一个缓冲区，但是这么做的话它不知道下一个日志事件的具体位置

- 事件类型，在这里是 Query

- 原服务器上执行事件的线程 ID，对于审计和执行 `CONNECTION_ID()` 函数很重要

- exec_time，这是语句的时间戳和写入二进制日志的时间之差，不要依赖这个值，因为它可能在复制落后的备库上会有很大的偏差

- 原服务器上事件产生的错误代码。如果事件在一个备库上重放时导致不同的错误，那么复制将会由于安全预警而失败

### 清除二进制日志

尽量不要删除二进制日志，如果可以，只要二进制日志有用，就不要删除它

使用类似下面的命令来清除 $N$ 天之前的二进制日志：

```shell
mysql -uroot -p -e "PURGE MASTER LOGS BEFORE CURRENT_DATE - INTERVAL N DAY"
```

如果希望定期自动删除日志，可以通过设置 `binlog_expire_logs_seconds` 变量来告知 MySQL 来完成这项工作：

```sql
-- 每隔 30 天清理二进制日志
mysql> SET GLOBAL binlog_expire_logs_seconds=2592000;
```

## 备份数据

### 生成逻辑备份

#### SQL 导出

SQL 导出是 `mysqldump` 默认的导出方式，如果需要导出 `lxh_db` 中表 `logs` ，那么可以执行如下的命令：

```shell
mysqldump -uroot -p lxh_db logs
```

#### 符号分隔文件备份

使用 SQL 语句 `SELECT xxx INTO OUTFILE` 以符号分隔文件格式创建数据的逻辑备份（可以使用类似 `mysqldump` 的 `--tab` 选项导出到符号分隔文件中）。符号分隔文件包含以 ASCII 展示的原始数据，没有 SQL 的注释和列名。

具体使用如下所示（单独导出 logs 表的数据）：

```sql
SELECT *
INTO OUTFILE '/tmp/logs.txt'
    FIELDS TERMINATED BY ',' -- 列数据分隔符
    OPTIONALLY ENCLOSED BY '"' LINES -- 数据封闭符
    TERMINATED BY '\n' -- 行数据分隔符
FROM logs;
```

在使用这种命令时，可能会遇到类似下面的错误信息：

```textile
The MySQL server is running with the --secure-file-priv option so it cannot execute this statement
```

针对这种错误，有以下几种解决方案：

- 将处理的文件目录放入到 `secure-file-priv` 变量指定的目录，这个目录需要手动在 `my.cnf` 或 `my.ini` 配置文件中进行修改

- 或者直接将 `secure-file-priv` 设置为空值，使得这项操作能够正常进行

修改后的 `my.ini` 配置文件如下：

```ini
[mysqld]
# set basedir to your installation path
basedir=D:\\mysql-8.0.25-winx64
# set datadir to the location of your data directory
datadir=D:\\mysql-8.0.25-winx64\\data
secure_file_priv=""
```

读取数据时，可以通过 `LOAD DATA` 语句来将数据加载到数据表中，如下所示：

```sql
LOAD DATA INFILE '/tmp/logs.txt'
INTO TABLE logs
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\n';
```

这种方式在性能上要比使用 SQL 导出的方式要好一点，但是这种方式也存在一些局限性：

- 只能备份到运行 MySQL 服务器的机器上的文件中

- 运行 MySQL 的系统必须具有目标目录的写入和读取权限，因为是由 MySQL 来执行文件的写入，而不是用户进程

- 处于安全原因，不能覆盖已经存在的文件，不管权限如何

- 不能直接导出到压缩文件中

- 在某些情况下很难进行正确的导入和导出，例如：非标准的字符集

### 文件系统快照

创建快照是减少必须持有锁的时间的一种解决方案。有时候，可以创建 InnoDB 的快照而不需要锁定。

快照对于特别用途的备份是一个非常好的方法，一个例子是在升级过程中遇到有问题而回退的情况。可以在升级之前创建一个镜像，这样如果升级存在问题，那么只需要回滚到该镜像。可以对任何不确定和有风险的操作都进行这样的操作，例如对一个巨大表做变更

#### LVM 快照

LVM 使用写时复制（COW）技术来创建快照，例如，对于整个卷的某个瞬间的逻辑副本。和  MVCC 类似，不同的地方在于 COW 只是保留一个老的数据版本

L相比复制数据到快照中，LVM 只是简单地标记创建快照的时间点，然后对快照请求数据时，实际上是从原始卷中读取的。因此，初始的复制基本上是一个瞬间就能完成的操作，不管创建的快照的卷又多大

当原始卷中的数据发生变化时，LVM 在任何写入变更之前，会复制受到影响的块到快照预留的区域中。LVM 不保留数据的多个版本，因此对原始卷中变更块的额外写入并不需要对快照做其它更多的工作。换句话说，对每个块只有第一次写入才会导致写时复制到预留的区域

现在，在快照中请求这些块时，LVM 会从复制块中而不是原始卷中读取。因此，可以看到快照中相同时间点的数据而不需要阻塞任何原始卷，具体如下图所示：

![image.png](https://s2.loli.net/2022/04/18/Se95KRkfJyY4iXj.png)

创建一个快照几乎不需要消耗什么时间，但是还是需要确保系统配置可以让你获取在备份瞬间的所有需要的文件的一致性副本，首先，需要确保系统满足以下的条件：

- 所有的 InnoDB 文件（InnoDB 的表空间文件和 InnoDB 事务日志）必须是在单个的逻辑卷（分区）。这里需要绝对的时间点一致性，LVM 不能为多余一个卷做某一个时间点的一致性快照
- 如果需要备份表定义，MySQL 的数据目录必须在相同的逻辑卷中。如果使用另外一种方法来备份表的定义，例如只是备份 Schema 到版本控制系统中，就不要担心这个问题
- 必须在卷组中有足够的空间来创建快照，具体需要多少空间取决于负载。

LVM 有卷组的概念，它可以包含一个或者多个逻辑卷，可以通过 `vgs` 命令或者 `vgdisplay` 来查看系统中的卷组：

#### 用于在线备份的 LVM 快照

首先查看一下如何在不停止 MySQL 服务的情况下备份 InnoDB 数据库，这里需要使用一个全局的读锁。连接 MySQL 服务器并使用一个全局读锁将表刷到磁盘上，然后获取二进制日志的位置：

```sql
mysql> FLUSH TABLES WITH READ LOCK; SHOW MASTER STATUS;
```

记录 `SHOW MASTER STATUS` 的输出，确保 MySQL 的连接处于打开状态，使得读锁不被释放，然后获取 LVM 的快照并立即释放该读锁，可以使用 `UNLOCK TABLES` 或者直接关闭连接来释放锁。最后加载快照并复制文件到备份位置。

这种方法的主要问题在于，获取读锁可能需要一点时间，如果存在大量查询的情况下，这个时间将会变得很长。当连接等待全局读锁时，所有的查询都将被阻塞，并且这个阻塞时间是不可预期的

> 即使锁住所有的表，InnoDB 的后台线程依旧会继续工作，因此，即使在创建快照时，仍然可以向文件中写入数据。并且，由于 InnoDB 没有执行关闭操作，如果服务器意外断电，快照中的 InnoDB 文件回合服务器意外掉电后文件的遭遇一样

#### LVM 快照无锁 InnoDB 备份

无锁备份只有一点不同，区别在于不需要执行 `FLUSH TABLES WITH READ LOCK`。这意味着不能保证 MyISAM 文件在磁盘上一致。对于 InnoDB 来讲，这不是问题。在 MySQL 的系统中依旧存在这部分的 MyISAM 表。

如果认为 MySQL 系统表可能会发生变更，那么可以锁住并刷新这些表。一般不会对这些表有长时间的查询，因此通常都会很快。

```sql
LOCK TABLE mysql.user READ, mysql.db READ, .....;
FLUSH TABLES mysql.user, mysql.db, .....;
```

由于没有使用全局锁，因此不会从 `SHOW MASTER STATUS` 中获取到任何有用的信息。

#### 规划 LVM 备份

一般考虑如下几个方面：

- LVM 只需要复制每个修改块到快照一次

- 如果只是使用 InnoDB，要考虑 InnoDB 是如何写数据的。InnoDB 实际上需要对数据写两遍，至少一半的 InnoDB 的写 IO 会到双写缓冲、日志文件以及其它磁盘上相对小的区域中。这些部分会多次重用相同的磁盘块，因此第一次时对快照有影响，但是写过一次之后就不会对快照打来太大的压力

- 对于反复修改的数据，需要评估有多少 IO 需要写入到那些还没有复制到写时复制空间的块中，对评估的结果需要保留足够的余量

- 使用 `vmstat` 和 `iostat` 来收集服务器每秒写多少块的统计信息

- 衡量复制备份到其它地方需要多久，换言之，需要在复制期间保持 LVM 快照打开多长时间

## 从备份中恢复

从备份中恢复数据或多或少要经历以下的步骤：

- 停止 MySQL 服务器

- 记录服务器的配置和文件权限

- 将数据从备份中移动到 MySQL 的数据目录

- 更改配置

- 改变文件权限

- 以限制访问模式重启服务器，等待完成启动

- 载入逻辑备份文件

- 检查和重放二进制日志

- 检测已经还原的数据

- 以完全权限重启服务器

### 恢复物理备份

// TODO

### 恢复逻辑备份

原则：使用较小的事务，多次提交，避免一次事务过大带来的问题

#### 加载 SQL 文件

对于导出的 SQL 文件，再导入回去时一般的做法是开启一个 MySQL 客户端然后运行这个文件中的 SQL，一般会执行如下的命令进行恢复：

```shell
mysql < xxx.sql
```

也可以使用客户端工具来恢复数据，使用 `SOURCE` 即可：

```sql
mysql> SET SQL_LOG_BIN=0; -- 关闭 binlog，恢复时 binlog 的写入是多余的
mysql> SOURCE xxx.sql;
mysql> SET SQL_LOG_BIN=1;
```

**注意：** 使用 `SOURCE` 时需要确保文件在 `mysql` 的运行目录下

如果备份做过压缩，那么不需要单独进行解压，Unix 的管道是一个十分好的工具：

```shell
gunzip -c xxx.sql.gz | mysql
```

如果只是想恢复单个表，那么可以将数据使用 `grep` 或者 `sed` 进行过滤再放入 MySQL 客户端执行：

```shell
grep 'INSERT INTO `table_name`'  xxx.sql | mysql table_name
```

如果对于压缩好的文件，多使用一条管道即可：

```sql
gunzip -c xxx.sql.gz | grep 'INSERT INTO `table_name`' | mysql table_name
```

如果 schema 和 data 混合在一个 SQL 文件中，只是使用 `grep` 可能很难检出 `CREATE TABLE` 这样的语句，这样的话使用 `sed` 会是一个比较好的策略：

```shell
sed -e '/./{H;$!d;}' -e 'x;/CREATE TABLE `table_name`/!d;q' schema.sql
```

**Hint：** 这段 `sed` 脚本需要是使用即可，不要过于深究

#### 加载符号分隔文件

如果是通过 `SELECT INTO OUTFILE` 的方式导出的符号分隔符文件，那么可以使用 `LOAD DATA INFILE` 通过相同的参数来加载。也可以使用 `mysqlimport`，这个工具包装了 `LOAD DATA INFILE`

`LOAD DATA INFILE` 必须直接从文本文件中读取，因此，对于压缩文件来讲，需要首先对文件进行解压，这是一个比较耗时的操作。在 Unix 类操作系统上，使用 FIFO（命名管道）可以高效的进行处理：

```shell
mkfifo /tmp/table_name.fifo
chmod 666 /tmp/table_name.fifo
gunzip -c /tmp/table.sql > /tmp/table_name.fifo
```

对于 FIFO 的介绍，可以参考 《Unix 环境高级编程》第十五章 进程间通信

现在，该 FIFO 将会等待，直到有进程读取数据，读取 FIFO 中的数据，注意在读取之前关闭 binlog：

```sql
mysql> SET SQL_LOG_BIN=0;
mysql> LOAD DATA INFILE '/tmp/table_name.info' INTO TABLE table_name;
mysql> SET SQL_LOG_BIN=1;
```

执行完成之后，`gunzip` 也会成功结束，使用 `SOURCE` 加载压缩文件数据也可以使用类似的技术

### 基于时间点的恢复

使用 binlog 可以实现从指定的时间点开始的数据恢复，可以参考 [MySQL的binlog日志 - 马丁传奇 - 博客园](https://www.cnblogs.com/martinzhang/p/3454358.html)

### 高级恢复技术

复制和基于时间点的恢复使用的是相同的技术：服务器的二进制日志。

- 用于快速恢复的延时数据
  
  如果有一个延时的备库，并且在备库执行问题语句之前就已经发现了问题，那么基于时间点的恢复就更快、更加容易
  
  恢复的过程如下：停止备库，使用 `START SLAVE UNTIL` 来重放事件直到要执行问题语句，接着执行 `SET GLOBAL SAL_SLAVE_SKIP_COUNTER=1` 来跳过问题语句，如果希望跳过多个事件，可以设置一个大于 $1$ 的值
  
  最后执行 `START SLAVE`，让备库执行完所有的中继日志

- 使用日志服务器进行恢复
  
  设置日志服务器，比使用 `mysqlbinlog` 更加可靠，更加灵活。
  具体步骤如下：
  
  1. 将需要恢复数据的服务器称为 sever-1
  
  2. 在另一台叫做 server-2 的服务器上恢复昨晚的备份，在这台服务器上运行恢复进程，以免在恢复时出现错误
  
  3. 设置日志服务器来接受 server-1 的二进制日志
  
  4. 修改 server-2 的配置文件，增加如下内容：
     
     ```ini
     replicate-do-table=sakila.payment
     ```
  
  5. 重启 server-2，然后使用 `CHANGE MASTER TO` 来让他成为日志服务器的备库，配置它从昨晚备份的二进制坐标读取，这个时候切记不要运行 `START SLAVE`
  
  6. 检测 server-2 上的 `SHOW SLAVE STATUS` 输出，验证一切正常
  
  7. 找到二进制日志中的问题语句的位置，在 server-2 上执行 `START SLAVE UNTIL` 来重放事件直到该位置
  
  8. 在 server-2 上使用 `STOP SLAVE` 停止复制进程，现在应该有被删除的表，因为现在从库停止在被删除之前的时间点
  
  9. 将所需表从 server-2 复制到 server-1
  
  只有在不存在任何多表的 `UPDATE`、`DELETE`、`INSERT` 语句时，上述流程才是可行的

### InnoDB 崩溃恢复

InnoDB 依赖于无缓存的 IO 调用和 `fsync` 调用，直到数据完全写入到物理介质上才会成功返回，如果硬件不能保证写入的持久化，InnoDB 就无法保证数据的持久，奔溃就有可能导致数据损坏

InnoDB 崩溃有以下三种情况：

- 二级索引损坏
  
  一般可以通过 `OPTIMIZE TABLE` 来修复损坏的二级索引，此外，也可以使用 `SELECT INTO OUTFILE` 来删除和重建表，然后使用 `LOAD DATA INFILE` 的方式。这些方法都是通过重新构建一个表来修改收影响的表，来修复损坏的数据

- 聚簇索引损坏
  
  如果聚簇索引损坏，也许只能使用 `innodb_force_recovery` 选项来导出表，这个过程可能会导致 InnoDB 崩溃，如果出现这样的情况，或许需要跳过导致奔溃的损坏页以导出其它记录

- 损坏系统结构
  
  系统结构包括 InnoDB 事务日志、表空间的撤销日志区域和数据字典。这种损坏可能需要做整个数据库的导出和还原，因为 InnoDB 内部的绝大部分的工作都有可能受到影响

一般可以修复损坏的二级索引而不丢失数据，然而，另外两种情形往往会导致数据的丢失，如果已经存在备份，那最好还是从备份中还原，而不是尝试从损坏的文件中去提取数据

如果必须从损坏的数据中提取数据，那么一般的过程是先尝试让 InnoDB 运行起来，然后使用 `SELECT INTO OUTFILE` 导出数据。如果此时服务器已经奔溃，并且每次启动 InnoDB 都会崩溃，那么可以配置 InnoDB 停止常规恢复和后台进程的运行。这样也许可以启动服务器，然后在缺少或不做完整性检查的情况下做逻辑备份

`innodb_force_recovery` 参数控制着 InnoDB 在启动和常规操作时要做哪一类的操作。通常情况下这个值是 $0$，可以增大到 $6$ https://dev.mysql.com/doc/refman/8.0/en/forcing-innodb-recovery.html 。当把 `innodb_force_recovery` 设置为大于 $0$ 的某个值时，InnoDB 基本上是只读的，但是仍然可以创建和删除表，这可以防止进一步的数据损坏，InnoDB 会放松一些常规检查，以便在发现坏数据时不会特意崩溃，在常规操作中，这样做是有安全保障的，但是在恢复时，最好还是避免这样做。

如果 InnoDB 的数据损坏到了已经不能启动的程度，那么就需要使用特殊的工具来检出数据来进行恢复
