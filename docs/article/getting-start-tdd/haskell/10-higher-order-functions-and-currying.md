---
type: Article
title: "第 10 章: 高階関数とカリー化"
description: "第 3 部では代数的データ型、型クラス、パターンマッチ、モジュール設計を学びました。"
tags: [article, getting-start-tdd, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 10 章: 高階関数とカリー化

## 10.1 はじめに

第 3 部では代数的データ型、型クラス、パターンマッチ、モジュール設計を学びました。この章からは関数型プログラミングの本格的なテクニックに入ります。

Haskell は純粋関数型言語であり、関数は**ファーストクラスオブジェクト**です。関数を変数に束縛する、引数として渡す、戻り値として返す、といった操作がすべて自然に行えます。この章では高階関数とカリー化を中心に、FizzBuzz の実装を拡張します。

### この章で学ぶこと

- 高階関数の概念（関数を引数に取る、関数を返す）
- `map`、`filter`、`foldl`/`foldr` の使い方
- `generateWith` による高階関数の実践
- カリー化と部分適用
- `transform` と `filterList` の実装とテスト

## 10.2 高階関数とは

高階関数（higher-order function）とは、以下のいずれかを満たす関数です。

1. **関数を引数として受け取る**
2. **関数を戻り値として返す**

Haskell の標準ライブラリには多くの高階関数が用意されています。まず最も基本的な `map`、`filter`、`foldl`/`foldr` を FizzBuzz の文脈で使ってみましょう。

### map — リストの変換

`map` は関数とリストを受け取り、リストの各要素に関数を適用した新しいリストを返します。

```haskell
map :: (a -> b) -> [a] -> [b]
```

型シグネチャを読むと、`map` は「`a` 型を `b` 型に変換する関数」と「`a` 型のリスト」を受け取り、「`b` 型のリスト」を返すことがわかります。`a` と `b` は型変数であり、任意の型を取れます。

FizzBuzz リストに感嘆符を付加する例を見てみましょう。

```haskell
-- FizzBuzz リストの各要素に "!" を付加する
map (++ "!") (generateList 5)
-- => ["1!", "2!", "Fizz!", "4!", "Buzz!"]
```

`generateList` の定義自体も `map` を使っています。

```haskell
generateList :: Int -> [String]
generateList n = map generate [1..n]
```

`map generate [1..n]` は「`[1..n]` の各要素に `generate` を適用する」という意味です。これは命令型の for ループと同等ですが、より宣言的に記述できます。

### filter — リストの絞り込み

`filter` は述語関数（predicate）とリストを受け取り、述語を満たす要素だけを含む新しいリストを返します。

```haskell
filter :: (a -> Bool) -> [a] -> [a]
```

FizzBuzz リストから "Fizz" だけを抽出する例です。

```haskell
filter (== "Fizz") (generateList 15)
-- => ["Fizz", "Fizz", "Fizz", "Fizz"]
```

数値だけを抽出する例も見てみましょう。

```haskell
-- "Fizz"、"Buzz"、"FizzBuzz" でない要素を抽出
filter (\s -> s /= "Fizz" && s /= "Buzz" && s /= "FizzBuzz") (generateList 15)
-- => ["1", "2", "4", "7", "8", "11", "13", "14"]
```

ここで `\s -> ...` はラムダ式（無名関数）です。`\` は数学のラムダ記号（λ）を ASCII で表現したものです。

### foldl / foldr — リストの畳み込み

`foldl` と `foldr` はリストの全要素を 1 つの値に集約する関数です。

```haskell
foldl :: (b -> a -> b) -> b -> [a] -> b
foldr :: (a -> b -> b) -> b -> [a] -> b
```

`foldl` は左から右へ、`foldr` は右から左へ畳み込みます。FizzBuzz リストの集計に使ってみましょう。

```haskell
-- "Fizz" の出現回数をカウントする
foldl (\count s -> if s == "Fizz" then count + 1 else count) 0 (generateList 15)
-- => 4
```

```haskell
-- FizzBuzz リストをカンマ区切りの文字列に結合する
foldl (\acc s -> if null acc then s else acc ++ ", " ++ s) "" (generateList 5)
-- => "1, 2, Fizz, 4, Buzz"
```

`foldl` と `foldr` の違いは畳み込みの方向と結合の仕方にあります。

| 関数 | 畳み込み方向 | 評価順序 | 無限リスト |
|------|------------|---------|----------|
| `foldl` | 左から右 | `((((z `f` x1) `f` x2) `f` x3) ...)` | 使用不可 |
| `foldr` | 右から左 | `(x1 `f` (x2 `f` (x3 `f` ... z)))` | 条件付きで使用可 |

実用上は `Data.List` の `foldl'`（正格版 `foldl`）が推奨されます。`foldl` は遅延評価のためサンク（未評価の計算）が蓄積し、大きなリストでスタックオーバーフローを起こす可能性があります。

## 10.3 generateWith — 高階関数の実践

### Red: カスタムルールのテスト

`generateWith` は「FizzBuzz の生成ルールを外部から注入する」高階関数です。まずテストを書きます。

```haskell
-- test/FizzBuzz/FizzBuzzSpec.hs（抜粋）
describe "generateWith" $ do
  it "カスタムルールで生成できる" $ do
    let rule n = if even n then "Even" else "Odd"
    generateWith rule 2 `shouldBe` "Even"
    generateWith rule 3 `shouldBe` "Odd"
```

### Green: generateWith の実装

```haskell
-- src/FizzBuzz.hs
generateWith :: (Int -> String) -> Int -> String
generateWith rule = rule
```

型シグネチャを分解すると以下のようになります。

```
generateWith :: (Int -> String) -> Int -> String
               ^^^^^^^^^^^^^^^^    ^^^    ^^^^^^
               第 1 引数:          第 2 引数  戻り値
               ルール関数          数値      文字列
```

`(Int -> String)` という型は「`Int` を受け取って `String` を返す関数」を表します。これが「関数を引数に取る」高階関数の典型的な形です。

### 実践: デフォルトルールの利用

`generateWith` に `generate` を渡せば、通常の FizzBuzz と同じ結果が得られます。

```haskell
generateWith generate 15
-- => "FizzBuzz"

generateWith generate 3
-- => "Fizz"
```

カスタムルールも自由に定義できます。

```haskell
-- 偶数・奇数ルール
let evenOdd n = if even n then "Even" else "Odd"
generateWith evenOdd 4
-- => "Even"

-- すべて大文字に変換するルール
import Data.Char (toUpper)
let shoutRule n = map toUpper (generate n)
generateWith shoutRule 3
-- => "FIZZ"
```

## 10.4 カリー化と部分適用

### すべての関数はカリー化されている

Haskell では、すべての関数はデフォルトで**カリー化**（curried）されています。つまり、複数の引数を取る関数は「1 つの引数を取り、残りの引数を取る関数を返す関数」として定義されています。

```haskell
-- 以下の 2 つの定義は等価
add :: Int -> Int -> Int
add x y = x + y

-- 明示的にカリー化した形
add' :: Int -> (Int -> Int)
add' x = \y -> x + y
```

型シグネチャ `Int -> Int -> Int` は、実際には `Int -> (Int -> Int)` と読みます。`->` は右結合なので、括弧を省略できるのです。

### 部分適用

カリー化された関数に引数を 1 つだけ渡すと、残りの引数を待つ新しい関数が得られます。これが**部分適用**（partial application）です。

```haskell
-- add に 1 だけ渡す → 「1 を加える関数」が得られる
increment :: Int -> Int
increment = add 1

increment 5
-- => 6
```

FizzBuzz の文脈で部分適用を使ってみましょう。

```haskell
-- map に generate を渡す → 「リストの各要素に generate を適用する関数」が得られる
fizzBuzzAll :: [Int] -> [String]
fizzBuzzAll = map generate

fizzBuzzAll [1, 2, 3, 15]
-- => ["1", "2", "Fizz", "FizzBuzz"]
```

```haskell
-- filter に条件を渡す → 「Fizz を含む要素だけを残す関数」が得られる
onlyFizz :: [String] -> [String]
onlyFizz = filter (== "Fizz")

onlyFizz (generateList 15)
-- => ["Fizz", "Fizz", "Fizz", "Fizz"]
```

### セクション

演算子の部分適用を**セクション**（section）と呼びます。

```haskell
-- (+ 1) は \x -> x + 1 と同じ
map (+ 1) [1, 2, 3]
-- => [2, 3, 4]

-- (== "Fizz") は \x -> x == "Fizz" と同じ
filter (== "Fizz") (generateList 15)
-- => ["Fizz", "Fizz", "Fizz", "Fizz"]

-- (`mod` 3) は \x -> x `mod` 3 と同じ
map (`mod` 3) [1..9]
-- => [1, 2, 0, 1, 2, 0, 1, 2, 0]
```

## 10.5 transform と filterList の実装とテスト

### Red: transform のテスト

`transform` は `map` をラップした関数です。まずテストを書きます。

```haskell
-- test/FizzBuzz/FizzBuzzSpec.hs（抜粋）
describe "transform" $ do
  it "リストを変換できる" $ do
    let result = transform (++ "!") ["Fizz", "Buzz"]
    result `shouldBe` ["Fizz!", "Buzz!"]
```

### Green: transform の実装

```haskell
-- src/FizzBuzz.hs
transform :: (a -> b) -> [a] -> [b]
transform = map
```

`transform` の型シグネチャは `map` と同じです。`transform = map` は、`transform` を `map` の別名として定義しています。これは**ポイントフリー**（引数を明示しない）スタイルの定義です。次の章で詳しく解説します。

### Red: filterList のテスト

```haskell
-- test/FizzBuzz/FizzBuzzSpec.hs（抜粋）
describe "filterList" $ do
  it "リストをフィルタリングできる" $ do
    let result = filterList (/= "Fizz") (generateList 5)
    result `shouldBe` ["1", "2", "4", "Buzz"]
```

### Green: filterList の実装

```haskell
-- src/FizzBuzz.hs
filterList :: (a -> Bool) -> [a] -> [a]
filterList = filter
```

### テストの実行

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  generateWith
    カスタムルールで生成できる
  transform
    リストを変換できる
  filterList
    リストをフィルタリングできる

Finished in 0.0002 seconds
3 examples, 0 failures
```

## 10.6 高階関数の組み合わせ

高階関数を組み合わせると、複雑なデータ変換を宣言的に記述できます。

### FizzBuzz の集計

```haskell
-- FizzBuzz リストを集計する
import Data.Map (Map)
import qualified Data.Map as Map

summarize :: Int -> Map String Int
summarize n =
  foldl
    (\acc s -> Map.insertWith (+) (classify s) 1 acc)
    Map.empty
    (generateList n)
  where
    classify "Fizz"     = "Fizz"
    classify "Buzz"     = "Buzz"
    classify "FizzBuzz" = "FizzBuzz"
    classify _          = "Number"

summarize 15
-- => fromList [("Buzz",2),("Fizz",4),("FizzBuzz",1),("Number",8)]
```

### FizzBuzz リストの加工パイプライン

```haskell
-- 1 から 30 までの FizzBuzz リストから、
-- "Fizz" または "Buzz" の要素だけを取り出し、
-- 大文字に変換して、件数を数える
import Data.Char (toUpper)

result =
  length
    (map (map toUpper)
      (filter (\s -> s == "Fizz" || s == "Buzz")
        (generateList 30)))
-- => 14
```

括弧が深くなりがちですが、これは次の章で学ぶ関数合成と `$` 演算子で解決できます。

## 10.7 他言語との比較

### Java の Stream API

Java 8 以降の Stream API は Haskell の `map`、`filter`、`fold` に対応する操作を提供します。

```java
// Java
List<String> result = IntStream.rangeClosed(1, 15)
    .mapToObj(FizzBuzz::generate)
    .filter(s -> s.equals("Fizz"))
    .collect(Collectors.toList());
```

```haskell
-- Haskell
result = filter (== "Fizz") (map generate [1..15])
```

Java の Stream API は**メソッドチェーン**で処理を繋ぎます。Haskell では関数の組み合わせ（合成やネスト）で同じことを実現します。

| 概念 | Haskell | Java |
|------|---------|------|
| 変換 | `map f xs` | `stream.map(f)` |
| 絞り込み | `filter p xs` | `stream.filter(p)` |
| 畳み込み | `foldl f z xs` | `stream.reduce(z, f)` |
| 遅延評価 | デフォルト | `Stream` で明示 |

### Python の map / filter

Python にも組み込みの `map` と `filter` がありますが、Python ではリスト内包表記が好まれます。

```python
# Python
result = [generate(n) for n in range(1, 16) if generate(n) == "Fizz"]

# map / filter を使う場合
result = list(filter(lambda s: s == "Fizz", map(generate, range(1, 16))))
```

```haskell
-- Haskell（リスト内包表記も使える）
result = [s | n <- [1..15], let s = generate n, s == "Fizz"]

-- map / filter を使う場合
result = filter (== "Fizz") (map generate [1..15])
```

Haskell のリスト内包表記は Python と似た構文ですが、ガード条件の記述がより自然です。

### Elixir の Enum モジュール

Elixir は `Enum.map`、`Enum.filter`、`Enum.reduce` とパイプライン演算子 `|>` を組み合わせます。

```elixir
# Elixir
1..15
|> Enum.map(&FizzBuzz.generate/1)
|> Enum.filter(&(&1 == "Fizz"))
|> Enum.count()
```

```haskell
-- Haskell
length (filter (== "Fizz") (map generate [1..15]))
```

Elixir のパイプライン演算子は処理の流れを左から右へ読める点が直感的です。Haskell では関数合成 `.` や関数適用 `$` で同様の可読性を実現します（次章で詳述）。

### カリー化の比較

| 概念 | Haskell | Java | Python | Elixir |
|------|---------|------|--------|--------|
| カリー化 | デフォルト | なし | なし | なし |
| 部分適用 | 自然に可能 | ラムダで模倣 | `functools.partial` | `&fun/arity` + クロージャ |
| セクション | `(+ 1)`, `(== "Fizz")` | なし | なし | `&(&1 + 1)` |

Haskell ではカリー化がデフォルトのため、部分適用が非常に簡潔に書けます。他の言語ではクロージャやヘルパー関数で模倣する必要があります。

## 10.8 まとめ

この章では以下のことを学びました。

- **高階関数** は関数を引数に取るか、関数を返す関数である
- `map` はリストの変換、`filter` はリストの絞り込み、`foldl`/`foldr` はリストの畳み込みを行う
- `generateWith` は生成ルールを外部から注入する高階関数である
- Haskell の関数は **デフォルトでカリー化** されており、**部分適用** が自然に行える
- **セクション** は演算子の部分適用を簡潔に書く構文である
- `transform` と `filterList` は `map` と `filter` のラッパーとして、ポイントフリースタイルで定義できる
- Java の Stream API、Python の map/filter、Elixir の Enum モジュールと比較すると、Haskell ではカリー化と部分適用により最も簡潔に高階関数を扱える

次の章では、関数合成演算子 `.` とポイントフリースタイルを学び、括弧のネストを解消する方法を見ていきます。
