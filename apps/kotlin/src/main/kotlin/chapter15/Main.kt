package chapter15

import dataset.dataDir
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import java.io.File

/** 学習済みモデルの保存先（apps/kotlin/model/ は .gitignore の対象） */
val MODEL_DIR = File("model")
const val PORT = 8015

fun main() {
    trainAndReport(MODEL_DIR)
    startServer(MODEL_DIR, PORT, wait = true)
}

fun trainAndReport(modelDir: File) {
    trainAndSaveModels(dataDir(), FileModelStore(modelDir))
    println("学習済みモデルを保存しました: $SALES_MODEL.json, $SURVIVAL_MODEL.ser")
    println("API を起動します: http://127.0.0.1:$PORT")
}

fun startServer(
    modelDir: File,
    port: Int,
    wait: Boolean,
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
    embeddedServer(Netty, port = port, host = "127.0.0.1") {
        predictionModule(PredictionService(FileModelStore(modelDir)))
    }.start(wait = wait)
