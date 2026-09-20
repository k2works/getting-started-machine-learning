namespace MachineLearning.Chapter11;

using MachineLearning.Chapter02;

/// <summary>交差検証に渡すモデル。既存の章の学習と予測を <see cref="Model{T}"/> の形にまとめる。</summary>
public static class Models
{
    /// <summary>深さの上限を決めた、第 3 章の決定木。</summary>
    public static Model<string> DecisionTree(int maxDepth) =>
        (x, t) =>
        {
            var tree = Chapter03.DecisionTree.WithMaxDepth(maxDepth).Fit(x, t);
            return tree.Predict;
        };

    /// <summary>第 7 章の、正規方程式で解く線形回帰。</summary>
    public static Model<double> LinearRegression =>
        (x, t) =>
        {
            var model = Chapter07.LinearRegression.Fit(x, t);
            return newX => Chapter07.LinearRegression.Predict(model, newX);
        };

    /// <summary>訓練データの正解の平均値を常に予測するモデル。交差検証そのものを試すために使う。</summary>
    public static Model<double> Mean =>
        (_, t) =>
        {
            var mean = t.Average();
            return newX => [.. newX.Select(_ => mean)];
        };

    /// <summary>特徴量をそのまま使わず、常に同じラベルを予測するモデル。正解率の落とし穴を示すために使う。</summary>
    public static Model<string> Constant(string label) =>
        (_, _) => newX => [.. ((IReadOnlyList<Features>)newX).Select(_ => label)];
}
