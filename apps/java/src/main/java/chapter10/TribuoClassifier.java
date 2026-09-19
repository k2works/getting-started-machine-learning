package chapter10;

import chapter02.Features;
import chapter03.TribuoTrees;
import java.util.List;
import org.tribuo.Model;
import org.tribuo.Trainer;
import org.tribuo.classification.Label;
import org.tribuo.classification.dtree.CARTClassificationTrainer;
import org.tribuo.classification.ensemble.VotingCombiner;
import org.tribuo.common.tree.RandomForestTrainer;

/** Tribuo のトレーナーを Classifier に合わせるアダプター。 */
public final class TribuoClassifier implements Classifier {
  /** 深さを制限しないことを表す値 */
  public static final int UNLIMITED = Integer.MAX_VALUE;

  // 分割ごとに、半分の特徴量から分割の候補を選ぶ
  private static final float FRACTION_FEATURES_IN_SPLIT = 0.5f;

  private final Trainer<Label> trainer;
  private Model<Label> model;

  public TribuoClassifier(Trainer<Label> trainer) {
    this.trainer = trainer;
  }

  /** ブートストラップ標本で nEstimators 本の CART を学習し、多数決する Tribuo のランダムフォレスト。 */
  public static TribuoClassifier randomForest(int nEstimators, int maxDepth, long seed) {
    var tree = new CARTClassificationTrainer(maxDepth, FRACTION_FEATURES_IN_SPLIT, seed);
    return new TribuoClassifier(
        new RandomForestTrainer<>(tree, new VotingCombiner(), nEstimators, seed));
  }

  @Override
  public TribuoClassifier fit(List<Features> x, List<String> t) {
    model = trainer.train(TribuoTrees.toDataset(x, t));
    return this;
  }

  @Override
  public List<String> predict(List<Features> x) {
    if (model == null) {
      throw new IllegalStateException("fit で学習してから predict を呼んでください");
    }
    return TribuoTrees.predict(model, x);
  }
}
