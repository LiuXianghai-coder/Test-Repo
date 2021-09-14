# 动态规划问题（十）0-1 背包问题

### 问题描述

​	在一堆物品中，由相应的价值和重量，现在你有一个有容量限制的背包，每个物品你只能选择拿或者不拿。现在要求计算能够得到的最大物品的总价值。

​	例如，对于一堆物品，它的价值为 {60, 100, 120}，重量为 {10, 20, 30}，你现在的背包容量为 50，因此你最大可以拿到的物品价值为 220（取第二个和第三个物品）

### 解决思路

​	对于每一个物品，它最终的状态都只能要么在所选物品集合中，要么不在。因此，可以通过递归简单地枚举出所有的组合集合，从而找到价值最大的最优解。

- 递归
  - 定义最优子集为最终会放入背包的物品集合
  - 对于每一个物品，它最终要么在最优子集中，要么不在；对于在最优子集的物品，加上它的价值，同时背包容量减去它的重量，在此基础上进行递归操作。最终得到最优子集
  - 对于重量大于背包当前容量的物品，只能选择放弃
- 动态规划
  - 与上文递归的相对应，由于在穷举时会重复计算之前已经计算过的问题，因此可以使用动态规划来解决这一类的重复子问题

### 实现

- 递归

  ```java
  public class Solution {
      /**
       * 根据当前的物品信息和背包的容量信息得到能够得到的最大收益
       * @param values ： 当前可选物品的价值信息列表
       * @param weight ： 当前可选物品的重量信息列表
       * @param capacity ：当前背包的可用容量
       * @param index ： 当前待选择的物品的位置索引，类似游标
       * @return ：当前条件下能够得到的最大收益
       */
      public static int knapSack(int[] values, int[] weight, int capacity, int index) {
          // 边界条件，对于背包容量为 0 或者当前无可选物品时，达到终止条件
          if (0 == capacity || 0 > index)
              return 0;
  
          // 如果当前选取的物品的重量大于背包当前的可用容量，那么就不能添加该物品了
          if (weight[index] > capacity)
              return knapSack(values, weight, capacity, index - 1);
  
          return Math.max(
                  // 假设当前选取的物品是在最优子结构中的，那么把它放入背包然后与不放入背包的情况后的结果进行比较
                  values[index]
                          + knapSack(values, weight, capacity - weight[index], index - 1),
                  knapSack(values, weight, capacity, index - 1) // 当前的物品不放入背包
          );
      }
  }
  ```

- 动态规划

  ```java
  public class Solution {
       public static int bagProblem(int[] values, int[] weight, int capacity) {
          int len = values.length;
          // 当前的 dp[i][j] 表示在过滤了 i 件物品后，在 j 容量的情况下能够得到的最大收益
          int[][] dp = new int[len + 1][capacity + 1];
  
          // 由于 dp 的意义，因此 i 的位置需要从 1 开始
          for (int i = 1; i <= len; ++i) {
              for (int j = 1; j <= capacity; ++j) {
                  // 如果当前的物品重量大于背包的可用容量，那么直接跳过这个物品
                  if (weight[i - 1] > j)
                      dp[i][j] = dp[i - 1][j]; // i - 1 代表当前的物品不放入背包，因为 i 在这代表的是第 i 个物品的选取情况
                  else dp[i][j] = Math.max(
                          dp[i - 1][j], // 当前的物品不放入背包的情况
                          values[i - 1] + dp[i - 1][j - weight[i - 1]] // 当前物品放入背包的情况，values 内的 i - 1 是索引，不要弄混淆了
                  );
              }
          }
  
          return dp[len][capacity];
      }
  }
  ```

- 压缩空间

  可以压缩上文动态规划中的存储数组，用于节省空间

  ```java
  public class Solution {
      public static int bagProblemOp(int[] values, int[] weight, int capacity) {
          int[] dp = new int[capacity + 1];
          int len = values.length;
  
          for (int i = 1; i <= len; ++i) {
              // 遍历找到最大的收益价值
              for (int j = capacity; j >= 0; --j) {
                  if (weight[i - 1] <= j)
                      dp[j] = Math.max(
                              dp[j],
                              dp[j - weight[i - 1]] + values[i - 1]
                      );
              }
          }
  
          return dp[capacity];
      }
  }
  ```

  

