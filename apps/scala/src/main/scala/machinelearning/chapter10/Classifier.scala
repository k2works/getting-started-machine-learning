package machinelearning.chapter10

import machinelearning.chapter01.KinokoTakenoko
import machinelearning.chapter02.Features

/** 分類器の約束。学習して、予測する。 */
trait Classifier:
  /** 訓練データから学習する。 */
  def fit(x: Vector[Features], t: Vector[String]): Classifier

  /** 特徴量ごとのラベルを予測する。 */
  def predict(x: Vector[Features]): Vector[String]

object Classifier:
  /** 訓練データとテストデータの正解率を求める。 */
  def score(
      model: Classifier,
      xTrain: Vector[Features],
      tTrain: Vector[String],
      xTest: Vector[Features],
      tTest: Vector[String]
  ): (Double, Double) =
    val fitted = model.fit(xTrain, tTrain)
    (
      KinokoTakenoko.accuracy(fitted.predict(xTrain), tTrain),
      KinokoTakenoko.accuracy(fitted.predict(xTest), tTest)
    )
