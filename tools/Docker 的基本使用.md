# Docker 的基本使用

> Docker 是一个开放源代码软件平台，用于开发应用、交互应用、运行应用。Docker 允许用户将基础设施中的应用单独分割出来，形成更小的应用，从而提高软件交付的速度

<sup>[1]</sup> 

Docker 和虚拟机类似，二者都是为了提供一个可靠的运行环境使得部署的应用程序能够正常运行；两者的不同之处在于虚拟机是对计算机硬件做了一层虚拟化，而 Docker 则是复用了操作系统的内核，因此 Docker 会比虚拟机更小，更快

二者的比较如下图所示：

<img src="https://s6.jpg.cm/2022/01/11/Lsq7NQ.png" style="zoom:80%" />

<br />

## 安装 Docker 

相关的安装手册可以参考：https://docs.docker.com/engine/install/，这里以在 CentOS 8 上安装 Docker 为例，

有几种不同的安装方式，这里采取比较简单地以存储仓库的方式来安装

1. 首先，删除旧有的有关 Docker 的相关组件

    ```bash
    sudo dnf erase docker*
    ```

2. 添加 Docker 的存储仓库

    ```bash
    # yum-utils 提供了 yum-config-manager 用于设置稳定的存储库
    sudo dnf install yum-utils
    
    # 使用 yum-config-manager 添加 Docker 的存储仓库
    sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
    ```

3. 安装 Docker 引擎

    ```bash
    # 该命令会安装版本最新的 Docker 引擎，同时也会安装必需的 Docker 容器和客户端工具
    sudo dnf install docker-ce docker-ce-cli containerd.io
    ```

    如果想要安装指定版本的 Docker 引擎，可以首先执行如下的命令查看可用的版本：

    ```bash
    sudo dnf list docker-ce --showduplicates | sort -r
    ```

    <img src="https://s6.jpg.cm/2022/01/11/Ls544i.png" style="zoom:80%" />

    可以按照下面的形式安装不同版本的 Docker：

    ```bash
    sudo dnf install docker-ce-<VERSION_STRING> docker-ce-cli-<VERSION_STRING> containerd.io
    ```

    注意，对应的版本号为中间 `3:20.10.9-3.el8` 中从 `:` 开始到 `-` 之间的部分，表示对应的版本号为 `20.10.9`

    如果想要安装版本为 `20.10.9` 的 Docker 引擎，可以像下面这么做：

    ```bash
    sudo dnf install docker-ce-20.10.9 docker-ce-cli-20.10.9 containerd.io
    ```

    安装之后不会启动 Docker， 同时会默认创建一个组名为 "docker" 的用户组，但是不会添加任何用户

4. 启动 Docker

    使用系统管理 Service 的命令可以启动 Docker，如下所示

    ```bash
    sudo systemctl start docker
    ```

    此时，再查看 Docker 的启动状态：

    ```bash
    sudo systemctl status docker.service
    ```

    如果是类似下图所示的状态，那么就说明启动成功了：

    <img src="https://s6.jpg.cm/2022/01/11/Ls7heH.png" style="zoom:80%" />

<br />

## 运行容器

容器时通过镜像来创建的，镜像相当于定义了容器的行为，可以将镜像类比于 Class，而容器则对应于按照 Class 新创建的实例对象

1. 拉取镜像

    可以从 <a href="https://hub.docker.com/"> Docker Hub </a> 上寻找需要的镜像，一般选择拉取量最多的即可，这里以拉取 `RabbitMQ` 的镜像为例：

    ```bash
    # 在 Docker Hub 上找到对应的镜像，以下的拉取镜像的命令来自官方
    # 使用 Docker 时由于需要使用到套接字文件，因此大部分情况下都需要通过超级用户的权限来执行 Docker 命令
    sudo docker pull rabbitmq
    ```

    如果不指定版本，则默认拉取最新的 `rabbitmq` 的镜像，如果想要拉取不同版本的镜像，则可以通过如下的命令进行拉取：

    ```bash
    # tag 表示需要拉取的版本对应的标签，Docker 通过 Tag 来区分不同的版本
    sudo docker pull rabbitmq:<tag>
    ```

    拉取完成之后，查看拉取到的镜像，可能像下面这样：

    <img src="https://s6.jpg.cm/2022/01/11/LslCLU.png" style="zoom:100%" />

