# MyBatis—Spring 动态数据源事务的处理

在一般的 Spring 应用中，如果底层数据库访问采用的是 MyBatis，那么在大多数情况下，只使用一个单独的数据源，Spring 的事务管理在大多数情况下都是有效的。然而，在一些复杂的业务场景下，如需要在某一时刻访问不同的数据库，由于 Spring 对于事务管理实现的方式，可能不能达到预期的效果。本文将简要介绍 Spring 中事务的实现方式，并对以 MyBatis 为底层数据库访问的系统为例，提供多数据源事务处理的解决方案

## Spring 事务的实现原理

常见地，在 Spring 中添加事务的方式通常都是在对应的方法或类上加上 `@Transactional` 注解显式地将这部分处理加上事务，对于 `@Transactional` 注解，Spring 会在 `org.springframework.transaction.annotation.AnnotationTransactionAttributeSource` 定义方法拦截的匹配规则（即 AOP 部分中的 PointCut），而具体的处理逻辑（即 AOP 中的 Advice）则是在 `org.springframework.transaction.interceptor.TransactionInterceptor` 中定义

具体事务执行的调用链路如下

<img src="https://s2.loli.net/2024/01/01/8AqRo4zGnsUxIJr.png" alt="spring-transaction-flow.png" style="zoom:150%;" />

Spring 对于事务切面采取的具体行为实现如下：

```java
public class TransactionInterceptor 
    extends TransactionAspectSupport 
    implements MethodInterceptor, Serializable {
    
    // 这里的方法定义为 MethodInterceptor，即 AOP 实际调用点
    @Override
	@Nullable
	public Object invoke(MethodInvocation invocation) throws Throwable {
		// Work out the target class: may be {@code null}.
		// The TransactionAttributeSource should be passed the target class
		// as well as the method, which may be from an interface.
		Class<?> targetClass = (invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null);

		// Adapt to TransactionAspectSupport's invokeWithinTransaction...
        // invokeWithinTransaction 为父类 TransactionAspectSupport 定义的方法
		return invokeWithinTransaction(invocation.getMethod(), targetClass, new CoroutinesInvocationCallback() {
			@Override
			@Nullable
			public Object proceedWithInvocation() throws Throwable {
				return invocation.proceed();
			}
			@Override
			public Object getTarget() {
				return invocation.getThis();
			}
			@Override
			public Object[] getArguments() {
				return invocation.getArguments();
			}
		});
	}
}
```

继续进入 `TransactionAspectSupport` 的 `invokeWithinTransaction` 方法：

```java
public abstract class TransactionAspectSupport 
    implements BeanFactoryAware, InitializingBean {
    protected Object invokeWithinTransaction(Method method, @Nullable Class<?> targetClass,
			final InvocationCallback invocation) throws Throwable {
        // 省略响应式事务和编程式事务的处理逻辑

        // 当前事务管理的实际
		PlatformTransactionManager ptm = asPlatformTransactionManager(tm);
		final String joinpointIdentification = methodIdentification(method, targetClass, txAttr);

		if (txAttr == null || !(ptm instanceof CallbackPreferringPlatformTransactionManager)) {
			// Standard transaction demarcation with getTransaction and commit/rollback calls.
            /*
            	检查在当前的执行上下文中，是否需要创建新的事务，这是因为当前执行的业务处理可能在上一个已经开始
            	的事务处理中
            */
			TransactionInfo txInfo = createTransactionIfNecessary(ptm, txAttr, joinpointIdentification);

			Object retVal;
			try {
				// This is an around advice: Invoke the next interceptor in the chain.
				// This will normally result in a target object being invoked.
				retVal = invocation.proceedWithInvocation(); // 实际业务代码的业务处理
			}
			catch (Throwable ex) {
				// target invocation exception
				completeTransactionAfterThrowing(txInfo, ex); // 出现异常的回滚处理
				throw ex;
			}
			finally {
				cleanupTransactionInfo(txInfo);
			}

			if (retVal != null && vavrPresent && VavrDelegate.isVavrTry(retVal)) {
				// Set rollback-only in case of Vavr failure matching our rollback rules...
				TransactionStatus status = txInfo.getTransactionStatus();
				if (status != null && txAttr != null) {
					retVal = VavrDelegate.evaluateTryFailure(retVal, txAttr, status);
				}
			}

            // 如果没有出现异常，则提交本次事务
			commitTransactionAfterReturning(txInfo);
			return retVal;
		}
	}
}
```

在获取事务信息对象时，首先需要获取到对应的事务状态对象 `TransactionStatus`，这个状态对象决定了 Spring 后续要对当前事务采取的何种行为，具体代码在 `org.springframework.transaction.support.AbstractPlatformTransactionManager#getTransaction` 

``` java
// 这里的 definition 是通过解析 @Transactional 注解中的属性得到的配置对象
public final TransactionStatus getTransaction(@Nullable TransactionDefinition definition)
    throws TransactionException {

    // Use defaults if no transaction definition given.
    TransactionDefinition def = (definition != null ? definition : TransactionDefinition.withDefaults());

    /*
    	这里获取事务相关的对象（如持有的数据库连接等），具体由子类来定义相关的实现
    */
    Object transaction = doGetTransaction();
    boolean debugEnabled = logger.isDebugEnabled();

    // 如果当前已经在一个事务中，那么需要按照定义的属性采取对应的行为
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
    // 需要重新开启一个新的事务的情况，具体在 org.springframework.transaction.TransactionDefinition 有相关的定义
    else if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED ||
             def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW ||
             def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {
        SuspendedResourcesHolder suspendedResources = suspend(null);
        if (debugEnabled) {
            logger.debug("Creating new transaction with name [" + def.getName() + "]: " + def);
        }
        try {
            // 开启一个新的事务
            return startTransaction(def, transaction, debugEnabled, suspendedResources);
        }
        catch (RuntimeException | Error ex) {
            resume(null, suspendedResources);
            throw ex;
        }
    }
    else {
        // Create "empty" transaction: no actual transaction, but potentially synchronization.
        if (def.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT && logger.isWarnEnabled()) {
            logger.warn("Custom isolation level specified but no actual transaction initiated; " +
                        "isolation level will effectively be ignored: " + def);
        }
        boolean newSynchronization = (getTransactionSynchronization() == SYNCHRONIZATION_ALWAYS);
        return prepareTransactionStatus(def, null, true, newSynchronization, debugEnabled, null);
    }
}
```

