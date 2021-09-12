# 动态规划问题（十五）不含连续1的非负整数

## 问题描述

​	给定一个正整数 n，找出小于或等于 n 的非负整数中，其二进制表示不包含连续的 1 的数字的数量，注意，这里的连续是指：两个及以上的 1 连续出现则称之为连续的 1。

​	比如，对于输入的的整数 n = 8, 那么结果为 6

```text
0：0000
1：0001
2：0010
3：0011
4：0100
5：0101
6：0110
7：0111
8：1000

其中，3、5、7 是不符合要求的
```

## 解决思路

这种问题是很明显的动态规划问题，因为当前位置的结果可以从之前的结果中推导出来。比如，如果想要知道 8 的结果，那么只需要在 7 的结果的基础上，判断 8 是否满足要求再进行累加即可。在这种情况下，DP 的转换方程为 
$$
f(n) = \begin{cases} 
0 & j = 0\\
f(n - 1) + valid(n) & j > 0
\end{cases}
$$
其中，$valid(n)$ 表示 n 是否为符合要求的数，如果是，则为 1，否则为 0。



然而，在 `leetcode` <a href="https://leetcode-cn.com/problems/non-negative-integers-without-consecutive-ones">不含连续1的非负整数</a> 上给出的输入的的范围限制在$(0, 10^9)$ 的范围内，这会使得存储中间结果的数组过大，超出内存限制。为了解决这个问题，可以使用状态压缩的中间数组，使用两个位置记录奇数和偶数的结果，从而压缩空间。但是这并不是一个最好的解决方案，参考相关的题解之后，使用**数位 DP** 可以很顺利地解决这个问题。

一般来讲，数位 DP 一般解决的是以下类型的问题：在区间  $(a, b)\quad 0< a < b $  内符合条件的数值的个数有多少？通常情况下一般会实现一个查询 $[0, x]$ 范围内有多少个合法数值的函数 $f(n)$，然后使用容斥原理求解出 $[a, b]$ 区间内的个数<sup>[1]</sup>。

不失一般性的，从高位向低位考虑数值 n 的某一位 $cur$ 是如何被处理的：

1. 如果当前的位位置 $cur=1$  的话，如果将当前的位设置为 0，后面的低位位置填任意的 0 或 1 都是可行的。需要注意的是，当前位为 1 是否是合法的，因为问题描述中提到不能有连续的1，因此需要使用一个 $prev$ 变量来记录上一位的元素，确保 $cur$ 和 $prev$ 不会同时为 1
2. 如果当前位位置 $cur = 0$ 的话，只能将当前的位设置为 0，因为设置为 1 将会大于 n

> 以 25 为例：
>
> 25 的二进制表示：11001
>
> 当从高位向低位移动时，遇到第一个不为 0 的位，此时 cur = 1, prev = 0.
>
> 如果将当前的 cur 设置为 0，那么此时将 prev = 0, 然后递归地去处理后面的低位（0**1001**）即可.
>
> 如果依旧将当前的位设置为 1，那么此时 prev 置为 1，此时无法处理后面的位置内容
>
> 
>
> 最后，将两个递归处理的结果相加即可得到最终的结果。

终止条件：到达最低位时即可终止。



然而，在二进制里面直接做递归处理不是那么的简单，因此，就引出了使用中间数组记录的方式，使用此种方式不仅更简单，而且速度也会更快。

定义 `f[i][j]` 表示长度为 `i` 的有效二进制长度，到最高位元素为 `j` 的数值的数量。在此问题中，由于处理的是二进制的格式，因此最高位元素 `j` = 1。

考虑`f[i][j]` 的状态转换：

- 如果当前位为 1 的话，需要统计$(1……)_2$ 和 $(0……)_2$ 的所有合法数值
  - 对于 $(1……)_2$，当前位为 1 的情况下，下一位的元素只能为 0，此时 `f[i + 1][1] = f[i][0]`
  - 对于 $(0……)_2$，当前位为 0 的情况下，下一位的元素只能为 1，因为下一位为 1 的元素已经被计算过了（当前位为 0 将会忽略这个前导），因此 `f[i + 1] = f[i][1]`
  - 最终 `f[i + 1][1] = f[i][1] + f[i][0]`。
- 如果当前位为 0 的话，需要统计 $(0……)_2$  形式的合法数值，此时有 `f[i + 1][0] = f[i][1]` 

## 实现

- 一般动态规划

```java
class Solution {

    public int findIntegers(int n) {
        if (n == 0) return 1;

        final int[] ans = new int[n + 1];
        ans[0] = 1;

        for (int i = 1; i <= n; ++i) {
            if (ans[i] != 0) continue;

            int a = i;
            int count = 0;
            while ((a | 0) != 0) {
                if ((a & 1) != 0)
                    count++;
                else 
                    count = 0;
                
                if (count >= 2) break;

                a >>= 1;
            }

            if (count >= 2) {
                ans[i] = ans[i - 1];
            } else {
                ans[i] = ans[i - 1] + 1;
            }
        }
        
        return ans[n];
    }
}
```



- 数位 DP

```java
public class Solution {
    private final static int 		N 	= 	63;
    private final static int[][] 	f 	= 	new int[N][1];
    
    static {
        f[1][0] = 1;
        f[1][1] = 2;
        
        for (int i = 1; i < N - 1; ++i) {
            f[i + 1][0] = f[i][1];
            f[i + 1][1] = f[i][1] + f[i][0];
        }
    }
    
    private static int len(int n) {
        for (int i = 31; i >= 0; --i) {
            if (((n >> i) & 1) == 1) 
                return i;
        }
        return 0;
    }
    
    public int solution(int n) {
        if (0 == n) return 1;
        int len = len(n);
        int ans = 0, cur, prev = 0;
        
        for (int i = len; i >= 0; --i) {
            cur = ((n >> i) & 1);
            if (cur == 1) {
                ans += f[i + 1][0];
            }
            
            if (prev == 1 && cur == 1) break;
            prev = cur;
            if (i == 0) ans++;
        }
        
        return ans;
    }
}
```



参考：https://leetcode-cn.com/problems/non-negative-integers-without-consecutive-ones/solution/gong-shui-san-xie-jing-dian-shu-wei-dp-y-mh92/