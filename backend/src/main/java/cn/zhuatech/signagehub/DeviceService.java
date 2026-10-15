// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 独立设备认证、离线租期及实际播放回报。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class DeviceService {
  final Store db;
  final AdminService admin;
  final Clock clock;
  final JsonMapper json = new JsonMapper();

  public DeviceService(Store db, AdminService admin, Clock clock) {
    this.db = db;
    this.admin = admin;
    this.clock = clock;
  }

  /** 令牌仅从Authorization读取，不能放在URL或公开目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Screen authenticate(String header) {
    if (header == null || !header.matches("Bearer [A-Za-z0-9_-]{43}"))
      throw new Problem(401, "DEVICE_UNAUTHENTICATED");
    String hash =
        MediaService.hash(header.substring(7).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var rows = db.query(Screen.class, "from Screen where tokenHash=?1", hash);
    if (rows.isEmpty()) throw new Problem(401, "DEVICE_UNAUTHENTICATED");
    var s = rows.getFirst();
    if (!s.enabled || !db.get(Department.class, s.departmentId).enabled)
      throw new Problem(403, "DEVICE_DISABLED");
    return s;
  }

  /** 发送常驻节目和未来七天排期；租期与节目内容分别更新。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> manifest(String token) {
    var s = authenticate(token);
    var now = clock.instant();
    var programs = new ArrayList<Map<String, Object>>();
    if (s.baselineRelease != null) programs.add(program(s.baselineRelease, null, null));
    var schedules =
        db.query(
            Schedule.class,
            "from Schedule where screenId=?1 and cancelled=false and endsAt>?2 and startsAt<?3 order by startsAt",
            s.id,
            now,
            now.plusSeconds(7L * 86400));
    Rules.check(schedules.size() <= 100, "SCHEDULE_LIMIT");
    for (var x : schedules) programs.add(program(x.releaseId, x.startsAt, x.endsAt));
    var body = new LinkedHashMap<String, Object>();
    body.put("schema", 1);
    body.put("screenId", s.id);
    body.put("screenName", s.name);
    body.put(
        "revision",
        MediaService.hash(
            json.writeValueAsBytes(Map.of("version", s.version, "programs", programs))));
    body.put("serverTime", now.toString());
    body.put("validUntil", now.plusSeconds(admin.setting("offlineHours") * 3600L).toString());
    body.put("programs", programs);
    return body;
  }

  private Map<String, Object> program(String id, Instant start, Instant end) {
    var r = db.get(PublishedRelease.class, id);
    var out = new LinkedHashMap<String, Object>();
    out.put("release", json.readValue(r.snapshot, Map.class));
    if (start != null) {
      out.put("startsAt", start.toString());
      out.put("endsAt", end.toString());
    }
    return out;
  }

  /** 设备仅可下载已经明确投放给它的不可变媒体。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public MediaAsset media(String token, Long id) {
    var s = authenticate(token);
    var a = db.get(MediaAsset.class, id);
    Rules.check(a.departmentId.equals(s.departmentId), "MEDIA_FORBIDDEN");
    boolean allowed =
        !db.query(
                ReleaseAsset.class,
                "select ra from ReleaseAsset ra, Delivery d where ra.assetId=?1 and ra.releaseId=d.releaseId and d.screenId=?2",
                id,
                s.id)
            .isEmpty();
    if (!allowed) throw new Problem(403, "MEDIA_FORBIDDEN");
    return a;
  }

  /** 区分已缓存、已播放与下载错误；心跳不能自行授予节目。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object heartbeat(String token, Map<String, Object> b) {
    var initial = authenticate(token);
    var s = db.lock(Screen.class, initial.id);
    db.refresh(s);
    Rules.check(
        s.tokenHash.equals(
            MediaService.hash(
                token.substring(7).getBytes(java.nio.charset.StandardCharsets.UTF_8))),
        "DEVICE_UNAUTHENTICATED");
    Rules.check(s.enabled, "DEVICE_DISABLED");
    String state = Rules.text(b.get("state"), 30, true);
    Rules.check(
        Set.of("PLAYING", "IDLE", "DOWNLOADING", "ERROR", "EXPIRED").contains(state),
        "INVALID_INPUT");
    String id = Rules.text(b.get("appliedRelease"), 36, false);
    if (!id.isEmpty())
      Rules.check(
          !db.query(Delivery.class, "from Delivery where screenId=?1 and releaseId=?2", s.id, id)
              .isEmpty(),
          "RELEASE_NOT_ASSIGNED");
    String error = Rules.text(b.get("error"), 120, false);
    Rules.check(error.matches("[A-Z0-9_: .-]{0,120}"), "INVALID_INPUT");
    s.lastSeen = clock.instant();
    s.appliedRelease = id.isEmpty() ? null : id;
    s.playerState = state;
    s.playerError = error.isEmpty() ? null : error;
    return Map.of("ok", true, "serverTime", clock.instant());
  }
}