在 `AbstractPlatformTransactionManager` 中已经定义了事务处理的大体框架，而实际的事务实现则交由具体的子类实现，在一般情况下，由 `org.springframework.jdbc.datasource.DataSourceTransactionManager` 采取具体的实现

主要关注的点在于对于事务信息对象的创建，事务的开启、提交回滚操作，具体对应的代码如下：

事务信息对象的创建代码：

``` java
protected Object doGetTransaction() {
    /*
    	简单地理解，DataSourceTransactionObject 就是一个持有数据库连接的资源对象
    */
    DataSourceTransactionObject txObject = new DataSourceTransactionObject();
    txObject.setSavepointAllowed(isNestedTransactionAllowed());
    /*
    	TransactionSynchronizationManager 是用于管理在事务执行过程相关的信息对象的一个工具类，基本上
    	这个类持有的事务信息贯穿了整个 Spring 事务管理
    */
    ConnectionHolder conHolder =
        (ConnectionHolder) TransactionSynchronizationManager.getResource(obtainDataSource());
    txObject.setConnectionHolder(conHolder, false);
    return txObject;
}
```

开启事务对应的源代码：

``` java
protected void doBegin(Object transaction, TransactionDefinition definition) {
    DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
    Connection con = null;

    try {
        /*
        	如果当前事务对象没有持有数据库连接，则需要从对应的 DataSource 中获取对应的连接
        */
        if (!txObject.hasConnectionHolder() ||
            txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
            Connection newCon = obtainDataSource().getConnection();
            if (logger.isDebugEnabled()) {
                logger.debug("Acquired Connection [" + newCon + "] for JDBC transaction");
            }
            txObject.setConnectionHolder(new ConnectionHolder(newCon), true);
        }

        txObject.getConnectionHolder().setSynchronizedWithTransaction(true);
        con = txObject.getConnectionHolder().getConnection();

        Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);
        txObject.setPreviousIsolationLevel(previousIsolationLevel);
        txObject.setReadOnly(definition.isReadOnly());

        // Switch to manual commit if necessary. This is very expensive in some JDBC drivers,
        // so we don't want to do it unnecessarily (for example if we've explicitly
        // configured the connection pool to set it already).
        
        /*
        	由于当前的事务已经交由 Spring 进行管理，那么在这种情况下，原有数据库连接的自动提交
        	必须是关闭的，因为如果开启了自动提交，那么实际上就相当于每一次的 SQL 都会执行一次事务的提交，
        	这种情况下事务的管理没有意义
        */
        if (con.getAutoCommit()) {
            txObject.setMustRestoreAutoCommit(true);
            if (logger.isDebugEnabled()) {
                logger.debug("Switching JDBC Connection [" + con + "] to manual commit");
            }
            con.setAutoCommit(false);
        }

        prepareTransactionalConnection(con, definition);
        txObject.getConnectionHolder().setTransactionActive(true);

        int timeout = determineTimeout(definition);
        if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
            txObject.getConnectionHolder().setTimeoutInSeconds(timeout);
        }

        // Bind the connection holder to the thread.
        
        /*
        	如果是新创建的事务，那么需要绑定这个数据库连接对象到这个事务中，使得后续再进来的业务处理
        	能够顺利地进入原有的事务中
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

事务提交相关的代码：

``` java
private void processCommit(DefaultTransactionStatus status) throws TransactionException {
    try {
        boolean beforeCompletionInvoked = false;

        try {
            boolean unexpectedRollback = false;
            
            /*
            	一些事务提交时的钩子方法，使得第三方的数据库持久话框架（如 MyBatis）的
            	事务能够被 Spring 管理
            */
            prepareForCommit(status);
            triggerBeforeCommit(status);
            triggerBeforeCompletion(status);
            beforeCompletionInvoked = true;

            if (status.hasSavepoint()) {
                if (status.isDebug()) {
                    logger.debug("Releasing transaction savepoint");
                }
                unexpectedRollback = status.isGlobalRollbackOnly();
                status.releaseHeldSavepoint();
            }
            else if (status.isNewTransaction()) {
                if (status.isDebug()) {
                    logger.debug("Initiating transaction commit");
                }
                unexpectedRollback = status.isGlobalRollbackOnly();
                doCommit(status);
            }
            else if (isFailEarlyOnGlobalRollbackOnly()) {
                unexpectedRollback = status.isGlobalRollbackOnly();
            }

            // Throw UnexpectedRollbackException if we have a global rollback-only
            // marker but still didn't get a corresponding exception from commit.
            if (unexpectedRollback) {
                throw new UnexpectedRollbackException(
                    "Transaction silently rolled back because it has been marked as rollback-only");
            }
        }
        catch (UnexpectedRollbackException ex) {
            // can only be caused by doCommit
            triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);
            throw ex;
        }
        catch (TransactionException ex) {
            // can only be caused by doCommit
            if (isRollbackOnCommitFailure()) {
                doRollbackOnCommitException(status, ex);
            }
            else {
                triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
            }
            throw ex;
        }
        catch (RuntimeException | Error ex) {
            if (!beforeCompletionInvoked) {
                triggerBeforeCompletion(status);
            }
            doRollbackOnCommitException(status, ex);
            throw ex;
        }

        // Trigger afterCommit callbacks, with an exception thrown there
        // propagated to callers but the transaction still considered as committed.
        try {
            // 事务正常提交后的钩子方法
            triggerAfterCommit(status);
        }
        finally {
            // 事务正常提交后有关资源清理的钩子方法
            triggerAfterCompletion(status, TransactionSynchronization.STATUS_COMMITTED);
        }

    }
    finally {
        cleanupAfterCompletion(status);
    }
}
```

事务回滚的相关代码：

``` java
private void doRollbackOnCommitException(DefaultTransactionStatus status, Throwable ex) throws TransactionException {
    try {
        if (status.isNewTransaction()) {
            if (status.isDebug()) {
                logger.debug("Initiating transaction rollback after commit exception", ex);
            }
            doRollback(status);
        }
        else if (status.hasTransaction() && isGlobalRollbackOnParticipationFailure()) {
            if (status.isDebug()) {
                logger.debug("Marking existing transaction as rollback-only after commit exception", ex);
            }
            doSetRollbackOnly(status);
        }
    }
    catch (RuntimeException | Error rbex) {
        logger.error("Commit exception overridden by rollback exception", ex);
        triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
        throw rbex;
    }
    // 一些事务相关的钩子方法
    triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);
}
```

## MyBatis 与 Spring 事务的整合

在 MyBatis 中，实际获取连接是通过 `BaseExecutor` 中 `Transaction` 属性来获取对应的连接，实际上 MyBatis 执行时并不会意识到当前上下文是否处于一个事务中，在整合到 Spring 的过程中，默认的 `Transaction` 实现类为 `org.mybatis.spring.transaction.SpringManagedTransaction`：

``` java
public class SpringManagedTransaction implements Transaction {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringManagedTransaction.class);

    private final DataSource dataSource;

    private Connection connection;

    private boolean isConnectionTransactional;

    private boolean autoCommit;

    public SpringManagedTransaction(DataSource dataSource) {
        notNull(dataSource, "No DataSource specified");
        this.dataSource = dataSource;
    }

    /**
   * {@inheritDoc}
   */
    @Override
    public Connection getConnection() throws SQLException {
        if (this.connection == null) {
            openConnection();
        }
        return this.connection;
    }

    /*
    	从当前的数据源对象 dataSource 中获取一个连接对象，而结合上文 Spring 中对于事务的处理，如果已经将
    	dataSource 属性绑定到了当前的线程，那么在这里就会获取到原有创建事务时已经创建的连接，而不是从头重新生成一个连接
    */
    private void openConnection() throws SQLException {
        this.connection = DataSourceUtils.getConnection(this.dataSource);
        this.autoCommit = this.connection.getAutoCommit();
        /*
        	这里的目的是为了处理 MyBatis 部分关于事务提交的处理，因为 MyBatis 会将自己的事务处理放入到 Spring 事务中的
        	钩子方法中进行处理，如果此时持有的连接对象与整个 Spring 事务持有的连接对象一致时，由于 MyBatis 的事务提交会
        	早于 Spring 的事务提交（triggerBeforeCommit() 钩子方法），从而导致 Spring 在提交事务时出现事务重复提交的异常
        */
        this.isConnectionTransactional = DataSourceUtils.isConnectionTransactional(this.connection, this.dataSource);

        LOGGER.debug(() -> "JDBC Connection [" + this.connection + "] will"
                     + (this.isConnectionTransactional ? " " : " not ") + "be managed by Spring");
    }

    @Override
    public void commit() throws SQLException {
        if (this.connection != null && !this.isConnectionTransactional && !this.autoCommit) {
            LOGGER.debug(() -> "Committing JDBC Connection [" + this.connection + "]");
            this.connection.commit();
        }
    }

    @Override
    public void rollback() throws SQLException {
        if (this.connection != null && !this.isConnectionTransactional && !this.autoCommit) {
            LOGGER.debug(() -> "Rolling back JDBC Connection [" + this.connection + "]");
            this.connection.rollback();
        }
    }

    @Override
    public void close() throws SQLException {
        DataSourceUtils.releaseConnection(this.connection, this.dataSource);
    }

    @Override
    public Integer getTimeout() throws SQLException {
        ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        if (holder != null && holder.hasTimeout()) {
            return holder.getTimeToLiveInSeconds();
        }
        return null;
    }
}
```

而 MyBatis 对于 `Transaction` 中的提交处理，需要将其整合到 Spring 中，是通过向 `TransactionSynchronizationManager` 注册 `TransactionSynchronization` 来实现的，在 MyBatis 中，实际的实现类为 `SqlSessionSynchronization`：

``` java
private static final class SqlSessionSynchronization extends TransactionSynchronizationAdapter {

