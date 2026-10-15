// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import jakarta.persistence.*;

/** 已发布屏幕与节目快照的不可变关联。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "delivery")
public class Delivery {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "screen_id", nullable = false)
  public Long screenId;

  @Column(name = "release_id", nullable = false, length = 36)
  public String releaseId;
}
