package chapter12;

/**
 * 正則化の強さ 1 つ分の実験結果。作ったあとで変えられない。
 *
 * @param alpha 正則化の強さ
 * @param trainScore 訓練データの決定係数
 * @param validationScore 検証データの決定係数
 * @param coefficientAbsSum 係数の絶対値の合計
 */
public record Experiment(
    double alpha, double trainScore, double validationScore, double coefficientAbsSum) {}
