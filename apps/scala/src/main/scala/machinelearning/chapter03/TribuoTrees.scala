package machinelearning.chapter03

import machinelearning.chapter02.Features
import org.tribuo.classification.dtree.CARTClassificationTrainer
import org.tribuo.classification.dtree.impurity.GiniIndex
import org.tribuo.classification.{Label, LabelFactory}
import org.tribuo.datasource.ListDataSource
import org.tribuo.impl.ArrayExample
import org.tribuo.provenance.SimpleDataSourceProvenance
import org.tribuo.{Example, Model, MutableDataset}
import scala.jdk.CollectionConverters.*

/** 特徴量を Tribuo の事例に変え、Tribuo の CART で学習・予測する。 */
object TribuoTrees:
  /** 深さを制限しないことを表す値 */
  val Unlimited: Int = Int.MaxValue

  private val labelFactory = LabelFactory()

  /** 子の節に必要な事例の重みの最小値。1 にすると、自作の木と同じく 1 件になるまで分けられる。 */
  private val MinChildWeight = 1.0f

  /** 特徴量と正解ラベルを、Tribuo のデータセットにする。 */
  def toDataset(x: Vector[Features], t: Vector[String]): MutableDataset[Label] =
    val examples = x.zip(t).map((features, label) => toExample(features, Label(label)))
    val provenance = SimpleDataSourceProvenance("features", labelFactory)
    MutableDataset(ListDataSource(examples.asJava, labelFactory, provenance))

  /** ジニ不純度で分割する CART を学習する。 */
  def train(x: Vector[Features], t: Vector[String], maxDepth: Int): Model[Label] =
    val trainer = CARTClassificationTrainer(maxDepth, MinChildWeight, 0.0f, 1.0f, GiniIndex(), 0L)
    trainer.train(toDataset(x, t))

  /** 学習したモデルで、特徴量ごとのラベルを予測する。 */
  def predict(model: Model[Label], x: Vector[Features]): Vector[String] =
    x.map(features =>
      model.predict(toExample(features, LabelFactory.UNKNOWN_LABEL)).getOutput.getLabel
    )

  private def toExample(features: Features, label: Label): Example[Label] =
    ArrayExample[Label](label, features.columns.toArray, features.values.toArray)
