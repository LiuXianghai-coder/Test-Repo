# Spring 多线程的事务处理

## 问题起因

Spring 的 `JDBC` 相关的依赖库已经提供了对 `JDBC` 类事务处理的统一解决方案，在正常情况下，我们只需要在需要添加事务的业务处理方法上加上 `@Transactional` 注解即可开启声明式的事务处理。这种方式在单线程的处理模式下都是可行的，这是因为 Spring 在对 `@Transactional` 注解的切面处理上通过一些 `ThreaLocal` 变量来绑定了事务的相关信息，因此在单线程的模式下能够很好地工作。

然而，由于与数据库的交互属于 IO 密集型的操作，在某些情况下，与数据库的交互次数可能会成为性能的瓶颈。在这种情况下，一种可行的优化方式便是通过多线程的方式来并行化与数据库的交互，因为在大部分的情况下，数据的查询之间属于相互独立的任务，因此使用多线程的方式来解决这一类性能问题在概念上来讲是可行的

如果通过多线程的方式来优化，随之而来的一个问题就是对于事务的处理，可能客户端希望在一个任务出现异常时就回滚整个事务，就像使用 `@Transactional` 注解的效果一样。但是很遗憾，在这种情况下，我们不能直接使用 `@Transactional` 来开启事务，因为多线程的处理导致预先绑定的事务信息无法被找到，因此需要寻找其它的解决方案

## Spring 事务源码分析

本文将仅分析声明式的事务处理方式，同时不会分析有关切面的具体加载逻辑，因为我们的目标是通过分析源码来找到在多线程下事务的可行解决方案

`@Transaction` 对应的切面处理是在 `org.springframework.transaction.interceptor.TransactionAspectSupport#invokeWithinTransaction` 方法中处理的，由于大部分情况下我们使用的都是声明式的阻塞式事务处理形式，因此我们也只关心这部分的处理逻辑：

``` java
protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
                                         final InvocationCallback invocation) throws Throwable {
    
    /*
    	获取事务的相关属性，如需要回滚的异常，事务的传播方式等
    */
    TransactionAttributeSource tas = getTransactionAttributeSource();
    final TransactionAttribute txAttr = (tas != null ? tas.getTransactionAttribute(method, targetClass) : null);
    
    /*
    	根据当前所处的环境，决定具体要采用何种事务管理对象类型，对于 JDBC 事务来说，
    	该类型为 DataSourceTransactionManager
    */
    final TransactionManager tm = determineTransactionManager(txAttr);
    
    // 省略响应式事务的处理逻辑。。。。。。。

    PlatformTransactionManager ptm = asPlatformTransactionManager(tm);
    final String joinpointIdentification = methodIdentification(method, targetClass, txAttr);

    if (txAttr == null || !(ptm instanceof CallbackPreferringPlatformTransactionManager)) {
        // 检查是否需要开启事务，这里是我们重点分析的一个地方
        TransactionInfo txInfo = createTransactionIfNecessary(ptm, txAttr, joinpointIdentification);

        Object retVal;
        try {
            // 实际业务方法的处理
            retVal = invocation.proceedWithInvocation();
        }
        catch (Throwable ex) {
            // 事务的回滚处理
            completeTransactionAfterThrowing(txInfo, ex);
            throw ex;
        }
        finally {
            cleanupTransactionInfo(txInfo);
        }
        // 省略部分代码。。。。。
        
        // 提交事务
        commitTransactionAfterReturning(txInfo);
        return retVal;
    }
    // 省略编程式的事务处理逻辑 。。。。。
}
```

接下来我们具体分析一下对于创建事务的有关处理：

``` java
protected TransactionInfo 
    createTransactionIfNecessary(@Nullable PlatformTransactionManager tm,
                                 @Nullable TransactionAttribute txAttr, 
                                 final String joinpointIdentification) {
    
    TransactionStatus status = null;
    if (txAttr != null) {
        if (tm != null) {
            // 实际创建事务的处理
            status = tm.getTransaction(txAttr);
        }
        else {
            // 省略部分代码
        }
    }
    // 针对事务的准备工作，目的是为了将事务信息绑定到当前的线程
    return prepareTransactionInfo(tm, txAttr, joinpointIdentification, status);
}
```

