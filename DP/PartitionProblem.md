# 动态规划问题（十三）是否能够划分为总和相等的子集

### 问题描述

​	给你一个数组，你的任务是检测是否能过够将这个数组分成两个总和相等的子集。

​	例如，对于数组 {1, 5, 11, 5}，它能过够分为两个子数组 {1, 5, 1} 和 {11}。对于{1, 5, 3}数组则不能分为两个总和相等的子数组。

### 解决思路

​	首先，一个数组能否分为符合要求的两个数组，整个数组的总和应该是一个偶数，如果是一个奇数的话，那么无论如何这个数组都不能分为两个数组。

​	之后，将这个数组和的一般作为目标和，枚举所有的可能组合检测是否能够得到最终的结果。

- 递归方案

  - 对于当前的元素，它要么是在当前要组合的子数组中的，要么不是，因此：
    $$
    result(arr, n, target) = result(arr, n - 1, target) || result(arr, n - 1, target - arr[n-1]) 
    $$
    其中，n表示当前的添加元素的位置索引

  - 边界情况：对于 target = 0 的情况，说明可以得到子数组；对于 n < 0 的情况，说明已经完成了所有的检测，但是无法组成子数组。

- 动态规划

  - 使用自底向上的方式，通过记录中间的计算结果，可以极大地提高运算速度。

### 实现

- 递归

  ```java
  public class Solution {
      public static boolean findPartition(int[] arr) {
          int sum = 0;
          for (int v : arr) sum += v;
  
          if ((sum & 1) != 0) // 检测原数组总和是否能够被分为两部分
              return false;
  
          return isSubsetSum(arr, arr.length - 1, sum / 2);
      }
      
      public static boolean isSubsetSum(int[] arr, int n, int target) {
          if (target == 0) return true; 
          if (n < 0) return false;
  
          if (arr[n] > target) // 如果当前元素大于目标值，那么不能放入当前的子数组
              return isSubsetSum(arr, n - 1, target);
  
          return isSubsetSum(arr, n - 1, target) // 当前元素没有放入子数组
              || isSubsetSum(arr, n - 1, target - arr[n]); // 当前元素放入了数组
      }
  }
  ```

- 动态规划

  ```java
  public class Solution {
      public static boolean findPartitionDp(int[] arr) {
          int sum = 0;
          for (int v : arr) sum += v;
  
          if ((sum & 1) != 0)
              return false;
  
          int len = arr.length;
          int target = sum / 2;
          boolean[][] dp = new boolean[len + 1][target + 1];
  
          for (int i = 0; i <= len; ++i)
              dp[i][0] = true; // 边界情况，对于目标值为 0 的情况，不管数组内的元素是什么，总能找到对应的子集
  
          for (int i = 1; i <= len; ++i) {
              for (int j = 1; j <= target; ++j) {
                  dp[i][j] = dp[i - 1][j]; // 当前元素未放入检出子数组中的情况
                  if (arr[i - 1] <= j) {
                      dp[i][j] = dp[i][j] || dp[i - 1][j - arr[i - 1]]; // 与上文递归的公式对应
                  }
              }
          }
  
          return dp[len][target];
      }
  }
  ```

  

