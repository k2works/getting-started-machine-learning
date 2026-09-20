---
type: Article
title: "第 10 章: ロジスティック回帰とアンサンブル学習"
description: "ソフトマックスと勾配降下法のロジスティック回帰を C# で TDD で自作し、第 3 章の決定木を再利用したランダムフォレストと特徴量の重要度を求める。分類器をインターフェースで表して、ML.NET の LbfgsMaximumEntropy と FastForest（OneVersusAll）を同じ拡張メソッドで評価する。"
tags: [article,getting-start-ml,csharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T01:30:00Z }
---

# 第 10 章: ロジスティック回帰とアンサンブル学習

## 10.1 はじめに

第 3 章では、決定木を自作して iris を分類しました。この章では、分類のアルゴリズムを 2 つ増やします。

- **ロジスティック回帰**: 特徴量の重み付きの和から、各品種である確率を求めるモデル。勾配降下法で重みを学習する
- **ランダムフォレスト**: 少しずつ違うデータで学習した決定木をたくさん作り、多数決で予測するモデル。複数のモデルを組み合わせる手法を **アンサンブル学習** と呼ぶ

モデルが増えると、「学習させて正解率を測る」処理をモデルごとに書くのは重複です。そこで、モデルに共通する形を型で表し、どのモデルも同じ方法で評価できるようにします。最後に、どの特徴量が分類に効いたかを表す **特徴量の重要度** を自作します。

