package support

import java.io.ByteArrayOutputStream
import java.io.PrintStream

/** 標準出力に書かれた内容を取り出す。 */
fun captureStdout(block: () -> Unit): String {
    val original = System.out
    val buffer = ByteArrayOutputStream()
    System.setOut(PrintStream(buffer, true, Charsets.UTF_8))
    try {
        block()
    } finally {
        System.setOut(original)
    }
    return buffer.toString(Charsets.UTF_8).replace("\r\n", "\n")
}
