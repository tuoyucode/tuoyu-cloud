package org.tuoyu.gen.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 比较生成的代码与期望输出（{@code src/test/resources/gen-golden/} 下的文件）。
 * <ul>
 *     <li>Java：比较去掉注释、package 与 import 语句、全部空白之后的代码；package 必须相同；
 *     期望文件中的每条 import 都必须出现（顺序不限，可以有多余的）；</li>
 *     <li>SQL、XML：去掉 {@code --} 开头的注释行后，比较去掉全部空白的文本。</li>
 * </ul>
 * 注释、空行、缩进与换行方式不在比较范围内，注释的内容由各测试单独检查。
 */
public final class GeneratedCode {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(static\\s+)?([\\w.*]+)\\s*;");

    private GeneratedCode() {
    }

    /**
     * 是否重写快照：{@code -Dtuoyu.gen.update-golden=true} 时，{@link #assertSnapshot} 不比较，而是把当前输出写入
     * {@code src/test/resources/gen-golden/}（相对模块目录，即 Maven 运行测试时的工作目录）。用于有意修改模板之后更新快照
     */
    public static boolean updateGolden() {
        return Boolean.getBoolean("tuoyu.gen.update-golden");
    }

    /**
     * 与快照逐字比较（换行统一为 \n）；{@link #updateGolden()} 时写入快照
     */
    public static void assertSnapshot(String goldenPath, String actual, String message) {
        if (updateGolden()) {
            try {
                Path target = Path.of("src", "test", "resources", "gen-golden").resolve(goldenPath);
                Files.createDirectories(target.getParent());
                Files.writeString(target, actual, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return;
        }
        assertEquals(golden(goldenPath), actual, message);
    }

    public static String golden(String path) {
        try (InputStream in = GeneratedCode.class.getClassLoader().getResourceAsStream("gen-golden/" + path)) {
            if (in == null) {
                throw new IllegalStateException("缺少期望输出 gen-golden/" + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void assertJava(String goldenPath, String actual) {
        String expected = golden(goldenPath);
        assertEquals(packageName(expected), packageName(actual), goldenPath + "：package");
        List<String> actualImports = imports(actual);
        for (String required : imports(expected)) {
            boolean present = actualImports.contains(required);
            if (!present && required.endsWith(".*")) {
                // 按需导入可以写成同一个包中的单类型导入
                String prefix = required.substring(0, required.length() - 1);
                present = actualImports.stream().anyMatch(name -> name.startsWith(prefix)
                    && name.indexOf('.', prefix.length()) < 0);
            }
            assertTrue(present, goldenPath + "：缺少 import " + required + "\n" + actual);
        }
        assertEquals(javaBody(expected), javaBody(actual), goldenPath + "：代码（忽略注释与空白）\n" + actual);
    }

    public static void assertText(String goldenPath, String actual) {
        assertEquals(compactText(golden(goldenPath)), compactText(actual), goldenPath + "\n" + actual);
    }

    public static String packageName(String java) {
        Matcher matcher = PACKAGE.matcher(stripComments(java));
        return matcher.find() ? matcher.group(1) : null;
    }

    public static List<String> imports(String java) {
        List<String> result = new ArrayList<>();
        Matcher matcher = IMPORT.matcher(stripComments(java));
        while (matcher.find()) {
            result.add((matcher.group(1) == null ? "" : "static ") + matcher.group(2));
        }
        return result;
    }

    /**
     * 去掉注释、package 与 import 语句、全部空白之后的代码
     */
    public static String javaBody(String java) {
        String code = stripComments(java);
        code = PACKAGE.matcher(code).replaceAll("");
        code = IMPORT.matcher(code).replaceAll("");
        return removeWhitespace(code);
    }

    /**
     * 去掉 Java 的块注释与行注释（字符串与字符字面量中的内容不受影响）
     */
    public static String stripComments(String java) {
        StringBuilder out = new StringBuilder(java.length());
        int i = 0;
        int n = java.length();
        while (i < n) {
            char c = java.charAt(i);
            if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < n && java.charAt(end) != c) {
                    if (java.charAt(end) == '\\') {
                        end++;
                    }
                    end++;
                }
                end = Math.min(end + 1, n);
                out.append(java, i, end);
                i = end;
            } else if (c == '/' && i + 1 < n && java.charAt(i + 1) == '*') {
                int end = java.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                out.append(' ');
            } else if (c == '/' && i + 1 < n && java.charAt(i + 1) == '/') {
                int end = java.indexOf('\n', i);
                i = end < 0 ? n : end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * SQL、XML 等文本：去掉 {@code --} 开头的注释行与全部空白
     */
    public static String compactText(String text) {
        StringBuilder out = new StringBuilder();
        for (String line : text.replace("\r\n", "\n").split("\n")) {
            if (!line.trim().startsWith("--")) {
                out.append(line).append('\n');
            }
        }
        return removeWhitespace(out.toString());
    }

    public static String removeWhitespace(String text) {
        return text.replaceAll("\\s+", "");
    }

    /**
     * 某个声明（例如 {@code private String postCode;}）之前紧挨着的 Javadoc 注释的文本；没有时返回 null
     */
    public static String javadocBefore(String java, String declaration) {
        int index = java.indexOf(declaration);
        if (index < 0) {
            return null;
        }
        String before = java.substring(0, index);
        int end = before.lastIndexOf("*/");
        int start = before.lastIndexOf("/**");
        if (start < 0 || end < start) {
            return null;
        }
        String between = before.substring(end + 2);
        // 注释与声明之间只能有注解与空白
        if (!between.replaceAll("@\\w+(\\([^)]*\\))?", "").isBlank()) {
            return null;
        }
        return before.substring(start, end + 2);
    }
}