[Python 版の第 10 章](../python/10-logistic-regression-and-ensemble.md)・[Kotlin 版の第 10 章](../kotlin/10-logistic-regression-and-ensemble.md)・[F# 版の第 10 章](../fsharp/10-logistic-regression-and-ensemble.md) と同じ TODO リストで進めます。C# 版では、次の 3 点に注目してください。

- 勾配降下法の繰り返しを、C# らしく `for` と配列の書き換えで書く。F# 版が `List.fold` で「書き換えない」書き方を選んだのと対になります。学習の設定は record の `with` 式で写して変える
- ランダムフォレストの乱数は、第 2 章の `Shuffle` と同じく **シードを受け取るメソッド** に閉じ込める。同じシードなら同じ森になることをテストで固定する
- 分類器を **インターフェース（`IClassifier`）** で表す。第 3 章の `DecisionTree` は `Fit`・`Predict` の形が違うので、第 3 章のコードを変更せずに **アダプター** で合わせる

ライブラリとの突き合わせには、ML.NET の `LbfgsMaximumEntropy`（ソフトマックスのロジスティック回帰）と `FastForest`（ランダムフォレスト）を使います。FastForest は 2 クラス分類の学習器なので、第 3 章と同じく `OneVersusAll` で多クラスにします（[ADR 004](../../../adr/004-fsharp-ml-libraries.md)）。最適化の方法や乱数の使い方が自作と違うので、予測の完全一致は求めず、正解率を比べます。

データは第 2 章・第 3 章と同じ iris を使い、第 2 章の `Preprocessing.PrepareIris` で前処理します。分割は F# 版と同じ `System.Random` + Fisher–Yates なので、訓練データとテストデータに入る行は F# 版と一致します。そのうえで正解率が F# 版と一致するかどうかは、10.10 節で実測して確かめます。

## 10.2 TODO リストの作成

**TODO リスト**:

- [ ] ソフトマックス関数で確率に変換する
  - [ ] 値がすべて同じなら確率は均等になる
  - [ ] 値の差が指数の比になる
  - [ ] 大きな値でもあふれない
- [ ] ロジスティック回帰を学習して予測する
  - [ ] 2 種類・3 種類のラベルを予測する
  - [ ] 学習を繰り返すと損失が小さくなる
- [ ] ランダムフォレストを学習して予測する
  - [ ] 多数決で予測を 1 つに決める
  - [ ] ブートストラップ標本を選ぶ
  - [ ] 第 3 章の決定木を再利用する
- [ ] 特徴量の重要度を計算する
  - [ ] 決定木 1 本の重要度
  - [ ] ランダムフォレストの重要度
- [ ] どのモデルも同じインターフェースで評価する
  - [ ] 第 3 章の決定木を変更せずに共通の型に合わせる
  - [ ] ML.NET の学習器も同じ方法で評価する
- [ ] 実データで ML.NET と正解率を突き合わせる

## 10.3 ソフトマックス関数

### ロジスティック回帰の仕組み

ロジスティック回帰は、品種ごとに「特徴量の重み付きの和 + 切片」を計算し、それを **ソフトマックス関数** で確率に変換します。

```text
スコア(品種 k) = w(k, がく片長さ) × がく片長さ + … + w(k, 花弁幅) × 花弁幅 + b(k)
確率(品種 k)   = exp(スコア(品種 k)) / Σ exp(スコア(品種 j))
```

多クラスへの広げ方には、品種ごとに「その品種か、それ以外か」の 2 値分類器を作る one-vs-rest と、この章のように 1 つのモデルで全品種の確率を同時に求めるソフトマックス（多項ロジスティック回帰）があります。ソフトマックスを選んだのは、3 品種の確率の合計が必ず 1 になり、確率として解釈しやすいからです。ML.NET の `LbfgsMaximumEntropy` も同じソフトマックスのモデルです（「最大エントロピー」は多項ロジスティック回帰の別名です）。

### 仮実装

1 サンプル分のスコアを受け取り、確率の配列を返すメソッドにします。

```csharp
// tests/MachineLearning.Tests/Chapter10/LogisticRegressionTests.cs
namespace MachineLearning.Tests.Chapter10;

using MachineLearning.Chapter02;
using MachineLearning.Chapter10;

public class LogisticRegressionTests
{
    [Fact(DisplayName = "値がすべて同じなら確率は均等になる")]
    public void UniformScores()
    {
        Assert.Equal([0.25, 0.25, 0.25, 0.25], LogisticRegression.Softmax([0.0, 0.0, 0.0, 0.0]));
    }
}
```

Kotlin 版では、`DoubleArray` の `equals` が中身ではなく同じ配列かどうかを比べるので、リストに直してから比べていました。xUnit の `Assert.Equal` はコレクションを要素ごとに比べるので、配列のまま比べられます。C# 11 以降のコレクション式（`[0.25, 0.25, …]`）で、型名を書かずに配列を渡せます。

ビルドすると、まだ何も無いので失敗します。

```text
error CS0234: 型または名前空間の名前 'Chapter10' が名前空間 'MachineLearning' に存在しません
```

均等な確率を返す仮実装で Green にします。

```csharp
// src/MachineLearning/Chapter10/LogisticRegression.cs
namespace MachineLearning.Chapter10;

public sealed class LogisticRegression
{
    public static double[] Softmax(IReadOnlyList<double> z)
    {
        ArgumentNullException.ThrowIfNull(z);
        return [.. Enumerable.Repeat(1.0 / z.Count, z.Count)];
    }
}
```

F# 版は `.fsproj` の `Compile` にファイルを順番どおりに書き足す必要がありましたが、C# のプロジェクトはディレクトリ以下の `.cs` を自動で拾うので、ファイルを置くだけで済みます。

`ArgumentNullException.ThrowIfNull` を書いているのは、.NET アナライザー（`AnalysisMode` は `Recommended`）と `TreatWarningsAsErrors` の下で、公開メソッドの引数の検査を省くと指摘が出る箇所があるためです。第 1 章から続けている書き方です。

### 三角測量

値の差が `log 2` なら、確率の比は 2 倍になるはずです。

```csharp
[Fact(DisplayName = "値の差が指数の比になる")]
public void RatioOfExponentials()
{
    var probabilities = LogisticRegression.Softmax([0.0, Math.Log(2.0)]);

    Assert.Equal(1.0 / 3.0, probabilities[0], 12);
    Assert.Equal(2.0 / 3.0, probabilities[1], 12);
}
```

`Math.Log` は自然対数です。`Assert.Equal` の 3 つ目の引数は、比べる小数点以下の桁数です。

```text
失敗 値の差が指数の比になる (99ms)
  Assert.Equal() Failure: Values are not within 12 decimal places
  Expected: 0.33333333333300003 (rounded from 0.33333333333333331)
  Actual:   0.5 (rounded from 0.5)
```

定義どおりに一般化します。

```csharp
public static double[] Softmax(IReadOnlyList<double> z)
{
    ArgumentNullException.ThrowIfNull(z);
    var exps = z.Select(Math.Exp).ToArray();
    var total = exps.Sum();
    return [.. exps.Select(value => value / total)];
}
```

`z.Select(Math.Exp)` は、メソッドグループをそのままデリゲートとして渡す書き方です。F# 版の `Array.map exp` に当たります。

### 大きな値でもあふれない

学習の途中では、スコアが大きな値になることがあります。

```csharp
[Fact(DisplayName = "大きな値でもあふれずに確率を求める")]
public void NoOverflow()
{
    Assert.Equal([0.5, 0.5], LogisticRegression.Softmax([1000.0, 1000.0]));
}
```

```text
失敗 大きな値でもあふれずに確率を求める (195ms)
  Assert.Equal() Failure: Collections differ
             ↓ (pos 0)
  Expected: [0.5, 0.5]
  Actual:   [NaN, NaN]
             ↑ (pos 0)
```

`Math.Exp(1000.0)` は `double` で表せないので `Infinity` になり、`Infinity / Infinity` が `NaN` になりました。C# は `double` の桁あふれを例外にしませんし、アナライザーも止めません。境界の値のテストが無ければ、学習が途中から `NaN` だらけになるまで気づけません。

すべてのスコアから最大値を引いてから `exp` を計算します。分母と分子に同じ `exp(-最大値)` を掛けることになるので、確率は変わりません。

```csharp
/// <summary>スコアを確率に変換する。最大値を引いてから exp を計算して、大きな値でもあふれないようにする。</summary>
public static double[] Softmax(IReadOnlyList<double> z)
{
    ArgumentNullException.ThrowIfNull(z);
    var max = z.Max();
    var exps = z.Select(value => Math.Exp(value - max)).ToArray();
    var total = exps.Sum();
    return [.. exps.Select(value => value / total)];
}
```

## 10.4 ロジスティック回帰

### 学習の設定とモデル

学習の設定は、第 3 章までと同じく record で表します。既定値は `static` プロパティに置き、変えたいところだけ `with` 式で写します。

```csharp
/// <summary>ロジスティック回帰の学習の設定。</summary>
public sealed record LogisticSettings(double LearningRate, int Epochs)
{
    /// <summary>既定の設定（学習率 1.0、繰り返し 5000 回）。</summary>
    public static LogisticSettings Default { get; } = new(1.0, 5000);
}
```

`LogisticSettings.Default with { Epochs = 100 }` は、F# 版の `{ defaults with Epochs = 100 }` と同じ働きです。record は値で比べられ、`with` 式で写しを作れるので、設定のような小さな値の入れ物にちょうど合います。

学習の結果は `LogisticModel` に入れます。予測に必要なのは、特徴量の列名・品種・重み・切片です。損失の履歴も、学習が進んでいるかを確かめるために持ちます。

```csharp
[Fact(DisplayName = "2 種類のラベルを境界の左右で予測する")]
public void TwoLabels()
{
    var model = LogisticModel.Learn(
        LogisticSettings.Default,
        ByPetalWidth(0.1, 0.2, 0.8, 0.9),
        ["setosa", "setosa", "virginica", "virginica"]);

    Assert.Equal(["setosa", "virginica"], model.Predict(ByPetalWidth(0.15, 0.85)));
}
```

`ByPetalWidth` は、花弁幅だけを持つ第 2 章の `Features` を並べるテスト用のヘルパーです。

```csharp
/// <summary>花弁幅だけを特徴量に持つ行のリストを作る。</summary>
internal static IReadOnlyList<Features> ByPetalWidth(params double[] values) =>
    [.. values.Select(value => new Features(["花弁幅"], [value]))];
```

第 2 章の `Features` は、列名の並びと値の配列を持つクラスです。F# 版は `Map<string, float>` なので列名の順が辞書順に並びましたが、C# 版は **CSV の列の順** がそのまま残ります。この違いが結果に出るかどうかは、10.10 節で確かめます。

### 勾配降下法で学習する

学習は、次の 3 つを繰り返します。

1. いまの重みで全サンプルの確率を求める
2. 「確率 − 正解」（正解の品種だけ 1 を引いたもの）を誤差にする
3. 誤差と特徴量の積の平均を勾配として、重みと切片を学習率の分だけ動かす

```csharp
/// <summary>バッチ勾配降下法で重みと切片を学習する。</summary>
public static LogisticModel Learn(
    LogisticSettings settings, IReadOnlyList<Features> x, IReadOnlyList<string> t)
{
    ArgumentNullException.ThrowIfNull(settings);
    ArgumentNullException.ThrowIfNull(x);
    ArgumentNullException.ThrowIfNull(t);
    var features = x[0].Columns;
    var rows = x.Select(row => row.Values.ToArray()).ToArray();
    var classes = t.Distinct(StringComparer.Ordinal).Order(StringComparer.Ordinal).ToArray();
    var targets = t.Select(label => Array.IndexOf(classes, label)).ToArray();
    double n = rows.Length;

    var weights = Enumerable.Range(0, features.Count).Select(_ => new double[classes.Length]).ToArray();
    var bias = new double[classes.Length];
    var losses = new List<double>(settings.Epochs);

    for (var epoch = 0; epoch < settings.Epochs; epoch++)
    {
        var probabilities = rows.Select(row => LogisticRegression.Softmax(Scores(weights, bias, row))).ToArray();

        // 確率 − 正解（正解の品種だけ 1 を引く）
        var errors = probabilities
            .Select((p, i) => p.Select((value, k) => k == targets[i] ? value - 1.0 : value).ToArray())
            .ToArray();

        for (var f = 0; f < features.Count; f++)
        {
            for (var k = 0; k < classes.Length; k++)
            {
                var gradient = rows.Select((row, i) => row[f] * errors[i][k]).Sum();
                weights[f][k] -= settings.LearningRate * gradient / n;
            }
        }

        for (var k = 0; k < classes.Length; k++)
        {
            bias[k] -= settings.LearningRate * errors.Sum(error => error[k]) / n;
        }

        losses.Add(LogisticRegression.CrossEntropy(probabilities, targets));
    }

    return new LogisticModel(features, classes, weights, bias, losses);
}
```

F# 版は、重みを書き換えずに `List.fold` で「1 回分の更新」を畳み込み、毎回新しい配列を作りました。C# 版は `for` で配列をその場で書き換えます。どちらでも結果は同じですが、書き換えるぶん C# 版は「いまの重み」と「次の重み」を取り違える余地があります。ここでは、勾配を計算し終えてから更新するのではなく、`f`・`k` ごとに即座に更新している点に注意してください。`gradient` は `weights` ではなく `errors`（このエポックの初めに固定した値）から計算しているので、更新の順番は結果に影響しません。

品種の並びは `Order(StringComparer.Ordinal)` で決めます。C# の `Order()` は既定でカルチャに依存する比較を使うので、F# の `List.sort`（序数比較）と同じにするために比較子を明示します。文字列の比較でカルチャを指定しないと、.NET アナライザーの CA1304・CA1311 が指摘する場面もあり、「どの規則で並べるか」を書く習慣が身につきます。

予測は、確率を計算せずにスコアが最大の品種を選びます。ソフトマックスは大小関係を変えないので、確率に直す必要がありません。

```csharp
/// <summary>スコアが最大の品種を予測する（ソフトマックスは大小関係を変えないので確率は計算しない）。</summary>
public IReadOnlyList<string> Predict(IReadOnlyList<Features> x)
{
    ArgumentNullException.ThrowIfNull(x);
    return [.. x.Select(row =>
    {
        var scores = Scores(this.weights, this.bias, [.. this.Features.Select(row.Value)]);
        return this.Classes[Array.IndexOf(scores, scores.Max())];
    })];
}
```

`this.Features.Select(row.Value)` は、学習したときの列の順で値を並べ直します。予測に渡す `Features` の列の順が違っても、学習した重みと対応が崩れません。

### 損失の記録

学習が進んでいるかを確かめるため、繰り返しごとの **交差エントロピー**（正解の品種の確率の対数の平均にマイナスを付けたもの）を記録します。

```csharp
[Fact(DisplayName = "学習を繰り返すと損失が小さくなる")]
public void LossDecreases()
{
    var model = LogisticModel.Learn(
        LogisticSettings.Default with { Epochs = 100 },
        ByPetalWidth(0.1, 0.2, 0.8, 0.9),
        ["setosa", "setosa", "virginica", "virginica"]);

    Assert.Equal(100, model.Losses.Count);
    Assert.True(model.Losses[^1] < model.Losses[0]);
}
```

`model.Losses[^1]` は末尾の要素です。F# 版は損失をリストの先頭に積んでから `List.rev` で戻しましたが、C# 版は `List<double>.Add` で末尾に足すので、そのまま順番に並びます。

```csharp
/// <summary>確率が 0 のときに log 0 が負の無限大にならないように足す小さな値。</summary>
private const double Epsilon = 1e-12;

/// <summary>交差エントロピー。正解の品種の確率の対数の平均にマイナスを付けたもの。</summary>
public static double CrossEntropy(IReadOnlyList<double[]> probabilities, IReadOnlyList<int> targets)
{
    ArgumentNullException.ThrowIfNull(probabilities);
    ArgumentNullException.ThrowIfNull(targets);
    return -probabilities.Select((p, i) => Math.Log(p[targets[i]] + Epsilon)).Average();
}
```

### 分類器にする

学習と予測がそろったので、ロジスティック回帰そのものは「設定を持ち、訓練データから予測する関数を作る」だけの薄いクラスになります。

```csharp
/// <summary>ソフトマックスのロジスティック回帰。学習したモデルで予測する分類器。</summary>
public sealed class LogisticRegression(LogisticSettings settings) : IClassifier
{
    public Func<IReadOnlyList<Features>, IReadOnlyList<string>> Fit(
        IReadOnlyList<Features> x, IReadOnlyList<string> t) =>
        LogisticModel.Learn(settings, x, t).Predict;
}
```

`LogisticRegression(LogisticSettings settings)` はプライマリコンストラクターです。フィールドを宣言しなくても、`settings` をメソッドの本体から使えます。`IClassifier` は 10.7 節で作るインターフェースです。

## 10.5 ランダムフォレスト

### 仕組み

ランダムフォレストは、次の 2 つの「ばらつき」を持たせた決定木をたくさん作り、予測を多数決で決めます。

1. **ブートストラップ標本**: 訓練データから、同じ件数を重複を許して選び直したデータで木を学習する（バギング）
2. **特徴量の部分集合**: 木ごとに使う特徴量を一部だけに絞る

1 本 1 本の決定木は訓練データの細部を覚えて過学習しがちですが、違うデータ・違う特徴量で学習した木の多数決を取ると、個々の木の癖が打ち消し合います。

この章では、第 3 章の `DecisionTree` を **変更せずに** 再利用します。そのため、特徴量の絞り込みは「木ごと」に行います。ML.NET の FastForest は、分割のたびに特徴量を選び直すなど、仕組みが少し異なります。

### 多数決・ブートストラップ標本・特徴量の選択

乱数を使う処理は、第 2 章の `Shuffle` と同じく **シードを受け取るメソッド** にします。

```csharp
[Fact(DisplayName = "元のデータと同じ件数の行番号を重複を許して選ぶ")]
public void BootstrapSample()
{
    var rows = RandomForest.BootstrapSample(0, 100);

    Assert.Equal(100, rows.Count);
    Assert.All(rows, row => Assert.InRange(row, 0, 99));
    Assert.True(rows.Distinct().Count() < 100);
}

[Fact(DisplayName = "同じシードなら同じ行を選ぶ")]
public void SameSeedSameRows()
{
    Assert.Equal(RandomForest.BootstrapSample(42, 10), RandomForest.BootstrapSample(42, 10));
}
```

重複を許して選ぶので、100 件から選び直した行番号には必ず重複が現れます（同じ 100 個の番号が 1 つも重ならない確率は、実質ゼロです）。

```csharp
/// <summary>シードを使って、0 以上 size 未満の行番号を size 個、重複を許して選ぶ。</summary>
public static IReadOnlyList<int> BootstrapSample(int seed, int size)
{
    var random = new Random(seed);
    return [.. Enumerable.Range(0, size).Select(_ => random.Next(size))];
}

/// <summary>シードで並べ替えた先頭 maxFeatures 個の特徴量を、元の列の順で返す。</summary>
public static IReadOnlyList<string> ChooseFeatures(
    int seed, int maxFeatures, IReadOnlyList<string> features)
{
    ArgumentNullException.ThrowIfNull(features);
    var chosen = Preprocessing.Shuffle(features, seed).Take(maxFeatures).ToHashSet(StringComparer.Ordinal);
    return [.. features.Where(chosen.Contains)];
}
```

`ChooseFeatures` は、第 2 章の `Shuffle`（Fisher–Yates）で並べ替えてから先頭を取り、**元の列の順に戻して** 返します。選ばれた列の集合が同じなら、並びも同じになるので、テストで比べやすくなります。

多数決は、木ごとの予測のリストを「サンプルごと」に組み替えて、第 3 章の `Majority` に渡します。

```csharp
/// <summary>木ごとの予測のリストから、サンプルごとに最も多い予測を選ぶ。</summary>
public static IReadOnlyList<string> MajorityVote(IReadOnlyList<IReadOnlyList<string>> votes)
{
    ArgumentNullException.ThrowIfNull(votes);
    return [.. Enumerable.Range(0, votes[0].Count)
        .Select(i => DecisionTrees.Majority([.. votes.Select(vote => vote[i])]))];
}
```

F# 版は `List.transpose` 1 つで行と列を入れ替えられましたが、C# の LINQ には転置がないので、添字で組み替えます。同数のときに先に現れたラベルを選ぶ規則は、第 3 章の `Majority` がそのまま持っています。

### 森を作る

木ごとに「ブートストラップ標本のシード」と「特徴量を選ぶシード」を、森のシードから作ります。

```csharp
/// <summary>森のシードから、木ごとに「ブートストラップ標本のシード」と「特徴量を選ぶシード」の組を作る。</summary>
private static IReadOnlyList<(int RowSeed, int FeatureSeed)> TreeSeeds(int seed, int count)
{
    var random = new Random(seed);
    return [.. Enumerable.Range(0, count).Select(_ => (random.Next(), random.Next()))];
}
```

1 本分の学習結果は、使った列・行番号・木を持つ record にします。

```csharp
/// <summary>1 本分の学習結果。使った列と、ブートストラップ標本の行番号と、学習した木。</summary>
public sealed record FittedTree(IReadOnlyList<string> Columns, IReadOnlyList<int> Rows, Tree Tree);
```

行番号を覚えておくのは、10.6 節で特徴量の重要度を求めるときに「その木が実際に学習したデータ」をもう一度作り直すためです。

```csharp
/// <summary>行番号と列名で、1 本分の学習データを取り出す。同じ行番号が重複していれば、その回数だけ行が並ぶ。</summary>
public static (IReadOnlyList<Features> X, IReadOnlyList<string> T) SampleOf(
    IReadOnlyList<int> rows,
    IReadOnlyList<string> columns,
    IReadOnlyList<Features> x,
    IReadOnlyList<string> t)
{
    return (
        [.. rows.Select(row => new Features(columns, [.. columns.Select(x[row].Value)]))],
        [.. rows.Select(row => t[row])]);
}

/// <summary>ブートストラップ標本と特徴量の部分集合で、第 3 章の決定木を NEstimators 本学習する。</summary>
public static IReadOnlyList<FittedTree> Learn(
    Settings settings, IReadOnlyList<Features> x, IReadOnlyList<string> t)
{
    ArgumentNullException.ThrowIfNull(settings);
    ArgumentNullException.ThrowIfNull(x);
    var features = x[0].Columns;
    return [.. TreeSeeds(settings.Seed, settings.NEstimators).Select(seeds =>
    {
        var rows = BootstrapSample(seeds.RowSeed, x.Count);
        var columns = ChooseFeatures(seeds.FeatureSeed, settings.MaxFeatures, features);
        var (sampleX, sampleT) = SampleOf(rows, columns, x, t);
        var tree = settings.MaxDepth is int maxDepth
            ? DecisionTree.WithMaxDepth(maxDepth)
            : DecisionTree.Unlimited();
        return new FittedTree(columns, rows, tree.Fit(sampleX, sampleT).Tree!);
    })];
}

/// <summary>木ごとに予測して多数決する。第 3 章の木は分割に使った列だけを見るので、列を絞らずに渡せる。</summary>
public static IReadOnlyList<string> Predict(IReadOnlyList<FittedTree> forest, IReadOnlyList<Features> x)
{
    ArgumentNullException.ThrowIfNull(forest);
    ArgumentNullException.ThrowIfNull(x);
    return MajorityVote(
        [.. forest.Select(fitted =>
            (IReadOnlyList<string>)[.. x.Select(features => DecisionTrees.PredictOne(fitted.Tree, features))])]);
}
```

深さの上限は `int?`（null 許容値型）で表し、`settings.MaxDepth is int maxDepth` のパターンマッチで「上限あり」と「上限なし」を分けます。F# 版の `int option` と `match` に当たる書き方です。

設定も record にして、既定値を `Default` に置きます。

```csharp
/// <param name="NEstimators">木の本数。</param>
/// <param name="MaxFeatures">木 1 本が使う特徴量の数。</param>
/// <param name="MaxDepth">木の深さの上限。null なら上限なし。</param>
/// <param name="Seed">乱数のシード。</param>
public sealed record Settings(int NEstimators, int MaxFeatures, int? MaxDepth, int Seed)
{
    /// <summary>既定の設定（10 本・特徴量 2 つ・深さの上限なし・シード 0）。</summary>
    public static Settings Default { get; } = new(10, 2, null, 0);
}
```

### record の比較に注意する

「同じシードなら同じ森になる」ことをテストにします。F# 版はリストとレコードが値で比べられるので、森をまるごと 1 行で比べられました。C# で素直に書くと、次のようになります。

```csharp
Assert.Equal(first, second);
```

これは失敗します。

```text
失敗 同じシードなら同じ森になる (580ms)
  Assert.Equal() Failure: Collections differ
             ↓ (pos 0)
  Expected: [FittedTree { Columns = <>z__ReadOnlyList`1[System.String],
             Rows = <>z__ReadOnlyList`1[System.Int32],
             Tree = Node { Split = Split { Feature = 花弁幅, Threshold = 0.49, Impurity = 0 },
             Left = Leaf { Label = setosa }, Right = Leaf { Label = virginica } } }, …]
  Actual:   [FittedTree { Columns = <>z__ReadOnlyList`1[System.String], …（表示は同じ）]
             ↑ (pos 0)
