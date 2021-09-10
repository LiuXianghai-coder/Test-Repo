# 动态规划问题（十四）自动换行问题

## 问题描述

​	给你一系列的单词，现在要把这些单词放到文本域里，为了美观，要求将这些单词进行换行处理。现在已知每一行的宽度 width，求将这些单词放入文本域之后最少会浪费多少个单位字符的宽度（注意，单词与单词之间的间隔空格不算在其中），假定每个单词的宽度小于行的宽度。

​	例如，对于输入的单词序列：{"Geeks", "for",  "Geeks",  "presents",  "word",  "wrap", "problem"}，每一行的宽度 width = 15， 那么最佳的排列方式为：

```
Geeks for Geeks
presents word  
wrap problem   
```

​	由于第二行和第三行分别浪费了 2 个和 3个字符，因此最少需要浪费 5 个字符的空间。

## 解决思路

​	参考：https://zh.wikipedia.org/wiki/%E8%87%AA%E5%8A%A8%E6%8D%A2%E8%A1%8C

实现自动换行的两种算法：

- 贪心算法

  这是一般编辑器采用的方法

  > 尽可能的将多的单词放入一行，直到所有的单词都放进去为止

  这个算法在大多数的情况下都能取得很好的效果，但是在极个别的情况下效果不是很好。

  这个算法思路比较简单，在这里不做过多的介绍

- 动态规划

  参考《算法导论》

  > ​	首先计算在二维表 `lc[][]` 中所有的可能的行的情况。`lc[i][j]` 表示将单词序列中的 `i` 到 `j` 的单词放入一行中会占用的单位字符的数量。如果从 `i` 到 `j` 的单词序列不能够放入到一行中，那么 `lc[i][j]` 将被置为无穷大，一旦 `lc[][]` 被构建，我们就可以通过以下的递归公式计算总的字符消耗数。 在以下公式中， `C[j]` 表示从 1 到 `j` 的最优总消耗数。
  > $$
  > C[j] = \begin{cases}
  > 0 & j=0\\
  > min(C[i - 1] + l_c[i, j]) & 1\leqslant i \leqslant j
  > \end{cases}
  > $$
  > ​	以上的递归方式有重复子问题的属性，因此可以使用动态规划的方式来解决。`C[]` 数组可以被从左到右进行计算，因为每一个元素值都依赖于先前的元素。

## 实现

​	由于能力有限，以下代码来自 https://www.geeksforgeeks.org/word-wrap-problem-dp-19/

```java
public class Solution {
    private void wrapLine(String[] words, final int width) {
        int N = words.length;

        int[] l = new int[N];
        for (int i = 0; i < N; i++) {
            l[i] = words[i].length();
        }

        final int[][]   lc      =   new int[N + 1][N + 1];
        // 记录插入 i-j 的元素后的可用空间
        final int[][]   extra   =   new int[N + 1][N + 1];
        final int[]     C       =   new int[N + 1];
        // 用于打印结果
        final int[]     p       =   new int[N + 1];

        for (int i = 1; i <= N; i++) {
            extra[i][i] = width - l[i - 1];
            for (int j = i + 1; j <= N; j++) {
                extra[i][j] = extra[i][j - 1] - l[j - 1] - 1;
            }
        }

        for (int i = 1; i <= N; i++) {
            for (int j = i; j <= N; j++) {
                if (extra[i][j] < 0) { 
                    lc[i][j] = Integer.MAX_VALUE;
                } else if (j == N && extra[i][j] >= 0) { // 刚好填满行的情况
                    lc[i][j] = 0;
                } else {
                    lc[i][j] = extra[i][j] * extra[i][j];
                }
            }
        }

        C[0] = 0;
        for (int j = 1; j <= N; j++) {
            C[j] = Integer.MAX_VALUE;
            for (int i = 1; i <= j; i++) {
                if (
                        C[i - 1] != Integer.MAX_VALUE
                                && lc[i][j] != Integer.MAX_VALUE
                                && C[i - 1] + lc[i][j] < C[j]
                ) {
                    C[j] = C[i - 1] + lc[i][j];
                    p[j] = i;
                }
            }
        }

        printSolution(words, p, N);
    }

    private int printSolution(String[] words, int[] p, int n) {
        int k;
        if (p[n] == 1)
            k = 1;
        else
            k = printSolution(words, p, p[n] - 1) + 1;

        for (int i = p[n]; i <= n; i++) {
            System.out.print(words[i - 1] + " ");
        }
        System.out.println();

        return k;
    }
}
```



参考：

- https://www.geeksforgeeks.org/word-wrap-problem-dp-19/
- https://zh.wikipedia.org/wiki/%E8%87%AA%E5%8A%A8%E6%8D%A2%E8%A1%8C

