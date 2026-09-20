namespace MachineLearning.Chapter10;

using MachineLearning.Chapter01;
using MachineLearning.Chapter02;
using MachineLearning.Chapter03;
using Features = MachineLearning.Chapter02.Features;

/// <summary>訓練データとテストデータの正解率。</summary>
public sealed record Score(double Train, double Test);

/// <summary>
/// 分類器。訓練データ（特徴量と正解ラベル）を受け取り、予測する関数を返す。
/// F# 版は関数の型（Classifier）で表したが、C# ではインターフェースで表す。
/// </summary>
public interface IClassifier
{
    /// <summary>訓練データから学習し、予測する関数を返す。</summary>
    Func<IReadOnlyList<Features>, IReadOnlyList<string>> Fit(
        IReadOnlyList<Features> x, IReadOnlyList<string> t);
}

/// <summary>どの分類器にも共通の振る舞い。F# 版のモジュールの関数を、C# では拡張メソッドで表す。</summary>
public static class ClassifierExtensions
{
    /// <summary>訓練データで学習させてから、訓練データとテストデータの正解率を求める。</summary>
    public static Score Evaluate(this IClassifier classifier, TrainTestSplit<Features, string> split)
    {
        ArgumentNullException.ThrowIfNull(classifier);
        ArgumentNullException.ThrowIfNull(split);
        var predict = classifier.Fit(split.XTrain, split.TTrain);
        return new Score(
            KinokoTakenoko.Accuracy(predict(split.XTrain), split.TTrain),
            KinokoTakenoko.Accuracy(predict(split.XTest), split.TTest));
    }
}

/// <summary>
/// 第 3 章の決定木を分類器として使うためのアダプター。
/// 第 3 章のコードは変更せず、Fit・Predict の呼び出し方だけをここで合わせる。
/// </summary>
public sealed class DecisionTreeClassifier : IClassifier
{
    private readonly Func<DecisionTree> create;

    private DecisionTreeClassifier(Func<DecisionTree> create) => this.create = create;

    /// <summary>深さを制限しない決定木の分類器。</summary>
    public static DecisionTreeClassifier Unlimited() => new(DecisionTree.Unlimited);

    /// <summary>深さの上限を指定した決定木の分類器。</summary>
    public static DecisionTreeClassifier WithMaxDepth(int maxDepth) =>
        new(() => DecisionTree.WithMaxDepth(maxDepth));

    public Func<IReadOnlyList<Features>, IReadOnlyList<string>> Fit(
        IReadOnlyList<Features> x, IReadOnlyList<string> t) =>
        this.create().Fit(x, t).Predict;
}
