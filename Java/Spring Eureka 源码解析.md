# Spring Eureka 源码解析

本文将简要分析一下关于 `Spring Eureka` 相关的一些必要的源代码，对应的版本：Spring Cloud 2021.0.1

## `@EnableEurekaServer` 注解

`@EnableEurekaServer` 注解标记当前的应用程序作为一个注册中心，查看 `@EnableEurekaServer` 的源代码，具体内容如下所示：


```java
package org.springframework.cloud.netflix.eureka.server;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(EurekaServerMarkerConfiguration.class)
public @interface EnableEurekaServer {
}
```

可以看到，该注解导入了 `EurekaServerMarkerConfiguration` 配置类，继续查看 `EurekaServerMarkerConfiguration` 对应的源代码，具体内容如下所示：

```java
package org.springframework.cloud.netflix.eureka.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EurekaServerMarkerConfiguration {

	@Bean
	public Marker eurekaServerMarkerBean() {
		return new Marker();
	}

	class Marker {

	}

}
```

可以看到，该配置类只是定义了一个 `Mark` 类型的 `Bean`。可以看到，`Mark` 就是在 `EurekaServerMarkerConfiguration` 中定义的一个类，该类并没有任何特殊的地方。

由于 `Spring-Boot` 的自动配置是通过加载 `spring.factories` 文件中的内容来完成自动配置的，因此，可以试着从当前 `EurekaServerMarkerConfiguration` 配置类所在的包的 `spring.factories` 文件入手来进一步的进行分析

查看 `EurekaServerMarkerConfiguration` 对应的包的 `spring.factories` 文件，具体内容如下所示：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  org.springframework.cloud.netflix.eureka.server.EurekaServerAutoConfiguration
```

可以看到，在 `spring.factories` 文件中只有一个自动配置类 `.EurekaServerAutoConfiguratio`，查看该类对应的源代码，具体的内容如下所示：

```java
package org.springframework.cloud.netflix.eureka.server;

// 省略一些其它的包的导入

@Configuration(proxyBeanMethods = false)
@Import(EurekaServerInitializerConfiguration.class)
/*
    可以看到，在这里定义了一个条件 Bean，只有当这个 Bean 出现在 
    Spring 上下文环境中时，才会将 EurekaServerAutoConfiguration Bean 
    加载到 Spring 应用的上下文中
*/
@ConditionalOnBean(EurekaServerMarkerConfiguration.Marker.class)
@EnableConfigurationProperties({ EurekaDashboardProperties.class, InstanceRegistryProperties.class })
@PropertySource("classpath:/eureka/server.properties")
public class EurekaServerAutoConfiguration implements WebMvcConfigurer {
    // 省略具体的源代码
}
```

也就是说，只有 `Spring` 中存在 `EurekaServerMarkerConfiguration.Mark` 类型的 `Bean` 时，才会将 `EurekaServerAutoConfiguration` 自动配置类加载到 `Spring` 上下文中，从而实现 `Eureka` 注册中心的功能



## DashBoard Path

我们知道，当成功启动一个 `Spring Eureka` 注册中心时，访问对应的 `IP` 和端口即可看到对应的控制台界面，这里的访问路径是通过 `EurekaDashboardProperties` 来进行配置的。

查看 `EurekaDashboardProperties` 对应的源代码，如下所示：

```java
package org.springframework.cloud.netflix.eureka.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

/*
    这里通过配置属性的方式来进行 dashboard 访问路径的配置    
*/
@ConfigurationProperties("eureka.dashboard")
public class EurekaDashboardProperties {
    // 默认的 dashboard 访问路径
	private String path = "/";
    
    // 省略一部分不是特别重要的代码
}
```

这就是为什么直接访问注册中心的 `IP` 加端口号就能访问到控制台的原因

## `EnableDiscoveryClient` 注解

实际上，对于普通的应用服务来讲（为了和注册中心区分，将非注册中心的服务称为客户端），加上 `@EnableDiscoveryClient` 就能够实现服务的自动注册和发现，`@EnableDiscoveryClient` 注解对应的源代码如下所示：

```java
package org.springframework.cloud.client.discovery;

