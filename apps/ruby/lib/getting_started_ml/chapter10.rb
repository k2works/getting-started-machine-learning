# frozen_string_literal: true

require_relative "chapter10/logistic_regression"
require_relative "chapter10/random_forest"
require_relative "chapter10/importance"
require_relative "chapter10/rumale_models"

module GettingStartedMl
  # 第 10 章: ロジスティック回帰とアンサンブル学習。
  module Chapter10
    # テストデータの割合。
    TEST_SIZE = 0.3
    # 分割の乱数のシード。
    SEED = 0
    # ランダムフォレストの木の本数。
    N_ESTIMATORS = 100
    # ランダムフォレストの木ごと（Rumale は節ごと）に使う特徴量の数。
    MAX_FEATURES = 2
    # 浅い決定木の深さ。
    SHALLOW_DEPTH = 2
    # Rumale のロジスティック回帰の収束の判定を、既定（1e-4）より厳しくするときの値。
    STRICT_TOL = 1e-8

    # 名前とモデル。表示する順に並べる。どれも fit と predict を持つだけで、共通の親クラスは無い。
    def self.models
      { "決定木（深さ #{SHALLOW_DEPTH}）" => Chapter03::DecisionTree.new(max_depth: SHALLOW_DEPTH),
        "ロジスティック回帰" => LogisticRegression.new,
        "ランダムフォレスト（#{N_ESTIMATORS} 本）" => forest,
        "ランダムフォレスト（#{N_ESTIMATORS} 本・深さ #{SHALLOW_DEPTH}）" => forest(max_depth: SHALLOW_DEPTH),
        "Rumale ロジスティック回帰（既定）" => RumaleLogisticRegression.new,
        "Rumale ロジスティック回帰（正則化なし）" => RumaleLogisticRegression.new(reg_param: 0.0, tol: STRICT_TOL),
        "Rumale ランダムフォレスト（#{N_ESTIMATORS} 本）" => rumale_forest }
    end

    # 自作のランダムフォレスト。
    def self.forest(max_depth: nil)
      RandomForest.new(n_estimators: N_ESTIMATORS, max_features: MAX_FEATURES, max_depth:, seed: SEED)
    end

    # Rumale のランダムフォレスト。
    def self.rumale_forest
      RumaleRandomForest.new(n_estimators: N_ESTIMATORS, max_features: MAX_FEATURES, seed: SEED)
    end

    # モデルごとの正解率と、自作と Rumale のランダムフォレストの特徴量の重要度を表示する。
    def self.run(out = $stdout)
      split = Chapter02.prepare_iris(File.join(Dataset.dir, "iris.csv"), test_size: TEST_SIZE, seed: SEED)

      out.puts "モデル\t訓練データ\tテストデータ"
      models.each { |name, model| out.puts [name, *score_texts(Score.evaluate(model, split))].join("\t") }
      out.puts
      out.puts "ランダムフォレスト（#{N_ESTIMATORS} 本）の特徴量の重要度:"
      out.puts importance_lines(split)
    end

    # 正解率を小数 4 桁の文字列にする。
    def self.score_texts(score)
      [score.train, score.test].map { |value| four(value) }
    end

    # 特徴量ごとに、自作と Rumale の重要度を並べた行。
    def self.importance_lines(split)
      ours = forest_importances(forest.fit(split.x_train, split.t_train), split.x_train, split.t_train)
      theirs = rumale_forest.fit(split.x_train, split.t_train).feature_importances

      ["特徴量\t自作\tRumale", *ours.zip(theirs).map { |(column, mine), other| "#{column}\t#{four(mine)}\t#{four(other)}" }]
    end

    # 小数 4 桁の文字列にする。
    def self.four(value)
      Kernel.format("%.4f", value)
    end
  end
end
