---
type: Article
title: "第15章: Gossiping Bus Drivers — 6言語統合ガイド"
description: "Gossiping Bus Drivers は、バス運転手たちが停留所で出会うたびに噂を交換し、全員が同じ噂を共有するまでシミュレーションを続ける問題です。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第15章: Gossiping Bus Drivers — 6言語統合ガイド

## 1. はじめに

Gossiping Bus Drivers は、バス運転手たちが停留所で出会うたびに噂を交換し、全員が同じ噂を共有するまでシミュレーションを続ける問題です。関数型プログラミングの**状態遷移**、**集合演算**、**無限シーケンス**を学ぶ格好の題材です。

## 2. 共通の本質

### 問題の構造

1. 各運転手は固定のルートを**循環**する
2. 同じ停留所にいる運転手同士が**噂を交換**する（集合の和）
3. 全運転手が**同じ噂の集合**を持ったら終了
4. 480 ステップ（1 日分）以内に収束しなければ「不可能」

### 核となるアルゴリズム

```
ループ:
  1. 各運転手の現在位置を計算（ルートの循環）
  2. 同じ停留所にいる運転手をグループ化
  3. 同一グループ内の噂を集合和で統合
  4. 全員の噂が一致すれば終了
```

## 3. 言語別実装比較

### 3.1 循環ルートの表現

| 言語 | 実装方法 | 特徴 |
|------|---------|------|
| Clojure | `(cycle route)` | 遅延無限シーケンス |
| Scala | `position % route.length` | 剰余演算 |
| Elixir | `Stream.cycle(route)` | Stream ベース |
| F# | `position % route.Length` | 剰余演算 |
| Haskell | `cycle route` | 遅延無限リスト |
| Rust | `current_index % stops.len()` | 剰余演算 |

Clojure と Haskell は言語の遅延評価を活かし、`cycle` で無限シーケンスを直接生成します。他の言語は剰余演算で循環を表現します。

<details>
<summary>Clojure / Haskell: 遅延無限シーケンス</summary>

```clojure
;; Clojure: 無限列からn番目を取得
(defn current-stop [driver step]
  (nth (cycle (:route driver)) step))
```

```haskell
-- Haskell: cycle で無限リスト
currentStop :: Driver -> Int -> Stop
currentStop driver step = cycle (driverRoute driver) !! step
```

</details>

<details>
<summary>Rust / Scala: 剰余演算</summary>

```rust
pub fn current_stop(&self, step: usize) -> &Stop {
    &self.route.stops[step % self.route.stops.len()]
}
```

```scala
def currentStop(step: Int): Stop =
  route(step % route.length)
```

</details>

### 3.2 噂の交換（集合演算）

| 言語 | 集合和の方法 |
|------|-----------|
| Clojure | `clojure.set/union` |
| Scala | `rumors ++ otherRumors` |
| Elixir | `MapSet.union(a, b)` |
| F# | `Set.unionMany` |
| Haskell | `Set.unions` |
| Rust | `a.union(&b).cloned().collect()` |

<details>
<summary>噂交換の実装比較</summary>

```clojure
;; Clojure
(defn share-rumors [drivers-at-stop]
  (let [all-rumors (apply set/union (map :rumors drivers-at-stop))]
    (map #(assoc % :rumors all-rumors) drivers-at-stop)))
```

```scala
// Scala
def shareRumors(drivers: Vector[Driver]): Vector[Driver] =
  val allRumors = drivers.flatMap(_.rumors).toSet
  drivers.map(_.copy(rumors = allRumors))
```

```elixir
# Elixir
def share_rumors(drivers) do
  all_rumors = Enum.reduce(drivers, MapSet.new(), fn d, acc ->
    MapSet.union(acc, d.rumors)
  end)
  Enum.map(drivers, &%{&1 | rumors: all_rumors})
end
```

</details>

### 3.3 状態管理のアプローチ

| 言語 | 状態の扱い | 更新方法 |
|------|----------|---------|
| Clojure | 不変マップ | 新しいマップを返す |
| Scala | case class | `copy` で新インスタンス |
| Elixir | 不変構造体 | `%{struct \| field: value}` |
| F# | 不変レコード | `{ record with field = value }` |
| Haskell | 不変レコード | レコード更新構文 |
| Rust | 可変 + Clone | `clone()` で新インスタンス |

全言語が**不変の状態遷移**を基本とし、各ステップで新しいワールド状態を生成します。

## 4. 比較分析

### 4.1 遅延評価 vs 剰余演算

- **遅延評価**（Clojure, Haskell）: 概念的に美しく、ルートが無限シーケンスとして自然に表現される
- **剰余演算**（Scala, F#, Rust）: シンプルで効率的、メモリ使用量が予測可能

### 4.2 集合演算の表現力

Clojure と Haskell は集合演算が最も簡潔です。Rust は所有権システムのため `cloned().collect()` が必要になりますが、メモリ安全性が保証されます。

## 5. 実践的な選択指針

| 要件 | 推奨言語 | 理由 |
|------|---------|------|
| 概念的な明快さ | Clojure, Haskell | 遅延無限シーケンスが問題の本質に合致 |
| パフォーマンス | Rust | ゼロコスト抽象化と所有権管理 |
| 並行シミュレーション | Elixir | BEAM VM の並行処理が自然に拡張可能 |

## 6. まとめ

Gossiping Bus Drivers は、関数型プログラミングの 3 つの要素を統合的に学べる問題です：

1. **循環データ**: 遅延シーケンス or 剰余演算
2. **集合演算**: 噂の伝播は集合の和
3. **状態遷移**: 各ステップが純粋関数

## 言語別個別記事

- [Clojure](../clojure/15-gossiping-bus-drivers.md) | [Scala](../scala/15-gossiping-bus-drivers.md) | [Elixir](../elixir/15-gossiping-bus-drivers.md) | [F#](../fsharp/15-gossiping-bus-drivers.md) | [Haskell](../haskell/15-gossiping-bus-drivers.md) | [Rust](../rust/15-gossiping-bus-drivers.md)
