# 滑动窗口问题（一）最小覆盖子串

## 问题描述

​	给你一个字符串 `s` 和一个字符串 `t`。返回 `s` 中涵盖 `t` 所有字符的最小子串。如果 `s` 中不存在这样的子串，那么返回 ""。

​	比如，对于输入的字符串 `s` = "ADOBECODEBANC"，`t` = "ABC"，那么 `s` 中的最小子串为 `BANC`

## 解决思路

​	首先考虑使用暴力的方式进行解决，对于 `s` 来讲，截取一段区间，然后区间的右边界每次移动一个字符，然后判断当前的截取区间是否包含 `t` 中的所有字符， 如果包含所有字符，那么再移动左边界，直到不包含所有的字符。最终遍历得到最下的区间长度即可。

​	以上面的示例为例：

1. 初始的状态，将区间选择为 [0, 0]，使用 left 指针表示左区间，right 表示右区间。

   <img src="https://s3.jpg.cm/2021/09/10/ISKw7D.png" />

2. 将 right 向右移动，直到 [left, right] 区间内的字符包含了 `t` 字符串内的所有字符，此时的情况如下所示：

   <img src="https://s3.jpg.cm/2021/09/10/ISK38S.png" />

3. 移动left，直到 [left, right] 区间内的子串不再满足 ”包含目标字符串的所有字符“ 的条件，此时的 left 指针指向的是 D，由于是首次移动，因此 [left - 1, right] 的区间子串就是当前的第一个满足条件的子串。

4. 移动 right，直到 [left, right] 的区间子串再次满足 ”包含目标字符串的所有字符“的条件，此时的情况如下所示

   <img src="https://s3.jpg.cm/2021/09/10/ISh2HC.png" />

5. 移动 left，直到不再满足条件，此时的 left 指向 O（第二个，索引为 6），此时的 [left - 1, right] 的区间长度要比之前的要长，因此不需要更新区间

6. 移动 right，直到再次满足条件或者到达字符串的 s 的末尾，此时的情况如下所示：
   <img src="https://s3.jpg.cm/2021/09/10/IShDWp.png" />

7. 移动 left，直到不再满足条件或者越过 right，在当前情况下 left 指向 A （索引为 10），此时的子串区间为 [left - 1, right]，由于此区间的长度小于之前的区间长度，因此更新区间。

8. 最终得到 [9, 12] 区间的子串为最终的答案。

​	以上就是一个被称为 ”**滑动窗口**“ 大致的思路，与双指针类似，通过 right 的不断移动扩大窗口区间，left 的移动来收缩空间，使得这个区间类似一个窗口在移动一样。实际上这就是一个双指针的巧妙运用。

​	由于两个字符串之间的比较，暴力的比较的方式会导致 $O(n^2)$ 的时间复杂度。因此可以从优化比较字符串的角度来进行优化。使用哈希表来存储 `t` 中每个字符出现的频率，这样可以将时间复杂度从 $O(n^2)$ 降低到 $O(n)$

## 实现

​	可能算法看上去很简单，但是实际上实现起来的细节需要做很多的处理。主要的细节存在于以下几个方面：

- 如何使用哈希表来验证区间内是否存在目标子串的所有字符
- 对于重复的元素应该如何处理
- 边界问题

```java
public class Solution {
    private String mcs(String s, String t) {
        if (s.length() < t.length()) return "";

        // 将这两个字符串转换为字符数组，可以有效提高字符的访问速度
        char[] source = s.toCharArray();
        char[] target = t.toCharArray();

        final int N = source.length;

        /*
        	使用数组来代替哈希表来存储对应的字符信息，这是一种很好的替代方案
        	map 用于存储目标字符的数量信息，之所以大小设置为 128 是因为字符的范围在 A-z，自动转换为整形下标的话再这个范围内
        	tmpMap 记录 ‘滑动’ 过程中子串的字符数量信息
        */
        final int[] map     =   new int[128];
        final int[] tmpMap  =   new int[128];
        for (char ch : target) map[ch]++;

        int left = 0, right = 0;
        // 满足条件的子串区间
        final int[] ans = new int[]{-1, Integer.MAX_VALUE - 2};
        while (right < N) {
            // right 向右滑动
            tmpMap[source[right]]++;

            // 对应上文算法的 left 右移
            while (left <= right && check(map, tmpMap)) {
                tmpMap[source[left]]--;

                /*
                	根据本人踩过的坑，区间的更新需要放到此循环内部是合理的，
                	不要试图把它移到外边，否则，可能会遇到一些让人头疼的问题
                */
                if (right - left < ans[1] - ans[0]) {
                    ans[0] = left;
                    ans[1] = right;
                }

                left++;
            }

            right++;
        }

        // 如果区间没有更新，则说明没有这样的子串
        if (ans[0] == -1) return "";

        return s.substring(ans[0], ans[1] + 1);
    }

    /*
    	检查 ‘滑动’过程中的子串是否含有目标子串的所有字符
    */
    private boolean check(int[] map, int[] tmpMap) {
        for (int i = 0; i < map.length; ++i) {
            /* 
            	实际上，只要当前 tmpMap 包含的目标字符串的字符的数量
            	大于 map 中存储的数量，那么就说明当前的子串就是符合条件的
            	（因为 left 可以右移来移除多余的字符）
            	
             	对于不在目标字符串中的字符，则无需做比较
            */
            if (tmpMap[i] < map[i]) return false;
        }

        return true;
    }
}
```



