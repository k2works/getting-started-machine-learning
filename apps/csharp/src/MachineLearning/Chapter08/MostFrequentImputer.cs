namespace MachineLearning.Chapter08;

using MachineLearning.Chapter02;

/// <summary>文字列の列の欠損値を、訓練データで最も多い値（最頻値）で補完する前処理。</summary>
public sealed record MostFrequentImputer(string Column) : ITransformer
{
    public IFittedTransformer Fit(Table x)
    {
        ArgumentNullException.ThrowIfNull(x);
        var counts = new Dictionary<string, int>(StringComparer.Ordinal);
        foreach (var row in x.Rows.Where(row => !row.IsMissing(this.Column)))
        {
            counts[row.Text(this.Column)] = counts.GetValueOrDefault(row.Text(this.Column)) + 1;
        }

        var mostFrequent = counts.Aggregate((best, next) => next.Value > best.Value ? next : best).Key;
        return new Fitted(this.Column, mostFrequent);
    }

    /// <summary>Fit で求めた最頻値を持ち、欠損値を補完する。</summary>
    public sealed record Fitted(string Column, string MostFrequent) : IFittedTransformer
    {
        public Table Transform(Table x)
        {
            ArgumentNullException.ThrowIfNull(x);
            return new Table(x.Columns, [.. x.Rows.Select(row => this.Fill(x.Columns, row))]);
        }

        private Row Fill(IReadOnlyList<string> columns, Row row) =>
            row.IsMissing(this.Column) ? Rows.With(columns, row, this.Column, this.MostFrequent) : row;
    }
}
