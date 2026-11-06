package org.tuoyu.common.encrypt.api;

/**
 * 加密响应的信封，密钥为请求中客户端生成的 AES 密钥
 *
 * @param v    协议版本，目前为 1
 * @param iv   12 字节 GCM 初始向量（Base64）
 * @param data AES-256-GCM 密文，末尾附认证标签（Base64）
 * @author tuoyu
 */
public record ApiCryptoResponseEnvelope(int v, String iv, String data) {
}
