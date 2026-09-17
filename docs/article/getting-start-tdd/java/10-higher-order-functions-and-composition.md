---
type: Article
title: "第 10 章: 高階関数と関数合成"
description: "第 3 部では、手続き型の FizzBuzz をオブジェクト指向設計に変換しました。"
tags: [article, getting-start-tdd, java]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 10 章: 高階関数と関数合成

## 10.1 はじめに

第 3 部では、手続き型の FizzBuzz をオブジェクト指向設計に変換しました。この第 4 部では、Java の **関数型プログラミング** 機能を活用して、さらにコードを発展させます。

Java 8 以降で導入された主要な関数型機能:

| 機能 | 説明 |
|------|------|
| Lambda 式 | 匿名関数を簡潔に記述 |
| メソッド参照 | 既存メソッドを関数として渡す |
| `Function` インターフェース | 関数を値として扱う |
| Stream API | コレクションの宣言的処理 |

## 10.2 Lambda 式とメソッド参照

### Lambda 式

Lambda 式を使うと、関数を値として変数に代入したり、引数として渡したりできます。

```java
// 従来の匿名クラス
Comparator<FizzBuzzValue> comparator = new Comparator<FizzBuzzValue>() {
    @Override
    public int compare(FizzBuzzValue a, FizzBuzzValue b) {
        return Integer.compare(a.getNumber(), b.getNumber());
    }
};

// Lambda 式
Comparator<FizzBuzzValue> comparator =
    (a, b) -> Integer.compare(a.getNumber(), b.getNumber());
```

### メソッド参照

メソッド参照は Lambda 式をさらに簡潔に書く記法です。

```java
// Lambda 式
values.stream().map(v -> v.getValue());

// メソッド参照
values.stream().map(FizzBuzzValue::getValue);
```

| 種類 | 構文 | 例 |
|------|------|-----|
| 静的メソッド | `クラス::メソッド` | `Integer::parseInt` |
| インスタンスメソッド | `オブジェクト::メソッド` | `type::generate` |
| 任意オブジェクトのメソッド | `クラス::メソッド` | `FizzBuzzValue::getValue` |
| コンストラクタ | `クラス::new` | `ArrayList::new` |

## 10.3 Function インターフェース

`java.util.function` パッケージには、関数を型として表現するインターフェースが用意されています。

| インターフェース | シグネチャ | 用途 |
|----------------|-----------|------|
| `Function<T, R>` | `R apply(T t)` | 変換 |
| `Predicate<T>` | `boolean test(T t)` | 条件判定 |
| `Consumer<T>` | `void accept(T t)` | 副作用のある操作 |
| `Supplier<T>` | `T get()` | 値の生成 |
| `UnaryOperator<T>` | `T apply(T t)` | 同じ型への変換 |

### FizzBuzz での活用

`FizzBuzzType` の `generate` メソッドは `int → FizzBuzzValue` の関数です。これを `IntFunction` として表現できます。

```java
import java.util.function.IntFunction;

// FizzBuzzType.generate は IntFunction<FizzBuzzValue> と同じシグネチャ
IntFunction<FizzBuzzValue> generator = type::generate;
FizzBuzzValue result = generator.apply(15);
```

## 10.4 Stream API の基本

### for ループから Stream へ

`FizzBuzzListCommand` の `executeList` メソッドを Stream API でリファクタリングします。

```java
// Before: for ループ
public FizzBuzzList executeList(int count) {
    List<FizzBuzzValue> values = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
        values.add(type.generate(i));
    }
    return new FizzBuzzList(values);
}

// After: Stream API
public FizzBuzzList executeList(int count) {
    List<FizzBuzzValue> values = IntStream.rangeClosed(1, count)
        .mapToObj(type::generate)
        .collect(Collectors.toList());
    return new FizzBuzzList(values);
}
```

### Stream の 3 ステップ

1. **生成**: `IntStream.rangeClosed(1, count)` — 1 から count までの数列
2. **中間操作**: `.mapToObj(type::generate)` — 各数値を FizzBuzzValue に変換
3. **終端操作**: `.collect(Collectors.toList())` — リストに集約

## 10.5 関数合成

### compose と andThen

`Function` インターフェースは関数を合成するメソッドを持っています。

```java
Function<Integer, FizzBuzzValue> generate = type::generate;
Function<FizzBuzzValue, String> getValue = FizzBuzzValue::getValue;

// andThen: generate した後に getValue を実行
Function<Integer, String> generateAndGetValue =
    generate.andThen(getValue);

String result = generateAndGetValue.apply(15); // "FizzBuzz"
```

### Predicate の合成

```java
Predicate<FizzBuzzValue> isFizz =
    v -> v.getValue().equals("Fizz");
Predicate<FizzBuzzValue> isBuzz =
    v -> v.getValue().equals("Buzz");

// or: Fizz または Buzz
Predicate<FizzBuzzValue> isFizzOrBuzz = isFizz.or(isBuzz);

// and + negate: Fizz でも Buzz でもない
Predicate<FizzBuzzValue> isNumber =
    isFizz.negate().and(isBuzz.negate());
```

## 10.6 Stream API を使ったフィルタリング

Stream API と Predicate を組み合わせて、FizzBuzzList にフィルタリング機能を追加します。

```java
// FizzBuzzList にメソッドを追加
public FizzBuzzList filter(Predicate<FizzBuzzValue> predicate) {
    List<FizzBuzzValue> filtered = values.stream()
        .filter(predicate)
        .collect(Collectors.toList());
    return new FizzBuzzList(filtered);
}
```

使用例:

```java
FizzBuzzList list = listCommand.executeList(100);

// Fizz だけを抽出
FizzBuzzList fizzOnly = list.filter(
    v -> v.getValue().equals("Fizz")
);

// 数値だけを抽出（Fizz でも Buzz でも FizzBuzz でもない）
FizzBuzzList numbersOnly = list.filter(
    v -> !v.getValue().equals("Fizz")
        && !v.getValue().equals("Buzz")
        && !v.getValue().equals("FizzBuzz")
);
```

## 10.7 まとめ

この章では Java の関数型プログラミングの基礎を学びました。

| 概念 | 適用 |
|------|------|
| Lambda 式 | 匿名関数の簡潔な記述 |
| メソッド参照 | `type::generate`、`FizzBuzzValue::getValue` |
| Function インターフェース | 関数を値として扱う |
| Stream API | for ループの宣言的な置き換え |
| 関数合成 | `andThen`、`compose` による関数の連結 |
| Predicate 合成 | `and`、`or`、`negate` による条件の組み合わせ |

次の第 11 章では、Stream API をさらに活用してパイプライン処理を構築し、不変データの操作パターンを深掘りします。
