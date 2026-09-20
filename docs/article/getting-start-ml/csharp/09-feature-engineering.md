---
type: Article
title: "第 9 章: 特徴量エンジニアリング"
description: "ダミー変数・標準化・多項式特徴量・四分位範囲による外れ値の除去を C# で自作し、特徴量の組み合わせごとに線形回帰の決定係数を比べる。TSV と Shift_JIS のファイルは第 2 章の Table を変えずに読み込む口を足して読み、CodePagesEncodingProvider の登録だけで Shift_JIS が扱えることを確かめる。ML.NET の NormalizeMeanVariance の既定が平均を引かないことを学習用テストで固定する。"
tags: [article,getting-start-ml,csharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T01:18:58Z }
---

# 第 9 章: 特徴量エンジニアリング

## 9.1 はじめに

モデルの性能は、アルゴリズムだけでなく、どんな特徴量を渡すかで大きく変わります。手元の列から、モデルが使いやすい特徴量を作り出す作業を **特徴量エンジニアリング** と呼びます。

この章では、次の 4 つの道具を作り、住宅価格の予測（線形回帰）の決定係数がどう変わるかを確かめます。

- **ダミー変数**: 文字のカテゴリ（`high`・`low` など）を 0 と 1 の列にする
- **標準化**: 列ごとに平均 0・標準偏差 1 にそろえる
- **多項式特徴量**: 2 乗の項や、列どうしの積（交互作用）の項を加える
- **外れ値の除去**: 四分位範囲から外れた価格の行を、訓練データから除く

さらに、2 つの表を共通の列で **結合** して、特徴量を増やします。

