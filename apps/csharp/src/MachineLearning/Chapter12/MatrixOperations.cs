namespace MachineLearning.Chapter12;

using MachineLearning.Chapter07;

/// <summary>
/// 第 7 章の <see cref="Matrix"/> に、リッジ回帰に必要な演算を足す。第 7 章のファイルは書き換えない。
/// C# では拡張メソッドにできるので、呼び出し側は元からある操作と同じ書き方で使える。
/// </summary>
public static class MatrixOperations
{
    /// <summary>size 行 size 列の単位行列。</summary>
    public static Matrix Identity(int size) =>
        Matrix.FromRows([.. Enumerable.Range(0, size).Select(i =>
            Enumerable.Range(0, size).Select(j => i == j ? 1.0 : 0.0).ToArray())]);

    /// <summary>同じ形の行列を成分ごとに足す。</summary>
    public static Matrix Add(this Matrix a, Matrix b)
    {
        ArgumentNullException.ThrowIfNull(a);
        ArgumentNullException.ThrowIfNull(b);
        if (a.RowCount != b.RowCount || a.ColumnCount != b.ColumnCount)
        {
            throw new ArgumentException(
                $"{a.RowCount} 行 {a.ColumnCount} 列の行列と {b.RowCount} 行 {b.ColumnCount} 列の行列は足せません",
                nameof(b));
        }

        return Matrix.FromRows([.. Enumerable.Range(0, a.RowCount).Select(i =>
            Enumerable.Range(0, a.ColumnCount).Select(j => a[i, j] + b[i, j]).ToArray())]);
    }

    /// <summary>行列のすべての成分に数を掛ける。</summary>
    public static Matrix Scale(this Matrix a, double k)
    {
        ArgumentNullException.ThrowIfNull(a);
        return Matrix.FromRows([.. Enumerable.Range(0, a.RowCount).Select(i =>
            Enumerable.Range(0, a.ColumnCount).Select(j => a[i, j] * k).ToArray())]);
    }

    /// <summary>列ごとの平均値。</summary>
    public static IReadOnlyList<double> ColumnMeans(this Matrix a)
    {
        ArgumentNullException.ThrowIfNull(a);
        var columns = a.Transpose();
        return [.. Enumerable.Range(0, a.ColumnCount).Select(j => columns.Row(j).Average())];
    }

    /// <summary>列ごとに、その列の平均値を引く（中心化）。</summary>
    public static Matrix Center(this Matrix a, IReadOnlyList<double> means)
    {
        ArgumentNullException.ThrowIfNull(a);
        ArgumentNullException.ThrowIfNull(means);
        return Matrix.FromRows([.. Enumerable.Range(0, a.RowCount).Select(i =>
            a.Row(i).Select((value, j) => value - means[j]).ToArray())]);
    }
}
