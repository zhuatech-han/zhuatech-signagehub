// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
const stage = document.querySelector("#stage"),
  waiting = document.querySelector("#waiting");
let release = null,
  index = 0,
  deadline = 0,
  node = null,
  csrf = "",
  ackAt = 0,
  loading = false,
  rendered = false;
async function ack(error) {
  if (!release || !csrf) return;
  try {
    const response = await fetch("/local/ack", {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Player-CSRF": csrf },
      body: JSON.stringify({ releaseId: release.id, error }),
    });
    // The local service issues a new CSRF value after restart; renew it on the next poll.
    if (response.status === 403) csrf = "";
  } catch {
    /* The local service must recover before reporting playback. */
  }
}
/** A new item appears after it loads; no HTML from the CMS is executed. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
function show() {
  if (!release) return;
  rendered = false;
  const current = release,
    at = index,
    item = current.items[at];
  const el = document.createElement(
    item.assetId === undefined
      ? "div"
      : item.kind === "IMAGE"
        ? "img"
        : "video",
  );
  el.className = item.assetId === undefined ? "text-slide" : "media-slide";
  el.style.color = item.color;
  el.style.objectFit = current.fit;
  const commit = () => {
    if (release?.id !== current.id || index !== at) {
      if (el.tagName === "VIDEO") el.pause();
      return;
    }
    if (node?.tagName === "VIDEO") node.pause();
    node = el;
    rendered = true;
    stage.replaceChildren(el);
    stage.style.background = current.background;
    const ratio = current.orientation === "PORTRAIT" ? 9 / 16 : 16 / 9;
    stage.style.width = `min(100vw, ${100 * ratio}vh)`;
    stage.style.height = `min(100vh, ${100 / ratio}vw)`;
    waiting.hidden = true;
    deadline = performance.now() + item.seconds * 1000;
    void ack();
  };
  const failed = () => {
    rendered = false;
    waiting.hidden = false;
    waiting.textContent = "素材播放失败，请检查播放器设置。";
    deadline = performance.now() + 3000;
    void ack("MEDIA_PLAYBACK_FAILED");
  };
  deadline = performance.now() + Math.max(10000, item.seconds * 1000);
  if (item.assetId === undefined) {
    el.textContent = item.text;
    commit();
  } else if (item.kind === "IMAGE") {
    el.alt = "";
    el.addEventListener("load", commit, { once: true });
    el.addEventListener("error", failed, { once: true });
    el.src = item.url;
  } else {
    el.muted = true;
    el.playsInline = true;
    el.preload = "auto";
    el.addEventListener("error", failed, { once: true });
    el.addEventListener("playing", commit, { once: true });
    el.src = item.url;
    el.play().catch(failed);
    el.addEventListener(
      "ended",
      () => {
        if (release?.id === current.id && index === at) next();
      },
      { once: true },
    );
  }
}
function next() {
  if (!release) return;
  index = (index + 1) % release.items.length;
  show();
}
function stop(message) {
  if (node?.tagName === "VIDEO") node.pause();
  stage.replaceChildren();
  node = null;
  release = null;
  rendered = false;
  waiting.hidden = false;
  waiting.textContent = message;
}
/** Poll the local lease and schedule even while the CMS is offline. https://www.zhuatech.cn/; WeChat zhuatech / zhuatech2. */
async function poll() {
  if (loading) return;
  loading = true;
  try {
    const r = await fetch("/local/program");
    if (!r.ok) throw Error();
    const data = await r.json();
    if (!csrf) csrf = (await (await fetch("/local/status")).json()).csrf;
    if (!data.release) {
      const message =
        data.status.error === "OFFLINE_LEASE_EXPIRED"
          ? "离线授权已到期，请联网同步。"
          : data.status.error === "CLOCK_INVALID"
            ? "播放器时间异常，请联网校正。"
            : data.status.error === "DEVICE_REVOKED"
              ? "屏幕已停用或令牌已失效。"
              : "等待节目";
      stop(message);
    } else if (data.release.id !== release?.id) {
      release = data.release;
      index = 0;
      show();
    } else if (performance.now() >= deadline) next();
    if (release && rendered && performance.now() - ackAt > 10000) {
      ackAt = performance.now();
      void ack();
    }
  } catch {
    stop("本机播放器服务不可达。");
  } finally {
    loading = false;
  }
}
document
  .querySelector("#fullscreen")
  .addEventListener("click", () =>
    document.documentElement.requestFullscreen().catch(() => {}),
  );
void poll();
setInterval(poll, 1000);
