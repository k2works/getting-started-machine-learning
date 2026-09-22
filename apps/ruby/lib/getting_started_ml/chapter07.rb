# frozen_string_literal: true

require_relative "chapter07/matrix"
require_relative "chapter07/metrics"
require_relative "chapter07/linear_regression"
require_relative "chapter07/rumale_regression"
require_relative "chapter07/cinema"

module GettingStartedMl
  # 第 7 章: 線形回帰による数値予測。正規方程式を自作し、Rumale の線形回帰と突き合わせる。
  module Chapter07
    # テストデータの割合。
    TEST_SIZE = 0.2
    # 分割の乱数のシード。
    SEED = 0

    # 映画の興行収入を線形回帰で予測し、係数と評価指標を表示する。
    def self.run(out = $stdout)
      csv_file = File.join(Dataset.dir, "cinema.csv")
      table = Chapter02::Table.load(csv_file)
      split = Cinema.prepare(csv_file, test_size: TEST_SIZE, seed: SEED)

      out.puts summary(table, split)
      out.puts report(fit(split.x_train, split.t_train), RumaleRegression.fit(split.x_train, split.t_train), split)
    end

    # 件数を表示用の 3 行にする。
    def self.summary(table, split)
      ["データ件数: #{table.rows.size}",
       "外れ値を除いた件数: #{Cinema.remove_outliers(table).rows.size}",
       "訓練データ: #{split.x_train.size} 件, テストデータ: #{split.x_test.size} 件"]
    end

    # 自作と Rumale の係数と、テストデータの評価指標を表示用の 4 行にする。
    def self.report(model, library, split)
      y = model.predict(split.x_test)

      [Kernel.format("切片: %.2f", model.intercept),
       "係数: #{format_coefficients(model)}",
       Kernel.format("Rumale の切片: %.2f, 係数: #{format_coefficients(library)}", library.intercept),
       Kernel.format("テストデータの評価: R2=%<r2>.4f, MAE=%<mae>.2f, RMSE=%<rmse>.2f",
                     r2: r2_score(split.t_test, y), mae: mean_absolute_error(split.t_test, y),
                     rmse: root_mean_squared_error(split.t_test, y))]
    end

    # 係数を「列名=値」の並びにする。
    def self.format_coefficients(model)
      model.columns.zip(model.coefficients)
           .map { |column, weight| "#{column}=#{Kernel.format('%.4f', weight)}" }.join(", ")
    end
  end
end
