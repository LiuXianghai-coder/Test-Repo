# 动态规划（十七）分割数组的最大值

### 问题描述

​	给定一个非负整数数组 `nums` 和一个整数 `m`，你需要将这个数组分成 `m` 个非空的连续子数组。设计一个算法使得这 `m` 个子数组各自和的最大值最小。

​	例如，对于输入 `nums` 为 `{7, 2, 5, 10, 8}`，`m` 为 2，那么结果应当为 18，因为将 `nums`  划分为 `{7, 2, 5}` 和 `{10, 8}`，两个连续的子数组，才能使得两个子数组各自的和在所有的切分方式中最大值是最小的。

### 解决思路

这个问题比较复杂，首先问题的定义就十分绕，要求所有切分的子数组中总和最大值在所有切分方案中是最小的；其次，这个问题的解决思路一般不太能够想到，一般普遍的做法是通过列举出所有的划分结构，然后取得最小值。但是如果直接列举出所有的划分结构实现起来不仅复杂，而且时间复杂度是极高的。因此必须选择其它的方式进行优化。

参考：https://leetcode-cn.com/problems/split-array-largest-sum/solution/fen-ge-shu-zu-de-zui-da-zhi-by-leetcode-solution/

- 动态规划

  对于这个问题来讲，一般估计是不会想到动态规划来解决的，即使想到了动态规划，估计也是无从下手（起码我就是这样的）。

  按照参考上给出的思路，首先定义一个二维数组 `dp[i][j]`，这个二维数组表示将 `nums` 数组的前 `i` 项划分为 `j` 段时，在所有的划分情况中，各个数组和的最大值最小的情况的最大值。现在引入 `k`，从 0 开始向 `i` 进行枚举，通过 `k` 将 `dp[i][j]` 划分为 `dp[k][j - 1]`（注意 `dp` 的含义，这里表示 `[0, k]` 区域内划分为 `j - 1` 段的最小值）和 `dp[i - k][1]`（将后面的元素划分为单独的一段，即 `[k + 1, i]` 为单独的一段）

  现在引入前缀和数组 `preSum`，即每个元素表示 `nums` 的对应的前缀和，因此，上面的 `dp[i - k][1]` 又可以表示为 `preSum[i] - preSum[k]`

  现在，从头至尾依次递推，可以发现，对应的递推公式为 :
  $$
  dp[i][j] = \min_{k=0}^{i-1}\lbrace\max(dp[k][j - 1], preSum[i] - preSum[k])\rbrace
  $$
  边界情况：

  - 由于不能划分空数组，因此 `i` 必须大于等于 `j`，因此初始化时必须将每个元素初始化为一个最大值
  - `dp[0][0]` 为 0，因为在不划分的情况下，最大值应当为 0

- 二分搜索

  通过逆向思维的方式，与其一个一个地划分数组求最大值，不如转换一下思路，通过输入一个可能的最大值 `max`，求出能够划分的子数组的数量，通过不断二分，最终得到 `max` 的最小值即为需要的答案。

  首先确定一下 `max` 的可能范围，由于是数组和，按照题意，最终的 `max` 一定会大于等于这个数组的最大元素，这便是 `max` 的最小值；不管怎么划分，最终的 `max` 一定是小于整个数组的整个和的，因此这便是上界。

  还有一个棘手的地方在于能够划分的数组的数量，如何通过传入的 `max` 值得到能够划分的数组数量？实际上，如果将每个子数组的元素和尽可能地朝着 `max` 的值去靠拢，那么这样得到的子数组的数量将会是最小的（因为最大和不能超过 `max`），如果在这种情况下划分的子数组数量可以小于 `m`，那么说明这个 `max` 是比预期答案要大的，否则就是小于的，这就符合了二分的思路，因此可以通过二分不断缩小空间区域来得到最终的答案。（灵活的二分 :-) ）

### 实现

- 动态规划

  ```java
  class Solution {
      public int splitArray(int[] nums, int m) {
          final int N = nums.length;
          final int[][] dp = new int[N + 1][m + 1];
          final int[] preSum = new int[N + 1];
  
          /*
          	初始化前缀和数组和 dp 数组，注意这里的前缀和数组是从 1 开始的
          */
          preSum[0] = 0;
          for (int i = 0; i < N; ++i) {
              Arrays.fill(dp[i], Integer.MAX_VALUE);
              preSum[i + 1] = preSum[i] + nums[i];
          }
          Arrays.fill(dp[N], Integer.MAX_VALUE);
  
          dp[0][0] = 0;
          for (int i = 1; i <= N; ++i) {
              for (int j = 1; j <= Math.min(i, m); ++j) {
                  for (int k = 0; k < i; ++k) {
                      // 对应上文的递推方程式
                      dp[i][j] = Math.min(dp[i][j], Math.max(dp[k][j - 1], preSum[i] - preSum[k]));
                  }
              }
          }
  
          return dp[N][m];
      }
  }
  ```

  

- 二分搜索

  ```java
  class Solution {
      public int splitArray(int[] nums, int m) {
          int lo = 0, hi = 0;
          for (int i = 0; i < nums.length; ++i) {
              hi += nums[i];
              lo = nums[i] > lo ? nums[i] : lo;
          }
  
          while (lo < hi) {
              int mid = lo + hi >> 1;
              
              // 如果能够划分的子数组的数量小于 m，那么说明 max 是大于最终答案的
              if (split(nums, mid) <= m) 
                  hi = mid;
              else 
                  lo = mid + 1;
          }
  
          return hi;
      }
  
      // 使用 max 值得到能够划分 nums 数组的最小子数组数量
      private int split(int[] nums, int max) {
          int cnt = 1; // 不管怎么说，至少都存在一个子数组
          int sum = 0;
  
          for (int i = 0; i < nums.length; ++i) {
              /*
              	将每个子数组的元素和尽可能地向 max 靠，这样就能得到最小的划分子数组的数量 
              */
              if (sum + nums[i] > max) {
                  cnt++;
                  sum = nums[i];
              } else {
                  sum += nums[i];
              }
          }
  
          return cnt;
      }
  }
  ```

  

