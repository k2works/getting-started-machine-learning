---
type: Article
title: "第 8 章: 実践的な分類と前処理パイプライン"
description: "Survived データのグループ別中央値・最頻値の補完とダミー変数化を、インターフェース（ITransformer・IFittedTransformer）と record で TDD で実装し、パイプラインにつなぐ。重み付きのジニ不純度でクラスの重みを表し、System.Text.Json の多態で学習済みのパイプラインを保存して読み込み、ML.NET の FastTree の行の重み（ExampleWeightColumnName）と突き合わせる。"
tags: [article,getting-start-ml,csharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T01:40:00Z }
---

# 第 8 章: 実践的な分類と前処理パイプライン

## 8.1 はじめに

これまでの章のデータは、欠損値が少なく、特徴量もすべて数値でした。現実のデータはそうはいきません。この章で扱うタイタニック号の乗客データには、次の 3 つの難しさがあります。

- **欠損値が多い** — 年齢が 2 割近く欠けている
- **カテゴリ値がある** — 性別や乗船した港が文字列で記録されている
- **クラスが偏っている** — 生存者より死亡者のほうが多い

この章では、これらを 1 つずつ小さな部品として TDD で実装し、前処理からモデルまでを 1 つの **パイプライン** につなぎます。最後にそのパイプラインをファイルに保存し、読み込んで予測できるようにします。保存したパイプラインは、第 15 章で作る機械学習 API から使います。

