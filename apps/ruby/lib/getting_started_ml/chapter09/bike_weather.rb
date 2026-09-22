# frozen_string_literal: true

require "csv"

module GettingStartedMl
  # 第 9 章の、区切り文字・文字コードを指定した読み込みと表の結合。
  module Chapter09
    # Shift_JIS のファイルを読み、UTF-8 の文字列にする指定。
    SHIFT_JIS = "Shift_JIS:UTF-8"
    # UTF-8 のファイルを読む指定。
    UTF8 = "UTF-8"
    # 結合のキーの列。
    WEATHER_KEY = "weather_id"

    module_function

    # 1 行目を列名として読み込む。文字コードが合わなければ CSV::InvalidEncodingError になる。
    def load_table(file, col_sep: ",", encoding: UTF8)
      csv = CSV.read(file, headers: true, col_sep:, encoding:)
      rows = csv.map { |record| Chapter02::Row.new(record.to_h.transform_values(&:to_s)) }

      Chapter02::Table.new(columns: csv.headers, rows:)
    end

    # 自転車の表（タブ区切り・UTF-8）を読み込む。
    def load_bike(tsv_file)
      load_table(tsv_file, col_sep: "\t")
    end

    # 天気の表（カンマ区切り・Shift_JIS）を読み込む。
    def load_weather(csv_file)
      load_table(csv_file, encoding: SHIFT_JIS)
    end

    # 天気 ID をキーにして、自転車の表に天気の列を加える（内部結合）。天気の表に無い ID の行は残さない。
    def join_weather(bike, weather)
      by_id = weather.rows.to_h { |row| [row.text(WEATHER_KEY), row] }
      rows = bike.rows.filter_map { |row| join_row(row, by_id[row.text(WEATHER_KEY)]) }

      Chapter02::Table.new(columns: bike.columns + (weather.columns - [WEATHER_KEY]), rows:)
    end

    # 自転車の行に天気の行のセルを加える。天気の行が無ければ nil を返す。
    def join_row(row, found)
      Chapter02::Row.new(row.cells.merge(found.cells.except(WEATHER_KEY))) if found
    end

    # 天気ごとの平均利用者数を、多い順に並べる。
    def mean_count_by_weather(joined)
      joined.rows.group_by { |row| row.text("weather") }
            .map { |weather, rows| [weather, rows.sum { |row| row.number("cnt") } / rows.size] }
            .sort_by { |_weather, mean| -mean }
    end
  end
end
