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

    在运行容器之前需要创建容器，但是 `docker run` 会自动完成这一步操作

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

3. 修改挂载卷

    默认情况下，每个运行时的容器都有自己的文件系统，别的容器无法干预当前正在运行的容器，这在一定程度上提高了每个应用之间的独立性。但是有时需要修改对应的配置文件，或者希望将某些插件加入到运行的容器中时，虽然也可以通过执行 `docker exec` 命令进入容器所在的底层内核来完成相关的操作，但是如果能够直接在宿主操作系统上直接完成无疑是一个更好的选择

    

4. 





<br />

参考：
<sup>[1]</sup> https://zh.wikipedia.org/wiki/Docker