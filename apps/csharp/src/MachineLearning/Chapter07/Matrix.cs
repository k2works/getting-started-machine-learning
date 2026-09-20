namespace MachineLearning.Chapter07;

using System.Globalization;

/// <summary>
/// 行列。行ごとの double の配列で持つ、変更できない型。
/// F# 版は型の別名（type Matrix = float[][]）にしているが、C# の配列は共変で中身を書き換えられるので、
/// 配列をそのまま公開せず、複製して包むクラスにする。成分で比べられるように Equals を書く。
/// </summary>
public sealed class Matrix : IEquatable<Matrix>
{
    private readonly double[][] rows;

    private Matrix(double[][] rows) => this.rows = rows;

    /// <summary>行数。</summary>
    public int RowCount => this.rows.Length;

    /// <summary>列数。</summary>
    public int ColumnCount => this.rows[0].Length;

    /// <summary>row 行 column 列の成分。</summary>
    public double this[int row, int column] => this.rows[row][column];

    /// <summary>行ごとの値の並びから行列を作る。すべての行の長さが同じでなければならない。</summary>
    public static Matrix FromRows(IReadOnlyList<IReadOnlyList<double>> rows)
    {
        ArgumentNullException.ThrowIfNull(rows);
        if (rows.Count == 0)
        {
            throw new ArgumentException("行が 1 つもありません", nameof(rows));
        }

        if (rows.Any(row => row.Count != rows[0].Count))
        {
            throw new ArgumentException("行ごとに列数が違います", nameof(rows));
        }

        return new Matrix([.. rows.Select(row => row.ToArray())]);
    }

    /// <summary>2 つのベクトルの対応する成分を掛けて足す（内積）。</summary>
    public static double Dot(IReadOnlyList<double> u, IReadOnlyList<double> v)
    {
        ArgumentNullException.ThrowIfNull(u);
        ArgumentNullException.ThrowIfNull(v);
        if (u.Count != v.Count)
        {
            throw new ArgumentException($"ベクトルの長さが違います: {u.Count} と {v.Count}", nameof(v));
        }

        var sum = 0.0;
        for (var i = 0; i < u.Count; i++)
        {
            sum += u[i] * v[i];
        }

        return sum;
    }

    /// <summary>index 行目の値の並び。</summary>
    public IReadOnlyList<double> Row(int index) => this.rows[index];

    /// <summary>行と列を入れ替える。</summary>
    public Matrix Transpose() =>
        new([.. Enumerable.Range(0, this.ColumnCount)
            .Select(column => this.rows.Select(row => row[column]).ToArray())]);

    /// <summary>行列の積。左の列数と右の行数が同じでなければならない。</summary>
    public Matrix Multiply(Matrix other)
    {
        ArgumentNullException.ThrowIfNull(other);
        if (this.ColumnCount != other.RowCount)
        {
            throw new ArgumentException(
                $"左の行列の列数 {this.ColumnCount} と右の行列の行数 {other.RowCount} が違います", nameof(other));
        }

        var columns = other.Transpose();
        return new([.. this.rows.Select(row =>
            Enumerable.Range(0, columns.RowCount).Select(i => Dot(row, columns.rows[i])).ToArray())]);
    }

    /// <summary>行列とベクトルの積。行ごとの内積を並べる。</summary>
    public IReadOnlyList<double> Multiply(IReadOnlyList<double> vector)
    {
        ArgumentNullException.ThrowIfNull(vector);
        if (this.ColumnCount != vector.Count)
        {
            throw new ArgumentException(
                $"行列の列数 {this.ColumnCount} とベクトルの長さ {vector.Count} が違います", nameof(vector));
        }

        return [.. this.rows.Select(row => Dot(row, vector))];
    }

    /// <summary>連立方程式 a x = b の解 x を、部分ピボット選択つきのガウスの消去法で求める。</summary>
    public IReadOnlyList<double> Solve(IReadOnlyList<double> b)
    {
        ArgumentNullException.ThrowIfNull(b);
        var n = this.RowCount;
        if (n != this.ColumnCount || n != b.Count)
        {
            throw new ArgumentException($"{n} 行 {this.ColumnCount} 列の行列と長さ {b.Count} のベクトルは解けません", nameof(b));
        }

        // 右辺 b を右に並べた拡大係数行列。引数を書き換えないように、新しい配列を作る
        var augmented = this.rows.Select((row, i) => row.Append(b[i]).ToArray()).ToArray();

        // 前進消去: 対角成分より下を 0 にする
        for (var pivot = 0; pivot < n; pivot++)
        {
            // 部分ピボット選択: この列で絶対値が最も大きい行を対角の位置に入れ替える
            var largest = pivot;
            for (var i = pivot + 1; i < n; i++)
            {
                if (Math.Abs(augmented[i][pivot]) > Math.Abs(augmented[largest][pivot]))
                {
                    largest = i;
                }
            }

            (augmented[pivot], augmented[largest]) = (augmented[largest], augmented[pivot]);

            for (var i = pivot + 1; i < n; i++)
            {
                var factor = augmented[i][pivot] / augmented[pivot][pivot];
                for (var j = pivot; j <= n; j++)
                {
                    augmented[i][j] -= factor * augmented[pivot][j];
                }
            }
        }

        // 後退代入: 下の行から解を 1 つずつ決める
        var x = new double[n];
        for (var i = n - 1; i >= 0; i--)
        {
            var known = 0.0;
            for (var j = i + 1; j < n; j++)
            {
                known += augmented[i][j] * x[j];
            }

            x[i] = (augmented[i][n] - known) / augmented[i][i];
        }

        return x;
    }

    /// <summary>すべての成分が 1 の列を先頭に足す。この列にかかる係数が切片になる。</summary>
    public Matrix WithLeadingOnes() => new([.. this.rows.Select(row => row.Prepend(1.0).ToArray())]);

    public bool Equals(Matrix? other) =>
        other is not null && this.rows.Length == other.rows.Length
        && this.rows.Zip(other.rows).All(pair => pair.First.SequenceEqual(pair.Second));

    public override bool Equals(object? obj) => this.Equals(obj as Matrix);

    public override int GetHashCode()
    {
        var hash = default(HashCode);
        foreach (var value in this.rows.SelectMany(row => row))
        {
            hash.Add(value);
        }

        return hash.ToHashCode();
    }

    public override string ToString() =>
        "Matrix[" + string.Join(
            "; ",
            this.rows.Select(row => string.Join(", ", row.Select(v => v.ToString(CultureInfo.InvariantCulture))))) + "]";
}