// 省略一部分不太重要的代码

public @interface EnableDiscoveryClient {
    /*
        可以看到，这里的默认值为 true，因此默认情况下，
        会将当前的服务注册到注册中心
    */
	boolean autoRegister() default true;
}
```

这是加上 `@EnableDiscoveryClient` 注解后的默认属性，不是

即使在启动类上没有加上 `@EnableDiscoveryClient` 注解，该服务依旧能够被注册中心发现，这是由于在 `Spring Eureka Client` 的自动配置类中默认了这种行为。

查看 `spring-cloud-starter-netflix-eureka-client` 包中的 `spring.factories` 文件，具体的内容如下所示：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
org.springframework.cloud.netflix.eureka.config.EurekaClientConfigServerAutoConfiguration,\
org.springframework.cloud.netflix.eureka.config.DiscoveryClientOptionalArgsConfiguration,\
org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration,\
org.springframework.cloud.netflix.eureka.EurekaDiscoveryClientConfiguration,\
org.springframework.cloud.netflix.eureka.reactive.EurekaReactiveDiscoveryClientConfiguration,\
org.springframework.cloud.netflix.eureka.loadbalancer.LoadBalancerEurekaAutoConfiguration

org.springframework.cloud.bootstrap.BootstrapConfiguration=\
org.springframework.cloud.netflix.eureka.config.EurekaConfigServerBootstrapConfiguration

org.springframework.boot.BootstrapRegistryInitializer=\
org.springframework.cloud.netflix.eureka.config.EurekaConfigServerBootstrapper

```

其中，`org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration` 自动配置类定义了这一默认的行为，具体的源代码如下所示：

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
@ConditionalOnClass(EurekaClientConfig.class)
@ConditionalOnProperty(value = "eureka.client.enabled", matchIfMissing = true)
@ConditionalOnDiscoveryEnabled
@AutoConfigureBefore({ CommonsClientAutoConfiguration.class, ServiceRegistryAutoConfiguration.class })
@AutoConfigureAfter(name = { "org.springframework.cloud.netflix.eureka.config.DiscoveryClientOptionalArgsConfiguration",
		"org.springframework.cloud.autoconfigure.RefreshAutoConfiguration",
        // 在这个自动配置类中定义了服务发现的默认行为
		"org.springframework.cloud.netflix.eureka.EurekaDiscoveryClientConfiguration",
		"org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationAutoConfiguration" })
public class EurekaClientAutoConfiguration {
    // 省略一部分不太重要的代码
}
```

继续查看 `EurekaDiscoveryClientConfiguration` 配置类的源代码，相关的内容如下所示：

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
@ConditionalOnClass(EurekaClientConfig.class)
// 重点在这里，可以看到，这里的默认值为 true，即默认允许被自动发现和注册
@ConditionalOnProperty(value = "eureka.client.enabled", matchIfMissing = true)
@ConditionalOnDiscoveryEnabled
@ConditionalOnBlockingDiscoveryEnabled
public class EurekaDiscoveryClientConfiguration {
    // 省略一部分不太重要的代码
}
```

## `EurekaServerAutoConfiguration` 解析

前文提到，在 `EurekaServerMarkerConfiguration.Marker` 类型的 `Bean` 装载到 `Spring` 上下文环境中时，才会加载 `EurekaServerAutoConfiguration`，这个配置类定义了有关 `Eureka` 服务端的相关配置，在这里进行简要的解析

### 相关属性介绍

`EurekaServerAutoConfiguration` 定义的相关属性如下：

```java
// 省略一部分包的导入以及注解的声明

public class EurekaServerAutoConfiguration implements WebMvcConfigurer {
    @Autowired
	private ApplicationInfoManager applicationInfoManager;

    /*
        Eureka 服务端相关的配置
    */
    @Autowired
	private EurekaServerConfig eurekaServerConfig;

    /*
        Eureka 客户端的相关配置
    */
    @Autowired
	private EurekaClientConfig eurekaClientConfig;        
    
    @Autowired
	private EurekaClient eurekaClient;

    @Autowired
	private InstanceRegistryProperties instanceRegistryProperties;
}  
```

