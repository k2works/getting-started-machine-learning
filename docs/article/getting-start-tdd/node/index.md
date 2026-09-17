# テスト駆動開発から始める TypeScript 入門

TypeScript は JavaScript に静的型付けを加えた言語で、大規模アプリケーション開発における型安全性と開発者体験を向上させます。本シリーズでは、FizzBuzz 問題を題材に TDD（テスト駆動開発）の手法を TypeScript で実践します。

## 特徴

- **静的型付け**: コンパイル時の型チェックによりバグを早期発見
- **JavaScript との互換性**: 既存の JavaScript コードとシームレスに連携
- **マルチパラダイム**: オブジェクト指向、関数型を柔軟に組み合わせ可能
- **豊富な型システム**: ジェネリクス、ユニオン型、型ガードによる表現力

## 開発環境

| ツール | 用途 |
|--------|------|
| [Vitest](https://vitest.dev/) | テスティングフレームワーク |
| [npm](https://www.npmjs.com/) | パッケージマネージャ |
| [ESLint](https://eslint.org/) | リンター |
| [Prettier](https://prettier.io/) | フォーマッター |
| [TypeScript](https://www.typescriptlang.org/) | 静的型チェック（tsc） |
| [Gulp](https://gulpjs.com/) | タスクランナー |
| [@vitest/coverage-v8](https://vitest.dev/guide/coverage.html) | コードカバレッジ |

## 章構成

### 第 1 部: TDD の基本サイクル

| 章 | テーマ |
|----|--------|
| [第 1 章](01-todo-list-and-first-test.md) | TODO リストと最初のテスト |
| [第 2 章](02-fake-it-and-triangulation.md) | 仮実装と三角測量 |
| [第 3 章](03-obvious-implementation-and-refactoring.md) | 明白な実装とリファクタリング |

### 第 2 部: 開発環境と自動化

| 章 | テーマ |
|----|--------|
| [第 4 章](04-version-control-and-conventional-commits.md) | バージョン管理と Conventional Commits |
| [第 5 章](05-package-management-and-static-analysis.md) | パッケージ管理と静的解析 |
| [第 6 章](06-task-runner-and-ci-cd.md) | タスクランナーと CI/CD |

### 第 3 部: オブジェクト指向設計

| 章 | テーマ |
|----|--------|
| [第 7 章](07-encapsulation-and-polymorphism.md) | カプセル化とポリモーフィズム |
| [第 8 章](08-design-patterns.md) | デザインパターンの適用 |
| [第 9 章](09-solid-principles-and-module-design.md) | SOLID 原則とモジュール設計 |

### 第 4 部: 関数型プログラミングへの展開

| 章 | テーマ |
|----|--------|
| [第 10 章](10-higher-order-functions-and-composition.md) | 高階関数と関数合成 |
| [第 11 章](11-immutable-data-and-pipeline.md) | 不変データとパイプライン処理 |
| [第 12 章](12-error-handling-and-type-safety.md) | エラーハンドリングと型安全性 |
