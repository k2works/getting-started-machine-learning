ThisBuild / scalaVersion := "3.3.6"
ThisBuild / organization := "machinelearning"

lazy val root = (project in file("."))
  .settings(
    name := "machine-learning",
    // 警告をエラーにする。使っていない値・import や、捨てている戻り値を見つける
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Wunused:all",
      "-Wvalue-discard",
      "-Xfatal-warnings"
    ),
    libraryDependencies ++= Seq(
      "org.tribuo" % "tribuo-classification-tree" % "4.3.2",
      "org.scalatest" %% "scalatest" % "3.2.20" % Test
    ),
    // 学習データの場所（未指定なら ../data/sukkiri-ml）をテストに渡す
    Test / envVars := sys.env.get("ML_DATA_DIR").map("ML_DATA_DIR" -> _).toMap
  )
