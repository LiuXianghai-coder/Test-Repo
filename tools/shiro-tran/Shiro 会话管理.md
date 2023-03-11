# 会话管理

Apache Shiro 提供了安全框架领域中独一无二的东西：适用于任何应用程序的完整企业级会话解决方案，从最简单的命令行和智能手机应用程序到最大的集群企业 Web 应用程序。

这对许多应用程序有很大的影响，在 Shiro 之前，如果您需要会话支持，则需要将应用程序部署在 Web 容器中或使用 EJB 状态会话 Bean。 Shiro 的 Session 支持比这两种机制中的任何一种都更易于使用和管理，并且它可以在任何应用程序中使用，而不管容器时怎么样的。

即使您将应用程序部署在 Servlet 或 EJB 容器中，仍然有令人信服的理由使用 Shiro 的 Session 支持而不是容器的支持。 以下是 Shiro 会话支持提供的最理想的功能列表：

- 基于 **POJO/J2SE**（IOC 友好）：Shiro 中的所有内容（包括 `Sessions` 和 Session Management 的所有方面）都是基于接口并使用 POJO 实现的。 这允许您使用任何与 `JavaBeans` 兼容的配置格式（如 `JSON`、`YAML`、`Spring XML` 或类似机制）轻松配置所有会话组件。您还可以轻松扩展 Shiro 的组件或根据需要编写自己的组件以完全自定义会话管理功能。
- 容易配置的会话存储：因为 Shiro 的 `Session` 对象是基于 `POJO` 的，所以会话数据可以很容易地存储在任意数量的数据源中。这使您可以准确地自定义应用程序会话数据所在的位置，例如，文件系统、内存、网络分布式缓存、关系数据库或专有数据存储。
- 独立于容器的集群：Shiro 的会话可以使用任何现成的网络缓存产品轻松集群，例如 Ehcache + Terracotta、Coherence、GigaSpaces 等。 人。 这意味着您可以为 Shiro 配置一次且仅一次的会话集群，无论您部署到哪个容器，您的会话都将以相同的方式进行集群。 无需容器特定的配置！
- 异质的客户端访问：与 EJB 或 Web 会话不同，Shiro 会话可以在各种客户端技术之间“共享”。例如，桌面应用程序可以 “查看” 和 “共享” 同一用户在 Web 应用程序中使用的同一物理会话。 除了 Shiro 之外，我们不知道有任何框架可以支持这一点。
- 事件监听：事件侦听器允许您在会话的生命周期内侦听生命周期事件。 您可以侦听这些事件并对自定义应用程序行为做出反应 - 例如，在会话到期时更新用户记录。
- 主机地址保留：Shiro Sessions 保留发起会话的主机的 IP 地址或主机名。这使您可以确定用户所在的位置并做出相应的反应（通常在 IP 关联具有确定性的 Intranet 环境中很有用）。
- 不活动/过期支持：会话由于预期的不活动而过期，但如果需要，可以通过 `touch()` 方法延长它们以保持它们“活着”。 这在富 Internet 应用程序 (RIA) 环境中很有用，其中用户可能正在使用桌面应用程序，但可能不会定期与服务器通信，但服务器会话不应过期。
- 透明网络使用：Shiro 的 Web 支持完全实现并支持 `Servlet 2.5` 规范的 `Sessions`（`HttpSession` 接口及其所有相关 API）。 这意味着您可以在现有的 Web 应用程序中使用 Shiro 会话，而无需更改任何现有的 Web 代码。
- 可用于单点登录：因为 Shiro 会话是基于 `POJO` 的，所以它们很容易存储在任何数据源中，并且可以在需要时跨应用程序 “共享”。 我们称之为 “穷人的 SSO”，它可以用来提供简单的登录体验，因为共享会话可以保留身份验证状态。

<br />

## 使用会话

就像 Shiro 中的几乎所有其他东西一样，您通过与当前正在执行的 `Subject` 交互来获得一个 `Session`：

```java
Subject currentUser = SecurityUtils.getSubject();

Session session = currentUser.getSession();
session.setAttribute( "someKey", someValue);
```

`currentUser.getSession()` 相当于 `currentUser.getSession(true)`

对于熟悉 `HttpServletRequest` API 的人来说，`Subject.getSession(boolean create)` 方法的功能与 `HttpServletRequest.getSession(boolean create)` 方法相同：

- 如果 `Subject` 已经有 `Session`，则忽略布尔参数并立即返回 `Session`
- 如果 `Subject` 还没有 `Session` 并且 `create` 参数为 `true`，则将创建并返回一个新会话。
- 如果 `Subject` 还没有 `Session` 并且 `create` 参数为 `false`，则不会创建新会话并返回 `null`。

`getSession` 方法适用于任何应用程序，甚至是非 Web 应用程序。

在开发框架代码时，可以使用 `subject.getSession(false)` 来确保不会不必要地创建 `Session`。

