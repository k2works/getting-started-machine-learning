package chapter10;

import chapter02.Features;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/** ソフトマックスと勾配降下法によるロジスティック回帰。 */
public final class LogisticRegression implements Classifier {
  private static final double DEFAULT_LEARNING_RATE = 1.0;
  private static final int DEFAULT_EPOCHS = 5000;
  private static final double EPSILON = 1e-12;

  private final double learningRate;
  private final int epochs;
  private List<String> classes = List.of();
  // weights[特徴量][品種]
  private double[][] weights = new double[0][0];
  private double[] bias = new double[0];
  private List<Double> losses = List.of();

  /** 学習率 1.0、繰り返し 5000 回のロジスティック回帰。 */
  public LogisticRegression() {
    this(DEFAULT_LEARNING_RATE, DEFAULT_EPOCHS);
  }

  public LogisticRegression(double learningRate, int epochs) {
    this.learningRate = learningRate;
    this.epochs = epochs;
  }

  /** スコアを、合計が 1 になる確率に変換する。最大値を引いてから exp を求めるので、大きな値でもあふれない。 */
  public static double[] softmax(double[] z) {
    double max = Arrays.stream(z).max().orElseThrow();
    double[] exps = Arrays.stream(z).map(v -> Math.exp(v - max)).toArray();
    double total = Arrays.stream(exps).sum();
    return Arrays.stream(exps).map(e -> e / total).toArray();
  }

  /** 交差エントロピー。正解の品種の確率の対数の平均に、マイナスを付けたもの。 */
  public static double crossEntropy(List<double[]> probabilities, int[] targets) {
    return -IntStream.range(0, probabilities.size())
            .mapToDouble(i -> Math.log(probabilities.get(i)[targets[i]] + EPSILON))
            .sum()
        / probabilities.size();
  }

  /** 学習した品種の並び（名前の順）。 */
  public List<String> classes() {
    return classes;
  }

  /** 繰り返しごとの訓練データの損失。 */
  public List<Double> losses() {
    return losses;
  }

  private double[] scores(double[] row) {
    double[] scores = bias.clone();
    for (int f = 0; f < row.length; f++) {
      for (int k = 0; k < scores.length; k++) {
        scores[k] += row[f] * weights[f][k];
      }
    }
    return scores;
  }

  /** バッチ勾配降下法で重みと切片を学習する。 */
  @Override
  public LogisticRegression fit(List<Features> x, List<String> t) {
    List<double[]> rows = x.stream().map(Features::values).toList();
    classes = t.stream().distinct().sorted().toList();
    int[] targets = t.stream().mapToInt(classes::indexOf).toArray();
    int nFeatures = x.getFirst().columns().size();
    weights = new double[nFeatures][classes.size()];
    bias = new double[classes.size()];
    List<Double> recorded = new ArrayList<>();
    for (int epoch = 0; epoch < epochs; epoch++) {
      List<double[]> probabilities = rows.stream().map(row -> softmax(scores(row))).toList();
      recorded.add(crossEntropy(probabilities, targets));
      // 確率 − 正解（正解の品種だけ 1 を引く）
      List<double[]> errors =
          IntStream.range(0, rows.size())
              .mapToObj(
                  i -> {
                    double[] error = probabilities.get(i).clone();
                    error[targets[i]] -= 1.0;
                    return error;
                  })
              .toList();
      update(rows, errors);
    }
    losses = List.copyOf(recorded);
    return this;
  }

  private void update(List<double[]> rows, List<double[]> errors) {
    int n = rows.size();
    for (int k = 0; k < classes.size(); k++) {
      for (int f = 0; f < weights.length; f++) {
        double gradient = 0.0;
        for (int i = 0; i < n; i++) {
          gradient += rows.get(i)[f] * errors.get(i)[k];
        }
        weights[f][k] -= learningRate * gradient / n;
      }
      double biasGradient = 0.0;
      for (double[] error : errors) {
        biasGradient += error[k];
      }
      bias[k] -= learningRate * biasGradient / n;
    }
  }

  /** スコアが最大の品種を予測する。 */
  @Override
  public List<String> predict(List<Features> x) {
    if (classes.isEmpty()) {
      throw new IllegalStateException("fit で学習してから predict を呼んでください");
    }
    return x.stream().map(features -> classes.get(argmax(scores(features.values())))).toList();
  }

  private static int argmax(double[] values) {
    int best = 0;
    for (int i = 1; i < values.length; i++) {
      if (values[i] > values[best]) {
        best = i;
      }
    }
    return best;
  }
}
