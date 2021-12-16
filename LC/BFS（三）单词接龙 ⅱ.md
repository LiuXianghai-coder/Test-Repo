# BFS（三）单词接龙 ⅱ

对应 [126. 单词接龙 II](https://leetcode-cn.com/problems/word-ladder-ii/)

<br />

### 问题描述

按字典 wordList 完成从单词 beginWord 到单词 endWord 转化，一个表示此过程的 转换序列 是形式上像 beginWord -> s1 -> s2 -> ... -> sk 这样的单词序列，并满足：

- 每对相邻的单词之间仅有单个字母不同。
- 转换过程中的每个单词 si（1 <= i <= k）必须是字典 wordList 中的单词。注意，beginWord 不必是字典 wordList 中的单词。
- sk == endWord

给你两个单词 beginWord 和 endWord ，以及一个字典 wordList 。请你找出并返回所有从 beginWord 到 endWord 的 最短转换序列 ，如果不存在这样的转换序列，返回一个空列表。每个序列都应该以单词列表 [beginWord, s1, s2, ..., sk] 的形式返回。

<br />

### 解决思路

相比较于之前的 <a href="https://leetcode-cn.com/problems/word-ladder/">127.单词接龙</a>，该问题需要在找到最短路径的基础上再找到所有的可行路径。一般的思路就是首先搜索一遍，找到可能存在的路径，再通过深度优先搜索找到所有的最短路径

<br />

### 实现

具体实现如下

```java
class Solution {
    List<List<String>> ans = new ArrayList<>();
    Set<String> dict = new HashSet<>();

    public List<List<String>> findLadders(String s, String e, List<String> wl) {
        dict.addAll(wl);
        if (!dict.contains(e)) return ans;

        Map<String, Integer> steps = new HashMap<>();
        Map<String, List<String>> from = new HashMap<>(); // 记录每层出现的单词，方便之后的 DFS
        steps.put(s, 0);

        int step = 1;
        boolean found = false;
        int n = s.length();

        Deque<String> d = new LinkedList<>();
        d.offer(s);
        
        // 首先 BFS 找到可行的路径
        while (!d.isEmpty()) {
            int size = d.size();
            while (size-- > 0) {
                String curWord = d.poll();
                char[] array = curWord.toCharArray();
                for (int i = 0; i < n; ++i) {
                    char ch = array[i];
                    for (int j = 0; j < 26; ++j) {
                        array[i] = (char) ('a' + j);
                        String sub = String.valueOf(array);
                        if (steps.containsKey(sub) && step == steps.get(sub)) {
                            from.get(sub).add(curWord);
                        }
                        if (!dict.contains(sub)) continue;

                        dict.remove(sub);
                        d.offer(sub);

                        from.putIfAbsent(sub, new ArrayList<>());
                        from.get(sub).add(curWord);

                        steps.put(sub, step);
                        if (sub.equals(e)) found = true;
                    }
                    array[i] = ch;
                }
            }

            step++;
            if (found) break;
        }

        if (!found) return ans;
        
        // DFS 搜素所有的可行路径
        Deque<String> path = new ArrayDeque<>();
        path.add(e);
        dfs(path, from, e, s);

        return ans;
    }

    void dfs(Deque<String> path, Map<String, List<String>> from, String cur, String des) {
        if (cur.equals(des)) {
            ans.add(new ArrayList<>(path));
            return;
        }

        for (String prev : from.get(cur)) {
            path.offerFirst(prev);
            dfs(path, from, prev, des);
            path.pollFirst();
        }
    }
}
```

复杂度分析：略