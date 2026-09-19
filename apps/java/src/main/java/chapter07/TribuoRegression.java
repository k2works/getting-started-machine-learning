package chapter07;

import chapter02.Features;
import java.util.List;
import java.util.stream.IntStream;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.Trainer;
import org.tribuo.datasource.ListDataSource;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.RegressionFactory;
import org.tribuo.regression.Regressor;

/** 特徴量を Tribuo の回帰の事例に変え、Tribuo のトレーナーで学習・予測する。 */
public final class TribuoRegression {
  /** 予測する数値の名前 */
  public static final String OUTPUT_NAME = Cinema.TARGET;

  private static final RegressionFactory REGRESSION_FACTORY = new RegressionFactory();

  private TribuoRegression() {}

  private static Example<Regressor> toExample(Features features, Regressor output) {
    return new ArrayExample<>(output, features.columns().toArray(String[]::new), features.values());
  }

  /** 特徴量と実測値を、Tribuo のデータセットにする。 */
  public static MutableDataset<Regressor> toDataset(List<Features> x, List<Double> t) {
    List<Example<Regressor>> examples =
        IntStream.range(0, x.size())
            .mapToObj(i -> toExample(x.get(i), new Regressor(OUTPUT_NAME, t.get(i))))
            .toList();
    var provenance = new SimpleDataSourceProvenance("features", REGRESSION_FACTORY);
    return new MutableDataset<>(new ListDataSource<>(examples, REGRESSION_FACTORY, provenance));
  }

  /** 渡したトレーナーで学習する。 */
  public static Model<Regressor> train(
      Trainer<Regressor> trainer, List<Features> x, List<Double> t) {
    return trainer.train(toDataset(x, t));
  }

  /** 学習したモデルで、特徴量ごとの数値を予測する。 */
  public static List<Double> predict(Model<Regressor> model, List<Features> x) {
    return x.stream()
        .map(features -> model.predict(toExample(features, RegressionFactory.UNKNOWN_REGRESSOR)))
        .map(prediction -> prediction.getOutput().getValues()[0])
        .toList();
  }
}
