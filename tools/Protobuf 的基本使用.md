# Protobuf 的基本使用

Protobuf 是 Google 用于序列化数据对象的一种机制，使得数据对象能够在应用程序和服务器之间进行交互，尽管现在 Java 已经对应的序列化的实现方式，但是传统的序列化方式存在严重的缺陷，因此现在应该避免使用 Java 自带的序列化方式。

<br />

## 传统序列化方式的局限性

按照传统的实现，如果要使得一个对象能够被序列化成为一个二进制数据流，只需要使得定义的类实现 `Serializable` 接口即可，由于这个接口是一个空接口，因此大多数人认为实现序列化很简单，导致了 `Serializable` 接口的滥用

《Effective Java》（第三版）中总结了 `Serializable` 存在的不足<sup>[2]</sup>：

1. 类如果实现 `Serializable` 接口会大幅度降低类的灵活性
2. 实现 `Serializable` 接口会提高 bug 和漏洞出现的概率
3. 随着类的发行版本的更新，相关测试的负担也会加重

在书中，作者建议如果必须实现传输二进制数据对象的话，建议使用 JSON 的序列化方式或者是 Protocol Buffer（ProtoBuf）的方式来实现

使用 JSON 的序列化方式比较简单，因为序列化之后的数据对于人类来讲是可见的，Jackson 和 Gson 都能够很好地帮助我们完成这一工作，再次不做过多的介绍

<br />

## protoc 的安装

ProtoBuf 通过对应的 `.proto` 文件来生成需要的具体类信息，通过选择对应的参数选项即可生成不同的编程语言的版本，这个转换的过程是通过 `protoc` 来实现的。你可以认为这个工具是 `.proto` 源文件的一个编译器，与传统的编译器不同的地方在于别的编译器是将源文件转换为二进制文件，而 `protoc` 则是将 `.proto` 文件文件转换成为对应编程语言的源文件

如果是在 `Ubuntu` 操作系统上，可以通过如下的命令直接安装 `protoc` ：

```bash
sudo apt-get install protobuf-compiler
```

如果是别的操作系统，如 Windows，可以直接到官方的 GitHub 的发行版本中直接下载对应的二进制版本，具体地址：https://github.com/protocolbuffers/protobuf/releases/tag/v3.19.1，然后将 `protoc` 程序所在的目录添加到 `PATH` 环境变量即可

<br />

## 编写 proto 文件

以一个简单的 `addressBook.proto` 文件文件为例，介绍一下有关 `.proto` 的语法：

```protobuf
// 使用的 proto 语法的版本
syntax="proto2";

// 如果没有指定 java_package，那么将这个包位置作为生成类的目的包
package org.xhliu.proto.entity;

// 确保能够为每个类生成独立的 .java 文件而不是单个的 .java 文件
option java_multiple_files = true;
// 生成的目标类所在的包
option java_package = "org.xhliu.proto.entity";
// 定义生成的类的名字
option java_outer_classname = "AddressBookProtos";

// 简单的理解： message 和 class 关键字是对应的
message Person {
/*
对于属性的修饰：
optional 表示这个字段可能被设置属性，也可能不会设置属性
repeated 表示这个字段可能被设置多次，和数组是相对应的
required 表示这个字段必须被设置值，否则将会抛出 RuntimeException
*/
optional string name = 1;
optional int32 id = 2;
optional string email = 3;

// 相当于在类中定义了一个枚举类
enum PhoneType {
MOBILE = 0;
HOME = 1;
WORK = 2;
}

// 相当于定义了一个内部类
message PhoneNumber {
optional string number = 1;
// type 属性默认为 HOME
optional PhoneType type = 2 [default = HOME];
}

repeated PhoneNumber phones = 4;
}

// 再定义了一个类，其中包含 Person 类型的属性
message AddressBook {
repeated Person people = 1;
}
```

具体类型和实际变成语言中的类型对应如下<sup>[1]</sup>：

