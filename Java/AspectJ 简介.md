# `AspectJ` 简介

## 引言

首先，明确以下几个概念：

- 切面（Aspect）：跨越多个对象的连接点的模块化（简单理解为监视切点的类）。
- 连接点（Joint Point）：程序执行过程中的一个点，例如方法的的执行或者属性的访问
- 通知（Advice）：在切面中特定的连接点采取的行为
- 切点（Pointcut）：通过相关表达式匹配的连接点



​	一般来讲，实现 `AOP`  主要有以下两种手段：静态代理、动态代理。

​	静态代理将要执行的织入点的操作和原有类封装成一个代理对象，在执行到相应的切点时执行代理的操作，这种方式较为笨拙，一般也不会采用这种方式来实现 `AOP`。

​	动态代理是一种比较好的解决方案，通过在程序运行时动态生成代理对象来完成相关的 `Advice` 操作。Spring 便是通过动态代理的方式来实现 `AOP` 的。使用动态代理的方式也有一定的局限性，操作更加复杂，同时相关的类之间也会变得耦合起来。

​	相比较与使用代理的方式来实现 `AOP`，`AspectJ`是目前作为 `AOP` （Aspect-Oriented Programming 面向切面编程） 实现的一种最有效的解决方案。



## 使用介绍

`AspectJ` 提供了三种方式来实现 `AOP`，通过在类加载的不同时间段来完成相关代码的织入以达到目的。

具体有以下三种方式：

- 编译期（compiler-time）织入：在类进行编译的时候就将相应的代码织入到元类文件的 `.class` 文件中
- 编译后（post-compiler）织入：在类编译后，再将相关的代码织入到 `.class` 文件中
- 加载时（load-time） 织入：在 `JVM` 加载 `.class` 文件的时候将代码织入



### 需要的依赖项

使用 `AspectJ` 主要依赖于以下两个依赖：

```xml
<properties>
    <!-- AspectJ 依赖的版本 -->
    <aspectj.version>1.9.7</aspectj.version>
</properties>

<!-- 织入的依赖 -->
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjweaver</artifactId>
    <version>${aspectj.version}</version>
</dependency>

<!-- 运行时需要的依赖 -->
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjrt</artifactId>
    <version>${aspectj.version}</version>
</dependency>
```



### 定义具体实体类

定义一个 `Account` 类，以及相关的一些行为方法

```java
public class Account {
    int balance = 20;
    
    // 提款操作
    public boolean withDraw(int amount) {
        if (balance < amount) return false;
        balance -= amount;

        return true;
    }
}
```



### 定义 `Aspect`

可以通过使用 `AspectJ` 的语法来定义切面，也可以通过 `Java` 来定义

- 使用 `AsjpectJ` 定义切面

  ```java
  public aspect AccountAspect { // Aspect，注意概念的对应关系
      final int MIN_BALANCE = 10;
      
      /*
      	定义切点，对应 Pointcut
      	这里的切点定义在调用 Account 对象在调用 witdraw 方法
      */
      pointcut callWithDraw(int amount, Account acc):
      call(boolean Account.withdraw(int)) && args(amount) && target(acc);
  
      /*
      	在上文定义的切点执行之前采取的行为，这就被称之为 Advice
      */
      before(int amount, Account acc): callWithDraw(amount, acc) {
          System.out.println("[AccountAspect] 付款前总额: " + acc.balance);
          System.out.println("[AccountAspect] 需要付款: " + amount);
      }
  
      /*
      	在对应的切点执行前后采取的行为
      */
      boolean around(int amount, Account acc):
      callWithDraw(amount, acc) {
          if (acc.balance < amount) {
              System.out.println("[AccountAspect] 拒绝付款！");
              return false;
          }
          return proceed(amount, acc);
      }
  
      /*
      	对应的切点执行后的采取的行为
      */
      after(int amount, Account balance): callWithDraw(amount, balance) {
          System.out.println("[AccountAspect] 付款后剩余：" + balance.balance);
      }
  }
  ```

  

- 使用 `Java` 来定义切面

  ```java
  import org.aspectj.lang.ProceedingJoinPoint;
  import org.aspectj.lang.annotation.Aspect;
  import org.aspectj.lang.annotation.Pointcut;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  
  @Aspect
  public class ProfilingAspect {
      private final static Logger log = LoggerFactory.getLogger(ProfilingAspect.class);
  
      // 定义切点
      @Pointcut("execution(* org.xhliu.aop.entity.Account.*(..))")
      public void modelLayer() {}
      
      // 在切点执行前后采取的行为，这里是记录方法调用的时间
      @Around("modelLayer()")
      public Object logProfile(ProceedingJoinPoint joinPoint) throws Throwable {
          long startTime = System.currentTimeMillis();
          Object result = joinPoint.proceed();
          log.info("[ProfilingAspect] 方法：【" + joinPoint.getSignature()
                   + "】结束，耗时：" + (System.currentTimeMillis() - startTime));
  
          return result;
      }
  }
  ```



## 编译时织入

由于 `javac` 无法编译 `AspectJ`，因此首先需要加入相关的 `AspectJ` 插件来完成编译时的织入：