```

表示された中身は同じなのに等しくない、という分かりにくい失敗です。record の既定の `Equals` は、成分を `object.Equals` で比べます。`Tree` は record なので中身で比べられますが、`Columns` と `Rows` はリストなので **参照で** 比べられ、別々に作ったリストは等しくなりません。第 2 章で `Features` を record ではなくクラスにして `Equals` を自分で書いたのと同じ落とし穴です。

ここでは `FittedTree` に `Equals` を書き足すのではなく、テストの側で成分ごとに比べます。

```csharp
[Fact(DisplayName = "同じシードなら同じ森になる")]
public void SameSeedSameForest()
{
    var settings = RandomForest.Settings.Default with { NEstimators = 5, MaxFeatures = 1, Seed = 7 };

    var first = RandomForest.Learn(settings, TwoSpeciesX, TwoSpeciesT);
    var second = RandomForest.Learn(settings, TwoSpeciesX, TwoSpeciesT);

    // record の既定の比較はリストを参照で比べるので、成分ごとに比べる（第 2 章の Features と同じ落とし穴）
    Assert.Equal(first.Select(fitted => fitted.Columns), second.Select(fitted => fitted.Columns));
    Assert.Equal(first.Select(fitted => fitted.Rows), second.Select(fitted => fitted.Rows));
    Assert.Equal(first.Select(fitted => fitted.Tree), second.Select(fitted => fitted.Tree));
}
```

`Assert.Equal` はコレクションを要素ごとに比べるので、リストの中身どうしはこれで比べられます。`Tree` は第 3 章で抽象レコードと sealed な派生にしてあるため、木の形まで値で比べられます。

## 10.6 特徴量の重要度

### 計算方法

決定木の各節で、

```text
減少量 = その節に届いた件数 × (その節のジニ不純度 − 分割後のジニ不純度)
```

を求め、分割に使った特徴量ごとに合計し、全体が 1 になるように割合にします。第 3 章の `Split.Impurity` は「分割後のジニ不純度（件数で重み付けした平均）」なので、そのまま使えます。

ただし、第 3 章の `Node` は、その節に届いた件数を持っていません。そこで、学習に使ったデータをもう一度木に流して、節ごとに件数とジニ不純度を求めます。重要度は手で計算した値でテストします。

### 決定木 1 本の重要度

```csharp
[Fact(DisplayName = "1 回だけ分割する木は分割に使った特徴量の重要度が 1")]
public void SingleSplitHasImportanceOne()
{
    var x = RowsOf(("がく片幅", [0.3, 0.5, 0.4, 0.6]), ("花弁幅", [0.1, 0.2, 0.8, 0.9]));
    string[] t = ["setosa", "setosa", "virginica", "virginica"];

    var tree = DecisionTree.Unlimited().Fit(x, t).Tree!;

    var importances = FeatureImportance.TreeImportances(tree, x, t);

    Assert.Equal(0.0, importances["がく片幅"]);
    Assert.Equal(1.0, importances["花弁幅"]);
}

