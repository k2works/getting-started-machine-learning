package support;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** 標準出力に書かれた内容を取り出す。 */
public final class StdoutCapture {
  private StdoutCapture() {}

  /** 標準出力に書く処理。例外を投げてよい。 */
  @FunctionalInterface
  public interface Block {
    void run() throws Exception;
  }

  // 元の System.out は後で戻すために保持するだけで、閉じてはいけない
  @SuppressWarnings("PMD.CloseResource")
  public static String capture(Block block) throws Exception {
    PrintStream original = System.out;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (PrintStream capturing = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
      System.setOut(capturing);
      block.run();
    } finally {
      System.setOut(original);
    }
    return buffer.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
  }
}