一旦你获得了一个主题的会话，你可以用它做很多事情，比如设置或检索属性、设置它的超时等等。 请参阅 `Session` JavaDoc 以了解单个 `Session` 能做的事情

<br />

## 会话管理

`SessionManager`，顾名思义，管理应用程序中所有主题的会话 — 创建、删除、不活动和验证等。与 Shiro 中的其他核心架构组件一样，`SessionManager` 是由 `SecurityManager` 维护的顶级组件。

默认的 `SecurityManager` 实现默认使用开箱即用的 `DefaultSessionManager`。`DefaultSessionManager` 实现提供了应用程序所需的所有企业级会话管理功能，例如会话验证、孤儿清理等。这可以在任何应用程序中使用。

Web 应用程序使用不同的 `SessionManager` 实现。 有关特定于 Web 的会话管理信息，请参阅 Web 文档。

与 `SecurityManager` 管理的所有其他组件一样，`SessionManager` 可以通过所有 Shiro 的默认 `SecurityManager` 实现 (`getSessionManager()/setSessionManager()`) 上的 JavaBeans 样式的 getter/setter 方法获取或设置

在 `shiro.ini` 配置文件中配置 `SessionManager`：

```ini
[main]
...
sessionManager = com.foo.my.SessionManagerImplementation
securityManager.sessionManager = $sessionManager
```

但是从头开始创建 `SessionManager` 是一项复杂的任务，而且大多数人自己并不希望自己去创建它。Shiro 开箱即用的 `SessionManager` 实现具有高度可定制性和可配置性，可以满足大多数需求。本文档的其余部分假设您在介绍配置选项时将使用 Shiro 的默认 `SessionManager` 实现，但请注意，您基本上可以创建或添加几乎任何您想要的东西。

<br />

### 会话超时

默认情况下，Shiro 的 `SessionManager` 实现默认为 30 分钟会话超时。也就是说，如果创建的任何 `Session` 保持空闲（空闲： `lastAccessedTime` 未更新）30 分钟或更长时间，则认为该 `Session` 已过期，将不再允许使用。

您可以设置 `SessionManager` 的 `globalSessionTimeout` 属性来定义所有会话的默认超时值。 例如，如果您希望超时为一小时而不是 30 分钟，可以在 `shiro.ini` 配置文件中做如下的配置：

```ini
[main]
...
# 3,600,000 milliseconds = 1 hour
securityManager.sessionManager.globalSessionTimeout = 3600000
```

上面的 `globalSessionTimeout` 值是所有新创建的 Sessions 的默认值。您可以通过设置单个会话的超时值来控制每个会话的会话超时。 和上面的 `globalSessionTimeout` 一样，以毫秒为单位

<br />

### 会话监听

Shiro 支持 `SessionListener` 的概念，以允许您在重要的会话事件发生时对其做出反应。 您可以实现 `SessionListener` 接口（或继承 `SessionListenerAdapter`）并对会话操作做出相应的响应。

由于默认的 `SessionManager` 的 `sessionListeners` 属性是一个集合，您可以像 `shiro.ini` 中的任何其他集合一样，使用一个或多个侦听器实现来配置 `SessionManager`：

```ini
[main]
...
aSessionListener = com.foo.my.SessionListener
anotherSessionListener = com.foo.my.OtherSessionListener

securityManager.sessionManager.sessionListeners = $aSessionListener, $anotherSessionListener, etc.
```

当任意 `Session` 发生事件时，`SessionListener` 会收到通知 - 而不仅仅是特定的 `Session`。

<br />

### 会话存储

每当创建或更新会话时，它的数据都需要持久保存到存储位置，以便应用程序以后可以访问它。同样，当会话无效且使用时间更长时，需要将其从存储中删除，以免会话数据存储空间耗尽。`SessionManager` 的实现将这些创建/读取/更新/删除 (CRUD) 操作委托给内部组件 `SessionDAO`，它反映了<a href="https://en.wikipedia.org/wiki/Data_access_object">数据访问对象 (DAO) </a>设计模式。

`SessionDAO` 的强大之处在于您可以实现此接口以与您希望的任何数据存储进行通信。这意味着您的会话数据可以驻留在内存、文件系统、关系数据库或 NoSQL 数据存储中，或者您需要的任何其他位置。 您可以控制持久化行为。

您可以将任何 `SessionDAO` 实现配置为 `SessionManager` 实例上的属性，例如，在 `shiro.ini` 中：

**配置一个 `SessionDAO`**

```ini
[main]
...
sessionDAO = com.foo.my.SessionDAO
securityManager.sessionManager.sessionDAO = $sessionDAO
```

然而，正如您所料，Shiro 已经有一些很好的 `SessionDAO` 实现，您可以根据自己的需要使用开箱即用的类或者继承这些类来实现你自己需要的功能。

