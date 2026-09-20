namespace MachineLearning.Chapter13;

using MachineLearning.Chapter02;
using MachineLearning.Chapter07;
using MachineLearning.Chapter09;

/// <summary>列名の並びと、標準化した値の行列。</summary>
/// <param name="Columns">列名の並び</param>
/// <param name="X">1 行が 1 件のデータを表す行列</param>
public sealed record StandardizedTable(IReadOnlyList<string> Columns, Matrix X);

/// <summary>Boston.csv を、主成分分析に渡せる形（欠損値が無く、標準化した数値だけの表）にする。</summary>
public static class BostonStandardized
{
    /// <summary>
    /// CRIME をダミー変数の列に置き換え、欠損値を列の平均値で補完してから、すべての列を標準化する。
    /// 第 9 章と違い、この章は分割せずに全件を使う（教師なし学習なので正解ラベルが無い）。
    /// </summary>
    public static StandardizedTable Standardize(Table table)
    {
        ArgumentNullException.ThrowIfNull(table);
        var categories = Dummies.Categories(table.Rows.Select(row => row.Text(Boston.Category)));
        var encoded = Dummies.Encode(table, Boston.Category, categories);
        var columns = encoded.Columns;
        var filled = Preprocessing.FillMissing(
            encoded.Rows, columns, Preprocessing.ColumnMeans(encoded.Rows, columns));
        var standardized = Standardizer.Fit(filled).Transform(filled);
        return new StandardizedTable(columns, Matrix.FromRows([.. standardized.Select(x => x.Values)]));
    }

    /// <summary>Boston.csv を読み込んで標準化する。</summary>
    public static StandardizedTable Load(string csvFile) => Standardize(Table.Load(csvFile));
}