    private final SqlSessionHolder holder; // 当前持有的 SqlSession

    /*
    	用于绑定到 TransactionSynchronizationManager 的 Key 对象，由于 Spring 对于 Bean 的单例处理，实际上每次
    	都是唯一的 SqlSessionFactory 实例对象，因此在 TransactionSynchronizationManager 中的 ThreadLocal 可以通过
    	这个对象找到当前线程绑定的实际 Value 对象
    */
    private final SqlSessionFactory sessionFactory;

    private boolean holderActive = true;

    public SqlSessionSynchronization(SqlSessionHolder holder, SqlSessionFactory sessionFactory) {
        notNull(holder, "Parameter 'holder' must be not null");
        notNull(sessionFactory, "Parameter 'sessionFactory' must be not null");

        this.holder = holder;
        this.sessionFactory = sessionFactory;
    }


    @Override
    public int getOrder() {
        // order right before any Connection synchronization
        return DataSourceUtils.CONNECTION_SYNCHRONIZATION_ORDER - 1;
    }

    @Override
    public void suspend() {
        if (this.holderActive) {
            LOGGER.debug(() -> "Transaction synchronization suspending SqlSession [" + this.holder.getSqlSession() + "]");
            TransactionSynchronizationManager.unbindResource(this.sessionFactory);
        }
    }

    @Override
    public void resume() {
        if (this.holderActive) {
            LOGGER.debug(() -> "Transaction synchronization resuming SqlSession [" + this.holder.getSqlSession() + "]");
            TransactionSynchronizationManager.bindResource(this.sessionFactory, this.holder);
        }
    }

