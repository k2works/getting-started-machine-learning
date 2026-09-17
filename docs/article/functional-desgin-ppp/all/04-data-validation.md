---
type: Article
title: "第4章: データ検証 — 6言語統合ガイド"
description: "データ検証は、ソフトウェアの信頼性を支える基礎です。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第4章: データ検証 — 6言語統合ガイド

## 1. はじめに

データ検証は、ソフトウェアの信頼性を支える基礎です。不正なデータがシステムの奥深くまで浸透すると、デバッグが困難なバグを引き起こします。関数型プログラミングでは、**型レベルの保証**と**ランタイム検証**を組み合わせ、「不正な状態を表現不可能にする」ことを目指します。

本章では、各言語のバリデーション戦略を**静的検証（コンパイル時）**と**動的検証（実行時）**の軸で比較します。

## 2. 共通の本質

### バリデーション結果の表現

すべての言語が共通して持つ 2 つのパターン：

1. **逐次検証（Fail-Fast）**: 最初のエラーで停止し、そのエラーを返す
2. **蓄積検証（Error Accumulation）**: すべての検証を実行し、エラーをまとめて返す

```
逐次検証:  validate(a) → validate(b) → validate(c)  ※ 最初の失敗で停止
蓄積検証:  validate(a) ⊕ validate(b) ⊕ validate(c)  ※ すべて実行してエラーを収集
```

### ドメインプリミティブ

「生の値」ではなく「検証済みの値」を型で表現するパターンです。スマートコンストラクタを通じてのみ値を生成し、不正な状態を構造的に排除します。

## 3. 言語別実装比較

### 3.1 バリデーション結果の型表現

| 言語 | 逐次検証 | 蓄積検証 | ドメインプリミティブ |
|------|---------|---------|------------------|
| Clojure | `s/valid?` + `s/explain` | Spec による自動検証 | Spec + スマートコンストラクタ |
| Scala | `Either[List[String], A]` | `Validated` パターン | Opaque Type |
| Elixir | `:ok` / `:error` タプル | `with` 式チェーン | 構造体 + スマートコンストラクタ |
| F# | `Result<'T, 'E>` | `Validated` 型 | プライベートコンストラクタ |
| Haskell | `Either ValidationError a` | `Validated` 型 | `newtype` |
| Rust | `Result<T, E>` | `Validated<E, A>` 型 | struct + private フィールド |

### 3.2 Clojure Spec — ランタイム仕様定義

Clojure は他の言語とは根本的に異なるアプローチを取ります。`clojure.spec` は**ランタイム仕様定義システム**であり、バリデーション・テストデータ生成・ドキュメンテーションを統合的に提供します。

```clojure
(require '[clojure.spec.alpha :as s])

;; スペック定義
(s/def ::name (s/and string? #(>= (count %) 1)))
(s/def ::age (s/and int? #(>= % 0) #(<= % 150)))
(s/def ::email (s/and string? #(re-matches #".+@.+\..+" %)))

;; 複合スペック
(s/def ::person (s/keys :req-un [::name ::age ::email]))

;; 検証
(s/valid? ::person {:name "田中" :age 30 :email "tanaka@example.com"})
;; => true

;; エラー詳細
(s/explain ::person {:name "" :age -1 :email "invalid"})
;; => 全エラーを一覧表示
```

Spec の特筆すべき点は、仕様定義からテストデータを**自動生成**できることです：

```clojure
(require '[clojure.spec.gen.alpha :as gen])
(gen/sample (s/gen ::person) 3)
;; => ({:name "a" :age 42 :email "x@y.z"} ...)
```

### 3.3 静的型付け言語の型レベル検証

<details>
<summary>Scala: Either + Validated + Opaque Type</summary>

