// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 有组织范围的节目、屏幕与不可变发布事务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class SignageService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final Clock clock;
  final JsonMapper json = new JsonMapper();

  public SignageService(Store db, AccessService access, AdminService admin, Clock clock) {
    this.db = db;
    this.access = access;
    this.admin = admin;
    this.clock = clock;
  }

  private void scope(Long dept, String permission) {
    access.library(dept, permission);
    var department = db.lock(Department.class, dept);
    db.refresh(department);
    access.library(dept, permission);
    Rules.check(department.enabled, "DEPARTMENT_DISABLED");
  }

  private void version(long current, Map<String, Object> b) {
    Rules.check(current == Rules.id(b.get("version")), "VERSION_CONFLICT");
  }

  private String color(Object x) {
    String s = Rules.text(x, 7, true);
    Rules.check(s.matches("#[0-9a-fA-F]{6}"), "INVALID_COLOR");
    return s.toLowerCase(Locale.ROOT);
  }

  private Predicate<Long> visibility() {
    boolean all = access.role().scope.equals("ALL");
    Long department = access.current().departmentId;
    return id -> all || Objects.equals(department, id);
  }

  /** 返回当前岗位需要且在组织范围内的目录；不包含设备密钥。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> catalog() {
    var isVisible = visibility();
    Rules.check(
        access.has("media")
            || access.has("programs")
            || access.has("publish")
            || access.has("screens")
            || access.has("reports"),
        "FORBIDDEN");
    var out = new LinkedHashMap<String, Object>();
    out.put(
        "departments",
        db.all(Department.class).stream().filter(x -> isVisible.test(x.id)).toList());
    out.put("categories", db.all(DictionaryEntry.class));
    out.put(
        "media",
        access.has("media")
            ? db.all(MediaAsset.class).stream().filter(x -> isVisible.test(x.departmentId)).toList()
            : List.of());
    out.put(
        "groups",
        db.all(ScreenGroup.class).stream().filter(x -> isVisible.test(x.departmentId)).toList());
    out.put(
        "screens",
        db.all(Screen.class).stream().filter(x -> isVisible.test(x.departmentId)).toList());
    out.put(
        "playlists",
        (access.has("programs") || access.has("publish"))
            ? db.all(Playlist.class).stream().filter(x -> isVisible.test(x.departmentId)).toList()
            : List.of());
    out.put(
        "releases",
        access.has("publish")
            ? db.all(PublishedRelease.class).stream()
                .filter(x -> isVisible.test(x.departmentId))
                .map(this::releaseSummary)
                .toList()
            : List.of());
    out.put(
        "schedules",
        access.has("publish")
            ? db.all(Schedule.class).stream().filter(x -> isVisible.test(x.departmentId)).toList()
            : List.of());
    out.put("serverTime", clock.instant());
    return out;
  }

  private Map<String, Object> releaseSummary(PublishedRelease r) {
    return Map.of(
        "id",
        r.id,
        "playlistId",
        r.playlistId,
        "departmentId",
        r.departmentId,
        "name",
        r.name,
        "createdAt",
        r.createdAt,
        "actor",
        r.actor,
        "screenIds",
        db.query(Delivery.class, "from Delivery where releaseId=?1", r.id).stream()
            .map(d -> d.screenId)
            .toList());
  }

  /** 屏幕与分组同组织约束，身份保持稳定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveGroup(Long id, Map<String, Object> b) {
    Long dept = Rules.id(b.get("departmentId"));
    scope(dept, "screens");
    var x = id == null ? new ScreenGroup() : db.get(ScreenGroup.class, id);
    if (id != null) {
      Rules.check(x.departmentId.equals(dept), "IDENTITY_LOCKED");
      version(x.version, b);
      x.version++;
    }
    x.departmentId = dept;
    x.name = Rules.text(b.get("name"), 120, true);
    x.enabled = Rules.flag(b.get("enabled"));
    if (id == null) db.save(x);
    db.flush();
    access.audit("GROUP_SAVE", x.id, dept);
    return x;
  }

  /** 创建屏幕只返回一次设备令牌，数据库只保留摘要。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> saveScreen(Long id, Map<String, Object> b) {
    Long dept = Rules.id(b.get("departmentId"));
    scope(dept, "screens");
    var s = id == null ? new Screen() : db.get(Screen.class, id);
    if (id != null) {
      Rules.check(s.departmentId.equals(dept), "IDENTITY_LOCKED");
      version(s.version, b);
      s.version++;
    } else
      Rules.check(
          db.query(Screen.class, "from Screen where departmentId=?1", dept).size()
              < admin.setting("screenLimit"),
          "SCREEN_LIMIT");
    s.departmentId = dept;
    s.name = Rules.text(b.get("name"), 120, true);
    s.enabled = Rules.flag(b.get("enabled"));
    s.groupId = b.get("groupId") == null ? null : Rules.id(b.get("groupId"));
    if (s.groupId != null) {
      var g = db.get(ScreenGroup.class, s.groupId);
      Rules.check(g.enabled && g.departmentId.equals(dept), "GROUP_INVALID");
    }
    String token = id == null ? newToken(s) : null;
    if (id == null) db.save(s);
    db.flush();
    access.audit("SCREEN_SAVE", s.id, dept);
    var out = new LinkedHashMap<String, Object>();
    out.put("screen", s);
    if (token != null) out.put("token", token);
    return out;
  }

  private String newToken(Screen s) {
    byte[] raw = new byte[32];
    new SecureRandom().nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    s.tokenHash = MediaService.hash(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return token;
  }

  /** 显式轮换令牌立即撤销旧联网客户端，不改已冻结内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> rotate(Long id, Map<String, Object> b) {
    var s = db.get(Screen.class, id);
    scope(s.departmentId, "screens");
    db.refresh(s);
    version(s.version, b);
    String token = newToken(s);
    s.version++;
    access.audit("DEVICE_TOKEN_ROTATE", id, s.departmentId);
    return Map.of("screen", s, "token", token);
  }

  /** 未配对、未投放过的屏幕或空分组可删除；业务外键保留历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteDirectory(String kind, Long id) {
    if (kind.equals("groups")) {
      var g = db.get(ScreenGroup.class, id);
      scope(g.departmentId, "screens");
      Rules.check(
          db.query(Screen.class, "from Screen where groupId=?1", id).isEmpty(), "GROUP_IN_USE");
      db.delete(g);
      access.audit("GROUP_DELETE", id, g.departmentId);
    } else if (kind.equals("screens")) {
      var s = db.get(Screen.class, id);
      scope(s.departmentId, "screens");
      Rules.check(
          s.lastSeen == null
              && db.query(Delivery.class, "from Delivery where screenId=?1", id).isEmpty(),
          "SCREEN_HAS_HISTORY");
      db.delete(s);
      access.audit("SCREEN_DELETE", id, s.departmentId);
    } else throw new Problem(404, "NOT_FOUND");
  }

  /** 完整保存有序草稿并验证每项素材归属，防止并发覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Playlist savePlaylist(Long id, Map<String, Object> b) {
    Long dept = Rules.id(b.get("departmentId"));
    scope(dept, "programs");
    var p = id == null ? new Playlist() : db.get(Playlist.class, id);
    if (id != null) {
      Rules.check(p.departmentId.equals(dept), "IDENTITY_LOCKED");
      version(p.version, b);
      p.version++;
    }
    p.categoryId =
        b.get("categoryId") == null
            ? db.all(DictionaryEntry.class).stream()
                .filter(c -> c.enabled)
                .findFirst()
                .orElseThrow(() -> new Problem(409, "CATEGORY_DISABLED"))
                .id
            : Rules.id(b.get("categoryId"));
    Rules.check(db.get(DictionaryEntry.class, p.categoryId).enabled, "CATEGORY_DISABLED");
    p.departmentId = dept;
    p.name = Rules.text(b.get("name"), 160, true);
    p.nameEn = Rules.text(b.get("nameEn"), 160, true);
    p.orientation = Rules.text(b.get("orientation"), 20, true);
    p.fit = Rules.text(b.get("fit"), 20, true);
    p.background = color(b.get("background"));
    Rules.check(
        Set.of("LANDSCAPE", "PORTRAIT").contains(p.orientation)
            && Set.of("contain", "cover").contains(p.fit),
        "INVALID_INPUT");
    if (!(b.get("items") instanceof List<?> list) || list.size() > 100)
      throw new Problem(400, "ITEM_LIMIT");
    if (id == null) {
      db.save(p);
      db.flush();
    } else {
      for (var old : items(id)) db.delete(old);
      db.flush();
    }
    int pos = 0;
    for (var raw : list) {
      if (!(raw instanceof Map<?, ?> x)) throw new Problem(400, "INVALID_INPUT");
      var it = new PlaylistItem();
      it.playlistId = p.id;
      it.position = pos++;
      it.seconds = Rules.integer(x.get("seconds"), 2, 600);
      it.color = color(x.get("color"));
      if (x.get("assetId") != null) {
        var a = db.get(MediaAsset.class, Rules.id(x.get("assetId")));
        Rules.check(a.enabled && a.departmentId.equals(dept), "MEDIA_INVALID");
        it.assetId = a.id;
        it.text = null;
      } else {
        it.text = Rules.paragraph(x.get("text"), 4000, true);
      }
      db.save(it);
    }
    db.flush();
    access.audit("PROGRAM_SAVE", p.id, dept);
    return p;
  }

  private List<PlaylistItem> items(Long id) {
    return db.query(
        PlaylistItem.class, "from PlaylistItem where playlistId=?1 order by position", id);
  }

  /** 查看真实节目预览；按组织和岗位检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> playlist(Long id) {
    var p = db.get(Playlist.class, id);
    access.library(p.departmentId, access.has("programs") ? "programs" : "publish");
    return Map.of("playlist", p, "items", items(id));
  }

  /** 从未发布过的草稿可删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deletePlaylist(Long id) {
    var p = db.get(Playlist.class, id);
    scope(p.departmentId, "programs");
    Rules.check(
        db.query(PublishedRelease.class, "from PublishedRelease where playlistId=?1", id).isEmpty(),
        "PROGRAM_HAS_HISTORY");
    for (var i : items(id)) db.delete(i);
    db.delete(p);
    access.audit("PROGRAM_DELETE", id, p.departmentId);
  }

  /** 发布请求绑定版本、目标和时段；相同请求只创建一个发布快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> publish(Long id, Map<String, Object> b) {
    var p = db.get(Playlist.class, id);
    scope(p.departmentId, "publish");
    db.refresh(p);
    String request = Rules.text(b.get("requestId"), 36, true);
    UUID.fromString(request);
    Rules.check(UUID.fromString(request).toString().equals(request), "INVALID_INPUT");
    String fingerprint = MediaService.hash(json.writeValueAsBytes(new TreeMap<>(b)));
    var prior = db.query(PublishedRelease.class, "from PublishedRelease where id=?1", request);
    if (!prior.isEmpty()) {
      var r = prior.getFirst();
      Rules.check(
          r.playlistId.equals(id)
              && r.actor.equals(access.current().username)
              && r.requestHash.equals(fingerprint),
          "REQUEST_REUSED");
      return releaseSummary(r);
    }
    Rules.check(db.get(DictionaryEntry.class, p.categoryId).enabled, "CATEGORY_DISABLED");
    Rules.check(p.version == Rules.id(b.get("playlistVersion")), "VERSION_CONFLICT");
    var draft = items(id);
    Rules.check(!draft.isEmpty(), "PROGRAM_EMPTY");
    Set<Long> targets = targetIds(b, p.departmentId);
    Instant start = null, end = null;
    if (b.get("startsAt") != null) {
      start =
          Instant.parse(Rules.text(b.get("startsAt"), 40, true))
              .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
      end =
          Instant.parse(Rules.text(b.get("endsAt"), 40, true))
              .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
      Rules.check(
          start.isAfter(clock.instant())
              && end.isAfter(start)
              && end.isBefore(clock.instant().plusSeconds(90L * 86400))
              && Duration.between(start, end).toDays() < 7,
          "SCHEDULE_INVALID");
      for (Long target : targets) {
        Instant st = start, en = end;
        Rules.check(
            db.query(
                    Schedule.class,
                    "from Schedule where screenId=?1 and cancelled=false and startsAt<?2 and endsAt>?3",
                    target,
                    en,
                    st)
                .isEmpty(),
            "SCHEDULE_OVERLAP");
      }
    } else Rules.check(b.get("endsAt") == null, "SCHEDULE_INVALID");
    var snapshot = new LinkedHashMap<String, Object>();
    snapshot.put("id", request);
    snapshot.put("name", p.name);
    snapshot.put("nameEn", p.nameEn);
    snapshot.put("orientation", p.orientation);
    snapshot.put("fit", p.fit);
    snapshot.put("background", p.background);
    var rows = new ArrayList<Map<String, Object>>();
    Set<Long> assets = new HashSet<>();
    long total = 0;
    for (var item : draft) {
      var row = new LinkedHashMap<String, Object>();
      row.put("seconds", item.seconds);
      row.put("color", item.color);
      if (item.assetId != null) {
        var a = db.get(MediaAsset.class, item.assetId);
        Rules.check(a.enabled && a.departmentId.equals(p.departmentId), "MEDIA_INVALID");
        if (assets.add(a.id)) total += a.bytes;
        row.put("assetId", a.id);
        row.put("sha256", a.sha256);
        row.put("mime", a.mime);
        row.put("bytes", a.bytes);
        row.put("kind", a.kind);
        row.put("width", a.width);
        row.put("height", a.height);
        row.put("duration", a.duration);
        row.put("url", "/api/player/media/" + a.id);
      } else row.put("text", item.text);
      rows.add(row);
    }
    Rules.check(total <= 256L * 1024 * 1024, "PROGRAM_TOO_LARGE");
    snapshot.put("items", rows);
    var r = new PublishedRelease();
    r.id = request;
    r.departmentId = p.departmentId;
    r.playlistId = p.id;
    r.name = p.name;
    r.snapshot = json.writeValueAsString(snapshot);
    r.createdAt = clock.instant();
    r.actor = access.current().username;
    r.requestHash = fingerprint;
    db.save(r);
    db.flush();
    for (Long aid : assets) {
      var ref = new ReleaseAsset();
      ref.releaseId = r.id;
      ref.assetId = aid;
      db.save(ref);
    }
    for (Long target : targets) {
      var s = db.get(Screen.class, target);
      var link = new Delivery();
      link.releaseId = r.id;
      link.screenId = target;
      db.save(link);
      s.version++;
      if (start == null) s.baselineRelease = r.id;
      else {
        var sc = new Schedule();
        sc.departmentId = p.departmentId;
        sc.screenId = target;
        sc.releaseId = r.id;
        sc.startsAt = start;
        sc.endsAt = end;
        db.save(sc);
      }
    }
    db.flush();
    for (Long target : targets) enforceQuota(db.get(Screen.class, target));
    access.audit(start == null ? "PROGRAM_PUBLISH" : "PROGRAM_SCHEDULE", r.id, p.departmentId);
    return releaseSummary(r);
  }

  private void enforceQuota(Screen s) {
    Set<String> releases = new HashSet<>();
    if (s.baselineRelease != null) releases.add(s.baselineRelease);
    for (var sc :
        db.query(
            Schedule.class,
            "from Schedule where screenId=?1 and cancelled=false and endsAt>?2",
            s.id,
            clock.instant())) releases.add(sc.releaseId);
    Set<Long> ids = new HashSet<>();
    long total = 0;
    for (String release : releases)
      for (var asset :
          db.query(ReleaseAsset.class, "from ReleaseAsset where releaseId=?1", release))
        if (ids.add(asset.assetId)) total += db.get(MediaAsset.class, asset.assetId).bytes;
    Rules.check(total <= 256L * 1024 * 1024, "PROGRAM_TOO_LARGE");
  }

  private Set<Long> targetIds(Map<String, Object> b, Long dept) {
    Set<Long> ids = new TreeSet<>();
    for (String kind : List.of("screenIds", "groupIds")) {
      if (!(b.get(kind) instanceof List<?> list) || list.size() > 500)
        throw new Problem(400, "TARGET_INVALID");
      for (var raw : list) {
        Long n = Rules.id(raw);
        if (kind.equals("screenIds")) ids.add(n);
        else {
          var g = db.get(ScreenGroup.class, n);
          Rules.check(g.enabled && g.departmentId.equals(dept), "GROUP_INVALID");
          for (var s : db.query(Screen.class, "from Screen where groupId=?1 and enabled=true", n))
            ids.add(s.id);
        }
      }
    }
    Rules.check(!ids.isEmpty() && ids.size() <= 500, "TARGET_INVALID");
    for (var id : ids) {
      var s = db.get(Screen.class, id);
      Rules.check(s.enabled && s.departmentId.equals(dept), "TARGET_INVALID");
    }
    return ids;
  }

  /** 撤销排期，结束后/下次同步回到屏幕基线。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object cancelSchedule(Long id, Map<String, Object> b) {
    var x = db.get(Schedule.class, id);
    scope(x.departmentId, "publish");
    db.refresh(x);
    version(x.version, b);
    Rules.check(!x.cancelled && x.endsAt.isAfter(clock.instant()), "SCHEDULE_FINISHED");
    x.cancelled = true;
    x.version++;
    db.get(Screen.class, x.screenId).version++;
    access.audit("SCHEDULE_CANCEL", id, x.departmentId);
    return x;
  }

  /** 回滚只允许该屏幕已有的发布历史，保留新旧快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object rollback(Long id, Map<String, Object> b) {
    var s = db.get(Screen.class, id);
    scope(s.departmentId, "publish");
    db.refresh(s);
    version(s.version, b);
    String r = Rules.text(b.get("releaseId"), 36, true);
    Rules.check(
        s.enabled
            && !db.query(Delivery.class, "from Delivery where screenId=?1 and releaseId=?2", id, r)
                .isEmpty(),
        "RELEASE_NOT_ASSIGNED");
    s.baselineRelease = r;
    s.version++;
    db.flush();
    access.audit("PROGRAM_ROLLBACK", id, s.departmentId);
    return s;
  }

  /** 可追溯的发布快照预览，媒体下载仍要求认证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object release(String id) {
    var r = db.get(PublishedRelease.class, id);
    access.library(r.departmentId, "publish");
    return json.readValue(r.snapshot, Map.class);
  }

  /** 根据服务器时间计算在线状态，区分实际播放和目标节目。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object reports() {
    access.require("reports");
    var isVisible = visibility();
    var screens =
        db.all(Screen.class).stream().filter(s -> isVisible.test(s.departmentId)).toList();
    var rows = new ArrayList<Map<String, Object>>();
    for (var s : screens) {
      var row = new LinkedHashMap<String, Object>();
      row.put("id", s.id);
      row.put("name", s.name);
      row.put("departmentId", s.departmentId);
      row.put("enabled", s.enabled);
      row.put(
          "online", s.lastSeen != null && s.lastSeen.isAfter(clock.instant().minusSeconds(120)));
      row.put("lastSeen", s.lastSeen);
      row.put("appliedRelease", s.appliedRelease);
      row.put("state", s.playerState);
      row.put("error", s.playerError);
      var schedules =
          db.query(
              Schedule.class,
              "from Schedule where screenId=?1 and cancelled=false and startsAt<=?2 and endsAt>?2",
              s.id,
              clock.instant());
      row.put(
          "targetRelease",
          schedules.isEmpty() ? s.baselineRelease : schedules.getFirst().releaseId);
      rows.add(row);
    }
    return rows;
  }

  /** 查询本组织审计，禁止修改或删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    var isVisible = visibility();
    return db.all(AuditEvent.class).stream()
        .filter(x -> isVisible.test(x.departmentId))
        .sorted(Comparator.comparing((AuditEvent x) -> x.id).reversed())
        .toList();
  }
}
