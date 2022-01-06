package org.xhliu.kafkaexample;

public class BTree<Key extends Comparable<Key>, Value> {
    private final int M;

    /**
     * 该类表示树的节点，每个节点包含对应的元素列表
     */
    static final class Node {
        private int m; // 当前有效元素的数量
        /**
         * 元素的存储容器，按照 B 树的定义，这个容器中的元素应当是有序的
         * 注意：这里的容器通过数组来实现，这里的数据的实际有效元素从 1 开始计数，这是为了预留一个哨兵
         * 位置使得能够获取到最左边的节点的元素区间
         * <br />
         * <img src="https://s2.loli.net/2022/01/06/e39N4ruwRQHdyB7.jpg" />
         * <br />
         * 每个节点在创建时都会首先实例化哨兵元素，元素列表的大小为 M + 1，
         * 这是因为在处理的过程中允许节点的元素暂时地溢出 M - 1 的上限
         */
        private final Entry[] children;

        private Node(int m, int M) {
            this.m = m;
            this.children = new Entry[M + 1];
            this.children[0] = new Entry(null, null, null);
        }
    }

    /**
     * 具体存储键值对的对象，每个对象包含三个属性：key、value 和 next
     * value 存储的是具体的数据，可以某个文件块的位置、或者是某个网页的地址等等
     * <p>
     * 这里是只是使用 next 而不是使用传统的树的左右孩子节点的原因在于：<br />
     * 1. 引入左右孩子节点在这里是多余的，使用一个 next 指针完全是足够的 <br />
     * 2. 使用左右孩子节点可能会导致链接混乱
     */
    static class Entry {
        private Comparable key;
        private Object value;
        private Node next;

