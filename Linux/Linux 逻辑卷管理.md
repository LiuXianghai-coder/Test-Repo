# Linux 逻辑卷管理

如果用标准分区在硬盘上创建了文件系统，为已有的文件系统添加额外的空间是一件十分痛苦的事情。只能在已有的硬盘上的可用空间范围内调整分区大小，如果硬盘空间不够的话，就只能换一个大容量的硬盘，然后手动将已有的文件系统移动到新的硬盘上。

这个时候可以通过将另外一个硬盘上的分区加入已有的文件系统，动态地添加存储空间。Linux 可以通过 LVM（逻辑卷管理）来完成这项工作

## 逻辑卷管理布局

逻辑卷管理的核心在于如何处理安装在系统上的硬盘分区。在逻辑卷管理的世界中，硬盘分区被称为 “物理卷”（physical volume PV），每个物理卷都会映射到硬盘上特定的物理分区

多个物理卷集中在一起可以形成一个卷组（volume group VG）。逻辑卷管理系统将卷组视为一个物理硬盘，但事实上卷组可能是由分布在多个物理硬盘上的不同分区组成的。卷组提供了一个创建逻辑分区的平台，而这些逻辑分区则包含了文件系统

整个结构中的最后一层是逻辑卷（logical volume LV）。逻辑卷为 Linux 提供了创建文件系统的分区环境，作用类似于 Linux 中的物理硬盘分区。Linux 系统将逻辑卷视为物理分区

可以使用任意一种标准的 Linux 文件系统来格式化逻辑卷，然后再将他们加入 Linux 虚拟目录中的某个挂载点

逻辑卷和物理分区之间的关系如下图所示：

