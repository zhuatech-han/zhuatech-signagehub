// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

/** 只含业务拒绝代码，不泄露票码、账号或密钥。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public class Refusal extends RuntimeException {
  Refusal(String code) {
    super(code);
  }
}
