package chapter03;

import chapter02.Features;
import java.util.List;
import java.util.stream.IntStream;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.classification.dtree.CARTClassificationTrainer;
import org.tribuo.classification.dtree.impurity.GiniIndex;
import org.tribuo.datasource.ListDataSource;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;

/** 特徴量を Tribuo の事例に変え、Tribuo の CART で学習・予測する。 */
public final class TribuoTrees {
  /** 深さを制限しないことを表す値 */
  public static final int UNLIMITED = Integer.MAX_VALUE;

  private static final LabelFactory LABEL_FACTORY = new LabelFactory();

  /** 子の節に必要な事例の重みの最小値。1 にすると、自作の木と同じく 1 件になるまで分けられる。 */
  private static final float MIN_CHILD_WEIGHT = 1.0f;

  private TribuoTrees() {}

  private static Example<Label> toExample(Features features, Label label) {
    return new ArrayExample<>(label, features.columns().toArray(String[]::new), features.values());
  }

  /** 特徴量と正解ラベルを、Tribuo のデータセットにする。 */
  public static MutableDataset<Label> toDataset(List<Features> x, List<String> t) {
    List<Example<Label>> examples =
        IntStream.range(0, x.size())
            .mapToObj(i -> toExample(x.get(i), new Label(t.get(i))))
            .toList();
    var provenance = new SimpleDataSourceProvenance("features", LABEL_FACTORY);
    return new MutableDataset<>(new ListDataSource<>(examples, LABEL_FACTORY, provenance));
  }

  /** ジニ不純度で分割する CART を学習する。 */
  public static Model<Label> train(List<Features> x, List<String> t, int maxDepth) {
    var trainer =
        new CARTClassificationTrainer(maxDepth, MIN_CHILD_WEIGHT, 0.0f, 1.0f, new GiniIndex(), 0L);
    return trainer.train(toDataset(x, t));
  }

  /** 学習したモデルで、特徴量ごとのラベルを予測する。 */
  public static List<String> predict(Model<Label> model, List<Features> x) {
    return x.stream()
        .map(features -> model.predict(toExample(features, LabelFactory.UNKNOWN_LABEL)))
        .map(prediction -> prediction.getOutput().getLabel())
        .toList();
  }
}
