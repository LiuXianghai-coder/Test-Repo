# Linux 中查看文件系统的块大小

有时可能需要查看 Unix 操作系统中有关于文件基本单元的块大小，以便对有的系统进行适当的优化（如 MySQL），本文将介绍几种在 Unix 上以及类 Unix 操作系统上可行的查看方式

<br />

## 检查文件系统<sup>[1]</sup>

- 使用 `df` 命令

  具体的命令如下：

  ```sh
  # df 命令本身是用于报告磁盘的使用情况，经过扩展 -T 选项也可以打印文件系统的类型，
  # -h 表示将使用情况转换为人类可读的
  df -Th
  
  # 如果只想查看指定分区的文件系统，也可以这么做
  # 其实就是过滤了一下输出而已，当然使用 sed 和 awk 也能够做到，
  # 这里就是只查看 /dev/sda11 分区的文件系统类型以及使用情况
  df -Th | grep "/dev/sda11" 
  ```

  输出看起来可能像下面这样：

  <img src="https://s6.jpg.cm/2021/12/22/LUzmtE.png" style="zoom:80%">

  可以看到，`/dev/sda1` 分区所属的文件系统类型为 `vfat`（虚拟文件分配表，操作系统用于组织和访问在硬件驱动上的文件），`/dev/sda11` 分区所属的文件系统类型为 `ext4`（第四代扩展文件系统）

- `fsck` 命令

  `fsck` 命令本身用于检查和修复文件系统，也可以用来检测文件系统所属的类型

  具体使用如下所示：

  ```sh
  # 查看 /dev/sda10 分区所属的文件系统类型，-N 选项表示不要检测文件系统中出现的错误，
  # 只打印出将要做的行为，因此能够得到有关文件系统的一部分信息，包括文件系统的类型
  fsck -N /dev/sda10 # 这里检查分区 /dev/sda10 所属的文件系统类型
  ```

  输出如下图所示：

  <img src="https://s6.jpg.cm/2021/12/22/LUzyUi.png" style="zoom:80%">

- `lsblk` 命令

  `lsblk`（List Block Devices），用于显示块设备，通过指定 `-f` 选项即可打印出关于文件系统相关的信息

  具体使用如下所示：

  ```sh
  lsblk -f
  ```

  输出结果如下图所示：

  <img src="https://s6.jpg.cm/2021/12/22/LU0LLz.png" style="zoom:80%">

  一目了然

- `mount` 命令

  `mount` 命令的本意是挂载一个文件系统，或者是一个 ISO 镜像以及远程的文件系统等其它类似的东西，如果不给 `mount` 指定任何参数，那么 `mount` 将会打印有关磁盘分区的信息，其中包括文件系统类型

  具体的使用方式如下所示：

  ```sh
  # 由于磁盘分区的信息有点多，使用 grep 来过滤一下输出是一个很好的想法，当然，awk 和 sed 也是可以的
  mount | grep "^/dev" # 这里的正则表达式表示的是以 /dev 开头的输出内容
  ```

  具体的输出结果如下图所示：

  <img src="https://s6.jpg.cm/2021/12/22/LU07yp.png" style="zoom:80%">

- `blkid` 命令

  `blkid` 命令用于定位或者打印文件块的设备属性，也能够输出磁盘的文件系统类型，直接使用即可

  ```sh
  blkid
  ```

  输出结果如下：

  <img src="https://s6.jpg.cm/2021/12/22/LU0XMW.png" style="zoom:80%">

- `file` 命令

  `file` 命令本身是用于获取一个文件的属性的，但是在 Unix 中，一切皆文件，磁盘系统、外部设备等也不例外，因此通过 `file` 命令也可以查看文件系统相关的信息

  ```sh
  # 默认情况下，file 命令只会读取文件的类型信息，加上 -s 选项使得 file 命令能够读取 block 或者字符文件，
  # -L 选项使得符号链接能够起到作用
  sudo file -sL /dev/sda11 # -s 选项在读取 block 时必须有 root 权限
  ```

  输出结果如下图所示：

  <img src="https://s6.jpg.cm/2021/12/22/LU0y3T.png" style="zoom:80%">

- 使用 `/etc/fstab` 文件

  `/etc/fstab` 文件中定义了静态的文件系统信息，包括挂载点、文件系统类型、挂载选项等

  大致的内容如下图所示：

  <img src="https://s6.jpg.cm/2021/12/22/LU2IdL.png" style="zoom:80%">



<br />

## 查看块大小<sup>[2]</sup>

- 使用 `tune2fs` 命令

  `tune2fs` 用于调整和查看 `ext` 系列的文件系统的参数信息，其中就包括了块大小的信息，如果想要查看分区 `/dev/sda11` 的块大小信息，可以像下面这么做：

  ```sh
  # -l 选项列出文件系统超级块的参数内容，包括已经设置了的参数和可以被设置的参数
  # 该命令必须有超级用户的权限才能执行
  tune2fs -l /dev/sda11 | grep -i "Block size" # 由于参数过长，使用 grep 来过滤块大小的信息，-i 表示忽略大小写
  ```

  输出结果如下：

  <img src="https://s6.jpg.cm/2021/12/22/LU25Oi.png" style="zoom:80%">

  可以看到，`/dev/sda11` 的块大小为 4096 bit，即 4 KB

- `stat` 命令

  `stat` 用于显示文件或者文件系统的状态，检查目录即可查看有关块大小的信息（目录就是一个块）

  具体使用如下：

  ```sh
  # . 可以换成任意的其它目录，使用这种方式不需要超级用户权限
  stat .
  
  # 或者直接得到块大小，-f 选项表示显示文件系统状态而不是文件状态；-c 表示按照指定的格式输出；%s 表示输出总计大小的输出格式，
  stat -fc %s .
  ```

  输出如下：

  <img src="https://s6.jpg.cm/2021/12/22/LU2eTy.png" style="zoom:80%">

- `dumpe2fs` 命令

  `dumpe2fs` 命令用于获取 `ext` 系列文件系统的信息，具体使用如下所示：

  ```sh
  # 该命令只也需要超级用户的权限，-h 选项表示只显示超级块的信息
  sudo dumpe2fs -h /dev/sda11 | grep -i "Block Size" # 查看分区 /dev/sda11 的信息，使用 grep 过滤掉输出
  ```

  输出结果如下图所示：

  <img src="https://s6.jpg.cm/2021/12/22/LU2sqT.png" style="zoom:80%">

- `blockdev` 命令

  `blockdev` 表示从命令行中调用 `ioctl`，具体的使用方式如下所示：

  ```sh
  # 该命令同样需要超级用户的权限；--getbsz 表示打印块大小（单位为 bit）
  sudo blockdev --getbsz /dev/sda11
  ```

- `du` 检测小文件

  `du` 本身是用于检测文件在磁盘中的占用空间的，但是写入的文件内容是按照块大小来划分的，因此，只需要检测一个很小的文件的大小，即可得到块单元的大小，具体如下所示：

  ```sh
  echo 1 > test # 创建一个小文件，小于块大小
  du -h test # 由于文件存储是按照块来划分的，因此这个小文件占用的磁盘空间就是一个块的大小
  ```

  

<br />

参考：

<sup>[1]</sup> https://www.tecmint.com/find-linux-filesystem-type/

<sup>[2]</sup> https://serverfault.com/questions/29887/how-do-i-determine-the-block-size-of-an-ext3-partition-on-linux

