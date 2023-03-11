# SQL 的递归查询

在一般的业务场景中，特别是针对相关的业务线相关的功能开发时，可能会遇到一些具有层级关系的数据关联结构。比如，一个员工可能属于一个领导管辖，而同时，这个领导也被另一个更高级别的领导管辖……，而本质上这些领导本身也是一个公司的员工，和普通员工有着相同的属性。因此，在为个功能设计相关的表结构时，单独为领导角色创建一个对应的表不是一个可靠的解决方案。

针对上文提到的业务情况，在**一个员工只有一个领导的前提下**，我提出我个人想到的解决思路：由于员工和领导具有相同的属性，那么可以尝试将领导和普通员工的数据放入同一个表中。同时，在表中新增一个字段 `p_id`，表示当前员工的领导的 `id`，这样，最后的结果将会是每条员工的的数据都包含一个指向对应领导的指针（对于 CEO 一类角色 `p_id` 设置为 `NULL`）。在这种情况下，数据将会通过 `p_id` 组成一种类似链表的结构：

![employee.png](https://s2.loli.net/2022/07/03/TpuHsIqWUDwhtNl.png)

为此，创建样例数据库脚本文件：

```sql
CREATE TABLE employee
(
    id   INT NOT NULL PRIMARY KEY,
    name VARCHAR(32),
    p_id INT
);

INSERT INTO employee (id, name, p_id) VALUES (1, 'CEO', null);
INSERT INTO employee (id, name, p_id) VALUES (2, '董事长', 1);
INSERT INTO employee (id, name, p_id) VALUES (3, '经理', 2);
INSERT INTO employee (id, name, p_id) VALUES (4, '组长', 3);
INSERT INTO employee (id, name, p_id) VALUES (5, '员工', 4);
```

然而，这样的设计使得 SQL 的查询操作变得不是那么简单。使用以下的左外连接的方式可以查询对应的关联关系：

``` sql
SELECT E.id, E.name AS "Employee", M.id, M.name AS "Manager"
FROM employee E
LEFT OUTER JOIN employee M
ON E.p_id = M.id;
```

在 `id` 是严格递增的前提下，可以通过相关的 `WHERE` 语句来查找指定记录的所有父级节点，例如，在上面的例子中，如果需要查询 `id` 为 $3$ 的员工的所有的领导，那么加上 `WHERE` 语句可以达到这个目的：

``` sql
SELECT E.id, E.name AS "Employee", M.id, M.name AS "Manager"
FROM employee E
LEFT OUTER JOIN employee M
ON E.p_id = M.id
WHERE E.id <= 3
```

这种方式可行的前提条件是 `id` 必须是严格递增的（`p_id` 必须小于当前记录的 `id`），因此对于一般的场景这种方式并不适用

在大部分的关系数据库管理系统中，都提供了递归查询的语句以支持递归查询（MySQL 8.0 后支持、PostgreSQL、SQL Server、Oracle 都支持），具体如下所示：

``` sql
WITH RECURSIVE cte AS (
    SELECT *, 1 AS lv -- lv 自定义列用于记录递归查询的深度
    FROM employee
    WHERE employee.id = 3 -- 这里为初始查询语句，这里我们将会将 id 为 3 的员工记录作为初始记录
    UNION
    SELECT employee.*, lv + 1 -- 每次递归调用时都需要将深度 +1
    FROM employee
    JOIN cte ON employee.id = cte.p_id -- cte 当前查询到的记录，通过这个记录递归进行查询
)
SELECT *
FROM cte
ORDER BY id; -- 该语句是实际的调用者
```

通过上面的语句进行查询，对应的查询结果如下（在 MySQL 8.0 以及 PostgreSQL 上测试）：

``` text
+------+-----------+------+------+
| id   | name      | p_id | lv   |
+------+-----------+------+------+
|    1 | CEO       | NULL |    3 |
|    2 | 董事长    |    1 |    2 |
|    3 | 经理      |    2 |    1 |
+------+-----------+------+------+
```

相比较之前通过左外连接的方式进行的查询，递归查询的方式可以有效地查询相关的父节点。这种查询方式存在的一个缺点在于当递归查询的深度达到一定值时，查询可能会崩溃，对于这个问题，可以考虑更改数据库管理系统的相关设置。例如，对于 MySQL 来讲，可以通过设置相关的全局变量来设置最大递归查询深度：

``` sql
SET @@GLOBAL.max_sp_recursion_depth = 255; -- 设置全局最大查询深度
```

在实际的业务场景中，可能一个员工并不只有一个直系领导，还可能存在多个直接领导。对于这种情况，单独的 `p_id` 已经无法再满足实际的需要了。在这种情况下，需要建立一张单独的表来存储这些关联关系：

``` sql
CREATE TABLE ep_rel (
    id INT NOT NULL PRIMARY KEY , -- 当前表的主键
    ep_id INT NOT NULL , -- 员工 id
    p_id INT NOT NULL -- 员工领导的 id
);

INSERT INTO ep_rel (id, ep_id, p_id) VALUES (1, 5, 4);
INSERT INTO ep_rel (id, ep_id, p_id) VALUES (2, 5, 3);
INSERT INTO ep_rel (id, ep_id, p_id) VALUES (3, 5, 2);
INSERT INTO ep_rel (id, ep_id, p_id) VALUES (4, 5, 1);
```

现在员工被设置为所有领导都会直接管辖了，和上文的递归查询类似，只是在进行递归查询的时候需要再进行一次内连接：

``` sql
WITH RECURSIVE ep_cte AS (
    SELECT *, 1 AS lv
    FROM employee
    WHERE id = 5 -- 初始员工记录查询
    
    UNION ALL
    
    SELECT employee.*, lv + 1
    FROM ep_cte
    JOIN ep_rel ON ep_cte.id = ep_rel.ep_id
    JOIN employee ON ep_rel.p_id = employee.id -- 关联到的父节点记录
)
SELECT * FROM ep_cte;
```

对应的查询结果如下：

``` text
+------+-----------+------+------+
| id   | name      | p_id | lv   |
+------+-----------+------+------+
|    5 | 员工      |    4 |    1 |
|    4 | 组长      |    3 |    2 |
|    3 | 经理      |    2 |    2 |
|    2 | 董事长    |    1 |    2 |
|    1 | CEO       | NULL |    2 |
+------+-----------+------+------+
```

注意这里通过 `lv` 列来标识递归的层级，因此 `lv` 相同说名查询处于同一递归查询深度

<br />

参考：

<sup>[1]</sup> https://web.archive.org/web/20180729174436/http://www.tomjewett.com/dbdesign/dbdesign.php?page=recursive.php

<sup>[2]</sup> https://www.sqlshack.com/mysql-recursive-queries/

<sup>[3]</sup> https://www.mysqltutorial.org/mysql-recursive-cte/

<sup>[4]</sup> https://stackoverflow.com/questions/20215744/how-to-create-a-mysql-hierarchical-recursive-query