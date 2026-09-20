namespace MachineLearning.Tests.Chapter09;

using MachineLearning.Chapter02;

/// <summary>第 9 章のテストで使う、架空の値の表と特徴量。</summary>
public static class Samples
{
    /// <summary>列名と、セルの並びから表を作る。</summary>
    public static Table Table(IReadOnlyList<string> columns, params string[][] rows)
    {
        ArgumentNullException.ThrowIfNull(columns);
        ArgumentNullException.ThrowIfNull(rows);
        return new Table(columns, [.. rows.Select(cells => Row(columns, cells))]);
    }

    /// <summary>列名と値の並びから 1 行を作る。</summary>
    public static Row Row(IReadOnlyList<string> columns, IReadOnlyList<string> cells)
    {
        ArgumentNullException.ThrowIfNull(columns);
        ArgumentNullException.ThrowIfNull(cells);
        return new Row(columns.Select((column, i) => (Column: column, Cell: cells[i]))
            .ToDictionary(pair => pair.Column, pair => pair.Cell, StringComparer.Ordinal));
    }

    /// <summary>1 列の特徴量を値の数だけ作る。</summary>
    public static IReadOnlyList<Features> Column(string name, params double[] values)
    {
        ArgumentNullException.ThrowIfNull(values);
        return [.. values.Select(value => new Features([name], [value]))];
    }
}
