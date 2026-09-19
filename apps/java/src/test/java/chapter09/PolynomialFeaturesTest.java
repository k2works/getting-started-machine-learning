package chapter09;

import static org.assertj.core.api.Assertions.assertThat;

import chapter02.Features;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PolynomialFeaturesTest {
  @Test
  @DisplayName("1 列なら元の列と 2 乗の列を返す")
  void oneColumn() {
    List<Features> features =
        PolynomialFeatures.expand(List.of(Samples.rm(2), Samples.rm(3)), List.of("RM"));

    assertThat(features.getFirst().columns()).containsExactly("RM", "RM^2");
    assertThat(features).extracting(f -> f.value("RM^2")).containsExactly(4.0, 9.0);
  }

  @Test
  @DisplayName("2 列なら 2 乗の列と 2 つの列の積の列を加える")
  void twoColumns() {
    var columns = List.of("RM", "LSTAT");
    var x =
        List.of(
            new Features(columns, new double[] {2, 5}), new Features(columns, new double[] {3, 7}));

    List<Features> features = PolynomialFeatures.expand(x, columns);

    assertThat(features.getFirst().columns())
        .containsExactly("RM", "LSTAT", "RM^2", "RM LSTAT", "LSTAT^2");
    assertThat(features).extracting(f -> f.value("RM LSTAT")).containsExactly(10.0, 21.0);
    assertThat(features).extracting(f -> f.value("LSTAT^2")).containsExactly(25.0, 49.0);
  }

  @Test
  @DisplayName("3 列なら scikit-learn の PolynomialFeatures と同じ並びで 9 列を作る")
  void threeColumnsInSklearnOrder() {
    var columns = List.of("RM", "LSTAT", "PTRATIO");
    var x = List.of(new Features(columns, new double[] {5.5, 12, 18}));

    List<Features> features = PolynomialFeatures.expand(x, columns);

    assertThat(features.getFirst().columns())
        .containsExactly(
            "RM",
            "LSTAT",
            "PTRATIO",
            "RM^2",
            "RM LSTAT",
            "RM PTRATIO",
            "LSTAT^2",
            "LSTAT PTRATIO",
            "PTRATIO^2");
  }

  @Test
  @DisplayName("指定した列だけをその順に選ぶ")
  void selectsColumns() {
    var columns = List.of("RM", "LSTAT");
    var x = List.of(new Features(columns, new double[] {2, 5}));

    assertThat(PolynomialFeatures.select(x, List.of("LSTAT")))
        .containsExactly(new Features(List.of("LSTAT"), new double[] {5}));
  }
}
