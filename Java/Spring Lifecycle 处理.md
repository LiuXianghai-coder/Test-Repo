# Spring Lifecycle 处理

## 前言

在 Spring 完成所有 `Bean` 的加载和实例化后，在 `finishRefresh` 中会启动所有 `Lifecycle` 类型的 `Bean`，这些 `Lifecycle` 类型的 `Bean` 在某些场景下可能会很有用（如消息监听、定时任务等）。注意这里说的 `Lifecycle` 不是指 `Bean` 的生命周期，而是一种特定类型的 `Bean`，对应的类型为 `org.springframework.context.Lifecycle`

## 实现原理

`org.springframework.context.Lifecycle` 所包含三个关键方法如下：

```java
public interface Lifecycle {
    // 启动这个 Bean    	
    void start();
    
    // 停止这个 Bean
    void stop();
    
    // 检查这个 Bean 是否已经被在运行
    boolean isRunning();
}
```

在大多数情况下，一般需求都只需要在 `Spring` 容器启动的时候就自动调用 `start` 方法启动这个 `Bean`，因此引入了 `org.springframework.context.SmartLifecycle` 的 `isAutoStartup` 来扩展这个自动启动的行为

对应注册启动的源码如下：

```java
// 当所有 Bean 被初始化完成后会调用这个方法
protected void finishRefresh() {
    clearResourceCaches();

    /*
    	检查当前 BeanFactory 中是否有 LifecycleProcessor 的实例，将其作为 Lifecycle 的管理容器
    	否则将创建默认 DefaultLifecycleProcessor 作为其 Lifecycle 的管理容器
    */
    initLifecycleProcessor();

    /*
    	通过查找到的 LifecycleProcessor 管理容器，启动这些 Lifecycle Bean
    */
    getLifecycleProcessor().onRefresh();
    
    // 一些不太重要的方法
}
```

对于 Spring Boot 一类的项目，由于会自动引入 `autoconfigure` 的依赖包，也会定义一个 `DefaultLifecycleProcessor` 类型的处理 `Bean`，因此在我们未手动进行干预的情况下，`LifecycleProcessor` 对应的处理 `Bean` 就是 `DefaultLifecycleProcessor`

对应的源码如下：

```java
public class DefaultLifecycleProcessor implements LifecycleProcessor, BeanFactoryAware {
    @Override
    public void onRefresh() {
        startBeans(true);
        this.running = true;
    }

    private void startBeans(boolean autoStartupOnly) {
        Map<String, Lifecycle> lifecycleBeans = getLifecycleBeans();
        Map<Integer, LifecycleGroup> phases = new TreeMap<>();

        lifecycleBeans.forEach((beanName, bean) -> {
            /*
            	这里结合前文提到的，对于大部分的需求都是默认直接启动即可，因此这里会进入到此 if 分支内
            	将自身添加到一个 LifecycleGroup 组中
            */
            if (!autoStartupOnly || (bean instanceof SmartLifecycle && ((SmartLifecycle) bean).isAutoStartup())) {
                int phase = getPhase(bean);
                phases.computeIfAbsent(
                    phase,
                    p -> new LifecycleGroup(phase, this.timeoutPerShutdownPhase, lifecycleBeans, autoStartupOnly)
                ).add(beanName, bean);
            }
        });
        if (!phases.isEmpty()) {
            phases.values().forEach(LifecycleGroup::start);
        }
    }

    private void doStart(Map<String, ? extends Lifecycle> lifecycleBeans, String beanName, boolean autoStartupOnly) {
        Lifecycle bean = lifecycleBeans.remove(beanName);
        if (bean != null && bean != this) {
            /*
            	如果存在依赖的 Lifecycle，则先启动依赖的 Lifecycle
            */
            String[] dependenciesForBean = getBeanFactory().getDependenciesForBean(beanName);
            for (String dependency : dependenciesForBean) {
                doStart(lifecycleBeans, dependency, autoStartupOnly);
            }
            if (!bean.isRunning() &&
                (!autoStartupOnly || !(bean instanceof SmartLifecycle) || ((SmartLifecycle) bean).isAutoStartup())) {
                try {
                    // 实际的 Lifecycle 启动
                    bean.start();
                }
                catch (Throwable ex) {
                    throw new ApplicationContextException("Failed to start bean '" + beanName + "'", ex);
                }
            }
        }
    }
}

/*
	这个类的目的是给不同阶段的 Lifecycle Bean 进行封装
	以使得这些 Lifecycle 能按照 Phase 给定的顺序依次启动

	这部分对 Lifecycle 来讲不是特别重要，只需要知道最终会调用 Lifecycle 的 start 方法即可
*/
private class LifecycleGroup {
    private final int phase;

    private final long timeout;

    private final Map<String, ? extends Lifecycle> lifecycleBeans;

    private final boolean autoStartupOnly;

    private final List<LifecycleGroupMember> members = new ArrayList<>();

    private int smartMemberCount;

    public LifecycleGroup(
        int phase, long timeout, Map<String, ? extends Lifecycle> lifecycleBeans, boolean autoStartupOnly) {

        this.phase = phase;
        this.timeout = timeout;
        this.lifecycleBeans = lifecycleBeans;
        this.autoStartupOnly = autoStartupOnly;
    }

    public void add(String name, Lifecycle bean) {
        this.members.add(new LifecycleGroupMember(name, bean));
        if (bean instanceof SmartLifecycle) {
            this.smartMemberCount++;
        }
    }

    public void start() {
        if (this.members.isEmpty()) {
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Starting beans in phase " + this.phase);
        }
        Collections.sort(this.members);
        for (LifecycleGroupMember member : this.members) {
            /*
            	调用 DefaultLifecycleProcessor 的 start 方法，启动 Lifecyle
            */
            doStart(this.lifecycleBeans, member.name, this.autoStartupOnly);
        }
    }
}
```

