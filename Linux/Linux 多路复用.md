# Linux 多路复用（多路转接）

## 出现原因

如果需要从一个文件描述符中读取数据，然后将数据写入到另一个文件描述符时，可以按照如下的阻塞 IO ：

```c
while ((n = read(STDIN_FILENO, buf, BUFFER_SIZE)) > 0) {
    if (write(STDOUT, buf, n) != n) {
        fprintf(stderr, "write error");
    }
}
```

这种方式在只有一个读 `fd` 和一个写 `fd` 的情况下，这种方式能够正常工作，不会有什么问题。但是如果存在多个文件描述符需要被读取，那么在这种情况下，如果一直阻塞等待某个文件描述符读取完成，那么剩下的待读取文件描述符即使能够被读取，也会一直等待。为了解决这个问题，引入多路复用（多路转接）技术来进行处理

假设现在让我们自己设计 `telnet` ，在这里我们主要考虑一下 `telnet` 和远程主机之间的通信问题：`telnet` 从终端（标准输入）中读取输入，将读取到的输入数据写入到网络连接（`fd`）上，同时从网络连接中读取数据，将读取到的数据写回到终端上；在网络连接的另一端，`telneted` 守护进程读取用户输入的命令，并将其返回到终端，具体情况如下图所示：

![telnet.png](https://s2.loli.net/2022/03/24/yB8NYRgHSkszopm.png)

由于 `telnet` 有两个输入，因此传统的阻塞读的方式是不可取的，因为无法知道什么时候读取哪个输入。

如果没有多路复用技术，那么可以考虑以下几种方式解决这个问题：

- 将一个进程通过 `fork`，变成两个进程

    由于变成了两个子进程，可以单独地对每个输入执行阻塞读的操作。但是这样又会产生新的问题：如果 `telnet` 断开了连接，那么需要将对应的进程关闭，这个操作可以通过信号量来进行操作，但是使得程序变得更加复杂

- 不使用进程，而是使用两个线程

    通过创建两个线程来分别维护两个输入的读取，避免了由于进程间通信带来的复杂性，但是由于需要同步这两个线程，因此在复杂性这一方面不见得会比使用两个进程的方式更好

- 依旧使用一个进程来进行处理，但是使用非阻塞 IO

    将两个输入都变成非阻塞的，对第一个输入发送一个 `read`，如果该输入上有数据，则读取数据并处理它；如果没有数据可读，则直接返回，然后对第二个输入执行类似的操作，在此之后，等待一定的时间，再次执行相同的处理。这种方式被成为 “轮询”，大部分情况下都是无数据可读的，浪费了 CPU 的处理时间，因此应该避免使用

- 还是使用一个进程，但是数据的读取采用异步 IO

    采用异步 IO 的方式来进行处理，每当有准备好的 IO 可以进行时，发送信号通知进程进行处理。这种信号对于每个进程来讲都只有一个，因此当多个 IO 准备好的情况下，无法正确判断到底是那个 IO 准备好了，特别是，能够使用信号量的数量是有限的，因此当文件描述符变多时将会存在问题。

传统的方式都未能很好地处理 `telnet` 存在的问题，IO 多路复用技术可能是解决该问题比较好的一种方案。

IO 多路复用描述如下：首先构造一个文件描述符列表，然后调用一个函数，直到这个列表中至少存在一个 IO 已经准备好的情况下，该函数才返回，在从该函数返回时，进程可以得到已经准备好 IO 的文件描述符号

## 多路复用函数

### select 和 pselect

select：调用 select 函数需要以下几个参数：

- 待检查的 `fd` 集合
- 对于每个 `fd` 我们所关心的操作：读、写以及异常操作
- 希望等待多长时间（可以永远等待、等待一个固定时间或者根本不等待）

调用 select 之后，可以通过 select 得到以下内容：

- 已经准备好的 `fd` 的数量
- 对于读、写或异常这三个条件中的每一个，哪些 `fd` 已经准备好

select 函数的原型如下所示：

```c
#include <sys/select.h>

int select(int nfds, fd_set *readfds, fd_set *writefds,
           fd_set *exceptfds, struct timeval *timeout);
```

函数的最后一个参数  $timeout$ 表示 select 函数愿意等待的时间长度，有以下三种情况：

1. `tvptr == NULL`：表示永远等待，如果捕捉到一个信号则中断此状态
2. `tvptr->tv_sec == 0 && tvptr->tv_usec == 0` ：表示不等待
3. `tvptr->tv_sec != 0 || tvptr->tv_usec != 0` ：表示等待指定的秒数和微秒数

对于中间的三个参数 $readfds$、$writefds$、$exceptfds$ 表示指向 `fd` 集合的指针，具体的状态如下所示：

<img src="https://s2.loli.net/2022/03/24/w8GNBtSRnhCmkDq.png" alt="select.png" style="zoom:80%;" />

具体的集合实现可以不同，这里假设只是一个简单的字节数组。对于 `fd_set` 数据类型，可以通过调用以下几个函数：

```c
#include <sys/select.h>

void FD_CLR(int fd, fd_set *set); // 清除 set 中的某一位 fd
int  FD_ISSET(int fd, fd_set *set); // 如果 fd 在 set 中，返回非 0 值，否则返回 0 值
void FD_SET(int fd, fd_set *set); // 开启 set 中的 fd
void FD_ZERO(fd_set *set); // 将 set 的所有位都设置为 0
```

对应的示例如下：

```c
#include <stdio.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>

int main(int argc, char ** argv) {
    fd_set rfds; 
    struct timeval tv;
    int retval;

    FD_ZERO(&rfds); // 默认 fd 问终端输入，fd 为 0
    FD_SET(0, &rfds);

    /* 等待 5 秒 */
    tv.tv_sec = 5;
    tv.tv_usec = 0;
    retval = select(1, &rfds, NULL, NULL, &tv);

    if (retval == -1) {
        perror("select()");
    } else if (retval) {
        printf("Data is available now.\n");
        /* FD_ISSET(0, &rfds) will be true. */
    } else {
        printf("No data within five seconds.\n");
    }

    return 0;
}
```

### poll

poll 接口类似于 select，和 select 最大的不同之处在于 poll 可以支持任意类型的文件描述符，函数的原型如下所示：

```c
#include <poll.h>
int poll(struct pollfd *fds, nfds_t nfds, int timeout);
```

其中，`struct pollfd` 的具体结构如下所示：

```c
struct pollfd {
    int   fd;         /* 文件描述符号 */
    short events;     /* 对当前 fd 关心的事件 */
    short revents;    /* 在该 fd 上发生的时间 */
}
```

### epoll

epoll 是现代多路复用中使用最为广泛的多路复用接口，性能想比较于 `select有` 和 `poll` 有很大的改进，这里重点分析一下 `epoll` 以及它的实现原理

#### 连接的创建

首先，对于每一个新创建的 `socket`连接，都会生成对应的 `fd`，这个 `fd` 将会保存到当前进程持有的 “打开文件列表” 中，具体的情况如下图所示：

![socket.png](https://s2.loli.net/2022/03/25/BJTURrM6jcKs1gI.png)

#### 创建 eventpoll

当调用 `epoll_create` 时，会在内核中生成一个 `struct eventpoll` 的内核对象，并同时将这个对象放入到进程打开的文件描述符列表中，此时的情况可能如下图所示：

![epoll_socket.png](https://s2.loli.net/2022/03/25/v451jS9sieWlTbB.png)

对于 `struct eventpoll`，在这里我们主要关心的字段属性如下：

![epoll_data.png](https://s2.loli.net/2022/03/25/aEiMgjhAvutcBG4.png)

对上图的字段解释如下：

- **wq**：等待队列，当调用 `epoll` 时阻塞的进程会放入这个队列，当数据准备就绪时，在这个队列上找到对应的阻塞进程
- **rblist**：已经就绪的 `fd` 链表。当有的连接已经准备就绪时，会调用对应的回调函数，将这个连接对应的 `fd` 当如这个链表中，这样进程只需要在这个链表中获取就绪的 `fd`，而不需要遍历整个 `fd` 列表
- **rbr**：为了支持大量连接的高效查找、插入和删除，在 `eventpoll` 中会维护一棵红黑树，通过这颗树来管理已经建立的所有的 `socket` 连接

#### 添加 socket

当创建一个 `socket` 连接之后，需要将这个连接对应的 `fd` 注册到 `eventpoll` 中，注册时，内核会做以下几件事情：

1. 创建一个新的红黑树节点 `epollitem`
2. 添加等待事件到该 `socket` 的等待队列中，并注册回调函数 `ep_poll_callback`
3. 将 `epitem` 插入到 `epoll` 对象的红黑树中

以上文的例子为例，将原有的两个 `socket` 注册到 `epoll` 之后的情况如下图所示：

![epoll_add.png](https://s2.loli.net/2022/03/26/cKNEbhZa8uwrfg5.png)

#### epoll_wait 等待接收

如果进程 A 在调用 `epoll_wait` 时发现有 `socket` 可用，那么将会从 `eventpoll` 的 `rdlist` 中获取一个 `socket` 进行处理，如果不存在可用的 `socket`，那么需要按照以下的步骤进行处理：

1. 调用 `epoll_wait`，检查就绪队列中是否存在就绪的 `socket`
2. 如果不存在就绪的 `socket`，那么首先需要定义一个等待队列节点，准备添加到 `eventpoll` 的等待队列中
3. 将准备好的等待队列节点插入到 `eventpoll` 的等待队列中
4. 进程挂起，让出当前持有的 CPU

具体流程如下图所示：

<img src="https://s2.loli.net/2022/03/26/UeaAh7wfPpKZRBV.png" alt="epoll_await.png" style="zoom:80%;" />

#### 唤醒进程

当 socket 接收数据完成之后，会通过调用已经注册到等待队列节点中的回调函数，在 socket 的等待队列中，这个回调函数是 `ep_poll_callback`，该函数对应的源代码如下：

```c
static int ep_poll_callback(wait_queue_t *wait, unsigned mode, int sync, void *key)
{
    //获取 wait 对应的 epitem
    struct epitem *epi = ep_item_from_wait(wait);

    //获取 epitem 对应的 eventpoll 结构体
    struct eventpoll *ep = epi->ep;

    //1. 将当前epitem 添加到 eventpoll 的就绪队列中
    list_add_tail(&epi->rdllink, &ep->rdllist);

    //2. 查看 eventpoll 的等待队列上是否有在等待
    if (waitqueue_active(&ep->wq))
        wake_up_locked(&ep->wq);
    
    // 省略部分源代码
}
```

唤醒之后的进程会发现 `eventepoll` 的就绪队列中已经存在就绪的 `socket`，因此会正常执行

#### 水平触发和边沿触发

当 `socket` 准备好时，在唤醒 `eventpoll` 中等待队列的进程时，有两种触发模式：水平出发和边沿触发。

边沿触发：仅在新的事件被首次加入到 `eventepoll` 的就绪队列中时才触发，比如：当 `socket buffer` 从空变为非空、`buffer` 数据增多、进程对 `buffer` 修改、`buffer` 数据减少等都会执行一次触发

水平触发：在事件状态未变更之前，将会不断触发唤醒事件，由于这个触发模式涵盖了大部分的场景，因此这是 `epoll` 的默认触发模式

举两个例子：

- 假设现在注册到 `eventpoll` 的一个 `socket` 的缓冲区已经可读了，那么在水平触发的模式下，只要该事件没有被处理完毕（缓冲区不为空），那么每次调用 `epoll_wait` 时都会包含该事件，直到该事件被处理完成；而如果是在边沿触发的模式下，只会触发一次读事件，不会反复通知
- 假设现在注册到 `eventpoll` 的一个 `socket` 的缓冲区已经可写了，在水平触发的模式下，只要该 `socket` 对应的缓冲区没有被写满，就会一直触发 “可写” 事件；如果是在边沿触发的模式下，只会在初始时触发一次 “可写” 事件

以下两种情况下的 `fd` 推荐使用 “边沿触发”：

- `read` 或者 `write` 系统调用返回了 `EAGAIN`
- 非阻塞的文件描述符

边沿触发的模式可能会有以下的问题：

- 如果一次可读的 IO 很大，由于你不得不一次性将这些 IO 处理完成，因此很可能会导致此时你无法处理其它的 `fd`



<br />

参考：

<sup>[1]</sup> 《Unix 环境高级编程》（第三版）

<sup>[2]</sup> https://mp.weixin.qq.com/s?__biz=MzI3NzA5MzUxNA==&mid=2664609790&idx=1&sn=1e8db814b07314f11987d05a2d39eff4

<sup>[3]</sup> https://mp.weixin.qq.com/s?__biz=MzkzMTIyNzM5NA==&mid=2247486775&idx=1&sn=c77e367a5c5284ce970f89afaa1fecad

<sup>[4]</sup> https://zh.wikipedia.org/wiki/Epoll