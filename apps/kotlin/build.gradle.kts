plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    // 学習データの場所（未指定なら ../data/sukkiri-ml）
    System.getenv("ML_DATA_DIR")?.let { environment("ML_DATA_DIR", it) }
}

// 章ごとの main を実行する: ./gradlew runChapter -Pchapter=01
tasks.register<JavaExec>("runChapter") {
    group = "application"
    description = "章の main 関数を実行する"
    val chapter = providers.gradleProperty("chapter").orElse("01")
    mainClass.set(chapter.map { "chapter$it.MainKt" })
    classpath = sourceSets["main"].runtimeClasspath
}
