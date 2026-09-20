package machinelearning.chapter11

import machinelearning.chapter02.Features
import machinelearning.chapter03.{DecisionTrees, Tree}
import machinelearning.chapter07.{LinearModel, LinearRegression}

/** 第 3 章の自作の決定木を、この章の Model として使うアダプター。
  *
  * 第 3 章の `DecisionTree` クラスは学習した木を var で持つが、ここでは `fit` が学習済みの木を持つ新しい値を返すので、
  * 同じモデルを分割の数だけ使い回しても前の学習が残らない。
  */
case class DecisionTreeModel(maxDepth: Int, tree: Option[Tree] = None) extends Model[String]:

  override def fit(x: Vector[Features], t: Vector[String]): DecisionTreeModel =
    copy(tree = Some(DecisionTrees.build(x, t, Some(maxDepth))))

  override def predict(x: Vector[Features]): Vector[String] =
    val fitted = tree.getOrElse(throw IllegalStateException("fit で学習してから predict を呼んでください"))
    x.map(DecisionTrees.predictOne(fitted, _))

/** 第 7 章の正規方程式による線形回帰を、この章の Model として使うアダプター。 */
case class LinearRegressionModel(model: Option[LinearModel] = None) extends Model[Double]:

  override def fit(x: Vector[Features], t: Vector[Double]): LinearRegressionModel =
    copy(model = Some(LinearRegression.fit(x, t)))

  override def predict(x: Vector[Features]): Vector[Double] =
    model.getOrElse(throw IllegalStateException("fit で学習してから predict を呼んでください")).predict(x)
