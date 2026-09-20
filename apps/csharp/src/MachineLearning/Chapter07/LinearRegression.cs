namespace MachineLearning.Chapter07;

using MachineLearning.Chapter02;

/// <summary>
/// 線形回帰の学習結果。切片と、特徴量の列ごとの係数。
/// 係数は第 2 章の Features（列名の並びと値の並び）で持つ。列名で引けて、値で比べられる。
/// </summary>
public sealed record LinearModel(double Intercept, Features Coefficients);

/// <summary>正規方程式で解く線形回帰。</summary>
public static class LinearRegression
{
    /// <summary>正規方程式 (Xᵀ X) w = Xᵀ t を解いて、切片と係数を求める。</summary>
    public static LinearModel Fit(IReadOnlyList<Features> x, IReadOnlyList<double> t)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        if (x.Count != t.Count)
        {
            throw new ArgumentException($"特徴量 {x.Count} 件と正解ラベル {t.Count} 件の数が違います", nameof(t));
        }

        var design = DesignMatrix(x);
        var designT = design.Transpose();
        var weights = designT.Multiply(design).Solve(designT.Multiply(t));
        return new LinearModel(weights[0], new Features(x[0].Columns, [.. weights.Skip(1)]));
    }

    /// <summary>切片 + 係数 × 特徴量の和を行ごとに求める。係数を持つ列だけを使う。</summary>
    public static IReadOnlyList<double> Predict(LinearModel model, IReadOnlyList<Features> x)
    {
        ArgumentNullException.ThrowIfNull(model);
        ArgumentNullException.ThrowIfNull(x);
        var columns = model.Coefficients.Columns;
        var weights = model.Coefficients.Values;
        return [.. x.Select(row =>
            model.Intercept + Matrix.Dot([.. columns.Select(row.Value)], weights))];
    }

    /// <summary>特徴量を行列にして、先頭に 1 の列（切片にかかる列）を足す。</summary>
    public static Matrix DesignMatrix(IReadOnlyList<Features> x)
    {
        ArgumentNullException.ThrowIfNull(x);
        return Matrix.FromRows([.. x.Select(row => row.Values)]).WithLeadingOnes();
    }
}