上面的 `securityManager.sessionManager.sessionDAO = $sessionDAO` 分配只在使用 Shiro 本地会话管理器时有效。 默认情况下，Web 应用程序不使用本地会话管理器，而是使用不支持 `SessionDAO` 的 `Servlet` 容器的默认会话管理器。 如果您想在基于 Web 的应用程序中启用 `SessionDAO` 以进行自定义会话存储或会话集群，则必须首先配置本地 Web 会话管理器，例如：

```ini
[main]
...
sessionManager = org.apache.shiro.web.session.mgt.DefaultWebSessionManager
securityManager.sessionManager = $sessionManager

# Configure a SessionDAO and then set it:
securityManager.sessionManager.sessionDAO = $sessionDAO
```

Shiro 的默认配置 `SessionManagers` 将 `Session` 存储在内存中。 这不适用于大多数生产应用程序。 大多数生产应用程序都希望配置提供的 EHCache 支持（见下文）或提供自己的 `SessionDAO` 实现。 请注意，Web 应用程序默认使用基于 servlet-container 的 `SessionManager` 并且没有此问题。 这只是使用 Shiro 本地 `SessionManager` 时的问题

<br />

#### EHCache `SessionDAO`

默认情况下不启用 EHCache，但如果您不打算实现自己的 `SessionDAO`，强烈建议您为 Shiro 的 `SessionManagement` 启用 EHCache 支持。EHCache `SessionDAO` 会将会话存储在内存中，并在内存受限时支持溢出到磁盘。 这对于生产应用程序来说是非常可取的，以确保您不会在运行时偶然 “丢失” 会话。

如果您不编写自定义 `SessionDAO`，请务必在 Shiro 配置中启用 EHCache。除了 `Session` 之外，EHCache 还可以有用于缓存身份验证和授权数据。 有关详细信息，请参阅 <a href="https://shiro.apache.org/caching.html">缓存</a> 文档。

如果您快速需要独立于容器的会话集群，EHCache 也是一个不错的选择。您可以在 EHCache 上加上 <a href="http://www.terracotta.org/">TerraCotta</a>，并拥有独立于容器的集群会话缓存。 再也不用担心 Tomcat、JBoss、Jetty、WebSphere 或 WebLogic 特定的会话集群了！

为会话启用 EHCache 非常简单。首先，确保您的类路径中有 `shiro-ehcache-<version>.jar` 文件（请参阅 <a href="https://shiro.apache.org/download.html">下载</a> 页面或使用 Maven 或 Ant+Ivy）。

如果已经将 `shiro-ehcache` 加入的类路径中了，下面的 `shiro.ini` 示例将向您展示如何使用 EHCache 来满足 Shiro 的所有缓存需求（不仅仅是 `Session` 支持）：

```ini
[main]

sessionDAO = org.apache.shiro.session.mgt.eis.EnterpriseCacheSessionDAO
securityManager.sessionManager.sessionDAO = $sessionDAO

cacheManager = org.apache.shiro.cache.ehcache.EhCacheManager
securityManager.cacheManager = $cacheManager
```

最后一行 `securityManager.cacheManager = $cacheManager` 为 Shiro 的所有需求配置了一个 `CacheManager`。此 `CacheManager` 实例将自动向下传播到 `SessionDAO`（根据 `EnterpriseCacheSessionDAO `实现 `CacheManagerAware` 接口的性质）。

然后，当 `SessionManager` 要求 `EnterpriseCacheSessionDAO` 持久化 `Session` 时，它将使用 EHCache 支持的 Cache 实现来存储 `Session` 数据。

不要忘记分配 `SessionDAO` 是使用 Shiro 本地 `SessionManager`默认情况下，Web 应用程序默认使用基于容器的 `Servlet` 而不支持 `SessionDAO`。如果您想在 Web 应用程序中使用基于 EHCache 的会话存储，请按照上述说明配置本地 Web `SessionManager`。

<br />

#### EHCache 会话存储配置

默认情况下，`EhCacheManager` 使用 Shiro 特定的 `ehcache.xml` 文件，该文件设置 `Session` 缓存部分的配置和其它一些必要的设置，以确保正确存储和检索 `Session`。

但是，如果您希望更改缓存设置，或配置自己的 `ehcache.xml` 或 EHCache `net.sf.ehcache.CacheManager` 实例，则需要配置缓存部分的配置以确保正确处理会话。

如果您查看默认的 `ehcache.xml` 文件，您将看到以下 `shiro-activeSessionCache` 缓存配置：

```xml
<cache name="shiro-activeSessionCache"
       maxElementsInMemory="10000"
       overflowToDisk="true"
       eternal="true"
       timeToLiveSeconds="0"
       timeToIdleSeconds="0"
       diskPersistent="true"
       diskExpiryThreadIntervalSeconds="600"/>
```

如果您希望使用自己的 `ehcache.xml` 文件，请确保您已经为 Shiro 的需要定义了类似的缓存条目。 您很可能会更改 `maxElementsInMemory` 属性值以满足您的需要。然而，在您自己的配置中至少存在（并且不会更改）以下两个属性是非常重要的：

