# Grokking Functional Programming：多言語統合解説

本記事シリーズは、11 の言語（Scala, Java, F#, C#, Haskell, Clojure, Elixir, Rust, Python, TypeScript, Ruby）での実装を横断的に比較し、関数型プログラミングの**本質**と**言語固有の表現**を統合的に解説します。

## 本シリーズの目的

各言語の個別記事は「その言語でどう実装するか」に焦点を当てています。本統合記事は、それらを横断して以下を明らかにします：

- **共通の本質**: 言語を超えて成り立つ関数型プログラミングの原則
- **言語間の差異**: 型システム、ランタイム、ライブラリの違いが実装にどう影響するか
- **選択の指針**: どの言語・ライブラリがどのパターンに適しているか

## 言語特性マトリクス

### 言語分類

| グループ | 言語 | 特徴 |
|---------|------|------|
| 関数型ファースト | Haskell, Clojure, Elixir, F# | 関数型が主パラダイム |
| マルチパラダイム（静的） | Scala, Rust, TypeScript | OOP と FP を高度に統合 |
| OOP ファースト + FP ライブラリ | Java, C#, Python, Ruby | ライブラリで FP 機能を補完 |

### 特性比較

| 特性 | Scala | Java | F# | C# | Haskell | Clojure | Elixir | Rust | Python | TypeScript | Ruby |
|------|-------|------|-----|-----|---------|---------|--------|------|--------|------------|------|
| 型システム | 静的（強い） | 静的（強い） | 静的（推論） | 静的（強い） | 静的（純粋） | 動的 | 動的 | 静的（所有権） | 動的（型ヒント） | 静的（構造的） | 動的 |
| 不変性 | case class | record (16+) | デフォルト不変 | record (9+) | 完全不変 | デフォルト不変 | デフォルト不変 | デフォルト不変 | 慣習 + NamedTuple | readonly | freeze |
| Option/Maybe | `Option[A]` | `Option<T>` (Vavr) | `Option<'a>` | `Option<A>` (LE) | `Maybe a` | `nil` / some | `nil` / `{:ok}` | `Option<T>` | `Maybe` (returns) | `Option<A>` (fp-ts) | `Maybe` (dry) |
| Either/Result | `Either[E, A]` | `Either<L, R>` (Vavr) | `Result<'a, 'e>` | `Either<L, R>` (LE) | `Either a b` | 手動 | `{:ok}/{:error}` | `Result<T, E>` | `Result` (returns) | `Either<E, A>` (fp-ts) | `Result` (dry) |
| IO モナド | cats-effect IO | 独自パターン | Async | Eff/Aff (LE) | IO モナド | lazy-seq | Agent/GenServer | async/await | IO (returns) | Task/TaskEither (fp-ts) | Task (dry) |
| 並行処理 | Ref/Fiber | Virtual Thread | MailboxProcessor | Atom/Task (LE) | STM/MVar | atom/core.async | OTP/GenServer | tokio/Mutex | asyncio | Promise.all | Fiber/Ractor |
| PBT ライブラリ | ScalaCheck | jqwik | FsCheck | FsCheck | QuickCheck | test.check | StreamData | proptest | Hypothesis | fast-check | rspec |
| 実行環境 | JVM | JVM | .NET (CLR) | .NET (CLR) | GHC | JVM | BEAM | ネイティブ | CPython | Node.js / Deno | CRuby |

> **LE** = LanguageExt、**Vavr** = Java 向け FP ライブラリ、**dry** = dry-rb エコシステム

## 記事構成

### Part I: 関数型プログラミングの基礎（第 1-2 章）

| 章 | 統合記事 | テーマ | 比較のポイント |
|----|---------|--------|---------------|
| 1 | [関数型プログラミング入門](./part-1-ch01-fp-introduction.md) | 命令型 vs 関数型、基本構文 | 各言語のパラダイムポジション、FP の表現スタイルの違い |
| 2 | [純粋関数と副作用](./part-1-ch02-pure-functions.md) | 純粋関数、参照透過性、副作用の排除 | 純粋性の強制度合い（Haskell の完全純粋 vs 他言語の慣習的純粋） |

