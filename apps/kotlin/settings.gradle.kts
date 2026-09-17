plugins {
    // jvmToolchain で指定した JDK が無ければ自動で取得する
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "getting-started-ml"
