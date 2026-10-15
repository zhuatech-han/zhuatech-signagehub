<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import { api, download } from "../api.js";
import { t, localized, dateTime, cloneRecord, requestId, ask } from "../ui.js";
const props = defineProps({
  section: { type: String, required: true },
  me: { type: Object, required: true },
  pending: Boolean,
  perform: { type: Function, required: true },
});
const emit = defineEmits(["error", "recovered"]);
const catalog = ref({
    departments: [],
    media: [],
    groups: [],
    screens: [],
    playlists: [],
    categories: [],
    releases: [],
    schedules: [],
  }),
  ready = ref(false),
  available = ref(false),
  q = ref(""),
  filter = ref("all"),
  page = ref(1),
  editor = ref(null),
  initial = ref(""),
  token = ref(""),
  showToken = ref(false),
  preview = ref(null),
  rows = ref([]),
  notice = ref(""),
  file = ref(null),
  publishForm = ref(null);
const labels = {
  media: () => t("素材库", "Media library"),
  programs: () => t("节目", "Programs"),
  publish: () => t("发布与排期", "Publication & schedule"),
  screens: () => t("屏幕与分组", "Screens & groups"),
  reports: () => t("运行概况", "Operations overview"),
  audit: () => t("操作审计", "Audit history"),
};
const title = computed(() => labels[props.section]?.() || "SignageHub");
// 发布编号是 UUID；历史记录按实际创建时间排列，不能依赖数据库返回顺序。
const releaseHistory = computed(() =>
  [...catalog.value.releases].sort(
    (a, b) =>
      Date.parse(b.createdAt) - Date.parse(a.createdAt) ||
      b.id.localeCompare(a.id),
  ),
);
const descriptions = {
  media: () =>
    t("图片、短视频与素材使用状态。", "Images, short videos and availability."),
  programs: () =>
    t(
      "编辑节目草稿，发布版本独立保存。",
      "Edit drafts; published versions remain unchanged.",
    ),
  publish: () =>
    t(
      "选择屏幕立即发布，或配置限时播放。",
      "Publish to screens now or schedule a limited interval.",
    ),
  screens: () =>
    t(
      "管理屏幕、分组与设备连接。",
      "Manage screens, groups and device connections.",
    ),
  reports: () =>
    t(
      "目标节目与播放器实际回报分别显示。",
      "Target content and actual player reports are shown separately.",
    ),
  audit: () =>
    t(
      "账号、配置与内容变更记录。",
      "Account, configuration and content changes.",
    ),
};
const allowed = (code) => props.me.permissions.includes(code);
const deptName = (id) =>
  catalog.value.departments.find((x) => x.id === id)?.name || "#" + id;
const screenName = (id) =>
  catalog.value.screens.find((x) => x.id === id)?.name || "#" + id;
const groupName = (id) =>
  catalog.value.groups.find((x) => x.id === id)?.name || "—";
const releaseName = (id) =>
  catalog.value.releases.find((x) => x.id === id)?.name ||
  id?.slice(0, 8) ||
  "—";
const bytes = (n) =>
  n > 1024 * 1024
    ? (n / 1024 / 1024).toFixed(1) + " MB"
    : Math.ceil(n / 1024) + " KB";
