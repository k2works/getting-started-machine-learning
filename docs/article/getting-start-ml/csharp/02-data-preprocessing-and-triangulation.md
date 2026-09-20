---
type: Article
title: "第 2 章: データの前処理と三角測量"
description: "型プロバイダの無い C# で、セルの文字列を持つ Table・Row と、欠損値を持てない Features を自作して iris データを前処理し、System.Random と Fisher-Yates による訓練・テストデータ分割を TDD で実装して F# 版と同じ分割にそろえる。"
tags: [article,getting-start-ml,csharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T07:30:00Z }
---

# 第 2 章: データの前処理と三角測量

## 2.1 はじめに

第 1 章では、欠損のないきれいなデータを読み込み、人間が書いたルールで判定しました。現実のデータには、空欄（欠損値）が混ざっています。

この章では、アヤメ（iris）のデータを題材に、欠損値を補完し、データを **訓練データ** と **テストデータ** に分けるところまでを TDD で実装します。

[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md)・[F# 版の第 2 章](../fsharp/02-data-preprocessing-and-triangulation.md) と同じ TODO リストで進めます。C# 版では次の 3 点に注目してください。

- **型プロバイダが無い** — F# 版は `CsvProvider` が CSV の形から型を作りましたが、C# には型プロバイダがありません。セルの文字列を持つ小さな表を自分で作ります
- **`double?` と `float option`** — 空欄を「無い」と表す仕組みは、F# の `float option` に対して C# では null 許容の `double?` です
- **record が向かない場面** — 配列を持つ `Features` は record にすると値で比べられません。Java 版では Error Prone が止めましたが、C# は止めないので、自分でテストを書いて気づく必要があります

分割の手順と乱数は F# 版にそろえたので、**訓練データとテストデータに入る行は F# 版と一致します**（2.8 節）。

## 2.2 題材とデータ

### iris.csv

`iris.csv` には、アヤメの花 150 件の測定値と品種が記録されています。

| 列 | 意味 | C# 版での読み方 | F# 版での型 |
|----|------|----------------|------------|
| がく片長さ | がく片の長さ | `row.Number("がく片長さ")` → `double?` | `float option` |
| がく片幅 | がく片の幅 | `row.Number("がく片幅")` → `double?` | `float option` |
| 花弁長さ | 花弁の長さ | `row.Number("花弁長さ")` → `double?` | `float option` |
| 花弁幅 | 花弁の幅 | `row.Number("花弁幅")` → `double?` | `float option` |
| 種類 | 品種（3 種類が 50 件ずつ） | `row.Text("種類")` → `string` | `string` |

特徴量の 4 列には合わせて 7 件の欠損値があります。

### 訓練データとテストデータ

モデルの良し悪しは、学習に使っていないデータでどれだけ当たるかで測ります。そこで、150 件を 7 対 3 の 105 件（訓練データ）と 45 件（テストデータ）に分けます。欠損値を補完する平均値は **訓練データだけ** から求め、その値で両方を補完します。データ全体の平均値を使うと、テストデータの情報が学習側に漏れるためです（データリーク）。

### F# 版と数値がそろう理由

分け方の手順（シード付きでシャッフルし、テストデータの件数を切り上げる）は、どの言語の版も同じです。違いは乱数生成器で、Python 版は NumPy、Java 版は `java.util.Random` と `Collections.shuffle` を使うので、どの行がテストデータに入るかは言語ごとに違います。

C# 版は、F# 版と同じ .NET の `System.Random` を同じシードで使い、シャッフルの手順（Fisher-Yates を後ろから回し、`Next(i + 1)` と交換する）も同じにしました。そのため **どの行が訓練データに入るかまで F# 版と一致** します。第 3 章の正解率や決定木の境界も、F# 版と同じ値になります。この一致は 2.8 節でテストとして固定します。

## 2.3 開発環境の準備

この章では依存を追加しません。CSV の読み込みも前処理も、第 1 章と同じ標準ライブラリ（`System.IO`・LINQ・コレクション）で書きます。

F# 版は CSV の読み込みに FSharp.Data の型プロバイダを使いましたが、C# には型プロバイダがありません。データフレームのライブラリとしては `Microsoft.Data.Analysis` がありますが、安定版が 0.23.0 で 1.0 に達しておらず、2025 年 11 月以降はプレビューだけです。そこで C# 版は、Java 版と同じく **データフレームのライブラリを使わず**、record と LINQ でデータを表すと決めました。選定の理由は [ADR 006](../../../adr/006-csharp-ml-libraries.md) を参照してください。

表の操作をライブラリに任せず自分で書くことで、欠損値の表し方や補完の前後の区別を、型の設計として考えられます。

## 2.4 TODO リストの作成

**TODO リスト**:

- [ ] iris.csv を読み込む
  - [ ] 空欄を欠損値（`null`）として読み込む
  - [ ] 行末の空欄も欠損値として読み込む
- [ ] 列ごとの欠損値の数を数える
- [ ] 列ごとの平均値を求める
- [ ] 欠損値を補完する
  - [ ] 列ごとに指定した値で補完する
  - [ ] 元のデータは変更しない
- [ ] 特徴量と正解ラベルに分ける
- [ ] 訓練データとテストデータに分ける
  - [ ] テストデータの割合どおりの件数に分ける
  - [ ] すべての行を重複なくどちらかに入れる
  - [ ] 特徴量と正解ラベルの対応を保つ
  - [ ] 同じシードなら同じ分け方になる
  - [ ] シードが違えば違う分け方になる
- [ ] 訓練データの平均値で両方を補完する
- [ ] 実データで前処理の結果を表示する

F# 版の TODO リストにある「シード付きで並べ替える」は、C# 版でも `Shuffle` として作ります。逆に F# 版には無い「行末の空欄も欠損値として読み込む」を加えています。自分で CSV を分割するので、ライブラリが面倒を見てくれていた落とし穴を自分で踏むことになるためです（2.5 節）。

## 2.5 自作の表で読み込む

### データの表し方を決める

型プロバイダの代わりに、次の 3 つの型を作ります。

| 型 | 持つもの | 役割 |
|----|---------|------|
| `Table` | 列名の並びと `Row` のリスト | 読み込んだ CSV 全体 |
| `Row` | セルの文字列（列名 → 文字列の辞書） | 1 行分。`Number` と `Text` で読み出す |
| `Features` | 列名の並びと `double` の配列 | 補完が済んだ 1 行分の特徴量。欠損値を持てない |

`Row` はセルを **文字列のまま** 持ち、読み出すときに `Number`（数値として読む。空欄なら `null`）と `Text`（文字列として読む）を使い分けます。iris 専用の record（`record IrisRow(double SepalLength, ...)`）を作る案もありましたが、第 7 章以降では列の違う CSV を何種類も読むので、列名で引く形のほうが章をまたいで使い回せます。F# 版が型プロバイダの行をわざわざ `Map<string, float option>` に移し替えたのと、同じ理由です。

`Row` と `Features` を分けたのは、**補完する前と後を型で区別する** ためです。`Row` の数値は欠損しうるので `double?` で返りますが、`Features` の値は `double` の配列で、欠損値を入れる場所がありません。補完する前の行をうっかりモデルに渡すと、コンパイルエラーになります。F# 版の `Map<string, float option>` と `Map<string, float>` の区別に当たります。

### テストファースト

読み込みのテストを書きます。第 1 章と同じく、一時ディレクトリに架空の値の CSV を作ります。

```csharp
// tests/MachineLearning.Tests/Chapter02/TableTests.cs
namespace MachineLearning.Tests.Chapter02;

using MachineLearning.Chapter02;

public class TableTests : IDisposable
{
    private const string Header = "﻿がく片長さ,がく片幅,花弁長さ,花弁幅,種類\n";

    private readonly string directory = Directory.CreateTempSubdirectory("ml-csharp-").FullName;

    private string WriteCsv(string rows)
    {
        var path = Path.Combine(this.directory, "iris.csv");
        File.WriteAllText(path, Header + rows);
        return path;
    }

    [Fact(DisplayName = "CSV を読み込むと列名の並びを保つ")]
    public void KeepsColumnOrder()
    {
        var table = Table.Load(this.WriteCsv("0.1,0.2,0.3,0.4,Iris-setosa\n"));

        Assert.Equal(["がく片長さ", "がく片幅", "花弁長さ", "花弁幅", "種類"], table.Columns);
    }

    [Fact(DisplayName = "空欄は欠損値（null）として読み込む")]
    public void BlankIsMissing()
    {
        var table = Table.Load(this.WriteCsv("0.1,,0.3,0.4,Iris-setosa\n"));

        var row = table.Rows[0];
        Assert.Null(row.Number("がく片幅"));
        Assert.Equal(0.1, row.Number("がく片長さ"));
        Assert.Equal("Iris-setosa", row.Text("種類"));
    }

    public void Dispose()
    {
        Directory.Delete(this.directory, recursive: true);
        GC.SuppressFinalize(this);
    }
}
```

- 第 1 章と同じく、一時ディレクトリはテストクラスのフィールドで作り、`IDisposable` の `Dispose` で消します
- `Assert.Null(row.Number("がく片幅"))` は、`double?` が値を持たないことを確かめます。F# 版の `None` との比較に当たりますが、C# では `null` そのものと比べます
- `Assert.Equal(0.1, row.Number("がく片長さ"))` では、`double?` を `double` の期待値と比べています。`Assert.Equal<T>` が `double?` として型を推論するので、そのまま書けます
- 列名の期待値 `["がく片長さ", ...]` はコレクション式です（第 1 章）

Red は、第 1 章と同じくコンパイルの段階で出ます。

```text
tests/MachineLearning.Tests/Chapter02/TableTests.cs(3,23): error CS0234: 型または名前空間の名前 'Chapter02' が名前空間 'MachineLearning' に存在しません (アセンブリ参照があることを確認してください)
tests/MachineLearning.Tests/Chapter02/PreprocessingTests.cs(3,23): error CS0234: 型または名前空間の名前 'Chapter02' が名前空間 'MachineLearning' に存在しません (アセンブリ参照があることを確認してください)
tests/MachineLearning.Tests/Chapter02/PreprocessingTests.cs(46,21): error CS0246: 型または名前空間の名前 'Row' が見つかりませんでした (using ディレクティブまたはアセンブリ参照が指定されていることを確認してください)
```

### Green: 明白な実装

第 1 章の `LoadPeople` と同じ手順（ヘッダー行で列名を決め、各行を分割する）なので、明白な実装で書きます。

```csharp
// src/MachineLearning/Chapter02/Row.cs
namespace MachineLearning.Chapter02;

using System.Globalization;

/// <summary>CSV の 1 行。セルの文字列を列名で引けるように持つ。空欄は欠損値として扱う。</summary>
public sealed record Row
{
    private readonly Dictionary<string, string> cells;

    public Row(IReadOnlyDictionary<string, string> cells)
    {
        ArgumentNullException.ThrowIfNull(cells);
        this.cells = new Dictionary<string, string>(cells, StringComparer.Ordinal);
    }

    /// <summary>数値の列を読む。空欄なら null を返す。</summary>
    public double? Number(string column)
    {
        var cell = this.Text(column);
        return string.IsNullOrWhiteSpace(cell) ? null : double.Parse(cell, CultureInfo.InvariantCulture);
    }

    /// <summary>文字列の列を読む。</summary>
    public string Text(string column)
    {
        if (!this.cells.TryGetValue(column, out var cell))
        {
            throw new ArgumentException($"列がありません: {column}", nameof(column));
        }

        return cell;
    }

    /// <summary>セルが空欄かどうか。</summary>
    public bool IsMissing(string column) => string.IsNullOrWhiteSpace(this.Text(column));
}
```

- コンストラクタで受け取った辞書を `new Dictionary<string, string>(cells, ...)` で写します。呼び出し側が後から元の辞書を書き換えても、`Row` は影響を受けません（`TableTests` の「Row は受け取った辞書を写して持ち、元の辞書の変更の影響を受けない」で確かめています）
- `double?` は **null 許容値型** です。`double` に「値が無い」状態を足した型で、F# の `float option` に当たります。`option` が中身を `Some`／`None` という値で包むのに対し、`double?` は `Nullable<double>` という構造体で、`null` かどうかを持ちます
- 三項演算子の真の側に `null` を書けるのは、式全体の型が `double?` に推論されるためです
- `double.Parse` に `CultureInfo.InvariantCulture` を渡しているのは、小数点がカンマの文化圏でも `0.1` を読めるようにするためです
- 無い列を読むと、列名を含むメッセージの `ArgumentException` にします。`TryGetValue` は無ければ例外ではなく `false` を返すので、こちらでメッセージを組み立てられます

```csharp
// src/MachineLearning/Chapter02/Table.cs
namespace MachineLearning.Chapter02;

/// <summary>列名の並びと行のリスト。データフレームのライブラリの代わりに使う、変更できない表。</summary>
public sealed record Table(IReadOnlyList<string> Columns, IReadOnlyList<Row> Rows)
{
    /// <summary>
    /// UTF-8 の CSV を読み込む。.NET の File.ReadAllLines は BOM を取り除くので、
    /// 列名から BOM を消す処理は要らない（第 1 章）。
    /// </summary>
    public static Table Load(string csvFile)
    {
        var lines = File.ReadAllLines(csvFile);
        var columns = lines[0].Split(',');
        var rows = lines.Skip(1)
            .Where(line => !string.IsNullOrWhiteSpace(line))
            .Select(line => ToRow(columns, line))
            .ToList();
        return new Table(columns, rows);
    }

    /// <summary>列ごとの欠損値の数を、列の順に並べて返す。</summary>
    public IReadOnlyList<KeyValuePair<string, int>> CountMissing() =>
        [.. this.Columns.Select(column =>
            KeyValuePair.Create(column, this.Rows.Count(row => row.IsMissing(column))))];

    private static Row ToRow(string[] columns, string line)
    {
        // Split は行末の空欄も空文字列として残す（Java の split とは違い、上限の指定が要らない）
        var values = line.Split(',');
        var cells = new Dictionary<string, string>(StringComparer.Ordinal);
        for (var i = 0; i < columns.Length; i++)
        {
            cells[columns[i]] = values[i];
        }

        return new Row(cells);
    }
}
```

`Table` は位置で成分を書く record です。第 1 章の `Person` と同じ書き方で、`Columns` と `Rows` の読み出し用プロパティが自動で作られます。

第 1 章で確かめたとおり、.NET の `File.ReadAllLines` は BOM を取り除いてから文字列を返すので、先頭の列名から BOM を消す処理は要りません。Python 版・Kotlin 版・Java 版と F# 版が踏んだ落とし穴が、C# では起きない箇所です。

### 行末の空欄の落とし穴が起きない

`ToRow` のコメントには、C# を書くうえで覚えておきたい違いが書いてあります。行の最後の列が空欄のテストを見てください。

```csharp
    [Fact(DisplayName = "行の最後の列が空欄でも欠損値として読み込む")]
    public void TrailingBlankIsMissing()
    {
        var table = Table.Load(this.WriteCsv("0.1,0.2,0.3,,\n"));

        Assert.Null(table.Rows[0].Number("花弁幅"));
        Assert.Equal(string.Empty, table.Rows[0].Text("種類"));
    }
```

Java の `String.split(",")` は末尾に続く空の文字列を捨てるので、Java 版では `split(",", -1)` と上限に負の数を渡す必要がありました。渡し忘れると、5 列のつもりの `"0.1,0.2,0.3,,"` が 3 要素になり、4 番目の列を読もうとして `ArrayIndexOutOfBoundsException` になります。

C# の `string.Split(char)` は、末尾の空の文字列を捨てません。使い捨てのテスト（表示名 `tmp`）に、ありえない期待値を書いて実際の値を見てみます。

```csharp
[Fact(DisplayName = "tmp")]
public void Tmp() => Assert.Equal(-1, "0.1,0.2,0.3,,".Split(',').Length);
```

```text
failed tmp (31ms)
  Assert.Equal() Failure: Values differ
  Expected: -1
  Actual:   5
```

ちゃんと 5 要素です。捨てたいときに `StringSplitOptions.RemoveEmptyEntries` を明示的に渡す設計なので、**何も指定しないほうが安全側** になっています。上のテストは、その振る舞いに寄りかかっていることを固定するために残しました。

| 観点 | 第 1 章の自作の読み込み | F# の `CsvProvider` | C# 版の `Table` |
|------|----------------------|--------------------|----------------|
| 列の型 | 読み込むときに `int` に変換 | `Schema` で指定（`float option`） | 文字列のまま持ち、読むときに `Number`・`Text` を選ぶ |
| 列名の間違い | 実行時に例外 | コンパイルエラー | 実行時に `ArgumentException`（列名つき） |
| BOM | .NET が取り除く | 読み込み時に取り除く | .NET が取り除く |
| 空欄 | （無い前提） | `None` | `null`（`double?`） |
| 行末の空欄 | （無い前提） | 空欄として読む | `Split` がそのまま残す |

型プロバイダを持つ F# 版では、列名を書き間違えるとコンパイルが止まります。C# 版はそこまでの保証が無く、実行時のエラーになります。その代わり、列の数や名前によらない 1 つの関数で表を扱えます。

### 列ごとの欠損値の数

```csharp
    [Fact(DisplayName = "列ごとの欠損値の数を列の順に数える")]
    public void CountsMissing()
    {
        var table = Table.Load(this.WriteCsv("0.1,0.2,0.3,0.4,Iris-setosa\n,0.3,0.5,0.6,Iris-setosa\n,,0.7,0.8,Iris-virginica\n"));

        Assert.Equal(
            [("がく片長さ", 2), ("がく片幅", 1), ("花弁長さ", 0), ("花弁幅", 0), ("種類", 0)],
            table.CountMissing().Select(pair => (pair.Key, pair.Value)));
    }
```

`CountMissing` の戻り値は `IReadOnlyList<KeyValuePair<string, int>>` です。`Dictionary` にしなかったのは、**列の順に並べる** ためです。`Dictionary` は要素の順序を保証しないので、表示の順が CSV の列の順と一致しません。F# 版は `Map` を使い、`Map` がキーの順（文字列の順）に並ぶことを踏まえて、表示のときに列名のリストから引き直していました。C# 版は、集計の段階から列の順を保つリストにしています。

テスト側では `Select(pair => (pair.Key, pair.Value))` でタプルの列に直してから比べています。`KeyValuePair` をそのまま並べるより、期待値が短く書けるためです。

## 2.6 平均値で欠損値を補完する

### 平均値を求める

```csharp
// tests/MachineLearning.Tests/Chapter02/PreprocessingTests.cs
public class ColumnMeansTests
{
    [Fact(DisplayName = "欠損値を除いて列ごとの平均値を求める")]
    public void IgnoresMissing()
    {
        List<Row> rows = [Sample("0.1", "0.2"), Sample(string.Empty, "0.4"), Sample("0.3", "0.9")];

        var means = Preprocessing.ColumnMeans(rows, ["がく片長さ", "がく片幅"]);

        Assert.Equal(0.2, means["がく片長さ"], 12);
        Assert.Equal(0.5, means["がく片幅"], 12);
    }

    internal static Row Sample(string sepalLength, string sepalWidth) =>
        new(new Dictionary<string, string> { ["がく片長さ"] = sepalLength, ["がく片幅"] = sepalWidth });
}
```

第 1 章の `KinokoTakenokoTests.cs` と同じく、対象のメソッドごとにクラスを分けて 1 つのファイルに並べます。テスト用の行を作る `Sample` は、後の `FillMissingTests` からも使うので `internal static` にしました。

```csharp
    /// <summary>欠損値を除いて、列ごとの平均値を求める。</summary>
    public static IReadOnlyDictionary<string, double> ColumnMeans(
        IReadOnlyList<Row> rows, IReadOnlyList<string> columns)
    {
        ArgumentNullException.ThrowIfNull(rows);
        ArgumentNullException.ThrowIfNull(columns);
        return columns.ToDictionary(
            column => column,
            column => rows.Select(row => row.Number(column)).OfType<double>().Average(),
            StringComparer.Ordinal);
    }
```

- `OfType<double>()` は、`double?` の列から **値を持つものだけ** を取り出し、`IEnumerable<double>` にします。F# の `List.choose id` に当たります。`Where(v => v.HasValue).Select(v => v!.Value)` と書かずに済むのは、`OfType` が「その型の要素だけを残す」LINQ のメソッドで、null を除く働きも兼ねるためです
- `Average()` は、要素が 1 つも無いと `InvalidOperationException` になります。値がすべて欠損している列の平均は求められないので、黙って 0 を返すよりこのほうが安全です
- 補完に使う値は `IReadOnlyDictionary<string, double>` で返します。ここでは順序が要らないので、リストではなく辞書です
- `StringComparer.Ordinal` を渡しているのは、文字列のキーを文化圏によらずコードポイントで比べるためです。既定の比較でもこのデータなら同じ結果になりますが、列名の比較が実行環境の文化圏に左右されないことを、書いて示しておきます

### 補完して特徴量にする

補完に使う値は引数で受け取ります。補完の結果は `Row` ではなく `Features` です。

```csharp
public class FillMissingTests
{
    [Fact(DisplayName = "欠損値を列ごとに指定した値で補完して特徴量にする")]
    public void FillsWithGivenValues()
    {
        List<Row> rows = [ColumnMeansTests.Sample("0.1", string.Empty), ColumnMeansTests.Sample(string.Empty, "0.4")];
        List<string> columns = ["がく片長さ", "がく片幅"];

        var filled = Preprocessing.FillMissing(rows, columns, new Dictionary<string, double> { ["がく片長さ"] = 0.2, ["がく片幅"] = 0.5 });

        Assert.Equal([new Features(columns, [0.1, 0.5]), new Features(columns, [0.2, 0.4])], filled);
    }

    [Fact(DisplayName = "元の行は変更しない")]
    public void KeepsOriginalRows()
    {
        List<Row> rows = [ColumnMeansTests.Sample(string.Empty, "0.2")];

        Preprocessing.FillMissing(rows, ["がく片長さ", "がく片幅"], new Dictionary<string, double> { ["がく片長さ"] = 0.2 });

        Assert.True(rows[0].IsMissing("がく片長さ"));
    }
}
```

```csharp
    /// <summary>欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変更しない。</summary>
    public static IReadOnlyList<Features> FillMissing(
        IReadOnlyList<Row> rows, IReadOnlyList<string> columns, IReadOnlyDictionary<string, double> values)
    {
        ArgumentNullException.ThrowIfNull(rows);
        ArgumentNullException.ThrowIfNull(columns);
        ArgumentNullException.ThrowIfNull(values);
        return [.. rows.Select(row => new Features(
            columns,
            [.. columns.Select(column => row.Number(column) ?? FillValue(values, column))]))];
    }

    private static double FillValue(IReadOnlyDictionary<string, double> values, string column) =>
        values.TryGetValue(column, out var value)
            ? value
            : throw new ArgumentException($"補完する値がありません: {column}", nameof(values));
```

`row.Number(column) ?? FillValue(values, column)` の `??` は、第 1 章で使った null 合体演算子です。`double?` が値を持てばその値を、`null` なら補完する値を返し、式全体の型は `double` になります。F# の `Option.defaultValue` に当たりますが、**null 許容値型に対しても同じ演算子が使える** のが C# の書き味です。

`FillValue` が `?:` の中で `throw` を書いているのは **throw 式**（C# 7 以降）です。文ではなく式なので、条件演算子の片側に置けます。

`Row` は変更できないので、「元の行は変更しない」はこの設計から自然に満たされます。F# 版ではリストも `Map` も不変なのでこのテスト自体が要りませんでしたが、C# では「変更できないように作った」ことをテストで固定しておきます。

### Features を record にしなかった理由

`Features` は、最初は `Row` や `Table` と同じく record で書こうとしました。

```csharp
public sealed record Features(IReadOnlyList<string> Columns, double[] Values);
```

これはコンパイルも静的解析も通ります。Java 版では、同じことを書くと Error Prone の `ArrayRecordComponent`（record の成分に配列を使うな）がビルドを止めてくれました。**C# のアナライザーには、これに当たるルールがありません**。止めてくれる人がいないので、自分でテストを書いて確かめます。

```csharp
public class FeaturesTests
{
    [Fact(DisplayName = "値の配列を写して持ち、渡した配列を後から変えても影響を受けない")]
    public void CopiesValues()
    {
        double[] values = [0.1, 0.2];
        var features = new Features(["a", "b"], values);

        values[0] = 9.9;

        Assert.Equal(0.1, features.Value("a"));
    }

    [Fact(DisplayName = "列名と値が同じなら等しい")]
    public void EqualByValue()
    {
        var one = new Features(["a"], [0.1]);
        var other = new Features(["a"], [0.1]);

        Assert.Equal(one, other);
        Assert.Equal(one.GetHashCode(), other.GetHashCode());
    }

    [Fact(DisplayName = "列名と値の数が違えばエラーになる")]
    public void SizeMismatch() =>
        Assert.Throws<ArgumentException>(() => new Features(["a", "b"], [0.1]));
}
```

上の record のままこの 3 件を実行すると、すべて失敗します。

```text
failed 値の配列を写して持ち、渡した配列を後から変えても影響を受けない (61ms)
  Assert.Equal() Failure: Values differ
  Expected: 0.10000000000000001
  Actual:   9.9000000000000004

failed 列名と値が同じなら等しい (10ms)
  Assert.Equal() Failure: Values differ
  Expected: Features { Columns = <>z__ReadOnlySingleElementList`1[System.String], Values = System.Double[] }
  Actual:   Features { Columns = <>z__ReadOnlySingleElementList`1[System.String], Values = System.Double[] }

failed 列名と値の数が違えばエラーになる (2ms)
  Assert.Throws() Failure: No exception was thrown
  Expected: typeof(System.ArgumentException)
```

失敗メッセージが問題の性質をよく表しています。

- 1 件目: record は受け取った配列を **そのまま** 持つので、呼び出し側が後から配列を書き換えると `Features` の値まで変わります
- 2 件目: 期待値と実際の値が **同じ文字列で表示されているのに、等しくない**。record が自動で作る `Equals` は成分を `Equals` で比べますが、配列の `Equals` は中身ではなく **同じ配列かどうか** を比べます。`ToString` も配列を `System.Double[]` としか表示しないので、何が違うのかも読み取れません
- 3 件目: 列名と値の数が合わない組み合わせを、record は素通しします

この失敗の出方は、`Features` を使った他のテストにも波及します。補完のテストも、`Features` の中身が等しいのに落ちました。

```text
failed 欠損値を列ごとに指定した値で補完して特徴量にする (56ms)
  Assert.Equal() Failure: Collections differ
                         ↓ (pos 0)
  Expected: <generated> [Features { Columns = ..., Values = System.Double[] }, ...]
  Actual:   <generated> [Features { Columns = ..., Values = System.Double[] }, ...]
                         ↑ (pos 0)
```

`double` の配列にこだわるのは、第 3 章で ML.NET に `float` のベクトルとして渡すのに、値の並びがそのまま使えるからです。そこで record をやめて、配列を写して持ち、`Equals`・`GetHashCode`・`ToString` を自分で書くクラスにしました。

```csharp
// src/MachineLearning/Chapter02/Features.cs
namespace MachineLearning.Chapter02;

using System.Globalization;

/// <summary>
/// 補完が済んだ 1 行分の特徴量。欠損値を持てないので、補完する前の行をモデルに渡すことはできない。
/// 値は double の配列で持つ。record にすると配列の成分を参照で比べてしまうので、
/// クラスにして Equals・GetHashCode・ToString を中身で比べるように書く。
/// </summary>
public sealed class Features : IEquatable<Features>
{
    private readonly string[] columns;
    private readonly double[] values;

    public Features(IReadOnlyList<string> columns, IReadOnlyList<double> values)
    {
        ArgumentNullException.ThrowIfNull(columns);
        ArgumentNullException.ThrowIfNull(values);
        if (columns.Count != values.Count)
        {
            throw new ArgumentException("列名と値の数が違います", nameof(values));
        }

        this.columns = [.. columns];
        this.values = [.. values];
    }

    /// <summary>列名の並び。</summary>
    public IReadOnlyList<string> Columns => this.columns;

    /// <summary>値の並び。</summary>
    public IReadOnlyList<double> Values => this.values;

    /// <summary>列名で値を読む。</summary>
    public double Value(string column)
    {
        var index = Array.IndexOf(this.columns, column);
        return index < 0 ? throw new ArgumentException($"列がありません: {column}", nameof(column)) : this.values[index];
    }

    public bool Equals(Features? other) =>
        other is not null && this.columns.SequenceEqual(other.columns) && this.values.SequenceEqual(other.values);

    public override bool Equals(object? obj) => this.Equals(obj as Features);

    public override int GetHashCode()
    {
        var hash = default(HashCode);
        foreach (var column in this.columns)
        {
            hash.Add(column);
        }

        foreach (var value in this.values)
        {
            hash.Add(value);
        }

        return hash.ToHashCode();
    }

    public override string ToString() =>
        "Features[" + string.Join(
            ", ",
            this.columns.Select((column, i) => $"{column}={this.values[i].ToString(CultureInfo.InvariantCulture)}")) + "]";
}
```

- 引数の型を `IReadOnlyList<double>` にして、`[.. values]` で写した配列を持ちます。受け取るときに写すので、外から書き換えられません。読み出す `Values` も `IReadOnlyList<double>` として返すので、呼び出し側が要素を書き換えることはできません。Java 版は `double[] values()` で写しを返していましたが、C# は「読み取り専用のインターフェースとして見せる」ほうが呼び出しごとの写しが要らず簡潔です
- `IEquatable<Features>` を実装すると、`Equals(Features?)` が型の決まったオーバーロードとして呼ばれます。`object` を受け取るほうは `as` で型を確かめてから委譲します
- 中身の比較は `SequenceEqual`（LINQ）です。Java 版の `Arrays.equals` に当たります
- `GetHashCode` は `HashCode` 構造体に成分を足していきます。等しいオブジェクトは同じハッシュ値を持つ、という約束を守るためです。片方だけを書くとコンパイラが警告し、`TreatWarningsAsErrors` によってエラーになります（2.11 節）
- `ToString` を書いたので、テストが失敗したときに `Features[a=0.1, b=0.2]` と読める形で表示されます。2 件目の失敗メッセージが `System.Double[]` しか出さなかったのを、ここで直しています

record なら 1 行で済んだものが 50 行以上になりました。その代わり、「写しを持つ」「中身で比べる」という約束を、上の 3 件のテストで固定しています。

> C# で record を使うときの一般則として、**成分が参照型なら値では比べられません**。この章の `Row` と `Table` も record ですが、成分が辞書やリストなので、2 つの `Row` を `Assert.Equal` で比べても等しくなりません。テストの期待値として並べるのは `Features` だけなので、値で比べる必要があるのも `Features` だけ、という整理です。

## 2.7 特徴量と正解ラベルに分ける

```csharp
    /// <summary>正解ラベルの列を取り出し、残りの列を特徴量の列にする。</summary>
    public static (IReadOnlyList<string> Columns, IReadOnlyList<Row> Rows, IReadOnlyList<string> Target)
        SplitFeaturesAndTarget(Table table, string target)
    {
        ArgumentNullException.ThrowIfNull(table);
        return (
            [.. table.Columns.Where(column => !string.Equals(column, target, StringComparison.Ordinal))],
            table.Rows,
            [.. table.Rows.Select(row => row.Text(target))]);
    }
```

戻り値は、第 1 章の `SplitFeaturesAndLabels` と同じく **名前付きのタプル** です。Java 版は 3 つの値を返すために `FeaturesAndTarget` という record を作りましたが、C# では型を宣言せずに名前付きで返せます。呼び出し側は分解して受け取れます。

```csharp
var (columns, rows, target) = Preprocessing.SplitFeaturesAndTarget(Table.Load(csvFile), Preprocessing.Target);
```

F# 版は特徴量を `Map` にして持つ `IrisRow` レコードにまとめ、読み込みの段階で正解ラベルと分けていました。C# 版の `Row` は列名で引く形なので、行から列を消す必要はありません。「どの列を特徴量として読むか」を列名のリストで持ち、行はそのまま渡します。

`string.Equals(column, target, StringComparison.Ordinal)` と書いているのは、`ColumnMeans` の `StringComparer.Ordinal` と比べ方をそろえるためです。`column != target` でも同じ結果になりますが、列名の比較が文化圏に左右されないことを、この章では一貫して明示しています。

## 2.8 訓練データとテストデータに分ける

### 分割結果を表す型

```csharp
// src/MachineLearning/Chapter02/TrainTestSplit.cs
namespace MachineLearning.Chapter02;

/// <summary>訓練データとテストデータ。TX は特徴量の型、TT は正解ラベルの型。</summary>
public sealed record TrainTestSplit<TX, TT>(
    IReadOnlyList<TX> XTrain,
    IReadOnlyList<TX> XTest,
    IReadOnlyList<TT> TTrain,
    IReadOnlyList<TT> TTest);
```

F# 版の `TrainTestSplit<'X, 'T>` と同じく、特徴量の型と正解ラベルの型の両方を型引数にします。2.9 節で見るように、補完する前の `Row` のまま分けてから、訓練データの平均値で補完して `Features` にするためです。第 7 章からは、価格のような数値の正解ラベルも同じ型で受け取ります。

### 仮実装

0 から始まる番号を振ったデータで、件数だけを確かめます。

```csharp
public class SplitTrainTestTests
{
    private static readonly List<int> X = [.. Enumerable.Range(0, 10)];
    private static readonly List<string> T = [.. Enumerable.Range(0, 10).Select(i => $"label{i}")];

    [Fact(DisplayName = "テストデータの割合どおりの件数に分ける")]
    public void SplitsByRatio()
    {
        var split = Preprocessing.SplitTrainTest(X, T, 0.3, 0);

        Assert.Equal(7, split.XTrain.Count);
        Assert.Equal(3, split.XTest.Count);
        Assert.Equal(7, split.TTrain.Count);
        Assert.Equal(3, split.TTest.Count);
    }
}
```

`SplitTrainTest` を **ジェネリックメソッド** にしたので、テストでは特徴量に `int` の番号をそのまま使えます。

仮実装では、先頭の 7 件を訓練データにします。

```csharp
    public static TrainTestSplit<TX, TT> SplitTrainTest<TX, TT>(
        IReadOnlyList<TX> x, IReadOnlyList<TT> t, double testSize, int seed)
    {
        IReadOnlyList<(TX First, TT Second)> pairs = [.. x.Zip(t)];
        var trainCount = 7;
        var train = pairs.Take(trainCount).ToList();
        var test = pairs.Skip(trainCount).ToList();
        return new TrainTestSplit<TX, TT>(
            [.. train.Select(pair => pair.First)],
            [.. test.Select(pair => pair.First)],
            [.. train.Select(pair => pair.Second)],
            [.. test.Select(pair => pair.Second)]);
    }
```

`x.Zip(t)` は、2 つの列を先頭から組にします。F# 版が `List.zip` で組にしてからシャッフルしたのと同じで、**組のまま動かせば特徴量と正解ラベルの対応は崩れようがありません**。Java 版は行の位置をシャッフルし、その位置で取り出す形でした。C# は `Zip` が標準にあるので、F# 版と同じ書き方ができます。

`Zip` が返す要素は `(TFirst First, TSecond Second)` という名前付きのタプルです。`pair.First`・`pair.Second` で取り出せます。

### 三角測量: 件数を一般化する

```csharp
    [Fact(DisplayName = "件数が変わってもテストデータの割合どおりに分ける")]
    public void SplitsOtherSizes()
    {
        var twenty = Enumerable.Range(0, 20).ToList();

        var split = Preprocessing.SplitTrainTest(twenty, twenty, 0.25, 0);

        Assert.Equal(15, split.XTrain.Count);
        Assert.Equal(5, split.XTest.Count);
    }
```

```text
failed 件数が変わってもテストデータの割合どおりに分ける (55ms)
  Assert.Equal() Failure: Values differ
  Expected: 15
  Actual:   7
```

2 つ目の例で、7 がベタ書きの値だったことが露わになりました。F# 版と同じく、件数と割合の **両方** を変えているのがポイントです。10 件のまま割合だけを 0.25 にすると、10 件の 2 割 5 分は 2.5 件で、切り上げると 3 件、つまり 0.3 のときと同じ 7 件と 3 件になり、仮実装のままでも通ってしまいます。

テストデータの件数を切り上げて求めます。

```csharp
        var trainCount = pairs.Count - (int)Math.Ceiling(pairs.Count * testSize);
```

`Math.Ceiling` の戻り値は `double` なので、`(int)` で整数に変換してから引きます。

### 三角測量: 並び順に頼らない分け方にする

件数だけでなく、分け方の性質もテストします。

```csharp
    [Fact(DisplayName = "すべての行を重複なく訓練データとテストデータのどちらかに入れる")]
    public void CoversAllRowsOnce()
    {
        var split = Preprocessing.SplitTrainTest(X, T, 0.3, 0);

        Assert.Empty(split.XTrain.Intersect(split.XTest));
        Assert.Equal(X, [.. split.XTrain.Concat(split.XTest).Order()]);
    }

    [Fact(DisplayName = "特徴量と正解ラベルの対応を保ったまま分ける")]
    public void KeepsPairs()
    {
        var split = Preprocessing.SplitTrainTest(X, T, 0.3, 0);

        Assert.Equal(split.XTrain.Select(i => $"label{i}"), split.TTrain);
        Assert.Equal(split.XTest.Select(i => $"label{i}"), split.TTest);
    }

    [Fact(DisplayName = "同じシードなら同じ分け方になる")]
    public void SameSeedSameSplit() =>
        Assert.Equal(
            Preprocessing.SplitTrainTest(X, T, 0.3, 42).TTest,
            Preprocessing.SplitTrainTest(X, T, 0.3, 42).TTest);

    [Fact(DisplayName = "シードが違えば違う分け方になる")]
    public void DifferentSeedDifferentSplit() =>
        Assert.NotEqual(
            Preprocessing.SplitTrainTest(X, T, 0.3, 0).TTest,
            Preprocessing.SplitTrainTest(X, T, 0.3, 1).TTest);
```

- `Intersect`（共通する要素）と `Order`（並べ替え）は LINQ の集合・整列の演算です。Java 版が `HashSet` を作って AssertJ の専用メソッドで確かめていたところを、C# は標準の LINQ だけで書けます
- 最後の 2 件は、シードによる再現性を確かめます

```text
failed シードが違えば違う分け方になる (5ms)
  Assert.NotEqual() Failure: Collections are equal
  Expected: Not ["label7", "label8", "label9"]
  Actual:       ["label7", "label8", "label9"]
```

Python 版・F# 版・Java 版と同じく、先頭から順に分けるだけでは「シードが違えば違う分け方になる」だけが失敗します。どのシードでも最後の 3 件がテストデータになるためです。

### Green: F# 版と同じシャッフルにする

シャッフルは、分割とは別の関数として取り出します。F# 版が `Random.shuffle` を独立した関数にしていたのと同じ切り分けです。

```csharp
    /// <summary>
    /// シードを使って Fisher-Yates のシャッフルで並べ替える。F# 版（Chapter02.Random.shuffle）と
    /// 同じ手順・同じ乱数なので、同じシードなら同じ並びになる。元のリストは変更しない。
    /// </summary>
    public static IReadOnlyList<T> Shuffle<T>(IReadOnlyList<T> items, int seed)
    {
        ArgumentNullException.ThrowIfNull(items);
        var random = new Random(seed);
        var array = items.ToArray();
        for (var i = array.Length - 1; i >= 1; i--)
        {
            var j = random.Next(i + 1);
            (array[i], array[j]) = (array[j], array[i]);
        }

        return array;
    }
```

```csharp
        var pairs = Shuffle([.. x.Zip(t)], seed);
        var trainCount = pairs.Count - (int)Math.Ceiling(pairs.Count * testSize);
```

- `new Random(seed)` は、シードが同じなら同じ乱数列を返す生成器です。F# 版の `Random seed` と同じクラスです
- `(array[i], array[j]) = (array[j], array[i])` は **タプルによる分解代入** で、一時変数を使わずに 2 つの要素を入れ替えます。F# 版が `let tmp = ...` と 3 行で書いていたところが 1 行になります
- `items.ToArray()` で写してから並べ替えるので、引数のリストは変わりません。F# 版が「不変なリストを配列に写してから入れ替え、配列をリストに戻す」と書いたのと同じ手順です
- 戻り値を `IReadOnlyList<T>` にして、呼び出し側からは書き換えられない並びとして見せます

Fisher-Yates のシャッフルは、末尾から順に「それより前のどこか」と入れ替えていく方法で、すべての並べ方が同じ確率で出ます。F# 版と **回す向き・`Next` に渡す範囲・交換する相手** をそろえてあるので、同じシードなら同じ並びになります。

```csharp
    [Fact(DisplayName = "F# 版と同じ Fisher-Yates の並べ替えになる")]
    public void SameAsFSharp()
    {
        // F# 版（apps/fsharp の Chapter02.Random.shuffle）と同じ手順・同じシードなら同じ並びになる
        var shuffled = Preprocessing.Shuffle(X, 0);

        Assert.Equal(X.Count, shuffled.Count);
        Assert.Equal(X, [.. shuffled.Order()]);
    }
```

このテスト自体は「要素を失わない」ことしか確かめていません。F# 版と本当に同じ並びになるかは、実データの平均値とテストデータの先頭のラベルで固定します（2.10 節）。

.NET 6 以降の `Random` は、シードを渡さないと新しいアルゴリズム（xoshiro256\*\*）を使いますが、シードを渡すと以前の .NET と同じ乱数列を返すアルゴリズムを使います。シード付きの乱数列が .NET の版によって変わるかどうかは、第 4 章で扱います。

第 7 章からは、価格のような数値の正解ラベルも同じメソッドで分けます。最初から `<TX, TT>` にしたので、数値の正解ラベルのテストも実装を変えずに通ります。

```csharp
    [Fact(DisplayName = "数値の正解ラベルも特徴量との対応を保ったまま分ける")]
    public void NumericLabels()
    {
        var numeric = X.Select(i => i * 0.5).ToList();

        var split = Preprocessing.SplitTrainTest(X, numeric, 0.3, 0);

        Assert.Equal(split.XTest.Select(i => i * 0.5), split.TTest);
    }
```

## 2.9 前処理をまとめる

読み込み、分割、補完を 1 つのメソッドにまとめます。

```csharp
    /// <summary>正解ラベルの列</summary>
    public const string Target = "種類";

    /// <summary>iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。</summary>
    public static TrainTestSplit<Features, string> PrepareIris(string csvFile, double testSize, int seed)
    {
        var (columns, rows, target) = SplitFeaturesAndTarget(Table.Load(csvFile), Target);
        var split = SplitTrainTest(rows, target, testSize, seed);
        var means = ColumnMeans(split.XTrain, columns);
        return new TrainTestSplit<Features, string>(
            FillMissing(split.XTrain, columns, means),
            FillMissing(split.XTest, columns, means),
            split.TTrain,
            split.TTest);
    }
```

型の流れを追うと、補完の前後の区別がはっきり見えます。

1. `SplitTrainTest` は `Row`（欠損しうる）のまま分けて `TrainTestSplit<Row, string>` を返す
2. `ColumnMeans` は訓練データの `Row` だけから平均値を求める
3. `FillMissing` がその平均値で訓練データとテストデータの両方を補完し、`TrainTestSplit<Features, string>`（欠損値を持てない）にする

戻り値の型が `TrainTestSplit<Features, string>` なので、`PrepareIris` を通ったデータに欠損値が残っていないことは型が保証します。F# 版が `TrainTestSplit<Map<string, float>, string>` で同じことを保証していたのと同じ設計です。

補完の前と後で型が違うので、F# 版と同じく新しい `TrainTestSplit` を作って返しています。C# の record には `with` 式があり、一部の成分だけを変えた写しを作れますが、ここでは型引数そのものが変わるので使えません。

## 2.10 実データで前処理の結果を表示する

### 実データのテスト

第 1 章と同じく、学習データが無ければスキップします。

```csharp
// tests/MachineLearning.Tests/Chapter02/IrisDataTests.cs
namespace MachineLearning.Tests.Chapter02;

using MachineLearning.Chapter02;
using MachineLearning.Dataset;

public class IrisDataTests
{
    private readonly string csvFile = Path.Combine(DataDir.Current(), "iris.csv");

    [Fact(DisplayName = "実データの列ごとの欠損値の数を数える")]
    public void CountsMissing()
    {
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");

        Assert.Equal(
            [("がく片長さ", 2), ("がく片幅", 1), ("花弁長さ", 2), ("花弁幅", 2), ("種類", 0)],
            Table.Load(this.csvFile).CountMissing().Select(pair => (pair.Key, pair.Value)));
    }

    [Fact(DisplayName = "実データを 105 件と 45 件に分けて欠損値を補完する")]
    public void SplitsAndFills()
    {
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");

        var split = Preprocessing.PrepareIris(this.csvFile, 0.3, 0);

        Assert.Equal(105, split.XTrain.Count);
        Assert.Equal(45, split.XTest.Count);
        Assert.Equal(105, split.TTrain.Count);
        Assert.Equal(45, split.TTest.Count);
    }
}
```

F# 版の実データのテストは、補完した後の欠損値の数が 0 であることも確かめていました。C# 版の `Features` は欠損値を持てないので、その検査は型が代わりに担います。数えようにも、`Features` には欠損値を数える方法がありません。

### F# 版と同じ分割になることを固定する

2.8 節でそろえた分割が、実データでも F# 版と一致することをテストで固定します。

```csharp
    [Fact(DisplayName = "訓練データの平均値は F# 版と同じになる（同じ乱数と同じ分け方）")]
    public void SameSplitAsFSharp()
    {
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");

        var (columns, rows, target) = Preprocessing.SplitFeaturesAndTarget(Table.Load(this.csvFile), Preprocessing.Target);
        var split = Preprocessing.SplitTrainTest(rows, target, 0.3, 0);

        var means = Preprocessing.ColumnMeans(split.XTrain, columns);

        // F# 版（apps/fsharp、System.Random と同じ Fisher-Yates）で実測した値
        Assert.Equal(0.424808, means["がく片長さ"], 6);
        Assert.Equal(0.462286, means["がく片幅"], 6);
        Assert.Equal(0.479135, means["花弁長さ"], 6);
        Assert.Equal(0.432404, means["花弁幅"], 6);
        Assert.Equal(
            ["Iris-versicolor", "Iris-setosa", "Iris-setosa", "Iris-versicolor", "Iris-versicolor"],
            split.TTest.Take(5));
    }
```

- **訓練データの平均値** が一致すれば、訓練データに入った 105 件が同じであることの強い裏付けになります。1 件でも入れ替わっていれば、小数第 6 位まで一致することはまずありません
- **テストデータの先頭 5 件のラベル** が一致すれば、並び順まで同じであることが分かります

学習データの行そのものを記事やテストに書くわけにはいかないので、「派生した数値が一致すること」で分割の一致を表しています。第 3 章の正解率や決定木の境界が F# 版とそろうのは、この分割が同じだからです。

Java 版は `Collections.shuffle` を使うので、同じシードでもこの値にはなりません。乱数生成器をそろえるかどうかで、こうしたテストが書けるかが変わります。

### 結果を表示する

表示のテストを書きます。第 1 章と同じく、出力先を `TextWriter` として受け取ります。

```csharp
    [Fact(DisplayName = "実行すると前処理の結果を表示する")]
    public void PrintsSummary()
    {
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");

        using var output = new StringWriter();
        Program.Run(output);

        Assert.Equal(
            string.Join(
                Environment.NewLine,
                "データ件数: 150",
                "欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0",
                "訓練データ: 105 件, テストデータ: 45 件",
                "特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅") + Environment.NewLine,
            output.ToString());
    }
```

Java 版はテキストブロック（`"""`）で期待する表示を書きましたが、C# の生文字列リテラル（`"""`）は改行を OS ごとに変えてくれません。`WriteLine` が書き込む改行は Windows では `\r\n` なので、`string.Join(Environment.NewLine, ...)` で組み立てています。行ごとに並べて読めるという点では、テキストブロックと同じくらい読みやすく書けます。

```csharp
// src/MachineLearning/Chapter02/Program.cs
namespace MachineLearning.Chapter02;

using MachineLearning.Dataset;

/// <summary>アヤメのデータの前処理の結果を表示する。</summary>
public static class Program
{
    private const double TestSize = 0.3;
    private const int Seed = 0;

    public static void Run(TextWriter output)
    {
        ArgumentNullException.ThrowIfNull(output);
        var csvFile = Path.Combine(DataDir.Current(), "iris.csv");
        var table = Table.Load(csvFile);
        var split = Preprocessing.PrepareIris(csvFile, TestSize, Seed);
        output.WriteLine($"データ件数: {table.Rows.Count}");
        output.WriteLine(
            "欠損値の数: " + string.Join(", ", table.CountMissing().Select(pair => $"{pair.Key}={pair.Value}")));
        output.WriteLine($"訓練データ: {split.XTrain.Count} 件, テストデータ: {split.XTest.Count} 件");
        output.WriteLine("特徴量: " + string.Join(", ", split.XTrain[0].Columns));
    }
}
```

F# 版の最後の行は「補完後の欠損値の数: 訓練データ 0, テストデータ 0」でした。F# 版は補完後の型が `Map<string, float>` で `None` が入りえないため、代わりに `NaN` の数を数えて表示していました。C# 版では、その行の代わりに補完した特徴量の列を表示しています。`Features` は欠損値を持てないので、数えるまでもないからです。

`CountMissing` がリストを返すので、表示は列の順に並びます。F# 版が `Map` のキーの順を避けるために `FeatureNames` の順で引き直していた手間が要りません。

`src/MachineLearning/Program.cs` の対応表に、第 2 章を加えます。

```csharp
    private static readonly Dictionary<string, Action<TextWriter>> Chapters = new(StringComparer.Ordinal)
    {
        ["chapter01"] = Chapter01.Program.Run,
        ["chapter02"] = Chapter02.Program.Run,
    };
```

第 1 章で辞書にしておいたので、1 行足すだけで済みます。使い方の表示にも同じ辞書を使うので、そちらは直さなくても新しい章の名前が並びます。

```bash
dotnet run --project src/MachineLearning -- chapter02
```

```text
データ件数: 150
欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
訓練データ: 105 件, テストデータ: 45 件
特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅
```

件数と欠損値の数は乱数に関係しないので、どの言語の版でも同じです。

テストを実行します。`dotnet test` は `apps/csharp` を作業ディレクトリにして実行します（第 1 章 1.5 節）。

```bash
cd apps/csharp
dotnet test
```

第 2 章までのテストは 39 件（第 1 章の 15 件と第 2 章の 24 件）です。データが無い環境では、実データのテスト 7 件（第 1 章の 3 件と第 2 章の 4 件）がスキップされます。

```bash
ML_DATA_DIR=/nonexistent dotnet run --project tests/MachineLearning.Tests/MachineLearning.Tests.csproj
```

```text
skipped 実データの列ごとの欠損値の数を数える (0ms)
skipped 訓練データの平均値は F# 版と同じになる（同じ乱数と同じ分け方） (0ms)
skipped 実データを 105 件と 45 件に分けて欠損値を補完する (0ms)
skipped 実行すると前処理の結果を表示する (0ms)

  total: 39
  failed: 0
  succeeded: 32
  skipped: 7
```

**TODO リスト**:

- [x] iris.csv を読み込む
  - [x] 空欄を欠損値（`null`）として読み込む
  - [x] 行末の空欄も欠損値として読み込む
- [x] 列ごとの欠損値の数を数える
- [x] 列ごとの平均値を求める
- [x] 欠損値を補完する
  - [x] 列ごとに指定した値で補完する
  - [x] 元のデータは変更しない
- [x] 特徴量と正解ラベルに分ける
- [x] 訓練データとテストデータに分ける
  - [x] テストデータの割合どおりの件数に分ける
  - [x] すべての行を重複なくどちらかに入れる
  - [x] 特徴量と正解ラベルの対応を保つ
  - [x] 同じシードなら同じ分け方になる
  - [x] シードが違えば違う分け方になる
- [x] 訓練データの平均値で両方を補完する
- [x] 実データで前処理の結果を表示する

## 2.11 リファクタリング

この章で、検査に指摘されて直したことを整理します。

| 仕組み | 指摘されたこと | 直したもの |
|--------|--------------|-----------|
| C# コンパイラ + `TreatWarningsAsErrors` | `Equals` を書いたのに `GetHashCode` が無い | `HashCode` で中身から計算 |
| 自分で書いたテスト | 配列を持つ record が値で比べられない | `Features` をクラスにして `IEquatable<Features>` を実装 |

1 つ目は、第 1 章と同じくビルドの中でエラーとして止まります。`Features` から `GetHashCode` を消してビルドすると、次のように止まりました。

```text
src/MachineLearning/Chapter02/Features.cs(10,21): error CS0659: 'Features' は Object.Equals(object o) をオーバーライドしますが、Object.GetHashCode() をオーバーライドしません。
```

「等しいオブジェクトは同じハッシュ値を持つ」という約束を破ると辞書やハッシュ集合が壊れるので、片方だけを書くことは許されません。

2 つ目は **誰も止めてくれなかった** ものです。Java 版では Error Prone がビルドを止め、記事にも「コンパイラが教えてくれた」と書けましたが、C# 版では自分でテストを書くまで気づけませんでした。

この差は、どちらの言語が優れているかという話ではありません。「ツールが見てくれる範囲は言語ごとに違う」という事実であり、**見てくれない範囲は自分でテストにする** という当たり前の規律に戻る、ということです。`Features` の 3 件のテスト（写して持つ・中身で比べる・数が合わなければエラー）は、Error Prone の代わりを務めています。

整形と検査を通します。

```bash
dotnet format
dotnet build
dotnet test
```

学習データを配置した状態では、39 件すべてが通ります。

## 2.12 可視化について

C# 版には Notebook の節を設けません。欠損値の分布や、品種ごとの特徴量の散布図は、[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md) と [F# 版の第 2 章](../fsharp/02-data-preprocessing-and-triangulation.md) の「Notebook で探索する」の節を参照してください。

C# 版の分割は F# 版と一致するので、F# 版の Notebook が示した品種ごとの平均値（`Iris-setosa` は花弁長さ・花弁幅がほかの 2 品種よりはっきり小さい）は、そのまま C# 版の訓練データの傾向でもあります。第 3 章の決定木は、この傾向を自動で見つけます。

<details>
<summary>この章の完成コード（src/MachineLearning/Chapter02/Preprocessing.cs）</summary>

```csharp
namespace MachineLearning.Chapter02;

/// <summary>アヤメのデータの前処理。</summary>
public static class Preprocessing
{
    /// <summary>正解ラベルの列</summary>
    public const string Target = "種類";

    /// <summary>欠損値を除いて、列ごとの平均値を求める。</summary>
    public static IReadOnlyDictionary<string, double> ColumnMeans(
        IReadOnlyList<Row> rows, IReadOnlyList<string> columns)
    {
        ArgumentNullException.ThrowIfNull(rows);
        ArgumentNullException.ThrowIfNull(columns);
        return columns.ToDictionary(
            column => column,
            column => rows.Select(row => row.Number(column)).OfType<double>().Average(),
            StringComparer.Ordinal);
    }

    /// <summary>欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変更しない。</summary>
    public static IReadOnlyList<Features> FillMissing(
        IReadOnlyList<Row> rows, IReadOnlyList<string> columns, IReadOnlyDictionary<string, double> values)
    {
        ArgumentNullException.ThrowIfNull(rows);
        ArgumentNullException.ThrowIfNull(columns);
        ArgumentNullException.ThrowIfNull(values);
        return [.. rows.Select(row => new Features(
            columns,
            [.. columns.Select(column => row.Number(column) ?? FillValue(values, column))]))];
    }

    /// <summary>正解ラベルの列を取り出し、残りの列を特徴量の列にする。</summary>
    public static (IReadOnlyList<string> Columns, IReadOnlyList<Row> Rows, IReadOnlyList<string> Target)
        SplitFeaturesAndTarget(Table table, string target)
    {
        ArgumentNullException.ThrowIfNull(table);
        return (
            [.. table.Columns.Where(column => !string.Equals(column, target, StringComparison.Ordinal))],
            table.Rows,
            [.. table.Rows.Select(row => row.Text(target))]);
    }

    /// <summary>
    /// シードを使って Fisher-Yates のシャッフルで並べ替える。F# 版（Chapter02.Random.shuffle）と
    /// 同じ手順・同じ乱数なので、同じシードなら同じ並びになる。元のリストは変更しない。
    /// </summary>
    public static IReadOnlyList<T> Shuffle<T>(IReadOnlyList<T> items, int seed)
    {
        ArgumentNullException.ThrowIfNull(items);
        var random = new Random(seed);
        var array = items.ToArray();
        for (var i = array.Length - 1; i >= 1; i--)
        {
            var j = random.Next(i + 1);
            (array[i], array[j]) = (array[j], array[i]);
        }

        return array;
    }

    /// <summary>並べ替えてから、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。</summary>
    public static TrainTestSplit<TX, TT> SplitTrainTest<TX, TT>(
        IReadOnlyList<TX> x, IReadOnlyList<TT> t, double testSize, int seed)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        if (x.Count != t.Count)
        {
            throw new ArgumentException("特徴量と正解ラベルの件数が違います", nameof(t));
        }

        var pairs = Shuffle([.. x.Zip(t)], seed);
        var trainCount = pairs.Count - (int)Math.Ceiling(pairs.Count * testSize);
        var train = pairs.Take(trainCount).ToList();
        var test = pairs.Skip(trainCount).ToList();
        return new TrainTestSplit<TX, TT>(
            [.. train.Select(pair => pair.First)],
            [.. test.Select(pair => pair.First)],
            [.. train.Select(pair => pair.Second)],
            [.. test.Select(pair => pair.Second)]);
    }

    /// <summary>iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。</summary>
    public static TrainTestSplit<Features, string> PrepareIris(string csvFile, double testSize, int seed)
    {
        var (columns, rows, target) = SplitFeaturesAndTarget(Table.Load(csvFile), Target);
        var split = SplitTrainTest(rows, target, testSize, seed);
        var means = ColumnMeans(split.XTrain, columns);
        return new TrainTestSplit<Features, string>(
            FillMissing(split.XTrain, columns, means),
            FillMissing(split.XTest, columns, means),
            split.TTrain,
            split.TTest);
    }

    private static double FillValue(IReadOnlyDictionary<string, double> values, string column) =>
        values.TryGetValue(column, out var value)
            ? value
            : throw new ArgumentException($"補完する値がありません: {column}", nameof(values));
}
```

</details>

`Table.cs`・`Row.cs`・`Features.cs`・`TrainTestSplit.cs`・`Program.cs` は、本文に載せたものが完成版です。

## 2.13 まとめ

この章では、型プロバイダもデータフレームのライブラリも使わずに、欠損値を含むデータを前処理し、訓練データとテストデータに分けました。

1. **小さな表を自作する** — セルの文字列を持つ `Row` と、列名で引く `Number`・`Text` で、章をまたいで使い回せる表にした。F# の型プロバイダが与えてくれた「列名の間違いがコンパイルエラーになる」保証は無く、実行時の `ArgumentException` になる
2. **`double?` で欠損値を表す** — F# の `float option` に当たる null 許容値型を使い、`OfType<double>()` で値だけを取り出し、`??` で既定値に落とした
3. **補完の前後を型で分ける** — 欠損しうる `Row` と、欠損値を持てない `Features` を分け、`PrepareIris` の戻り値の型で「欠損値が残っていない」ことを保証した
4. **record の限界** — 配列を持つ record は値で比べられない。Java 版では Error Prone が止めてくれたが、C# のアナライザーは止めないので、自分でテストを書いて `Features` をクラスにした
5. **三角測量と再現性** — 件数と割合の両方を変えたテストで件数の計算を一般化し、シードによる再現性のテストでシャッフルに一般化した
6. **F# 版と分割をそろえる** — `System.Random` と Fisher-Yates を F# 版と同じ手順にし、訓練データの平均値とテストデータの先頭のラベルで一致を固定した

次の章では、抽象レコードと sealed な派生で決定木を自作し、`switch` 式の網羅性の検査がどこまで効くかを確かめ、ML.NET の FastTree と予測を突き合わせます。
