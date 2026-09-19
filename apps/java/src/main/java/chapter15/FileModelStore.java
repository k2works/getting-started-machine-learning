package chapter15;

import chapter07.LinearModel;
import chapter08.FittedPipeline;
import chapter08.ModelFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 学習済みモデルをディレクトリのファイルに保存し、読み込む。 */
public final class FileModelStore implements ModelStore {
  public static final String SALES_MODEL = "cinema";
  public static final String SURVIVAL_MODEL = "survived";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Path modelDir;

  public FileModelStore(Path modelDir) {
    this.modelDir = modelDir;
  }

  /** 線形回帰モデルを JSON で保存する。 */
  public void saveSalesModel(LinearModel model) throws IOException {
    Files.createDirectories(modelDir);
    MAPPER.writeValue(salesModelFile().toFile(), model);
  }

  /** 学習済みパイプラインを Java のシリアライズで保存する（第 8 章の ModelFiles）。 */
  public void saveSurvivalModel(FittedPipeline pipeline) throws IOException {
    Files.createDirectories(modelDir);
    ModelFiles.save(pipeline, survivalModelFile());
  }

  @Override
  public SalesModel loadSalesModel() throws ModelNotFoundException {
    requireFile(SALES_MODEL, salesModelFile());
    try {
      return new LinearSalesModel(MAPPER.readValue(salesModelFile().toFile(), LinearModel.class));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public SurvivalModel loadSurvivalModel() throws ModelNotFoundException {
    requireFile(SURVIVAL_MODEL, survivalModelFile());
    try {
      return new PipelineSurvivalModel(ModelFiles.load(survivalModelFile()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("モデル " + SURVIVAL_MODEL + " を読み込めません", e);
    }
  }

  private static void requireFile(String model, Path file) throws ModelNotFoundException {
    if (!Files.exists(file)) {
      throw new ModelNotFoundException(model);
    }
  }

  private Path salesModelFile() {
    return modelDir.resolve(SALES_MODEL + ".json");
  }

  private Path survivalModelFile() {
    return modelDir.resolve(SURVIVAL_MODEL + ".ser");
  }
}
