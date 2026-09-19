package chapter09;

import chapter02.Features;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 列ごとの平均と標準偏差（件数で割る標準偏差）で、平均 0・標準偏差 1 にそろえる。
 *
 * <p>訓練データで {@link #fit} し、同じ平均と標準偏差で訓練データとテストデータの両方を {@link #transform} する。
 *
 * @param means 列名ごとの平均
 * @param stds 列名ごとの標準偏差。すべて同じ値の列は 1 にして、標準化した値が 0 になるようにする
 */
public record Standardizer(Map<String, Double> means, Map<String, Double> stds) {
  public Standardizer {
    if (!means.keySet().equals(stds.keySet())) {
      throw new IllegalArgumentException("平均と標準偏差の列が違います");
    }
    means = Collections.unmodifiableMap(new LinkedHashMap<>(means));
    stds = Collections.unmodifiableMap(new LinkedHashMap<>(stds));
  }

  /** 特徴量のすべての列について、平均と標準偏差を求める。 */
  public static Standardizer fit(List<Features> x) {
    if (x.isEmpty()) {
      throw new IllegalArgumentException("特徴量が 1 件もありません");
    }
    Map<String, Double> means = new LinkedHashMap<>();
    Map<String, Double> stds = new LinkedHashMap<>();
    for (String column : x.getFirst().columns()) {
      double[] values = x.stream().mapToDouble(f -> f.value(column)).toArray();
      double mean = mean(values);
      double variance = mean(Arrays.stream(values).map(v -> (v - mean) * (v - mean)).toArray());
      double std = Math.sqrt(variance);
      means.put(column, mean);
      stds.put(column, std == 0 ? 1.0 : std);
    }
    return new Standardizer(means, stds);
  }

  private static double mean(double[] values) {
    return Arrays.stream(values).average().orElseThrow();
  }

  /** 1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。 */
  public Features transform(Features features) {
    List<String> columns = features.columns();
    double[] values = features.values();
    for (int i = 0; i < values.length; i++) {
      String column = columns.get(i);
      if (means.containsKey(column)) {
        values[i] = (values[i] - means.get(column)) / stds.get(column);
      }
    }
    return new Features(columns, values);
  }

  /** 特徴量のリストを標準化する。 */
  public List<Features> transform(List<Features> x) {
    return x.stream().map(this::transform).toList();
  }
}
