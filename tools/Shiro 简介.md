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



<br />

## 验证 (Authentication)

`Authentication` 是身份验证的一个过程，也就是说，证明一个用户确实是他们所说的那个人。为了让用户证明他们的身份，他们需要提供一些认证信息以及你的系统能够理解和信任的某种身份证明

<img src="https://s2.loli.net/2022/01/24/wza6bKR9c1J8UE5.png" alt="image.png" style="zoom:80%;" />

这是通过向 Shiro 提交用户的 `principals`（主体）和 `credentials`（凭证），查看他们是否符合应用程序的预期来完成的

- `Principals`（主体）是 `Subject`（主题）的 “认证属性”，`Principals` 可以是标识一个 `Subject` 的任意东西，如：名字、姓、用户名、社会保险号等等。当然，这些类似姓的东西在用于标识 `Subject` 时不是一个很好的想法，因此，用于身份验证的最好的 `Principal` 对于应用程序来讲应当是唯一的，如用户名或者邮箱地址
- `Credentials`（凭证）通常是只有 `Subject` 才知道的加密值，作为证明它们确实拥有它们声称的身份的支持证据

大部分的 `Principal` 和 `Credential` 示例都是 “用户名—密码” 对。用户名被用来标识身份，密码被用来证明匹配其声明的身份。如果提交的密码和应用程序预期的相匹配，那么应用程序可以很大的把握假设当前的用户确实是它声明的用户，因为除了该用户之外，应该没有其它的用户能够知道相同的密码

<br />

### 验证 Subject

验证主题的过程可以有效地分解为以下三个步骤：

1. 收集 `Subject` 提交的 `principals` 和 `credentials`
2. 提交 `principals` 和 `credentials` 到 `Authentication`
3. 如果这次的提交是成功的，那么允许当前的登录用户访问，否则的话将重新进行认证或者阻塞访问操作

以下代码演示了 Shiro 的 API 如何反映这些步骤：

- 收集 `Subject` 提交的 `principals` 和 `credentials`

    ```java
    // 收集 Subject 提交的 principal，在这里是 username，credentials，在这里是 password
    UsernamePasswordToken token = new UsernamePasswordToken(username, password);
    token.setRememberMe(true);
    ```

    在这个特别地示例中，我们使用了 <a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/authc/UsernamePasswordToken.html">UsernamePasswordToken</a> ，该类支持大部分常见的 ”用户名/密码“ 认证方式。这个类是 `org.apache.shiro.authc.AuthenticationToken` 接口的实现，`AuthenticationToken` 是基本接口，被 Shiro 认证系统用作代表提交的 `principal` 和 `credentials` 

    值得注意的一点是 Shiro 并不关心是如何获取到这个信息(principal 和 credential)的：可能这个数据是从用户提交的表单中获取、或者可能是从而 Http 头信息中获取、或者是从 Swing 或 Flex 的 GUI 密码表单中获取，甚至是从命令行参数中获取。从应用程序最终用户收集信息的过程与 Shiro 的 `AuthenticationToken` 概念完全解耦。

    您可以随心所欲地构造和表示 `AuthenticationToken` 实例 - 它与协议无关。

    这个例子还表明我们已经表明我们希望 Shiro 为身份验证尝试执行“记住我”服务，这样可以确保 Shiro 在之后返回应用程序时记住用户身份。 我们将在后面的章节中介绍“记住我”服务。

- 提交 `principals` 和 `credentials` 到 `Authentication`

    在收集了 `principal` 和 `credentials` 并表示为 `AuthenticationToken` 实例之后，我们需要提交 token

    到 Shiro 以执行实际的认证操作

    ```java
    Subject currentUser = SecurityUtils.getSubject();
    
    currentUser.login(token);
    ```

    在获取到当前的执行操作的 `Subject` 之后，我们生成了一个 `login` 调用，通过我们之前创建的 `AuthenticationToken` 实例对象。

    一次的 `login` 方法的调用有效地代表了一个认证尝试