        Entry(Comparable key, Object value, Node next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private Node root; // B- 的根节点

    private int height; // 当前树的高度，有些操作必须依赖于树的高度来进行

    private int size; // 当前树中元素的个数

    public BTree(int m) {
        /*
            这里强制阶数 M 只能为奇数，不同的参考文档对于 B 树的定义不一致，有的认为
            B 树的每个节点的最大元素个数可以达到 M，但是一般认为每个节点最多只能有 M - 1 的元素

            如果每个节点最多只能有 M - 1 个元素，那么当 M 是偶数的时候，当两个节点合并时
            ，将会突破 M - 1上限，而如果将 M 设置为奇数的话，最终会留下多余一个元素，从而满足条件

            同时，将 M 强制设置为奇数，对于实现来讲更加简单
         */
        if (m % 2 == 0) {
            throw new IllegalArgumentException("阶数 M 只能是奇数");
        }

        M = m;
        root = new Node(0, M);
    }

    public int size() {
        return this.size;
    }

    public int height() {
        return this.height;
    }

    /**
     * 提供给客户端的 API，通过传入的 key 查找对应的 Value
     *
     * @param key : 待搜索的 key
     * @return : 如果能够查找到 key，那么直接返回对应的 value，否则返回 null
     */
    public Value get(Key key) {
        if (key == null) {
            throw new IllegalArgumentException("key 不能为 null");
        }

        return search(root, key);
    }

    /**
     * 一般常规化的搜索，如果在当前的搜索节点 x 查找到了指定的 key，则直接返回当前元素对应的 value
     * <br />
     * 如果无法再当前节点搜索到元素 key，那么将在对应的区间向下递归地进行搜索
     *
     * @param x   ：当前待搜索的节点 x
     * @param key ：待查找的 key
     * @return ：查找到的 key 所对应的 value，如果该树不包含该 key，则返回 null
     */
    @SuppressWarnings("unchecked")
    public Value search(Node x, Key key) {
        if (x == null) return null;
        Entry[] entries = x.children;
        for (int i = 1; i <= x.m; ++i) {
            if (eq(entries[i].key, key))
                return (Value) entries[i].value;

            if (less(entries[i - 1].key, key) && less(key, entries[i].key))
                return search(entries[i - 1].next, key);
        }

        // 尝试搜索最后一个节点的后一个区间
        return search(entries[x.m].next, key);
    }

    /**
     * 将对应的键值对 key-value 插入到树中，如果树中已经存在了 key，
     * 那么将使用这个键值对覆盖树中原有的键值对
     *
     * @param key   : 键值对对应的 key
     * @param value : 键值对对应的 value
     */
    public void put(Key key, Value value) {
        if (key == null) {
            throw new IllegalArgumentException("argument key fot put() is null");
        }

        Node res = insert(root, key, value, 0);
        size++;

        if (res == null) return;
        /*
            插入之后形成了分裂节点，此时树的高度增加，同时需要修改 root 节点指向的对象
         */
        root = res;
        height++;
    }

    /**
     * 在指定的一个节点中插入对应的键值对，首先会递归向下查找，在叶子节点中执行插入操作
     * 当插入之后可能会导致节点中元素个数 “溢出”，此时需要对该节点进行重平衡以维护节点
     * 的平衡性，重平衡之后可能也会使得父节点不再满足条件，因此同样地也需要对父节点进行
     * 重平衡，直到满足条件或者到达根节点
     *
     * @param x     : 当前插入的目标节点
     * @param key   : 待插入的键值对的 key
     * @param value : 待插入键值对的 value
     * @param h     : 当前节点所在的树的高度，如果 h 达到了树的高度，说明已经到达了叶子节点
     * @return : 插入成功之后，如果重平衡了节点，那么返回重平衡之后的根节点；否则，返回 null
     */
    @SuppressWarnings("unchecked")
    private Node insert(Node x, Key key, Value value, int h) {
        int idx;
        Entry t = new Entry(key, value, null);
        Entry[] entries = x.children;
        // 已经到达了叶子节点，直接进行插入
        if (h == height) {
            for (idx = 1; idx <= x.m; ++idx) {
                // 键值对已经存在，使用当前的键值对覆盖原有的键值对
                if (eq(key, entries[idx].key)) {
                    entries[idx].value = value;
                    size--;
                    return null;
                }

                /*
                    找到待插入的位置，如果小于中间的某个节点说明插入位置在中间，
                    否则将会插入到该节点元素列表的末尾
                 */
                if (less(key, entries[idx].key)) break;
            }
        } else {
            for (idx = 1; idx <= x.m; ++idx) {
                // 如果 key 对应当前节点中的某个元素，则替换掉它
                if (eq(key, entries[idx].key)) {
                    entries[idx].value = value;
                    size--;
                    return null;
                }

                if (less(entries[idx].key, key)) continue;
                break;
            }

            // 插入到前一个区间元素中，因为此时的元素已经大于现有的 key 了
            Node u = insert(entries[idx - 1].next, key, value, h + 1);
            // 插入结果为 null 说明没有发生节点分裂，正常返回即可
            if (u == null) return null;

            /*
                由于此时发生了节点分裂，需要将分裂后的节点的根节点插入到当前的节点中，
                首先需要找到根节点的插入位置
            */
            for (idx = 1; idx <= x.m; ++idx)
                if (less(u.children[1].key, entries[idx].key)) break;

            /*
                分裂后的节点将通过一个根节点和两个子节点的形式返回，为了调整这个根节点，应当将根节点的元素插入到
                当前节点，首先需要将根节点的左区间链接放入到插入的位置的节点

                idx 表示第一个大于分裂的根节点的位置，因此需要调整的是前面的区间链接节点
             */
            entries[idx - 1].next = u.children[0].next;
            t = u.children[1]; // t 表示待插入的节点
        }

        /*
            需要将 idx 之后的所有 children 元素向后移动一位，为新插入的键值对提供空间
         */
        if (M - idx >= 0)
            System.arraycopy(x.children, idx, x.children, idx + 1, M - idx);

        // 插入键值对
        x.children[idx] = t;
        x.m++;

        /*
            如果当前节点插入之后依旧满足限制条件，则正常执行;
            否则，需要对当前的节点进行分裂的操作
         */
        if (x.m < M) return null;
        return split(x);
    }

    /**
     * 通过传入的 key 删除对应的键值对，如果树中不存在这样的 key，则返回 null
     * 在删除的过程中依旧需要保持树的平衡性
     *
     * @param key : 待删除的键值对的 key
     * @return : 如果删除成功，则返回该 key 对应的键值对; 否则，返回 null
     */
    public Entry delete(Key key) {
        Entry entry = delete(null, root, key, 0);
        if (entry != null) size--;

        return entry;
    }

    /**
     * 通过传入的父节点和当前处理的节点，按照传入的 key 对对应的键值对进行删除
     *
     * @param parent : 当前处理的节点的父节点，特别地，根节点的父节点为 null
     * @param cur    : 当前处理的节点
     * @param key    : 待删除的 key
     * @param h      : 当前处理的节点的树的高度
     * @return : 如果删除成功，返回 key 在树中对应的键值对对象
     */
    @SuppressWarnings("unchecked")
    private Entry delete(Node parent, Node cur, Key key, int h) {
        Entry entry;
        int idx;
        // 待删除的节点是一个叶子节点，直接进行删除
        if (h == height) {
            for (idx = 1; idx <= cur.m; ++idx)
                if (eq(cur.children[idx].key, key)) break;

            // 如果当前叶子节点不存在这样的键值对元素，则跳过
            if (idx > cur.m) return null;
            entry = cur.children[idx];

            // 移动节点元素列表，删除元素
            if (cur.m + 1 - idx >= 0)
                System.arraycopy(cur.children, idx + 1, cur.children, idx, cur.m + 1 - idx);
            cur.m--;
        } else {
            /*
                待删除的节点不是叶子节点，那么需要从元素的所有后继节点中找到最小的元素（或者从前驱节点中找到最大的元素）
                覆盖掉当前 key 所在的元素，然后在后继节点中删除该最小元素
             */
            for (idx = 1; idx <= cur.m; ++idx) {
                if (eq(cur.children[idx].key, key)) break;

                // 当前节点在该节点的后继节点中，递归进行删除
                if (less(key, cur.children[idx].key)) {
                    entry = delete(cur, cur.children[idx - 1].next, key, h + 1);
                    if (cur.m < M / 2) reBalance(parent, cur, h);
                    return entry;
                }
            }

            // idx > cur.m 说明待删除的节点在最后的一个区间内，同样地，通过递归的方式进行删除
            if (idx > cur.m) {
                entry = delete(cur, cur.children[cur.m].next, key, h + 1);
                if (cur.m < M / 2) reBalance(parent, cur, h);
                return entry;
            }

            // 删除内部节点元素
            Entry min = min(cur);
            cur.children[idx].key = min.key;
            cur.children[idx].value = min.value;

            entry = delete(cur, cur.children[idx].next, (Key) min.key, h + 1);
        }

        // 如果删除节点之后该节点不满足条件，需要对该节点进行重平衡
        if (cur.m < M / 2) reBalance(parent, cur, h);

        return entry;
    }

    /**
     * 对传入的节点进行重平衡，具体的做法为: <br />
     *  1. 如果当前处理的节点的兄弟节点存在多余的元素，则将父节点的分隔节点移动到当前节点中，
     *     然后再将兄弟节点中的最小（最大）元素复制到父节点的分隔元素中 <br />
     *  2. 如果兄弟节点都不存在多余的元素，则将父节点和当前处理节点结合左兄弟节点（或右兄弟节点）
     *     成为一个新的节点，然后在父节点中移除这个分隔元素 <br />
     *  3. 由于合并之后会使得父节点的元素数目减少，此时父节点可能会不满足 B 树节点的限制条件，
     *     此时需要递归地重平衡父节点。如果此时的父节点是根节点，那么需要重新修改根节点，
     *     同时将树的高度 -1<br />
     *
     * @param parent : 当前处理的节点的父节点
     * @param cur : 当前的处理节点，父节点为 null 表示当前处理的是根节点
     * @param h : 当前处理的节点的树的高度
     */
    @SuppressWarnings("unchecked")
    private void reBalance(Node parent, Node cur, int h) {
        // parent 为 null 表示当前处理的节点是 root 节点，root 节点不需要重平衡
        if (parent == null) return;
        int idx;
        Entry[] children = parent.children;
        // 首先找到当前处理节点所在的区间元素
        for (idx = 1; idx <= parent.m; ++idx)
            if (less(cur.children[cur.m].key, children[idx].key))
                break;
        idx -= 1;

        // 找到该叶子节点的左右兄弟节点
        Node left = null, right = null;
        if (idx > 0) left = children[idx - 1].next;
        if (idx < parent.m) right = children[idx + 1].next;

        if (left == null && right == null) return;

        /*
            左子节点存在多余的元素，从左子节点借用一个元素，使得节点最终满足 B 树的条件
         */
        if (left != null && left.m > M / 2) {
            // 移动当前节点的元素，为新加入的元素腾出位置
            for (int i = cur.m + 1; i > idx; --i) {
                if (cur.children[i] == null)
                    cur.children[i] = new Entry(null, null, null);
                cur.children[i].key = cur.children[i - 1].key;
                cur.children[i].value = cur.children[i - 1].value;
            }

            // 复制属性到当前节点的第一个元素（从 1 开始计数）
            cur.children[1].key = children[idx].key;
            cur.children[1].value = children[idx].value;
            cur.children[1].next = cur.children[0].next;
            cur.children[0].next = left.children[left.m].next;
            cur.m++;

            // 将从左子节点借用到的元素的属性复制到父节点的分隔元素，使得树最终是有序的
            children[idx].key = left.children[left.m].key;
            children[idx].value = left.children[left.m].value;

            // 删除左子节点的最大元素
            left.children[left.m] = null;
            left.m--;
            return;
        }

        /*
            右子节点存在多余的元素，因此从右子节点借用一个元素到父节点的分隔节点，
            同时将原有的旧分隔元素移动到到当前的节点，使得它维持 B 树的结构
        */
        if (right != null && right.m > M / 2) {
            ++cur.m;
            // 如果此时这个位置的对象未实例化，那么首先实例化该位置的对象
            if (cur.children[cur.m] == null)
                cur.children[cur.m] = new Entry(null, null, null);

            // 单纯地复制属性到当前的节点，如果使用引用复制的话会导致出现冗余的链接，甚至出现环
            cur.children[cur.m].key = children[idx + 1].key;
            cur.children[cur.m].value = children[idx + 1].value;
            cur.children[cur.m].next = right.children[0].next;
            right.children[0].next = right.children[1].next;

            // 更新父节点的分隔元素
            children[idx + 1].key = right.children[1].key;
            children[idx + 1].value = right.children[1].value;

            // 由于右子节点被借用了一个元素，因此需要移动右子节点的元素列表使得其依旧是有序的
            if (right.m >= 0)
                System.arraycopy(right.children, 2, right.children, 1, right.m);
            right.m--;
            return;
        }

        /*
            由于左右兄弟节点都不存在多余的元素，因此需要从父节点借用一个元素，合并成为一个节点
            一般会优先选择左兄弟节点作为合并后的节点，因为这样就不需要移动前半部分元素
         */
        if (left != null) {
            // 首先将父节点的分隔节点复制到当前节点的末尾，由于这个位置可能未实例化，因此首先实例化
            ++left.m;
            if (left.children[left.m] == null)
                left.children[left.m] = new Entry(null, null, null);

            left.children[left.m].key = children[idx].key;
            left.children[left.m].value = children[idx].value;
            left.children[left.m].next = cur.children[0].next;
            // 复制父节点的分隔节点结束。。。。

            // 再将当前节点的所有元素复制到左兄弟节点，由于位置 0 是一个哨兵元素，因此从元素 1 开始进行复制
            for (int i = 1; i <= cur.m; ++i)
                left.children[++left.m] = cur.children[i];

            // 合并之后，会出现一条多余的链接，这个链接是多余的
            children[idx].next = null;
            // 删除父节点的分隔元素之后，移动父节点的分隔元素列表，使得原有的父节点的元素依旧是有序的
            if (parent.m + 1 - idx >= 0)
                System.arraycopy(children, idx + 1, children, idx, parent.m + 1 - idx);
            parent.m--;
            return;
        }

        /*
            由于不存在左兄弟节点，因此只能选择右边的兄弟节点，将右兄弟节点的元素复制到当前节点
            Hint：根据 B 树的定义，不可能存在既不含有左兄弟节点，也不含有右兄弟节点的节点
         */
        ++cur.m;
        if (cur.children[cur.m] == null)
            cur.children[cur.m] = new Entry(null, null, null);
        cur.children[cur.m].key = children[idx + 1].key;
        cur.children[cur.m].value = children[idx + 1].value;
        cur.children[cur.m].next = right.children[0].next;
        children[idx + 1].next = null;

        // 复制右兄弟节点的所有元素到当前的处理节点
        for (int i = 1; i <= right.m; ++i)
            cur.children[++cur.m] = right.children[i];

        // 调整父节点的元素列表
        if (parent.m + 1 - (idx + 1) >= 0)
            System.arraycopy(children, idx + 1 + 1, children, idx + 1, parent.m + 1 - (idx + 1));
        parent.m--;

        /*
            只有当处理节点所在的树的高度为 1 时，
            才有机会转变成为新的根节点，同时将树的高度 -1
         */
        if (parent.m == 0 && h == 1) {
            root = cur;
            height--;
        }
    }

    private Entry min(Node x) {
        if (x.children[1].next == null)
            return x.children[1];

        return min(x.children[1].next);
    }

    /**
     * 对传入的节点 x 进行分裂操作，具体的行为:
     * 由于 M 是奇数，因此达到上限时元素的个数一定也是奇数，这个时候就会取这个节点中的
     * 中位数作为根节点来维护 B 树节点的有序性; 之后，再将 x 中两边的元素平分到两个子节点中，
     * 调整对应的 next 链接，返回分裂之后的根节点
     *
     * @param x : 待分裂的节点 x
     * @return : 分裂之后的子树的根节点
     */
    private Node split(Node x) {
        Node t = new Node(M / 2, M);
        x.m = M / 2;

        // 将 x 中的后半部分的节点放入 t 中
        for (int i = 1; i <= M / 2; ++i) {
            t.children[i] = x.children[M / 2 + i + 1];
            x.children[M / 2 + i + 1] = null;
        }

        Node p = new Node(1, M); // 分裂后形成的根节点
        Entry mid = x.children[M / 2 + 1]; // x 的中间节点，它的属性将会被作为根节点的属性

        // 调整相关的链接
        t.children[0].next = mid.next;
        x.children[M / 2 + 1] = null; // clear mid
        p.children[0].next = x;
        p.children[1] = new Entry(mid.key, mid.value, t);

        return p;
    }

    @SuppressWarnings("unchecked")
    private boolean less(Comparable<Key> key1, Comparable<Key> key2) {
        if (key1 == null) return true;
        if (key2 == null) return false;
        return key1.compareTo((Key) key2) < 0;
    }

    @SuppressWarnings("unchecked")
    private boolean eq(Comparable<Key> key1, Comparable<Key> key2) {
        if (key1 == null || key2 == null) return false;
        return key1.compareTo((Key) key2) == 0;
    }

    public String toString() {
        return toString(root, height, "") + "\n";
    }

    private String toString(Node h, int ht, String indent) {
        if (h == null) return "";
        StringBuilder s = new StringBuilder();
        Entry[] children = h.children;

        if (ht == 0) {
            for (int j = 0; j <= h.m; j++) {
                if (children[j] == null) continue;
                s.append(indent).append(children[j].key).append(" ").append(children[j].value).append("\n");
            }
        } else {
            for (int j = 0; j <= h.m; j++) {
                if (children[j] == null) continue;
                if (j > 0) s.append(indent).append("(").append(children[j].key).append(")\n");
                assert children[j] != null;
                s.append(toString(children[j].next, ht - 1, indent + "     "));
            }
        }
        return s.toString();
    }

    public static void main(String[] args) {
        BTree<String, String> st = new BTree<>(5);

        st.put("www.cs.princeton.edu", "128.112.136.12");
        st.put("www.cs.princeton.edu", "128.112.136.11");
        st.put("www.princeton.edu", "128.112.128.15");
        st.put("www.yale.edu", "130.132.143.21");
        st.put("www.simpsons.com", "209.052.165.60");
        st.put("www.apple.com", "17.112.152.32");
        st.put("www.amazon.com", "207.171.182.16");
        st.put("www.ebay.com", "66.135.192.87");
        st.put("www.cnn.com", "64.236.16.20");
        st.put("www.google.com", "216.239.41.99");
        st.put("www.nytimes.com", "199.239.136.200");
        st.put("www.microsoft.com", "207.126.99.140");
        st.put("www.dell.com", "143.166.224.230");
        st.put("www.slashdot.org", "66.35.250.151");
        st.put("www.espn.com", "199.181.135.201");
        st.put("www.weather.com", "63.111.66.11");
        st.put("www.yahoo.com", "216.109.118.65");


        System.out.println("cs.princeton.edu:  " + st.get("www.cs.princeton.edu"));
        System.out.println("hardvardsucks.com: " + st.get("www.harvardsucks.com"));
        System.out.println("simpsons.com:      " + st.get("www.simpsons.com"));
        System.out.println("apple.com:         " + st.get("www.apple.com"));
        System.out.println("ebay.com:          " + st.get("www.ebay.com"));
        System.out.println("dell.com:          " + st.get("www.dell.com"));
        System.out.println();

        System.out.println("size:    " + st.size());
        System.out.println("height:  " + st.height());
        System.out.println(st);
        System.out.println();

        BTree<Integer, Integer> bTree = new BTree<>(5);
        for (int i = 1; i <= 300; ++i)
            bTree.put(i, i);
        for (int i = 1; i <= 250; ++i)
            bTree.delete(i);
        System.out.println("B-Tree size  :   " + bTree.size());
        System.out.println("B-Tres height:   " + bTree.height());
        System.out.println(bTree);
        System.out.println();
    }
}