package machinelearning.chapter09

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Path
import machinelearning.chapter02.{Row, Table}

/** 自転車の利用者数（bike.tsv）と天気（weather.csv）の結合と集計。 */
object BikeWeather:

  /** weather.csv の文字コード */
  val ShiftJis: Charset = Charset.forName("Shift_JIS")

  private val Key = "weather_id"

  /** タブ区切りの bike.tsv（UTF-8）を読み込む。 */
  def loadBike(tsvFile: Path): Table = DelimitedFiles.load(tsvFile, StandardCharsets.UTF_8, "\t")

  /** Shift_JIS の weather.csv を読み込む。 */
  def loadWeather(csvFile: Path): Table = DelimitedFiles.load(csvFile, ShiftJis, ",")

  /** 天気 ID をキーにした Map を引いて、天気の列を加える（内部結合）。天気の表に無い ID の行は残さない。 */
  def joinWeather(bike: Table, weather: Table): Table =
    val byId = weather.rows.map(row => row.text(Key) -> row).toMap
    require(byId.size == weather.rows.size, s"$Key が一意ではありません")
    val added = weather.columns.filterNot(_ == Key)
    val rows = bike.rows.flatMap(row => byId.get(row.text(Key)).map(join(row, _, added)))
    Table(bike.columns ++ added, rows)

  private def join(row: Row, found: Row, added: Vector[String]): Row =
    Row(row.cells ++ added.map(column => column -> found.text(column)))

  /** 天気ごとの平均利用者数を、多い順に並べて返す。 */
  def meanCountByWeather(joined: Table): Vector[(String, Double)] =
    joined.rows
      .groupBy(_.text("weather"))
      .view
      .mapValues(rows => rows.flatMap(_.number("cnt")).sum / rows.size)
      .toVector
      .sortBy(-_._2)
