# 機械学習から始める TypeScript 入門

TypeScript は JavaScript に静的な型を加えた言語で、構造的型付け・判別可能なユニオン・ジェネリクス・型の絞り込みを備えています。本シリーズでは、Python 版・Kotlin 版と同じ題材の機械学習のアルゴリズムを TDD で自作し、次に JavaScript の機械学習ライブラリ（ml.js 系）に置き換えて結果を突き合わせながら、TypeScript の書き方とエコシステムを学びます。

TypeScript は、Python や JVM に比べて機械学習のライブラリが成熟していない環境の代表として扱います。データフレームのライブラリを使わず、1 行を `interface` で型付けしたレコードの配列でデータを表し、ライブラリが無い部分は自作で埋めます。

## 特徴

- **構造的型付け**: 型の名前ではなく形で互換性を判断するので、テストでは `{ height: 161, weight: 52, ageGroup: 20 }` のようなオブジェクトリテラルをそのまま渡せる
- **型の絞り込み**: `strict` と `noUncheckedIndexedAccess` で、`undefined` になりうる値を確かめるまで使えないことを型チェックで保証する
- **型は実行時に消える**: 型チェックとテストの実行は別の工程で、外部から来るデータ（CSV・API の入力）は実行時に検証する
- **Node.js の型除去**: Node.js 22 は型の注釈を取り除いて `.ts` をそのまま実行できる

## Python 版・Kotlin 版との違い

分割の手順は Python 版・Kotlin 版と同じですが、TypeScript 版ではシード付きの乱数生成器を自作するので、訓練データとテストデータに入る行は一致しません。件数は一致しますが、正解率や係数などの数値は TypeScript 版の実装で実測した値を載せています。

Notebook による探索と可視化は、Python 版・Kotlin 版だけで扱います。可視化の節がある章では、各章の冒頭で Python 版・Kotlin 版の該当する節へ案内します。

## 開発環境

| ツール | 用途 |
|--------|------|
| [Node.js](https://nodejs.org/) 22 | 実行環境（型除去で `.ts` を直接実行） |
| [npm](https://docs.npmjs.com/) | パッケージ管理・タスクの実行（npm scripts） |
| [TypeScript](https://www.typescriptlang.org/) 6.0 | 型チェック（`tsc --noEmit`） |
| [Vitest](https://vitest.dev/) | テスティングフレームワーク・カバレッジ |
| [ESLint](https://eslint.org/)（typescript-eslint）・[Prettier](https://prettier.io/) | 静的解析・コードの整形 |

## ライブラリ

| ライブラリ | 用途 | 初出 |
|-----------|------|------|
| [csv-parse](https://csv.js.org/parse/) | CSV の読み込み | 第 2 章 |
| [ml.js](https://github.com/mljs) 系（ml-cart・ml-matrix・ml-kmeans・ml-pca など） | 機械学習（自作との突き合わせ） | 第 3 章 |
| [Hono](https://hono.dev/)・[zod](https://zod.dev/) | 予測 API と入力の検証 | 第 15 章 |

選定の理由と、章ごとにライブラリへ置き換えられる範囲は [ADR 003](../../../adr/003-typescript-ml-libraries.md) を参照してください。

## サンプルコード

サンプルコードは `apps/node/` にあります。学習データの配置方法は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。

```bash
cd apps/node
npm ci
npm run check
node src/chapter01/main.ts
```

## 章構成

### 第 1 部: 機械学習と TDD の基本サイクル

| 章 | テーマ |
|----|--------|
| [第 1 章](01-machine-learning-and-first-test.md) | 機械学習とはじめてのテスト |
| [第 2 章](02-data-preprocessing-and-triangulation.md) | データの前処理と三角測量 |
| [第 3 章](03-decision-tree-and-obvious-implementation.md) | 決定木による分類と明白な実装 |

### 第 2 部: 開発環境と自動化

| 章 | テーマ |
|----|--------|
| [第 4 章](04-version-control-and-data-management.md) | バージョン管理とデータ管理 |
| [第 5 章](05-package-management-and-static-analysis.md) | パッケージ管理と静的解析 |
| [第 6 章](06-task-runner-and-ci-cd.md) | タスクランナーと CI/CD |

### 第 3 部: 回帰と実践的な前処理

| 章 | テーマ |
|----|--------|
| [第 7 章](07-linear-regression.md) | 線形回帰による数値予測 |
| 第 8 章（未執筆） | 実践的な分類と前処理パイプライン |
| 第 9 章（未執筆） | 特徴量エンジニアリング |

### 第 4 部: モデルの改善と評価

| 章 | テーマ |
|----|--------|
| 第 10 章（未執筆） | ロジスティック回帰とアンサンブル学習 |
| 第 11 章（未執筆） | 評価指標と交差検証 |
| 第 12 章（未執筆） | 正則化とモデル選択 |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ |
|----|--------|
| [第 13 章](13-principal-component-analysis.md) | 主成分分析による次元削減 |
| 第 14 章（未執筆） | K-means によるクラスタリング |
| 第 15 章（未執筆） | 機械学習 API とモジュール設計 |

### 付録

総合演習（Bank）の解答例は Python 版のみです。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に TypeScript で取り組んでください。
