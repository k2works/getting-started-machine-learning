package dataset

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DataDirTest {
    @Test
    fun `環境変数ML_DATA_DIRが指定されていればそのディレクトリを返す`() {
        val env = mapOf("ML_DATA_DIR" to "/tmp/ml-data")

        assertEquals(File("/tmp/ml-data"), dataDir(env::get))
    }

    @Test
    fun `環境変数が無ければappsのdataディレクトリを返す`() {
        assertEquals(File("../data/sukkiri-ml"), dataDir { null })
    }
}