- `overflowToDisk="true"` - 这确保如果您用完进程内存，会话不会丢失并且可以序列化到磁盘
- eternal="true" - 确保缓存条目（会话实例）永远不会过期或被缓存自动清除。 这是必要的，因为 Shiro 会根据计划的流程进行自己的验证（请参阅下面的“会话验证和计划”）。 如果我们关闭它，缓存可能会在 Shiro 不知情的情况下删除 `Session`，这可能会导致问题。

<br />

#### EHCache 会话缓存名

默认情况下，`EnterpriseCacheSessionDAO` 向 `CacheManager` 请求一个名为 “shiro-activeSessionCache” 的缓存。 如上所述，此缓存名称/区域预计将在 `ehcache.xml` 中配置。

如果您想使用其他名称而不是此默认名称，您可以在 `EnterpriseCacheSessionDAO` 上配置该名称，例如：

```ini
...
sessionDAO = org.apache.shiro.session.mgt.eis.EnterpriseCacheSessionDAO
sessionDAO.activeSessionsCacheName = myname
...
```

只需确保 `ehcache.xml` 中的相应条目与该名称匹配，并且您已如上所述配置了 `overflowToDisk="true"` 和 `eternal="true"`。

<br />

### 配置会话 ID

Shiro 的 `SessionDAO` 实现使用内部 `SessionIdGenerator` 组件在每次创建新会话时生成新的 `Session ID`。 生成 ID，分配给新创建的 `Session` 实例，然后通过 `SessionDAO` 保存 `Session`。

默认的 `SessionIdGenerator` 是 `JavaUuidSessionIdGenerator`，它根据 Java `UUID` 生成 `String` ID。 此实现适用于所有生产环境。

如果这不能满足您的需求，您可以实现 `SessionIdGenerator` 接口并在 Shiro 的 `SessionDAO` 实例上配置实现。 例如，在 `shiro.ini` 中：

```ini
[main]
...
sessionIdGenerator = com.my.session.SessionIdGenerator
securityManager.sessionManager.sessionDAO.sessionIdGenerator = $sessionIdGenerator
```

<br />

## 会话验证和调度

必须验证会话，以便可以从会话数据存储中删除任何无效（过期或停止）的会话。 这确保了数据存储不会随着时间的推移而被永远不会再次使用的会话填满。

出于性能考虑，仅验证会话以查看它们在访问时是否已停止或过期（即 `subject.getSession()`）。这意味着如果没有额外的定期验证，那些 *孤儿会话* 将会填满会话数据存储。

说明 *孤儿会话* 的一个常见示例是 Web 浏览器场景：假设用户登录到 Web 应用程序并创建会话以保留数据（身份验证状态、购物车等）。如果用户在应用程序不知道的情况下没有注销并关闭浏览器，那么他们的会话本质上只是在会话数据存储中 “闲置”（孤立）。`SessionManager` 无法检测到用户已经没有在使用浏览器了，并且会话不再被访问（它是孤立的）。

*孤儿会话* ，如果不定期清除它们，将填满会话数据存储（这会很糟糕）。 因此，为了防止孤儿堆积，`SessionManager` 实现支持 `SessionValidationScheduler` 的概念。`SessionValidationScheduler` 负责定期验证会话，以确保在必要时清理它们。

<br />

### 默认的 `SessionValidationScheduler`

在所有环境中使用的 `SessionValidationScheduler` 默认是 `ExecutorServiceSessionValidationScheduler`，它使用 JDK `ScheduledExecutorService` 来控制验证发生的频率。

默认情况下，该默认实现将每小时执行一次验证。 您可以通过指定 `ExecutorServiceSessionValidationScheduler` 的新实例并指定不同的时间间隔（以毫秒为单位）来更改验证发生的速率：

```ini
[main]
...
sessionValidationScheduler = org.apache.shiro.session.mgt.ExecutorServiceSessionValidationScheduler
# Default is 3,600,000 millis = 1 hour:
sessionValidationScheduler.interval = 3600000

securityManager.sessionManager.sessionValidationScheduler = $sessionValidationScheduler
```

<br />

### 配置 `SessionValidationScheduler `

如果您希望提供自定义 `SessionValidationScheduler` 实现，可以将其指定为默认 `SessionManager` 实例的属性。 在 `shiro.ini` 中：

```ini
[main]
...
sessionValidationScheduler = com.foo.my.SessionValidationScheduler
securityManager.sessionManager.sessionValidationScheduler = $sessionValidationScheduler
```

<br />

### 禁用会话验证

在某些情况下，您可能希望完全禁用会话验证，因为您已经设置了一个不受 Shiro 控制的进程来为您执行验证。 例如，您可能正在使用企业缓存并依赖缓存的生存时间设置来自动清除旧会话。 或者，您可能已经设置了一个 cron 作业来自动清除自定义数据存储。 在这些情况下，您可以关闭会话验证计划：