- 处理成功或者失败

    如果 `login` 方法成功返回了，也就是说我们的认证已经完成了！`Subject` 已经被认证过了。这个应用程序线程可以继续不间断地对 `SecurityUtils.getSubject()` 的所有进一步调用都返回经过身份验证的 `Subject` 实例，对于之后 `Subject` 的任何调用 `isAuthenticated()` 方法都将返回 `true`

    但是如果认证失败会发生什么呢？例如，如果最终用户提供了错误的密码，或者访问系统的次数过多，可能他们的账户被锁定了怎么办？

    Shiro 有一个丰富的运行时 `AuthenticationException ` 层次结构，可以准确指示认证失败的原因。你可以将 `login` 方法的调用通过 `try/catch` 代码块包裹起来，然后在 `catch` 中捕获任意你希望做出响应的异常，如下所示：

    ```java
    try {
        currentUser.login(token);
    } catch ( UnknownAccountException uae ) { ...
    } catch ( IncorrectCredentialsException ice ) { ...
    } catch ( LockedAccountException lae ) { ...
    } catch ( ExcessiveAttemptsException eae ) { ...
    } ... catch your own ...
    } catch ( AuthenticationException ae ) {
        //unexpected error?
    }
    ```

    如果这些异常没有一个是能够满足你现在的需求，那么你可以通过自定义 `AuthenticationExceptions` 的实现来表示特定的失败场景

    <br />

### 记住 vs. 认证

就像在上面的例子中展示的那样，Shiro 除了支持正常地登录过程之外，还支持 “记住我” 的概念。值得指出的是， Shiro 对于 “记住” 的 `Subject` 和实际的已验证的 `Subject` 之间做了非常明确的区分：

- **记住**（Remembered ）：一个记住的 `Subject` 不是一个匿名的 `Subject`，而且它还有一个已知的认证（例如：`subject.getPrincipal()` 不为空）。但是这个认证是在上一个会话期间从以前的身份验证中记住的。如果 `subject.isRemembered()` 返回 `true`，则认为已经记住该 `Subject`
- **认证**（Authenticated ）：一个认证的 `Subject` 是在当前 `Subject` 的当前会话中已经被成功认证（例如：`login()` 方法被调用而没有抛出异常）的 `Subject`，如果 `subject.isAuthenticated()` 返回 `true` 则认为该 `Subject` 已经被认证了

<br />

#### 为什么区分

“认证”一词具有很强的证明内涵。 也就是说，有一个预期的保证，即对象已经证明他们就是他们所说的那个人。

当仅从与应用程序的先前交互中记住用户时，“认证”状态不再存在；“记住” 的身份让系统知道该用户可能是谁，但实际上，无法绝对保证记住的 `Subject` 是否代表预期的用户。一旦 `Subject` 通过身份验证，它们就不再被视为仅被 “记住”，因为它们的身份将在当前会话期间已经得到验证

因此，尽管应用程序的许多部分仍然可以基于 “记住” 的 `principal` 执行用户特定的逻辑，例如自定义视图，但它通常不应该执行高度敏感的操作，直到用户通过执行成功的身份验证尝试合法地验证了他们的身份。

例如，检查 `Subject` 是否可以访问财务信息应该几乎总是依赖于 `isAuthenticated()`，而不是 `isRemembered()`，以保证预期和验证的身份。

<br />

#### 一个说明性地例子

以下是一个相当常见的场景，有助于说明为什么 “记住” 和  “认证” 之间的区别很重要。

假设您使用的是 <a href="https://www.amazon.com/">Amazon.com</a>，你已成功登录并在购物车中添加了几本书。但是你必须跑去开会，但忘记退出，会议结束时，该回家了，你离开办公室。

第二天当你上班时，你意识到你没有完成购买，所以你回到了 amazon.com。这一次，亚马逊 “记住” 了你是谁，用名字打招呼，仍然给你一些个性化的书籍推荐。 对于亚马逊，`subject.isRemembered()` 将返回 true。

但是，如果您尝试访问你的帐户以更新您的信用卡信息来购买图书，会发生什么？虽然亚马逊 “记住” 了您（`isRemembered() == true`），但它不能保证您实际上就是您（例如，可能一位同事正在使用您的计算机）。

因此，在您执行更新信用卡信息等敏感操作之前，亚马逊会强制你登录，以便他们可以保证您的身份。登录后，您的身份已经过验证，对于亚马逊来说，`isAuthenticated()`  现在为 `true`。

对于许多类型的应用程序，这种情况经常发生，因此该功能内置于 Shiro，因此您可以将其用于您自己的应用程序。 现在，是否使用 `isRemembered()` 或 `isAuthenticated()` 来自定义视图和工作流程取决于您，但 Shiro 将保持这个基本状态以备不时之需。

