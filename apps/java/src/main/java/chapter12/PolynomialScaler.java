package chapter12;

import chapter02.Features;
import chapter07.Matrix;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 列ごとに標準化してから、2 乗の列と 2 列の積の列を加える。訓練データで {@link #fit} し、同じ平均と標準偏差で変換する。
 *
 * @param inputNames 元の特徴量の列名
 * @param means 列ごとの平均
 * @param stds 列ごとの標準偏差（件数 n で割る母標準偏差）
 */
public record PolynomialScaler(List<String> inputNames, List<Double> means, List<Double> stds) {
  public PolynomialScaler {
    inputNames = List.copyOf(inputNames);
    means = List.copyOf(means);
    stds = List.copyOf(stds);
    if (means.size() != inputNames.size() || stds.size() != inputNames.size()) {
      throw new IllegalArgumentException("列名と平均と標準偏差の数が違います");
    }
  }

  /** 訓練データの列ごとの平均と、件数 n で割る標準偏差を求める。列名は先頭の行から取る。 */
  public static PolynomialScaler fit(List<Features> x) {
    List<String> names = x.getFirst().columns();
    List<Double> means = new ArrayList<>();
    List<Double> stds = new ArrayList<>();
    for (String name : names) {
      double[] values = x.stream().mapToDouble(f -> f.value(name)).toArray();
      double mean = Arrays.stream(values).average().orElseThrow();
      double variance =
          Arrays.stream(values).map(v -> (v - mean) * (v - mean)).sum() / values.length;
      means.add(mean);
      stds.add(Math.sqrt(variance));
    }
    return new PolynomialScaler(names, means, stds);
  }

  /** 2 次の項を作る列の組（i <= j）。{0, 0} は 1 列目の 2 乗、{0, 1} は 1 列目と 2 列目の積。 */
  private List<int[]> pairs() {
    List<int[]> pairs = new ArrayList<>();
    for (int i = 0; i < inputNames.size(); i++) {
      for (int j = i; j < inputNames.size(); j++) {
        pairs.add(new int[] {i, j});
      }
    }
    return pairs;
  }

  /** 変換後の列名。元の列、2 乗の列（"RM^2"）、積の列（"RM LSTAT"）の順。 */
  public List<String> featureNames() {
    List<String> names = new ArrayList<>(inputNames);
    for (int[] pair : pairs()) {
      String first = inputNames.get(pair[0]);
      names.add(pair[0] == pair[1] ? first + "^2" : first + " " + inputNames.get(pair[1]));
    }
    return List.copyOf(names);
  }

  /** 標準化した値と、その 2 次の項を並べた行列にする。 */
  public Matrix transform(List<Features> x) {
    List<int[]> pairs = pairs();
    double[][] rows = new double[x.size()][];
    for (int r = 0; r < x.size(); r++) {
      double[] z = new double[inputNames.size()];
      for (int i = 0; i < z.length; i++) {
        z[i] = (x.get(r).value(inputNames.get(i)) - means.get(i)) / stds.get(i);
      }
      double[] row = Arrays.copyOf(z, z.length + pairs.size());
      for (int k = 0; k < pairs.size(); k++) {
        row[z.length + k] = z[pairs.get(k)[0]] * z[pairs.get(k)[1]];
      }
      rows[r] = row;
    }
    return Matrix.of(rows);
  }
}
