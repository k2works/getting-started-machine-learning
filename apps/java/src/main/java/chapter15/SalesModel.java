package chapter15;

/** 映画の特徴量から興行収入を予測するモデルの約束。 */
@FunctionalInterface
public interface SalesModel {
  double predictSales(Movie movie);
}