2. 运行容器

    在运行容器之前需要创建容器，但是 `docker run` 会自动完成这一步操作，可以通过 `docker create` 显式地创建一个容器，但是该命令不会启动容器

    使用如下的命令来创建 `rabbitmq` 对应的容器，并运行它：

    ```bash
    sudo docker run -d --hostname lxh -p 5700:5762 --name lxh-rabbitmq rabbitmq 
    ```

    说明：

    - `-d` 选项表示将启动的容器放入后台运行，因此运行容器时无法看到启动时的输出；
    - `--hostname` 则是指定运行的容器的主机名；
    - `-p` 选项用于指定端口映射，上面的命令表示将本地主机的 `5700` 端口映射到容器的 `5672` 端口，因此访问本地主机的 `5700` 端口即可访问到容器的 `5762` 端口；
    - `--name` 选项则用于指定启动的容器的名称

    启动之后，应该会看到一串很长的由字母和数字组成的 `hash` 码，这个是当前生成的容器的摘要信息

    如果查看当前系统中运行的容器，使用 `docker ps` 命令可以做到，如下图所示：
    <img src="https://s6.jpg.cm/2022/01/11/Ls6r88.png" style="zoom:100%" />

    如果要查看该容器的输出日志信息，`docker logs <containerId>` 命令可以完成这一工作

    ```bash
    # -f 选项表示输出会随着日志的增加随之显示，21058bb96210 是当前运行的容器的 id
    sudo docker logs -f 21058bb96210 
    ```

3. 挂载卷

    有时运行数据库管理系统的容器，此时数据存放在容器内部独有的一个文件系统中，此时和宿主操作系统的交互将会变得比较麻烦，比如：希望执行宿主操作系统中的一个 `SQL` 脚本，或者希望将容器中的数据映射到宿主文件系统上。这种情况下，可以考虑将宿主机器上的一个目录挂载到容器上，使得容器和宿主机之间存在对应的关联关系：

    以 `PostgresQL` 的挂载卷为例：

    ```bash
    sudo docker run 
    -d --name my-postgres \
    -p 5555:5432 \
    -e POSTGRES_PASSWOED=12345678 \ 
    -e PGDATA=/var/lib/postgresql/data/pgdata \
    -v /root/pgdata:/var/lib/postgresql/data postgres
    ```

    注：`-e` 选项用于指定相关的启动参数，如环境变量等；`-v` 选项用于挂载卷

    现在，容器中的数据将会映射到宿主文件系统的 `/root/pgdata` 目录下，如果对目录下的配置文件进行修改，同样也会映射到宿主操作系统的文件系统中

    此时对于容器中的 `PostgresQL` 的数据库的操作，数据都会显式落到宿主机的挂载卷上，这样就保证了数据的持久性（即使容器被删除数据也不会丢失）

    <br />

    **注意**：并不建议在容器中运行数据库管理系统，这样做不仅增加了复杂性，同时也会降低数据库管理系统的性能

4. 在容器中执行命令

    前文介绍到，容器和虚拟机最大的不同之处在于容器复用了本地操作系统的内核。因此可以对运行中的容器执行本地操作系统的相关命令，`docker exec` 可以实现这一操作

    ```bash
    # 21058bb96210 是上文运行的 rabbitmq 容器的 id，-i 选项表示进入交互模式，
    # -t 表示分配一个 tty，/bin/bash 表示在 tty 中执行的命令
    sudo docker exec -it 21058bb96210 /bin/bash
    ```

    进入交互环境之后，会保留内核中原有的执行程序，因此可以运行基础的 `Linux` 命令，但是发行版可能和宿主机操作系统不一致，但是内核是一致的，如下图所示：

    <img src="https://s6.jpg.cm/2022/01/12/Ls05wy.png" style="zoom:100%" />



