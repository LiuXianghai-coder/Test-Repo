# 验证 (Authentication)

`Authentication` 是身份验证的一个过程，也就是说，证明一个用户确实是他们所说的那个人。为了让用户证明他们的身份，他们需要提供一些认证信息以及你的系统能够理解和信任的某种身份证明

<img src="https://s2.loli.net/2022/01/24/wza6bKR9c1J8UE5.png" alt="image.png" style="zoom:80%;" />

这是通过向 Shiro 提交用户的 `principals`（主体）和 `credentials`（凭证），查看他们是否符合应用程序的预期来完成的

- `Principals`（主体）是 `Subject`（主题）的 “认证属性”，`Principals` 可以是标识一个 `Subject` 的任意东西，如：名字、姓、用户名、社会保险号等等。当然，这些类似姓的东西在用于标识 `Subject` 时不是一个很好的想法，因此，用于身份验证的最好的 `Principal` 对于应用程序来讲应当是唯一的，如用户名或者邮箱地址
- `Credentials`（凭证）通常是只有 `Subject` 才知道的加密值，作为证明它们确实拥有它们声称的身份的支持证据

大部分的 `Principal` 和 `Credential` 示例都是 “用户名—密码” 对。用户名被用来标识身份，密码被用来证明匹配其声明的身份。如果提交的密码和应用程序预期的相匹配，那么应用程序可以很大的把握假设当前的用户确实是它声明的用户，因为除了该用户之外，应该没有其它的用户能够知道相同的密码

<br />

## 验证 Subject

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

## Login Out

身份验证的反面是释放所有已知的识别状态。当 `Subject` 完成与应用程序的交互后，你可以调用 `subject.logout()` 以放弃所有标识信息，如下所示：

```java
// 移除当前登录的 Subject 的所以认证信息
currentUser.logout();
```

当你调用 `logout` 时，任何现有会话都将失效，并且任何身份都将被取消关联（例如，在网络应用程序中，RememberMe cookie 也将被删除）。

在 `Subject` 注销后，`Subject` 实例再次被认为是匿名的，并且除了 Web 应用程序之外，如果需要，可以再次调用 `login` 方法进行登录。



<br />

## 认证的执行顺序

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