通过上面的分析，我们可以知道当前事务传里对象为 `DataSourceTransactionManager`，我们继续查看它对创建事务的处理过程：

``` java
public final TransactionStatus 
    getTransaction(@Nullable TransactionDefinition definition)
    throws TransactionException {

    // Use defaults if no transaction definition given.
    TransactionDefinition def = (definition != null ? definition : TransactionDefinition.withDefaults());

    // 获取与当前线程绑定的事务信息
    Object transaction = doGetTransaction();
    boolean debugEnabled = logger.isDebugEnabled();

    /*
    	检查当前执行过程中是否已经存在事务，如果存在，那么需要通过对 TransactionDefinition 的 propagationBehavior
    	来对当前的执行做出合适的事务处理形式
    */
    if (isExistingTransaction(transaction)) {
        // Existing transaction found -> check propagation behavior to find out how to behave.
        return handleExistingTransaction(def, transaction, debugEnabled);
    }

    // Check definition settings for new transaction.
    if (def.getTimeout() < TransactionDefinition.TIMEOUT_DEFAULT) {
        throw new InvalidTimeoutException("Invalid transaction timeout", def.getTimeout());
    }

    // No existing transaction found -> check propagation behavior to find out how to proceed.
    if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_MANDATORY) {
        throw new IllegalTransactionStateException(
            "No existing transaction found for transaction marked with propagation 'mandatory'");
    }
    else if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED ||
             def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW ||
             def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {
        SuspendedResourcesHolder suspendedResources = suspend(null);
        if (debugEnabled) {
            logger.debug("Creating new transaction with name [" + def.getName() + "]: " + def);
        }
        try {
            /*
            	由于没有检测到事务，因此需要开启一个事务来支持事务的有关操作
            */
            return startTransaction(def, transaction, debugEnabled, suspendedResources);
        }
        catch (RuntimeException | Error ex) {
            resume(null, suspendedResources);
            throw ex;
        }
    }
    else {
        boolean newSynchronization = (getTransactionSynchronization() == SYNCHRONIZATION_ALWAYS);
        return prepareTransactionStatus(def, null, true, newSynchronization, debugEnabled, null);
    }
}
```

继续分析获取事务的代码，可以看到实际上最终是通过 `TransactionSynchronizationManager` 来检查绑定的事务信息的

``` java
// 获取当前绑定的事务
protected Object doGetTransaction() {
    DataSourceTransactionObject txObject = new DataSourceTransactionObject();
    txObject.setSavepointAllowed(isNestedTransactionAllowed());
    /*
    	获取与当前线程绑定的事务信息
    */
    ConnectionHolder conHolder =
        (ConnectionHolder) TransactionSynchronizationManager.getResource(obtainDataSource());
    txObject.setConnectionHolder(conHolder, false);
    return txObject;
}

/*
	检查当前线程持有的事务是否是有效的，如果在 doGetTransaction 方法中 TransactionSynchronizationManager
	中能够检测到对应的 ConnectionHolder 并且是活跃的，则说明确实在方法执行前就已经存了事务
*/
protected boolean isExistingTransaction(Object transaction) {
    DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
    return (txObject.hasConnectionHolder() && txObject.getConnectionHolder().isTransactionActive());
}

// JdbcTransactionObjectSupport
public boolean hasConnectionHolder() {
    return (this.connectionHolder != null);
}
```

如果当前已经存在了事务，需要针对事务传播行为对当前的事务做出对应的行为：