<br />

## 构建镜像

### .`dockerignore`  文件

类似于 `.gitignore`，当 Docker 客户端将当前项目的上下文发送到 Docker daemon 中生成 Docker Image 的这个过程中，如果发现了 `.dockerignore` 文件，那么将会按照 `.dockerignore` 文件中的相关部分去除掉，从而加快构建镜像的速度

一个 `.dockerignore` 文件的示例如下所示：

```dockerfile
*/temp* # 在 root 的直接子目录中移除以 temp 开头的目录和文件
```

<br />

### Dockerfile

Dockerfile 是用于构建容器镜像的一系列指令，一个 Dockerfile 是一个包含了用户能够通过调用所有的命令汇编成一个镜像的文本文档。通过 `docker build` 命令可以根据 Dockerfile 文件来构建对应的镜像

一个 Dockerfile 文件示例如下：

```dockerfile
# 基础镜像层，Dockerfile 要求一个合法的 Dockerfile 必须以 FROM 指令开始（ARG 可以在此之前出现）
FROM alpine:3.5

# RUN 指令将会执行对应的命令，这里执行的命令时安装 py2-pip
# RUN 指令会在原有的基础层上创建一个新的镜像层
RUN apk add --update py2-pip

# COPY 指令复制当前目录下的文件到容器中文件系统中的指定目录
COPY requirements.txt /usr/src/app/

RUN pip install --no-cache-dir -r /usr/src/app/requirements.txt

# 将需要的脚本文件复制到指令的位置
COPY app.py /usr/src/app/
COPY templates/index.html /usr/src/app/templates/

# EXPOSE 指令暴露端口，使得外部能够访问
# 默认情况下，端口采取的协议将是 TCP，当然也可以指定 UDP：EXPOSE 5000/udp
EXPOSE 5000

# CMD 命令用于执行一个命令，和 RUN 命令不同的地方在于 CMD 不会创建新的镜像层
# CMD 命令在一个 Dockerfile 文件中只能出现一次，如果出现多次，那么前面的 CMD 命令产生的影响将会被清除
# 这是由于 CMD 直接作用于容器所在的镜像层
CMD ["python", "/usr/src/app/app.py"]
```

除了上文中使用到的一些指令之外，还有以下一些比较重要的指令：

- `ARG`

    用于定义在构建镜像时能够能够访问到的变量，和使用 `docker build` 命令时使用 `--build-arg <varname>=<value>` 的参数一致。

    每个 `ARG` 指令都存在默认值，如果在整个构建过程中都没有设置值的话，将会使用默认值

    具体结构如下所示：

    ```dockerfile
    ARG <name>[=<default value>]
    ```

- `ENV`

    该指令用于定义相关的环境变量，和 `ARG` 指令的作用返回不同，该指令设置的变量将一直持续到容器中

    具体结构如下所示：

    ```dockerfile
    ENV <key>=<value> ...
    ```

    后续对环境变量的任意赋值都将直接影响到当前的环境变量，从而影响到最终构建的镜像。为此，需要特别注意

- `ENTRYPOINT`

    和 `CMD` 指令类似，`ENTRYPOINT` 指令是专门为了运行程序而设计的（`CMD` 指令只能保留最后一次的效果），和 `CMD` 一样，在同一个 Dockerfile 中，只有最后一个 `ENTRYPOINT` 才会产生实际的效果

    具体结构如下所示：

    ```dockerfile
    ENTRYPOINT ["executable", "param1", "param2"]
    ```

- `VOLUME`

    `VOLUME` 指令用于创建一个挂载点，类似于 `mount` 命令，使得通过构建的镜像对应的容器能够访问到外部设备（宿主操作系统、其它的容器）的相关文件信息

    使用示例如下所示：

    ```dockerfile
    # 创建 /data、/logs、/files 三个挂载点
    VOLUME ["/data", "/logs", "/files"]
    ```



创建 Dockerfile 文件之后，使用 `docker build` 命令即可构建容器镜像，如下所示：

