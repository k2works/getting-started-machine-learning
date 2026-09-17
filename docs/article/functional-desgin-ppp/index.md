# 関数型デザイン - 原則、パターン、実践

本記事シリーズは、関数型プログラミングにおける設計原則とデザインパターンを実践的に学ぶためのガイドです。

Robert C. Martin の「Functional Design: Principles, Patterns, and Practices」をベースに、6 つの関数型言語で同じデザインパターンを実装し、言語ごとの特性と共通する本質を探求します。

## 言語別解説

| 言語 | 特徴 |
|------|------|
| [Clojure](clojure/index.md) | JVM 上の LISP 方言。動的型付け、ホモイコニシティ、マクロによるメタプログラミング |
| [Scala](scala/index.md) | JVM 上の OOP と FP のハイブリッド。強力な型システム、パターンマッチング |
| [Elixir](elixir/index.md) | Erlang VM 上の関数型言語。並行処理、パターンマッチング、OTP フレームワーク |
| [F#](fsharp/index.md) | .NET 上の関数型ファースト言語。代数的データ型、型推論、Computation Expression |
| [Haskell](haskell/index.md) | 純粋関数型言語。型クラス、モナド、遅延評価 |
| [Rust](rust/index.md) | システムプログラミング言語。所有権システム、トレイト、ゼロコスト抽象化 |

## 多言語統合解説

[多言語統合解説](all/index.md) では、6 言語の実装を横断的に比較し、関数型デザインパターンの本質と言語固有の表現を統合的に解説します。

## 章構成

### 第 1 部: 関数型プログラミングの基礎原則

| 章 | テーマ | Clojure | Scala | Elixir | F# | Haskell | Rust |
|----|--------|---------|-------|--------|-----|---------|------|
| 1 | 不変性とデータ変換 | [Clojure](clojure/01-immutability-and-data-transformation.md) | [Scala](scala/01-immutability-and-data-transformation.md) | [Elixir](elixir/01-immutability-and-data-transformation.md) | [F#](fsharp/01-immutability-and-data-transformation.md) | [Haskell](haskell/01-immutability-and-data-transformation.md) | [Rust](rust/01-immutability-and-data-transformation.md) |
| 2 | 関数合成と高階関数 | [Clojure](clojure/02-function-composition.md) | [Scala](scala/02-function-composition.md) | [Elixir](elixir/02-function-composition.md) | [F#](fsharp/02-function-composition.md) | [Haskell](haskell/02-function-composition.md) | [Rust](rust/02-function-composition.md) |
| 3 | 多態性の実現方法 | [Clojure](clojure/03-polymorphism.md) | [Scala](scala/03-polymorphism.md) | [Elixir](elixir/03-polymorphism.md) | [F#](fsharp/03-polymorphism.md) | [Haskell](haskell/03-polymorphism.md) | [Rust](rust/03-polymorphism.md) |

### 第 2 部: 仕様とテスト

| 章 | テーマ | Clojure | Scala | Elixir | F# | Haskell | Rust |
|----|--------|---------|-------|--------|-----|---------|------|
| 4 | データ検証 | [Clojure](clojure/04-clojure-spec.md) | [Scala](scala/04-data-validation.md) | [Elixir](elixir/04-data-validation.md) | [F#](fsharp/04-data-validation.md) | [Haskell](haskell/04-data-validation.md) | [Rust](rust/04-data-validation.md) |
| 5 | プロパティベーステスト | [Clojure](clojure/05-property-based-testing.md) | [Scala](scala/05-property-based-testing.md) | [Elixir](elixir/05-property-based-testing.md) | [F#](fsharp/05-property-based-testing.md) | [Haskell](haskell/05-property-based-testing.md) | [Rust](rust/05-property-based-testing.md) |
| 6 | TDD と関数型 | [Clojure](clojure/06-tdd-in-functional.md) | [Scala](scala/06-tdd-functional.md) | [Elixir](elixir/06-tdd-and-fp.md) | [F#](fsharp/06-tdd-functional.md) | [Haskell](haskell/06-tdd-functional.md) | [Rust](rust/06-tdd-and-functional.md) |

### 第 3 部: デザインパターン - 構造パターン

| 章 | テーマ | Clojure | Scala | Elixir | F# | Haskell | Rust |
|----|--------|---------|-------|--------|-----|---------|------|
| 7 | Composite パターン | [Clojure](clojure/07-composite-pattern.md) | [Scala](scala/07-composite-pattern.md) | [Elixir](elixir/07-effects-and-pure-functions.md) | [F#](fsharp/07-composite-pattern.md) | [Haskell](haskell/07-composite-pattern.md) | [Rust](rust/07-composite-pattern.md) |
| 8 | Decorator パターン | [Clojure](clojure/08-decorator-pattern.md) | [Scala](scala/08-decorator-pattern.md) | [Elixir](elixir/08-error-handling-strategies.md) | [F#](fsharp/08-decorator-pattern.md) | [Haskell](haskell/08-decorator-pattern.md) | [Rust](rust/08-decorator-pattern.md) |
| 9 | Adapter パターン | [Clojure](clojure/09-adapter-pattern.md) | [Scala](scala/09-adapter-pattern.md) | [Elixir](elixir/09-io-and-external-systems.md) | [F#](fsharp/09-adapter-pattern.md) | [Haskell](haskell/09-adapter-pattern.md) | [Rust](rust/09-adapter-pattern.md) |

### 第 4 部: デザインパターン - 振る舞いパターン

| 章 | テーマ | Clojure | Scala | Elixir | F# | Haskell | Rust |
|----|--------|---------|-------|--------|-----|---------|------|
| 10 | Strategy パターン | [Clojure](clojure/10-strategy-pattern.md) | [Scala](scala/10-strategy-pattern.md) | [Elixir](elixir/10-concurrency-patterns.md) | [F#](fsharp/10-strategy-pattern.md) | [Haskell](haskell/10-strategy-pattern.md) | [Rust](rust/10-strategy-pattern.md) |
| 11 | Command パターン | [Clojure](clojure/11-command-pattern.md) | [Scala](scala/11-command-pattern.md) | [Elixir](elixir/11-command-pattern.md) | [F#](fsharp/11-command-pattern.md) | [Haskell](haskell/11-command-pattern.md) | [Rust](rust/11-command-pattern.md) |
| 12 | Visitor パターン | [Clojure](clojure/12-visitor-pattern.md) | [Scala](scala/12-visitor-pattern.md) | [Elixir](elixir/12-visitor-pattern.md) | [F#](fsharp/12-visitor-pattern.md) | [Haskell](haskell/12-visitor-pattern.md) | [Rust](rust/12-visitor-pattern.md) |

### 第 5 部: デザインパターン - 生成パターン

| 章 | テーマ | Clojure | Scala | Elixir | F# | Haskell | Rust |
|----|--------|---------|-------|--------|-----|---------|------|
| 13 | Abstract Factory パターン | [Clojure](clojure/13-abstract-factory-pattern.md) | [Scala](scala/13-abstract-factory-pattern.md) | [Elixir](elixir/13-abstract-factory-pattern.md) | [F#](fsharp/13-abstract-factory-pattern.md) | [Haskell](haskell/13-abstract-factory-pattern.md) | [Rust](rust/13-abstract-factory-pattern.md) |
| 14 | Abstract Server パターン | [Clojure](clojure/14-abstract-server-pattern.md) | [Scala](scala/14-abstract-server-pattern.md) | [Elixir](elixir/14-abstract-server-pattern.md) | [F#](fsharp/14-abstract-server-pattern.md) | [Haskell](haskell/14-abstract-server-pattern.md) | [Rust](rust/14-abstract-server-pattern.md) |

### 第 6 部: 実践的なケーススタディ

| 章 | テーマ | Clojure | Scala | Elixir | F# | Haskell | Rust |
|----|--------|---------|-------|--------|-----|---------|------|
| 15 | Gossiping Bus Drivers | [Clojure](clojure/15-gossiping-bus-drivers.md) | [Scala](scala/15-gossiping-bus-drivers.md) | [Elixir](elixir/15-gossiping-bus-drivers.md) | [F#](fsharp/15-gossiping-bus-drivers.md) | [Haskell](haskell/15-gossiping-bus-drivers.md) | [Rust](rust/15-gossiping-bus-drivers.md) |
| 16 | 給与計算システム | [Clojure](clojure/16-payroll-system.md) | [Scala](scala/16-payroll-system.md) | [Elixir](elixir/16-payroll-system.md) | [F#](fsharp/16-payroll-system.md) | [Haskell](haskell/16-payroll-system.md) | [Rust](rust/16-payroll-system.md) |
| 17 | レンタルビデオシステム | [Clojure](clojure/17-video-rental-system.md) | [Scala](scala/17-video-rental-system.md) | [Elixir](elixir/17-video-rental-system.md) | [F#](fsharp/17-video-rental-system.md) | [Haskell](haskell/17-video-rental-system.md) | [Rust](rust/17-video-rental-system.md) |
| 18 | 並行処理システム | [Clojure](clojure/18-concurrency-system.md) | [Scala](scala/18-concurrency-system.md) | [Elixir](elixir/18-concurrency-system.md) | [F#](fsharp/18-concurrency-system.md) | [Haskell](haskell/18-concurrency-system.md) | [Rust](rust/18-concurrency-system.md) |
| 19 | Wa-Tor シミュレーション | [Clojure](clojure/19-wator-simulation.md) | [Scala](scala/19-wator-simulation.md) | [Elixir](elixir/19-wator-simulation.md) | [F#](fsharp/19-wator-simulation.md) | [Haskell](haskell/19-wator-simulation.md) | [Rust](rust/19-wator-simulation.md) |

### 第 7 部: まとめと応用

| 章 | テーマ | Clojure | Scala | Elixir | F# | Haskell | Rust |
|----|--------|---------|-------|--------|-----|---------|------|
| 20 | パターン間の相互作用 | [Clojure](clojure/20-pattern-interactions.md) | [Scala](scala/20-pattern-interactions.md) | [Elixir](elixir/20-pattern-interactions.md) | [F#](fsharp/20-pattern-interactions.md) | [Haskell](haskell/20-pattern-interactions.md) | [Rust](rust/20-pattern-interactions.md) |
| 21 | ベストプラクティス | [Clojure](clojure/21-best-practices.md) | [Scala](scala/21-best-practices.md) | [Elixir](elixir/21-best-practices.md) | [F#](fsharp/21-best-practices.md) | [Haskell](haskell/21-best-practices.md) | [Rust](rust/21-best-practices.md) |
| 22 | OO から FP への移行 | [Clojure](clojure/22-oo-to-fp-migration.md) | [Scala](scala/22-oo-to-fp-migration.md) | [Elixir](elixir/22-oo-to-fp-migration.md) | [F#](fsharp/22-oo-to-fp-migration.md) | [Haskell](haskell/22-oo-to-fp-migration.md) | [Rust](rust/22-oo-to-fp-migration.md) |

## 参照

- 「Functional Design: Principles, Patterns, and Practices」Robert C. Martin
- 「Clean Code」Robert C. Martin
- [Clojure 公式ドキュメント](https://clojure.org/)
- [Scala 公式ドキュメント](https://www.scala-lang.org/)
- [Elixir 公式ドキュメント](https://elixir-lang.org/)
- [F# 公式ドキュメント](https://fsharp.org/)
- [Haskell 公式ドキュメント](https://www.haskell.org/)
- [Rust 公式ドキュメント](https://www.rust-lang.org/)