在 `shiro.ini` 配置文件中：

```ini
[main]
...
securityManager.sessionManager.sessionValidationSchedulerEnabled = false
```

当从会话数据存储中检索会话时，会话仍将被验证，但这将禁用 Shiro 的定期验证。

如果您关闭 Shiro 的会话验证调度程序，您必须通过其他一些机制（cron 作业等）执行定期会话验证。 这是保证 *孤儿会话*  不会填满数据存储的唯一方法。

<br />

### 删除无效的会话

如上所述，定期会话验证的目的主要是删除任何无效（过期或停止）的会话，以确保它们不会填满会话数据存储。

默认情况下，每当 Shiro 检测到无效会话时，它都会尝试通过 `SessionDAO.delete(session)` 方法将其从底层会话数据存储中删除。 对于大多数应用程序来说，这是一种很好的做法，可确保会话数据存储空间不会耗尽。

但是，某些应用程序可能不希望 Shiro 自动删除会话。 例如，如果应用程序提供了支持可查询数据存储的 `SessionDAO`，则应用程序团队可能希望旧的或无效的会话在一段时间内可用。 这将允许团队对数据存储运行查询，以查看例如用户在上周创建了多少会话，或用户会话的平均持续时间，或类似的报告类型查询。

在这些情况下，您可以完全关闭无效会话删除。 例如，在 `shiro.in`i 中：

```ini
[main]
...
securityManager.sessionManager.deleteInvalidSessions = false
```

不过要小心！ 如果您关闭它，您有责任确保您的会话数据存储不会耗尽其空间。 您必须自己从数据存储中删除无效会话！

另请注意，即使您阻止 Shiro 删除无效会话，您仍然应该以某种方式启用会话验证 - 通过 Shiro 现有的验证机制或通过您自己提供的自定义机制（请参阅上面的 “禁用会话验证” 部分了解更多信息）。 验证机制将更新您的会话记录以反映无效状态（例如，何时失效、上次访问时间等），即使您将在其他时间自己手动删除它们。

**Note：**

如果您将 Shiro 配置为不删除无效会话，则您有责任确保您的会话数据存储不会耗尽其空间。 您必须自己从数据存储中删除无效会话！

另请注意，禁用会话删除与禁用会话验证调度不同。 您几乎应该始终使用会话验证调度机制——Shiro 直接支持或您自己支持的一种。

<br />

## 会话集群

关于 Apache Shiro 会话功能的一个非常令人兴奋的事情是，您可以在本地对主题会话进行集群，而无需再担心如何根据您的容器环境对会话进行集群。 也就是说，如果您使用 Shiro 的本机会话并配置会话集群，您可以在开发中部署到 Jetty 或 Tomcat，在生产中部署到 JBoss 或 Geronimo，或任何其他环境 - 同时永远不必担心容器/环境特定 集群设置或配置。 在 Shiro 中配置一次会话集群，无论您的部署环境如何，它都可以工作。

那么它是怎样工作的？

由于 Shiro 基于 POJO 的 N 层架构，启用 `Session` 集群就像在 `Session` 持久性级别启用集群机制一样简单。也就是说，如果你配置了支持集群的 `SessionDAO`，`DAO` 可以与集群机制交互，Shiro 的 `SessionManager` 永远不需要知道集群问题。

<br />

### 分布式缓存

分布式缓存，例如 <a href="http://www.ehcache.org/documentation/2.7/configuration/distributed-cache-configuration.html">Ehcache+TerraCotta</a>、<a href="http://www.gigaspaces.com/">GigaSpaces</a>、<a href="http://www.oracle.com/technetwork/middleware/coherence/overview/index.html">Oracle Coherence</a> 和 <a href="http://memcached.org/">Memcached</a>（以及许多其他的工具）已经解决了分布式数据在持久性级别的问题。 因此，在 Shiro 中启用 `Session` 集群就像配置 Shiro 使用分布式缓存一样简单。

这使您可以灵活地选择适合您的环境的确切集群机制。

请注意，当启用分布式/企业缓存作为会话集群数据存储时，以下两种情况之一必须为真：

- 分布式缓存有足够的集群范围内存来保留 所有活动/当前会话
- 如果分布式缓存没有足够的集群范围内存来保留所有活动会话，则它必须支持磁盘溢出，以便不会丢失会话。

缓存无法支持这两种情况中的任何一种都可能会导致会话丢失，这可能会让最终用户感到沮丧。

<br />

### `EnterpriseCacheSessionDAO`

正如您所料，Shiro 已经提供了一个 `SessionDAO` 实现，它将数据持久化到企业/分布式缓存中。`EnterpriseCacheSessionDAO` 期望在其上配置 Shiro Cache 或 `CacheManager`，以便它可以利用缓存机制。

