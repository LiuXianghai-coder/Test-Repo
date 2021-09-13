# 滑动窗口问题（二）字符串的排列

## 问题描述

​	给你一个字符串 `s1` 和 `s2` ，编写一个函数来判断 `s2` 是否包含 `s1` 的排列。换句话说，`s1` 的排列之一是 `s2` 的一个子串。

​	比如，对于输入的 `s1=adc` 和 `s2=dcda`，`s2` 包含 `s1` 的排列子串 `cda` ，因此返回 `true` 

## 解决思路

- 滑动窗口

  固定一个长度为 `s1.length()`的窗口数组 `window`，从 `s2` 的开始位置开始从左向右进行滑动，只要当前滑动的窗口区间内包含了`s1` 的所有字母，则说明包含 `s1` 的排列子串

- 双指针

  使用一个 `right` 指针作为快速指针向前移动，`left` 作为慢指针移动。为了保证在 [left, right] 区间内的元素都是有效的，因此每遇到一个不是`s1` 字符串内的字符，`left` 就要移动到这个位置的后一位。为了确保结果的正确性，`right` 指针添加的重复有效字符，在 `left` 向右移动时必须丢弃这个重复的字符。

## 实现

- 滑动窗口

  ```java
  class Solution {
      private int[] map = new int[127]; // 存储有效字符的表
      private int[] tmpMap = new int[127]; // 窗口移动过程中记录的字符表
  
      public boolean checkInclusion(String s1, String s2) {
          if (s2.length() < s1.length()) 
              return false;
  
          final char[] s1Array = s1.toCharArray();
          final char[] s2Array = s2.toCharArray();
  
          for (char ch: s1Array)
              map[ch]++;
  
          int N = s2Array.length;
          int left = 0, right = s1Array.length - 1;
          while (right < N) {
              // 每次重新移动时都要清楚上次的窗口记录表
              Arrays.fill(tmpMap, 0);
  
              // 统计该 [lef, right] 窗口区间内的有效字符数
              for (int i = left; i <= right; ++i)
                  tmpMap[s2Array[i]]++;
  
              if (equal(map, tmpMap)) return true;
  
              // 向右移动窗口
              right++;
              left++;
          }
  
          return false;
      }
  
      private static boolean equal(int[] map, int[] tmpMap) {
          for (int i = 0; i < map.length; ++i) {
              if (map[i] != tmpMap[i]) return false;
          }
  
          return true;
      }
  }
  ```

- 双指针

  ```java
  class Solution {
      public boolean checkInclusion(String s1, String s2) {
          if (s2.length() < s1.length()) 
              return false;
  
          final char[] s1Array = s1.toCharArray();
          final char[] s2Array = s2.toCharArray();
          final int[]  map     = new int[127];
  
          for (char ch: s1Array)
              map[ch]--;// 记录s1字符的情况
          
          final int M = s1.length();
          final int N = s2.length();
  
          for (int right = 0, left = 0; right < N; ++right) {
              char ch = s2Array[right];
              map[ch]++;
  
              // 确保当前的区间内的字符都是有效的
              while (map[ch] > 0) {
                  map[s2Array[left++]]--;
              }
  
              if (right - left + 1 == M) 
                  return true;
          }
  
          return false;
      }
  }
  ```

  

参考：https://leetcode-cn.com/problems/permutation-in-string/solution/zi-fu-chuan-de-pai-lie-by-leetcode-solut-7k7u/