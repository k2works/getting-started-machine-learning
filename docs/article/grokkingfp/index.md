# Grokking Functional Programming

「Grokking Functional Programming」（Michal Plachta 著）の学習用リポジトリです。
Scala、Java、F#、C#、Haskell、Clojure、Elixir、Rust、Python、TypeScript、Ruby の11言語で関数型プログラミングの実装例と日本語解説を提供します。

## 11言語統合比較

全 11 言語を横断的に比較する統合記事シリーズです。各章ごとに言語間の共通点と相違点を分析しています。

- [統合記事 目次](all/index.md)

| Part | 章 | タイトル | 統合記事 |
|------|-----|---------|---------|
| I | 01 | 関数型プログラミング入門 | [11言語比較](all/part-1-ch01-fp-introduction.md) |
| I | 02 | 純粋関数と副作用 | [11言語比較](all/part-1-ch02-pure-functions.md) |
| II | 03 | イミュータブルなデータ操作 | [11言語比較](all/part-2-ch03-immutable-data.md) |
| II | 04 | 高階関数 | [11言語比較](all/part-2-ch04-higher-order-functions.md) |
| II | 05 | flatMap とモナド的合成 | [11言語比較](all/part-2-ch05-flatmap.md) |
| III | 06 | Option 型 | [11言語比較](all/part-3-ch06-option.md) |
| III | 07 | Either 型と ADT | [11言語比較](all/part-3-ch07-either-adt.md) |
| IV | 08 | IO モナドと副作用の分離 | [11言語比較](all/part-4-ch08-io-monad.md) |
| IV | 09 | ストリーム処理 | [11言語比較](all/part-4-ch09-streams.md) |
| V | 10 | 並行・並列処理 | [11言語比較](all/part-5-ch10-concurrency.md) |
| VI | 11 | 実践アプリケーション | [11言語比較](all/part-6-ch11-application.md) |
| VI | 12 | テスト戦略 | [11言語比較](all/part-6-ch12-testing.md) |

## 言語別解説

### Scala 版

Scala 3 と cats-effect/fs2 を使った関数型プログラミングの実装例です。

- [Scala 解説](scala/index.md)

### Java 版

Java 21 と Vavr を使った関数型プログラミングの実装例です。

- [Java 解説](java/index.md)

### F# 版

F# 8 と .NET を使った関数型プログラミングの実装例です。