[Fact(DisplayName = "分割で減った不純度を件数で重み付けして割合にする")]
public void WeightsImpurityDecreaseByCount()
{
    var x = RowsOf(("花弁長さ", [0.1, 0.2, 0.3, 0.8, 0.7, 0.9]), ("花弁幅", [0.1, 0.1, 0.2, 0.2, 0.9, 0.9]));
    string[] t = ["setosa", "setosa", "setosa", "versicolor", "virginica", "virginica"];

    var importances = FeatureImportance.TreeImportances(DecisionTree.Unlimited().Fit(x, t).Tree!, x, t);

    Assert.Equal(7.0 / 11.0, importances["花弁長さ"], 12);
    Assert.Equal(4.0 / 11.0, importances["花弁幅"], 12);
}
```

2 つ目のテストの値は手で計算しています。根の節は 6 件で、内訳は setosa 3・versicolor 1・virginica 2 なので、ジニ不純度は `1 − ((3/6)² + (1/6)² + (2/6)²) = 11/18` です。花弁長さ 0.5 を境に分けると、左は setosa 3 件（不純度 0）、右は versicolor 1・virginica 2（不純度 4/9）になり、分割後の不純度は `(3 × 0 + 3 × 4/9) / 6 = 2/9`。減少量は `6 × (11/18 − 2/9) = 7/3` です。右の 3 件をさらに花弁幅で分けると `3 × (4/9 − 0) = 4/3` が減ります。合計 `7/3 + 4/3 = 11/3` に対する割合は、花弁長さが `7/11`、花弁幅が `4/11` です。テストに分数のまま書いておくと、あとから読んだときに「どの数をどう割ったのか」が残ります。

実装は、木をたどりながら特徴量ごとの減少量を辞書に積み上げます。

```csharp
/// <summary>決定木 1 本の特徴量の重要度。</summary>
public static IReadOnlyDictionary<string, double> TreeImportances(
    Tree tree, IReadOnlyList<Features> x, IReadOnlyList<string> t)
{
    ArgumentNullException.ThrowIfNull(x);
    var decreases = new Dictionary<string, double>(StringComparer.Ordinal);
    Collect(tree, x, t, decreases);
    return Normalize(x[0].Columns.ToDictionary(
        column => column, decreases.GetValueOrDefault, StringComparer.Ordinal));
}