<br />

### Login Out

身份验证的反面是释放所有已知的识别状态。当 `Subject` 完成与应用程序的交互后，你可以调用 `subject.logout()` 以放弃所有标识信息，如下所示：

```java
// 移除当前登录的 Subject 的所以认证信息
currentUser.logout();
```

当你调用 `logout` 时，任何现有会话都将失效，并且任何身份都将被取消关联（例如，在网络应用程序中，RememberMe cookie 也将被删除）。

在 `Subject` 注销后，`Subject` 实例再次被认为是匿名的，并且除了 Web 应用程序之外，如果需要，可以再次调用 `login` 方法进行登录。



<br />

### 认证的执行顺序

到目前为止，我们只看到了如何从应用程序代码中验证 `Subject`，现在我们将介绍发生身份验证尝试时 Shiro 内部发生的情况。

我们从 <a href="#arch-detail">架构章节</a> 中获取了我们之前的架构图，并且只突出了与身份验证相关的组件。 每个数字代表身份验证尝试期间的一个步骤：

![image.png](https://s2.loli.net/2022/01/24/4BbcZslzuTtovWg.png)

步骤 1：应用程序代码调用 `Subject.login` 方法，传入构造的 `AuthenticationToken` 实例，表示最终用户的`principal` 和 `credential`。

步骤 2：`Subject `实例，通常是 `DelegatingSubject`（或子类）通过调用 `securityManager.login(token)` 委托给应用程序的 `SecurityManager`，实际身份验证工作从这里开始。

步骤 3：`SecurityManager` 是一个基本的 “保护伞” 组件，它接收 token 并通过调用`authenticator.authenticate(token)` 简单地委托给其内部的 `Authenticator` 实例。 这几乎总是一个 `ModularRealmAuthenticator` 实例，它支持在身份验证期间协调一个或多个 `Realm` 实例。 `ModularRealmAuthenticator` 本质上为 Apache Shiro 提供了 PAM 样式的范例（其中每个领域都是 PAM 术语中的“模块”）

步骤 4：如果为应用程序配置了多个 `Realm`，那么 `ModularRealmAuthenticator` 实例将使用其配置的 `AuthenticationStrategy` 启动多个 `Realm` 进行身份验证尝试。 在调用 `Realm` 进行身份验证之前、期间和之后，将调用 `AuthenticationStrategy` 以允许它对每个 `Realm` 的结果做出响应。 我们将很快介绍 `AuthenticationStrategies`。如果只有一个 `Realm` 被配置了，那么不需要 `AuthenticationStrategy` 来做额外的工作

步骤 5：每个配置的 `Realm` 都会被访问，查看是否支持处理提交的 `AuthenticationToken`。 如果该 `Realm` 能够处理该 token，那么该 `Realm` 将会调用自身的 `getAuthenticationInfo` 方法，并将提交的 token 作为对应的方法参数。 `getAuthenticationInfo` 方法有效地表示对该特定 `Realm` 的单一身份验证尝试。 我们将很快介绍 `Realm` 身份验证行为

<br />

### 认证器（Authenticator）

如前所述，Shiro `SecurityManager` 的实现默认使用 `ModularRealmAuthenticator` 实例来实现认证。	`ModularRealmAuthenticator` 同样支持只有单个 `Realm` 的应用程序和具有多个 `Realm` 的应用程序。

在只有单个 `Realm` 的应用程序中，`ModularRealmAuthenticator` 将会直接调用这个单个的 `Realm`。如果配置了两个或者更多的 `Realm`，`ModularRealmAuthenticator` 将会使用 `AuthenticationStrategy` 实例来协调这些 `Realm`，我们将在下面介绍这些 `AuthenticationStrategy`

如果你希望使用自定义的 `Authenticator` 来配置 `SecurityManager`，那么你可以在 `shiro.ini` 配置文件中进行如下的配置：

```ini
[main]
...
authenticator = com.foo.bar.CustomAuthenticator

securityManager.authenticator = $authenticator
```

尽管在实践中，`ModularRealmAuthenticator` 可能适合大多数需求。

<br />

### 认证策略（AuthenticationStrategy）

当为应用程序配置两个或多个 `Realm` 时，`ModularRealmAuthenticator` 依赖于内部的`AuthenticationStrategy ` 组件来确定身份验证尝试成功或失败的条件。

例如，如果只有一个 `Realm` 验证 `AuthenticationToken` 成功，但其他的 `Realm` 都验证失败，那么这样的验证是否被视为成功？ 还是所有 `Realm` 都必须成功验证才能使整体被视为成功？或者，如果一个 `Realm` 验证成功，是否需要进一步咨询其他 `Realm`？ `AuthenticationStrategy` 根据应用程序的需要做出适当的决定。

`AuthenticationStrategy` 是一个无状态组件，在身份验证期间被查询 4 次（这 4 次交互所需的任何必要状态都将作为方法参数给出）：

1. 在任意的 `Realm` 被调用之前
2. 在调用单个 `Realm` 的 `getAuthenticationInfo` 方法之前
3. 在调用单个 `Realm` 的 `getAuthenticationInfo` 方法之后
4. 在调用了所有 `Realm` 之后

`AuthenticationStrategy` 还负责聚合来自每个验证成功的 `Realm` 的结果，并将它们 “捆绑” 成单个 `AuthenticationInfo` 表示。 这个最终聚合的 `AuthenticationInfo` 实例是 `Authenticator` 实例返回的内容，也是 Shiro 用来表示 `Subject` 的最终身份（又名 Principals）的内容。

如果您在应用程序中使用多个 `Realm` 从多个数据源获取帐户数据，则 `AuthenticationStrategy` 负责生成应用程序看到的 `Subject` 身份的 “合并” 视图。

`AuthenticationStrategy` 有三个具体的实现，如下表所示：

| `AuthenticationStrategy` class | Description                                                  |
| :----------------------------- | :----------------------------------------------------------- |
| AtLeastOneSuccessfulStrategy   | 如果一个（或多个）`Realm` 验证成功，则整体被认为是成功的。 如果没有一个验证成功，则认为是失败的。 |
| FirstSuccessfulStrategy        | 只会得到从第一个成功认证的 `Realm` 返回的信息。 其他的 `Realm` 将被忽略。 如果没有成功验证，则尝试失败。 |
| AllSuccessfulStrategy          | 所有配置的 `Realm` 都必须成功地进行身份验证，整个尝试才能被视为成功。 如果任何一个未成功验证，则整体视为失败。 |

`ModularRealmAuthenticator`  默认使用 `AtLeastOneSuccessfulStrategy` 作为实现，这适应与大部分场景的需求，然而，你也可以配置你想要的 `AuthenticationStrategy`，如下所示：

```ini
[main]
...
authcStrategy = org.apache.shiro.authc.pam.FirstSuccessfulStrategy

securityManager.authenticator.authenticationStrategy = $authcStrategy
```

如果你想要创建你自己的 `AuthenticationStrategy` 实现，你可以使用 `org.apache.shiro.authc.pam.AbstractAuthenticationStrategy` 作为开始点。`AbstractAuthenticationStrategy` 类自动实现了将每个 `Realm` 的结果合并到单个 `AuthenticationInfo` 实例中的 “捆绑”/聚合 行为。

<br />

### Realm 认证顺序

`ModularRealmAuthenticator` 与 `Realm` 实例交互的迭代顺序是非常重要的。

`ModularRealmAuthenticator` 可以访问在 `SecurityManager` 上配置的 `Realm` 实例。执行身份验证的尝试时，它将遍历该集合，并且对于能够处理提交的 `AuthenticationToken` 的每个 `Realm`，调用这些 `Realm` 的 `getAuthenticationInfo` 方法。

<br />

#### 隐式顺序

当使用 Shiro 的 INI 配置方式来配置 Shiro 时，你应该配置 `Realm` 集合在处理  `AuthenticationToken`  的处理顺序。例如，在 `Shiro.ini` 文件中，`Realms` 将会按照在 `shiro.ini` 中配置的先后顺序进行处理。也就是说，对于下面的 `Shiro.ini` 示例：

```ini
blahRealm = com.company.blah.Realm
...
fooRealm = com.company.foo.Realm
...
barRealm = com.company.another.Realm
```

`SecurityManager` 将会配置 `shiro.ini` 文件中的三个 `Realm`，在认证尝试的过程中，`blahRealm`、``fooRealm`、`barRealm` 将会按照这个顺序进行认证调用处理

这与定义以下行的效果基本相同：

```ini
securityManager.realms = $blahRealm, $fooRealm, $barRealm
```

通过这种方式，你不需要再设置 `SecurityManager` 的 `realms` 的属性，每个定义的 `Realm` 都将自动地被添加到 `relams` 属性中

<br />

#### 显式顺序

如果要显式地定义与 `Realm` 交互的顺序，而不管它们是按照怎样的顺序来定义的，那么你可以将 `securityManager` 的 `realms` 属性作为一个显式集合属性。例如，如果定义了如下的配置，但是你希望 `blahRealm` 是最后一个访问的 `Realm` 而不是第一个：

```ini
blahRealm = com.company.blah.Realm
...
fooRealm = com.company.foo.Realm
...
barRealm = com.company.another.Realm

# 显式地设置执行顺序，使得 blahRealm 是最后一个呗访问的
securityManager.realms = $fooRealm, $barRealm, $blahRealm
```

当你显式地配置了 `securityManager.realms` 属性时，只有该属性引用到的 `Realm` 才会被配置到 `SecurityManager` 中。这意味着，你可以在 `shiro.ini` 文件中配置 5 个 `Realm`，但是只有三个 `Realm` 被配置到 `securityManager.realms` 中，那么实际上最终只会有 3 个 `Realm` 会被配置到 `SecurityManager` 中，这和隐式顺序的方式不同

<br />

## 授权（Authorization）

授权（Authorization） 也被称为访问控制，是管理资源访问的处理。换句话说，控制 “谁” 可以访问应用程序的内容

![image.png](https://s2.loli.net/2022/01/24/fTMO5sHVFzgAde8.png)

授权检查的示例有：用户是否允许查看此网页、编辑此数据、查看此按钮或打印到此打印机？ 这些都是决定用户可以访问什么的决定。

<br />

### 授权组成元素

授权具有我们在 Shiro 中经常引用的三个核心元素：权限、角色和用户。

#### 权限

Apache Shiro 中的权限代表了安全策略中最基本的元素。它们从根本上是关于行为的陈述，并明确表示可以在应用程序中完成的操作。格式良好的许可声明本质上描述了资源以及当主体与这些资源交互时可能执行的操作。

权限声明的一些示例：

- 打开一个文件
- 查看 `/user/list` web 页面
- 打印文档
- 删除 “jsmth” 用户

大多数资源将支持典型的 CRUD（创建、读取、更新、删除）操作，但任何对特定资源类型有意义的操作都是可以的。 基本思想是权限声明至少基于资源和行为。

在查看权限时，要意识到的最重要的事情可能是权限语句没有表示谁可以执行所表示的行为。 它们只是说明可以在应用程序中执行的操作。

权限语句只反映行为（和资源类型之间的关联）。它们不会反映谁能够执行这个行为

定义 “谁”（用户）被允许做 “什么”（权限）是以某种方式为用户分配权限的一种行为。这始终由应用程序的数据模型完成，并且在应用程序之间可能会有很大差异。

例如，权限可以分组在一个角色中，并且该角色可以与一个或多个用户对象相关联。或者某些应用程序可以有一个用户组，并且可以为一个组分配一个角色，通过传递关联意味着该组中的所有用户都被隐式授予角色中的权限。

授予用户权限的方式有很多变化 - 应用程序根据应用程序要求确定如何对其进行建模。

稍后我们将介绍 Shiro 如何确定一个 `Subject` 是否被允许做某事。

<br />

##### 权限粒度

上面的权限示例都指定了对资源类型（文件、客户等）的操作（打开、读取、删除等）。在某些情况下，他们甚至指定了非常细粒度的实例级行为——例如，使用用户名“jsmith”（实例标识符）“删除”（操作）“用户”（资源类型）。在 Shiro 中，您可以准确定义这些语句的粒度。

我们在 Shiro 的 <a href="https://shiro.apache.org/permissions.html">权限文档</a> 中更详细地介绍了权限粒度和权限声明的“级别”。

<br />

#### 角色

角色是一个命名实体，通常代表一组行为或职责。这些行为（或职责）转化为您能或不能用软件应用程序做的事情。角色通常分配给用户帐户，因此通过关联，用户可以 “做” 归属于各种角色的事情

实际上有两种类型的角色，Shiro 支持这两种概念：

1. 隐式角色

    大多数人将角色用作隐式构造：您的应用程序仅基于角色名称便隐式地具有一组行为（即权限）。对于隐式角色，在软件级别上来讲，没有任何内容会说“允许角色 X 执行行为 A、B 和 C”。行为仅由名称暗指

    虽然是更简单和最常见的方法，但隐式角色可能会带来许多软件维护和管理问题。

    例如，如果您只想添加或删除角色，或者稍后重新定义角色的行为，该怎么办？每次需要进行此类更改时，您都必须返回源代码并更改所有角色检查以反映安全模型的更改！更不用说这将产生的运营成本（重新测试、通过 QA、关闭应用程序、使用新角色检查升级软件、重新启动应用程序等）。

    这对于非常简单的应用程序可能没问题（例如，可能有一个 “管理员” 角色和 “其他所有人”）。但是对于更复杂或可配置的应用程序，这可能是整个应用程序生命周期中的一个主要问题，并为您的软件带来大量维护成本

2. 显式角色

    显式角色本质上是实际权限声明的命名集合。在这种形式中，应用程序（和 Shiro）确切地知道拥有或不拥有特定角色意味着什么。因为知道可以执行或不执行的确切行为，所以没有猜测或暗示特定角色可以或不可以做什么。

Shiro 团队提倡使用权限和显式角色，而不是旧的隐式方法。 您将对应用程序的安全体验有更大的控制权。

 **Resource-Based Access Control**

请务必阅读 Les Hazlewood 的文章：[The New RBAC: Resource-Based Access Control](https://stormpath.com/blog/new-rbac-resource-based-access-control)，其中深入介绍了使用权限和显式角色（以及它们对源代码的积极影响）而不是旧的隐式角色方法的好处。

<br />

#### 用户

用户本质上是应用程序的 “谁”。 然而，正如我们之前所介绍的，`Subject` 实际上是 Shiro 的 “用户” 概念。

允许用户（`Subject`）通过与角色或直接权限的关联在您的应用程序中执行某些操作，您的应用程序的数据模型准确地定义了一个 `Subject` 如何被允许做某事或不做某事。

例如，在您的数据模型中，也许您有一个实际的 `User` 类，并且您将权限直接分配给 `User` 实例；或者，也许您只直接将权限分配给角色，然后将角色分配给用户，因此通过关联，用户可传递地 “拥有” 分配给其角色的权限；或者你可以用“组”概念来表示这些东西。 这取决于您 - 使用对您的应用程序有意义的东西。

您的数据模型准确定义了授权的运作方式。 Shiro 依靠 Realm 实现将您的数据模型关联细节转换为 Shiro 可以理解的格式。 稍后我们将介绍 Realms 是如何做到这一点的。

**Note：**

最终，您的 Realm 实现与您的数据源（RDBMS、LDAP 等）进行通信。因此，您的 `Realm` 将告诉 Shiro 是否存在角色或权限。 您可以完全控制授权模型的结构和定义方式



<br />

### 授权给 Subject

在 Shiro 中执行授权可以通过 3 中方式来完成：

- 程序化 — 您可以使用 `if` 和 `else` 块等结构在您的 java 代码中执行授权检查。
- JDK 注解 — 您可以将授权注解附加到您的 Java 方法
- JSP/GSP TagLibs — 您可以根据角色和权限控制 JSP 或 GSP 页面输出

<br />

#### 程序化的授权

执行授权的最简单和最常见的方法可能是以编程方式直接与当前的 `Subject` 实例交互。

**基于角色的授权**

如果您想基于更简单/传统的隐式角色名称来控制访问，您可以执行角色检查：

##### 角色检查

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

您可以调用几个面向角色的 `Subject` 方法，具体取决于您的需要：

| Subject Method                              | Description                                                  |
| :------------------------------------------ | :----------------------------------------------------------- |
| `hasRole(String roleName)`                  | 如果为 `Subject` 分配了指定的角色，则返回 `true`，否则返回 `false` |
| `hasRoles(List<String> roleNames)`          | 返回与方法参数中的索引相对应的 hasRole 结果数组。 如果需要执行许多角色检查（例如，在自定义复杂视图时），可用作性能增强 |
| `hasAllRoles(Collection<String> roleNames)` | 如果为 `Subject` 分配了所有指定的角色，则返回 `true`，否则返回 `false`。 |

