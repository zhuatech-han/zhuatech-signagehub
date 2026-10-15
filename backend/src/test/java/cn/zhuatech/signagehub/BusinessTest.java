// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 发布、排期、隔离与身份的持久化回归；不使用页面假数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BusinessTest {
  @Autowired Store db;
  @Autowired SignageService signs;
  @Autowired DeviceService devices;
  @Autowired AdminService admin;
  @Autowired MediaService media;
  String token;
  Screen screen;
  Playlist playlist;

  @BeforeEach
  void setup() {
    authenticate("admin");
    var result =
        signs.saveScreen(null, Map.of("departmentId", 1, "name", "QA screen", "enabled", true));
    token = (String) result.get("token");
    screen = (Screen) result.get("screen");
    playlist = signs.savePlaylist(null, body(null, "Original"));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void authenticate(String username) {
    var a = db.query(Account.class, "from Account where username=?1", username).getFirst();
    var auth = UsernamePasswordAuthenticationToken.authenticated(username, null, List.of());
    auth.setDetails(a.passwordHash);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  Map<String, Object> body(Playlist old, String text) {
    var b = new LinkedHashMap<String, Object>();
    b.put("departmentId", 1);
    b.put("name", "QA program");
    b.put("nameEn", "QA program");
    b.put("orientation", "LANDSCAPE");
    b.put("fit", "contain");
    b.put("background", "#000000");
    b.put("items", List.of(Map.of("text", text, "seconds", 10, "color", "#ffffff")));
    if (old != null) b.put("version", old.version);
    return b;
  }

  Map<String, Object> request() {
    var b = new LinkedHashMap<String, Object>();
    b.put("requestId", UUID.randomUUID().toString());
    b.put("playlistVersion", playlist.version);
    b.put("screenIds", List.of(screen.id));
    b.put("groupIds", List.of());
    return b;
  }

  String publish() {
    return (String) signs.publish(playlist.id, request()).get("id");
  }

  void refusal(String code, org.junit.jupiter.api.function.Executable action) {
    assertEquals(code, assertThrows(Problem.class, action).getMessage());
  }

  @Test
  void publishFreezeAndDraftChangeAreIndependent() {
    String id = publish();
    signs.savePlaylist(playlist.id, body(playlist, "Changed"));
    var view = new JsonMapper().writeValueAsString(signs.release(id));
    assertTrue(view.contains("Original"));
    assertFalse(view.contains("Changed"));
    assertEquals(id, screen.baselineRelease);
  }

  @Test
  void exactRequestReplayCreatesOneRelease() {
    var req = request();
    var first = signs.publish(playlist.id, req);
    assertEquals(first, signs.publish(playlist.id, req));
    assertEquals(1, db.query(Delivery.class, "from Delivery where screenId=?1", screen.id).size());
    req.put("screenIds", List.of(99999));
    refusal("REQUEST_REUSED", () -> signs.publish(playlist.id, req));
  }

  @Test
  void staleDraftVersionCannotOverwrite() {
    var stale = body(playlist, "Stale");
    signs.savePlaylist(playlist.id, body(playlist, "New"));
    refusal("VERSION_CONFLICT", () -> signs.savePlaylist(playlist.id, stale));
  }

  @Test
  void emptyProgramCannotPublish() {
    var b = body(playlist, "Unused");
    b.put("items", List.of());
    signs.savePlaylist(playlist.id, b);
    refusal("PROGRAM_EMPTY", this::publish);
  }

  @Test
  void scheduledIntervalsRejectOverlap() {
    var now = Instant.now();
    var b = request();
    b.put("startsAt", now.plusSeconds(300).toString());
    b.put("endsAt", now.plusSeconds(900).toString());
    signs.publish(playlist.id, b);
    var overlap = request();
    overlap.put("startsAt", now.plusSeconds(600).toString());
    overlap.put("endsAt", now.plusSeconds(1200).toString());
    refusal("SCHEDULE_OVERLAP", () -> signs.publish(playlist.id, overlap));
  }

  @Test
  void adjacentSchedulesAreAllowed() {
    var now = Instant.now();
    for (int n = 0; n < 2; n++) {
      var b = request();
      b.put("startsAt", now.plusSeconds(300 + n * 300).toString());
      b.put("endsAt", now.plusSeconds(600 + n * 300).toString());
      signs.publish(playlist.id, b);
    }
    assertEquals(2, db.query(Schedule.class, "from Schedule where screenId=?1", screen.id).size());
  }

  @Test
  void scheduleCancellationLeavesBaseline() {
    String id = publish();
    var b = request();
    b.put("startsAt", Instant.now().plusSeconds(300).toString());
    b.put("endsAt", Instant.now().plusSeconds(600).toString());
    signs.publish(playlist.id, b);
    var sc = db.query(Schedule.class, "from Schedule where screenId=?1", screen.id).getFirst();
    signs.cancelSchedule(sc.id, Map.of("version", sc.version));
    assertEquals(id, screen.baselineRelease);
    assertTrue(sc.cancelled);
  }

  @Test
  void rollbackMustHaveScreenDeliveryHistory() {
    String one = publish();
    String two = publish();
    assertEquals(two, screen.baselineRelease);
    var history = (List<?>) signs.catalog().get("releases");
    assertEquals(2, history.size());
    assertTrue(
        history.stream()
            .allMatch(r -> ((Map<?, ?>) r).get("screenIds").equals(List.of(screen.id))));
    signs.rollback(screen.id, Map.of("version", screen.version, "releaseId", one));
    assertEquals(one, screen.baselineRelease);
    refusal(
        "RELEASE_NOT_ASSIGNED",
        () ->
            signs.rollback(
                screen.id,
                Map.of("version", screen.version, "releaseId", UUID.randomUUID().toString())));
  }

  @Test
  void invalidDeviceHasNoManifest() {
    refusal("DEVICE_UNAUTHENTICATED", () -> devices.manifest(null));
    refusal("DEVICE_UNAUTHENTICATED", () -> devices.manifest("Bearer " + "A".repeat(43)));
  }

  @Test
  void rotationRevokesOldToken() {
    String next = (String) signs.rotate(screen.id, Map.of("version", screen.version)).get("token");
    refusal("DEVICE_UNAUTHENTICATED", () -> devices.manifest("Bearer " + token));
    assertEquals(screen.id, devices.manifest("Bearer " + next).get("screenId"));
  }

  @Test
  void disabledScreenRefusesDevices() {
    signs.saveScreen(
        screen.id,
        Map.of(
            "departmentId", 1, "name", screen.name, "enabled", false, "version", screen.version));
    refusal("DEVICE_DISABLED", () -> devices.manifest("Bearer " + token));
  }

  @Test
  void deviceHeartbeatCannotClaimUnassignedRelease() {
    refusal(
        "RELEASE_NOT_ASSIGNED",
        () ->
            devices.heartbeat(
                "Bearer " + token,
                Map.of("state", "PLAYING", "appliedRelease", UUID.randomUUID().toString())));
  }

  @Test
  void appliedAndTargetStatesAreDifferent() {
    String id = publish();
    devices.heartbeat("Bearer " + token, Map.of("state", "IDLE"));
    var rows = (List<?>) signs.reports();
    var row = (Map<?, ?>) rows.getFirst();
    assertEquals(id, row.get("targetRelease"));
    assertNull(row.get("appliedRelease"));
    devices.heartbeat("Bearer " + token, Map.of("state", "PLAYING", "appliedRelease", id));
    assertEquals(id, screen.appliedRelease);
  }

  @Test
  void publishedDraftAndScreenCannotBeDeleted() {
    publish();
    refusal("PROGRAM_HAS_HISTORY", () -> signs.deletePlaylist(playlist.id));
  }

  @Test
  void departmentScopeCannotReadOrPublishOthers() {
    var d = new Department();
    d.name = "QA other";
    d.zone = "UTC";
    db.save(d);
    var r = new AccessRole();
    r.name = "QA staff";
    r.scope = "DEPARTMENT";
    r.permissions.addAll(Set.of("programs", "publish", "screens"));
    db.save(r);
    var a = new Account();
    a.username = "qa-other";
    a.displayName = "QA";
    a.passwordHash = "not-a-password";
    a.roleId = r.id;
    a.departmentId = d.id;
    db.save(a);
    authenticate(a.username);
    refusal("OUT_OF_SCOPE", () -> signs.playlist(playlist.id));
    refusal("OUT_OF_SCOPE", this::publish);
  }

  @Test
  void lastFullAdminCannotBeDisabled() {
    var a = db.query(Account.class, "from Account where username=?1", "admin").getFirst();
    refusal(
        "LAST_ADMIN",
        () ->
            admin.save(
                "users",
                a.id,
                Map.of(
                    "username",
                    a.username,
                    "displayName",
                    a.displayName,
                    "roleId",
                    a.roleId,
                    "departmentId",
                    a.departmentId,
                    "enabled",
                    false,
                    "version",
                    a.version)));
  }

  @Test
  void offlineLeaseUsesValidatedSystemParameter() {
    admin.save(
        "settings",
        db.query(SystemSetting.class, "from SystemSetting where code=?1", "offlineHours")
            .getFirst()
            .id,
        Map.of("code", "offlineHours", "value", "2"));
    var m = devices.manifest("Bearer " + token);
    assertEquals(
        7200,
        Duration.between(
                Instant.parse((String) m.get("serverTime")),
                Instant.parse((String) m.get("validUntil")))
            .getSeconds());
  }

  @Test
  void plainTextDoesNotBecomeHtml() {
    var b = body(playlist, "<script>alert('x')</script>");
    signs.savePlaylist(playlist.id, b);
    String id = publish();
    assertTrue(new JsonMapper().writeValueAsString(signs.release(id)).contains("<script>"));
  }
}
