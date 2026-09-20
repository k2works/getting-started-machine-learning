namespace MachineLearning.Chapter09;

using MachineLearning.Chapter02;

/// <summary>カテゴリ値の列を、カテゴリごとの 0 と 1 の列（ダミー変数）に変える。</summary>
public static class Dummies
{
    /// <summary>
    /// 空欄を除いたカテゴリを辞書順に並べ、先頭を除いて返す（pandas の drop_first=True と同じ）。
    /// 先頭を残すと「すべての列の合計が 1」という余分な関係ができ、線形回帰の正規方程式が解けなくなる。
    /// </summary>
    public static IReadOnlyList<string> Categories(IEnumerable<string> values)
    {
        ArgumentNullException.ThrowIfNull(values);
        return [.. values
            .Where(value => !string.IsNullOrWhiteSpace(value))
            .Distinct(StringComparer.Ordinal)
            .Order(StringComparer.Ordinal)
            .Skip(1)];
    }

    /// <summary>
    /// 元の列を取り除き、カテゴリごとに「列名_カテゴリ」の列を末尾に加える。
    /// 値がカテゴリと一致すれば "1"、それ以外は "0" にする。カテゴリに無い値はすべての列が "0" になる。
    /// </summary>
    public static Table Encode(Table table, string column, IReadOnlyList<string> categories)
    {
        ArgumentNullException.ThrowIfNull(table);
        ArgumentNullException.ThrowIfNull(categories);
        IReadOnlyList<string> columns =
        [
            .. table.Columns.Where(name => !string.Equals(name, column, StringComparison.Ordinal)),
            .. categories.Select(category => $"{column}_{category}"),
        ];
        var kept = table.Columns.Where(name => !string.Equals(name, column, StringComparison.Ordinal)).ToList();
        return new Table(columns, [.. table.Rows.Select(row => EncodeRow(row, kept, column, categories))]);
    }

    private static Row EncodeRow(Row row, IReadOnlyList<string> kept, string column, IReadOnlyList<string> categories)
    {
        var value = row.Text(column);
        var cells = kept.ToDictionary(name => name, row.Text, StringComparer.Ordinal);
        foreach (var category in categories)
        {
            cells[$"{column}_{category}"] = string.Equals(value, category, StringComparison.Ordinal) ? "1" : "0";
        }

        return new Row(cells);
    }
}
