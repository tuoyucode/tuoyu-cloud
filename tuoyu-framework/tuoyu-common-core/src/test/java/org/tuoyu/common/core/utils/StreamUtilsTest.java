package org.tuoyu.common.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StreamUtils：集合转换")
class StreamUtilsTest {

    private static final List<Item> ITEMS = Arrays.asList(
        new Item(1L, "first"),
        new Item(2L, "second"),
        null,
        new Item(1L, "duplicate"),
        new Item(3L, null)
    );

    @Test
    @DisplayName("filter：保持顺序，结果可修改；null 元素也交给条件；空集合返回可修改的空列表")
    void filter() {
        List<Integer> odd = StreamUtils.filter(List.of(1, 2, 3, 4, 5), value -> value % 2 == 1);
        assertThat(odd).containsExactly(1, 3, 5);
        odd.add(7);
        assertThat(odd).hasSize(4);

        List<Item> nulls = StreamUtils.filter(ITEMS, item -> item == null);
        assertThat(nulls).hasSize(1).containsNull();

        List<Integer> empty = StreamUtils.filter(null, value -> true);
        assertThat(empty).isEmpty();
        empty.add(1);
        assertThat(StreamUtils.filter(new ArrayList<Integer>(), value -> true)).isEmpty();
    }

    @Test
    @DisplayName("findFirst：第一个满足条件的元素")
    void findFirst() {
        assertThat(StreamUtils.findFirst(List.of(1, 2, 3, 4), value -> value > 1)).contains(2);
        assertThat(StreamUtils.findFirst(List.of(1, 2), value -> value > 5)).isEmpty();
        Optional<Integer> none = StreamUtils.findFirst(null, value -> true);
        assertThat(none).isEmpty();
    }

    @Test
    @DisplayName("join：映射结果为 null 的跳过，默认逗号分隔，空集合为空串")
    void join() {
        List<String> values = List.of("first", "skip", "last");
        assertThat(StreamUtils.join(values, value -> "skip".equals(value) ? null : value)).isEqualTo("first,last");
        assertThat(StreamUtils.join(values, value -> value, "|")).isEqualTo("first|skip|last");
        assertThat(StreamUtils.join(List.<String>of(), value -> value)).isEmpty();
        assertThat(StreamUtils.join(null, value -> String.valueOf(value), "|")).isEmpty();
    }

    @Test
    @DisplayName("toIdentityMap：跳过 null 元素，键重复时保留第一个")
    void toIdentityMap() {
        Map<Long, Item> map = StreamUtils.toIdentityMap(ITEMS, Item::id);
        assertThat(map).containsOnlyKeys(1L, 2L, 3L);
        assertThat(map.get(1L).value()).isEqualTo("first");
        map.put(9L, new Item(9L, "x"));
        assertThat(StreamUtils.toIdentityMap(null, Item::id)).isEmpty();
    }

    @Test
    @DisplayName("toMap：跳过 null 元素，键重复时保留第一个，值为 null 时抛出异常")
    void toMap() {
        List<Item> items = Arrays.asList(new Item(1L, "first"), null, new Item(1L, "second"), new Item(2L, "third"));
        Map<Long, String> map = StreamUtils.toMap(items, Item::id, Item::value);
        assertThat(map).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "first", 2L, "third"));
        map.put(9L, "x");
        assertThat(StreamUtils.toMap(List.<Item>of(), Item::id, Item::value)).isEmpty();
        assertThatThrownBy(() -> StreamUtils.toMap(ITEMS, Item::id, Item::value))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("groupByKey：跳过 null 元素，键按首次出现的顺序，组内保持原顺序")
    void groupByKey() {
        Map<Long, List<Item>> groups = StreamUtils.groupByKey(ITEMS, Item::id);
        assertThat(groups.keySet()).containsExactly(1L, 2L, 3L);
        assertThat(groups.get(1L)).extracting(Item::value).containsExactly("first", "duplicate");
        assertThat(StreamUtils.groupByKey(null, Item::id)).isEmpty();
    }

    @Test
    @DisplayName("toList / toSet：映射后跳过 null，结果可修改；null 元素也交给映射函数")
    void toListAndSet() {
        List<Item> withoutNull = Arrays.asList(new Item(1L, "a"), new Item(2L, null), new Item(3L, "a"));
        List<String> list = StreamUtils.toList(withoutNull, Item::value);
        assertThat(list).containsExactly("a", "a");
        list.add("b");
        Set<String> set = StreamUtils.toSet(withoutNull, Item::value);
        assertThat(set).containsExactly("a");
        set.add("b");

        List<String> mapped = StreamUtils.toList(ITEMS, item -> item == null ? "null-element" : item.value());
        assertThat(mapped).containsExactly("first", "second", "null-element", "duplicate");
        assertThat(StreamUtils.toList(null, Item::value)).isEmpty();
        assertThat(StreamUtils.toSet(List.<Item>of(), Item::value)).isEmpty();
    }

    private record Item(Long id, String value) {
    }
}