### 服务启动

有关集群和应用服务程序的注册对应的源代码：

```java
// 省略一部分包的导入以及注解的声明

public class EurekaServerAutoConfiguration implements WebMvcConfigurer {
    // 省略部分源代码

    /*
        初始化集群注册表
    */
    @Bean
	public PeerAwareInstanceRegistry peerAwareInstanceRegistry(ServerCodecs serverCodecs) {
		this.eurekaClient.getApplications(); // force initialization
		return new InstanceRegistry(this.eurekaServerConfig, this.eurekaClientConfig, serverCodecs, this.eurekaClient,
				this.instanceRegistryProperties.getExpectedNumberOfClientsSendingRenews(),
				this.instanceRegistryProperties.getDefaultOpenForTrafficCount());
	}

    /*
        初始化集群节点集合
    */
	@Bean
	@ConditionalOnMissingBean
	public PeerEurekaNodes peerEurekaNodes(PeerAwareInstanceRegistry registry, ServerCodecs serverCodecs,
			ReplicationClientAdditionalFilters replicationClientAdditionalFilters) {
		return new RefreshablePeerEurekaNodes(registry, this.eurekaServerConfig, this.eurekaClientConfig, serverCodecs,
				this.applicationInfoManager, replicationClientAdditionalFilters);
	}

    /*
        Eureka 服务端上下文
    */
	@Bean
	@ConditionalOnMissingBean
	public EurekaServerContext eurekaServerContext(ServerCodecs serverCodecs, PeerAwareInstanceRegistry registry,
			PeerEurekaNodes peerEurekaNodes) {
		return new DefaultEurekaServerContext(this.eurekaServerConfig, serverCodecs, registry, peerEurekaNodes,
				this.applicationInfoManager);
	}

    /*
        Eureka 服务端启动类
    */
	@Bean
	public EurekaServerBootstrap eurekaServerBootstrap(PeerAwareInstanceRegistry registry,
			EurekaServerContext serverContext) {
		return new EurekaServerBootstrap(this.applicationInfoManager, this.eurekaClientConfig, this.eurekaServerConfig,
				registry, serverContext);
	}
}
```

具体的初始化类，可以看一下 `EurekaServerAutoConfiguration` 引入的注解：

```java
@Configuration(proxyBeanMethods = false)
/*
    可以看到，这里引入了 EurekaServerInitializerConfiguration 类型的 Bean，
    该 Bean 的作用是用于初始化 Eureka Server
*/
@Import(EurekaServerInitializerConfiguration.class)
@ConditionalOnBean(EurekaServerMarkerConfiguration.Marker.class)
@EnableConfigurationProperties({ EurekaDashboardProperties.class, InstanceRegistryProperties.class })
@PropertySource("classpath:/eureka/server.properties")
public class EurekaServerAutoConfiguration implements WebMvcConfigurer {
    // 省略一部分具体的代码
}
```

继续查看 `EurekaServerInitializerConfiguration` 对应的源代码，如下所示：

```java
// 省略一部分包的导入

@Configuration(proxyBeanMethods = false)
public class EurekaServerInitializerConfiguration 
    /*
        注意这里的 SmartLifecycle 接口，这个接口继承了 Lifecycle,
        用于管理 Spring Context 的生命周期
        
        具体到当前分析的环境，由于是初始化 Eureka Server，因此需要调用
        start() 方法
    */
    implements ServletContextAware, SmartLifecycle, Ordered {

	private static final Log log = LogFactory.getLog(EurekaServerInitializerConfiguration.class);

	@Autowired
	private EurekaServerConfig eurekaServerConfig;

	private ServletContext servletContext;

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private EurekaServerBootstrap eurekaServerBootstrap;

	private boolean running;

	private int order = 1;

	@Override
	public void setServletContext(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	@Override
	public void start() {
		new Thread(() -> {
			try {
				// TODO: is this class even needed now?
                /*
                    Eureka Server 启动之前的初始化
                */
				eurekaServerBootstrap.contextInitialized(EurekaServerInitializerConfiguration.this.servletContext);
				log.info("Started Eureka Server");

                /*
                    由于 Eureka Server 已经启动了，因此发布 Eureka Server 注册中心的启动事件
                */
				publish(new EurekaRegistryAvailableEvent(getEurekaServerConfig()));
                
                // 设置 Eureka 的运行状态
				EurekaServerInitializerConfiguration.this.running = true;
                
                // 现在 Eureka Server 已经完全启动了，此时再发布一个 Eureka Server 启动事件
				publish(new EurekaServerStartedEvent(getEurekaServerConfig()));
			} catch (Exception ex) {
				// Help!
				log.error("Could not initialize Eureka servlet context", ex);
			}
		}).start();
    }
    
    // 省略一部分代码
}
```

