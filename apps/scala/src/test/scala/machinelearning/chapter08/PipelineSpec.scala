package machinelearning.chapter08

import machinelearning.chapter02.TrainTestSplit
import machinelearning.chapter08.Passengers.{newPassengers, trainT, trainX}
import org.scalatest.funsuite.AnyFunSuite

class PipelineSpec extends AnyFunSuite:

  test("CSV の行から特徴量の列の表と Survived 列の正解ラベルを作る") {
    val csv = Tables.table(
      "PassengerId,Survived,Pclass,Sex,Age,SibSp,Parch,Ticket,Fare,Cabin,Embarked",
      "1,1,2,female,28,0,1,X-2,15,,C"
    )

    assert(
      SurvivedData.features(csv.rows).columns
        === Vector("Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked")
    )
    assert(SurvivedData.target(csv.rows) === Vector(1))
  }

  test("欠損値を含むデータで学習して予測できる") {
    val pipeline = Pipeline.build(Some(3), ClassWeight.Unweighted)

    val fitted = pipeline.fit(trainX, trainT)

    assert(fitted.predict(newPassengers) === Vector(1, 0))
  }

  test("クラスの重みと深さをモデルに渡す") {
    val pipeline = Pipeline.build(Some(5), ClassWeight.Balanced)

    assert(pipeline.model === DecisionTreeClassifier(Some(5), ClassWeight.Balanced))
  }

  test("正解率と、見つけた生存者の数を求める") {
    val split = TrainTestSplit(trainX.rows, newPassengers.rows, trainT, Vector(1, 1))
    val pipeline = Pipeline.build(Some(3), ClassWeight.Unweighted).fit(trainX, trainT)

    val evaluation = Evaluation.evaluate(pipeline, split)

    assert(evaluation === Evaluation(1.0, 0.5, 1, 2))
  }

  test("前処理を合成した変換は、パイプラインの transform と同じ表を返す") {
    val fitted = Pipeline.build(Some(3), ClassWeight.Unweighted).fit(trainX, trainT)

    val composed = fitted.transformers.map(_.transform).reduce(_ andThen _)

    assert(composed(newPassengers) === fitted.transform(newPassengers))
  }
