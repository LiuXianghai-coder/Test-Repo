# Git 的底层原理

## 前言

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



## Git 对象

### 二进制文件对象

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

​	对 `objects` 进行查找，可以看到对应的对象已经被存储到 Git 中了。

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

​	现在，对于 Git 对于对象的存储的过程已经有了一个基本的了解，接下来看看对于修改的文件对象的存储。

​	首先，创建一个文件并且写入一些内容，按照上面的方法将文件存储到 Git 中：

```bash
# 创建一个文件，输入一些内容
echo "test.txt version-1" > test.txt
# 生成 test.txt 的 hash 键并且存储到 Git 中
git hash-object -w test.txt

# 这里输出生成的对象的键
335d079908a9ed113c12509b3e41b2d35f0610fd
```

​	然后，修改 `test.txt` 文件中的内容，然后再重新添加到 Git 中

```bash
# 修改 test.txt 文件中的内容
echo "test.txt version-2" > test.txt
# 再次生成 test.txt 的 hash 键并且存储到 Git 中
git hash-object -w test.txt

# 这里输出生成的对象的键
e3d5c7939df71039542c56017d0258d11ea4051d
```

​	现在，查看 `objects` 目录下的文件内容：

<img src="https://s3.jpg.cm/2021/08/31/IJ6fd2.png" >

​	现在，创建的 `test.txt` 的两个不同版本的文件就都存储在 `objects` 的目录中了。

​	此时 Git 的状态就类似于使用 `git commit` 命令将文件存储到本地数据库了。即使此时 `test.txt` 文件被删除了，也能通过对应的 `SHA1` 哈希值找回该对象，现在，删除 `test.txt` 文件对象。

```bash
rm -i test.txt
```

<img src="https://s3.jpg.cm/2021/09/02/IJ9tou.png">

​	然后通过 `git cat-file` 命令来找回该对象，这里以找回第一个版本的 `test.txt` 文件对象为例。

```bash
# 使用 `git cat-file` 从对象树中取回 335d0799 文件对象，将得到的输入重定向到 test.txt 文件中
git cat-file -p 335d079908a9ed113c12509b3e41b2d35f0610fd > test.txt

# 查看得到的 test.txt 文件对象
cat test.txt
```

<img src="https://s3.jpg.cm/2021/09/02/IJ9rFX.png">

​	可以看到，`test.txt` 文件对象的第一个版本已经被恢复了。

​	这就是 Git 存储文件对象的基本原理，通过生成对应的 `SHA1` 哈希值，将对应的文件放入由该哈希值组成的 目录 +  文件名的结构中，即可完成对文件对象的存储；通过对不同的文件生成相对应的哈希值，即可完成对文件版本的控制！

​	然而，如果在现实生活中记住这些哈希值时不可能的，因此，Git 引入了树对象进行进一步的管理



### 树对象

​	Git 通过一种类似于 `Unix` 文件系统的方式对存储相关的内容，所有的内容都以二进制数据对象和树对象的形式进行存储。其中，树对象对应 `Unix` 文件系统中的目录项，二进制数据对象则大致对应了 `inodes` 或者文件内容。一个树对象包含了一条或多条树对象记录，每条记录都包含着一个指向数据对象或者子树对象的 `SHA1` 指针，以及相应的模式、类型、文件名信息。

```bash
# 查看 master 分支下面的最新提交指向的树对象，
# 注意，使用之前的 `git hash-object -w` 写入对象时不会初始化默认的分支 master，这里的 master 分支是另一个 Git 存储库的分支。
git cat-file -p master^{tree}
```

<img src="https://s3.jpg.cm/2021/09/02/IJaxt5.png">

​	可以看到，这里的 `tree` 表示的就是一个树对象，使用 `git cat-file` 查看该树对象

```bash
git cat-file -p d9bf1fc487b0165948a2bf981804a3090d8f82b3
```

​	可能会看到类似的输出：

```bash
100644 blob 20d36530d4b131c26649c0291a0a5f912cd266ea    file
```

​	此时，该仓库内部存储的数据组成如下图所示：

<img src="https://s3.jpg.cm/2021/09/02/IJi5tp.png" style="zoom:80">

​	通常情况下，Git 根据某一时刻暂存区所表示的状态创建并记录一个对应的树对象，如此便可以依次记录某一时间段内的一系列树对象。

