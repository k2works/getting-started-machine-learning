---
type: Article
title: "第 8 章: パターンマッチとガード"
description: "前章では代数的データ型と型クラスを使って FizzBuzz のタイプ別ロジックを構造化しました。"
tags: [article, getting-start-tdd, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 8 章: パターンマッチとガード

## 8.1 はじめに

前章では代数的データ型と型クラスを使って FizzBuzz のタイプ別ロジックを構造化しました。この章では、Haskell のパターンマッチとガード式の仕組みを深掘りし、`Either` 型によるエラーハンドリングを導入します。

前章の TODO リストから続けます。

**TODO リスト**:

- [x] タイプ 1 の場合（通常の FizzBuzz）
- [x] タイプ 2 の場合（数字のみ）
- [x] タイプ 3 の場合（FizzBuzz の場合のみ）
- [ ] それ以外のタイプの場合（エラーを返す）
- [x] 値オブジェクト FizzBuzzValue の導入
- [x] コレクション FizzBuzzList の導入

## 8.2 パターンマッチの基本

### 関数引数のパターンマッチ

Haskell では関数定義の引数にパターンを直接記述できます。前章の `generate` 関数を見てみましょう。

```haskell
instance Generatable FizzBuzzType where
  generate Type01 n = ...
  generate Type02 n = ...
  generate Type03 n = ...
```

この定義では、第 1 引数に対して 3 つのパターン（`Type01`、`Type02`、`Type03`）が列挙されています。Haskell のランタイムは引数の値を上から順にパターンと照合し、最初にマッチしたパターンの右辺を実行します。

これは Rust の `match` 式に似ていますが、Haskell では関数定義そのものにパターンマッチが組み込まれている点が特徴です。

| 言語 | パターンマッチ | 記法 |
|------|--------------|------|
| Haskell | 関数定義に直接記述 | `f Pattern1 = ...; f Pattern2 = ...` |
| Rust | `match` 式 | `match x { Pattern1 => ..., Pattern2 => ... }` |
| Scala | `match` 式 | `x match { case Pattern1 => ... }` |
| Clojure | `condp` / マルチメソッド | `(condp = x pattern1 ...)` |

### パターンの種類

Haskell のパターンマッチで使える主なパターンを整理します。

```haskell
-- (1) リテラルパターン: 具体的な値にマッチ
createType 1 = Right Type01
createType 2 = Right Type02

-- (2) コンストラクタパターン: データコンストラクタにマッチ
generate Type01 n = ...
generate Type02 n = ...

-- (3) ワイルドカードパターン: 任意の値にマッチ（値を使わない）
createType _ = Left "未定義のタイプです"

-- (4) 変数パターン: 任意の値にマッチし、変数に束縛
generate Type02 n = show n

-- (5) レコードパターン: レコードのフィールドにマッチ
executeValue cmd = ... vcNumber cmd ... vcType cmd ...
```

### 評価順序

パターンは **上から順に** 評価されます。最初にマッチしたパターンが採用され、残りは評価されません。

```haskell
createType 1 = Right Type01   -- (1) まず 1 と比較
createType 2 = Right Type02   -- (2) 次に 2 と比較
createType 3 = Right Type03   -- (3) 次に 3 と比較
createType _ = Left "未定義のタイプです"  -- (4) どれにもマッチしなければここ
```

`createType 2` を呼び出すと、(1) で `2 == 1` が `False` となり、(2) で `2 == 2` が `True` となるため、`Right Type02` が返されます。

ワイルドカード `_` は必ず最後に置きます。`_` より下のパターンには到達できないため、コンパイラが警告を出します。

## 8.3 ガード式

### ガードの構文

ガード式は、パターンマッチに条件分岐を追加する構文です。`|` で始まり、条件式と結果を `=` で結びます。

```haskell
generate Type01 n
  | n `mod` 15 == 0 = "FizzBuzz"
  | n `mod` 3 == 0  = "Fizz"
  | n `mod` 5 == 0  = "Buzz"
  | otherwise        = show n
```

この定義は次のように読めます。

1. 第 1 引数が `Type01` にマッチした場合、さらにガード条件を評価する
2. `n `mod` 15 == 0` が `True` なら `"FizzBuzz"` を返す
3. `n `mod` 3 == 0` が `True` なら `"Fizz"` を返す
4. `n `mod` 5 == 0` が `True` なら `"Buzz"` を返す
5. `otherwise` は常に `True`（`otherwise = True` と定義されている）なのでデフォルト値を返す

### ガードの評価順序

ガードもパターンと同様に **上から順に** 評価されます。これが FizzBuzz で重要な意味を持ちます。

```haskell
generate Type01 n
  | n `mod` 15 == 0 = "FizzBuzz"  -- 15 の倍数チェックが最初
  | n `mod` 3 == 0  = "Fizz"      -- 3 の倍数チェックが次
  | n `mod` 5 == 0  = "Buzz"      -- 5 の倍数チェックが最後
  | otherwise        = show n
```

15 は 3 と 5 の公倍数なので、`n `mod` 15 == 0` のガードは `n `mod` 3 == 0` と `n `mod` 5 == 0` より **前に** 配置する必要があります。順序を入れ替えると、15 の倍数が `"Fizz"` や `"Buzz"` として処理されてしまいます。

```haskell
-- 誤った順序（15 の倍数が "Fizz" になる）
generate Type01 n
  | n `mod` 3 == 0  = "Fizz"      -- 15 も 3 の倍数なのでここでマッチ
  | n `mod` 5 == 0  = "Buzz"
  | n `mod` 15 == 0 = "FizzBuzz"  -- ここに到達しない
  | otherwise        = show n
```

### otherwise の正体

`otherwise` は特別なキーワードではなく、`Prelude` モジュールで定義された単なる定数です。

```haskell
-- GHC.Base での定義
otherwise :: Bool
otherwise = True
```

`otherwise` は常に `True` なので、すべてのガード条件が `False` だった場合の **フォールバック** として機能します。名前が意図を明確にするため慣例的に使われますが、技術的には `True` と書いても同じです。

### パターンマッチとガードの組み合わせ

タイプ 3 の定義を見ると、パターンマッチとガードの組み合わせの良い例です。

```haskell
generate Type03 n
  | n `mod` 15 == 0 = "FizzBuzz"
  | n `mod` 3 == 0  = "Fizz"
  | otherwise        = show n
```

タイプ 3 は `Buzz` を返しません。タイプ 1 との違いは `n `mod` 5 == 0` のガードがない点だけです。パターンマッチ（`Type03`）で分岐先を選び、ガード式で細かい条件分岐を行うという 2 段階の分岐が自然に表現されています。

## 8.4 case 式との比較

### case 式の構文

Haskell にはパターンマッチのもう 1 つの形式として `case` 式があります。

```haskell
-- case 式を使った場合
createType' :: Int -> Either String FizzBuzzType
createType' n = case n of
  1 -> Right Type01
  2 -> Right Type02
  3 -> Right Type03
  _ -> Left "未定義のタイプです"
```

これは関数引数でのパターンマッチと等価です。

```haskell
-- 関数引数のパターンマッチ（プロジェクトで採用している形式）
createType :: Int -> Either String FizzBuzzType
createType 1 = Right Type01
createType 2 = Right Type02
createType 3 = Right Type03
createType _ = Left "未定義のタイプです"
```

### 使い分けの指針

| 形式 | 適する場面 | 例 |
|------|-----------|-----|
| 関数引数パターンマッチ | トップレベル関数の定義 | `createType 1 = Right Type01` |
| case 式 | 関数の途中で分岐したい場合 | `case result of Right v -> ...; Left e -> ...` |

本プロジェクトでは、関数引数のパターンマッチを優先的に使用しています。関数の定義が複数の等式として並ぶため、各ケースの対応が視覚的に分かりやすくなります。

### case 式でのガード

`case` 式でもガードを使えます。

```haskell
describe :: FizzBuzzType -> Int -> String
describe fbType n = case fbType of
  Type01
    | n `mod` 15 == 0 -> "Type01: FizzBuzz"
    | n `mod` 3 == 0  -> "Type01: Fizz"
    | n `mod` 5 == 0  -> "Type01: Buzz"
    | otherwise        -> "Type01: " ++ show n
  Type02 -> "Type02: " ++ show n
  Type03
    | n `mod` 15 == 0 -> "Type03: FizzBuzz"
    | n `mod` 3 == 0  -> "Type03: Fizz"
    | otherwise        -> "Type03: " ++ show n
```

ただし、この形式は関数引数パターンマッチ + ガードに比べてネストが深くなるため、通常はトップレベル関数で使うことを推奨します。

## 8.5 Either 型によるエラーハンドリング

### Either 型とは

`Either` は Haskell の標準ライブラリで定義された代数的データ型です。

```haskell
data Either a b = Left a | Right b
```

慣例として、`Left` はエラー（失敗）、`Right` は正常値（成功）を表します。`Right` が「正しい」（right）という英語の語呂合わせにもなっています。

Java の例外や Go の `error` 戻り値と異なり、`Either` は **型安全** なエラーハンドリングを提供します。コンパイラがエラーケースの処理を強制するため、エラーを無視するコードを書くことが難しくなります。

| 言語 | エラーハンドリング | 型安全性 |
|------|------------------|---------|
| Haskell | `Either String a` | コンパイル時に検査 |
| Rust | `Result<T, E>` | コンパイル時に検査 |
| Java | `throws Exception` | チェック例外のみ |
| Go | `(value, error)` | 型安全だが無視可能 |
| Python | `raise Exception` | ランタイムのみ |

### Red: createType のテスト

`createType` 関数のテストを追加します。

```haskell
  describe "createType" $ do
    it "タイプ 1 を生成できる" $
      createType 1 `shouldBe` Right Type01

    it "タイプ 2 を生成できる" $
      createType 2 `shouldBe` Right Type02

    it "タイプ 3 を生成できる" $
      createType 3 `shouldBe` Right Type03

    it "未定義のタイプでエラーを返す" $
      createType 4 `shouldBe` Left "未定義のタイプです"
```

### Green: createType の実装

```haskell
createType :: Int -> Either String FizzBuzzType
createType 1 = Right Type01
createType 2 = Right Type02
createType 3 = Right Type03
createType _ = Left "未定義のタイプです"
```

この関数は 4 つの点で注目に値します。

1. **リテラルパターンマッチ**: 引数が `1`、`2`、`3` のいずれかにマッチする
2. **ワイルドカード**: `_` でそれ以外のすべての値をキャッチする
3. **Right で成功を表現**: 正常な値は `Right` で包む
4. **Left でエラーを表現**: エラーメッセージは `Left` で包む

モジュールのエクスポートリストに `createType` を追加します。

```haskell
module FizzBuzz.Type
  ( FizzBuzzType(..)
  , Generatable(..)
  , createType
  ) where
```

テストを実行します。

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
  createType
    タイプ 1 を生成できる
    タイプ 2 を生成できる
    タイプ 3 を生成できる
    未定義のタイプでエラーを返す

Finished in 0.0001 seconds
12 examples, 0 failures
```

**TODO リスト**:

- [x] タイプ 1 の場合（通常の FizzBuzz）
- [x] タイプ 2 の場合（数字のみ）
- [x] タイプ 3 の場合（FizzBuzz の場合のみ）
- [x] それ以外のタイプの場合（エラーを返す）
- [x] 値オブジェクト FizzBuzzValue の導入
- [x] コレクション FizzBuzzList の導入

## 8.6 パターンマッチの網羅性チェック

### GHC の網羅性警告

GHC はパターンマッチが網羅的でない場合に警告を出します。例えば、`createType` からワイルドカードパターンを削除すると以下のようになります。

```haskell
-- 網羅的でない定義（警告が出る）
createType :: Int -> Either String FizzBuzzType
createType 1 = Right Type01
createType 2 = Right Type02
createType 3 = Right Type03
-- createType _ = Left "..." を削除
```

```bash
$ stack build
warning: [-Wincomplete-patterns]
    Pattern match(es) are non-exhaustive
    In an equation for 'createType':
        Patterns of type 'Int' not matched: _
```

`Int` 型は無限の値を持つため、`1`、`2`、`3` だけではすべてのケースを網羅できません。ワイルドカード `_` を最後に追加することで、すべての値をカバーできます。

### 代数的データ型の網羅性

代数的データ型の場合、パターンの網羅性チェックがさらに有効です。

```haskell
-- 網羅的でない定義（Type03 が欠けている）
generate' :: FizzBuzzType -> Int -> String
generate' Type01 n = show n
generate' Type02 n = show n
-- Type03 のケースがない
```

```bash
warning: [-Wincomplete-patterns]
    Pattern match(es) are non-exhaustive
    In an equation for 'generate'':
        Patterns of type 'FizzBuzzType' not matched: Type03
```

GHC は `FizzBuzzType` が `Type01`、`Type02`、`Type03` の 3 つの値コンストラクタを持つことを知っているため、`Type03` が欠けていることを指摘できます。これは、後で新しいタイプ（`Type04` など）を追加した場合にも、すべてのパターンマッチ箇所で警告が出るため、実装漏れを防げます。

### -Wall オプション

`package.yaml` で `-Wall` を有効にすると、網羅性警告を含むすべての警告が有効になります。

```yaml
ghc-options:
  - -Wall
```

本プロジェクトでは `-Wall` を有効にして、パターンマッチの網羅性を含む潜在的な問題を早期に検出しています。

## 8.7 パターンマッチのベストプラクティス

この章で扱ったパターンマッチの知見をベストプラクティスとしてまとめます。

1. **網羅性を確保する**: すべてのコンストラクタをカバーするか、ワイルドカード `_` でフォールバックを用意する
2. **ガードの順序に注意**: より具体的な条件を先に書く（15 の倍数 → 3 の倍数 → 5 の倍数）
3. **ワイルドカードは最後に**: `_` より下のパターンには到達できない
4. **`-Wall` を有効にする**: コンパイラの網羅性チェックを活用する
5. **`Either` でエラーを型安全に扱う**: 例外ではなく戻り値でエラーを表現する
6. **関数引数パターンマッチを優先**: トップレベル関数では `case` 式より関数定義のパターンマッチを使う

## 8.8 まとめ

この章では以下のことを学びました。

- **関数引数のパターンマッチ** で値コンストラクタごとに処理を分岐する
- **ガード式** で条件分岐を追加し、FizzBuzz ロジックを自然に記述する
- **ガードの評価順序** と **otherwise** の仕組みを理解する
- **case 式** と関数引数パターンマッチの使い分け
- **Either 型** による型安全なエラーハンドリング（`createType` のスマートコンストラクタ）
- GHC の **パターンマッチ網羅性チェック** による安全性の確保
- **`-Wall`** オプションによる警告の有効化

次章では、モジュール設計とスマートコンストラクタのパターンを詳しく見ていきます。
