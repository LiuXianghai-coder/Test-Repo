# Docker 部署工具

Docker 容器的创建比较简单，容器解决了应用程序对于运行环境的依赖问题，但是在当前所处的微服务盛行的情况下，手动管理容器是一件比较重复其及其枯燥的工作，这项工作理论上可以通过计算机来完成，因此涌现除了许多的部署容器的工具，本文将简要介绍一下 Docker Compose 和 Docker Swarm 的使用

<br />

## Docker Compose<sup>[1]</sup> 

Docker Compose 是一个用于定义和运行多个容器的 Docker 应用程序，通过 Docker Compose，你可以使用一个 yaml 文件来定义你的应用服务。然后，通过一个单独的命令，可以创建并且运行所有你已经配置好的服务。

使用以下三个步骤来使用 Docker Compose：

1. 通过 `Dockerfile` 定义你的应用程序的运行环境，使得你的应用程序可以在任意的环境下都能够运行

2. 在 `docker-compose.yml` 配置文件中定义你应用服务的配置关系，使得这些容器能够在彼此独立的环境下能够协同工作

3. 运行 `docker compose up` 以及一些其它的 Docker Compose 命令启动或者运行你的整个应用程序。直接使用 `docker-compose up` 命令也是可以的，前提是需要安装 `docker-compse` 

    以 `CentOS` 为例，按照如下的命令来安装 `docker-compose`

    ```bash
    # 直接获取对应的 docker-compose 二进制文件，具体将 v2.2.3 换成指定的版本
    sudo curl -L "https://github.com/docker/compose/releases/download/v2.2.3/docker-compose-linux-x86_64" -o /usr/local/bin/docker-compose
    
    #  修改 docker-compose 的执行权限
    sudo chmod +x /usr/local/bin/docker-compose
    ```

Docker Compose 存在 `V2` 和 `V3` 两种版本，开发时建议使用 `V3` 版本，因为许多 `V2` 版本的特性在 `V3` 版本中已经被移除了，当升级时可能会出现问题

一个可能的 `docker-compose.yml` 文件示例如下所示：

```yaml
services:
  backend:
    build: backend
    ports:
      - 8080:8080
    environment:
      - POSTGRES_DB=example
    networks:
      - spring-postgres
  db:
    image: postgres
    restart: always
    secrets:
      - db-password
    volumes:
      - db-data:/var/lib/postgresql/data
    networks:
      - spring-postgres
    environment:
      - POSTGRES_DB=example
      - POSTGRES_PASSWORD_FILE=/run/secrets/db-password
    expose:
      - 5432
volumes:
  db-data:
secrets:
  db-password:
    file: db/password.txt
networks:
  spring-postgres:
```

在 `docker-compose.yml` 文件所在的目录下， 执行 `docker-compose up -d` 命令即可完成相关容器的部署

具体的语法细节请参考：https://docs.docker.com/compose/compose-file/compose-file-v3/

<br />

## Docker Swarm

Docker Compose 解决了在单个主机上部署多个服务带来的问题，除了在单台主机上部署相关的服务之外，在多台主机上部署应用程序也是一个不小的挑战。所幸，Docker 提供了 Docker Swarm 工具来帮助我们完成这一步骤

<br />

### 简介





<br />

参考：

<sup>[1]</sup> https://docs.docker.com/compose/