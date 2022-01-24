# Shiro 简介

官方文档的介绍如下：

> Apache Shiro is a powerful and flexible open-source security framework that cleanly handles authentication, authorization, enterprise session management and cryptography.
>
> Apache Shiro’s first and foremost goal is to be easy to use and understand. Security can be very complex at times, even painful, but it doesn’t have to be. A framework should mask complexities where possible and expose a clean and intuitive API that simplifies the developer’s effort to make their application(s) secure

简单的翻译如下：

> Apache Shiro 是一个强大的、灵活的开源安全框架，可以干净地处理验证、授权、企业会话管理和加密等功能
>
> Apache Shiro 首要的和最关键的目标是使得容易使用和理解。处理安全相关的业务时，可以是十分复杂的问题，甚至是令人痛苦的事情。但是不一定是这样，一个框架应当尽可能隐藏复杂性，并暴露简洁直观的 API，以简化开发人员确保其应用程序安全的工作

<br />

## 相关特性

Apache Shiro 具有的主要特性如下图所示：

<img src="https://s2.loli.net/2022/01/23/JfzqwkyHiYWBC52.png" alt="image.png" style="zoom:120%;" />

主要关注的地方在于 `Primary Concerns` 这一部分，具体介绍如下：

- `Authentication`（验证）：有时也被称为 “登录”
- `Authorization`（授权）：访问控制，例如：谁能够去做什么
- `Session Management`（会话管理）：管理用户指定的会话
- `Cryptography`（加密）：使用加密算法保持数据安全，同时仍然易于使用

<br />

## 架构设计

### 概念设计

在最高级别的概念水平上，Shiro 的架构有以下三个关键概念：`Subject`、`SecurityManager` 和 `Realms`。这几个组件之间的交互如下图所示：

<img src="https://s2.loli.net/2022/01/23/piH48Nmag1uGeTr.png" alt="image.png" style="zoom:120%;" />

- `Subject`（主题）：`Subject` 本质上是当前执行的用户的安全特定视图（这里的用户可以是人也可以是其它软件），一个 Subject 可以是一个人，也可以是一个第三方服务。`Subject` 实例都需要绑定到 `SecurityManager`，当你和 `Subject` 交互时，这些交互将转换为与 `Subject` 指定的 `SecurityManager` 进行交互
- `SecurityManager`：`SecurityManager` 是 Shiro 架构的核心，`SecurityManager` 充当了一种伞型结构，协调其内部安全组件，共同构成一个对象图。然而，一旦 `SecurityManager` 和其内部对象图被一个应用配置了，那么它通常会失效，应用程序的开发者们几乎将他们的时间都花费在了他们的 `Subject` API 上。
- `Realm`：`Realm` 作为在 Shiro 和你的应用的安全数据之间充当桥梁（或连接器）的作用，当需要与安全相关的数据（如账户信息）进行实际交互以执行身份验证（登录）和访问控制（授权）时，Shiro 会从应用程序中配置的一个或者多个 `Realm` 中查找相关的内容。

`Realm` 本质上是一个安全特定的 `DAO`（数据访问对象），它封装了连接到数据源的连接细节，并且使得 Shiro 需要的关联数据变得可用。

当配置 Shiro 时，你必须指定至少一个 `Realm` 用于身份验证或者授权。`SecurityManager` 可以配置多个 `Realm` ，但是至少需要一个 `Realm`

Shiro 提供了许多的开箱即用的 `Realm` 来连接到安全数据源（也被称为目录），如 LDAP、关系数据库（JDBC）、文本配置源（如 `ini` 文件）以及其它。如果这些默认的 `Realm` 不能呢个满足你的要求，您可以插入你自己的 `Realm` 实现来表示自定义自定义的安全数据源

<br />

### 具体架构<a id="arch-detail"></a>

具体架构设计如下图所示：

<img src="https://s2.loli.net/2022/01/23/84pXUemu7qMIG5z.png" alt="image.png" style="zoom:120%;" />

- `Subject`：`org.apache.shiro.subject.Subject`

    简单理解就是当前和系统进行交互的对象

- `SecurityManager`：`org.apache.shiro.mgt.SecurityManager`

    如上文 “概念设计” 部分提到的，`SecurityManager` 封装了大部分的功能，是 Shiro 的核心组件。它主要是一个 “伞型” 对象，用于协调其托管组组件以确保它们顺利协同工作。除此之外，`SecurityManager` 还用于管理每个应用程序用户的视图。因此它可以知道如何为每个用户执行安全操作

- `Authenticator`：`org.apache.shiro.authc.Authenticator`

    `Authenticator` 是负责执行和i响应用户身份验证的（登录）的组件，当一个用户尝试登录时，登录逻辑将会被 `Authenticator` 执行。

    `Authenticator` 知道如何协调一个或多个存储用户（账户）信息的 `Realm` ，从这些 `Realm` 中获取数据用于验证用户的身份，以确保用户确实是正确的用户。

    - `Authentication Strategy`：`org.apache.shiro.authc.pam.AuthenticationStrategy`

        如果超过一个 `Realm` 被配置了，那么 `AuthenticationStrategy` 将会协调这些 `Realm` 以确定身份验证成功哦你或者失败的条件（例如，如果多个 `Realm` 中有一个是成功的，但是其它的 `Realm` 都是失败的，那么本次尝试是否是成功的？必须是所有的 `Realm` 都成功？还是只需要一个成功即可？ ）

- `Authorizer`：`org.apache.shiro.authz.Authorizer`

    `Authorizer` 组件用于负责用户的访问权限，它是最终决定用户是否被允许做某事的机制。类似 `Authenticator`，`Authorizer` 也知道如何协调多个后端数据源来获取访问角色和权限的信息。`Authorizer` 使用这些信息来确定是否允许用户执行给定的操作

- `SessionManager`：`org.apache.shiro.session.mgt.SessionManager`

    `SessionManager` 知道如何创建和管理用户 `Session` 的生命周期，以便为所有环境中的用户提供强大的 `Session` 体验。在所有的安全框架中，这是 Shiro 特有的一个特征，Shiro 能够在任何环境中本地管理用户会话，即使没有可用的 Web 或 EJB 容器也是如此。默认情况下，Shiro 将会使用现有的会话机制（如 Servlet Container），但是如果没有（例如在独立的应用程序或非 Web 应用程序中），它将使用内置的企业会话管理来提供相同的编程体验

    <br />

    - `SessionDAO`：`org.apache.shiro.session.mgt.eis.SessionDAO`

        `SessionDAO` 代表 `SessionManager` 提供了 `Session` 持久化的操作，这允许将任何数据存储插入到会话管理基础架构中。

- `CacheManager`：`org.apache.shiro.cache.CacheManager`

    `CacheManager` 用于创建和管理其它 Shiro 组件使用的 `Cache` 实例的生命周期。由于 Shiro 可以访问许多后端数据源进行身份验证、授权和会话管理，所以缓存一直是框架中的一流架构特性，可以在使用这些数据源的同时提高性能。任何现代的开源或或企业缓存产品都可以插入 Shiro 的缓存中以提高快速高效的用户体验

- `Cryptography`：`org.apache.shiro.crypto.*`

    加密是企业安全框架的补充。Shiro 加密包下包含了易于使用和理解的加密密码、消息摘要和不同编解码器的实现。这个加密包中的所有类都经过精心设计，非常易于使用和理解。

- `Realm`：`org.apache.shiro.realm.Realm`

    如 “概念设计” 中提到的，`Realm` 是应用程序的安全数据和 Shiro 之间进行连接的桥梁
