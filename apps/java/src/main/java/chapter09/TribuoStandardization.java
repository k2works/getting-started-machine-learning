package chapter09;

import java.util.List;
import org.tribuo.transform.TransformStatistics;
import org.tribuo.transform.Transformer;
import org.tribuo.transform.transformations.MeanStdDevTransformation;

/** Tribuo の MeanStdDevTransformation で 1 列を標準化する。自作の {@link Standardizer} と突き合わせるために使う。 */
public final class TribuoStandardization {
  private TribuoStandardization() {}

  /** 訓練データの値から平均と標準偏差を求め、別の値を標準化する。 */
  public static List<Double> standardize(List<Double> train, List<Double> values) {
    TransformStatistics statistics = new MeanStdDevTransformation().createStats();
    train.forEach(statistics::observeValue);
    Transformer transformer = statistics.generateTransformer();
    return values.stream().map(transformer::transform).toList();
  }
}
