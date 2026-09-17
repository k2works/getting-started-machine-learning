---
type: Article
title: "第 10 章: 高階関数と関数合成"
description: "C# は LINQ やラムダ式を通じて関数型プログラミングの機能を豊富にサポートしています。"
tags: [article, getting-start-tdd, csharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 10 章: 高階関数と関数合成

## 10.1 はじめに

C# は LINQ やラムダ式を通じて関数型プログラミングの機能を豊富にサポートしています。この章では **デリゲート** と **LINQ** を中心に、高階関数と関数合成の考え方を学びます。

## 10.2 デリゲートとラムダ式

C# のデリゲートは関数を値として扱うための仕組みです。`Func<T, TResult>` と `Action<T>` が代表的なデリゲート型です。

```csharp
// Func: 戻り値を持つ関数
Func<int, int, int> add = (a, b) => a + b;
Assert.Equal(5, add(2, 3));

// Action: 戻り値を持たない関数
Action<string> print = message => Console.WriteLine(message);

// Predicate: bool を返す関数（Func<T, bool> のエイリアス）
Predicate<int> isEven = n => n % 2 == 0;
Assert.True(isEven(4));
```

Rust のクロージャ `|x| x * 2` に相当するのが C# のラムダ式 `x => x * 2` です。Rust のように所有権の概念はありませんが、キャプチャした変数の参照を保持する点は共通です。

### 環境のキャプチャ

```csharp
int factor = 3;
Func<int, bool> isMultiple = n => n % factor == 0;
Assert.True(isMultiple(9));
Assert.False(isMultiple(7));
```

## 10.3 LINQ と高階関数

### Select — 要素の変換

`Select` は各要素に関数を適用して変換します。Rust の `.map()` に相当します。

```csharp
var values = list.Values
    .Select(v => v.Value)
    .ToList();
```

### Where — 要素の選別

`Where` は条件に一致する要素だけを残します。Rust の `.filter()` に相当します。

```csharp
var fizzValues = list.Values
    .Where(v => v.Value == "Fizz")
    .ToList();
```

### FirstOrDefault — 最初の一致要素

`FirstOrDefault` は条件に一致する最初の要素を返します。Rust の `.find()` に相当します。

```csharp
var firstBuzz = list.Values
    .FirstOrDefault(v => v.Value == "Buzz");
```

### Any / All — 条件判定

```csharp
bool hasFizzBuzz = list.Values
    .Any(v => v.Value == "FizzBuzz");

bool allNonEmpty = list.Values
    .All(v => !string.IsNullOrEmpty(v.Value));
```

## 10.4 FizzBuzzList への関数型メソッド追加

```csharp
public class FizzBuzzList
{
    // ...既存のコード

    public FizzBuzzList Filter(Func<FizzBuzzValue, bool> predicate)
    {
        return new FizzBuzzList(_values.Where(predicate).ToList());
    }

    public FizzBuzzValue? FindFirst(Func<FizzBuzzValue, bool> predicate)
    {
        return _values.FirstOrDefault(predicate);
    }

    public List<string> ToStringValues()
    {
        return _values.Select(v => v.Value).ToList();
    }

    public Dictionary<string, int> CountByValue()
    {
        return _values
            .GroupBy(v => v.Value)
            .ToDictionary(g => g.Key, g => g.Count());
    }
}
```

### テストの作成

```csharp
[Fact]
public void フィルタリングできる()
{
    var list = new FizzBuzzList(new List<FizzBuzzValue>
    {
        new FizzBuzzValue(1, "1"),
        new FizzBuzzValue(3, "Fizz"),
        new FizzBuzzValue(5, "Buzz"),
        new FizzBuzzValue(15, "FizzBuzz")
    });
    var filtered = list.Filter(v => v.Value == "Fizz");
    Assert.Equal(1, filtered.Count);
    Assert.Equal("Fizz", filtered.Get(0).Value);
}

[Fact]
public void 最初の一致する値を取得できる()
{
    var list = new FizzBuzzList(new List<FizzBuzzValue>
    {
        new FizzBuzzValue(1, "1"),
        new FizzBuzzValue(3, "Fizz"),
        new FizzBuzzValue(6, "Fizz")
    });
    var found = list.FindFirst(v => v.Value == "Fizz");
    Assert.NotNull(found);
    Assert.Equal(3, found.Number);
}

[Fact]
public void 値ごとにカウントできる()
{
    var list = new FizzBuzzList(new List<FizzBuzzValue>
    {
        new FizzBuzzValue(1, "1"),
        new FizzBuzzValue(3, "Fizz"),
        new FizzBuzzValue(6, "Fizz"),
        new FizzBuzzValue(5, "Buzz")
    });
    var counts = list.CountByValue();
    Assert.Equal(1, counts["1"]);
    Assert.Equal(2, counts["Fizz"]);
    Assert.Equal(1, counts["Buzz"]);
}
```

## 10.5 まとめ

| 概念 | C# | Rust | Java |
|------|------|------|------|
| クロージャ | `x => x * 2` | `\|x\| x * 2` | `x -> x * 2` |
| map | `.Select(f)` | `.iter().map(f).collect()` | `.stream().map(f)` |
| filter | `.Where(f)` | `.iter().filter(f).collect()` | `.stream().filter(f)` |
| find | `.FirstOrDefault(f)` | `.iter().find(f)` → `Option` | `.stream().findFirst()` |
| デリゲート | `Func<T, TResult>` | `Fn(T) -> TResult` | `Function<T, R>` |

次章では、LINQ チェーンによるパイプライン処理と不変データの考え方を学びます。