``` java
private TransactionStatus handleExistingTransaction(
    TransactionDefinition definition, 
    Object transaction, 
    boolean debugEnabled)
    throws TransactionException {
    /*
    	由于默认的是传播行为是 PROPAGATION_REQUIRED，即默认将当前的处理加入到当前已经存在的事务中，
    	我们主要关心这部分内容，因此省略了其它传播行为的处理
    */
    boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);
    return prepareTransactionStatus(definition, transaction, false, newSynchronization, debugEnabled, null);
}
```

继续查看对于当前事务的处理：

```java
// 简单地标记一下当前的事务状态
protected final DefaultTransactionStatus prepareTransactionStatus(
    TransactionDefinition definition, @Nullable Object transaction, boolean newTransaction,
    boolean newSynchronization, boolean debug, @Nullable Object suspendedResources) {
    
    DefaultTransactionStatus status = newTransactionStatus(
        definition, transaction, newTransaction, newSynchronization, debug, suspendedResources);
    prepareSynchronization(status, definition);
    return status;
}

// TransactionSynchronizationManager 线程绑定对象的部分处理，不是特别重要
protected void prepareSynchronization(DefaultTransactionStatus status, 
                                      TransactionDefinition definition) {
    if (status.isNewSynchronization()) {
        TransactionSynchronizationManager.setActualTransactionActive(status.hasTransaction());
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(
            definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT ?
            definition.getIsolationLevel() : null);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(definition.isReadOnly());
        TransactionSynchronizationManager.setCurrentTransactionName(definition.getName());
        TransactionSynchronizationManager.initSynchronization();
    }
}
```

接下来我们继续查看对于启动事务时的有关处理：

``` java
// 开启一个新的事务
private TransactionStatus startTransaction(TransactionDefinition definition, 
                                           Object transaction,
                                           boolean debugEnabled, 
                                           @Nullable SuspendedResourcesHolder suspendedResources) {
    boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);
    DefaultTransactionStatus status = newTransactionStatus(
        definition, transaction, true, newSynchronization, debugEnabled, suspendedResources);
    doBegin(transaction, definition);
    prepareSynchronization(status, definition);
    return status;
}

// 相当于开启事务的 BEGIN 语句
protected void doBegin(Object transaction, TransactionDefinition definition) {
    /*
    	由上文的 doGetTransaction 方法可知当前的 transaction 一定为 DataSourceTransactionObject 类型
    */
    DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
    Connection con = null;

    try {
        /*
        	由于 TransactionSynchronizationManager.getResource(obtainDataSource()) 可能为 null，
        	因此需要对这种情况进行处理，避免为 null
        */
        if (!txObject.hasConnectionHolder() ||
            txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
            Connection newCon = obtainDataSource().getConnection();
            if (logger.isDebugEnabled()) {
                logger.debug("Acquired Connection [" + newCon + "] for JDBC transaction");
            }
            txObject.setConnectionHolder(new ConnectionHolder(newCon), true);
        }

        // 省略一些不是特别重要的数据库连接和准备的相关代码

        /*
        	到这里就比较关键了，结合上文 doGetTransaction 方法可知，这两者之间是对应的，只要 TransactionSynchronizationManager 绑定的资源对象一致，那么就能够检测到当前执行的事务信息
        */
        if (txObject.isNewConnectionHolder()) {
            TransactionSynchronizationManager.bindResource(obtainDataSource(), txObject.getConnectionHolder());
        }
    }

    catch (Throwable ex) {
        if (txObject.isNewConnectionHolder()) {
            DataSourceUtils.releaseConnection(con, obtainDataSource());
            txObject.setConnectionHolder(null, false);
        }
        throw new CannotCreateTransactionException("Could not open JDBC Connection for transaction", ex);
    }
}
```

通过对 `doBegin` 和 `doGetTransaction` 方法的分析，我们可以知道，只要 `TransactionSynchronizationManager#getResource(Object)` 能够拿到同一个线程绑定的资源对象，那么我们就可以将这两个操作合并到一个事务中，并且能够直接复用 Spring 现有的事务处理逻辑！

## 具体解决方案

