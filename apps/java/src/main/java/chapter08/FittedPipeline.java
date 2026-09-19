package chapter08;

import chapter02.Features;
import chapter02.Row;
import chapter02.Table;
import java.io.Serializable;
import java.util.List;

/** 学習済みの前処理とモデル。予測するときは前処理の transform だけを使う。 */
public record FittedPipeline(List<FittedTransformer> transformers, FittedDecisionTree model)
    implements Serializable {
  public FittedPipeline {
    transformers = List.copyOf(transformers);
  }

  /** 学習済みの前処理を順に合成して、データを変換する。 */
  public Table transform(Table x) {
    return transformers.stream()
        .reduce(FittedTransformer.identity(), FittedTransformer::andThen)
        .transform(x);
  }

  /** 前処理をして、モデルに渡す特徴量にする。 */
  public List<Features> features(Table x) {
    return toFeatures(transform(x));
  }

  /** 前処理をしてから、1 行ごとのラベル（1 が生存、0 が死亡）を予測する。 */
  public List<Integer> predict(Table x) {
    return model.predict(features(x));
  }

  /** 前処理の済んだ表を特徴量にする。欠損値が残っていれば失敗する。 */
  static List<Features> toFeatures(Table x) {
    return x.rows().stream().map(row -> toFeatures(x.columns(), row)).toList();
  }

  private static Features toFeatures(List<String> columns, Row row) {
    double[] values = new double[columns.size()];
    for (int i = 0; i < values.length; i++) {
      String column = columns.get(i);
      values[i] =
          row.number(column)
              .orElseThrow(() -> new IllegalArgumentException("欠損値が残っています: " + column));
    }
    return new Features(columns, values);
  }
}
