namespace MachineLearning.Chapter08;

using MachineLearning.Chapter02;

/// <summary>カテゴリ値の列を、最初のカテゴリを除いたカテゴリごとの 0 と 1 の列（ダミー変数）にする前処理。</summary>
public sealed record DummyEncoder(IReadOnlyList<string> Columns) : ITransformer
{
    public IFittedTransformer Fit(Table x)
    {
        ArgumentNullException.ThrowIfNull(x);
        return new Fitted(
            [.. this.Columns.Select(column => new Dummies(column, [.. CategoriesOf(x, column).Skip(1)]))]);
    }

    /// <summary>列の値を重複なく並べ替える。欠損値は除く。</summary>
    private static IReadOnlyList<string> CategoriesOf(Table x, string column) =>
        [.. x.Rows
            .Where(row => !row.IsMissing(column))
            .Select(row => row.Text(column))
            .Distinct(StringComparer.Ordinal)
            .Order(StringComparer.Ordinal)];

    /// <summary>1 つの列と、その列でダミー変数にするカテゴリ（最初のカテゴリを除く）。</summary>
    public sealed record Dummies(string Column, IReadOnlyList<string> Categories);

    /// <summary>Fit で求めたカテゴリを持ち、どのデータにも同じダミー変数の列を作る。</summary>
    public sealed record Fitted(IReadOnlyList<Dummies> Dummies) : IFittedTransformer
    {
        public Table Transform(Table x)
        {
            ArgumentNullException.ThrowIfNull(x);
            var columns = x.Columns.ToList();
            foreach (var dummies in this.Dummies)
            {
                columns.Remove(dummies.Column);
                columns.AddRange(dummies.Categories.Select(category => $"{dummies.Column}_{category}"));
            }

            return new Table(columns, [.. x.Rows.Select(row => this.Encode(x.Columns, row))]);
        }

        private Row Encode(IReadOnlyList<string> columns, Row row)
        {
            // 元の列のセルを写してから、ダミー変数の列を足す。元のカテゴリの列は表の列から外れるので残っていてよい
            var cells = columns.ToDictionary(name => name, row.Text, StringComparer.Ordinal);
            foreach (var dummies in this.Dummies)
            {
                var value = row.Text(dummies.Column);
                foreach (var category in dummies.Categories)
                {
                    cells[$"{dummies.Column}_{category}"] =
                        string.Equals(value, category, StringComparison.Ordinal) ? "1" : "0";
                }
            }

            return new Row(cells);
        }
    }
}
