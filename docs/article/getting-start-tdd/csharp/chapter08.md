---
type: Article
title: "第 8 章: デザインパターンの適用"
description: "前章では class と interface を使ってカプセル化とポリモーフィズムを実現しました。"
tags: [article, getting-start-tdd, csharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 8 章: デザインパターンの適用

## 8.1 はじめに

前章では class と interface を使ってカプセル化とポリモーフィズムを実現しました。この章では **デザインパターン** を適用して、設計をさらに改善します。

## 8.2 Value Object パターン

### FizzBuzzValue の強化

前章で作成した `FizzBuzzValue` は既に Value Object パターンを実現しています。

```csharp
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

`IEquatable<T>` の実装により、2 つの `FizzBuzzValue` を値で比較できます。読み取り専用プロパティにより不変性が保証されます。Rust の `#[derive(PartialEq, Clone)]` に対し、C# では `IEquatable<T>` と `GetHashCode` を明示的に実装します。

## 8.3 First-Class Collection パターン

### FizzBuzzList クラス

FizzBuzzValue のコレクションを専用の型でラップします。

```csharp
namespace FizzBuzz.Domain.Model;

public class FizzBuzzList : IEquatable<FizzBuzzList>
{
    private readonly List<FizzBuzzValue> _values;

    public IReadOnlyList<FizzBuzzValue> Values => _values.AsReadOnly();

    public FizzBuzzList()
    {
        _values = new List<FizzBuzzValue>();
    }

    public FizzBuzzList(List<FizzBuzzValue> values)
    {
        _values = new List<FizzBuzzValue>(values);
    }

    public FizzBuzzValue Get(int index) => _values[index];
    public int Count => _values.Count;

    public FizzBuzzList Add(FizzBuzzValue value)
    {
        var newValues = new List<FizzBuzzValue>(_values) { value };
        return new FizzBuzzList(newValues);
    }

    public List<string> ToStringValues()
    {
        return _values.Select(v => v.Value).ToList();
    }

    // ...等価性の実装
}
```

First-Class Collection パターンにより、コレクション操作の責務を `FizzBuzzList` に集約できます。`Add` メソッドは新しいリストを返すイミュータブルな設計です。`IReadOnlyList<T>` で公開することで外部からの変更を防ぎます。

### テストの作成

```csharp
public class FizzBuzzListTest
{
    [Fact]
    public void 空のリストを作成できる()
    {
        var list = new FizzBuzzList();
        Assert.Equal(0, list.Count);
    }

    [Fact]
    public void 値を追加できる()
    {
        var list = new FizzBuzzList();
        var newList = list.Add(new FizzBuzzValue(1, "1"));
        Assert.Equal(1, newList.Count);
        Assert.Equal(0, list.Count); // 元のリストは変更されない
    }

    [Fact]
    public void 文字列リストに変換できる()
    {
        var list = new FizzBuzzList(new List<FizzBuzzValue>
        {
            new FizzBuzzValue(1, "1"),
            new FizzBuzzValue(3, "Fizz"),
            new FizzBuzzValue(5, "Buzz")
        });
        var strings = list.ToStringValues();
        Assert.Equal(new List<string> { "1", "Fizz", "Buzz" }, strings);
    }
}
```

## 8.4 Command パターン

### IFizzBuzzCommand インターフェース

FizzBuzz の操作をコマンドとして抽象化します。

```csharp
namespace FizzBuzz.Application;

using FizzBuzz.Domain.Model;

public interface IFizzBuzzCommand
{
    FizzBuzzValue ExecuteValue(int number);
    FizzBuzzList ExecuteList(int count);
}
```

### 単一値コマンド

```csharp
public class FizzBuzzValueCommand : IFizzBuzzCommand
{
    private readonly IFizzBuzzType _type;

    public FizzBuzzValueCommand(IFizzBuzzType type)
    {
        _type = type;
    }

    public FizzBuzzValue ExecuteValue(int number)
    {
        return _type.Generate(number);
    }

    public FizzBuzzList ExecuteList(int count)
    {
        throw new NotSupportedException(
            "FizzBuzzValueCommand does not support list execution.");
    }
}
```

### リストコマンド

```csharp
public class FizzBuzzListCommand : IFizzBuzzCommand
{
    private readonly IFizzBuzzType _type;

    public FizzBuzzListCommand(IFizzBuzzType type)
    {
        _type = type;
    }

    public FizzBuzzValue ExecuteValue(int number)
    {
        throw new NotSupportedException(
            "FizzBuzzListCommand does not support single value execution.");
    }

    public FizzBuzzList ExecuteList(int count)
    {
        var values = Enumerable.Range(1, count)
            .Select(i => _type.Generate(i))
            .ToList();
        return new FizzBuzzList(values);
    }
}
```

コンストラクタインジェクションで `IFizzBuzzType` を受け取ることで、具体的なタイプに依存しない設計を実現しています。

### テストの作成

```csharp
public class FizzBuzzCommandTest
{
    [Fact]
    public void ValueCommandで単一値を取得できる()
    {
        var command = new FizzBuzzValueCommand(
            FizzBuzzTypeFactory.Create(FizzBuzzTypeName.Standard));
        var result = command.ExecuteValue(3);
        Assert.Equal("Fizz", result.Value);
    }

    [Fact]
    public void ListCommandでリストを生成できる()
    {
        var command = new FizzBuzzListCommand(
            FizzBuzzTypeFactory.Create(FizzBuzzTypeName.Standard));
        var result = command.ExecuteList(100);
        Assert.Equal(100, result.Count);
    }
}
```

## 8.5 Strategy パターン

`IFizzBuzzType` インターフェースと 3 つの実装クラス（`FizzBuzzType01`, `FizzBuzzType02`, `FizzBuzzType03`）は、Strategy パターンを実現しています。コマンドのコンストラクタに異なる `IFizzBuzzType` を渡すことで、同じインターフェースで異なる振る舞いを切り替えられます。

### Factory Method パターン

`FizzBuzzTypeFactory.Create` メソッドは Factory Method パターンです。

```csharp
public static IFizzBuzzType Create(FizzBuzzTypeName name)
{
    return Create((int)name);
}
```

`FizzBuzzTypeName` enum を使うことで、型安全なファクトリ呼び出しが可能です。

## 8.6 まとめ

この章では以下のデザインパターンを適用しました。

| パターン | 目的 | C# の実現方法 |
|---------|------|-------------|
| Value Object | 値の等価性保証 | `IEquatable<T>` + 読み取り専用プロパティ |
| First-Class Collection | コレクション操作の集約 | `class` + `List<T>` + `IReadOnlyList<T>` |
| Command | 操作の抽象化と遅延実行 | `interface` + コンストラクタインジェクション |
| Strategy | アルゴリズムの交換可能性 | `interface` + 複数の実装クラス |
| Factory Method | タイプ番号による生成 | `switch` 式 + `static` メソッド |

次章では SOLID 原則の観点から設計を評価し、モジュール分割を行います。
