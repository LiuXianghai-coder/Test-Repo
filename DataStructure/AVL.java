import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AVL 树的简单实现
 */
public class AVL {
    /**
     * AVL 树的节点对象
     */
    private static class Node implements Comparable<Node> {
        private int val;
        private Node left, right;
        private int height = 0;

        private Node(int val, Node left, Node right) {
            this.val = val;
            this.left = left;
            this.right = right;
        }

        @Override
        public int compareTo(Node o) {
            if (null == o) return 1;

            return this.val - o.val;
        }

        @Override
        public boolean equals(Object obj) {
            if (null == obj) return false;
            if (obj.getClass() != this.getClass())
                return false;

            Node o2 = (Node) obj;

            return this.val == o2.val;
        }

        @Override
        public String toString() {
            return "val: " + val;
        }
    }

    private Node root = null;

    public AVL(int factor) {
        this.factor = factor;
    }

    // 高度阈值，当左右子树的高度差大于此阈值时，将会重新调整树
    private final int factor;

    /**
     * 插入一个节点，返回插入后的根节点，如果插入重复元素，则会覆盖原有的节点
     * @param node ： 待插入的节点元素
     * @return ： 插入后的根节点
     */
    public Node insert(Node node) {
        // 根节点为 null， 表示当前树还没有节点，因此将该节点置为根节点
        if (null == root) {
            root = node;
            root.height = 1;
            return root;
        }

        // 递归插入该节点，同时更新根节点
        root = insert(node, root);
        return root;
    }

    private Node insert(Node node, Node parent) {
        if (parent == null) {
            parent = node;
            return parent;
        }

        if (less(node, parent)) {
            parent.left = insert(node, parent.left);
        } else {
            parent.right = insert(node, parent.right);
        }

        return reBalance(parent, node);
    }

    /**
     * 重新调节当前的节点树
     * @param parent ： 父节点
     * @param node ： 插入的节点
     * @return ： 重新调节之后的得到的根节点
     */
    private Node reBalance(Node parent, Node node) {
        parent.height = Math.max(getHeight(parent.left), getHeight(parent.right)) + 1;
        /*
            当左右子树的高度差达到指定阈值时，需要进行相应的调整
         */
        if (getHeight(parent.left) - getHeight(parent.right) == factor) {
            if (less(node, parent.left))
                /*
                   此时的情况如下所示（带 ? 表示该节点可能存在）：node 节点在 left 节点的左边，此时的情况如 左左情况一致
                   因此，只需将根节点进行一次右旋转即可。
                                parent
                               /      \
                             left     right?
                            /    \
                          left?  right?
                          /   \
                       node?  node? （node 的位置不确定可能在左边，可能在右边，甚至可能在父节点 left）
                */
                parent = rrRotate(parent, parent.right);
            else
                /*
                   此时的情况如下所示（带 ? 表示该节点可能存在）：node 节点在 left 节点的右边，此时的情况如 左右情况一致
                   此时，需要先对左子节点进行一次左旋转，然后对根节点进行一次右旋转
                                parent
                               /      \
                             left     right?
                            /    \
                          left?  right?
                                 /    \
                               node?  node? （node 的位置不确定可能在左边，可能在右边，甚至可能在父节点 right）
                 */
                parent = lrRotate(parent, parent.left);
        } else if (getHeight(parent.right) - getHeight(parent.left) == factor) {
            if (less(node, parent.right))
                /*
                   此时的情况如下所示（带 ? 表示该节点可能存在）：node 节点在 right 节点的左边，此时的情况如 右左情况一致
                   此时，需要先对右子节点进行一次右旋转，然后对根节点进行一次左旋转
                                parent
                               /      \
                             left?    right
                                     /    \
                                   left?  right?
                                   /    \
                                 node?  node? （node 的位置不确定可能在左边，可能在右边，甚至可能在父节点 left）
                */
                parent = rlRotate(parent, parent.right);
            else
                /*
                   此时的情况如下所示（带 ? 表示该节点可能存在）：node 节点在 right 节点的右边，此时的情况如 右右情况一致
                   因此，对根节点执行一次左旋转即可
                                parent
                               /      \
                             left?    right
                                     /    \
                                   left?  right?
                                          /    \
                                        node?  node? （node 的位置不确定可能在左边，可能在右边，甚至可能在父节点 right）
                */
                parent = llRotate(parent, parent.right);
        }

        return parent;
    }

    /**
     * 删除对应的 node 节点，然后返回删除节点之后的根节点
     * @param node：待删除的节点
     * @return ： 删除节点之后的根节点
     */
    public Node delete(Node node) {
        return delete(node, root);
    }

