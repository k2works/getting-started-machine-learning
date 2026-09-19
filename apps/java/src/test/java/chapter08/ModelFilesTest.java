package chapter08;

import static chapter08.Passengers.newPassengers;
import static chapter08.Passengers.trainT;
import static chapter08.Passengers.trainX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Point;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelFilesTest {
  @TempDir Path directory;

  @Test
  @DisplayName("保存したパイプラインを読み込むと同じ予測をする")
  void saveAndLoad() throws Exception {
    var pipeline = Pipeline.build(3, ClassWeight.NONE).fit(trainX(), trainT());
    var modelFile = directory.resolve("model/survived.ser");

    ModelFiles.save(pipeline, modelFile);
    var loaded = ModelFiles.load(modelFile);

    assertThat(loaded.predict(newPassengers())).containsExactly(1, 0);
  }

  @Test
  @DisplayName("許可していないクラスを含むファイルは読み込まない")
  void rejectsUnknownClass() throws Exception {
    var modelFile = directory.resolve("unknown.ser");
    try (var out = new ObjectOutputStream(Files.newOutputStream(modelFile))) {
      out.writeObject(new Point(1, 2));
    }

    assertThatThrownBy(() -> ModelFiles.load(modelFile)).isInstanceOf(InvalidClassException.class);
  }
}
