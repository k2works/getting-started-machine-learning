package chapter15;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PredictionServiceTest {
  private static final Movie MOVIE = new Movie(100.0, 200.0, 300.0, 1);
  private static final Passenger PASSENGER =
      new Passenger(1, "female", OptionalDouble.of(30.0), 0, 0, 50.0, Optional.of("S"));

  @Test
  @DisplayName("映画の特徴量から興行収入を予測する")
  void predictsSales() throws ModelNotFoundException {
    var service = new PredictionService(Stubs.store(movie -> 1234.5, passenger -> true));

    assertThat(service.predictSales(MOVIE)).isEqualTo(new SalesPrediction(1234.5));
  }

  @Test
  @DisplayName("生存と判定されれば生存と予測する")
  void survives() throws ModelNotFoundException {
    var service = new PredictionService(Stubs.store(movie -> 0.0, passenger -> true));

    assertThat(service.predictSurvival(PASSENGER)).isEqualTo(new SurvivalPrediction(true));
  }

  @Test
  @DisplayName("死亡と判定されれば死亡と予測する")
  void dies() throws ModelNotFoundException {
    var service = new PredictionService(Stubs.store(movie -> 0.0, passenger -> false));

    assertThat(service.predictSurvival(PASSENGER)).isEqualTo(new SurvivalPrediction(false));
  }

  @Test
  @DisplayName("モデルが無ければ ModelNotFoundException を投げる")
  void missingModel() {
    var service = new PredictionService(Stubs.emptyStore());

    assertThatThrownBy(() -> service.predictSales(MOVIE))
        .isInstanceOf(ModelNotFoundException.class)
        .hasMessage("学習済みモデル cinema が見つかりません");
  }

  @Test
  @DisplayName("モデルを読み込めればそれぞれ true を返す")
  void healthy() {
    var service = new PredictionService(Stubs.store(movie -> 0.0, passenger -> true));

    assertThat(service.health())
        .containsExactly(Map.entry("cinema", true), Map.entry("survived", true));
  }

  @Test
  @DisplayName("モデルが無ければそれぞれ false を返す")
  void unhealthy() {
    assertThat(new PredictionService(Stubs.emptyStore()).health())
        .containsExactly(Map.entry("cinema", false), Map.entry("survived", false));
  }
}
