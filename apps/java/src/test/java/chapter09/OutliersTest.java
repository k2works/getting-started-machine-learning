package chapter09;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import chapter02.Features;
import chapter02.TrainTestSplit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutliersTest {
  @Test
  @DisplayName("四分位数の位置が値の間にあれば前後の値から線形補間する")
  void quantileInterpolates() {
    assertThat(Outliers.quantile(List.of(4.0, 1.0, 3.0, 2.0), 0.25)).isCloseTo(1.75, within(1e-12));
  }

  @Test
  @DisplayName("第 3 四分位数から IQR の 1.5 倍より大きい値を外れ値とする")
  void upperOutlier() {
    assertThat(Outliers.iqrOutliers(List.of(1.0, 2.0, 3.0, 4.0, 100.0)))
        .containsExactly(false, false, false, false, true);
  }

  @Test
  @DisplayName("第 1 四分位数から IQR の 1.5 倍より小さい値も外れ値とする")
  void lowerOutlier() {
    assertThat(Outliers.iqrOutliers(List.of(-100.0, 1.0, 2.0, 3.0, 4.0)))
        .containsExactly(true, false, false, false, false);
  }

  @Test
  @DisplayName("訓練データから価格が外れ値の行を取り除きテストデータは残す")
  void removesTargetOutliersFromTrain() {
    var split =
        new TrainTestSplit<Features, Double>(
            List.of(5.0, 6.0, 6.5, 7.0, 8.0).stream().map(Samples::rm).toList(),
            List.of(Samples.rm(9)),
            List.of(1.0, 2.0, 3.0, 4.0, 100.0),
            List.of(500.0));

    var removed = Outliers.removeTargetOutliers(split);

    assertThat(removed.xTrain()).extracting(f -> f.value("RM")).containsExactly(5.0, 6.0, 6.5, 7.0);
    assertThat(removed.tTrain()).containsExactly(1.0, 2.0, 3.0, 4.0);
    assertThat(removed.xTest()).isEqualTo(split.xTest());
    assertThat(removed.tTest()).containsExactly(500.0);
  }
}