    @Override
    public void beforeCommit(boolean readOnly) {
        /*
        	注意 Spring 事务中的 triggerBeforeCommit() 钩子方法，在事务提交前会依次检查 TransactionSynchronizationManager 中绑定的 TransactionSynchronization，并在事务实际提交前（即当前事务信息是新开启的事务）前调用每个 TransactionSynchronization 的 beforeCommit 方法
        */
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            try {
                LOGGER.debug(() -> "Transaction synchronization committing SqlSession [" + this.holder.getSqlSession() + "]");
                /*
                	由于 SqlSession 最终的方法调用会委托给对应的 Executor 进行处理，而 executor 的 commit()
                	则会继续调用 Transaction 对象的 commit() 方法，从而实现与上文 SpringManagedTransaction 对象整合
                */
                this.holder.getSqlSession().commit();
            } catch (PersistenceException p) {
                if (this.holder.getPersistenceExceptionTranslator() != null) {
                    DataAccessException translated = this.holder.getPersistenceExceptionTranslator()
                        .translateExceptionIfPossible(p);
                    if (translated != null) {
                        throw translated;
                    }
                }
                throw p;
            }
        }
    }

    /*
    	triggerBeforeCompletion() 钩子方法
    */
    @Override
    public void beforeCompletion() {
        // Issue #18 Close SqlSession and deregister it now
        // because afterCompletion may be called from a different thread
        if (!this.holder.isOpen()) {
            LOGGER
                .debug(() -> "Transaction synchronization deregistering SqlSession [" + this.holder.getSqlSession() + "]");
            TransactionSynchronizationManager.unbindResource(sessionFactory);
            this.holderActive = false;
            LOGGER.debug(() -> "Transaction synchronization closing SqlSession [" + this.holder.getSqlSession() + "]");
            this.holder.getSqlSession().close();
        }
    }

    /*
    	triggerAfterCompletion() 钩子方法，主要是为了清理相关 ThreadLocal 绑定的资源对象
     */
    @Override
    public void afterCompletion(int status) {
        if (this.holderActive) {
            // afterCompletion may have been called from a different thread
            // so avoid failing if there is nothing in this one
            LOGGER
                .debug(() -> "Transaction synchronization deregistering SqlSession [" + this.holder.getSqlSession() + "]");
            TransactionSynchronizationManager.unbindResourceIfPossible(sessionFactory);
            this.holderActive = false;
            LOGGER.debug(() -> "Transaction synchronization closing SqlSession [" + this.holder.getSqlSession() + "]");
            this.holder.getSqlSession().close();
        }
        this.holder.reset();
    }
}
```

为了使得 MyBatis 在执行的过程中能够 Spring 进行管理，因此需要代理实际执行的 `SqlSession`，实际执行类为 `SqlSessionTemplate`，在执行的过程中，实际行为在 `SqlSessionInterceptor` 中定义：

``` java
// InvocationHandler 为 JDK 动态代理的部分，定义了代理类需要采取的相关行为
private class SqlSessionInterceptor implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        /*
        	getSqlSession 为 SqlSessionUtil 的静态方法，实际上在执行过程中也是通过  TransactionSynchronizationManager 来感知当前上下文所处的事务信息，当处于同一个事务中时，则会通过 sqlSessionFactory
        	作为 key 来获取之前的 SqlSession，从而保证事务的正常运行
        */
        SqlSession sqlSession = getSqlSession(SqlSessionTemplate.this.sqlSessionFactory,
                                              SqlSessionTemplate.this.executorType, SqlSessionTemplate.this.exceptionTranslator);
        try {
            Object result = method.invoke(sqlSession, args);
            if (!isSqlSessionTransactional(sqlSession, SqlSessionTemplate.this.sqlSessionFactory)) {
                // force commit even on non-dirty sessions because some databases require
                // a commit/rollback before calling close()
                sqlSession.commit(true);
            }
            return result;
        } catch (Throwable t) {
            Throwable unwrapped = unwrapThrowable(t);
            // 省略部分异常处理代码
            throw unwrapped;
        } finally {
            if (sqlSession != null) {
                closeSqlSession(sqlSession, SqlSessionTemplate.this.sqlSessionFactory);
            }
        }
    }
}
```

 `getSqlSession` 是 `SqlSessionUtil` 的静态方法，实际源代码如下所示：

``` java
public static SqlSession getSqlSession(SqlSessionFactory sessionFactory, 
                                       ExecutorType executorType,
                                       PersistenceExceptionTranslator exceptionTranslator) {

    notNull(sessionFactory, NO_SQL_SESSION_FACTORY_SPECIFIED);
    notNull(executorType, NO_EXECUTOR_TYPE_SPECIFIED);

    /*
    	如果能在 TransactionSynchronizationManager 中找到和当前 SqlSessionFactory 绑定的 SqlSession
    	信息，则说明当前可能处于一个事务中
    */
    SqlSessionHolder holder = (SqlSessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);

    SqlSession session = sessionHolder(executorType, holder);
    if (session != null) {
        return session;
    }

    /*
    	执行到这里，说明要么此时是第一次进入事务，或者当前的执行方式是以非事务的形式执行的，但无论是那种形式，都需要创建一个新的 SqlSession
    */
    LOGGER.debug(() -> "Creating a new SqlSession");
    session = sessionFactory.openSession(executorType);

    /*
    	如果当前是以事务的形式执行的，则需要将创建的 SqlSession 注册到当前事务上下文中
    */
    registerSessionHolder(sessionFactory, executorType, exceptionTranslator, session);

    return session;
}

private static SqlSession sessionHolder(ExecutorType executorType, SqlSessionHolder holder) {
    SqlSession session = null;
    /* 
    	holder 在注册到 TransactionSynchronizationManager 中时就会将 synchronizedWithTransaction
    	设置为 true，因此实际上只要注册到了 TransactionSynchronizationManager 中则说明已经在一个事务中了
    */
    if (holder != null && holder.isSynchronizedWithTransaction()) {
        if (holder.getExecutorType() != executorType) {
            throw new TransientDataAccessResourceException(
                "Cannot change the ExecutorType when there is an existing transaction");
        }

        holder.requested();

        LOGGER.debug(() -> "Fetched SqlSession [" + holder.getSqlSession() + "] from current transaction");
        session = holder.getSqlSession();
    }
    return session;
}

private static void registerSessionHolder(SqlSessionFactory sessionFactory, ExecutorType executorType,
                                          PersistenceExceptionTranslator exceptionTranslator, SqlSession session) {
    SqlSessionHolder holder;
    /*
    	TransactionSynchronizationManager.isSynchronizationActive() 检查当前是否处于一个事务上下文中，这个属性
    	会在创建事务的时候进行初始化
    */
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        Environment environment = sessionFactory.getConfiguration().getEnvironment();

        if (environment.getTransactionFactory() instanceof SpringManagedTransactionFactory) {
            LOGGER.debug(() -> "Registering transaction synchronization for SqlSession [" + session + "]");

            holder = new SqlSessionHolder(session, executorType, exceptionTranslator);
            
            /*
            	注册 sessionFactory 到事务上下文，使得能够被后续的处理感知
            */
            TransactionSynchronizationManager.bindResource(sessionFactory, holder);
            
            /*
            	注册一个 TransactionSynchronization，这个 TransactionSynchronization 相关的方法会在 Spring 事务的钩子方法中被调用
            */
            TransactionSynchronizationManager
                .registerSynchronization(new SqlSessionSynchronization(holder, sessionFactory));
            holder.setSynchronizedWithTransaction(true); // 与上面 sessionHolder 同步
            holder.requested();
        } else {
            if (TransactionSynchronizationManager.getResource(environment.getDataSource()) == null) {
                LOGGER.debug(() -> "SqlSession [" + session
                             + "] was not registered for synchronization because DataSource is not transactional");
            } else {
                throw new TransientDataAccessResourceException(
                    "SqlSessionFactory must be using a SpringManagedTransactionFactory in order to use Spring transaction synchronization");
            }
        }
    } else {
        LOGGER.debug(() -> "SqlSession [" + session
                     + "] was not registered for synchronization because synchronization is not active");
    }
