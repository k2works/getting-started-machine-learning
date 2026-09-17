---
type: Article
title: "第 11 章: 関数合成とポイントフリースタイル"
description: "前章では高階関数とカリー化を学びました。"
tags: [article, getting-start-tdd, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 11 章: 関数合成とポイントフリースタイル

## 11.1 はじめに

前章では高階関数とカリー化を学びました。`map`、`filter`、`foldl` を組み合わせた処理は強力ですが、括弧のネストが深くなるという問題がありました。

```haskell
-- 括弧が深くなりがち
length (filter (== "Fizz") (map generate [1..15]))
```

この章では、**関数合成演算子** `(.)` と **関数適用演算子** `($)` を使って、このような処理をより読みやすく書く方法を学びます。さらに、引数を明示しない**ポイントフリースタイル**と、`compose` 関数の実装を通じて、Haskell の関数合成の本質を理解します。

### この章で学ぶこと

- 関数合成演算子 `(.)` の仕組み
- 関数適用演算子 `($)` による括弧の削減
- ポイントフリースタイルの定義と活用
- `compose` 関数の実装
- パイプライン的な処理の関数合成への書き換え

## 11.2 関数合成演算子 (.)

### 数学的な背景

関数合成は数学の概念に直接対応します。数学で `(f ∘ g)(x) = f(g(x))` と書くように、Haskell では `(f . g) x = f (g x)` と書きます。

### 型シグネチャ

```haskell
(.) :: (b -> c) -> (a -> b) -> a -> c
```

この型シグネチャを分解しましょう。

```
(.) :: (b -> c)    -- 第 1 引数: b を c に変換する関数 f
    -> (a -> b)    -- 第 2 引数: a を b に変換する関数 g
    -> a           -- 第 3 引数: 入力値
    -> c           -- 戻り値: f(g(x)) の結果
```

つまり `(.)` は「2 つの関数を受け取り、それらを連結した新しい関数を返す」高階関数です。

### 基本的な使い方

```haskell
-- 2 つの関数を合成する
addExclaim :: String -> String
addExclaim = (++ "!")

toLength :: String -> Int
toLength = length

-- 文字列の長さを計算してから 1 を足す... ではなく、
-- まず文字列に "!" を付加してから長さを計算する
exclaimThenLength :: String -> Int
exclaimThenLength = toLength . addExclaim

exclaimThenLength "Fizz"
-- => 5  ("Fizz!" の長さ)
```

**注意**: `f . g` は「まず `g` を適用し、その結果に `f` を適用する」という順序です。右から左に読みます。

### FizzBuzz での活用

前章の括弧が深い例を関数合成で書き直してみましょう。

```haskell
-- Before: 括弧のネスト
countFizz :: Int -> Int
countFizz n = length (filter (== "Fizz") (map generate [1..n]))

-- After: 関数合成
countFizz' :: Int -> Int
countFizz' n = (length . filter (== "Fizz") . map generate) [1..n]
```

関数合成版は「`map generate` → `filter (== "Fizz")` → `length`」というパイプラインを右から左に表現しています。

もう 1 つの例を見ましょう。

```haskell
import Data.Char (toUpper)

-- FizzBuzz リストを大文字に変換する
shoutFizzBuzz :: [String] -> [String]
shoutFizzBuzz = map (map toUpper)

-- "Fizz" または "Buzz" だけを抽出する
onlyFizzOrBuzz :: [String] -> [String]
onlyFizzOrBuzz = filter (\s -> s == "Fizz" || s == "Buzz")

-- 合成: 抽出してから大文字にする
shoutOnlyFizzBuzz :: [String] -> [String]
shoutOnlyFizzBuzz = shoutFizzBuzz . onlyFizzOrBuzz

shoutOnlyFizzBuzz (generateList 15)
-- => ["FIZZ","BUZZ","FIZZ","FIZZ","BUZZ","FIZZ","FIZZ","BUZZ","FIZZ"]
```

## 11.3 関数適用演算子 ($)

### 型シグネチャ

```haskell
($) :: (a -> b) -> a -> b
f $ x = f x
```

`($)` は関数適用そのものです。通常の関数適用 `f x` と同じ結果を返します。では何のために存在するのでしょうか。

### 優先順位による括弧の削減

通常の関数適用は最も高い優先順位を持ち、左結合です。一方、`$` は最も低い優先順位を持ち、右結合です。

| 演算子 | 優先順位 | 結合性 |
|--------|---------|--------|
| 関数適用 (空白) | 10（最高） | 左結合 |
| `.` (関数合成) | 9 | 右結合 |
| `$` (関数適用) | 0（最低） | 右結合 |

この性質を利用して、右端の括弧を削除できます。

```haskell
-- Before: 括弧のネスト
length (filter (== "Fizz") (map generate [1..15]))

-- After: $ で右端の括弧を除去
length $ filter (== "Fizz") $ map generate [1..15]
```

`$` を使うと「右側の式をすべて評価してから、左側の関数に渡す」と読めます。

### (.) と ($) の組み合わせ

`(.)` と `($)` を組み合わせると、さらに読みやすくなります。

```haskell
-- (.) で関数パイプラインを構築し、($) で引数を渡す
countFizz :: Int -> Int
countFizz = length . filter (== "Fizz") . map generate . enumFromTo 1

-- 使用時
countFizz 15
-- => 4

-- インラインで使う場合
length . filter (== "Fizz") . map generate $ [1..15]
-- => 4
```

最後の例では、`(.)` で合成された関数パイプラインに `($)` でリスト `[1..15]` を渡しています。

## 11.4 ポイントフリースタイル

### ポイントフリーとは

**ポイントフリースタイル**（point-free style）とは、関数の定義において引数（ポイント）を明示しないスタイルです。「ポイント」はトポロジー由来の用語で、ここでは「関数が作用する値」を意味します。

```haskell
-- ポイントあり（引数 n を明示）
countFizz :: Int -> Int
countFizz n = length (filter (== "Fizz") (map generate [1..n]))

-- ポイントフリー（引数を明示しない）
countFizz' :: Int -> Int
countFizz' = length . filter (== "Fizz") . map generate . enumFromTo 1
```

ポイントフリースタイルは「何をするか」ではなく「どの関数をどう組み合わせるか」に焦点を当てます。

### 前章の実装を振り返る

前章で定義した `transform` と `filterList` はすでにポイントフリースタイルでした。

```haskell
-- ポイントフリースタイル
transform :: (a -> b) -> [a] -> [b]
transform = map

filterList :: (a -> Bool) -> [a] -> [a]
filterList = filter
```

これらは引数を一切明示していません。`transform` は `map` そのものであり、`filterList` は `filter` そのものです。ポイントあり（明示的）に書き直すと以下のようになります。

```haskell
-- ポイントありスタイル（等価）
transform' :: (a -> b) -> [a] -> [b]
transform' f xs = map f xs

filterList' :: (a -> Bool) -> [a] -> [a]
filterList' p xs = filter p xs
```

`transform f xs = map f xs` の両辺から `xs` を消去し、`transform f = map f` とし、さらに `f` も消去すると `transform = map` になります。これがポイントフリー化の過程です。

### ポイントフリーの利点と注意点

**利点**:

- 関数の構造が明確になる（「この関数は `map` と同じ」と一目でわかる）
- 不要な中間変数名を考えなくてよい
- 関数合成のパイプラインが読みやすくなる

**注意点**:

- 過度なポイントフリー化は逆に読みにくくなる
- 型エラーのメッセージが分かりにくくなることがある
- 複雑な条件分岐がある場合は引数を明示した方がよい

```haskell
-- 適度なポイントフリー（読みやすい）
onlyFizz :: [String] -> [String]
onlyFizz = filter (== "Fizz")

-- 過度なポイントフリー（読みにくい）
-- foo = (. (>>= (return .))) . (.) . (.)
-- ↑ 何をしているかわからない
```

HLint は適切なポイントフリー化を提案してくれるため、HLint の提案に従うのが実用的です。

## 11.5 compose 関数の実装

### Red: compose のテスト

`compose` は関数合成演算子 `(.)` をラップした関数です。

```haskell
-- test/FizzBuzz/FizzBuzzSpec.hs（抜粋）
describe "compose" $ do
  it "2 つの関数を合成できる" $ do
    let addExclaim = (++ "!")
        toUpper' = map (\c -> if isAsciiLower c then toEnum (fromEnum c - 32) else c)
        composed = compose addExclaim toUpper'
    composed "fizz" `shouldBe` "FIZZ!"
```

このテストは、`toUpper'` で小文字を大文字に変換してから、`addExclaim` で `"!"` を付加する合成関数を検証しています。

### Green: compose の実装

```haskell
-- src/FizzBuzz.hs
compose :: (b -> c) -> (a -> b) -> a -> c
compose = (.)
```

`compose` は `(.)` のポイントフリーな別名です。型シグネチャは `(.)` と同じです。

### 実践: compose を使ったパイプライン

```haskell
import Data.Char (toUpper, isAsciiLower)

-- 個別の変換関数
addExclaim :: String -> String
addExclaim = (++ "!")

toUpperStr :: String -> String
toUpperStr = map toUpper

-- compose で合成
shout :: String -> String
shout = compose addExclaim toUpperStr

shout "fizz"
-- => "FIZZ!"

-- 3 つ以上の関数を合成
addBrackets :: String -> String
addBrackets s = "[" ++ s ++ "]"

format :: String -> String
format = compose addBrackets (compose addExclaim toUpperStr)
-- または
format = addBrackets . addExclaim . toUpperStr

format "fizz"
-- => "[FIZZ!]"
```

## 11.6 パイプライン的な処理の書き換え

### 命令型スタイルからの変換

処理の流れを段階的に関数合成へ書き換えてみましょう。

**Step 1: 中間変数を使う（命令型的）**

```haskell
processFizzBuzz :: Int -> [String]
processFizzBuzz n =
  let list     = generateList n
      filtered = filter (\s -> s == "Fizz" || s == "Buzz") list
      upper    = map (map toUpper) filtered
  in  upper
```

**Step 2: 括弧のネスト**

```haskell
processFizzBuzz :: Int -> [String]
processFizzBuzz n =
  map (map toUpper) (filter (\s -> s == "Fizz" || s == "Buzz") (generateList n))
```

**Step 3: $ で括弧を削減**

```haskell
processFizzBuzz :: Int -> [String]
processFizzBuzz n =
  map (map toUpper) $ filter (\s -> s == "Fizz" || s == "Buzz") $ generateList n
```

**Step 4: 関数合成 + ポイントフリー**

```haskell
processFizzBuzz :: Int -> [String]
processFizzBuzz =
  map (map toUpper) . filter (\s -> s == "Fizz" || s == "Buzz") . generateList
```

Step 4 が最も Haskell らしいスタイルです。関数の「つなぎ方」が明確で、データの流れが右から左に読めます。

### データの流れ

```
generateList → filter → map (map toUpper)
    [1..n]      条件     大文字変換
```

関数合成は右から左に適用されるため、コードの並びとデータの流れが一致します。

```haskell
processFizzBuzz = map (map toUpper) . filter isFizzOrBuzz . generateList
--               ^^^^^^^^^^^^^^^^    ^^^^^^^^^^^^^^^^^      ^^^^^^^^^^^^
--               Step 3: 大文字化    Step 2: 絞り込み       Step 1: 生成
```

## 11.7 Elixir のパイプライン演算子との比較

Elixir のパイプライン演算子 `|>` は、左から右にデータを流す演算子です。

```elixir
# Elixir: 左から右にデータが流れる
1..15
|> Enum.map(&FizzBuzz.generate/1)
|> Enum.filter(&(&1 == "Fizz"))
|> Enum.count()
```

```haskell
-- Haskell: 右から左にデータが流れる（関数合成）
length . filter (== "Fizz") . map generate $ [1..15]
```

| 特徴 | Haskell (.) | Elixir |> |
|------|------------|---------|
| データの流れ | 右から左 | 左から右 |
| 本質 | 関数合成（新しい関数を作る） | 関数適用（データを次々に渡す） |
| 型安全性 | コンパイル時に型チェック | 動的型付け（Dialyzer で補助） |
| ポイントフリー | 自然にポイントフリーになる | 常にデータを明示 |

Haskell でも `&` 演算子（`Data.Function` モジュール）を使えば左から右への記述が可能です。

```haskell
import Data.Function ((&))

-- & を使って左から右に書く
[1..15] & map generate & filter (== "Fizz") & length
-- => 4
```

ただし、Haskell のコミュニティでは `(.)` と `($)` を使うスタイルが主流です。`&` は GHCi での探索的プログラミングや、データの流れを強調したい場合に便利です。

## 11.8 まとめ

この章では以下のことを学びました。

- **関数合成演算子 `(.)`** は `(f . g) x = f (g x)` であり、2 つの関数を連結する
- **関数適用演算子 `($)`** は優先順位が最も低く、右端の括弧を削減できる
- `(.)` と `($)` の組み合わせで、括弧のネストを解消し読みやすいコードを書ける
- **ポイントフリースタイル** は引数を明示せずに関数を定義するスタイルである
- `compose` は `(.)` のポイントフリーな別名として実装できる
- 命令型の中間変数スタイルから、関数合成によるポイントフリースタイルまで段階的に変換できる
- Elixir の `|>` は左から右のパイプラインだが、Haskell の `(.)` は右から左の関数合成である

次の章では、モナドとエラーハンドリングを学び、`Maybe`、`Either`、`do` 記法、遅延評価を理解します。