通过对 Spring 事务处理的源码分析，我们可以知道，如果希望当前线程执行上下文能够检测到事务的存在，我们只要通过 `TransactionSynchronizationManager` 绑定一致的事务资源对象即可。为了方便，我们可以直接将线程执行的任务封装到一个新的任务类中，在这个任务类中绑定相关的事务资源即可，对应的任务类如下：

``` java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * 用于 {@link DataSourceTransactionManager} 事务管理对应的任务线程，这个类的存在是为了使得
 * Spring 中的事务管理能够在多线程的环境下依旧能够有效地工作.
 * 对于要要执行的任务，可以将其封装成此任务对象，该任务对象在执行时将会绑定与 {@link  DataSourceTransactionManager#getResourceFactory()}
 * 对应的 {@link TransactionSynchronizationManager#getResourceMap()} 中关联的事务对象，以使得要执行的任务包含在已有的事务中
 * （至少能保证存在一种可行的方式能够得到父线程的所处事务上下文），从而使得当前待执行的任务能够被现有统领事务进行管理
 *
 * @see DataSourceTransactionManager
 * @see TransactionSynchronizationManager
 *@author lxh
 */
public class DataSourceTransactionTask
    implements Callable<TransactionStatus> {

    private final static Logger log = LoggerFactory.getLogger(DataSourceTransactionTask.class);

    /*
        与 TransactionSynchronizationManager.resources 关联的事务属性对象的 Value 值，
        在当前上下文中，为了保存与原有事务的完整性，这里的 resource 存储的是 DataSourceTransactionObject
     */
    private final Object resource;

    // 当前 Spring 平台的事务管理对象
    private final DataSourceTransactionManager txManager;

    // 实际需要运行的任务
    private final Runnable runnable;

    // 与事务有关的描述信息
    private final TransactionDefinition definition;

    public DataSourceTransactionTask(Object resource,
                                     DataSourceTransactionManager txManager,
                                     Runnable runnable,
                                     TransactionDefinition definition) {
        this.resource = resource;
        this.txManager = txManager;
        this.runnable = runnable;
        this.definition = definition;
    }

    @Override
    public TransactionStatus call() {
        // 通过源码分析可知，对于 JDBC 事务的处理，key 为对应的 DataSource 对象
        Object key = txManager.getResourceFactory();
        /* 
        	resource 是在启动这个线程之前就已经被主线程开启的事务对象，
        	我们可以知道它实际上就是 DataSourceTransactionObject，我们将他绑定到
        	当前线程，即可使得当前线程能够感知到这个事务的存在
        */
        TransactionSynchronizationManager.bindResource(key, resource);
        TransactionStatus status = txManager.getTransaction(definition);
        try {
            runnable.run();
        } catch (Throwable t) {
            log.debug("任务执行出现异常", t);
            status.setRollbackOnly(); // 出现了异常，需要将整个事务进行回滚
        } finally {
            // 移除与当前线程执行的关联关系，避免任务执行过程中的资源混乱
            TransactionSynchronizationManager.unbindResource(key);
        }
        return status;
    }
}
```

更进一步，我们可以使用线程池来进一步封装，从而避免自己手动创建线程或者其它的线程管理容器：

