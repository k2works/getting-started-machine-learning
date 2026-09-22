# frozen_string_literal: true

module GettingStartedMl
  # 交差検証に渡す特徴量と正解ラベル。
  #
  # この章では分割の前に全体の平均値で補完する、簡略化した前処理を使う。分割ごとに補完し直すのが
  # 本来だが、交差検証そのものを見せるために手順を短くしている。
  module Chapter11
    # Survived.csv の特徴量の列。
    SURVIVED_FEATURES = %w[Pclass Age male].freeze
    # Survived.csv の正解の列。
    SURVIVED_TARGET = "Survived"
    # 生存（正例）を表すラベル。
    SURVIVED_POSITIVE = "1"

    module_function

    # 客室クラス・年齢・男性かどうか（1 か 0）を特徴量に、Survived の文字列を正解ラベルにする。
    # 年齢の欠損値は全体の平均値で補う。特徴量と正解ラベルの組を返す。
    def prepare_survived(table)
      age_mean = Chapter02.column_means(table.rows, ["Age"]).fetch("Age")
      x = table.rows.map do |row|
        Chapter02::Features.new(columns: SURVIVED_FEATURES,
                                values: [Chapter07::Cinema.number(row, "Pclass"), row.number("Age") || age_mean,
                                         row.text("Sex") == "male" ? 1.0 : 0.0])
      end

      [x, table.rows.map { |row| row.text(SURVIVED_TARGET) }]
    end

    # 第 7 章の 4 列を特徴量に、興行収入を正解にする。欠損値は列ごとの平均値で補う。外れ値は除かない。
    def prepare_cinema(table)
      columns = Chapter07::Cinema::FEATURES
      x = Chapter02.fill_missing(table.rows, columns, Chapter02.column_means(table.rows, columns))

      [x, table.rows.map { |row| Chapter07::Cinema.number(row, Chapter07::Cinema::TARGET) }]
    end
  end
end
