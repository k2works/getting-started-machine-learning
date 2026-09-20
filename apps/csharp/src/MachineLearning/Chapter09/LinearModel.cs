namespace MachineLearning.Chapter09;

using MachineLearning.Chapter02;

/// <summary>
/// 線形回帰のモデル。先頭に 1 の列を足した計画行列 X で、正規方程式 XᵀXβ = Xᵀt をガウスの消去法で解く。
/// 第 7 章の線形回帰とは独立に、この章だけで完結するように書いている。
/// </summary>
public sealed class LinearModel
{
    private readonly double[] weights;

    private LinearModel(double intercept, double[] weights)
    {
        this.Intercept = intercept;
        this.weights = weights;
    }

    /// <summary>切片。</summary>
    public double Intercept { get; }

    /// <summary>特徴量の列ごとの係数。</summary>
    public IReadOnlyList<double> Weights => this.weights;

    /// <summary>訓練データから係数を求める。</summary>
    public static LinearModel Fit(IReadOnlyList<Features> x, IReadOnlyList<double> t)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        if (x.Count != t.Count)
        {
            throw new ArgumentException("特徴量と正解の件数が違います", nameof(t));
        }

        // 先頭に 1 の列（切片の分）を足した計画行列。配列初期化子の中では .. をスプレッドとして書けないので、
        // コレクション式（[...]）で書く
        var design = x.Select(double[] (features) => [1.0, .. features.Values]).ToList();
        var width = design[0].Length;
        var normal = new double[width][];
        for (var i = 0; i < width; i++)
        {
            normal[i] = new double[width + 1];
            for (var j = 0; j < width; j++)
            {
                normal[i][j] = design.Sum(row => row[i] * row[j]);
            }

            normal[i][width] = design.Select((row, k) => row[i] * t[k]).Sum();
        }

        var beta = SolveGaussian(normal);
        return new LinearModel(beta[0], [.. beta.Skip(1)]);
    }

    /// <summary>決定係数。1 から、残差の 2 乗和を平均との差の 2 乗和で割った値を引く。</summary>
    public static double RSquared(IReadOnlyList<double> actual, IReadOnlyList<double> predicted)
    {
        ArgumentNullException.ThrowIfNull(actual);
        ArgumentNullException.ThrowIfNull(predicted);
        var mean = actual.Average();
        var residual = actual.Select((value, i) => (value - predicted[i]) * (value - predicted[i])).Sum();
        var total = actual.Sum(value => (value - mean) * (value - mean));
        return 1 - (residual / total);
    }

    /// <summary>1 行の特徴量から予測する。</summary>
    public double Predict(Features features)
    {
        ArgumentNullException.ThrowIfNull(features);
        return this.Intercept + features.Values.Select((value, i) => value * this.weights[i]).Sum();
    }

    /// <summary>行ごとに予測する。</summary>
    public IReadOnlyList<double> Predict(IReadOnlyList<Features> x)
    {
        ArgumentNullException.ThrowIfNull(x);
        return [.. x.Select(this.Predict)];
    }

    /// <summary>部分ピボット選択つきのガウスの消去法で、拡大係数行列を解く。</summary>
    private static double[] SolveGaussian(double[][] matrix)
    {
        var size = matrix.Length;
        for (var i = 0; i < size; i++)
        {
            var pivot = Enumerable.Range(i, size - i).MaxBy(row => Math.Abs(matrix[row][i]));
            (matrix[i], matrix[pivot]) = (matrix[pivot], matrix[i]);
            if (matrix[i][i] == 0)
            {
                throw new ArgumentException("特徴量の列が互いに独立でないため、正規方程式を解けません", nameof(matrix));
            }

            for (var row = 0; row < size; row++)
            {
                if (row == i)
                {
                    continue;
                }

                var factor = matrix[row][i] / matrix[i][i];
                for (var column = i; column <= size; column++)
                {
                    matrix[row][column] -= factor * matrix[i][column];
                }
            }
        }

        return [.. Enumerable.Range(0, size).Select(i => matrix[i][size] / matrix[i][i])];
    }
}
