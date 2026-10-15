// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import jakarta.persistence.*;
import java.time.Instant;

/** 无重叠的限时投放，结束后回到基线。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "schedule")
public class Schedule {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "screen_id", nullable = false)
  public Long screenId;

  @Column(name = "release_id", nullable = false, length = 36)
  public String releaseId;

  @Column(name = "starts_at", nullable = false)
  public Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  public Instant endsAt;

  @Column(name = "cancelled", nullable = false)
  public boolean cancelled;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
