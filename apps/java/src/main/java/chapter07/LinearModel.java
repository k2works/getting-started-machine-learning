package chapter07;

import chapter02.Features;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学習した線形回帰のモデル。切片と、列名つきの係数を持つ。
 *
 * <p>係数は列の順を保つ。Map.copyOf は順を保たないので、LinkedHashMap に写してから変更できないように包む。
 */
public record LinearModel(double intercept, Map<String, Double> coefficients) {
  public LinearModel {
    coefficients = Collections.unmodifiableMap(new LinkedHashMap<>(coefficients));
  }

  /** 列名と、同じ順に並んだ係数からモデルを作る。 */
  public static LinearModel of(double intercept, List<String> columns, double[] coefficients) {
    if (columns.size() != coefficients.length) {
      throw new IllegalArgumentException("列名と係数の数が違います");
    }
    Map<String, Double> named = new LinkedHashMap<>();
    for (int i = 0; i < coefficients.length; i++) {
      named.put(columns.get(i), coefficients[i]);
    }
    return new LinearModel(intercept, named);
  }

  /** 1 行分の特徴量の予測値。係数は列名で対応させるので、列の並び順は問わない。 */
  public double predict(Features features) {
    double sum = intercept;
    for (Map.Entry<String, Double> coefficient : coefficients.entrySet()) {
      sum += coefficient.getValue() * features.value(coefficient.getKey());
    }
    return sum;
  }

  /** 行ごとの予測値。 */
  public List<Double> predict(List<Features> x) {
    return x.stream().map(this::predict).toList();
  }
}
