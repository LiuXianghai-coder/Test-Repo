# 动态规划（三）最长递增子序列长度（LIS）

### 问题描述

​	有一个数组，它内部的顺序是乱序的，现在要求你找出该数组中的最长的递增子序列长度。

​	例如：对于数组 {10, 20, 9, 33, 21, 50, 41, 60, 80}，它的最长递增子序列为{10, 22, 33, 50, 60, 80}，长度为 4

### 解决思路

- DP 方案：

  令 $L(i)$ 表示在 $i$​ 位置最长的递增子序列长度.

  - 当 `0 < j < i`  并且 `arr[j] < arr[i]` 时，$L(i)=1 + max(L(j))$ $(j \in [0, i])$​ 

  - 当 `i == 0` 时， $L(i) = 1$​

  - 因此，状态转移方程为 
    $$
    L(i)=\begin{cases}
    1 + max(L(j)) & j \in [0, i] \\
    1 & i = 0 \\
    \end{cases}
    $$

- 贪心策略和二分搜索：

  - 定义一个数组，这个数组的元素是单调递增的。
  - 贪心策略：每次遇到一个元素将它插入到预先定义的数组中，使得整个数组的增长是最 “缓慢”的。
  - 由于插入后数组的元素元素是有序的，因此下次插入时可以使用二分查找进行替换。

### 实现

- DP 方案的实现

  ```java
  public class Solution {
      public static int lis(int[] array) {
          int len = array.length;
          if (1 == len) return 1; // 边界条件
  
          int ans = 1; // 对任意的数组序列，最少的递增子序列长度至少为 1
          int[] dp = new int[len];
          dp[0] = 1; 
          for (int i = 1; i < len; ++i) {
              dp[i] = 1;
              // 从小于 i 的数组索引中找到最大的递增序列长度
              for (int j = 0; j < i; ++j) {
                  if (array[i] > array[j])
                      dp[i] = Math.max(dp[i], dp[j] + 1);
              }
  
              // 与当前的最大递增序列长度进行比较，得到最终的最长递增子序列长度
              ans = Math.max(dp[i], ans);
          }
  
          return ans;
      }
  }
  ```

  

- 贪心策略 + 二分搜索的实现

  ```java
  class Solution {
      public int lis(int[] array) {
          int len = array.length;
          if (1 == len) return 1; // 边界条件检测
  
          int ans = 1;
          int[] dp = new int[len];
          dp[0] = array[0]; // 存储有序插入结果
          for (int i = 1; i < len; ++i) {
              // 如果当前元素大于定义数组的最大元素，则直接添加它到末尾
              if (array[i] > dp[ans - 1]) {
                  dp[ans++] = array[i];
              } else {
                  // 查找当前元素的插入位置
                  int lo = 0, hi = ans - 1, pos = 0;
                  while (lo <= hi) {
                      int mid = lo + (hi - lo) / 2;
                      if (dp[mid] == array[i]) {
                          pos = mid;
                          break;
                      } else if (dp[mid] > array[i]) {
                          hi = mid - 1;
                      } else {
                          lo = mid + 1;
                          pos = lo;
                      }
                  }
  
                  dp[pos] = array[i];
              }
          }
  
          return ans;
      }
  }
  ```

  