## 实际使用

一个比较经典的使用场景是 `spring-cloud-stream` 的使用。假设现在有一个 `SAAS` 平台，其中有部分业务是从第三方客户端拉取所需的业务数据， 对其进行处理后返回给客户端，我们将业务数据处理定义为 `Handler-A`，客户端数据的拉取以及响应定义为 `Handler-B`，由于 `Handler-A` 属于计算密集型操作、`Handler-B` 属于 IO 密集型操作，在实际使用中发现 `Handler-A` 的处理速度远大于 `Handler-B` 的处理速度。为了提高系统的利用率和减少所需的实例数，我们将 `Handler-A` 提取到 `Service-A` 作为单独的一套服务，将 `Handler-B` 作为 `Service-B` 单独部署。根据上文提到的场景，很明显属于需要 “削峰填谷” 的场景。在 `Service-A` 和 `Service-B` 之间的消息通信很适合使用消息队列的方式来进行通信

可问题也随之而来，在部署时由于数据格式的原因，以及为了提高更可靠的服务，在通信时需要对上游调用隐藏调用的 `MQ` 类型，并且需要支持不同类型的 `MQ` 的上线与下线。为了实现这一目的，可以通过引入 `spring-cloud-stream` 来抽象消息的发送与接收

`spring-cloud-stream` 提供了 `BindingService` 来解决这一问题，在 `application.yml` 中定义如下绑定关系：

```yaml
spring:
  cloud:
    stream:
      bindings:
        # input 和 output 为消息队列的名称
        input:
          destination: my-topic # 消费的主题名称
          group: my-consumer-group-1 # 消费者组名称
          content-type: application/json # 消息内容类型（例如JSON）
          consumer:
            concurrency: 1
        output:
          destination: my-topic # 发送的主题名称
          group: my-producer-group # 发送的主题名称
          content-type: application/json
      rocketmq:
        default:
          consumer:
            pullThresholdForQueue: 1
            pullThresholdSizeForQueue: 1
            pullBatchSize: 1
            pull:
              pullThreadNums: 1
              pullThresholdForAll: 1
        binder:
          name-server: 127.0.0.1:9876 # RocketMQ NameServer 地址，确保是可用的
          producer-group: my-producer-group # 生产者组名称
          consumer-group: my-consumer-group # 消费者组名称
          instance-name: my-instance # 可选：RocketMQ实例名称
          group: my-group

      default:
        content-type: application/json # 默认的消息类型
      default-binder: rocketmq
```

然后，定义对 `input` 和 `output` 的流处理通道：

``` java
public interface StreamProcessor {

    String INPUT = "input";
    String OUTPUT = "output";

    @Input(INPUT)
    MessageChannel input();

    @Output(OUTPUT)
    MessageChannel output();
}
```

引入定义的流处理通道，对发送的消息进行监听，以及对消息的发送：

```java
@Service
@EnableBinding(StreamProcessor.class)
public class BizService {

    private final static Logger logger = LoggerFactory.getLogger(BizService.class);

    @Resource
    private StreamProcessor streamProcessor;

    // 监听收到的消息
    @StreamListener(StreamProcessor.INPUT)
    public void handleMessage(@Payload String message) {
        logger.info("Received message length: {}", message.length());
        logger.info("msg consumer finished.......");
    }

    // 向 output 发送消息
    public void sendMessage(String message) {
        streamProcessor.output().send(MessageBuilder.withPayload(message).build());
    }
}
```