例如，在 `shiro.ini` 配置文件中：

```ini
#This implementation would use your preferred distributed caching product's APIs:
activeSessionsCache = my.org.apache.shiro.cache.CacheImplementation

sessionDAO = org.apache.shiro.session.mgt.eis.EnterpriseCacheSessionDAO
sessionDAO.activeSessionsCache = $activeSessionsCache

securityManager.sessionManager.sessionDAO = $sessionDAO
```

尽管您可以如上所示将 Cache 实例直接注入 `SessionDAO`，但通常更常见的是配置通用 `CacheManager` 以用于 Shiro 的所有缓存需求（会话以及身份验证和授权数据）。在这种情况下，您无需直接配置 Cache 实例，而是告诉 `EnterpriseCacheSessionDAO `： 在 `CacheManager` 中的缓存名称应该被用于存储活跃的会话。

例如：

```ini
# This implementation would use your caching product's APIs:
cacheManager = my.org.apache.shiro.cache.CacheManagerImplementation

# Now configure the EnterpriseCacheSessionDAO and tell it what
# cache in the CacheManager should be used to store active sessions:
sessionDAO = org.apache.shiro.session.mgt.eis.EnterpriseCacheSessionDAO
# This is the default value.  Change it if your CacheManager configured a different name:
sessionDAO.activeSessionsCacheName = shiro-activeSessionsCache
# Now have the native SessionManager use that DAO:
securityManager.sessionManager.sessionDAO = $sessionDAO

# Configure the above CacheManager on Shiro's SecurityManager
# to use it for all of Shiro's caching needs:
securityManager.cacheManager = $cacheManager
```

但是上面的配置有点奇怪。 你注意到了吗？

这个配置的有趣之处在于，我们实际上并没有在配置中告诉 `sessionDAO` 实例使用 Cache 或 `CacheManager`！ 那么 `sessionDAO` 是如何使用分布式缓存的呢？

Shiro在初始化 `SecurityManager` 时，会检查 `SessionDAO` 是否实现了 `CacheManagerAware` 接口。 如果是这样，它将自动提供任何可用的全局配置的 `CacheManager`。

因此，当 Shiro 读取`securityManager.cacheManager = $cacheManager` 配置行时，它会发现 `EnterpriseCacheSessionDAO` 实现了 `CacheManagerAware` 接口，并以您配置的 `CacheManager` 作为方法参数调用 `setCacheManager` 方法。

然后在运行时，当 `EnterpriseCacheSessionDAO` 需要 `activeSessionsCache` 时，它会要求 `CacheManager` 实例返回它，使用 `activeSessionsCacheName` 作为查找键来获取 Cache 实例。 该 Cache 实例（由您的分布式/企业缓存产品的 API 支持）将用于存储和检索所有 `SessionDAO` CRUD 操作的会话。

<br />

### Ehcache + Terracotta

在使用 Shiro 时，一种有效的分布式缓存解决方案是 Ehcache + Terracotta 配对。 有关如何使用 Ehcache 启用分布式缓存的完整详细信息，请参阅 <a href="http://www.ehcache.org/documentation/get-started/about-distributed-cache">Ehcache-hosted Distributed Caching With Terracotta</a> 文档。

一旦你让 Terracotta 集群与 Ehcache 一起工作，Shiro 特定的部分就非常简单了。 阅读并遵循 Ehcache `SessionDAO` 文档，但我们需要进行一些更改。

之前引用的 Ehcache 会话缓存配置将不起作用 - 需要特定于 Terracotta 的配置。 这是一个经过测试可以正常工作的示例配置。 将其内容保存在文件中，并将其保存在 `ehcache.xml` 文件中：

```xml
<ehcache>
    <terracottaConfig url="localhost:9510"/>
    <diskStore path="java.io.tmpdir/shiro-ehcache"/>
    <defaultCache
            maxElementsInMemory="10000"
            eternal="false"
            timeToIdleSeconds="120"
            timeToLiveSeconds="120"
            overflowToDisk="false"
            diskPersistent="false"
            diskExpiryThreadIntervalSeconds="120">
        <terracotta/>
    </defaultCache>
    <cache name="shiro-activeSessionCache"
           maxElementsInMemory="10000"
           eternal="true"
           timeToLiveSeconds="0"
           timeToIdleSeconds="0"
           diskPersistent="false"
           overflowToDisk="false"
           diskExpiryThreadIntervalSeconds="600">
        <terracotta/>
    </cache>
    <!-- Add more cache entries as desired, for example,
         Realm authc/authz caching: -->
</ehcache>
```

当然，您需要更改您的 `<terracottaConfig url="localhost:9510"/>` 条目以引用您的 Terracotta 服务器阵列的适当主机/端口。 另请注意，与之前的配置不同，`ehcache-activeSessionCache` 元素不会将 `diskPersistent `或 `overflowToDisk` 属性设置为 `true`。 它们都应该为 `false`，因为集群配置中不支持 `true `值。