```

具体整合关系如下图所示：

![mybatis_transaction.png](https://s2.loli.net/2024/01/03/QXyPi6IvBtdcglE.png)

## 动态数据源的处理

### 基本处理

一般在 Spring 中实现动态数据源都是基于 `AbstractRoutingDataSource` 并实现 `determineCurrentLookupKey` 来实现的，在实现的过程中，`AbstractRoutingDataSource` 会持有一个关于数据源 `DataSource` 的映射关系，通过 `determineCurrentLookupKey` 作为 `key` 来决定实际要采取的实际数据源。这种方式相当于多累加了一层，在一般的使用场景下可能不会有什么问题，但是当涉及到事务时，可能会出现一些不可思议的问题

假如现在我们有两个数据源：`MySQL` 和 `PostgreSQL`，我们可以定义自己的数据源枚举类（当然直接使用字符串也可以，但是使用枚举会更好）`DataSourceType`：

``` java
public enum DataSourceType {

    MYSQL,

    POSTGRESQL
}
```

现在，我们需要在系统中定义我们自己的实际数据源，这里为了简便，直接使用 `DataSourceBuilder` 的方式进行构建：

``` java
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    /*
    	MySQL 数据源
    */
    @Bean(name = "mysqlDataSource")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create()
                .url("jdbc:mysql://127.0.0.1:3306/lxh_db")
                .username("root")
                .password("12345678")
                .type(DruidDataSource.class)
                .build();
    }

    /*
    	PostgreSQL 数据源
    */
    @Bean(name = "psqlDataSource")
    public DataSource psqlDataSource() {
        return DataSourceBuilder.create()
                .url("jdbc:postgresql://127.0.0.1:5432/lxh_db")
                .username("postgres")
                .password("12345678")
                .type(DruidDataSource.class)
                .build();
    }
}
```

为了实现动态数据源，我们需要继承 `AbstractRoutingDataSource`，并实现 `determineCurrentLookupKey` 方法。为了能够动态地改变当前执行上下文的数据源类型，我们使用一个 `ThreadLocal` 来存储当前需要的数据源类型：

``` java
public class DataSourceHolder {

    private static final ThreadLocal<DataSourceType> dataSourceHolder = new ThreadLocal<>();

    public static void setCurDataSource(DataSourceType type) {
        dataSourceHolder.set(type);
    }

    public static DataSourceType getCurDataSource() {
        return dataSourceHolder.get();
    }
}
```

之后，我们重新定义我们自己的动态数据源类型：

```java
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class DynamicDataSource
        extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceHolder.getCurDataSource();
    }
}
```

现在我们的动态数据源还没有实际的 `DataSource` 映射，因此我们在实例化 `DynamicDataSource` 时需要手动注册：

``` java
import com.google.common.collect.ImmutableMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
public class DataSourceConfig {
    
    /*
    	由于我们已经在系统中定义了多个 DataSource，因此我们需要使用 @Primary 注解来标记当前定义的 DataSource 是实际需要用到的 DataSource
    */
    @Primary
    @Bean(name = "dynamicDataSource")
    public DataSource dynamicDataSource(@Qualifier("mysqlDataSource") DataSource mysqlDataSource,
                                        @Qualifier("psqlDataSource") DataSource psqlDataSource) {
        DynamicDataSource dataSource = new DynamicDataSource();
        
        // 绑定目标 key 到实际数据源的映射关系，并将它们注册到我们的动态数据源中
        Map<Object, Object> dataSourceMap = ImmutableMap.builder()
                .put(DataSourceType.MYSQL, mysqlDataSource)
                .put(DataSourceType.POSTGRESQL, psqlDataSource)
                .build();
        dataSource.setTargetDataSources(dataSourceMap);
        
        // 当通过 key 无法找到对应的数据源时，默认的数据源类型
        dataSource.setDefaultTargetDataSource(mysqlDataSource);
        return dataSource;
    }
}
```

这样做就可以使用我们的动态数据源了，在使用前，只需要调用 `DataSourceHolder.setCurDataSource` 来进行数据源切换即可：

``` java
public class XXService {
    
    @Resource
    private BBService bbService;
    
    public void handler() {
        DataSourceType prevType = DataSourceHolder.getCurDataSource();
        DataSourceHolder.setCurDataSource(DataSourceType.XXX); // 设置当前的数据源类型
        bbService.handler(); // bbService 在处理时就会使用 XXX 对应的数据源
        DataSourceHolder.setCurDataSource(prevType); // 还原回之前的数据源
    }
}
```

### 进一步简化

上面动态数据源的使用似乎有些繁琐，我们可以使用 AOP 来简化这个步骤，由于我们无法在运行中得知用户需要使用的数据源类型，因此我们只能要求用户决定。为了达到这一目的，我们可以自己定义一个注解来标记用户希望使用的数据源类型：

``` java
import java.lang.annotation.*;