![LVM.png](https://s2.loli.net/2022/04/20/wKWSb4FzmyVDtaY.png)

图中一共包含了三个不同的物理硬盘，根据这三个硬盘的情况，得到了每个硬盘的分区，对应到不同的物理卷。这些物理卷共同组成了一个卷组，此时 Linux 的 LVM 将整个卷组看作是一个硬盘，然后在这个硬盘上创建逻辑卷。现在，Linux 就可以单独对每个逻辑卷使用不同的文件系统进行格式化（Unix 编程哲学之“加一层”）

可以注意到硬盘 $3$ 有一部分的空间是没有被使用的，通过逻辑卷可以轻松地管理这部分未使用的空间：将这部分空间加入到已有的卷组中，或者为它单独创建一个逻辑卷

## Linux 的 LVM

Linux 的 LVM 有两个可用的版本：

- LVM 1：最初 LVM 于 1998 年发布，只能用于 Linux 2.4 版本，该 LVM 仅仅提供了基本的逻辑卷管理功能
- LVM 2：LVM 的更新版本，它在标准的 LVM 功能外提供了一些额外的功能

一般情况下建议使用 LVM 2，LVM 2 提供了以下的一些新功能：

- 快照

    Linux LVM允许你在逻辑卷在线的状态下将其复制到另一个设备，这个功能被称为 “快照”。这个功能在备份数据时特别有用，比如：MySQL 的数据备份可以通过快照进行备份，而不需要显式地加锁。

    LVM 1 创建的快照在创建完成之后就不能再写入数据，而 LVM 2 则允许创建可读写的快照，在这种情况下，如果某一个逻辑卷出现了问题，就可以直接将快照替换掉原来的逻辑卷，这个特性对于故障转移来讲特别有用

- 条带化

    当 Linux LVM 文件写入逻辑卷时，文件中的数据块会被分散到多个物理硬盘上

    条带化这一特性可以提高 IO 的访问速度，因为此时将数据的读写分散到了多个硬盘中。LVM 条带化和 RAID 条带化不同，LVM 条带化不提供用来创建容错环境的校验信息，这会增加文件由于磁盘故障而丢失的概率，并且单个磁盘的故障将会导致整个逻辑卷都无法使用

- 镜像

    通过 LVM 安装文件系统不能确保文件系统不再出现问题，一旦出现问题，就有可能再也无法恢复

    尽管 LVM 提供的快照功能提供了一些可能的帮助，但是对于某些情况快照功能并不能完全避免问题（比如涉及到大量数据的系统，自上次快照之后可能要存储上千条记录）。

    解决这种问题的一个方案就是使用 LVM 镜像，镜像是一个实时更新的逻辑卷的完整副本，当创建镜像逻辑卷时，LVM 会将原始逻辑卷同步到镜像副本中。一旦同步完成，LVM 会为文件系统的每次写入都执行两次（一次写入住主逻辑卷，一次写入镜像逻辑卷），尽管降低了写入性能，但是提高了系统的可靠性

## 使用 Linux LVM

这里使用 LVM 2，如果没有安装，可以使用相关的软件安装工具安装 `lvm2`，对于 Ubuntu 来讲，可以执行如下的安装命令：

```shell
sudo apt install lvm2
```

### 定义物理卷

创建逻辑卷的第一步是定义物理卷，将硬盘上的物理分区转换为 Linux LVM 使用的物理卷。在这个过程中，可以使用 `fdisk` 来管理安装在系统上的任何存储设备上的分区。

首先，使用 `fdisk` 来查看磁盘的分区情况：

```shell
fdisk -l
```

可能会看到类似下面的输出：

```text
…………………………

Disk /dev/sdc：8 GiB，8589934592 字节，16777216 个扇区
单元：扇区 / 1 * 512 = 512 字节
扇区大小(逻辑/物理)：512 字节 / 512 字节
I/O 大小(最小/最佳)：512 字节 / 512 字节

Disk /dev/mapper/cs-root：12.5 GiB，13417578496 字节，26206208 个扇区
单元：扇区 / 1 * 512 = 512 字节
扇区大小(逻辑/物理)：512 字节 / 512 字节
I/O 大小(最小/最佳)：512 字节 / 512 字节

Disk /dev/mapper/cs-swap：1.5 GiB，1610612736 字节，3145728 个扇区
单元：扇区 / 1 * 512 = 512 字节
扇区大小(逻辑/物理)：512 字节 / 512 字节
I/O 大小(最小/最佳)：512 字节 / 512 字节

…………………………
```

可以看到，当前系统中存在一个名为 ‘/dev/sdc’ 的分区，并且这个分区目前还没有被格式化，现在，让我们把这个分区创建为基本的 Linux 的分区，可以使用 `fdisk` 的交互式 `n` 命令来添加该分区：

![2022-04-21 20-48-46 的屏幕截图.png](https://s2.loli.net/2022/04/21/M5ryVRQOjH2T8lS.png)

至此将 ‘/dev/sdc’ 转换为了基本的 Linux 分区，接下来将通过这个分区创建对应的物理卷，这个过程可以通过 `pvcreate` 命令来完成：

```bash
sudo pvcreate /dev/sdc
```

如果看到类似下面的输出信息，则说明已经创建了该分区对应的物理卷：

```text
Physical volume "/dev/sdc" successfully created.
```

创建完成之后可以通过 `pvdisplay` 来查看当前的创建情况，对于当前的分区，可以执行如下的命令查看创建进度：

```bash
sudo pvdisplay /dev/sdc
```

![2022-04-21 20-59-53 的屏幕截图.png](https://s2.loli.net/2022/04/21/LeZGtzDvhwkSbfO.png)

### 创建卷组

创建物理卷之后，就需要将这些物理卷组合到一个卷组中，形成一个新的 “磁盘”。创建卷组可以使用 `vgcreate` 命令，如果希望创建一个名为 `Vo11` 的卷组，并将 `/dev/sdc` 添加到卷组中，可以执行如下的命令：

```bash
sudo vgcreate Vo11 /dev/sdc
```

在这个过程中会自动创建 Vo11 卷组，如果希望物理卷添加到已有的卷组中，可以使用 `vgextend` 命令，如下所示：

```bash
# 将 /dev/sdc 添加到 Vo10 卷组中
sudo vgextend Vo10 /dev/sdc
```

### 创建逻辑卷

Linux 使用逻辑卷来模拟物理分区，并在其中保存文件系统。Linux 会像处理物理分区一样处理逻辑卷，允许自定义逻辑卷中的文件系统，然后将文件系统挂载到虚拟目录上

要创建逻辑卷，可以使用 `lvcreate` 命令来完成，例如上文中已经创建的卷组，可以执行如下的命令创建一个逻辑卷：

```bash
# -l 指定逻辑卷占用卷组的空间大小，-n 指定创建的逻辑卷的名称
sudo lvcreate -l 100%FREE -n lvtest Vo11
```

创建完成之后，可以使用 `lvdisplay` 来查看创建的逻辑卷的详细情况：

```bash
sudo lvdisplay Vo11
```

### 创建文件系统

运行完 `lvcreate` 命令之后，逻辑卷就已经创建完成了，但是此时的逻辑卷还没有对应的文件系统。在一般物理分区上创建文件系统的命令在逻辑卷上同样有效。例如，如果希望给创建的逻辑卷设置为 `ext4` 文件系统，可以执行如下的命令：

```bash
sudo mkfs.ext4 /dev/Vo11/lvtest
```

创建了文件系统之后，就可以将这个逻辑卷挂载到虚拟目录中，和物理分区的使用一样，唯一需要注意的是需要使用特殊的路径来引用这个逻辑卷。如下所示：

```bash
# 将创建好的逻辑卷挂在到 /mnt/lxh_part，注意在挂载之前确保挂载点存在
# 即 /mnt/lxh_part 目录必须存在
sudo mount /dev/Vo11/lvtest /mnt/lxh_part
```

挂载之后，就可以像使用物理分区一样使用逻辑卷了

### 修改 fstab

为了避免每次启动系统都要手动挂载，可以在 `fstab` 中进行配置，使得系统在启动时自动挂载。如下所示：

```text
#
# /etc/fstab
# Created by anaconda on Sun Jan 16 13:14:50 2022
#
# Accessible filesystems, by reference, are maintained under '/dev/disk/'.
# See man pages fstab(5), findfs(8), mount(8) and/or blkid(8) for more info.
#
# After editing this file, run 'systemctl daemon-reload' to update systemd
# units generated from this file.
#
/dev/mapper/cs-root     /                       xfs     defaults        0 0
UUID=e36a4b91-186c-48f0-850d-e3c90ebfeb20 /boot                   xfs     defaults        0 0
/dev/mapper/cs-swap     none                    swap    defaults        0 0
# 逻辑卷的挂载配置
/dev/Vo11/lvtest	/mnt/lxh_part		ext4	defaults	0	0
```

有关 `fstab` 的配置可以参考：https://www.cnblogs.com/FatalFlower/p/15419794.html

## 修改 LVM

具体可以参考以下的相关命令：`vgchange`、`vgremove`、`vgreduce`、`lvextend`、`lvreduce`

如果可以的话，使用 LVM 的 GUI 工具是一个有用的手段

<br />

参考：

<sup>[1]</sup> https://www.cnblogs.com/gaojun/archive/2012/08/22/2650229.html

<sup>[2]</sup> 《Linux 命令行和 Shell 脚本编程大全》