保存此 `ehcache.xml` 文件后，我们需要在 Shiro 的配置中引用它。 假设您已经在类路径的根目录中创建了 terracotta 特定的 `ehcache.xml` 文件，这是最终的 Shiro 配置，它启用了 Terracotta+Ehcache 集群来满足 Shiro 的所有需求（包括会话）：

```ini
sessionDAO = org.apache.shiro.session.mgt.eis.EnterpriseCacheSessionDAO
# This name matches a cache name in ehcache.xml:
sessionDAO.activeSessionsCacheName = shiro-activeSessionsCache
securityManager.sessionManager.sessionDAO = $sessionDAO

# Configure The EhCacheManager:
cacheManager = org.apache.shiro.cache.ehcache.EhCacheManager
cacheManager.cacheManagerConfigFile = classpath:ehcache.xml

# Configure the above CacheManager on Shiro's SecurityManager
# to use it for all of Shiro's caching needs:
securityManager.cacheManager = $cacheManager
```

请记住, 通过最后在 `securityManager` 上配置 `cacheManager`，我们确保 `CacheManager` 可以传播到所有之前配置的 `CacheManagerAware` 组件（例如 `EnterpriseCachingSessionDAO`）。

<br />

### Zookeeper

有的用户也使用 <a href="http://zookeeper.apache.org/">Apache Zookeeper</a> 来管理/协调分布式会话。如果您有任何关于这将如何工作的文档/评论，请将它们发布到 Shiro <a href="https://shiro.apache.org/mailing-lists.html">邮件列表</a>

<br />

## 会话和 Subject 状态

### 有状态的应用程序（允许的会话）

默认情况下，Shiro 的 `SecurityManager` 实现将使用 `Subject` 的 `Session` 作为策略来存储 `Subject` 的身份 (`PrincipalCollection`) 和身份验证状态 (`subject.isAuthenticated()`) 以供继续参考。 这通常发生在主题登录后或通过 RememberMe 服务发现主题身份时。

这种默认方法有几个好处：

- 任何服务请求、调用或消息的应用程序都可以将会话 ID 与请求/调用/消息负载相关联，这就是 Shiro 将用户与入站请求相关联所必需的。 例如，如果使用 `Subject.Builder`，这就是获取相关主题所需的全部内容：

    ```java
    Serializable sessionId = //get from the inbound request or remote method invocation payload Subject requestSubject = new Subject.Builder().sessionId(sessionId).buildSubject();
    ```

    这对于大多数 Web 应用程序以及任何编写远程或消息传递框架的人来说都非常方便。 （这实际上是 Shiro 的 Web 支持在其自己的框架代码中将 `Subjects` 与 `ServletRequests` 关联起来的方式）。

- 在初始请求中找到的任何 “RememberMe” 身份都可以在首次访问时持久保存到会话中。 这确保了 `Subject ` 的记忆身份可以跨请求保存，而无需在每次请求时对其进行反序列化和解密。 例如，在 Web 应用程序中，如果身份在会话中已知，则无需在每个请求上读取加密的 RememberMe cookie。 这可以是一个很好的性能增强。

虽然上述默认策略对于大多数应用程序来说都很好（并且通常是可取的），但在尽可能尝试无状态的应用程序中这并不可取。 许多无状态架构要求请求之间不能存在持久状态，在这种情况下，不允许使用会话（会话本质上代表持久状态）。

但是这个要求是以方便为代价的—不能跨请求保留 `Subject` 状态。 这意味着具有此要求的应用程序必须确保可以针对每个请求以其他方式表示 `Subject` 状态。

这几乎总是通过验证应用程序处理的每个请求/调用/消息来实现。 例如，大多数无状态 Web 应用程序通常通过强制执行 HTTP Basic 身份验证来支持这一点，允许浏览器代表最终用户对每个请求进行身份验证。 远程处理或消息传递框架必须确保主题主体和凭据附加到每个调用或消息负载，通常由框架代码执行。

<br />

### 禁用 Subject 状态会话存储

从 Shiro 1.2 及更高版本开始，希望禁用 Shiro 将 Subject 状态持久化到会话的内部实现策略的应用程序可以通过执行以下操作在所有 Subject 中完全禁用此功能：

```ini
[main]
...
securityManager.subjectDAO.sessionStorageEvaluator.sessionStorageEnabled = false
...
```

这将阻止 Shiro 使用 `Subject` 的会话来跨所有 `Subject` 的请求/调用/消息存储该 `Subject` 的状态。 只要确保您对每个请求进行身份验证，这样 Shiro 就会知道任何给定请求/调用/消息的 `Subject`。

这将禁止 Shiro 自己的实现使用 `Sessions` 作为存储策略。 它不会完全禁用会话。 如果您自己的任何代码显式调用 `subject.getSession()` 或 `subject.getSession(true)`，仍将创建会话。

