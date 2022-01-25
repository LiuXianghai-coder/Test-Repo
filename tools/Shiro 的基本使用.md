# Shiro 的基本使用

## 简介

Apache Shiro 是一个强大的、灵活的开源安全框架，可以干净地处理验证、授权、企业会话管理和加密等功能

### 相关特性

Apache Shiro 具有的主要特性如下图所示：

<img src="https://s2.loli.net/2022/01/23/JfzqwkyHiYWBC52.png" alt="image.png" style="zoom:120%;" />

主要关注的地方在于 `Primary Concerns` 这一部分，具体介绍如下：

- `Authentication`（验证）：有时也被称为 “登录”
- `Authorization`（授权）：访问控制，例如：谁能够去做什么
- `Session Management`（会话管理）：管理用户指定的会话
- `Cryptography`（加密）：使用加密算法保持数据安全，同时仍然易于使用

<br />

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

### 具体组件

具体相关组件如下图所示：

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

<br />

## 基本使用

对于一般的项目，首先需要将 Shiro 的依赖项加入到你的项目得类路径下，如果是一般的 `Maven` 项目，需要添加类似如下的依赖项：

```xml
<dependency>
    <groupId>org.apache.shiro</groupId>
    <artifactId>shiro-core</artifactId>
    <version>1.8.0</version> <!-- 具体选择对应的版本 -->
</dependency>
```

按照 Shiro 官方给出的处理流程，首先会将 `Subject` 的请求信息发送给 `SecurityManager`，由 `SecurityManager` 进行相关的处理。在 `SecurityManager` 内部，根据不同的功能通过不同的模块进行进一步的处理。

`SecurityManager` 在 Shiro 中的默认实现是 `org.apache.shiro.mgt.DefaultSecurityManager`，首先，通过 `SecurityUtils` 的静态方法设置 `SecurityManager`：

```java
// 设置当前 Shiro 上下文中的 SecurityManager，默认为 DefaultWebSecurityManager
DefaultSecurityManager defaultSecurityManager = new DefaultWebSecurityManager();
SecurityUtils.setSecurityManager(defaultSecurityManager); 
```

之后，所有的操作都将围绕 `SecurityManager` 来进行

<br />

### 用户验证

用户验证分为以下五个步骤，参考上文中的系统组件，具体流程如下图所示：

