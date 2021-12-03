# BFS（一）单词接龙

对应 <a href="https://leetcode-cn.com/problems/word-ladder/">LeetCode 127 单词接龙</a> 

### 问题定义

给定一个字典序列 `wordList`，一个初始的单词 `beginWord` 和一个目标单词 `endWord`，现在要求每次变换满足以下条件将 `beginWord` 转换为 `endWord`：

- 每次只能转换一个字母
- 转换后的单词必须出现在 `wordList` 中

求是否能够在满足对应的转换条件的前提下，能否将 `beginWord` 转换成 `endWord`，如果可以转换，则返回转换的最少次数；如果不能转换，则返回 0

数据范围：

- $1 <= beginWord.length <= 10$
- $endWord.length == beginWord.length$
- $1 <= wordList.length <= 5000$
- $wordList[i].length == beginWord.length$
- $beginWord$、$endWord$ 和 $wordList[i]$ 由小写英文字母组成
- $beginWord != endWord$



### 解决思路

- `BFS`

  这个问题是一个典型的 `BFS` 问题，只需要每次遍历时对每个单词的每个位置进行相应的转换，再进行比较即可，使用一个 `Map` 来记录当前的操作次数

- 双向 `BFS`

  由于数据量比较大，使用一般的 `BFS` 的搜索方式使得导致每次的维度的节点数量爆炸式的增长，根据题意，最终的 `end` 需要在 `wordList` 中才有可能能进行转换，因此可以从 `beginWord` 和 `endWord` 两个方向出发，使得搜索的节点数大幅度减少，从而减少计算时间

- `A*`

  每个转换的单词之间的相差的字母的数量可以看成是一个带有权重的边，此时这个问题就可以转换成为一般的图搜索问题，即找到一条权重最小的路径从 `beinWord` ——> `endWord`



### 实现 

- `BFS`

  ```java
  class Solution {
      public int ladderLength(String beginWord, String endWord, List<String> wordList) {
          if (!wordList.contains(endWord)) return 0;
  
          Deque<String> deque = new LinkedList<>();
          Map<String, Integer> map = new HashMap<>(); // 用于记录已经访问的路径长度
  
          deque.offer(beginWord);
          map.put(beginWord, 1);
  
          while (!deque.isEmpty()) {
              int size = deque.size(); // 每层需要遍历的节点的数量
              while (size-- > 0) {
                  String node = deque.poll();
                  int n = node.length();
                  char[] array = node.toCharArray();
                  // 转换部分。。
                  for (int i = 0; i < n; ++i) {
                      char ch = array[i];
                      for (int j = 0; j < 26; ++j) {
                          array[i] = (char) ('a' + j);
                          String sub = String.valueOf(array);
  
                          if (!wordList.contains(sub)) continue;
                          if (map.containsKey(sub)) continue;
                          
                          if (sub.equals(endWord)) 
                              return map.get(node) + 1;
                          
                          map.put(sub, map.get(node) + 1);
                          deque.offer(sub);
                      }
  
                      array[i] = ch;
                  }
              }
          }
  
          return 0;
      }
  }
  ```

  直接使用 `BFS` 的方式会导致访问的节点数量爆炸性地增长，因此这种方式会导致超时

  

- 双向 `BFS`

  ```java
  class Solution {
      private final Set<String> set = new HashSet<>();
  
      public int ladderLength(String begin, String end, List<String> wordList) {
          if (!wordList.contains(end)) return 0;
  
          set.addAll(wordList);
  
          int ans = bfs(begin, end);
  
          return ans == -1 ? 0 : ans + 1;
      }
  
      int bfs(String begin, String end) {
          Deque<String> cur = new LinkedList<>();
          Deque<String> other = new LinkedList<>();
          
          // 从 beiginWord 向下搜索
          Map<String, Integer> top = new HashMap<>();
          // 从 endWord 向上搜素
          Map<String, Integer> bottom = new HashMap<>();
  
          top.put(begin, 0);
          bottom.put(end, 0);
  
          cur.offer(begin);
          other.offer(end);
  
          while (!cur.isEmpty() && !other.isEmpty()) {
              int t = -1;
              // 交替搜索，使得每次遍历的节点数是平衡的
              if (cur.size() <= other.size()) {
                  t = update(cur, top, bottom);
              } else {
                  t = update(other, bottom, top);
              }
  
              if (t != -1) return t;
          }
  
          return -1;
      }
  
      int update(
          Deque<String> d,
          Map<String, Integer> top,
          Map<String, Integer> bottom
      ) {
          String node = d.poll();
          int val = top.get(node);
  
          int n = node.length();
          
          // 转换每个位置的字母，搜索满足条件的单词
          char[] array = node.toCharArray();
          for (int i = 0; i < n; ++i) {
              char ch = array[i];
              for (int j = 0; j < 26; ++j) {
                  array[i] = (char) ('a' + j);
                  String tmp = String.valueOf(array);
  
                  if (!set.contains(tmp)) continue;
                  if (top.containsKey(tmp)) continue;
  
                  if (bottom.containsKey(tmp)) 
                      return val + bottom.get(tmp) + 1;
  
                  d.offer(tmp);
                  top.put(tmp, val + 1);
              }
              array[i] = ch;
          }
  
          return -1;
      }
  }
  ```

  

- `A*` 搜索

  ```java
  class Solution {
      static class Node {
          String str;
          int val;
  
          Node(String _str, int _val) {
              this.str= _str;
              this.val = _val;
          }
      }
  
      String s, e;
      int INF = 0x3f3f3f3f;
      Set<String> set = new HashSet<>();
  
      public int ladderLength(String _s, String _e, List<String> wordList) {
          this.s = _s;
          this.e = _e;
  
          this.set.addAll(wordList);
  
          int ans = aStar();
  
          return ans == -1 ? 0 : ans + 1;
      }
  
      int aStar() {
          PriorityQueue<Node> pq = new PriorityQueue<>((a, b) -> a.val - b.val);
          Map<String, Integer> dist = new HashMap<>();
          dist.put(s, 0);
          pq.offer(new Node(s, find(s)));
          
          // 搜索边。。
          while (!pq.isEmpty()) {
              Node node = pq.poll();
              String str = node.str;
              if (str.equals(e)) break;
  
              int distance = dist.get(str);
              int n = str.length();
              
              // 字母转换部分
              char[] array = str.toCharArray();
              for (int i = 0; i < n; ++i) {
                  char ch = array[i];
                  for (int j = 0; j < 26; ++j) {
                      array[i] = (char) ('a' + j);
                      String sub = String.valueOf(array);
                      if (!set.contains(sub)) continue;
  
                      if (!dist.containsKey(sub) || dist.get(sub) > distance + 1) {
                          dist.put(sub, distance + 1);
                          pq.offer(new Node(sub, find(sub) + dist.get(sub)));
                      }
                  }
  
                  array[i] = ch;
              }
          }
  
          return dist.containsKey(e) ? dist.get(e) : -1;
      }
  
      int find(String str) {
          if (str.length() != e.length()) return INF;
          int n = str.length();
          int ans = 0;
  
          for (int i = 0; i < n; ++i) {
              ans += str.charAt(i) == e.charAt(i) ? 0 : 1;
          }
  
          return ans;
      }
  }
  ```



参考：

<sup>[1]</sup> https://leetcode-cn.com/problems/word-ladder/solution/gong-shui-san-xie-ru-he-shi-yong-shuang-magjd/