    /**
     * 删除操作的基本流程：
     *  1、找到对应的节点，然后使用右子节点的最小节点替换掉当前的节点
     *  2、在右子树中递归地删除使用的替换节点，直到为空
     *  3、在删除的过程中需要重新调整树
     * @param node ： 待删除的节点
     * @param parent ：当前处理的节点
     * @return ： 处理完之后的根节点
     */
    private Node delete(Node node, Node parent) {
        // 当前处理的节点为空，说明已经到到递归终点了，停止递归
        if (parent == null)
            return null;

        /*
            删除节点之后，需要使用一个别的节点来替换该节点以维持树的平衡
            有两种方案可以选取：左子树的最右节点和右子树的最左节点，这里选用的是第二种
         */
        if (node.equals(parent)) {
            if (parent.right == null) {
                // 右子节点为空，那么只需要放弃这个节点的引用，使得垃圾收集器删除即可
                parent = parent.left;
                return parent;
            } else {
                // 查找右子树的最左节点，以充当平衡节点
                Node rNode = parent.right;
                while (rNode.left != null)
                    rNode = rNode.left;
                // 查找替换节点结束

                parent.val = rNode.val; // 更新当前的待删除节点，此时的树依旧是平衡的
                // 由于已经将替换节点提上来了，因此需要删除这个节点
                parent.right = delete(rNode, parent.right);
            }
        } else if (less(node, parent)) {
            parent.left = delete(node, parent.left);
        } else {
            parent.right = delete(node, parent.right);
        }

        // 重新调整树的平衡
        parent.height = Math.max(getHeight(parent.left), getHeight(parent.right)) + 1;
        if (getHeight(parent.right) - getHeight(parent.left) == factor) {
            if (getHeight(parent.right.right) >= getHeight(parent.right.left))
                /*
                   此时的情况如下所示（带 ? 表示该节点可能存在）：与右右情况一致
                   因此，对根节点执行一次左旋转即可
                                parent
                               /      \
                             left?    right
                                     /    \
                                   left?  right?
                                          /    \
                                        left?   right?
                */
                parent = llRotate(parent, parent.right);
            else
                /*
                   此时的情况如下所示（带 ? 表示该节点可能存在）：与右左情况一致
                   需要对右子节点执行一次右旋转，然后对根节点执行一次左旋转
                                parent
                               /      \
                             left?    right
                                     /    \
                                   left?  right?
                                  /    \
                               left?   right?
                */
                parent = rlRotate(parent, parent.right);

        } else if (getHeight(parent.left) - getHeight(parent.right) == factor) {
            if (getHeight(parent.left.left) >= getHeight(parent.left.right))
                /*
                   此时的情况如下所示（带 ? 表示该节点可能存在）：与左左情况一致
                   对根节点执行一次右旋转即可
                                parent
                               /      \
                             left    right?
                            /    \
                          left?  right?
                         /    \
                      left?   right?
                */
                parent = rrRotate(parent, parent.left);
            else
                /*
                   此时的情况如下所示（带 ? 表示该节点可能存在）：与左右情况一致
                   需要对左子节点执行一次左旋转，然后对根节点执行一次右旋转即可
                                parent
                               /      \
                             left    right?
                            /    \
                          left?  right?
                                 /    \
                              left?   right?
                */
                parent = lrRotate(parent, parent.right);
        }

        return parent;
    }

    public Node search(Node node) {
        return search(node, root);
    }

    private Node search(Node node, Node root) {
        if (root == null) return null;

        if (root.equals(node))
            return root;

        if (less(node, root))
            return search(node, root.left);

        return search(node, root.right);
    }

    public int getHeight(Node node) {
        return node == null ? -1 : node.height;
    }

