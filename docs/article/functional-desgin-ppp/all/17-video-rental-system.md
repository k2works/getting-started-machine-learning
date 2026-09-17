---
type: Article
title: "第17章: レンタルビデオシステム — 6言語統合ガイド"
description: "レンタルビデオシステムは、Martin Fowler の「リファクタリング」で有名な例題を関数型で再設計するケーススタディです。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第17章: レンタルビデオシステム — 6言語統合ガイド

## 1. はじめに

レンタルビデオシステムは、Martin Fowler の「リファクタリング」で有名な例題を関数型で再設計するケーススタディです。映画のカテゴリ別料金計算、明細書のフォーマット出力、頻繁なルール変更への対応を通じて、**Strategy パターンの関数型実現**と**ドメインモデリング**を学びます。

## 2. 共通の本質

### ドメインモデル

すべての言語で共通のドメイン概念：

- **映画カテゴリ**: 通常（Regular）、新作（New Release）、子供向け（Children's）
- **顧客**: 名前とレンタルのリストを保持
- **レンタル**: 映画と貸出日数の組み合わせ
- **明細書**: テキスト形式と HTML 形式の出力

### 料金計算ルール

```
通常:     2日まで $2.0、3日目以降 $1.5/日
新作:     $3.0/日（ボーナスポイント対象）
子供向け: 3日まで $1.5、4日目以降 $1.5/日
```

### 設計上の課題

1. **カテゴリ追加**: 新しい映画カテゴリへの対応
2. **料金ルール変更**: ビジネスルールの頻繁な変更
3. **出力形式追加**: テキスト以外のフォーマット対応

## 3. 言語別実装比較

### 3.1 映画カテゴリの型表現

| 言語 | 型表現 | 特徴 |
|------|--------|------|
| Clojure | キーワード（`:regular`, `:new-release`, `:childrens`） | 動的、スキーマレス |
| Scala | `enum MovieCategory` | 型安全、列挙 |
| Elixir | アトム（`:regular`, `:new_release`, `:children`） | 動的、パターンマッチ向き |
| F# | 判別共用体 `MovieType` | 網羅性チェック付き |
| Haskell | ADT `Category` | 最も厳密な型チェック |
| Rust | `enum PriceCode` | 型安全 + 所有権管理 |

<details>
<summary>Clojure: キーワード + マルチメソッド</summary>

```clojure
(defmulti determine-amount
  (fn [r] (rental/get-movie-category r)))

(defmethod determine-amount :regular [r]
  (let [days (rental/get-days r)]
    (if (> days 2) (+ 2.0 (* (- days 2) 1.5)) 2.0)))

(defmethod determine-amount :new-release [r]
  (* (rental/get-days r) 3.0))

(defmethod determine-amount :childrens [r]
  (let [days (rental/get-days r)]
    (if (> days 3) (+ 1.5 (* (- days 3) 1.5)) 1.5)))
```

</details>

<details>
<summary>Scala: enum + パターンマッチ</summary>

```scala
enum MovieCategory:
  case Regular
  case NewRelease
  case Childrens

def calculateAmount(rental: Rental): Double =
  rental.movie.category match
    case MovieCategory.Regular =>
      if rental.days > 2 then 2.0 + (rental.days - 2) * 1.5
      else 2.0
    case MovieCategory.NewRelease => rental.days * 3.0
    case MovieCategory.Childrens =>
      if rental.days > 3 then 1.5 + (rental.days - 3) * 1.5
      else 1.5
```

</details>

<details>
<summary>Haskell: ADT + case 式</summary>

```haskell
data Category = Regular | NewRelease | Childrens deriving (Show, Eq)

calculateAmount :: Rental -> Double
calculateAmount rental = case movieCategory (rentalMovie rental) of
    Regular ->
        if days > 2 then 2.0 + fromIntegral (days - 2) * 1.5 else 2.0
    NewRelease -> fromIntegral days * 3.0
    Childrens ->
        if days > 3 then 1.5 + fromIntegral (days - 3) * 1.5 else 1.5
  where days = rentalDays rental
```

</details>

