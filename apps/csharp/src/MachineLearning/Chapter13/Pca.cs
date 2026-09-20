namespace MachineLearning.Chapter13;

using MachineLearning.Chapter07;

/// <summary>学習した主成分分析のモデル。Components の 1 行が 1 つの主成分を表す。</summary>
/// <param name="Mean">列ごとの平均</param>
/// <param name="Components">主成分（長さ 1 の固有ベクトル）の並び</param>
/// <param name="ExplainedVariance">主成分ごとの分散（固有値）</param>
/// <param name="ExplainedVarianceRatio">主成分ごとの寄与率</param>
public sealed record PcaModel(
    IReadOnlyList<double> Mean,
    IReadOnlyList<IReadOnlyList<double>> Components,
    IReadOnlyList<double> ExplainedVariance,
    IReadOnlyList<double> ExplainedVarianceRatio);

/// <summary>分散共分散行列の固有ベクトルを主成分にする、主成分分析。</summary>
public static class Pca
{
    /// <summary>列ごとの平均。x の 1 行が 1 件のデータを表す。</summary>
    public static IReadOnlyList<double> ColumnMeans(Matrix x)
    {
        ArgumentNullException.ThrowIfNull(x);
        return [.. Enumerable.Range(0, x.ColumnCount)
            .Select(column => Enumerable.Range(0, x.RowCount).Average(row => x[row, column]))];
    }

    /// <summary>各行から列の平均を引く（中心化）。</summary>
    public static Matrix Center(IReadOnlyList<double> means, Matrix x)
    {
        ArgumentNullException.ThrowIfNull(means);
        ArgumentNullException.ThrowIfNull(x);
        return Matrix.FromRows([.. Enumerable.Range(0, x.RowCount).Select(row =>
            x.Row(row).Select((value, column) => value - means[column]).ToArray())]);
    }

    /// <summary>分散共分散行列。対角成分が列ごとの分散、それ以外が 2 列の共分散になる。</summary>
    public static Matrix CovarianceMatrix(Matrix x)
    {
        ArgumentNullException.ThrowIfNull(x);
        var columns = Center(ColumnMeans(x), x).Transpose();
        var n = (double)x.RowCount;
        return Matrix.FromRows([.. Enumerable.Range(0, columns.RowCount).Select(i =>
            Enumerable.Range(0, columns.RowCount)
                .Select(j => Matrix.Dot(columns.Row(i), columns.Row(j)) / (n - 1.0))
                .ToArray())]);
    }

    /// <summary>固有ベクトルは符号が逆でも同じ向きを表すので、絶対値が最大の成分が正になるようにそろえる。</summary>
    public static IReadOnlyList<IReadOnlyList<double>> NormalizeSigns(
        IReadOnlyList<IReadOnlyList<double>> components)
    {
        ArgumentNullException.ThrowIfNull(components);
        return [.. components.Select(pc =>
        {
            var sign = Math.Sign(pc.MaxBy(Math.Abs));
            return (IReadOnlyList<double>)[.. pc.Select(value => value * sign)];
        })];
    }

    /// <summary>分散共分散行列の固有値の大きい順に、nComponents 個の主成分を求める。</summary>
    public static PcaModel Fit(int nComponents, Matrix x)
    {
        ArgumentNullException.ThrowIfNull(x);
        var pairs = Eigen.Symmetric(CovarianceMatrix(x));
        var total = pairs.Sum(pair => pair.Value);
        var selected = pairs.Take(nComponents).ToList();
        return new PcaModel(
            ColumnMeans(x),
            NormalizeSigns([.. selected.Select(pair => pair.Vector)]),
            [.. selected.Select(pair => pair.Value)],
            [.. selected.Select(pair => pair.Value / total)]);
    }

    /// <summary>平均を引いてから、主成分ごとの座標（主成分の向きとの内積）に変換する。</summary>
    public static Matrix Transform(PcaModel model, Matrix x)
    {
        ArgumentNullException.ThrowIfNull(model);
        ArgumentNullException.ThrowIfNull(x);
        var centered = Center(model.Mean, x);
        return Matrix.FromRows([.. Enumerable.Range(0, centered.RowCount).Select(row =>
            model.Components.Select(pc => Matrix.Dot(centered.Row(row), pc)).ToArray())]);
    }

    /// <summary>累積寄与率がしきい値に届くまでに必要な主成分の数。</summary>
    public static int ComponentsNeeded(double threshold, IReadOnlyList<double> ratios)
    {
        ArgumentNullException.ThrowIfNull(ratios);
        var sum = 0.0;
        for (var i = 0; i < ratios.Count; i++)
        {
            sum += ratios[i];
            if (sum >= threshold)
            {
                return i + 1;
            }
        }

        return ratios.Count;
    }

    /// <summary>主成分の係数の絶対値が大きい順に、上位 k 個の列名と係数を返す。</summary>
    public static IReadOnlyList<KeyValuePair<string, double>> TopLoadings(
        int k, IReadOnlyList<string> columns, IReadOnlyList<double> pc)
    {
        ArgumentNullException.ThrowIfNull(columns);
        ArgumentNullException.ThrowIfNull(pc);
        return [.. columns.Zip(pc, KeyValuePair.Create)
            .OrderByDescending(pair => Math.Abs(pair.Value))
            .Take(k)];
    }
}
