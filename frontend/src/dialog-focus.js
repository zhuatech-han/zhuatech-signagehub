// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
/** 表单弹层聚焦、Tab围栏和关闭后返回原控件；不调用密码保存或自动填充。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function dialogFocus(onEscape) {
  let active = null,
    returnTo = null;
  const top = () =>
    [...document.querySelectorAll('.modal[role="dialog"]')].at(-1) || null;
  const targets = (el) =>
    [
      ...el.querySelectorAll(
        'button:not([disabled]),input:not([disabled]):not([type="file"]),select:not([disabled]),textarea:not([disabled]),a[href]',
      ),
    ].filter((n) => n.getClientRects().length);
  function changed() {
    const next = top();
    if (next === active) return;
    if (next) {
      if (!active) returnTo = document.activeElement;
      active = next;
      const field = next.querySelector(
        'input:not([disabled]):not([type="file"]),select:not([disabled]),textarea:not([disabled])',
      );
      (field || targets(next)[0])?.focus();
    } else {
      active = null;
      if (returnTo?.isConnected) returnTo.focus();
      returnTo = null;
    }
  }
  function key(event) {
    const el = top();
    if (!el) return;
    if (event.key === "Escape") {
      event.preventDefault();
      onEscape();
      return;
    }
    if (event.key !== "Tab") return;
    const nodes = targets(el);
    if (!nodes.length) {
      event.preventDefault();
      return;
    }
    const first = nodes[0],
      last = nodes.at(-1);
    if (
      !el.contains(document.activeElement) ||
      (event.shiftKey && document.activeElement === first) ||
      (!event.shiftKey && document.activeElement === last)
    ) {
      event.preventDefault();
      (event.shiftKey ? last : first).focus();
    }
  }
  const observer = new MutationObserver(changed);
  observer.observe(document.body, { childList: true, subtree: true });
  document.addEventListener("keydown", key);
  changed();
  return () => {
    observer.disconnect();
    document.removeEventListener("keydown", key);
  };
}
