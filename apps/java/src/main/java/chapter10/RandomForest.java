package chapter10;

import chapter02.Features;
import chapter03.DecisionTree;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

/** 第 3 章の決定木をブートストラップ標本と特徴量の部分集合で学習し、多数決で予測するランダムフォレスト。 */
public final class RandomForest implements Classifier {
  private static final int UNLIMITED = -1;

  private final int nEstimators;
  private final int maxFeatures;
  private final int maxDepth;
  private final long seed;
  private List<FittedTree> trees = List.of();

  private RandomForest(int nEstimators, int maxFeatures, int maxDepth, long seed) {
    this.nEstimators = nEstimators;
    this.maxFeatures = maxFeatures;
    this.maxDepth = maxDepth;
    this.seed = seed;
  }

  /** 深さを制限しない決定木の森。 */
  public static RandomForest of(int nEstimators, int maxFeatures, long seed) {
    return new RandomForest(nEstimators, maxFeatures, UNLIMITED, seed);
  }

  /** 深さの上限を指定した決定木の森。 */
  public static RandomForest withMaxDepth(
      int nEstimators, int maxFeatures, int maxDepth, long seed) {
    if (maxDepth < 0) {
      throw new IllegalArgumentException("深さの上限は 0 以上にしてください");
    }
    return new RandomForest(nEstimators, maxFeatures, maxDepth, seed);
  }

  /** サンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ。 */
  public static List<String> majorityVote(List<List<String>> votes) {
    return IntStream.range(0, votes.getFirst().size())
        .mapToObj(sample -> mostCommon(votes.stream().map(vote -> vote.get(sample)).toList()))
        .toList();
  }

  private static String mostCommon(List<String> labels) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    labels.forEach(label -> counts.merge(label, 1, Integer::sum));
    String best = labels.getFirst();
    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
      if (entry.getValue() > counts.get(best)) {
        best = entry.getKey();
      }
    }
    return best;
  }

  /** 0 から size - 1 までの行番号を、重複を許して size 個選ぶ。 */
  public static List<Integer> bootstrapSample(int size, Random random) {
    return IntStream.range(0, size).mapToObj(i -> random.nextInt(size)).toList();
  }

  /** 特徴量から、指定した列だけを取り出す。 */
  static Features selectColumns(Features features, List<String> columns) {
    return new Features(columns, columns.stream().mapToDouble(features::value).toArray());
  }

  static List<Features> selectColumns(List<Features> x, List<String> columns) {
    return x.stream().map(features -> selectColumns(features, columns)).toList();
  }

  private DecisionTree newTree() {
    return maxDepth == UNLIMITED ? DecisionTree.unlimited() : DecisionTree.withMaxDepth(maxDepth);
  }

  /** 学習した決定木。 */
  public List<FittedTree> trees() {
    return trees;
  }

  @Override
  public RandomForest fit(List<Features> x, List<String> t) {
    Random random = new Random(seed);
    List<String> allColumns = x.getFirst().columns();
    List<FittedTree> fitted = new ArrayList<>();
    for (int i = 0; i < nEstimators; i++) {
      List<Integer> rows = bootstrapSample(x.size(), random);
      List<String> shuffled = new ArrayList<>(allColumns);
      Collections.shuffle(shuffled, random);
      List<String> chosen = shuffled.subList(0, maxFeatures);
      // 列の順は元のまま残す
      List<String> columns = allColumns.stream().filter(chosen::contains).toList();
      List<Features> sampleX =
          rows.stream().map(row -> selectColumns(x.get(row), columns)).toList();
      List<String> sampleT = rows.stream().map(t::get).toList();
      fitted.add(new FittedTree(columns, rows, newTree().fit(sampleX, sampleT)));
    }
    trees = List.copyOf(fitted);
    return this;
  }

  @Override
  public List<String> predict(List<Features> x) {
    if (trees.isEmpty()) {
      throw new IllegalStateException("fit で学習してから predict を呼んでください");
    }
    return majorityVote(
        trees.stream()
            .map(tree -> tree.model().predict(selectColumns(x, tree.columns())))
            .toList());
  }
}
