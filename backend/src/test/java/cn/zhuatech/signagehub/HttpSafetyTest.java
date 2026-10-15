// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 实际会话、CSRF和权限接口回归；生产接口不接受客户端指定权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class HttpSafetyTest {
  @Autowired MockMvc mvc;
  @Autowired Store db;
  @Autowired BCryptPasswordEncoder encoder;
  final JsonMapper json = new JsonMapper();

  MockHttpSession login(String user, String password) throws Exception {
    var session = new MockHttpSession();
    return (MockHttpSession)
        mvc.perform(
                post("/api/auth/login")
                    .session(session)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(Map.of("username", user, "password", password))))
            .andExpect(status().isOk())
            .andReturn()
            .getRequest()
            .getSession();
  }

  MockHttpSession admin() throws Exception {
    return login("admin", "TestOnlyAdmin9Strong");
  }

  void staff() {
    var role = db.query(AccessRole.class, "from AccessRole where name=?1", "内容编辑").getFirst();
    var a = new Account();
    a.username = "qa-editor";
    a.displayName = "QA editor";
    a.passwordHash = encoder.encode("OnlyTestEditor9Password");
    a.roleId = role.id;
    a.departmentId = 1L;
    db.save(a);
    db.flush();
  }

  @Test
  void coreHealthIsAnonymousAndUp() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void freshAdministratorCanNavigateEveryImplementedWorkspace() throws Exception {
    mvc.perform(get("/api/auth/me").session(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.menus.length()").value(9))
        .andExpect(jsonPath("$.menus[0].code").value("media"))
        .andExpect(jsonPath("$.menus[8].code").value("settings"));
  }

  @Test
  void editorNavigationContainsOnlyItsAllowedContentWorkspaces() throws Exception {
    staff();
    mvc.perform(get("/api/auth/me").session(login("qa-editor", "OnlyTestEditor9Password")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.menus.length()").value(2))
        .andExpect(jsonPath("$.menus[0].code").value("media"))
        .andExpect(jsonPath("$.menus[1].code").value("programs"));
  }

  @Test
  void anonymousCannotReadCatalogOrAccounts() throws Exception {
    mvc.perform(get("/api/catalog")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void loginRequiresCsrf() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"TestOnlyAdmin9Strong\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void failedLoginDoesNotCreateStaffSession() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"WrongTestPassword\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedAccountsNeverReturnPasswordHashes() throws Exception {
    var session = admin();
    var raw =
        mvc.perform(get("/api/admin/users").session(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(raw.contains("passwordHash"));
    assertFalse(raw.contains("$2a$"));
  }

  @Test
  void editorCannotPublishOrManageAccounts() throws Exception {
    staff();
    var session = login("qa-editor", "OnlyTestEditor9Password");
    mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/screens")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"departmentId\":1,\"name\":\"QA\",\"enabled\":true}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void writeWithoutCsrfDoesNotSaveAnything() throws Exception {
    var session = admin();
    mvc.perform(
            post("/api/screens")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"departmentId\":1,\"name\":\"QA\",\"enabled\":true}"))
        .andExpect(status().isForbidden());
    assertEquals(0, db.all(Screen.class).size());
  }

  @Test
  void logoutRevokesSession() throws Exception {
    var session = admin();
    mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void ownPasswordChangeRevokesOldSession() throws Exception {
    var session = admin();
    mvc.perform(
            post("/api/auth/password")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"oldPassword\":\"TestOnlyAdmin9Strong\",\"newPassword\":\"ChangedTestAdmin8Strong\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    login("admin", "ChangedTestAdmin8Strong");
  }

  @Test
  void adminCanResetOwnPasswordWithoutAuditRollback() throws Exception {
    var session = admin();
    var a = db.query(Account.class, "from Account where username=?1", "admin").getFirst();
    mvc.perform(
            put("/api/admin/users/" + a.id)
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
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
                            true,
                            "version",
                            a.version,
                            "password",
                            "ResetTestAdmin8Strong"))))
        .andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    login("admin", "ResetTestAdmin8Strong");
  }

  @Test
  void deviceRoutesRequireTokenEvenWithAdminSession() throws Exception {
    mvc.perform(get("/api/player/manifest").session(admin())).andExpect(status().isUnauthorized());
  }
}
