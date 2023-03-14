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

然而，这样的设计使得 SQL 的查询操作变得不是那么简单，如果可以：

```sql
SELECT E.id, E.name AS "Employee", M.id, M.name AS "Manager"
FROM employee E LEFT OUTER JOIN employee M
                                 ON E.p_id = M.id
WHERE E.id<=3;
```
