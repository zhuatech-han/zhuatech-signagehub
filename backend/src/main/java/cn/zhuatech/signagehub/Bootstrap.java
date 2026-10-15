// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 首次空库初始化身份与管理目录，不插入客户素材或节目。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  public static final Set<String> CODES =
      Set.of(
          "media",
          "programs",
          "publish",
          "screens",
          "reports",
          "audit",
          "users",
          "roles",
          "settings");
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${signagehub.admin-username}") String username,
      @Value("${signagehub.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
  }

  /** 建立强密码管理员、编辑、发布及观察岗位。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    Rules.check(username.matches("[a-z0-9_.-]{3,60}"), "INVALID_INPUT");
    var d = new Department();
    d.name = "总部";
    d.zone = "Asia/Shanghai";
    db.save(d);
    String[][] entries = {
      {"media", "素材库", "Media"},
      {"programs", "节目", "Programs"},
      {"publish", "发布与排期", "Publication"},
      {"screens", "屏幕与分组", "Screens"},
      {"reports", "运行概况", "Overview"},
      {"audit", "操作审计", "Audit"},
      {"users", "登录账号", "Accounts"},
      {"roles", "角色权限", "Roles"},
      {"settings", "组织与设置", "Settings"}
    };
    for (int i = 0; i < entries.length; i++) {
      var p = new Permission();
      p.code = entries[i][0];
      p.name = entries[i][1];
      db.save(p);
      var m = new NavMenu();
      m.code = p.code;
      m.name = p.name;
      m.nameEn = entries[i][2];
      m.permissionCode = p.code;
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    role("管理员", "ALL", CODES);
    role("内容编辑", "DEPARTMENT", Set.of("media", "programs"));
    role(
        "门店发布",
        "DEPARTMENT",
        Set.of("media", "programs", "publish", "screens", "reports", "audit"));
    role("运行观察", "DEPARTMENT", Set.of("reports"));
    var a = new Account();
    a.username = username;
    a.displayName = "管理员";
    a.passwordHash = encoder.encode(password);
    a.departmentId = d.id;
    a.roleId = db.all(AccessRole.class).getFirst().id;
    db.save(a);
    for (var kv : Map.of("offlineHours", "72", "screenLimit", "100").entrySet()) {
      var s = new SystemSetting();
      s.code = kv.getKey();
      s.value = kv.getValue();
      db.save(s);
    }
    for (String[] x :
        new String[][] {
          {"PROMOTION", "促销", "Promotion"}, {"NOTICE", "公告", "Notice"}, {"MENU", "菜单", "Menu"}
        }) {
      var c = new DictionaryEntry();
      c.type = "CATEGORY";
      c.code = x[0];
      c.name = x[1];
      c.nameEn = x[2];
      c.enabled = true;
      db.save(c);
    }
  }

  private void role(String name, String scope, Set<String> codes) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions.addAll(codes);
    db.save(r);
  }
}
