# MySQL 体系结构

<img src="https://media.geeksforgeeks.org/wp-content/uploads/20210211183907/MySQLArchi.png" />

<img src="https://onurdesk.com/wp-content/uploads/2020/12/image-4.png" />

## 各个存储引擎之间的比较

- MyISAM：不支持事务，只支持表锁，支持全文索引，主要面向一些 OLAP 的数据库应用。对于使用 MyISAM 存储引擎的表，MySQL 只会缓存索引文件，而不会缓存数据文件

- InnoDB：支持事务，支持行锁，支持外键。通过 MVCC 来获得事务的高并发性，并且实现了 SQL 的四种隔离级别。

- NDB：集群存储引擎，能够提高可用性。将数据存放在内存中，因此主键查找的速度特别快。对于连接查询操作，将会放入下一层 File System 层中进行处理，因此连接查询性能较差

- Memory：
