package chapter11;

import chapter02.Features;
import chapter07.LinearModel;
import chapter07.LinearRegression;
import java.util.List;

/** 第 7 章の正規方程式による線形回帰を、この章の Model として使うアダプター。 */
public final class LinearRegressionModel implements Model<Double> {
  private LinearModel model;

  @Override
  public void fit(List<Features> x, List<Double> t) {
    model = LinearRegression.fit(x, t);
  }

  @Override
  public List<Double> predict(List<Features> x) {
    if (model == null) {
      throw new IllegalStateException("fit で学習してから predict を呼んでください");
    }
    return model.predict(x);
  }
}