- [F# 解説](fsharp/index.md)

### C# 版

C# 12 と LanguageExt を使った関数型プログラミングの実装例です。

- [C# 解説](csharp/index.md)

### Haskell 版

Haskell（GHC 9.x）を使った純粋関数型プログラミングの実装例です。

- [Haskell 解説](haskell/index.md)

### Clojure 版

Clojure 1.11 と core.async を使った関数型プログラミングの実装例です。

- [Clojure 解説](clojure/index.md)

### Elixir 版

Elixir 1.15 と OTP を使った関数型プログラミングの実装例です。

- [Elixir 解説](elixir/index.md)

### Rust 版

Rust と tokio を使った関数型プログラミングの実装例です。

- [Rust 解説](rust/index.md)

### Python 版

Python 3.11+ と returns ライブラリを使った関数型プログラミングの実装例です。

- [Python 解説](python/index.md)

### TypeScript 版

TypeScript 5.x と fp-ts を使った関数型プログラミングの実装例です。

- [TypeScript 解説](typescript/index.md)

### Ruby 版

Ruby 3.x と dry-rb を使った関数型プログラミングの実装例です。

- [Ruby 解説](ruby/index.md)

## 章構成

| Part | 内容 | Scala | Java | F# | C# | Haskell | Clojure | Elixir | Rust | Python | TypeScript | Ruby |
|------|------|-------|------|-----|-----|---------|---------|--------|------|--------|------------|------|
| I | 関数型プログラミングの基礎 | [part-1](scala/part-1.md) | [part-1](java/part-1.md) | [part-1](fsharp/part-1.md) | [part-1](csharp/part-1.md) | [part-1](haskell/part-1.md) | [part-1](clojure/part-1.md) | [part-1](elixir/part-1.md) | [part-1](rust/part-1.md) | [part-1](python/part-1.md) | [part-1](typescript/part-1.md) | [part-1](ruby/part-1.md) |
| II | 関数型スタイルのプログラミング | [part-2](scala/part-2.md) | [part-2](java/part-2.md) | [part-2](fsharp/part-2.md) | [part-2](csharp/part-2.md) | [part-2](haskell/part-2.md) | [part-2](clojure/part-2.md) | [part-2](elixir/part-2.md) | [part-2](rust/part-2.md) | [part-2](python/part-2.md) | [part-2](typescript/part-2.md) | [part-2](ruby/part-2.md) |
| III | エラーハンドリング | [part-3](scala/part-3.md) | [part-3](java/part-3.md) | [part-3](fsharp/part-3.md) | [part-3](csharp/part-3.md) | [part-3](haskell/part-3.md) | [part-3](clojure/part-3.md) | [part-3](elixir/part-3.md) | [part-3](rust/part-3.md) | [part-3](python/part-3.md) | [part-3](typescript/part-3.md) | [part-3](ruby/part-3.md) |
| IV | IO と副作用の管理 | [part-4](scala/part-4.md) | [part-4](java/part-4.md) | [part-4](fsharp/part-4.md) | [part-4](csharp/part-4.md) | [part-4](haskell/part-4.md) | [part-4](clojure/part-4.md) | [part-4](elixir/part-4.md) | [part-4](rust/part-4.md) | [part-4](python/part-4.md) | [part-4](typescript/part-4.md) | [part-4](ruby/part-4.md) |
| V | 並行処理 | [part-5](scala/part-5.md) | [part-5](java/part-5.md) | [part-5](fsharp/part-5.md) | [part-5](csharp/part-5.md) | [part-5](haskell/part-5.md) | [part-5](clojure/part-5.md) | [part-5](elixir/part-5.md) | [part-5](rust/part-5.md) | [part-5](python/part-5.md) | [part-5](typescript/part-5.md) | [part-5](ruby/part-5.md) |
| VI | 実践的なアプリケーション | [part-6](scala/part-6.md) | [part-6](java/part-6.md) | [part-6](fsharp/part-6.md) | [part-6](csharp/part-6.md) | [part-6](haskell/part-6.md) | [part-6](clojure/part-6.md) | [part-6](elixir/part-6.md) | [part-6](rust/part-6.md) | [part-6](python/part-6.md) | [part-6](typescript/part-6.md) | [part-6](ruby/part-6.md) |

## 主要トピック

| Part | 主要トピック | Scala | Java | F# | C# | Haskell | Clojure | Elixir | Rust | Python | TypeScript | Ruby |
|------|-------------|-------|------|-----|-----|---------|---------|--------|------|--------|------------|------|
| I | 純粋関数、参照透過性 | 関数型基礎 | 関数型インターフェース | let バインディング | 式形式メソッド | 純粋関数、遅延評価 | defn, let | def, fn | fn, 所有権 | def, 型ヒント | pipe, 関数合成 | def, ブロック |
| II | イミュータブル、高階関数、flatMap | List, Option | Vavr List, Option | List, パイプライン | Seq, LINQ | リスト内包表記, fold | map/filter/reduce | Enum, パイプ | Iterator, クロージャ | map/filter, ジェネレータ | ReadonlyArray, chain | Enumerable, flat_map |
| III | Option、Either、ADT | sealed trait | sealed interface | 判別共用体 | Option, Either | Maybe, Either, ADT | nil, some->/some->> | {:ok}/{:error} | Option, Result | Maybe, Result (returns) | Option, Either, fold | nil安全, Result型 |
| IV | IO モナド、Stream | cats-effect, fs2 | 独自 IO, Vavr Stream | Async, Seq | Task, IAsyncEnumerable | IO モナド, conduit | lazy-seq | Agent, Stream | async/await, Stream | IO, ジェネレータ | Task, TaskEither, IO | IO, Enumerator |
| V | Ref、Fiber、並列処理 | cats-effect Ref/Fiber | 独自 Ref, Virtual Thread | Ref, MailboxProcessor | Ref, Task並列 | STM, TVar, async | atom/ref, core.async | Task, GenServer | Arc, Mutex, tokio | asyncio, FutureResult | sequenceT, traverseArray | Ref, Thread |
| VI | Resource、テスト | ScalaCheck | JUnit 5 プロパティテスト | use, FsCheck | Resource, Validator | bracket, QuickCheck | with-open, test.check | ExUnit, StreamData | トレイト, proptest | Protocol, Hypothesis | Reader, fast-check | Resource, RSpec |

## 参照

- [Grokking Functional Programming](https://www.manning.com/books/grokking-functional-programming) - 原著
- [Scala 公式ドキュメント](https://docs.scala-lang.org/)
- [cats-effect](https://typelevel.org/cats-effect/)
- [fs2](https://fs2.io/)
- [Vavr](https://www.vavr.io/)
- [F# 公式ドキュメント](https://docs.microsoft.com/ja-jp/dotnet/fsharp/)
- [F# for Fun and Profit](https://fsharpforfunandprofit.com/)
- [LanguageExt](https://github.com/louthy/language-ext)
- [Haskell 公式サイト](https://www.haskell.org/)
- [Learn You a Haskell](http://learnyouahaskell.com/)
- [Clojure 公式サイト](https://clojure.org/)
- [ClojureDocs](https://clojuredocs.org/)
- [Elixir 公式ドキュメント](https://elixir-lang.org/docs.html)
- [Elixir School](https://elixirschool.com/ja/)
- [Rust 公式ドキュメント](https://doc.rust-lang.org/book/)
- [tokio](https://tokio.rs/)
- [Python 公式ドキュメント](https://docs.python.org/3/)
- [returns](https://returns.readthedocs.io/)
- [Hypothesis](https://hypothesis.readthedocs.io/)
- [TypeScript 公式ドキュメント](https://www.typescriptlang.org/docs/)
- [fp-ts](https://gcanti.github.io/fp-ts/)
- [fast-check](https://fast-check.dev/)
- [Ruby 公式ドキュメント](https://docs.ruby-lang.org/ja/)
- [dry-rb](https://dry-rb.org/)
- [RSpec](https://rspec.info/)