[Python 版の第 9 章](../python/09-feature-engineering.md)・[Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md)・[F# 版の第 9 章](../fsharp/09-feature-engineering.md) と同じ題材で進めます。C# 版では、次の 3 点に注目してください。

- 第 2 章で作った `Table` を **変えずに**、文字コードと区切り文字を指定して読む口を足す
- Shift_JIS を読むには、.NET では **エンコーディングの登録** が要る。ただし追加のパッケージは要らない
- 標準化の結果を、**第 14 章（K-means）からも使える形** で公開する

## 9.2 題材とデータ

### Boston.csv

第 12 章でも使う、ボストンの地区ごとの住宅価格（`PRICE`）のデータです。`CRIME` だけが文字のカテゴリ（`high`・`low`・`very_low`）で、ほかの 12 列は数値です。いくつかの列に空欄（欠損値）があります。

### bike.tsv と weather.csv

`bike.tsv` は、自転車の貸し出しサービスの 1 日ごとの利用者数（`cnt`）と、天気の番号（`weather_id`）を記録した **タブ区切り** のファイルです。`weather.csv` は、天気の番号と名前（晴れ・曇り・雨）の対応表で、文字コードが **Shift_JIS** です。

## 9.3 TODO リストの作成

**TODO リスト**:

- [ ] カテゴリ値をダミー変数にする
- [ ] 訓練データの平均と標準偏差で標準化する
  - [ ] 標準偏差が 0 の列は 0 にする
  - [ ] ML.NET の正規化と突き合わせる
- [ ] 2 乗の項と交互作用の項を作る
- [ ] 四分位範囲で外れ値を見つけ、訓練データから除く
- [ ] TSV と Shift_JIS の CSV を読み、表を結合する
- [ ] 特徴量の組み合わせごとに決定係数を比べる

## 9.4 カテゴリ値をダミー変数にする

線形回帰は数値しか扱えないので、`CRIME` の `high`・`low`・`very_low` を 0 と 1 の列にします。3 つのカテゴリに 3 列を作ると、「3 列の合計は常に 1」という余分な関係ができてしまうので、先頭のカテゴリの列は作りません（`CRIME_low` も `CRIME_very_low` も 0 なら `high`）。

```csharp
// tests/MachineLearning.Tests/Chapter09/DummiesTests.cs
[Fact(DisplayName = "先頭を除いたカテゴリを辞書順に返す")]
public void DropsFirstCategory() =>
    Assert.Equal(["low", "very_low"], Dummies.Categories(["low", "high", "very_low", "low"]));

[Fact(DisplayName = "元の列を取り除き、カテゴリごとの 0 と 1 の列を末尾に加える")]
public void EncodesColumns()
{
    var table = Samples.Table(
        ["CRIME", "PRICE"],
        ["high", "27.5"],
        ["low", "13.2"]);

    var encoded = Dummies.Encode(table, "CRIME", ["low", "very_low"]);

    Assert.Equal(["PRICE", "CRIME_low", "CRIME_very_low"], encoded.Columns);
    Assert.Equal(0.0, encoded.Rows[0].Number("CRIME_low"));
    Assert.Equal(1.0, encoded.Rows[1].Number("CRIME_low"));
}
```

```csharp
// src/MachineLearning/Chapter09/Dummies.cs
public static IReadOnlyList<string> Categories(IEnumerable<string> values)
{
    ArgumentNullException.ThrowIfNull(values);
    return [.. values
        .Where(value => !string.IsNullOrWhiteSpace(value))
        .Distinct(StringComparer.Ordinal)
        .Order(StringComparer.Ordinal)
        .Skip(1)];
}

public static Table Encode(Table table, string column, IReadOnlyList<string> categories)
{
    ArgumentNullException.ThrowIfNull(table);
    ArgumentNullException.ThrowIfNull(categories);
    IReadOnlyList<string> columns =
    [
        .. table.Columns.Where(name => !string.Equals(name, column, StringComparison.Ordinal)),
        .. categories.Select(category => $"{column}_{category}"),
    ];
    var kept = table.Columns.Where(name => !string.Equals(name, column, StringComparison.Ordinal)).ToList();
    return new Table(columns, [.. table.Rows.Select(row => EncodeRow(row, kept, column, categories))]);
}
```

- F# 版は `Map<string, float>` を返す関数でしたが、C# 版は第 2 章の `Table` をそのまま受け取って `Table` を返します。表の形のまま進めるので、続く `SplitFeaturesAndTarget`・`SplitTrainTest`・`FillMissing` に第 2 章の処理をそのまま使えます
- `Distinct` も `Order` も、既定では現在のカルチャで文字列を比べます。C# 版ではアナライザー（CA1304・CA1311）が「カルチャを指定せよ」と指摘するので、`StringComparer.Ordinal` を明示しています。「どの環境でも同じ順になる」ことが、ダミー変数の列の順を決めるこの処理では大事です
- カテゴリの一覧を引数で受け取るのは、訓練データで決めたカテゴリをテストデータにも同じように当てはめるためです。テストデータだけに現れた値は、すべての列が 0 になります

## 9.5 特徴量を標準化する

### 訓練データから平均と標準偏差を求める

列ごとに桁が違うと、正則化（第 12 章）や距離（第 14 章）を使う手法で、桁の大きい列ばかりが効いてしまいます。**標準化** は、(値 − 平均) / 標準偏差 で、列ごとに平均 0・標準偏差 1 にそろえます。

平均と標準偏差は **訓練データだけ** から求め、テストデータにも同じ値で当てはめます（第 2 章のデータリークと同じ理由です）。求める処理（`Fit`）と当てはめる処理（`Transform`）を分けます。

```csharp
// tests/MachineLearning.Tests/Chapter09/StandardizerTests.cs
[Fact(DisplayName = "訓練データの平均と標準偏差で、指定した列だけを標準化する")]
public void StandardizesSelectedColumns()
{
    var train = Samples.Column("RM", 1.0, 2.0, 3.0);

    var standardized = Standardizer.Fit(train, ["RM"])
        .Transform(new Features(["RM", "LSTAT"], [4.0, 7.0]));

    Assert.Equal(2.0 / Math.Sqrt(2.0 / 3.0), standardized.Value("RM"), 12);
    Assert.Equal(7.0, standardized.Value("LSTAT"));
}

[Fact(DisplayName = "標準偏差が 0 の列は 0 にする")]
public void ConstantColumnBecomesZero()
{
    var train = Samples.Column("CHAS", 1.0, 1.0);

    var standardized = Standardizer.Fit(train).Transform(train);

    Assert.Equal([0.0, 0.0], standardized.Select(features => features.Value("CHAS")));
}
```

```csharp
// src/MachineLearning/Chapter09/Standardizer.cs
public sealed class Standardizer
{
    private readonly Dictionary<string, double> means;
    private readonly Dictionary<string, double> stds;

    /// <summary>列ごとの平均。</summary>
    public IReadOnlyDictionary<string, double> Means => this.means;

    /// <summary>列ごとの標準偏差。すべて同じ値の列は 1 にして、標準化した値が 0 になるようにする。</summary>
    public IReadOnlyDictionary<string, double> Stds => this.stds;

    /// <summary>特徴量のすべての列について、平均と標準偏差を求める。</summary>
    public static Standardizer Fit(IReadOnlyList<Features> x) { /* 省略 */ }

    /// <summary>指定した列だけについて、平均と標準偏差を求める。ほかの列は標準化しない。</summary>
    public static Standardizer Fit(IReadOnlyList<Features> x, IReadOnlyList<string> columns)
    {
        // …列ごとに
        var values = x.Select(features => features.Value(column)).ToList();
        var mean = values.Average();
        var std = Math.Sqrt(values.Average(value => (value - mean) * (value - mean)));
        means[column] = mean;
        stds[column] = std == 0 ? 1.0 : std;
    }

    /// <summary>1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。</summary>
    public Features Transform(Features features)
    {
        ArgumentNullException.ThrowIfNull(features);
        return new Features(
            features.Columns,
            [.. features.Columns.Select((column, i) => this.means.TryGetValue(column, out var mean)
                ? (features.Values[i] - mean) / this.stds[column]
                : features.Values[i])]);
    }

    /// <summary>特徴量のリストを標準化する。</summary>
    public IReadOnlyList<Features> Transform(IReadOnlyList<Features> x) => [.. x.Select(this.Transform)];
}
```

- 学習した結果（平均と標準偏差）をオブジェクトで返し、当てはめるメソッドをそのオブジェクトに持たせます。第 3 章の「`Fit` が木を返し、`Predict` が木を受け取る」と同じ形です。F# 版は `fitStandardizer` と `transformStandardized` の 2 つの関数でしたが、C# ではメソッドにまとめるほうが自然に読めます
- 標準偏差は **件数で割る母標準偏差** です。件数 − 1 で割る不偏標準偏差ではありません。scikit-learn の `StandardScaler` と同じ定義にそろえてあります。[ADR 004](../../../adr/004-fsharp-ml-libraries.md) のとおり、ML.NET の `NormalizeMeanVariance` も母標準偏差なので、両者を突き合わせられます
- すべて同じ値の列（標準偏差 0）で割ると `NaN` になります。F# 版は「標準偏差 0 の列は変換後 0 にする」と書き、C# 版（と Java 版）は「標準偏差 0 を 1 に置き換える」と書いています。式は違いますが、結果はどちらも 0 です。分岐が `Transform` から消えるぶん、C# 版のほうが `Transform` が短くなります
- `Standardizer` は `record` にしていません。`record` は `IReadOnlyDictionary` の成分を **参照で** 比べるので、値の等価判定が期待どおりになりません。第 2 章の `Features` を `class` にしたのと同じ理由です

### 第 14 章から使えるようにする

第 14 章（K-means）は、距離でクラスタを作るので、列の桁をそろえておく必要があります。そこで `Standardizer` は次の形で公開しています。

| 使い方 | 呼び方 |
| :--- | :--- |
| すべての列をそろえる（第 14 章） | `Standardizer.Fit(x).Transform(x)` |
| 一部の列だけをそろえる | `Standardizer.Fit(x, ["RM"]).Transform(x)` |
| 訓練データで学び、テストデータに当てはめる | `var s = Standardizer.Fit(train); s.Transform(train); s.Transform(test);` |
| 平均と標準偏差を見る | `s.Means["RM"]`・`s.Stds["RM"]` |

入出力はどちらも第 2 章の `Features` なので、ほかの章の前処理や学習器にそのままつなげられます。

### ML.NET の正規化と突き合わせる

ML.NET には、平均と分散で正規化する `NormalizeMeanVariance` があります。[ADR 004](../../../adr/004-fsharp-ml-libraries.md) で、既定のままでは平均を引かないことが分かっていたので、学習用テストで確かめてから突き合わせます。

```csharp
// tests/MachineLearning.Tests/Chapter09/MlNetNormalizationTests.cs
private static readonly double[] Values = [1.0, 2.0, 3.0, 6.0];

[Fact(DisplayName = "学習用テスト: NormalizeMeanVariance の既定（fixZero）は平均を引かず、どの値にも同じ数を掛けるだけ")]
public void FixZeroKeepsZero()
{
    var normalized = MlNetNormalization.NormalizeMeanVariance(true, Values);

    var ratios = normalized.Select((value, i) => value / Values[i]).ToList();

    Assert.NotEqual(0.0, normalized.Average(), 5);
    Assert.All(ratios, ratio => Assert.Equal(ratios[0], ratio, 5));
}

[Fact(DisplayName = "fixZero を外すと、自作の標準化と同じ値になる")]
public void MatchesStandardizer()
{
    var rows = Samples.Column("x", Values);
    var mine = Standardizer.Fit(rows).Transform(rows).Select(features => features.Value("x")).ToList();

    var library = MlNetNormalization.NormalizeMeanVariance(false, Values);

    Assert.Equal(mine.Count, library.Count);
    Assert.All(library.Select((value, i) => (value, i)), pair => Assert.Equal(mine[pair.i], pair.value, 5));
}
```

`fixZero`（既定は `true`）は、「元の 0 を変換後も 0 のままにする」という指定です。0 を保つには平均を引けないので、既定では値を定数倍するだけになり、変換後の平均は 0 になりません。疎なデータ（0 の多いデータ）の 0 を崩さないための既定です。`fixZero: false` にすると、自作の標準化と小数第 5 位まで一致しました（ML.NET は `float`（単精度）で計算するので、桁はそこまでです）。

```csharp
// src/MachineLearning/Chapter09/MlNetNormalization.cs
/// <summary>ML.NET に渡す 1 行（1 列）。ML.NET は書き換えられるプロパティを持つクラスを求める。</summary>
public sealed class ValueRow
{
    public float Value { get; set; }
}

public static IReadOnlyList<double> NormalizeMeanVariance(bool fixZero, IReadOnlyList<double> values)
{
    ArgumentNullException.ThrowIfNull(values);
    var context = new MLContext(seed: 0);
    var data = context.Data.LoadFromEnumerable(
        values.Select(value => new ValueRow { Value = (float)value }).ToList());
    var transformed = context.Transforms
        .NormalizeMeanVariance("Value", fixZero: fixZero)
        .Fit(data)
        .Transform(data);
    return [.. context.Data
        .CreateEnumerable<ValueRow>(transformed, reuseRowObject: false)
        .Select(row => (double)row.Value)];
}
```

ここは F# 版が `[<CLIMutable>]` を付けたレコードで書いていた箇所です。ML.NET は「書き換えられるプロパティを持つクラス」を求めるので、F# では「レコードなのに書き換えられる」という属性が要りました。C# では普通のクラスがそのまま条件を満たすので、橋渡しは要りません。ML.NET が C# 向けに設計された API であることが、いちばん素直に出る場所です。

## 9.6 多項式特徴量を作る

住宅価格と部屋数（`RM`）の関係が直線とは限りません。`RM` の 2 乗の列を加えると、線形回帰でも曲がった関係を表せます。列どうしの積（**交互作用**）の項は、「部屋数が多く、かつ低所得者の割合が低い」のような組み合わせの効果を表します。

```csharp
// tests/MachineLearning.Tests/Chapter09/PolynomialFeaturesTests.cs
[Fact(DisplayName = "同じ列の組も含めて、列の組を重複なく作る")]
public void PairsWithReplacement() =>
    Assert.Equal(
        ["a^2", "a b", "b^2"],
        PolynomialFeatures.PairsWithReplacement(["a", "b"]).Select(term => term.Name));

[Fact(DisplayName = "元の列と 2 次の項の列を持つ特徴量を作る")]
public void ExpandsToSecondOrder()
{
    IReadOnlyList<Features> x = [new Features(["a", "b", "c"], [2.0, 3.0, 9.0])];

    var expanded = PolynomialFeatures.Expand(x, ["a", "b"]);

    Assert.Equal(["a", "b", "a^2", "a b", "b^2"], expanded[0].Columns);
    Assert.Equal([2.0, 3.0, 4.0, 6.0, 9.0], expanded[0].Values);
}
```

```csharp
// src/MachineLearning/Chapter09/PolynomialFeatures.cs
/// <summary>2 つの列の組。Left と Right が同じなら 2 乗の項を表す。</summary>
public sealed record Term(string Left, string Right)
{
    /// <summary>項の名前。scikit-learn の get_feature_names_out と同じ形（"RM^2"・"RM LSTAT"）にする。</summary>
    public string Name => string.Equals(this.Left, this.Right, StringComparison.Ordinal)
        ? $"{this.Left}^2"
        : $"{this.Left} {this.Right}";
}

public static IReadOnlyList<Term> PairsWithReplacement(IReadOnlyList<string> columns)
{
    ArgumentNullException.ThrowIfNull(columns);
    return [.. columns.SelectMany((left, i) => columns.Skip(i).Select(right => new Term(left, right)))];
}

public static IReadOnlyList<Features> Expand(IReadOnlyList<Features> x, IReadOnlyList<string> columns)
{
    var terms = PairsWithReplacement(columns);
    IReadOnlyList<string> names = [.. columns, .. terms.Select(term => term.Name)];
    return [.. x.Select(features => new Features(
        names,
        [
            .. columns.Select(features.Value),
            .. terms.Select(term => features.Value(term.Left) * features.Value(term.Right)),
        ]))];
}

/// <summary>指定した列だけを、その順に選ぶ。</summary>
public static IReadOnlyList<Features> Select(IReadOnlyList<Features> x, IReadOnlyList<string> columns) =>
    [.. x.Select(features => new Features(columns, [.. columns.Select(features.Value)]))];
```

- F# 版は組を `string * string` のタプルで表し、名前を作る関数（`termName`）を別に置きました。C# 版は `record Term` にして、名前を計算プロパティ（`Name`）にしています。値で比べられるので、`Assert.Equal(new Term("a", "b"), …)` のような書き方もできます
- `SelectMany((left, i) => columns.Skip(i).Select(...))` は、位置 `i` を受け取って、それより前の列を飛ばすことで、同じ組を 2 回作らないようにしています。F# 版の `List.mapi` + `List.skip i` と同じ考え方です
- コレクション式（`[a, .. b]`）でスプレッドが書けるので、「元の列の後ろに 2 次の項を足す」が 1 つの式になります
- 指定しなかった列（テストの `c`）は、結果に残しません。どの列を使うかを呼び出す側が決められます

## 9.7 外れ値を検出する

四分位範囲（IQR）を使って外れ値を見つけます。値を小さい順に並べて 4 等分したときの、1/4 の位置の値（第 1 四分位点）と 3/4 の位置の値（第 3 四分位点）の差が四分位範囲です。第 1 四分位点より「四分位範囲の 1.5 倍」以上小さい値と、第 3 四分位点より 1.5 倍以上大きい値を外れ値とします。

```csharp
// tests/MachineLearning.Tests/Chapter09/OutliersTests.cs
private static readonly double[] Quantiles = [0.0, 0.25, 0.5, 1.0];

[Fact(DisplayName = "分位点は並べた値の間を線形補間する")]
public void InterpolatesQuantile()
{
    IReadOnlyList<double> values = [4.0, 1.0, 3.0, 2.0];

    Assert.Equal(
        [1.0, 1.75, 2.5, 4.0],
        Quantiles.Select(q => Outliers.Quantile(values, q)));
}

[Fact(DisplayName = "訓練データだけから正解の外れ値の行を除く")]
public void RemovesFromTrainOnly()
{
    var split = new TrainTestSplit<int, double>(
        [1, 2, 3, 4, 5], [6], [1.0, 2.0, 3.0, 4.0, 100.0], [1000.0]);

    var removed = Outliers.RemoveTargetOutliers(split);

    Assert.Equal([1, 2, 3, 4], removed.XTrain);
    Assert.Equal([1.0, 2.0, 3.0, 4.0], removed.TTrain);
    Assert.Equal([1000.0], removed.TTest);
}
```

```csharp
// src/MachineLearning/Chapter09/Outliers.cs
/// <summary>外れ値とみなす、四分位点から四分位範囲の何倍離れているか。</summary>
public const double DefaultK = 1.5;

public static IReadOnlyList<bool> IqrOutliers(IReadOnlyList<double> values, double k = DefaultK)
{
    ArgumentNullException.ThrowIfNull(values);
    var q1 = Quantile(values, FirstQuartile);
    var q3 = Quantile(values, ThirdQuartile);
    var iqr = q3 - q1;
    return [.. values.Select(value => value < q1 - (k * iqr) || value > q3 + (k * iqr))];
}

public static TrainTestSplit<TX, double> RemoveTargetOutliers<TX>(TrainTestSplit<TX, double> split)
{
    ArgumentNullException.ThrowIfNull(split);
    var outliers = IqrOutliers(split.TTrain);
    var kept = Enumerable.Range(0, outliers.Count).Where(i => !outliers[i]).ToList();
    return new TrainTestSplit<TX, double>(
        [.. kept.Select(i => split.XTrain[i])],
        split.XTest,
        [.. kept.Select(i => split.TTrain[i])],
        split.TTest);
}
```

- F# 版は `iqrOutliersWith 1.5` の **部分適用** で `iqrOutliers` を作りました。C# には部分適用がないので、**省略可能な引数**（`double k = DefaultK`）で同じ使い勝手にしています。既定値がシグネチャに書かれるぶん、呼び出す側からは何が既定かが見えます
- `RemoveTargetOutliers` は、第 2 章の `TrainTestSplit<TX, TT>` を受け取って返します。特徴量の型は型引数 `TX` のままなので、どんな特徴量の分割にも使えます（テストでは `int` を渡しています）。F# 版のジェネリックな `'X` と同じ考え方です
- テストデータの外れ値は除きません。本番のデータから外れ値を選んで捨てることはできないためです
- `TrainTestSplit` は `record` なので、F# 版は `{ split with XTrain = … }` と書けました。C# の `with` 式は **すべての成分** を位置で持つ record なら使えますが、ここでは 4 つのうち 2 つを差し替えるので、素直に新しい `TrainTestSplit` を作っています

## 9.8 表を結合して特徴量を増やす

### 文字コードと区切り文字を指定して読む

第 2 章の `Table.Load` は、UTF-8 のカンマ区切りだけを読みます。`bike.tsv` はタブ区切り、`weather.csv` は Shift_JIS なので、どちらもそのままでは読めません。

ここで `Table.Load` に引数を足したくなりますが、第 2 章のコードは変えません。読み込む口を **この章に足す** ほうが、第 2 章の読者が読むコードを増やさずに済みます。

```csharp
// src/MachineLearning/Chapter09/DelimitedFile.cs
public static class DelimitedFile
{
    /// <summary>1 行目を列名として読み込む。</summary>
    public static Table Load(string file, Encoding encoding, char delimiter)
    {
        var lines = File.ReadAllLines(file, encoding);
        var columns = lines[0].Split(delimiter);
        var rows = lines.Skip(1)
            .Where(line => !string.IsNullOrWhiteSpace(line))
            .Select(line => ToRow(columns, line.Split(delimiter)))
            .ToList();
        return new Table(columns, rows);
    }
}
```

`File.ReadAllLines` は文字コードを引数で受け取れます。返す型は第 2 章の `Table` なので、読み込んだ後は第 2 章の `Row.Number`・`Row.Text` がそのまま使えます。

F# 版は型プロバイダ（`CsvProvider<..., Separators="\t">`）で TSV を読みました。列の名前と型がコンパイル時に決まるのが型プロバイダの強みですが、C# には型プロバイダがないので、第 2 章から続く「セルの文字列を列名で引く表」で通します。

### Shift_JIS を読む

`weather.csv` は Shift_JIS で書かれています。.NET の標準では UTF-8 などしか使えず、Shift_JIS のようなコードページのエンコーディングは、一度 **登録** してからでないと使えません。

```csharp
// tests/MachineLearning.Tests/Chapter09/BikeWeatherTests.cs
[Fact(DisplayName = "Shift_JIS の CSV を読み込む")]
public void ReadsShiftJis()
{
    File.WriteAllText(this.file, "weather_id,weather\n1,晴れ\n2,曇り\n", BikeWeather.ShiftJis());

    var weather = BikeWeather.LoadWeather(this.file);

    Assert.Equal(["weather_id", "weather"], weather.Columns);
    Assert.Equal(["晴れ", "曇り"], weather.Rows.Select(row => row.Text("weather")));
}

[Fact(DisplayName = "学習用テスト: Shift_JIS のファイルを UTF-8 として読むと文字化けする")]
public void MojibakeWhenUtf8()
{
    File.WriteAllText(this.file, "晴れ", BikeWeather.ShiftJis());

    Assert.NotEqual("晴れ", File.ReadAllText(this.file, Encoding.UTF8));
}
```

```csharp
// src/MachineLearning/Chapter09/BikeWeather.cs
/// <summary>
/// Shift_JIS のエンコーディング。.NET では、コードページのエンコーディングを使う前に
/// CodePagesEncodingProvider を一度登録する必要がある（登録は何度行っても害はない）。
/// </summary>
public static Encoding ShiftJis()
{
    Encoding.RegisterProvider(CodePagesEncodingProvider.Instance);
    return Encoding.GetEncoding("shift_jis");
}

/// <summary>タブ区切りの bike.tsv（UTF-8）を読み込む。</summary>
public static Table LoadBike(string tsvFile) => DelimitedFile.Load(tsvFile, Encoding.UTF8, '\t');

/// <summary>Shift_JIS の weather.csv を読み込む。</summary>
public static Table LoadWeather(string csvFile) => DelimitedFile.Load(csvFile, ShiftJis(), ',');
```

C# を書き始めるときに迷うのが「`System.Text.Encoding.CodePages` パッケージを足す必要があるか」です。**足しませんでした。** .NET Framework 時代の記事では追加のパッケージが要ると書かれていますが、`net10.0` では `CodePagesEncodingProvider` は共有フレームワーク（`Microsoft.NETCore.App`）に入っているので、`using System.Text;` だけで使えます。上のテストを実行して、`Directory.Packages.props` にも `packages.lock.json` にも手を入れずに通ることを確かめています。

なお、`Directory.Build.props` で `InvariantGlobalization` を `true` にしていますが、これはカルチャ（日付や並べ替えの規則）の話なので、エンコーディングの登録には影響しませんでした。

Java 版は、文字コードの違うファイルを UTF-8 として読むと `MalformedInputException` を投げます。.NET は既定で読み替えを行うので、例外にはならず **文字化けした文字列が返ります**。上の 2 本目のテストは、この「黙って壊れる」振る舞いを固定するための学習用テストです。例外が出ないぶん、C# では文字コードの指定漏れに気づきにくい、ということでもあります。

### 表を結合する

```csharp
[Fact(DisplayName = "天気の番号で天気の名前を結合し、名前が無い行は除く")]
public void JoinsOnWeatherId()
{
    var bike = Samples.Table(["weather_id", "cnt"], ["1", "100"], ["2", "50"], ["9", "1"]);
    var weather = Samples.Table(["weather_id", "weather"], ["1", "晴れ"], ["2", "曇り"]);

    var joined = BikeWeather.JoinWeather(bike, weather);

    Assert.Equal(["weather_id", "cnt", "weather"], joined.Columns);
    Assert.Equal(["晴れ", "曇り"], joined.Rows.Select(row => row.Text("weather")));
}
```

```csharp
/// <summary>天気の番号で天気の列を加える（内部結合）。天気の表に無い番号の行は残さない。</summary>
public static Table JoinWeather(Table bike, Table weather)
{
    ArgumentNullException.ThrowIfNull(bike);
    ArgumentNullException.ThrowIfNull(weather);
    var byId = weather.Rows.ToDictionary(row => row.Text(Key), row => row, StringComparer.Ordinal);
    var added = weather.Columns.Where(column => !string.Equals(column, Key, StringComparison.Ordinal)).ToList();
    var rows = bike.Rows
        .Where(row => byId.ContainsKey(row.Text(Key)))
        .Select(row => Join(row, bike.Columns, byId[row.Text(Key)], added))
        .ToList();
    return new Table([.. bike.Columns, .. added], rows);
}

/// <summary>天気ごとの平均利用者数を、多い順に並べて返す。</summary>
public static IReadOnlyList<KeyValuePair<string, double>> MeanCountByWeather(Table joined) =>
    [.. joined.Rows
        .GroupBy(row => row.Text(WeatherColumn), StringComparer.Ordinal)
        .Select(group => KeyValuePair.Create(group.Key, group.Average(row => row.Number(CountColumn) ?? 0)))
        .OrderByDescending(pair => pair.Value)];
```

- 対応表を `Dictionary` にしておけば、結合は「番号で名前を引く」だけです。`ContainsKey` で絞ってから引くので、対応表に無い番号の行は落ちます。SQL の内部結合（INNER JOIN）に当たります
- F# 版は `Map.tryFind` が返す `option` を `List.choose` で畳んで「無い行を捨てる」を 1 つの式にしました。C# では `Where` + 添字のほうが読みやすいので、2 段に分けています
- `GroupBy` → `Select` → `OrderByDescending` は、F# 版の `List.groupBy` → `List.map` → `List.sortByDescending` とそのまま対応します。LINQ は遅延評価なので、最後に `[.. …]` でリストに落として、並び順を確定させます

## 9.9 特徴量の効果を測る

### 線形回帰をこの章で作る

決定係数を測るには線形回帰が要ります。F# 版は第 7 章の `fitLinearRegression`・`r2Score` をそのまま使いました。C# 版では、この章だけで完結するように、正規方程式をガウスの消去法で解く小さな `LinearModel` を作ります。

```csharp
// src/MachineLearning/Chapter09/LinearModel.cs
public static LinearModel Fit(IReadOnlyList<Features> x, IReadOnlyList<double> t)
{
    // 先頭に 1 の列（切片の分）を足した計画行列。配列初期化子の中では .. をスプレッドとして書けないので、
    // コレクション式（[...]）で書く
    var design = x.Select(double[] (features) => [1.0, .. features.Values]).ToList();
    // …XᵀX と Xᵀt を作り、部分ピボット選択つきのガウスの消去法で解く
}

/// <summary>決定係数。1 から、残差の 2 乗和を平均との差の 2 乗和で割った値を引く。</summary>
public static double RSquared(IReadOnlyList<double> actual, IReadOnlyList<double> predicted)
{
    var mean = actual.Average();
    var residual = actual.Select((value, i) => (value - predicted[i]) * (value - predicted[i])).Sum();
    var total = actual.Sum(value => (value - mean) * (value - mean));
    return 1 - (residual / total);
}
```

最初は計画行列の 1 行を `new double[] { 1.0, .. features.Values }` と書いて、次のコンパイルエラーになりました。

```text
error CS0029: 型 'System.Collections.Generic.IReadOnlyList<double>' を 'System.Index' に暗黙的に変換できません
error IDE0055: 書式設定を修正
```

`..` をスプレッドとして書けるのは **コレクション式**（`[...]`）の中だけで、配列初期化子（`new double[] { ... }`）の中では範囲演算子として解釈されます。コンパイラは `..features.Values` を「末尾を `features.Values` とする範囲」と読み、`System.Index` への変換を探しに行ったわけです。ラムダの戻り値の型を明示（`double[] (features) => [...]`）してコレクション式で書くと通りました。IDE0055 は、コンパイルできないコードの整形が判断できないために付いてきた指摘です。

同じ列が 2 つあると XᵀX が正則でなくなるので、ピボットが 0 になった時点で例外にしています。ダミー変数で先頭のカテゴリを落としたのは、ここで例外にならないようにするためです。

```csharp
[Fact(DisplayName = "同じ値の列が 2 つあると正規方程式を解けない")]
public void RejectsDependentColumns()
{
    IReadOnlyList<Features> x =
    [
        new Features(["a", "b"], [1.0, 1.0]),
        new Features(["a", "b"], [2.0, 2.0]),
        new Features(["a", "b"], [3.0, 3.0]),
    ];

    Assert.Throws<ArgumentException>(() => LinearModel.Fit(x, [1.0, 2.0, 3.0]));
}
```

### 読み込みから決定係数まで

```csharp
// src/MachineLearning/Chapter09/Boston.cs
/// <summary>CRIME をダミー変数にし、分割してから訓練データの平均値で両方の欠損値を補完する。</summary>
public static TrainTestSplit<Features, double> Prepare(string csvFile, double testSize, int seed)
{
    var table = Table.Load(csvFile);
    var categories = Dummies.Categories(table.Rows.Select(row => row.Text(Category)));
    var encoded = Dummies.Encode(table, Category, categories);
    var (columns, rows, target) = Preprocessing.SplitFeaturesAndTarget(encoded, Target);
    var prices = target.Select(value => double.Parse(value, CultureInfo.InvariantCulture)).ToList();
    var split = Preprocessing.SplitTrainTest(rows, prices, testSize, seed);
    var means = Preprocessing.ColumnMeans(split.XTrain, columns);
    return new TrainTestSplit<Features, double>(
        Preprocessing.FillMissing(split.XTrain, columns, means),
        Preprocessing.FillMissing(split.XTest, columns, means),
        split.TTrain,
        split.TTest);
}

/// <summary>
/// columns から 2 次の項を作って terms の列だけを選び、訓練データの平均と標準偏差で標準化してから
/// 線形回帰で学習し、訓練データとテストデータの決定係数を返す。
/// </summary>
public static Scores ScoreFeatureSet(
    TrainTestSplit<Features, double> split, IReadOnlyList<string> columns, IReadOnlyList<string> terms)
{
    var train = PolynomialFeatures.Select(PolynomialFeatures.Expand(split.XTrain, columns), terms);
    var test = PolynomialFeatures.Select(PolynomialFeatures.Expand(split.XTest, columns), terms);
    var standardizer = Standardizer.Fit(train);
    var xTrain = standardizer.Transform(train);
    var xTest = standardizer.Transform(test);
    var model = LinearModel.Fit(xTrain, split.TTrain);
    return new Scores(
        LinearModel.RSquared(split.TTrain, model.Predict(xTrain)),
        LinearModel.RSquared(split.TTest, model.Predict(xTest)));
}
```

F# 版は 14 列を型プロバイダの `Schema` に書く代わりに、型を持たない `CsvFile` に切り替えるという判断をしました。C# 版は第 2 章から一貫して「セルの文字列を列名で引く表」なので、列が 14 列でも読み方は変わりません。型の助けがないぶん、列名の書き間違いは実行時に分かります（`Row.Text` が列名を添えた例外を投げます）。

`Scores` は `readonly record struct` にしています。成分が `double` 2 つだけなので、値で比べられて、ヒープにも載りません。

`RM`・`LSTAT`・`PTRATIO` の 3 列から、次の 3 通りを比べます。

- 元の特徴量（3 列）
- 2 乗の項を追加（6 列）
- 交互作用の項も追加（9 列）

### 実データで測る

```bash
dotnet run --project src/MachineLearning -- chapter09
```

```text
訓練データ: 70 件, テストデータ: 30 件
特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low
標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00
ML.NET で正規化した訓練データの RM: 平均 0.00, 標準偏差 1.00
決定係数:
  元の特徴量（3 列）: 訓練 0.5723, テスト 0.6970
  2 乗の項を追加（6 列）: 訓練 0.7561, テスト 0.8351
  交互作用の項も追加（9 列）: 訓練 0.7779, テスト 0.8078
訓練データの PRICE の外れ値: 6 件
  外れ値を除いて 2 乗の項を追加: 訓練 0.6351, テスト 0.7110
天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
```

- 2 乗の項を加えると、テストデータの決定係数は 0.70 から 0.84 に上がりました。価格と部屋数・低所得者の割合の関係が、直線より曲線に近いことを表しています
- 交互作用の項まで加えると、訓練データの決定係数は上がりましたが、テストデータでは 0.81 に下がりました。100 件のデータに 9 列は多く、訓練データに合わせすぎています（第 12 章の過学習です）
- 価格の外れ値 6 件を訓練データから除くと、テストデータの決定係数は 0.84 から 0.71 に下がりました。外れ値はすべて高額の側（39.8・43.8・44.8・48.5・50.0・50.0）にあり、除いた訓練データでは、高額な物件の傾向を学べなくなります。外れ値は「誤った値」とは限りません。除くかどうかは、値が誤りかどうかを確かめてから判断します
- 表示の「平均 0.00」は、0 とみなす幅を 1e-6 にしてあります。ML.NET は `float` で計算するので、平均の誤差が 1e-9 より大きく残り、幅が狭いと `-0.00` と表示されてしまうためです

### F# 版との突き合わせ

この章の数値は、**F# 版の第 9 章の表示と 1 行残らず一致しました**。決定係数（0.5723 / 0.6970、0.7561 / 0.8351、0.7779 / 0.8078、0.6351 / 0.7110）も、外れ値の 6 件も、天気ごとの平均利用者数（晴れ 4876.8・曇り 4052.7・雨 1803.3）も同じです。

一致する理由は 2 つあります。

1. 分割が同じ — 第 2 章で、訓練データとテストデータの分け方を F# 版と同じ `System.Random(seed)` と Fisher–Yates のシャッフルにそろえました。同じ .NET の乱数なので、70 件と 30 件に入る行が F# 版と同じになります
2. 計算が同じ — 標準化は母標準偏差、決定係数は同じ定義、線形回帰は同じ正規方程式です。解き方は F# 版が行列ライブラリ、C# 版がガウスの消去法と違いますが、解は同じなので小数第 4 位まで一致します

Java 版は分割に `Collections.shuffle` を使うので、同じデータでも訓練データに入る行が違い、決定係数は一致しません。天気ごとの平均利用者数は分割に関係しないので、どの言語版でも同じ値です。

### 実データのテスト

表示をまるごと比べるテストと、決定係数・四分位点・外れ値・結合の件数を確かめるテストを残しています（学習データが無ければスキップします）。

```csharp
// tests/MachineLearning.Tests/Chapter09/FeatureEngineeringDataTests.cs
[Fact(DisplayName = "訓練データの PRICE の外れ値 6 件はすべて高額の側にある")]
public void OutliersAreExpensive()
{
    this.SkipUnlessBoston();

    var prices = this.Split().TTrain;
    var outliers = Outliers.IqrOutliers(prices);

    Assert.Equal(17.95, Outliers.Quantile(prices, 0.25), 2);
    Assert.Equal(24.8, Outliers.Quantile(prices, 0.75), 2);
    Assert.Equal(
        [39.8, 43.8, 44.8, 48.5, 50.0, 50.0],
        prices.Where((_, i) => outliers[i]).Order());
}
```

第 9 章のテストは 35 件です。学習データを置かずに実行すると、実データのテスト 7 件がスキップされ、残りは通ります。

## 9.10 可視化

C# 版では可視化を扱いません。標準化の前後の分布、`RM`・`LSTAT` と価格の散布図、価格の箱ひげ図は、[Python 版の第 9 章](../python/09-feature-engineering.md) と [Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md) を参照してください。数値はどちらの版でも同じ形のデータから得られるので、図を見てから C# の実装に戻ると、2 乗の項が効いた理由（関係が曲がっていること）と、外れ値が高額の側に偏っていることが読み取れます。

<details>
<summary>この章の完成コード（src/MachineLearning/Chapter09/Standardizer.cs）</summary>

```csharp
namespace MachineLearning.Chapter09;

using MachineLearning.Chapter02;

/// <summary>
/// 列ごとの平均と標準偏差（件数で割る母標準偏差）で、平均 0・標準偏差 1 にそろえる。
/// 訓練データで <see cref="Fit(IReadOnlyList{Features})"/> し、同じ平均と標準偏差で
/// 訓練データとテストデータの両方を <see cref="Transform(IReadOnlyList{Features})"/> する。
/// 第 14 章（K-means）もこの型で特徴量をそろえる。
/// </summary>
public sealed class Standardizer
{
    private readonly Dictionary<string, double> means;
    private readonly Dictionary<string, double> stds;

    private Standardizer(Dictionary<string, double> means, Dictionary<string, double> stds)
    {
        this.means = means;
        this.stds = stds;
    }

    /// <summary>列ごとの平均。</summary>
    public IReadOnlyDictionary<string, double> Means => this.means;

    /// <summary>列ごとの標準偏差。すべて同じ値の列は 1 にして、標準化した値が 0 になるようにする。</summary>
    public IReadOnlyDictionary<string, double> Stds => this.stds;

    /// <summary>特徴量のすべての列について、平均と標準偏差を求める。</summary>
    public static Standardizer Fit(IReadOnlyList<Features> x)
    {
        ArgumentNullException.ThrowIfNull(x);
        return Fit(x, x.Count > 0 ? x[0].Columns : throw new ArgumentException("特徴量が 1 件もありません", nameof(x)));
    }

    /// <summary>指定した列だけについて、平均と標準偏差を求める。ほかの列は標準化しない。</summary>
    public static Standardizer Fit(IReadOnlyList<Features> x, IReadOnlyList<string> columns)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(columns);
        if (x.Count == 0)
        {
            throw new ArgumentException("特徴量が 1 件もありません", nameof(x));
        }

        var means = new Dictionary<string, double>(StringComparer.Ordinal);
        var stds = new Dictionary<string, double>(StringComparer.Ordinal);
        foreach (var column in columns)
        {
            var values = x.Select(features => features.Value(column)).ToList();
            var mean = values.Average();
            var std = Math.Sqrt(values.Average(value => (value - mean) * (value - mean)));
            means[column] = mean;
            stds[column] = std == 0 ? 1.0 : std;
        }

        return new Standardizer(means, stds);
    }

    /// <summary>1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。</summary>
    public Features Transform(Features features)
    {
        ArgumentNullException.ThrowIfNull(features);
        return new Features(
            features.Columns,
            [.. features.Columns.Select((column, i) => this.means.TryGetValue(column, out var mean)
                ? (features.Values[i] - mean) / this.stds[column]
                : features.Values[i])]);
    }

    /// <summary>特徴量のリストを標準化する。</summary>
    public IReadOnlyList<Features> Transform(IReadOnlyList<Features> x)
    {
        ArgumentNullException.ThrowIfNull(x);
        return [.. x.Select(this.Transform)];
    }
}
```

</details>

## 9.11 まとめ

この章では、手元の列から特徴量を作り、線形回帰の決定係数で効果を確かめました。

1. **学習と適用を分ける** — ダミー変数のカテゴリも、標準化の平均と標準偏差も、訓練データで決めてからテストデータに当てはめた。`Fit` が状態を持つオブジェクトを返し、`Transform` がそれを使う形にそろえた
2. **後の章から使える形で公開する** — `Standardizer` は全列・一部の列のどちらでも `Fit` でき、入出力は第 2 章の `Features` にした。第 14 章（K-means）はこの型をそのまま使う
3. **既存のコードを変えずに足す** — TSV と Shift_JIS は、第 2 章の `Table` に引数を足すのではなく、`DelimitedFile` という読み込む口をこの章に足して解決した
4. **Shift_JIS に追加の依存は要らない** — `net10.0` では `CodePagesEncodingProvider` が共有フレームワークにあるので、登録するだけで読める。ただし .NET は文字コードが違っても例外を投げず、黙って文字化けするので、学習用テストでその振る舞いを固定した
5. **コレクション式はどこでも使えるわけではない** — `..` をスプレッドとして書けるのは `[...]` の中だけで、`new double[] { ... }` の中では範囲演算子になる（CS0029）
6. **特徴量は増やせばよいわけではない** — 2 乗の項は効いたが、交互作用の項まで加えると過学習した。外れ値も、機械的に除くと性能が下がった
7. **分割をそろえると数値がそろう** — 第 2 章で分割を F# 版と同じにしておいたおかげで、この章の決定係数は F# 版と小数第 4 位まで一致した

次の章では、ロジスティック回帰と、決定木を組み合わせるアンサンブル学習を扱います。
