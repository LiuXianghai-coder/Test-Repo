# 动态规划问题（五）数字1的个数

### 问题描述

​	给你一个整数 n，计算所有小于等于 n 的非负整数中数字 1 出现的个数。

​	例如：对于整数 13，所有包含 1 的非负整数为 13、12、11、10、1 这些数里面数字 1 总共出现了 6 次，因此得到的结果为 6.

### 解决思路

​	按照分位数计算 1 出现的次数即可。比如说，对于 13，我们可以首先计算个位数 1 出现的次数，再统计十位数上的出现次数即可。

​	一般来讲，对于任意的一个整数 n，从个位开始依次计算每个位数 1 出现的次数，最终累加即可。

​	比如说，对于百分位来讲（如果 n 此时大于 100），那么当前就会有 $\lfloor \frac {n}{1000} \rfloor$ 个百分位的 1 出现，因此此时就会有 $\lfloor \frac {n}{1000} \rfloor \times 100$  个 1 出现（因为 100 本身自带一个 1，因此对于任意的 100，101 .....） 都至少会有一个 1。而对于余数部分，此时要分三种情况：

- 余数 < 100，那么这种情况下在百分位的 1 就是 0，不要再考虑
- 100 <= 余数 < 200，此时只有 `余数 - 100 + 1` 个百分位的 1，+ 1 是由于要包括 100
- 余数 >= 200，此时就会包含整个百分位的 1，因此这种情况下 1 的个数为 100

因此，对于当前n，再当前的百分位上包含的 1 的个数为：

​	$\lfloor \frac {n}{1000} \rfloor \times 100 + min(max(n \mod 1000 - 100 + 1, 0), 100)$​

对于一般的位数：

​	$\lfloor \frac {n}{10^{k+1}} \rfloor \times 10^k + min(max(n \mod 10^{k + 1} - 10^k + 1, 0), 10^k)$​

依次遍历每个位数即可得到最终的结果。

### 实现

```java
public class Solution {
    public static int countDigitOne(int n) {
        int ans     =   0;
        // 初始计算个位数的 1
        int digit   =   1;

        // 不断迭代每个位数的 1，即可得到最终的结果
        while (digit <= n) {
            ans += (n / (digit * 10)) * digit
                    + Math.min(Math.max(n % (digit * 10) - digit + 1, 0), digit);

            digit *= 10;
        }

        return ans;
    }
}
```


