# JVM 垃圾回收

## 什么是垃圾对象

在内存中再也不可能会被使用到的对象



判断一个对象是否是垃圾对象的方法（标记）：

- 可达性分析：从根节点开始，如果能够被访问到，则说明这个对象是可用的，否则，就说明这个对象不可达的，即是一个垃圾对象
- 引用器计数：判断当前的对象是否有其他的引用引用它，如果存在，则说明这个对象不是一个垃圾对象，否则就是一个垃圾对象



## 引用级别

- 强引用

  平常最基本的使用对象，垃圾回收不会回收这些带有强引用的对象。如果内存不足，也不会回收这一部分的引用对象，而是直接抛出 `OutOfMemoryError`异常

  ```java
  Object obj = new Object();
  ```

  这就是一个最常见的强引用

- 软引用

  对于一个只存在软引用的对象来讲，在内存空间足够的情况下不会回收它。当内存空间不足时，进行垃圾回收将会回收这些只存在软引用的对象

  一般会使用软引用实现一些对于内存敏感的缓存

- 弱引用

  弱引用的生命周期更短，只要发生 `GC`，就会直接将这些只存在若引用的对象直接进行回收，而无论当前的内存是否足够

  一般使用弱引用来实现一些规范化映射，如 `java.util.WeakHashMap`，当 `key` 或者 `value` 不再被引用时可以自动被回收

- 虚引用

  虚引用并不决定对象的生命周期，只存在虚引用的对象在任何时刻都有可能会被回收

  虚引用的主要目的是为了跟踪对象被垃圾回收器回收的活动，当垃圾回收器准备回收一个对象时，如果发现它还存在虚引用，那么就会把这个虚引用加到与之关联引用队列。

  程序可以通过判断引用队列中是否加入了虚引用，来了解被引用的对象是否要进行垃圾回收。如果程序发现某个虚拟引用已经被加入到了引用队列，那么就可以在所引用的对象的内存在回收之前采取必要的行动



## 垃圾回收算法

### 标记清除算法

> 算法流程：
>
> 1. 标记垃圾节点
> 2. 清除所有垃圾节点，不做任何额外操作
>
> 优点：清理速度较快
>
> 缺点：容易造成内存碎片化

如下图所示：

<img src="https://static001.geekbang.org/infoq/21/211a3bc9503beb5bebff30ab80ed6248.png" />

### 标记复制算法

> 算法流程：
>
> 1. 将原有内存分为两块，每次只使用其中一块内存
> 2. `GC` 时将存活的对象复制到另一空白内存，同时清空整个当前使用的内存
>
> 优点：速度快、无内存碎片
>
> 缺点：降低了系统的整个实际可用空间

如下图所示：

<img src="https://static001.geekbang.org/infoq/f7/f7abf09c61ddd49e45b87f4bbf735cbd.png" />

### 标记整理算法

> 算法流程：
>
> 1. 标记所有的存活节点
> 2. 将所有的存活节点压缩到内存的另一端
> 3. 清除所有的其它节点
>
> 优点：不会产生内存碎片、可利用内存空间足
>
> 缺点：需要耗费更多的时间（主要使用在老年代）

如下图所示：

<img src="https://static001.geekbang.org/infoq/85/85592dde987fa92751d8a1e5c5ba55ef.png" />



## 垃圾回收器

### 串行回收器

#### Serial收集器

> Serial 收集器是最基础、历史最悠久的收集器，在 JDK 1.3.1 之前是 `HotSpot`  虚拟机新生代收集器的唯一选择。

执行流程如下所示（新生代采用 `Serial`收集器，老生代采用 `Serial Old` 收集器）：

