package chapter08;

import static chapter08.Passengers.newPassengers;
import static chapter08.Passengers.trainT;
import static chapter08.Passengers.trainX;
import static org.assertj.core.api.Assertions.assertThat;

import chapter02.Table;
import chapter02.TrainTestSplit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PipelineTest {
  @Test
  @DisplayName("CSV の行から特徴量の列の表と Survived 列の正解ラベルを作る")
  void featuresAndTarget() {
    Table csv =
        Tables.table(
            "PassengerId,Survived,Pclass,Sex,Age,SibSp,Parch,Ticket,Fare,Cabin,Embarked",
            "1,1,2,female,28,0,1,X-2,15,,C");

    assertThat(SurvivedData.features(csv.rows()).columns())
        .containsExactly("Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked");
    assertThat(SurvivedData.target(csv.rows())).containsExactly(1);
  }

  @Test
  @DisplayName("欠損値を含むデータで学習して予測できる")
  void fitAndPredict() {
    var pipeline = Pipeline.build(3, ClassWeight.NONE);

    var fitted = pipeline.fit(trainX(), trainT());

    assertThat(fitted.predict(newPassengers())).containsExactly(1, 0);
  }

  @Test
  @DisplayName("クラスの重みと深さをモデルに渡す")
  void modelSettings() {
    var pipeline = Pipeline.build(5, ClassWeight.BALANCED);

    assertThat(pipeline.model()).isEqualTo(new DecisionTreeClassifier(5, ClassWeight.BALANCED));
  }

  @Test
  @DisplayName("正解率と、見つけた生存者の数を求める")
  void evaluate() {
    var split =
        new TrainTestSplit<>(trainX().rows(), newPassengers().rows(), trainT(), List.of(1, 1));
    var pipeline = Pipeline.build(3, ClassWeight.NONE).fit(trainX(), trainT());

    var evaluation = Evaluation.evaluate(pipeline, split);

    assertThat(evaluation).isEqualTo(new Evaluation(1.0, 0.5, 1, 2));
  }
}
