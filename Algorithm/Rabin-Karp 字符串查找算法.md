# Rabin-Karp 字符串查找算法

和一般的比较字符串的方式不同，Rabin-Karp 查找算法通过对子字符串进行 `hash`，如果在原有字符串中找到了 `hash` 值相同的字符串，那么继续比较是否是需要查找的字串，一般来讲，如果 `hash` 操作做的很好的话，那么一般一次匹配就是待查找的子串

## 基本思想

长度为 $M$ 的字符串对应着一个 $R$ 进制的 $M$ 位数。为了能够使用一张大小为 $Q$ 的散列表来保存这种类型的键，需要一个能够将 $R$ 进制的 $M$ 位数值转换为一个 $0$ 到 $Q - 1$ 的整数，在实际中，$Q$ 会是一个比较大的素数。

例如，假设现在要搜索的目标字符串为 `1234`，假设现在将 $Q$ 取为 $10007$，这里由于目标字符串都是数字，因此可以考虑将其直接对 $Q$ 进行取模操作，得到 $mod=1234$。为了简单起见，假设待搜索的字符串的所有字符都是数字，为 `011122123456`，那么查找的过程如下所示：

<img src="https://s2.loli.net/2022/04/04/tXrxAcJBDjZGpV5.png" alt="Rabin-Karp.png" style="zoom:80%;" />

当然，实际使用的过程中不能直接将字符串转换为对应的整数，一般会通过某种方式将字符串转换为对应的整数，如下面的 `hash` 函数：

```java
private long hash(String key, int M) {
    long h = 0;
    for (int i = 0; i < M; ++i) 
        h = (R*h + key.charAt(i)) % Q;
    return h;
}i
```

事实上，由于这种 `hash` 的存在，会使得搜索的时间复杂度在最坏的情况下为 $O(NM)$，相比较一般的暴力搜索，该方式没有任何性能上的改进。

Rabin-Karp 则通过某种方式减少了每个子串的 `hash` 操作，具体为：

- 对于原字符串所有的位置 $i$，高效地计算文本中 $i + 1$ 位置的子字符串的 `hash` 值

    使用 $t_{i}$ 表示 `txt.charAt(i)`，那么文本  `txt` 中起始位置为 $i$，含有 $M$ 个字符的子串对应的数为：
    $$
    x_{i} = t_{i}R^{M - 1} + t_{i + 1}R^{M - 2} + …… + t_{i + M -1}R^0
    $$
    假设现在的 `hash` 函数为一般的 $h(x_{i}) = x_{i} \mod Q$，那么将模式字符串右移一位等价于将 $x_{i}$ 替换为：
    $$
    x_{i + 1} = (x_{i} - t_{i}R^{M -1})R + t_{i + M}
    $$
    即：$i + 1$ 位置的子字符串的散列值为当前处理的子串的散列值减去子串第一个字符的 `hash` 值，然后再乘以 $R$ 再加上最后一个字符的散列值

这是 Rabin-Karp 算法的核心思想，该方式可以保证在搜索的过程中以常数的时间复杂度进行搜索操作

## 实现

```java
import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;

public class RabinKarp {
    private final String pat; // 待查找的模式字符串
    private final long patHash; // 模式字符串的 hash 值
    private final int M; // 模式字符串的长度
    private final long Q; // 大素数
    private final int R; // 进制数，默认为 256
    private final long RM; // R^{M - 1}

    public RabinKarp(String pat) {
        this.pat = pat;
        this.R  = 256;
        M = pat.length();
        Q = longRandomPrime();

        long rm = 1;
        for (int i = 1; i < M; i++) {
            rm = (R * rm) % Q;
        }
        RM = rm;

        patHash = hash(pat, M);
    }

    // 在 txt 中搜索是 pat，如果不存在，返回 txt 的长度
    public int search(String txt) {
        int N = txt.length();
        if (N < M) return N;

        long txtHash = hash(txt, M);
        if (txtHash == patHash && check(txt, 0)) return 0;

        for (int i = M; i < N; ++i) {
            // 带入公式，假设这里不会出现 long 整数溢出
            txtHash = txtHash - RM*txt.charAt(i - M);
            txtHash = txtHash*R + txt.charAt(i);

            if (txtHash == patHash && check(txt, i - M + 1)) {
                return i - M + 1;
            }
        }

        return N;
    }

    // 检查 hash 匹配的两个字符串是否相等
    private boolean check(String txt, int i) {
        for (int j = 0; j < M; ++j) {
            if (txt.charAt(i + j) != pat.charAt(j)) {
                System.out.println("check false"); // 理论上来讲会执行的概率特别低
                return false;
            }
        }

        return true;
    }

    // 生成子串对应的 hash 值
    private long hash(String key, int len) {
        long h = 0;
        for (int j = 0; j < len; j++) {
            h = (R*h + key.charAt(j)) % Q;
        }
        return h;
    }

    // 随机生成一个大的素数
    private long longRandomPrime() {
        BigInteger prime = BigInteger.probablePrime(31,
                ThreadLocalRandom.current()
        );
        return prime.longValue();
    }
}
```



<br />

参考：

<sup>[1]</sup> 《算法（第四版）》