#### 主な言語間比較テーマ

- **FP の表現力**: Haskell/Clojure/Elixir の FP ファースト vs Java/C# の OOP + FP アドオン
- **純粋性の保証**: Haskell の型レベル純粋性 vs Scala/F# の慣習的純粋性 vs 動的型付け言語の規約的純粋性
- **基本構文**: val/let バインディング vs 再代入可能変数、式ベース vs 文ベース

### Part II: 関数型スタイルのプログラミング（第 3-5 章）

| 章 | 統合記事 | テーマ | 比較のポイント |
|----|---------|--------|---------------|
| 3 | [イミュータブルなデータ操作](./part-2-ch03-immutable-data.md) | 不変データ構造とコピーオンライト | 構造共有、永続データ構造、言語レベルの不変保証 |
| 4 | [高階関数](./part-2-ch04-higher-order-functions.md) | map/filter/fold による変換パイプライン | パイプ演算子の有無、メソッドチェーン vs 関数適用スタイル |
| 5 | [flatMap とモナド的合成](./part-2-ch05-flatmap.md) | flatMap/bind によるコンテキスト付き計算の連鎖 | for 内包表記 vs do 記法 vs パイプ vs LINQ vs スレッディングマクロ |

#### 主な言語間比較テーマ

- **不変性の保証レベル**: Haskell の完全不変 vs Rust の `mut` オプトイン vs Clojure の永続データ構造 vs Java/C# のライブラリ依存
- **データ変換スタイル**: Elixir パイプ演算子 `|>` vs F# パイプ `|>` vs Haskell 関数合成 `.` vs Clojure スレッディングマクロ `->>`
- **flatMap の表現**: Scala for 内包表記 vs Haskell do 記法 vs C# LINQ vs TypeScript pipe + chain

### Part III: エラーハンドリングと Option/Either（第 6-7 章）

| 章 | 統合記事 | テーマ | 比較のポイント |
|----|---------|--------|---------------|
| 6 | [Option 型による安全なエラーハンドリング](./part-3-ch06-option.md) | null 安全性、値の有無の型表現 | 言語組み込み vs ライブラリ提供、パターンマッチのサポート度合い |
| 7 | [Either 型と代数的データ型](./part-3-ch07-either-adt.md) | 成功/失敗の型表現、ADT | 判別共用体 vs sealed trait vs enum vs Union、式問題への対応 |

#### 主な言語間比較テーマ

- **Option/Maybe の実装方式**: Haskell/Rust/F# の言語組み込み vs Scala の標準ライブラリ vs Java/C#/Python/Ruby/TypeScript のサードパーティライブラリ
- **ADT の表現**: Haskell `data` vs F# 判別共用体 vs Scala sealed trait vs Rust enum vs Clojure マップ vs Elixir タグ付きタプル
- **パターンマッチ**: Scala/Haskell/F#/Rust/Elixir の強力なパターンマッチ vs Java/C#/Python/TypeScript/Ruby の折衷的サポート

### Part IV: IO と副作用の管理（第 8-9 章）

| 章 | 統合記事 | テーマ | 比較のポイント |
|----|---------|--------|---------------|
| 8 | [IO モナドと副作用の分離](./part-4-ch08-io-monad.md) | 副作用の遅延実行、IO 型による記述と実行の分離 | Haskell IO vs cats-effect vs LanguageExt Eff vs 各言語の代替アプローチ |
| 9 | [ストリーム処理](./part-4-ch09-streams.md) | 無限ストリーム、遅延評価、リアクティブ処理 | fs2 vs Elixir Stream vs Haskell conduit vs 各言語のイテレータ/ジェネレータ |

#### 主な言語間比較テーマ

- **IO の抽象化**: Haskell IO モナド（言語組み込み） vs Scala cats-effect IO vs C# LanguageExt Eff vs 他言語の慣習的分離
- **遅延評価**: Haskell のデフォルト遅延 vs Clojure lazy-seq vs 他言語の明示的遅延（Lazy, Stream）
- **ストリーム処理**: fs2 Stream（Scala） vs Elixir Stream + GenStage vs Haskell conduit vs Python ジェネレータ vs Ruby Enumerator::Lazy

