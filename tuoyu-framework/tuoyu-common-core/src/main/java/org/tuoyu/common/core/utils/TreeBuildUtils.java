package org.tuoyu.common.core.utils;

import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNodeConfig;
import cn.hutool.core.lang.tree.TreeUtil;
import cn.hutool.core.lang.tree.parser.NodeParser;
import org.tuoyu.common.core.utils.reflect.ReflectUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 构建树形结构。
 * <p>
 * 树节点使用 hutool 的 {@link Tree}（它是一个 Map，接口按其中的键输出 JSON）。本类构建的节点把名称放在键
 * {@code label} 下（前端读取 {@code label}），其他键保持 hutool 的默认值（{@code id}、{@code parentId}、
 * {@code weight}、{@code children}）；同级节点按 weight 升序。使用本类自己的配置，不修改 hutool 的全局默认配置。
 *
 * @author tuoyu
 */
public final class TreeBuildUtils {

    /**
     * 名称键
     */
    private static final String NAME_KEY = "label";

    private TreeBuildUtils() {
    }

    /**
     * 构建树，顶级父 ID 取第一个元素的 {@code parentId} 属性（调用它的 {@code getParentId()}）。
     *
     * @param list       节点数据
     * @param nodeParser 把数据填充到树节点
     * @param <T>        数据类型
     * @param <K>        ID 类型
     * @return 顶级父 ID 下的树；list 为空时为可修改的空列表
     */
    public static <T, K> List<Tree<K>> build(List<T> list, NodeParser<T, K> nodeParser) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        K parentId = ReflectUtils.invokeGetter(list.get(0), "parentId");
        return buildUnder(list, parentId, nodeParser);
    }

    /**
     * 按分类函数分组后由调用方设置子节点，不使用 hutool 的树。
     *
     * @param items      元素
     * @param parentId   顶级父 ID
     * @param classifier 分类函数（通常取父 ID），不能返回 null
     * @param action     对每个元素调用一次，参数为元素与分组表（父 ID → 子元素），调用方据此设置子节点
     * @param <K>        ID 类型
     * @param <T>        元素类型
     * @return 分组表中 parentId 对应的元素；没有时为 null
     */
    public static <K, T> List<T> build(List<T> items, K parentId, Function<T, K> classifier,
                                       BiConsumer<T, Map<K, List<T>>> action) {
        Map<K, List<T>> groups = new LinkedHashMap<>();
        for (T item : items) {
            K key = Objects.requireNonNull(classifier.apply(item), "分类函数的结果不能为 null");
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        for (T item : items) {
            action.accept(item, groups);
        }
        return groups.get(parentId);
    }

    /**
     * 构建有多个顶级节点的树：顶级父 ID 为不在 ID 集合中的父 ID。不同顶级父 ID 之间的先后顺序不保证。
     *
     * @param list        节点数据
     * @param getId       取 ID
     * @param getParentId 取父 ID
     * @param parser      把数据填充到树节点
     * @param <T>         数据类型
     * @param <K>         ID 类型
     * @return 全部顶级父 ID 下的树；list 为空时为空列表
     */
    public static <T, K> List<Tree<K>> buildMultiRoot(List<T> list, Function<T, K> getId, Function<T, K> getParentId,
                                                      NodeParser<T, K> parser) {
        List<Tree<K>> result = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return result;
        }
        Set<K> ids = new HashSet<>();
        Set<K> parentIds = new LinkedHashSet<>();
        for (T item : list) {
            ids.add(getId.apply(item));
            K parentId = getParentId.apply(item);
            if (parentId != null) {
                parentIds.add(parentId);
            }
        }
        parentIds.removeAll(ids);
        for (K rootParentId : parentIds) {
            result.addAll(buildUnder(list, rootParentId, parser));
        }
        return result;
    }

    /**
     * 建立节点路径表：深度优先（先父后子，同级按列表顺序）遍历，键为从根到节点的取值用连接符连接的路径。
     * 取值为 null 或空串的节点连同其子树跳过；路径重复时后者覆盖前者。
     *
     * @param trees       树
     * @param joiner      路径连接符
     * @param fieldGetter 取节点的值
     * @param <K>         ID 类型
     * @return 路径 → 节点，按遍历顺序；trees 为空时为空 Map
     */
    public static <K> Map<String, Tree<K>> buildTreeNodeMap(List<Tree<K>> trees, String joiner,
                                                            Function<Tree<K>, CharSequence> fieldGetter) {
        Map<String, Tree<K>> result = new LinkedHashMap<>();
        if (trees != null) {
            for (Tree<K> tree : trees) {
                collectPaths(tree, null, joiner, fieldGetter, result);
            }
        }
        return result;
    }

    private static <K> void collectPaths(Tree<K> node, String parentPath, String joiner,
                                         Function<Tree<K>, CharSequence> fieldGetter, Map<String, Tree<K>> result) {
        CharSequence value = fieldGetter.apply(node);
        if (value == null || value.isEmpty()) {
            return;
        }
        String path = parentPath == null ? value.toString() : parentPath + joiner + value;
        result.put(path, node);
        Collection<Tree<K>> children = node.getChildren();
        if (children != null) {
            for (Tree<K> child : children) {
                collectPaths(child, path, joiner, fieldGetter, result);
            }
        }
    }

    private static <T, K> List<Tree<K>> buildUnder(List<T> list, K parentId, NodeParser<T, K> parser) {
        List<Tree<K>> trees = TreeUtil.build(list, parentId, config(), parser);
        return trees == null ? new ArrayList<>() : trees;
    }

    /**
     * hutool 的配置是可修改的对象，每次构建用一份新的，避免被其他代码改动
     */
    private static TreeNodeConfig config() {
        TreeNodeConfig config = new TreeNodeConfig();
        config.setNameKey(NAME_KEY);
        return config;
    }

}
