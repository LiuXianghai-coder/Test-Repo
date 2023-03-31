# Java 注解的实现原理

## 注解的本质

在 `java.lang.annotation.Annotation` 接口中有这样的描述：

> The common interface extended by all annotation interfaces.

大致意思就是所有的注解接口都继承自该 `Annotaion` 接口

假设现在我们编写了一个新的注解 `ReadAuth`，该注解的目的是标记那些读取数据需要权限的操作，如下所示：

```java
public @interface ReadAuth {
}
```

现在，编译这个注解类，然后通过 `javap` 命令查看反编译之后的结果：

```java
Compiled from "ReadAuth.java"
public interface com.example.eamples.annotations.ReadAuth extends java.lang.annotation.Annotation {
}
```

可以看到，注解的本质是一个继承了 `java.lang.annotation.Annotation `接口的接口类

注解是元数据的一种提供形式，提供不属于程序本身的数据，相当与给某个程序区域打上标签。

然而，如果使用 Spring 开发项目的话，经常会见到使用注解就能完成许多任务的情况，如：通过 `@Controller` 定义控制器、`@RequestMapping` 定义请求 `url` 等。这些注解本质上也只是一个标记的作用，具体功能的实现是通过 Spring 来解析这些注解来实现

解析注解有两种方式：一是在编译阶段扫描注解，二是在运行期间通过反射的方式来获取相关的注解信息。第一种方式要求编译器能够检测到合法的注解，由于编译器一般情况下没有办法修改它们的行为，因此对于用户或者框架自定义的注解，都需要通过反射的方式来获取注解的元数据信息

<br />

## 元注解

“元注解” 是 `JDK`  中内置的几种用于修饰注解的注解。通常在注解的定义上能够看到这些注解，如常见的方法重写注解 `@Override`

```java
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Override {
}
```

其中，`@Target` 和 `@Retention` 注解就是 `JDK` 中内置的元注解，表示自定义的注解应该作用的代码范围和保留时间段

`JDK` 中存在以下几个元注解：

- `@Retention`：`@Retention`注解指定标记的注解的存储方式，有以下三种存储方式：
    - `RetentionPolicy.SOURCE`：标记的注解仅保留在源代码级别，并被编译器忽略
    - `RetentionPolicy.CLASS`：标记的注释在编译时由编译器保留，但被 Java 虚拟机忽略（即类加载阶段忽略）
    - `RetentionPolicy.RUNTIME`：标记的注解由 JVM 保留，因此它可以被运行时环境使用
- `@Documented`：`@Documented` 注解表示无论注解的存储方式如何，这些注解都能够使用 `javadoc` 工具生成到文档中（默认情况下，注解将不会被包括到 `javadoc` 生成的文档中）
- `@Target`：`@Target` 注解标记另一个注解，以限制该注解可以应用于哪些 Java 元素。`@Target` 可以指定以下元素类型的一个或多个作为其值：
    - `ElementType.ANNOTATION_TYPE`表示该注解的作用范围为注解
    - `ElementType.CONSTRUCTOR` 作用于构造函数
    - `ElementType.FIELD` 作用于字段或者属性
    - `ElementType.LOCAL_VARIABLE` 作用于局部变量
    - `ElementType.METHOD` 作用于方法级别
    - `ElementType.PACKAGE` 作用于包声明
    - `ElementType.PARAMETER` 作用于一个方法的参数
    - `ElementType.TYPE` 作用于一个类的任意元素（该类可以是一般类、接口或枚举）
- `@Inherited`：@Inherited 注解表示注解类型可以继承自父类（默认情况下不可以继承）。当用户查询注解类型并且类没有该类型的注解时，查询该类的父类的注解类型。 该注解仅适用于类声明。
- `@Repeatable`：`@Repeatable` 注解，在 Java SE 8 中引入，表示标记的注解可以多次应用于同一个声明或类型使用。

<br />

## JDK 预定义注解

