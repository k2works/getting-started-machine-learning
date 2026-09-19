package chapter12;

import chapter07.Matrix;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 正則化した線形回帰のモデル。特徴量の列の順に並んだ係数と、切片を持つ。
 *
 * @param coefficients 列の順に並んだ係数
 * @param intercept 切片
 */
public record RegularizedModel(List<Double> coefficients, double intercept) {
  public RegularizedModel {
    coefficients = List.copyOf(coefficients);
  }

  /** 行ごとの予測値。行列の列数は係数の数と同じでなければならない。 */
  public List<Double> predict(Matrix x) {
    if (x.columnCount() != coefficients.size()) {
      throw new IllegalArgumentException(
          "特徴量の列数 " + x.columnCount() + " と係数の数 " + coefficients.size() + " が違います");
    }
    return IntStream.range(0, x.rowCount())
        .mapToObj(
            i -> {
              double sum = intercept;
              for (int j = 0; j < coefficients.size(); j++) {
                sum += x.get(i, j) * coefficients.get(j);
              }
              return sum;
            })
        .toList();
  }

  /** 係数の絶対値の合計。正則化が強いほど小さくなる。 */
  public double coefficientAbsSum() {
    return coefficients.stream().mapToDouble(Math::abs).sum();
  }
}
