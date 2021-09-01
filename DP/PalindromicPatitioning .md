# 动态规划问题（十二）最小回文串分割次数

### 问题描述

​	给你一个字符串，现在你需要将它进行分割，使得每个子串都是回文，求最少需要分割的次数。

​	例如，对于字符串 “abaedaba”，至少需要进行三次切分，将它切分为 {"aba", "e", "d", "aba"} 才能使得每个子串都是回文。

### 解决思路

​	首先，先尝试将问题的规模进行分解，检查是否能够简化问题。因此首先可以考虑使用递归的方式来解决这个问题。

- 递归
  - 类似于“矩阵相乘的最小操作次数”，可以将一部分分割，然后对剩余的两部分做相似的处理，最终通过穷举出所有存在的分割方式即可得到最小的分割次数。
- 动态规划
  - 上文的递归方案将会进行多次的重复运算，因此使用临时的二维数组来存储这个结算结果可以极大地提高计算的效率。（典型的重复子问题计算）
- 中心扩展
  - 转换下思路，与其一个一个地去找，不如考虑从某个位置开始能够扩展为回文的最长长度。

### 实现

- 递归

  ```java
  public class Solution {
      public static int minPalPartitionRecur(String str, int i, int j) {
          if (i == j) return 0; // 只有一个字符，那么本身就是一个回文，无需进一步细分
          if (isPalindrome(str, i, j)) return 0; // 如果这个范围内的是回文，那么就无需进一步处理，因为递归的方式是自顶向下的，因此最长的回文肯定是首先出现的。
  
          int ans = Integer.MAX_VALUE;
          // 对每个位置进行递归处理，穷举出所有的分割情况，最后得到最小的分割次数
          for (int k = i; k < j; ++k) {
              ans = Math.min(
                      ans,
                      minPalPartitionRecur(str, i, k)
                              + minPalPartitionRecur(str, k + 1, j)
                              + 1
              );
          }
  
          return ans;
      }
      
      // 判断输入的字符串 str 在 [i, j] 的范围内的子串是否为回文
      private static boolean isPalindrome(String str, int i, int j) {
          if (i == j) return true;
  
          while (i <= j) {
              if (str.charAt(i) != str.charAt(j))
                  return false;
              i++;
              j--;
          }
  
          return true;
      }
  }
  ```

- 动态规划

  - 一般动态规划

  ```java
  public class Solution {
      public static int minPalPartitionDp(String str) {
          int len = str.length();
  
          int[][] dp = new int[len][len]; // 存储中间计算结果，dp[i][j] 表示当前 [i, j] 范围内的最少分割次数
          boolean[][] mark = new boolean[len][len]; // 标记 [i, j] 范围内的子串是否是回文
  
          for (int i = 0; i < len; ++i)
              mark[i][i] = true; // 每个位置本身它都是一个回文
  
          for (int L = 2; L <= len; ++L) { // 自底向上计算每个范围的情况
              for (int i = 0; i < len - L + 1; ++i) {
                  int j = i + L - 1;
                  final boolean compare = str.charAt(i) == str.charAt(j);
                  if (L == 2)
                      mark[i][j] = compare;
                  else
                      mark[i][j] = (compare && mark[i + 1][j - 1]);
  
                  if (mark[i][j]) { // 如果 [i, j] 内的是回文，那么就不需要进行分割了
                      dp[i][j] = 0;
                      continue;
                  }
  
                  dp[i][j] = Integer.MAX_VALUE;
                  for (int k = i; k < j; ++k) { // 穷举所有可能出现的情况，得到当前范围内的最小切割次数
                      dp[i][j] = Math.min(
                              dp[i][j],
  
                              dp[i][k]
                                      + dp[k + 1][j]
                                      + 1
                      );
                  }
              }
          }
  
          return dp[0][len - 1]; // 最终 [0, len - 1] 范围内的最小切割次数就是最终需要的结果 
      }
  }
  ```

  - 优化：上面的 DP 的时间复杂度为 $O(n^3)$，空间复杂度为 $O(n^2)$, 可以优化它

    ```java
    public class Solution {
        public static int minPalPartitionDpOp(String str) {
            int len = str.length();
            int[] dp = new int[len];
            boolean[][] mark = new boolean[len][len];
    
            // 通过递推的方式来处理会更加高效
            for (int i = 0; i < len; ++i) {
                int minCut = i;
                for (int j = 0; j <= i; ++j) {
                    if (
                            str.charAt(i) == str.charAt(j) &&
                                    (i - j < 2 || mark[j + 1][i - 1])
                    ) {
                        mark[j][i] = true;
                        minCut = Math.min(minCut, j == 0 ? 0 : (dp[j - 1] + 1));
                    }
                }
                dp[i] = minCut;
            }
    
            return dp[len - 1];
        }
    }
    ```

- 中心扩展

  ```java
  public class Solution {
      private static int minCut(String str) {
          int len = str.length();
          char[] strArray = str.toCharArray(); // 转换为字符数组可能更加高效
          int[] minCuts = new int[len]; // minCuts[i] 表示在 i 位置的最少切割次数
  
          for (int i = 0; i < len; ++i)
              minCuts[i] = i; // 每个字符都是回文的情况下，需要对每一个字符进行切分，因此与字符串当前的长度相等
  
          for (int i = 0; i < len; ++i) {
              // 从当前 i 位置开始，将它向两边扩展
              expansionCenter(strArray, i, i, minCuts);
              // i + 1 是为了避免由于数组长度造成的问题
              expansionCenter(strArray, i, i + 1, minCuts);
          }
  
          return minCuts[len - 1];
      }
  
      private static void expansionCenter(char[] strArray, int left, int right, int[] minCuts) {
          while (left >= 0 && right < strArray.length && strArray[left] == strArray[right]) {
              int i = left--, j = right++;
  
              if (i == 0) // 最低位为0，说明已经头了，此时 [i, j] 的部分不需要切分，因此 minCuts[j] 为 0
                  minCuts[j] = 0;
              else
                  // i - 1 表示前一部分的切割次数，因为这两个部分需要进行依次切分，所以需要 + 1
                  // 由于可能当前位置有多种情况，因此要取最小值
                  minCuts[j] = Math.min(minCuts[j], minCuts[i - 1] + 1);
          }
      }
  }
  ```

  