/// <summary>
/// 学習に使ったデータをもう一度木に流して、
/// 節ごとに「分割に使った特徴量と、件数で重み付けした不純度の減少量」を集める。
/// </summary>
private static void Collect(
    Tree tree, IReadOnlyList<Features> x, IReadOnlyList<string> t, Dictionary<string, double> decreases)
{
    if (tree is not Node node)
    {
        return;
    }

    var split = node.Split;
    var left = Enumerable.Range(0, x.Count).Where(i => x[i].Value(split.Feature) <= split.Threshold).ToList();
    var right = Enumerable.Range(0, x.Count).Where(i => x[i].Value(split.Feature) > split.Threshold).ToList();
    decreases[split.Feature] =
        decreases.GetValueOrDefault(split.Feature) + (t.Count * (DecisionTrees.Gini(t) - split.Impurity));
    Collect(node.Left, Pick(x, left), Pick(t, left), decreases);
    Collect(node.Right, Pick(x, right), Pick(t, right), decreases);
}
```

F# 版は、節ごとの `(特徴量, 減少量)` のリストを連結してから特徴量ごとに合計しました。C# 版は、辞書を引数で引き回して足し込みます。`if (tree is not Node node) return;` は、葉のときに何もしないという意味で、型のパターンマッチと早期 return を組み合わせた C# らしい書き方です。

割合にする処理は、合計が 0 のとき（分割が 1 つも無い木）にゼロ除算にならないようにします。

```csharp
/// <summary>合計が 1 になるように割合にする。合計が 0 ならそのまま返す。</summary>
private static Dictionary<string, double> Normalize(Dictionary<string, double> totals)
{
    var total = totals.Values.Sum();
    return total == 0.0
        ? totals
        : totals.ToDictionary(pair => pair.Key, pair => pair.Value / total, StringComparer.Ordinal);
}
```

`Normalize` の戻り値を `IReadOnlyDictionary<string, double>` で書いたところ、.NET アナライザーの CA1859 が「private なメソッドなので具体的な型を返したほうが速い」と指摘しました。`TreatWarningsAsErrors` によってビルドが止まるので、指摘どおり `Dictionary<string, double>` に直しています。

### ランダムフォレストの重要度

森の重要度は、木ごとの重要度を平均してから、もう一度割合にします。木ごとの重要度は「その木が学習したデータ」で計算しなければならないので、`FittedTree` が覚えている行番号と列名から標本を作り直します。

```csharp
/// <summary>
/// ランダムフォレストの特徴量の重要度。
/// 木ごとの重要度（学習に使ったブートストラップ標本で計算）を平均し、割合にする。
/// </summary>
public static IReadOnlyDictionary<string, double> ForestImportances(
    IReadOnlyList<FittedTree> forest, IReadOnlyList<Features> x, IReadOnlyList<string> t)
{
    ArgumentNullException.ThrowIfNull(forest);
    ArgumentNullException.ThrowIfNull(x);
    var perTree = forest.Select(fitted =>
    {
        var (sampleX, sampleT) = RandomForest.SampleOf(fitted.Rows, fitted.Columns, x, t);
        return TreeImportances(fitted.Tree, sampleX, sampleT);
    }).ToList();

    return Normalize(x[0].Columns.ToDictionary(
        column => column,
        column => perTree.Average(importances => importances.GetValueOrDefault(column)),
        StringComparer.Ordinal));
}
```

木が 1 本だけの森なら、その木の重要度と一致するはずです。

```csharp
[Fact(DisplayName = "木が 1 本なら学習に使った行でのその木の重要度と一致する")]
public void SingleTreeForest()
{
    var x = RowsOf(
        ("がく片幅", [0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4]),
        ("花弁幅", [0.1, 0.12, 0.14, 0.16, 0.8, 0.82, 0.84, 0.86]));
    string[] t = [.. Enumerable.Repeat("setosa", 4), .. Enumerable.Repeat("virginica", 4)];

    var forest = RandomForest.Learn(
        RandomForest.Settings.Default with { NEstimators = 1, MaxFeatures = 2 }, x, t);
    var fitted = Assert.Single(forest);

    var expected = FeatureImportance.TreeImportances(
        fitted.Tree,
        [.. fitted.Rows.Select(row => x[row])],
        [.. fitted.Rows.Select(row => t[row])]);

    Assert.Equal(expected, FeatureImportance.ForestImportances(forest, x, t));
}
```

`Assert.Single` は「要素がちょうど 1 つあること」を確かめ、その要素を返します。辞書どうしの `Assert.Equal` は、キーと値の組を比べるので、そのまま使えます。

`SampleOf` を `RandomForest` に置いて `FeatureImportance` からも呼んでいるのは、同じ処理が 2 か所に現れる前に切り出したからです。

## 10.7 モデル共通の型

### 分類器をインターフェースで表す

決定木・ロジスティック回帰・ランダムフォレストは、どれも「訓練データで学習し、新しいデータのラベルを予測する」ものです。F# 版はこれを **関数の型** で表しましたが、C# 版は **インターフェース** で表します。

```csharp
/// <summary>
/// 分類器。訓練データ（特徴量と正解ラベル）を受け取り、予測する関数を返す。
/// F# 版は関数の型（Classifier）で表したが、C# ではインターフェースで表す。
/// </summary>
public interface IClassifier
{
    /// <summary>訓練データから学習し、予測する関数を返す。</summary>
    Func<IReadOnlyList<Features>, IReadOnlyList<string>> Fit(
        IReadOnlyList<Features> x, IReadOnlyList<string> t);
}
```

`Fit` が「モデル」ではなく **予測する関数** を返すところは F# 版と同じです。モデルの型はモデルごとに違うので、インターフェースの型引数を増やさずに済みます。

評価は、どの分類器にも共通なので、拡張メソッドにします。

```csharp
/// <summary>どの分類器にも共通の振る舞い。F# 版のモジュールの関数を、C# では拡張メソッドで表す。</summary>
public static class ClassifierExtensions
{
    /// <summary>訓練データで学習させてから、訓練データとテストデータの正解率を求める。</summary>
    public static Score Evaluate(this IClassifier classifier, TrainTestSplit<Features, string> split)
    {
        ArgumentNullException.ThrowIfNull(classifier);
        ArgumentNullException.ThrowIfNull(split);
        var predict = classifier.Fit(split.XTrain, split.TTrain);
        return new Score(
            KinokoTakenoko.Accuracy(predict(split.XTrain), split.TTrain),
            KinokoTakenoko.Accuracy(predict(split.XTest), split.TTest));
    }
}
```

C# 8 以降はインターフェースに既定の実装（default interface member）を書けるので、`Evaluate` をインターフェース側に置くこともできます。ただしその場合、`IClassifier` 型の変数からしか呼べません。実装クラスの変数から `model.Evaluate(split)` と書けないと不便なので、拡張メソッドを選びました。

正解率の組は record にします。

```csharp
/// <summary>訓練データとテストデータの正解率。</summary>
public sealed record Score(double Train, double Test);
```

まず、何を学習しても setosa と予測する分類器で、`Evaluate` の振る舞いを決めます。

```csharp
[Fact(DisplayName = "学習させてから訓練データとテストデータの正解率を求める")]
public void EvaluatesTrainAndTest()
{
    Assert.Equal(new Score(0.5, 1.0), new AlwaysSetosa().Evaluate(SmallSplit));
}

