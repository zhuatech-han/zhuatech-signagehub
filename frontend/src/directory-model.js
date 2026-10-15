// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { ref } from "vue";

/** 后台目录读取隔离：旧请求不能覆盖新目录，失败时禁止使用不完整表单。https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function createDirectoryModel(request, onError, onRecovered) {
  const rows = ref([]);
  const options = ref({ roles: [], departments: [] });
  const ready = ref(false);
  const available = ref(false);
  let generation = 0;
  let disposed = false;
  async function load(kind) {
    if (disposed) return false;
    const current = ++generation;
    ready.value = false;
    available.value = false;
    rows.value = [];
    options.value = { roles: [], departments: [] };
    try {
      const records = await request("/admin/" + kind);
      const metadata =
        kind === "users" ? await request("/admin-options") : null;
      if (disposed || current !== generation) return false;
      rows.value = records;
      if (metadata) options.value = metadata;
      available.value = true;
      onRecovered?.();
      return true;
    } catch (error) {
      if (!disposed && current === generation) onError?.(error);
      return false;
    } finally {
      if (!disposed && current === generation) ready.value = true;
    }
  }
  function dispose() {
    disposed = true;
    generation++;
  }
  return { rows, options, ready, available, load, dispose };
}