```scala
// 逐次検証（Either）
type ValidationResult[A] = Either[List[String], A]

def validateName(name: String): ValidationResult[String] =
  if name.nonEmpty then Right(name)
  else Left(List("名前は必須です"))

// 蓄積検証（Validated）
case class Validated[+E, +A](value: Either[List[E], A])

object Validated:
  def valid[E, A](a: A): Validated[E, A] = Validated(Right(a))
  def invalid[E, A](e: E): Validated[E, A] = Validated(Left(List(e)))

  def combine[E, A, B, C](va: Validated[E, A], vb: Validated[E, B])
    (f: (A, B) => C): Validated[E, C] = (va.value, vb.value) match
      case (Right(a), Right(b)) => valid(f(a, b))
      case (Left(e1), Left(e2)) => Validated(Left(e1 ++ e2))
      case (Left(e), _) => Validated(Left(e))
      case (_, Left(e)) => Validated(Left(e))

// ドメインプリミティブ（Opaque Type）
opaque type ProductId = String
object ProductId:
  def apply(value: String): Either[String, ProductId] =
    if value.matches("^[A-Z]{2}-\\d{4}$") then Right(value)
    else Left("商品IDの形式が不正です")
```

</details>

<details>
<summary>F#: Result + Validated + パイプライン</summary>

```fsharp
// 逐次検証（Result）
let validateName (name: string) =
    if String.IsNullOrWhiteSpace name then Error "名前は必須です"
    else Ok name

// 蓄積検証（Validated）
type Validated<'E, 'A> =
    | Valid of 'A
    | Invalid of 'E list

module Validated =
    let combine f va vb =
        match va, vb with
        | Valid a, Valid b -> Valid (f a b)
        | Invalid e1, Invalid e2 -> Invalid (e1 @ e2)
        | Invalid e, _ | _, Invalid e -> Invalid e

// パイプラインによる検証チェーン
let validatePerson name age email =
    validateName name
    |> Result.bind (fun n ->
        validateAge age
        |> Result.map (fun a -> (n, a)))
    |> Result.bind (fun (n, a) ->
        validateEmail email
        |> Result.map (fun e -> { Name = n; Age = a; Email = e }))
```

</details>

<details>
<summary>Haskell: Either + Validated + newtype</summary>

```haskell
-- 逐次検証（Either）
validateName :: String -> Either ValidationError String
validateName name
    | null name = Left (ValidationError "名前は必須です")
    | otherwise = Right name

-- 蓄積検証（Validated）
data Validated a = Valid a | Invalid [ValidationError]

combineValidated :: Validated a -> Validated b -> (a -> b -> c) -> Validated c
combineValidated (Valid a) (Valid b) f = Valid (f a b)
combineValidated (Invalid e1) (Invalid e2) _ = Invalid (e1 ++ e2)
combineValidated (Invalid e) _ _ = Invalid e
combineValidated _ (Invalid e) _ = Invalid e

-- ドメインプリミティブ（newtype）
newtype Email = Email String

mkEmail :: String -> Either ValidationError Email
mkEmail s
    | isValidEmail s = Right (Email s)
    | otherwise = Left (ValidationError "無効なメールアドレス")
```

Haskell の `newtype` はコンパイル時に除去されるため、**ゼロコスト**で型安全性を追加できます。

</details>

<details>
<summary>Rust: Result + Validated + Newtype</summary>

```rust
// 逐次検証（Result）
pub fn validate_name(name: &str) -> Result<String, String> {
    if name.is_empty() {
        Err("名前は必須です".to_string())
    } else {
        Ok(name.to_string())
    }
}

// ドメインプリミティブ（Newtype パターン）
pub struct ProductId(String);

impl ProductId {
    pub fn new(value: &str) -> Result<Self, String> {
        if value.len() >= 3 && value.starts_with("P-") {
            Ok(ProductId(value.to_string()))
        } else {
            Err("商品IDの形式が不正です".to_string())
        }
    }

    pub fn value(&self) -> &str { &self.0 }
}
```

</details>

<details>
<summary>Elixir: :ok/:error タプル + with 式</summary>

```elixir
def validate_name(name) when is_binary(name) and byte_size(name) > 0 do
  {:ok, name}
end
def validate_name(_), do: {:error, "名前は必須です"}

# with 式による逐次検証（Railway Oriented Programming）
def validate_person(params) do
  with {:ok, name} <- validate_name(params.name),
       {:ok, age} <- validate_age(params.age),
       {:ok, email} <- validate_email(params.email) do
    {:ok, %Person{name: name, age: age, email: email}}
  end
end
```

