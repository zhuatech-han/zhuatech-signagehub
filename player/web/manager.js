// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
let csrf = "",
  configured = false,
  syncing = false;
const message = document.querySelector("#message");
const errors = {
  DEVICE_REVOKED: "屏幕已停用或令牌已失效，请重新连接。",
  CMS_UNAVAILABLE: "内容后台暂不可达；有效缓存会继续播放。",
  CACHE_INVALID: "本机缓存校验失败，请联网重新同步。",
  CLOCK_INVALID: "本机时间异常，请联网校正。",
  OFFLINE_LEASE_EXPIRED: "离线授权已到期，请联网同步。",
  MEDIA_CHECKSUM_MISMATCH: "素材校验失败，继续保留原节目。",
  MEDIA_DOWNLOAD_FAILED: "素材下载失败，继续保留原节目。",
  SERVER_INVALID: "请输入HTTPS后台地址；本机调试可用localhost。",
  TOKEN_INVALID: "设备令牌格式不正确。",
  PROGRAM_INVALID: "节目格式不受支持。",
  MEDIA_PLAYBACK_FAILED: "浏览器无法播放当前素材。",
  MEDIA_SIZE_MISMATCH: "素材下载不完整，继续保留原节目。",
  PROGRAM_TOO_LARGE: "节目素材超过本机缓存上限，请在后台减少素材。",
  CONFIG_INVALID: "本机连接配置损坏，请重新填写后台地址和设备令牌。",
  CACHE_PATH_INVALID: "缓存路径不是可用的私有目录，请检查播放器启动配置。",
  SYNC_BUSY: "当前同步尚未结束，请稍后重试。",
  INPUT_INVALID: "连接信息不完整，请检查后台地址和设备令牌。",
  INPUT_TOO_LARGE: "输入过长，请检查后台地址和设备令牌。",
  LOCAL_FORBIDDEN: "本机连接已更新，请刷新页面再操作。",
};
/** Render non-sensitive device status with DOM text only. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
async function refresh() {
  try {
    const s = await (await fetch("/local/status")).json();
    csrf = s.csrf;
    configured = s.configured;
    document.querySelector("#sync").disabled = !configured || syncing;
    const serverField = document.querySelector("#config").elements.server;
    if (
      !serverField.value &&
      s.server &&
      document.activeElement !== serverField
    )
      serverField.value = s.server;
    document.querySelector("#screen").textContent =
      s.screenName || "尚未连接屏幕";
    const items = [
      ["后台", s.server || "未设置"],
      ["连接", s.online ? "在线" : "未连接"],
      ["节目", s.ready ? "已缓存可播放" : "暂无可播放内容"],
      [
        "离线有效期",
        s.validUntil ? new Date(s.validUntil).toLocaleString() : "—",
      ],
    ];
    const box = document.querySelector("#details");
    box.replaceChildren();
    for (const [k, v] of items) {
      const dt = document.createElement("dt"),
        dd = document.createElement("dd");
      dt.textContent = k;
      dd.textContent = v;
      box.append(dt, dd);
    }
    if (s.error) message.textContent = errors[s.error] || s.error;
  } catch {
    message.textContent = "本机播放器服务不可达。";
  }
}
async function post(url, b) {
  const r = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Player-CSRF": csrf },
    body: JSON.stringify(b),
  });
  const s = await r.json();
  if (!r.ok) throw Error(errors[s.error] || s.error);
  return s;
}
document.querySelector("#config").addEventListener("submit", async (e) => {
  e.preventDefault();
  const f = e.currentTarget,
    button = f.querySelector("button");
  button.disabled = true;
  message.textContent = "正在连接并下载节目…";
  try {
    await post("/local/config", {
      server: f.elements.server.value,
      token: f.elements.token.value,
    });
    f.elements.token.value = "";
    message.textContent = "连接配置已保存。";
    await refresh();
  } catch (e) {
    message.textContent = e.message;
  } finally {
    button.disabled = false;
  }
});
document.querySelector("#sync").addEventListener("click", async (e) => {
  syncing = true;
  e.currentTarget.disabled = true;
  message.textContent = "正在同步节目…";
  try {
    await post("/local/sync", {});
    message.textContent = "同步检查完成。";
    await refresh();
  } catch (e) {
    message.textContent = e.message;
  } finally {
    syncing = false;
    document.querySelector("#sync").disabled = !configured;
  }
});
void refresh();
setInterval(refresh, 5000);
