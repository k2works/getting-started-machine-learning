package chapter11;

import static org.assertj.core.api.Assertions.assertThat;

import chapter02.Features;
import chapter03.DecisionTree;
import chapter07.LinearRegression;
import java.util.List;
import java.util.stream.DoubleStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModelsTest {
  static List<Features> features(double... values) {
    return DoubleStream.of(values)
        .mapToObj(v -> new Features(List.of("feature"), new double[] {v}))
        .toList();
  }

  @Test
  @DisplayName("DecisionTreeModel は第 3 章の決定木と同じ予測をする")
  void decisionTree() {
    var x = features(0.1, 0.2, 0.3, 0.6, 0.7, 0.9);
    var t = List.of("0", "0", "1", "1", "0", "1");
    var newX = features(0.15, 0.35, 0.8);

    var model = new DecisionTreeModel(1);
    model.fit(x, t);

    assertThat(model.predict(newX)).isEqualTo(DecisionTree.withMaxDepth(1).fit(x, t).predict(newX));
  }

  @Test
  @DisplayName("LinearRegressionModel は第 7 章の線形回帰と同じ予測をする")
  void linearRegression() {
    var x = features(1, 2, 3, 4);
    var t = List.of(2.1, 3.9, 6.2, 7.8);
    var newX = features(1.5, 5);

    var model = new LinearRegressionModel();
    model.fit(x, t);

    assertThat(model.predict(newX)).isEqualTo(LinearRegression.fit(x, t).predict(newX));
  }
}