```xml
<!-- 编译时织入的 maven 插件 -->
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>aspectj-maven-plugin</artifactId>
    <version>1.7</version>
    <configuration>
        <complianceLevel>1.8</complianceLevel>
        <source>1.8</source>
        <target>1.8</target>
        <showWeaveInfo>true</showWeaveInfo>
        <verbose>true</verbose>
        <Xlint>ignore</Xlint>
        <encoding>UTF-8</encoding>
    </configuration>
    <executions>
        <execution>
            <goals>
                <!-- use this goal to weave all your main classes -->
                <goal>compile</goal>
                <!-- use this goal to weave all your test classes -->
                <goal>test-compile</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

现在，定义一个 `Main` 方法来执行 `Account` 的方法：

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AspectApplication {
    private final static Logger log = LoggerFactory.getLogger(AspectApplication.class);

    public static void main(String[] args) {
        Account account = new Account();
        log.info("================ 分割线 ==================");
        account.withDraw(10);
        account.withDraw(100);
        log.info("================ 结束 ==================");
    }
}
```

此时运行 `Main` 方法（可能会存在缓存，使用 `mvn clean` 来清理原来的生成文件），会看到类似于如下的输出：

<img src="https://s6.jpg.cm/2021/11/12/ITzpPu.png" /> 

对 `Main` 方法所在的类进行反编译，得到类似如下图所示的结果：

<img src="https://s6.jpg.cm/2021/11/12/ITzvuG.png" />

可以看到，在生成的 `.class` 文件中已经添加了 `Aspect` 的相关内容，因此在运行时会执行在 `AccountAspect` 中定义的内容。

再查看 `Account` 编译后的类：

<img src="https://s6.jpg.cm/2021/11/12/ITzw44.png" />

可以看到，`Account.class` 也已经被织入了一些 `Aspect` 的内容



以上操作都是在 `shell` 中完成，因为有的 `IDE` 将使用自己的一些特有的处理方式而不是使用插件。

```shell
# 使用 mvn 来启动相关的主类
mvn exec:java -D"exec.mainClass"="org.xhliu.aop.entity.AspectApplication"
```



## 编译后织入

一般来讲，使用编译后的织入方式已经足够了，但是试想一下这样的场景：现在已经得到了一个 `SDK`，需要在这些 `SDK` 的类上定义一些切点的行为，这个时候只能针对编译后的 `.class` 文件进行进一步的织入，或者在加载 `.class` 时再织入。

在另一个项目中定义一个 `UserAccount` 的主类

```java
package com.example.aopshare.entity;

public class UserAccount {
    private int balance = 20;

    public UserAccount() {
    }

	public int getBalance(){return this.balance;}

	public void setBalance(int balance){
			this.balance = balance;
	}

    public boolean withDraw(int amount) {
        if (this.balance < amount) {
            return false;
        } else {
            this.balance -= amount;
            return true;
        }
    }
}
```

将这个项目打包到本地的 `maven` 仓库

```shell
mvn clean package

mvn install
```



现在就可以直接在当前的项目中引用这个 `SDK` 了，加入对应的 `gav` 即可：

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>aop-share</artifactId>
    <version>1.0</version>
</dependency>
```



为这个第三方库的 `UserAccount` 创建一个 `Aspect`：

```java
import com.example.aopshare.entity.UserAccount;

public aspect UserAccountAspect {
    pointcut callWithDraw(int amount, UserAccount acc):
            call(boolean UserAccount.withDraw(int)) && args(amount) && target(acc);

    before(int amount, UserAccount acc): callWithDraw(amount, acc) {
        System.out.println("[UserAccountAspect] 付款前总额: " + acc.getBalance());
        System.out.println("[UserAccountAspect] 需要付款: " + amount);
    }

    boolean around(int amount, UserAccount acc):
            callWithDraw(amount, acc) {
        if (acc.getBalance() < amount) {
            System.out.println("[UserAccountAspect] 拒绝付款！");
            return false;
        }
        return proceed(amount, acc);
    }

    after(int amount, UserAccount balance): callWithDraw(amount, balance) {
        System.out.println("[UserAccountAspect] 付款后剩余：" + balance.getBalance());
    }
}
```

分割线——————————————————————————————

准备工作已经完成，现在正式开始实现编译后的织入，只需添加对应的插件即可完成：

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>aspectj-maven-plugin</artifactId>
    <version>1.7</version>
    <configuration>
        <complianceLevel>1.8</complianceLevel>
        <!-- 要处理的第三方 SDK，只有在这里定义的 SDK 才会执行对应后织入 -->
        <weaveDependencies>
            <weaveDependency>
                <groupId>com.example</groupId>
                <artifactId>aop-share</artifactId>
            </weaveDependency>
        </weaveDependencies>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>compile</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

现在再运行 `Main` 类，得到与编译时织入类似的输出：

<img src="https://s6.jpg.cm/2021/11/12/IThMYr.png" />





## 加载时织入

在 `JVM` 加载 Class 对象的时候完成织入。`Aspect` 通过在启动时指定 `Agent` 来实现这个功能

加载时织入与编译后织入的使用场景十分相似，因此依旧以上文的例子来展示加载时织入的使用

**首先，注释掉使用到的 `aspect` 编译插件，这回影响到这部分的测试**

在项目的 `resources` 目录下的 `META-INF`目录（如果没有就创建一个）中，添加一个 `aop.xml` 文件，具体内容如下所示：

```xml
<!DOCTYPE aspectj PUBLIC "-//AspectJ//DTD//EN" "http://www.eclipse.org/aspectj/dtd/aspectj.dtd">
<aspectj>
    <aspects>
        <aspect name="com.javadoop.aspectjlearning.aspect.ProfilingAspect"/>
        <weaver options="-verbose -showWeaveInfo">
            <include within="com.javadoop.aspectjlearning..*"/>
        </weaver>
    </aspects>
</aspectj>
```



