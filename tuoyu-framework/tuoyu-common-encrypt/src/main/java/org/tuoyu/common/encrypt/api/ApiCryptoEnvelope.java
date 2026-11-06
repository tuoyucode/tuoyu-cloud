package org.tuoyu.common.encrypt.api;

/**
 * 加密请求的信封
 *
 * @param v     协议版本，目前为 1
 * @param kid   加密 AES 密钥所用公钥的 kid
 * @param key   RSA-OAEP 加密后的 32 字节 AES 密钥（Base64）
 * @param iv    12 字节 GCM 初始向量（Base64）
 * @param ts    客户端毫秒时间戳
 * @param nonce 16 字节随机数（Base64）
 * @param data  AES-256-GCM 密文，末尾附认证标签（Base64）
 * @author tuoyu
 */
public record ApiCryptoEnvelope(Integer v, String kid, String key, String iv, Long ts, String nonce, String data) {
}
