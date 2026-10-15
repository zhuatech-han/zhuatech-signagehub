// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import globals from "globals";
export default [
  {
    files: ["**/*.js"],
    languageOptions: { globals: { ...globals.node, ...globals.browser } },
    rules: { "no-unused-vars": "error", "no-undef": "error" },
  },
];
