# 动态规划问题（七）编辑距离

### 问题描述

​	给你两个字符串 S1 和 S2，现要求将 S1 通过有限的操作次数得到 S2，可以执行的操作有以下三种：插入、删除、替换。求最少需要的操作次数。

​	例如：对于输入的字符串 S1 ”sunday“ 和 S2 ”saturday“，你至少需要操作三次才能将 S1 转变为 S2（将 n 替换为 r，再插入 a、插入 t）。

### 解决思路

​	对于这一类问题首先考虑递归，因为当前的问题不是特别好分析。

- 递归
  - 从两个字符串的第 0 个位置开始，如果两个字符串的首字母相同，那么直接递归操作两个字符串的后面一节子串
  - 如果当前首部位置的两个字符不相同，那么就需要将当前的 S1 分别进行上文的三种操作，再将得到的字符串与当前的 S2 进行递归处理
  - 如果 S2 的当前处理长度为 0，那么 当前 S1 只能通过不断删除字符得到 S2
  - 如果 S1 当前的长度为 0，那么 S1 只能通过不断地插入字符得到 S2
- 动态规划
  - 对于上文的递归方案来讲，将会重复计算大量的相同数据，因此，使用动态规划的方式记录中间过程，从而提高计算效率

### 实现

- 递归

  ```java
  public class Solution {
      public static int editDistance(String s1, String s2) {
          int lenS1 = s1.length(), lenS2 = s2.length();
  
          // 边界条件
          if (lenS2 == 0) return lenS1;
          if (lenS1 == 0) return lenS2;
  
          // 如果当前递归的两个字串的首字符相同，那么递归处理两个字串的后一部分子串
          if (s1.charAt(0) == s2.charAt(0))
              return editDistance(s1.substring(1), s2.substring(1));
  
          // 当前的两个子串的首字符不相同，那么需要对 S1 进行相应的操作，再递归处理
          return Math.min(
  	            // 这是替换操作，将 S1 的第一个字符修改为 S2 的第一个字符，那么就只要递归操作两个子串后面的部分了
                  editDistance(s1.substring(1), s2.substring(1)), 
                  Math.min(
                      	// 这是插入操作，因为插入字符与 S2 首字符相等，因此只需要把 S1 与 S2 的后面部分进行递归操作即可
                          editDistance(s1, s2.substring(1)),
                      	// 这是删除操作，由于删除了首字符，因此只需要将 S1 的后面部分与 S2 进行递归处理
                          editDistance(s1.substring(1), s2)
                  )
          ) + 1; // 由于这三个操作都是修改了 S1，因此操作数必须 +1
      }
  }
  ```

- 动态规划

  ```java
  public class Solution {
      public static int editDistanceDp(String s1, String s2) {
          int lenS1 = s1.length(), lenS2 = s2.length();
          
          // dp 在这里用于存储中间计算结果
          int[][] dp = new int[lenS1 + 1][lenS2 + 1];
  
          for (int i = 0; i <= lenS1; ++i) {
              for (int j = 0; j <= lenS2; ++j) {
                  if (0 == i) dp[i][j] = j;  // 上文的边界条件
                  else if (0 == j) dp[i][j] = i; // 上文的边界条件
                  
                  else if (s1.charAt(i - 1) == s2.charAt(j - 1)) 
                      dp[i][j] = dp[i - 1][j - 1]; // 与上文递归方案中的对应
                  else dp[i][j] = Math.min(
                              dp[i - 1][j], // i - 1 代表的是删除 S1 的首字符后的处理结果
                              Math.min(
                                      dp[i - 1][j - 1], // i - 1, j - 1 表示的是替换操作
                                      dp[i][j - 1] // j - 1 表示 S1 插入了 S2 的首字符，因此它的处理长度不变
                              )
                      ) + 1;
              }
          }
  
          return dp[lenS1][lenS2];
      }
  }
  ```

  