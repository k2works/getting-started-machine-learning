# frozen_string_literal: true

require "test_helper"

class Chapter09TablesTest < Minitest::Test
  C = GettingStartedMl::Chapter09
  Chapter02 = GettingStartedMl::Chapter02

  # 区切り文字・文字コードを指定した読み込みと、表の結合

  def with_file(name, content)
    Dir.mktmpdir do |dir|
      path = File.join(dir, name)
      File.binwrite(path, content)
      yield path
    end
  end

  def weather_csv
    "weather_id,weather\n1,晴れ\n2,雨\n".encode("Shift_JIS")
  end

  def test_タブ区切りのファイルを読み込む
    with_file("bike.tsv", "weather_id\tcnt\n1\t100\n") do |path|
      table = C.load_table(path, col_sep: "\t")

      assert_equal %w[weather_id cnt], table.columns
      assert_equal "100", table.rows.first.text("cnt")
    end
  end

  def test_shift_jisのファイルを読み込む
    with_file("weather.csv", weather_csv) do |path|
      table = C.load_table(path, encoding: C::SHIFT_JIS)

      assert_equal(%w[晴れ 雨], table.rows.map { |row| row.text("weather") })
    end
  end

  def test_shift_jisのファイルはutf8として読めない
    with_file("weather.csv", weather_csv) do |path|
      assert_raises(CSV::InvalidEncodingError) { C.load_table(path) }
    end
  end

  def table(columns, *rows)
    Chapter02::Table.new(columns:, rows: rows.map { |values| Chapter02::Row.new(columns.zip(values).to_h) })
  end

  def test_天気の列を加え天気の表に無い行は残さない
    bike = table(%w[weather_id cnt], %w[1 100], %w[2 200], %w[9 900])
    joined = C.join_weather(bike, table(%w[weather_id weather], %w[1 晴れ], %w[2 雨]))

    assert_equal %w[weather_id cnt weather], joined.columns
    assert_equal(%w[晴れ 雨], joined.rows.map { |row| row.text("weather") })
  end

  def test_天気ごとの平均利用者数を多い順に並べる
    joined = table(%w[cnt weather], %w[100 雨], %w[300 晴れ], %w[200 雨], %w[500 晴れ])

    assert_equal [["晴れ", 400.0], ["雨", 150.0]], C.mean_count_by_weather(joined)
  end

  def test_Boston_の表をダミー変数にして分割し欠損値を補完する
    csv = "CRIME,RM,PRICE\nlow,5,10\nhigh,,20\nvery_low,7,30\nlow,8,40\n"
    with_file("Boston.csv", csv) do |path|
      split = C.prepare_boston(path, test_size: 0.5, seed: 0)

      assert_equal %w[RM CRIME_low CRIME_very_low], split.x_train.first.columns
      assert_equal 2, split.x_test.size
      assert_equal [10.0, 20.0, 30.0, 40.0], (split.t_train + split.t_test).sort
    end
  end
end