const filtered = computed(() =>
  rows.value.filter(
    (r) =>
      (filter.value === "all" ||
        (filter.value === "enabled" ? r.enabled : r.enabled === false)) &&
      JSON.stringify(r).toLowerCase().includes(q.value.toLowerCase()),
  ),
);
const visible = computed(() =>
  filtered.value.slice((page.value - 1) * 15, page.value * 15),
);
const source = computed(() =>
  props.section === "screens"
    ? catalog.value.screens
    : props.section === "media"
      ? catalog.value.media
      : props.section === "programs"
        ? catalog.value.playlists
        : [],
);
const stats = computed(() => ({
  total: rows.value.length,
  online: rows.value.filter((x) => x.online).length,
  playing: rows.value.filter(
    (x) => x.enabled && x.online && x.state === "PLAYING",
  ).length,
  error: rows.value.filter((x) => x.error).length,
}));
/** Refresh only persisted records; no manufactured screens or playback statistics. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
async function load() {
  ready.value = false;
  available.value = false;
  try {
    if (props.section === "audit") rows.value = await api("/audit");
    else if (props.section === "reports") rows.value = await api("/reports");
    else {
      catalog.value = await api("/catalog");
      rows.value = source.value;
    }
    page.value = Math.min(
      page.value,
      Math.max(1, Math.ceil(filtered.value.length / 15)),
    );
    available.value = true;
    emit("recovered");
  } catch (e) {
    emit("error", e);
  } finally {
    ready.value = true;
  }
}
/** Leave guards preserve unsaved edits and ambiguous publish requests. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
async function canLeave() {
  if (
    editor.value &&
    JSON.stringify(editor.value) !== initial.value &&
    !(await ask(t("放弃尚未保存的修改？", "Discard unsaved changes?")))
  )
    return false;
  if (
    publishForm.value &&
    !(await ask(
      t(
        "关闭尚未完成的发布？如连接中断，请先在发布历史核对。",
        "Close this publication? If connection was lost, verify its history first.",
      ),
    ))
  )
    return false;
  editor.value = null;
  publishForm.value = null;
  token.value = "";
  return true;
}
defineExpose({ canLeave });
function open(kind, row) {
  file.value = null;
  notice.value = "";
  editor.value = {
    kind,
    data: row
      ? cloneRecord(row)
      : {
          departmentId:
            catalog.value.departments.find((x) => x.enabled)?.id || "",
          name: "",
          enabled: true,
          ...(kind === "playlist"
            ? {
                categoryId:
                  catalog.value.categories?.find((x) => x.enabled)?.id || "",
                nameEn: "",
                orientation: "LANDSCAPE",
                fit: "contain",
                background: "#000000",
                items: [],
              }
            : {}),
        },
  };
  initial.value = JSON.stringify(editor.value);
}
async function editProgram(row) {
  try {
    const r = await api("/playlists/" + row.id);
    open("playlist", {
      ...r.playlist,
      items: r.items.map((x) => ({
        assetId: x.assetId || null,
        text: x.text || "",
        seconds: x.seconds,
        color: x.color,
      })),
    });
  } catch (e) {
    emit("error", e);
  }
}
function addItem() {
  editor.value.data.items.push({
    assetId: null,
    text: "",
    seconds: 10,
    color: "#ffffff",
  });
}
function move(index, direction) {
  const items = editor.value.data.items;
  const other = index + direction;
  if (other < 0 || other >= items.length) return;
  [items[index], items[other]] = [items[other], items[index]];
}
async function closeEditor() {
  if (
    editor.value &&
    JSON.stringify(editor.value) !== initial.value &&
    !(await ask(t("放弃尚未保存的修改？", "Discard unsaved changes?")))
  )
    return;
  editor.value = null;
}
/** Save complete forms once; only a confirmed response closes the editor. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
async function save() {
  const { kind, data } = editor.value;
  let path,
    method = "POST",
    body = data;
  if (kind === "upload") {
    if (!file.value) {
      notice.value = t("请选择文件。", "Choose a file.");
      return;
    }
    const form = new FormData();
    form.append("name", data.name);
    form.append("departmentId", data.departmentId);
    form.append("file", file.value);
    body = form;
    path = "/media";
  } else {
    const route = {
      media: "media",
      screen: "screens",
      group: "groups",
      playlist: "playlists",
    }[kind];
    path = "/" + route + (data.id ? "/" + data.id : "");
    if (data.id) method = "PUT";
  }
  const r = await props.perform(path, method, body);
  if (r) {
    editor.value = null;
    if (r.token) {
      token.value = r.token;
      showToken.value = false;
    }
    await load();
  }
}
async function remove(kind, row) {
  if (
    !(await ask(
      t(
        "删除未使用的记录？已有发布历史会保留。",
        "Delete an unused record? Published history is retained.",
      ),
    ))
  )
    return;
  if (await props.perform("/" + kind + "/" + row.id, "DELETE")) await load();
}
async function rotate(row) {
  if (
    !(await ask(
      t(
        "重新生成设备令牌？旧设备联网后会立即失效。",
        "Regenerate the device token? The previous device loses access when online.",
      ),
    ))
  )
    return;
  const r = await props.perform("/screens/" + row.id + "/rotate", "POST", {
    version: row.version,
  });
  if (r) {
    token.value = r.token;
    showToken.value = false;
    await load();
  }
}
async function copyToken() {
  try {
    await navigator.clipboard.writeText(token.value);
    notice.value = t("已复制。", "Copied.");
  } catch {
    notice.value = t(
      "复制不可用，请显示令牌后手动复制。",
      "Clipboard unavailable; reveal and copy the token manually.",
    );
  }
}
async function previewMedia(a) {
  preview.value = { media: a };
}
async function previewProgram(p) {
  try {
    const r = await api("/playlists/" + p.id);
    preview.value = { program: { ...r.playlist, items: r.items } };
  } catch (e) {
    emit("error", e);
  }
}
async function previewRelease(r) {
  try {
    preview.value = { program: await api("/releases/" + r.id) };
  } catch (e) {
    emit("error", e);
  }
}
function startPublish(p) {
  publishForm.value = {
    playlistId: p.id,
    playlistVersion: p.version,
    departmentId: p.departmentId,
    requestId: requestId(),
    screenIds: [],
    groupIds: [],
    mode: "now",
    start: "",
    end: "",
  };
}
/** Store a stable request ID so retries after a lost response recover the same release. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
async function publish() {
  const f = publishForm.value;
  const body = {
    requestId: f.requestId,
    playlistVersion: f.playlistVersion,
    screenIds: f.screenIds,
    groupIds: f.groupIds,
  };
  if (f.mode === "schedule") {
    if (!f.start || !f.end) {
      notice.value = t("请选择开始和结束时间。", "Select start and end times.");
      return;
    }
    body.startsAt = new Date(f.start).toISOString();
    body.endsAt = new Date(f.end).toISOString();
  }
  const r = await props.perform(
    "/playlists/" + f.playlistId + "/publish",
    "POST",
    body,
  );
  if (r) {
    publishForm.value = null;
    notice.value = t(
      "发布已保存，播放器完成同步后切换。",
      "Publication saved. Players switch after synchronization completes.",
    );
    await load();
  }
}
async function cancelSchedule(s) {
  if (
    !(await ask(
      t(
        "撤销此排期？播放器下次同步后恢复常驻节目。",
        "Cancel this schedule? Players return to baseline after syncing.",
      ),
    ))
  )
    return;
  if (
    await props.perform("/schedules/" + s.id + "/cancel", "POST", {
      version: s.version,
    })
  )
    await load();
}
async function rollback(s) {
  const choices = releaseHistory.value.filter(
    (r) => r.departmentId === s.departmentId && r.screenIds.includes(s.id),
  );
  editor.value = {
    kind: "rollback",
    data: { id: s.id, version: s.version, releaseId: "", choices },
  };
  initial.value = JSON.stringify(editor.value);
}
async function rollbackSave() {
  const f = editor.value.data;
  const r = await props.perform("/screens/" + f.id + "/rollback", "POST", {
    version: f.version,
    releaseId: f.releaseId,
  });
  if (r) {
    editor.value = null;
    await load();
  }
}
async function exportReports() {
  try {
    await download("/reports.csv", "screen-status.csv");
  } catch (e) {
    emit("error", e);
  }
}
const statusName = (s) =>
  ({
    PLAYING: t("播放中", "Playing"),
    IDLE: t("待播放", "Idle"),
    ERROR: t("异常", "Error"),
    EXPIRED: t("授权到期", "Lease expired"),
    DOWNLOADING: t("下载中", "Downloading"),
  })[s] || "—";
const scheduleName = (s) =>
  s.cancelled
    ? t("已撤销", "Cancelled")
    : Date.parse(s.endsAt) < Date.now()
      ? t("已结束", "Ended")
      : Date.parse(s.startsAt) <= Date.now()
        ? t("当前时段", "Current interval")
        : t("待开始", "Upcoming");
onMounted(load);
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <h1>{{ title }}</h1>
        <p class="muted">{{ descriptions[section]?.() }}</p>
      </div>
      <div class="actions">
        <button :disabled="pending || !ready" @click="load">
          {{ t("刷新", "Refresh") }}</button
        ><button
          v-if="section === 'media'"
          class="primary"
          :disabled="pending || !available"
          @click="open('upload')"
        >
          {{ t("上传素材", "Upload media") }}</button
        ><button
          v-if="section === 'programs' && allowed('programs')"
          class="primary"
          :disabled="pending || !available"
          @click="open('playlist')"
        >
          {{ t("新建节目", "New program") }}</button
        ><button
          v-if="section === 'screens'"
          class="primary"
          :disabled="pending || !available"
          @click="open('screen')"
        >
          {{ t("添加屏幕", "Add screen") }}</button
        ><button v-if="section === 'reports'" @click="exportReports">
          {{ t("导出CSV", "Export CSV") }}
        </button>
      </div>
    </header>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="!ready" class="empty">{{ t("正在读取…", "Loading…") }}</p>
    <template v-else>
      <div v-if="section === 'reports'" class="stats">
        <div>
          <strong>{{ stats.total }}</strong
          ><span>{{ t("屏幕", "Screens") }}</span>
        </div>
        <div>
          <strong>{{ stats.online }}</strong
          ><span>{{ t("在线", "Online") }}</span>
        </div>
        <div>
          <strong>{{ stats.playing }}</strong
          ><span>{{ t("在线播放回报", "Online playing reports") }}</span>
        </div>
        <div>
          <strong>{{ stats.error }}</strong
          ><span>{{ t("异常回报", "Reported errors") }}</span>
        </div>
      </div>
      <template v-if="section === 'publish'"
        ><section class="subsection">
          <h2>{{ t("节目发布", "Publish a program") }}</h2>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("节目", "Program") }}</th>
                  <th>{{ t("组织", "Organization") }}</th>
                  <th>{{ t("草稿版本", "Draft version") }}</th>
                  <th>{{ t("操作", "Actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in catalog.playlists" :key="p.id">
                  <td>{{ localized(p) }}</td>
                  <td>{{ deptName(p.departmentId) }}</td>
                  <td>{{ p.version }}</td>
                  <td>
                    <button @click="previewProgram(p)">
                      {{ t("预览", "Preview") }}
                    </button>
                    <button class="primary" @click="startPublish(p)">
                      {{ t("发布", "Publish") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!catalog.playlists.length" class="empty">
              {{
                t(
                  "先在节目中保存草稿，再选择屏幕发布。",
                  "Save a draft program, then choose screens to publish.",
                )
              }}
            </p>
          </div>
        </section>
        <section class="subsection">
          <h2>{{ t("限时排期", "Limited schedules") }}</h2>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("屏幕", "Screen") }}</th>
                  <th>{{ t("节目", "Program") }}</th>
                  <th>{{ t("开始", "Start") }}</th>
                  <th>{{ t("结束", "End") }}</th>
                  <th>{{ t("状态", "Status") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="s in [...catalog.schedules].reverse()" :key="s.id">
                  <td>{{ screenName(s.screenId) }}</td>
                  <td>{{ releaseName(s.releaseId) }}</td>
                  <td>{{ dateTime(s.startsAt) }}</td>
                  <td>{{ dateTime(s.endsAt) }}</td>
                  <td>
                    <span class="badge">{{ scheduleName(s) }}</span>
                  </td>
                  <td>
                    <button
                      v-if="!s.cancelled && Date.parse(s.endsAt) > Date.now()"
                      :disabled="pending"
                      @click="cancelSchedule(s)"
                    >
                      {{ t("撤销", "Cancel") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!catalog.schedules.length" class="empty">
              {{ t("暂无限时排期。", "No scheduled intervals.") }}
            </p>
          </div>
        </section>
        <section class="subsection">
          <h2>{{ t("发布历史", "Publication history") }}</h2>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("节目", "Program") }}</th>
                  <th>{{ t("发布版本", "Release") }}</th>
                  <th>{{ t("发布人", "Published by") }}</th>
                  <th>{{ t("时间", "Time") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in releaseHistory" :key="r.id">
                  <td>{{ r.name }}</td>
                  <td>
                    <code>{{ r.id.slice(0, 8) }}</code>
                  </td>
                  <td>{{ r.actor }}</td>
                  <td>{{ dateTime(r.createdAt) }}</td>
                  <td>
                    <button @click="previewRelease(r)">
                      {{ t("查看快照", "View snapshot") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!catalog.releases.length" class="empty">
              {{ t("暂无发布记录。", "No publications.") }}
            </p>
          </div>
        </section></template
      >
      <template v-else
        ><div class="toolbar">
          <label class="search"
            ><span>{{ t("搜索", "Search") }}</span
            ><input
              v-model="q"
              :placeholder="t('名称或关键字', 'Name or keyword')"
              @input="page = 1" /></label
          ><select
            v-if="['media', 'screens'].includes(section)"
            v-model="filter"
            :aria-label="t('状态筛选', 'Status filter')"
            @change="page = 1"
          >
            <option value="all">{{ t("全部状态", "All statuses") }}</option>
            <option value="enabled">{{ t("启用", "Enabled") }}</option>
            <option value="disabled">
              {{ t("停用", "Disabled") }}
            </option></select
          ><span class="muted"
            >{{ filtered.length }} {{ t("条", "records") }}</span
          >
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr v-if="section === 'media'">
                <th>{{ t("素材", "Media") }}</th>
                <th>{{ t("格式与尺寸", "Format & dimensions") }}</th>
                <th>{{ t("大小", "Size") }}</th>
                <th>{{ t("组织", "Organization") }}</th>
                <th>{{ t("状态", "Status") }}</th>
                <th>{{ t("操作", "Actions") }}</th>
              </tr>
              <tr v-else-if="section === 'programs'">
                <th>{{ t("节目", "Program") }}</th>
                <th>{{ t("组织", "Organization") }}</th>
                <th>{{ t("画面", "Canvas") }}</th>
                <th>{{ t("草稿版本", "Draft version") }}</th>
                <th>{{ t("操作", "Actions") }}</th>
              </tr>
              <tr v-else-if="section === 'screens'">
                <th>{{ t("屏幕", "Screen") }}</th>
                <th>{{ t("分组", "Group") }}</th>
                <th>{{ t("组织", "Organization") }}</th>
                <th>{{ t("常驻节目", "Baseline") }}</th>
                <th>{{ t("状态", "Status") }}</th>
                <th>{{ t("操作", "Actions") }}</th>
              </tr>
              <tr v-else-if="section === 'reports'">
                <th>{{ t("屏幕", "Screen") }}</th>
                <th>{{ t("连接", "Connection") }}</th>
                <th>{{ t("实际回报", "Actual report") }}</th>
                <th>{{ t("目标版本", "Target release") }}</th>
                <th>{{ t("播放版本", "Applied release") }}</th>
                <th>{{ t("最近回报", "Last report") }}</th>
                <th>{{ t("异常", "Error") }}</th>
              </tr>
              <tr v-else>
                <th>{{ t("时间", "Time") }}</th>
                <th>{{ t("账号", "Account") }}</th>
                <th>{{ t("动作", "Action") }}</th>
                <th>{{ t("对象", "Object") }}</th>
                <th>{{ t("组织", "Organization") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in visible" :key="r.id">
                <template v-if="section === 'media'"
                  ><td>
                    <div class="media-name">
                      <img
                        v-if="r.kind === 'IMAGE'"
                        :src="'/api/media/' + r.id + '/content'"
                        alt=""
                        loading="lazy"
                      /><span v-else class="video-mark">MP4</span
                      ><strong>{{ r.name }}</strong>
                    </div>
                  </td>
                  <td>
                    {{
                      r.kind === "IMAGE"
                        ? t("图片", "Image")
                        : t("视频", "Video")
                    }}
                    · {{ r.width }}×{{ r.height
                    }}<small v-if="r.duration">
                      · {{ r.duration.toFixed(1) }}s</small
                    >
                  </td>
                  <td>{{ bytes(r.bytes) }}</td>
                  <td>{{ deptName(r.departmentId) }}</td>
                  <td>
                    <span class="badge" :class="{ good: r.enabled }">{{
                      r.enabled
                        ? t("可发布", "Available")
                        : t("已停用", "Disabled")
                    }}</span>
                  </td>
                  <td>
                    <div class="row-actions">
                      <button @click="previewMedia(r)">
                        {{ t("预览", "Preview") }}</button
                      ><button @click="open('media', r)">
                        {{ t("编辑", "Edit") }}</button
                      ><button @click="remove('media', r)">
                        {{ t("删除", "Delete") }}
                      </button>
                    </div>
                  </td></template
                >
                <template v-else-if="section === 'programs'"
                  ><td>
                    <strong>{{ localized(r) }}</strong>
                  </td>
                  <td>{{ deptName(r.departmentId) }}</td>
                  <td>
                    {{
                      r.orientation === "PORTRAIT"
                        ? t("竖屏", "Portrait")
                        : t("横屏", "Landscape")
                    }}
                  </td>
                  <td>{{ r.version }}</td>
                  <td>
                    <div class="row-actions">
                      <button @click="previewProgram(r)">
                        {{ t("预览", "Preview") }}</button
                      ><button
                        v-if="allowed('programs')"
                        @click="editProgram(r)"
                      >
                        {{ t("编辑", "Edit") }}</button
                      ><button
                        v-if="allowed('publish')"
                        class="primary"
                        @click="startPublish(r)"
                      >
                        {{ t("发布", "Publish") }}</button
                      ><button
                        v-if="allowed('programs')"
                        @click="remove('playlists', r)"
                      >
                        {{ t("删除", "Delete") }}
                      </button>
                    </div>
                  </td></template
                >
                <template v-else-if="section === 'screens'"
                  ><td>
                    <strong>{{ r.name }}</strong>
                  </td>
                  <td>{{ groupName(r.groupId) }}</td>
                  <td>{{ deptName(r.departmentId) }}</td>
                  <td>{{ releaseName(r.baselineRelease) }}</td>
                  <td>
                    <span class="badge" :class="{ good: r.enabled }">{{
                      r.enabled ? t("启用", "Enabled") : t("停用", "Disabled")
                    }}</span>
                  </td>
                  <td>
                    <div class="row-actions">
                      <button @click="open('screen', r)">
                        {{ t("编辑", "Edit") }}</button
                      ><button @click="rotate(r)">
                        {{ t("更换令牌", "New token") }}</button
                      ><button
                        v-if="allowed('publish') && r.baselineRelease"
                        @click="rollback(r)"
                      >
                        {{ t("回滚", "Rollback") }}</button
                      ><button @click="remove('screens', r)">
                        {{ t("删除", "Delete") }}
                      </button>
                    </div>
                  </td></template
                >
                <template v-else-if="section === 'reports'"
                  ><td>
                    <strong>{{ r.name }}</strong>
                  </td>
                  <td>
                    <span class="badge" :class="{ good: r.online }">{{
                      r.online
                        ? t("在线", "Online")
                        : t("离线/未连接", "Offline / unpaired")
                    }}</span>
                  </td>
                  <td>{{ statusName(r.state) }}</td>
                  <td>
                    <code>{{ r.targetRelease?.slice(0, 8) || "—" }}</code>
                  </td>
                  <td>
                    <code>{{ r.appliedRelease?.slice(0, 8) || "—" }}</code>
                  </td>
                  <td>{{ dateTime(r.lastSeen) }}</td>
                  <td>{{ r.error || "—" }}</td></template
                >
                <template v-else
                  ><td>{{ dateTime(r.createdAt) }}</td>
                  <td>{{ r.actor }}</td>
                  <td>{{ r.action }}</td>
                  <td>
                    <code>{{ r.objectId }}</code>
                  </td>
                  <td>{{ r.departmentId }}</td></template
                >
              </tr>
            </tbody>
          </table>
          <p v-if="!visible.length" class="empty">
            {{
              q
                ? t("没有匹配记录。", "No matching records.")
                : t(
                    "暂无记录。请从上方操作开始。",
                    "No records yet. Use the actions above to get started.",
                  )
            }}
          </p>
        </div>
        <footer class="pagination">
          <button :disabled="page <= 1" @click="page--">
            {{ t("上一页", "Previous") }}</button
          ><span
            >{{ page }} /
            {{ Math.max(1, Math.ceil(filtered.length / 15)) }}</span
          ><button :disabled="page * 15 >= filtered.length" @click="page++">
            {{ t("下一页", "Next") }}
          </button>
        </footer>
        <section v-if="section === 'screens'" class="subsection">
          <header class="inline-heading">
            <h2>{{ t("屏幕分组", "Screen groups") }}</h2>
            <button @click="open('group')">
              {{ t("新建分组", "New group") }}
            </button>
          </header>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("分组", "Group") }}</th>
                  <th>{{ t("组织", "Organization") }}</th>
                  <th>{{ t("状态", "Status") }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="g in catalog.groups" :key="g.id">
                  <td>{{ g.name }}</td>
                  <td>{{ deptName(g.departmentId) }}</td>
                  <td>
                    {{
                      g.enabled ? t("启用", "Enabled") : t("停用", "Disabled")
                    }}
                  </td>
                  <td>
                    <button @click="open('group', g)">
                      {{ t("编辑", "Edit") }}
                    </button>
                    <button @click="remove('groups', g)">
                      {{ t("删除", "Delete") }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="!catalog.groups.length" class="empty">
              {{ t("暂无分组。", "No groups.") }}
            </p>
          </div>
        </section></template
      ></template
    >
  </section>
  <div v-if="editor" class="overlay">
    <section
      class="dialog"
      :class="{ large: editor.kind === 'playlist' }"
      role="dialog"
      aria-modal="true"
      :aria-label="
        editor.kind === 'playlist'
          ? t('节目编辑', 'Program editor')
          : t('编辑记录', 'Edit record')
      "
    >
      <header>
        <h2>
          {{
            editor.kind === "upload"
              ? t("上传素材", "Upload media")
              : editor.kind === "playlist"
                ? t("节目编辑", "Program editor")
                : editor.kind === "rollback"
                  ? t("回滚常驻节目", "Rollback baseline")
                  : t("编辑记录", "Edit record")
          }}
        </h2>
        <button :disabled="pending" @click="closeEditor">
          {{ t("关闭", "Close") }}
        </button>
      </header>
      <form
        @submit.prevent="editor.kind === 'rollback' ? rollbackSave() : save()"
      >
        <template v-if="editor.kind === 'rollback'"
          ><label
            >{{ t("发布版本", "Published release")
            }}<select v-model="editor.data.releaseId" required>
              <option disabled value="">
                {{
                  t(
                    "选择已投放给此屏幕的版本",
                    "Choose a version previously assigned to this screen",
                  )
                }}
              </option>
              <option
                v-for="r in editor.data.choices"
                :key="r.id"
                :value="r.id"
              >
                {{ r.name }} · {{ dateTime(r.createdAt) }} ·
                {{ r.id.slice(0, 8) }}
              </option>
            </select></label
          >
          <p class="muted">
            {{
              t(
                "限时排期继续按已有时段生效。",
                "Scheduled intervals remain active as configured.",
              )
            }}
          </p></template
        >
        <template v-else
          ><div class="form-grid">
            <label
              >{{ t("名称", "Name")
              }}<input
                v-model="editor.data.name"
                required
                maxlength="160" /></label
            ><label v-if="editor.kind !== 'media'"
              >{{ t("组织", "Organization")
              }}<select
                v-model="editor.data.departmentId"
                required
                :disabled="!!editor.data.id"
              >
                <option disabled value="">{{ t("请选择", "Select") }}</option>
                <option
                  v-for="d in catalog.departments.filter((x) => x.enabled)"
                  :key="d.id"
                  :value="d.id"
                >
                  {{ d.name }}
                </option>
              </select></label
            ><label v-if="editor.kind === 'playlist'"
              >{{ t("内容类别", "Category")
              }}<select v-model="editor.data.categoryId" required>
                <option disabled value="">{{ t("请选择", "Select") }}</option>
                <option
                  v-for="c in catalog.categories.filter((x) => x.enabled)"
                  :key="c.id"
                  :value="c.id"
                >
                  {{ localized(c) }}
                </option>
              </select></label
            ><label v-if="editor.kind === 'playlist'"
              >{{ t("英文名称", "English name")
              }}<input
                v-model="editor.data.nameEn"
                required
                maxlength="160" /></label
            ><label v-if="editor.kind === 'screen'"
              >{{ t("分组", "Group")
              }}<select v-model="editor.data.groupId">
                <option :value="null">{{ t("未分组", "Ungrouped") }}</option>
                <option
                  v-for="g in catalog.groups.filter(
                    (x) =>
                      x.departmentId === editor.data.departmentId && x.enabled,
                  )"
                  :key="g.id"
                  :value="g.id"
                >
                  {{ g.name }}
                </option>
              </select></label
            ><label v-if="editor.kind === 'playlist'"
              >{{ t("画面方向", "Orientation")
              }}<select v-model="editor.data.orientation">
                <option value="LANDSCAPE">
                  {{ t("横屏16:9", "Landscape 16:9") }}
                </option>
                <option value="PORTRAIT">
                  {{ t("竖屏9:16", "Portrait 9:16") }}
                </option>
              </select></label
            ><label v-if="editor.kind === 'playlist'"
              >{{ t("素材适配", "Media fit")
              }}<select v-model="editor.data.fit">
                <option value="contain">{{ t("完整显示", "Contain") }}</option>
                <option value="cover">{{ t("填满画面", "Cover") }}</option>
              </select></label
            ><label v-if="editor.kind === 'playlist'"
              >{{ t("背景颜色", "Background")
              }}<input v-model="editor.data.background" type="color"
            /></label>
          </div>
          <label v-if="editor.kind === 'upload'"
            >{{ t("文件", "File")
            }}<input
              type="file"
              accept="image/png,image/jpeg,video/mp4"
              required
              @change="file = $event.target.files[0]"
            /><small>{{
              t(
                "PNG/JPEG；H.264/AAC MP4，每个文件最多64MB。",
                "PNG/JPEG; H.264/AAC MP4, up to 64MB per file.",
              )
            }}</small></label
          >
          <label
            v-if="['media', 'screen', 'group'].includes(editor.kind)"
            class="check"
            ><input v-model="editor.data.enabled" type="checkbox" />{{
              t("启用", "Enabled")
            }}</label
          >
          <template v-if="editor.kind === 'playlist'"
            ><header class="inline-heading">
              <h3>{{ t("节目项", "Program items") }}</h3>
              <button type="button" @click="addItem">
                {{ t("添加一项", "Add item") }}
              </button>
            </header>
            <p v-if="!editor.data.items.length" class="empty">
              {{
                t(
                  "添加素材或纯文本，按顺序循环播放。",
                  "Add media or plain text, played in sequence.",
                )
              }}
            </p>
            <div
              v-for="(item, i) in editor.data.items"
              :key="i"
              class="program-item"
            >
              <header>
                <strong>{{ i + 1 }}</strong>
                <div class="row-actions">
                  <button
                    type="button"
                    :disabled="i === 0"
                    @click="move(i, -1)"
                  >
                    ↑</button
                  ><button
                    type="button"
                    :disabled="i === editor.data.items.length - 1"
                    @click="move(i, 1)"
                  >
                    ↓</button
                  ><button
                    type="button"
                    @click="editor.data.items.splice(i, 1)"
                  >
                    {{ t("移除", "Remove") }}
                  </button>
                </div>
              </header>
              <div class="form-grid">
                <label
                  >{{ t("内容", "Content")
                  }}<select v-model="item.assetId">
                    <option :value="null">
                      {{ t("纯文本", "Plain text") }}
                    </option>
                    <option
                      v-for="a in catalog.media.filter(
                        (x) =>
                          x.enabled &&
                          x.departmentId === editor.data.departmentId,
                      )"
                      :key="a.id"
                      :value="a.id"
                    >
                      {{ a.name }}
                    </option>
                  </select></label
                ><label
                  >{{ t("显示秒数", "Display seconds")
                  }}<input
                    v-model.number="item.seconds"
                    type="number"
                    min="2"
                    max="600"
                    required
                /></label>
              </div>
              <label v-if="item.assetId === null"
                >{{ t("文本内容", "Text")
                }}<textarea
                  v-model="item.text"
                  required
                  maxlength="4000"
                  rows="3"
                ></textarea></label
              ><label v-if="item.assetId === null"
                >{{ t("文字颜色", "Text color")
                }}<input v-model="item.color" type="color"
              /></label></div></template
        ></template>
        <p v-if="notice" class="notice">{{ notice }}</p>
        <footer class="actions">
          <button type="button" :disabled="pending" @click="closeEditor">
            {{ t("取消", "Cancel") }}</button
          ><button class="primary" :disabled="pending">
            {{ pending ? t("保存中…", "Saving…") : t("保存", "Save") }}
          </button>
        </footer>
      </form>
    </section>
  </div>
  <div v-if="publishForm" class="overlay">
    <section
      class="dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="t('发布节目', 'Publish program')"
    >
      <header>
        <h2>{{ t("发布节目", "Publish program") }}</h2>
        <button :disabled="pending" @click="canLeave">
          {{ t("关闭", "Close") }}
        </button>
      </header>
      <form @submit.prevent="publish">
        <fieldset>
          <legend>{{ t("目标屏幕", "Target screens") }}</legend>
          <label
            v-for="s in catalog.screens.filter(
              (x) => x.enabled && x.departmentId === publishForm.departmentId,
            )"
            :key="s.id"
            class="check"
            ><input
              v-model="publishForm.screenIds"
              type="checkbox"
              :value="s.id"
            />{{ s.name }}</label
          >
          <p
            v-if="
              !catalog.screens.some(
                (x) => x.enabled && x.departmentId === publishForm.departmentId,
              )
            "
            class="muted"
          >
            {{
              t(
                "本组织没有启用的屏幕，请先添加。",
                "No enabled screens in this organization; add one first.",
              )
            }}
          </p>
        </fieldset>
        <fieldset
          v-if="
            catalog.groups.some(
              (x) => x.enabled && x.departmentId === publishForm.departmentId,
            )
          "
        >
          <legend>{{ t("或选择分组", "Or select groups") }}</legend>
          <label
            v-for="g in catalog.groups.filter(
              (x) => x.enabled && x.departmentId === publishForm.departmentId,
            )"
            :key="g.id"
            class="check"
            ><input
              v-model="publishForm.groupIds"
              type="checkbox"
              :value="g.id"
            />{{ g.name }}</label
          >
        </fieldset>
        <label
          >{{ t("发布时间", "Publication timing")
          }}<select v-model="publishForm.mode">
            <option value="now">
              {{ t("立即更新常驻节目", "Update baseline now") }}
            </option>
            <option value="schedule">
              {{ t("限时排期", "Limited interval") }}
            </option>
          </select></label
        >
        <div v-if="publishForm.mode === 'schedule'" class="form-grid">
          <label
            >{{ t("开始（浏览器本地时区）", "Start (browser local timezone)")
            }}<input
              v-model="publishForm.start"
              type="datetime-local"
              required /></label
          ><label
            >{{ t("结束（浏览器本地时区）", "End (browser local timezone)")
            }}<input v-model="publishForm.end" type="datetime-local" required
          /></label>
        </div>
        <p class="muted">
          {{
            t(
              "发布后播放器先缓存完整节目，再切换画面。",
              "Players cache the complete program before switching.",
            )
          }}
        </p>
        <p v-if="notice" class="notice">{{ notice }}</p>
        <footer class="actions">
          <button type="button" :disabled="pending" @click="canLeave">
            {{ t("取消", "Cancel") }}</button
          ><button
            class="primary"
            :disabled="
              pending ||
              (!publishForm.screenIds.length && !publishForm.groupIds.length)
            "
          >
            {{ t("确认发布", "Publish") }}
          </button>
        </footer>
      </form>
    </section>
  </div>
  <div v-if="token" class="overlay">
    <section
      class="dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="t('设备令牌', 'Device token')"
    >
      <header>
        <h2>{{ t("连接播放器", "Connect the player") }}</h2>
        <button
          @click="
            token = '';
            notice = '';
          "
        >
          {{ t("已保存，关闭", "Saved, close") }}
        </button>
      </header>
      <p>
        {{
          t(
            "设备令牌只显示一次。将后台地址与此令牌输入电脑播放器设置页。",
            "The device token is shown once. Enter the CMS address and token in the computer player settings.",
          )
        }}
      </p>
      <label
        >{{ t("设备令牌", "Device token")
        }}<input
          :value="token"
          :type="showToken ? 'text' : 'password'"
          readonly
          autocomplete="off"
      /></label>
      <div class="actions">
        <button @click="showToken = !showToken">
          {{ showToken ? t("隐藏", "Hide") : t("显示", "Reveal") }}</button
        ><button @click="copyToken">{{ t("复制令牌", "Copy token") }}</button>
      </div>
      <p class="muted">
        {{
          t(
            "不要分享令牌或将其写入截图、网址。",
            "Do not share tokens or put them in screenshots or URLs.",
          )
        }}
      </p>
      <p v-if="notice">{{ notice }}</p>
    </section>
  </div>
  <div v-if="preview" class="overlay">
    <section
      class="dialog large"
      role="dialog"
      aria-modal="true"
      :aria-label="t('内容预览', 'Content preview')"
    >
      <header>
        <h2>{{ t("内容预览", "Content preview") }}</h2>
        <button @click="preview = null">{{ t("关闭", "Close") }}</button>
      </header>
      <template v-if="preview.media"
        ><img
          v-if="preview.media.kind === 'IMAGE'"
          class="preview-media"
          :src="'/api/media/' + preview.media.id + '/content'"
          :alt="preview.media.name"
        /><video
          v-else
          class="preview-media"
          controls
          muted
          :src="'/api/media/' + preview.media.id + '/content'"
        ></video>
        <p>
          {{ preview.media.name }} · {{ preview.media.width }}×{{
            preview.media.height
          }}
        </p></template
      ><template v-else
        ><h3>{{ localized(preview.program) }}</h3>
        <div class="preview-list">
          <div
            v-for="(item, i) in preview.program.items"
            :key="i"
            class="preview-item"
            :style="{
              background: preview.program.background,
              color: item.color,
            }"
          >
            <img
              v-if="
                item.assetId &&
                catalog.media.find((x) => x.id === item.assetId)?.kind !==
                  'VIDEO'
              "
              :src="'/api/media/' + item.assetId + '/content'"
              alt=""
            /><video
              v-else-if="item.assetId"
              controls
              muted
              :src="'/api/media/' + item.assetId + '/content'"
            ></video>
            <p v-else>{{ item.text }}</p>
            <span>{{ i + 1 }} · {{ item.seconds }}s</span>
          </div>
        </div></template
      >
    </section>
  </div>
</template>
