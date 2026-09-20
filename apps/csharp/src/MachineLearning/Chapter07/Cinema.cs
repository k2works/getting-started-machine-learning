namespace MachineLearning.Chapter07;

using MachineLearning.Chapter02;

/// <summary>映画の興行収入のデータ（cinema.csv）の読み込みと前処理。</summary>
public static class Cinema
{
    /// <summary>予測したい数値の列。</summary>
    public const string Target = "sales";

    /// <summary>映画を区別するための番号。特徴量には使わない。</summary>
    public const string Id = "cinema_id";

    /// <summary>SNS2 がこの値を超え、かつ興行収入が OutlierSales 未満の映画を外れ値とする。</summary>
    public const double OutlierSns2 = 1000.0;

    /// <summary>外れ値と判断する興行収入の上限。</summary>
    public const double OutlierSales = 8500.0;

    /// <summary>cinema.csv を第 2 章の Table として読み込む。</summary>
    public static Table Load(string csvFile) => Table.Load(csvFile);

    /// <summary>特徴量の列。ID と正解ラベルを除いた残り。</summary>
    public static IReadOnlyList<string> FeatureColumns(Table table)
    {
        ArgumentNullException.ThrowIfNull(table);
        return [.. table.Columns.Where(column =>
            !string.Equals(column, Id, StringComparison.Ordinal)
            && !string.Equals(column, Target, StringComparison.Ordinal))];
    }

    /// <summary>外れ値の行を取り除く。SNS2 が欠損している行は判断できないので残す。</summary>
    public static Table RemoveOutliers(Table table)
    {
        ArgumentNullException.ThrowIfNull(table);
        return table with { Rows = [.. table.Rows.Where(row => !IsOutlier(row))] };
    }

    /// <summary>読み込み、外れ値を除き、分割してから、訓練データの平均値で両方を補完する。</summary>
    public static TrainTestSplit<Features, double> Prepare(string csvFile, double testSize, int seed)
    {
        var table = RemoveOutliers(Load(csvFile));
        var columns = FeatureColumns(table);
        var sales = table.Rows.Select(row => row.Number(Target) ?? throw new InvalidDataException("興行収入が空欄です"));
        var split = Preprocessing.SplitTrainTest(table.Rows, [.. sales], testSize, seed);
        var means = Preprocessing.ColumnMeans(split.XTrain, columns);
        return new TrainTestSplit<Features, double>(
            Preprocessing.FillMissing(split.XTrain, columns, means),
            Preprocessing.FillMissing(split.XTest, columns, means),
            split.TTrain,
            split.TTest);
    }

    private static bool IsOutlier(Row row) =>
        row.Number("SNS2") is > OutlierSns2 && row.Number(Target) < OutlierSales;
}
