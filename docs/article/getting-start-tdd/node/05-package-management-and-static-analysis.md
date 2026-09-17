---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "前章では Conventional Commits によるコミットメッセージの規約を学びました。"
tags: [article, getting-start-tdd, node]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前章では Conventional Commits によるコミットメッセージの規約を学びました。この章では、**パッケージ管理** と **静的コード解析** を導入し、コードの品質を自動でチェックできるようにします。

## 5.2 npm によるパッケージ管理

### npm とは

> npm（Node Package Manager）は、Node.js のパッケージマネージャです。`package.json` で依存関係を管理し、プロジェクトのビルドプロセスを自動化できます。

第 1 部で Vitest と TypeScript はすでに導入しています。ここでは、品質管理ツールのための依存関係を追加します。

### package.json の更新

品質管理ツールを追加した `package.json` は以下のようになります。

```json
{
  "name": "fizzbuzz",
  "version": "0.1.0",
  "description": "FizzBuzz TDD project for Node (JS/TS)",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "vitest run",
    "test:watch": "vitest",
    "test:coverage": "vitest run --coverage",
    "lint": "eslint src/ test/",
    "lint:fix": "eslint src/ test/ --fix",
    "format": "prettier --write \"src/**/*.ts\" \"test/**/*.ts\"",
    "format:check": "prettier --check \"src/**/*.ts\" \"test/**/*.ts\"",
    "typecheck": "tsc --noEmit",
    "check": "npm run format:check && npm run lint && npm run typecheck && npm test",
    "fix": "npm run format && npm run lint:fix",
    "setup": "npm install && npm run check"
  },
  "devDependencies": {
    "typescript": "^5.7",
    "@types/node": "^24.0",
    "vitest": "^3.2",
    "@vitest/coverage-v8": "^3.2",
    "eslint": "^9.0",
    "@typescript-eslint/parser": "^8.0",
    "@typescript-eslint/eslint-plugin": "^8.0",
    "eslint-config-prettier": "^10.0",
    "prettier": "^3.4"
  }
}
```

依存関係をインストールします。

```bash
$ npm install
```

### devDependencies の解説

| パッケージ | 用途 |
|-----------|------|
| `typescript` | TypeScript コンパイラ |
| `@types/node` | Node.js の型定義 |
| `vitest` | テスティングフレームワーク |
| `@vitest/coverage-v8` | コードカバレッジ（V8 ベース） |
| `eslint` | 静的コード解析 |
| `@typescript-eslint/parser` | TypeScript 用 ESLint パーサー |
| `@typescript-eslint/eslint-plugin` | TypeScript 用 ESLint ルール |
| `eslint-config-prettier` | ESLint と Prettier の競合回避 |
| `prettier` | コードフォーマッター |

## 5.3 静的コード解析（ESLint）

### ESLint とは

> ESLint は JavaScript / TypeScript のための静的解析ツールです。コードの問題を自動的に検出し、一部は自動修正できます。

Java の Checkstyle や PMD に相当するツールです。

### eslint.config.mjs の設定

ESLint v9 では **フラットコンフィグ** 形式を使用します。

```javascript
// eslint.config.mjs
import tseslint from "@typescript-eslint/eslint-plugin";
import tsparser from "@typescript-eslint/parser";
import eslintConfigPrettier from "eslint-config-prettier";

export default [
  {
    files: ["src/**/*.ts", "test/**/*.ts"],
    languageOptions: {
      parser: tsparser,
      parserOptions: {
        ecmaVersion: "latest",
        sourceType: "module",
      },
    },
    plugins: {
      "@typescript-eslint": tseslint,
    },
    rules: {
      ...tseslint.configs.recommended.rules,
      "no-console": "off",
      "@typescript-eslint/no-unused-vars": "warn",
      complexity: ["error", { max: 7 }],
    },
  },
  eslintConfigPrettier,
];
```

### 循環的複雑度の制限

`complexity: ["error", { max: 7 }]` は **循環的複雑度**（Cyclomatic Complexity）を 7 以下に制限するルールです。

Java の PMD で設定した `CyclomaticComplexity` や Python の Ruff で設定した `max-complexity` と同じ基準です。複雑度が高いメソッドは分割を検討しましょう。

### ESLint の実行

```bash
# 解析の実行
$ npx eslint src/ test/

# 自動修正
$ npx eslint src/ test/ --fix
```

## 5.4 コードフォーマッター（Prettier）

### Prettier とは

> Prettier はコードフォーマッターです。コードの見た目を統一し、スタイルに関する議論をなくします。

Java の Checkstyle（フォーマットルール）や Python の Ruff（フォーマッター機能）に相当します。

### .prettierrc の設定

```json
{
  "semi": true,
  "trailingComma": "all",
  "singleQuote": false,
  "printWidth": 80,
  "tabWidth": 2
}
```

