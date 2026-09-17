import { defineConfig } from "eslint/config";
import tseslint from "typescript-eslint";
import eslintConfigPrettier from "eslint-config-prettier";

export default defineConfig(
  { ignores: ["node_modules/", "coverage/", "dist/"] },
  tseslint.configs.recommended,
  {
    rules: {
      // 分割代入で一部のプロパティを取り除き、残りを使う書き方を許す
      "@typescript-eslint/no-unused-vars": [
        "error",
        { ignoreRestSiblings: true },
      ],
    },
  },
  eslintConfigPrettier,
);
