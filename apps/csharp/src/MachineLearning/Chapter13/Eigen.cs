namespace MachineLearning.Chapter13;

using MachineLearning.Chapter07;

/// <summary>固有値と、それに対応する長さ 1 の固有ベクトル。</summary>
/// <param name="Value">固有値</param>
/// <param name="Vector">長さ 1 の固有ベクトル</param>
public sealed record EigenPair(double Value, IReadOnlyList<double> Vector);

/// <summary>対称行列の固有値と固有ベクトルを、ヤコビ法で求める。</summary>
public static class Eigen
{
    /// <summary>対角成分以外の 2 乗和がこれ以下になったら、対角行列になったとみなす。</summary>
    public const double Tolerance = 1e-24;

    /// <summary>回転を繰り返す回数の上限（すべての非対角成分を 1 回ずつ回すのを 1 巡とする）。</summary>
    public const int MaxSweeps = 100;

    /// <summary>n 行 n 列の単位行列。</summary>
    public static Matrix Identity(int n) =>
        Matrix.FromRows([.. Enumerable.Range(0, n).Select(i =>
            Enumerable.Range(0, n).Select(j => i == j ? 1.0 : 0.0).ToArray())]);

    /// <summary>対角成分以外の要素の 2 乗和。0 に近いほど対角行列に近い。</summary>
    public static double OffDiagonal(Matrix a)
    {
        ArgumentNullException.ThrowIfNull(a);
        var sum = 0.0;
        for (var i = 0; i < a.RowCount; i++)
        {
            for (var j = 0; j < a.ColumnCount; j++)
            {
                if (i != j)
                {
                    sum += a[i, j] * a[i, j];
                }
            }
        }

        return sum;
    }

    /// <summary>(p, q) 成分と (q, p) 成分を 0 にする、p・q の 2 つの軸の平面での回転行列。</summary>
    public static Matrix Rotation(Matrix a, int p, int q)
    {
        ArgumentNullException.ThrowIfNull(a);
        var theta = (a[q, q] - a[p, p]) / (2.0 * a[p, q]);
        var t = (theta >= 0.0 ? 1.0 : -1.0) / (Math.Abs(theta) + Math.Sqrt((theta * theta) + 1.0));
        var c = 1.0 / Math.Sqrt((t * t) + 1.0);
        var s = t * c;
        return Matrix.FromRows([.. Enumerable.Range(0, a.RowCount).Select(i =>
            Enumerable.Range(0, a.RowCount).Select(j => (i, j) switch
            {
                _ when (i == p && j == p) || (i == q && j == q) => c,
                _ when i == p && j == q => s,
                _ when i == q && j == p => -s,
                _ => i == j ? 1.0 : 0.0,
            }).ToArray())]);
    }

    /// <summary>
    /// 対称行列の固有値と固有ベクトルを、固有値の大きい順に返す（ヤコビ法）。
    /// 回転で対角行列に近づけると、対角成分が固有値、回転を掛け合わせた行列の列が固有ベクトルになる。
    /// </summary>
    public static IReadOnlyList<EigenPair> Symmetric(Matrix a)
    {
        ArgumentNullException.ThrowIfNull(a);
        var n = a.RowCount;
        var diagonal = a;
        var vectors = Identity(n);
        for (var sweep = 0; sweep < MaxSweeps && OffDiagonal(diagonal) > Tolerance; sweep++)
        {
            for (var p = 0; p < n - 1; p++)
            {
                for (var q = p + 1; q < n; q++)
                {
                    if (diagonal[p, q] != 0.0)
                    {
                        var j = Rotation(diagonal, p, q);
                        diagonal = j.Transpose().Multiply(diagonal).Multiply(j);
                        vectors = vectors.Multiply(j);
                    }
                }
            }
        }

        var columns = vectors.Transpose();
        return [.. Enumerable.Range(0, n)
            .Select(i => new EigenPair(diagonal[i, i], columns.Row(i)))
            .OrderByDescending(pair => pair.Value)];
    }
}
