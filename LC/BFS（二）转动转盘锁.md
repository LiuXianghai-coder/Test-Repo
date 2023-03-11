# BFS（二）转动转盘锁

对应 <a href="https://leetcode-cn.com/problems/open-the-lock/">LeetCode 752.转动转盘锁</a>

### 问题定义

你有一个带有四个圆形拨轮的转盘锁。每个拨轮都有10个数字： '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' 。每个拨轮可以自由旋转：例如把 '9' 变为 '0'，'0' 变为 '9' 。每次旋转都只能旋转一个拨轮的一位数字。

锁的初始数字为 '0000' ，一个代表四个拨轮的数字的字符串

列表 `deads` 包含了一组死亡数字，一旦拨轮的数字和列表里的任何一个元素相同，这个锁将会被永久锁定，无法再被旋转。

字符串 target 代表可以解锁的数字，你需要给出解锁需要的最小旋转次数，如果无论如何不能解锁，返回  -1 。

### 解决思路

- 穷举

    这个问题比较简单，使用穷举的方式列出从 "0000" 开始满足所有条件的转动情况，进行转动分析即可

    这里的穷举使用 `BFS` 是一个很好的思路，每层的高度就对应着转动的次数，只要当前层中存在目标数字 $target$ ，那么当前的层数就是其搜索的次数

- 双向 `BFS` 优化

    由于每层的每个数字的都可以向上或向下转动，因此在搜索过程中将会出现 “搜索爆炸” 的情况。可选的解决方案交替使用从上和从下的搜索方式进行遍历，这样就能够有效地解决 “搜索爆炸” 的问题

细节处理：实际上，有些被搜索过的数字可能在之后会再次出现，因此需要记录之前已经搜索过的数字；使用双向搜索的方式进行搜索时，使用 `Map` 来记录两个方向的搜索次数，当搜索成功时相加即可。

### 实现

- 一般的穷举

    ```java
    class Solution {
        Set<String> deadSet = new HashSet<>();
        static String start = "0000";
        Set<String> accessed = new HashSet<>();
    
        public int openLock(String[] deadends, String target) {
            for (String s : deadends) deadSet.add(s);
            
            // 特殊情况处理
            if (deadSet.contains(start) || deadSet.contains(target)) 
                return -1;
            if (target.equals(start)) return 0;
    
            Deque<String> deque = new LinkedList<>();
            deque.offer(start);
            accessed.add(start);
    
            int ans = 0;
            while (!deque.isEmpty()) {
                int size = deque.size();
                ans++;
    
                while (size-- > 0) {
                    String word = deque.poll();
                    for (int i = 0; i < 4; ++i) {
                        String plus = plus(word.toCharArray(), i);
                        if (!deadSet.contains(plus) && !accessed.contains(plus)) {
                            if (plus.equals(target)) return ans;
                            deque.offer(plus);
                            accessed.add(plus);
                        }
    
                        String minus = minus(word.toCharArray(), i);
                        if (!deadSet.contains(minus) && !accessed.contains(minus)) {
                            if (minus.equals(target)) return ans;
                            deque.offer(minus);
                            accessed.add(minus);
                        }
                    }
                }
            }
    
            return -1;
        }
        
        // 指定的数字位 +1
        String plus(char[] array, int index) {
            if (array[index] < '9') array[index] = (char) (array[index] + 1);
            else array[index] = '0';
    
            return String.valueOf(array);
        }
        
        // 指定的数字位 -1
        String minus(char[] array, int index) {
            if (array[index] > '0') array[index] = (char) (array[index] - 1);
            else array[index] = '9';
    
            return String.valueOf(array);
        }
    }
    ```

    复杂度分析：略

- 双向 `BFS`

    ```java
    class Solution {
        Set<String> deadSet = new HashSet<>();
        static String start = "0000";
        Set<String> accessed = new HashSet<>();
    
        public int openLock(String[] deadends, String target) {
            for (String s : deadends) deadSet.add(s);
    
            if (deadSet.contains(start) || deadSet.contains(target)) 
                return -1;
            if (target.equals(start)) return 0;
    
            Deque<String> top = new LinkedList<>();
            Deque<String> bottom = new LinkedList<>();
            Map<String, Integer> topMap = new HashMap<>();
            Map<String, Integer> bottomMap = new HashMap<>();
    
            top.offer(start);
            topMap.put(start, 0);
    
            bottom.offer(target);
            bottomMap.put(target, 0);
    
            while (!top.isEmpty() && !bottom.isEmpty()) {
                int t = -1;
                if (top.size() <= bottom.size()) {
                    t = update(top, bottom, topMap, bottomMap);
                } else {
                    t = update(bottom, top, bottomMap, topMap);
                }
    
                if (t != -1) return t;
            }
    
            return -1;
        }
    
        int update(
            Deque<String> d1,
            Deque<String> d2,
            Map<String, Integer> map1,
            Map<String, Integer> map2
        ) {
            int size = d1.size();
    
            while (size-- > 0) {
                String word = d1.poll();
    
                for (int i = 0; i < 4; ++i) {
                    String plus = plus(word.toCharArray(), i);
                    if (!deadSet.contains(plus) && !map1.containsKey(plus)) {
                        if (map2.containsKey(plus)) 
                            return map1.get(word) + map2.get(plus) + 1; // 本次已经再转动了一次
                        d1.offer(plus);
                        map1.put(plus, map1.get(word) + 1);
                    }
    
                    String minus = minus(word.toCharArray(), i);
                    if (!deadSet.contains(minus) && !map1.containsKey(minus)) {
                        if (map2.containsKey(minus)) 
                            return map1.get(word) + map2.get(minus) + 1;
                        d1.offer(minus);
                        map1.put(minus, map1.get(word) + 1);
                    }
                }
            }
    
            return -1;
        }
    
        String plus(char[] array, int index) {
            if (array[index] < '9') array[index] = (char) (array[index] + 1);
            else array[index] = '0';
    
            return String.valueOf(array);
        }
    
        String minus(char[] array, int index) {
            if (array[index] > '0') array[index] = (char) (array[index] - 1);
            else array[index] = '9';
    
            return String.valueOf(array);
        }
    }
    ```

    复杂度分析：略