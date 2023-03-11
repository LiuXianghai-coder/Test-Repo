# Shiro Realms

`Realm` 是一个组件，可以访问特定于应用程序的安全数据，例如用户、角色和权限。`Realm` 将这种特定于应用程序的数据转换为 Shiro 可以理解的格式，因此无论存在多少数据源或您的数据可能是多么特定于应用程序，Shiro 都可以反过来提供一个易于理解的 `Subject`  API。

`Realm` 通常与数据源（例如关系数据库、LDAP 目录、文件系统或其他类似资源）具有一对一的关联。因此，`Realm` 接口的实现使用特定于数据源的 API 来发现授权数据（角色、权限等），例如 JDBC、文件 IO、Hibernate 或 JPA，或任何其他数据访问 API。

`Realm` 本质上是一个特定于安全的 DAO

由于这些数据源中的大多数通常既存储身份验证数据（例如密码等凭据）也存储授权数据（例如角色或权限），因此每个 Shiro `Realm` 都可以执行身份验证和授权操作。

<br />

## 配置 Realm

如果使用 Shiro 的 `INI` 配置，您可以像在 `[main]` 部分中的任何其他对象一样定义和引用 `Realm`，但它们在 `securityManager` 上通过以下两种方式之一进行配置：显式或隐式。

### 显式指定

基于迄今为止的 `INI` 配置知识，这是一种显式的配置方法。定义一个或多个 `Realm` 后，您将它们设置为 `securityManager` 对象的集合属性。

例如：

```ini
fooRealm = com.company.foo.Realm
barRealm = com.company.another.Realm
bazRealm = com.company.baz.Realm

securityManager.realms = $fooRealm, $barRealm, $bazRealm
```

显式分配是确定性的 - 您可以准确控制使用哪些 `Realm` 以及它们将用于身份验证和授权的顺序。`Realm` 的处理顺序在 `Authentication` 章节的 <a href="https://shiro.apache.org/authentication.html#Authentication-sequence">Authentication Sequence</a> 部分中有详细描述

<br />

### 隐式指定

**如果您更改定义 `Realm` 的顺序，隐式分配可能会导致意外行为。建议您避免使用这种方法并使用具有确定性行为的显式赋值。 未来的 Shiro 版本可能会弃用/删除隐式分配。**

如果由于某种原因您不想显式配置 `securityManager.realms` 属性，您可以允许 Shiro 检测所有配置的领域并将它们直接分配给 `securityManager`。

使用这种方法，`Realm` 将按照定义的顺序分配给 `securityManager` 实例。

例如，如下面的 `shiro.ini` 配置文件：

```ini
blahRealm = com.company.blah.Realm
fooRealm = com.company.foo.Realm
barRealm = com.company.another.Realm

# no securityManager.realms assignment here
```

基本上与下行显式配置的效果相同：

```ini
securityManager.realms = $blahRealm, $fooRealm, $barRealm
```

但是，要意识到，通过隐式分配，`Realm` 定义的顺序直接影响在身份验证和授权尝试期间如何查询它们。 如果您更改它们的定义顺序，您将更改主身份验证器的身份验证序列的功能。

因此，为了确保确定性行为，我们建议使用显式指定而不是隐式指定。

<br />

## Realm 认证

一旦您了解了 Shiro 的 <a href="https://shiro.apache.org/authentication.html#Authentication-sequence">身份验证工作流程</a>，重要的是要准确了解身份验证器在身份验证尝试期间与 Realm 交互时会发生什么。

<br />

### 支持 `AuthenticationTokens`

正如 <a href="https://shiro.apache.org/authentication.html#Authentication-sequence">认证顺序</a> 中提到的，在一个 `Realm` 被访问以执行认证尝试之前，它的 `supports` 方法将被调用。如果返回值为 `true`，那么它的 `getAuthenticationInfo(token)` 方法会被调用。

