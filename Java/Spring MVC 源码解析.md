# Spring MVC 源码解析

**本文的 MVC 基于传统的 Servlet 应用****



## 静态资源的加载

参考 Spring Boot 中给出的文档，原文如下：

> By default, Spring Boot serves static content from a directory called `/static` (or `/public` or `/resources` or `/META-INF/resources`) in the classpath or from the root of the `ServletContext`. It uses the `ResourceHttpRequestHandler` from Spring MVC so that you can modify that behavior by adding your own `WebMvcConfigurer` and overriding the `addResourceHandlers` method.
>
> In a stand-alone web application, the default servlet from the container is also enabled and acts as a fallback, serving content from the root of the `ServletContext` if Spring decides not to handle it. Most of the time, this does not happen (unless you modify the default MVC configuration), because Spring can always handle requests through the `DispatcherServlet`.

大致的翻译如下：

> 默认情况下，Spring Boot 服务端的静态内容来自以下几个在 classpath 路径下或者是 `ServletContext` 的根目录路径下的目录的三个子目录：/static、/resource 和 /META-NF/resources。它使用 Spring MVC 中的 `ResourceHttpRequestHandler`  来加载这些静态资源，因此你可以通过添加你自己的 `WebMvcConfigurer` 并且重写 `addResourceHandlers` 方法来修改它的行为
>
> 在单一的 Web 应用中，默认的容器中的 Servlet 已经启用作为备用。如果 Spring 决定不去处理它，则从  `ServletContext`  的根目录下获取内容。在大部分的情况下，这种事件不会发生（除非你修改了默认的 MVC 配置），因为 Spring 总是能够通过 `DispatcherServlet` 来处理请求



对于一般的 Spring Boot 的 Web 应用来讲，`classpath` 就是对应的项目中的 `resources` 目录，具体结构如下图所示：
![IDm0Pk.png](https://i.loli.net/2021/11/18/39AWIxeETLpz2rb.png)

可以通过加入相关的配置来修改静态资源的加载行为：

`application.yml` 或 `application.properties` 配置文件中：

```yml
spring:
  mvc:
  	# 这个配置属性的作用是为每个加载的静态资源添加一个访问地址前缀，默认是 "/"
    static-path-pattern: "/static/**" # 现在将访问地址前缀设置为 "/static"
    
   web:
    resources:
      # 这个配置属性的作用是定义静态资源的加载路径（相对于 "resources"）
      static-locations: [classpath:/mine/]
```



### 源码分析

按照官方文档，具体的逻辑定义在 `WebMvcConfigurer` 的 `addResourceHandlers` 方法中：

```java
/**
	Spring 默认的具体实现类为 org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
*/
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // 如果在配置文件中禁用了自动添加 Mapping 的属性，则不会为静态资源创建 Mapping
    if (!this.resourceProperties.isAddMappings()) {
        logger.debug("Default resource handling disabled");
        return;
    }
    
    addResourceHandler(registry, "/webjars/**", "classpath:/META-INF/resources/webjars/");
    
    /* 
    	在这里设置静态资源的前缀请求路径，
    	以及将对应的静态资源的路径注册到 this.resourceProperties.getStaticLocations() 中	
    	
    	这里最终会生成对应的具体资源访问路径
    */
    addResourceHandler(registry, this.mvcProperties.getStaticPathPattern(), (registration) -> {
        registration.addResourceLocations(this.resourceProperties.getStaticLocations());
        if (this.servletContext != null) { // 对应官方文档中
            ServletContextResource resource = new ServletContextResource(this.servletContext, SERVLET_LOCATION);
            registration.addResourceLocations(resource);
        }
    });
}
```





## Spring MVC 的执行流程

一般的执行流程如下：

<img src="https://upload-images.jianshu.io/upload_images/5220087-3c0f59d3c39a12dd.png?imageMogr2/auto-orient/strip|imageView2/2/w/1002/format/webp" />

