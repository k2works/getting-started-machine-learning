---
type: Article
title: "第 7 章: 代数的データ型と型クラスによるポリモーフィズム"
description: "第 1 部では手続き型の FizzBuzz プログラムを TDD で構築し、第 2 部では開発環境と自動化を整備しました。"
tags: [article, getting-start-tdd, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 7 章: 代数的データ型と型クラスによるポリモーフィズム

## 7.1 はじめに

第 1 部では手続き型の FizzBuzz プログラムを TDD で構築し、第 2 部では開発環境と自動化を整備しました。この章からは Haskell の型システムを活用して、プログラムを構造化された設計に進化させます。

第 1 部で作成した `generate` 関数を振り返りましょう。

```haskell
generate :: Int -> String
generate n
  | n `mod` 15 == 0 = "FizzBuzz"
  | n `mod` 3 == 0  = "Fizz"
  | n `mod` 5 == 0  = "Buzz"
  | otherwise        = show n
```

この関数は正しく動作しますが、新しい FizzBuzz のバリエーション（数値のみ返す、FizzBuzz だけ返す等）を追加するには、既存の関数を直接修正するか、別の関数を作る必要があります。Haskell の **代数的データ型** と **型クラス** を使えば、この問題をエレガントに解決できます。

### 追加仕様

```
タイプごとに出力を切り替えることができる。
タイプ 1 は通常の FizzBuzz、タイプ 2 は数字のみ、タイプ 3 は FizzBuzz の場合のみをプリントする。
```

## 7.2 TODO リスト

**TODO リスト**:

- [ ] タイプ 1 の場合（通常の FizzBuzz）
- [ ] タイプ 2 の場合（数字のみ）
- [ ] タイプ 3 の場合（FizzBuzz の場合のみ）
- [ ] それ以外のタイプの場合（エラーを返す）
- [ ] 値オブジェクト FizzBuzzValue の導入
- [ ] コレクション FizzBuzzList の導入

## 7.3 代数的データ型 — FizzBuzzType

### data 宣言による直和型

Haskell の `data` キーワードを使って、FizzBuzz のタイプを表す代数的データ型を定義します。

```haskell
data FizzBuzzType = Type01 | Type02 | Type03
  deriving (Show, Eq)
```

`FizzBuzzType` は **直和型**（sum type）です。`Type01`、`Type02`、`Type03` の 3 つの値コンストラクタを持ち、`FizzBuzzType` 型の値はこの 3 つのいずれかです。Java の `enum` や Rust の `enum` に近い概念ですが、Haskell の代数的データ型はフィールドを持つこともできるため、より柔軟です。

`deriving (Show, Eq)` は、型クラスのインスタンスを自動導出するディレクティブです。

| 型クラス | 提供される機能 | 他言語の相当物 |
|----------|---------------|---------------|
| `Show` | 値を文字列に変換（`show Type01` → `"Type01"`） | Java の `toString()` |
| `Eq` | 値の等値比較（`Type01 == Type01` → `True`） | Java の `equals()` |

### Red: タイプ別テストの作成

新しいテストファイル `test/FizzBuzz/TypeSpec.hs` を作成します。

```haskell
-- test/FizzBuzz/TypeSpec.hs
module FizzBuzz.TypeSpec (spec) where

import Test.Hspec
import FizzBuzz.Type

spec :: Spec
spec = do
  describe "Type01" $ do
    it "1 を渡すと '1' を返す" $
      generate Type01 1 `shouldBe` "1"
```

```bash
$ stack test

test/FizzBuzz/TypeSpec.hs:4:8: error:
    Could not find module 'FizzBuzz.Type'
```

モジュールが存在しないためコンパイルエラーになります。

### Green: 型クラスと instance の定義

`src/FizzBuzz/Type.hs` を作成します。

```haskell
-- src/FizzBuzz/Type.hs
module FizzBuzz.Type
  ( FizzBuzzType(..)
  , Generatable(..)
  ) where

data FizzBuzzType = Type01 | Type02 | Type03
  deriving (Show, Eq)

class Generatable a where
  generate :: a -> Int -> String

instance Generatable FizzBuzzType where
  generate Type01 n
    | n `mod` 15 == 0 = "FizzBuzz"
    | n `mod` 3 == 0  = "Fizz"
    | n `mod` 5 == 0  = "Buzz"
    | otherwise        = show n
  generate Type02 n = show n
  generate Type03 n
    | n `mod` 15 == 0 = "FizzBuzz"
    | n `mod` 3 == 0  = "Fizz"
    | otherwise        = show n
```

ここで登場するのが **型クラス**（type class）です。

```haskell
class Generatable a where
  generate :: a -> Int -> String
```

`Generatable` は「`generate` 関数を持つ型」を表すインターフェースです。Java のインターフェースや Rust のトレイトに相当しますが、型クラスはデータ型の定義とは独立に後からインスタンスを追加できる点が異なります。これを **アドホックポリモーフィズム** と呼びます。

```haskell
instance Generatable FizzBuzzType where
  generate Type01 n = ...
  generate Type02 n = ...
  generate Type03 n = ...
```

`instance` 宣言で、`FizzBuzzType` が `Generatable` 型クラスのインスタンスであることを定義します。`generate` 関数の第 1 引数でパターンマッチを行い、タイプごとに異なるロジックを実装しています。

| 概念 | Haskell | Java | Rust | Clojure |
|------|---------|------|------|---------|
| 型の列挙 | `data ... = A \| B` | `enum` | `enum` | `defrecord` |
| インターフェース | `class` (型クラス) | `interface` | `trait` | `defprotocol` |
| 実装 | `instance` | `implements` | `impl ... for` | `extend-type` |

テストを実行します。

```bash
$ stack test
FizzBuzz.TypeSpec
  Type01
    1 を渡すと '1' を返す

Finished in 0.0001 seconds
1 example, 0 failures
```

テストが通りました。

### タイプ 1 のテストを拡充

```haskell
spec :: Spec
spec = do
  describe "Type01" $ do
    it "1 を渡すと '1' を返す" $
      generate Type01 1 `shouldBe` "1"

    it "3 の倍数を渡すと 'Fizz' を返す" $
      generate Type01 3 `shouldBe` "Fizz"

    it "5 の倍数を渡すと 'Buzz' を返す" $
      generate Type01 5 `shouldBe` "Buzz"

    it "15 の倍数を渡すと 'FizzBuzz' を返す" $
      generate Type01 15 `shouldBe` "FizzBuzz"
```

```bash
$ stack test
FizzBuzz.TypeSpec
  Type01
    1 を渡すと '1' を返す
    3 の倍数を渡すと 'Fizz' を返す
    5 の倍数を渡すと 'Buzz' を返す
    15 の倍数を渡すと 'FizzBuzz' を返す

Finished in 0.0001 seconds
4 examples, 0 failures
```

**TODO リスト**:

- [x] タイプ 1 の場合（通常の FizzBuzz）
- [ ] タイプ 2 の場合（数字のみ）
- [ ] タイプ 3 の場合（FizzBuzz の場合のみ）
- [ ] それ以外のタイプの場合（エラーを返す）
- [ ] 値オブジェクト FizzBuzzValue の導入
- [ ] コレクション FizzBuzzList の導入

### タイプ 2・タイプ 3 のテスト

タイプ 2（数字のみ）とタイプ 3（FizzBuzz の場合のみ）のテストを追加します。

```haskell
  describe "Type02" $ do
    it "数値を文字列に変換する" $
      generate Type02 1 `shouldBe` "1"

  describe "Type03" $ do
    it "1 を渡すと '1' を返す" $
      generate Type03 1 `shouldBe` "1"

    it "3 を渡すと 'Fizz' を返す" $
      generate Type03 3 `shouldBe` "Fizz"

    it "15 の倍数で 'FizzBuzz' を返す" $
      generate Type03 15 `shouldBe` "FizzBuzz"
```

```bash
$ stack test
FizzBuzz.TypeSpec
  Type01
    1 を渡すと '1' を返す
    3 の倍数を渡すと 'Fizz' を返す
    5 の倍数を渡すと 'Buzz' を返す
    15 の倍数を渡すと 'FizzBuzz' を返す
  Type02
    数値を文字列に変換する
  Type03
    1 を渡すと '1' を返す
    3 を渡すと 'Fizz' を返す
    15 の倍数で 'FizzBuzz' を返す

Finished in 0.0001 seconds
8 examples, 0 failures
```

タイプ 2 は `show n` で数値をそのまま文字列にするだけです。タイプ 3 は 5 の倍数の判定がなく、`Buzz` を返しません。`generate Type03 5` は `"5"` を返します。

**TODO リスト**:

- [x] タイプ 1 の場合（通常の FizzBuzz）
- [x] タイプ 2 の場合（数字のみ）
- [x] タイプ 3 の場合（FizzBuzz の場合のみ）
- [ ] それ以外のタイプの場合（エラーを返す）
- [ ] 値オブジェクト FizzBuzzValue の導入
- [ ] コレクション FizzBuzzList の導入

## 7.4 値オブジェクト — FizzBuzzValue

### レコード構文によるデータ型

数値と変換結果を 1 つにまとめた値オブジェクトを定義します。Haskell の **レコード構文** を使います。

```haskell
data FizzBuzzValue = FizzBuzzValue
  { number :: Int
  , value  :: String
  } deriving (Show, Eq)
```

レコード構文は、フィールド名付きのデータ型を定義する構文です。各フィールド名はそのまま **アクセサ関数** として利用できます。

```haskell
let v = FizzBuzzValue { number = 3, value = "Fizz" }
number v  -- 3
value v   -- "Fizz"
```

これは Java のレコードクラスや Rust の構造体に相当します。

| 概念 | Haskell | Java | Rust |
|------|---------|------|------|
| 定義 | `data FizzBuzzValue = FizzBuzzValue { ... }` | `record FizzBuzzValue(...)` | `struct FizzBuzzValue { ... }` |
| アクセス | `number v` | `v.number()` | `v.number` |
| 等値比較 | `deriving Eq` | 自動生成 | `#[derive(PartialEq)]` |
| 文字列表現 | `deriving Show` | 自動生成 | `#[derive(Debug)]` |

### valueToString の定義

値オブジェクトから文字列を取り出すヘルパー関数を定義します。

```haskell
valueToString :: FizzBuzzValue -> String
valueToString = value
```

`valueToString = value` は **ポイントフリースタイル** の記法です。`valueToString v = value v` と同等ですが、引数を省略して関数の合成を直接表現しています。

**TODO リスト**:

- [x] タイプ 1 の場合（通常の FizzBuzz）
- [x] タイプ 2 の場合（数字のみ）
- [x] タイプ 3 の場合（FizzBuzz の場合のみ）
- [ ] それ以外のタイプの場合（エラーを返す）
- [x] 値オブジェクト FizzBuzzValue の導入
- [ ] コレクション FizzBuzzList の導入

## 7.5 ファーストクラスコレクション — FizzBuzzList

### newtype による薄いラッパー

FizzBuzz の結果リストを表す専用のコレクション型を定義します。ここでは `data` ではなく `newtype` を使います。

```haskell
newtype FizzBuzzList = FizzBuzzList
  { values :: [FizzBuzzValue]
  } deriving (Show, Eq)
```

`newtype` は **フィールドが 1 つだけ** のデータ型を定義するキーワードです。`data` との違いは以下の通りです。

| 特性 | `data` | `newtype` |
|------|--------|-----------|
| フィールド数 | 複数可 | 1 つのみ |
| ランタイムコスト | 新しいデータ構造を生成 | コンパイル時に消去（ゼロコスト） |
| 用途 | 一般的なデータ型 | 既存型の薄いラッパー |

`newtype` はランタイムでは内部の型とまったく同じ表現になるため、パフォーマンスのオーバーヘッドがありません。にもかかわらず、型安全性は確保されます。`[FizzBuzzValue]` と `FizzBuzzList` は異なる型として扱われるため、誤って別のリストを渡すことはできません。

このパターンは **ファーストクラスコレクション** と呼ばれます。生のリストをドメイン固有の型で包むことで、リストに対する操作を集約し、不正な操作を型レベルで防ぎます。

### listCount と createList

```haskell
listCount :: FizzBuzzList -> Int
listCount = length . values

createList :: Int -> FizzBuzzType -> FizzBuzzList
createList count fbType =
  FizzBuzzList $ map createValue [1..count]
  where
    createValue n = FizzBuzzValue { number = n, value = generate fbType n }
```

`listCount = length . values` は関数合成（`.`）を使っています。`values` でリストを取り出し、`length` で要素数を数えます。`where` 句はローカル定義を行う構文で、`createValue` は `createList` の内部でのみ使われるヘルパー関数です。

`$` は **関数適用演算子** で、右結合の低い優先順位を持ちます。`FizzBuzzList $ map createValue [1..count]` は `FizzBuzzList (map createValue [1..count])` と同等ですが、括弧を減らして読みやすくするために使います。

**TODO リスト**:

- [x] タイプ 1 の場合（通常の FizzBuzz）
- [x] タイプ 2 の場合（数字のみ）
- [x] タイプ 3 の場合（FizzBuzz の場合のみ）
- [ ] それ以外のタイプの場合（エラーを返す）
- [x] 値オブジェクト FizzBuzzValue の導入
- [x] コレクション FizzBuzzList の導入

## 7.6 型クラスの仕組み

型クラスのメカニズムをもう少し深掘りしましょう。

### アドホックポリモーフィズム

型クラスが提供するポリモーフィズムは **アドホックポリモーフィズム** と呼ばれます。「アドホック」とは「その場限りの」という意味で、型ごとに異なる実装を提供できることを指します。

```haskell
class Generatable a where
  generate :: a -> Int -> String
```

この定義は「`Generatable` のインスタンスである型 `a` は、`a -> Int -> String` という型の `generate` 関数を持つ」と読みます。

### パラメトリックポリモーフィズムとの違い

Haskell には 2 種類のポリモーフィズムがあります。

| 種類 | 例 | 特徴 |
|------|-----|------|
| パラメトリック | `length :: [a] -> Int` | すべての型に対して同じ実装 |
| アドホック | `generate :: Generatable a => a -> Int -> String` | 型ごとに異なる実装 |

`length` はリストの要素の型に関係なく同じアルゴリズムで動作します。一方、`generate` は `FizzBuzzType` の値（`Type01`、`Type02`、`Type03`）ごとに異なるロジックを実行します。

### 辞書渡し

GHC は型クラスを内部的に **辞書渡し**（dictionary passing）で実装しています。`generate Type01 3` というコードは、コンパイル時に「`FizzBuzzType` 用の `Generatable` 辞書」を暗黙的に渡すコードに変換されます。ランタイムでの仮想関数テーブルルックアップ（Java の vtable）とは異なり、GHC はインスタンスが静的に決まる場合に辞書をインライン化してオーバーヘッドを除去できます。

## 7.7 deriving の仕組み

```haskell
data FizzBuzzType = Type01 | Type02 | Type03
  deriving (Show, Eq)
```

`deriving` は、コンパイラに型クラスのインスタンスを自動生成させるディレクティブです。

```haskell
-- deriving (Show) が自動生成するコード（概念的）
instance Show FizzBuzzType where
  show Type01 = "Type01"
  show Type02 = "Type02"
  show Type03 = "Type03"

-- deriving (Eq) が自動生成するコード（概念的）
instance Eq FizzBuzzType where
  Type01 == Type01 = True
  Type02 == Type02 = True
  Type03 == Type03 = True
  _ == _ = False
```

`deriving` できる型クラスは限られています。`Show`、`Eq`、`Ord`、`Enum`、`Bounded`、`Read` などの標準型クラスが対象です。独自の型クラス（`Generatable` など）は `deriving` できないため、`instance` 宣言を手書きする必要があります。

## 7.8 まとめ

この章では以下のことを学びました。

- **代数的データ型**（`data`）で FizzBuzz のタイプを直和型として定義する
- **型クラス**（`class`）でアドホックポリモーフィズムのインターフェースを定義する
- **instance** 宣言でタイプごとの `generate` 実装を分離する
- **レコード構文** で値オブジェクト `FizzBuzzValue` を定義し、フィールドアクセサを自動生成する
- **newtype** でファーストクラスコレクション `FizzBuzzList` をゼロコストで定義する
- **deriving** で `Show`、`Eq` のインスタンスを自動導出する
- パラメトリックポリモーフィズムとアドホックポリモーフィズムの違い

次章では、ガード式とパターンマッチの詳細を掘り下げ、`Either` 型によるエラーハンドリングを導入します。
