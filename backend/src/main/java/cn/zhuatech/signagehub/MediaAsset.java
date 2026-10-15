// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import jakarta.persistence.*;
import java.time.Instant;

/** 不可变媒体及服务器验证的摘要、格式和大小。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "media_asset")
public class MediaAsset {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "name", nullable = false, length = 160)
  public String name;

  @Column(name = "kind", nullable = false, length = 20)
  public String kind;

  @Column(name = "mime", nullable = false, length = 60)
  public String mime;

  @Column(name = "sha256", nullable = false, length = 64)
  public String sha256;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "blob_name", nullable = false, length = 80)
  public String blobName;

  @Column(name = "bytes", nullable = false)
  public long bytes;

  @Column(name = "width", nullable = false)
  public int width;

  @Column(name = "height", nullable = false)
  public int height;

  @Column(name = "duration", nullable = false)
  public double duration;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;

  @Column(name = "version", nullable = false)
  public long version = 1;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
