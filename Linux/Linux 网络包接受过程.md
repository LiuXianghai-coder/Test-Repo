# Linux 网络包接受过程

## 网络包接受总揽

按照传统 TCP/IP 的网络栈模型，整个协议栈从底向上分为以下几层：物理层、数据链路层、网络层、传输层、应用层。在 Linux 内核中，主要负责数据链路层、网络层和传输层的功能。

在 Linux 的内核实现中，链路层协议依靠网卡驱动来实现，内核协议栈来实现网络层和传输层。具体如下图所示：

![image.png](https://s2.loli.net/2022/04/13/WuXtK3Hxym6oUid.png)

网络驱动是通过中断的方式来实现的，当有数据到达网卡设备时，会给 CPU 的相关引脚上触发一个电压变化，以通知 CPU 来处理数据。对于网络模块来说，由于处理过程比较复杂并且耗时较长，因此如果在中断函数中完成所有数据的处理，可能会导致中断处理函数持续占用 CPU，导致其它进程没有机会使用 CPU。因此 Linux 将该中断处理函数分为上下两部分，其中，上半部分只是处理最简单的工作，然后快速释放 CPU，使得其它进程能够使用 CPU；而下半部分则需要完成绝大部分的工作。在 Linux 2.4 版本之后，下半部分的实现方式为软中断，由 ksoftirqd 内核线程全权处理。

下图是自网络设备上收到数据之后的处理流程图：

![image.png](https://s2.loli.net/2022/04/13/98LQNla7zMu5GI6.png)

## Linux 启动

在具备接受网卡的数据包之前，Linux 驱动、内核协议栈等等模块之间需要做许多准备工作，比如创建 ksoftirqd 内核线程、注册各个协议对应的处理函数等。

### 创建 ksoftirqd 线程

系统初始化的时候在 `kernel/smboot.c` 中调用 `smpboot_register_percpu_thread`，该函数进一步会执行到 `spawn_ksoftirqd` 来创建 ksoftirqd 线程，具体的流程如下所示：

<img src="https://mmbiz.qpic.cn/mmbiz_png/BBjAFF4hcwpulpVSSOZzV3DkhoIk0qkZiawJB4ExTibmicqBsHWI66IhbdCFuo0BUQAlC4QqnZmFvLocbvMre1xKQ/640?wx_fmt=png&wxfrom=5&wx_lazy=1&wx_co=1" />

相关的代码如下所示：

```c
static struct smp_hotplug_thread softirq_threads = {                                                                                          
    .store          = &ksoftirqd,
    .thread_should_run  = ksoftirqd_should_run,
    .thread_fn      = run_ksoftirqd,
    .thread_comm        = "ksoftirqd/%u",
}; 

static __init int spawn_ksoftirqd(void) {
    cpuhp_setup_state_nocalls(CPUHP_SOFTIRQ_DEAD, "softirq:dead", NULL,
                              takeover_tasklets);
    BUG_ON(smpboot_register_percpu_thread(&softirq_threads));

    return 0;
}

early_initcall(spawn_ksoftirqd);
```

当 ksoftirqd 线程被创建完成之后，它就会进入自己的线程循环函数 `ksoft_should_run` 和 `run_ksoftirqd`，不停地判断是否存在相关的软中断需要处理。这里的软中断指的不仅仅是网络的软中断，还包括其他类型的软中断，具体在 `linux/interrupt.h` 头文件中有定义：

```c
enum {
    HI_SOFTIRQ=0,
    TIMER_SOFTIRQ,
    NET_TX_SOFTIRQ,
    NET_RX_SOFTIRQ,
    BLOCK_SOFTIRQ,
    IRQ_POLL_SOFTIRQ,
    TASKLET_SOFTIRQ,
    SCHED_SOFTIRQ,
    HRTIMER_SOFTIRQ,
    RCU_SOFTIRQ,    /* Preferable RCU should always be the last softirq */
    NR_SOFTIRQS
};
```

### <a name="netSubSysInit" >网络子系统初始化</a>

Linux 内核通过调用 `subsys_initcall` 来初始化各个子系统，当前讨论的是有关网络子系统的初始化，会执行 `net_dev_init` 函数，对应的代码如下所示：

```c
static int __init net_dev_init(void) {
    // 省略部分源代码。。。。。
    
    for_each_possible_cpu(i) {
        struct work_struct *flush = per_cpu_ptr(&flush_works, i);
        struct softnet_data *sd = &per_cpu(softnet_data, i);

        INIT_WORK(flush, flush_backlog);

        skb_queue_head_init(&sd->input_pkt_queue);
        skb_queue_head_init(&sd->process_queue);
        #ifdef CONFIG_XFRM_OFFLOAD
        skb_queue_head_init(&sd->xfrm_backlog);
        #endif
        INIT_LIST_HEAD(&sd->poll_list);
        sd->output_queue_tailp = &sd->output_queue;
        #ifdef CONFIG_RPS
        INIT_CSD(&sd->csd, rps_trigger_softirq, sd);
        sd->cpu = i;
        #endif

        init_gro_hash(&sd->backlog);
        sd->backlog.poll = process_backlog;
        sd->backlog.weight = weight_p;
    }
    
    // 省略一部分源代码。。。。
    open_softirq(NET_TX_SOFTIRQ, net_tx_action);
    open_softirq(NET_RX_SOFTIRQ, net_rx_action);
    
    // 省略一部分源代码。。。。
}
```

在 `net_dev_init` 中，会为每一个 CPU 都申请一个 `softnet_data`数据结构，在这个数据结构中的 `poll_list` 将会等待驱动程序将 `poll` 函数注册进来。

在 `open_softirq` 函数中为每一种软中断都注册了一个处理函数，`NET_TX_FOSTIQR` 的处理函数为 `net_tx_action`,`NET_RX_SOFTIRQ` 为 `net_rx_action`。实际上，`open_softiqr` 会将这些注册的处理函数保存到 `softirq_vec` 变量中，后续 ksoftirqd 线程在收到软中断时，也会使用这个变量来找到每一种软中断对应的处理函数。

具体的流程如下图所示：

<img src="https://mmbiz.qpic.cn/mmbiz_png/BBjAFF4hcwpulpVSSOZzV3DkhoIk0qkZPfM15PjPIACZNWDEcueAGX4TTCP0260lcenfLNcN3CdzahoTdlW0aA/640?wx_fmt=png&wxfrom=5&wx_lazy=1&wx_co=1" />

### <a name="protoReg">栈协议注册</a>

内核实现了网络层的 IP 协议，也实现了传输层的 TCP/UDP 协议。这些协议对应的实现函数分别是 `ip_rcv()`、`tcp_v4_rcv()`、`udp_rcv()`。和一般直接编写网络程序不同，内核是通过注册函数的方式来实现，Linux 内核中的 `fs_initcall` 和 `subsys_call` 类似，都是初始模块的入口。`fs_initcall` 调用 `inet_init` 之后开始执行网络协议栈的注册任务，通过 `inet_init`，将这些函数都注册到 `inet_protos` 和 `ptype_base` 数据结构中，具体过程如下图所示：

<img src=“https://mmbiz.qpic.cn/mmbiz_png/BBjAFF4hcwpulpVSSOZzV3DkhoIk0qkZV9JSezJvxlR460Wj9icia2b6tE9ibnMEwLuMzia5jjQmprXmWKiadQGtibNw/640?wx_fmt=png&wxfrom=5&wx_lazy=1&wx_co=1” />

相关的代码如下所示：

```c
// file: net/ipv4/af_inet.c
static struct packet_type ip_packet_type __read_mostly = {
    .type = cpu_to_be16(ETH_P_IP),
    .func = ip_rcv,
};

// UDP 协议的注册函数
static const struct net_protocol udp_protocol = {
    .handler =  udp_rcv, 
    .err_handler =  udp_err,
    .no_policy =    1,
    .netns_ok = 1,
};

// TCP 协议的注册函数
static const struct net_protocol tcp_protocol = {
    .early_demux    =   tcp_v4_early_demux,
    .handler    =   tcp_v4_rcv,
    .err_handler    =   tcp_v4_err,
    .no_policy  =   1,
    .netns_ok   =   1,
};

static int __init inet_init(void){
    // 省略部分源代码......
    if (inet_add_protocol(&icmp_protocol, IPPROTO_ICMP) < 0)
        pr_crit("%s: Cannot add ICMP protocol\n", __func__);
    if (inet_add_protocol(&udp_protocol, IPPROTO_UDP) < 0)
        pr_crit("%s: Cannot add UDP protocol\n", __func__);
    if (inet_add_protocol(&tcp_protocol, IPPROTO_TCP) < 0)
        pr_crit("%s: Cannot add TCP protocol\n", __func__);

    // 省略部分源代码......
    dev_add_pack(&ip_packet_type);
}
```

从上面的源代码中可以看到，`udp_protocol` 中的 Handler 为 `udp_rcv`，而 `tcp_protocol` 中的 Handler 为 `tcp_v4_rcv`，而这些实现都是在 `inet_init` 方法中通过调用 `inet_add_protocol` 函数来实现的，`inet_add_protocol` 函数对应的源代码如下所示：

```c
// file:  net/ipv4/protocol.c
int inet_add_protocol(const struct net_protocol *prot, unsigned char protocol) {
    return !cmpxchg((const struct net_protocol **)&inet_protos[protocol],
                    NULL, prot) ? 0 : -1;
}
```

`inet_add_protocol` 函数将 TCP 和 UDP 对应的处理函数都注册到了 `inet_protos` 数组中，再回到 `inet_init` 函数，查看 `dev_add_pack` 函数的具体内容：

```c
//file: net/core/dev.c
/*
	pt 中的 type 字段表示协议名，func 为 ip_rcv，在这个方法中，将会将 pt 注册到 ptype_base 的哈系表中
*/
void dev_add_pack(struct packet_type *pt) { 
    struct list_head *head = ptype_head(pt);

    spin_lock(&ptype_lock);
    list_add_rcu(&pt->list, head);
    spin_unlock(&ptype_lock);
}

static inline struct list_head *ptype_head(const struct packet_type *pt) {
    if (pt->type == htons(ETH_P_ALL))
        return pt->dev ? &pt->dev->ptype_all : &ptype_all;
    else
        return pt->dev ? &pt->dev->ptype_specific :
    &ptype_base[ntohs(pt->type) & PTYPE_HASH_MASK];
}
```

 **注意：** `inet_protos` 记录着 UDP，TCP 的处理函数地址，`ptype_base`存储着`ip_rcv()`函数的处理地址，在后续的软中断处理中，会通过 `ptype_base` 找到 `ip_rcv()` 函数的地址，进而将正确的 IP 包正确地发送到 `ip_rcv` 函数中执行。而在 `ip_rcv` 函数中，将会通过 `inet_protos` 找到 TCP 或者 UDP 的处理函数，在将包转发给 `udp_rcv` 或 `tcp_v4_rcv` 进行进一步的处理

### 网卡驱动初始化

每一个驱动处理函数都会使用 `module_init` 函数向内核注册一个初始化函数，当驱动被加载时，内核会调用这个函数。对于 Intel 的 igb 网卡驱动，对应的源代码如下：

```c
static struct pci_driver igb_driver = {
    .name     = igb_driver_name,
    .id_table = igb_pci_tbl,
    .probe    = igb_probe,
    .remove   = igb_remove,

    // 省略部分源代码
};

static int __init igb_init_module(void) {
    int ret;
    // 省略部分源代码。。。。。
    ret = pci_register_driver(&igb_driver);
    return ret;
}

module_init(igb_init_module);
```

当 `pci_register_driver` 调用完成之后，Linux 内核就已经知道了该驱动相关的信息。当网卡设备被识别之后，内核就会调用其驱动的 `probe` 方法（igb 对应的是 `igb_probe` 方法）。`probe` 方法的目的是使得设备进入 ready 状态，具体执行的操作如下图所示：

<img src=“https://mmbiz.qpic.cn/mmbiz_png/BBjAFF4hcwpulpVSSOZzV3DkhoIk0qkZZYBjJiaYYBgGHSop8UoWZw0MoIEvS6oDiaicROqerLyBVHpCTgbdApcrw/640?wx_fmt=png&wxfrom=5&wx_lazy=1&wx_co=1” />

在第 $5$ 步中，可以看到，网卡驱动实现了 ethtool 所需要的接口，也是在这里完成函数地址的注册。当 ethtool 发起一个系统调用之后，内核会找到对应的回调函数，这也是 `ethtool` 命令的工作原理。

第 $6$ 步注册的 `igb_net_dev_ops` 中包含的是 `igb_open` 等函数，该函数在网卡启动的时候就会被调用，具体的源代码如下所示：

```c
//file: drivers/net/ethernet/intel/igb/igb_main.c
static const struct net_device_ops igb_netdev_ops = {
    .ndo_open		= igb_open,
    .ndo_stop		= igb_close,
    .ndo_start_xmit		= igb_xmit_frame,
    .ndo_get_stats64	= igb_get_stats64,
    .ndo_set_rx_mode	= igb_set_rx_mode,
    // 省略一大部分源代码。。。。。
};
```

在第 $7$ 步中，在 `igb_probe` 初始化过程中，还调用了 `igb_alloc_q_vector`，该函数注册了一个 NAPI 机制所必须的 `poll` 函数，对于 igb 网卡驱动来说，这个函数就是 `igb_poll`，如下面的代码所示：

```c
//file: drivers/net/ethernet/intel/igb/igb_main.c
static int igb_alloc_q_vector(struct igb_adapter *adapter,
                              int v_count, int v_idx,
                              int txr_count, int txr_idx,
                              int rxr_count, int rxr_idx) {
    // 省略一大部分源代码。。。。。
    
    /* initialize NAPI */
    netif_napi_add(adapter->netdev, &q_vector->napi,
                   igb_poll, 64);
    
    // 省略一大部分源代码。。。。。
}
```

### <a name="startEth">启动网卡</a>

当上述步骤都完成之后，就可以启动网卡了。回忆一下上文提到驱动向内核注册的 `structure net_device_ops ` 变量，这个变量包含着网卡启动、发送包、设置 MAC 地址等回调函数。当启动一个网卡时（通过 `ifconfig eth0 up`），`net_device_ops` 中的 `igb_open` 方法将会被调用。这个过程如下图所示：

<img src="https://mmbiz.qpic.cn/mmbiz_png/BBjAFF4hcwpulpVSSOZzV3DkhoIk0qkZRVcKOWkvrPsf4PeCCSbibFxibuoXFnthOIxaM0JLMNia8MFc9YyAoQgtw/640?wx_fmt=png&wxfrom=5&wx_lazy=1&wx_co=1" />

对应的源代码如下所示：

```c
static int __igb_open(struct net_device *netdev, bool resuming) {
    /* allocate transmit descriptors */
    err = igb_setup_all_tx_resources(adapter);

    /* allocate receive descriptors */
    err = igb_setup_all_rx_resources(adapter);

    // 注册中断处理函数
    err = igb_request_irq(adapter);
    if (err)
        goto err_req_irq;

    //  启用NAPI 
    for (i = 0; i < adapter->num_q_vectors; i++)
        napi_enable(&(adapter->q_vector[i]->napi));

    // 省略一大部分源代码。。。。
}
```

在 `__igb_open` 函数中调用了 `igb_setup_all_tx_resources` 和 `igb_setup_all_rx_resources`。在 `igb_setup_all_rx_resources` 这个函数调用中，将会分配 RingBuffer，并建立内存和 RX 队列之间的映射关系（Rx 队列的数量和大小可以通过 `ethtool` 进行配置）。

查看 `igb_request_irq` 中断处理函数，对应的源代码如下所示：

```c
static int igb_request_irq(struct igb_adapter *adapter) {
    // 省略部分源代码。。。。。
    if (adapter->flags & IGB_FLAG_HAS_MSIX) {
        err = igb_request_msix(adapter);
        if (!err)
            goto request_done
            // 省略部分源代码。。。。。
    }
}

static int igb_request_msix(struct igb_adapter *adapter) {
    // 省略部分源代码。。。。。
    for (i = 0; i < num_q_vectors; i++) {
        // 省略部分源代码。。。。。
        
        err = request_irq(adapter->msix_entries[vector].vector,
                          igb_msix_ring, 0, q_vector->name,
                          q_vector);
    }
}
```

在上面的函数调用中，具体的调用过程为 `__igb_open` —> `igb_request_irq` —> `igb_request_msix`，在i `igb_request_msix` 中可以看到，对于多队列的网卡，为没一个队列都注册了一个中断处理函数 `igb_msix_ring`。可以看到，在 msix 方式下，每个 RX 队列都有对立的 MSI-X 中断，从网卡硬件层面的中断就可以设置让收到的包被不同的 CPU 处理。

当上述工作完成之后，就可以接收数据包

## 数据的接收

### 硬中断处理

首先当数据帧到达网卡之后，第一站是网卡的接收队列。网卡在分配给自己的 RingBuffer 中寻找可用的内存位置，找到之后 DMA 引擎会把数据 DMA 到之前关联的内存中，此时 CPU 对于这个过程是无感的。当 DMA 操作完成之后，网卡会向 CPU 发起一个硬中断，通知 CPU 有数据到达。

网卡的硬中断过程如下图所示：

<img src="https://mmbiz.qpic.cn/mmbiz_png/BBjAFF4hcwpulpVSSOZzV3DkhoIk0qkZ3JHgKj8GpTZWK6vC4ue2J8zK8zG3K0FshpqibY2Z367hibuFQibwYQuEw/640?wx_fmt=png&wxfrom=5&wx_lazy=1&wx_co=1" />

**注意：** 当 RingBuffer 已满的情况下，新来的数据包将会被丢弃。使用 `ifconfig` 查看网卡时，可以看到里面存在一个 overruns，表示由于 RingBuffer 已满而被丢弃的包。如果发现有丢包的存在，可以通过 `ethtool` 增大 RingBuffer 的大小

在 <a href="#startEth">启动网卡</a> 中，提及到网卡的硬中断注册函数是 `igb_msix_ring`，对应的源代码如下所示：

```c
// file: drivers/net/ethernet/intel/igb/igb_main.c
static irqreturn_t igb_msix_ring(int irq, void *data) {
    struct igb_q_vector *q_vector = data;

    /* Write the ITR value calculated from the previous interrupt. */
    igb_write_itr(q_vector); // 记录一下硬件中断频率

    napi_schedule(&q_vector->napi);

    return IRQ_HANDLED;
}
```

顺着 `napi_schedule` 调用一路跟踪，可以发现调用链为 `__napi_schedule` —> `____napi_schedule`，具体的代码如下：

```c
//file: net/core/dev.c

/* Called with irq disabled */
static inline void ____napi_schedule(struct softnet_data *sd,
                                     struct napi_struct *napi) {
    // 省略部分源代码。。。。。

    list_add_tail(&napi->poll_list, &sd->poll_list);
    __raise_softirq_irqoff(NET_RX_SOFTIRQ);
}
```

这里可以看到，`list_add_tail` 修改了 CPU 变量 `softnet_data` 中的 `poll_list`，将驱动传过来的 `poll_list` 加入到 `sodtnet_data` 的 `poll_list` 中。`softnet_data` 的 `poll_list` 是一个双向链表，其中的设备设备都带有输入的数据帧正在等待被处理。紧接着 `__raise_softirq_irqoff` 触发了一个软中断 `NET_RX_SOFTIRQ`，这个触发过程仅仅只是对一个变量进行了一次或运算，对应的代码如下所示：

```c
// file: kernel/softirq.c

void __raise_softirq_irqoff(unsigned int nr) {
    lockdep_assert_irqs_disabled();
    trace_softirq_raise(nr);
    or_softirq_pending(1UL << nr);
}
```

前面提到过，Linux 在硬中断中只完成简单必要的工作，剩下的大部分的处理都交给软中断进行处理。通过上面的代码可以看到，硬中断的处理过程真的很短，只是记录了一个寄存器，修改了一下 CPU 的 `poll_list`，然后发出一个软终中断。

### ksoftirqd 线程处理软中断

大致的处理流程如下所示：

<img src="https://mmbiz.qpic.cn/mmbiz_png/BBjAFF4hcwpulpVSSOZzV3DkhoIk0qkZXanSTtgPYeic6NVFAQHpNfbUiaE84rL0TqxfTCReUria6VdviaskNJYCnA/640?wx_fmt=png&wxfrom=5&wx_lazy=1&wx_co=1" />

在初始化 ksoftirqd 线程时，我们介绍了 ksoftirqd 线程中的两个线程函数 `ksoftirqd_should_run` 和 `run_ksoftirqd`，其中 `ksoftirqd_should_run` 函数对应的源代码如下所示：

```c
// file: kernel/softirq.c

static int ksoftirqd_should_run(unsigned int cpu) {
    return local_softirq_pending();
}
```

可以看到，这里的软中断调用了一个和硬中断一样的函数 `local_softirq_pending`，与硬中断不同的地方在于硬中断是为了写入标记，而在这里仅仅只是为了读取。如果在硬中断中设置了 `NET_RX_SOFTIRQ`，那么在这个地方就能够读取到对应的值，接下来进入线程函数 `run_ksoftirqd` 进行处理，对应的源代码如下所示：

```c
// file: kernel/softirq.c

static void run_ksoftirqd(unsigned int cpu) {
    ksoftirqd_run_begin();
    if (local_softirq_pending()) {
        __do_softirq();
        ksoftirqd_run_end();
        cond_resched();
        return;
    }
    ksoftirqd_run_end();
}
```

在 `__do_softirq` 函数中，根据当前的 CPU 类型来决定软中断类型，调用其注册的 action 方法，该函数对应的源代码如下所示：

```c
// file: kernel/softirq.c
asmlinkage __visible void __softirq_entry __do_softirq(void) {
    while ((softirq_bit = ffs(pending))) {
        unsigned int vec_nr;
        int prev_count;

        h += softirq_bit - 1;

        vec_nr = h - softirq_vec;
        prev_count = preempt_count();

        kstat_incr_softirqs_this_cpu(vec_nr);

        trace_softirq_entry(vec_nr);
        h->action(h);
        trace_softirq_exit(vec_nr);
        // 省略部分源代码。。。。
        h++;
        pending >>= softirq_bit;
    }
    
     // 省略部分源代码。。。。
}
```

在 <a href="#netSubSysInit">网络子系统初始化 </a> 一节中提到，为 `NET_RX_SOFTIRQ` 注册了处理函数 `net_rx_action`，因此此时 `net_rx_action` 将会被调用

在这里需要注意的一点是，硬中断设置中断标记、ksoftirqd 线程判断是否有中断到达，都是基于 `smp_processor_id`。这意味着只要硬中断在哪个 CPU 上响应，那么软中断也是在这个 CPU 上处理的。因此，如果发现在 Linux 的软中断中 CPU 都集中消耗在一个核上的话，那么解决方案可以是调整硬中断的 CPU 亲和性，来将硬中断打散到不同的 CPU 核上

继续查看 `net_rx_action` 的源代码：

```c
// file: net/core/dev.c 
static __latent_entropy void net_rx_action(struct softirq_action *h) {
    struct softnet_data *sd = this_cpu_ptr(&softnet_data);
    /* 
    	这两个变量的作用是用于控制 `net_rx_action` 主动退出，这是为了保证网络包接收数据是长时间占用 CPU
  */
    unsigned long time_limit = jiffies +
        usecs_to_jiffies(netdev_budget_usecs);
    int budget = netdev_budget; // 该参数可以通过内核参数进行调整

    LIST_HEAD(list);
    LIST_HEAD(repoll);

    local_irq_disable();
    list_splice_init(&sd->poll_list, &list);
    local_irq_enable();

    for (;;) {
        struct napi_struct *n;

        // 省略部分源代码。。。。
        n = list_first_entry(&list, struct napi_struct, poll_list);
        budget -= napi_poll(n, &repoll);

        if (unlikely(budget <= 0 ||
                     time_after_eq(jiffies, time_limit))) {
            sd->time_squeeze++;
            break;
        }
    }

    // 省略部分源代码。。。。
}
```

该函数的核心逻辑是获取到当前 CPU 变量 `softnet_data`，对其 `poll_list` 进行遍历，然后执行网卡驱动注册到 `poll` 函数，对于 igb 类型的网卡来说，对应 `igb_poll` 函数，对应的源代码如下所示：

```c
// file: drivers/net/ethernet/intel/igb/igb_main.c

static int igb_poll(struct napi_struct *napi, int budget) {
    // 省略部分源代码。。。。
    if (q_vector->tx.ring)
        clean_complete = igb_clean_tx_irq(q_vector, budget);

    if (q_vector->rx.ring) {
        int cleaned = igb_clean_rx_irq(q_vector, budget);

        work_done += cleaned;
        if (cleaned >= budget)
            clean_complete = false;
    }
}
```

在读取操作中，`igb_poll` 的重点工作是对 `igb_clean_rx_irq` 函数的调用，对应的源代码如下所示：

```c
static int igb_clean_rx_irq(struct igb_q_vector *q_vector, const int budget) {
    while (likely(total_packets < budget)) {
        dma_rmb();

        rx_buffer = igb_get_rx_buffer(rx_ring, size, &rx_buf_pgcnt);
        pktbuf = page_address(rx_buffer->page) + rx_buffer->page_offset;

        // 省略一大段获取 skb 的代码

        igb_put_rx_buffer(rx_ring, rx_buffer, rx_buf_pgcnt);
        cleaned_count++;

        /* fetch next buffer in frame if non-eop */
        if (igb_is_non_eop(rx_ring, rx_desc))
            continue;

        /* verify the packet layout is correct */
        if (igb_cleanup_headers(rx_ring, rx_desc, skb)) {
            skb = NULL;
            continue;
        }

        /* probably a little skewed due to removing CRC */
        total_bytes += skb->len;

        /* populate checksum, timestamp, VLAN, and protocol */
        igb_process_skb_fields(rx_ring, rx_desc, skb);

        napi_gro_receive(&q_vector->napi, skb);
    }
}
```

由于一个帧可能要占用多个 RingBuffer，因此需要在一个循环中进行数据的获取，直至帧尾部，获取下来的数据使用一个 `sk_buffer` 结构体来表示。接收完数据之后，对这些数据进行一些校验，然后设置 skb 变量的 `timstamp`、`VLAN`、`id`、`protocol` 等字段 。然后进行 `napi_gro_receive`中进行进一步的处理：

```c
// file：net/core/gro.c
gro_result_t napi_gro_receive(struct napi_struct *napi, struct sk_buff *skb) {
    gro_result_t ret;

    skb_mark_napi_id(skb, napi);
    trace_napi_gro_receive_entry(skb);

    skb_gro_reset_offset(skb, 0);

    ret = napi_skb_finish(napi, skb, dev_gro_receive(napi, skb));
    trace_napi_gro_receive_exit(ret);

    return ret;
}
EXPORT_SYMBOL(napi_gro_receive);
```

`dev_gro_receive` 这个函数代表网卡的 GRO 特性，可以简单理解为把相关的小包合并成为一个大包，这样做的目的是为了减少传递给网络栈的包的数量。

继续查看 `napi_skb_finish` 函数，对应的源代码如下所示：

```c
// file：net/core/gro.c
static gro_result_t napi_skb_finish(struct napi_struct *napi,
                                    struct sk_buff *skb,
                                    gro_result_t ret) {
    switch (ret) {
        case GRO_NORMAL:
            gro_normal_one(napi, skb, 1); // 将数据包输送到协议栈中
            break;
            // 省略部分源代码。。。。
    }

    return ret;
}
```

#### 网络协议栈处理

上文中 `gro_normal_one` 函数对应的源代码如下：

```c
// file：include/net/gro.h
static inline void gro_normal_one(struct napi_struct *napi, struct sk_buff *skb, int segs) {
    list_add_tail(&skb->list, &napi->rx_list);
    napi->rx_count += segs;
    if (napi->rx_count >= gro_normal_batch)
        gro_normal_list(napi);
}
```

该函数会根据包的协议，假如是 UDP 数据包，会依次将包送到 `ip_rcv()`、`udp_rcv()` 等处理函数中进行处理。具体的处理流程如下所示：

<img src="https://mmbiz.qpic.cn/mmbiz_png/BBjAFF4hcwpulpVSSOZzV3DkhoIk0qkZtyS5eiaemA252y9jx8BA6aAByCnblY0pbIxOblWgBR0qmAIzTCMh9bg/640?wx_fmt=png&wxfrom=5&wx_lazy=1&wx_co=1" />

接受数据包对应的源代码如下：

```c
int netif_receive_skb(struct sk_buff *skb) {
	int ret;

	trace_netif_receive_skb_entry(skb);

	ret = netif_receive_skb_internal(skb);
	trace_netif_receive_skb_exit(ret);

	return ret;
}
```

最终，这个函数会调用 `__netif_receive_skb`，该函数对应的源代码如下所示：

```c
// file：net/core/dev.c

static int __netif_receive_skb(struct sk_buff *skb) {
    int ret;

    if (sk_memalloc_socks() && skb_pfmemalloc(skb)) {
        unsigned int noreclaim_flag;

        noreclaim_flag = memalloc_noreclaim_save();
        ret = __netif_receive_skb_one_core(skb, true);
        memalloc_noreclaim_restore(noreclaim_flag);
    } else
        ret = __netif_receive_skb_one_core(skb, false);

    return ret;
}

static int __netif_receive_skb_one_core(struct sk_buff *skb, bool pfmemalloc) {
    struct net_device *orig_dev = skb->dev;
    struct packet_type *pt_prev = NULL;
    int ret;
    
    ret = __netif_receive_skb_core(&skb, pfmemalloc, &pt_prev);
    if (pt_prev)
        ret = INDIRECT_CALL_INET(pt_prev->func, ipv6_rcv, ip_rcv, skb,
                                 skb->dev, pt_prev, orig_dev);
    return ret;
}

// 这个函数会去取出 protocol，根据数据包来获取协议信息，然后遍历注册在这个协议上的回调函数列表
static int __netif_receive_skb_core(struct sk_buff **pskb, bool pfmemalloc,
                                    struct packet_type **ppt_prev) {
    // pcap 逻辑，这里会将数据送入抓包点。tcpdump 就是从这里获取数据包的
    list_for_each_entry_rcu(ptype, &ptype_all, list) {
        if (pt_prev)
            ret = deliver_skb(skb, pt_prev, orig_dev);
        pt_prev = ptype;
    }

    if (likely(!deliver_exact)) {
        deliver_ptype_list_skb(skb, &pt_prev, orig_dev, type,
                               &ptype_base[ntohs(type) &
                                           PTYPE_HASH_MASK]);
    }

    // 省略部分源代码。。。。
}

static inline void deliver_ptype_list_skb(struct sk_buff *skb,
                                          struct packet_type **pt,
                                          struct net_device *orig_dev,
                                          __be16 type,
                                          struct list_head *ptype_list) {
    struct packet_type *ptype, *pt_prev = *pt;

    list_for_each_entry_rcu(ptype, ptype_list, list) {
        if (ptype->type != type)
            continue;
        if (pt_prev)
            deliver_skb(skb, pt_prev, orig_dev);
        pt_prev = ptype;
    }
    *pt = pt_prev;
}
```

继续查看 `deliver_skb` 对应的源代码：

```c
// file：net/core/dev.c

static inline int deliver_skb(struct sk_buff *skb,
                              struct packet_type *pt_prev,
                              struct net_device *orig_dev) {
    if (unlikely(skb_orphan_frags_rx(skb, GFP_ATOMIC)))
        return -ENOMEM;
    refcount_inc(&skb->users);
    
    // 在这里调用注册的处理函数，对于 IP 包来讲，将会进入 ip_rcv 中
    return pt_prev->func(skb, skb->dev, pt_prev, orig_dev);
}
```

#### IP 协议层处理

查看 IP 协议层的处理，关键在于 `ipc_rcv` 函数，具体的源代码如下所示：

```c
// //file: net/ipv4/ip_input.c

int ip_rcv(struct sk_buff *skb, struct net_device *dev, struct packet_type *pt,
           struct net_device *orig_dev) {
    struct net *net = dev_net(dev);

    skb = ip_rcv_core(skb, net);
    if (skb == NULL)
        return NET_RX_DROP;
    
    return NF_HOOK(NFPROTO_IPV4, NF_INET_PRE_ROUTING,
                   net, NULL, skb, dev, NULL,
                   ip_rcv_finish);
}
```

`NF_HOOK` 是一个钩子函数，当执行完成钩子函数之后就会执行最后一个参数指定的函数，在这里函数是 `ip_rcv_finish`。其中 `ip_rcv_finish` 对应的源代码如下所示：

```c
// //file: net/ipv4/ip_input.c

static int ip_rcv_finish(struct net *net, struct sock *sk, struct sk_buff *skb) {
    struct net_device *dev = skb->dev;
    int ret;

    skb = l3mdev_ip_rcv(skb);
    if (!skb)
        return NET_RX_SUCCESS;

    ret = ip_rcv_finish_core(net, sk, skb, dev, NULL);
    if (ret != NET_RX_DROP)
        ret = dst_input(skb);
    return ret;
}

static int ip_rcv_finish_core(struct net *net, struct sock *sk,
                              struct sk_buff *skb, struct net_device *dev,
                              const struct sk_buff *hint) {
    // 省略部分源代码。。。。
    if (!skb_valid_dst(skb)) {
        err = ip_route_input_noref(skb, iph->daddr, iph->saddr,
                                   iph->tos, dev);
        // 省略部分源代码。。。。
    }
    
    // 省略部分源代码。。。。
}
```

继续查看 `ip_route_input_noref`，具体的源代码如下所示：

```c
// file：net/ipv4/route.c

int ip_route_input_noref(struct sk_buff *skb, __be32 daddr, __be32 saddr,
                         u8 tos, struct net_device *dev) {
    struct fib_result res;
    int err;

    tos &= IPTOS_RT_MASK;
    rcu_read_lock();
    err = ip_route_input_rcu(skb, daddr, saddr, tos, dev, &res);
    rcu_read_unlock();

    return err;
}
EXPORT_SYMBOL(ip_route_input_noref);
```

继续查看 `ip_route_input_rcu`，会调用 `ip_route_input_mc` 函数，在这个函数中，会将 `ip_local_deliver` 的值赋值给 `ds.input`，相关的源代码如下所示：

```c
static int ip_route_input_mc(struct sk_buff *skb, __be32 daddr, __be32 saddr,
                             u8 tos, struct net_device *dev, int our) {
    // 省略部分源代码。。。。

    #ifdef CONFIG_IP_MROUTE
    if (!ipv4_is_local_multicast(daddr) && IN_DEV_MFORWARD(in_dev))
        rth->dst.input = ip_mr_input;
    #endif
    RT_CACHE_STAT_INC(in_slow_mc);

    skb_dst_set(skb, &rth->dst);
    return 0;
}
```

再回到 `ip_rcv_finish` 中的 `dst_input` 函数，对应的源代码如下：

```c
// file：include/net/dst.h

static inline int dst_input(struct sk_buff *skb) {
    return INDIRECT_CALL_INET(skb_dst(skb)->input,
                              ip6_input, ip_local_deliver, skb);
}
```

`skb_dst(skb)->input`调用的input方法就是路由子系统赋的 `ip_local_deliver`，该函数对应的源代码如下所示：

```c
//file: net/ipv4/ip_input.c

int ip_local_deliver(struct sk_buff *skb){
    struct net *net = dev_net(skb->dev);

    if (ip_is_fragment(ip_hdr(skb))) {
        if (ip_defrag(net, skb, IP_DEFRAG_LOCAL_DELIVER))
            return 0;
    }

    return NF_HOOK(NFPROTO_IPV4, NF_INET_LOCAL_IN,
                   net, NULL, skb, skb->dev, NULL,
                   ip_local_deliver_finish);
}
EXPORT_SYMBOL(ip_local_deliver);

static int ip_local_deliver_finish(struct net *net, struct sock *sk, struct sk_buff *skb) {
	__skb_pull(skb, skb_network_header_len(skb));

	rcu_read_lock();
	ip_protocol_deliver_rcu(net, skb, ip_hdr(skb)->protocol);
	rcu_read_unlock();

	return 0;
}

void ip_protocol_deliver_rcu(struct net *net, struct sk_buff *skb, int protocol) {
    // 省略部分源代码。。。。
    ipprot = rcu_dereference(inet_protos[protocol]);
}
```

如同在 <a href="#protoReg">协议栈注册</a> 中提到的那样，在 `inet_protos` 中保存着 `udp_rcv()` 和 `tcp_rcv()` 函数的地址，在这里将会根据协议的类型进行分发，在这里 sdk 包将会进一步被派送到更上层的协议中，如 UDP 和 TCP

#### UDP 协议层处理

UDP 协议层接收数据包是通过 `udp_rcv()` 函数来实现的，对应的源代码如下所示：

```c
//file: net/ipv4/udp.c

int udp_rcv(struct sk_buff *skb) {
    return __udp4_lib_rcv(skb, &udp_table, IPPROTO_UDP);
}

int __udp4_lib_rcv(struct sk_buff *skb, struct udp_table *udptable,
                   int proto) {
    struct sock *sk;
    struct udphdr *uh;
    unsigned short ulen;
    struct rtable *rt = skb_rtable(skb);
    __be32 saddr, daddr;
    struct net *net = dev_net(skb->dev);
    bool refcounted;
    int drop_reason;

    // 省略部分源代码。。。。

    sk = __udp4_lib_lookup_skb(skb, uh->source, uh->dest, udptable);

    if (sk)
        return udp_unicast_rcv_skb(sk, skb, uh);
    
    // 省略部分源代码。。。。
    
    icmp_send(skb, ICMP_DEST_UNREACH, ICMP_PORT_UNREACH, 0);
    
    // 省略部分源代码。。。。
}
```

`__udp4_lib_rcv` 根据 sbk 来寻找对应的 socket，当找到之后将数据包放入到 socket 的缓存队列中，如果没有找到，则发送一个目标不可达的 ICMP 包。

其中，将数据包放入缓存队列的这个过程是 `udp_queue_rcv_skb`  来实现的，具体的源代码如下所示：

```c
static int __udp_queue_rcv_skb(struct sock *sk, struct sk_buff *skb) {
    if (likely(!udp_unexpected_gso(sk, skb)))
        return udp_queue_rcv_one_skb(sk, skb);

    // 省略部分源代码。。。。
}

static int udp_queue_rcv_one_skb(struct sock *sk, struct sk_buff *skb) {
    // 省略部分源代码。。。。
    return __udp_queue_rcv_skb(sk, skb);
}

static int __udp_queue_rcv_skb(struct sock *sk, struct sk_buff *skb) {
    // 省略部分源代码。。。。
    rc = __udp_enqueue_schedule_skb(sk, skb);
}

int __udp_enqueue_schedule_skb(struct sock *sk, struct sk_buff *skb) {
    // 省略部分源代码。。。。

    // 当队列已满，则丢弃这个数据包
    rmem = atomic_add_return(size, &sk->sk_rmem_alloc);
}
```

## rcvfrom 系统调用

通过上述的步骤，现在数据包已经放入了对应的 socket 队列中，是时候接收数据了。在理解 `rcvfrom` 系统调用之前，首先查看一下 socket 队列中的元素结构，如下图所示:

<img src="https://mmbiz.qpic.cn/mmbiz_png/BBjAFF4hcwpulpVSSOZzV3DkhoIk0qkZXmcqMaqmkPYxzMfcMgicOibRtF3NFfqOwnuneiamyTXeCm2z9kXmj2SlQ/640?wx_fmt=png&wxfrom=5&wx_lazy=1&wx_co=1" />

结构内容如下：

```c
// file: include/linux/net.h
struct socket {
    socket_state		state;
    short			type;
    unsigned long		flags;
    struct file		*file;
    struct sock		*sk;
    const struct proto_ops	*ops; // 对应的协议的方法集合
    struct socket_wq	wq;
};
```

对于 UDP 来说，`ops` 是通过 `inet_dgram_ops` 来定义的，其中注册了 `inet_recvmsg` 函数，对应的代码如下：

```c
const struct proto_ops inet_stream_ops = {
    .recvmsg       = inet_recvmsg,
    .mmap          = sock_no_mmap,

    // 省略部分其它字段
}

const struct proto_ops inet_dgram_ops = {
    .sendmsg       = inet_sendmsg,
    .recvmsg       = inet_recvmsg,
    
    // 省略部分其它字段
}
```

`socket`数据结构中的另一个数据结构`struct sock *sk`是一个非常大，非常重要的子结构体。其中的`sk_prot`又定义了二级处理函数。对于UDP协议来说，会被设置成UDP协议实现的方法集`udp_prot`

```c
//file: net/ipv4/udp.c

struct proto udp_prot = {
    .name          = "UDP",
    .owner         = THIS_MODULE,
    .close         = udp_lib_close,
    .connect       = ip4_datagram_connect,
    .sendmsg       = udp_sendmsg,
    .recvmsg       = udp_recvmsg,
    .sendpage      = udp_sendpage,
    
    // 省略部分其它字段
}
```

在熟悉 socket 的结构之后，再继续查看 `sys_rcvfrom` 的实现过程，具体如下图所示：

<img src="https://mmbiz.qpic.cn/mmbiz_png/BBjAFF4hcwpulpVSSOZzV3DkhoIk0qkZgLvVceiaywTHxTBgCicoZyTqLjcMWv062YBU6NVUr7PfQwZdPv6RaibrA/640?wx_fmt=png&wxfrom=5&wx_lazy=1&wx_co=1" />

在`inet_recvmsg`调用了`sk->sk_prot->recvmsg`，对应的源代码如下所示：

```c
int inet_recvmsg(struct socket *sock, struct msghdr *msg, size_t size,
                 int flags){
    struct sock *sk = sock->sk;
    int addr_len = 0;
    int err;
    
    // 省略部分源代码。。。。

    err = INDIRECT_CALL_2(sk->sk_prot->recvmsg, tcp_recvmsg, udp_recvmsg,
                          sk, msg, size, flags & MSG_DONTWAIT,
                          flags & ~MSG_DONTWAIT, &addr_len);
    if (err >= 0)
        msg->msg_namelen = addr_len;
    return err;
}
EXPORT_SYMBOL(inet_recvmsg);
```

对于 UDP 协议的 socket 来说，这个`sk_prot`就是`net/ipv4/udp.c`下的`struct proto udp_prot`，因此进一步的处理发生在 `udp_recvmsg`，该函数关键的地方在于调用了 `__skb_recv_datagram`，该函数的源代码如下：

```c
//file：net/core/datagram.c
struct sk_buff *__skb_recv_datagram(struct sock *sk,
                                    struct sk_buff_head *sk_queue,
                                    unsigned int flags, int *off, int *err) {
    // 省略部分源代码。。。。
    struct sk_buff *skb, *last;
    do {
        skb = __skb_try_recv_datagram(sk, sk_queue, flags, off, err,
                                      &last);
    } while (timeo &&
             !__skb_wait_for_more_packets(sk, sk_queue, err,
                                          &timeo, last));

    return NULL;
}

struct sk_buff *__skb_try_recv_datagram(struct sock *sk,
                                        struct sk_buff_head *queue,
                                        unsigned int flags, int *off, int *err,
                                        struct sk_buff **last) {
    do {
        // 省略部分源代码。。。。
        skb = __skb_try_recv_from_queue(sk, queue, flags, off, &error,
                                        last);
    } while (READ_ONCE(queue->prev) != *last);
}

// 尝试访问一 socket 队列
struct sk_buff *__skb_try_recv_from_queue(struct sock *sk,
                                          struct sk_buff_head *queue,
                                          unsigned int flags,
                                          int *off, int *err,
                                          struct sk_buff **last) {
    // 省略部分源代码。。。。
    *last = queue->prev;
    
    skb_queue_walk(queue, skb)  {
        // 省略部分源代码。。。。
    }
}
```

在上面我们看到了所谓的读取过程，就是访问`sk->sk_receive_queue`。如果没有数据，且用户也允许等待，则将调用 `wait_for_more_packets()` 执行等待操作，它加入会让用户进程进入睡眠状态

<br />

参考

<sup>[1]</sup> https://mp.weixin.qq.com/s?__biz=MjM5Njg5NDgwNA==&mid=2247484058&idx=1&sn=a2621bc27c74b313528eefbc81ee8c0f