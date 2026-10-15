<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import {
  ref,
  computed,
  onMounted,
  onUnmounted,
  nextTick,
  defineAsyncComponent,
} from "vue";
import { api, resetApi } from "./api.js";
import { language, t, errorMessage, confirmation, ask } from "./ui.js";
import { dialogFocus } from "./dialog-focus.js";
const Board = defineAsyncComponent(() => import("./components/Board.vue"));
const AdminPanel = defineAsyncComponent(
  () => import("./components/AdminPanel.vue"),
);
const me = ref(null),
  ready = ref(false),
  section = ref(location.hash.slice(2) || "reports"),
  pending = ref(false),
  message = ref(""),
  child = ref(null),
  about = ref(false),
  passwordDialog = ref(false),
  identity = ref({ username: "", password: "" }),
  passwords = ref({ oldPassword: "", newPassword: "" });
const menus = computed(() => me.value?.menus || []);
const title = computed(() => {
  const m = menus.value.find((x) => x.code === section.value);
  return m ? (language.value === "zh" ? m.name : m.nameEn) : "SignageHub";
});
let cleanup,
  errorCode = "";
function failure(e) {
  errorCode = e.message;
  message.value = errorMessage(e);
  if (e.status === 401) {
    me.value = null;
    resetApi();
    identity.value.password = "";
  }
}
function recoverReadFailure() {
  if (errorCode === "NETWORK_ERROR") {
    message.value = "";
    errorCode = "";
  }
}
/** Exactly one write is issued; an unknown outcome remains visible for verification. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
async function perform(path, method = "POST", body) {
  if (pending.value) return null;
  pending.value = true;
  message.value = "";
  try {
    return await api(path, { method, body });
  } catch (e) {
    failure(e);
    return null;
  } finally {
    pending.value = false;
  }
}
async function navigate(code, fromHash = false) {
  if (pending.value) return;
  if (child.value?.canLeave && !(await child.value.canLeave())) {
    if (fromHash) history.replaceState(null, "", `#/${section.value}`);
    return;
  }
  section.value = menus.value.some((x) => x.code === code)
    ? code
    : menus.value[0]?.code || "reports";
  message.value = "";
  if (!fromHash) location.hash = "/" + section.value;
  await nextTick();
  window.scrollTo(0, 0);
}
async function login() {
  const res = await perform("/auth/login", "POST", identity.value);
  identity.value.password = "";
  if (res) {
    resetApi();
    me.value = res;
    await navigate(section.value);
  }
}
async function logout() {
  if (!(await ask(t("退出当前账号？", "Sign out?")))) return;
  const r = await perform("/auth/logout");
  if (r) {
    me.value = null;
    resetApi();
  }
}
function switchLanguage() {
  language.value = language.value === "zh" ? "en" : "zh";
  localStorage.setItem("signage-language", language.value);
  document.documentElement.lang = language.value === "zh" ? "zh-CN" : "en";
}
async function passwordSave() {
  if (await perform("/auth/password", "POST", passwords.value)) {
    passwordDialog.value = false;
    passwords.value = { oldPassword: "", newPassword: "" };
    me.value = null;
    resetApi();
  }
}
function doneConfirm(ok) {
  const v = confirmation.value;
  confirmation.value = null;
  v?.resolve(ok);
}
const hashChange = () => navigate(location.hash.slice(2), true);
onMounted(async () => {
  cleanup = dialogFocus();
  window.addEventListener("hashchange", hashChange);
  try {
    me.value = await api("/auth/me");
    await navigate(section.value);
  } catch (e) {
    if (e.status !== 401) failure(e);
  } finally {
    ready.value = true;
  }
});
onUnmounted(() => {
  cleanup?.();
  window.removeEventListener("hashchange", hashChange);
});
</script>
<template>
  <div v-if="!ready" class="loading">{{ t("正在连接…", "Connecting…") }}</div>
  <div v-else-if="!me" class="login-page">
    <header class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" /><strong>SignageHub</strong
      ><button class="link" @click="switchLanguage">
        {{ language === "zh" ? "English" : "中文" }}
      </button>
    </header>
    <main class="login-box">
      <h1>{{ t("登录内容后台", "Sign in to the content workspace") }}</h1>
      <p class="muted">
        {{
          t(
            "管理素材、屏幕与节目发布。",
            "Manage media, screens and program publication.",
          )
        }}
      </p>
      <form autocomplete="off" @submit.prevent="login">
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="identity.username"
            required
            autocomplete="off"
            maxlength="60" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="identity.password"
            type="password"
            required
            autocomplete="off"
            maxlength="72"
        /></label>
        <p v-if="message" class="notice error" role="alert">{{ message }}</p>
        <button class="primary wide" :disabled="pending">
          {{ pending ? t("登录中…", "Signing in…") : t("登录", "Sign in") }}
        </button>
      </form>
      <div class="login-foot">
        <span>{{
          t(
            "公开源码学习版 · 非商业使用",
            "Public source learning edition · Non-commercial",
          )
        }}</span
        ><a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >www.zhuatech.cn</a
        >
      </div>
    </main>
  </div>
  <div v-else class="workspace">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" /><strong>SignageHub</strong>
      </div>
      <nav aria-label="业务菜单">
        <button
          v-for="m in menus"
          :key="m.code"
          :class="{ active: section === m.code }"
          @click="navigate(m.code)"
        >
          {{ language === "zh" ? m.name : m.nameEn }}
        </button>
      </nav>
      <footer>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
          t("知华科技", "ZhiHua Technology")
        }}</a
        ><button class="link" @click="about = true">
          {{ t("关于与授权", "About & licensing") }}
        </button>
      </footer>
    </aside>
    <div class="content">
      <header class="topbar">
        <span>{{ title }}</span>
        <div>
          <button class="link" @click="switchLanguage">
            {{ language === "zh" ? "English" : "中文" }}</button
          ><span>{{ me.displayName }}</span
          ><button class="link" @click="passwordDialog = true">
            {{ t("修改密码", "Password") }}</button
          ><button @click="logout">{{ t("退出", "Sign out") }}</button>
        </div>
      </header>
      <main class="page">
        <p v-if="message" class="notice error" role="alert">{{ message }}</p>
        <AdminPanel
          v-if="['users', 'roles', 'settings'].includes(section)"
          :key="section"
          ref="child"
          :section="section"
          :pending="pending"
          :message="message"
          :perform="perform"
          @error="failure"
          @recovered="recoverReadFailure"
        /><Board
          v-else
          :key="section"
          ref="child"
          :section="section"
          :me="me"
          :pending="pending"
          :perform="perform"
          @error="failure"
          @recovered="recoverReadFailure"
        />
      </main>
    </div>
  </div>
  <div v-if="about" class="overlay" @click.self="about = false">
    <section
      class="dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="t('关于SignageHub', 'About SignageHub')"
    >
      <header>
        <h2>SignageHub 1.0.0</h2>
        <button @click="about = false">{{ t("关闭", "Close") }}</button>
      </header>
      <p>
        {{
          t(
            "知华科技（上海如静知华信息科技有限公司）",
            "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
          )
        }}
      </p>
      <p>
        {{
          t(
            "源码公开、非商业使用。仅限个人学习交流，未经公司书面授权不得商用。",
            "Source available for personal study and non-commercial exchange. Commercial use requires written company authorization.",
          )
        }}
      </p>
      <p><a href="https://www.zhuatech.cn/">www.zhuatech.cn</a></p>
      <p>
        {{
          t(
            "商业授权或深度定制开发请联系知华科技。微信 zhuatech / zhuatech2。",
            "Commercial licensing and customization: han@zhuatech.cn / jack@zhuatech.cn; WhatsApp +86 17521234993.",
          )
        }}
      </p>
    </section>
  </div>
  <div v-if="passwordDialog" class="overlay">
    <section
      class="dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="t('修改密码', 'Change password')"
    >
      <header>
        <h2>{{ t("修改密码", "Change password") }}</h2>
        <button @click="passwordDialog = false">
          {{ t("取消", "Cancel") }}
        </button>
      </header>
      <form autocomplete="off" @submit.prevent="passwordSave">
        <label
          >{{ t("原密码", "Current password")
          }}<input
            v-model="passwords.oldPassword"
            type="password"
            autocomplete="off"
            required /></label
        ><label
          >{{ t("新密码", "New password")
          }}<input
            v-model="passwords.newPassword"
            type="password"
            autocomplete="off"
            required
            minlength="12"
            maxlength="72"
        /></label>
        <p class="muted">
          {{
            t(
              "12–72字节，含大小写字母及数字；保存后重新登录。",
              "12–72 bytes, upper/lowercase and a digit. Sign in again after saving.",
            )
          }}
        </p>
        <button class="primary" :disabled="pending">
          {{ t("保存", "Save") }}
        </button>
      </form>
    </section>
  </div>
  <div v-if="confirmation" class="overlay">
    <section
      class="dialog compact"
      role="dialog"
      aria-modal="true"
      :aria-label="t('确认操作', 'Confirm action')"
    >
      <h2>{{ t("确认操作", "Confirm action") }}</h2>
      <p>{{ confirmation.message }}</p>
      <div class="actions">
        <button @click="doneConfirm(false)">{{ t("取消", "Cancel") }}</button
        ><button class="primary" @click="doneConfirm(true)">
          {{ t("确认", "Confirm") }}
        </button>
      </div>
    </section>
  </div>
</template>
