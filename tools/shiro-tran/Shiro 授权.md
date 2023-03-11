# 授权（Authorization）

授权（Authorization） 也被称为访问控制，是管理资源访问的处理。换句话说，控制 “谁” 可以访问应用程序的内容

![image.png](https://s2.loli.net/2022/01/24/fTMO5sHVFzgAde8.png)

授权检查的示例有：用户是否允许查看此网页、编辑此数据、查看此按钮或打印到此打印机？ 这些都是决定用户可以访问什么的决定。

<br />

## 授权组成元素

授权具有我们在 Shiro 中经常引用的三个核心元素：权限、角色和用户。

### 权限

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

#### 权限粒度

上面的权限示例都指定了对资源类型（文件、客户等）的操作（打开、读取、删除等）。在某些情况下，他们甚至指定了非常细粒度的实例级行为——例如，使用用户名“jsmith”（实例标识符）“删除”（操作）“用户”（资源类型）。在 Shiro 中，您可以准确定义这些语句的粒度。

我们在 Shiro 的 <a href="https://shiro.apache.org/permissions.html">权限文档</a> 中更详细地介绍了权限粒度和权限声明的“级别”。

<br />

### 角色

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

### 用户

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

## 程序化的授权

执行授权的最简单和最常见的方法可能是以编程方式直接与当前的 `Subject` 实例交互。

**基于角色的授权**

如果您想基于更简单/传统的隐式角色名称来控制访问，您可以执行角色检查：

### 角色检查

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

<br />

### 角色断言

除了检查布尔值以查看 `Subject` 是否具有角色之外，您可以简单地在执行逻辑之前断言它们具有预期的角色。如果 `Subject` 没有预期的角色，将抛出 `AuthorizationException`。 如果它们确实具有预期的作用，则断言将安静地执行，逻辑将按预期继续。

例如：

```java
Subject currentUser = SecurityUtils.getSubject();

// 断言当前的用户确实居于 BankTeller 的角色
currentUser.checkRole("bankTeller");
openBankAccount();
```

这种方法相对于 `hasRole*` 方法的一个好处是代码可以更简洁一些，因为如果当前 `Subject` 不满足预期条件（如果您不想这样做），您不必构造自己的 `AuthorizationExceptions`。

您可以调用的面向角色的 `Subject` 断言方法很少，具体取决于您的需要：

| Subject Method                             | Description                                                  |
| :----------------------------------------- | :----------------------------------------------------------- |
| `checkRole(String roleName)`               | 如果为 `Subject` 分配了指定的角色，则安静地返回，否则抛出 `AuthorizationException` |
| `checkRoles(Collection<String> roleNames)` | 如果为 `Subject` 分配了所有指定的角色，则安静地返回，否则抛出 `AuthorizationException` |
| `checkRoles(String... roleNames)`          | 与上述 checkRoles 方法的效果相同，但允许使用可变长参数       |

<br />

### 基于权限的授权

正如我们在角色概述中所述，执行访问控制的更好方法通常是通过基于权限的授权。

基于权限的授权，因为它与您的应用程序的原始功能（以及应用程序核心资源上的行为）密切相关，所以基于权限的授权源代码会在您的功能更改时更改，而不是在安全策略更改时更改。 这意味着代码受到的影响远低于类似的基于角色的授权代码。

<br />

#### 权限检查

如果你想检查一个 `Subject` 是否被允许做某事，你可以调用任何各种 `isPermitted*` 方法变体。检查权限有两种主要方法 - 使用基于对象的权限实例或使用表示权限的字符串

- 基于对象的权限检查

    执行权限检查的一种可能方法是实例化 Shiro 的 `org.apache.shiro.authz.Permission` 接口的实例，并将其传递给接受权限实例的 `*isPermitted` 方法。

    例如，考虑如下假设：办公室中有一台打印机，其唯一标识符为 `laserjet4400n`，在我们允许当前用户按下“打印”按钮之前，我们的软件需要检查是否允许当前用户在该打印机上打印文档。他的权限检查是否可以这样表述：

    ```java
    Permission printPermission = new PrinterPermission("laserjet4400n", "print");
    
    Subject currentUser = SecurityUtils.getSubject();
    
    if (currentUser.isPermitted(printPermission)) {
        //show the Print button 
    } else {
        //don't show the button?  Grey it out?
    }
    ```

    在这个示例中，我们还看到了一个非常强大的实例级访问控制检查的例子 — 基于单个数据实例限制行为的能力。

    基于对象的权限检查在以下情况下很有用：

    - 你想要编译时类型安全
    - 您要保证正确表示和使用权限
    - 您希望显式控制权限解析逻辑（称为权限隐含逻辑，基于 Permission 接口的蕴含方法）的执行方式。
    - 您希望确保权限准确反映应用程序资源（例如，权限类可能可以在项目构建期间基于项目的域模型自动生成）

    根据您的需要，您可以调用几个面向*对象权限*的 `Subject` 方法：

    | Subject Method                                 | Description                                                  |
    | :--------------------------------------------- | :----------------------------------------------------------- |
    | `isPermitted(Permission p)`                    | 如果允许 `Subject` 执行操作或访问由指定 `Permission` 实例汇总的资源，则返回 `true`，否则返回 `false`。 |
    | `isPermitted(List<Permission> perms)`          | 返回与方法参数中的索引相对应的 `isPermitted` 结果数组。 如果需要执行许多权限检查（例如在自定义复杂视图时），可用作性能增强 |
    | `isPermittedAll(Collection<Permission> perms)` | 如果 `Subject` 被允许所有指定的权限，则返回 `true`，否则返回 `false`。 |

- 基于字符串的权限检查

    虽然基于对象的权限可能很有用（编译时类型安全、有保证的行为、自定义的隐含逻辑等），但对于许多应用程序来说，它们有时会感觉有点“笨拙”。另一种方法是使用普通字符串来表示权限实例。

    例如，基于上面的打印权限示例，我们可以将相同的检查重新制定为基于字符串的权限检查：

    ```java
    Subject currentUser = SecurityUtils.getSubject();
    
    if (currentUser.isPermitted("printer:print:laserjet4400n")) {
        //show the Print button
    } else {
        //don't show the button?  Grey it out? 
    }
    ```

    此示例仍然显示相同的实例级权限检查，但权限的重要部分 - 打印机（资源类型）、打印（操作）和 `laserjet4400n`（实例 ID） - 都以字符串表示。

    这个特殊的例子展示了由 Shiro 的默认 `org.apache.shiro.authz.permission.WildcardPermission` 实现定义的特殊冒号分隔格式，大多数人会觉得它合适

    也就是说，上面的代码块（大部分）是以下内容的快捷方式：

    ```java
    Subject currentUser = SecurityUtils.getSubject();
    
    Permission p = new WildcardPermission("printer:print:laserjet4400n");
    
    if (currentUser.isPermitted(p) {
        //show the Print button
    } else {
        //don't show the button?  Grey it out?
    }
    ```

    Shiro 的<a href="https://shiro.apache.org/permissions.html">权限文档</a>中深入介绍了 `WildcardPermission` 令牌格式和形成选项。

    虽然上面的 String 默认为 `WildcardPermission` 格式，但您实际上可以创造自己的 String 格式并根据需要使用它。 我们将在下面的 `Realm` 授权部分介绍如何执行此操作。

    基于字符串的权限是有益的，因为您不必强制实现接口，并且简单的字符串通常易于阅读。缺点是您没有类型安全，并且如果您需要超出字符串所代表范围的更复杂的行为，您将需要基于权限接口实现自己的权限对象。

    在实践中，大多数 Shiro 最终用户选择基于字符串的方法是为了简单，但最终您的应用程序的要求将决定哪种方法更好。

    与基于对象的权限检查方法一样，有一些字符串变体支持基于字符串的权限检查：

    | Subject Method                    | Description                                                  |
    | :-------------------------------- | :----------------------------------------------------------- |
    | `isPermitted(String perm)`        | 如果允许 `Subject` 执行操作或访问由指定 `String` 权限汇总的资源，则返回 `true`，否则返回 `false`。 |
    | `isPermitted(String... perms)`    | 返回与方法参数中的索引相对应的 `isPermitted` 结果数组。 如果需要执行许多字符串权限检查（例如，在自定义复杂视图时），可用作性能增强 |
    | `isPermittedAll(String... perms)` | 如果 `Subject` 被允许所有指定的 `String` 权限，则返回 `true`，否则返回 `false`。 |

    <br />

#### 权限断言

作为检查布尔值以查看是否允许 `Subject` 做某事的替代方法，您可以简单地断言它们在执行逻辑之前具有预期的权限。如果 `Subject` 不允许，将抛出 `AuthorizationException`。 如果它们按预期被允许，则断言将安静地执行，逻辑将按预期继续。

例如：

```java
Subject currentUser = SecurityUtils.getSubject();

// 确保当前的账户确实有权限能够打开一个银行账户
Permission p = new AccountPermission("open");
currentUser.checkPermission(p);
openBankAccount();
```

或者，同样的检查，使用基于 String 的权限断言：

```java
Subject currentUser = SecurityUtils.getSubject();

// 确保当前的账户确实有权限能够打开一个银行账户
currentUser.checkPermission("account:open");
openBankAccount();
```

这种方法相对于 `isPermitted*` 方法的一个好处是代码可以更简洁一些，因为如果当前 `Subject` 不满足预期条件（如果您不想这样做），您不必构造自己的 `AuthorizationExceptions`

根据您的需要，您可以调用一些面向权限的 `Subject` 断言方法：

| Subject Method                               | Description                                                  |
| :------------------------------------------- | :----------------------------------------------------------- |
| `checkPermission(Permission p)`              | 如果允许 `Subject` 执行操作或访问由指定 `Permission` 实例汇总的资源，则安静地返回，否则抛出 `AuthorizationException`。 |
| `checkPermission(String perm)`               | 如果 `Subject` 被允许执行操作或访问由指定 `String` 权限汇总的资源，则安静地返回，否则抛出 `AuthorizationException`。 |
| `checkPermissions(Collection<String> perms)` | 如果 `Subject` 被允许所有指定的权限，则安静地返回，否则抛出 `AuthorizationException`。 |
| `checkPermissions(String... perms)`          | 效果与上面的 `checkPermissions` 方法相同，但使用基于字符串的权限。 |

<br />

## 基于注解的授权

除了 `Subject` 的 API 调用，如果您更喜欢基于元数据的授权控制，Shiro 还提供了基于注解的方式来实现授权。

### 配置

在您可以使用 Java 注解之前，您需要在您的应用程序中启用 AOP 支持。有许多不同的 AOP 框架，因此遗憾的是，没有标准的方法可以在应用程序中启用 AOP。

对于 AspectJ，你可以查看 <a href="https://github.com/apache/shiro/tree/main/samples/aspectj"> AspectJ 示例程序</a>

对于 Spring 应用，你可以查看我们的<a href="https://shiro.apache.org/spring.html">Spring 整合</a>文档

对于 Guice 应用，你可以查看我们的 <a href="https://shiro.apache.org/guice.html">Guice 整合</a>文档

<br />

#### `RequiresAuthentication` 注解

`RequiresAuthentication` 注解要求当前 `Subject` 在其当前会话期间已通过身份验证，以便访问或调用注解的类/实例/方法。

例如：

```java
@RequiresAuthentication
public void updateAccount(Account userAccount) {
    //this method will only be invoked by a
    //Subject that is guaranteed authenticated
    ...
}
```

这主要等同于以下基于 `Subject` 的逻辑：

```java
public void updateAccount(Account userAccount) {
    if (!SecurityUtils.getSubject().isAuthenticated()) {
        throw new AuthorizationException(...);
    }

    //Subject is guaranteed authenticated here
    ...
}
```

<br />

#### `RequiresGuest` 注解

`RequiresGuest` 注解要求当前的 `Subject` 是一个“guest”，也就是说，它们没有被认证或从先前的会话中被记住，以便访问或调用注解的类/实例/方法。

例如：

```java
@RequiresGuest
public void signUp(User newUser) {
    //this method will only be invoked by a
    //Subject that is unknown/anonymous
    ...
}
```

这主要等同于以下基于 `Subject` 的逻辑：

```java
public void signUp(User newUser) {
    Subject currentUser = SecurityUtils.getSubject();
    PrincipalCollection principals = currentUser.getPrincipals();
    if (principals != null && !principals.isEmpty()) {
        //known identity - not a guest:
        throw new AuthorizationException(...);
    }

    //Subject is guaranteed to be a 'guest' here
    ...
}
```

<br />

#### `RequiresPermissions` 注解

`RequiresPermissions` 注解要求当前 `Subject` 被授予一个或多个权限，以便执行注解的方法。

例如：

```java
@RequiresPermissions("account:create")
public void createAccount(Account account) {
    //this method will only be invoked by a Subject
    //that is permitted to create an account
    ...
}
```

这主要等同于以下基于 `Subject` 的逻辑：

```java
public void createAccount(Account account) {
    Subject currentUser = SecurityUtils.getSubject();
    if (!subject.isPermitted("account:create")) {
        throw new AuthorizationException(...);
    }

    //Subject is guaranteed to be permitted here
    ...
}
```

<br />

#### `RequiresRoles` 注解

`RequiresRoles` 注释要求当前 `Subject` 具有所有指定的角色。 如果他们没有角色，则不会执行该方法并抛出 `AuthorizationException`。

例如：

```java
@RequiresRoles("administrator")
public void deleteUser(User user) {
    //this method will only be invoked by an administrator
    ...
}
```

这主要等同于以下基于 `Subject` 的逻辑：

```java
public void deleteUser(User user) {
    Subject currentUser = SecurityUtils.getSubject();
    if (!subject.hasRole("administrator")) {
        throw new AuthorizationException(...);
    }

    //Subject is guaranteed to be an 'administrator' here
    ...
}
```

<br />

#### `RequiresUser` 注解

`RequiresUser*` 注释要求当前 `Subject` 是应用程序用户，以便访问或调用带注释的类/实例/方法。 “应用程序用户” 被定义为具有已知身份的 `Subject`，该身份是由于在当前会话期间通过身份验证而已知的，或者是从先前会话的 “RememberMe” 服务中记住的。

例如：

```java
@RequiresUser
public void updateAccount(Account account) {
    //this method will only be invoked by a 'user'
    //i.e. a Subject with a known identity
    ...
}
```

这主要等同于以下基于 `Subject` 的逻辑：

```java
public void updateAccount(Account account) {
    Subject currentUser = SecurityUtils.getSubject();
    PrincipalCollection principals = currentUser.getPrincipals();
    if (principals == null || principals.isEmpty()) {
        //no identity - they're anonymous, not allowed:
        throw new AuthorizationException(...);
    }

    //Subject is guaranteed to have a known identity here
    ...
}
```

<br />

## JSP TagLib 授权

Shiro 提供了一个标签库，用于根据 `Subject` 状态控制 JSP/GSP 页面输出。 这在 Web 章节的 JSP/GSP 标记库部分中有介绍。

<br />

## 授权顺序

既然我们已经了解了如何基于当前 Subject 执行授权，那么让我们看看每当进行授权调用时 Shiro 内部会发生什么。

我们从架构一章中获取了之前的架构图，并且只突出了与授权相关的组件。 每个数字代表授权操作过程中的一个步骤：

![image.png](https://s2.loli.net/2022/01/24/eiYEXyDWAuIq1Vr.png)

步骤 1：应用程序或框架代码调用任何 `Subject` 的 `hasRole*`、`checkRole*`、`isPermitted*` 或 `checkPermission*` 方法变体，传入所需的任何权限或角色表示。

步骤 2：`Subject` 实例，通常是 `DelegatingSubject`（或子类），通过调用 `securityManager` 的几乎相同的相应 `hasRole*`、`checkRole*`、`isPermitted*` 或 `checkPermission*` 方法变体，委托给应用程序的 `SecurityManager`（`securityManager` 实现了 `org.apache.shiro.authz.Authorizer` 接口，它定义了所有特定于 `Subject` 的授权方法）

步骤 3：`SecurityManager` 是一个基本的“保护伞”组件，通过调用授权方各自的 `hasRole*`、`checkRole*`、`isPermitted*` 或 `checkPermission*` 方法来中继/委托到其内部 `org.apache.shiro.authz.Authorizer` 实例。 授权器实例默认是一个 `ModularRealmAuthorizer` 实例，它支持在任何授权操作期间协调一个或多个 `Realm` 实例。

步骤 4：检查每个配置的 `Realm` 以查看它是否实现了相同的 `Authorizer` 接口。 如果是，则调用 `Realm` 各自的 `hasRole*`、`checkRole*`、`isPermitted*` 或 `checkPermission*` 方法。

<br />

### ModularRealmAuthorizer

如前所述，Shiro `SecurityManager` 实现默认使用 `ModularRealmAuthorizer` 实例。 `ModularRealmAuthorizer` 同样支持具有单个 `Realm` 的应用程序以及具有多个 `Realm` 的应用程序。

对于任何授权操作，`ModularRealmAuthorizer` 将迭代其内部的 `Realm` 集合，并按迭代顺序与每个 `Realm` 交互。 每个 `Realm` 交互作用如下：

1. 如果 `Realm` 本身实现了 `Authorizer` 接口，则调用其各自的 `Authorizer` 方法（`hasRole*`、`checkRole*`、`isPermitted*` 或 `checkPermission*`）。
    1. 如果 `Realm` 的方法导致异常，则异常会作为 `AuthorizationException` 传播给 `Subject` 调用者。 这会缩短授权过程，并且不会为该授权操作访问任何剩余的 `Realm`。
    2. 如果 `Realm` 的方法是一个 `hasRole*` 或 `isPermitted*` 变体，它返回一个布尔值并且返回值为 `true`，则立即返回 `true`，并且所有剩余的 Realms 都会短路。 这种行为作为一种性能增强存在，通常如果一个 `Realm` 允许，则隐式地说明该 `Subject` 是被允许的。 这有利于安全策略，默认情况下禁止一切，明确允许事物，这是最安全的安全策略类型。
2. 如果 `Realm` 没有实现 `Authorizer` 接口，它会被忽略。

<br />

### Realm 授权顺序

重要的是要指出，就像身份验证一样，`ModularRealmAuthorizer` 将按迭代顺序与 `Realm` 实例交互。

`ModularRealmAuthorizer` 可以访问在 `SecurityManager` 上配置的 `Realm` 实例。执行授权操作时，它将遍历该集合，并且对于实现 `Authorizer` 接口本身的每个 `Realm`，调用 `Realm` 各自的 `Authorizer` 方法（例如，`hasRole`*、`checkRole*`、`isPermitted*` 或  `checkPermission*`）。

<br />

### 配置全局 `PermissionResolver`

在执行基于 String 的权限检查时，Shiro 的大多数默认 `Realm` 实现都会先将此 String 转换为实际的 Permission 实例，然后再执行权限隐含逻辑。

这是因为权限是基于隐含逻辑而不是直接相等性检查来评估的（有关隐含与相等性的更多信息，请参阅<a href="https://shiro.apache.org/permissions.html">权限文档</a>）。 隐含逻辑在代码中要比通过字符串来表示会更好。因此，大多数 `Realm` 需要将提交的权限字符串转换或解析为相应的代表性 `Permission` 实例。

为了帮助进行这种转换，Shiro 支持 `PermissionResolver` 的概念。大多数 Shiro `Realm` 实现通过`PermissionResolver` 来支持他们实现 `Authorizer` 接口的基于字符串的权限方法：当在 `Realm` 上调用这些方法之一时，它将使用 `PermissionResolver` 将字符串转换为 `Permission` 实例，并以这种方式执行检查。

所有 Shiro `Realm` 实现都默认使用内部 `WildcardPermissionResolver`，它采用 Shiro 的 `WildcardPermission` 字符串格式。

如果您想创建自己的 `PermissionResolver` 实现，也许是为了支持您自己的 `Permission` 字符串语法，并且您希望所有配置的 `Realm` 实例都支持该语法，您可以为所有可以配置的 `Realm` 全局设置 `PermissionResolver`。

例如，在 `shiro.ini` 配置文件中：

```ini
globalPermissionResolver = com.foo.bar.authz.MyPermissionResolver
...
securityManager.authorizer.permissionResolver = $globalPermissionResolver
```

如果要配置全局 `PermissionResolver`，每个接收配置的 `PermissionResolver` 的 `Realm` 都必须实现 `PermisionResolverAware` 接口。 这保证了配置的实例可以中继到支持这种配置的每个 `Realm`。

如果你不想使用全局 `PermissionResolver` 或者你不想被 `PermissionResolverAware` 接口干扰，你总是可以显式地配置一个带有 `PermissionResolver` 实例的 `Realm`（假设有一个 `JavaBeans` 兼容的 `setPermissionResolver` 方法）：

```ini
permissionResolver = com.foo.bar.authz.MyPermissionResolver

realm = com.foo.bar.realm.MyCustomRealm
realm.permissionResolver = $permissionResolver
...
```

<br />

### 配置全局 `RolePermissionResolver`

在概念上类似于 `PermissionResolver`，`RolePermissionResolver` 能够表示 `Realm` 执行权限检查所需的 `Permission` 实例。

然而，与 `RolePermissionResolver` 的主要区别在于输入字符串是角色名称，而不是权限字符串。

当需要将角色名称转换为一组具体的权限实例时，`Realm` 可以在内部使用 `RolePermissionResolver`。

对于支持可能没有权限概念的遗留或不灵活的数据源，这是一个特别有用的功能。

例如，许多 LDAP 目录存储角色名称（或组名称）但不支持将角色名称关联到具体权限，因为它们没有“权限”概念。基于 Shiro 的应用程序可以使用存储在 LDAP 中的角色名称，但实现 `RolePermissionResolver` 将 LDAP 名称转换为一组显式权限，以执行首选的显式访问控制。权限关联将存储在另一个数据存储中，可能是本地数据库。

因为这种将角色名称转换为权限的概念是非常特定于应用程序的，所以 Shiro 的默认 `Realm`实现不使用它们。

但是，如果您想创建自己的 `RolePermissionResolver` 并拥有多个要配置的 `Realm` 实现，则可以为所有可以配置一个的 `Realm` 全局设置 `RolePermissionResolver`。

在 `shiro.ini` 文件中做如下的配置：

```ini
globalRolePermissionResolver = com.foo.bar.authz.MyPermissionResolver
...
securityManager.authorizer.rolePermissionResolver = $globalRolePermissionResolver
...
```

如果要配置全局 `RolePermissionResolver`，每个接收配置的 `RolePermissionResolver` 的 `Realm` 都必须实现 `RolePermisionResolverAware` 接口。 这保证了配置的全局 `RolePermissionResolver` 实例可以中继到支持这种配置的每个 `Realm`。

如果你不想使用全局 `RolePermissionResolver` 或者你不想被 `RolePermissionResolverAware` 接口所困扰，你总是可以显式地配置一个带有 `RolePermissionResolver` 实例的 `Realm`（假设有一个 `JavaBeans` 兼容的 `setRolePermissionResolver` 方法）：

在 `shiro.ini` 配置文件做如下配置：

```ini
rolePermissionResolver = com.foo.bar.authz.MyRolePermissionResolver

realm = com.foo.bar.realm.MyCustomRealm
realm.rolePermissionResolver = $rolePermissionResolver
...
```

<br />

### 配置授权

如果您的应用程序使用多个 `Realm` 来执行授权，并且 `ModularRealmAuthorizer` 的默认简单基于迭代的短路授权行为不适合您的需求，您可能需要创建自定义 `Authorizer` 并相应地配置 `SecurityManager`。

例如，在 `shiro.ini` 文件中做如下配置：

```ini
[main]
...
authorizer = com.foo.bar.authz.CustomAuthorizer

securityManager.authorizer = $authorizer
```