### Part V: 並行処理（第 10 章）

| 章 | 統合記事 | テーマ | 比較のポイント |
|----|---------|--------|---------------|
| 10 | [並行・並列処理](./part-5-ch10-concurrency.md) | Ref/Fiber、アトミック参照、軽量スレッド | 並行処理モデルの根本的な違い |

#### 主な言語間比較テーマ

- **並行処理モデル**: Scala Ref/Fiber vs Elixir OTP/GenServer vs Haskell STM/MVar vs Rust tokio/Arc/Mutex vs Clojure atom/core.async vs Java Virtual Thread
- **共有状態管理**: 関数型 Ref（Scala/F#） vs STM（Haskell/Clojure） vs 所有権（Rust） vs Atom（C# LanguageExt） vs Actor（Elixir）
- **軽量スレッド**: Scala Fiber vs Elixir プロセス vs Haskell green thread vs Java Virtual Thread vs Ruby Fiber

### Part VI: 実践的なアプリケーション構築とテスト（第 11-12 章）

| 章 | 統合記事                                           | テーマ | 比較のポイント |
|----|------------------------------------------------|--------|---------------|
| 11 | [実践的なアプリケーション構築](./part-6-ch11-application.md) | TravelGuide アプリ、Resource 管理、依存性注入 | DI の関数型アプローチ（Reader vs Protocol vs trait vs インターフェース） |
| 12 | [テスト戦略とプロパティベーステスト](./part-6-ch12-testing.md)  | PBT、テスト設計、副作用のテスト | 各言語の PBT ライブラリとジェネレータ設計 |

#### 主な言語間比較テーマ

- **リソース管理**: Scala Resource vs Haskell bracket vs Rust 所有権 + Drop vs Java try-with-resources vs C# using vs Python with
- **依存性注入**: Reader モナド（Scala/TypeScript） vs Protocol（Python） vs trait（Rust） vs ビヘイビア（Elixir） vs 型クラス（Haskell）
- **PBT ライブラリ**: ScalaCheck vs jqwik vs FsCheck vs QuickCheck vs test.check vs StreamData vs proptest vs Hypothesis vs fast-check vs rspec

## 各章の統合記事構成テンプレート

各統合記事は以下の構成で執筆します：

```
1. はじめに
   - テーマの概要と関数型での意義

2. 共通の本質
   - 言語を超えて成り立つ原則の核心

3. 言語別実装比較
   - 11 言語の代表的なコードを並べて比較
   - 各言語のイディオムを活かした実装の違い
   - 言語グループ別の傾向分析

4. 比較分析
   - 型システムの影響
   - ライブラリ依存度
   - 表現力と簡潔さのトレードオフ

5. 実践的な選択指針
   - どの言語特性がこのテーマに最も適しているか
   - プロジェクト要件に応じた選択基準

6. まとめ
   - 言語横断的な学び
   - 各言語の個別記事へのリンク
```

## 執筆ルール

### 1. 執筆フォーマット

````markdown
# Part N - 第M章：章タイトル

## M.1 セクションタイトル

本文...

### 言語別実装比較

#### 関数型ファースト言語

<details>
<summary>Haskell 実装</summary>

```haskell
-- コード例
```

</details>

<details>
<summary>Clojure 実装</summary>

```clojure
;; コード例
```

</details>

#### マルチパラダイム言語

<details>
<summary>Scala 実装</summary>

```scala
// コード例
```

</details>

#### OOP + FP ライブラリ言語

<details>
<summary>Java 実装</summary>

```java
// コード例
```

</details>
````

### 2. リスト記述ルール

タスク項目やリスト項目は、ラベル行の後に **1 行空けて** から記述します。

**OK**:

```markdown
**比較ポイント**:

- 不変性の保証レベル
- パフォーマンス特性
- 開発者体験
```

