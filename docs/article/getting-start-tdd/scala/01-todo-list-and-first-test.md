---
type: Article
title: "第 1 章: TODO リストと最初のテスト"
description: "この章では、TDD の基本サイクルである Red-Green-Refactor を FizzBuzz 問題で体験します。"
tags: [article, getting-start-tdd, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 1 章: TODO リストと最初のテスト

## 1.1 はじめに

この章では、TDD の基本サイクルである Red-Green-Refactor を FizzBuzz 問題で体験します。

- Red: まず失敗するテストを書きます。
- Green: テストを通す最小限の実装を書きます。
- Refactor: テストを壊さずに実装を整理します。

題材の FizzBuzz 仕様は次の通りです。

- 3 の倍数は `Fizz` を返します。
- 5 の倍数は `Buzz` を返します。
- 15 の倍数は `FizzBuzz` を返します。
- それ以外は数値を文字列で返します。

## 1.2 仕様の確認

最初に仕様をテスト可能な単位で整理します。

- 入力は `Int` 型です。
- 出力は `String` 型です。
- 1 は `"1"` を返します。
- 3 は `"Fizz"` を返します。
- 5 は `"Buzz"` を返します。
- 15 は `"FizzBuzz"` を返します。

この段階では、まだ実装方法を決めません。仕様だけを明確にします。

## 1.3 TODO リストの作成

仕様を小さく分解して TODO リストにします。

- [ ] `FizzBuzz.generate(1)` が `"1"` を返す
- [ ] `FizzBuzz.generate(2)` が `"2"` を返す
- [ ] `FizzBuzz.generate(3)` が `"Fizz"` を返す
- [ ] `FizzBuzz.generate(5)` が `"Buzz"` を返す
- [ ] `FizzBuzz.generate(15)` が `"FizzBuzz"` を返す
- [ ] `FizzBuzz.generateList(100)` が 100 件の結果を返す

最初のタスクは、もっとも単純で失敗時の原因が明確なものを選びます。
このため、最初は `1` のケースから始めます。

## 1.4 テストファースト

### プロジェクトのセットアップ

Scala の sbt プロジェクトは、主に次の構成で進めます。

- `build.sbt`: プロジェクト名、Scala バージョン、依存関係
- `project/`: sbt 設定
- `src/main/scala/`: 本番コード
- `src/test/scala/`: テストコード

`ScalaTest` を利用するため、`build.sbt` に test scope の依存関係を追加します。

```scala
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
```

### 最初のテストを書く

最初の失敗するテストを `FizzBuzzSpec.scala` に書きます。

```scala
package fizzbuzz

import org.scalatest.funsuite.AnyFunSuite

class FizzBuzzSpec extends AnyFunSuite:
  test("1 を渡すと文字列 1 を返す") {
    assert(FizzBuzz.generate(1) === "1")
  }
```

### テストを実行して失敗を確認（Red）

`sbt test` を実行すると、最初は `FizzBuzz` が未定義でコンパイルエラーになります。
これは Red の成功です。失敗するテストから開発を開始できています。

## 1.5 仮実装

次に最小限の実装を追加して Green を目指します。

```scala
package fizzbuzz

object FizzBuzz:
  def generate(number: Int): String = "1"
```

この時点では `1` を固定で返す仮実装で十分です。
再度 `sbt test` を実行し、最初のテストが通ることを確認します。

## 1.6 まとめ

この章では、TDD の最初の 1 サイクルを実行しました。

- Red: 最初のテストを追加
- Green: 最小実装でテストを通過
- Refactor: 今回は対象なし（次章で一般化）

TODO リストも更新して進捗を明確にします。

- [x] `FizzBuzz.generate(1)` が `"1"` を返す
- [ ] `FizzBuzz.generate(2)` が `"2"` を返す
- [ ] `FizzBuzz.generate(3)` が `"Fizz"` を返す
- [ ] `FizzBuzz.generate(5)` が `"Buzz"` を返す
- [ ] `FizzBuzz.generate(15)` が `"FizzBuzz"` を返す
- [ ] `FizzBuzz.generateList(100)` が 100 件の結果を返す
