package org.tuoyu.common.core.utils;

import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNodeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TreeBuildUtils：树形结构")
class TreeBuildUtilsTest {

    private static List<Long> ids(List<Tree<Long>> trees) {
        return trees.stream().map(Tree::getId).toList();
    }

    @Test
    @DisplayName("按第一个元素的父 ID 构建；名称放在 label 键下；同级按 weight 升序")
    void buildFromFirstParent() {
        List<Menu> menus = List.of(
            new Menu(1L, 0L, "系统管理", 2),
            new Menu(2L, 0L, "系统监控", 1),
            new Menu(3L, 1L, "用户管理", 2),
            new Menu(4L, 1L, "角色管理", 1));

        List<Tree<Long>> trees = TreeBuildUtils.build(menus, (menu, tree) -> tree.setId(menu.getMenuId())
            .setParentId(menu.getParentId())
            .setName(menu.getMenuName())
            .setWeight(menu.getOrderNum()));

        assertThat(ids(trees)).containsExactly(2L, 1L);
        Tree<Long> system = trees.get(1);
        assertThat(system.get("label")).isEqualTo("系统管理");
        assertThat(system.containsKey("name")).isFalse();
        assertThat(system.getName()).isEqualTo("系统管理");
        assertThat(ids(system.getChildren())).containsExactly(4L, 3L);
    }

    @Test
    @DisplayName("空列表返回可修改的空列表")
    void buildEmpty() {
        List<Tree<Long>> trees = TreeBuildUtils.build(new ArrayList<Menu>(), (menu, tree) -> tree.setId(menu.getMenuId()));
        assertThat(trees).isEmpty();
        trees.add(new Tree<>());
        assertThat(TreeBuildUtils.<Menu, Long>buildMultiRoot(List.of(), Menu::getMenuId, Menu::getParentId,
            (menu, tree) -> tree.setId(menu.getMenuId()))).isEmpty();
    }

    @Test
    @DisplayName("不修改 hutool 的全局默认配置（其他直接使用 hutool 树的代码不受影响）")
    void keepsHutoolDefaults() {
        TreeBuildUtils.build(List.of(new Menu(1L, 0L, "a", 1)), (menu, tree) -> tree.setId(menu.getMenuId())
            .setParentId(menu.getParentId()).setName(menu.getMenuName()));
        assertThat(TreeNodeConfig.DEFAULT_CONFIG.getNameKey()).isEqualTo("name");
    }

    @Test
    @DisplayName("多个顶级节点：顶级父 ID 为不在 ID 集合中的父 ID，名称同样放在 label 键下")
    void buildMultiRoot() {
        List<Menu> nodes = List.of(
            new Menu(1L, 0L, "root-a", 1),
            new Menu(2L, 1L, "child-a", 1),
            new Menu(10L, 9L, "root-b", 1));

        List<Tree<Long>> result = TreeBuildUtils.buildMultiRoot(nodes, Menu::getMenuId, Menu::getParentId,
            (node, tree) -> tree.setId(node.getMenuId()).setParentId(node.getParentId()).setName(node.getMenuName()));

        Set<Long> rootIds = result.stream().map(Tree::getId).collect(Collectors.toSet());
        assertThat(rootIds).containsExactlyInAnyOrder(1L, 10L);
        Tree<Long> first = result.stream().filter(tree -> tree.getId().equals(1L)).findFirst().orElseThrow();
        assertThat(ids(first.getChildren())).containsExactly(2L);
        assertThat(first.get("label")).isEqualTo("root-a");
        assertThat(first.containsKey("name")).isFalse();
    }

    @Test
    @DisplayName("分组构建：按分类函数分组，对每个元素调用一次回调，返回父 ID 对应的组")
    void buildByClassifier() {
        Item root1 = new Item(1L, 0L);
        Item root2 = new Item(2L, 0L);
        Item child = new Item(3L, 1L);
        List<Item> items = List.of(root1, root2, child);
        List<Item> visited = new ArrayList<>();

        List<Item> roots = TreeBuildUtils.build(items, 0L, Item::getParentId, (item, table) -> {
            visited.add(item);
            item.setChildren(table.getOrDefault(item.getId(), List.of()));
        });

        assertThat(roots).containsExactly(root1, root2);
        assertThat(visited).containsExactly(root1, root2, child);
        assertThat(root1.getChildren()).containsExactly(child);
        assertThat(root2.getChildren()).isEmpty();
        assertThat(TreeBuildUtils.build(items, 99L, Item::getParentId, (item, table) -> {
        })).isNull();
    }

    @Test
    @DisplayName("节点路径表：深度优先，值为空的节点连同子树跳过")
    void treeNodeMap() {
        Tree<Long> root = tree(1L, "root");
        Tree<Long> child = tree(2L, "child");
        Tree<Long> leaf = tree(3L, "leaf");
        Tree<Long> unnamed = tree(4L, "");
        Tree<Long> hidden = tree(5L, "hidden");
        child.setChildren(List.of(leaf));
        unnamed.setChildren(List.of(hidden));
        root.setChildren(List.of(unnamed, child));
        Tree<Long> other = tree(6L, "other");

        Map<String, Tree<Long>> result = TreeBuildUtils.buildTreeNodeMap(List.of(root, other), "/", Tree::getName);

        assertThat(result.keySet()).containsExactly("root", "root/child", "root/child/leaf", "other");
        assertThat((Object) result.get("root/child/leaf")).isSameAs(leaf);
        assertThat(TreeBuildUtils.<Long>buildTreeNodeMap(null, "/", Tree::getName)).isEmpty();
    }

    private static Tree<Long> tree(Long id, String name) {
        Tree<Long> tree = new Tree<>();
        tree.setId(id);
        tree.setName(name);
        return tree;
    }

    /**
     * JavaBean 形式的节点：按第一个元素构建时通过 getParentId() 读取顶级父 ID
     */
    public static class Menu {

        private final Long menuId;

        private final Long parentId;

        private final String menuName;

        private final Integer orderNum;

        public Menu(Long menuId, Long parentId, String menuName, Integer orderNum) {
            this.menuId = menuId;
            this.parentId = parentId;
            this.menuName = menuName;
            this.orderNum = orderNum;
        }

        public Long getMenuId() {
            return menuId;
        }

        public Long getParentId() {
            return parentId;
        }

        public String getMenuName() {
            return menuName;
        }

        public Integer getOrderNum() {
            return orderNum;
        }
    }

    public static class Item {

        private final Long id;

        private final Long parentId;

        private List<Item> children;

        public Item(Long id, Long parentId) {
            this.id = id;
            this.parentId = parentId;
        }

        public Long getId() {
            return id;
        }

        public Long getParentId() {
            return parentId;
        }

        public List<Item> getChildren() {
            return children;
        }

        public void setChildren(List<Item> children) {
            this.children = children;
        }
    }
}
