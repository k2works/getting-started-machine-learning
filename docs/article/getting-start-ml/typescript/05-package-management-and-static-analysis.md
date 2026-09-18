---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "npm の save-exact・package-lock.json・npm ci と engine-strict による版の固定、tsc の strict 系オプションと型宣言の自作、ESLint・Prettier による静的解析、@vitest/coverage-v8 のカバレッジと npm audit を学ぶ。"
tags: [article,getting-start-ml,typescript]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-18T00:45:55Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前の章では、実験を再現するには乱数のシードとアルゴリズムに加えて、ライブラリと Node.js のバージョンを固定する必要があると述べました。この章では、それを担う npm の仕組みと、コードを実行せずに問題を見つける **静的解析**、テストがコードのどこを通ったかを測る **カバレッジ** を整えます。

| 道具 | 役割 | 本章の節 |
|------|------|---------|
| [npm](https://docs.npmjs.com/)（`package.json`・`package-lock.json`・`.npmrc`） | 依存ライブラリの管理 | 5.2 |
| `.nvmrc`・`engines` | Node.js のバージョンの指定 | 5.3 |
| [TypeScript](https://www.typescriptlang.org/) のコンパイラ（`tsc`） | 型チェック | 5.4 |
| [ESLint](https://eslint.org/)・[typescript-eslint](https://typescript-eslint.io/) | コードの問題の検査 | 5.5 |
| [Prettier](https://prettier.io/) | コードの整形 | 5.6 |
| [@vitest/coverage-v8](https://vitest.dev/guide/coverage) | テストのカバレッジ計測 | 5.7 |
| `npm audit` | 依存ライブラリの既知の脆弱性の検査 | 5.8 |

本章のバージョンは、執筆時点の `apps/node/package.json` に書かれたものです（TypeScript 6.0.3、ESLint 10.10.0、typescript-eslint 8.70.0、Prettier 3.9.7、Vitest 5.0.1）。選定の理由は [ADR 003](../../../adr/003-typescript-ml-libraries.md) を参照してください。

[Python 版の第 5 章](../python/05-package-management-and-static-analysis.md) の uv・Ruff・mypy・pytest-cov、[Kotlin 版の第 5 章](../kotlin/05-package-management-and-static-analysis.md) の Gradle・ktlint・detekt・Kover に当たるものを、TypeScript 版では npm と、npm で入れる開発用のパッケージで組み立てます。

## 5.2 npm によるパッケージ管理

### package.json

npm の設定の中心は `apps/node/package.json` です。第 7 章以降で使うライブラリまで入れた、執筆時点の内容です。

```json
{
  "name": "getting-started-ml",
  "version": "0.1.0",
  "description": "機械学習から始めるプログラミング入門（TypeScript 版）",
  "private": true,
  "type": "module",
  "engines": {
    "node": ">=22.13.0"
  },
  "scripts": {
    "test": "vitest run",
    "test:coverage": "vitest run --coverage",
    "lint": "eslint .",
    "format": "prettier --write .",
    "format:check": "prettier --check .",
    "typecheck": "tsc --noEmit",
    "check": "npm run format:check && npm run lint && npm run typecheck && npm test"
  },
  "devDependencies": {
    "@types/node": "22.20.3",
    "@vitest/coverage-v8": "5.0.1",
    "eslint": "10.10.0",
    "eslint-config-prettier": "10.1.8",
    "prettier": "3.9.7",
    "typescript": "6.0.3",
    "typescript-eslint": "8.70.0",
    "vitest": "5.0.1"
  },
  "dependencies": {
    "@hono/node-server": "2.1.1",
    "csv-parse": "7.0.2",
    "hono": "4.13.8",
    "ml-cart": "2.1.1",
    "ml-confusion-matrix": "2.0.0",
    "ml-cross-validation": "1.3.0",
    "ml-kmeans": "7.0.1",
    "ml-logistic-regression": "2.0.0",
    "ml-matrix": "6.15.0",
    "ml-pca": "4.1.1",
    "ml-random-forest": "2.1.0",
    "ml-regression-lasso": "0.1.2",
    "ml-regression-multivariate-linear": "2.0.4",
    "zod": "4.6.5"
  }
}
```

| 項目 | 意味 |
|------|------|
| `private: true` | npm のレジストリに誤って公開しないようにする |
| `type: "module"` | `.js`・`.ts` のファイルを ES モジュール（`import`・`export`）として扱う |
| `engines` | 動作する Node.js の版（5.3 節） |
| `scripts` | `npm run <名前>` で実行するコマンド（第 6 章） |
| `dependencies`・`devDependencies` | 依存ライブラリ（次の項） |

### 本番依存と開発依存

依存ライブラリは、使いみちで 2 つに分けています。

| 区分 | 中身 | 判断の基準 |
|------|------|----------|
| `dependencies` | csv-parse・ml.js 系・Hono・zod | `src` のコードが実行時に `import` するもの |
| `devDependencies` | TypeScript・Vitest・ESLint・Prettier・`@types/node` | 型チェック・テスト・整形など、開発のときだけ使うもの |

TypeScript そのものも `devDependencies` です。Node.js 22 は型の注釈を取り除いて `.ts` を直接実行するので、プログラムを動かすだけなら TypeScript のコンパイラは要りません。`@types/node` は Node.js の API（`node:fs` など）の型定義で、型チェックのときだけ使います。

`npm ls --depth=0` で、直接の依存関係を一覧できます。

```bash
npm ls --depth=0
```

```text
getting-started-ml@0.1.0 ...\apps\node
+-- @hono/node-server@2.1.1
+-- @types/node@22.20.3
+-- @vitest/coverage-v8@5.0.1
+-- csv-parse@7.0.2
+-- eslint-config-prettier@10.1.8
+-- eslint@10.10.0
+-- hono@4.13.8
+-- ml-cart@2.1.1
+-- ml-confusion-matrix@2.0.0
+-- ml-cross-validation@1.3.0
+-- ml-kmeans@7.0.1
+-- ml-logistic-regression@2.0.0
+-- ml-matrix@6.15.0
+-- ml-pca@4.1.1
+-- ml-random-forest@2.1.0
+-- ml-regression-lasso@0.1.2
+-- ml-regression-multivariate-linear@2.0.4
+-- prettier@3.9.7
+-- typescript-eslint@8.70.0
+-- typescript@6.0.3
+-- vitest@5.0.1
`-- zod@4.6.5
```

### 正確な版を書く — .npmrc

`npm install` でライブラリを追加すると、npm は既定で `^7.0.2` のような **範囲** を `package.json` に書きます。`^7.0.2` は「7.0.2 以上 8.0.0 未満」の意味で、インストールした日によって入る版が変わりえます。本リポジトリでは、`apps/node/.npmrc` で正確な版を書くように設定しています。

```text
# package.json に範囲ではなく正確な版を書く
save-exact=true
engine-strict=true
```

第 3 章で ml-cart を追加したときは、次のように実行しました。

```bash
npm install ml-cart@2.1.1
```

`package.json` の差分は次のとおりで、`^` の付かない正確な版が書かれています。

```diff
   "dependencies": {
-    "csv-parse": "7.0.2"
+    "csv-parse": "7.0.2",
+    "ml-cart": "2.1.1"
   }
```

開発用のライブラリは `npm install --save-dev <名前>@<版>` で `devDependencies` に追加します。

### package-lock.json と npm ci

`package.json` に正確な版を書いても、依存ライブラリがさらに依存するライブラリ（推移的な依存）は範囲で指定されています。`package-lock.json` から ml-cart の記録を抜き出すと、次のようになっています。

```json
{
  "node_modules/ml-cart": {
    "version": "2.1.1",
    "resolved": "https://registry.npmjs.org/ml-cart/-/ml-cart-2.1.1.tgz",
    "integrity": "sha512-f6rIj4EzbjqKLJa2Qmm5AjZ0WVgk+Y7J1N/+pQVaFr0d4oM1uZPLOh5h665LyH+bLBHTFEbvSR4OLKmJRQ8KfA==",
    "license": "MIT",
    "dependencies": {
      "ml-array-mean": "^1.1.5",
      "ml-matrix": "^6.8.2"
    }
  }
}
```

- `resolved` はダウンロード元、`integrity` はダウンロードしたファイルのハッシュです。ハッシュが一致しなければインストールは失敗するので、配布物が途中で差し替えられていないことを確かめられます
- ml-cart は ml-matrix を `^6.8.2` という範囲で求めています。どの版を入れたかは、`package-lock.json` の `node_modules/ml-matrix` の記録で決まります

`npm ls` で調べると、ml-cart を含む 8 つのパッケージが求める ml-matrix は、すべて本リポジトリが直接入れた 6.15.0 にまとめられていました（`deduped`）。

```bash
npm ls ml-matrix
```

```text
getting-started-ml@0.1.0 ...\apps\node
+-- ml-cart@2.1.1
| `-- ml-matrix@6.15.0 deduped
+-- ml-kmeans@7.0.1
| +-- ml-matrix@6.15.0 deduped
| `-- ml-spectra-processing@14.35.1
|   `-- ml-matrix@6.15.0 deduped
+-- ml-logistic-regression@2.0.0
| `-- ml-matrix@6.15.0 deduped
+-- ml-matrix@6.15.0
+-- ml-pca@4.1.1
| `-- ml-matrix@6.15.0 deduped
+-- ml-random-forest@2.1.0
| `-- ml-matrix@6.15.0 deduped
+-- ml-regression-lasso@0.1.2
| `-- ml-matrix@6.15.0 deduped
`-- ml-regression-multivariate-linear@2.0.4
  `-- ml-matrix@6.15.0 deduped
```

`package-lock.json` があれば、推移的な依存まで同じ版を入れられます。Python 版の `uv.lock` と同じ役割です。Kotlin 版では、依存関係の依存関係の版が公開済みの POM に書かれていて後から変わらないので、ロックファイルを使いませんでした。npm の依存関係は範囲で書かれるのが普通なので、TypeScript 版では `package-lock.json` をコミットします。

依存関係を入れるときは、`npm install` ではなく `npm ci` を使います。

| コマンド | 振る舞い |
|---------|---------|
| `npm install` | `package.json` の範囲に合う版を解決し、必要なら `package-lock.json` を書き換える |
| `npm ci` | `node_modules` を消してから、`package-lock.json` のとおりに入れる。`package-lock.json` は書き換えない |

`npm ci` は、`package.json` と `package-lock.json` が食い違っていると失敗します。試しに、スクラッチのディレクトリに `package.json`・`package-lock.json`・`.npmrc` を写し、`package.json` の csv-parse だけを 7.0.1 に書き換えて `npm ci` を実行しました。

```text
npm error code EUSAGE
npm error
npm error `npm ci` can only install packages when your package.json and package-lock.json or npm-shrinkwrap.json are in sync. Please update your lock file with `npm install` before continuing.
npm error
npm error Invalid: lock file's csv-parse@7.0.2 does not satisfy csv-parse@7.0.1
```

`package.json` だけを書き換えてコミットしてしまっても、CI の `npm ci` がこの食い違いを見つけます。

## 5.3 Node.js のバージョンを固定する

TypeScript のプログラムは Node.js の上で動きます。Python 版で `.python-version` を、Kotlin 版で JDK ツールチェーンを使ったのと同じように、Node.js の版も指定します。

### .nvmrc と engines

| ファイル | 中身 | 使われる場面 |
|---------|------|------------|
| `apps/node/.nvmrc` | `22` | nvm などのバージョン管理ツールが、使う Node.js を選ぶ |
| `package.json` の `engines` | `"node": ">=22.13.0"` | npm が、今の Node.js で動くかを確かめる |
| `ops/nix/environments/node/shell.nix` | `nodejs_22` | Nix の環境（CI を含む）に入れる Node.js（第 6 章） |

`.nvmrc` と `engines` は、どちらも「どの版を使うか」を宣言するだけで、npm は既定では `engines` に合わなくても警告を出すだけです。`.npmrc` の `engine-strict=true` を指定すると、警告ではなくエラーにしてインストールをやめます。

### engine-strict が見つけた下限の誤り

第 1 章でプロジェクトを作ったときは、Vitest 5 の `engines`（`^22.12.0 || ^24.0.0 || >=26.0.0`）に合わせて、`engines` を `>=22.12.0` にしていました。ところが、この章を書くために依存ライブラリの `engines` を確かめたところ、ESLint 10 とその関連パッケージは `^20.19.0 || ^22.13.0 || >=24` を求めていました。

実際に Node.js 22.12.0 で `npm ci` を実行すると、`engine-strict=true` のもとでは、ESLint の関連パッケージのところで失敗しました。

```text
npm error code EBADENGINE
npm error engine Unsupported engine
npm error engine Not compatible with your version of node/npm: @eslint/config-array@0.23.5
npm error notsup Not compatible with your version of node/npm: @eslint/config-array@0.23.5
npm error notsup Required: {"node":"^20.19.0 || ^22.13.0 || >=24"}
npm error notsup Actual:   {"npm":"10.9.3","node":"v22.12.0"}
```

`package.json` の `engines` は「22.12.0 で動く」と宣言していたのに、依存関係は 22.13.0 以上を求めていたわけです。`package-lock.json` に記録されたすべてのパッケージの `engines` を満たす 22 系の最小の版を調べると 22.13.0 だったので、`engines` を `>=22.13.0` に上げました（`package-lock.json` の先頭にも同じ値が記録されるので、あわせて更新しています）。上げたあとで Node.js 22.12.0 を使うと、今度はプロジェクト自身の `engines` で止まります。

```text
npm error code EBADENGINE
npm error engine Unsupported engine
npm error engine Not compatible with your version of node/npm: getting-started-ml@0.1.0
npm error notsup Not compatible with your version of node/npm: getting-started-ml@0.1.0
npm error notsup Required: {"node":">=22.13.0"}
npm error notsup Actual:   {"npm":"10.9.3","node":"v22.12.0"}
```

Node.js 22.13.0 では `npm ci` が成功しました。どちらのエラーでもインストールは止まりますが、プロジェクトの `engines` で止まるほうが、何を入れればよいかが最初の 1 行で分かります。手元（22.18.0）と CI（22.21.1）はどちらも条件を満たしていたので、この誤りはこれまで表に出ていませんでした。

## 5.4 型チェック — TypeScript コンパイラ

### tsconfig.json

Python 版では型ヒントを mypy で検査し、Kotlin 版ではコンパイラが型を検査しました。TypeScript 版では TypeScript のコンパイラ（`tsc`）が型を検査します。設定は `apps/node/tsconfig.json` です。

```json
{
  "compilerOptions": {
    "target": "ES2023",
    "lib": ["ES2023"],
    "module": "nodenext",
    "moduleResolution": "nodenext",
    "types": ["node"],
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "noEmit": true,
    "allowImportingTsExtensions": true,
    "erasableSyntaxOnly": true,
    "verbatimModuleSyntax": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true
  },
  "include": ["src/**/*.ts", "test/**/*.ts", "*.config.ts"]
}
```

設定は、型チェックを厳しくするものと、Node.js で `.ts` を直接実行するためのものに分かれます。

| 設定 | 役割 |
|------|------|
| `strict` | `null`・`undefined` の検査（`strictNullChecks`）や、暗黙の `any` の禁止（`noImplicitAny`）など、厳しい検査をまとめて有効にする |
| `noUncheckedIndexedAccess` | 配列やオブジェクトの添字アクセスの結果に `undefined` を含める。`strict` には含まれない |
| `noEmit` | JavaScript を出力せず、型チェックだけをする |
| `allowImportingTsExtensions` | `import ... from "./random.ts"` のように `.ts` の拡張子で読み込めるようにする。Node.js は拡張子を補わないので必要 |
| `erasableSyntaxOnly` | 型を消すだけでは JavaScript にならない構文（`enum`・コンストラクターの引数プロパティなど）を禁止する。Node.js の型除去はこれらを扱えない |
| `verbatimModuleSyntax` | 型だけの読み込みに `import type` を書かせる。型除去で `import` ごと消してよいかを、書いたとおりに判断できるようにする |

`noUncheckedIndexedAccess` の効果を確かめます。配列の先頭を返すだけの関数を書いて `tsc` を実行すると、エラーになります。

```typescript
export function first(values: number[]): number {
  return values[0];
}
```

```text
src/lint-demo.ts(2,3): error TS2322: Type 'number | undefined' is not assignable to type 'number'.
  Type 'undefined' is not assignable to type 'number'.
```

空の配列なら `values[0]` は `undefined` です。この設定があると、要素があることを確かめるか、`as number` で「必ずある」と明示するまで、`number` として使えません。第 2 章の `shuffle` の `result[j] as T` は、添字が範囲内であることを人が保証した箇所です。

### Vitest は型を検査しない

第 1 部で何度か見たとおり、Vitest は型を検査しません。Vitest は型の注釈を取り除いてから実行するだけなので、型が合わないコードもテストとしては実行されます。第 3 章では、コンストラクターに引数を追加し忘れたコードが、Vitest では「予測が違う」という失敗として現れ、`tsc` では「引数の数が違う」というエラーとして現れました。

```text
test/chapter03/decision-tree.test.ts(94,36): error TS2554: Expected 0 arguments, but got 1.
```

そのため、テスト（`npm test`）と型チェック（`npm run typecheck`）を別々に実行し、第 6 章で `npm run check` にまとめています。

### 型定義を同梱しないパッケージ

ml.js のパッケージのうち、ml-cart・ml-logistic-regression・ml-cross-validation・ml-regression-lasso は型定義を同梱していません（[ADR 003](../../../adr/003-typescript-ml-libraries.md)）。第 3 章で書いた型宣言 `src/types/ml-cart.d.ts` を一時的に外して `tsc` を実行すると、次のエラーになりました（パスの前半は省略しています）。

```text
src/chapter03/ml-cart-adapter.ts(1,40): error TS7016: Could not find a declaration file for module 'ml-cart'. '.../apps/node/node_modules/ml-cart/cart.js' implicitly has an 'any' type.
  Try `npm i --save-dev @types/ml-cart` if it exists or add a new declaration (.d.ts) file containing `declare module 'ml-cart';`
src/chapter03/ml-cart-adapter.ts(31,45): error TS7006: Parameter 'index' implicitly has an 'any' type.
test/chapter03/ml-cart-adapter.test.ts(1,40): error TS7016: Could not find a declaration file for module 'ml-cart'. '.../apps/node/node_modules/ml-cart/cart.js' implicitly has an 'any' type.
  Try `npm i --save-dev @types/ml-cart` if it exists or add a new declaration (.d.ts) file containing `declare module 'ml-cart';`
```

エラーメッセージは 2 つの方法を示しています。

1. **`@types/ml-cart` を入れる** — コミュニティが型定義を公開していれば、それを使います。`npm view @types/ml-cart` を実行すると `404` で、公開されていませんでした
2. **型宣言のファイルを書く** — `declare module 'ml-cart';` とだけ書けばエラーは消えますが、ml-cart のすべてが `any` になり、型チェックが効かなくなります

本リポジトリでは、使う範囲だけを宣言したファイルを書いています。

```typescript
// ml-cart は型定義を同梱しないので、本リポジトリで使う範囲だけを宣言する
declare module "ml-cart" {
  export interface DecisionTreeClassifierOptions {
    /** 分割の基準。ml-cart 2.1.1 は "gini" だけ */
    gainFunction?: "gini";
    /** 境界の決め方。ml-cart 2.1.1 は隣り合う値の平均（"mean"）だけ */
    splitFunction?: "mean";
    /** 件数がこの値以下になったら分割せずに葉にする（既定値 3） */
    minNumSamples?: number;
    maxDepth?: number;
    /** 利得がこの値以下なら分割しない（既定値 0.01） */
    gainThreshold?: number;
  }

  export class DecisionTreeClassifier {
    constructor(options?: DecisionTreeClassifierOptions);
    /** 正解ラベルは 0 始まりの整数 */
    train(trainingSet: number[][], trainingLabels: number[]): void;
    predict(toPredict: number[][]): number[];
  }
}
```

自分で書いた型宣言は、ライブラリの実際の振る舞いと合っている保証がありません。型が「正しい」と言っていても、実行時には違うことがありえます。第 3 章で、ml-cart のアダプターを学習用テストで確かめたのはそのためです。型宣言のコメントに書いた既定値や制約も、そのテストで確かめた事実です。

## 5.5 静的解析 — ESLint

### ESLint の設定

コードの問題の検査には、ESLint と、ESLint で TypeScript を扱うための typescript-eslint を使います。設定は `apps/node/eslint.config.mjs` です。ESLint 9 以降の形式（flat config）で、設定を配列として並べます。

```javascript
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
```

| 要素 | 役割 |
|------|------|
| `ignores` | 検査しないディレクトリ |
| `tseslint.configs.recommended` | typescript-eslint の推奨ルール |
| `rules` | 推奨ルールの一部を本リポジトリに合わせて変える |
| `eslintConfigPrettier` | 書式に関するルールを無効にし、書式を Prettier に任せる。最後に置いて、それまでの設定を上書きする |

### ESLint の実行

検査の効果を確かめるため、問題を 2 つ含むファイルを書いて検査しました。

```typescript
export function mean(values: any[]): number {
  const count = values.length;
  let total = 0;
  for (const value of values) total += value;
  return total / values.length;
}

export function withoutLabel(row: { label: string; x: number }) {
  const { label, ...rest } = row;
  return rest;
}
```

```bash
npx eslint src/lint-demo.ts
```

先頭のファイルのパスの行は省略しています。

```text
  1:30  error  Unexpected any. Specify a different type    @typescript-eslint/no-explicit-any
  2:9   error  'count' is assigned a value but never used  @typescript-eslint/no-unused-vars

✖ 2 problems (2 errors, 0 warnings)
```

- `any` は型チェックを無効にするので、`no-explicit-any` が禁止します。型の分からない値には `unknown` を使い、確かめてから使います
- 使っていない変数 `count` は、消し忘れか、使い忘れのどちらかです

一方、`withoutLabel` の `label` は指摘されていません。`label` も使っていない変数ですが、分割代入で `label` を取り除き、残り（`rest`）を使うための書き方です。`ignoreRestSiblings: true` は、この書き方の変数を「使っていない」と見なさないようにする設定です。第 2 章でこの設定を加えるまでは、`_target` のような名前でも指摘されていました。

### 型チェックとの役割分担

`tsc` と ESLint は、どちらもコードを実行せずに問題を見つけますが、見るものが違います。

| 道具 | 見つけるもの | 例 |
|------|------------|-----|
| `tsc` | 型が合わないこと | `number \| undefined` を `number` として返す、引数の数が違う |
| ESLint | 型は合っているが、望ましくない書き方 | `any` の使用、使っていない変数 |

Kotlin 版の ktlint と detekt の関係に近く、TypeScript 版ではコンパイラが型を、ESLint が書き方を受け持ちます。

## 5.6 整形 — Prettier

### Prettier の設定

コードの書式は Prettier に任せます。設定ファイル `apps/node/.prettierrc` は `{}` で、既定の設定（1 行 80 文字、ダブルクォート、文末のセミコロンあり、改行コード LF など）をそのまま使います。整形しないファイルは `apps/node/.prettierignore` に書きます。

```text
node_modules/
coverage/
dist/
package-lock.json
```

`package-lock.json` は npm が書き出すファイルなので、Prettier で書式を変えないようにしています。

### Prettier の実行

セミコロンを省き、シングルクォートを使ったファイルを検査すると、書式の違いを報告します。

```typescript
export function mean(values: number[]): number {
  let total = 0
  for (const value of values) total += value
  return total / values.length
}

export const FEATURES = ['がく片長さ', 'がく片幅', '花弁長さ', '花弁幅'] as const;
```

```bash
npx prettier --check src/lint-demo.ts
```

```text
Checking formatting...
[warn] src/lint-demo.ts
[warn] Code style issues found in the above file. Run Prettier with --write to fix.
```

`npx prettier --write` で整形すると、次のようになります。

```typescript
export function mean(values: number[]): number {
  let total = 0;
  for (const value of values) total += value;
  return total / values.length;
}

export const FEATURES = [
  "がく片長さ",
  "がく片幅",
  "花弁長さ",
  "花弁幅",
] as const;
```

`FEATURES` の行は、日本語の文字を 2 文字分の幅として数えるので 80 文字を超え、1 行に 1 つずつに折り返されました。第 2 章の `FEATURES` が縦に並んでいるのはこのためです。

Prettier は書式だけを扱い、ESLint のような「使っていない変数」の検査はしません。逆に ESLint の書式のルールは `eslint-config-prettier` で無効にしているので、2 つの道具が同じ箇所で違う指示を出すことはありません。

## 5.7 コードカバレッジ — @vitest/coverage-v8

テストがプロダクションコードのどこを実行したかを、@vitest/coverage-v8 で計測します。V8（Node.js の JavaScript エンジン）が実行中に記録する情報を使うので、コードに計測用の処理を埋め込む必要がありません。設定は `apps/node/vitest.config.ts` です。

```typescript
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    include: ["test/**/*.test.ts"],
    coverage: {
      provider: "v8",
      include: ["src/**/*.ts"],
      reporter: ["text-summary"],
    },
  },
});
```

`coverage.include` で、計測の対象を `src` のコードに限っています。

```bash
npm run test:coverage
```

学習データを配置した環境での結果です。

```text
=============================== Coverage summary ===============================
Statements   : 96.92% ( 189/195 )
Branches     : 87.5% ( 63/72 )
Functions    : 100% ( 71/71 )
Lines        : 96.66% ( 174/180 )
================================================================================
```

学習データが無い環境での結果です。

```bash
ML_DATA_DIR=/nonexistent npm run test:coverage
```

```text
=============================== Coverage summary ===============================
Statements   : 82.05% ( 160/195 )
Branches     : 79.16% ( 57/72 )
Functions    : 91.54% ( 65/71 )
Lines        : 80.55% ( 145/180 )
================================================================================
```

データが無いと、実データのテストがスキップされ、各章の `main` 関数などが実行されないので、カバレッジが下がります。Python 版・Kotlin 版と同じく、カバレッジの数値を比べるときは、データのある環境で計測したものかを確認してください。

ファイルごとに、実行されなかった行を知りたいときは、レポーターを `text` に変えます。データのある環境での結果です。

```bash
npx vitest run --coverage --coverage.reporter=text
```

```text
-------------------|---------|----------|---------|---------|-------------------
File               | % Stmts | % Branch | % Funcs | % Lines | Uncovered Line #s 
-------------------|---------|----------|---------|---------|-------------------
All files          |   96.92 |     87.5 |     100 |   96.66 |                   
 src/chapter01     |   93.33 |       75 |     100 |   93.33 |                   
  ...o-takenoko.ts |   95.65 |    77.77 |     100 |   95.65 | 21                
  main.ts          |   85.71 |    66.66 |     100 |   85.71 | 22                
 src/chapter02     |   98.41 |     90.9 |     100 |    98.3 |                   
  main.ts          |   93.33 |    66.66 |     100 |   93.33 | 43                
 src/chapter03     |      97 |    89.13 |     100 |   96.62 |                   
  decision-tree.ts |    97.1 |    94.28 |     100 |   96.72 | 112-113           
  main.ts          |   94.73 |    71.42 |     100 |   94.73 | 40                
  ...rt-adapter.ts |     100 |       75 |     100 |     100 | 16                
-------------------|---------|----------|---------|---------|-------------------
```

実行されなかった行を見ると、テストが足りないのではなく、実行されないことに意味がある行だと分かります。

| 行 | 内容 | 実行されない理由 |
|----|------|----------------|
| `kinoko-takenoko.ts` の 21 行目 | 列が見つからないときの `throw` | 実データにも単体テストのデータにも、列の欠けた CSV が無い |
| 各章の `main.ts` の最後 | `if (import.meta.filename === process.argv[1])` の中の `main()` | テストは `main` を直接呼ぶので、`node src/chapter01/main.ts` で実行したときだけ通る |
| `decision-tree.ts` の 112〜113 行目 | `switch` の `default` の `never` | 第 3 章で見たとおり、型チェックで到達しないことを保証した行 |

本リポジトリでは、カバレッジの下限（`coverage.thresholds`）を設けていません。CI には学習データを置けないので、CI で計測したカバレッジは手元より必ず低くなり、下限を決めても手元と CI で意味が変わってしまうためです。レポートは `coverage/` に書き出され、第 4 章のとおり `.gitignore` で除外しています。

## 5.8 依存関係の脆弱性 — npm audit

依存ライブラリに既知の脆弱性が無いかは、`npm audit` で確かめます。npm のレジストリに登録された脆弱性の情報と、`package-lock.json` のすべてのパッケージの版を突き合わせます。

```bash
npm audit
```

```text
found 0 vulnerabilities
```

執筆時点では、推移的な依存を含めて既知の脆弱性はありませんでした。`npm install` と `npm ci` も、終わりに同じ検査の結果（`found 0 vulnerabilities`）を表示します。脆弱性が見つかったら、`npm audit` の表示する版に上げ、テストが通ることを確かめてからコミットします。

ml.js の多くのパッケージは 2022〜2023 年から更新されていません（[ADR 003](../../../adr/003-typescript-ml-libraries.md)）。更新の止まったパッケージに脆弱性が見つかると、修正版が出ない可能性があります。`npm audit` を定期的に実行し、必要なら代わりのパッケージや自作への切り替えを検討します。

## 5.9 まとめ

この章では、再現できる依存関係と、実行せずに問題を見つける仕組みを整えました。

1. **npm** — `dependencies` と `devDependencies` で使いみちを分け、`.npmrc` の `save-exact` で正確な版を書く。推移的な依存は範囲で指定されるので、`package-lock.json` をコミットし、`npm ci` で入れる
2. **Node.js の版** — `.nvmrc`・`engines`・Nix の環境で指定し、`engine-strict` で合わない版でのインストールを止める。`engines` の下限は、依存関係の `engines` をすべて満たす版にする
3. **型チェック** — `tsc` の `strict` と `noUncheckedIndexedAccess` で `undefined` を確かめさせる。Vitest は型を検査しない。型定義の無いパッケージには、使う範囲だけの型宣言を書き、学習用テストで裏付ける
4. **ESLint と Prettier** — ESLint が `any` や使っていない変数などの書き方を、Prettier が書式を受け持つ。`eslint-config-prettier` で両者がぶつからないようにする
5. **カバレッジと脆弱性** — @vitest/coverage-v8 で計測し、データが無い環境では数値が下がることに注意する。`npm audit` で依存ライブラリの既知の脆弱性を確かめる

次の章では、これらを npm scripts としてまとめ、GitHub Actions で自動実行します。
