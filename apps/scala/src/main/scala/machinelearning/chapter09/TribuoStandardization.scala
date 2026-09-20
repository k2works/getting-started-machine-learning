package machinelearning.chapter09

import org.tribuo.transform.transformations.MeanStdDevTransformation

/** Tribuo の MeanStdDevTransformation で 1 列を標準化する。自作の [[Standardizer]] と突き合わせるために使う。 */
object TribuoStandardization:

  /** 訓練データの値から平均と標準偏差を求め、別の値を標準化する。 */
  def standardize(train: Vector[Double], values: Vector[Double]): Vector[Double] =
    val statistics = MeanStdDevTransformation().createStats()
    train.foreach(statistics.observeValue)
    val transformer = statistics.generateTransformer()
    values.map(transformer.transform)
