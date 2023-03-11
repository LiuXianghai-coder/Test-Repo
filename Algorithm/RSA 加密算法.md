# RSA 加密算法

> **RSA加密算法**是一种非对称加密算法，在公开密钥加密和电子商业中被广泛使用。RSA是由罗纳德·李维斯特（Ron Rivest）、阿迪·萨莫尔（Adi Shamir）和伦纳德·阿德曼（Leonard Adleman）在1977年一起提出的

<sup>[1]</sup>

RSA 加密算法的可靠性源自于对于极大的整数做因数分解很难在有限的时间内得到有效的解，在未来的某一天，随着计算机性能的不断提高，RSA 算法的可靠性可能会降低，但是就目前的计算机来讲很难通过暴力的方式直接破解经过 RSA 算法加密之后的信息

## 数学基础

- 互质关系
  
    对于两个正整数，如果这两个正整数除了 $1$ 之外没有任何公因数，那么就将这两个数称为两者之间是 “互质” 的，比如，对于 $7$  和 $12$，由于它们之间没有除了 $1$ 之外的公因数，因此它们之间的关系就是 “互质” 的
  
    根据定义，可以得到以下的关系：
  
  - 任意的两个质数将会构成 “互质” 关系
  - 如果一个数是一个质数，那么只要另一个数不是前一个数的倍数，那么这两者之间将会构成 “互质” 关系
  - 对于两个树，如果较大的那个数是质数，那么这两者之间同样构成 “互质“ 关系
  - $1$ 和任意的一个自然数都会构成 “互质” 关系
  - 对于任意的大于 $1$ 的整数 $p$，那么 $p$  和 $p - 1$ 将会构成 “互质” 关系
  - 对于任意的大于 $1$ 的整数 $p$，如果 $p$ 是一个奇数，那么 $p$ 和 $p - 2$ 将会构成  “互质” 关系

- 欧拉函数 [4]
  
  > 在数论中，对于给定的任意正整数 $n$，欧拉函数 $\varphi(n)$ 表示小于等于 $n$ 的正整数中与 $n$ 互质的数的个数
  
    对于任意的正整数，都可以写成一系列质数的积，即：
    $$
    n = p_1^{k_1}p_2^{k_2}……p_r^{k_r}\qquad(p_r 表示质数，k_r 表示出现相乘次数)
    $$
    此时，欧拉函数 $\varphi(n)$ 的计算公式如下：
    $$
    \begin{aligned}
    \varphi(n) &= p_1^{k_1 - 1} p_2^{k_2 - 1}……p_r^{k_r - 1}(p_1-1)(p_2-1)……(p_r-1) \\
    &=n(1 - \frac{1}{p_1})(1 - \frac{1}{p_2})……(1 - \frac{1}{p_r})
    \end{aligned}
    $$
    欧拉函数的积表示形式如下：
    $$
    \varphi(mn) = \varphi(m)\varphi(n)\qquad (m, n 互质)
    $$
    特别地，对于 $n$ 是质数的情况，有 $\varphi(n) = n - 1$，这是因为当 $n$ 是质数时，它和所有小于它的正整数都是 “互质” 的 
  
  公式的证明请参考：https://zh.wikipedia.org/wiki/%E6%AC%A7%E6%8B%89%E5%87%BD%E6%95%B0

- 欧拉定理<sup>[2]</sup>
  
    如果两个正整数 $a$ 和 $n$ 互质，那么则有如下的等式成立：
    $$
    a^{\varphi(n)} \equiv 1\pmod{n}
    $$
    即：$a$ 的 $\varphi(n)$ 次方被 $n$ 取余后剩 $1$​

- 模反元素
  
    如果两个正整数 $a$ 和 $n$ 互质，那么一定可以找到整数 $b$，使得 $a*b - 1$ 被 $n$ 整除，即：
    $$
    ab \equiv 1\pmod{n}
    $$
    此时 $b$ 就被称为 $a$ 的模反元素，这是由于
    $$
    a^{\varphi(n)} = a * a^{\varphi(n) - 1} \equiv 1\pmod{n}
    $$
    因此，$a^{\varphi(n) - 1}$ 就是 $a$ 的模反元素

## 算法原理

### 公钥和私钥

1. 随意选择两个大的质数 $p$ 和 $q$，计算两者的积 $N = p*q$
2. 根据上文提到的欧拉函数，求得 $r = \varphi(N) = \varphi(p)*\varphi(q)=(p - 1)*(q - 1)$​
3. 选择一个小于 $r$ 的正整数 $e$，使得 $e$ 和 $r$ 互质，并求得 $e$ 关于 $r$ 的模反元素 $d$
4. 删除 $p$ 和 $q$

