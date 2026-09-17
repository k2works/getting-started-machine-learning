# テスト駆動開発から始めるプログラミング入門

本記事シリーズは、テスト駆動開発（TDD）の手法を用いて、様々なプログラミング言語の特徴を実践的に学ぶためのガイドです。

FizzBuzz 問題を共通題材に、14 の言語で同じ TDD サイクル（Red-Green-Refactor）を体験し、言語ごとの設計思想と共通する本質を探求します。

## 言語別解説

| 言語 | 環境 | 特徴 |
|------|------|------|
| [Java](java/index.md) | JVM | 静的型付け、オブジェクト指向、豊富なエコシステム |
| [JavaScript](node/index.md) | Node.js | 動的型付け、プロトタイプベース、非同期処理 |
| [TypeScript](node/index.md) | Node.js | JavaScript + 静的型付け、型推論、インターフェース |
| [Python](python/index.md) | CPython | 動的型付け、読みやすさ重視、マルチパラダイム |
| [Ruby](ruby/index.md) | CRuby | 動的型付け、すべてがオブジェクト、DSL 親和性 |
| [PHP](php/index.md) | PHP | 動的型付け、Web 特化、漸進的型付け対応 |
| [Go](go/index.md) | Go | 静的型付け、ゴルーチン、シンプルな言語仕様 |
| [Rust](rust/index.md) | Rust | 所有権システム、ゼロコスト抽象化、メモリ安全性 |
| [C#](csharp/index.md) | .NET | 静的型付け、OOP + FP ハイブリッド、LINQ |
| [F#](fsharp/index.md) | .NET | 関数型ファースト、判別共用体、パイプライン |
| [Clojure](clojure/index.md) | JVM | LISP 方言、動的型付け、不変データ構造 |
| [Scala](scala/index.md) | JVM | OOP と FP の融合、強力な型システム、パターンマッチング |
| [Elixir](elixir/index.md) | Erlang VM | 関数型、並行処理、パターンマッチング、OTP |
| [Haskell](haskell/index.md) | GHC | 純粋関数型、型クラス、モナド、遅延評価 |

## 多言語統合解説

[多言語統合解説](integration/index.md) では、14 言語の実装を横断的に比較し、TDD の本質と言語固有の表現を統合的に解説します。

## 章構成

### 第 1 部: TDD の基本サイクル

| 章 | テーマ | Java | JS/TS | Python | Ruby | PHP | Go | Rust | C# | F# | Clojure | Scala | Elixir | Haskell |
|----|--------|------|-------|--------|------|-----|-----|------|-----|-----|---------|-------|--------|---------|
| 1 | TODO リストと最初のテスト | [Java](java/01-todo-list-and-first-test.md) | [JS/TS](node/01-todo-list-and-first-test.md) | [Python](python/01-todo-list-and-first-test.md) | [Ruby](ruby/01-todo-list-and-first-test.md) | [PHP](php/01-todo-list-and-first-test.md) | [Go](go/01-todo-list-and-first-test.md) | [Rust](rust/01-todo-list-and-first-test.md) | [C#](csharp/chapter01.md) | [F#](fsharp/chapter01.md) | [Clojure](clojure/01-todo-list-and-first-test.md) | [Scala](scala/01-todo-list-and-first-test.md) | [Elixir](elixir/01-todo-list-and-first-test.md) | [Haskell](haskell/01-todo-list-and-first-test.md) |
| 2 | 仮実装と三角測量 | [Java](java/02-fake-it-and-triangulation.md) | [JS/TS](node/02-fake-it-and-triangulation.md) | [Python](python/02-fake-it-and-triangulation.md) | [Ruby](ruby/02-fake-it-and-triangulation.md) | [PHP](php/02-fake-it-and-triangulation.md) | [Go](go/02-fake-it-and-triangulation.md) | [Rust](rust/02-fake-it-and-triangulation.md) | [C#](csharp/chapter02.md) | [F#](fsharp/chapter02.md) | [Clojure](clojure/02-fake-it-and-triangulation.md) | [Scala](scala/02-fake-it-and-triangulation.md) | [Elixir](elixir/02-fake-it-and-triangulation.md) | [Haskell](haskell/02-fake-it-and-triangulation.md) |
| 3 | 明白な実装とリファクタリング | [Java](java/03-obvious-implementation-and-refactoring.md) | [JS/TS](node/03-obvious-implementation-and-refactoring.md) | [Python](python/03-obvious-implementation-and-refactoring.md) | [Ruby](ruby/03-obvious-implementation-and-refactoring.md) | [PHP](php/03-obvious-implementation-and-refactoring.md) | [Go](go/03-obvious-implementation-and-refactoring.md) | [Rust](rust/03-obvious-implementation-and-refactoring.md) | [C#](csharp/chapter03.md) | [F#](fsharp/chapter03.md) | [Clojure](clojure/03-obvious-implementation-and-refactoring.md) | [Scala](scala/03-obvious-implementation-and-refactoring.md) | [Elixir](elixir/03-obvious-implementation-and-refactoring.md) | [Haskell](haskell/03-obvious-implementation-and-refactoring.md) |

### 第 2 部: 開発環境と自動化

| 章 | テーマ | Java | JS/TS | Python | Ruby | PHP | Go | Rust | C# | F# | Clojure | Scala | Elixir | Haskell |
|----|--------|------|-------|--------|------|-----|-----|------|-----|-----|---------|-------|--------|---------|
| 4 | バージョン管理と Conventional Commits | [Java](java/04-version-control-and-conventional-commits.md) | [JS/TS](node/04-version-control-and-conventional-commits.md) | [Python](python/04-version-control-and-conventional-commits.md) | [Ruby](ruby/04-version-control-and-conventional-commits.md) | [PHP](php/04-version-control-and-conventional-commits.md) | [Go](go/04-version-control-and-conventional-commits.md) | [Rust](rust/04-version-control-and-conventional-commits.md) | [C#](csharp/chapter04.md) | [F#](fsharp/chapter04.md) | [Clojure](clojure/04-version-control-and-conventional-commits.md) | [Scala](scala/04-version-control-and-conventional-commits.md) | [Elixir](elixir/04-version-control-and-conventional-commits.md) | [Haskell](haskell/04-version-control-and-conventional-commits.md) |
| 5 | パッケージ管理と静的解析 | [Java](java/05-package-management-and-static-analysis.md) | [JS/TS](node/05-package-management-and-static-analysis.md) | [Python](python/05-package-management-and-static-analysis.md) | [Ruby](ruby/05-package-management-and-static-analysis.md) | [PHP](php/05-package-management-and-static-analysis.md) | [Go](go/05-package-management-and-static-analysis.md) | [Rust](rust/05-package-management-and-static-analysis.md) | [C#](csharp/chapter05.md) | [F#](fsharp/chapter05.md) | [Clojure](clojure/05-package-management-and-static-analysis.md) | [Scala](scala/05-package-management-and-static-analysis.md) | [Elixir](elixir/05-package-management-and-static-analysis.md) | [Haskell](haskell/05-package-management-and-static-analysis.md) |
| 6 | タスクランナーと CI/CD | [Java](java/06-task-runner-and-ci-cd.md) | [JS/TS](node/06-task-runner-and-ci-cd.md) | [Python](python/06-task-runner-and-ci-cd.md) | [Ruby](ruby/06-task-runner-and-ci-cd.md) | [PHP](php/06-task-runner-and-ci-cd.md) | [Go](go/06-task-runner-and-ci-cd.md) | [Rust](rust/06-task-runner-and-ci-cd.md) | [C#](csharp/chapter06.md) | [F#](fsharp/chapter06.md) | [Clojure](clojure/06-task-runner-and-ci-cd.md) | [Scala](scala/06-task-runner-and-ci-cd.md) | [Elixir](elixir/06-task-runner-and-ci-cd.md) | [Haskell](haskell/06-task-runner-and-ci-cd.md) |

### 第 3 部: オブジェクト指向設計

| 章 | テーマ | Java | JS/TS | Python | Ruby | PHP | Go | Rust | C# | F# | Clojure | Scala | Elixir | Haskell |
|----|--------|------|-------|--------|------|-----|-----|------|-----|-----|---------|-------|--------|---------|
| 7 | カプセル化とポリモーフィズム | [Java](java/07-encapsulation-and-polymorphism.md) | [JS/TS](node/07-encapsulation-and-polymorphism.md) | [Python](python/07-encapsulation-and-polymorphism.md) | [Ruby](ruby/07-encapsulation-and-polymorphism.md) | [PHP](php/07-encapsulation-and-polymorphism.md) | [Go](go/07-encapsulation-and-polymorphism.md) | [Rust](rust/07-encapsulation-and-polymorphism.md) | [C#](csharp/chapter07.md) | [F#](fsharp/chapter07.md) | [Clojure](clojure/07-protocols-and-records.md) | [Scala](scala/07-case-classes-and-traits.md) | [Elixir](elixir/07-structs-and-protocols.md) | [Haskell](haskell/07-algebraic-data-types-and-type-classes.md) |
| 8 | デザインパターンの適用 | [Java](java/08-design-patterns.md) | [JS/TS](node/08-design-patterns.md) | [Python](python/08-design-patterns.md) | [Ruby](ruby/08-design-patterns.md) | [PHP](php/08-design-patterns.md) | [Go](go/08-design-patterns.md) | [Rust](rust/08-design-patterns.md) | [C#](csharp/chapter08.md) | [F#](fsharp/chapter08.md) | [Clojure](clojure/08-multimethods-and-design-patterns.md) | [Scala](scala/08-pattern-matching-and-sealed-traits.md) | [Elixir](elixir/08-pattern-matching-and-guards.md) | [Haskell](haskell/08-pattern-matching-and-guards.md) |
| 9 | SOLID 原則とモジュール設計 | [Java](java/09-solid-principles-and-module-design.md) | [JS/TS](node/09-solid-principles-and-module-design.md) | [Python](python/09-solid-principles-and-module-design.md) | [Ruby](ruby/09-solid-principles-and-module-design.md) | [PHP](php/09-solid-principles-and-module-design.md) | [Go](go/09-solid-principles-and-module-design.md) | [Rust](rust/09-solid-principles-and-module-design.md) | [C#](csharp/chapter09.md) | [F#](fsharp/chapter09.md) | [Clojure](clojure/09-namespaces-and-module-design.md) | [Scala](scala/09-packages-and-module-design.md) | [Elixir](elixir/09-module-design-and-behaviours.md) | [Haskell](haskell/09-module-design-and-smart-constructors.md) |

### 第 4 部: 関数型プログラミングへの展開

| 章 | テーマ | Java | JS/TS | Python | Ruby | PHP | Go | Rust | C# | F# | Clojure | Scala | Elixir | Haskell |
|----|--------|------|-------|--------|------|-----|-----|------|-----|-----|---------|-------|--------|---------|
| 10 | 高階関数と関数合成 | [Java](java/10-higher-order-functions-and-composition.md) | [JS/TS](node/10-higher-order-functions-and-composition.md) | [Python](python/10-higher-order-functions-and-composition.md) | [Ruby](ruby/10-higher-order-functions-and-composition.md) | [PHP](php/10-higher-order-functions-and-composition.md) | [Go](go/10-higher-order-functions-and-composition.md) | [Rust](rust/10-higher-order-functions-and-composition.md) | [C#](csharp/chapter10.md) | [F#](fsharp/chapter10.md) | [Clojure](clojure/10-higher-order-functions-and-composition.md) | [Scala](scala/10-higher-order-functions-and-composition.md) | [Elixir](elixir/10-higher-order-functions-and-pipeline.md) | [Haskell](haskell/10-higher-order-functions-and-currying.md) |
| 11 | 不変データとパイプライン処理 | [Java](java/11-immutable-data-and-pipeline.md) | [JS/TS](node/11-immutable-data-and-pipeline.md) | [Python](python/11-immutable-data-and-pipeline.md) | [Ruby](ruby/11-immutable-data-and-pipeline.md) | [PHP](php/11-immutable-data-and-pipeline.md) | [Go](go/11-immutable-data-and-pipeline.md) | [Rust](rust/11-immutable-data-and-pipeline.md) | [C#](csharp/chapter11.md) | [F#](fsharp/chapter11.md) | [Clojure](clojure/11-persistent-data-and-pipeline.md) | [Scala](scala/11-collections-and-lazy-evaluation.md) | [Elixir](elixir/11-stream-and-lazy-evaluation.md) | [Haskell](haskell/11-function-composition-and-point-free.md) |
| 12 | エラーハンドリングと型安全性 | [Java](java/12-error-handling-and-type-safety.md) | [JS/TS](node/12-error-handling-and-type-safety.md) | [Python](python/12-error-handling-and-type-safety.md) | [Ruby](ruby/12-error-handling-and-type-safety.md) | [PHP](php/12-error-handling-and-type-safety.md) | [Go](go/12-error-handling-and-type-safety.md) | [Rust](rust/12-error-handling-and-type-safety.md) | [C#](csharp/chapter12.md) | [F#](fsharp/chapter12.md) | [Clojure](clojure/12-error-handling-and-spec.md) | [Scala](scala/12-error-handling-and-type-safety.md) | [Elixir](elixir/12-error-handling-and-with.md) | [Haskell](haskell/12-monad-and-error-handling.md) |

## テスト駆動開発について

このシリーズでは、各プログラミング言語でテスト駆動開発の手法を用いて実装していきます。テスト駆動開発の基本的な流れは以下の通りです：

1. **レッド** - まず、失敗するテストを書く
2. **グリーン** - テストが通るように最小限のコードを実装する
3. **リファクタリング** - コードの品質を向上させる（重複の除去、可読性の向上など）

この流れを繰り返すことで、確実に動作するコードを段階的に作り上げていきます。

## 対象読者

- プログラミングの基本構文を理解している方
- 複数のプログラミング言語に興味がある方
- テスト駆動開発に興味がある方
- 言語の特徴を理解したい方

## 共通のテーマ

- FizzBuzz 問題を題材にした TDD 体験
- 各言語のテスティングフレームワーク
- 言語固有の特徴と設計思想
- 開発環境の構築とツールチェーン

## 参照

- 『テスト駆動開発』 - Kent Beck
- 『リファクタリング』 - Martin Fowler
- 『Clean Code』 - Robert C. Martin
- 『エクストリームプログラミング』 - Kent Beck