​	如果想要创建一个树对象，首先需要通过暂存一些文件来创建一个暂存区。通过使用 `git update-index` 可以为一个单独的文件创建一个暂存区。

```bash
# 为 text.txt 创建一个暂存区，这里 335d079908a9ed113c12509b3e41b2d35f0610fd(参见上文) 代表的是 test.txt 文件的第一个版本

# --add 是一个必须的选项，因为在此操作之前该文件并不存在于暂存区中

# --cacheinfo 表示的是加载不在当前工作目录下的文件，可以回忆一下使用 `git reset --hard` 将 Git 中的文件加载到工作区。在这里，是将 Git 仓库中的 test.txt 文件，SHA 为 335…… 的普通文本文件加载到工作区。使用 --cacheinfo 选项时需要指定这三个参数，具体形式为 --cacheinfo <mode><object><file>，注意，Git 中是使用 SHA 值来表示唯一对象的
git update-index --add --cacheinfo 100644 \
335d079908a9ed113c12509b3e41b2d35f0610fd test.txt
```

​	`--cacheinfo` 可以指定的一些模式：

> - 100644：表示这个文件是一个普通文件
> - 100755：表示该文件是一个可执行文件
> - 120000：表示该文件是一个符号链接

​	现在 `test.txt` 文件已经存在于暂存区了，使用上层命令 `git status` 可以看到。

​	使用 `git write-tree` 命令将暂存区的内容写入到一个树对象，如果这个树对象在此之前并不存在的话，当调用此命令时，会根据当前暂存区状态自动创建一个新的树对象。

```bash
# 将暂存区的内容写入到一个树对象
git write-tree

# 这是在写入树对象之后得到的该树对象对应的 SHA1 哈希值
c1659d273de8521e1bd6568705bcc6dde4a15202
```

​	可以通过 `git cat-file` 命令来查看该树对象的内容

```bash
# 查看该数对象的内容，现在该树对象只包含一个 text.txt 普通文件
git cat-file -p c1659d273de8521e1bd6568705bcc6dde4a15202
# 得到的输出，这里保存的是 test.txt 文件的第一个版本(从 335d…… 的 SHA 可以看到)
100644 blob 335d079908a9ed113c12509b3e41b2d35f0610fd    test.txt

# 查看该树对象的类型，很明显，这是一个树对象
git cat-file -t c1659d273de8521e1bd6568705bcc6dde4a15202
# 得到的输出应该只是一个单纯的 tree
tree
```

​	现在，多创建几个文件对象，在这里以创建 `new.txt`、`hello.txt`，然后使用上文的命令它们写入 Git 存储仓库中，然后在将它们写入到树对象中。（注意，此时 Git 的暂存区是没有提交的，因此在每次新写入时都会将原先在暂存区内的内容一同写入到树对象中）。

```bash
# 创建 new.txt 文件
echo "new file" > new.txt
# 将 new.txt 文件对象写入到 Git 仓库中
git hash-object -w new.txt
# 得到该文件对象的 SHA1 哈希值
fa49b077972391ad58037050f2a75f74e3671e92
# 将 new.txt 文件从 Git 仓库中加载到暂存区中
git update-index --add --cacheinfo 100644 \
fa49b077972391ad58037050f2a75f74e3671e92 new.txt
# 将当前暂存区的内容写入到树对象
git write-tree
# 写入树对象后得到的树对象的 SHA 值
d91b14ea5a45f1f321adf350f3b36d0f5cba65d0

# 创建一个 hello.txt 普通文本文件
echo "Hello World" > hello.txt
# 将 hello.txt 文件写入到 Git 仓库
git hash-object -w hello.txt
# 将 hello.txt 文件从工作区加载到暂存区
git update-index --add hello.txt
# 将当前的暂存区内容写入到树对象
git write-tree
# 得到的树对象下的 SHA 哈希值
4b86f8f05940fd25b57e02eb600381a64aabc06e
```

​	查看这三个树对象，可能看起来像下面这样：

<img src="https://s3.jpg.cm/2021/09/03/ItcUvt.png">

​	现在这三个树对象都是相互独立的，可以通过将树对象加载到暂存区再写入到别的树对象，就可以将它们组合起来了。

