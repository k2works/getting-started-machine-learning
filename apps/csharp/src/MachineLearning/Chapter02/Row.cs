namespace MachineLearning.Chapter02;

using System.Globalization;

/// <summary>CSV の 1 行。セルの文字列を列名で引けるように持つ。空欄は欠損値として扱う。</summary>
public sealed record Row
{
    private readonly Dictionary<string, string> cells;

    public Row(IReadOnlyDictionary<string, string> cells)
    {
        ArgumentNullException.ThrowIfNull(cells);
        this.cells = new Dictionary<string, string>(cells, StringComparer.Ordinal);
    }

    /// <summary>数値の列を読む。空欄なら null を返す。</summary>
    public double? Number(string column)
    {
        var cell = this.Text(column);
        return string.IsNullOrWhiteSpace(cell) ? null : double.Parse(cell, CultureInfo.InvariantCulture);
    }

    /// <summary>文字列の列を読む。</summary>
    public string Text(string column)
    {
        if (!this.cells.TryGetValue(column, out var cell))
        {
            throw new ArgumentException($"列がありません: {column}", nameof(column));
        }

        return cell;
    }

    /// <summary>セルが空欄かどうか。</summary>
    public bool IsMissing(string column) => string.IsNullOrWhiteSpace(this.Text(column));
}