通常，`Realm` 会检查提交的 token 的类型（接口或类），以查看它是否可以处理它。例如，处理生物特征数据的 `Realm` 可能根本不理解 `UsernamePasswordTokens`，在这种情况下，它会从 `support` 方法返回 `false`。

<br />

### 处理支持的 `AuthenticationTokens`

如果 `Realm` 支持处理提交的 `AuthenticationToken`，则 `Authenticator` 将调用 `Realm` 的 `getAuthenticationInfo(token)` 方法。这有效地表现了 `Realm` 通过后端数据源的身份验证尝试。该方法，按照如下的顺序进行处理：

1. 检查标识 `principal ` 的 token（帐户标识信息）
2. 基于 `principal ` ，在数据源中查找对应的账户数据
3. 确保 token 提供的 `credentials` 与存储在数据存储中的 `credentials` 相匹配
4. 如果 `credentials ` 匹配，则返回一个<a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/authc/AuthenticationInfo.html">`AuthenticationInfo` </a> 实例，该实例以 Shiro 理解的格式封装帐户数据
5. 如果 `credentials` 不匹配，则抛出 <a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/authc/AuthenticationException.html">AuthenticationException</a>

这是所有 `Realm` 的 `getAuthenticationInfo` 实现的最高级别的工作流。在此方法期间，`Realm` 可以自由地做任何他们想做的事情，例如在审计日志中记录尝试、更新数据记录或任何其他对该数据存储的身份验证尝试有意义的事情。

唯一需要的是，如果给定主体的凭据匹配，则返回一个非空 `AuthenticationInfo` 实例，该实例表示来自该数据源的主题帐户信息。

**Note: 节约时间**

直接实现 `Realm` 接口可能很耗时且容易出错。大多数人选择继承 `AuthorizingRealm` 抽象类，而不是从头开始。 该类实现了常见的身份验证和授权工作流程，以节省您的时间和精力。

<br />

### 凭证匹配

在上述 `Realm` 身份验证工作流程中，`Realm` 必须验证 `Subject` 提交的 `Credential`（例如密码）必须与存储在数据存储中的 `Credential` 匹配

将提交的 `Credential` 与存储在 `Realm` 支持数据存储中的 `Credential` 进行匹配是每个 `Realm` 的责任，而不是 “Authenticator” 的责任。每个 `Realm` 都对 `Credential`的格式和存储有深入的了解，并且可以执行详细的 `Credential` 匹配，而 `Authenticator` 是一个通用的工作流组件。

`Credential` 匹配过程在所有应用程序中几乎相同，并且通常仅在比较的数据上有所不同。为确保此过程在必要时可插入和可定制，`AuthenticatingRealm` 及其子类支持 <a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/authc/credential/CredentialsMatcher.html">`CredentialsMatcher`</a> 的概念来执行 `Credential` 比较。

发现帐户数据后，将其和提交的 `AuthenticationToken` 呈现给 `CredentialsMatcher` 以查看提交的内容是否与存储在数据存储中的内容匹配

Shiro 有一些 `CredentialsMatcher` 实现让您开箱即用，例如 <a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/authc/credential/SimpleCredentialsMatcher.html">`SimpleCredentialsMatcher`</a> 和 <a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/authc/credential/HashedCredentialsMatcher.html">`HashedCredentialsMatcher`</a> 实现，但是如果您想为自定义匹配逻辑配置自定义实现，您可以直接这样做：

```java
Realm myRealm = new com.company.shiro.realm.MyRealm();
CredentialsMatcher customMatcher = new com.company.shiro.realm.CustomCredentialsMatcher();
myRealm.setCredentialsMatcher(customMatcher);
```

或者，使用 `shiro.ini` 配置：

```ini
[main]
...
customMatcher = com.company.shiro.realm.CustomCredentialsMatcher
myRealm = com.company.shiro.realm.MyRealm
myRealm.credentialsMatcher = $customMatcher
...
```

<br />

#### 简单的等式检查

