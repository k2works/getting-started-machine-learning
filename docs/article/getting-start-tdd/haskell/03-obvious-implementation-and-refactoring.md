---
type: Article
title: "第 3 章: 明白な実装とリファクタリング"
description: "前章では、三角測量とガード式で FizzBuzz のコアロジックを完成させました。"
tags: [article, getting-start-tdd, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 3 章: 明白な実装とリファクタリング

## 3.1 はじめに

前章では、三角測量とガード式で FizzBuzz のコアロジックを完成させました。この章では、残りの TODO（リスト生成）を実装し、リファクタリングで「動作するきれいなコード」を目指します。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 3.2 1 から 100 までのリスト生成

### Red: リスト生成のテスト

1 から指定した数までの FizzBuzz の結果をリストとして返す `generateList` 関数をテストします。

```haskell
  describe "generateList" $ do
    it "100 件のリストを生成する" $
      length (generateList 100) `shouldBe` 100

    it "最初の要素は '1'" $
      generateList 100 !! 0 `shouldBe` "1"

    it "3 番目の要素は 'Fizz'" $
      generateList 100 !! 2 `shouldBe` "Fizz"

    it "5 番目の要素は 'Buzz'" $
      generateList 100 !! 4 `shouldBe` "Buzz"

    it "15 番目の要素は 'FizzBuzz'" $
      generateList 100 !! 14 `shouldBe` "FizzBuzz"
```

`!!` は Haskell のリストのインデックスアクセス演算子で、0 始まりです。Rust の `result[0]` や Go の `result[0]` に相当します。

```bash
$ stack test
error:
    Variable not in scope: generateList :: Int -> [String]
```

`generateList` がまだ定義されていないため、コンパイルエラーになります。Haskell は静的型付け言語なので、関数が存在しないとコンパイルの段階でエラーが報告されます。

### Green: 明白な実装

ここでは **明白な実装** を適用します。`map` 関数を使って、リストの各要素に `generate` を適用します。

> 明白な実装
>
> シンプルな操作を実現するにはどうすればいいだろうか——そのまま実装しよう。
>
> — テスト駆動開発

まず `src/FizzBuzz.hs` にモジュールエクスポートと関数を追加します。

```haskell
-- src/FizzBuzz.hs
module FizzBuzz
  ( generate
  , generateList
  ) where

generate :: Int -> String
generate n
  | n `mod` 15 == 0 = "FizzBuzz"
  | n `mod` 3 == 0  = "Fizz"
  | n `mod` 5 == 0  = "Buzz"
  | otherwise        = show n

generateList :: Int -> [String]
generateList n = map generate [1..n]
```

`[1..n]` は Haskell の **リスト内包表記**（正確にはリストの算術列）で、1 から `n` までの整数リストを生成します。`map generate [1..n]` は、このリストの各要素に `generate` 関数を適用し、新しいリストを返します。

Rust の `(1..=n).map(generate).collect()` と比べると、Haskell ではリストが遅延評価されるため `collect()` のような明示的な変換が不要です。`map` は関数型プログラミングの基本操作であり、Haskell ではリスト操作のほとんどが `map`、`filter`、`fold` の組み合わせで記述できます。

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  generate
    1 を渡すと '1' を返す
    2 を渡すと '2' を返す
    3 の倍数を渡すと 'Fizz' を返す
    5 の倍数を渡すと 'Buzz' を返す
    6 を渡すと 'Fizz' を返す
    10 を渡すと 'Buzz' を返す
    15 の倍数を渡すと 'FizzBuzz' を返す
    30 を渡すと 'FizzBuzz' を返す
  generateList
    100 件のリストを生成する
    最初の要素は '1'
    3 番目の要素は 'Fizz'
    5 番目の要素は 'Buzz'
    15 番目の要素は 'FizzBuzz'

Finished in 0.0001 seconds
13 examples, 0 failures
```

すべてのテストが通りました。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [x] 1 から 100 までの数
- [ ] プリントする

## 3.3 プリント機能

プリント機能は、生成したリストの各要素を標準出力に出力するものです。Haskell の `main` 関数で `generateList` の結果を出力する形になります。

```haskell
-- app/Main.hs
module Main where

import FizzBuzz (generateList)

main :: IO ()
main = mapM_ putStrLn (generateList 100)
```

`mapM_` は `map` のモナド版で、各要素にアクション（ここでは `putStrLn`）を適用し、結果を捨てます。`putStrLn` は文字列を 1 行出力する IO アクションです。Haskell では副作用（画面出力など）は `IO` モナドの中で行われ、純粋な関数（`generate`、`generateList`）とは明確に分離されます。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [x] 1 から 100 までの数
- [x] プリントする

## 3.4 リファクタリング

テスト駆動開発の流れを確認しておきましょう。

> 1. レッド：動作しない、おそらく最初のうちはコンパイルも通らないテストを 1 つ書く。
> 2. グリーン：そのテストを迅速に動作させる。このステップでは罪を犯してもよい。
> 3. リファクタリング：テストを通すために発生した重複をすべて除去する。
>
> レッド・グリーン・リファクタリング。それが TDD のマントラだ。
>
> — テスト駆動開発

### プロダクトコードの確認

最終的な `src/FizzBuzz.hs` を確認します。

```haskell
module FizzBuzz
  ( generate
  , generateList
  ) where

generate :: Int -> String
generate n
  | n `mod` 15 == 0 = "FizzBuzz"
  | n `mod` 3 == 0  = "Fizz"
  | n `mod` 5 == 0  = "Buzz"
  | otherwise        = show n

generateList :: Int -> [String]
generateList n = map generate [1..n]
```

Haskell のコードは非常に簡潔です。注目すべきポイントは以下の通りです。

- **型シグネチャ**: `generate :: Int -> String` と `generateList :: Int -> [String]` により、関数の入出力の型が明確に宣言されています。Haskell では型推論が強力なため型シグネチャは省略可能ですが、トップレベル関数には明示的に書くのがベストプラクティスです。
- **モジュールエクスポート**: `module FizzBuzz (generate, generateList) where` で、外部に公開する関数を明示的に列挙しています。エクスポートリストを省略するとすべてが公開されますが、情報隠蔽の観点から明示的に指定します。
- **純粋関数**: `generate` と `generateList` はどちらも純粋関数です。副作用がなく、同じ入力に対して常に同じ出力を返します。これによりテストが非常に書きやすくなっています。

### テストコードの確認

最終的な `test/FizzBuzz/FizzBuzzSpec.hs` を確認します。

```haskell
module FizzBuzz.FizzBuzzSpec (spec) where

import Test.Hspec
import FizzBuzz

spec :: Spec
spec = do
  describe "generate" $ do
    it "1 を渡すと '1' を返す" $
      generate 1 `shouldBe` "1"

    it "2 を渡すと '2' を返す" $
      generate 2 `shouldBe` "2"

    it "3 の倍数を渡すと 'Fizz' を返す" $
      generate 3 `shouldBe` "Fizz"

    it "5 の倍数を渡すと 'Buzz' を返す" $
      generate 5 `shouldBe` "Buzz"

    it "15 の倍数を渡すと 'FizzBuzz' を返す" $
      generate 15 `shouldBe` "FizzBuzz"

    it "30 を渡すと 'FizzBuzz' を返す" $
      generate 30 `shouldBe` "FizzBuzz"

  describe "generateList" $ do
    it "100 件のリストを生成する" $
      length (generateList 100) `shouldBe` 100

    it "最初の要素は '1'" $
      generateList 100 !! 0 `shouldBe` "1"

    it "3 番目の要素は 'Fizz'" $
      generateList 100 !! 2 `shouldBe` "Fizz"

    it "5 番目の要素は 'Buzz'" $
      generateList 100 !! 4 `shouldBe` "Buzz"

    it "15 番目の要素は 'FizzBuzz'" $
      generateList 100 !! 14 `shouldBe` "FizzBuzz"
```

HSpec の `describe` と `it` による階層構造で、テストが仕様書のように読めます。`shouldBe` は `Eq` 型クラスと `Show` 型クラスを利用して値を比較・表示するため、テスト失敗時のエラーメッセージも分かりやすいものになります。

## 3.5 他言語との比較

| 概念 | Java | Python | TypeScript | Ruby | Go | Rust | Haskell |
|------|------|--------|-----------|------|------|------|---------|
| テストフレームワーク | JUnit 5 | pytest | Vitest | Minitest | testing（標準） | cargo test（標準） | HSpec |
| テスト実行 | `./gradlew test` | `pytest` | `npx vitest` | `bundle exec rake test` | `go test ./...` | `cargo test` | `stack test` |
| 文字列変換 | `String.valueOf(n)` | `str(n)` | `n.toString()` | `n.to_s` | `strconv.Itoa(n)` | `n.to_string()` | `show n` |
| 剰余判定 | `n % 3 == 0` | `n % 3 == 0` | `n % 3 === 0` | `(n % 3).zero?` | `n%3 == 0` | `n % 3 == 0` | `` n `mod` 3 == 0 `` |
| 条件分岐 | `if-else` | `if-elif` | `if-else` | `case-when` | `switch` | `match` | ガード式 |
| リスト生成 | `IntStream.rangeClosed` | `[f(n) for n in range]` | `Array.from({length})` | `(1..100).map { }` | `for` + `append` | `(1..=100).map(f).collect()` | `map f [1..n]` |

## 3.6 まとめ

この章では以下のことを学びました。

- **明白な実装** でシンプルな操作をそのまま実装する手法
- Haskell の `map` 関数と `[1..n]` によるリスト生成
- `!!` 演算子によるリストのインデックスアクセス
- `mapM_` と `putStrLn` による IO アクションの実行
- Haskell の **純粋関数** と **IO モナド** の分離
- **型シグネチャ** と **モジュールエクスポート** によるコードの明確化
- Red-Green-Refactor サイクルの完了

第 1 部の 3 章を通じて、TDD の基本サイクル（仮実装 → 三角測量 → 明白な実装 → リファクタリング）を一通り体験しました。Haskell の純粋関数型プログラミングでは、副作用のない関数が基本となるため、TDD との相性が非常に良いことが分かりました。入力を与えて出力を検証するだけでテストが完結し、モック・スタブなどのテストダブルを必要としません。

次の第 2 部では、開発環境の自動化（バージョン管理、パッケージ管理、CI/CD）に進みます。
