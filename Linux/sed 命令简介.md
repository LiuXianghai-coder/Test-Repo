# sed 命令简介

sed（Stream Editor）即 “流编辑器”，是一个转换文本的 Unix 程序，类似的命令还有 awk、ed、grep、tr 等

## 工作模式

sed 通过从输入流（标准输入或者管道）中逐行读取文本到一个被称为 “模式空间” 的 Buffer 中，通过在 sed 命令中传入的脚本参数对处理的文本进行相应的处理。

由于 sed 只是针对获取到的输入行进行处理，因此效率要比那些需要随机访问数据的工具要高

sed 的一般使用格式如下：

```sh
sed [OPTION]... {script-only-if-no-other-script} [input-file]...
```



## 基本使用

本文使用的测试数据 `data.txt` 中的内容如下（选自《双城记》）：

```text
It was the Dover road that lay,
on a Friday night late in November, 
before the first of the persons with whom this history has business.
The Dover road lay, as to him, beyond the Dover mail, 
as it lumbered up Shooter’s Hill. 
He walked uphill in the mire by the side of the mail,
as the rest of the passengers did; 
not because they had the least relish for walking exercise,
under the circumstances, 
but because the hill,
and the harness,
and the mud,
and the mail,
were all so heavy
```

### 替换文本

通过在 sed 命令中传入 `s` (substitute) 选项进行文本的替换操作，该替换操作只是针对输出文本，对于原有文本不会有任何改动。

如果希望将每行的输入文本中的 “the” 替换为 “a”，可以执行如下的 sed 命令（这里的匹配可以是正则表达式）：

```sh
# /the 表示匹配每行所有的 "the"，/a 表示将搜索到的 "the" 替换为 a
sed 's/the/a' data.txt 
```

一般情况下，sed 默认只会替换掉第一个匹配到的文本内容，如果希望改变这个默认的行为，可以在向 `s` 命令中加入额外的 `flag` 选项来进行设置，`s` 命令的一般格式如下所示：

```bash
s/pattern/replacement/flag
```

其中，`flag` 的有以下几个可选项：

- 数字：如果 `flag` 为数字的话，会替换掉对应的第几处的匹配项
- g（global）：将会将所有匹配到的模式都进行替换
- p（print）：将原来未进行替换的行进行输出
- w（write） *file*：将替换后的结果写入到 *file* 中

注意，这些选项可以组合使用，如：

```sh
sed 's/the/a/gp' data.txt 
```

<br />

有时 “/” 可能也是需要进行处理的字符，为了解决转义问题，可以为每个单独的 “/” 加上 “\” 进行转义，如下所示：

```bash
sed 's/\/bin\/bash/bash/gp' /etc/passwd
```

这种方式多少看起来有些奇怪，为此 sed 提供了自定义分割符来替换 “/”，如下所示：

```bash
sed 's!/bin/bash!/bash' /etc/passwd
```

`s` 命令后出现的第一个字符就是新的命令分割符

<br />

sed 默认会对所有读取的文本行执行对应的操作，如果希望 sed 只作用于特定的行，那么可以通过 “行寻址” 的方式来进行对应的操作，”行寻址“ 有两种方式：一是通过传入数字区间来进行定位，二是通过文本匹配来进行定位。两种方式的使用模式相同，以如下的格式所示：

```bash
address command
或者
address {
	command1,
	command2,
	command3
}
```

值得注意的一点是，sed 使用 `$` 来表示最后一行，如果希望从某行开始执行对应的命令，可以如下所示：

```bash
# 将第三行到最后一行的文本中出现的 "the" 替换成 "a"
sed '3, $s/the/a/g' data.txt
```

### 删除行

通过 sed 的 `d`（delete）选项对对应的行进行删除，可以使用数字或者模式匹配匹配对应的行

如：