Shiro 所有开箱即用的 `Realm` 实现都默认使用 <a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/authc/credential/SimpleCredentialsMatcher.html">`SimpleCredentialsMatcher`</a>。 `SimpleCredentialsMatcher` 对存储的帐户凭据与在 `AuthenticationToken` 中提交的内容执行简单的直接相等性检查。

例如，如果提交了 <a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/authc/UsernamePasswordToken.html">`UsernamePasswordToken`</a>，`SimpleCredentialsMatcher` 会验证提交的密码是否与存储在数据库中的密码完全相同。

`SimpleCredentialsMatcher` 不仅对字符串执行直接相等比较，它也可以处理最常见的字节源，例如字符串、字符数组、字节数组、文件和输入流。 有关更多信息，请参阅其 JavaDoc。

<br />

#### Hash 凭证

与其以原始形式存储 `Credential` 并进行比较，存储最终用户 `Credential`（例如密码）的一种更安全的方法是在将它们存储到数据存储之前先对它们进行单向哈希处理。

这确保了最终用户的 `Credential` 永远不会以原始形式存储，并且没有人可以知道原始值。这是一种比纯文本或原始比较更安全的机制，所有关心安全的应用程序都应该支持这种方法而不是非散列存储。

为了支持这些哈希策略，Shiro 提供了 <a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/authc/credential/HashedCredentialsMatcher.html">`HashedCredentialsMatcher` </a>实现用于在 `Realm` 上配置，而不是前面提到的 `SimpleCredentialsMatcher`。

散列凭证以及加盐和多次散列迭代的好处超出了本 `Realm` 文档的范围，但请务必阅读详细介绍这些原则的  <a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/authc/credential/HashedCredentialsMatcher.html">HashedCredentialsMatcher JavaDoc</a>。

<br />

##### 散列和对应的匹配器

那么如何配置一个支持 Shiro 的应用程序来轻松地做到这一点呢？

Shiro 提供了多个 `HashedCredentialsMatcher` 子类实现。您必须在您的 `Realm` 配置特定的实现，以匹配您用来散列用户凭据的散列算法。

例如，假设您的应用程序使用用户名/密码对进行身份验证。由于上述散列凭据的好处，假设您想在创建用户帐户时使用 <a href="https://en.wikipedia.org/wiki/SHA_hash_functions">SHA-256</a> 算法对用户密码进行单向散列。您将散列用户输入的纯文本密码并保存该值：

```java
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
...

// 生成一个随机数，来为散列值加盐
RandomNumberGenerator rng = new SecureRandomNumberGenerator();
Object salt = rng.nextBytes();
 
// 现在密码通过随机加盐和多次迭代，最终转变为基于 64 位的编码格式
String hashedPasswordBase64 = new Sha256Hash(plainTextPassword, salt, 1024).toBase64();

User user = new User(username, hashedPasswordBase64);

// 将盐值保存在新创建的账户对象中，HashedCredentialsMatcher 在之后处理登录尝试时需要用到这些
user.setPasswordSalt(salt);
userDAO.create(user);
```

由于您使用 `SHA-256` 散列用户密码，因此您需要告诉 Shiro 使用适当的 `HashedCredentialsMatcher` 来匹配您的散列偏好。 在此示例中，我们创建了一个随机盐并执行 1024 次哈希迭代以增强安全性（请参阅 `HashedCredentialsMatcher` JavaDoc 了解原因）

在 `shiro.ini` 中进行配置使得其能够工作：

```ini
[main]
...
credentialsMatcher = org.apache.shiro.authc.credential.Sha256CredentialsMatcher
# base64 encoding, not hex in this example:
credentialsMatcher.storedCredentialsHexEncoded = false
credentialsMatcher.hashIterations = 1024
# This next property is only needed in Shiro 1.0\.  Remove it in 1.1 and later:
credentialsMatcher.hashSalted = true

...
myRealm = com.company.....
myRealm.credentialsMatcher = $credentialsMatcher
...
```