在 JDK 1.8 中，预先定义了以下几种注解：

- `@Deprecated`：`@Deprecated` 注解表示标记的元素已被弃用，不应再使用。每当程序使用带有 @Deprecated 注释的方法、类或字段时，编译器都会生成警告。
- `@Override`：`@Override` 注释通知编译器该元素将要重写在父类中声明的元素。虽然重写方法时不需要使用此注释，但它有助于防止错误。 如果标有 `@Override` 的方法未能正确覆盖其父类之一中的方法，则编译器会生成错误。
- `@SuppressWarnings`：`@SuppressWarnings` 注释告诉编译器抑制它将生成的警告。每个编译器警告都属于一个类别。 Java 语言规范列出了两个类别：弃用和未选中。
- `@SafeVarargs`：`@SafeVarargs` 注释，当应用于方法或构造函数时，断言代码不会对其 varargs 参数执行潜在的不安全操作。 使用此注释类型时，与可变参数使用相关的未经检查的警告将被禁止。
- `@FunctionalInterface`：`@FunctionalInterface` 注解，在 Java SE 8 中引入，表示类型声明旨在成为 Java 语言规范所定义的功能接口



<br />

## 注解与反射

在 Java 虚拟机规范中，定义了一系列和注解相关的属性表，也就是说，无论是字段、方法还是类，如果被注解修饰了，那么就可以写入到对应的字节码文件。对应的属性表有以下几种：

- `RuntimeVisibleAnnotations`：运行时可见的注解
- `RuntimeInVisibleAnnotations`：运行时不可见的注解
- `RuntimeVisibleParameterAnnotations`：运行时可见的方法参数注解
- `RuntimeInvisibleParameterAnnotations`：运行时不可见的方法参数注解
- `AnnotationDefault`：注解类元素的默认值

由于在 `Class` 文件中存在这些属性，因此对于一个类或者接口来说，相关类的`Class`对象能够提供以下几种和注解交互的方法：

- `getAnnotation`：返回指定的注解
- `isAnnotationPresent`：判断当前的元素是否被指定的注解修饰过
- `getAnnotations`：返回该元素上的所有注解
- `getDeclaredAnnotation`：返回本元素的指定注解
- `getDeclaredAnnotations`：返回本元素的所有注解，不包括从父注解继承来的注解

接下来，让我们看看 JDK 是如何获取到相关的注解的

依旧以前面提到的 `@ReadAuth` 为例，下面是自定义的 `@ReadAuth` 的定义：

```java
import java.lang.annotation.*;

@Inherited
@Documented
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReadAuth {
}
```

然后编写下面的示例来获取方法的注解：

```java

import java.lang.reflect.Method;

public class TestReadAuth {
    @ReadAuth
    static void readTest() {
        System.out.println("Read Auth Test");
    }

    static {
        /* 
        	JDK 8 及其之前的版本需要设置 sun.misc.ProxyGenerator.saveGeneratedFiles 属性为 true，JDK 8 之后版本
        	则需要设置 jdk.proxy.ProxyGenerator.saveGeneratedFiles 属性为 true，具体可以查看 ProxyGenerator  的saveGeneratedFiles 定义的属性
            
                配置这个属性的目的在于保存在程序运行过程中生成的 Proxy 对象，
                假设获取注解的过程是通过代理的方式来实现的，通过配置该属性就能够保存中间的代理对象
        */
        //  System.getProperties().put("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");
        System.getProperties().put("jdk.proxy.ProxyGenerator.saveGeneratedFiles", "true");
    }

    public static void main(String[] args) throws NoSuchMethodException {
        Class<?> cls = TestReadAuth.class;
        Method method = cls.getDeclaredMethod("readTest"); // 通过反射获取类的方法
        
        ReadAuth readAuth = method.getAnnotation(ReadAuth.class); // 获取方法上的注解
    }
}
```

