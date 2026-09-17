plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

val ktlint: Configuration = configurations.create("ktlint")

dependencies {
    implementation(libs.dataframe)
    implementation(libs.tribuo.classification.tree)
    testImplementation(kotlin("test"))
    ktlint(libs.ktlint.cli) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = false
    }
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
    // 学習データの場所（未指定なら ../data/sukkiri-ml）。値が変わればテストを再実行する
    val mlDataDir = providers.environmentVariable("ML_DATA_DIR")
    inputs.property("mlDataDir", mlDataDir.orElse(""))
    mlDataDir.orNull?.let { environment("ML_DATA_DIR", it) }
}

// 章ごとの main を実行する: ./gradlew runChapter -Pchapter=01
tasks.register<JavaExec>("runChapter") {
    group = "application"
    description = "章の main 関数を実行する"
    val chapter = providers.gradleProperty("chapter").orElse("01")
    mainClass.set(chapter.map { "chapter$it.MainKt" })
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}

val kotlinSources = listOf("src/**/*.kt", "*.kts")

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "ktlint でコードスタイルを検査する"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args(kotlinSources)
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "ktlint でコードを整形する"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    args(listOf("-F") + kotlinSources)
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}

// Notebook の出力セルには学習データが含まれうるので、出力を消してからコミットする
val notebooks = fileTree("notebooks") { include("*.ipynb") }

fun codeCells(notebook: Map<*, *>): List<MutableMap<String, Any?>> =
    (notebook["cells"] as List<*>)
        .map {
            @Suppress("UNCHECKED_CAST")
            it as MutableMap<String, Any?>
        }.filter { it["cell_type"] == "code" }

fun hasOutputs(cell: Map<String, Any?>): Boolean = (cell["outputs"] as? List<*>).orEmpty().isNotEmpty() || cell["execution_count"] != null

tasks.register("notebookVerify") {
    group = "verification"
    description = "Notebook に出力セルが残っていれば失敗する"
    inputs.files(notebooks)
    doLast {
        val dirty =
            notebooks.files.filter { file ->
                codeCells(groovy.json.JsonSlurper().parse(file) as Map<*, *>).any(::hasOutputs)
            }
        if (dirty.isNotEmpty()) {
            throw GradleException("出力セルが残っている Notebook: ${dirty.joinToString { it.name }}（./gradlew notebookStrip で消す）")
        }
    }
}

tasks.register("notebookStrip") {
    group = "formatting"
    description = "Notebook の出力セルを消す"
    doLast {
        notebooks.files.forEach { file ->
            @Suppress("UNCHECKED_CAST")
            val notebook = groovy.json.JsonSlurper().parse(file) as MutableMap<String, Any?>
            val cells = codeCells(notebook)
            if (cells.any(::hasOutputs)) {
                cells.forEach { cell ->
                    cell["outputs"] = emptyList<Any>()
                    cell["execution_count"] = null
                }
                val json =
                    groovy.json.JsonGenerator
                        .Options()
                        .disableUnicodeEscaping()
                        .build()
                        .toJson(notebook)
                file.writeText(groovy.json.JsonOutput.prettyPrint(json, true) + "\n", Charsets.UTF_8)
                println("出力セルを消した: ${file.name}")
            }
        }
    }
}

// Notebook を IDE なしで実行して動作を確認する（手元専用。uv と学習データが必要）
tasks.register("notebookExecute") {
    group = "verification"
    description = "kotlin-jupyter-kernel で Notebook を実行する（出力は build/notebooks に書き出す）"
    dependsOn("jar")
    doLast {
        notebooks.files.sortedBy { it.name }.forEach { file ->
            println("実行: ${file.name}")
            providers
                .exec {
                    workingDir = file.parentFile
                    commandLine(
                        "uvx",
                        "--python",
                        "3.12",
                        "--from",
                        "nbconvert",
                        "--with",
                        "kotlin-jupyter-kernel",
                        "--with",
                        "ipykernel",
                        "jupyter-nbconvert",
                        "--to",
                        "notebook",
                        "--execute",
                        "--output-dir",
                        layout.buildDirectory
                            .dir("notebooks")
                            .get()
                            .asFile.absolutePath,
                        file.name,
                    )
                }.result
                .get()
                .assertNormalExitValue()
        }
    }
}

tasks.named("check") {
    dependsOn("notebookVerify")
}
