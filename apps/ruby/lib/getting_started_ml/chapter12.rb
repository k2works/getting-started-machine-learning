# frozen_string_literal: true

require_relative "chapter12/regularized_model"
require_relative "chapter12/ridge"
require_relative "chapter12/lasso"
require_relative "chapter12/selection"
require_relative "chapter12/boston"
require_relative "chapter12/rumale_regularized"

module GettingStartedMl
  # 第 12 章: 正則化とモデル選択。
  module Chapter12
    # テストデータの割合。
    TEST_SIZE = 0.3
    # 検証データの割合（訓練用のうち）。
    VALIDATION_SIZE = 0.3
    # 分割の乱数のシード。
    SEED = 0
    # 試す正則化の強さ。
    ALPHAS = [0.0, 0.1, 1.0, 10.0, 100.0].freeze
    # ラッソ回帰で試す正則化の強さ。
    LASSO_ALPHAS = [1.0, 10.0, 50.0, 100.0].freeze
    # 中心化せずに Rumale のラッソ回帰に渡すときの正則化の強さ。
    RAW_LASSO_ALPHA = 10.0

    # 正則化の強さごとにリッジ回帰を学習し、検証データで選んだモデルとラッソ回帰の結果を表示する。
    def self.run(out = $stdout)
      csv_file = File.join(Dataset.dir, "Boston.csv")
      data = prepare(csv_file, test_size: TEST_SIZE, validation_size: VALIDATION_SIZE, seed: SEED)
      out.puts summary_lines(data)
      best = best_experiment(experiment_lines(out, data))
      out.puts ridge_lines(data, best.alpha)
      out.puts lasso_lines(data)
      out.puts raw_lines(data, best.alpha)
    end

    # 件数と特徴量の行。
    def self.summary_lines(data)
      ["データ件数: #{data.kept}（外れ値 #{data.removed} 件を除外）",
       "訓練データ: #{data.t_train.size} 件, 検証データ: #{data.t_valid.size} 件, テストデータ: #{data.t_test.size} 件",
       "特徴量: #{data.feature_names.join(', ')}"]
    end

    # alpha ごとの実験結果を表示し、実験の並びを返す。
    def self.experiment_lines(out, data)
      experiments = run_ridge_experiments(data, ALPHAS)
      out.puts "alpha\t訓練 R²\t検証 R²\t係数の絶対値の合計"
      experiments.each { |experiment| out.puts experiment_line(experiment) }
      experiments
    end

    # 実験 1 つ分の行。
    def self.experiment_line(experiment)
      Kernel.format("%<alpha>.1f\t%<train_score>.4f\t%<validation_score>.4f\t%<coefficient_abs_sum>.3f",
                    **experiment.to_h)
    end

    # 選んだ alpha のリッジ回帰と線形回帰をテストデータで比べ、Rumale との差を並べた行。
    def self.ridge_lines(data, alpha)
      linear, ridge = [0.0, alpha].map { |value| fit_ridge(data.feature_names, data.x_train, data.t_train, value) }
      library = rumale_ridge(data.feature_names, data.x_train, data.t_train, alpha)

      ["検証データで選んだ alpha: #{alpha}",
       Kernel.format("テストデータの決定係数: 線形回帰 %<linear>.4f, リッジ回帰 %<ridge>.4f",
                     linear: test_score(linear, data), ridge: test_score(ridge, data)),
       Kernel.format("リッジ回帰の係数の最大の差（自作と Rumale）: %.2e", max_difference(ridge, library))]
    end

    # テストデータの決定係数。
    def self.test_score(model, data)
      Chapter07.r2_score(data.t_test, model.predict(data.x_test))
    end

    # alpha ごとにラッソ回帰で 0 になった特徴量を、自作と Rumale で並べた行。
    def self.lasso_lines(data)
      args = [data.feature_names, data.x_train, data.t_train]
      pairs = LASSO_ALPHAS.map { |alpha| [alpha, fit_lasso(*args, alpha), rumale_lasso(*args, alpha)] }

      ["ラッソ回帰（alpha ごとに 0 になった特徴量）",
       *pairs.map { |alpha, own, library| "  alpha=#{alpha}: 自作 #{own.zero_columns} / Rumale #{library.zero_columns}" },
       Kernel.format("ラッソ回帰の係数の最大の差（自作と Rumale）: %.2e",
                     pairs.map { |_, own, library| max_difference(own, library) }.max)]
    end

    # 中心化せずに Rumale に渡したとき（切片にも罰則がかかる）の、切片と 0 になる特徴量の違いを並べた行。
    def self.raw_lines(data, alpha)
      args = [data.feature_names, data.x_train, data.t_train]
      intercepts = [fit_ridge(*args, alpha), rumale_ridge_raw(*args, alpha)].map(&:intercept)
      zeros = [fit_lasso(*args, RAW_LASSO_ALPHA), rumale_lasso_raw(*args, RAW_LASSO_ALPHA)].map(&:zero_columns)

      ["中心化せずに Rumale に渡したとき（切片にも罰則がかかる）",
       Kernel.format("  リッジ回帰（alpha=%<alpha>s）の切片: 自作 %<own>.2f / Rumale %<raw>.2f",
                     alpha:, own: intercepts[0], raw: intercepts[1]),
       "  ラッソ回帰（alpha=#{RAW_LASSO_ALPHA}）で 0 になった特徴量: 自作 #{zeros[0]} / Rumale #{zeros[1]}"]
    end

    # 2 つのモデルの、同じ位置の係数どうしの差の最大値。
    def self.max_difference(left, right)
      left.coefficients.zip(right.coefficients).map { |a, b| (a - b).abs }.max
    end
  end
end
