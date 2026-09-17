---
type: Article
title: "第 9 章: SOLID 原則とモジュール設計"
description: "前章ではデザインパターンを適用して設計を改善しました。"
tags: [article, getting-start-tdd, csharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 9 章: SOLID 原則とモジュール設計

## 9.1 はじめに

前章ではデザインパターンを適用して設計を改善しました。この章では **SOLID 原則** の観点から設計を評価し、**namespace によるモジュール分割** でコードの責務を明確にします。

## 9.2 SOLID 原則の適用

### 単一責任の原則（SRP）

| namespace | 責務 |
|-----------|------|
| `FizzBuzz.Domain.Type` | FizzBuzz タイプの定義と生成ルール |
| `FizzBuzz.Domain.Model` | 値オブジェクトとコレクション |
| `FizzBuzz.Application` | コマンドの実行と結果の出力 |

各クラスが単一の責任を持つように設計されています。`FizzBuzzType01` は通常の FizzBuzz ロジックのみ、`FizzBuzzValue` は値の保持と等価性のみを担当します。

### 開放閉鎖の原則（OCP）

`IFizzBuzzType` インターフェースを実装する新しいクラスを追加するだけで、既存のコードを変更せずに新しいタイプを追加できます。

```csharp
// 既存コードを変更せずに新しいタイプを追加
public class FizzBuzzType04 : IFizzBuzzType
{
    public FizzBuzzValue Generate(int number)
    {
        // 新しいルールの実装
        return new FizzBuzzValue(number, number.ToString());
    }
}
```

### リスコフの置換の原則（LSP）

`IFizzBuzzType` を実装するすべてのクラスは、`IFizzBuzzType` 型として交換可能です。

```csharp
IFizzBuzzType type = new FizzBuzzType01(); // どの実装でも動作する
IFizzBuzzType type = new FizzBuzzType02(); // 交換可能
```

### インターフェース分離の原則（ISP）

`IFizzBuzzType` インターフェースは `Generate` メソッドのみを定義しており、不必要なメソッドを強制しません。

```csharp
public interface IFizzBuzzType
{
    FizzBuzzValue Generate(int number); // 必要最小限のメソッド
}
```

### 依存性逆転の原則（DIP）

`FizzBuzzListCommand` は具体的な `FizzBuzzType01` ではなく `IFizzBuzzType` インターフェースに依存しています。

```csharp
public class FizzBuzzListCommand : IFizzBuzzCommand
{
    private readonly IFizzBuzzType _type; // 抽象に依存

    public FizzBuzzListCommand(IFizzBuzzType type) // コンストラクタインジェクション
    {
        _type = type;
    }
}
```

## 9.3 モジュール分割

### ディレクトリ構成

```
FizzBuzz/
├── FizzBuzz.cs                       (公開 API)
├── FizzBuzz.csproj
├── Application/
│   ├── IFizzBuzzCommand.cs
│   ├── FizzBuzzValueCommand.cs
│   └── FizzBuzzListCommand.cs
└── Domain/
    ├── Model/
    │   ├── FizzBuzzValue.cs
    │   └── FizzBuzzList.cs
    └── Type/
        ├── IFizzBuzzType.cs
        ├── FizzBuzzType01.cs
        ├── FizzBuzzType02.cs
        ├── FizzBuzzType03.cs
        ├── FizzBuzzTypeName.cs
        └── FizzBuzzTypeFactory.cs
```

### C# の namespace システム

C# の `namespace` はモジュールを論理的にグループ化し、`using` で参照します。

```csharp
// FizzBuzz/Domain/Type/IFizzBuzzType.cs
namespace FizzBuzz.Domain.Type;

using FizzBuzz.Domain.Model;

public interface IFizzBuzzType
{
    FizzBuzzValue Generate(int number);
}
```

```csharp
// FizzBuzz/Application/FizzBuzzListCommand.cs
namespace FizzBuzz.Application;

using FizzBuzz.Domain.Model;
using FizzBuzz.Domain.Type;

public class FizzBuzzListCommand : IFizzBuzzCommand
{
    // ...
}
```

Rust の `mod` + `pub` によるモジュールシステムに対して、C# では `namespace` + `using` でモジュール間の依存関係を表現します。Java の `package` + `import` に近い感覚です。

### FizzBuzzTypeName enum

タイプの名前を enum で型安全に定義します。

```csharp
namespace FizzBuzz.Domain.Type;

public enum FizzBuzzTypeName
{
    Standard = 1,
    NumberOnly = 2,
    FizzBuzzOnly = 3
}
```

C# の `enum` は整数値に紐づく列挙型です。Rust の `enum` ほど強力ではありませんが、`switch` 式と組み合わせることで型安全なディスパッチが可能です。

## 9.4 依存関係

```
Application → Domain.Type
Application → Domain.Model
Domain.Type → Domain.Model
```

- `Application` は `Domain` に依存
- `Domain.Type` は `Domain.Model` に依存（`FizzBuzzValue` を生成）
- 逆方向の依存は存在しない（単方向依存）

## 9.5 まとめ

この章では以下を実現しました。

| 原則 | 適用内容 |
|------|---------|
| SRP | Type / Model / Application の責務分離 |
| OCP | interface による拡張ポイント |
| LSP | IFizzBuzzType による交換可能性 |
| ISP | 最小限のインターフェース定義 |
| DIP | コンストラクタインジェクションによる依存の抽象化 |

| namespace | 責務 | 含まれる型 |
|-----------|------|-----------|
| `Domain.Type` | FizzBuzz 生成ルール | IFizzBuzzType, Type01-03, Factory, TypeName |
| `Domain.Model` | 値オブジェクト | FizzBuzzValue, FizzBuzzList |
| `Application` | コマンド実行 | IFizzBuzzCommand, ValueCommand, ListCommand |

第 3 部を通じて、C# の interface、class、namespace を使ったオブジェクト指向的な設計を学びました。次の第 4 部では、C# の関数型プログラミング機能（LINQ、デリゲート、パターンマッチ）を活用します。
