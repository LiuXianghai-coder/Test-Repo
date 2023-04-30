# Implementation of JAXB-API has not been found on module path or classpath

在将部分 Spring Cloud 相关的项目从 JDK 8 迁移到 JDK 11 的过程中，使用 `Eureka` 作为注册中心，但是在 JDK 11 的环境下会出现类似下面的异常：

``` text
javax.xml.bind.JAXBException: Implementation of JAXB-API has not been found on module path or classpath.
	at javax.xml.bind.ContextFinder.newInstance(ContextFinder.java:131) ~[jakarta.xml.bind-api-2.3.3.jar:2.3.3]
	at javax.xml.bind.ContextFinder.find(ContextFinder.java:318) ~[jakarta.xml.bind-api-2.3.3.jar:2.3.3]
	at javax.xml.bind.JAXBContext.newInstance(JAXBContext.java:478) ~[jakarta.xml.bind-api-2.3.3.jar:2.3.3]
	at javax.xml.bind.JAXBContext.newInstance(JAXBContext.java:435) ~[jakarta.xml.bind-api-2.3.3.jar:2.3.3]
	at javax.xml.bind.JAXBContext.newInstance(JAXBContext.java:336) ~[jakarta.xml.bind-api-2.3.3.jar:2.3.3]
	at com.sun.jersey.server.impl.wadl.WadlApplicationContextImpl.<init>(WadlApplicationContextImpl.java:107) ~[jersey-server-1.19.4.jar:1.19.4]
	at com.sun.jersey.server.impl.wadl.WadlFactory.init(WadlFactory.java:100) ~[jersey-server-1.19.4.jar:1.19.4]
	at com.sun.jersey.server.impl.application.RootResourceUriRules.initWadl(RootResourceUriRules.java:169) ~[jersey-server-1.19.4.jar:1.19.4]
	....................................
```

这是因为 JDK 8 中的一部分 Java EE 类自 JDK 9 开始便已经被废弃，在这里出现的问题为 `JAXB`（Java Architecture for XML Binding）库已经被弃用，如果使用的是 JDK 9 或 JDK 10，那么可以在启动时的参数中添加以下选项以开启这些类库的使用：

``` shell
# 添加 java ee 部分的模块
java -jar xxx.jar --add-moudles java.se.ee
```

但是在更高版本的 JDK 中，这些类库已经被彻底废弃了，即使加上这个选项依旧无法开启这些类库，因此需要手动在相关的项目构建工具中添加对应的依赖项，对于 Maven 类的项目来讲，需要添加以下几个依赖项：

``` xml
<!-- java.xml.bind 模块的接口 API -->
<dependency>
    <groupId>jakarta.xml.bind</groupId>
    <artifactId>jakarta.xml.bind-api</artifactId>
    <version>4.0.0</version>
</dependency>

<!-- 具体对应的实现类模块 -->
<dependency>
    <groupId>org.glassfish.jaxb</groupId>
    <artifactId>jaxb-runtime</artifactId>
    <version>2.3.0</version>
</dependency>
```

<hr />

参考：

<sup>[1]</sup> https://stackoverflow.com/questions/43574426/how-to-resolve-java-lang-noclassdeffounderror-javax-xml-bind-jaxbexception?page=1&tab=scoredesc#tab-top

<sup>[2]</sup> https://eclipse-ee4j.github.io/jaxb-ri/