<br />

##### `SaltedAuthenticationInfo`

确保此工作正常进行的最后一件事是您的 `Realm` 实现必须返回一个 `SaltedAuthenticationInfo` 实例而不是普通的 `AuthenticationInfo` 实例。`SaltedAuthenticationInfo` 接口确保您在创建用户帐户时使用的盐（例如，上面的 `user.setPasswordSalt(salt);` 调用）可以被 `HashedCredentialsMatcher` 引用

`HashedCredentialsMatcher` 需要 `salt` 才能对提交的 `AuthenticationToken` 执行相同的哈希操作，以查看 token 是否与您在数据存储中保存的内容匹配。因此，如果您对用户密码使用加盐（并且应该！！！），请确保您的 `Realm` 实现通过返回 `SaltedAuthenticationInfo` 实例来代表它。

<br />

### 禁用身份认证

如果出于某种原因，您不希望 `Realm` 对数据源执行身份验证（可能是因为您只希望 `Realm` 执行授权），您可以通过始终从 `Realm` 的 `supports` 方法返回 `false` 来完全禁用 `Realm` 对身份验证的支持。然后在身份验证尝试期间将永远不会访问您的 `Realm`。

当然，如果要对 `Subjects` 进行身份验证，至少需要一个配置的 `Realm` 能够支持 `AuthenticationTokens`。

<br />

## Realm 授权

`SecurityManager` 将 `Permission` 或 `Role` 检查的任务委托给 `Authorizer`，默认为 `ModularRealmAuthorizer`。

### 基于角色的授权

当在 `Subject` 上调用重载方法 `hasRoles` 或 `checkRoles` 方法时，会按照如下顺序执行：

1. `Subject` 委托给 `SecurityManager` 以识别是否分配了给定的角色
2. `SecurityManager` 然后委托给 `Authorizer`
3. <a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/authz/Authorizer.html">Authorizer</a> 引用所有 `Authorizer Realms`，直到找到分配给 `Subject` 的给定角色
4. `Authorizing Realm`— <a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/authz/AuthorizationInfo.html">AuthorizationInfo</a> 的 `getRoles()` 方法获取分配给 `Subject` 的所有角色
5. 如果在 `AuthorizationInfo.getRoles` 方法调用返回的角色列表中找到给定角色，则授予访问权限

<br />

### 基于权限的授权

当重载方法是 `Permitted()` 或 `checkPermission()` 方法在 `Subject` 上调用时，按照如下顺序进行处理：

1. `Subject` 将授予或拒绝权限的任务委托给 `SecurityManager`
2. `SecurityManager` 委托给 `Authorizer`
3. Authorizer 引用到所有 `Authorizer Realms` 直到它被授予 `Permission`
    如果所有的授权 `Realm` 都没有被授予权限，则 `Subject` 被拒绝
4. 授权 `Realm` 执行以下操作以检查 `Subject` 是否被允许：
    1. 首先，它通过在 <a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/authz/AuthorizationInfo.html">`AuthorizationInfo`</a> 上调用 `getObjectPermissions()` 和 `getStringPermissions` 方法并聚合结果来直接识别分配给 `Subject` 的所有权限。
    2. 如果注册了<a href="https://shiro.apache.org/static/current/apidocs/org/apache/shiro/authz/permission/RolePermissionResolver.html">`RolePermissionResolver`</a>，那么注册的 `RolePermissionResolver` 将通过调用 `RolePermissionResolver.resolvePermissionsInRole()` 方法来获取分配给 `Suject` 的所有角色的权限
    3. 对于来自 **1** 和 **2** 的聚合 `Permissions ` 对象 ，调用 `implies()` 方法来检查这些权限中的是否隐含检查权限。可以查看 <a href="https://shiro.apache.org/permissions.html#Permissions-WildcardPermissions">WildcardPermissions</a>

