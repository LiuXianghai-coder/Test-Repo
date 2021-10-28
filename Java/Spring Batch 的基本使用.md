# Spring Batch 的基本使用

## 简介

> A lightweight, comprehensive batch framework designed to enable the development of robust batch applications vital for the daily operations of enterprise systems.
>
> Spring Batch provides reusable functions that are essential in processing large volumes of records, including logging/tracing, transaction management, job processing statistics, job restart, skip, and resource management. It also provides more advanced technical services and features that will enable extremely high-volume and high performance batch jobs through optimization and partitioning techniques. Simple as well as complex, high-volume batch jobs can leverage the framework in a highly scalable manner to process significant volumes of information.

大体翻译如下：

> 一个轻量的、广泛的批处理框架，该框架的设计目的是为了支持对企业系统日常运营至关重要的批处理应用程序的开发。
>
> Spring Batch 提供了在处理大量记录时必需的可复用功能，包括日志记录/跟踪、事务管理、任务处理统计、任务重启、任务跳过和资源管理。它也提供了更加高级的技术服务和特征，通过优化和分区的方式获得极高容量和高性能的批处理任务。简单和复杂的大容量批处理任务都可以以高度可扩展的方式利用该框架来处理大量的信息



## 处理架构

Spring Batch 的处理结构如下所示：

<img src="https://spring.io/images/diagram-batch-5001274a87227c34b690542c45ca0c9d.svg" />

其中，任务的处理是在 `Step` 这个阶段定义的。在 `Step` 中，需要定义数据的读取、数据的处理、数据的写出操作，在这三个阶段中，数据的处理是真正进行数据处理的地方。具体 `Step` 的流程如下图所示：

![Batch.png](https://i.loli.net/2021/10/28/cWC7eSDdkMtpqr2.png)

- `Reader`（架构图中的 `Item Reader`）：主要的任务是定义数据的读取操作，包括读取文件的位置、对读取首先要进行的划分（如以 ',' 作为分隔符）、将读取到的文件映射到相关对象的属性字段等
- `Process`（架构图中的 `Item Processor`）：这里是真正对数据进行处理的地方，数据的处理逻辑都在这里定义
- `Writer`（架构图中的 `Item Writer`）：这个阶段的主要任务是定义数据的输出操作，包括将数据写入到数据库等



## 使用前的准备

在使用 Spring Batch 之前，首先需要创建 Spring Batch 需要的元数据表和它需要使用的元数据类型，这些可以在数据库中进行定义，这些元数据表和元数据类型是和 Spring Batch 中的域对象紧密相关的。



### 元数据表

元数据表的关联关系如下所示：

<img src="https://i.loli.net/2021/10/28/IhyUrXBEDVLHsnZ.png" alt="image.png" style="zoom:120%;" />

相关的表解释如下：

- `BATCH_JOB_INSTANCE`：与这个表相对应的是 `JobInstance` 域对象，这个域对象是整个层次结构的顶层，表示具体的任务
- ``BATCH_JOB_EXECUTION_PARAMS``：与这个表对应的是 `JobParameters` 域对象，它包含了 0 个或多个 key-value 键值对，作为每次运行任务时使用的参数，通过 `JobParameters` 对象和 `Job` 对象，可以得到唯一确定的 `JobInstance` 实例
- ``BATCH_JOB_EXECUTION``：与这个表对应的是 `JobExecution` 域对象，每次运行一个任务时，都会创建一个新的 `JobExecution`对象
- ``BATCH_STEP_EXECUTION``：与这个表对应的是 `StepExecution` 对象，这个对象与 `JobExecution` 类似，与 `JobExecution` 相关联的地方在于一个 `JobExecution` 可以有多个 `StepExecution`
- ``BATCH_JOB_EXECUTION_CONTEXT``：这个表存储的是每个 `Job` 的执行上下文信息
- ``BATCH_STEP_EXECUTION_CONTEXT``：这个表存储的是每个  `Job` 中每个 `Step` 的执行上下文信息



### 元数据类型

`BATCH_JOB_INSTANCE`、`BATCH_JOB_EXECUTION`、`BATCH_STEP_EXECUTION` 这三个表都包含了以 `_ID` 结尾的列，这个列会作为它们所在表的实际主键。然而，这个列不是由数据库产生的，而是由单独的序列来产生的，这是因为：在插入数据之后，需要在插入的数据上设置给定的键，这样才能确保它们在 `Java` 应用中的唯一性。尽管较新的 `JDBC` 支持主键自增，但是为了能够更好地兼容，因此还是有必要为这三个数据表设置对应的序列类型。

定义元数据类型的 `SQL` 如下：

```sql
CREATE SEQUENCE BATCH_STEP_EXECUTION_SEQ;
CREATE SEQUENCE BATCH_JOB_EXECUTION_SEQ;
CREATE SEQUENCE BATCH_JOB_SEQ;
```

由于有的数据库（如`MySQL`）不支持 `SEQUENCE` 这种类型，一般的做法是创建一个表来代理 `SEQUENCE`：

```sql
CREATE TABLE BATCH_STEP_EXECUTION_SEQ (ID BIGINT NOT NULL) ENGINE = InnoDB;
INSERT INTO BATCH_STEP_EXECUTION_SEQ values(0);
CREATE TABLE BATCH_JOB_EXECUTION_SEQ (ID BIGINT NOT NULL) ENGINE =InnoDB;
INSERT INTO BATCH_JOB_EXECUTION_SEQ values(0);
CREATE TABLE BATCH_JOB_SEQ (ID BIGINT NOT NULL) ENGINE = InnoDB;
INSERT INTO BATCH_JOB_SEQ values(0);
```

