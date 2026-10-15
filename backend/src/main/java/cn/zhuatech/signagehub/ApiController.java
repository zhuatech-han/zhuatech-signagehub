// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import java.util.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 管理业务与设备接口；授权统一由事务服务校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final SignageService signs;
  final MediaService media;
  final AdminService admin;
  final DeviceService devices;

  public ApiController(
      SignageService signs, MediaService media, AdminService admin, DeviceService devices) {
    this.signs = signs;
    this.media = media;
    this.admin = admin;
    this.devices = devices;
  }

  /** 组织业务目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/catalog")
  public Object catalog() {
    return signs.catalog();
  }

  /** 受控媒体上传；名称作为独立字段而非磁盘路径。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/media")
  public Object upload(
      @RequestParam Long departmentId, @RequestParam String name, @RequestPart MultipartFile file) {
    return media.upload(departmentId, name, file);
  }

  /** 管理端预览不使用设备令牌。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/media/{id}/content")
  public ResponseEntity<Resource> content(@PathVariable Long id) {
    return file(media.readable(id));
  }

  private ResponseEntity<Resource> file(MediaAsset a) {
    return ResponseEntity.ok()
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(a.mime))
        .contentLength(a.bytes)
        .body(new FileSystemResource(media.path(a)));
  }

  /** 修改媒体名称与新发布可用性。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/media/{id}")
  public Object mediaEdit(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return media.edit(id, b);
  }

  /** 删除未引用媒体。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/media/{id}")
  public Object mediaDelete(@PathVariable Long id) {
    media.delete(id);
    return Map.of("ok", true);
  }

  /** 屏幕/分组创建及账号目录新增。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/groups")
  public Object groupCreate(@RequestBody Map<String, Object> b) {
    return signs.saveGroup(null, b);
  }

  /** 修改分组配置并检查版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/groups/{id}")
  public Object groupEdit(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return signs.saveGroup(id, b);
  }

  /** 创建设备，令牌只在此响应显示一次。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/screens")
  public Object screenCreate(@RequestBody Map<String, Object> b) {
    return signs.saveScreen(null, b);
  }

  /** 修改屏幕配置，停用会撤销联网播放器。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/screens/{id}")
  public Object screenEdit(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return signs.saveScreen(id, b);
  }

  /** 明确轮换设备令牌。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/screens/{id}/rotate")
  public Object rotate(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return signs.rotate(id, b);
  }

  /** 删除没有使用历史的设备或空分组。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/{kind:groups|screens}/{id}")
  public Object deleteDirectory(@PathVariable String kind, @PathVariable Long id) {
    signs.deleteDirectory(kind, id);
    return Map.of("ok", true);
  }

  /** 保存节目草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/playlists")
  public Object playlistCreate(@RequestBody Map<String, Object> b) {
    return signs.savePlaylist(null, b);
  }

  /** 草稿并发版本保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/playlists/{id}")
  public Object playlistEdit(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return signs.savePlaylist(id, b);
  }

  /** 已持久化节目及有序项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/playlists/{id}")
  public Object playlist(@PathVariable Long id) {
    return signs.playlist(id);
  }

  /** 未发布草稿删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/playlists/{id}")
  public Object playlistDelete(@PathVariable Long id) {
    signs.deletePlaylist(id);
    return Map.of("ok", true);
  }

  /** 立即或限时发布，内容和目标冻结。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/playlists/{id}/publish")
  public Object publish(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return signs.publish(id, b);
  }

  /** 不可变发布预览。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/releases/{id}")
  public Object release(@PathVariable String id) {
    return signs.release(id);
  }

  /** 屏幕回滚基线，排期仍按已配置时段生效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/screens/{id}/rollback")
  public Object rollback(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return signs.rollback(id, b);
  }

  /** 撤销尚未结束的排期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/schedules/{id}/cancel")
  public Object cancel(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return signs.cancelSchedule(id, b);
  }

  /** 屏幕真实运行统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  public Object reports() {
    return signs.reports();
  }

  /** 受组织范围限制的审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return signs.audit();
  }

  /** 运行报表CSV，不附品牌广告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports.csv")
  public ResponseEntity<byte[]> csv() {
    StringBuilder out = new StringBuilder("screen,online,state,target,applied,error\r\n");
    for (var raw : (List<?>) signs.reports()) {
      var r = (Map<?, ?>) raw;
      out.append(
              String.join(
                  ",",
                  List.of("name", "online", "state", "targetRelease", "appliedRelease", "error")
                      .stream()
                      .map(k -> Rules.csv(r.get(k)))
                      .toList()))
          .append("\r\n");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header("Content-Disposition", "attachment; filename=screen-status.csv")
        .body(out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  /** 系统目录需要完整管理范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{kind}")
  @Transactional(readOnly = true)
  public Object adminRead(@PathVariable String kind) {
    return admin.read(kind);
  }

  /** 账号岗位与组织选项。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin-options")
  @Transactional(readOnly = true)
  public Object options() {
    return admin.options();
  }

  /** 新建管理目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{kind}")
  public Object adminCreate(@PathVariable String kind, @RequestBody Map<String, Object> b) {
    return admin.save(kind, null, b);
  }

  /** 更新管理目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{kind}/{id}")
  public Object adminEdit(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return admin.save(kind, id, b);
  }

  /** 独立设备获取节目，不接受网页登录身份代替令牌。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/player/manifest")
  public Object manifest(@RequestHeader(value = "Authorization", required = false) String token) {
    return devices.manifest(token);
  }

  /** 按该屏幕发布历史验证媒体读取。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/player/media/{id}")
  public ResponseEntity<Resource> deviceMedia(
      @PathVariable Long id,
      @RequestHeader(value = "Authorization", required = false) String token) {
    return file(devices.media(token, id));
  }

  /** 实际状态回报，不修改目标内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/player/heartbeat")
  public Object heartbeat(
      @RequestHeader(value = "Authorization", required = false) String token,
      @RequestBody Map<String, Object> b) {
    return devices.heartbeat(token, b);
  }
}
