package chapter11;

import java.util.List;

/**
 * 2 値分類の混同行列。正例（見つけたいほう）を決めて、予測の当たり外れを 4 つに分けて数える。
 *
 * @param tp 実際は正例で、正例と予測した件数（真陽性）
 * @param fp 実際は負例で、正例と予測した件数（偽陽性）
 * @param fn 実際は正例で、負例と予測した件数（偽陰性）
 * @param tn 実際は負例で、負例と予測した件数（真陰性）
 */
public record ConfusionMatrix(int tp, int fp, int fn, int tn) {
  /** 正解と予測を 1 件ずつ比べて数える。positive と等しいラベルを正例、それ以外を負例とする。 */
  public static <T> ConfusionMatrix of(List<T> actual, List<T> predicted, T positive) {
    Metrics.requireSameSize(actual, predicted);
    int tp = 0;
    int fp = 0;
    int fn = 0;
    int tn = 0;
    for (int i = 0; i < actual.size(); i++) {
      boolean isPositive = actual.get(i).equals(positive);
      boolean predictedPositive = predicted.get(i).equals(positive);
      if (isPositive && predictedPositive) {
        tp++;
      } else if (predictedPositive) {
        fp++;
      } else if (isPositive) {
        fn++;
      } else {
        tn++;
      }
    }
    return new ConfusionMatrix(tp, fp, fn, tn);
  }
}