/**
 * 用于定义处理上下文的所需要持有的数据源类型
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface DataSource {

    DataSourceType value() default DataSourceType.MYSQL;
}
```

这样，用户如果希望在 `XXService` 服务中都使用 `MySQL` 数据源，而在 `BBService` 中都使用 `PostrgreSQL` 数据源，可以这么做：

``` java
@Service
@DataSource(MYSQL)
public class XXService {
}

@Service
@DataSource(POSTGRESQL)
public class BBService {
}
```

现在我们已经定义了需要拦截的位置，还需要定义相关的行为来达到自动切换数据源上下文的目的：

``` java
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xhliu.springtransaction.annotation.DataSource;
import org.xhliu.springtransaction.datasource.DataSourceHolder;
import org.xhliu.springtransaction.datasource.DataSourceType;

@Aspect
@Component
public class DataSourceAspect {

    private final static Logger log = LoggerFactory.getLogger(DataSourceAspect.class);

    @Around("@annotation(org.xhliu.springtransaction.annotation.DataSource)")
    public Object dataSourceSelect(ProceedingJoinPoint pjp) throws Throwable {
        DataSourceType prevType = DataSourceHolder.getCurDataSource();
        // 获取当前用户需要使用的动态数据源类型
        DataSource dataSource = parseDataSourceAnno(pjp);
        try {
            log.debug("当前执行的上下文中，数据源的所属类型: {}", dataSource.value());
            DataSourceHolder.setCurDataSource(dataSource.value());
            return pjp.proceed();
        } finally {
            // 最终需要还原回一开始的数据源
            DataSourceHolder.setCurDataSource(prevType);
        }
    }

    private static DataSource parseDataSourceAnno(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        DataSource dataSource = signature.getMethod().getDeclaredAnnotation(DataSource.class);
        if (dataSource != null) return dataSource;
        Object target = pjp.getTarget();
        return target.getClass().getDeclaredAnnotation(DataSource.class);
    }
}
```

## 修改 MyBatis 事务的行为

### 基本处理

由于 Spring 事务是通过 `TransactionSynchronizationManager` 的 `ThreadLocal` 绑定 `DataSource` 和对应的 `Connection` 来实现事务的上下文检测，因此我们创建的 `DataSource` 在事务的执行过程中是无法再动态地切换数据源。为了解决这一问题，我们需要重新定义 `MyBatis` 事务的处理逻辑，使得它能够动态地切换数据源

我们定义自己的 `DynamicTransaction` 来替换现有的 `SpringManagedTransaction`：

``` java
import org.apache.ibatis.transaction.Transaction;
import org.mybatis.spring.transaction.SpringManagedTransaction;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.xhliu.springtransaction.datasource.DataSourceType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于定义在当前 MyBatis 处理上下文中，正在被使用的事务对象类型，由于现有的 {@link SpringManagedTransaction}
 * 实现只能绑定到一个数据源，在基于 {@link AbstractRoutingDataSource} 的数据源中，当同属于一个事务时，无法切换到希望的
 * 数据源，为此，需要定义一个特殊的事务类型来替换现有的事务类型，从而实现在一个事务中能够切换数据源的效果
 *
 * @author lxh
 */
public class DynamicTransaction
        extends SpringManagedTransaction {

    // 缓存当前数据源之间的映射关系
    private final Map<DataSourceType, Transaction> txMap = new ConcurrentHashMap<>();

    // 实际当前系统中持有的动态数据源对象
    private final DataSource dataSource;

    public DynamicTransaction(DataSource dataSource) {
        super(dataSource);
        this.dataSource = dataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = getConnection(DynamicDataSourceUtils.determineDataSourceType());
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            connection.setAutoCommit(false); // 如果当前已经持有了事务，那么获取到的连接应当都是非自动提交的
        }
        return connection;
    }

    @Override
    public void commit() throws SQLException {
        /*
        	由于该方法的调用发生在 Spring 事务提交之前 `triggerBeforeCommit` 钩子方法
        */
        for (Map.Entry<DataSourceType, Transaction> entry : txMap.entrySet()) {
            if (!entry.getValue().getConnection().getAutoCommit()) {
                entry.getValue().getConnection().commit();
            }
        }
    }

    @Override
    public void rollback() throws SQLException {
        /*
        	前面提到，MyBatis 整合 Spring 的事务过程中是通过 AbstractPlatformTransactionManager 的钩子方法实现的，
        	在回滚时如果能够检测到事务存活，那么说明此时事务依旧被 Spring 管理，因此此时这部分的处理不应当被回滚
        */
        if (TransactionSynchronizationManager.isActualTransactionActive()) return;
        for (Map.Entry<DataSourceType, Transaction> entry : txMap.entrySet()) {
            entry.getValue().getConnection().rollback();
        }
    }

    @Override
    public void close() throws SQLException {
        if (TransactionSynchronizationManager.isActualTransactionActive()) return;
        for (Map.Entry<DataSourceType, Transaction> entry : txMap.entrySet()) {
            DataSourceUtils.releaseConnection(entry.getValue().getConnection(), curDataSource(entry.getKey()));
        }
    }

    private Connection getConnection(DataSourceType type) throws SQLException {
        if (txMap.containsKey(type)) {
            return txMap.get(type).getConnection();
        }

        txMap.put(type, new SpringManagedTransaction(curDataSource(type)));
        return txMap.get(type).getConnection();
    }

    private DataSource curDataSource(DataSourceType type) {
        DataSource curDS = dataSource;
        /*
        	由于有可能存在代理，因此需要不断剥离现有数据源对象，直到获取到实际的数据源对象
        */
        while (curDS instanceof DelegatingDataSource) {
            curDS = ((DelegatingDataSource) curDS).getTargetDataSource();
        }
        /*
        	对于动态数据源对象，需要通过对应 Key 获取到对应的实际 DataSource 对象
        */
        if (curDS instanceof AbstractRoutingDataSource) {
            Map<Object, DataSource> dss = ((AbstractRoutingDataSource) curDS).getResolvedDataSources();
            return dss.getOrDefault(type, ((AbstractRoutingDataSource) curDS).getResolvedDefaultDataSource());
        }
        
        return curDS; // 其它一般情况的数据源。。。。
    }
}
```

为了使得 `MyBatis`能够使用我们自定义的 `Transaction`，我们需要重新配置 `MyBatis` 的 `TransactionFactory`，因此我们需要重新定义自己的 `TransactionFactory`：

``` java
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;

import javax.sql.DataSource;

/**
 * 重新定义 MyBatis 中的事务工厂，使得自定义的动态数据源事务能够被 MyBatis 加载
 *
 * @author lxh
 */
