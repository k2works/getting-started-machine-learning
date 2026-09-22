# frozen_string_literal: true

module GettingStartedMl
  # 第 13 章の Boston データの前処理。
  module Chapter13
    module_function

    # CRIME をダミー変数にし、欠損値を列の平均値で補完してから、すべての列を標準化する。
    # 正解の列 PRICE も分析の対象にするので残し、訓練データとテストデータには分けない。
    def standardize_boston(table)
      crimes = table.rows.map { |row| row.text(Chapter09::BOSTON_CATEGORY) }
      encoded = Chapter09.encode(table, Chapter09::BOSTON_CATEGORY, Chapter09.categories(crimes))
      means = Chapter02.column_means(encoded.rows, encoded.columns)
      filled = Chapter02.fill_missing(encoded.rows, encoded.columns, means)

      Chapter09::Standardizer.fit(filled).transform(filled)
    end

    # CSV を読み込んで前処理する。
    def load_boston(csv_file)
      standardize_boston(Chapter02::Table.load(csv_file))
    end

    # 特徴量の並びを、1 件を 1 行とする行列にする。
    def to_matrix(x)
      Numo::DFloat.cast(x.map(&:values))
    end
  end
end
