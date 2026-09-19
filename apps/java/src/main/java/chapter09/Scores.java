package chapter09;

/**
 * 訓練データとテストデータの決定係数。
 *
 * @param train 訓練データの決定係数
 * @param test テストデータの決定係数
 */
public record Scores(double train, double test) {}