后续需要切换 `MQ` 时，只需要配置对应的 `application.yml` 即可，无需再重写消息的发送与接收处理

### Binder 实现原理

和大多数 `Spring Boot Starter` 类似，引入 `spring-cloud-stream` 依赖时，通过 `META-INF/spring.factories` 文件加载到对应的自动配置类 `org.springframework.cloud.stream.config.BindingServiceConfiguration`，可以看到定义了几个关键的 `Bean`：

```java
@Configuration
@EnableConfigurationProperties({ BindingServiceProperties.class,
		SpringIntegrationProperties.class, StreamFunctionProperties.class })
@Import({ DestinationPublishingMetricsAutoConfiguration.class,
		SpelExpressionConverterConfiguration.class })
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@ConditionalOnBean(value = BinderTypeRegistry.class, search = SearchStrategy.CURRENT)
public class BindingServiceConfiguration {
    
    @Bean
	@ConditionalOnMissingBean(BinderFactory.class)
	public BinderFactory binderFactory(BinderTypeRegistry binderTypeRegistry,
			BindingServiceProperties bindingServiceProperties) {

		DefaultBinderFactory binderFactory = new DefaultBinderFactory(
				getBinderConfigurations(binderTypeRegistry, bindingServiceProperties),
				binderTypeRegistry);
		binderFactory.setDefaultBinder(bindingServiceProperties.getDefaultBinder());
		binderFactory.setListeners(this.binderFactoryListeners);
		return binderFactory;
	}
    
    
    @Bean
	@ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
	public BindingService bindingService(
			BindingServiceProperties bindingServiceProperties,
			BinderFactory binderFactory, TaskScheduler taskScheduler) {

		return new BindingService(bindingServiceProperties, binderFactory, taskScheduler);
	}

    /*
    	输出流的 Lifecycle Bean
    */
	@Bean
	@DependsOn("bindingService")
	public OutputBindingLifecycle outputBindingLifecycle(BindingService bindingService,
			Map<String, Bindable> bindables) {

		return new OutputBindingLifecycle(bindingService, bindables);
	}

    /*
    	输入流的 Lifecycle Bean
    */
	@Bean
	@DependsOn("bindingService")
	public InputBindingLifecycle inputBindingLifecycle(BindingService bindingService,
			Map<String, Bindable> bindables) {
		return new InputBindingLifecycle(bindingService, bindables);
	}
}
```

这里比较关键的一点是 `BinderTypeRegistry` 的定义，会通过扫描依赖项下所有的 `META_INF/spring.biners` 对应类型的配置类，例如，对于 `RocketMQ` 来讲，它的配置是这样的：

```txt
rocketmq:com.alibaba.cloud.stream.binder.rocketmq.autoconfigurate.RocketMQBinderAutoConfiguration
```

在扫描时会加载 `rocketmq` 类型的 Binder 对应的自动配置类，在配置类中定义了实际对于消息监听和发送的具体处理细节，这也就是为什么 `spring-cloud-stream` 可以**实现不同类型的 `MQ`之间的切换的原因**

对于 `BinderTypeRegistry` 定义的源码如下：

```java
@Bean
public BinderTypeRegistry binderTypeRegistry(
    ConfigurableApplicationContext configurableApplicationContext) {
    Map<String, BinderType> binderTypes = new HashMap<>();
    ClassLoader classLoader = configurableApplicationContext.getClassLoader();
    try {
        /*
        	加载 META-INF/spring.binders 的文件，准备将他们进行读取和解析
        */
        Enumeration<URL> resources = classLoader.getResources("META-INF/spring.binders");
        // see if test binder is available on the classpath and if so add it to the binderTypes
        try {
            BinderType bt = new BinderType("integration", new Class[] {
                classLoader.loadClass("org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration")});
            binderTypes.put("integration", bt);
        }
        catch (Exception e) {
            // ignore. means test binder is not available
        }

        if (binderTypes.isEmpty() && !Boolean.valueOf(this.selfContained)
            && (resources == null || !resources.hasMoreElements())) {
            this.logger.debug(
                "Failed to locate 'META-INF/spring.binders' resources on the classpath."
                + " Assuming standard boot 'META-INF/spring.factories' configuration is used");
        }
        else {
            // 绑定 bindeType ——> AutoConfiguration
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                UrlResource resource = new UrlResource(url);
                for (BinderType binderType : parseBinderConfigurations(classLoader, resource)) {
                    binderTypes.put(binderType.getDefaultName(), binderType);
                }
            }
        }

    }
    catch (IOException | ClassNotFoundException e) {
        throw new BeanCreationException("Cannot create binder factory:", e);
    }
    return new DefaultBinderTypeRegistry(binderTypes);
}
```

