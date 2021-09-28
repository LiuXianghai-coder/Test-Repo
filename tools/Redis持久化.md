## `Redis` 数据持久化

## `RDB`

支持手工执行和服务端定期执行。持久化的内容为二进制数据文件

```c
// server.h
struct redisServer {
    ……………………
    // 保存 saveparams 数组
    struct saveparam *saveparams;   /* Save points array for RDB */
    // 修改记录计数器，记录上一次成功执行 SAVE 或者 BGSAVE 后，数据进行了多少次修改（包括写入、删除、更新等操作）
    long long dirty;                /* Changes to DB from the last save */
    
    // 上一次执行保存的时间，记录上一次成功执行 SAVE 或者 BGSAVE 的时间
    time_t lastsave;                /* Unix time of last successful save */
    ……………………
};

// server.h
struct saveparam {
    // 执行的秒数
    time_t seconds;
    // 修改的次数
    int changes;
    // 只有在两个条件都满足的情况下，才会执行一次保存操作
    // 具体配置为 save <seconds> <changes> （redis.conf）
};
```





## `AOF`

> `AOF`：Append Only File。通过记录 Redis 命令来记录数据库的变更

客户端——> `Redis` 服务器 ——> 执行命令 ——> 保存执行的i命令 ——> `AOF` 文件

```c
// server.h
struct redisServer {
    …………………………
    sds aof_buf;      /* AOF buffer, written before entering the event loop */
    …………………………
};
```



`aof_buf` ：在 `redis.conf` 中配置 `AOF` ，开启 `AOF`，每次执行完命令，就会把命令写入到 `aof_buf` 中。

### `AOF` 备份流程：

1. 处理命令请求和响应

2. 处理事件时间（`Server Cron` 函数）

3. 判断是否要写入 `AOF`（具体由配置决定）

4. 将 `aof_buf` 中的内容写入到磁盘中

   具体流程：

![1.png](https://i.loli.net/2021/09/28/oDApTJi2kH6zfVR.png)

- `appendfsync always`

  将 `aof_buf` 中的内容写入并且同步到 `AOF` 文件中，真正把指令存入了磁盘。

  优点：数据不会丢失

  缺点：效率低

- `appendfsync everysec`

  将 `aof_buf` 中的内容写入到 `AOF` 文件。上次同步时间距离现在时间超过 1 s，则执行 `AOF` 同步

-  `appendfsync no`

  将 `aof_buf` 中的内容写入到 `AOF` 文件。但是不对 `AOF` 同步，由操作系统决定



### `AOF`恢复流程：

1. 创建一个伪客户端

2. 读取 `AOF` 文件中的命令数据，依次执行

3. 当所有命令都被执行完成时，流程结束

   具体流程：

   ![1.png](https://i.loli.net/2021/09/28/xZCzTmLHAblv4eu.png)

### 操作系统层面写入与同步：

1. 调用系统函数 `write`，将内容写入到操作系统缓冲区

2. 操作系统决定何时将缓冲区内的数据写入到磁盘

   具体流程如下所示:

   <img src="https://i.loli.net/2021/09/28/wDBrxTAyH3otzMk.png" alt="1.png" style="zoom:80%;" />





### `AOF` 重写

`AOF`  存在的缺陷：

1. `AOF` 越来越大，造成空间的浪费，数据加载也会非常慢
2. 多条执行的命令，有很大的几率都是多余的

解决方案：

- `AOF` 重写：通过读取 `Redis` 中存在的键的值，转换为对应的 `Redis` 命令再保存到 `AOF` 文件中

  - 配置

  ```bash
  // 比上次重写后的比例增加了 100%
  auto-aof-rewrite-percentage 100
  // 并且 aof 文件体积超过 64mb 的情况下，才会发生重写
  auto-aof-rewrite-min-size 64mb
  ```

  通过 `fork` 一个子进程进行 `AOF` 重写。使得主进程不会被阻塞

  针对数据不一致的情况，`Redis` 设置了一个 `AOF` 重写缓冲区，子进程在建立时会使用该缓冲区内的内容。

  具体流程如下：

  ![1.png](https://i.loli.net/2021/09/28/9KMTmlOjVrYw6cp.png)

1. 首先判断是否满足重写 `AOF` 的条件，如果不满足，那么执行一般的 `AOF` 写入，将输入的命令保存到 `AOF` 缓冲区，再进行进一步的写入磁盘操作

2. 如果满足重写 `AOF` 的条件，那么就执行 `AOF` 重写。此时主进程将会 `fork` 一个子进程，让子进程完成重写的操作。

3. 然而 `fork` 子进程之后主进程依旧要接收命令，因此在 `fork`一个子进程之后将会创建一个 `AOF` 重写缓冲区，再 `fork` 子进程之后的所有命令都会写入到这个缓冲区。

4. 当子进程完成`AOF` 的重写任务之后，将会给主进程发送一个信号，使得主进程将 `AOF` 重写缓冲区内的内容添加到子进程写入的新的 `AOF` 文件中

5. 最后使用新的 `AOF` 文件替换掉旧的 `AOF` 文件，完成 `AOF` 的重写任务

   **注意：**在主进程处理子进程发送的信号和将 `AOF` 缓冲区内容写入到新的 `AOF` 文件中的过程中，主进程是阻塞的