运行这段代码，会发现在项目的根目录下看到类似下图所示的代理类：

<img src="https://s2.loli.net/2022/02/14/AxZzepoDcCvWKiH.png" alt="2022-02-14 07-40-46 的屏幕截图.png" style="zoom:100%;" />

如果没有看到这些，那么请尝试移除当前项目中的其它依赖（如 Spring），这些依赖项目的存在很有可能会导致相关属性的配置失效

通过发现这些 Proxy，可以大致推断注解的获取极有可能是通过代理的方式来实现的，反编译查看生成的 Proxy 类，关键的 Proxy 是实现 `ReadAuth` 接口的 Proxy，构造函数部分如下：

![2022-02-14 07-47-33 的屏幕截图.png](https://s2.loli.net/2022/02/14/Fd5fa6kZNocJmVy.png)

关键的部分就是使用 `InvocationHandler` 参数这个构造函数（`m1`、`m2`、`m3`、`m4` 都是 `Annotation` 接口定义的方法，因为所有的注解都继承自 `Annotation`）。`InvocationHandler` 是使用 JDK 动态代理时需要实现的接口，因此可以判断这里的代理类型为 JDK 动态代理

查看 `InvocationHandler` 的具体实现，可以发现在 `AnnotationInvocationHandler` 中有一段这样的描述：

> InvocationHandler for dynamic proxy implementation of Annotation.

大致意思就是：用于注解的动态代理实现的 `InvocationHandler`

也就是说，生成的代理类的 `InvocationHandler` 参数的具体实现就是 `AnnotationInvocationHandler`

按照 JDK 动态代理的基本使用，关键的部分是 `invoke` 方法的实现，具体在 `AnnotationInvocationHandler` 的实现如下：

```java
public Object invoke(Object proxy, Method method, Object[] args) {
    String member = method.getName();
    int parameterCount = method.getParameterCount();

    // Handle Object and Annotation methods
    if (parameterCount == 1 && member == "equals" &&
        method.getParameterTypes()[0] == Object.class) {
        return equalsImpl(proxy, args[0]);
    }
    if (parameterCount != 0) {
        throw new AssertionError("Too many parameters for an annotation method");
    }

    // 如果 是 Annotation 中定义的方法，那么则调用 AnnotationInvocationHandler 中的具体实现
    if (member == "toString") {
        return toStringImpl();
    } else if (member == "hashCode") {
        return hashCodeImpl();
    } else if (member == "annotationType") {
        return type;
    }

    // Handle annotation member accessors
    /*
    	走到这说明是自定义的方法（属性），尝试获取属性值
    	
    	这里的  memberValues 在构造 AnnotationInvocationHandler 时就已经完成初始化了，这是一个
    	Map 字段，存储的时注解中配置的属性名 ——> 属性值的映射
    */
    Object result = memberValues.get(member);

    if (result == null)
        throw new IncompleteAnnotationException(type, member);

    if (result instanceof ExceptionProxy)
        throw ((ExceptionProxy) result).generateException();

    if (result.getClass().isArray() && Array.getLength(result) != 0)
        result = cloneArray(result);

    return result;
}
```

<br />

## 总结

- 注解本质上是继承了 `Annotation` 接口的接口类，用于提供相关元素的元数据信息
- Java 虚拟机中会按照注解的存储方法存储在类的不同时间段，如果保留时间为 `RUNTIME`，那么在 Java 虚拟机中将会保存这个注解，同时有相关的属性表来存储这些注解，因此通过反射获取注解在理论上具有可行性
- 实际获取注解时是通过代理的方式来实现的，`AnnotationInvocationHandler` 是实际方法调用所有者。对于注解参数的获取，`AnnotationInvocationHandler` 中通过 `memberValues` 的 `Map` 结构来存储相关的映射关系



<br />

参考：

<sup>[1]</sup> https://juejin.cn/post/6844903636733001741#heading-0

<sup>[2]</sup> https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7