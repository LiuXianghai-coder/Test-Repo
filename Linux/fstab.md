# fstab

在一般的 `Unix` 或者 类`Unix` 中，为了更好地管理磁盘资源，有时不得不挂载一个外部的磁盘，使用 `mount` 命令可以快速地挂载一个外部磁盘，具体用法为：

```shell
# 将磁盘分区 sda2 挂载在 /mnt 上
mount /dev/sda2 /mnt

# 要查看有哪些磁盘分区是，可以通过 fdisk 命令来查看，添加对应地一些选项可以看到一些详细信息
# -l 表示列出分区
fdisk -l
```

挂载的时候会加载 `/etc/fstab` 内的配置选项，在挂载磁盘时使用对应的配置信息，如挂载的磁盘所用的文件系统类型等，下面是一个典型的 `/etc/fstab` 的内容：
<img src="https://geek-university.com/wp-content/images/linux/etc_fstab_file1.jpg" /> 

每一行都对应着一块磁盘分区的挂载配置，每行总共有 6 个字段用于指定相关的配置信息，每个配置信息用一个或多个空格分开（注意 `Unix` 会把 `Tab` 转换为相同长度的空格）。以 “#” 开头的行的配置信息将会被忽略

这 6 个字段的对应信息如下所示（从左到右）：

- Device：第一个配置字段，表示挂载的磁盘分区，这些分区一般都可以在 `/device/` 下找到，或者通过 `fdisk` 命令也可以查看到，但是现在大部分都是通过使用分区的 `UUID` 或者是对应的标签来指定（可以通过 `blkid /dev/sda1` 来查看 `/dev/sda1` 的 `UUID` 和标签）

- Mount Point：第二个配置字段，表示挂载当前的磁盘分区的挂载位置，挂载完成之后这个目录就是磁盘分区的挂载点，可以通过这个挂载点来访问磁盘分区。指定的挂载位置最好指定一个空的目录。

- File System Type：第三个配置字段，要挂载的磁盘分区的文件类型。同样地，可以通过 `blkid` 命令来查看对应地磁盘分区所属的文件系统类型

-  Options：第四个配置字段，表示挂载时内核会如何处理挂载的磁盘分区，这个字段可以同时指定多个选项，一般常见的可用选项如下所示：

  - `auto`、`noauto`

    `auto` 表示在 `boot` 引导系统启动时自动将这个磁盘分区进行挂载，`noauto`则表示这个磁盘分区应当被用户显式地进行挂载，即手动地挂载。当执行 `mount -a` 命令挂载 `fstab` 中的分区时，所有设置了 `auto` 选项的磁盘分区都会自动地进行挂载

  - `exec`、`noexec`

    `exec` 表示驻留在这个磁盘分区中地可执行文件能够被执行，而 `noexec` 则表示移除这个磁盘分区内可执行文件地可执行能力。如果要挂载的磁盘分区只是为了保留非可执行文件，那么将这些磁盘分区设置为 `noexec` 能够更好地维持系统地安全性

  - `user`、`nouser`

    `user` 选项指定能够挂载磁盘分区的用户，而 `nouser` 则表示只有 `root` 用户才能挂载分区。如果指定了能够挂载分区的用户，那么一定要确保挂载点对于用户来讲存在对应的访问权限。

    例如：

    ```bash
    # 表示只有 opensource 用户组下的 linux 用户才能将 /dev/sda5 挂载到 /mnt/sda5
    /dev/sda5 /mnt/sda5	ext4	uid=linux,gid=opensource	0	0
    ```

  - `ro`、`rw`

    `ro` 表示挂载的文件系统应当是只读的，`rw` 则表示挂载的文件系统既可以是可读的，也可以是可写的

  - `sync`、`async`

    这个选项指定了如何完成对挂载的磁盘分区的输入和输出操作。`sync`表示以同步的方式完成所有的操作，也就是说，当使用 `copy` 命令复制一个文件到挂载的磁盘分区时，会直接将数据写入到挂载的磁盘分区。而 `async` 则意味着只有在卸载时才会将数据写入到磁盘分区  

  - `suid`、`nosuid`

    `suid` 表示允许 `suid` 操作，而 `nosuid` 则表示禁止 `suid` 操作。（`suid` 表示特殊权限，具体详情可以查看 https://en.wikipedia.org/wiki/Setuid）

  - `defaults`

    Ext3 文件系统默认的选项 https://linoxide.com/explained-in-detail-linux-ext2-ext3-and-ext4-filesystem/。具体为：`rw`, `suid`, `exec`, `auto`, `nouser`, `async`

- Backup Operation：第五个配置字段，表示是否需要对当前挂载的磁盘分区使用备份程序进行备份，通过设置该字段为 1 开启备份。

- File System Check Order：第六个配置字段，表示在 `boot` 启动系统时使用 `fsck`进行磁盘分区的错误检测顺序，如果将这个字段设置为 0，则表示不需要对这个挂载的磁盘分区进行错误检测；如果是 `root` 分区需要进行磁盘检测的话，那么就需要将这个字段设置为 1，使得在 `boot` 引导启动系统时优先对这个磁盘分区进行错误检测；而其它的磁盘分区如果需要进行错误检测则将它置为 2，在系统重启时会对这个配置字段设置为 2 的所有挂载分区进行错误检测。



参考：

<sup>[1]</sup> https://geek-university.com/linux/etc-fstab-file/

<sup>[2]</sup> https://linoxide.com/understanding-each-entry-of-linux-fstab-etcfstab-file/