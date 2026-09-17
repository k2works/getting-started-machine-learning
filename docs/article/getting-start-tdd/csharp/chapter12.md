---
type: Article
title: "第 12 章: エラーハンドリングと型安全性"
description: "前章では LINQ チェーンによるパイプライン処理を学びました。"
tags: [article, getting-start-tdd, csharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 12 章: エラーハンドリングと型安全性

## 12.1 はじめに

前章では LINQ チェーンによるパイプライン処理を学びました。この最終章では、C# の **Nullable 参照型** と **パターンマッチング** を使ったエラーハンドリングと型安全性を学びます。

## 12.2 Nullable 参照型

C# 8.0 以降では、Nullable 参照型を有効にすることで null 安全性をコンパイル時にチェックできます。

```xml
<PropertyGroup>
  <Nullable>enable</Nullable>
</PropertyGroup>
```

### null 許容型と非 null 型

```csharp
string name = "hello";    // 非 null 型（null を代入するとコンパイラ警告）
string? nullable = null;  // null 許容型（null を許容）
```

### FizzBuzzList での活用

`FindFirst` メソッドは一致する要素が見つからない場合に `null` を返します。

```csharp
public FizzBuzzValue? FindFirst(Func<FizzBuzzValue, bool> predicate)
{
    return _values.FirstOrDefault(predicate);
}
```

戻り値の型 `FizzBuzzValue?` は「null の可能性がある」ことを明示します。Rust の `Option<T>` に相当する C# の表現方法です。

### テストの作成

```csharp
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
public void 一致する値がない場合はnullを返す()
{
    var list = new FizzBuzzList(new List<FizzBuzzValue>
    {
        new FizzBuzzValue(1, "1"),
        new FizzBuzzValue(2, "2")
    });
    var found = list.FindFirst(v => v.Value == "Fizz");
    Assert.Null(found);
}
```

## 12.3 パターンマッチング

C# のパターンマッチングは `switch` 式で豊富なパターンを記述できます。

### switch 式

`FizzBuzzTypeFactory` は `switch` 式を使って型安全なファクトリを実現しています。

```csharp
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
```

C# の `switch` 式は Rust の `match` 式に近く、値を返す式として使えます。`_` はデフォルトケース（Rust のワイルドカードに相当）です。

### 型パターン

`is` キーワードと型パターンで、オブジェクトの型をチェックしながらキャストできます。

```csharp
public bool Equals(object? obj)
{
    if (obj is FizzBuzzValue other)
    {
        return Number == other.Number && Value == other.Value;
    }
    return false;
}
```

`obj is FizzBuzzValue other` は「`obj` が `FizzBuzzValue` 型であれば `other` に代入する」というパターンマッチングです。Rust の `if let Some(value) = option` に近い表現です。

### null チェックパターン

```csharp
public bool Equals(FizzBuzzValue? other)
{
    if (other is null) return false;        // null パターン
    if (ReferenceEquals(this, other)) return true;
    return Number == other.Number && Value == other.Value;
}
```

### FizzBuzzTypeName enum と switch 式

```csharp
public enum FizzBuzzTypeName
{
    Standard = 1,
    NumberOnly = 2,
    FizzBuzzOnly = 3
}

public static IFizzBuzzType Create(FizzBuzzTypeName name)
{
    return Create((int)name);
}
```

enum を使うことで、タイプ番号をハードコーディングせずに型安全に指定できます。

### テストの作成

```csharp
public class FizzBuzzTypeFactoryTest
{
    [Fact]
    public void タイプ1を生成できる()
    {
        var type = FizzBuzzTypeFactory.Create(1);
        Assert.IsType<FizzBuzzType01>(type);
    }

    [Fact]
    public void 不正なタイプは例外を投げる()
    {
        Assert.Throws<ArgumentException>(
            () => FizzBuzzTypeFactory.Create(0));
    }

    [Fact]
    public void Enumで生成できる()
    {
        var type = FizzBuzzTypeFactory.Create(FizzBuzzTypeName.Standard);
        Assert.IsType<FizzBuzzType01>(type);
    }
}
```

## 12.4 例外ハンドリング

C# では `try-catch` と例外型の階層で例外を処理します。Rust の `Result<T, E>` とは異なるアプローチです。

```csharp
try
{
    var type = FizzBuzzTypeFactory.Create(0);
}
catch (ArgumentException ex)
{
    Console.WriteLine($"エラー: {ex.Message}");
}
```

### Rust との比較

| 概念 | C# | Rust |
|------|------|------|
| エラーの表現 | 例外（`Exception`） | `Result<T, E>` |
| エラーの伝播 | `throw` / `try-catch` | `?` 演算子 |
| null の表現 | `T?`（Nullable 参照型） | `Option<T>` |
| null チェック | `is null` パターン | `match` / `if let` |

C# は例外ベースのエラーハンドリングですが、Nullable 参照型とパターンマッチングにより、null に起因するバグをコンパイル時に検出できるようになっています。

## 12.5 他言語との比較

| 概念 | C# | Rust | Java | TypeScript |
|------|------|------|------|------|
| エラーハンドリング | `try-catch` | `Result<T, E>` | `try-catch` | `try-catch` |
| null 安全 | `T?` (Nullable) | `Option<T>` | `Optional<T>` | `T \| null` |
| 列挙型 | `enum`（整数ベース） | `enum`（代数的データ型） | `enum`（クラスベース） | `enum`（数値/文字列） |
| パターンマッチ | `switch` 式 + 型パターン | `match`（網羅性チェック） | `switch` + `instanceof` | — |
| エラー伝播 | `throw` | `?` 演算子 | `throws` | `throw` |

## 12.6 まとめ

この章では以下を学びました。

| 概念 | C# の実現方法 |
|------|-------------|
| null 安全 | Nullable 参照型 (`T?`) + `<Nullable>enable</Nullable>` |
| パターンマッチ | `switch` 式 + 型パターン + null パターン |
| 型安全なファクトリ | `enum` + `switch` 式 |
| 例外ハンドリング | `ArgumentException` + `try-catch` |

全 12 章を通じて、C# の TDD 基本サイクル、開発環境の自動化、オブジェクト指向的な設計、関数型プログラミングの活用を一通り学びました。C# の強力な型システム、interface / abstract class、LINQ、Nullable 参照型、パターンマッチングは、安全で堅牢なソフトウェアを構築するための強力な基盤です。
