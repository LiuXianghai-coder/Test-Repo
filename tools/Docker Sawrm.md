# Docker Sawrm

Docker Swarm 存在以下特点：

- 使用 Docker 引擎整合集群管理

    使用 Docker 引擎的即可创建一个集群，而不需要额外的编排软件去创建或管理集群

- 去中心化设计

    Docker 引擎不是在部署的时候处理节点角色之间的区别，而是在运行时的任意时刻进行特定的处理。你可以使用 Docker 引擎部署两种类型的节点：manager 和 worker。这意味着你可以从单个的磁盘镜像中构建整个集群

- 声明式服务模型

    Docker 引擎使用一种声明式的方式让你能够在你的应用程序栈中定义各种服务的期望状态。例如，你可能会构建一个应用程序，该应用程序由 Web 前端、消息队列服务以及一个数据库后端组成

- 可缩放

    对于每个服务，你可以声明你想要运行的的任务的数量。当你扩大或缩小应用时，Docker Swarm 会自动地通过添加或移除任务来自动适应以维持期望的状态

- 期望状态和解

    Docker Swarm 的管理节点持续不断地监视着集群的状态并且调节任意在实际状态和你期望的状态之间的不同。例如，如果你设置一个服务来运行 10 个容器的副本，此时一个工作的机器主机中两个运行的副本已经奔溃，那么 Swarm Manager 将会创建两个新的副本来替换已经奔溃的副本，Swarm Manager 会新创建的两个副本放入到已经运行并且可用的工作节点中

- 多主机网络

    你可以为你的服务指定一个覆盖网络。Swarm Manager 在初始化或者更新应用程序时会自动为该覆盖网络上的容器分配地址

- 服务发现

    Swarm 的 manager 节点为每个在 Swarm 中的节点分配了一个唯一的 `DNS` 名称，并对运行中的容器进行负载均衡。你可以通过在 Swarm 中内嵌的 `DNS` 服务查询每个运行在 Swarm 中的容器

- 负载均衡

    你可以开放服务的端口用于外部的负载均衡。在内部，Swarm 允许你指定如何在节点中分发服务容器

- 默认安全

    每个在 Swarm 中的节点，Swarm 都强制执行 TLS 的相互身份验证和加密，以保护自身和其它所有节点之间的通信，你可以选择使用自签名根证书或来自自定义根 CA 的证书。

- 滚动更新

    在更新时，你可以逐步地将服务更新到各个节点。Swarm Manager 让你可以控制部署服务到不同节点之间的延迟。如果在更新时遇到了问题，你可以回滚到之前的服务版本



<br />

## 组件介绍

- 节点

    Docker Swarm 中节点分为两类：一类是 Worker 节点，用于执行任务；另一类是 Manager 节点，用于管理Worker 节点，同时自身也可以作为一个 Woker 节点用于执行任务。两者的关系如下图所示：
    <img src="https://s6.jpg.cm/2022/01/17/LF2zCC.png" style="zoom:80%">

    Manager 节点主要具有以下的几个作用：

    - 维护集群的状态
    - 调度服务
    - 为 Swarm 集群提供外部可调用的 API 接口
    - 提供服务注册发现、负载均衡等功能

    Manager 节点通过 <a href="https://raft.github.io/raft.pdf">Raft</a> 算法来维持内部服务的内部状态一致性。为了获得 Docker Swarm 故障容忍的优势，Docker 建议根据具体的高可用需求，尽可能将 Manager 节点数设置为奇数个

    <br />

    Worker 节点用于执行具体的任务，不参与 Raft 的分布式状态，不做调度决策，也不服务于 Swarm 的 Http API，只是单纯地执行 Manager 分发的任务。

    <br />

    由于 Manager 节点在默认情况下也可以作为 Worker 节点参与任务的执行，为了提高系统的稳定性，请将 Manager 节点的可用性设置为 Drain，这样可以将任务分配到实际的工作节点上，使得分工更加明确，具体如下所示：

    ```bash
    # 将 manager1 的可用性设置为 drain
    sudo docker node update --availability drain manager1
    ```

    <br />

- 服务

    任务（Task）是 Swarm 中最小的调度单位，任务包含了一个 Docker 容器和在容器内运行的命令，如果某一个任务奔溃，那么协调器将会创建一个新的副本任务，将该副本任务放入到其它可用的容器中

    任务是一个单向的机制，它单调进行经历了一系列的状态：assigned、prepared、running 等，具体如下图所示：

<img src="https://s6.jpg.cm/2022/01/17/LFKtpG.png" style="zoom:60%" />

如果在执行的过程中失败了，那么 Manager 的编排器将会将会直接将该任务以及该任务对应的容器进行删除，然后在其它的节点上创建一个新的任务继续执行以维护 Swarm 的状态

<br />

Service 是一组任务的集合，Service 定义了任务的属性，如：任务的个数、服务策略、镜像的版本号等

服务、任务以及容器之间的关系如下图所示：

