---
type: Article
title: "第 12 章: モナドとエラーハンドリング"
description: "前章までに高階関数、カリー化、関数合成、ポイントフリースタイルを学びました。"
tags: [article, getting-start-tdd, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 12 章: モナドとエラーハンドリング

## 12.1 はじめに

前章までに高階関数、カリー化、関数合成、ポイントフリースタイルを学びました。この章では Haskell の関数型プログラミングの中核概念である**モナド**を、エラーハンドリングと遅延評価という実践的な文脈で理解します。

モナドは抽象的な概念ですが、「文脈を持つ計算を連鎖させる仕組み」として捉えるとわかりやすくなります。この章では `Maybe` 型、`Either` 型、`do` 記法、遅延評価を順に学び、FizzBuzz の `safeGenerate`、`safeGenerateList`、`lazyStream` の実装とテストを通じて理解を深めます。

### この章で学ぶこと

- `Maybe` 型による安全な値アクセス
- `Either` 型によるエラーハンドリング
- `safeGenerate` / `safeGenerateList` の実装とテスト
- `do` 記法によるモナドの連鎖
- 遅延評価と無限リスト（`lazyStream`）
- 他言語のエラーハンドリングとの比較

## 12.2 Maybe 型 — 値の有無を表現する

### Maybe の定義

`Maybe` 型は「値があるかもしれないし、ないかもしれない」という状態を型で表現します。

```haskell
data Maybe a = Nothing | Just a
```

`Maybe` は 2 つの値コンストラクタを持つ直和型です。

| コンストラクタ | 意味 | 例 |
|--------------|------|-----|
| `Nothing` | 値がない | 検索結果がない、変換に失敗した |
| `Just a` | 値 `a` がある | `Just "Fizz"`、`Just 42` |

### FizzBuzz での活用

リストのインデックスアクセスを安全にする例を見てみましょう。

```haskell
-- 安全なインデックスアクセス
safeIndex :: [a] -> Int -> Maybe a
safeIndex xs i
  | i < 0 || i >= length xs = Nothing
  | otherwise                = Just (xs !! i)

-- 使用例
safeIndex (generateList 5) 2
-- => Just "Fizz"

safeIndex (generateList 5) 10
-- => Nothing
```

通常の `(!!)` はインデックスが範囲外のとき実行時エラーになりますが、`safeIndex` は `Nothing` を返すため、呼び出し側が安全に処理できます。

### Maybe のパターンマッチ

```haskell
displayResult :: Maybe String -> String
displayResult Nothing  = "結果がありません"
displayResult (Just s) = "結果: " ++ s

displayResult (safeIndex (generateList 5) 2)
-- => "結果: Fizz"

displayResult (safeIndex (generateList 5) 10)
-- => "結果がありません"
```

### Maybe はモナドである

`Maybe` はモナドの一種です。モナドであるとは、`>>=`（バインド演算子）が定義されていることを意味します。

```haskell
(>>=) :: Maybe a -> (a -> Maybe b) -> Maybe b
Nothing  >>= _ = Nothing
(Just x) >>= f = f x
```

`>>=` は「`Maybe a` の値が `Just x` なら関数 `f` に `x` を渡し、`Nothing` なら `Nothing` をそのまま返す」という動作をします。

```haskell
-- Maybe の連鎖
safeIndex (generateList 5) 2 >>= \s -> Just (s ++ "!")
-- => Just "Fizz!"

safeIndex (generateList 5) 10 >>= \s -> Just (s ++ "!")
-- => Nothing  (途中で Nothing なので後続の処理はスキップ)
```

`Nothing` が途中で発生すると、以降の処理は自動的にスキップされます。これが「失敗するかもしれない計算の連鎖」です。

## 12.3 Either 型 — エラー情報を保持する

### Either の定義

`Maybe` は「値があるかないか」しか表現できません。エラーの原因を知りたい場合は `Either` を使います。

```haskell
data Either a b = Left a | Right b
```

| コンストラクタ | 慣習的な意味 | 例 |
|--------------|------------|-----|
| `Left a` | エラー（失敗） | `Left "正の整数を指定してください"` |
| `Right b` | 成功 | `Right "Fizz"` |

`Left` がエラー、`Right` が成功という慣習は、英語の "right" が「正しい」を意味することに由来します。

### Either もモナドである

```haskell
(>>=) :: Either a b -> (b -> Either a c) -> Either a c
Left  e >>= _ = Left e
Right x >>= f = f x
```

`Maybe` と同様に、`Left` が途中で発生すると以降の処理はスキップされます。`Maybe` との違いは、`Left` がエラーの情報を保持している点です。

## 12.4 safeGenerate — Either によるエラーハンドリング

### Red: safeGenerate のテスト

```haskell
-- test/FizzBuzz/FizzBuzzSpec.hs（抜粋）
describe "safeGenerate" $ do
  it "正の整数で成功する" $
    safeGenerate 3 `shouldBe` Right "Fizz"

  it "0 以下でエラーを返す" $
    safeGenerate 0 `shouldBe` Left "正の整数を指定してください"

  it "負数でエラーを返す" $
    safeGenerate (-1) `shouldBe` Left "正の整数を指定してください"
```

### Green: safeGenerate の実装

```haskell
-- src/FizzBuzz.hs
safeGenerate :: Int -> Either String String
safeGenerate n
  | n <= 0    = Left "正の整数を指定してください"
  | otherwise = Right (generate n)
```

型シグネチャを分解しましょう。

```
safeGenerate :: Int -> Either String String
               ^^^    ^^^^^^^^^^^^^^^^^^^
               入力    戻り値: エラー(String) または 成功(String)
```

`Either String String` は「エラーメッセージ（`String`）か、FizzBuzz の結果（`String`）のどちらか」を表します。エラーの型と成功の型が異なる場合は、例えば `Either String Int` のように区別されます。

### テストの実行

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  safeGenerate
    正の整数で成功する
    0 以下でエラーを返す
    負数でエラーを返す

3 examples, 0 failures
```

### Either の活用: パターンマッチ

```haskell
-- Either の結果をパターンマッチで処理する
handleResult :: Either String String -> String
handleResult (Left err)    = "エラー: " ++ err
handleResult (Right value) = "成功: " ++ value

handleResult (safeGenerate 3)
-- => "成功: Fizz"

handleResult (safeGenerate 0)
-- => "エラー: 正の整数を指定してください"
```

## 12.5 safeGenerateList — Either のリスト版

### Red: safeGenerateList のテスト

```haskell
-- test/FizzBuzz/FizzBuzzSpec.hs（抜粋）
describe "safeGenerateList" $ do
  it "正の整数で成功する" $
    safeGenerateList 3 `shouldBe` Right ["1", "2", "Fizz"]

  it "0 以下でエラーを返す" $
    safeGenerateList 0 `shouldBe` Left "正の整数を指定してください"
```

### Green: safeGenerateList の実装

```haskell
-- src/FizzBuzz.hs
safeGenerateList :: Int -> Either String [String]
safeGenerateList n
  | n <= 0    = Left "正の整数を指定してください"
  | otherwise = Right (generateList n)
```

`Either String [String]` は「エラーメッセージか、FizzBuzz 文字列のリストのどちらか」を表します。

## 12.6 do 記法によるモナドの連鎖

### do 記法とは

`>>=` を使った連鎖は可読性が低くなることがあります。`do` 記法はモナドの連鎖を命令型プログラミングに近い見た目で記述するための構文糖衣です。

```haskell
-- >>= を使った連鎖
safeGenerate 3 >>= \result1 ->
safeGenerate 5 >>= \result2 ->
Right (result1 ++ " and " ++ result2)
-- => Right "Fizz and Buzz"

-- do 記法で書き直す（等価）
do
  result1 <- safeGenerate 3
  result2 <- safeGenerate 5
  Right (result1 ++ " and " ++ result2)
-- => Right "Fizz and Buzz"
```

`<-` は「モナドから値を取り出す」操作です。`result1 <- safeGenerate 3` は「`safeGenerate 3` が `Right value` なら `value` を `result1` に束縛し、`Left err` ならそのまま `Left err` を返して以降の処理をスキップする」という意味です。

### do 記法の脱糖衣

`do` 記法はコンパイラによって `>>=` に変換されます。

```haskell
-- do 記法
safeFizzBuzzPair :: Int -> Int -> Either String String
safeFizzBuzzPair a b = do
  x <- safeGenerate a
  y <- safeGenerate b
  return (x ++ ", " ++ y)

-- コンパイラが変換する形（等価）
safeFizzBuzzPair' :: Int -> Int -> Either String String
safeFizzBuzzPair' a b =
  safeGenerate a >>= \x ->
  safeGenerate b >>= \y ->
  return (x ++ ", " ++ y)
```

`return` はモナドの文脈に値を包む関数です。`Either` の場合、`return x = Right x` です。

### エラーの自動伝播

`do` 記法の強力な点は、途中でエラー（`Left`）が発生すると自動的に後続の処理がスキップされることです。

```haskell
safeFizzBuzzPair 3 5
-- => Right "Fizz, Buzz"

safeFizzBuzzPair 0 5
-- => Left "正の整数を指定してください"
-- (safeGenerate 0 が Left を返した時点で終了)

safeFizzBuzzPair 3 (-1)
-- => Left "正の整数を指定してください"
-- (safeGenerate (-1) が Left を返した時点で終了)
```

この「エラーの自動伝播」は、Java の例外機構に似ていますが、型レベルで表現されるためコンパイラがエラーハンドリングの漏れを検出できます。

### 複数の safeGenerate を連鎖する

```haskell
-- 複数の値を安全に生成してリストにする
safeGenerateMultiple :: [Int] -> Either String [String]
safeGenerateMultiple = mapM safeGenerate

safeGenerateMultiple [1, 3, 5, 15]
-- => Right ["1", "Fizz", "Buzz", "FizzBuzz"]

safeGenerateMultiple [1, 0, 5]
-- => Left "正の整数を指定してください"
-- (0 の時点でエラー)
```

`mapM` は「モナドの文脈で `map` を行う」関数です。リストの各要素に `safeGenerate` を適用し、1 つでも `Left` があれば全体が `Left` になります。

## 12.7 遅延評価と無限リスト

### Haskell の遅延評価

Haskell は**遅延評価**（lazy evaluation）を採用しています。式は「値が必要になるまで評価されない」のが原則です。

```haskell
-- 無限リストの定義（遅延評価なので可能）
naturals :: [Int]
naturals = [1..]

-- 先頭 5 要素だけ取り出す（ここで初めて評価される）
take 5 naturals
-- => [1, 2, 3, 4, 5]
```

`[1..]` は 1 から始まる無限リストですが、`take 5` で先頭 5 要素しか必要とされないため、5 要素分しか計算されません。

### Red: lazyStream のテスト

```haskell
-- test/FizzBuzz/FizzBuzzSpec.hs（抜粋）
describe "lazyStream" $ do
  it "遅延ストリームから要素を取得できる" $ do
    take 3 lazyStream `shouldBe` ["1", "2", "Fizz"]

  it "15 番目の要素は 'FizzBuzz'" $
    lazyStream !! 14 `shouldBe` "FizzBuzz"
```

### Green: lazyStream の実装

```haskell
-- src/FizzBuzz.hs
lazyStream :: [String]
lazyStream = map generate [1..]
```

`lazyStream` は `[1..]`（1 から始まる無限リスト）の各要素に `generate` を適用した無限リストです。遅延評価のおかげで、実際に必要な要素だけが計算されます。

### テストの実行

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  lazyStream
    遅延ストリームから要素を取得できる
    15 番目の要素は 'FizzBuzz'

2 examples, 0 failures
```

### 遅延評価の活用

無限リストと高階関数を組み合わせると、宣言的なデータ処理が可能になります。

```haskell
-- 最初の 10 個の "Fizz" を取得する
take 10 (filter (== "Fizz") lazyStream)
-- => ["Fizz","Fizz","Fizz","Fizz","Fizz","Fizz","Fizz","Fizz","Fizz","Fizz"]

-- 最初の 5 個の "FizzBuzz" を取得する
take 5 (filter (== "FizzBuzz") lazyStream)
-- => ["FizzBuzz","FizzBuzz","FizzBuzz","FizzBuzz","FizzBuzz"]

-- 100 番目から 105 番目までの FizzBuzz
take 5 (drop 99 lazyStream)
-- => ["Buzz","101","Fizz","103","104"]
```

`filter` も遅延的に動作するため、無限リストに対して `filter` を適用しても問題ありません。`take` で必要な数だけ取り出せば、その分だけ計算が進みます。

### 遅延評価の注意点

遅延評価は強力ですが、注意点もあります。

| 利点 | 注意点 |
|------|--------|
| 無限データ構造を自然に扱える | メモリリーク（サンクの蓄積）が起こりうる |
| 必要な分だけ計算される | デバッグ時に評価タイミングがわかりにくい |
| モジュール性が向上する | 正格評価が必要な場合は `seq` や `BangPatterns` を使う |

## 12.8 全テストの実行

第 4 部で追加したすべてのテストを実行します。

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  generate
    1 を渡すと '1' を返す
    2 を渡すと '2' を返す
    3 の倍数を渡すと 'Fizz' を返す
    5 の倍数を渡すと 'Buzz' を返す
    15 の倍数を渡すと 'FizzBuzz' を返す
    30 を渡すと 'FizzBuzz' を返す
  generateList
    100 件のリストを生成する
    最初の要素は '1'
    3 番目の要素は 'Fizz'
    5 番目の要素は 'Buzz'
    15 番目の要素は 'FizzBuzz'
  generateWith
    カスタムルールで生成できる
  transform
    リストを変換できる
  filterList
    リストをフィルタリングできる
  compose
    2 つの関数を合成できる
  safeGenerate
    正の整数で成功する
    0 以下でエラーを返す
    負数でエラーを返す
  lazyStream
    遅延ストリームから要素を取得できる
    15 番目の要素は 'FizzBuzz'
  safeGenerateList
    正の整数で成功する
    0 以下でエラーを返す

Finished in 0.0003 seconds
21 examples, 0 failures
```

## 12.9 他言語との比較

### Elixir の {:ok, _} / {:error, _} タプル

Elixir はタグ付きタプルでエラーを表現します。

```elixir
# Elixir
def safe_generate(number) when is_integer(number) and number > 0 do
  {:ok, generate(number)}
end

def safe_generate(_), do: {:error, :non_positive}

# パターンマッチで処理
case safe_generate(3) do
  {:ok, value} -> "成功: #{value}"
  {:error, reason} -> "エラー: #{reason}"
end
```

```haskell
-- Haskell
safeGenerate :: Int -> Either String String
safeGenerate n
  | n <= 0    = Left "正の整数を指定してください"
  | otherwise = Right (generate n)

-- パターンマッチで処理
case safeGenerate 3 of
  Right value -> "成功: " ++ value
  Left err    -> "エラー: " ++ err
```

| 特徴 | Haskell `Either` | Elixir タグ付きタプル |
|------|-----------------|-------------------|
| 型安全性 | コンパイル時に型チェック | 動的型付け（Dialyzer で補助） |
| モナド連鎖 | `do` 記法 / `>>=` | `with` 構文 |
| 網羅性チェック | コンパイラが警告 | なし（ランタイムエラー） |
| エラー情報 | `Left` に任意の型 | `{:error, reason}` |

Elixir の `with` 構文は Haskell の `do` 記法に近い役割を果たします。

```elixir
# Elixir: with 構文
with {:ok, x} <- safe_generate(3),
     {:ok, y} <- safe_generate(5) do
  {:ok, "#{x}, #{y}"}
end
```

```haskell
-- Haskell: do 記法
do
  x <- safeGenerate 3
  y <- safeGenerate 5
  Right (x ++ ", " ++ y)
```

### Rust の Result / Option

Rust の `Result<T, E>` と `Option<T>` は Haskell の `Either` と `Maybe` に直接対応します。

```rust
// Rust
fn safe_generate(n: i32) -> Result<String, String> {
    if n <= 0 {
        Err("正の整数を指定してください".to_string())
    } else {
        Ok(generate(n))
    }
}

// ? 演算子でエラー伝播
fn safe_fizzbuzz_pair(a: i32, b: i32) -> Result<String, String> {
    let x = safe_generate(a)?;
    let y = safe_generate(b)?;
    Ok(format!("{}, {}", x, y))
}
```

```haskell
-- Haskell
safeGenerate :: Int -> Either String String
safeGenerate n
  | n <= 0    = Left "正の整数を指定してください"
  | otherwise = Right (generate n)

-- do 記法でエラー伝播
safeFizzBuzzPair :: Int -> Int -> Either String String
safeFizzBuzzPair a b = do
  x <- safeGenerate a
  y <- safeGenerate b
  Right (x ++ ", " ++ y)
```

| 概念 | Haskell | Rust |
|------|---------|------|
| 値の有無 | `Maybe a` (`Nothing` / `Just a`) | `Option<T>` (`None` / `Some(T)`) |
| エラーハンドリング | `Either e a` (`Left e` / `Right a`) | `Result<T, E>` (`Err(E)` / `Ok(T)`) |
| エラー伝播 | `do` 記法 / `>>=` | `?` 演算子 |
| パターンマッチ | `case ... of` | `match` |
| モナド連鎖 | Monad 型クラス | `and_then` メソッド |

Rust の `?` 演算子は Haskell の `do` 記法内の `<-` に相当します。どちらもエラーの自動伝播を実現しますが、Haskell ではモナドという一般的な仕組みの一部として提供されています。

### Java の Optional / 例外

Java は従来の例外機構に加え、Java 8 で `Optional<T>` が導入されました。

```java
// Java
Optional<String> safeGenerate(int n) {
    if (n <= 0) return Optional.empty();
    return Optional.of(generate(n));
}

// flatMap でチェーン
Optional<String> result = safeGenerate(3)
    .flatMap(x -> safeGenerate(5)
        .map(y -> x + ", " + y));
```

```haskell
-- Haskell（Maybe 版）
safeGenerateMaybe :: Int -> Maybe String
safeGenerateMaybe n
  | n <= 0    = Nothing
  | otherwise = Just (generate n)

-- do 記法でチェーン
result = do
  x <- safeGenerateMaybe 3
  y <- safeGenerateMaybe 5
  Just (x ++ ", " ++ y)
```

Java の `Optional.flatMap` は Haskell の `>>=` に対応し、`Optional.map` は `fmap` に対応します。ただし Java の `Optional` は `null` の代替であり、Haskell の `Maybe` のような一般的なモナドの仕組みは持ちません。

## 12.10 まとめ: Haskell の純粋関数型プログラミング

### 第 4 部の振り返り

第 4 部を通じて、以下の関数型プログラミングのテクニックを FizzBuzz の実装で実践しました。

| 章 | テーマ | 主な概念 |
|----|--------|---------|
| 第 10 章 | 高階関数とカリー化 | `map`、`filter`、`foldl`、`generateWith`、部分適用 |
| 第 11 章 | 関数合成とポイントフリー | `(.)`、`($)`、ポイントフリースタイル、`compose` |
| 第 12 章 | モナドとエラーハンドリング | `Maybe`、`Either`、`do` 記法、遅延評価 |

### Haskell の純粋関数型プログラミングの特徴

1. **純粋性**: すべての関数は副作用を持たず、同じ入力に対して常に同じ出力を返す。`safeGenerate 3` は常に `Right "Fizz"` を返す
2. **型安全性**: `Maybe` と `Either` でエラーを型レベルで表現し、コンパイラがエラーハンドリングの漏れを検出する
3. **遅延評価**: `lazyStream` のような無限データ構造を自然に扱え、必要な分だけ計算する
4. **高階関数とカリー化**: 関数を自在に組み合わせ、部分適用で新しい関数を生成する
5. **関数合成**: 小さな関数を組み合わせて大きな処理を構築する
6. **モナド**: `do` 記法で「文脈を持つ計算の連鎖」を命令型に近い見た目で書ける

### FizzBuzz の最終形

```haskell
module FizzBuzz
  ( generate, generateList, generateWith, transform, filterList
  , compose, safeGenerate, lazyStream, safeGenerateList
  ) where

-- 基本の FizzBuzz 生成
generate :: Int -> String
generate n
  | n `mod` 15 == 0 = "FizzBuzz"
  | n `mod` 3 == 0  = "Fizz"
  | n `mod` 5 == 0  = "Buzz"
  | otherwise        = show n

-- リスト生成（map の活用）
generateList :: Int -> [String]
generateList n = map generate [1..n]

-- 高階関数（ルールの注入）
generateWith :: (Int -> String) -> Int -> String
generateWith rule = rule

-- ポイントフリースタイルの高階関数
transform :: (a -> b) -> [a] -> [b]
transform = map

filterList :: (a -> Bool) -> [a] -> [a]
filterList = filter

-- 関数合成
compose :: (b -> c) -> (a -> b) -> a -> c
compose = (.)

-- Either によるエラーハンドリング
safeGenerate :: Int -> Either String String
safeGenerate n
  | n <= 0    = Left "正の整数を指定してください"
  | otherwise = Right (generate n)

-- 遅延評価による無限ストリーム
lazyStream :: [String]
lazyStream = map generate [1..]

-- Either によるリスト版エラーハンドリング
safeGenerateList :: Int -> Either String [String]
safeGenerateList n
  | n <= 0    = Left "正の整数を指定してください"
  | otherwise = Right (generateList n)
```

この最終形は、TDD のサイクル（Red → Green → Refactor）を繰り返しながら、関数型プログラミングのテクニックを段階的に積み上げた結果です。各関数は小さく、型シグネチャが仕様を語り、テストが振る舞いを保証しています。

Haskell の純粋関数型プログラミングは、「変更を楽に安全にできるソフトウェア」を実現するための強力なアプローチです。型による安全性の保証、純粋関数によるテスタビリティの高さ、関数合成によるモジュール性の向上が、その核心にあります。