/// <summary>何を学習しても setosa と予測する分類器。</summary>
private sealed class AlwaysSetosa : IClassifier
{
    public Func<IReadOnlyList<Features>, IReadOnlyList<string>> Fit(
        IReadOnlyList<Features> x, IReadOnlyList<string> t) =>
        newX => [.. newX.Select(_ => "setosa")];
}
```

`Score` は record なので、訓練データとテストデータの正解率をまとめて 1 行で比べられます。

### 3 つのモデルを同じインターフェースで評価する

第 3 章の `DecisionTree` は `Fit(x, t)` が自分自身を返し、`Predict(x)` が別のメソッドという形です。`IClassifier` とは形が違いますが、**第 3 章のコードは変更しません**。代わりにアダプターを書きます。

```csharp
/// <summary>
/// 第 3 章の決定木を分類器として使うためのアダプター。
/// 第 3 章のコードは変更せず、Fit・Predict の呼び出し方だけをここで合わせる。
/// </summary>
public sealed class DecisionTreeClassifier : IClassifier
{
    private readonly Func<DecisionTree> create;

    private DecisionTreeClassifier(Func<DecisionTree> create) => this.create = create;

    /// <summary>深さを制限しない決定木の分類器。</summary>
    public static DecisionTreeClassifier Unlimited() => new(DecisionTree.Unlimited);

    /// <summary>深さの上限を指定した決定木の分類器。</summary>
    public static DecisionTreeClassifier WithMaxDepth(int maxDepth) =>
        new(() => DecisionTree.WithMaxDepth(maxDepth));

    public Func<IReadOnlyList<Features>, IReadOnlyList<string>> Fit(
        IReadOnlyList<Features> x, IReadOnlyList<string> t) =>
        this.create().Fit(x, t).Predict;
}
```

第 3 章の `DecisionTree` は `Fit` で自分の状態（学習した木）を書き換えるので、`Fit` のたびに **新しいインスタンスを作る** 必要があります。そのために、インスタンスではなく「作る関数」を持っています。`this.create().Fit(x, t).Predict` の `.Predict` はメソッドグループで、そのまま `Func<...>` になります。

F# 版は、第 3 章の `DecisionTree.fit`・`DecisionTree.predict` が引数を取る関数だったので、`ofModel` に渡すだけで済みました。C# 版は、第 3 章の設計がクラスとメソッドだったぶん、アダプターのクラスが 1 つ増えます。これは「インターフェースで共通化する」と決めたときに引き受ける手間です。

```csharp
[Fact(DisplayName = "第 3 章の決定木と自作のモデルを同じインターフェースで評価できる")]
public void SameInterfaceForEveryModel()
{
    IClassifier[] classifiers =
    [
        DecisionTreeClassifier.WithMaxDepth(1),
        new LogisticRegression(LogisticSettings.Default),
        new RandomForest(RandomForest.Settings.Default with { NEstimators = 5, MaxFeatures = 1 }),
    ];

    var scores = classifiers.Select(classifier => classifier.Evaluate(SmallSplit));

    Assert.Equal(Enumerable.Repeat(new Score(1.0, 1.0), 3), scores);
}
```

3 つのモデルが `IClassifier[]` に同居し、同じ `Evaluate` で評価できました。

## 10.8 ML.NET の学習器を同じ型で使う

### ML.NET の学習器を IClassifier にする

ML.NET の学習器も `IClassifier` にしてしまえば、自作のモデルと並べて評価できます。第 3 章で書いた `MlRow`・`MlPrediction`（ML.NET に渡す行と、返る予測）はそのまま使います。

```csharp
/// <summary>ML.NET の多クラス分類の学習器を、第 10 章の分類器として使えるようにする。</summary>
public sealed class MlNetClassifier : IClassifier
{
    private readonly Func<MLContext, IEstimator<ITransformer>> trainer;

    private MlNetClassifier(Func<MLContext, IEstimator<ITransformer>> trainer) => this.trainer = trainer;

    /// <summary>ML.NET のソフトマックスのロジスティック回帰（L-BFGS で最適化する）。L1・L2 正則化の強さを指定する。</summary>
    public static MlNetClassifier LbfgsMaximumEntropy(float l1Regularization, float l2Regularization) =>
        new(context => context.MulticlassClassification.Trainers.LbfgsMaximumEntropy(
            l1Regularization: l1Regularization, l2Regularization: l2Regularization));

    /// <summary>ML.NET のランダムフォレスト（FastForest）。2 クラス用なので、OneVersusAll で多クラスにする。</summary>
    public static MlNetClassifier FastForest(int numberOfTrees, int minimumExampleCountPerLeaf) =>
        new(context => context.MulticlassClassification.Trainers.OneVersusAll(
            context.BinaryClassification.Trainers.FastForest(
                numberOfTrees: numberOfTrees, minimumExampleCountPerLeaf: minimumExampleCountPerLeaf)));

    public Func<IReadOnlyList<Features>, IReadOnlyList<string>> Fit(
        IReadOnlyList<Features> x, IReadOnlyList<string> t)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        var context = new MLContext(seed: 0);
        var schema = SchemaFor(x[0].Columns.Count);
        var pipeline = context.Transforms.Conversion.MapValueToKey("Label")
            .Append(this.trainer(context))
            .Append(context.Transforms.Conversion.MapKeyToValue("PredictedLabel"));
        var model = pipeline.Fit(context.Data.LoadFromEnumerable(ToRows(x, i => t[i]), schema));

        return newX =>
        {
            var data = context.Data.LoadFromEnumerable(ToRows(newX, _ => string.Empty), schema);
            return [.. context.Data
                .CreateEnumerable<MlPrediction>(model.Transform(data), reuseRowObject: false)
                .Select(prediction => prediction.PredictedLabel)];
        };
    }
}
```

F# 版では、学習器の型を `:> IEstimator<_>` で明示的にアップキャストする必要がありました。C# では `IEstimator<out TTransformer>` の共変性が効くので、`LbfgsMaximumEntropy(...)` が返す具体的な型がそのまま `IEstimator<ITransformer>` として渡ります。ML.NET が C# 向けに設計された API であることが、こうしたところに出ます。

パイプラインの組み立ても、F# 版が `EstimatorChain()` から始めたのに対し、C# 版は最初の変換に `.Append` を続けるだけで書けます。

`Features` 列のベクトルの長さは実行時に決まるので、第 3 章と同じく `SchemaDefinition` で指定します。

```csharp
/// <summary>Features 列のベクトルの長さは実行時に決まるので、スキーマで指定する（第 3 章と同じ）。</summary>
private static SchemaDefinition SchemaFor(int featureCount)
{
    var schema = SchemaDefinition.Create(typeof(MlRow));
    schema["Features"].ColumnType = new VectorDataViewType(NumberDataViewType.Single, featureCount);
    return schema;
}
```

ML.NET も `IClassifier` になったので、自作のモデルとまったく同じ書き方で評価できます。

```csharp
[Fact(DisplayName = "ML.NET のロジスティック回帰とランダムフォレストも同じインターフェースで評価できる")]
public void SameInterfaceForMlNet()
{
    IClassifier[] classifiers =
    [
        MlNetClassifier.LbfgsMaximumEntropy(1.0f, 1.0f),
        MlNetClassifier.FastForest(10, 1),
    ];

    var scores = classifiers.Select(classifier => classifier.Evaluate(TwoSpeciesSplit));

    Assert.Equal(Enumerable.Repeat(new Score(1.0, 1.0), 2), scores);
}
```

### ML.NET の設定と自作との違い

ML.NET の既定の設定は、自作と次の点が違います。

| 項目 | 自作 | ML.NET |
|------|------|--------|
| ロジスティック回帰の最適化 | バッチ勾配降下法（学習率 1.0）、5000 回 | L-BFGS（勾配の履歴から曲がり具合を近似する準ニュートン法） |
| ロジスティック回帰の正則化 | なし | L1・L2 正則化がどちらも 1（`l1Regularization`・`l2Regularization` の既定値） |
| ランダムフォレストの多クラス化 | 決定木そのものが多クラスを扱う | FastForest は 2 クラス用なので、品種ごとに「その品種か、それ以外か」の森を作る（OneVersusAll） |
| 決定木を分割する最小の件数 | 1 件になるまで分ける | `minimumExampleCountPerLeaf` の既定値 10（葉ごとに最低 10 件） |
| 葉の数 | 制限なし | `numberOfLeaves` の既定値 20 |

**正則化** は、重みが大きくなりすぎないように、損失に重みの大きさの罰則を足す仕組みです。訓練データへの当てはまりを少し犠牲にして、過学習を抑えます。第 12 章で詳しく扱います。

## 10.9 リファクタリング

ここまでで、TODO リストは実データでの突き合わせを残すだけになりました。

- `SampleOf` の切り出しは、10.6 節で `ForestImportances` を書く前に済ませました。同じ処理が 2 か所に現れる前に切り出したので、重複は一度も生まれていません
- `Normalize` は、アナライザー（CA1859）の指摘に従って戻り値の型を具体的な `Dictionary<string, double>` に変えました。`TreatWarningsAsErrors` を有効にしていると、こうした指摘を後回しにできません
- 第 3 章の `DecisionTree`・`DecisionTrees` には手を入れていません。形が合わない部分は `DecisionTreeClassifier` に閉じ込めました

整形と検査を実行して、指摘が無いことを確かめます。

```bash
dotnet format MachineLearning.sln --no-restore
dotnet build MachineLearning.sln --no-restore
```

`dotnet format` は、この章のコードで整形の変更を出しませんでした（ビルドの警告も 0 件です）。この記事のコードは、整形した後のものです。

## 10.10 実データで突き合わせる

### モデルを比べる

`Program.Run` で、iris のテストデータでの正解率と、ランダムフォレストの特徴量の重要度を表示します。

```csharp
// src/MachineLearning/Chapter10/Program.cs
/// <summary>表示名と分類器の組。</summary>
private static readonly (string Name, IClassifier Classifier)[] Models =
[
    ($"決定木（深さ {ShallowDepth}）", DecisionTreeClassifier.WithMaxDepth(ShallowDepth)),
    ("ロジスティック回帰", new LogisticRegression(LogisticSettings.Default)),
    ($"ランダムフォレスト（{NEstimators} 本）", new RandomForest(ForestSettings)),
    ($"ランダムフォレスト（{NEstimators} 本・深さ {ShallowDepth}）",
        new RandomForest(ForestSettings with { MaxDepth = ShallowDepth })),
    ("ML.NET LbfgsMaximumEntropy",
        MlNetClassifier.LbfgsMaximumEntropy(MlNetRegularization, MlNetRegularization)),
    ($"ML.NET FastForest（{NEstimators} 本）",
        MlNetClassifier.FastForest(NEstimators, MlNetMinimumExampleCountPerLeaf)),
];

