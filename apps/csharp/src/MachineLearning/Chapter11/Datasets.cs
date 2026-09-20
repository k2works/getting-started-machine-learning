namespace MachineLearning.Chapter11;

using MachineLearning.Chapter02;

/// <summary>
/// この章で評価に使うデータの読み込みと、簡略化した前処理。
/// 評価の仕組みに集中するため、交差検証の前にデータ全体の平均値で補完している
/// （厳密には分割ごとに訓練データの平均値で補完すべき。第 2 章で見たデータリーク）。
/// </summary>
public static class Datasets
{
    /// <summary>Survived.csv の特徴量の列。F# 版の Map のキーの順にそろえる。</summary>
    public static readonly string[] SurvivedColumns = ["Age", "Pclass", "male"];

    /// <summary>cinema.csv の特徴量の列。</summary>
    public static readonly string[] CinemaColumns = ["SNS1", "SNS2", "actor", "original"];

    /// <summary>客室クラス・年齢・男性かどうかを特徴量にし、生存（"0" か "1"）を正解ラベルにする。</summary>
    public static (IReadOnlyList<Features> X, IReadOnlyList<string> T) PrepareSurvived(string csvFile)
    {
        var table = Table.Load(csvFile);
        var values = table.Rows
            .Select(row => new double?[]
            {
                row.Number("Age"),
                row.Number("Pclass"),
                string.Equals(row.Text("Sex"), "male", StringComparison.Ordinal) ? 1.0 : 0.0,
            })
            .ToList();
        return (FillWithColumnMeans(SurvivedColumns, values), [.. table.Rows.Select(row => row.Text("Survived"))]);
    }

    /// <summary>SNS1・SNS2・actor・original を特徴量にし、興行収入を正解にする。</summary>
    public static (IReadOnlyList<Features> X, IReadOnlyList<double> T) PrepareCinema(string csvFile)
    {
        var table = Table.Load(csvFile);
        var values = table.Rows
            .Select(row => CinemaColumns.Select(row.Number).ToArray())
            .ToList();
        var sales = table.Rows
            .Select(row => row.Number(Chapter07.Cinema.Target) ?? throw new InvalidDataException("興行収入が空欄です"))
            .ToList();
        return (FillWithColumnMeans(CinemaColumns, values), sales);
    }

    /// <summary>列ごとに、欠損していない値の平均値で欠損値を補完して特徴量にする。</summary>
    private static IReadOnlyList<Features> FillWithColumnMeans(
        IReadOnlyList<string> columns, IReadOnlyList<double?[]> values)
    {
        var means = columns
            .Select((_, i) => values.Select(row => row[i]).OfType<double>().Average())
            .ToList();
        return [.. values.Select(row => new Features(columns, [.. row.Select((value, i) => value ?? means[i])]))];
    }
}
