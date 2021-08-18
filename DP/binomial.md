# 动态规划问题（九）二项式系数计算

### 问题描述

​	给定两个正整数 n 和 r，求出它们二项式系数 $\dbinom{n}{r}$​ 的结果。

### 解决思路

​	这个就是直接的公式套用就行，对于一般的 $\dbinom{n}{k}$ （n > k，k > 0） 都有 $\dbinom{n}{k}=\dbinom{n - 1}{k - 1} + \dbinom{n - 1}{k} $ 对于 n== k 和 k = 0 的情况，$\dbinom{n}{0} = \dbinom{n}{n} = 1$​

- 递归方式
  - 由上文的公式，C(n, k) = C(n - 1, k - 1) + C(n - 1, k)。因此使用递归可以很简单地实现
  - 边界条件，对于 n == k 和 k == 0 的情况，结果都是 1；对于 k > n 的情况，得到的结果为 0
- 动态规划
  - 由于递归调用会重复计算之前已经计算过的结果，因此可以使用动态规划来解决这类经典的重复子问题的问题
- 公式
  - 一般的，$\dbinom{n}{k} = \frac{n^k}{k!} = \frac{n(n-1)(n-2)\cdots(n-(k-1))}{k(k-1)(k-2)\cdots1}=\prod_{i=1}^k \frac{n + i - 1}{i}$

### 实现

- 递归

  ```java
  public class Solution {
      public static long nCr(int n, int r) {
          // 边界条件
          if (r > n) return 0L; 
          if (n == r || r == 0) return 1L;
          
          return nCr(n - 1, r - 1) + nCr(n - 1, r);
      }
  }
  ```

- 动态规划

  ```java
  public class Solution {
      public static long nCr(int n, int r) {
          if (r > n) return 0L; 
          if (n == r || r == 0) return 1L;
          
          long[][] dp = new long[n + 1][r + 1];
          
          dp[0][0] = 1;
          for (int i = 1; i <= n; ++i) {
              for (int j = 0; j <= r; ++j) {
                  // 边界条件
                  if (i == j || j == 0) dp[i][j] = 1;
                  else dp[i][j] = dp[i - 1][j - 1] + dp[i - 1][j]; // 递推关系
              }
          }
          
          return dp[n][r];
      }
  }
  ```

- 公式

  ```java
  public class Solution {
      public static long nCr(int n, int r) {
          if (r > n) return 0L; 
          if (n == r || r == 0) return 1L;
  
          long ans = 1L;
          for (int i = 1; i <= r; ++i) {
              ans *= n - i + 1;
              ans /= i;
          }
  
          return ans;
      }
  }
  ```

  

### 