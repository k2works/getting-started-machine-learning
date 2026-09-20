package machinelearning.chapter07

import machinelearning.chapter02.Features
import org.tribuo.datasource.ListDataSource
import org.tribuo.impl.ArrayExample
import org.tribuo.provenance.SimpleDataSourceProvenance
import org.tribuo.regression.{RegressionFactory, Regressor}
import org.tribuo.{Example, Model, MutableDataset, Trainer}
import scala.jdk.CollectionConverters.*

/** 特徴量を Tribuo の回帰の事例に変え、Tribuo のトレーナーで学習・予測する。 */
object TribuoRegression:
  /** 予測する数値の名前 */
  val OutputName: String = Cinema.Target

  private val regressionFactory = RegressionFactory()

  /** 特徴量と実測値を、Tribuo のデータセットにする。 */
  def toDataset(x: Vector[Features], t: Vector[Double]): MutableDataset[Regressor] =
    val examples =
      x.zip(t).map((features, value) => toExample(features, Regressor(OutputName, value)))
    val provenance = SimpleDataSourceProvenance("features", regressionFactory)
    MutableDataset(ListDataSource(examples.asJava, regressionFactory, provenance))

  /** 渡したトレーナーで学習する。 */
  def train(
      trainer: Trainer[Regressor],
      x: Vector[Features],
      t: Vector[Double]
  ): Model[Regressor] =
    trainer.train(toDataset(x, t))

  /** 学習したモデルで、特徴量ごとの数値を予測する。 */
  def predict(model: Model[Regressor], x: Vector[Features]): Vector[Double] =
    x.map(features =>
      model
        .predict(toExample(features, RegressionFactory.UNKNOWN_REGRESSOR))
        .getOutput
        .getValues()(0)
    )

  private def toExample(features: Features, output: Regressor): Example[Regressor] =
    ArrayExample[Regressor](output, features.columns.toArray, features.values.toArray)