<br />

## 混合方式

上面的 `shiro.ini` 配置行 (`securityManager.subjectDAO.sessionStorageEvaluator.sessionStorageEnabled = false`) 将禁止 Shiro 使用 `Session` 作为所有 `Subjects` 的实现策略。

但是，如果您想要一种混合方法怎么办？ 如果某些 `Subject` 应该有会话而其他 `Subject` 不应该怎么办？ 这种混合方法对许多应用都是有益的。例如：

- 也许人类主体（例如网络浏览器用户）应该能够使用会话来获得上述好处。
- 也许非人类主体（例如 API 客户端或第 3 方应用程序）不应创建会话，因为它们与软件的交互可能是间歇性的和/或不稳定的。
- 也许某种类型的所有 `Subject` 或从某个位置访问系统的 `Subject` 都应该在会话中保持状态，但所有其他 `Subject` 都不应该保持。

如果您需要这种混合方法，您可以实现一个 `SessionStorageEvaluator`。

<br />

### `SessionStorageEvaluator`

如果您想准确控制哪些 `Subjects` 的状态可能会保留在其 `Session` 中，您可以实现 `org.apache.shiro.mgt.SessionStorageEvaluator` 接口并准确告诉 Shiro 哪些 `Subjects` 应该支持会话存储。

这个接口只有一个方法：

```java
public interface SessionStorageEvaluator {

    public boolean isSessionStorageEnabled(Subject subject);

}
```

有关更详细的 API 说明，请参阅 <a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/mgt/SessionStorageEvaluator.html">SessionStorageEvaluator JavaDoc</a>。

您可以实现此接口并检查 `Subject` 以获取做出此决定可能需要的任何信息。

<br />

#### `Subject` 检查

在实现 `isSessionStorageEnabled(subject)` 方法时，您始终可以查看 `Subject` 并访问做出决定所需的任何内容。 当然，所有预期的 `Subject` 方法都可以使用（`getPrincipals()` 等），但特定于环境的 `Subject` 实例也很有价值。

例如，在 Web 应用程序中，如果必须根据当前 `ServletRequest` 中的数据做出该决定，您可以获得请求或响应，因为运行时 `Subject` 实例实际上是一个 `WebSubject` 实例：

```java
...
    public boolean isSessionStorageEnabled(Subject subject) {
        boolean enabled = false;
        if (WebUtils.isWeb(Subject)) {
            HttpServletRequest request = WebUtils.getHttpRequest(subject);
            //set 'enabled' based on the current request.
        } else {
            //not a web request - maybe a RMI or daemon invocation?
            //set 'enabled' another way...
        }

        return enabled;
    }
```

注： 框架开发人员应牢记这种类型的访问，并确保任何请求/调用/消息上下文对象都可通过特定于环境的 `Subject` 实现获得。 如果您需要一些帮助来为您的框架/环境设置它，请联系 Shiro 用户邮件列表。

<br />

#### 配置

在你实现了 `SessionStorageEvaluator` 接口之后，你可以在 `shiro.ini` 中配置它：

```ini
[main]
...
sessionStorageEvaluator = com.mycompany.shiro.subject.mgt.MySessionStorageEvaluator
securityManager.subjectDAO.sessionStorageEvaluator = $sessionStorageEvaluator

...
```

<br />

## Web 应用

Web 应用程序通常希望在每个请求的基础上简单地启用或禁用会话创建，而不管哪个 `Subject` 正在执行请求。 这通常用于支持 `REST` 和 `Messaging/RMI` 架构，效果很好。 例如，也许普通的最终用户（使用浏览器的人）可以创建和使用会话，但远程 API 客户端使用 `REST` 或 SOAP，根本不应该有会话（因为它们对每个请求进行身份验证，这在 REST/SOAP 架构）。

为了支持这种混合/每个请求的功能，一个 `noSessionCreation` 的过滤器已添加到 Shiro 为 Web 应用程序启用的默认过滤器“池”中。 此过滤器将防止在请求期间创建新会话，以保证无状态体验。 在 `shiro.ini [urls]` 部分中，您通常在所有其他过滤器之前定义此过滤器，以确保永远不会使用会话。

例如：

```ini
[urls]
...
/rest/** = noSessionCreation, authcBasic, ...
```

此过滤器允许对任何现有会话使用会话，但不允许在过滤请求期间创建新会话。 也就是说，对一个请求或 `Subject` 的以下四个方法调用中的任何一个都将自动触发 `DisabledSessionException`：

- `httpServletRequest.getSession()`
- `httpServletRequest.getSession(true)`
- `subject.getSession()`
- `subject.getSession(true)`

如果 `Subject` 在访问 noSessionCreation-protected-URL 之前已经有一个会话，那么上述 4 个调用仍将按预期工作。

最后，在所有情况下都将始终允许以下调用：

- `httpServletRequest.getSession(false)`
- `subject.getSession(false)`