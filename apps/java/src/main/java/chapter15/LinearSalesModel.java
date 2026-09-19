package chapter15;

import chapter02.Features;
import chapter07.Cinema;
import chapter07.LinearModel;

/** 第 7 章の線形回帰モデルを、ドメインの SalesModel の約束に合わせるアダプター。 */
public record LinearSalesModel(LinearModel model) implements SalesModel {
  @Override
  public double predictSales(Movie movie) {
    double[] values = {movie.sns1(), movie.sns2(), movie.actor(), movie.original()};
    return model.predict(new Features(Cinema.FEATURES, values));
  }
}