```bash
# 将加入 new.txt 时创建的树对象和 加入 hello.txt 时创建的树对象加载到暂存区
git read-tree --prefix=new d91b14ea5a45f1f321adf350f3b36d0f5cba65d0
git read-tree --prefix=hello 4b86f8f05940fd25b57e02eb600381a64aabc06e

# 将暂存区的内容写入到树对象
git write-tree
# 得到写入的树对象的 SHA
edbbbaf17a0b477c133d457d4ec092af44bfed3a
```

​	此时查看该树对象，可能看起来像下面这样：

<img src="https://s3.jpg.cm/2021/09/03/Itc2wU.png">

​	可以看到，树对象已经合并到一起去了，现在该树对象的情况如下图所示：

<img src="https://s3.jpg.cm/2021/09/03/ItU6FQ.png">

​	看起来是不是和 `Unix` 的文件系统很像？需要注意的是，在这个示例中使用的文件对象的版本是一样的，当然可以通过对应的 SHA 来引入不同的文件版本，然后放到不同的树对象中进行组合，这样就像是一个简单的文件系统了， 请把树对象想象成文件系统中的目录项，而把一般的二进制对象想象成一般的文件，这样要好理解一些。	现在还有一个问题在于， 在这里写入树对象的内容都是基于暂存区的，然而，在实际应用中不可能是一直就这么一个暂存区。Git 的强大之处就在于它对于版本的控制，这将是即将引入的**提交对象**的内容



### 提交对象

​	回想一下，Git 的操作都是围绕三个区来进行的，**工作区**、**暂存区**、**本地Git仓库**，工作区无需做过多的讨论，对于暂存区，在上文的的树对象中已经提及到了相关的加载文件到暂存区和保存暂存区内容的相关部分，对于理解 Git 的原理来讲，应该已经足够了。现在要介绍的就是 Git 如何将暂存区的内容存储为一个提交对象，也就是如何将成为一个版本快照的过程。

​	上文介绍到 Git 使用树对象将不同的对象组合成了一个一般的文件系统，如果想要拿到某个文件的版本，只要加载到暂存区，然后保存到对应的树对象中，然后按照对应的 SHA 值找到对应的文件对象即可。这么乍一看确实使用数对象和二进制对象就可以实现版本控制了，然而，实际问题是这么一顿操作下来将会很繁琐，而且，在真实的场景中，应该也没有人会记得这个毫无规律的 SHA 哈希码。所以，Git 就引入了提交对象来解决这些问题。

​	可以通过 `git commit-tree` 来创建一个提交对象，为此需要指定要提交的树对象的 SHA 值，以及该提交对象的父提交对象（如果存在父提交）。现在，在当前的仓库没有父提交（甚至连分支都没有）

```bash
# 以 4b86f8f…… 树对象创建一个提交对象，同时提交信息为 `First Commit`
echo "First Commit" | git commit-tree 4b86f8f05940fd25b57e02eb600381a64aabc06e
# 得到的提交对象的 SHA 值
14e6b59146ab88445d612583f7942fd39deeef6d
```

​	使用 `git cat-file` 查看该提交对象，可能会看到类似下面的场景：

<img src="https://s3.jpg.cm/2021/09/06/It9srH.png">

​	是不是与提交时查看的提交信息类似？现在，多提交两次，这时需要指定父提交对象了。

```bash
echo "Second Commit" | git commit-tree edbbbaf -p 14e6b5

# 省略新建文件、加入暂存区、写入缓存、提交的步骤
```

​	现在，对最后一个提交执行 `git log` 命令

```bash
# --stat 选项表示显示每次提交的不同之处
git log --stat 9510a
```

​	可能的结果如下图所示：

<img src="https://s3.jpg.cm/2021/09/06/It9pfO.png" style="zoom:60%">

​	在没有使用任何上层命令的情况下，创建了一个 Git 的历史提交。这就是每次在运行 `git add` 和 `git commit` 时 Git 做的动作。将修改的文件保存为二进制对象，然后更新暂存区，记录树对象，最后指明一个指定了顶层树对象和父提交的树对象。Git 通过在二进制对象、树对象、提交对象之间来回切换，完成了一般常见的功能。这些对象文件都保存在 `.git/objects` 目录下。

