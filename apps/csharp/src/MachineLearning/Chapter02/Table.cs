namespace MachineLearning.Chapter02;

/// <summary>列名の並びと行のリスト。データフレームのライブラリの代わりに使う、変更できない表。</summary>
public sealed record Table(IReadOnlyList<string> Columns, IReadOnlyList<Row> Rows)
{
    /// <summary>
    /// UTF-8 の CSV を読み込む。.NET の File.ReadAllLines は BOM を取り除くので、
    /// 列名から BOM を消す処理は要らない（第 1 章）。
    /// </summary>
    public static Table Load(string csvFile)
    {
        var lines = File.ReadAllLines(csvFile);
        var columns = lines[0].Split(',');
        var rows = lines.Skip(1)
            .Where(line => !string.IsNullOrWhiteSpace(line))
            .Select(line => ToRow(columns, line))
            .ToList();
        return new Table(columns, rows);
    }

    /// <summary>列ごとの欠損値の数を、列の順に並べて返す。</summary>
    public IReadOnlyList<KeyValuePair<string, int>> CountMissing() =>
        [.. this.Columns.Select(column =>
            KeyValuePair.Create(column, this.Rows.Count(row => row.IsMissing(column))))];

    private static Row ToRow(string[] columns, string line)
    {
        // Split は行末の空欄も空文字列として残す（Java の split とは違い、上限の指定が要らない）
        var values = line.Split(',');
        var cells = new Dictionary<string, string>(StringComparer.Ordinal);
        for (var i = 0; i < columns.Length; i++)
        {
            cells[columns[i]] = values[i];
        }

        return new Row(cells);
    }
}
