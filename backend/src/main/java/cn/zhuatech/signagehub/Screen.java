// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import jakarta.persistence.*;
import java.time.Instant;

/** 屏幕身份、基线节目及播放器实际回报。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "screen")
public class Screen {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "group_id", nullable = true)
  public Long groupId;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "token_hash", nullable = true, length = 64)
  public String tokenHash;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;

  @Column(name = "version", nullable = false)
  public long version = 1;

  @Column(name = "baseline_release", nullable = true, length = 36)
  public String baselineRelease;

  @Column(name = "last_seen", nullable = true)
  public Instant lastSeen;

  @Column(name = "applied_release", nullable = true, length = 36)
  public String appliedRelease;

  @Column(name = "player_state", nullable = true, length = 30)
  public String playerState;

  @Column(name = "player_error", nullable = true, length = 120)
  public String playerError;
}
