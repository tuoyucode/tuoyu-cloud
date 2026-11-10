package org.tuoyu.mes.label.domain.bo;

/**
 * 标签打印的数量上限：一次打印的标签都在浏览器中排版，数量过多时页面生成与打印预览都会很慢
 *
 * @author tuoyu
 */
public final class MesLabelLimits {

    /**
     * 一次最多打印的标签数
     */
    public static final int MAX_LABELS = 500;

    private MesLabelLimits() {
    }

}