经过上述操作之后，得到的 $(N, e)$ 就被称之为公钥，而 $(N, d)$ 就被称之为私钥。一般会选择两个非常大的质数，经过操作之后会再将这两个数字进行转码，就是一般见到的存储形式x`

### 消息的加密

假如现在想要发送一个消息 $m$ 到指定的地点，由于公钥是可见的，因此首先将 $m$ 通过公钥 $(N, e)$ 转换为对应的整数（由于数据在计算机上都是通过二进制的方式存储的，可以以读取数字的方式读取信息），一般信息都会很长，因此会得到许多转换后的整数，具体单个整数的计算方式为：
$$
c = n^e\bmod N
$$
<br />

### 消息的解密

每读取到一个整数 $c$，通过下面的方式来进行解密：
$$
n = c^d \bmod N
$$
得到 $n$ 之后再对其进行对应的编码即可还原原来的信息

解密的原理：$c^d = n^{ed} \bmod N$

由于 $e*d \equiv 1 \pmod r$​，因此 $e*d = 1 + h\varphi(N)$​，则有：
$$
n^{ed} = n^{1+h\varphi(N)} = n*n^{h\varphi(N)} = n*(n^{\varphi(N)})^h
$$

- 如果 $n$ 和 $N$ 互质，那么
  
    $n^{ed}= n*(n^{\varphi(N)})^h \equiv (1)^h\equiv n\pmod N $​​​

- 如果 $n$​ 和 $N$​ 不是互质的，那么
  
    由于 $N = p*q$，假设现在 $n = h*p$，$ed - 1 = k(q - 1)$，那么：
  
    $n^{ed} = (hp)^{ed} \equiv 0 \equiv ph \equiv n \pmod p$
  
    $n^{ed} = n^{ed - 1}n = n^{k(q - 1)}n = (n^{1 - 1})^k n \equiv 1^kn \equiv n \pmod q$

因此 $n^{ed} \equiv n \pmod N$

## 具体实现

由于 Java 存在 `BigInteger` 类来支持任意精度的数值计算，因此实现起来就会变得特别方便，具体实现代码如下所示：

```java
static void test(String text) {
    int BIT_LENGTH = 2048;

    Random rand = new SecureRandom();
    BigInteger p = BigInteger.probablePrime(BIT_LENGTH / 2, rand);
    BigInteger q = BigInteger.probablePrime(BIT_LENGTH / 2, rand);
    // 计算 N
    BigInteger n = p.multiply(q);

    // 计算 r
    BigInteger phi = p.subtract(BigInteger.ONE)
        .multiply(q.subtract(BigInteger.ONE));

    BigInteger e = TWO;

    // 找到合适的 e
    while (e.compareTo(phi) < 0) {
        if (e.gcd(phi).intValue() == 1) break;
        e = e.add(ONE);
    }

    BigInteger d = e.modInverse(phi); // 获得 e 的模反元素

    BigInteger msg = new BigInteger(text.getBytes(UTF_8)); // 将消息转换为对应的整数
    BigInteger enc = msg.modPow(e, n); // 相当于对 msg 做 e 次乘法，再对 n 求模

    System.out.println("raw=" + text);
    System.out.println("enc=" + enc);
    BigInteger dec = enc.modPow(d, n);
    System.out.println("dec=" + new String(dec.toByteArray(), UTF_8));
}
```

假设现在输入的字符串为 “This is a simple text”，输出结果如下所示：

<img src="https://s2.loli.net/2022/01/10/sHeO6RjYQfmapbk.png" alt="2022-01-10 22-10-49 的屏幕截图.png" style="zoom:150%;" />

具体的，可以对编码后的数据进行特殊的处理，如：基于 16 位、基于 64 位 bit 的转换，就会变成常见的 key

<br />

参考：

<sup>[1]</sup> https://zh.wikipedia.org/wiki/RSA%E5%8A%A0%E5%AF%86%E6%BC%94%E7%AE%97%E6%B3%95

<sup>[2]</sup> https://www.ruanyifeng.com/blog/2013/06/rsa_algorithm_part_one.html

<sup>[3]</sup> https://www.ruanyifeng.com/blog/2013/07/rsa_algorithm_part_two.html

<sup>[4]</sup> https://zh.wikipedia.org/wiki/%E6%AC%A7%E6%8B%89%E5%87%BD%E6%95%B0