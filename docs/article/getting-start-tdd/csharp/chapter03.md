---
type: Article
title: "第 3 章: 明白な実装とリファクタリング"
description: "前章では、三角測量と明白な実装で FizzBuzz のコアロジックを完成させました。"
tags: [article, getting-start-tdd, csharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 3 章: 明白な実装とリファクタリング

## 3.1 はじめに

前章では、三角測量と明白な実装で FizzBuzz のコアロジックを完成させました。この章では、残りの TODO（リスト生成とプリント）を実装し、「動作するきれいなコード」を目指してリファクタリングします。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 3.2 1 から 100 までのリスト生成

### Red: リスト生成のテスト

1 から 100 までの FizzBuzz の結果を `List<string>` として返すメソッドをテストします。

```csharp
[Fact]
public void 一から百までのリストを生成する()
{
    var list = FizzBuzzRunner.GenerateList(100);
    Assert.Equal(100, list.Count);
    Assert.Equal("1", list[0]);
    Assert.Equal("Fizz", list[2]);
    Assert.Equal("Buzz", list[4]);
    Assert.Equal("FizzBuzz", list[14]);
}
```

```bash
$ dotnet test
# コンパイルエラー: GenerateList が見つからない
```

### Green: 明白な実装

C# の `Enumerable.Range` と LINQ の `Select` を使って、指定個数の FizzBuzz リストを生成します。

```csharp
public static List<string> GenerateList(int count)
{
    return Enumerable.Range(1, count)
        .Select(i => Generate(i))
        .ToList();
}
```

```bash
$ dotnet test
成功!   -失敗:     0、合格:     6、スキップ:     0、合計:     6
```

`Enumerable.Range(1, count)` は 1 から count 個の連続した整数を生成します。`.Select(i => Generate(i))` で各要素に `Generate` メソッドを適用し、`.ToList()` で `List<string>` に変換します。Rust の `(1..=end).map(f).collect()` に相当する C# の書き方です。

> 明白な実装
>
> シンプルな操作を実現するにはどうすればいいだろうか——そのまま実装しよう。
>
> — テスト駆動開発

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [x] 1 から 100 までの数
- [ ] プリントする

## 3.3 プリント機能

### 学習用テスト

プリント機能は、生成したリストの各要素を出力するものです。学習用テストとして、C# の `StringWriter` を使った出力のキャプチャ方法を確認します。

> 学習用テスト
>
> 外部のソフトウェアのテストを書くべきだろうか——そのソフトウェアに対して新しいことを初めて行おうとした段階で書いてみよう。
>
> — テスト駆動開発

C# では `System.IO.StringWriter` を使って `Console.Out` の出力をキャプチャできます。

```csharp
[Fact]
public void 学習用テスト_StringWriterで出力をキャプチャできる()
{
    var writer = new StringWriter();
    Console.SetOut(writer);
    Console.WriteLine("hello");
    Assert.Contains("hello", writer.ToString());
    Console.SetOut(new StreamWriter(Console.OpenStandardOutput()) { AutoFlush = true });
}
```

### Print メソッドの実装

`TextWriter` を引数に取ることで、テスト時には `StringWriter` に、本番では標準出力に出力できます。

```csharp
public static void PrintFizzBuzz(TextWriter writer)
{
    foreach (var s in GenerateList(100))
    {
        writer.WriteLine(s);
    }
}
```

テストを書きます。

```csharp
[Fact]
public void FizzBuzzの結果を出力する()
{
    var writer = new StringWriter();
    FizzBuzzRunner.PrintFizzBuzz(writer);
    var output = writer.ToString();
    Assert.Contains("1", output);
    Assert.Contains("Fizz", output);
    Assert.Contains("Buzz", output);
    Assert.Contains("FizzBuzz", output);
}
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [x] 1 から 100 までの数
- [x] プリントする

## 3.4 リファクタリング

テスト駆動開発の流れを確認しておきましょう。

> 1. レッド：動作しない、おそらく最初のうちはコンパイルも通らないテストを 1 つ書く。
> 2. グリーン：そのテストを迅速に動作させる。このステップでは罪を犯してもよい。
> 3. リファクタリング：テストを通すために発生した重複をすべて除去する。
>
> レッド・グリーン・リファクタリング。それが TDD のマントラだ。
>
> — テスト駆動開発

### プロダクトコードのリファクタリング

条件判定をメソッドに抽出して、意図を明確にします。

```csharp
public static class FizzBuzzRunner
{
    private const int FizzNumber = 3;
    private const int BuzzNumber = 5;

    public static string Generate(int number)
    {
        if (IsFizzBuzz(number)) return "FizzBuzz";
        if (IsFizz(number)) return "Fizz";
        if (IsBuzz(number)) return "Buzz";
        return number.ToString();
    }

    private static bool IsFizz(int number) => number % FizzNumber == 0;
    private static bool IsBuzz(int number) => number % BuzzNumber == 0;
    private static bool IsFizzBuzz(int number) => IsFizz(number) && IsBuzz(number);

    // ...GenerateList, PrintFizzBuzz は同じ
}
```

マジックナンバー（3, 5）を定数に抽出し、条件判定を `IsFizz`, `IsBuzz`, `IsFizzBuzz` メソッドに分離しました。C# の `=>` 式本体メンバーにより、単純なメソッドを簡潔に書けます。

## 3.5 他言語との比較

| 概念 | Java | Python | TypeScript | Rust | C# |
|------|------|--------|-----------|------|------|
| テストフレームワーク | JUnit 5 | pytest | Vitest | cargo test（標準） | xUnit.net |
| テスト実行 | `./gradlew test` | `pytest` | `npx vitest` | `cargo test` | `dotnet test` |
| 文字列変換 | `String.valueOf(n)` | `str(n)` | `n.toString()` | `n.to_string()` | `n.ToString()` |
| 剰余判定 | `n % 3 == 0` | `n % 3 == 0` | `n % 3 === 0` | `n % 3 == 0` | `n % 3 == 0` |
| リスト生成 | `IntStream.rangeClosed` | `[f(n) for n in range]` | `Array.from({length})` | `(1..=100).map(f).collect()` | `Enumerable.Range().Select()` |
| 出力テスト | `System.setOut` | `capsys` fixture | `vi.spyOn` | `Vec<u8>` + `Write` | `StringWriter` |

## 3.6 まとめ

この章では以下のことを学びました。

- **明白な実装** でシンプルな操作をそのまま実装する手法
- C# の `Enumerable.Range` と `Select` によるリスト生成
- `TextWriter` を活用したテスタブルな出力設計
- `StringWriter` を使った出力のキャプチャ（学習用テスト）
- **リファクタリング** でメソッドの抽出とマジックナンバーの除去を行う考え方
- Red-Green-Refactor サイクルの完了

第 1 部の 3 章を通じて、TDD の基本サイクル（仮実装 → 三角測量 → 明白な実装 → リファクタリング）を一通り体験しました。次の第 2 部では、開発環境の自動化（バージョン管理、パッケージ管理、CI/CD）に進みます。
