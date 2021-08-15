# 动态规划问题（二）最长回文序列长度

### 问题描述

​	有一段字符串，现在要求得到该字符串子序列中最长的回文字符序列的长度。这里的回文并不要求字符是连续的，只要字符是按照顺序出现的即可。

​	如：对于字符串 "GEEKSFORGEEKS"，最长的回文序列长度为 5，可能的序列有：”EEKEE“、”EESEE“ 等。

### 解决思路

首先这种问题的一般解决思路是考虑递归，将问题简化。

递归方案：

​	定义函数 $L(i,j)$ 表示从索引下标 $i$ 开始，到索引下标 $j$ 中的回文序列长度。

- 对于任意的 $i$ ，我们有 $L(i,i) = 1$ 这是因为对每一个字符，它们自生都是一个回文序列
- 如果 `X[i] != X[j]`，那么 $L(i, j)=max\{L(i + 1, j), L(i, j - 1)\}$ 
- 如果 `X[i] == X[j]`，那么 $L(i, j)=L(i + 1, j - 1) + 2$
- 递归终止条件：`j = i + 1`，此时如果 `X[i] == X[j]`，那么 $L(i, j) = 2$​

DP 方案：

​	递归方案是一种较为直观的解决方案，但是它的问题也很明显，它存在大量的重复运算。使用动态规划，从低向上推导得出结果，可以极大地提高运行时间。

- 使用 `dp[i][j]` 来表示递归方案中的 $L(i, j)$
- 然后按照递归给出的函数进行求解即可。

### 实现

- 递归方案的实现

  ```java
  public class Solution {
      public static int lps(String seq) {
          int len = seq.length();
          // 长度为 0 的字符串本身就是一个回文字符串
          if (1 == len) return 1;
  
          boolean check = seq.charAt(0) == seq.charAt(len - 1);
          // 对应上文的终止条件
          if (2 == len) {
              if (check)
                  return 2;
              return 1;
          }
  
          if (check) {
              return lps(seq.substring(1, len - 1)) + 2;
          }
  
          return Math.max(
                  lps(seq.substring(1, len)),
                  lps(seq.substring(0, len - 1))
          );
      }
  }
  ```

- DP 方案的实现

  ```java
  public class Solution {
      public static int lps(String seq) {
          int len = seq.length();
          if (1 == len) return 1;
  
          // 将字符串转变为对应的字符数组，提高查找效率
          char[] array = seq.toCharArray();
          // 存储中间记录数据的临时表
          int[][] dp = new int[len][len];
  
          // 对应上文，每个字符都是长度为 1 的回文
          for (int i = 0; i < len; ++i) 
              dp[i][i] = 1;
  
          /*
          * 从低向上，不断推导得到最终的结果
          * 这里的从低到上是通过不断提高步长区间来实现的
          */
          for (int step = 2; step <= len; ++step) {
              // 区间的索引起始位置
              for (int i = 0; i <= len - step; ++i) {
                  int j = i + step - 1;
                  if (array[i] == array[j]) {
                      if (step == 2) dp[i][j] = 2;
                      else dp[i][j] = dp[i + 1][j - 1] + 2;
                  } else {
                      dp[i][j] = Math.max(dp[i + 1][j], dp[i][j - 1]);
                  }
              }
          }
  
          return dp[0][len - 1];
      }
  }
  ```

  