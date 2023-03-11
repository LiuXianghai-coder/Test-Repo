# Spring Cloud（二）NacOS

NacOS 是 Spring Cloud Alibaba 中的一个组件，用于管理微服务。NacOS 主要包含两部分功能：配置中心和服务发现

## 快速入门

在这之前，需要首先安装 NacOS，你可以下载它的源代码，然后在自己的计算机上进行编译。这比较耗时，因此这里选择直接下载二进制版本：https://github.com/alibaba/nacos/releases

需要注意，NacOS 不同版本之间的差异较大，这里选择的二进制版本为 $2.0.4$，下载完成之后解压，进入解压后的 `nacos` 目录。

现在就能够正常启动 NacOS 了，但是需要确保你的 $8848$ 端口没有被其它进程占用，或者你也可以在配置文件中修改 NacOS 的启动端口。

如果是在 `Shell` 环境下，执行如下的命令就能够启动：

```shell
./bin/startup.sh -m standalone
```

这个命令将会以单机的方式启动 NacOS。

除此之外，你可能希望 NacOS 连接到数据库而不是仅仅只是能够使用，NacOS 目前只支持 `MySQL` 数据库的连接，在那之前，你需要在你的数据库服务器中创建对应的数据库，建议将数据库名设置为 `nacos`（因为这是 NacOS）默认的，然后将 `nacos` 目录下的 `conf` 目录中的 `nacos-mysql.sql` 脚本文件加载到对应的数据库中，如果你的数据库名为 `nacos`，那么执行如下的命令即可：

```sh
mysql -u root -p nacos < conf/nacos-mysql.sql
```

还有最后一步，修改 `conf` 目录中的 `application.properties` 配置文件，修改其中数据库的配置：

```properties
# 默认情况下这三个配置属性都是被注释掉的，因此需要手动解除注释
# 数据哭的访问 url，注意对应自己的数据库名
db.url.0=jdbc:mysql://127.0.0.1:3306/nacos?characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC
# 对应的数据库连接用户和密码
db.user.0=root
db.password.0=12345678
```

现在，再执行一次上文的启动命令，即可成功开启 NacOS

### NacOS 的配置管理

#### 数据模型

对于 NacOS 的配置管理，通过 `Namespace`、`Group`、`Data ID` 能够确定到一个配置集合，NacOS 的数据模型如下所示：

<img src="https://img2018.cnblogs.com/i-beta/1216484/202002/1216484-20200215203202878-2105320123.png" alt="img" style="zoom:80%;" />

`Namespace`：用于不同环境的配置隔离，例如：隔离开发环境（测试环境和生产环境，配置文件不同）、隔离不同的用户（不同的开发人员使用同一个 NacOS 管理各自的配置，在这一层进行隔离）

`Group`：即配置分组，可以类比 `Unix` 中的用户组。配置组的主要作用是用于区分不同的项目或者应用

`Data ID`：即配置文件的所属 ID，这个配置文件也被称为配置集（一个配置文件可以有许多的配置属性，因此被称为配置集）。在配置集中的具体配置则被称为 “配置项”





## 配置中心

一个应用程序服务在启动和运行的过程中，都需要加载一些配置信息，这些配置信息可以来自操作系统、配置文件等。当一个单体应用被拆分成多个微服务应用程序时，这些微服务应用程序也必须具有对应的配置属性。

手动管理这些微服务应用程序比较繁琐而且容易出现错误，一个好的解决方案是引入一个配置中心专门用于管理这些配置属性，如下图所示：

![config-center.png](https://s2.loli.net/2022/02/26/Gvj7bJ3kztENwU5.png)

NacOS 作为配置中心的处理流程如下：

- 客户端在配置中心中更新配置信息
- 服务集群中的服务得到配置信息被修改的通知，从配置中心中获取最新的配置信息



## 服务发现

参考：

<sup>[1]</sup> https://mp.weixin.qq.com/s/aAAAVdJFJ4--C2EYTNa_1A