public class DynamicTransactionFactory
        extends SpringManagedTransactionFactory {

    @Override
    public Transaction newTransaction(DataSource dataSource,
                                      TransactionIsolationLevel level,
                                      boolean autoCommit) {
        return new DynamicTransaction(dataSource);
    }
}
```

现在，我们要做的是替换现有 `MyBatis` 中的 `TransactionFactory`，这个配置是在 `MybatisAutoConfiguration` （如果是第三方的扩展的 MyBatis，则是在其对应的 `**AutoConfiguration` 中）中完成的配置：

``` java
@Bean
@ConditionalOnMissingBean
public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
    SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
    factory.setDataSource(dataSource);
    factory.setVfs(SpringBootVFS.class);
    if (StringUtils.hasText(this.properties.getConfigLocation())) {
        factory.setConfigLocation(this.resourceLoader.getResource(this.properties.getConfigLocation()));
    }
    /*
    	应用相关的 Configuration，包括 Mapper 的路径，日志等配置信息
    */
    applyConfiguration(factory);
    
    // 省略部分代码。。。。

    /*
    	这里是 MyBatis 提供的一个扩展点，用于修改 SqlSessionFactoryBean 的相关配置属性，如 TransactionFactory 等相关信息，具体详情可以查看 org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer
    */
    applySqlSessionFactoryBeanCustomizers(factory);

    return factory.getObject();
}
```

由于 `MyBatis` 已经提供了相关的扩展点，因此我们可以由此将我们自定义的 `TransactionFactory` 替换掉 `MyBatis` 中默认的 `TransactionFactory`：

``` java
import org.apache.ibatis.transaction.TransactionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer;

public class TransactionSqlSessionFactoryBeanCustomizer
        implements SqlSessionFactoryBeanCustomizer {

    private final TransactionFactory txFactory;

    public TransactionSqlSessionFactoryBeanCustomizer(TransactionFactory txFactory) {
        this.txFactory = txFactory;
    }

    @Override
    public void customize(SqlSessionFactoryBean factoryBean) {
        factoryBean.setTransactionFactory(txFactory);
    }
}
```

我们需要将这个类添加到 Spring 上下文中，使得 Spring 能够发现并实例化它（这里我们使用注解的形式）：

``` java
import org.mybatis.spring.SqlSessionFactoryBean;
import org.apache.ibatis.transaction.TransactionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.xhliu.springtransaction.transaction.DynamicTransactionFactory;

/**
 * @author lxh
 */
@Configuration
public class MyBatisConfig {

    @Bean(name = "dynamicTransactionFactory")
    public TransactionFactory dynamicTransactionFactory() {
        return new DynamicTransactionFactory();
    }

    @Bean(name = "dynamicDataSourceCustomizer")
    public SqlSessionFactoryBeanCustomizer
    dynamicDataSourceCustomizer(
            @Qualifier("dynamicTransactionFactory") TransactionFactory dynamicTransactionFactory
    ) {
        return new TransactionSqlSessionFactoryBeanCustomizer(dynamicTransactionFactory);
    }
}
```

现在每个组件的关系如下：

<img src="https://s2.loli.net/2024/01/07/hg5aD6fCBsbikWy.png" alt="mybatis_transaction_compnenet.png" style="zoom:80%;" />

### 一些可能出现的问题

#### `TransactionFactory` 无法注册

在一些低版本的 `MyBatis` 或者第三方 `MyBatis` 组件中，可能使用 `SqlSessionFactoryBeanCustomizer` 来配置 `SqlSessionFactoryBean`，在这种情况下，最佳的解决方式是提高 `MyBatis` 的版本，但是在一些三方组件中，这部分可能很难发生变化（不再维护或者其它原因无法修改），这种情况下，需要我们手动替换 `SqlSessionFactory` 的定义，比如我们创建自己的 `MineMyBatisAutoConfiguration`：

``` java
/**
 * @author lxh
 */
@Configuration
public class MineMyBatisAutoConfiguration {

    private final static Logger log = LoggerFactory.getLogger(MineMyBatisAutoConfiguration.class);

    private final MybatisProperties properties;

    private final Interceptor[] interceptors;

    private final TypeHandler[] typeHandlers;

    private final LanguageDriver[] languageDrivers;

    private final ResourceLoader resourceLoader;

    private final DatabaseIdProvider databaseIdProvider;

    private final List<ConfigurationCustomizer> configurationCustomizers;

    private final List<SqlSessionFactoryBeanCustomizer> sqlSessionFactoryBeanCustomizers;

    public MineMyBatisAutoConfiguration(
        MybatisProperties properties,
        ObjectProvider<Interceptor[]> interceptorsProvider,
        ObjectProvider<TypeHandler[]> typeHandlersProvider,
        ObjectProvider<LanguageDriver[]> languageDriversProvider,
        ResourceLoader resourceLoader,
        ObjectProvider<DatabaseIdProvider> databaseIdProvider,
        ObjectProvider<List<ConfigurationCustomizer>> configurationCustomizersProvider,
        ObjectProvider<List<SqlSessionFactoryBeanCustomizer>> sqlSessionFactoryBeanCustomizers
    ) {
        this.properties = properties;
        this.interceptors = interceptorsProvider.getIfAvailable();
        this.typeHandlers = typeHandlersProvider.getIfAvailable();
        this.languageDrivers = languageDriversProvider.getIfAvailable();
        this.resourceLoader = resourceLoader;
        this.databaseIdProvider = databaseIdProvider.getIfAvailable();
        this.configurationCustomizers = configurationCustomizersProvider.getIfAvailable();
        this.sqlSessionFactoryBeanCustomizers = sqlSessionFactoryBeanCustomizers.getIfAvailable();
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        // 三方组件相关的源码。。。。
        
        /*
        	将 SqlSessionFactoryBeanCustomizer 配置到当前的 SqlSessionFactoryBean，使得我们现有的
        	TransactionFactory 的配置能够生效
        */
        applySqlSessionFactoryBeanCustomizers(factory);
        return factory.getObject();
    }

    private void applySqlSessionFactoryBeanCustomizers(SqlSessionFactoryBean factory) {
        if (!CollectionUtils.isEmpty(this.sqlSessionFactoryBeanCustomizers)) {
            for (SqlSessionFactoryBeanCustomizer customizer : this.sqlSessionFactoryBeanCustomizers) {
                customizer.customize(factory);
            }
        }
    }

