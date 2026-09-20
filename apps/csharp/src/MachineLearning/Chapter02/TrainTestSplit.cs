namespace MachineLearning.Chapter02;

/// <summary>訓練データとテストデータ。TX は特徴量の型、TT は正解ラベルの型。</summary>
public sealed record TrainTestSplit<TX, TT>(
    IReadOnlyList<TX> XTrain,
    IReadOnlyList<TX> XTest,
    IReadOnlyList<TT> TTrain,
    IReadOnlyList<TT> TTest);
