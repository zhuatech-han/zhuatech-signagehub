// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import jakarta.persistence.*;

/** 可编辑节目草稿，与已发布快照分离。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "playlist")
public class Playlist {
  @Column(name = "category_id", nullable = false)
  public Long categoryId;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "name", nullable = false, length = 160)
  public String name;

  @Column(name = "name_en", nullable = false, length = 160)
  public String nameEn;

  @Column(name = "orientation", nullable = false, length = 20)
  public String orientation;

  @Column(name = "fit", nullable = false, length = 20)
  public String fit;

  @Column(name = "background", nullable = false, length = 7)
  public String background;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
