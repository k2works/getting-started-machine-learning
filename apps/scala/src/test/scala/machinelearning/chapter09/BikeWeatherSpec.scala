package machinelearning.chapter09

import java.nio.charset.{MalformedInputException, StandardCharsets}
import java.nio.file.{Files, Path}
import org.scalatest.funsuite.AnyFunSuite

class BikeWeatherSpec extends AnyFunSuite:
  private def writeFile(name: String, text: String, charset: java.nio.charset.Charset): Path =
    val file = Files.createTempDirectory("chapter09").resolve(name)
    val _ = Files.writeString(file, text, charset)
    file

  test("タブ区切りのファイルを読み込む") {
    val tsvFile =
      writeFile("bike.tsv", "dteday\tweather_id\tcnt\n2030-04-01\t1\t120\n", StandardCharsets.UTF_8)

    val bike = BikeWeather.loadBike(tsvFile)

    assert(bike.columns === Vector("dteday", "weather_id", "cnt"))
    assert(bike.rows.head.text("weather_id") === "1")
    assert(bike.rows.head.number("cnt") === Some(120.0))
  }

  test("Shift_JIS のファイルを読み込む") {
    val csvFile = writeFile("weather.csv", "weather_id,weather\n1,晴れ\n", BikeWeather.ShiftJis)

    val weather = BikeWeather.loadWeather(csvFile)

    assert(weather.rows.head.text("weather") === "晴れ")
  }

  test("Shift_JIS のファイルを UTF-8 として読むと例外になる") {
    val csvFile = writeFile("weather.csv", "weather_id,weather\n1,晴れ\n", BikeWeather.ShiftJis)

    assertThrows[MalformedInputException] {
      DelimitedFiles.load(csvFile, StandardCharsets.UTF_8, ",")
    }
  }

  test("天気 ID で天気の名前を結合する") {
    val bike = Samples.table(
      Vector("weather_id", "cnt"),
      Samples.row("weather_id" -> "2", "cnt" -> "80"),
      Samples.row("weather_id" -> "1", "cnt" -> "120")
    )
    val weather = Samples.table(
      Vector("weather_id", "weather"),
      Samples.row("weather_id" -> "1", "weather" -> "晴れ"),
      Samples.row("weather_id" -> "2", "weather" -> "曇り")
    )

    val joined = BikeWeather.joinWeather(bike, weather)

    assert(joined.columns === Vector("weather_id", "cnt", "weather"))
    assert(joined.rows.map(_.text("cnt")) === Vector("80", "120"))
    assert(joined.rows.map(_.text("weather")) === Vector("曇り", "晴れ"))
  }

  test("天気の表に無い天気 ID の行は残さない") {
    val bike = Samples.table(
      Vector("weather_id", "cnt"),
      Samples.row("weather_id" -> "1", "cnt" -> "120"),
      Samples.row("weather_id" -> "9", "cnt" -> "30")
    )
    val weather = Samples.table(
      Vector("weather_id", "weather"),
      Samples.row("weather_id" -> "1", "weather" -> "晴れ")
    )

    val joined = BikeWeather.joinWeather(bike, weather)

    assert(joined.rows.map(_.text("cnt")) === Vector("120"))
  }

  test("天気ごとの平均利用者数を多い順に求める") {
    val joined = Samples.table(
      Vector("weather", "cnt"),
      Samples.row("weather" -> "雨", "cnt" -> "20"),
      Samples.row("weather" -> "晴れ", "cnt" -> "100"),
      Samples.row("weather" -> "晴れ", "cnt" -> "140")
    )

    assert(BikeWeather.meanCountByWeather(joined) === Vector("晴れ" -> 120.0, "雨" -> 20.0))
  }
