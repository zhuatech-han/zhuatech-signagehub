// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 独立并发事务检查重复发布与旧草稿覆盖，分别在H2和MySQL运行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyTest {
  @Autowired Store db;
  @Autowired SignageService signs;
  @Autowired PlatformTransactionManager manager;

  <T> T tx(java.util.function.Supplier<T> fn) {
    return new TransactionTemplate(manager)
        .execute(
            s -> {
              var a = db.query(Account.class, "from Account where username=?1", "admin").getFirst();
              var auth =
                  UsernamePasswordAuthenticationToken.authenticated(a.username, null, List.of());
              auth.setDetails(a.passwordHash);
              SecurityContextHolder.getContext().setAuthentication(auth);
              try {
                return fn.get();
              } finally {
                SecurityContextHolder.clearContext();
              }
            });
  }

  Map<String, Object> draft() {
    return new LinkedHashMap<>(
        Map.of(
            "departmentId",
            1,
            "name",
            "QA concurrent " + UUID.randomUUID(),
            "nameEn",
            "QA concurrent",
            "orientation",
            "LANDSCAPE",
            "fit",
            "contain",
            "background",
            "#000000",
            "items",
            List.of(Map.of("text", "QA concurrent text", "seconds", 10, "color", "#ffffff"))));
  }

  @Test
  void concurrentSameRequestPublishesExactlyOnce() throws Exception {
    Long screen =
        tx(
            () ->
                ((Screen)
                        signs
                            .saveScreen(
                                null,
                                Map.of(
                                    "departmentId",
                                    1,
                                    "name",
                                    "QA concurrent screen",
                                    "enabled",
                                    true))
                            .get("screen"))
                    .id);
    Long program = tx(() -> signs.savePlaylist(null, draft()).id);
    String request = UUID.randomUUID().toString();
    var body =
        Map.<String, Object>of(
            "requestId",
            request,
            "playlistVersion",
            1,
            "screenIds",
            List.of(screen),
            "groupIds",
            List.of());
    try {
      try (var pool = Executors.newFixedThreadPool(8)) {
        var tasks = new ArrayList<Future<Object>>();
        for (int i = 0; i < 16; i++)
          tasks.add(pool.submit(() -> tx(() -> signs.publish(program, body))));
        for (var f : tasks)
          assertEquals(request, ((Map<?, ?>) f.get(60, TimeUnit.SECONDS)).get("id"));
      }
      assertEquals(
          1, tx(() -> db.query(Delivery.class, "from Delivery where screenId=?1", screen).size()));
    } finally {
      tx(
          () -> {
            var s = db.get(Screen.class, screen);
            s.baselineRelease = null;
            for (var d : db.query(Delivery.class, "from Delivery where screenId=?1", screen))
              db.delete(d);
            db.flush();
            for (var r :
                db.query(PublishedRelease.class, "from PublishedRelease where id=?1", request))
              db.delete(r);
            db.delete(s);
            for (var i :
                db.query(PlaylistItem.class, "from PlaylistItem where playlistId=?1", program))
              db.delete(i);
            db.delete(db.get(Playlist.class, program));
            return true;
          });
    }
  }

  @Test
  void concurrentStaleDraftHasOneWinner() throws Exception {
    Long id = tx(() -> signs.savePlaylist(null, draft()).id);
    var body = draft();
    body.put("version", 1);
    int success = 0, conflicts = 0;
    try {
      try (var pool = Executors.newFixedThreadPool(8)) {
        var tasks = new ArrayList<Future<String>>();
        for (int i = 0; i < 12; i++)
          tasks.add(
              pool.submit(
                  () -> {
                    try {
                      tx(() -> signs.savePlaylist(id, body));
                      return "OK";
                    } catch (Problem e) {
                      return e.getMessage();
                    }
                  }));
        for (var f : tasks) {
          String result = f.get(60, TimeUnit.SECONDS);
          if (result.equals("OK")) success++;
          else {
            assertEquals("VERSION_CONFLICT", result);
            conflicts++;
          }
        }
      }
      assertEquals(1, success);
      assertEquals(11, conflicts);
    } finally {
      tx(
          () -> {
            for (var i : db.query(PlaylistItem.class, "from PlaylistItem where playlistId=?1", id))
              db.delete(i);
            db.delete(db.get(Playlist.class, id));
            return true;
          });
    }
  }
}
