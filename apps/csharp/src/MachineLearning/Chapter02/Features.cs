namespace MachineLearning.Chapter02;

using System.Globalization;

/// <summary>
/// 補完が済んだ 1 行分の特徴量。欠損値を持てないので、補完する前の行をモデルに渡すことはできない。
/// 値は double の配列で持つ。record にすると配列の成分を参照で比べてしまうので、
/// クラスにして Equals・GetHashCode・ToString を中身で比べるように書く。
/// </summary>
public sealed class Features : IEquatable<Features>
{
    private readonly string[] columns;
    private readonly double[] values;

    public Features(IReadOnlyList<string> columns, IReadOnlyList<double> values)
    {
        ArgumentNullException.ThrowIfNull(columns);
        ArgumentNullException.ThrowIfNull(values);
        if (columns.Count != values.Count)
        {
            throw new ArgumentException("列名と値の数が違います", nameof(values));
        }

        this.columns = [.. columns];
        this.values = [.. values];
    }

    /// <summary>列名の並び。</summary>
    public IReadOnlyList<string> Columns => this.columns;

    /// <summary>値の並び。</summary>
    public IReadOnlyList<double> Values => this.values;

    /// <summary>列名で値を読む。</summary>
    public double Value(string column)
    {
        var index = Array.IndexOf(this.columns, column);
        return index < 0 ? throw new ArgumentException($"列がありません: {column}", nameof(column)) : this.values[index];
    }

    public bool Equals(Features? other) =>
        other is not null && this.columns.SequenceEqual(other.columns) && this.values.SequenceEqual(other.values);

    public override bool Equals(object? obj) => this.Equals(obj as Features);

    public override int GetHashCode()
    {
        var hash = default(HashCode);
        foreach (var column in this.columns)
        {
            hash.Add(column);
        }

        foreach (var value in this.values)
        {
            hash.Add(value);
        }

        return hash.ToHashCode();
    }

    public override string ToString() =>
        "Features[" + string.Join(
            ", ",
            this.columns.Select((column, i) => $"{column}={this.values[i].ToString(CultureInfo.InvariantCulture)}")) + "]";
}
