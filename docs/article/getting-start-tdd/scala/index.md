# テスト駆動開発から始める Scala 入門

## 概要

本シリーズでは、テスト駆動開発（TDD）の実践を通じて Scala の特徴を学びます。FizzBuzz 問題を題材に、Red-Green-Refactor サイクルを体験しながら、Scala のオブジェクト指向と関数型プログラミングの両方を段階的に習得します。

## 対象読者

- プログラミングの基礎知識を持つ方
- TDD に興味がある方
- Scala や JVM 上の関数型プログラミングに興味がある方

## 前提条件

- Scala 3.x、sbt がインストールされていること
- テキストエディタまたは IDE が利用可能であること
- Git の基本操作ができること

## Scala の特徴

| 特徴 | 説明 |
|------|------|
| OOP と FP の統合 | オブジェクト指向と関数型プログラミングを自然に組み合わせ可能 |
| 型推論 | 強力な型推論により型アノテーションを最小限に抑制 |
| ケースクラス | 不変の値オブジェクトを簡潔に定義 |
| パターンマッチ | 網羅的で型安全な分岐処理 |
| トレイト | 柔軟なミックスインによるコード再利用 |
| コレクションライブラリ | 統一的な API による豊富なコレクション操作 |
| JVM 互換 | Java エコシステムとの完全な相互運用性 |

## 開発ツール

| ツール | 用途 |
|--------|------|
| sbt | ビルドツール・パッケージマネージャ |
| ScalaTest | テスティングフレームワーク |
| scalafmt | コードフォーマッタ |
| WartRemover | 静的解析（コード品質チェック） |
| Metals | Language Server Protocol 実装 |

## 目次

### 第 1 部: TDD の基本サイクル

1. [第 1 章: TODO リストと最初のテスト](./01-todo-list-and-first-test.md)
2. [第 2 章: 仮実装と三角測量](./02-fake-it-and-triangulation.md)
3. [第 3 章: 明白な実装とリファクタリング](./03-obvious-implementation-and-refactoring.md)

### 第 2 部: 開発環境と自動化

4. [第 4 章: バージョン管理と Conventional Commits](./04-version-control-and-conventional-commits.md)
5. [第 5 章: パッケージ管理と静的解析](./05-package-management-and-static-analysis.md)
6. [第 6 章: タスクランナーと CI/CD](./06-task-runner-and-ci-cd.md)

### 第 3 部: オブジェクト指向設計

7. [第 7 章: ケースクラスとトレイトによるポリモーフィズム](./07-case-classes-and-traits.md)
8. [第 8 章: パターンマッチとシールドトレイト](./08-pattern-matching-and-sealed-traits.md)
9. [第 9 章: パッケージとモジュール設計](./09-packages-and-module-design.md)

### 第 4 部: 関数型プログラミング

10. [第 10 章: 高階関数と関数合成](./10-higher-order-functions-and-composition.md)
11. [第 11 章: コレクション処理と遅延評価](./11-collections-and-lazy-evaluation.md)
12. [第 12 章: エラーハンドリングと型安全性](./12-error-handling-and-type-safety.md)
