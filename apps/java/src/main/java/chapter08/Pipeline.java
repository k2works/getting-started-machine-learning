package chapter08;

import chapter02.Table;
import java.util.ArrayList;
import java.util.List;

/** 前処理を順に fit・transform してから、モデルを学習する。 */
public record Pipeline(List<Transformer> transformers, DecisionTreeClassifier model) {
  public Pipeline {
    transformers = List.copyOf(transformers);
  }

  /** Survived.csv 用のパイプライン。年齢・港の補完とダミー変数化の後に、決定木で分類する。 */
  public static Pipeline build(int maxDepth, ClassWeight classWeight) {
    return new Pipeline(
        List.of(
            new GroupMedianImputer("Age", List.of("Pclass", "Sex")),
            new MostFrequentImputer("Embarked"),
            new DummyEncoder(List.of("Sex", "Embarked"))),
        new DecisionTreeClassifier(maxDepth, classWeight));
  }

  /** 訓練データで前処理とモデルを学習する。前処理は、前の前処理で変換したデータで fit する。 */
  public FittedPipeline fit(Table x, List<Integer> t) {
    List<FittedTransformer> fitted = new ArrayList<>();
    Table prepared = x;
    for (Transformer transformer : transformers) {
      FittedTransformer fittedTransformer = transformer.fit(prepared);
      fitted.add(fittedTransformer);
      prepared = fittedTransformer.transform(prepared);
    }
    return new FittedPipeline(fitted, model.fit(FittedPipeline.toFeatures(prepared), t));
  }
}
