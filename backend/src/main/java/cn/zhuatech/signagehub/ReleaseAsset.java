// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import jakarta.persistence.*;

/** 发布媒体外键及设备下载授权依据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "release_asset")
public class ReleaseAsset {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "release_id", nullable = false, length = 36)
  public String releaseId;

  @Column(name = "asset_id", nullable = false)
  public Long assetId;
}
