---
type: Article
title: "第 11 章: 不変データとパイプライン処理"
description: "前章ではデリゲートと LINQ を使った高階関数を学びました。"
tags: [article, getting-start-tdd, csharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 11 章: 不変データとパイプライン処理

## 11.1 はじめに

前章ではデリゲートと LINQ を使った高階関数を学びました。この章では **LINQ チェーン** によるパイプライン処理と **不変データ** の考え方を深めます。

## 11.2 C# の不変性

C# では `readonly` キーワードと読み取り専用プロパティで不変性を実現します。

```csharp
// 読み取り専用フィールド
private readonly List<FizzBuzzValue> _values;

// 読み取り専用プロパティ（get のみ）
public int Number { get; }
public string Value { get; }
```

`FizzBuzzValue` はコンストラクタで値を設定した後は変更できません。Rust がデフォルトで不変（`let`）であるのに対し、C# では明示的に `readonly` や get のみのプロパティで不変性を宣言します。

### イミュータブルな Add 操作

`FizzBuzzList` の `Add` メソッドは、元のリストを変更せず新しいリストを返します。

```csharp
public FizzBuzzList Add(FizzBuzzValue value)
{
    var newValues = new List<FizzBuzzValue>(_values) { value };
    return new FizzBuzzList(newValues);
}
```

```csharp
[Fact]
public void 値を追加できる()
{
    var list = new FizzBuzzList();
    var newList = list.Add(new FizzBuzzValue(1, "1"));
    Assert.Equal(1, newList.Count);
    Assert.Equal(0, list.Count); // 元のリストは変更されない
}
```

## 11.3 LINQ チェーン（パイプライン）

C# の LINQ はメソッドチェーンで連結でき、パイプライン処理を実現します。

```csharp
// パイプライン: Where → Select → ToList
var fizzNumbers = list.Values
    .Where(v => v.Value == "Fizz")
    .Select(v => v.Number)
    .ToList();
```

### Aggregate（集約）

Rust の `.fold()` に相当する `Aggregate` で値を集約します。

```csharp
// 全 FizzBuzz 値を改行区切りで連結
var joined = list.Values
    .Select(v => v.Value)
    .Aggregate((acc, v) => $"{acc}\n{v}");
```

### Take（先頭 N 件）

```csharp
var firstFive = list.Values
    .Take(5)
    .Select(v => v.Value)
    .ToList();
```

### GroupBy（グループ化）

Rust では `HashMap` を手動で構築する必要がありますが、C# では `GroupBy` が標準で提供されています。

```csharp
public Dictionary<string, int> CountByValue()
{
    return _values
        .GroupBy(v => v.Value)
        .ToDictionary(g => g.Key, g => g.Count());
}
```

## 11.4 ImmutableList の活用

`System.Collections.Immutable` パッケージを使うと、よりイミュータブルなコレクションを利用できます。

```bash
$ dotnet add package System.Collections.Immutable
```

```csharp
using System.Collections.Immutable;

// ImmutableList の使用例
var immutableList = ImmutableList<FizzBuzzValue>.Empty;
var newList = immutableList.Add(new FizzBuzzValue(1, "1"));

// 元のリストは空のまま
Assert.Equal(0, immutableList.Count);
Assert.Equal(1, newList.Count);
```

`ImmutableList` は `Add` で常に新しいリストを返し、元のリストは変更されません。内部的にはバランス木を使って効率的に実装されています。

## 11.5 FizzBuzzList のパイプライン操作

```csharp
public class FizzBuzzList
{
    // ...既存のコード

    public FizzBuzzList AddRange(IEnumerable<FizzBuzzValue> values)
    {
        var newValues = new List<FizzBuzzValue>(_values);
        newValues.AddRange(values);
        return new FizzBuzzList(newValues);
    }

    public override string ToString()
    {
        return string.Join(", ", _values.Select(v => v.ToString()));
    }

    public bool Equals(FizzBuzzList? other)
    {
        if (other is null) return false;
        if (ReferenceEquals(this, other)) return true;
        return _values.SequenceEqual(other._values);
    }

    public override bool Equals(object? obj) => Equals(obj as FizzBuzzList);

    public override int GetHashCode()
    {
        var hash = new HashCode();
        foreach (var value in _values)
            hash.Add(value);
        return hash.ToHashCode();
    }
}
```

### テストの作成

```csharp
[Fact]
public void AddRangeで複数の値を追加できる()
{
    var list = new FizzBuzzList();
    var newList = list.AddRange(new List<FizzBuzzValue>
    {
        new FizzBuzzValue(1, "1"),
        new FizzBuzzValue(2, "2")
    });
    Assert.Equal(2, newList.Count);
}

[Fact]
public void 同じ内容のリストは等しい()
{
    var list1 = new FizzBuzzList(new List<FizzBuzzValue>
    {
        new FizzBuzzValue(1, "1"),
        new FizzBuzzValue(3, "Fizz")
    });
    var list2 = new FizzBuzzList(new List<FizzBuzzValue>
    {
        new FizzBuzzValue(1, "1"),
        new FizzBuzzValue(3, "Fizz")
    });
    Assert.Equal(list1, list2);
}
```

## 11.6 まとめ

| 概念 | C# | Rust | Java |
|------|------|------|------|
| 不変フィールド | `readonly` / get のみプロパティ | `let`（デフォルト不変） | `final` |
| パイプライン | `.Where().Select().ToList()` | `.iter().filter().map().collect()` | `.stream().filter().map().collect()` |
| 集約 | `.Aggregate(f)` | `.fold(init, f)` | `.reduce(f)` |
| イミュータブルコレクション | `ImmutableList<T>` | `Vec<T>`（所有権で制御） | `List.of()` |
| グループ化 | `.GroupBy(f).ToDictionary()` | `HashMap` 手動構築 | `.collect(groupingBy())` |

次章では、Nullable 参照型とパターンマッチングを使ったエラーハンドリングと型安全性を学びます。