    private void applyConfiguration(SqlSessionFactoryBean factory) {
        org.apache.ibatis.session.Configuration configuration = this.properties.getConfiguration();
        if (configuration == null && !StringUtils.hasText(this.properties.getConfigLocation())) {
            configuration = new org.apache.ibatis.session.Configuration();
        }
        if (configuration != null && !CollectionUtils.isEmpty(this.configurationCustomizers)) {
            for (ConfigurationCustomizer customizer : this.configurationCustomizers) {
                customizer.customize(configuration);
            }
        }
        factory.setConfiguration(configuration);
    }
}
```

由于配置类的加载顺序问题，可能需要手动地修改配置类定义的顺序，由于 Spring 会首先加载被 `@ComponentScan` 注解修饰的配置类，因此在启动类中需要将这个类作为最开始扫描的基类，从而不会被其它 `MyBatis` 组件替换：

``` java
/*
	强制将 MineMyBatisAutoConfiguration 的配置类定义放到最前
*/
@ComponentScan(basePackageClasses = {MineMyBatisAutoConfiguration.class, DemoApplication.class})
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

#### 死锁

实际上，由于 Spring 事务会绑定 `DataSource` 作为事务的关键信息对象，同时会通过 `DataSource` 的 `getConnection()` 方法作为此 `DataSource` 对应事务的唯一连接，这在原有的事务处理中是没有问题的。然而，由于我们修改了 `MyBatis` 获取数据库连接的方式，使得它不再是直接当前线程绑定的事务信息中的连接了，也就是说，`MyBatis` 获取到的 `Connection` 和 Spring 事务中的存活的 `Connection` 不再是同一个。在这种情况下，Spring 事务在等待 `MyBatis` 处理的结束去释放连接，而 `MyBatis` 获取数据又需要重新从 `DataSource` 中再获取一次（一般是通过数据库连接池，如果此时连接池中的连接数已经被耗尽了，那么此时 `MyBatis`  的处理会被阻塞），而 `MyBatis` 的阻塞又会导致 Spring 事务中的数据库连接无法被释放，这可能导致 `MyBatis` 永远无法再获取到新的连接！

具体情况如下图所示：

![mybatis_tran_dead_lock.png](https://s2.loli.net/2024/01/08/JIWk1NFh8rgXfiS.png)

回想一下死锁出现的几个条件：持有互斥锁、持有并等待、非抢占式以及构成循环回路。尽管在这个问题中并不存在实际意义上的互斥锁，但是对于连接池的请求也间接地相当于希望获取互斥锁，同时内部的两个获取连接的操作也在形式上满足了其余的几个条件。

为了解决死锁，只需要去掉其中的一个条件即可，最佳的条件去除就是互斥锁。经过上文的分析，出现死锁的原因是因为一个事务中多次获取了连接，我们**只需要保证在一个事务中不会出现对同一个数据源多次获取连接即可**

首先，我们需要确保在一个事务中绑定的 `DataSource` 为我们实际需要获取连接的数据源，而不是 `AbstractRoutingDataSource`（绑定该数据源就会使得后续 `MyBatis` 在获取连接时重新获取一次），因此，我们需要修改现在的 `DataSourceTransactionManager`，使得它能够绑定到正确的实际数据源：

``` java
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;

public class DynamicDataSourceTransactionManager
        extends DataSourceTransactionManager {

    public DynamicDataSourceTransactionManager(DataSource dataSource) {
        super(dataSource);
    }

    @NotNull
    @Override
    protected DataSource obtainDataSource() {
        /* 
        	剥离 AbstractRoutingDataSource，使得事务能够绑定实际的 DataSource，后续的 MyBatis 获取连接时即可通过 DataSource 获取到当前事务上下文中关联的数据库连接
        */
        DataSource curDataSource = super.obtainDataSource();
        while (curDataSource instanceof DelegatingDataSource) {
            curDataSource = ((DelegatingDataSource) curDataSource).getTargetDataSource();
        }
        if (curDataSource instanceof AbstractRoutingDataSource) {
            Map<Object, DataSource> dss = ((AbstractRoutingDataSource) curDataSource).getResolvedDataSources();
            return dss.getOrDefault(DynamicDataSourceUtils.determineDataSourceType(),
                    ((AbstractRoutingDataSource) curDataSource).getResolvedDefaultDataSource());
        }
        assert curDataSource != null;
        return curDataSource;
    }
}
```

为了使得这个事务管理能够生效，我们需要替换现有的 `DataSourceTransactionManager` Bean 定义：

``` java
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
public class TransactionConfiguration {

    /*
    	由于 spring-jdbc 定义的 DataSourceTransactionManager 是被 @ConditionalOnMissingBean 修饰的，因此我们
    	在这里直接定义 Bean 就可以重新覆盖原有的 DataSourceTransactionManager 定义
    */
    @Bean(name = "dynamicDataSourceTransactionManager")
    public DataSourceTransactionManager dynamicDataSourceTransactionManager(
        @Qualifier("dynamicDataSource") DataSource dataSource,
        ObjectProvider<TransactionManagerCustomizers> transactionManagerCustomizers
    ) {
        DynamicDataSourceTransactionManager transactionManager = new DynamicDataSourceTransactionManager(dataSource);
        transactionManagerCustomizers.ifAvailable((customizers) -> customizers.customize(transactionManager));
        return transactionManager;
    }
}
```

现在，死锁的问题便得到了顺利地解决

#### 多线程中的事务

由于 Spring 的事务信息是通过 `ThreadLocal` 控制的，因此在不同的线程中，Spring 事务便不能很好地工作，为了解决这个问题，我们可以在线程执行任务前将现有线程关联的事务信息绑定到当前工作线程，当出现异常时，我们可以将这个事务信息标记为 “只能回滚”，从而达到整体的一致性的目标

以下面的例子为例：

``` java
public TransactionStatus run() {
    /*
    	txManager 为创建任务时必须的 DataSourceTransactionManager 事务管理对象
    	resource 为之前事务所在线程绑定的资源对象，我们知道就是 DataSourceTransactionObject，持有数据库连接的信息对象，
    	这样，当前线程中后续的 MyBatis 组件在获取连接时也能够复用现有的数据库连接
    */
    Object key = txManager.getResourceFactory();
    TransactionSynchronizationManager.bindResource(key, resource);
    TransactionStatus status = txManager.getTransaction(definition);
    try {
        runnable.run();
    } catch (Throwable t) {
        log.debug("任务执行出现异常", t);
        status.setRollbackOnly(); // 出现异常时将整个事务设置为只能回滚的状态
    } finally {
        // 移除与当前线程执行的关联关系，避免任务执行过程中的资源混乱
        TransactionSynchronizationManager.unbindResource(key);
    }
    return status;
}
```

<br />

具体 demo 地址：https://github.com/LiuXianghai-coder/Spring-Study/tree/master/spring-transaction
