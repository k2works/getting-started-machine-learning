---
type: Article
title: "第 1 章: TODO リストと最初のテスト"
description: "プログラムを作成するにあたって、まず何をすればよいでしょうか？私たちは、仕様を確認して TODO リスト を作るところから始めます。"
tags: [article, getting-start-tdd, csharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 1 章: TODO リストと最初のテスト

## 1.1 はじめに

プログラムを作成するにあたって、まず何をすればよいでしょうか？私たちは、仕様を確認して **TODO リスト** を作るところから始めます。

> TODO リスト
>
> 何をテストすべきだろうか——着手する前に、必要になりそうなテストをリストに書き出しておこう。
>
> — テスト駆動開発

## 1.2 仕様の確認

今回取り組む FizzBuzz 問題の仕様は以下の通りです。

```
1 から 100 までの数をプリントするプログラムを書け。
ただし 3 の倍数のときは数の代わりに「Fizz」と、5 の倍数のときは「Buzz」とプリントし、
3 と 5 両方の倍数の場合には「FizzBuzz」とプリントすること。
```

この仕様をそのままプログラムに落とし込むには少しサイズが大きいですね。最初の作業は仕様を **TODO リスト** に分解する作業から着手しましょう。

## 1.3 TODO リストの作成

仕様を分解して TODO リストを作成します。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - [ ] 1 を渡したら文字列 "1" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

まず「1 を渡したら文字列 "1" を返す」という、最も小さなタスクから取り掛かります。

## 1.4 テスティングフレームワークの導入

### テストファースト

最初にプログラムする対象を決めたので、早速プロダクトコードを実装……ではなく **テストファースト** で作業を進めましょう。

> テストファースト
>
> いつテストを書くべきだろうか——それはテスト対象のコードを書く前だ。
>
> — テスト駆動開発

今回 C# のテスティングフレームワークには **xUnit.net** を利用します。xUnit はテストメソッドに `[Fact]` 属性を付けて宣言し、`Assert.Equal` でアサーションを行います。

### 開発環境のセットアップ

dotnet CLI でプロジェクトを初期化し、テスト環境をセットアップします。

```bash
# Nix 環境に入る
$ nix develop .#dotnet

# プロジェクトの初期化
$ cd apps/dotnet
$ dotnet new classlib -n FizzBuzz
$ dotnet new xunit -n FizzBuzzTest
$ dotnet add FizzBuzzTest/FizzBuzzTest.csproj reference FizzBuzz/FizzBuzz.csproj
```

xUnit テストプロジェクトの `.csproj` ファイルは以下のようになります。

```xml
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net8.0</TargetFramework>
    <ImplicitUsings>enable</ImplicitUsings>
    <Nullable>enable</Nullable>
    <IsPackable>false</IsPackable>
    <IsTestProject>true</IsTestProject>
  </PropertyGroup>
  <ItemGroup>
    <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.8.0" />
    <PackageReference Include="xunit" Version="2.5.3" />
    <PackageReference Include="xunit.runner.visualstudio" Version="2.5.3" />
  </ItemGroup>
  <ItemGroup>
    <ProjectReference Include="..\FizzBuzz\FizzBuzz.csproj" />
  </ItemGroup>
</Project>
```

### 環境確認テスト

環境が正しく設定されていることを確認するため、学習用テストを書きます。

```csharp
// FizzBuzzTest/UnitTest1.cs
namespace FizzBuzzTest;

public class HelloTest
{
    [Fact]
    public void Test_Greeting()
    {
        Assert.Equal("hello world", "hello world");
    }
}
```

テストを実行します。

```bash
$ dotnet test
成功!   -失敗:     0、合格:     1、スキップ:     0、合計:     1
```

テストが通りました。xUnit が正常に動作することが確認できました。`Assert.Equal` は Rust の `assert_eq!` や Java の `assertEquals` に相当する C# のアサーション手段です。

## 1.5 仮実装

テスト環境の準備ができたので、TODO リストの最初の作業に取り掛かりましょう。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - **1 を渡したら文字列 "1" を返す**
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

まずはアサーションを最初に書きましょう。

> アサートファースト
>
> いつアサーションを書くべきだろうか——最初に書こう。
>
> — テスト駆動開発

### Red: 最初のテスト

FizzBuzz のテストを書きます。

```csharp
// FizzBuzzTest/FizzBuzzTest.cs
namespace FizzBuzzTest;

using FizzBuzz;

public class FizzBuzzRunnerTest
{
    [Fact]
    public void 数を文字列にして返す_1を渡したら文字列1を返す()
    {
        Assert.Equal("1", FizzBuzzRunner.Generate(1));
    }
}
```

テストを実行します。

```bash
$ dotnet test
# コンパイルエラー: FizzBuzzRunner が見つからない
```

`FizzBuzzRunner` クラスがまだ存在しないため、コンパイルエラーが発生します。

### Green: 仮実装

テストを通すために **仮実装** から始めます。

> 仮実装を経て本実装へ
>
> 失敗するテストを書いてから、最初に行う実装はどのようなものだろうか——ベタ書きの値を返そう。
>
> — テスト駆動開発

`FizzBuzzRunner` クラスを定義して、文字列リテラルを返します。

```csharp
// FizzBuzz/FizzBuzz.cs
namespace FizzBuzz;

public static class FizzBuzzRunner
{
    public static string Generate(int number)
    {
        return "1";
    }
}
```

テストを実行します。

```bash
$ dotnet test
成功!   -失敗:     0、合格:     1、スキップ:     0、合計:     1
```

テストが通りました。C# では `static` メソッドとしてクラスに定義し、`return "1"` で文字列リテラルを返します。Rust の `"1".to_string()` と異なり、C# の文字列リテラルはそのまま `string` 型です。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

ここまでの作業をバージョン管理システムにコミットしておきましょう。

```bash
$ git add .
$ git commit -m 'test: 数を文字列にして返す'
```

## 1.6 まとめ

この章では以下のことを学びました。

- **TODO リスト** で仕様をプログラミング対象に分解する方法
- **テストファースト** で最初にテストを書く考え方
- xUnit.net のセットアップ（`[Fact]`、`Assert.Equal`）
- **仮実装** でベタ書きの値を返してテストを通す手法
- **アサートファースト** でテストの終わりから書き始めるアプローチ
- C# の `static` クラスと文字列リテラルの基本

次章では、2 つ目のテストケースを追加して **三角測量** を行い、プログラムを一般化していきます。