<details>
<summary>Rust: enum + match</summary>

```rust
#[derive(Debug, Clone, PartialEq)]
pub enum PriceCode {
    Regular,
    NewRelease,
    Children,
}

pub fn calculate_amount(rental: &Rental) -> Decimal {
    match rental.movie.price_code {
        PriceCode::Regular => {
            if rental.days > 2 {
                dec!(2.0) + Decimal::from(rental.days - 2) * dec!(1.5)
            } else { dec!(2.0) }
        }
        PriceCode::NewRelease => Decimal::from(rental.days) * dec!(3.0),
        PriceCode::Children => {
            if rental.days > 3 {
                dec!(1.5) + Decimal::from(rental.days - 3) * dec!(1.5)
            } else { dec!(1.5) }
        }
    }
}
```

</details>

### 3.2 多態的ディスパッチの実現方法

| 言語 | ディスパッチ方法 | 拡張性 |
|------|----------------|--------|
| Clojure | `defmulti` / `defmethod` | メソッド追加のみで拡張 |
| Scala | パターンマッチ / trait object | `PricingPolicy` 追加で拡張 |
| Elixir | `case` パターンマッチ | 句を追加 |
| F# | `match` 式 | 判別共用体に追加 + コンパイラ警告 |
| Haskell | `case` 式 | ADT に追加 + コンパイラ警告 |
| Rust | `match` 式 / `trait` | enum に追加 + コンパイラエラー |

Clojure のマルチメソッドは**オープンディスパッチ**（既存コードを変更せず拡張可能）を実現します。静的型付け言語は**閉じたディスパッチ**（型定義の変更が必要）ですが、コンパイラが未処理のケースを検出してくれます。

### 3.3 明細書フォーマッターの設計

| 言語 | フォーマッター設計 | 切り替え方法 |
|------|------------------|-------------|
| Clojure | マルチメソッド（`:text`, `:html`） | ディスパッチ値で選択 |
| Scala | `StatementFormatter` trait | trait object を差し替え |
| Elixir | 独立関数（`generate/1`, `generate_html/1`） | 関数を直接呼び分け |
| F# | モジュール内関数（`formatText`, `formatHtml`） | 関数を直接呼び分け |
| Haskell | `StatementFormat` enum | パターンマッチで選択 |
| Rust | `StatementFormatter` trait | trait object を差し替え |

<details>
<summary>Clojure: マルチメソッドによるフォーマット切り替え</summary>

```clojure
(defmulti format-statement (fn [format _customer] format))

(defmethod format-statement :text [_ customer]
  (str "Rental Record for " (:name customer) "\n"
       (apply str (map format-line-text (:rentals customer)))
       "Amount owed is " (total-amount customer) "\n"))

(defmethod format-statement :html [_ customer]
  (str "<h1>Rental Record for " (:name customer) "</h1>\n"
       "<ul>" (apply str (map format-line-html (:rentals customer))) "</ul>\n"))
```

</details>

<details>
<summary>Rust: trait による Strategy パターン</summary>

```rust
pub trait StatementFormatter {
    fn format_header(&self, customer_name: &str) -> String;
    fn format_line(&self, title: &str, amount: Decimal) -> String;
    fn format_footer(&self, total: Decimal, points: i32) -> String;
}

pub struct TextFormatter;
pub struct HtmlFormatter;

impl StatementFormatter for TextFormatter {
    fn format_header(&self, name: &str) -> String {
        format!("Rental Record for {}\n", name)
    }
    // ...
}
```

</details>

### 3.4 ドメインモデルのデータ表現

| 言語 | Customer | Rental | Movie | 不変性 |
|------|----------|--------|-------|--------|
| Clojure | マップ | マップ | マップ | 自動（永続データ構造） |
| Scala | case class | case class | case class | `copy` で新インスタンス |
| Elixir | 構造体 | 構造体 | 構造体 | `%{struct | field: value}` |
| F# | レコード | レコード | レコード | `{ record with field = value }` |
| Haskell | レコード | レコード | レコード | レコード更新構文 |
| Rust | struct | struct | struct | `clone()` + 明示的 `mut` |