Elixir の `with` 式は、エラーが発生した時点で自動的にそのエラーを返す**Railway Oriented Programming**を簡潔に表現します。

</details>

### 3.4 ドメインプリミティブの実装コスト

| 言語 | 実装方法 | ランタイムコスト | コンパイル時保証 |
|------|---------|----------------|----------------|
| Clojure | Spec + スマートコンストラクタ | 実行時検証のみ | なし |
| Scala | Opaque Type | ゼロ | 完全 |
| Elixir | 構造体 + コンストラクタ | 実行時検証のみ | なし |
| F# | プライベートコンストラクタ | ゼロ | 完全 |
| Haskell | `newtype` | ゼロ（コンパイル時除去） | 完全 |
| Rust | struct + private フィールド | ゼロ | 完全 |

## 4. 比較分析

### 4.1 検証スペクトラム

```
    コンパイル時 ←――――――――――――――――→ 実行時

    Haskell    F#    Rust    Scala    Elixir    Clojure
    ├─型クラス──┤     │       │        │         │
               ├─DU──┤       │        │         │
                     ├─enum──┤        │         │
                             ├─trait──┤         │
                                      ├─with 式─┤
                                                ├─Spec
```

静的型付け言語はコンパイル時に多くの不正状態を排除できます。動的型付け言語はテストとランタイム検証で同等の安全性を確保します。

### 4.2 エラー蓄積戦略

逐次検証と蓄積検証の使い分け：

| 戦略 | ユースケース | 実装する言語 |
|------|-----------|------------|
| 逐次検証（Fail-Fast） | 依存関係のある検証（ID 存在確認 → 権限チェック） | 全 6 言語 |
| 蓄積検証（Error Accumulation） | フォーム入力の一括検証 | Scala, F#, Haskell, Rust, Clojure |

Elixir は `with` 式が逐次検証に特化しており、蓄積検証を行うには独自の実装が必要です。

### 4.3 Clojure Spec の独自性

Clojure Spec は他の言語のバリデーションライブラリとは根本的に異なります：

| 機能 | Clojure Spec | 他言語の型システム |
|------|-------------|----------------|
| バリデーション | ランタイム | コンパイル時 |
| テストデータ生成 | 仕様から自動生成 | 別ライブラリが必要 |
| ドキュメンテーション | 仕様が文書を兼ねる | 型注釈が文書を兼ねる |
| 合成可能性 | `s/and`, `s/or` で自由に合成 | 型演算子で合成 |

## 5. 実践的な選択指針

| 要件 | 推奨言語 | 理由 |
|------|---------|------|
| コンパイル時の最大保証 | Haskell | `newtype` + 型クラスで不正状態を型レベルで排除 |
| バリデーションとテスト統合 | Clojure | Spec が検証・生成・文書を一体化 |
| 実務的なバランス | F#, Rust | 型安全性 + 実用的なエラーハンドリング |
| Web フォーム検証 | Elixir | `with` 式 + Ecto changeset との統合 |
| DSL 的な表現力 | Scala | Opaque Type + 流暢な API |

## 6. まとめ

データ検証は、関数型プログラミングにおける**正しさの保証**の出発点です：

1. **型レベル vs ランタイム**: 静的型付け言語はコンパイル時に、動的型付け言語はテストとランタイムで保証
2. **ドメインプリミティブ**: すべての言語で「生の値を型で包む」パターンが有効
3. **エラー蓄積**: 逐次検証と蓄積検証を使い分けることで、ユーザーフレンドリーな検証を実現

## 言語別個別記事

- [Clojure](../clojure/04-clojure-spec.md) | [Scala](../scala/04-data-validation.md) | [Elixir](../elixir/04-data-validation.md) | [F#](../fsharp/04-data-validation.md) | [Haskell](../haskell/04-data-validation.md) | [Rust](../rust/04-data-validation.md)
