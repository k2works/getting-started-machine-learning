# テスト駆動開発から始める Haskell 入門

純粋関数型プログラミング言語 Haskell で、TDD（テスト駆動開発）を実践しながら FizzBuzz を段階的に構築していきます。

## 対象読者

- プログラミングの基礎知識を持つ開発者
- 純粋関数型言語と TDD に興味がある方
- 型クラスや代数的データ型を実践的に学びたい方

## 開発環境

| ツール | バージョン | 用途 |
|--------|-----------|------|
| GHC | 9.8.4 | Haskell コンパイラ |
| Stack | 3.7.1 | ビルドツール・パッケージマネージャ |
| HSpec | 2.11.12 | BDD スタイルテストフレームワーク |
| HLint | 3.10 | 静的解析ツール |
| Nix | - | 開発環境管理 |

## 記事構成

### 第 1 部: TDD の基本サイクル

1. [TODO リストと最初のテスト](01-todo-list-and-first-test.md)
2. [仮実装と三角測量](02-fake-it-and-triangulation.md)
3. [明白な実装とリファクタリング](03-obvious-implementation-and-refactoring.md)

### 第 2 部: 開発環境と自動化

4. [バージョン管理と Conventional Commits](04-version-control-and-conventional-commits.md)
5. [パッケージ管理と静的解析](05-package-management-and-static-analysis.md)
6. [タスクランナーと CI/CD](06-task-runner-and-ci-cd.md)

### 第 3 部: 型クラスと代数的データ型

7. [代数的データ型と型クラスによるポリモーフィズム](07-algebraic-data-types-and-type-classes.md)
8. [パターンマッチとガード](08-pattern-matching-and-guards.md)
9. [モジュール設計とスマートコンストラクタ](09-module-design-and-smart-constructors.md)

### 第 4 部: 関数型プログラミング

10. [高階関数とカリー化](10-higher-order-functions-and-currying.md)
11. [関数合成とポイントフリースタイル](11-function-composition-and-point-free.md)
12. [モナドとエラーハンドリング](12-monad-and-error-handling.md)

## ソースコード

実装コードは [`apps/haskell/`](https://github.com/k2works/getting-started-tdd/tree/main/apps/haskell) にあります。