public static void Run(TextWriter output)
{
    ArgumentNullException.ThrowIfNull(output);
    var split = Preprocessing.PrepareIris(Path.Combine(DataDir.Current(), "iris.csv"), TestSize, Seed);
    output.WriteLine("モデル\t訓練データ\tテストデータ");
    foreach (var (name, classifier) in Models)
    {
        var score = classifier.Evaluate(split);
        output.WriteLine($"{name}\t{Format(score.Train)}\t{Format(score.Test)}");
    }

    var forest = RandomForest.Learn(ForestSettings, split.XTrain, split.TTrain);
    output.WriteLine();
    output.WriteLine($"ランダムフォレスト（{NEstimators} 本）の特徴量の重要度:");
    var importances = FeatureImportance.ForestImportances(forest, split.XTrain, split.TTrain);
    foreach (var feature in split.XTrain[0].Columns)
    {
        output.WriteLine($"{feature}\t{Format(importances[feature])}");
    }
}
```

6 つのモデルが同じ `(string, IClassifier)` の組に収まり、`foreach` 1 つで評価できています。重要度は辞書なので、表示の順は `Columns`（CSV の列の順）で決めます。

```bash
dotnet run --project src/MachineLearning -- chapter10
```

```text
モデル	訓練データ	テストデータ
決定木（深さ 2）	0.9619	0.8889
ロジスティック回帰	0.9524	0.8667
ランダムフォレスト（100 本）	1.0000	0.8889
ランダムフォレスト（100 本・深さ 2）	0.9524	0.8889
ML.NET LbfgsMaximumEntropy	0.9238	0.8444
ML.NET FastForest（100 本）	0.9619	0.8889

ランダムフォレスト（100 本）の特徴量の重要度:
がく片長さ	0.2008
がく片幅	0.1075
花弁長さ	0.2243
花弁幅	0.4674
```

花弁幅の重要度が最も高く、次いで花弁長さとがく片長さが並びます。第 3 章で作った深さ 2 の決定木が花弁幅だけで分けていたことと合っています。

### F# 版との一致

分割が F# 版と一致するので、正解率も一致するかを確かめました。結果は **一部だけ一致** です。

| モデル | C# 版（訓練／テスト） | F# 版（訓練／テスト） | 一致 |
|--------|--------------------|--------------------|------|
| 決定木（深さ 2） | 0.9619 / 0.8889 | 0.9619 / 0.8889 | 一致 |
| ロジスティック回帰 | 0.9524 / 0.8667 | 0.9524 / 0.8667 | 一致 |
| ランダムフォレスト（100 本） | 1.0000 / 0.8889 | 1.0000 / 0.8889 | 一致 |
| ランダムフォレスト（100 本・深さ 2） | 0.9524 / 0.8889 | 0.9619 / 0.8889 | 訓練が違う |
| ML.NET LbfgsMaximumEntropy | 0.9238 / 0.8444 | 0.9238 / 0.8444 | 一致 |
| ML.NET FastForest（100 本） | 0.9619 / 0.8889 | 0.9714 / 0.9111 | 違う |

理由は **特徴量の並び順** です。F# 版は特徴量を `Map<string, float>` で持つので、列は辞書順（がく片幅・がく片長さ・花弁幅・花弁長さ）に並びます。C# 版の `Features` は CSV の列の順（がく片長さ・がく片幅・花弁長さ・花弁幅）を保ちます。分割される行は同じでも、

- `ChooseFeatures` は並びをシャッフルして先頭を取るので、木ごとに選ばれる特徴量の組が変わる
- ML.NET に渡すベクトルの成分の順が変わるので、FastForest が分割のたびに特徴量を選ぶときの選ばれ方も変わる

という形で結果がずれます。並びに依存しないロジスティック回帰（自作・ML.NET とも）と、列を絞らない単独の決定木は、F# 版と完全に一致しました。特徴量の重要度も、F# 版（がく片幅 0.0982・がく片長さ 0.2055・花弁幅 0.4929・花弁長さ 0.2034）と C# 版で数値が少し違いますが、花弁幅がおよそ半分を占め、がく片幅が最も小さいという順位は同じです。

同じ乱数・同じ分割でも、**特徴量をどの順で持つか** が結果を変える、という事実がここで見えます。乱数のシードをそろえただけでは再現しません。

### 実データのテスト

記事に載せた数値は、テストで固定します。

```csharp
[Fact(DisplayName = "ロジスティック回帰はテストデータの 45 件中 39 件を正しく分類する")]
public void LogisticRegressionAccuracy()
{
    this.RequireData();

    var score = new LogisticRegression(LogisticSettings.Default).Evaluate(this.IrisSplit());

    Assert.Equal(39.0 / 45, score.Test, 12);
}

[Fact(DisplayName = "ランダムフォレストは訓練データを分け切りテストデータの 45 件中 40 件を正しく分類する")]
public void RandomForestAccuracy()
{
    this.RequireData();

    var score = new RandomForest(RandomForest.Settings.Default with { NEstimators = 100 })
        .Evaluate(this.IrisSplit());

    Assert.Equal(1.0, score.Train);
    Assert.Equal(40.0 / 45, score.Test, 12);
}

