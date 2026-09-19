package chapter15;

import dataset.DataDir;
import io.javalin.Javalin;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;

/** モデルを学習して保存し、予測 API を起動する。 */
public final class Main {
  /** 学習済みモデルの保存先（apps/java/model/ は .gitignore の対象） */
  public static final Path MODEL_DIR = Path.of("model");

  public static final int PORT = 8015;

  /** 自分のマシンからだけ接続できるループバックのアドレス（127.0.0.1） */
  private static final String HOST = InetAddress.getLoopbackAddress().getHostAddress();

  private Main() {}

  public static void main(String[] args) throws IOException {
    trainAndReport(MODEL_DIR);
    startServer(MODEL_DIR, PORT);
  }

  public static void trainAndReport(Path modelDir) throws IOException {
    Training.trainAndSaveModels(DataDir.dataDir(), new FileModelStore(modelDir));
    System.out.println(
        "学習済みモデルを保存しました: "
            + FileModelStore.SALES_MODEL
            + ".json, "
            + FileModelStore.SURVIVAL_MODEL
            + ".ser");
    System.out.println("API を起動します: http://" + HOST + ":" + PORT);
  }

  public static Javalin startServer(Path modelDir, int port) {
    return PredictionApi.create(new PredictionService(new FileModelStore(modelDir)))
        .start(HOST, port);
  }
}
