// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import jakarta.persistence.*;
import java.time.Instant;

/** 不可变节目快照；草稿修改不会修改正在播放的内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "published_release")
public class PublishedRelease {
  @Id
  @Column(length = 36)
  public String id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "playlist_id", nullable = false)
  public Long playlistId;

  @Column(nullable = false, length = 160)
  public String name;

  @Column(nullable = false, columnDefinition = "longtext")
  public String snapshot;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(nullable = false, length = 80)
  public String actor;

  @Column(name = "request_hash", nullable = false, length = 64)
  public String requestHash;
}