private void RequireData() =>
    Assert.SkipUnless(File.Exists(this.csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");
```

正解率を `39.0 / 45` のように分数で書くと、「45 件中 39 件」という意味がそのまま残ります。表示のテストも、第 3 章と同じく `Program.Run` の出力をまるごと比べます（完成したテストファイルにあります）。

### ML.NET と突き合わせる

**ロジスティック回帰**: ML.NET の既定（正則化あり）は、訓練データ 0.9238・テストデータ 0.8444 で、自作より低くなりました。正則化の強さだけを変えて実測しました。

| L1 正則化 | L2 正則化 | 訓練データ | テストデータ |
|----------|----------|-----------|-------------|
| 1（既定） | 1（既定） | 0.9238 | 0.8444 |
| 0 | 1 | 0.9333 | 0.8222 |
| 1 | 0 | 0.9429 | 0.8444 |
| 0 | 0.1 | 0.9333 | 0.8444 |
| 0 | 0.01 | 0.9524 | 0.8444 |
| 0 | 0 | 0.9524 | 0.8667 |

正則化を 0 にすると、自作（5000 回）と同じ訓練 0.9524・テスト 0.8667 になりました。この組み合わせはテストで固定しています。

```csharp
[Fact(DisplayName = "ML.NET のロジスティック回帰は正則化を 0 にすると自作と同じ正解率になる")]
public void MlNetMatchesOwnLogisticRegression()
{
    this.RequireData();

    Assert.Equal(
        new LogisticRegression(LogisticSettings.Default).Evaluate(this.IrisSplit()),
        MlNetClassifier.LbfgsMaximumEntropy(0.0f, 0.0f).Evaluate(this.IrisSplit()));
}
```

最適化の方法（L-BFGS と勾配降下法）が違っても、同じ損失を最小にしているので正解率がそろいます。この表は F# 版とまったく同じ数値になりました（[ADR 004](../../../adr/004-fsharp-ml-libraries.md) の「SDCA・L-BFGS の正則化の既定値」の観察が、C# でもそのまま当てはまります）。

自作のほうも繰り返し回数を変えて確かめました。

| 繰り返し回数 | 訓練データ | テストデータ |
|-------------|-----------|-------------|
| 100 | 0.9143 | 0.8667 |
| 1000 | 0.9429 | 0.8444 |
| 5000（既定） | 0.9524 | 0.8667 |
| 20000 | 0.9524 | 0.8667 |

5000 回と 20000 回で正解率が変わらないので、既定の繰り返し回数は Python 版と同じ 5000 回のままにしています。こちらも F# 版と同じ数値です。

**ランダムフォレスト**: ML.NET の FastForest は、既定の設定（葉ごとに最低 10 件）では訓練データ 0.9619・テストデータ 0.8889 で、訓練データを分け切っていません。値を変えて確かめました。

| 葉ごとの最小件数 | 訓練データ | テストデータ |
|----------------|-----------|-------------|
| 1 | 1.0000 | 0.9111 |
| 2 | 0.9905 | 0.9111 |
| 5 | 0.9714 | 0.9111 |
| 10（既定） | 0.9619 | 0.8889 |

1 にすると訓練データを分け切り、テストデータでも 0.9111 と自作（0.8889）より 1 件多く当てました。葉ごとに最低 10 件を求める既定の設定が、1 本 1 本の木の深さを抑えていたのです。

```csharp
[Fact(DisplayName = "ML.NET の FastForest は葉の最小件数を 1 にすると訓練データを分け切る")]
public void MlNetFastForestFitsTrainingData()
{
    this.RequireData();

    var score = MlNetClassifier.FastForest(100, 1).Evaluate(this.IrisSplit());

    Assert.Equal(1.0, score.Train);
    Assert.Equal(41.0 / 45, score.Test, 12);
}
```

葉の最小件数を 1 にしたときの 41/45 は F# 版と同じですが、既定（10）のときは F# 版（0.9714 / 0.9111）と違う値になりました。前述のとおり、ML.NET に渡す特徴量の順が F# 版と違うためです。

自作の森も、本数を変えて確かめました。

| 木の本数 | 訓練データ | テストデータ |
|---------|-----------|-------------|
| 1 | 0.8476 | 0.7333 |
| 5 | 0.9524 | 0.7778 |
| 10（既定） | 0.9810 | 0.8667 |
| 25 | 1.0000 | 0.8444 |
| 50 | 1.0000 | 0.8889 |
| 100 | 1.0000 | 0.8889 |

木が 1 本だけだと、特徴量を 2 つに絞ったぶん単独の決定木より弱くなります（テストデータ 0.7333）。本数を増やすと多数決が効いて上がり、50 本を超えると頭打ちになりました。

同じ「ランダムフォレスト」という名前でも、多クラスの扱い方（1 つの森か、品種ごとの森か）や、分割を止める条件、特徴量の並び順が違えば、正解率は変わります。ライブラリの結果と比べるときは、名前ではなく設定をそろえて比べます。

テストの実行結果です。

```bash
dotnet test
```

第 10 章のテストは 27 件すべて通ります。データが無い環境（`ML_DATA_DIR=/nonexistent`）では、実データのテスト 5 件がスキップされ、残りの 22 件が通ります。

## 10.11 可視化について

C# 版では Notebook と可視化の節を設けていません。損失の推移・モデルごとの正解率・特徴量の重要度・森の大きさと正解率のグラフは、[Python 版の第 10 章](../python/10-logistic-regression-and-ensemble.md)・[Kotlin 版の第 10 章](../kotlin/10-logistic-regression-and-ensemble.md) を参照してください。

この章で作った `LogisticModel.Losses`・`FeatureImportance.ForestImportances` は、可視化に必要な値をそのまま公開しているので、.NET 上で描きたい場合は F# 版が使っている Plotly.NET から同じ値を渡せます（[F# 版の 10.11 節](../fsharp/10-logistic-regression-and-ensemble.md)）。

## 10.12 まとめ

この章では、ロジスティック回帰とランダムフォレストを自作し、共通のインターフェースで ML.NET と並べて評価しました。

1. **数値として正しい実装** — ソフトマックス関数は、定義どおりでは大きな値で `NaN` になった。C# も `double` の桁あふれを例外にせず、アナライザーも止めない。境界の値のテストで見つけ、最大値を引く方法で直した
2. **書き換える勾配降下法** — 1 回分の更新を `for` と配列の書き換えで書いた。F# 版の `List.fold` と違って「いまの重み」と「次の重み」を取り違える余地があるので、勾配はエポックの初めに固定した誤差から計算する形にした。学習の設定は record の `with` 式で変えた
3. **シードを受け取るメソッド** — ブートストラップ標本・特徴量の選択・木ごとのシードを、シードから結果が決まるメソッドにした。同じシードなら同じ森になることをテストで固定した
4. **record の比較の落とし穴** — `FittedTree` を record にしても、リストの成分は参照で比べられる。表示は同じなのに等しくない、という分かりにくい失敗になるので、テストの側で成分ごとに比べた。第 2 章の `Features` と同じ理由
5. **インターフェースによる共通化** — 分類器を「訓練データを受け取り、予測する関数を返す」インターフェースにした。第 3 章の決定木は形が違うので、第 3 章を変更せずにアダプターで合わせた。共通の評価は拡張メソッドに置いた。ML.NET の学習器は、`IEstimator<out TTransformer>` の共変性が効くのでアップキャストを書かずに渡せた（F# 版は `:> IEstimator<_>` が必要だった）
6. **設定をそろえて突き合わせる** — ML.NET のロジスティック回帰は正則化、FastForest は葉ごとの最小件数が自作と違った。設定を変えて実測し、正則化を 0 にすると自作と同じ正解率になることをテストに残した
7. **特徴量の並び順が結果を変える** — 分割は F# 版と一致するのに、ランダムフォレストと FastForest の正解率は一致しなかった。特徴量を辞書（F#）で持つか CSV の列の順（C#）で持つかが、木ごとに選ばれる特徴量を変えるため。乱数のシードだけをそろえても再現しない

iris のテストデータ 45 件では、どのモデルも数件の差しかなく、1 回の分割での比較ではどのモデルが優れているとも言い切れません。次の章では、正解率以外の評価指標と、データの分け方を変えて何度も測る交差検証を学びます。
