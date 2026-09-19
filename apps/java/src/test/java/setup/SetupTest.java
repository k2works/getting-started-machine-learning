package setup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SetupTest {
  @Test
  @DisplayName("テスティングフレームワークが動作する")
  void testingFrameworkWorks() {
    assertThat(1 + 1).isEqualTo(2);
  }
}