[Python 版の第 8 章](../python/08-classification-and-preprocessing-pipeline.md) は scikit-learn の変換器・`Pipeline`・`class_weight` を、[F# 版の第 8 章](../fsharp/08-classification-and-preprocessing-pipeline.md) は型引数を持つレコードと関数の合成（`>>`）を使いました。C# 版では、次の 3 点に注目してください。

- **前処理をインターフェースで表す** — 「訓練データから値を学ぶ」`ITransformer` と「学んだ値でデータを変換する」`IFittedTransformer` に分ける。F# 版は「学習した値はデータ、変換は関数」を組（レコードと関数）で表しましたが、C# では 2 つのインターフェースと、それを実装する `record` で表します
- **第 3 章の木をなぞって、重みを通す** — 木の型は第 3 章と同じ「抽象レコードと sealed な派生」で作り、この章では 1 件ごとの重みを通す学習だけを書きます。ラベルは `int`（1 が生存、0 が死亡）です
- **ライブラリの境界で形を変えない** — System.Text.Json は多態（`JsonPolymorphic`）を標準で扱えるので、F# 版が必要とした DTO への詰め替えが要りません。ML.NET では、クラスの重みを **行の重み**（`ExampleWeightColumnName`）で表せることを確かめ、モデルを zip で保存します

可視化は [Python 版の 8.13 節](../python/08-classification-and-preprocessing-pipeline.md) と [Kotlin 版の第 8 章](../kotlin/08-classification-and-preprocessing-pipeline.md) を参照してください。C# 版では Notebook と可視化の節を作りません。

## 8.2 題材とデータ

### Survived.csv

`Survived.csv` には、乗客 891 人の情報が記録されています。

| 列 | 意味 | 値 | 欠損値 |
|----|------|-----|------|
| PassengerId | 乗客の番号 | 整数 | 0 件 |
| Survived | 生存したか（正解ラベル） | 1（生存）または 0（死亡） | 0 件 |
| Pclass | 客室クラス | 1, 2, 3 | 0 件 |
| Sex | 性別 | `male` または `female` | 0 件 |
| Age | 年齢 | 小数 | 177 件 |
| SibSp | 同乗した兄弟・配偶者の数 | 整数 | 0 件 |
| Parch | 同乗した親・子の数 | 整数 | 0 件 |
| Ticket | チケット番号 | 文字列 | 0 件 |
| Fare | 運賃 | 小数 | 0 件 |
| Cabin | 客室番号 | 文字列 | 687 件 |
| Embarked | 乗船した港 | `C`・`Q`・`S` | 2 件 |

正解ラベルは生存 342 人、死亡 549 人で、死亡のほうが 1.6 倍ほど多くなっています。

### 使う特徴量と、使わない列

特徴量には `Pclass`・`Sex`・`Age`・`SibSp`・`Parch`・`Fare`・`Embarked` の 7 列を使います。`PassengerId` と `Ticket` は乗客を区別するための値で、生存との関係は期待できません。`Cabin` は 891 件中 687 件が欠けているので、今回は使いません。

### 年齢はグループごとの中央値で補完する

年齢の欠損値を全体の平均値で補完すると、1 等客室の年配の乗客も、3 等客室の若い乗客も、同じ年齢で埋まってしまいます。そこで、客室クラスと性別の組み合わせ（グループ）ごとの中央値で補完します。平均値でなく中央値を使うのは、一部の高齢の乗客に値が引っ張られにくいからです。

グループ分けに正解ラベル（`Survived`）を使ってはいけません。予測するときには、その乗客が生存したかは分からないからです。グループ分けに使えるのは、予測の時点で分かっている特徴量だけです。

## 8.3 TODO リストの作成

**TODO リスト**:

- [ ] CSV を読み込み、特徴量の列と正解ラベルに分ける
- [ ] 年齢の欠損値を補完する
  - [ ] 同じグループの中央値で補完する
  - [ ] グループごとに異なる中央値で補完する
  - [ ] 訓練データで求めた中央値を、別のデータの補完に使う
  - [ ] 訓練データに無いグループは、全体の中央値で補完する
- [ ] 乗船した港の欠損値を、最も多い値で補完する
- [ ] カテゴリ値を 0 と 1 の列（ダミー変数）にする
  - [ ] 訓練データと別のデータで、同じ列を作る
- [ ] クラスの重みを付けた決定木を作る
- [ ] 前処理とモデルを 1 つのパイプラインにつなぐ
- [ ] モデルを保存して読み込む
- [ ] 正解率と、見つけた生存者の数で評価する
- [ ] 実データでクラスの重みの効果を確かめる

## 8.4 CSV を読み込み、特徴量と正解ラベルに分ける

第 2 章で作った `Table`・`Row` をそのまま使います。`Row` はセルの **文字列** を列名で持ち、`Number` で数値として（空欄なら `null`）、`Text` で文字列として読み出せます。第 8 章の `Sex`・`Embarked` は文字列の特徴量なので、`Text` で読みます。

```csharp
// src/MachineLearning/Chapter08/SurvivedData.cs
public static class SurvivedData
{
    /// <summary>正解ラベルの列（1 が生存、0 が死亡）</summary>
    public const string Target = "Survived";

    /// <summary>モデルに渡す特徴量の列</summary>
    public static IReadOnlyList<string> FeatureColumns { get; } =
        ["Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked"];

    /// <summary>行を、特徴量の列だけを並べた表にする。行がほかの列を持っていても使わない。</summary>
    public static Table ToTable(IReadOnlyList<Row> rows) => new(FeatureColumns, rows);

    /// <summary>行の Survived 列を、整数の正解ラベルにする。</summary>
    public static IReadOnlyList<int> Labels(IReadOnlyList<Row> rows)
    {
        ArgumentNullException.ThrowIfNull(rows);
        return [.. rows.Select(row => int.Parse(row.Text(Target), CultureInfo.InvariantCulture))];
    }
}
```

`Table` は「列名の並び」と「行のリスト」を持つ `record` です。`ToTable` は行はそのままに、列の並びだけを特徴量の 7 列に絞ります。行は `PassengerId` や `Cabin` のセルを持ったままですが、表の列に含まれないので前処理でもモデルでも使われません。F# 版が型プロバイダで `SurvivedRow` という型を作ったのに対し、C# 版は列名で引く表のまま最後まで進めます。

テストでは、架空の乗客を組み立てる小さな補助を用意しました。学習データの行はテストにも記事にも書きません。

```csharp
// tests/MachineLearning.Tests/Chapter08/Passengers.cs
internal static class Passengers
{
    /// <summary>特徴量の列の順に並べた値から、乗客 1 人分の行を作る。空文字列は欠損値。</summary>
    public static Row Of(params string[] values)
    {
        var cells = new Dictionary<string, string>(StringComparer.Ordinal);
        for (var i = 0; i < values.Length; i++)
        {
            cells[SurvivedData.FeatureColumns[i]] = values[i];
        }

        return new Row(cells);
    }

    /// <summary>行を、特徴量の列を持つ表にする。</summary>
    public static Table ToTable(params Row[] rows) => SurvivedData.ToTable(rows);
}
```

**TODO リスト**:

- [x] CSV を読み込み、特徴量の列と正解ラベルに分ける

## 8.5 年齢をグループごとの中央値で補完する

### 学習する値と、変換とを分ける

前処理には「訓練データから値を学ぶ」段階と「学んだ値でデータを変換する」段階があります。この 2 つを混ぜると、テストデータの中央値でテストデータを補完してしまい、評価が甘くなります（データ漏洩）。C# ではこれを 2 つのインターフェースで表します。

```csharp
// src/MachineLearning/Chapter08/Transformers.cs
/// <summary>訓練データから変換に必要な値を求める前処理。</summary>
public interface ITransformer
{
    IFittedTransformer Fit(Table x);
}

/// <summary>Fit で求めた値を使ってデータを変換する前処理。</summary>
public interface IFittedTransformer
{
    Table Transform(Table x);
}
```

F# 版は「学習した値はデータ、変換は関数」という分け方をしました。C# 版の `IFittedTransformer` も、実装はすべて **値だけを持つ `record`** です。関数（デリゲート）にしなかったのは、あとでそのままファイルに保存するためです（8.10 節）。

### Red: 最初のテスト

まず「同じグループの中で中央値を取る」ことだけをテストにします。

```csharp
// tests/MachineLearning.Tests/Chapter08/TransformersTests.cs
[Fact(DisplayName = "年齢の欠損値を同じグループの中央値で補完する")]
public void FillsAgeWithGroupMedian()
{
    var x = Passengers.ToTable(
        Passengers.Of("1", "female", "30", "0", "0", "50", "S"),
        Passengers.Of("1", "female", "40", "0", "0", "50", "S"),
        Passengers.Of("1", "female", string.Empty, "0", "0", "50", "S"));

    var filled = new GroupMedianImputer("Age", ["Pclass", "Sex"]).Fit(x).Transform(x);

    Assert.Equal(35.0, filled.Rows[2].Number("Age"));
}
```

第 1 章と同じく、C# では最初の Red が **コンパイルエラー** として届きます。

```text
error CS0246: 型または名前空間の名前 'GroupMedianImputer' が見つかりませんでした
（using ディレクティブまたはアセンブリ参照が指定されていることを確認してください）
```

### Green: 仮実装

いちばん簡単に緑にするなら、中央値を求めずに定数を返します。

```csharp
public IFittedTransformer Fit(Table x) => new Fitted(this.Column, this.By, [], 35.0);
```

### 三角測量: グループごとに異なる中央値

仮実装を追い出すために、グループが 2 つあるテストを足します。

```csharp
[Fact(DisplayName = "グループごとに異なる中央値で補完する")]
public void FillsAgeByGroup()
{
    var x = Passengers.ToTable(
        Passengers.Of("1", "female", "30", "0", "0", "50", "S"),
        Passengers.Of("3", "male", "20", "0", "0", "8", "S"),
        Passengers.Of("1", "female", string.Empty, "0", "0", "50", "S"),
        Passengers.Of("3", "male", string.Empty, "0", "0", "8", "S"));

    var filled = new GroupMedianImputer("Age", ["Pclass", "Sex"]).Fit(x).Transform(x);

    Assert.Equal(30.0, filled.Rows[2].Number("Age"));
    Assert.Equal(20.0, filled.Rows[3].Number("Age"));
}
```

```text
Assert.Equal() Failure: Values differ
Expected: 30
Actual:   35
```

### Green: グループごとの中央値

グループは「`By` の列の値を並べたもの」です。C# の `List<string>` は参照で比べられるので、そのまま辞書のキーにはできません。中身で比べる `IEqualityComparer` を用意します。

```csharp
// src/MachineLearning/Chapter08/GroupMedianImputer.cs
public sealed record GroupMedianImputer(string Column, IReadOnlyList<string> By) : ITransformer
{
    /// <summary>中央値。件数が偶数なら中央の 2 つの平均。</summary>
    public static double Median(IReadOnlyList<double> values)
    {
        ArgumentNullException.ThrowIfNull(values);
        var sorted = values.Order().ToList();
        var middle = sorted.Count / 2;
        return sorted.Count % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
    }

    public IFittedTransformer Fit(Table x)
    {
        ArgumentNullException.ThrowIfNull(x);
        var known = x.Rows.Where(row => !row.IsMissing(this.Column)).ToList();
        var medians = known
            .GroupBy(row => GroupOf(row, this.By), GroupComparer.Instance)
            .Select(group => new GroupMedian(
                group.Key,
                Median([.. group.Select(row => row.Number(this.Column)!.Value)])))
            .ToList();
        var overall = Median([.. known.Select(row => row.Number(this.Column)!.Value)]);
        return new Fitted(this.Column, this.By, medians, overall);
    }
```

`GroupBy` に比較子を渡すと、グループのキーを中身で比べてくれます。F# 版が「組（タプル）はそのまま `Map` のキーにできる」ことを学習用テストで確かめたのに対し、C# では **比べ方を自分で与える** のが既定です。`record` のキーを使う手もありますが、グループ分けの列は `By` で指定されるので、ここでは値の並びのまま持ちます。

### 訓練データで求めた値を別のデータに使う

`Fit` が返すのは、求めた値だけを持つ `record` です。別のデータを変換しても、覚えた中央値しか使いません。

```csharp
    /// <summary>Fit で求めたグループごとの中央値と全体の中央値を持ち、欠損値を補完する。</summary>
    public sealed record Fitted(
        string Column,
        IReadOnlyList<string> By,
        IReadOnlyList<GroupMedian> Medians,
        double OverallMedian) : IFittedTransformer
    {
        public Table Transform(Table x)
        {
            ArgumentNullException.ThrowIfNull(x);
            return new Table(x.Columns, [.. x.Rows.Select(row => this.Fill(x.Columns, row))]);
        }

        private Row Fill(IReadOnlyList<string> columns, Row row)
        {
            if (!row.IsMissing(this.Column))
            {
                return row;
            }

            var group = GroupOf(row, this.By);
            var median = this.Medians
                .FirstOrDefault(entry => GroupComparer.Instance.Equals(entry.Group, group))?.Median
                ?? this.OverallMedian;
            return Rows.With(columns, row, this.Column, median.ToString(CultureInfo.InvariantCulture));
        }
    }
```

訓練データに無いグループは、全体の中央値で補完します。`FirstOrDefault` は見つからなければ `null` を返すので、null 条件演算子（`?.`）と null 合体演算子（`??`）でそのまま既定値に落とせます。F# 版の `Option.orElse >> Option.defaultValue` に当たる書き方です。

`Row` は変更できない `record` なので、値を差し替えた新しい行を作ります。

```csharp
// src/MachineLearning/Chapter08/Rows.cs
internal static class Rows
{
    /// <summary>列の値を置き換えた（列が無ければ足した）新しい行を返す。元の行は変更しない。</summary>
    public static Row With(IReadOnlyList<string> columns, Row row, string column, string value)
    {
        var cells = columns.ToDictionary(name => name, row.Text, StringComparer.Ordinal);
        cells[column] = value;
        return new Row(cells);
    }
}
```

**TODO リスト**:

- [x] 同じグループの中央値で補完する
- [x] グループごとに異なる中央値で補完する
- [x] 訓練データで求めた中央値を、別のデータの補完に使う
- [x] 訓練データに無いグループは、全体の中央値で補完する

## 8.6 乗船した港を最頻値で補完する

乗船した港は文字列なので、中央値ではなく最頻値（最も多い値）で補完します。

```csharp
// src/MachineLearning/Chapter08/MostFrequentImputer.cs
public sealed record MostFrequentImputer(string Column) : ITransformer
{
    public IFittedTransformer Fit(Table x)
    {
        ArgumentNullException.ThrowIfNull(x);
        var counts = new Dictionary<string, int>(StringComparer.Ordinal);
        foreach (var row in x.Rows.Where(row => !row.IsMissing(this.Column)))
        {
            counts[row.Text(this.Column)] = counts.GetValueOrDefault(row.Text(this.Column)) + 1;
        }

        var mostFrequent = counts.Aggregate((best, next) => next.Value > best.Value ? next : best).Key;
        return new Fitted(this.Column, mostFrequent);
    }
```

同数のときに先に現れた値を選ぶのは、第 3 章の `Majority` と同じ考え方です。`Dictionary` は挿入した順に列挙されることが保証されていませんが、`Aggregate` で「厳密に大きいときだけ置き換える」ので、同数の値が複数あるときの選び方は列挙の順に依存します。第 3 章と同じく、この章でも同数の扱いはテストで固定していません（実データでは `S` が突出して多く、同数は起きません）。

**TODO リスト**:

- [x] 乗船した港の欠損値を、最も多い値で補完する

## 8.7 カテゴリ値をダミー変数にする

決定木は数値しか扱えないので、`male`・`female` のような文字列を 0 と 1 の列にします。カテゴリが n 種類あるとき、作る列は n - 1 本です（最初のカテゴリは「ほかの列がすべて 0」で表せます）。

```csharp
[Fact(DisplayName = "カテゴリ値を最初のカテゴリを除いた 0 と 1 の列にする")]
public void EncodesDummies()
{
    var x = Passengers.ToTable(
        Passengers.Of("1", "female", "30", "0", "0", "50", "S"),
        Passengers.Of("3", "male", "20", "0", "0", "8", "C"));

    var encoded = new DummyEncoder(["Sex", "Embarked"]).Fit(x).Transform(x);

    Assert.Equal(
        ["Pclass", "Age", "SibSp", "Parch", "Fare", "Sex_male", "Embarked_S"],
        encoded.Columns);
    Assert.Equal(0.0, encoded.Rows[0].Number("Sex_male"));
    Assert.Equal(1.0, encoded.Rows[0].Number("Embarked_S"));
}
```

変換では、元のカテゴリの列を表の列から外し、`列名_カテゴリ` という列を末尾に足します。

```csharp
// src/MachineLearning/Chapter08/DummyEncoder.cs
public sealed record Fitted(IReadOnlyList<Dummies> Dummies) : IFittedTransformer
{
    public Table Transform(Table x)
    {
        ArgumentNullException.ThrowIfNull(x);
        var columns = x.Columns.ToList();
        foreach (var dummies in this.Dummies)
        {
            columns.Remove(dummies.Column);
            columns.AddRange(dummies.Categories.Select(category => $"{dummies.Column}_{category}"));
        }

        return new Table(columns, [.. x.Rows.Select(row => this.Encode(x.Columns, row))]);
    }
```

カテゴリは `Fit` のときに並べ替えて覚えるので、訓練データに `Q` が 1 件でもあれば、`Q` を含まない別のデータにも `Embarked_Q` の列（すべて 0）が作られます。列の並びと本数がデータによって変わってしまうと、学習した木の分割が別のデータに使えません。

```csharp
[Fact(DisplayName = "別のデータにも訓練データと同じダミー変数の列を作る")]
public void EncodesSameColumnsForOtherData()
{
    // 訓練データには S・C・Q の 3 つの港がある
    var encoded = new DummyEncoder(["Sex", "Embarked"]).Fit(train).Transform(test);

    Assert.Equal(
        ["Pclass", "Age", "SibSp", "Parch", "Fare", "Sex_male", "Embarked_Q", "Embarked_S"],
        encoded.Columns);
    Assert.Equal(0.0, encoded.Rows[0].Number("Embarked_Q"));
}
```

**TODO リスト**:

- [x] カテゴリ値を 0 と 1 の列（ダミー変数）にする
- [x] 訓練データと別のデータで、同じ列を作る

## 8.8 クラスの重みを付けた決定木を作る

### なぜ自作するのか

生存 342 人・死亡 549 人のように偏ったデータでは、「全員が死亡」と予測するだけで正解率が 0.6 を超えます。生存者を見つけたいなら、少ないほうのクラスを重く数える必要があります。これがクラスの重みです。

ML.NET の FastTree には行の重み（`ExampleWeightColumnName`）がありますが、第 3 章と同じく「1 本の決定木」そのものは無いので、重みが木の作り方にどう効くかは自分で書いて確かめます（この節の最後で ML.NET と突き合わせます）。

### 重み付きのジニ不純度

第 3 章のジニ不純度は「ラベルごとの件数の割合」で求めました。重み付きでは、件数の代わりに **重みの合計** で割合を求めます。

```csharp
// src/MachineLearning/Chapter08/WeightedTrees.cs
/// <summary>重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。</summary>
public static double WeightedGini(IReadOnlyList<int> labels, IReadOnlyList<double> weights)
{
    ArgumentNullException.ThrowIfNull(weights);
    var total = weights.Sum();
    return 1.0 - WeightSums(labels, weights).Values.Sum(weight => Math.Pow(weight / total, 2));
}
```

重みをすべて 1 にすれば、第 3 章のジニ不純度と一致します。これはテストで確かめます。

```csharp
[Fact(DisplayName = "重みがすべて 1 なら重み付きのジニ不純度は第 3 章のジニ不純度と同じ")]
public void WeightedGiniWithEqualWeights()
{
    Assert.Equal(DecisionTrees.Gini(["0", "0", "1", "1"]), WeightedTrees.WeightedGini([0, 0, 1, 1], [1, 1, 1, 1]), 12);
    Assert.Equal(0.0, WeightedTrees.WeightedGini([1, 1], [1, 1]));
}
```

第 3 章の `Gini` は文字列のラベルを取るので、`"0"`・`"1"` を渡しています。この章のラベルは `int` です。C# の第 3 章の木はラベルを `string` で持つ非ジェネリックな型なので、F# 版のように `Tree<'L>` をそのまま使い回すことはできません。この章では `int` のラベルを持つ木を、第 3 章と同じ形（抽象レコードと sealed な派生）で作ります。

```csharp
// src/MachineLearning/Chapter08/TreeNode.cs
[JsonPolymorphic(TypeDiscriminatorPropertyName = "kind")]
[JsonDerivedType(typeof(LeafNode), "leaf")]
[JsonDerivedType(typeof(SplitNode), "split")]
public abstract record TreeNode
{
    internal TreeNode()
    {
    }
}

/// <summary>予測するラベル（1 が生存、0 が死亡）を持つ葉。</summary>
public sealed record LeafNode(int Label) : TreeNode;

/// <summary>特徴量の値が境界以下なら左、境界より大きければ右へ進む節。</summary>
public sealed record SplitNode(string Feature, double Threshold, TreeNode Left, TreeNode Right) : TreeNode;
```

属性（`JsonPolymorphic`・`JsonDerivedType`）は 8.10 節の保存のためのものです。木の型そのものには何も足していません。

### balanced の重み

`balanced` は、1 件の重みを「件数 / (クラスの数 × そのクラスの件数)」にします。こうすると、クラスごとの重みの合計がそろいます。

```csharp
/// <summary>クラスの件数に反比例する重み（件数 / (クラスの数 × そのクラスの件数)）を 1 件ごとに求める。</summary>
public static IReadOnlyList<double> BalancedWeights(IReadOnlyList<int> t)
{
    ArgumentNullException.ThrowIfNull(t);
    var counts = new Dictionary<int, int>();
    foreach (var label in t)
    {
        counts[label] = counts.GetValueOrDefault(label) + 1;
    }

    return [.. t.Select(label => (double)t.Count / (counts.Count * counts[label]))];
}
```

```csharp
[Fact(DisplayName = "balanced の重みはクラスごとの重みの合計をそろえる")]
public void BalancedWeights()
{
    var weights = WeightedTrees.BalancedWeights([0, 0, 0, 1]);

    Assert.Equal([4.0 / 6, 4.0 / 6, 4.0 / 6, 2.0], weights);
    Assert.Equal(2.0, weights.Where((_, i) => i < 3).Sum(), 12);
}
```

### 重み付けなしなら第 3 章と同じ木になる

学習は第 3 章の `Build` をなぞり、重みを一緒に分けていきます。

```csharp
/// <summary>深さの上限まで分割を繰り返して木を作る。maxDepth が負なら上限なし。</summary>
public static TreeNode Build(
    IReadOnlyList<Features> x, IReadOnlyList<int> t, IReadOnlyList<double> w, int maxDepth)
{
    ArgumentNullException.ThrowIfNull(x);
    var split = maxDepth == 0 ? null : BestSplit(x, t, w);
    if (split is null)
    {
        return new LeafNode(WeightedMajority(t, w));
    }

    var left = Enumerable.Range(0, x.Count).Where(i => GoesLeft(split, x[i])).ToList();
    var right = Enumerable.Range(0, x.Count).Where(i => !GoesLeft(split, x[i])).ToList();
    var childDepth = maxDepth < 0 ? maxDepth : maxDepth - 1;
    return new SplitNode(
        split.Feature,
        split.Threshold,
        Build(Pick(x, left), Pick(t, left), Pick(w, left), childDepth),
        Build(Pick(x, right), Pick(t, right), Pick(w, right), childDepth));
}
```

分割の候補は第 3 章と同じく「隣り合う値の中点」で、不純度だけが重み付きになります。分割の表現には第 3 章の `Split`（`record Split(string Feature, double Threshold, double Impurity)`）をそのまま使います。木のラベルの型は変わっても、分割の表し方は変わらないからです。

重みがすべて 1 なら、第 3 章と同じ木ができます。小さなデータで形を確かめ、実データでも深さ 1〜5 で第 3 章の木と一致することを確かめました（8.12 節）。

```csharp
[Fact(DisplayName = "重み付けなしの木は第 3 章の決定木と同じ形になる")]
public void SameTreeAsChapter03WithoutWeights()
{
    var x = new List<Features> { new(Columns, [1.0]), new(Columns, [2.0]), new(Columns, [3.0]), new(Columns, [4.0]) };

    var tree = new DecisionTreeClassifier(1, ClassWeight.None).Fit(x, [0, 0, 1, 1]).Root;

    Assert.Equal(new SplitNode("x", 2.5, new LeafNode(0), new LeafNode(1)), tree);
}
```

`record` は中身で比べられるので、木をまるごと 1 つの `Assert.Equal` で比較できます。F# の判別共用体と同じ利点です。

### 重みで葉のラベルが変わる

重みを付けると、同じ分割でも葉のラベルが変わります。

```csharp
[Fact(DisplayName = "重みを付けると少ないほうのクラスを予測する葉になる")]
public void WeightsChangePrediction()
{
    var x = new List<Features>
    {
        new(Columns, [1.0]), new(Columns, [2.0]), new(Columns, [3.0]), new(Columns, [4.0]), new(Columns, [5.0]),
    };
    var t = new List<int> { 0, 1, 0, 0, 0 };

    // 同じ分割（x <= 2.5）を選ぶが、左の葉のラベルが重みで変わる
    Assert.Equal([0], new DecisionTreeClassifier(1, ClassWeight.None).Fit(x, t).Predict([x[0]]));
    Assert.Equal([1], new DecisionTreeClassifier(1, ClassWeight.Balanced).Fit(x, t).Predict([x[0]]));
}
```

重みの付け方は `enum` で表し、分類器がラベルから重みを求めます。

```csharp
// src/MachineLearning/Chapter08/DecisionTreeClassifier.cs
public sealed record DecisionTreeClassifier(int MaxDepth, ClassWeight ClassWeight)
{
    /// <summary>深さを制限しないことを表す値</summary>
    public const int Unlimited = -1;

    /// <summary>訓練データから木を作る。</summary>
    public FittedDecisionTree Fit(IReadOnlyList<Features> x, IReadOnlyList<int> t) =>
        new(WeightedTrees.Build(x, t, Weights(this.ClassWeight, t), this.MaxDepth));

    /// <summary>正解ラベルから、1 件ごとの重みを求める。</summary>
    public static IReadOnlyList<double> Weights(ClassWeight classWeight, IReadOnlyList<int> t)
    {
        ArgumentNullException.ThrowIfNull(t);
        return classWeight switch
        {
            ClassWeight.None => [.. t.Select(_ => 1.0)],
            ClassWeight.Balanced => WeightedTrees.BalancedWeights(t),
            _ => throw new ArgumentOutOfRangeException(nameof(classWeight)),
        };
    }
}
```

`enum` に対する `switch` 式は、すべてのケースを書いても網羅性を保証しません（`enum` は宣言に無い値も持てるため）。既定のケースで例外を投げるのが C# の作法です。F# の判別共用体なら、ケースを足したときにコンパイラが漏れを知らせてくれます。ここは C# の型の弱いところです。

### ML.NET の行の重みでクラスの重みを表せるか

ML.NET の FastTree には `exampleWeightColumnName` があり、1 行ごとの重みを渡せます。第 3 章と同じく「木は 1 本、葉の数の上限は 2 の深さ乗」にして、自作の木と突き合わせます。

```csharp
// src/MachineLearning/Chapter08/MlNetAdapter.cs
/// <summary>ML.NET に渡す 1 行。生存したか（Label）と、行の重み（Weight）を持つ。</summary>
public sealed class WeightedRow
{
    public float[] Features { get; set; } = [];

    public bool Label { get; set; }

    public float Weight { get; set; }
}

/// <summary>木を 1 本だけ作る FastTree を、行の重み（Weight 列）を付けて学習する。</summary>
public static MlTransformer TrainFastTree(
    int numberOfLeaves,
    IReadOnlyList<Features> x,
    IReadOnlyList<int> t,
    IReadOnlyList<double> weights)
{
    ArgumentNullException.ThrowIfNull(x);
    var context = new MLContext(seed: 0);
    var fastTree = context.BinaryClassification.Trainers.FastTree(
        exampleWeightColumnName: "Weight",
        numberOfLeaves: numberOfLeaves,
        numberOfTrees: 1,
        minimumExampleCountPerLeaf: 1);
    return fastTree.Fit(ToDataView(context, x, t, weights));
}
```

2 値分類の `Label` は `bool` で、予測は `PredictedLabel` に返ります。第 3 章の多クラス分類（`MapValueToKey` と `OneVersusAll`）と違って、変換をつなぐ必要がありません。ML.NET は C# 向けの API なので、書き換えられるプロパティを持つクラスをそのまま渡せます。F# 版が `[<CLIMutable>]` を付けてレコードを可変にしたのに対し、C# では普通のクラスです。

この章では、ML.NET の `ITransformer` と、この章の前処理の `ITransformer` で名前が重なります。別名を付けて区別しました。

```csharp
// この章の ITransformer（前処理）と名前が重なるので、ML.NET の ITransformer には別名を付ける
using MlTransformer = Microsoft.ML.ITransformer;
```

実データでの一致は 8.12 節で確かめます。

**TODO リスト**:

- [x] クラスの重みを付けた決定木を作る

## 8.9 前処理とモデルをパイプラインにつなぐ

### Red: パイプラインで学習して予測する

補完もダミー変数化もしていない生のデータを渡して、学習して予測できることをテストにします。

```csharp
// tests/MachineLearning.Tests/Chapter08/PipelineTests.cs
[Fact(DisplayName = "パイプラインは補完もダミー変数化もしていないデータから学習して予測する")]
public void FitsAndPredicts()
{
    var pipeline = Pipeline.Build(2, ClassWeight.None).Fit(Passengers.ToTable(Train), Labels);

    Assert.Equal(Labels, pipeline.Predict(Passengers.ToTable(Train)));
}
```

### Green: 前処理を順に Fit・Transform する

前処理は、前の前処理で変換したデータで `Fit` します。ダミー変数化は、補完が済んだデータのカテゴリを見なければなりません。

```csharp
// src/MachineLearning/Chapter08/Pipeline.cs
public sealed record Pipeline(IReadOnlyList<ITransformer> Transformers, DecisionTreeClassifier Model)
{
    /// <summary>Survived.csv 用のパイプライン。年齢・港の補完とダミー変数化の後に、決定木で分類する。</summary>
    public static Pipeline Build(int maxDepth, ClassWeight classWeight) =>
        new(
            [
                new GroupMedianImputer("Age", ["Pclass", "Sex"]),
                new MostFrequentImputer("Embarked"),
                new DummyEncoder(["Sex", "Embarked"]),
            ],
            new DecisionTreeClassifier(maxDepth, classWeight));

    /// <summary>訓練データで前処理とモデルを学習する。前処理は、前の前処理で変換したデータで Fit する。</summary>
    public FittedPipeline Fit(Table x, IReadOnlyList<int> t)
    {
        ArgumentNullException.ThrowIfNull(x);
        var fitted = new List<IFittedTransformer>();
        var prepared = x;
        foreach (var transformer in this.Transformers)
        {
            var fittedTransformer = transformer.Fit(prepared);
            fitted.Add(fittedTransformer);
            prepared = fittedTransformer.Transform(prepared);
        }

        return new FittedPipeline(fitted, this.Model.Fit(FittedPipeline.FeaturesOf(prepared), t));
    }
}
```

予測のときは、学習済みの前処理を順につなぐだけです。合成は拡張メソッドで書きました。

```csharp
// src/MachineLearning/Chapter08/Transformers.cs
public static class FittedTransformers
{
    /// <summary>この変換の後に next の変換を行う、合成した変換を返す。</summary>
    public static IFittedTransformer AndThen(this IFittedTransformer first, IFittedTransformer next) =>
        new Composed(first, next);

    /// <summary>学習済みの前処理を順に合成する。1 つも無ければ何も変えない変換になる。</summary>
    public static IFittedTransformer Compose(IEnumerable<IFittedTransformer> transformers)
    {
        ArgumentNullException.ThrowIfNull(transformers);
        return transformers.Aggregate((IFittedTransformer)new Identity(), AndThen);
    }
```

F# 版は関数の合成演算子（`>>`）でつなぎました。C# には演算子がないので、合成した結果も `IFittedTransformer` を実装する `record`（`Composed`）にします。`Identity` を初期値にした `Aggregate` は、F# の `List.fold (>>) id` と同じ形です。

### 補完を飛ばすとどうなるか

F# 版は、補完の前と後を別の型にして「補完を飛ばしたパイプラインはコンパイルエラーになる」ようにしました。C# 版の表（`Table`）はどの段階でも同じ型なので、この誤りはコンパイル時には止められません。止まるのは、特徴量に変換するときです。

```csharp
/// <summary>前処理の済んだ表を特徴量にする。欠損値が残っていれば失敗する。</summary>
internal static IReadOnlyList<Features> FeaturesOf(Table x) =>
    [.. x.Rows.Select(row => new Features(
        x.Columns,
        [.. x.Columns.Select(column => row.Number(column)
            ?? throw new ArgumentException($"欠損値が残っています: {column}", nameof(x)))]))];
```

第 2 章の `Features` は欠損値を持てない型なので、「補完が済んでいる」ことは `Features` になれたかどうかで分かります。この振る舞いもテストで固定しました。

```csharp
[Fact(DisplayName = "補完を飛ばしたパイプラインは欠損値が残ったまま特徴量にできない")]
public void FailsWithoutImputation()
{
    var pipeline = new Pipeline([new DummyEncoder(["Sex", "Embarked"])], new DecisionTreeClassifier(2, ClassWeight.None));

    var error = Assert.Throws<ArgumentException>(() => pipeline.Fit(Passengers.ToTable(Train), Labels));

    Assert.Contains("欠損値が残っています: Age", error.Message, StringComparison.Ordinal);
}
```

型で止める F# 版に比べると、誤りに気づくのが実行時まで遅れます。その代わり、前処理を足したり順番を入れ替えたりするのに型を書き換える必要がなく、`Pipeline.Build` のリストを直すだけで済みます。

### 第 15 章から使う入口

学習済みのパイプラインが公開するのは、次の 4 つです。第 15 章の API は、保存したモデルを `ModelFiles.Load` で読み込み、乗客 1 人分の行を `PredictOne` に渡します。

```csharp
public sealed record FittedPipeline(IReadOnlyList<IFittedTransformer> Transformers, FittedDecisionTree Model)
{
    /// <summary>学習済みの前処理を順に合成して、データを変換する。</summary>
    public Table Transform(Table x);

    /// <summary>前処理をして、モデルに渡す特徴量にする。</summary>
    public IReadOnlyList<Features> ToFeatures(Table x);

    /// <summary>前処理をしてから、1 行ごとのラベル（1 が生存、0 が死亡）を予測する。</summary>
    public IReadOnlyList<int> Predict(Table x);

    /// <summary>乗客 1 人分の行から、生存（1）か死亡（0）かを予測する。</summary>
    public int PredictOne(Row row);
}
```

`PredictOne` に渡す行は、`Row` に特徴量の 7 列を入れたものです。年齢や港が空欄でも、パイプラインが覚えた値で補完してから予測します。

```csharp
[Fact(DisplayName = "年齢が欠けた乗客 1 人でも保存した値で補完して予測できる")]
public void PredictsOnePassengerWithMissingAge()
{
    var pipeline = Pipeline.Build(2, ClassWeight.None).Fit(Passengers.ToTable(Train), Labels);

    Assert.Equal(1, pipeline.PredictOne(Passengers.Of("1", "female", string.Empty, "0", "0", "50", "C")));
}
```

**TODO リスト**:

- [x] 前処理とモデルを 1 つのパイプラインにつなぐ

## 8.10 モデルを保存して読み込む

### System.Text.Json の多態

保存するのは、学習済みの前処理（`IFittedTransformer` のリスト）とモデル（木）です。中身は実装の違う `record` が並んだリストなので、読み込むときに「どの型だったか」が分からなければ復元できません。System.Text.Json は、インターフェースに属性を書けばこれを解決してくれます。

```csharp
[JsonPolymorphic(TypeDiscriminatorPropertyName = "type")]
[JsonDerivedType(typeof(GroupMedianImputer.Fitted), "groupMedian")]
[JsonDerivedType(typeof(MostFrequentImputer.Fitted), "mostFrequent")]
[JsonDerivedType(typeof(DummyEncoder.Fitted), "dummy")]
public interface IFittedTransformer
{
    Table Transform(Table x);
}
```

木も同じく `kind` で葉と節を書き分けます（8.8 節）。F# 版は、判別共用体と組をキーにした `Map` を JSON にできないため、保存する形（DTO）を別に用意して詰め替えました。C# 版では、保存する形と使う形が同じままです。

```csharp
// src/MachineLearning/Chapter08/ModelFiles.cs
public static class ModelFiles
{
    /// <summary>
    /// 読み込むときの設定。コンストラクターの引数が JSON に無ければ、null で埋めずに JsonException にする。
    /// </summary>
    private static readonly JsonSerializerOptions Options = new() { RespectRequiredConstructorParameters = true };

    /// <summary>学習済みのパイプラインを JSON で保存する。保存先のディレクトリが無ければ作る。</summary>
    public static void Save(FittedPipeline pipeline, string modelFile)
    {
        var directory = Path.GetDirectoryName(Path.GetFullPath(modelFile));
        if (!string.IsNullOrEmpty(directory))
        {
            Directory.CreateDirectory(directory);
        }

        File.WriteAllText(modelFile, JsonSerializer.Serialize(pipeline, Options));
    }

    /// <summary>保存したパイプラインを読み込む。JSON の形が違えば JsonException で失敗する。</summary>
    public static FittedPipeline Load(string modelFile) =>
        JsonSerializer.Deserialize<FittedPipeline>(File.ReadAllText(modelFile), Options)
        ?? throw new JsonException("パイプラインを読み込めません");
}
```

`RespectRequiredConstructorParameters` を付けないと、JSON に項目が無くても `null` や 0 で埋めた値が返ります。「形の違うファイルを読み込んだら失敗する」ことをテストにできるよう、この設定を入れました。

```csharp
[Fact(DisplayName = "形の違うファイルは読み込めない")]
public void RejectsWrongShape()
{
    var modelFile = Path.Combine(this.directory, "broken.json");
    File.WriteAllText(modelFile, "{\"Transformers\":[]}");

    Assert.Throws<JsonException>(() => ModelFiles.Load(modelFile));
}
```

保存したファイルには、種類を表す `type`・`kind` が並びます。

```csharp
[Fact(DisplayName = "木の節と葉は kind で書き分けられる")]
public void WritesTreeKind()
{
    ModelFiles.Save(pipeline, modelFile);

    var json = File.ReadAllText(modelFile);
    Assert.Contains("\"kind\":\"split\"", json, StringComparison.Ordinal);
    Assert.Contains("\"kind\":\"leaf\"", json, StringComparison.Ordinal);
    Assert.Contains("\"type\":\"groupMedian\"", json, StringComparison.Ordinal);
}
```

Java 版はオブジェクトのシリアライズ（`ObjectOutputStream`）で保存し、読み込むクラスを絞る対策（`ObjectInputFilter`）が必要でした。JSON にすると、読み込みで任意の型を作られる心配がありません。復元できるのは、属性で許した 3 つの実装だけです。

### ML.NET のモデルは zip で保存する

ML.NET のモデルは、ML.NET 自身の形式（zip）で保存します。

```csharp
/// <summary>学習した FastTree を zip で保存する。保存先のディレクトリが無ければ作る。</summary>
public static void SaveFastTree(MlTransformer model, string modelFile)
{
    var directory = Path.GetDirectoryName(Path.GetFullPath(modelFile));
    if (!string.IsNullOrEmpty(directory))
    {
        Directory.CreateDirectory(directory);
    }

    new MLContext(seed: 0).Model.Save(model, null, modelFile);
}

/// <summary>zip に保存した FastTree を読み込む。</summary>
public static MlTransformer LoadFastTree(string modelFile) =>
    new MLContext(seed: 0).Model.Load(modelFile, out _);
```

保存されるのは学習器だけで、この章の前処理は含まれません。読み込んだ後も、前処理は自作のパイプラインで行います。保存先の `apps/csharp/model/` は `.gitignore` の対象です。

**TODO リスト**:

- [x] モデルを保存して読み込む

## 8.11 評価する

正解率だけでは、クラスの重みの効果が見えません。「テストデータの生存者のうち、何人を生存と予測できたか」も一緒に数えます。

```csharp
// src/MachineLearning/Chapter08/Evaluation.cs
public sealed record Evaluation(double TrainAccuracy, double TestAccuracy, int FoundSurvivors, int Survivors)
{
    private const int Survived = 1;

    /// <summary>学習済みのパイプラインを、訓練データとテストデータで評価する。</summary>
    public static Evaluation Of(FittedPipeline pipeline, TrainTestSplit<Row, int> split)
    {
        ArgumentNullException.ThrowIfNull(pipeline);
        ArgumentNullException.ThrowIfNull(split);
        var predictions = pipeline.Predict(SurvivedData.ToTable(split.XTest));
        return new Evaluation(
            Accuracy(pipeline.Predict(SurvivedData.ToTable(split.XTrain)), split.TTrain),
            Accuracy(predictions, split.TTest),
            predictions.Zip(split.TTest).Count(pair => pair.First == Survived && pair.Second == Survived),
            split.TTest.Count(label => label == Survived));
    }
```

`Evaluation` は `record` なので、2 つの評価をまるごと `Assert.Equal` で比べられます（8.12 節で使います）。

**TODO リスト**:

- [x] 正解率と、見つけた生存者の数で評価する

## 8.12 実データでクラスの重みの効果を確かめる

### 実行する

Python 版・F# 版と同じく、テストデータの割合 0.2・シード 0 で分け、深さ 5 の決定木でクラスの重みなし（`None`）と `Balanced` を比べます。あわせて、同じ前処理をしたデータで ML.NET の FastTree（葉の数の上限 2⁵ = 32、木は 1 本、行の重みあり）を学習し、テストデータの予測がいくつ一致するかを数えます。

`Balanced` のパイプラインを `apps/csharp/model/survived.json` に、同じ前処理で学習した FastTree を `apps/csharp/model/survived-mlnet.zip` に保存し、読み込んで架空の乗客 2 人を予測します。

```csharp
// src/MachineLearning/Chapter08/Program.cs
private const double TestSize = 0.2;
private const int Seed = 0;
private const int MaxDepth = 5;

private static readonly ClassWeight[] ClassWeights = [ClassWeight.None, ClassWeight.Balanced];

/// <summary>保存先を指定して実行する。テストから一時ディレクトリを渡すために使う。</summary>
public static void Run(TextWriter output, string modelDirectory)
{
    ArgumentNullException.ThrowIfNull(output);
    var rows = Table.Load(Path.Combine(DataDir.Current(), "Survived.csv")).Rows;
    var t = SurvivedData.Labels(rows);
    var split = Preprocessing.SplitTrainTest(rows, t, TestSize, Seed);
    var survivors = t.Count(label => label == 1);
    output.WriteLine($"データ件数: {rows.Count}（生存 {survivors}, 死亡 {rows.Count - survivors}）");
    output.WriteLine($"訓練データ: {split.XTrain.Count} 件, テストデータ: {split.XTest.Count} 件");

    var pipelines = ClassWeights.ToDictionary(
        classWeight => classWeight,
        classWeight => Pipeline.Build(MaxDepth, classWeight)
            .Fit(SurvivedData.ToTable(split.XTrain), split.TTrain));

    // 評価・ML.NET との突き合わせ・保存と読み込みは完成コードを参照
}
```

`Run` は保存先のディレクトリを引数に取る版を用意したので、テストからは一時ディレクトリを渡せます。`Program.cs` の対応表には、既定の保存先を使う版（`Run(TextWriter)`）を登録します。

```bash
dotnet run --project src/MachineLearning -- chapter08
```

```text
データ件数: 891（生存 342, 死亡 549）
訓練データ: 712 件, テストデータ: 179 件
classWeight=None: 訓練 0.858, テスト 0.821, 生存者 72 人中 54 人を発見, ML.NET と一致 175/179
classWeight=Balanced: 訓練 0.840, テスト 0.788, 生存者 72 人中 59 人を発見, ML.NET と一致 170/179
架空の乗客の予測（survived.json）: [1, 0]
架空の乗客の予測（survived-mlnet.zip）: [1, 0]
```

### F# 版と一致したか

C# 版は F# 版と同じ `System.Random` と同じ Fisher-Yates のシャッフルで分けるので、訓練データ 712 件・テストデータ 179 件の中身も一致します。そのうえで、**数値もすべて一致しました**。

| 項目 | C# 版 | F# 版 |
|------|------|------|
| データ件数・生存者 | 891（生存 342, 死亡 549） | 同じ |
| 訓練・テストの件数 | 712 / 179 | 同じ |
| 重みなし: 訓練・テストの正解率 | 0.858 / 0.821 | 同じ |
| 重みなし: 見つけた生存者 | 72 人中 54 人 | 同じ |
| balanced: 訓練・テストの正解率 | 0.840 / 0.788 | 同じ |
| balanced: 見つけた生存者 | 72 人中 59 人 | 同じ |
| ML.NET と一致（深さ 5） | 175/179・170/179 | 同じ |
| 架空の乗客の予測 | [1, 0] | 同じ |

一致したのは、分割が同じだからというだけではありません。前処理（グループごとの中央値・最頻値・ダミー変数）の結果と、分割の候補の選び方・同点のときの選び方までが同じだからです。ただし **特徴量の列の並びは違います**。F# 版は `Map` のキー順（`Age`・`Embarked_Q`・`Embarked_S`・`Fare`・`Parch`・`Pclass`・`Sex_male`・`SibSp`）、C# 版は表の列順（`Pclass`・`Age`・`SibSp`・`Parch`・`Fare`・`Sex_male`・`Embarked_Q`・`Embarked_S`）です。不純度が同点の分割があれば「先に見つけたほう」が変わり、木の形が変わりえますが、このデータでは結果に差は出ませんでした（深さ 1〜10 の評価が F# 版と一致し、深さ 1〜5 では第 3 章の決定木と同じ形になることをテストで確かめています）。

`balanced` にすると、テストデータの生存者 72 人のうち見つけられた人数が 54 人から 59 人に増えました。その代わり、テストデータの正解率は 0.821 から 0.788 に下がっています。死亡者を生存と予測する誤りが増えたからです。Python 版では 69 人のうち 45 人から 51 人に、Kotlin 版の分割では逆に 46 人から 45 人に減りました。どちらに転ぶかは、データの分け方しだいです。

読み込んだパイプラインは、年齢が欠けた架空の乗客 2 人（1 等客室の女性、3 等客室の男性）を、それぞれ生存（1）・死亡（0）と予測しました。欠損値の補完からダミー変数化まで、保存したパイプラインの中で行われています。zip から読み込んだ ML.NET のモデルも、同じ前処理を通した特徴量で同じ予測をしました。

### 効果は深さによって変わる

深さを 1 から 10 まで変えて、見つけた生存者の数（テストデータの生存者は 72 人）、テストデータの正解率、ML.NET の FastTree（葉の数の上限 2 の深さ乗）と予測が一致した件数（179 件中）を並べました。

| 深さ | 生存者（None） | 生存者（Balanced） | テスト（None） | テスト（Balanced） | ML.NET と一致（None） | ML.NET と一致（Balanced） |
|------|------|------|------|------|------|------|
| 1 | 54 | 54 | 0.799 | 0.799 | 179 | 179 |
| 2 | 54 | 59 | 0.799 | 0.726 | 179 | 179 |
| 3 | 57 | 58 | 0.804 | 0.749 | 177 | 174 |
| 4 | 57 | 58 | 0.816 | 0.788 | 179 | 164 |
| 5 | 54 | 59 | 0.821 | 0.788 | 175 | 170 |
| 6 | 54 | 59 | 0.810 | 0.793 | 163 | 165 |
| 7 | 54 | 59 | 0.821 | 0.799 | 165 | 161 |
| 8 | 56 | 57 | 0.827 | 0.810 | 164 | 161 |
| 9 | 56 | 58 | 0.827 | 0.788 | 164 | 163 |
| 10 | 50 | 56 | 0.832 | 0.810 | 163 | 165 |

- 自作の決定木の結果（生存者とテストデータの正解率）は、深さ 1〜10 のすべてで F# 版と一致しました。重みを付けて見つけた生存者が減った深さはありません。深さ 1 では評価がまったく変わらず、深さ 2 では生存者が 5 人増える代わりにテストデータの正解率が 0.799 から 0.726 に下がりました
- ML.NET との一致は、深さ 1・2 では 179 件すべてで、深い木ほど減りました。深さ 8・9・10 の `Balanced` だけ F# 版と 1 件ずつ違います（161 対 160、163 対 162、165 対 164）。自作の木は F# 版と同じなので、違うのは ML.NET 側の予測です。FastTree は深さではなく **葉の数** で木の大きさを制限し、分けると得をする葉から順に分けるので、特徴量の並び順が違えば同点のときの選び方が変わりえます。どの行で違ったのかまでは確かめていません

「`balanced` にすれば生存者の見落としが減る」は、この分割では深さ 1 以外のすべてで成り立ちましたが、Kotlin 版の分割では成り立たない深さがありました。1 回の分割の結果から、いつでも成り立つ法則を導くことはできません。テストデータの結果を見て深さや重みを選び直すと、そのテストデータに合わせすぎた評価にもなります。本章では深さを Python 版と同じ 5 のままにし、選び方の正しい手順（交差検証）は第 11 章で扱います。

### 実データのテスト

実測した値と、第 3 章の決定木との突き合わせをテストで固定します。

```csharp
// tests/MachineLearning.Tests/Chapter08/SurvivedDataTests.cs
[Fact(DisplayName = "深さ 5 では balanced にすると見つけられる生存者が増える")]
public void BalancedFindsMoreSurvivors()
{
    this.RequireData();
    var split = this.Split();

    Assert.Equal(54, Evaluate(split, 5, ClassWeight.None).FoundSurvivors);
    Assert.Equal(59, Evaluate(split, 5, ClassWeight.Balanced).FoundSurvivors);
}

[Fact(DisplayName = "深さ 1 では balanced にしても評価が変わらない")]
public void DepthOneIsUnaffected()
{
    this.RequireData();
    var split = this.Split();

    Assert.Equal(Evaluate(split, 1, ClassWeight.None), Evaluate(split, 1, ClassWeight.Balanced));
}

[Theory(DisplayName = "重み付けなしなら第 3 章の決定木と同じ形の木を作る")]
[InlineData(1)]
[InlineData(2)]
[InlineData(3)]
[InlineData(4)]
[InlineData(5)]
public void SameShapeAsChapter03(int maxDepth)
{
    // 第 3 章の木はラベルが string、この章の木は int なので、分割の並びだけを取り出して比べる
    Assert.Equal(Shape(chapter03.Tree!), Shape(pipeline.Model.Root));
}
```

`Evaluation` は `record` なので、深さ 1 の 2 つの評価をまるごと比べられます。最後のテストは、実データの 712 件で、重み付けなしの自作の木が第 3 章の `DecisionTree` と同じ形（分割に使う列と境界の並び）になることを深さ 1〜5 で確かめています。木の型が違う（ラベルが `string` と `int`）ので、F# 版のように木ごと比べることはできません。ラベルを除いた形に落として比べました。

ほかに、件数と欠損値の数を確かめるテストと、`Program.Run` の出力をまるごと比べるテストがあります（完成コードを参照）。実データのテストは、実装を書いてから実データで値を確かめ、固定したものです。Red を経ていません。

```bash
dotnet format MachineLearning.sln --no-restore
dotnet build MachineLearning.sln
dotnet test -- --filter-namespace "MachineLearning.Tests.Chapter08"
```

```text
テストの実行の概要: 成功!
  合計: 34
  失敗: 0
  成功: 34
  スキップ済み: 0
```

第 8 章のテストは 34 件です。データが無い環境では、実データのテスト 9 件がスキップされます。

```bash
ML_DATA_DIR=/nonexistent dotnet test -- --filter-namespace "MachineLearning.Tests.Chapter08"
```

```text
テストの実行の概要: 成功!
  合計: 34
  失敗: 0
  成功: 25
  スキップ済み: 9
```

**TODO リスト**:

- [x] 実データでクラスの重みの効果を確かめる

## 8.13 まとめ

この章では、欠損値・カテゴリ値・クラスの偏りという現実のデータの難しさを、小さな部品に分けて TDD で扱い、1 つのパイプラインにつなぎました。

1. **前処理は 2 つのインターフェースに分ける** — `ITransformer`（訓練データから学ぶ）と `IFittedTransformer`（学んだ値で変換する）に分けると、テストデータで学び直す誤り（データ漏洩）が構造として起きなくなる
2. **学習した値は `record` で持つ** — 変換をデリゲートにせず値を持つ `record` にしたので、そのまま JSON に保存できた。F# 版の「学習した値はデータ、変換は関数」を、C# ではインターフェースと `record` で表した
3. **合成は拡張メソッドで書く** — F# の `>>` に当たる合成を `AndThen` と `Compose`（`Identity` を初期値にした `Aggregate`）で書いた
4. **型で止められるところと、止められないところ** — 補完を飛ばした誤りは、F# 版では型が止め、C# 版では `Features` に変換するときの例外で止まった。`enum` に対する `switch` 式は網羅性を保証しないので、既定のケースで例外を投げた
5. **第 3 章をなぞって重みを通す** — 木は第 3 章と同じ「抽象レコードと sealed な派生」で作り、ジニ不純度と多数決だけを重み付きにした。`record` の値による比較で、木をまるごと比べるテストが書けた
6. **System.Text.Json の多態** — `JsonPolymorphic` と `JsonDerivedType` で、前処理のリストと木をそのまま保存・復元できた。F# 版が必要とした DTO への詰め替えが要らず、Java 版のシリアライズのようにクラスを絞る対策も要らない
7. **ML.NET は行の重みでクラスの重みを表せる** — `exampleWeightColumnName` に重みの列を渡し、深さ 5 で 179 件中 170〜175 件が自作の木と一致した。2 値分類ではラベルが `bool` なので、第 3 章の多クラス分類より変換が少ない
8. **数値は F# 版と一致した** — 分割・前処理・木の作り方が同じなら、特徴量の列の並びが違っても結果は同じだった。違いが出たのは ML.NET 側の予測の一部（深さ 8 以上）だけ

次の章では、この章のデータに新しい特徴量を作って（特徴量エンジニアリング）、予測がどう変わるかを見ます。保存したパイプラインは、第 15 章の機械学習 API から `ModelFiles.Load` で読み込んで使います。
