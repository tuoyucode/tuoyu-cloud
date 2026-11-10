package org.tuoyu.mes.label.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 标签上的条码类型（条码由前端生成）
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum BarcodeType implements CodeEnum {

    /**
     * 一维码 Code 128
     */
    CODE128("code128", "一维码"),
    /**
     * 二维码 QR Code
     */
    QRCODE("qrcode", "二维码");

    private final String code;

    private final String label;

    BarcodeType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String label() {
        return label;
    }

}