<img src="https://s6.jpg.cm/2022/01/17/LFCALE.png" style="zoom:80%">

服务在 Swarm 中有两种模式：Replicated 和 Global。对于 Replicated 模式，你可以指定希望执行的任务的副本数，这些副本将会在不同的节点上执行相同的任务；对于 Global 模式，这种模式下的 Service 将会在 Swarm 集群的每个节点上都执行相同的任务。

Replicated Service 和 Global Service 的对比如下图所示：

<img src="https://s6.jpg.cm/2022/01/17/LFh5KD.png" style="zoom:80%">

<br />

## Swarm 网络

### 网络类型

在 Docker Swarm 的集群中，主要存在以下三种网络：

- Overlay Network：用于管理 Swarm 中每台机器的 Docker 的守护进程之间的通信。因此如果直接将一个服务附加到已有的 Overlay Network 中，那么新加入的服务就可以立即和其它的服务进行通信
- Ingress Network：一个特殊的 Overlay Network，用于服务中节点之间的通信。当任何 Swarm 节点在发布的端口上收到请求时，它会将该请求交给一个名为 IPVS 的模块。IPVS 跟踪参与该服务的所有 IP 地址，然后选择其中的一个，并通过 Ingress 网络将请求路由到它。初始化或加入 Swarm 集群时会自动创建 Ingress 网络，大多数情况下，不需要自定义配置
- Docker Gwbridge Network：一种桥接网络，将 Overlay 网络连接到一个单独的 Docker 守护进程的物理网络。默认情况下，每个正在运行的任务的容器都将连接到本地 Docker 守护进程所在的宿主机器的 docker_gwbridge 网络。

<br />

### 流量的分类 

Docker Swarm 的流量分为两大类：控制管理流量和应用数据流量

- 控制管理流量（Controller And Management Plane Traffic）

    对于 Swarm 的 Manager 节点而言，当数据 “流入/流出” 时，这些流量总是被加密的，如下图所示：
    <img src="https://s6.jpg.cm/2022/01/17/LFTT32.png" style="zoom:60%" />

- 应用数据流量（Application Data Plane Traffic）



<br />

## 实际使用

首先，我在我的计算机上安装了两台虚拟机，二者的操作系统都是 `CentOS 8`，现在将它们作为 Worker 节点，两者的 IP 地址（子网地址）分别为：192.168.0.105、192.168.0.107，宿主机的 IP 地址为 192.168.0.106，现在将宿主机作为 Manager 节点

<br />

### 配置 Swarm

首先，在宿主机上初始化 Docker Swarm，构建一个只有一个 Mannager 节点的集群

```bash
sudo docker swarm init --advertise-addr 192.168.0.106
```

执行完成之后，可能会得到类似下图的输出：

![2022-01-17 20-21-57 的屏幕截图.png](https://s2.loli.net/2022/01/17/pzQaFiX9uTPq25V.png)

其中，图中的输出 `docker swarm join --token` 需要在别的 Worker 节点的机器上执行，现在分别在两台 Worker 节点的机器上执行相应的命令

对于 192.168.0.105、192.168.0.107，都执行添加到 Swarm 的命令：

```bash
# 具体命令可能不同，请使用 docker swarm init 的实际输出
sudo docker swarm join --token SWMTKN-1-4wihvahpfwg5kp7i19fgadhq90434hnzeu10peey3fudn6rfvc-b94kh7q2vyvopg56jzwr9clhh 192.168.0.106:2377
```

执行完成之后，会发现此时已经有两个 Worker 节点被添加到 Swarm 中了：

![2022-01-17 20-37-29 的屏幕截图.png](https://s2.loli.net/2022/01/17/a5EPQqv7cnWAfLF.png)

<br />

### 创建 Service

以创建 Nginx 为例，创建一个具有两个副本任务的 Service，如下所示：

```bash
# 在宿主机器（Manager 节点）上创建 Service，由 Manager 分发到 Worker 节点
# 这里有个坑，如果宿主机上没有拉取指定的镜像，可能会导致其它主机上拉取到的镜像的版本是不一样的，因此创建 Service 时一定要指定拉取的镜像的标签
sudo docker service create --replicas 2 --name nginx --publish 8080:80 nginx:latest

# 在这里还有个坑，当宿主机器中存在代理服务器时，可能会被 Docker Hub 视为恶意的请求而拉取不到镜像，因此需要将主机中的代理去除：unset http_proxy
```

正常运行的结果类似下图所示：

![2022-01-17 21-40-49 的屏幕截图.png](https://s2.loli.net/2022/01/17/als2uiE7UAxyRtB.png)

由于 Docker Swarm 中负载均衡的存在，此时对任意节点的访问都将被负载到有效的容器主机上，如下图所示：

<img src="https://s2.loli.net/2022/01/17/Q7WgwrAPDZECbNO.png" alt="image.png" style="zoom:67%;" />

<br />

参考：

<sup>[1]</sup> https://www.cnblogs.com/wtzbk/p/15604370.html

<sup>[1]</sup> https://docs.docker.com/