    public void traverse() {
        System.out.println("Medium Traverse: ");
        mediumTraverse(root);
        System.out.println();
        System.out.println();

        System.out.println("####################################");
        System.out.println("Last Traverse: ");
        lastTraverse(root);

        // 创建对应的 dot 文件以便生成对应的树图
        StringBuilder sb = new StringBuilder();
        sb.append("digraph avl {\n");
        firstTraverse(root, sb);
        sb.append("}");
        try {
            File file = new File("/tmp/Graphviz");
            if (!file.exists()) {
                if (file.mkdir()) {
                    System.out.println("mkdir success...");
                }
                file = new File("/tmp/Graphviz/avl.dot");

                if (!file.exists()) {
                    if (file.createNewFile()) {
                        System.out.println("create avl.dot file success....");
                    }
                }
            }

            /*
                生成二插树的脚本文件：
                    https://gist.githubusercontent.com/nanpuyue/b5950f20937f01aa43227d269aa83918/raw/dec7bc293ef051ca159c546d14c6caed75be111c/tree.g
                保存该文件为 tree.g
                执行：dot /tmp/Graphviz/avl.dot | gvpr -c -f tree.g | neato -n -Tpng -o avl.png 即可
             */
            Path path = Paths.get("/tmp/Graphviz/avl.dot");
            Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void firstTraverse(Node node, StringBuilder builder) {
        if (node == null) return;

        if (node.left != null)
            builder.append("\t")
                    .append(node.val)
                    .append("->")
                    .append(node.left.val)
                    .append(";\n");
        if (node.right != null)
            builder.append("\t")
                    .append(node.val)
                    .append("->")
                    .append(node.right.val)
                    .append(";\n");

        firstTraverse(node.left, builder);
        firstTraverse(node.right, builder);
    }

    private void mediumTraverse(Node node) {
        if (node == null) return;

        mediumTraverse(node.left);
        System.out.printf("%d ", node.val);
        mediumTraverse(node.right);
    }

    private void lastTraverse(Node node) {
        if (node == null) return;

        lastTraverse(node.left);
        lastTraverse(node.right);
        System.out.printf("%d ", node.val);
    }

    private boolean less(Node o1, Node o2) {
        return o1.compareTo(o2) < 0;
    }

    /*
        对应 AVL 树中的左左情况：

                k1
               /  \
              k2   D                           k2
             /  \       ============>         /  \
            k3   C                           k3   k1
           /  \                             / \   / \
          A    B                           A   B  C  D
    */
    /*
     转换过程（单纯右转）
             above                  below
            /     \                /     \
          below    Z   =====>    X       above
         /     \                         /   \
        X       Y                       Y     Z

    */
    /*
        返回右旋转之后的 below 节点作为根节点
     */
    private Node rrRotate(Node above, Node below) {
        above.left = below.right;
        below.right = above;

        above.height = Math.max(getHeight(above.left), getHeight(above.right)) + 1;
        below.height = Math.max(getHeight(below.left), getHeight(below.right)) + 1;

        return below;
    }

    /*
        对应 AVL 树中的右右情况：

                k1
               /  \
              A   k2                            k2
                  /  \       ============>     /  \
                 B   k3                       k1   k3
                    /  \                     / \   / \
                    C   D                   A   B  C  D
    */
    /*
     转换过程（单纯左转）：

            above                              below
           /     \                            /     \
          X       below     ==========>    above     Z
                 /     \                  /     \
                Y       Z                X       Y
     */
    /*
        返回左旋转之后的 below 节点
     */
    private Node llRotate(Node above, Node below) {
        above.right = below.left;
        below.left = above;

        above.height = Math.max(getHeight(above.left), getHeight(above.right)) + 1;
        below.height = Math.max(getHeight(below.left), getHeight(below.right)) + 1;

        return below;
    }


    /*
        对应 AVL 中的左右情况：

                k1                              k1
               /  \                            /  \
              k2   D                          k3   D                           k3
             /  \       ============>        /  \          =========>         /   \
            A    k3                         k2   C                           k2    k1
                /  \                       / \                              / \   /  \
               B    C                     A   B                            A   B  C   D

     */
    /**
     *  转换过程：首先对子节点进行左转，然后再对根节点进行右转
     *  具体详情可以查看：左旋转 {@link AVL#llRotate(Node, Node)} 和右旋转 {@link AVL#rrRotate(Node, Node)}
     */
    private Node lrRotate(Node above, Node below) {
        above.left = llRotate(above.left, below.right);
        return rrRotate(above, above.left);
    }

    /*
        对应 AVL 树中的右左情况

                k1                              k1
               /  \                            /  \
              A   k2                          A   k3                       k3
                 /  \       ============>        /  \       =========>    /  \
                k3   D                          B   k2                   k1   k2
               /  \                                 / \                 / \  /  \
              B    C                               C   D               A   B C   D
     */

    /**
     * 
     * 转换过程：先对子节点进行右旋转，再对跟节点进行左旋转
     * 具体详情可以查看：右旋转 {@link AVL#rrRotate(Node, Node)} 和 左旋转 {@link AVL#llRotate(Node, Node)}
     */
    private Node rlRotate(Node above, Node below) {
        above.right = rrRotate(above.right, below.left);
        return llRotate(above, above.right);
    }

    /**
     * 单元测试，测试时不要使用单例对象，这将会导致一些不可预期的问题（个人惨痛经历告知于此）
     */
    public static void main(String[] args) {
        AVL avl = new AVL(2);
        Node node7 = new Node(7, null, null);
        Node node8 = new Node(8, null, null);

        avl.insert(new Node(3, null, null));
        avl.insert(new Node(4, null, null));
        avl.insert(new Node(2, null, null));
        avl.insert(new Node(1, null, null));
        avl.insert(new Node(6, null, null));
        avl.insert(new Node(5, null, null));
        avl.insert(new Node(7, null, null));
        avl.insert(new Node(8, null, null));
        avl.insert(new Node(10, null, null));

        avl.delete(new Node(7, null, null));
        avl.delete(new Node(8, null, null));

        System.out.println(avl.search(new Node(4, null, null)).toString());

        avl.traverse();
    }
}