``` java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 用于需要执行并行任务的事务管理线程池，由于现有的 Spring 声明式事务在不同的线程中不能很好地工作，
 * 而在某些情况下，可能需要考虑使用多个处理器的优势来提高方法的执行性能，因此定义了此类来提供一种类似
 * 线程池的方式来执行可以并行执行的任务，并对将这些任务整合到 Spring 的事务管理中 <br />
 * 具体的使用方式:
 * <ol>
 *     <li>
 *         如果你希望自己配置线程池的相关属性，可以手动创建一个 {@link ThreadPoolExecutor} 来作为构造参数通过
 *         {@link DataSourceTransactionExecutor#DataSourceTransactionExecutor(ThreadPoolExecutor,
 *         DataSourceTransactionManager, TransactionDefinition)} 进行构建，在构造时会复制这个 {@link ThreadPoolExecutor}
 *         的相关属性来重新创建一个 {@link ThreadPoolExecutor} 进行任务的处理，因此不会影响到现有线程池的工作. 同时，值得注意的是，
 *         对于传入的 {@link ThreadPoolExecutor} 参数中的 {@link ThreadPoolExecutor#getQueue()} 工作队列类型，必须保证提供一个
 *         无参的构造函数来使得工作队列对象能够被重新创建，否则将会抛出异常 <br />
 *         此外，除了任务执行者的参数外，至少还需要指定 {@link DataSourceTransactionManager} 用于任务执行时线程事务的统一管理，使得每个任务
 *         执行时能够被 Spring 事务进行管理，由于 Spring 提供了对于事务的不同处理方式，因此也可以自定义传入 {@link TransactionDefinition}
 *         来定义这些行为
 *     </li>
 *     <li>
 *         对于需要执行的任务，只要将其作为 {@link Runnable} 参数通过 {@link #addTask(Runnable)} 的形式加入到当前的任务列表中，
 *         在这个过程中实际的任务不会被执行.
 *         当确定已经将任务加入完成后，通过调用 {@link #execute()} 方法来执行这些任务，这些任务的执行会被构造时传入的
 *         {@link DataSourceTransactionManager} 进行统一的事务管理，同时，任务执行完毕之后，当前的任务执行者将会被关闭，
 *         并不能再继续添加任务
 *     </li>
 *     以下面的例子为例，我们可以执行如下的几个业务处理:
 *     <pre>
 *         DataSourceTransactionExecutor executor = new DataSourceTransactionExecutor(txManager);
 *         executor.addTask(this::service1);
 *         executor.addTask(this::service2);
 *         executor.addTask(this::service3);
 *         executor.execute();
 *     </pre>
 * </ol>
 *
 * @see DataSourceTransactionTask
 * @see DataSourceTransactionManager
 *@author lxh
 */
public class DataSourceTransactionExecutor {

    private final static Logger log = LoggerFactory.getLogger(DataSourceTransactionExecutor.class);

    private final List<Callable<TransactionStatus>> callableList = new ArrayList<>();

    private final DataSourceTransactionManager txManager;

    private final TransactionStatus txStatus;

    private final Object txResource;

    private final ThreadPoolExecutor executor;

    public DataSourceTransactionExecutor(int coreSize,
                                         int maxSize,
                                         int keepTime,
                                         TimeUnit timeUnit,
                                         BlockingQueue<Runnable> workQueue,
                                         ThreadFactory threadFactory,
                                         RejectedExecutionHandler rejectHandler,
                                         DataSourceTransactionManager txManager,
                                         TransactionDefinition definition) {
        this.txManager = txManager;
        this.txStatus = txManager.getTransaction(definition);
        this.txResource = TransactionSynchronizationManager.getResource(txManager.getResourceFactory());
        executor = new ThreadPoolExecutor(coreSize, maxSize, keepTime,
                                          timeUnit, workQueue, threadFactory, rejectHandler);
    }

    public DataSourceTransactionExecutor(DataSourceTransactionManager txManager,
                                         TransactionDefinition definition) {
        this(Runtime.getRuntime().availableProcessors() * 2,
             Runtime.getRuntime().availableProcessors() * 2,
             60,
             TimeUnit.SECONDS,
             new LinkedBlockingDeque<>(),
             Thread::new,
             new ThreadPoolExecutor.AbortPolicy(),
             txManager,
             definition
            );
    }

    @SuppressWarnings("unchecked")
    public DataSourceTransactionExecutor(ThreadPoolExecutor executor,
                                         DataSourceTransactionManager txManager,
                                         TransactionDefinition definition) {
        // 复制一个线程池对象，避免一些线程问题
        this.executor = new ThreadPoolExecutor(
            executor.getCorePoolSize(),
            executor.getMaximumPoolSize(),
            executor.getKeepAliveTime(TimeUnit.SECONDS),
            TimeUnit.SECONDS,
            ReflectTool.createInstance(executor.getQueue().getClass()),
            executor.getThreadFactory(),
            ReflectTool.createInstance(executor.getRejectedExecutionHandler().getClass())
        );
        this.txManager = txManager;
        this.txStatus = txManager.getTransaction(definition);
        this.txResource = TransactionSynchronizationManager.getResource(txManager.getResourceFactory());
    }

    public DataSourceTransactionExecutor(DataSourceTransactionManager txManager) {
        this(txManager, new DefaultTransactionDefinition());
    }

    public void addTask(Runnable task) {
        callableList.add(DataSourceTransactionTask.Builder.aTask()
                         .runnable(task).txManager(txManager)
                         .resource(txResource).definition(new DefaultTransactionDefinition())
                         .build()
                        );
    }

    public void addTask(Runnable task, TransactionDefinition def) {
        callableList.add(DataSourceTransactionTask.Builder.aTask()
                         .runnable(task).txManager(txManager)
                         .resource(txResource).definition(def)
                         .build()
                        );
    }

    public void execute() throws InterruptedException {
        List<Future<TransactionStatus>> futures = new ArrayList<>();
        for (Callable<TransactionStatus> callable : callableList) {
            futures.add(executor.submit(callable));
        }
        executor.shutdown();
        List<TransactionStatus> statusList = new ArrayList<>();
        for (Future<TransactionStatus> future : futures) {
            try {
                statusList.add(future.get());
            } catch (ExecutionException e) {
                log.error("任务执行出现异常", e);
                statusList.add(null);
            }
        }
        Object[] statusArgs = new Object[statusList.size()];
        statusList.toArray(statusArgs);
        mergeTaskResult(statusArgs); // 合并每个任务的事务信息
    }

    /**
     * 以 Reactor 异步的方式执行这些任务，需要注意的是，当使用这个方法时，由于
     * Reactor 的异步特性，如果业务方法使用了 @Transactional 注解修饰，Spring 的事务处理会发生在实际处理
     * 事务之前，可能会导致数据库连接被释放，从而无法绑定对应的事务对象，使用时需要注意这一点
     */
    public void asyncExecute() {
        List<Mono<TransactionStatus>> monoList = new ArrayList<>();

        Scheduler scheduler = Schedulers.fromExecutor(this.executor);
        for (Callable<TransactionStatus> callable : callableList) {
            monoList.add(Mono.fromCallable(callable)
                         .subscribeOn(scheduler));
        }
        Flux.zip(monoList, Tuples::fromArray)
            .single()
            .flatMap(tuple2 -> Mono.fromRunnable(() -> {
                TransactionSynchronizationManager.bindResource(txManager.getResourceFactory(), txResource);
                mergeTaskResult(tuple2.toArray());
            }))
            .subscribeOn(scheduler)
            .doOnSubscribe(any -> log.info("开始执行事务的合并操作"))
            .doFinally(any -> {
                log.debug("合并事务处理执行完成");
                scheduler.dispose();
                executor.shutdown();
            })
            .subscribe();
    }

    private void mergeTaskResult(Object... statusList) {
        boolean exFlag = false;
        for (Object obj : statusList) {
            if (obj == null) {
                exFlag = true;
                continue;
            }
            // 在当前上下文中一定是 TransactionStatus 类型的对象
            TransactionStatus status = (TransactionStatus) obj;
            if (status.isRollbackOnly()) exFlag = true;
        }
        if (exFlag) {
            log.debug("由于任务执行时出现异常，因此会将整个业务进操作进行回滚");
            txManager.rollback(txStatus);
            /*
            	这里抛出异常的原因是因为相关的业务方法可能被 @Transactional 修饰过，
            	从而导致提交只能回滚的事务而导致的提交异常，具体使用时可以考虑替换掉这个异常类型
            */
            throw new RuntimeException("需要回滚的异常");
        } else {
            txManager.commit(txStatus);
        }
    }
}
```

