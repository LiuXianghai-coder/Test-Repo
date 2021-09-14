# 动态规划问题（四）最长双音序列长度(LBS)

### 问题描述

​	以一个乱序的数组，求它的最长双音序列长度。双音序列指该序列先递增，再递减。

​	如：

- 对于序列 {1, 11, 2, 10, 4, 5, 2, 1}，它的最长双音序列长度为 6，{1, 2, 10, 4, 2, 1}
- 对于序列 {12, 11, 40, 5, 3, 1}，它的最长双音序列长度为 5，{12, 11, 5, 3, 1}

### 解决思路

​	这是最长递增子序列问题（LIS）的扩展，只要找到得到对应的递增子序列和最长递减子序列的零时数组进行组合即可。

### 实现

```java
public class Solution {
     public static int lbs(int[] array) {
        int len = array.length;

        if (1 == len) return 1; // 边界情况

        int[] lis = new int[len]; // 递增序列的每个索引位置的最长长度数组
        int[] lds = new int[len]; // 递减序列的每个索引位置的最长长度数组

        // 初始化这两个数组，对于每个元素来讲，它们的最短长度为 1
        for (int i = 0; i < len; ++i) {
            lis[i] = 1;
            lds[i] = 1;
        }

        // 得到该递增数组的具体细节可以查看 LIS 问题的解法
        for (int i = 1; i < len; ++i) {
            for (int j = 0; j < i; ++j) {
                if (array[i] > array[j])
                    lis[i] = Math.max(lis[i], lis[j] + 1);
            }
        }

        // 与寻找最长递增数组相反，从后往前找递增的序列即可得到递减序列的记录数组
        for (int i = len - 2; i >= 0; --i) {
            for (int j = len - 1; j > i; --j) {
                if (array[i] > array[j])
                    lds[i] = Math.max(lds[i], lds[j] + 1);
            }
        }

        int ans = 1;
        for (int i = 0; i < len; ++i) {
            // 将两个记录数组的长度进行组合即可得到最大的双音序列长度
            // 这里减一是因为这两个记录数组在相同的位置索引具有重复元素
            ans = Math.max(ans, lis[i] + lds[i] - 1);
        }

        return ans;
    }
}
```



### 