​	此时提交的历史看起来如下所示：

<img src="https://s3.jpg.cm/2021/09/06/It9xsX.png">

## Git 引用

### 一般分支引用

​	上文揭示了 Git 对于对象提交的处理流程，如果能够记住每次提交的 `SHA` 值，那么就可以正常使用 Git 了。但是，在实际情况中，记住这些 `SHA` 值是不可能的，因此，Git 使用一个带有名字的指针对象来指向对应的 `SHA` 值，这个指针被称为“**引用**”，这些引用指针都存放在 `.git/refs` 目录下。

```bash
# 查看 .git/refs 目录下的内容
find .git/refs/
# 输出的内容
.git/refs/
.git/refs/heads  # 分支引用目录
.git/refs/tags # 标签引用目录
```

​	基于上文的提交，可以通过使用以下命令创建或者更新一个 `master` 引用

```bash
# 将最后一个提交的 SHA 值输入到 master 引用中，使得 master 指向该提交，一般不推荐直接修改引用文件，考虑使用 Git 的 update-ref 命令
echo 9510a3b14758e89330c09acbb01384a73f2ac4f1 > .git/refs/heads/master

# 使用 git 命令来更新对应的引用，如果没有则创建（推荐使用这种方式）。这也是分支创建的工作原理，这里就相当于创建了一个 master 分支，该分支指向 9510a3b14758e89330c09acbb01384a73f2ac4f1 提交对象，该提交在本仓库中表示第三次提交
git update-ref refs/heads/master 9510a3b14758e89330c09acbb01384a73f2ac4f1

# 创建分支 test 指向原来的第二次提交
git update-ref refs/heads/test 719f66129bdd152eec268b8b630296b1713f858d
```

​	创建分支之后，具体指向如下图所示：

<img src="https://s3.jpg.cm/2021/09/06/Ita4Qe.png">

​	现在，运行 `git branch` 命令，你会发现已经创建了 `master`  和 `test`。

​	这就是使用 `git branch` 命令时 Git 所做的工作，在创建或者更新分支时只需要执行顶层的 `git update-index` 命令即可。

### HEAD 引用

​	如上文所述，现在创建了 `master`  和`test` 分支，那么 Git 是如何知道当前指向的分支的呢？Git 通过 `HEAD` 指针来确定当前的所在分支，`HEAD` 位于 `.git/HEAD`，这是一个符号引用，指向目前所在的分支。如果 `HEAD` 文件包含一个 Git 对象的 `SHA` 值，那么就会处于 “头指针分离” 状态。

​	当执行 `git commit` 命令时，会创建一个提交对象，并且使用 `HEAD` 指向的分支引用作为其提交的父提交。

​	可以通过手动编辑这个文件来修改 `HEAD` 引用，但是一般使用 `git symbolic-ref` 命令会更加靠谱。

```bash
# 查看 HEAD 引用指向的分支
git symbolic-ref HEAD

# 修改 HEAD 引用指向 test 分支，如果指向的分支不在 refs 目录下，Git 将会拒绝修改 HEAD 引用
git symbolic-ref HEAD refs/heads/test
```

经过修改后的情况具体如下所示：

<img src="https://s3.jpg.cm/2021/09/06/ItiImh.png">

### 标签引用

​	实际上，除了二进制对象、树对象和提交对象外，Git 还存在一种 **标签对象**，标签对象与提交对象十分相似，因此在此不会做过多的描述。标签对象的主要特点就是它只会指向一个提交对象，相当于给一个提交对象起的别名。

​	Git 中存在两种标签：轻量标签和附注标签。

​	创建一个轻量标签（只是一个单纯的引用对象，创建一个引用指向对应的提交即可）

```bash
# 创建 v1.0 标签指向 14e6b59…… 提交对象，14e6b59…… 在这里表示第一次的提交对象
git update-ref refs/tags/v1.0 14e6b59146ab88445d612583f7942fd39deeef6d
```

​	创建附注标签则要麻烦一些，因为附注标签包含又对应的附注信息等。因此，如果要创建一个附注标签，需要首先创建一个标签对象，然后使用一个引用来指向该标签。

```bash
# 创建一个标签对象 V1.1，该标签对象包含 719f66129……（第二次提交）对象的引用
git tag -a V1.1 719f66129bdd152eec268b8b630296b1713f858d -m "Tag info"
```

