# テスト駆動開発から始める Clojure 入門

## 概要

FizzBuzz 問題を題材に、テスト駆動開発（TDD）の基本サイクルから、開発環境の整備、プロトコルとマルチメソッドによるポリモーフィズム、関数型プログラミングの活用まで、Clojure の特徴を活かしながら段階的に学びます。

## 対象読者

- Clojure の基本文法を理解しているプログラミング学習者
- TDD を体験してみたい開発者
- LISP 系言語の関数型プログラミングと不変データ構造に興味がある方

## 前提条件

- Clojure 1.11 以降がインストールされていること
- Leiningen が利用可能であること（Nix 環境推奨: `nix develop .#clojure`）

## Clojure の特徴

| 特徴 | 説明 |
|------|------|
| S 式（S-expression） | LISP 系のシンプルで統一された構文 |
| 不変データ構造 | 永続データ構造による安全な並行処理 |
| プロトコル | `defprotocol` による型ベースのポリモーフィズム |
| マルチメソッド | `defmulti`/`defmethod` による値ベースのディスパッチ |
| 高階関数 | `map`/`filter`/`reduce` を中心としたコレクション処理 |
| スレッディングマクロ | `->`/`->>` によるパイプライン処理 |
| JVM 上で動作 | Java ライブラリとの相互運用が可能 |

## 開発ツール

| ツール | 用途 |
|--------|------|
| [clojure.test](https://clojure.github.io/clojure/clojure.test-api.html) | 標準テスティングフレームワーク |
| [Leiningen](https://leiningen.org/) | プロジェクト管理・ビルドツール |
| [Eastwood](https://github.com/jonase/eastwood) | 静的コード解析（Linter） |
| [Kibit](https://github.com/clj-commons/kibit) | イディオム検査 |
| [cljfmt](https://github.com/weavejester/cljfmt) | コードフォーマッター |
| [Makefile](https://www.gnu.org/software/make/) | タスクランナー |

## 目次

### 第 1 部: TDD の基本サイクル

1. [第 1 章: TODO リストと最初のテスト](01-todo-list-and-first-test.md)
2. [第 2 章: 仮実装と三角測量](02-fake-it-and-triangulation.md)
3. [第 3 章: 明白な実装とリファクタリング](03-obvious-implementation-and-refactoring.md)

### 第 2 部: 開発環境と自動化

4. [第 4 章: バージョン管理と Conventional Commits](04-version-control-and-conventional-commits.md)
5. [第 5 章: パッケージ管理と静的解析](05-package-management-and-static-analysis.md)
6. [第 6 章: タスクランナーと CI/CD](06-task-runner-and-ci-cd.md)

### 第 3 部: プロトコルとマルチメソッド

7. [第 7 章: プロトコルとレコードによるポリモーフィズム](07-protocols-and-records.md)
8. [第 8 章: マルチメソッドとデザインパターン](08-multimethods-and-design-patterns.md)
9. [第 9 章: 名前空間とモジュール設計](09-namespaces-and-module-design.md)

### 第 4 部: 関数型プログラミング

10. [第 10 章: 高階関数と関数合成](10-higher-order-functions-and-composition.md)
11. [第 11 章: 永続データ構造とパイプライン処理](11-persistent-data-and-pipeline.md)
12. [第 12 章: エラーハンドリングと Spec](12-error-handling-and-spec.md)
