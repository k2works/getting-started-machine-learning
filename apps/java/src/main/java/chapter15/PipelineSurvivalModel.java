package chapter15;

import chapter02.Row;
import chapter02.Table;
import chapter08.FittedPipeline;
import chapter08.SurvivedData;
import java.util.List;
import java.util.Map;

/** 第 8 章の学習済みパイプラインを、ドメインの SurvivalModel の約束に合わせるアダプター。 */
public record PipelineSurvivalModel(FittedPipeline pipeline) implements SurvivalModel {
  @Override
  public boolean survives(Passenger passenger) {
    // パイプラインは CSV と同じセルの文字列を受け取る。分からない値は空欄にする
    Row row =
        new Row(
            Map.of(
                "Pclass", String.valueOf(passenger.pclass()),
                "Sex", passenger.sex(),
                "Age",
                    passenger.age().isPresent()
                        ? String.valueOf(passenger.age().getAsDouble())
                        : "",
                "SibSp", String.valueOf(passenger.sibSp()),
                "Parch", String.valueOf(passenger.parch()),
                "Fare", String.valueOf(passenger.fare()),
                "Embarked", passenger.embarked().orElse("")));
    return pipeline.predict(new Table(SurvivedData.FEATURES, List.of(row))).getFirst() == 1;
  }
}