​	查看该标签对象

```bash
# 查看该标签对象的 SHA 值
cat .git/refs/tags/V1.1
# 得到的输出内容
537cd069f8f7fa4688cef81af57c98b5ebc81503

# 查看该标签对象
git cat-file -p 537cd069f8f7fa4688cef81af57c98b5ebc81503
```

<img src="https://s3.jpg.cm/2021/09/06/ItidOO.png">

​	值得注意的是，标签对象不仅能够指向提交对象，而且还能指向二进制数据对象和树对象，尽管常见的方式是指向提交对象。

### 远程引用

​	如果存在远程仓库并且执行过推送操作，那么就可以在 `refs/` 目录下找到一个 `remote` 文件夹，里面包含了最近依次提交时每一个分支所对应的值（以 `SHA` 值表示）。

​	远程引用类似于本地的分支引用，唯一的区别在于远程分支是只读的。由于本地只是保存了上次提交的快照，因此，即便你强制切换分支到远程分支，也依旧无法直接向远程分支提交对象。



## 数据的维护与恢复

### 包文件

​	为了节省存储空间，Git 一般会使用 `Zlib`  对添加的对象进行压缩。Git 对于文件的版本控制是通过生成每个提交对应的快照实现的，这么做会显得有点浪费。因此 Git 会时不时地将多个对象进行打包，成为一个称之为 “包文件” 二进制文件。Git 是通过 `git gc --auto` 来实现自动打包的，但是也可以手动执行 `git gc` 手动进行一次打包（当推送提交到服务器时也会进行打包）。

<img src="https://s3.jpg.cm/2021/09/07/INJrBk.png" />

​	此时再查看 `.git/objects` 目录，你会发现很多的对象都已经不见了，取而代之的是多了一堆的 `pack` 文件，同时，文件的大小也进一步变小了。

​	Git 在进行打包对象时，会查找那些名称以及大小相同的文件，并且打包时只保留不同文件版本之间的差异，因此可以很好的节约空间。

​	使用  `git verify-pack -v` 可以查看对应的包文件的内容

<img src="https://s3.jpg.cm/2021/09/07/INJa1h.png" />

​	可以看到，`edbba……` （第二次提交，6字节）树对象引用到 `34d3d……` （第三次提交 207 字节）。值得注意的是，是第二个版本的树对象引用了第三个版本的树对象，这是因为 Git 认为一般需要访问最新的提交对象。 

### 数据维护

​	上文提到，Git 会自动执行 `git gc --auto` 将比较松散的对象进行打包，成为一个“包文件”对象。`git gc` 除了会将对象进行打包之后，还会将 `.git/refs` 下的引用对象打包到 `.git/packed-refs` 文件中，这也是出于效率的考虑。查看之前 `git gc` 后保存的引用：

<img src="https://s3.jpg.cm/2021/09/07/INtuGG.png" />

​	如果更新了引用，Git 并不会直接修改对应的引用文件，而是向 `.git/refs/heads/` 下创建一个新的文件。

​	为了获得指定引用的正确 SHA 值，Git 会首先在 `.git/refs` 目录下查找是否存在对应的引用文件，如果无法找到，那么就会到 `.git/packed-refs` 文件中进行查找。

### 数据恢复

​	当强制切换当前的工作目录到原先的提交对象（`git reset --hard`）时，此时查看提交日志时只能看到本次提交之前的日志。此时可以考虑使用 `git reflog` 来解决这种窘迫的情况。在每次提交或者修改分支时，Git 会通过 `git update-index` 来更新引用日志。

```bash
# 查看引用日志
git reflog
```

<img src="https://s3.jpg.cm/2021/09/07/INSzOi.png">

​	使用 `git reflog` 显示的信息比较少，试试 `git log -g` （`-g` 选项表示输出引用日志）

​	通过引用日志，找到你要切换的提交，为它创建一个分支，即可解决这个问题。

​	如果引用日志被删除了，就不能再使用上面的方案解决这个问题了。比较实用的方法是使用 `git fsck` 来检测 Git 仓库的完整性，添加 `--full` 选项，会显示所有没有被其它对象指向的对象。

```bash
# 删除日志文件，这里只是为了掩饰，一般情况下不要这么做！
rm -rf .git/logs/

git fsck --full
```