```bash
# 删除 2-3 行的文本
sed '2, 3d' data.txt

# 删除从 5 行到最后一行的文本
sed '5,$d' data.txt

# 删除包含 mud 的行
sed '/mud/d' data.txt

# 将包含 Dover 的行到 包含 because 的行进行删除
# 这里需要特别小心，只需要一个 d 命令即可，如果存在两个 d 命令，将会关闭删除功能
# 其次，d 命令在第一个匹配中就已经开始执行了，而不是匹配到第二个匹配项之后
sed '/Dover/, /because/d' data.txt
```

### 插入和附加行

通过 `i`（insert）命令在匹配的行之前插入行；通过 `a`（append）命令在匹配的行之后插入行，两者都遵循下面的命令结构：

```sh
# 这里的 address 同样支持数字和文本匹配 来定位行
sed '{address}command\ {插入的内容}'
```

比如，如果希望在最后一行之前插入 ”Hello World“，可以执行如下的命令：

```bash
sed '$i\Hello World' data.txt
```

如果希望在最后一行插入 ”Hello World“，可以执行如下的命令：

```bash
sed '$a\Hello World' data.txt
```



### 修改行

修改行通过 `c`（change）命令来完成，注意，这里所说的 ”修改“，实际上指的是替换整个行，例如，如果希望将第二行整个替换为 “Hello World”，可以执行如下的命令：

```bash
# 同样支持通过数字或者文本匹配的方式来定位行
sed '2c\Hello World' data.txt
```

**注意：** 当使用区间定位进行修改行的操作时，会将整个区间中的内容全部替换为对应的替换文本，而不是逐行进行替换，这点需要特别注意

### 字符转换

通过 `y` 命令将行中的字符替换为新的字符，具体格式如下所示：

```bash
# 这里的 address 同样支持数字和文本匹配
# inchars 和 outchars 相当于定义了映射
{address}y/inchars/outchars/
```

例如，如果希望从第二行开始，将文本中的所有字符按照对应的规则进行对应的转换，可以执行如下的命令：

```bash
# 从第二行开始，将 t 转换为 a、h 转换为 b
sed  '2,$y/th/ab/' data.txt
```

### 打印信息

通过 sed 的 `-n` 选项禁止默认的输出，因此可以在命令中自定义需要输出的内容。

除了上文中提到的 `p` 标记来输出内容之外，还存在以下几种方式进行输出：

- 通过 `p` 命令来输出对应的行
- 通过 “=” 命令打印对应的行号
- 通过 `l` 命令来列出行

例如，如果需要输出每行的行号，可以执行如下的 sed 命令：

```bash
sed  '=' data.txt
```

也可以通过命令组的方式来执行对应的命令，例如，如果希望在替换文本之前输出当前行，可以执行如下的 sed 命令：

```bash
sed -n '1,${
p
s/the/a/p
}' data.txt
```

如果希望插卡行号，可以加入 `=` 命令：

```bash
sed -n '1,${
=
p
s/the/a/p
}' data.txt
```

有时文本中可能包含转义字符，可以考虑使用 `l` 命令来查看这些转义字符：

```bash
sed -n '1,$l' data.txt
```

### 处理文件

有时可能希望在执行命令的过程中读取文件来填充到输出中，或者将处理后的文本流写回到文件中，sed 提供了这样的功能。

- 写入文件

    通过 `w` 命令来将处理后的文本行写入到文件中，具体的使用格式如下所示：

    [address]w *filename*

    例如，如果希望将 `data.txt` 文件中的 $[2, 3]$ 的数据写入到 `result.txt` 文件中，可以执行如下的 sed 命令：

    ```bash
    sed '2, 3w result.txt' data.txt
    ```

- 读取文件

    通过 `r`（read）命令来读取相关的文件内容，具体的使用格式如下所示：

    [address]r *filename*

    例如，如果希望读取 `/etc/hosts` 中的内容插入到 `data.txt` 的第三行之前，可以执行如下的 sed 命令：

    ```bash
    sed '2{
    r /etc/hosts
    }' data.txt
    ```

<br />

参考：

<sup>[1]</sup> 《Linux 命令行与 Shell 脚本编程大全》