```bash
# -f 选项表示构建的 Dockerfile 的文件名，默认为 Dockerfile
# -t 选项表示生成的 image 的名称，以 name:tag 的形式出现
# . 表示 Dockerfile 文件所在的目录
sudo docker build -f /root/Dockerfile -t app:latest .
```



<br />

## Docker 的架构

Docker 的架构图如下所示：
<img src="https://s6.jpg.cm/2022/01/12/LsaMK2.png" style="zoom:60%" />

<sup>[2]</sup>

- Docker daemon

    Docker 守护进程和传统意义上的 `Unix` 守护进程不同，Docker 客户端和守护进程之间是通过 Rest API 而不是 Unix 套接字来实现通信的

    Docker 守护进程会监听来自 Docker 客户端的请求，同时也会管理 Docker 对象：镜像、容器、网络等。一台主机上的 Docker 守护进程可以和其它主机上的 Docker 守护进程进行通信

- Client

    Docker 的客户端工具，负责发送相关的请求给 Docker daemon

- Registry

    Registry 存储了 Docker 镜像，类似于 Github 这种存储代码的仓库

Docker 采用的是 “客户端—服务端” 的架构模式，单独的 `docker` 命令只是作为一个客户端工具来使用，实际有关容器以及镜像的操作都是由 Docker 守护进程来完成的。

当本地的 Docker 客户端执行 `docker run` 命令时，首先会将请求发送到 Docker daemon，由 Docker daemon 来完成具体的操作；Docker daemon 首先检查本地是否存在对应的镜像，如果不存在对应的镜像，则需要首先到 Registry 中拉取对应的镜像；最后按照对应的镜像创建 Contianer 并启动

<br />

## 镜像的加载原理

### 镜像的文件系统层

Docker 的镜像是由一层一层的文件系统构成的，每层文件系统都是只读的（由容器创建的层可写），这种层级的文件系统也被称为 ”联合文件系统“ — UnionFS

如下所示：

<img src="https://s6.jpg.cm/2022/01/12/LsO0Re.png" style="zoom:50%" />

比较关键的两个层级文件系统是 `bootfs` 和 `rootfs`

- `bootfs`

    主要包含了 `bootloader` 和 `kernel`，`bootloader` 的主要作用是引导加载 `kernel`。和 Linux 的启动类似，Linux 在启动时会首先加载 `bootfs`，同样地，在 Docker 最底层的也是 `bootfs`，尽管这两者并不是同一个东西，但是思想是一致的

    Docker 的 `bootfs` 也用于加载内核相关的内容，在加载完成之后会将使用权交给 `kernel`，这点和 Linux 也是一致的（注意 Docker 的 `kernel` 复用了宿主机的 `kernel`）

- `rootfs`（Base Image）

    `rootfs` 构建在 `bootfs` 之上，包含了基本的 `Linux` 中的文件和目录结构。简单地来讲， `rootfs` 就是各种不同的操作系统的发行版

<br />

### 加载原理

所有的 Docker 镜像都起始于一个基础的镜像层，当进行修改或者增加新的内容时，会在当前所在的镜像层上创建新的镜像层，如下图所示：

<img src="https://s6.jpg.cm/2022/01/12/LsOfSi.png" style="zoom:50%" />

比如，如果此时基于 `Ubuntu 18.04` 创建了一个新的镜像，那么此时 `Ubuntu 18.04` 所在的镜像层就是第一层；如果此时在该镜像中安装了 `JDK`，那么就会在第一层的基础上创建第二镜像层；如果此时又添加一个库，那么又会添加一个新的镜像层

为了节约资源，同时由于镜像的每一层都是只读的，因此不同的镜像中可以复用其它镜像已经引入的镜像，如下图所示：

<img src="https://s6.jpg.cm/2022/01/12/LEGYI2.jpg" style="zoom:80%" />





<br />

参考：
<sup>[1]</sup> https://zh.wikipedia.org/wiki/Docker

<sup>[2]</sup> https://docs.docker.com/get-started/overview/