<table>
    <tr>
        <th>.proto Type</th>
        <th>Notes</th>
        <th>C++ Type</th>
        <th>Java Type</th>
        <th>Python Type<sup>[2]</sup></th>
        <th>Go Type</th>
    </tr>
    <tr>
        <td>double</td>
        <td></td>
        <td>double</td>
        <td>double</td>
        <td>float</td>
        <td>*float64</td>
    </tr>
    <tr>
        <td>float</td>
        <td></td>
        <td>float</td>
        <td>float</td>
        <td>float</td>
        <td>*float32</td>
    </tr>
    <tr>
        <td>int32</td>
        <td>Uses variable-length encoding. Inefficient for encoding negative numbers – if your field is likely to have
            negative values, use sint32 instead.
        </td>
        <td>int32</td>
        <td>int</td>
        <td>int</td>
        <td>*int32</td>
    </tr>
    <tr>
        <td>int64</td>
        <td>Uses variable-length encoding. Inefficient for encoding negative numbers – if your field is likely to have
            negative values, use sint64 instead.
        </td>
        <td>int64</td>
        <td>long</td>
        <td>int/long<sup>[3]</sup></td>
        <td>*int64</td>
    </tr>
    <tr>
        <td>uint32</td>
        <td>Uses variable-length encoding.</td>
        <td>uint32</td>
        <td>int<sup>[1]</sup></td>
        <td>int/long<sup>[3]</sup></td>
        <td>*uint32</td>
    </tr>
    <tr>
        <td>uint64</td>
        <td>Uses variable-length encoding.</td>
        <td>uint64</td>
        <td>long<sup>[1]</sup></td>
        <td>int/long<sup>[3]</sup></td>
        <td>*uint64</td>
    </tr>
    <tr>
        <td>sint32</td>
        <td>Uses variable-length encoding. Signed int value. These more efficiently encode negative numbers than regular
            int32s.
        </td>
        <td>int32</td>
        <td>int</td>
        <td>int</td>
        <td>*int32</td>
    </tr>
    <tr>
        <td>sint64</td>
        <td>Uses variable-length encoding. Signed int value. These more efficiently encode negative numbers than regular
            int64s.
        </td>
        <td>int64</td>
        <td>long</td>
        <td>int/long<sup>[3]</sup></td>
        <td>*int64</td>
    </tr>
    <tr>
        <td>fixed32</td>
        <td>Always four bytes. More efficient than uint32 if values are often greater than 2<sup>28</sup>.</td>
        <td>uint32</td>
        <td>int<sup>[1]</sup></td>
        <td>int/long<sup>[3]</sup></td>
        <td>*uint32</td>
    </tr>
    <tr>
        <td>fixed64</td>
        <td>Always eight bytes. More efficient than uint64 if values are often greater than 2<sup>56</sup>.</td>
        <td>uint64</td>
        <td>long<sup>[1]</sup></td>
        <td>int/long<sup>[3]</sup></td>
        <td>*uint64</td>
    </tr>
    <tr>
        <td>sfixed32</td>
        <td>Always four bytes.</td>
        <td>int32</td>
        <td>int</td>
        <td>int</td>
        <td>*int32</td>
    </tr>
    <tr>
        <td>sfixed64</td>
        <td>Always eight bytes.</td>
        <td>int64</td>
        <td>long</td>
        <td>int/long<sup>[3]</sup></td>
        <td>*int64</td>
    </tr>
    <tr>
        <td>bool</td>
        <td></td>
        <td>bool</td>
        <td>boolean</td>
        <td>bool</td>
        <td>*bool</td>
    </tr>
    <tr>
        <td>string</td>
        <td>A string must always contain UTF-8 encoded or 7-bit ASCII text.</td>
        <td>string</td>
        <td>String</td>
        <td>unicode (Python 2) or str (Python 3)</td>
        <td>*string</td>
    </tr>
    <tr>
        <td>bytes</td>
        <td>May contain any arbitrary sequence of bytes.</td>
        <td>string</td>
        <td>ByteString</td>
        <td>bytes</td>
        <td>[]byte</td>
    </tr>
