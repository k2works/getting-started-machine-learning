package chapter08;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** 学習済みのパイプラインをファイルに保存し、読み込む。 */
public final class ModelFiles {
  /** 保存したパイプラインの復元に必要なクラスだけを許可し、それ以外のクラスが含まれていたら読み込みを止める */
  private static final ObjectInputFilter MODEL_CLASSES =
      ObjectInputFilter.Config.createFilter("chapter08.*;java.lang.*;java.util.*;!*");

  private ModelFiles() {}

  /** パイプライン全体（前処理で求めた値とモデル）を Java のシリアライズで保存する。 */
  public static void save(FittedPipeline pipeline, Path modelFile) throws IOException {
    Path parent = modelFile.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    try (var out = new ObjectOutputStream(Files.newOutputStream(modelFile))) {
      out.writeObject(pipeline);
    }
  }

  /** 保存したパイプラインを読み込む。許可していないクラスが含まれていれば InvalidClassException で失敗する。 */
  public static FittedPipeline load(Path modelFile) throws IOException, ClassNotFoundException {
    try (var in = new ObjectInputStream(Files.newInputStream(modelFile))) {
      in.setObjectInputFilter(MODEL_CLASSES);
      return (FittedPipeline) in.readObject();
    }
  }
}
