// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import jakarta.persistence.*;

/** 有序媒体或纯文本节目项。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "playlist_item")
public class PlaylistItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "playlist_id", nullable = false)
  public Long playlistId;

  @Column(name = "asset_id", nullable = true)
  public Long assetId;

  @Column(name = "position", nullable = false)
  public int position;

  @Column(name = "text", nullable = true, length = 4000)
  public String text;

  @Column(name = "seconds", nullable = false)
  public int seconds;

  @Column(name = "color", nullable = false, length = 7)
  public String color;
}
