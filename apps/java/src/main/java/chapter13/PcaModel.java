package chapter13;

import chapter07.Matrix;
import java.util.List;

/**
 * 学習した主成分分析のモデル。
 *
 * @param mean 列ごとの平均
 * @param components 主成分を 1 行に 1 つずつ、寄与率の大きい順に並べた行列
 * @param explainedVariance 主成分ごとの分散（固有値）
 * @param explainedVarianceRatio 主成分ごとの寄与率
 */
public record PcaModel(
    List<Double> mean,
    Matrix components,
    List<Double> explainedVariance,
    List<Double> explainedVarianceRatio) {
  public PcaModel {
    mean = List.copyOf(mean);
    explainedVariance = List.copyOf(explainedVariance);
    explainedVarianceRatio = List.copyOf(explainedVarianceRatio);
  }
}
