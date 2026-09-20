namespace MachineLearning.Chapter09;

using System.Text;
using MachineLearning.Chapter02;

/// <summary>自転車の利用者数（bike.tsv）と天気（weather.csv）の結合と集計。</summary>
public static class BikeWeather
{
    /// <summary>結合のキーにする列。</summary>
    public const string Key = "weather_id";

    private const string WeatherColumn = "weather";
    private const string CountColumn = "cnt";

    /// <summary>
    /// Shift_JIS のエンコーディング。.NET では、コードページのエンコーディングを使う前に
    /// CodePagesEncodingProvider を一度登録する必要がある（登録は何度行っても害はない）。
    /// </summary>
    public static Encoding ShiftJis()
    {
        Encoding.RegisterProvider(CodePagesEncodingProvider.Instance);
        return Encoding.GetEncoding("shift_jis");
    }

    /// <summary>タブ区切りの bike.tsv（UTF-8）を読み込む。</summary>
    public static Table LoadBike(string tsvFile) => DelimitedFile.Load(tsvFile, Encoding.UTF8, '\t');

    /// <summary>Shift_JIS の weather.csv を読み込む。</summary>
    public static Table LoadWeather(string csvFile) => DelimitedFile.Load(csvFile, ShiftJis(), ',');

    /// <summary>天気の番号で天気の列を加える（内部結合）。天気の表に無い番号の行は残さない。</summary>
    public static Table JoinWeather(Table bike, Table weather)
    {
        ArgumentNullException.ThrowIfNull(bike);
        ArgumentNullException.ThrowIfNull(weather);
        var byId = weather.Rows.ToDictionary(row => row.Text(Key), row => row, StringComparer.Ordinal);
        var added = weather.Columns.Where(column => !string.Equals(column, Key, StringComparison.Ordinal)).ToList();
        var rows = bike.Rows
            .Where(row => byId.ContainsKey(row.Text(Key)))
            .Select(row => Join(row, bike.Columns, byId[row.Text(Key)], added))
            .ToList();
        return new Table([.. bike.Columns, .. added], rows);
    }

    /// <summary>天気ごとの平均利用者数を、多い順に並べて返す。</summary>
    public static IReadOnlyList<KeyValuePair<string, double>> MeanCountByWeather(Table joined)
    {
        ArgumentNullException.ThrowIfNull(joined);
        return [.. joined.Rows
            .GroupBy(row => row.Text(WeatherColumn), StringComparer.Ordinal)
            .Select(group => KeyValuePair.Create(
                group.Key, group.Average(row => row.Number(CountColumn) ?? 0)))
            .OrderByDescending(pair => pair.Value)];
    }

    private static Row Join(Row row, IReadOnlyList<string> columns, Row found, IReadOnlyList<string> added)
    {
        var cells = columns.ToDictionary(column => column, row.Text, StringComparer.Ordinal);
        foreach (var column in added)
        {
            cells[column] = found.Text(column);
        }

        return new Row(cells);
    }
}
