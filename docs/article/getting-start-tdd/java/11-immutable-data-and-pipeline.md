---
type: Article
title: "第 11 章: 不変データとパイプライン処理"
description: "前章では Lambda 式、メソッド参照、Stream API の基本を学びました。"
tags: [article, getting-start-tdd, java]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 11 章: 不変データとパイプライン処理

## 11.1 はじめに

前章では Lambda 式、メソッド参照、Stream API の基本を学びました。この章では **不変データ** の設計パターンを深掘りし、**パイプライン処理** でデータを宣言的に変換する手法を学びます。

## 11.2 不変データの原則

### なぜ不変性が重要か

> 変更可能なデータはしばしば予期せぬ結果や、厄介なバグを引き起こします。そのため、関数型プログラミングは、データは不変であるべきで、更新時は常に元データ構造のコピーを返すようにし、元データには手を触れないという思想に基づいています。
>
> — Martin Fowler「リファクタリング（第 2 版）」

### FizzBuzz における不変設計の確認

第 8 章で作成した `FizzBuzzValue` と `FizzBuzzList` は既に不変設計です。

| クラス | 不変性の実装 |
|--------|------------|
| `FizzBuzzValue` | `final` フィールド、setter なし |
| `FizzBuzzList` | 防御的コピー、`add()` が新しいインスタンスを返す |

```java
// FizzBuzzList.add() は元のリストを変更しない
FizzBuzzList original = listCommand.executeList(10);
FizzBuzzList extended = original.add(newValues);

// original は変更されていない
assert original.size() == 10;
```

## 11.3 Stream パイプラインの構築

### パイプラインとは

Stream API のパイプラインは、データに対する一連の変換を宣言的に記述する仕組みです。

```
データソース → 中間操作 → 中間操作 → ... → 終端操作
```

### FizzBuzzList にパイプライン操作を追加

```java
// map: 各要素を変換
public <R> List<R> map(Function<FizzBuzzValue, R> mapper) {
    return values.stream()
        .map(mapper)
        .collect(Collectors.toList());
}

// toStringValues: 値の文字列リストを取得
public List<String> toStringValues() {
    return values.stream()
        .map(FizzBuzzValue::getValue)
        .collect(Collectors.toList());
}
```

### Collectors の活用

```java
// グルーピング: 値の種類ごとに分類
public Map<String, List<FizzBuzzValue>> groupByValue() {
    return values.stream()
        .collect(Collectors.groupingBy(FizzBuzzValue::getValue));
}

// カウント: 値の種類ごとの出現回数
public Map<String, Long> countByValue() {
    return values.stream()
        .collect(Collectors.groupingBy(
            FizzBuzzValue::getValue,
            Collectors.counting()
        ));
}

// 結合: カンマ区切りの文字列に変換
public String joining(String delimiter) {
    return values.stream()
        .map(FizzBuzzValue::getValue)
        .collect(Collectors.joining(delimiter));
}
```

## 11.4 IntStream によるリスト生成

### executeList のリファクタリング

`FizzBuzzListCommand.executeList` を `IntStream` でリファクタリングします。

```java
// Before: for ループ
public FizzBuzzList executeList(int count) {
    List<FizzBuzzValue> values = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
        values.add(type.generate(i));
    }
    return new FizzBuzzList(values);
}

// After: IntStream パイプライン
public FizzBuzzList executeList(int count) {
    List<FizzBuzzValue> values = IntStream.rangeClosed(1, count)
        .mapToObj(type::generate)
        .collect(Collectors.toList());
    return new FizzBuzzList(values);
}
```

### Stream 操作の組み合わせ

```java
// 1〜100 の FizzBuzz で "Fizz" の出現回数を数える
long fizzCount = IntStream.rangeClosed(1, 100)
    .mapToObj(type::generate)
    .map(FizzBuzzValue::getValue)
    .filter("Fizz"::equals)
    .count();
```

## 11.5 reduce による集約

### 基本的な reduce

```java
// 数値の合計を計算（FizzBuzz の数値のみ）
int sum = IntStream.rangeClosed(1, 100)
    .mapToObj(type::generate)
    .filter(v -> v.getValue().matches("\\d+"))
    .mapToInt(FizzBuzzValue::getNumber)
    .sum();
```

### FizzBuzzList での reduce 活用

```java
// FizzBuzzList に統計情報を追加
public FizzBuzzListStats getStats() {
    long fizzCount = values.stream()
        .filter(v -> "Fizz".equals(v.getValue())).count();
    long buzzCount = values.stream()
        .filter(v -> "Buzz".equals(v.getValue())).count();
    long fizzBuzzCount = values.stream()
        .filter(v -> "FizzBuzz".equals(v.getValue())).count();
    long numberCount = values.stream()
        .filter(v -> v.getValue().matches("\\d+")).count();

    return new FizzBuzzListStats(
        fizzCount, buzzCount, fizzBuzzCount, numberCount);
}
```

## 11.6 まとめ

この章では不変データとパイプライン処理を学びました。

| 概念 | 適用 |
|------|------|
| 不変データ | `FizzBuzzValue`、`FizzBuzzList` の不変設計を確認 |
| パイプライン | Stream の中間操作・終端操作の連鎖 |
| IntStream | `rangeClosed` による数列生成 |
| Collectors | `groupingBy`、`counting`、`joining` による集約 |
| reduce / sum | 統計情報の算出 |

次の第 12 章では、`Optional` によるエラーハンドリングと型安全性を学びます。