由于 `EurekaServerInitializerConfiguration` 实现了 `SmartLifecycle`，因此会被 `Spring` 容器进行相对应的处理，这里不做过多的介绍，有关 `Spring IOC` 部分的源码解析，可以查看 [Spring IOC 容器源码分析_Javadoop](https://javadoop.com/post/spring-ioc)，这是一篇关于 `Spring IOC` 源码分析比较好的文章。

在这里只需要知道由于 `EurekaServerInitializerConfiguration` 实现了 `SmartLifecycle`  接口，因此在 `Spring` 应用上下文的初始化过程中会调用 `start()` 方法即可

接下来继续分析 `contextInitialized(ServletContext)` 方法，该方法用于初始化启动 `Eureka Server` 之前的必要准备。对应的源代码如下所示：

```java
public void contextInitialized(ServletContext context) {
	try {
        /*
            这里的具体实现只是单纯地打印一行日志
        */
		initEurekaEnvironment();
        
        /*
            这里初始化 Eureka Server 的上下文
        */
		initEurekaServerContext();

		context.setAttribute(EurekaServerContext.class.getName(), this.serverContext);
	}
	catch (Throwable e) {
		log.error("Cannot bootstrap eureka server :", e);
		throw new RuntimeException("Cannot bootstrap eureka server :", e);
	}
}
```

比较关系的是 `Eureka Server` 上下文的设置，继续进入该方法，对应的源代码如下所示：

```java
protected void initEurekaServerContext() throws Exception {
	// For backward compatibility
	JsonXStream.getInstance().registerConverter(new V1AwareInstanceInfoConverter(), XStream.PRIORITY_VERY_HIGH);
	XmlXStream.getInstance().registerConverter(new V1AwareInstanceInfoConverter(), XStream.PRIORITY_VERY_HIGH);

    /*
        针对 AWS 的特殊处理，一般情况下可能用不到，略过
    */
	if (isAws(this.applicationInfoManager.getInfo())) {
	    this.awsBinder = new AwsBinderDelegate(this.eurekaServerConfig, this.eurekaClientConfig, this.registry,this.applicationInfoManager);
		this.awsBinder.start();
	}

	EurekaServerContextHolder.initialize(this.serverContext);

	log.info("Initialized server context");

	// Copy registry from neighboring eureka node
    /*
        邻接点的数据同步
    */
	int registryCount = this.registry.syncUp();
    
    /*
        服务剔除
    */
	this.registry.openForTraffic(this.applicationInfoManager, registryCount);

	// Register all monitoring statistics.
	EurekaMonitors.registerAllStats();
}
```

#### 邻节点的数据同步

邻接点的同步 `syncUp()`，对应的源代码如下所示：

```java
// 省略部分包的导入以及注解的声明

public class PeerAwareInstanceRegistryImpl 
    extends AbstractInstanceRegistry 
    implements PeerAwareInstanceRegistry {
    // 省略部分其它代码    

    @Override
    public int syncUp() {
        // Copy entire entry from neighboring DS node
        int count = 0;

        for (int i = 0; ((i < serverConfig.getRegistrySyncRetries()) && (count == 0)); i++) {
            if (i > 0) {
                try {
                    Thread.sleep(serverConfig.getRegistrySyncRetryWaitMs());
                } catch (InterruptedException e) {
                    logger.warn("Interrupted during registry transfer..");
                    break;
                }
            }
            /*
                apps 表示获取到的所有的应用信息
            */
            Applications apps = eurekaClient.getApplications();
            /*
                双层 Map 结构，第一层 Map 存储应用程序
            */
            for (Application app : apps.getRegisteredApplications()) {
                /*
                    第二层存储应用程序对应的实例信息
                */
                for (InstanceInfo instance : app.getInstances()) {
                    try {
                        /*
                            服务的注册实际执行的地方
                        */
                        if (isRegisterable(instance)) {
                            register(instance, instance.getLeaseInfo().getDurationInSecs(), true);
                            count++;
                        }
                    } catch (Throwable t) {
                        logger.error("During DS init copy", t);
                    }
                }
            }
        }
        return count;
    }
}
```

#### 服务剔除

服务剔除对应 `openForTraffic(ApplicationInfoManager, int)` 方法，实际对应的对象的源代码如下：

```java
// 省略部分包导入代码

public class PeerAwareInstanceRegistryImpl 
    extends AbstractInstanceRegistry 
    implements PeerAwareInstanceRegistry {
    // 省略部分关系不大的代码
    
    public void openForTraffic(ApplicationInfoManager applicationInfoManager, int count) {
        // Renewals happen every 30 seconds and for a minute it should be a factor of 2.
        this.expectedNumberOfClientsSendingRenews = count;
        updateRenewsPerMinThreshold();
        logger.info("Got {} instances from neighboring DS node", count);
        logger.info("Renew threshold is: {}", numberOfRenewsPerMinThreshold);
        this.startupTime = System.currentTimeMillis();
        if (count > 0) {
            this.peerInstancesTransferEmptyOnStartup = false;
        }
        DataCenterInfo.Name selfName = applicationInfoManager.getInfo().getDataCenterInfo().getName();
        
        /*
            这里也是针对 AWS 的处理，一般用不到，略过
        */
        boolean isAws = Name.Amazon == selfName;
        if (isAws && serverConfig.shouldPrimeAwsReplicaConnections()) {
            logger.info("Priming AWS connections for all replicas..");
            primeAwsReplicas(applicationInfoManager);
        }
        // 针对 AWS 的处理结束

        logger.info("Changing status to UP");
        applicationInfoManager.setInstanceStatus(InstanceStatus.UP);
        super.postInit();
    }   
}
```

继续查看父类的 `postInit()` 方法，对应的源代码如下所示：

```java
public abstract class AbstractInstanceRegistry 
    implements InstanceRegistry {
    // 省略部分不太重要的代码

    protected void postInit() {
        renewsLastMin.start();
        if (evictionTaskRef.get() != null) {
            evictionTaskRef.get().cancel();
        }
        // 这里添加一个剔除任务
        evictionTaskRef.set(new EvictionTask());
        
        // 设置这个剔除任务的执行周期
        evictionTimer.schedule(evictionTaskRef.get(),
                serverConfig.getEvictionIntervalTimerInMs(),
                serverConfig.getEvictionIntervalTimerInMs());
    }
}
```

这个剔除任务的定义对应的源代码如下所示：

```java
class EvictionTask extends TimerTask {
    @Override
    public void run() {
        try {
            long compensationTimeMs = getCompensationTimeMs();
            logger.info("Running the evict task with compensationTime {}ms", compensationTimeMs);
            evict(compensationTimeMs);
        } catch (Throwable e) {
            logger.error("Could not run the evict task", e);
        }
    }
}
```

`evict(int)` 对应的源代码比较长，这里就不再贴出。剔除工作交给 `internalCancel(String, String, boolean)` 方法来完成。

更加具体一点，`internalCancel(String, String, boolean)` 是按照实例 id 来删除双层 `Map` 中对应的元素来实现的。



<br />

参考：

<sup>[1]</sup>

https://mp.weixin.qq.com/s/Xfq5YCaSSc7WgOH6Yqz4zQhttps://mp.weixin.qq.com/s/Xfq5YCaSSc7WgOH6Yqz4zQ
