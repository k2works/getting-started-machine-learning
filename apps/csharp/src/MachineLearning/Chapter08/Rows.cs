namespace MachineLearning.Chapter08;

using MachineLearning.Chapter02;

/// <summary>行を書き換えた新しい行を作る。</summary>
internal static class Rows
{
    /// <summary>列の値を置き換えた（列が無ければ足した）新しい行を返す。元の行は変更しない。</summary>
    public static Row With(IReadOnlyList<string> columns, Row row, string column, string value)
    {
        var cells = columns.ToDictionary(name => name, row.Text, StringComparer.Ordinal);
        cells[column] = value;
        return new Row(cells);
    }
}