<details>
<summary>Scala: DSL によるビルダーパターン</summary>

Scala 版は DSL（ドメイン固有言語）を用いた流暢な API を提供しています：

```scala
customer("John")
  .rents(regular("Inception"), 3)
  .rents(newRelease("New Movie"), 2)
  .statement
```

このアプローチはテストコードの可読性を大幅に向上させます。

</details>

<details>
<summary>Elixir: Agent ベースのリポジトリ</summary>

Elixir 版は `Agent` を使ったステートフルなリポジトリパターンを採用しています：

```elixir
defmodule MovieRepository do
  use Agent

  def start_link(_opts) do
    Agent.start_link(fn -> %{} end, name: __MODULE__)
  end

  def add(movie) do
    Agent.update(__MODULE__, fn movies ->
      Map.put(movies, movie.title, movie)
    end)
  end
end
```

BEAM VM の並行処理モデルと自然に統合される設計です。

</details>

## 4. 比較分析

### 4.1 OOP の Strategy パターンとの関係

OOP では料金計算とフォーマットの切り替えに Strategy パターン（インターフェース + 実装クラス）を使います。関数型では各言語が異なるアプローチで同等の柔軟性を実現しています：

| アプローチ | 言語 | OOP との対応 |
|-----------|------|-------------|
| マルチメソッド | Clojure | 最もオープンな戦略切り替え |
| 高階関数 | Elixir, F# | 関数そのものが戦略 |
| パターンマッチ | Scala, Haskell | データ型が戦略を内包 |
| trait object | Rust, Scala | OOP に最も近い構造 |

### 4.2 金銭計算の精度

| 言語 | 数値型 | 精度 |
|------|--------|------|
| Clojure | `Double` | 浮動小数点 |
| Scala | `Double` | 浮動小数点 |
| Elixir | `Float` | 浮動小数点 |
| F# | `decimal` | 10 進精度（.NET 統合） |
| Haskell | `Double` | 浮動小数点 |
| Rust | `rust_decimal::Decimal` | 10 進精度（外部クレート） |

F# と Rust は金銭計算に適した 10 進精度型を使用しており、実務的な正確性を重視しています。

### 4.3 拡張性の比較

新しい映画カテゴリ（例: プレミアム）を追加する場合：

| 言語 | 必要な変更 | 安全性 |
|------|-----------|--------|
| Clojure | `defmethod` を追加するだけ | テストで確認 |
| Scala | enum + match に追加 | コンパイルエラーで検出 |
| Elixir | `case` に句を追加 | テストで確認 |
| F# | 判別共用体 + match に追加 | コンパイルエラーで検出 |
| Haskell | ADT + case に追加 | コンパイルエラーで検出 |
| Rust | enum + match に追加 | コンパイルエラーで検出 |

## 5. 実践的な選択指針

| 要件 | 推奨言語 | 理由 |
|------|---------|------|
| 頻繁なルール変更 | Clojure | マルチメソッドのオープンディスパッチ |
| 金銭計算の精度 | F#, Rust | 10 進精度型のネイティブサポート |
| DSL による可読性 | Scala | case class + ビルダーパターン |
| 並行リポジトリ | Elixir | Agent による状態管理 |
| 型安全な拡張 | Haskell | ADT + 網羅性チェック |

## 6. まとめ

レンタルビデオシステムは、OOP のリファクタリング例題を関数型で再設計する過程で、以下の学びを提供します：

1. **Strategy パターンの関数型表現**: マルチメソッド、高階関数、パターンマッチによる戦略切り替え
2. **ドメインモデルの型表現**: 動的タグ vs 代数的データ型 vs enum
3. **フォーマッターの分離**: 計算ロジックと出力形式の明確な分離

## 言語別個別記事

- [Clojure](../clojure/17-video-rental-system.md) | [Scala](../scala/17-video-rental-system.md) | [Elixir](../elixir/17-video-rental-system.md) | [F#](../fsharp/17-video-rental-system.md) | [Haskell](../haskell/17-video-rental-system.md) | [Rust](../rust/17-video-rental-system.md)
