---
type: Article
title: "第 7 章: カプセル化とポリモーフィズム"
description: "第 1 部では手続き型の FizzBuzz プログラムを TDD で構築しました。"
tags: [article, getting-start-tdd, csharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 7 章: カプセル化とポリモーフィズム

## 7.1 はじめに

第 1 部では手続き型の FizzBuzz プログラムを TDD で構築しました。この章からは **オブジェクト指向プログラミング** の要素を C# で実現していきます。まず **カプセル化** と **ポリモーフィズム** を導入し、手続き型コードを構造化された設計に進化させます。

## 7.2 手続き型コードの課題

第 1 部で作成した `Generate` メソッドは手続き型プログラミングの典型例です。

```csharp
public static string Generate(int number)
{
    if (number % 3 == 0 && number % 5 == 0) return "FizzBuzz";
    if (number % 3 == 0) return "Fizz";
    if (number % 5 == 0) return "Buzz";
    return number.ToString();
}
```

この設計の課題は、新しい FizzBuzz タイプ（数値のみ返す、FizzBuzz のみ返す等）を追加する場合に、既存のメソッドを直接修正する必要があることです。

## 7.3 カプセル化 — 値オブジェクトの導入

### FizzBuzzValue クラス

数値と FizzBuzz の結果をまとめた値オブジェクトを作成します。

```csharp
namespace FizzBuzz.Domain.Model;

public class FizzBuzzValue : IEquatable<FizzBuzzValue>
{
    public int Number { get; }
    public string Value { get; }

    public FizzBuzzValue(int number, string value)
    {
        Number = number;
        Value = value;
    }

    public override string ToString() => $"{Number}:{Value}";

    public bool Equals(FizzBuzzValue? other)
    {
        if (other is null) return false;
        if (ReferenceEquals(this, other)) return true;
        return Number == other.Number && Value == other.Value;
    }

    public override bool Equals(object? obj) => Equals(obj as FizzBuzzValue);
    public override int GetHashCode() => HashCode.Combine(Number, Value);
}
```

C# ではプロパティ（`{ get; }` のみの読み取り専用プロパティ）でフィールドをカプセル化します。`IEquatable<T>` インターフェースを実装して値の等価性を保証します。Rust の `#[derive(PartialEq)]` に相当する機能ですが、C# では手動で実装する必要があります。

### テストの作成

```csharp
public class FizzBuzzValueTest
{
    [Fact]
    public void 値を保持する()
    {
        var value = new FizzBuzzValue(1, "1");
        Assert.Equal(1, value.Number);
        Assert.Equal("1", value.Value);
    }

    [Fact]
    public void 同じ値のオブジェクトは等しい()
    {
        var value1 = new FizzBuzzValue(1, "1");
        var value2 = new FizzBuzzValue(1, "1");
        Assert.Equal(value1, value2);
    }
}
```

## 7.4 ポリモーフィズム — interface の導入

### IFizzBuzzType インターフェース

FizzBuzz のタイプを抽象化するインターフェースを定義します。

```csharp
namespace FizzBuzz.Domain.Type;

using FizzBuzz.Domain.Model;

public interface IFizzBuzzType
{
    FizzBuzzValue Generate(int number);
}
```

C# の `interface` は Rust の `trait` や Java の `interface` に相当します。実装クラスは明示的に `: IFizzBuzzType` と宣言してインターフェースを実装します。

### 3 つの実装

```csharp
// タイプ 1: 通常の FizzBuzz
public class FizzBuzzType01 : IFizzBuzzType
{
    private const int FizzNumber = 3;
    private const int BuzzNumber = 5;

    public FizzBuzzValue Generate(int number)
    {
        string value;
        if (IsFizzBuzz(number)) value = "FizzBuzz";
        else if (IsFizz(number)) value = "Fizz";
        else if (IsBuzz(number)) value = "Buzz";
        else value = number.ToString();

        return new FizzBuzzValue(number, value);
    }

    private static bool IsFizz(int number) => number % FizzNumber == 0;
    private static bool IsBuzz(int number) => number % BuzzNumber == 0;
    private static bool IsFizzBuzz(int number) => IsFizz(number) && IsBuzz(number);
}

// タイプ 2: 数値のみ
public class FizzBuzzType02 : IFizzBuzzType
{
    public FizzBuzzValue Generate(int number)
    {
        return new FizzBuzzValue(number, number.ToString());
    }
}

// タイプ 3: 15 の倍数のみ FizzBuzz
public class FizzBuzzType03 : IFizzBuzzType
{
    private const int FizzBuzzNumber = 15;

    public FizzBuzzValue Generate(int number)
    {
        var value = number % FizzBuzzNumber == 0 ? "FizzBuzz" : number.ToString();
        return new FizzBuzzValue(number, value);
    }
}
```

### テストの作成

```csharp
public class FizzBuzzType01Test
{
    private readonly IFizzBuzzType _type = new FizzBuzzType01();

    [Fact]
    public void 数を文字列にして返す()
    {
        Assert.Equal("1", _type.Generate(1).Value);
    }

    [Fact]
    public void 三の倍数のときはFizzを返す()
    {
        Assert.Equal("Fizz", _type.Generate(3).Value);
    }

    [Fact]
    public void 五の倍数のときはBuzzを返す()
    {
        Assert.Equal("Buzz", _type.Generate(5).Value);
    }

    [Fact]
    public void 三と五の倍数のときはFizzBuzzを返す()
    {
        Assert.Equal("FizzBuzz", _type.Generate(15).Value);
    }
}
```

## 7.5 ファクトリメソッド

タイプ番号から適切な実装を返すファクトリメソッドを作成します。

```csharp
public static class FizzBuzzTypeFactory
{
    public static IFizzBuzzType Create(int type)
    {
        return type switch
        {
            1 => new FizzBuzzType01(),
            2 => new FizzBuzzType02(),
            3 => new FizzBuzzType03(),
            _ => throw new ArgumentException($"Invalid FizzBuzz type: {type}")
        };
    }
}
```

C# の `switch` 式は Rust の `match` 式に近い構文で、値を返す式として使えます。`_` はデフォルトケースで、Rust のワイルドカードパターンと同じです。不正なタイプ番号の場合は `ArgumentException` をスローします。

## 7.6 まとめ

この章では以下を学びました。

| 概念 | C# の実現方法 | 他言語の対応 |
|------|-------------|-------------|
| カプセル化 | `class` + 読み取り専用プロパティ | Rust: struct + 非公開フィールド |
| ポリモーフィズム | `interface` + 明示的実装 | Rust: trait + impl |
| 値オブジェクト | `IEquatable<T>` + `GetHashCode` | Rust: `#[derive(PartialEq)]` |
| ファクトリ | `switch` 式 + `ArgumentException` | Rust: `match` + `Result` |

次章では、デザインパターン（Command、First-Class Collection）を適用していきます。
