# frozen_string_literal: true

require_relative "chapter02/table"
require_relative "chapter02/preprocessing"

module GettingStartedMl
  # 第 2 章: データの前処理。表の読み込み・欠損値の補完・訓練データとテストデータへの分割。
  module Chapter02
    # テストデータの割合。
    TEST_SIZE = 0.3
    # 分割の乱数のシード。
    SEED = 0

    # アヤメのデータの前処理の結果を表示する。
    def self.run(out = $stdout)
      csv_file = File.join(Dataset.dir, "iris.csv")
      table = Table.load(csv_file)

      out.puts "データ件数: #{table.rows.size}"
      out.puts "欠損値の数: #{format_missing(table.count_missing)}"
      out.puts summary(prepare_iris(csv_file, test_size: TEST_SIZE, seed: SEED))
    end

    # 分割の件数と特徴量の列を表示用の 2 行にする。
    def self.summary(split)
      ["訓練データ: #{split.x_train.size} 件, テストデータ: #{split.x_test.size} 件",
       "特徴量: #{split.x_train.first.columns.join(', ')}"]
    end

    # 列ごとの欠損値の数を「列=数」の並びにする。
    def self.format_missing(counts)
      counts.map { |column, count| "#{column}=#{count}" }.join(", ")
    end
  end
end