![1.png](https://i.loli.net/2021/10/01/EQvTJuzlM3nGqVH.png)

表示在垃圾收集的时候需要暂停所有的工作线程，让一个垃圾收集线程去完成垃圾的回收工作。`Serial` 的含义不仅仅只是使用一个线程去完成垃圾回收的工作，而且表示会暂停所有的用户线程来完成垃圾回收。



#### `Serial Old`收集器

Serial 收集器的老年代版本，主要的用途：一是为了在 JDK 5以及之前的版本中与 `Parallel Scavenge` 收集器搭配使用；二是作为 `CMS` 收集器发生失败后的备选方案，在并发收集发生 Concurrent Mode Failure 时使用



### 并行回收器

#### `ParNew`收集器

`ParNew` 收集器实质上是 `Serial` 收集器的多线程并发版本，除了同时使用多条线程进行垃圾收集之外，其余的行为和`Serial`收集器使用的参数完全一致（包括使用的控制参数等）

具体工作流程如下所示（新生代选择了 `ParNew`收集器，老生代就只能选择 `Serial Old` 收集器）：

![JVM.png](https://i.loli.net/2021/10/01/PLDTZByR61c2eqo.png)

#### `Parallel Scavenge` 收集器

`Parallel Scavenge` 收集器是一款新生代垃圾收集器，基于 **标记-复制算法** 实现的收集器，也能够实现并行收集的多线程收集器。`Parallel Scavenge` 收集器的特点在于致力于达到一个可控制的吞吐量。

通过 `-XX:MaxGCPauseMillis` 可以设置最大垃圾收集的停顿时间（> 0），`-XX:GCTimeRatio` 直接设置吞吐量的大小（0-100）。

此外，`Parallel Scavenge` 收集器还可以通过指定参数 `-XX:+UseAdaptiveSizePolicy` 自动地选择最佳的停顿时间或者最佳的吞吐量。



#### Parallel Scavenge

`Parallel Old` 收集器是 `Parallel Scavenge` 收集器的老年代版本，支持多线程并发执行，基于 **标记-整理** 算法实现。

具体执行流程如下所示（新生代采用 `Parallel Scavenge` 收集器，老生代采用 `Parallel Scavenge` 收集器）：

![JVM.png](https://i.loli.net/2021/10/01/reMgjSs4x7tCIDm.png)

#### `CMS` 收集器

> CMS （Concurrent Mark Sweep）是一种以获取最短回收停顿时间为目标的收集器。基于 **标记-清除** 算法实现
>
> 
>
> 运行步骤：
>
> 1. 初始标记（CMS initial mark）
> 2. 并发标记（CMS concurrent mark）
> 3. 重新标记（CMS remark）
> 4. 并发清除（CMS concurrent sweep）
>
> 在初始标记、重新标记这两个步骤中，依旧需要 `STW`。初始标记的目的仅仅只是标记一下 `GC Root` 能够直接关联到的对象，速度比较快；
>
> 并发标记阶段就是从 `GC Root` 直接关联对象开始遍历整个对象图的过程，这个过程耗时比较长但是会与用于线程一起执行；
>
> 重新标记阶段是为了修正并发标记期间，因用户继续运作而导致的标记产生变动的那一部分对象的标记记录，这个阶段的停顿时间通常回比初始标记阶段的停顿时间要长一些，但是也要远小于并发标记阶段所花费的时间；
>
> 最后并发清除阶段，清理掉标记阶段判断的已经死亡的对象，由于不需要移动存活对象，因此这个阶段也可以与用户线程同时运行

总体运行流程：

![1.png](https://i.loli.net/2021/10/01/wzauZ3fEVAh6MW1.png)

`CMS`收集器存在的缺点：

- 对处理器资源非常敏感：CPU 核心数过少时，会导致性能不佳
- 无法处理 “浮动垃圾”：清除垃圾对象时由于用户线程依旧在运行，因此这个过程依旧可以产生垃圾对象，但是不会被清除
- 基于 **标记-清除** 算法产生的内存碎片



#### `G1` 收集器

将堆区划分为多个大小相等的连续区域，这些区域被称为 `Region`，每一个 `Region` 都可以根据需要，扮演新生代的 Eden空间、Survivor空间或者老年代空间。尽管 `G1` 收集器依旧存在分代的概念，但是新生代和老生代已经不再是固定的了。

`G1`收集器的内存布局：

<img src="https://i.loli.net/2021/07/02/ABYoU7PcnZ2h8Km.png" alt="image.png" style="zoom:120%;" />

- 大对象

  H 代表 Humongous，表示这些 Region 存储的是巨大对象（H-Obj）（超过 `Region` 区大小的一般就可以认为是一个大对象，可以通过 `-XX: G1HeapRegionSize` 指定 `Region` 的大小）。

  H-Obj 的特征：

  - H-Obj 直接分配到 Old Region，防止反复拷贝和移动。
  - H-Obj 在 global concurrent marking 阶段的 clean up 和 `Full GC ` 阶段回收
    - 在分配 H-Obj 之前会检查是否超过 initiating  heap occupancy percent 和 the marking threshold，如果超过，则启动 global concurrent marking，为的是提早回收，防止 evacuation 和 Full GC 

  为了减少连续 H-Obj 分配对 GC 的影响，需要把大对象转换为普通的对象，同时建议增大 Region Size

  一个 Region 的大小可以通过参数 `-XX:G1HeapRegionSize` 指定，取值范围为 1 M 到 32 M，如果未设置该值，则 `G1` 会根据 Heap 大小自动指定。

`G1` 收集器的操作流程

- `Young GC`

  > 选定所有年轻代里的 Region 个数，即年轻代内存大小，来控制 `Young GC` 的开销

- global concurrent marking 

  > 1. 初始标记（Initial Marking）：仅仅只是标记一下 `GC Roots` 能够直接关联到的对象，这个阶段需要停止用户线程，但是耗时非常短，可以忽略不计。
  > 2. 并发标记（Concurrent Marking）：从 `GC Roots` 开始对堆中的对象进行可达性分析，递归扫描整个堆里的对象图，找出需要回收的对象，这个阶段耗时比较长，但是可以和用户线程并发地执行。
  > 3. 最终标记（Final Marking）：对用户线程做另一个短暂的暂停，用于处理并发阶段结束后仍遗留下来的最后那少量的垃圾对象
  > 4. 筛选回收（Live Data Counting and Evacuation）：负责更新 `Region` 的统计数据，对各个 `Region` 的回收价值和成本进行排序，根据用户期望的停顿时间来执行回收计划。这里的操作涉及到对象在内存的移动，因此必须暂停用户线程。这个操作是通过使用多个线程来完成的。
  >
  > 如下图所示：
  >
  > ![JVM.png](https://i.loli.net/2021/10/02/xnqKGa2SCewuHWY.png)

- `MixedGC`

  > ​	选定所有年轻代里的 Region，外加根据 global concurrent marking 统计得出收益高的若干老年代 Region。在用户指定的开销目标范围内尽可能地选择收益高的老年代 Region。



注意：`G1` 不提供 `Full GC`，当 `Mixed GC` 无法跟上程序分配内存额速度，导致老年代填满无法继续进行 `Mixed GC`，就会使用 `Serial Old GC`  （`Full GC`） 来回收整个堆区。



`G1` 相比较于 `CMS`，有以下不同：

1. `G1` 的垃圾回收器基于标记—整理算法，因此得到的空间时连续的，避免了 `CMS` 由于不连续空间造成的问题。
2. `G1` 的内存结构与 `CMS` 有很大不同，`G1` 将 内存划分为固定大小的 Region （2 的整次幂），内存的回收以 Region 为单位。
3. `G1` 的 `STW` 可控，`G1` 在停顿时间上添加了预测机制，可以指定期望停顿时间。



### 低延迟回收器

#### Shenandoah 收集器

`Shenandoah` 收集器 是 `OpenJDK` 中特有的一个垃圾收集器。该收集器的目标是实现一种能在任何堆内存下都可以把垃圾收集的停顿时间限制在十毫秒以内。

处理流程：

1. 初始标记：首先标记 `GC Roots` 直接关联的对象，这个阶段依旧是需要 `STW` 
2. 并发标记：遍历图对象，标记出所有的可达对象，这个阶段是和用户线程一起并发运行的
3. 最终标记：处理在并发标记过程中由用户线程产生的垃圾对象，并在这个阶段统计出回收价值最高的 `Region`，将这些 `Region` 构成一个回收集。这个阶段也会有短暂的 `STW`
4. 并发清理：这个阶段用于清理整个`Region` 都没有存活对象的 `Region`
5. 并发回收：把回收集里的存活对象复制一份到其他未被使用的 `Region` 中，这个过程是并发执行的
6. 初始引用更新：并发回收阶段复制对象结束之后，需要把堆中所有指向旧对象的引用修正到复制之后的新地址，这个操作被称为“引用更新”。这个阶段并没有真正开始执行引用更新，只是为了建立一个线程集合点，确保所有并发回收阶段阶段中的收集器线程都已经完成分配给它们的对象移动任务而已。这个阶段会有短暂的`STW`。
7. 并发引用更新：真正开始执行引用更新的任务，这个阶段是和用户线程并发执行的
8. 最终引用更新：解决对引用对象的更新之后，还需要修正在 `GC Roots` 中的引用。这个阶段也会有一次短暂的 `STW`
9. 并发清理：清理之前回收集中的 `Region` 空间

工作流程如下所示：

![JVM.png](https://i.loli.net/2021/10/02/28ykY6DiuhIaCBl.png)



#### `ZGC` 收集器

一款新提出的低延迟垃圾回收器，主要的设计目标为：停顿时间不会超过 10 ms；停顿的时间不会随着堆的大小或者活跃对象的大小增加而增加；支持 8 MB ～ 4 TB 级别的堆。

##### 着色指针

> 着色指针是一种把信息存储在指针中的方式

`ZGC`  只支持 64 位的系统，把 64 位的虚拟地址划分为多个子空间，如下图所示：

<img src="https://p0.meituan.net/travelcube/f620aa44eb0a756467889e64e13ee86338446.png@1568w_322h_80q" style="zoom:80%" />

其中，[0~4 TB) 对应Java堆，[4 TB ~ 8 TB) 称为 `M0` 地址空间，[8 TB ~ 12 TB) 称为`M1`地址空间，[12 TB ~ 16 TB) 预留未使用，[16 TB ~ 20 TB) 称为Remapped空间。

当应用程序创建对象时，首先在堆中申请一个虚拟内存，但是这个申请的虚拟内存并不会直接映射到真正的物理地址。`ZGC` 收集器会首先为该对象在 `M0`、`M1`、`Remapped` 地址中分别申请一个地址，且这三个虚拟地址对应同一个物理地址，但是这三个空间的地址在同一时间只能有一个空间有效。通过这三个虚拟空间，可以降低 `GC` 的停顿时间



为了与上述的地址相对应，`ZGC` 实际上只使用了 64 位地址空间的 0 ～ 46 位，其中，0 ～ 41 位表示地址空间，第 42 ～ 45 位存储元数据，如下图所示：

<img src="https://p0.meituan.net/travelcube/507f599016eafffa0b98de7585a1c80b338346.png@2080w_624h_80q" />

`ZGC` 收集器将对象的存活信息放在 42 ～ 42 位中，这与传统的垃圾回收将对象的存货信息放在对象头中完全不同



##### 读屏障

> 读屏障是JVM向应用代码插入一小段代码的技术。当应用线程从堆中读取对象引用时，就会执行这段代码。需要注意的是，仅“从堆中读取对象引用”才会触发这段代码。

如下所示：

```java
Object o = obj.FieldA   // 从堆中读取引用，需要加入屏障
<Load barrier>

Object p = o  // 无需加入屏障，因为不是从堆中读取引用
o.dosomething() // 无需加入屏障，因为不是从堆中读取引用
int i =  obj.FieldB  //无需加入屏障，因为不是对象引用
```

读屏障的主要作用：在对象标记和转移的过程中，用于确定对象的引用是否满足条件，并作出相应的动作



地址视图的切换过程：

- 初始化：`ZGC` 初始化之后，整个内存空间的地址视图被初始化为 `Remapped`。程序正常运行，在内存中分配对象，满足一定条件后垃圾回收启动，此时进入标记阶段
- 并发标记阶段：第一次进入标记阶段时视图为 `M0`，如果对象被 `GC` 标记线程或者应用线程访问过，那么就将对象的地址视图从 `Remapped` 转换为 `M0`。所以，在标记结束阶段之后，对象的地址要么是 `M0` 视图，要么是 `Remapped` 视图。如果对象的地址视图是 `M0`，那么就说明该对象是活跃的；否则，说明这个对象不是活跃的
- 并发转移阶段：标记结束之后进入转移阶段，此时地址视图再次被设置为 `Remapped`，如果对象被 `GC` 转移线程或者应用线程访问过，那么就将对象的地址视图从 `M0` 调整为 `Remapped`

其实，在标记阶段存在两个地址视图 `M0` 和 `M1`，上面的过程显示只用了一个地址视图。之所以设计成两个，是为了区别前一次标记和当前标记。也即，第二次进入并发标记阶段后，地址视图调整为 `M1`，而非 `M0`。

上述过程如下图所示：

<img src="https://p0.meituan.net/travelcube/a621733099b8fda2a0f38a8859e6a114213563.png@2070w_806h_80q" />

由于将对象的存货信息保存在着色指针中，修改对象的存活信息只需要修改对应的标记位而不用找到对应的对象再修改，因此提高了性能



主要工作流程：

1. 并发标记：遍历对象图，做可达性分析，这个阶段也会造成短暂的停顿。这个标记是在颜色指针上完成的
2. 并发预备重新分配：根据特定的查询条件统计出本次收集过程要清理哪些 `Region`，将这些要清理的 `Region` 组成重分配集
3. 并发重分配：将重分配集中的存活对象复制到新的 `Region` 上，并为重分配集中的每个 `Region` 维护一个转发表，记录旧对象到新对象的转换关系。由于染色指针的存在，`ZGC` 收集器能仅从引用上就能明确得知一个对象是否在重分配中。如果用户线程此时并发地访问了位于重分配集中的对象，这次的访问就会被预先设置的**读屏障**所截获，然后根据 `Region` 上的转发表将这个访问转发到新复制的对象上，同时更新该引用的值。
4. 并发重映射：修正整个堆中指向重分配集合中旧对象的所有引用。

具体流程如下图所示：

![ZGC.png](https://i.loli.net/2021/10/30/VWQIyzbNP6YJrq3.png)

参考：

- https://tech.meituan.com/2020/08/06/new-zgc-practice-in-meituan.html
- 《深入理解Java虚拟机：JVM高级特性与最佳实践（第3版）》