### 3. 言語グループ別の整理

コード比較では 11 言語を以下のグループに分けて提示します：

1. **関数型ファースト**: Haskell → Clojure → Elixir → F#
2. **マルチパラダイム（静的）**: Scala → Rust → TypeScript
3. **OOP + FP ライブラリ**: Java → C# → Python → Ruby

## 執筆計画

### 概要

全 12 章の統合記事を、Part ごとの統合難易度に基づき 3 つのイテレーションで執筆します。

- **総ソース量**: 55,784 行（11 言語 × 6 Part = 66 ファイル）
- **統合記事数**: 12 本
- **各記事の想定規模**: 500〜1,000 行
- **詳細計画**: [writing-plan.md](./writing-plan.md) を参照

### 難易度分類

| 難易度 | 章数 | 特徴 |
|--------|------|------|
| Low | 5 章（1-5） | 全言語で構造が一貫、基礎概念で統合しやすい |
| Medium | 4 章（6-9） | ライブラリ依存度が高く、言語固有セクションが必要 |
| High | 3 章（10-12） | 並行処理モデルや実践パターンの根本的差異への対応 |

### イテレーション 1: 基礎と関数型スタイル（Low 難易度）

**対象**: Part I + Part II（第 1-5 章）

全言語で概念構造が一貫しており、統合が最も容易です。

| 章 | タイトル | 難易度 | 統合方針 |
|----|---------|--------|---------|
| 01 | 関数型プログラミング入門 | Low | 命令型 vs 関数型の対比を全言語で横断比較 |
| 02 | 純粋関数と副作用 | Low | 純粋性の保証レベルを言語グループ別に比較 |
| 03 | イミュータブルなデータ操作 | Low | 不変データ構造の実現方法（言語組み込み vs ライブラリ）を比較 |
| 04 | 高階関数 | Low | map/filter/fold のイディオムとパイプライン記法を比較 |
| 05 | flatMap とモナド的合成 | Low | for/do/LINQ/パイプの糖衣構文を言語別に対比 |

**執筆順序**: 01 → 02 → 03 → 04 → 05

**完了条件**:

- [ ] 各章が統合記事構成テンプレートに準拠
- [ ] 11 言語すべてのコード例を含む
- [ ] 言語グループ別の傾向分析を含む

### イテレーション 2: エラーハンドリングと IO（Medium 難易度）

**対象**: Part III + Part IV（第 6-9 章）

各言語の Option/Either 実装方式とライブラリの差異が大きく、言語固有セクションの設計が必要です。

| 章 | タイトル | 難易度 | 統合方針 |
|----|---------|--------|---------|
| 06 | Option 型 | Medium | 言語組み込み vs ライブラリ提供の差異を軸に比較 |
| 07 | Either 型と ADT | Medium | ADT の表現力の違い（判別共用体 vs sealed trait vs enum vs タグ付きタプル） |
| 08 | IO モナド | Medium | IO の抽象化レベルの違い（Haskell IO vs cats-effect vs 慣習的分離） |
| 09 | ストリーム処理 | Medium | 遅延評価とストリームライブラリの多様な実装を比較 |

**執筆順序**: 06 → 07 → 08 → 09

**完了条件**:

- [ ] 各言語の Option/Either 実装の比較表を含む
- [ ] IO モナドの抽象化レベルの違いを図解
- [ ] ストリーム処理のパフォーマンス特性の比較を含む

### イテレーション 3: 並行処理と実践（High 難易度）

**対象**: Part V + Part VI（第 10-12 章）

並行処理モデルが言語ごとに根本的に異なり、最難関です。

| 章 | タイトル | 難易度 | 統合方針 |
|----|---------|--------|---------|
| 10 | 並行・並列処理 | High | 並行処理モデル（Fiber / OTP / STM / tokio / Virtual Thread）を言語別に深掘り |
| 11 | 実践アプリケーション | High | DI・リソース管理の関数型アプローチを言語別に比較 |
| 12 | テスト戦略 | Medium | PBT の共通原理を軸に各言語のライブラリを比較 |

