---
type: Article
title: "第5章: プロパティベーステスト — 6言語統合ガイド"
description: "従来の Example-Based Testing は「特定の入力に対する特定の出力」を検証しますが、プロパティベーステスト（PBT）は「すべての入力に対して成り立つ性質」を検証します。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第5章: プロパティベーステスト — 6言語統合ガイド

## 1. はじめに

従来の Example-Based Testing は「特定の入力に対する特定の出力」を検証しますが、**プロパティベーステスト（PBT）**は「すべての入力に対して成り立つ性質」を検証します。テストデータを自動生成し、数百〜数千のランダムケースで性質を検証することで、開発者が思いつかないエッジケースを発見できます。

## 2. 共通の本質

### PBT の基本構造

```
1. 性質（Property）を定義する
2. テストデータをランダムに生成する（Generator）
3. 性質が成り立つか検証する
4. 失敗時、最小の反例を見つける（Shrinking）
```

### 代表的な性質パターン

| パターン | 定義 | 例 |
|---------|------|-----|
| 冪等性（Idempotency） | `f(f(x)) = f(x)` | sort のソート済み再ソート |
| 対合性（Involution） | `f(f(x)) = x` | reverse の二重適用 |
| 不変量（Invariant） | 操作前後で保存される性質 | ソート後も要素数は同じ |
| 往復（Round-trip） | `decode(encode(x)) = x` | シリアライズの往復 |
| モデルベース | 別の実装と同じ結果 | 自作 sort と標準 sort の比較 |

## 3. 言語別実装比較

### 3.1 PBT ライブラリ

| 言語 | ライブラリ | 特徴 |
|------|----------|------|
| Clojure | test.check | Spec との統合、ジェネレータが豊富 |
| Scala | ScalaCheck | 型クラスベース、ScalaTest 統合 |
| Elixir | StreamData | Stream ベースの遅延生成 |
| F# | FsCheck | 属性ベース、`[<Property>]` で宣言的 |
| Haskell | QuickCheck | PBT の元祖、条件付きプロパティ |
| Rust | proptest | マクロベース、Strategy trait で拡張 |

### 3.2 冪等性の検証

「同じ操作を 2 回適用しても結果が変わらない」性質です。

<details>
<summary>Clojure: test.check</summary>

```clojure
(require '[clojure.test.check :as tc]
         '[clojure.test.check.generators :as gen]
         '[clojure.test.check.properties :as prop])

(def prop-sort-idempotent
  (prop/for-all [nums (gen/vector gen/small-integer)]
    (= (sort nums) (sort (sort nums)))))

(tc/quick-check 100 prop-sort-idempotent)
```

</details>

<details>
<summary>Scala: ScalaCheck</summary>

```scala
import org.scalacheck.Prop.forAll

test("ソートは冪等である") {
  forAll { (nums: List[Int]) =>
    val sorted = nums.sorted
    sorted shouldBe sorted.sorted
  }
}
```

</details>

<details>
<summary>Haskell: QuickCheck</summary>

```haskell
import Test.QuickCheck

prop_sortIdempotent :: [Int] -> Bool
prop_sortIdempotent xs = sort (sort xs) == sort xs

-- 実行
-- quickCheck prop_sortIdempotent
```

</details>

<details>
<summary>Rust: proptest</summary>

```rust
use proptest::prelude::*;

proptest! {
    #[test]
    fn sort_is_idempotent(mut xs: Vec<i32>) {
        xs.sort();
        let sorted_once = xs.clone();
        xs.sort();
        prop_assert_eq!(xs, sorted_once);
    }
}
```

</details>

<details>
<summary>F#: FsCheck</summary>

```fsharp
[<Property>]
let ``ソートは冪等である`` (nums: int list) =
    let sorted = List.sort nums
    sorted = List.sort sorted
```

F# の FsCheck は `[<Property>]` 属性をつけるだけでプロパティテストを宣言できます。

</details>

<details>
<summary>Elixir: StreamData</summary>

```elixir
property "ソートは冪等である" do
  check all nums <- list_of(integer()) do
    sorted = Enum.sort(nums)
    assert sorted == Enum.sort(sorted)
  end
end
```

</details>

### 3.3 対合性と往復の検証

<details>
<summary>reverse の対合性（全言語比較）</summary>

```clojure
;; Clojure
(def prop-reverse-involutory
  (prop/for-all [xs (gen/vector gen/small-integer)]
    (= xs (vec (reverse (reverse xs))))))
```

```scala
// Scala
forAll { (xs: List[Int]) =>
  xs.reverse.reverse shouldBe xs
}
```

```elixir
# Elixir
check all xs <- list_of(integer()) do
  assert xs == xs |> Enum.reverse() |> Enum.reverse()
end
```

```fsharp
// F#
[<Property>]
let ``reverseの対合性`` (xs: int list) =
    xs |> List.rev |> List.rev = xs
```

```haskell
-- Haskell
prop_reverseInvolutory :: [Int] -> Bool
prop_reverseInvolutory xs = reverse (reverse xs) == xs
```

```rust
// Rust
proptest! {
    #[test]
    fn reverse_involutive(xs: Vec<i32>) {
        let reversed: Vec<_> = xs.iter().rev().rev().cloned().collect();
        prop_assert_eq!(&xs, &reversed);
    }
}
```

</details>

### 3.4 ジェネレータの設計