</table>

<br />

## 生成具体类

由于对应的项目为 Java 类型的项目，因此需要生成 Java 类型的类，使用示例如下所示：

```bash
# -I 表示 .proto 文件所在的路径，--java_out 表示输出 Java 的类，并且指定对应的输出路径
protoc -I=. --java_out=. addressBook.proto
```

得到的结果如下图所示：

<img src="https://s2.loli.net/2022/01/01/Ae1D5bjnlLrZsSP.png" alt="2022-01-01 21-31-09 的屏幕截图.png" style="zoom:67%;" />

可以看到，已经自动生成了能够使用 ProtoBuf 的类

<br />

## 序列化对象

生成了对应的 ProtoBuf 的类之后，就可以使用这些类来结合 ProtoBuf 来序列化对象了，在那之前，需要添加如下依赖：

```xml
<dependency>
    <groupId>com.google.protobuf</groupId>
    <artifactId>protobuf-java</artifactId>
    <version>${protobuf.version}</version><!--  这里需要在 Maven 仓库找到对应的版本-->
</dependency>
```

明确一点，上文中的 `.proto` 文件只是帮助我们生成了能够使用 ProtoBuf 工具的类文件，实际上和序列化对象没有任何关系，具体的序列化操作是通过这个 `protobuf-java` 这个依赖项来实现的，请明确这一点

现在，直接使用 `.proto` 生成的类去调用 ProtoBuf 来实现对象的序列化和反序列化：

- 序列化

    ```java
    public static void main(String[] args) throws Exception {
        // 首先，通过构建者模式的方式来构造 Person 对象
        Person person = Person.newBuilder()
            .setId(1)
            .setEmail("123456789@gmail.com")
            .setName("xhliu")
            .addPhones(Person.PhoneNumber.newBuilder().setNumber("123456789").build())
            .build();
        
        // 再通过 AddressBook 的构建者方法来构建 AddressBook 对象
        AddressBook addressBook = AddressBook.newBuilder().addPeople(person).build();
        try (
            FileOutputStream out = new FileOutputStream("/tmp/addressBook.obj");
        ){
            addressBook.writeTo(out);
            System.out.println("序列化 AddressBook 对象成功");
        }
    }
    ```

    运行这段代码，可以在 `/tmp/` 目录下发现一个名为 `addressBook.obj` 的二进制文件，这个对象就是通过 ProtoBuf 序列化之后二进制数据，此时使用 `hexdump`  命令再加上 `-C` 选项，可以大致查看一下文件的内容，具体如下图所示：

    ![2022-01-01 21-45-53 的屏幕截图.png](https://s2.loli.net/2022/01/01/IFYt9BDsyJKQx5R.png)

- 反序列化

    ```java
    public static void main(String[] args) throws Exception {
        try (
            FileInputStream in = new FileInputStream("/tmp/addressBook.obj")
        ){
            AddressBook obj = AddressBook.parseFrom(in);
            System.out.println("反序列化之后的对象：");
            System.out.println(obj.getPeople(0));
        }
    }
    ```

    运行之后的结果如下图所示：

    ![2022-01-01 21-47-42 的屏幕截图.png](https://s2.loli.net/2022/01/01/RMQcbjskx3ThAno.png)



<br />

除了学习了 ProtoBuf 之外，我认为在这个过程中更加值得学习的是 Google 对于 ProtoBuf 的使用，通过 `.proto` 文件来结合使用对应的依赖库，大大减少了人工编写代码的成本，通过特定的小众语言来达到这一目标，这是十分值得学习的（《编程珠矶（续）》中讨论了对于小众语言的使用）

<br />

参考：

<sup>[1]</sup> https://developers.google.com/protocol-buffers/docs/proto#simple

<sup>[2]</sup> 《Effective Java》（第三版）