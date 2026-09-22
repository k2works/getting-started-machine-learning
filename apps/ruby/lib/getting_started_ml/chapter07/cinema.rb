# frozen_string_literal: true

module GettingStartedMl
  module Chapter07
    # 映画の興行収入のデータ（cinema.csv）の前処理。
    module Cinema
      # 特徴量の列。
      FEATURES = %w[SNS1 SNS2 actor original].freeze
      # 正解ラベル（興行収入）の列。
      TARGET = "sales"
      # SNS2 がこの値を超え、かつ興行収入が OUTLIER_SALES 未満の映画を外れ値とする。
      OUTLIER_SNS2 = 1000.0
      OUTLIER_SALES = 8500.0

      module_function

      # 欠損値を許さずに数値の列を読む。空欄なら ArgumentError を投げる。
      def number(row, column)
        row.number(column) || raise(ArgumentError, "値が空欄です: #{column}")
      end

      # 外れ値かどうかを判定する。
      def outlier?(row)
        number(row, "SNS2") > OUTLIER_SNS2 && number(row, TARGET) < OUTLIER_SALES
      end

      # 外れ値の行を除いた表を返す。元の表は変えない。
      def remove_outliers(table)
        table.with(rows: table.rows.reject { |row| outlier?(row) })
      end

      # 外れ値を除き、分割してから訓練データの平均値で両方の欠損値を補完する。
      def prepare(csv_file, test_size:, seed:)
        rows = remove_outliers(Chapter02::Table.load(csv_file)).rows
        t = rows.map { |row| number(row, TARGET) }
        split = Chapter02.split_train_test(rows, t, test_size:, seed:)
        means = Chapter02.column_means(split.x_train, FEATURES)

        split.with(x_train: Chapter02.fill_missing(split.x_train, FEATURES, means),
                   x_test: Chapter02.fill_missing(split.x_test, FEATURES, means))
      end
    end
  end
end
