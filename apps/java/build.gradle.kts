plugins {
    java
    pmd
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

repositories {
    mavenCentral()
}

dependencies {
    errorprone(libs.errorprone.core)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Error Prone の指摘を警告ではなくエラーにする
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// 整形は google-java-format に任せる。spotlessCheck は check タスクから実行される
spotless {
    java {
        googleJavaFormat(
            libs.versions.google.java.format
                .get(),
        )
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

// PMD の既定のルールセット（quickstart）で検査する。違反があれば check が失敗する
pmd {
    toolVersion = libs.versions.pmd.get()
    ruleSetFiles = files("config/pmd/ruleset.xml")
    ruleSets = emptyList()
    isConsoleOutput = true
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// CI でカバレッジを表示するため、HTML に加えて CSV を出す
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        csv.required = true
    }
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
