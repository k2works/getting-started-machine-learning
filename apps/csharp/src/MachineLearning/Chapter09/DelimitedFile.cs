namespace MachineLearning.Chapter09;

using System.Text;
using MachineLearning.Chapter02;

/// <summary>
/// 文字コードと区切り文字を指定して表を読み込む。第 2 章の <see cref="Table.Load(string)"/> は
/// UTF-8 のカンマ区切りだけを読むので、TSV と Shift_JIS のためにこの章で足す。
/// </summary>
public static class DelimitedFile
{
    /// <summary>1 行目を列名として読み込む。</summary>
    public static Table Load(string file, Encoding encoding, char delimiter)
    {
        var lines = File.ReadAllLines(file, encoding);
        var columns = lines[0].Split(delimiter);
        var rows = lines.Skip(1)
            .Where(line => !string.IsNullOrWhiteSpace(line))
            .Select(line => ToRow(columns, line.Split(delimiter)))
            .ToList();
        return new Table(columns, rows);
    }

    private static Row ToRow(string[] columns, string[] values)
    {
        var cells = new Dictionary<string, string>(StringComparer.Ordinal);
        for (var i = 0; i < columns.Length; i++)
        {
            cells[columns[i]] = values[i];
        }

        return new Row(cells);
    }
}
