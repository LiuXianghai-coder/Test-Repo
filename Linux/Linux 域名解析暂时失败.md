# Linux 域名解析暂时失败

## 问题描述

在安装完成 Ubuntu 22.04 之后，偶尔会遇到 “域名解析暂时失效” 的问题。当出现这个问题时，可能浏览器依旧能够正常使用，但是使用终端访问网络就无法找到网址对应的 IP 地址，由于缺少 IP，因此连接便无法建立。

比如，一般情况下会使用 `ping` 命令检查网络是否能够连接，一般在国内会尝试 `ping` 到 `www.baidu.com`，如下所示：

```shell
$ ping www.baidu.com
ping: www.baidu.com: 域名解析暂时失败
```

然而此时的；网络确实是可用的，如果将 `ping` 的 URL 换成对应的 IP 地址，那么应该能够连通。如下所示：

```shell
$ ping -c 10 36.152.44.95
PING 36.152.44.95 (36.152.44.95) 56(84) bytes of data.
64 bytes from 36.152.44.95: icmp_seq=1 ttl=52 time=75.5 ms
64 bytes from 36.152.44.95: icmp_seq=2 ttl=52 time=68.6 ms
64 bytes from 36.152.44.95: icmp_seq=3 ttl=52 time=71.0 ms
64 bytes from 36.152.44.95: icmp_seq=4 ttl=52 time=64.8 ms
64 bytes from 36.152.44.95: icmp_seq=5 ttl=52 time=67.7 ms
64 bytes from 36.152.44.95: icmp_seq=6 ttl=52 time=82.7 ms
64 bytes from 36.152.44.95: icmp_seq=7 ttl=52 time=80.2 ms
64 bytes from 36.152.44.95: icmp_seq=8 ttl=52 time=80.9 ms
64 bytes from 36.152.44.95: icmp_seq=9 ttl=52 time=91.7 ms
64 bytes from 36.152.44.95: icmp_seq=10 ttl=52 time=79.4 ms

--- 36.152.44.95 ping statistics ---
10 packets transmitted, 10 received, 0% packet loss, time 9015ms
rtt min/avg/max/mdev = 64.765/76.240/91.676/7.873 ms
```

在这种情况下，大量的系统应用可能都无法正常工作，比如 Intelij IDEA 就无法下载对应的 依赖项，因为此时的依赖项的下载地址已经找不到对应的 IP，连接无法建立

## 出现原因

这种问题的出现大概率是由于域名服务配置错误产生的，如果此时通过 `resolvectl` 命令查看相关的命令服务器的配置，可能输出如下所示：

```shell
$ resolvectl 
Global
       Protocols: -LLMNR -mDNS -DNSOverTLS DNSSEC=no/unsupported
resolv.conf mode: uplink

Link 2 (enp3s0)
Current Scopes: none
     Protocols: -DefaultRoute +LLMNR -mDNS -DNSOverTLS DNSSEC=no/unsupported

Link 3 (wlp2s0)
    Current Scopes: DNS
         Protocols: +DefaultRoute +LLMNR -mDNS -DNSOverTLS DNSSEC=no/unsupported
Current DNS Server: 192.168.0.1
       DNS Servers: 192.168.0.1 240e:40:8000::10
```

可以看到，此时的 DNS (Domain Name Server) 为 `192.168.0.1`，这很明显不是一个正确的 DNS （192 开头的一般是一个局域网的 IP 地址）。也就是说，此时的 DNS 的配置出现了问题

这个问题实际上是 `systemd` 存在的一个 [bug](https://bugs.launchpad.net/ubuntu/+source/systemd/+bug/1624320)

## 解决方案

只要正确配置 DNS 即可解决这个问题，在我遇到的情况中，由于网络配置的原因（所谓的移动 WIFI），Ubuntu 会通过 DHCP 自动设置对应的 DNS 地址，因此手动修改 DNS 是无效的。

比较有效的手段是直接禁用 Ubuntu 提供的 `systemd-resolved` 服务，这样就系统就不会自动配置 DNS，如下所示：

```shell
sudo systemctl disable systemd-resolved
sudo systemctl stop systemd-resolved
```

然后手动修改 `/etc/resolv.conf` 中配置的 DNS 地址：

```shell
# 默认情况下该文件是一个链接文件，需要取消这个链接
sudo unlink /etc/resolv.conf

# 然后创建 /etc/resolv.conf 配置文件
echo nameserver 8.8.8.8 | sudo tee /etc/resolv.conf
```

之后重新启动即可