| ジェネレータ | Clojure | Scala | Elixir | F# | Haskell | Rust |
|------------|---------|-------|--------|-----|---------|------|
| 整数 | `gen/int` | `arbitrary[Int]` | `integer()` | 型推論 | `arbitrary` | 型推論 |
| 文字列 | `gen/string` | `Gen.alphaStr` | `string(:alpha)` | 型推論 | `arbitrary` | 型推論 |
| リスト | `gen/vector` | `Gen.listOf` | `list_of` | 型推論 | `listOf` | 型推論 |
| カスタム型 | `gen/fmap` | `Gen.map` | `gen all` | 計算式 | `Gen` モナド | `Strategy` trait |
| 範囲指定 | `gen/choose` | `Gen.choose` | `integer(1..100)` | `Gen.choose` | `choose` | `1..100i32` |

<details>
<summary>カスタムジェネレータの比較</summary>

```clojure
;; Clojure: gen/fmap で変換
(def gen-person
  (gen/fmap (fn [[name age]]
              {:name name :age age})
            (gen/tuple gen/string-alphanumeric
                       (gen/choose 0 150))))
```

```scala
// Scala: for 式で合成
val genPerson: Gen[Person] = for
  name <- Gen.alphaStr
  age  <- Gen.choose(0, 150)
yield Person(name, age)
```

```haskell
-- Haskell: Gen モナドで合成
genPerson :: Gen Person
genPerson = do
    name <- arbitrary
    age  <- choose (0, 150)
    return $ Person name age
```

```rust
// Rust: Strategy trait で合成
fn person_strategy() -> impl Strategy<Value = Person> {
    ("[a-zA-Z]+", 0..150u32).prop_map(|(name, age)| {
        Person { name, age }
    })
}
```

</details>

### 3.5 シュリンキング（最小反例の発見）

テストが失敗した場合、PBT ライブラリは自動的に入力を**縮小**し、最小の反例を見つけます。

| 言語 | シュリンク方式 | 特徴 |
|------|-------------|------|
| Clojure | ジェネレータ統合 | ジェネレータが縮小方法も知っている |
| Scala | `Shrink` 型クラス | カスタムシュリンク定義可能 |
| Elixir | ジェネレータ統合 | StreamData が自動対応 |
| F# | `Shrink` 型 | FsCheck が自動対応 |
| Haskell | `Arbitrary` 型クラスの `shrink` | 最も細かく制御可能 |
| Rust | ジェネレータ統合 | Strategy に組み込み |

## 4. 比較分析

### 4.1 PBT の表現力

| 基準 | 最も表現力が高い言語 | 理由 |
|------|-------------------|------|
| ジェネレータ合成 | Haskell | モナド合成で自由に組み合わせ |
| Spec 統合 | Clojure | 仕様定義からテストデータを自動生成 |
| 宣言的記述 | F# | `[<Property>]` 属性で最も簡潔 |
| マクロの活用 | Rust | `proptest!` マクロで定型コードを削減 |
| テスト統合 | Scala | ScalaTest との seamless な統合 |
| ストリーム生成 | Elixir | Stream ベースで遅延評価 |

### 4.2 静的型付け vs 動的型付けの影響

**静的型付け言語（Scala, F#, Haskell, Rust）**：

- 型情報からジェネレータを**自動推論**できる
- `arbitrary` を書くだけで適切なジェネレータが選択される
- カスタム型のジェネレータも型クラスで定義可能

**動的型付け言語（Clojure, Elixir）**：

- ジェネレータを**明示的に指定**する必要がある
- Clojure は Spec との統合でこの弱点を克服
- より柔軟なジェネレータ合成が可能

### 4.3 Clojure Spec と PBT の統合

Clojure の最大の強みは、第 4 章で定義した Spec が PBT のジェネレータとして**直接再利用**できることです：

```clojure
;; 第4章で定義した Spec
(s/def ::person (s/keys :req-un [::name ::age ::email]))

;; そのまま PBT のジェネレータとして使用
(def prop-person-roundtrip
  (prop/for-all [person (s/gen ::person)]
    (= person (deserialize (serialize person)))))
```

この統合は他の言語では実現できない、Clojure 固有の強みです。

## 5. 実践的な選択指針

| 要件 | 推奨言語 | 理由 |
|------|---------|------|
| 仕様とテストの一体化 | Clojure | Spec が仕様・検証・テスト生成を統合 |
| 型駆動テスト生成 | Haskell | `Arbitrary` 型クラスで自動推論 |
| 既存テストへの段階的導入 | Scala | ScalaTest との統合が容易 |
| 宣言的テスト記述 | F# | `[<Property>]` 属性で最小限のボイラープレート |
| マクロによる簡潔さ | Rust | `proptest!` マクロが定型コードを削減 |
| ストリーム処理との統合 | Elixir | StreamData がデータ処理パイプラインと整合 |

## 6. まとめ

プロパティベーステストは、関数型プログラミングの**数学的性質**をテストに活かす手法です：

1. **性質パターン**: 冪等性・対合性・不変量・往復は言語を問わず共通
2. **ジェネレータ設計**: 静的型は自動推論、動的型は明示指定が基本
3. **Spec 統合**: Clojure の Spec → PBT パイプラインは他言語にない強み
4. **シュリンキング**: すべてのライブラリが最小反例の自動発見を提供

## 言語別個別記事

- [Clojure](../clojure/05-property-based-testing.md) | [Scala](../scala/05-property-based-testing.md) | [Elixir](../elixir/05-property-based-testing.md) | [F#](../fsharp/05-property-based-testing.md) | [Haskell](../haskell/05-property-based-testing.md) | [Rust](../rust/05-property-based-testing.md)
