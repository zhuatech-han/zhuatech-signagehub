// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { ref } from "vue";
export const language = ref(
  typeof localStorage === "undefined"
    ? "zh"
    : localStorage.getItem("signage-language") || "zh",
);
/** Presentation language never changes program contents or dates. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
export const t = (zh, en) => (language.value === "zh" ? zh : en);
export const localized = (r) =>
  language.value === "en" ? r.nameEn || r.name : r.name;
export const cloneRecord = (r) => JSON.parse(JSON.stringify(r));
export const dateTime = (v) =>
  v
    ? new Intl.DateTimeFormat(language.value === "zh" ? "zh-CN" : "en", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(v))
    : "—";
export const confirmation = ref(null);
/** Use an in-app confirmation instead of operating-system dialogs. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
export function ask(message) {
  if (confirmation.value) return Promise.resolve(false);
  return new Promise((resolve) => {
    confirmation.value = { message, resolve };
  });
}
const errors = {
  UNAUTHENTICATED: ["请重新登录。", "Please sign in again."],
  FORBIDDEN: ["没有操作权限。", "Permission denied."],
  OUT_OF_SCOPE: ["不在当前账号授权范围内。", "Outside your authorized scope."],
  LOGIN_FAILED: ["账号或密码不正确。", "Invalid username or password."],
  LOGIN_THROTTLED: [
    "尝试过多，请稍后再试。",
    "Too many attempts; retry later.",
  ],
  PASSWORD_WEAK: [
    "密码须12–72字节，含大小写字母和数字。",
    "Use 12–72 bytes with upper/lowercase and a digit.",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确。", "Original password invalid."],
  INVALID_INPUT: ["请核对必填项与格式。", "Check required fields and formats."],
  VERSION_CONFLICT: [
    "资料已更新，请刷新后再修改。",
    "Record changed; refresh before editing.",
  ],
  LAST_ADMIN: [
    "必须保留一个完整管理员。",
    "Keep at least one full administrator.",
  ],
  CONFLICT: ["记录重复或仍被引用。", "Duplicate or referenced record."],
  SYSTEM_MENU_REQUIRED: [
    "账号、角色和设置菜单须保留启用。",
    "Keep account, role and settings menus enabled.",
  ],
  IDENTITY_LOCKED: ["资料身份不能修改。", "Record identity cannot change."],
  DEPARTMENT_DISABLED: ["组织已停用。", "Organization disabled."],
  NETWORK_ERROR: [
    "服务器不可达，请稍后刷新。",
    "Server unavailable; retry later.",
  ],
  RESULT_UNKNOWN: [
    "连接中断，结果未知。请刷新核对后再操作。",
    "Connection lost; result unknown. Refresh and verify before retrying.",
  ],
  MEDIA_INVALID: [
    "素材无效、停用或不属于本组织。",
    "Media invalid, disabled or outside this organization.",
  ],
  MEDIA_CODEC: [
    "视频仅支持MP4中的H.264视频及AAC音频。",
    "Only H.264 video and AAC audio in MP4 are supported.",
  ],
  MEDIA_DIMENSIONS: [
    "图片最大4096×4096；视频最大1920×1920、300秒。",
    "Images: up to 4096×4096; videos: up to 1920×1920 and 300 seconds.",
  ],
  FILE_TOO_LARGE: ["素材超过64MB上限。", "Media exceeds 64MB."],
  MEDIA_QUOTA: [
    "本组织素材总量超过512MB。",
    "Organization media quota exceeds 512MB.",
  ],
  MEDIA_IN_USE: [
    "节目或历史发布正在引用此素材。",
    "Media is referenced by a draft or published program.",
  ],
  PROGRAM_HAS_HISTORY: [
    "已发布的节目保留历史，不能删除。",
    "Published program history must be retained.",
  ],
  SCREEN_HAS_HISTORY: [
    "已配对或投放的屏幕须停用，不能删除。",
    "Paired or published screens must be disabled rather than deleted.",
  ],
  GROUP_IN_USE: [
    "分组仍有屏幕，请先调整屏幕分组。",
    "Move screens out of this group first.",
  ],
  GROUP_INVALID: [
    "分组停用或不属于本组织。",
    "Group disabled or outside this organization.",
  ],
  PROGRAM_EMPTY: ["请至少添加一个节目项。", "Add at least one program item."],
  PROGRAM_TOO_LARGE: [
    "节目缓存总量超过256MB。",
    "Program cache exceeds 256MB.",
  ],
  TARGET_INVALID: [
    "请选择同组织已启用的屏幕或分组。",
    "Select enabled screens or groups in the same organization.",
  ],
  SCHEDULE_OVERLAP: [
    "此屏幕已有重叠排期，请调整时段。",
    "A screen already has an overlapping schedule.",
  ],
  SCHEDULE_INVALID: [
    "排期须在未来开始、90天内结束，单次不超过7天。",
    "Start in the future, end within 90 days and keep the interval below 7 days.",
  ],
  SCHEDULE_FINISHED: ["排期已撤销或结束。", "Schedule cancelled or finished."],
  REQUEST_REUSED: [
    "此发布请求已用于其他内容，请刷新核对。",
    "Publication request was reused; refresh and verify.",
  ],
  RELEASE_NOT_ASSIGNED: [
    "此版本没有投放给该屏幕。",
    "This release was not assigned to the screen.",
  ],
  SCREEN_LIMIT: [
    "超过配置的屏幕数量上限。",
    "Configured screen limit exceeded.",
  ],
  EMAIL_INVALID: ["请输入有效邮箱。", "Enter a valid email format."],
};
export const errorMessage = (e) =>
  errors[e.message]
    ? t(...errors[e.message])
    : t("操作未完成", "Action failed") + " (" + e.message + ")";
/** Secure UUID also works in local HTTP deployments. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
export function requestId() {
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 15) | 64;
  b[8] = (b[8] & 63) | 128;
  const s = [...b].map((x) => x.toString(16).padStart(2, "0")).join("");
  return [
    s.slice(0, 8),
    s.slice(8, 12),
    s.slice(12, 16),
    s.slice(16, 20),
    s.slice(20),
  ].join("-");
}