| 設定 | 値 | 説明 |
|------|-----|------|
| `semi` | `true` | 文末にセミコロンを付ける |
| `trailingComma` | `"all"` | 末尾カンマを付ける |
| `singleQuote` | `false` | ダブルクォートを使用 |
| `printWidth` | `80` | 1 行の最大文字数 |
| `tabWidth` | `2` | インデント幅 |

### Prettier の実行

```bash
# フォーマットチェック
$ npx prettier --check "src/**/*.ts" "test/**/*.ts"

# 自動フォーマット
$ npx prettier --write "src/**/*.ts" "test/**/*.ts"
```

## 5.5 TypeScript コンパイラ（型チェック）

### 型チェックの重要性

TypeScript の大きな利点は **静的型付け** です。`tsc --noEmit` で型チェックのみを実行し、型エラーを早期に検出できます。

Java のコンパイルチェックや Python の mypy に相当します。

### tsconfig.json

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ES2022",
    "moduleResolution": "bundler",
    "lib": ["ES2022"],
    "outDir": "./dist",
    "rootDir": ".",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "resolveJsonModule": true,
    "declaration": true,
    "declarationMap": true,
    "sourceMap": true
  },
  "include": ["src/**/*.ts", "test/**/*.ts"],
  "exclude": ["node_modules", "dist"]
}
```

`"strict": true` により、以下の厳格な型チェックが有効になります。

- `strictNullChecks` — null / undefined の厳格なチェック
- `noImplicitAny` — 暗黙の any を禁止
- `strictFunctionTypes` — 関数型の厳格なチェック

### 型チェックの実行

```bash
$ npx tsc --noEmit
```

## 5.6 コードカバレッジ

### Vitest のカバレッジ機能

Vitest には V8 ベースのカバレッジ機能が組み込まれています。Java の JaCoCo や Python の pytest-cov に相当します。

### vitest.config.ts の設定

```typescript
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    globals: false,
    environment: "node",
    include: ["test/**/*.test.ts"],
    coverage: {
      provider: "v8",
      reporter: ["text", "text-summary"],
      reportsDirectory: "coverage",
      exclude: [
        "node_modules/**",
        "dist/**",
        "**/*.test.ts",
        "**/*.config.*",
        "src/index.ts",
        "gulpfile.js",
      ],
      thresholds: {
        global: {
          branches: 80,
          functions: 80,
          lines: 80,
          statements: 80,
        },
      },
    },
  },
});
```

`thresholds` でカバレッジの最低基準を設定しています。80% を下回ると CI が失敗します。

### カバレッジの実行

```bash
$ npm run test:coverage
```

```
 ✓ test/fizzbuzz.test.ts (6 tests) 5ms

 % Coverage report from v8
-------------|---------|----------|---------|---------|-------------------
File         | % Stmts | % Branch | % Funcs | % Lines | Uncovered Line #s
-------------|---------|----------|---------|---------|-------------------
All files    |   83.33 |      100 |   66.66 |   83.33 |
 fizzbuzz.ts |   83.33 |      100 |   66.66 |   83.33 | 23-26
-------------|---------|----------|---------|---------|-------------------
```

`printFizzBuzz` メソッド（23-26 行目）が未カバーですが、これは学習用テストのため想定通りです。

## 5.7 品質チェックの一括実行

すべての品質チェックを一括で実行する `check` スクリプトを定義しています。

```bash
$ npm run check
```

このコマンドは以下を順番に実行します。

1. `format:check` — Prettier のフォーマットチェック
2. `lint` — ESLint の静的解析
3. `typecheck` — TypeScript の型チェック
4. `test` — Vitest のテスト実行

### 各言語の品質ツール比較

| 用途 | TypeScript | Java | Python |
|------|-----------|------|--------|
| パッケージ管理 | npm | Gradle | uv |
| テスト | Vitest | JUnit 5 | pytest |
| 静的解析 | ESLint | Checkstyle + PMD | Ruff |
| フォーマッター | Prettier | Checkstyle | Ruff |
| 型チェック | tsc | javac（暗黙） | mypy |
| カバレッジ | @vitest/coverage-v8 | JaCoCo | pytest-cov |
| 複雑度チェック | ESLint complexity | PMD | Ruff McCabe |

## 5.8 まとめ

この章では、以下の品質管理ツールを導入しました。

1. **npm** — パッケージ管理と依存関係の管理
2. **ESLint** — 静的コード解析（循環的複雑度の制限を含む）
3. **Prettier** — コードフォーマットの統一
4. **TypeScript コンパイラ** — 型チェックによるエラーの早期検出
5. **Vitest カバレッジ** — コードカバレッジの計測（80% 閾値）

次の章では、タスクランナーを導入してこれらの品質チェックを自動化し、CI/CD パイプラインを構築します。