对于 `OutputBindingLifecycle` 和 `InputBindingLifecycle` 来讲，它们的类型都是 `AbstractBindingLifecycle`，对 `start` 的实现如下：

```java
public void start() {
    if (!this.running) {
        if (this.context != null) {
            this.bindables.putAll(context.getBeansOfType(Bindable.class));
        }

        this.bindables.values().forEach(this::doStartWithBindable);
        this.running = true;
    }
}
```

以 `InputBindingLifecycle` 的实现为例，对 `doStartWithBindable` 的实现如下：

```java
/*
	这里需要注意的时，在 @EnableBinding 中导入 StreamProcessor 时，
	会创建一个类型为 BindableProxyFactory 的 Bindable Bean
*/
@Override
void doStartWithBindable(Bindable bindable) {
    Collection<Binding<Object>> bindableBindings = bindable
        .createAndBindInputs(this.bindingService);
    if (!CollectionUtils.isEmpty(bindableBindings)) {
        this.inputBindings.addAll(bindableBindings);
    }
}
```

对应 `BindableProxyFactory` 的 `createAndBindInputs` 如下：

```java
@Override
public Collection<Binding<Object>> createAndBindInputs(
    BindingService bindingService) {
    List<Binding<Object>> bindings = new ArrayList<>();
    /*
    	this.inputHolders 会在初始化的时候扫描 @Input 注解进行初始化
    */
    for (Map.Entry<String, BoundTargetHolder> boundTargetHolderEntry : this.inputHolders
         .entrySet()) {
        String inputTargetName = boundTargetHolderEntry.getKey();
        BoundTargetHolder boundTargetHolder = boundTargetHolderEntry.getValue();
        if (boundTargetHolder.isBindable()) {
            /*
            	这里 Binding 的创建最终委托给 BindingService 进行创建，这部分的代码较长，
            	只需要知道返回的是 spring.binders 配置下当前绑定的 binder 类型对应的 Binding 即可
            	
            	除此之外，这里的 bindConsumer 在创建时也会实际开启对队列的监听，至此完成监听的工作
            */
            bindings.addAll(bindingService.bindConsumer(
                boundTargetHolder.getBoundTarget(), inputTargetName));
        }
    }
    return bindings;
}
```

对于 `Input` 类型的 Binding 的创建，监听的实际调用链路为 `BindingService.doBindConsumer` —>  `Binder.bindConsumer` —> `AbstractBinder.bindConsumer` —> `AbstractMessageChannelBinder.doBindConsumer` —> `MessageProducer.satrt`

跳过前面不太重要的部分，关心的是实际调用中对监听的开启，在 `AbstractMessageChannelBinder` 中的定义如下：

```java
public final Binding<MessageChannel> doBindConsumer(String name, String group,
                                                    MessageChannel inputChannel, final C properties) throws BinderException {
    MessageProducer consumerEndpoint = null;
    try {
        ConsumerDestination destination = this.provisioningProvider
            .provisionConsumerDestination(name, group, properties);

        if (HeaderMode.embeddedHeaders.equals(properties.getHeaderMode())) {
            enhanceMessageChannel(inputChannel);
        }
        /*
        	这里是一个模板方法，对于不同的 MQ， 具体的实现类创建特定于自身的 MessageProducer，以开启实际的消息监听处理
        */
        consumerEndpoint = createConsumerEndpoint(destination, group, properties);
        consumerEndpoint.setOutputChannel(inputChannel);
        this.consumerCustomizer.configure(consumerEndpoint, name, group);
        if (consumerEndpoint instanceof InitializingBean) {
            /*
            	监听对象的初始化，这部分由具体的实现决定，如 RocketMQ 会创建对应的 Consumer
            */
            ((InitializingBean) consumerEndpoint).afterPropertiesSet();
        }
        if (properties.isAutoStartup() && consumerEndpoint instanceof Lifecycle) {
            /*
            	开启监听线程，监听可能会收到的消息
            */
            ((Lifecycle) consumerEndpoint).start();
        }
        
        // 省略一些不太重要的方法
        doPublishEvent(new BindingCreatedEvent(binding));
        return binding;
    }
    catch (Exception e) {
        // 省略一些异常处理代码
    }
}
```

