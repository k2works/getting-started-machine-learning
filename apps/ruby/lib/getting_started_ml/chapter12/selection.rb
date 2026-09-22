# frozen_string_literal: true

module GettingStartedMl
  # 第 12 章のモデル選択。正則化の強さごとに実験し、検証データで選ぶ。
  module Chapter12
    # 正則化の強さ 1 つ分の実験結果。
    Experiment = Data.define(:alpha, :train_score, :validation_score, :coefficient_abs_sum)

    module_function

    # alpha ごとにリッジ回帰を訓練データで学習し、訓練データと検証データの決定係数を記録する。
    # split は feature_names・x_train・t_train・x_valid・t_valid を持つもの（BostonSplit）。
    def run_ridge_experiments(split, alphas)
      alphas.map do |alpha|
        model = fit_ridge(split.feature_names, split.x_train, split.t_train, alpha)

        Experiment.new(alpha:, train_score: Chapter07.r2_score(split.t_train, model.predict(split.x_train)),
                       validation_score: Chapter07.r2_score(split.t_valid, model.predict(split.x_valid)),
                       coefficient_abs_sum: model.coefficient_abs_sum)
      end
    end

    # 検証データの決定係数が最も高い実験。同じ値なら先の実験を選ぶ（max_by は先の要素を残す）。
    def best_experiment(experiments)
      raise ArgumentError, "実験の結果が 1 件もありません" if experiments.empty?

      experiments.max_by(&:validation_score)
    end
  end
end
