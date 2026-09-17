---
type: Article
title: "第 1 章: TODO リストと最初のテスト"
description: "プログラムを作成するにあたって、まず何をすればよいでしょうか？私たちは、仕様を確認して TODO リスト を作るところから始めます。"
tags: [article, getting-start-tdd, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 1 章: TODO リストと最初のテスト

## 1.1 はじめに

プログラムを作成するにあたって、まず何をすればよいでしょうか？私たちは、仕様を確認して **TODO リスト** を作るところから始めます。

> TODO リスト
>
> 何をテストすべきだろうか——着手する前に、必要になりそうなテストをリストに書き出しておこう。
>
> — テスト駆動開発

## 1.2 仕様の確認

今回取り組む FizzBuzz 問題の仕様は以下の通りです。

```
1 から 100 までの数をプリントするプログラムを書け。
ただし 3 の倍数のときは数の代わりに「Fizz」と、5 の倍数のときは「Buzz」とプリントし、
3 と 5 両方の倍数の場合には「FizzBuzz」とプリントすること。
```

この仕様をそのままプログラムに落とし込むには少しサイズが大きいですね。最初の作業は仕様を **TODO リスト** に分解する作業から着手しましょう。

## 1.3 TODO リストの作成

仕様を分解して TODO リストを作成します。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - [ ] 1 を渡したら文字列 "1" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

まず「1 を渡したら文字列 "1" を返す」という、最も小さなタスクから取り掛かります。

## 1.4 テスティングフレームワークの導入

### テストファースト

最初にプログラムする対象を決めたので、早速プロダクトコードを実装……ではなく **テストファースト** で作業を進めましょう。

> テストファースト
>
> いつテストを書くべきだろうか——それはテスト対象のコードを書く前だ。
>
> — テスト駆動開発

今回 Haskell のテスティングフレームワークには **HSpec** を利用します。HSpec は BDD（振る舞い駆動開発）スタイルのテストフレームワークで、`describe` と `it` を使って自然言語に近い形式でテストを記述できます。

### 開発環境のセットアップ

Stack でプロジェクトを初期化し、テスト環境をセットアップします。

```bash
# Nix 環境に入る
$ nix develop .#haskell

# プロジェクトの初期化
$ cd apps
$ stack new haskell simple
$ cd haskell
```

`package.yaml` にテストの依存関係を設定します。

```yaml
name:                fizzbuzz
version:             0.1.0.0
synopsis:            FizzBuzz TDD implementation in Haskell
description:         A FizzBuzz implementation using Test-Driven Development
license:             BSD-3-Clause
author:              k2works

dependencies:
  - base >= 4.7 && < 5

library:
  source-dirs: src
  exposed-modules:
    - FizzBuzz

tests:
  fizzbuzz-test:
    main: Spec.hs
    source-dirs: test
    dependencies:
      - fizzbuzz
      - hspec
    build-tools:
      - hspec-discover
```

`hspec-discover` を使うことで、テストファイルを自動的に検出してくれます。テストのエントリポイントとなる `test/Spec.hs` には以下の 1 行だけを記述します。

```haskell
-- test/Spec.hs
{-# OPTIONS_GHC -F -pgmF hspec-discover #-}
```

この GHC プリプロセッサディレクティブにより、`test/` 配下の `*Spec.hs` ファイルが自動的にテストスイートに組み込まれます。

### 環境確認テスト

環境が正しく設定されていることを確認するため、学習用テストを書きます。`test/FizzBuzz/FizzBuzzSpec.hs` を作成します。

```haskell
-- test/FizzBuzz/FizzBuzzSpec.hs
module FizzBuzz.FizzBuzzSpec (spec) where

import Test.Hspec

spec :: Spec
spec = do
  describe "learning test" $ do
    it "整数を文字列へ変換できる" $
      show 42 `shouldBe` "42"
```

テストを実行します。

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  learning test
    整数を文字列へ変換できる

Finished in 0.0001 seconds
1 example, 0 failures
```

テストが通りました。HSpec が正常に動作することが確認できました。`shouldBe` は HSpec の Matcher で、期待値と実際の値が等しいことを検証します。Haskell の `show` 関数は `Show` 型クラスに属する任意の値を文字列に変換します。これは Rust の `to_string()`（`Display` トレイト）や Python の `str()` に相当します。

## 1.5 仮実装

テスト環境の準備ができたので、TODO リストの最初の作業に取り掛かりましょう。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - **1 を渡したら文字列 "1" を返す**
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

まずはアサーションを最初に書きましょう。

> アサートファースト
>
> いつアサーションを書くべきだろうか——最初に書こう。
>
> — テスト駆動開発

### Red: 最初のテスト

まずプロダクトコードのモジュールを作成します。`src/FizzBuzz.hs` を作ります。

```haskell
-- src/FizzBuzz.hs
module FizzBuzz
  ( generate
  ) where

generate :: Int -> String
generate = undefined
```

Haskell の `undefined` は「まだ実装されていない」ことを表す値で、評価されると例外を投げます。Rust の `todo!()` マクロに相当します。

テストファイルを更新して、FizzBuzz のテストを書きます。

```haskell
-- test/FizzBuzz/FizzBuzzSpec.hs
module FizzBuzz.FizzBuzzSpec (spec) where

import Test.Hspec
import FizzBuzz

spec :: Spec
spec = do
  describe "generate" $ do
    it "1 を渡すと '1' を返す" $
      generate 1 `shouldBe` "1"
```

テストを実行します。

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  generate
    1 を渡すと '1' を返す FAILED [1]

Failures:

  test/FizzBuzz/FizzBuzzSpec.hs:9:7:
  1) generate 1 を渡すと '1' を返す
       evaluate raised an unexpected exception:
         Prelude.undefined

1 example, 1 failure
```

`Prelude.undefined` -- まだ実装されていません。`undefined` が評価され、例外が発生しました。

### Green: 仮実装

テストを通すために **仮実装** から始めます。

> 仮実装を経て本実装へ
>
> 失敗するテストを書いてから、最初に行う実装はどのようなものだろうか——ベタ書きの値を返そう。
>
> — テスト駆動開発

`generate` 関数を定義して、文字列リテラルを返します。

```haskell
-- src/FizzBuzz.hs
module FizzBuzz
  ( generate
  ) where

generate :: Int -> String
generate _ = "1"
```

引数のパターンに `_`（ワイルドカード）を使い、どんな値が来ても無視して `"1"` を返します。これは Rust の `"1".to_string()` をベタ書きするのと同じ仮実装です。Haskell では文字列リテラル `"1"` がそのまま `String` 型（`[Char]` の型エイリアス）になるため、Rust のような `to_string()` 変換は不要です。

テストを実行します。

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  generate
    1 を渡すと '1' を返す

Finished in 0.0001 seconds
1 example, 0 failures
```

テストが通りました。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

ここまでの作業をバージョン管理システムにコミットしておきましょう。

```bash
$ git add .
$ git commit -m 'test: 数を文字列にして返す'
```

## 1.6 まとめ

この章では以下のことを学びました。

- **TODO リスト** で仕様をプログラミング対象に分解する方法
- **テストファースト** で最初にテストを書く考え方
- HSpec テストフレームワーク（`describe`、`it`、`shouldBe`）のセットアップ
- `hspec-discover` による自動テスト検出
- **仮実装** でベタ書きの値を返してテストを通す手法
- **アサートファースト** でテストの終わりから書き始めるアプローチ
- Haskell の `undefined`（未実装マーカー）と `show`（文字列変換）

次章では、2 つ目のテストケースを追加して **三角測量** を行い、プログラムを一般化していきます。