**執筆順序**: 12 → 10 → 11

**完了条件**:

- [ ] 第 10 章で 11 言語の並行処理モデル比較表を含む
- [ ] 第 11 章で DI パターンの言語別実装を比較
- [ ] 第 12 章で全 PBT ライブラリの比較表を含む

### 全体スケジュール

| イテレーション | 章数 | 難易度構成 | 主な課題 |
|---------------|------|-----------|---------|
| 1 | 5 章（01-05） | Low × 5 | ウォームアップ、11 言語比較のフォーマット確立 |
| 2 | 4 章（06-09） | Medium × 4 | ライブラリ依存の差異整理、IO モナドの抽象度の違い |
| 3 | 3 章（10-12） | High × 2 + Medium × 1 | 並行処理モデルの根本的差異、実践パターンの統合 |

### 進捗管理

| 章 | タイトル | イテレーション | 難易度 | 状態 |
|----|---------|---------------|--------|------|
| 01 | 関数型プログラミング入門 | 1 | Low | 完了 |
| 02 | 純粋関数と副作用 | 1 | Low | 完了 |
| 03 | イミュータブルなデータ操作 | 1 | Low | 完了 |
| 04 | 高階関数 | 1 | Low | 完了 |
| 05 | flatMap とモナド的合成 | 1 | Low | 完了 |
| 06 | Option 型 | 2 | Medium | 完了 |
| 07 | Either 型と ADT | 2 | Medium | 完了 |
| 08 | IO モナドと副作用の分離 | 2 | Medium | 完了 |
| 09 | ストリーム処理 | 2 | Medium | 完了 |
| 10 | 並行・並列処理 | 3 | High | 完了 |
| 11 | 実践アプリケーション | 3 | High | 完了 |
| 12 | テスト戦略 | 3 | Medium | 完了 |

## 言語別個別記事へのリンク

| 言語 | FP ライブラリ | 個別記事一覧 |
|------|-------------|-------------|
| Scala | cats-effect / fs2 | [全 6 Part](../scala/index.md) |
| Java | Vavr | [全 6 Part](../java/index.md) |
| F# | 標準ライブラリ | [全 6 Part](../fsharp/index.md) |
| C# | LanguageExt | [全 6 Part](../csharp/index.md) |
| Haskell | 標準ライブラリ | [全 6 Part](../haskell/index.md) |
| Clojure | core.async | [全 6 Part](../clojure/index.md) |
| Elixir | OTP | [全 6 Part](../elixir/index.md) |
| Rust | tokio | [全 6 Part](../rust/index.md) |
| Python | returns | [全 6 Part](../python/index.md) |
| TypeScript | fp-ts | [全 6 Part](../typescript/index.md) |
| Ruby | dry-rb | [全 6 Part](../ruby/index.md) |

## 参照

- 「Grokking Functional Programming」Michał Płachta, Manning Publications
- [Scala 公式ドキュメント](https://docs.scala-lang.org/)
- [cats-effect](https://typelevel.org/cats-effect/)
- [Java 公式ドキュメント](https://docs.oracle.com/en/java/)
- [Vavr](https://www.vavr.io/)
- [F# 公式ドキュメント](https://fsharp.org/)
- [C# 公式ドキュメント](https://docs.microsoft.com/ja-jp/dotnet/csharp/)
- [LanguageExt](https://github.com/louthy/language-ext)
- [Haskell 公式ドキュメント](https://www.haskell.org/)
- [Clojure 公式ドキュメント](https://clojure.org/)
- [Elixir 公式ドキュメント](https://elixir-lang.org/)
- [Rust 公式ドキュメント](https://www.rust-lang.org/)
- [Python 公式ドキュメント](https://docs.python.org/3/)
- [returns](https://returns.readthedocs.io/)
- [TypeScript 公式ドキュメント](https://www.typescriptlang.org/docs/)
- [fp-ts](https://gcanti.github.io/fp-ts/)
- [Ruby 公式ドキュメント](https://docs.ruby-lang.org/ja/)
- [dry-rb](https://dry-rb.org/)
