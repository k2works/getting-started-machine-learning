---
type: Article
title: "第 12 章: エラーハンドリングと型安全性"
description: "前章までで Stream API とパイプライン処理を学びました。"
tags: [article, getting-start-tdd, java]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 12 章: エラーハンドリングと型安全性

## 12.1 はじめに

前章までで Stream API とパイプライン処理を学びました。この章では、`Optional` を使った **null 安全** なコードの書き方と、**型安全** なファクトリパターンを学びます。

## 12.2 Optional による null 安全

### null の問題

`null` は「値がない」ことを表現しますが、NullPointerException の原因になります。

```java
// 危険: null チェック忘れ
FizzBuzzValue value = map.get(key); // null の可能性
String result = value.getValue();    // NullPointerException!
```

### Optional の基本

`Optional` は「値があるかもしれないし、ないかもしれない」を型で表現します。

```java
// Optional の生成
Optional<FizzBuzzValue> present = Optional.of(value);      // 値あり
Optional<FizzBuzzValue> empty = Optional.empty();           // 値なし
Optional<FizzBuzzValue> nullable = Optional.ofNullable(v);  // null 許容
```

### Optional の操作

```java
Optional<FizzBuzzValue> opt = Optional.of(type.generate(15));

// map: 値を変換
Optional<String> result = opt.map(FizzBuzzValue::getValue);

// filter: 条件に合わなければ empty
Optional<FizzBuzzValue> fizz = opt
    .filter(v -> "Fizz".equals(v.getValue()));

// orElse: 値がなければデフォルト値
String value = opt.map(FizzBuzzValue::getValue)
    .orElse("unknown");

// orElseThrow: 値がなければ例外
String value = opt.map(FizzBuzzValue::getValue)
    .orElseThrow(() ->
        new IllegalStateException("値が存在しません"));
```

## 12.3 FizzBuzzType ファクトリの改善

### 現在の実装: 例外スロー

```java
public static FizzBuzzType create(int type) {
    switch (type) {
        case 1: return new FizzBuzzType01();
        case 2: return new FizzBuzzType02();
        case 3: return new FizzBuzzType03();
        default:
            throw new IllegalArgumentException(
                "該当するタイプは存在しません");
    }
}
```

### Optional を使った安全なファクトリ

```java
public static Optional<FizzBuzzType> createOptional(int type) {
    switch (type) {
        case 1: return Optional.of(new FizzBuzzType01());
        case 2: return Optional.of(new FizzBuzzType02());
        case 3: return Optional.of(new FizzBuzzType03());
        default: return Optional.empty();
    }
}
```

使用例:

```java
// パターン 1: デフォルト値を指定
FizzBuzzType type = FizzBuzzType.createOptional(4)
    .orElse(FizzBuzzType.create(1));

// パターン 2: Optional のまま操作
FizzBuzzType.createOptional(1)
    .map(t -> t.generate(15))
    .map(FizzBuzzValue::getValue)
    .ifPresent(System.out::println);
```

## 12.4 FizzBuzzList の検索機能

### find: 最初の一致を Optional で返す

```java
// FizzBuzzList にメソッドを追加
public Optional<FizzBuzzValue> findFirst(
        Predicate<FizzBuzzValue> predicate) {
    return values.stream()
        .filter(predicate)
        .findFirst();
}
```

使用例:

```java
FizzBuzzList list = listCommand.executeList(100);

// 最初の FizzBuzz を検索
Optional<FizzBuzzValue> firstFizzBuzz = list.findFirst(
    v -> "FizzBuzz".equals(v.getValue())
);

// 値があれば表示、なければ "none"
String result = firstFizzBuzz
    .map(FizzBuzzValue::toString)
    .orElse("none");
```

## 12.5 型安全な列挙型ファクトリ

### enum を使った型安全なタイプ指定

```java
public enum FizzBuzzTypeName {
    STANDARD(1),
    NUMBER_ONLY(2),
    FIZZBUZZ_ONLY(3);

    private final int code;

    FizzBuzzTypeName(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
```

ファクトリメソッドを enum で受け取るバージョン:

```java
public static FizzBuzzType create(FizzBuzzTypeName name) {
    return create(name.getCode());
}
```

使用例:

```java
// Before: マジックナンバー
FizzBuzzType type = FizzBuzzType.create(1);

// After: 型安全な列挙型
FizzBuzzType type = FizzBuzzType.create(FizzBuzzTypeName.STANDARD);
```

## 12.6 全体の振り返り

### 第 4 部で学んだこと

| 章 | テーマ | 学んだこと |
|----|--------|-----------|
| 10 | 高階関数と関数合成 | Lambda 式、メソッド参照、Function、Stream 基本 |
| 11 | 不変データとパイプライン処理 | 不変設計、IntStream、Collectors、パイプライン |
| 12 | エラーハンドリングと型安全性 | Optional、型安全なファクトリ、enum |

### 全 12 章の総括

```
第 1 部（章 1-3）: TDD の基本サイクル
  → Red-Green-Refactor、テストファースト

第 2 部（章 4-6）: 開発環境と自動化
  → Git、静的解析、CI/CD

第 3 部（章 7-9）: オブジェクト指向設計
  → ポリモーフィズム、デザインパターン、SOLID

第 4 部（章 10-12）: 関数型プログラミング
  → Lambda、Stream、Optional
```

TDD の規律を守りながら、手続き型 → OOP → FP と段階的にコードを改善しました。これは「動作するきれいなコード」を目指す TDD の本質です。

## 12.7 まとめ

| 概念 | 適用 |
|------|------|
| Optional | null 安全な値の表現と操作 |
| Optional ファクトリ | `createOptional` で例外を回避 |
| findFirst | Stream + Optional による安全な検索 |
| enum ファクトリ | マジックナンバーの排除と型安全性 |

> **よいソフトウェアとは、変更を楽に安全にできて役に立つソフトウェアである。**
>
> TDD の規律と、オブジェクト指向・関数型の設計技法を組み合わせることで、この目標に近づくことができます。