![image.png](https://s2.loli.net/2022/01/24/4BbcZslzuTtovWg.png)

步骤 1：应用程序代码调用 `Subject.login` 方法，传入构造的 `AuthenticationToken` 实例，表示最终用户的`principal` 和 `credential`。

步骤 2：`Subject `实例，通常是 `DelegatingSubject`（或子类）通过调用 `securityManager.login(token)` 委托给应用程序的 `SecurityManager`，实际身份验证工作从这里开始。

步骤 3：`SecurityManager` 是一个基本的 “保护伞” 组件，它接收 token 并通过调用`authenticator.authenticate(token)` 简单地委托给其内部的 `Authenticator` 实例。 这几乎总是一个 `ModularRealmAuthenticator` 实例，它支持在身份验证期间协调一个或多个 `Realm` 实例。 `ModularRealmAuthenticator` 本质上为 Apache Shiro 提供了 PAM 样式的范例（其中每个领域都是 PAM 术语中的“模块”）

步骤 4：如果为应用程序配置了多个 `Realm`，那么 `ModularRealmAuthenticator` 实例将使用其配置的 `AuthenticationStrategy` 启动多个 `Realm` 进行身份验证尝试。 在调用 `Realm` 进行身份验证之前、期间和之后，将调用 `AuthenticationStrategy` 以允许它对每个 `Realm` 的结果做出响应。 我们将很快介绍 `AuthenticationStrategies`。如果只有一个 `Realm` 被配置了，那么不需要 `AuthenticationStrategy` 来做额外的工作

步骤 5：每个配置的 `Realm` 都会被访问，查看是否支持处理提交的 `AuthenticationToken`。 如果该 `Realm` 能够处理该 token，那么该 `Realm` 将会调用自身的 `getAuthenticationInfo` 方法，并将提交的 token 作为对应的方法参数。 `getAuthenticationInfo` 方法有效地表示对该特定 `Realm` 的单一身份验证尝试。 

<br />

#### 单个 `Realm`

针对一种特殊的情况，假如现在整个系统中只配置了一个 `Realm` 用于用户的认证，那么这种情况将会十分简单，具体的使用如下所示：

```java
// 注意：本示例使用的测试环境为 Junit 5

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.realm.SimpleAccountRealm;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SpringBootTest
public class ShiroTest {
    static Logger log = LoggerFactory.getLogger(ShiroTest.class);
    
    // Shiro 内置的一个简单的 Realm，通过简单的用户名和密码来进行验证
    SimpleAccountRealm simpleAccountRealm = new SimpleAccountRealm();
    
    static String USER_NAME = "xhliu";
    static String PASSWORD = "123456";
    
    // 在执行正式测试之前添加一个新的账户对象
    @BeforeEach
    public void addUser() {
        simpleAccountRealm.addAccount("xhliu", "123456", "admin", "user");
    }
    
    @Test
    public void simpleShiroTest() {
        // 1. 构建 SecurityManager（核心部分）
        DefaultSecurityManager defaultSecurityManager = new DefaultWebSecurityManager();
        defaultSecurityManager.setRealm(simpleAccountRealm);

        // 2. 主体提交认证请求
        SecurityUtils.setSecurityManager(defaultSecurityManager); // 设置当前 Shiro 上下文中的 SecurityManager

        // 3. 客户端请求对象
        Subject subject = SecurityUtils.getSubject(); // 获取当前 Shiro 上下文环境下的的 客户端请求对象
        UsernamePasswordToken token = new UsernamePasswordToken(USER_NAME, PASSWORD); // 通过解析请求得到的用户名和密码构成访问 Token

        try {
            subject.login(token);
        } catch (UnknownAccountException e) {
            log.info("用户帐号不存在");
            throw e;
        } catch (IncorrectCredentialsException e) {
            log.info("用户帐号或密码错误");
            throw e;
        }

        log.info("authenticated status: {}", subject.isAuthenticated());
        subject.logout(); // 用户退出
        log.info("authenticated status: {}", subject.isAuthenticated());
    }
}
```

执行测试，会得到类似如下的输出结果：

![2022-01-25 21-22-13 的屏幕截图.png](https://s2.loli.net/2022/01/25/MsWLofJduv5XF6A.png)

这是一种比较简单的情况，但是如果此时定义了两个不同的 `Realm`，比如 `SimpleAccountRealm` 和 `JdbcRealm`，但是只有其中一个 `Realm` 是匹配成功的，那么应该怎么办？这种情况下，`SecurityManager` 中的 `AuthenticationStrategy` 就要派上用场了

<br />

#### 多个 `Realm`

`AuthenticationStrategy` 是一个无状态组件，在身份验证期间被查询 4 次（这 4 次交互所需的任何必要状态都将作为方法参数给出）：

1. 在任意的 `Realm` 被调用之前
2. 在调用单个 `Realm` 的 `getAuthenticationInfo` 方法之前
3. 在调用单个 `Realm` 的 `getAuthenticationInfo` 方法之后
4. 在调用了所有 `Realm` 之后

`AuthenticationStrategy` 负责聚合来自每个验证成功的 `Realm` 的结果，并将它们 “捆绑” 成单个 `AuthenticationInfo` 。 这个最终聚合的 `AuthenticationInfo` 实例是 `Authenticator` 实例返回的内容，也是 Shiro 用来表示 `Subject` 的最终身份（又名 Principals）的内容

如果在应用程序中使用多个 `Realm` 从多个数据源获取帐户数据，那么 `AuthenticationStrategy` 负责生成应用程序看到的 `Subject` 身份的 “合并” 视图

`AuthenticationStrategy` 有三个具体的实现，如下表所示：

| `AuthenticationStrategy` 类    | 描述                                                         |
| :----------------------------- | :----------------------------------------------------------- |
| `AtLeastOneSuccessfulStrategy` | 如果一个（或多个）`Realm` 验证成功，则整体被认为是成功的。 如果没有一个验证成功，则认为是失败的。 |
| `FirstSuccessfulStrategy`      | 只会得到从第一个成功认证的 `Realm` 返回的信息。 其他的 `Realm` 将被忽略。 如果没有成功验证，则尝试失败。 |
| `AllSuccessfulStrategy`        | 所有配置的 `Realm` 都必须成功地进行身份验证，整个尝试才能被视为成功。 如果任何一个未成功验证，则整体视为失败。 |

前文提到，`SecurityManager` 默认的 `Authenticato` 是 `ModularRealmAuthenticator`，而 `ModularRealmAuthenticator` 的默认 `AuthenticationStrategy` 实现类是 `AtLeastOneSuccessfulStrategy`

当然，也可以通过在 `shiro.ini` 配置文件中进行修改，也可以通过程序化的方式来完成：

在 `shiro.ini` 配置文件中进行配置：

```ini
# 将 SecurityManager 的默认认证策略设置为 FirstSuccessfulStrategy
authStrategy = org.apache.shiro.authc.pam.FirstSuccessfulStrategy
securityManager.authenticator.authenticationStrategy = $authStrategy
```

此时通过该 `shiro.ini` 文件加载对应的配置，即可完成对应的配置

通过程序化的方式进行配置：

```java
DefaultSecurityManager securityManager = new DefaultWebSecurityManager();
// ModularRealmAuthenticator 是默认的 Authenticator
ModularRealmAuthenticator authenticator = (ModularRealmAuthenticator) securityManager.getAuthenticator();
// 设置存在多个 Realm 时的认证策略
authenticator.setAuthenticationStrategy(new AllSuccessfulStrategy());
securityManager.setAuthenticator(authenticator); 
```

<br />

### 用户授权

类似认证在 `SecurityManager` 中的顺序，授权在 `SecurityManager` 中的执行顺序如下图所示：

![image.png](https://s2.loli.net/2022/01/24/eiYEXyDWAuIq1Vr.png)

步骤 1：应用程序或框架代码调用任何 `Subject` 的 `hasRole*`、`checkRole*`、`isPermitted*` 或 `checkPermission*` 方法变体，传入所需的任何权限或角色表示。

步骤 2：`Subject` 实例，通常是 `DelegatingSubject`（或子类），通过调用 `securityManager` 的几乎相同的相应 `hasRole*`、`checkRole*`、`isPermitted*` 或 `checkPermission*` 方法变体，委托给应用程序的 `SecurityManager`（`securityManager` 实现了 `org.apache.shiro.authz.Authorizer` 接口，它定义了所有特定于 `Subject` 的授权方法）

步骤 3：`SecurityManager` 是一个基本的“保护伞”组件，通过调用授权方各自的 `hasRole*`、`checkRole*`、`isPermitted*` 或 `checkPermission*` 方法来中继/委托到其内部 `org.apache.shiro.authz.Authorizer` 实例。 授权器实例默认是一个 `ModularRealmAuthorizer` 实例，它支持在任何授权操作期间协调一个或多个 `Realm` 实例。

步骤 4：检查每个配置的 `Realm` 以查看它是否实现了相同的 `Authorizer` 接口。 如果是，则调用 `Realm` 各自的 `hasRole*`、`checkRole*`、`isPermitted*` 或 `checkPermission*` 方法。

<br />

Shiro 提供了三种方式来给指定的 `Subject` 进行授权：

- 程序化 — 您可以使用 `if` 和 `else` 块等结构在您的 java 代码中执行授权检查。
- JDK 注解 — 您可以将授权注解附加到您的 Java 方法
- JSP/GSP TagLibs — 您可以根据角色和权限控制 JSP 或 GSP 页面输出（本文不做介绍）

在介绍这几种方式之前，首先来介绍一下 Shiro 对于授权的处理方

<br />

#### 基于角色的授权

如果您只想检查当前 `Subject` 是否有角色，您可以在 `Subject` 实例上调用变体 `hasRole*` 方法。

例如，要查看 `Subject` 是否具有特定（单一）的角色，您可以调用  `subject.hasRole(roleName)` 方法，并做出相应的响应：

```java
Subject currentUser = SecurityUtils.getSubject();

if (currentUser.hasRole("administrator")) {
    //show the admin button 
} else {
    //don't show the button?  Grey it out? 
}
```

这种授权方式看起来比较正常，是将权限授予给角色，使得该用户具有该角色来判断是否具有对应的权限。这么做理论上来讲是合理的，但是在这个例子中，已经将角色和权限耦合到一起去了，因此目前 Shiro 的推荐做法是使用基于权限的授权而不是基于角色的授权。

RBAC： Role-Based Access Controller，现在应该转换为 Resource-Based Access Controller，具体可以看看 [The New RBAC: Resource-Based Access Control](https://stormpath.com/blog/new-rbac-resource-based-access-control)

<br />

#### 基于权限的授权

基于权限的授权是被推荐使用的，Shiro 提供了两种方式来进行权限的校验：

- 基于 `Permission` 的权限检查

    执行权限检查的一种方法是实例化 Shiro 的 `org.apache.shiro.authz.Permission` 接口的实例，并将其传递给接受权限实例的 `*isPermitted` 方法

    例如，考虑如下假设：办公室中有一台打印机，其唯一标识符为 `laserjet4400n`，在允许当前用户按下“打印”按钮之前，软件需要检查是否允许当前用户在该打印机上打印文档。权限检查可以这样表述：

    ```java
    Permission printPermission = new PrinterPermission("laserjet4400n", "print");
    
    Subject currentUser = SecurityUtils.getSubject();
    
    if (currentUser.isPermitted(printPermission)) {
        //show the Print button 
    } else {
        //don't show the button?  Grey it out?
    }
    ```

    这种方式的优点在于表达的权限十分清晰，能够正确表现其意图；缺点在于需要写更多的代码

- 基于字符串内容的权限检查

    上面的例子转换为对应的字符串权限检查如下所示：

    ```java
    Subject currentUser = SecurityUtils.getSubject();
    
    if (currentUser.isPermitted("printer:print:laserjet4400n")) {
        //show the Print button
    } else {
        //don't show the button?  Grey it out? 
    }
    ```

    这种方式在很多场景下能够减少代码量，因此大部分情况下都会采用这种方式来进行权限检查。

    这种方式最终是通过 `org.apache.shiro.authz.permission.WildcardPermission` 来完成每个部分的分离的，因此，这就相当于：

    ```java
    Subject currentUser = SecurityUtils.getSubject();
    
    Permission p = new WildcardPermission("printer:print:laserjet4400n");
    
    if (currentUser.isPermitted(p) {
        //show the Print button
    } else {
        //don't show the button?  Grey it out?
    }
    ```

<br />

#### 程序化的授权

如上面例子，就是一般的通过写代码的方式来完成授权的工作。具体的相关 API 请参考：https://shiro.apache.org/static/1.8.0/apidocs/

<br />

#### 注解式的授权

注解式的授权基于 AOP ，因此，在使用注解的方式来完成授权工作时，需要开启 AOP

注解式的授权主要有以下几种注解：

- `RequiresAuthentication`：`RequiresAuthentication` 注解要求当前 `Subject` 在其当前会话期间已通过身份验证，以便访问或调用注解的类/实例/方法。

    ```java
    @RequiresAuthentication
    public void updateAccount(Account userAccount) {
        // 进入该方法之前需要当前的账户已经通过认证
    }
    ```

- `RequiresGuest` ：`RequiresGuest` 注解要求当前的 `Subject` 是一个“guest”，也就是说，它们没有被认证或从先前的会话中被记住，以便访问或调用注解的类/实例/方法

    ```java
    @RequiresGuest
    public void signUp(User newUser) {
        // 进入该方法之前需要该账户没有被认证或“记住”
    }
    ```

- `RequiresPermissions` ：`RequiresPermissions` 注解要求当前 `Subject` 被授予一个或多个权限，以便执行注解的方法

    ```java
    @RequiresPermissions("account:create")
    public void createAccount(Account account) {
        // 进入该方法体之前要求账户具有创建账户的权限
    }
    ```

- `RequiresRoles` ：`RequiresRoles` 注释要求当前 `Subject` 具有所有指定的角色。 如果他们没有角色，则不会执行该方法并抛出 `AuthorizationException`

    ```java
    @RequiresRoles("administrator")
    public void deleteUser(User user) {
        /// 进入该方法体之前要求该用户的角色是 administrato
    }
    ```

- `RequiresUser`：`RequiresUser*` 注释要求当前 `Subject` 是应用程序用户，以便访问或调用带注释的类/实例/方法。

    应用程序用户” 被定义为具有已知身份的 `Subject`，该身份是由于在当前会话期间通过身份验证而已知的，或者是从先前会话的 “RememberMe” 服务中记住的

    ```java
    @RequiresUser
    public void updateAccount(Account account) {
        //  只有当当前的 Subject 用户是据有身份时才能执行
    }
    ```

<br />

## 整合到 Spring 





<br />

参考：

<sup>[1]</sup> https://shiro.apache.org/documentation.html