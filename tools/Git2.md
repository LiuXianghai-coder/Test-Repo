# Git 的底层原理

### 前言

​	基于 Git 的使用，已经在前文有过相关的介绍，使用 Git 用作日常的开发基本上是足够的。现在，本文将详细介绍一些有关 Git 的实现原理。



### 底层命令与上层命令

​	一般情况下，正常使用的 Git 命令，如 `git add`、`git checkout` 等都是由 Git 封装好的上层命令，这对于一般的用户来讲是友好的。但是，有时候如果想要在底层执行一些必要的操作，这时就需要使用底层命令了。

​	早期的  Git 是 Linus 为了管理 Linux 内核的版本而设计的，当时的 Git 设计的更加符合 Unix 的风格，了解 Git 的底层命令对于学习 Git 来讲也是至关重要的。



### `.git` 目录文件结构

​	在使用 `git init` 命令初始化一个空的 Git 仓库时，会在当前目录下创建一个 `.git` 文件夹，里面的内容看起来可能跟下面的有些相似。

<img src="https://s3.jpg.cm/2021/08/31/IJe6G8.png">

​	文件说明：

- `hooks`：钩子脚本文件的存放目录，钩子类似于一个代理，在执行提交操作之前执行对应的一些脚本，这个一般在自己搭建 Git 服务器的时候使用。
- `info`：包含一个全局排除（global exclude）文件，用于放置那些在 `.gitignore` 文件中配置的忽略跟踪文件
- `objects`：**存储所有的数据内容**
- `refs`：**存储指向数据的提交对象（分支、标签、远程仓库等）的指针**
- `config`：包含一些特有的项目配置选项
- `description`：可以忽略的文件
- `head`：**指向当前检出的分支**



### Git 对象

​	Git 更加像是一个键值对数据库，在向 Git 中写入内容时，都会得到唯一的一个键，然后通过这个键就可以再得到这个对象。可以通过 `git hash-object` 进行这个操作

```bash
# 将 "Hello World!" 字符串对象使用 Git 生成对应的键，--stdin 表示从标准输入流中获取对象，如果不指定 --stdin 选项，那么就需要在命令行尾部添加对应的存储文件路径
echo "Hello World!" | git hash-object --stdin
```

​	你可能会看到类似下面的输出：

```bash
980a0d5f19a64b4b30a87d4206aade58726b60e3
```

​	此时只是简单地执行一次 `hash` 操作，并没有将这个对象放入 Git 中进行存储，如果想要存储到 Git，需要添加`-w` 选项

```bash
# 将 "Hello World!" 字符串对象生成对应的键，并且存储到 Git 中
echo "Hello World!" | git hash-object -w --stdin
```

对 `objects` 进行查找，可以看到对应的对象已经被存储到 Git 中了。

<img src="https://s3.jpg.cm/2021/08/31/IJ6q3f.png">

​	现在进入 `.git/objects` 目录，你会看到一个 名为 `98` (对象 `hash` 值的前两个字符)目录，进入这个目录，你可以看到对应的文件，即上图中以 "0a0d"（对象 `hash` 值的后面部分） 开头的文件，该文件存储的是之前保存的 "Hello World!" 字符串对象的二进制对象。

​	可以使用 `git cat-file` 来查看这个二进制对象（blob）文件的内容。

```bash
# 使用 git cat-file 来查看对应的二进制对象的内容，使用 -p 选项表示漂亮地打印对象的内容
git cat-file -p 980a0d5f19a64b4b30a87d4206aade58726b60e3
```

​	输出的内容：

```bash
Hello World!
```

现在，对于 Git 对于对象的存储的过程已经有了一个基本的了解，接下来看看对于修改的文件对象的存储。

首先，创建一个文件并且写入一些内容，按照上面的方法将文件存储到 Git 中：

```bash
# 创建一个文件，输入一些内容
echo "test.txt version-1" > test.txt
# 生成 test.txt 的 hash 键并且存储到 Git 中
git hash-object -w test.txt

# 这里输出生成的对象的键
335d079908a9ed113c12509b3e41b2d35f0610fd
```

然后，修改 `test.txt` 文件中的内容，然后再重新添加到 Git 中

```bash
# 修改 test.txt 文件中的内容
echo "test.txt version-2" > test.txt
# 再次生成 test.txt 的 hash 键并且存储到 Git 中
git hash-object -w test.txt

# 这里输出生成的对象的键
e3d5c7939df71039542c56017d0258d11ea4051d
```

现在，查看 `objects` 目录下的文件内容：

<img src="https://s3.jpg.cm/2021/08/31/IJ6fd2.png" >



​	