<img src="https://s3.jpg.cm/2021/09/07/INSifz.png" />

​	`dangling commit` 就是已经丢失提交，使用 `git reset --hard` 可以回退到这个提交。

### 移除对象*

​	按照 Git 的处理方式，在添加一个对象提交之后，这个对象就会一直存在于之后的每次提交中。如果在一次提交中不小心添加了一个大的无用文件，那么即便移除了这个文件，但是它依旧存在于 Git 仓库中的某个提交上（Git 设计如此）。如果想要删除这个大的无用文件，必须从添加它的那次提交开始，对于之后的每次提交都移除这个文件。（这个操作具有很强的破坏性）

```bash
# 添加一个大的二进制文件，然后创建一次提交
git add node.exe
git commit -m "Add a big file"

# 移除这个二进制文件，创建一个提交
git rm node.exe
git add .
git commit -m "remove big file"
```

​	现在，把这些游离的对象打包

```bash
git gc
```

​	查看当前的对象的总大小

```bash
git count-objects -v
```

<img src="https://s3.jpg.cm/2021/09/07/INrlUz.png" />

​	可以看到，当前的包大小大概有 10MB

​	找到这个大文件

```bash
# 找到这个大文件
# 由于 verify-pack 输出的第三列表示文件大小，因此将 sort 的 -k 指定为 3，-n 表示按照数值进行排序
# tail -3 表示获取最后的三行输出
git verify-pack -v .git/objects/pack/pack-07607c557474562adfc40f88233d3789a9990ce3.idx | sort -k 3 -n | tail -3
```

<img src="https://s3.jpg.cm/2021/09/07/INr448.png" />

​	通过这个大文件的 SHA 找出这个大文件的名称

```bash
# git rev-list 列出所有的 SHA 值，--objects 表示输出提交的SHA、数据对象的 SHA 以及相关联的文件路径
git rev-list  --objects --all | grep b910fb4f15b71292efe102d7459fda6e6b716126
# 得到的输出
b910fb4f15b71292efe102d7459fda6e6b716126 node.exe
```

​	这样一顿操作后，就找到了这个“罪魁祸首”，然后要做的就是修改从添加这个文件开始的提交，使用 `git filter-branch` 可以做到。但是在那之前，要找到首先加入该文件的提交。

```bash
# 通过提交日志来找到最先开始提交该文件的提交对象
git log --oneline --branches -- node.exe

# 得到的输出
1fef53b (HEAD -> master) remove big file
da6a29d add a big file
```

​	现在，重写自 `da6a29d` 开始的提交

```bash
# --index-filter 表示修改暂存区或索引中的文件，可以通过 --tree-filter 会修改在硬盘上检出的文件（在此也是有效的）
# 必须使用 git rm --cached 来移除文件，因为需要从索引中移除这个文件（这个文件已经不在工作区中了）
# -- da6a29d^.. 表示修改自 da6a29d 以来的提交 
git filter-branch --index-filter \
  'git rm --ignore-unmatch --cached node.exe' -- da6a29d^..
```

<img src="https://s3.jpg.cm/2021/09/07/INsSRC.png" />

​	现在，在历史提交中已经不存在该文件的引用了，但是，在使用 `filter-branch` 中添加的新引用中，引用日志和 `.git/refs/original` 中依旧包含对该文件的引用，因此必须删除它们然后再重新打包。

```bash
# 删除引用日志和新添加的引用
rm -rf .git/refs/original
rm -rf .git/logs

# 重新打包
git gc
```

​	现在再来查看包文件对象的大小

<img src="https://s3.jpg.cm/2021/09/07/INEQXz.png" />

​	可以看到，包文件已经从原来的接近 10MB 变为了 2KB。但是到这里还没有结束，由于 Git 在写入文件时将它写入到了 Git 存储库中，因此现在在 Git 存储库中依旧保留有该文件对象，只是它不会在之后的提交和推送中出现而已。要完全删除它，使用 `git prune` 来彻底删除它。

```bash
# --expire now 表示只删除当前时间之前的游离对象
git prune --expire now
```

<img src="https://s3.jpg.cm/2021/09/07/INEsnL.png" />

​	现在，这个大文件终于已经被彻底删除了。

