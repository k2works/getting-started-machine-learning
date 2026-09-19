package chapter13;

import chapter07.Matrix;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.tribuo.math.la.DenseMatrix;
import org.tribuo.math.la.DenseMatrix.EigenDecomposition;

/** 主成分分析。 */
public final class Pca {
  private Pca() {}

  /** 列ごとの平均。 */
  public static double[] columnMeans(Matrix x) {
    double[] means = new double[x.columnCount()];
    for (int j = 0; j < means.length; j++) {
      means[j] = Arrays.stream(x.column(j)).average().orElseThrow();
    }
    return means;
  }

  /** 各列から平均を引く（中心化）。 */
  private static Matrix center(Matrix x, double[] means) {
    double[][] centered = x.toArray();
    for (double[] row : centered) {
      for (int j = 0; j < row.length; j++) {
        row[j] -= means[j];
      }
    }
    return Matrix.of(centered);
  }

  /** 列ごとの分散と、2 列ずつの共分散を並べた行列。件数から 1 を引いた数で割る。 */
  public static Matrix covarianceMatrix(Matrix x) {
    Matrix c = center(x, columnMeans(x));
    double[][] product = c.transpose().times(c).toArray();
    int n = x.rowCount();
    for (double[] row : product) {
      for (int j = 0; j < row.length; j++) {
        row[j] /= n - 1;
      }
    }
    return Matrix.of(product);
  }

  /** 分散共分散行列を固有値分解し、寄与率の大きい順に nComponents 個の主成分を求める。 */
  public static PcaModel fit(Matrix x, int nComponents) {
    DenseMatrix covariance = DenseMatrix.createDenseMatrix(covarianceMatrix(x).toArray());
    EigenDecomposition eigen = covariance.eigenDecomposition().orElseThrow();
    double[] eigenvalues = eigen.eigenvalues().toArray();
    double total = Arrays.stream(eigenvalues).sum();
    double[][] components = new double[nComponents][];
    for (int i = 0; i < nComponents; i++) {
      components[i] = eigen.getEigenVector(i).toArray();
    }
    List<Double> variances = Arrays.stream(eigenvalues).limit(nComponents).boxed().toList();
    return new PcaModel(
        Arrays.stream(columnMeans(x)).boxed().toList(),
        normalizeSigns(Matrix.of(components)),
        variances,
        variances.stream().map(v -> v / total).toList());
  }

  /** 固有ベクトルは符号が逆でも同じ向きを表すので、絶対値が最大の要素が正になるようにそろえる。 */
  public static Matrix normalizeSigns(Matrix components) {
    double[][] rows = components.toArray();
    for (double[] row : rows) {
      double largest = row[0];
      for (double value : row) {
        if (Math.abs(value) > Math.abs(largest)) {
          largest = value;
        }
      }
      double sign = Math.signum(largest);
      for (int j = 0; j < row.length; j++) {
        row[j] *= sign;
      }
    }
    return Matrix.of(rows);
  }

  /** 平均を引いてから、データを主成分の向きに射影する。 */
  public static Matrix transform(PcaModel model, Matrix x) {
    double[] means = model.mean().stream().mapToDouble(Double::doubleValue).toArray();
    return center(x, means).times(model.components().transpose());
  }

  /** 累積寄与率がしきい値に届くまでの主成分の数。 */
  public static int componentsNeeded(List<Double> ratios, double threshold) {
    double cumulative = 0;
    for (int i = 0; i < ratios.size(); i++) {
      cumulative += ratios.get(i);
      if (cumulative >= threshold) {
        return i + 1;
      }
    }
    return ratios.size();
  }

  /** 主成分の係数の絶対値が大きい順に、列名と係数を k 個返す。 */
  public static List<Loading> topLoadings(double[] component, List<String> columns, int k) {
    return IntStream.range(0, component.length)
        .mapToObj(j -> new Loading(columns.get(j), component[j]))
        .sorted(Comparator.comparingDouble((Loading l) -> Math.abs(l.value())).reversed())
        .limit(k)
        .toList();
  }
}
