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
