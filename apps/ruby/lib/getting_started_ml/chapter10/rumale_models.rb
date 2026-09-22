# frozen_string_literal: true

require "rumale"

module GettingStartedMl
  module Chapter10
    # Rumale のモデルに文字列のラベルで fit・predict するための共通部分。
    # ラベルの番号付けと行列への変換は、第 3 章の RumaleTree のものを使う。
    module RumaleClassifier
      # 訓練データで学習する。メソッドをつなげられるように自分を返す。
      def fit(x, t)
        codes, @classes = Chapter03::RumaleTree.encode(t)
        @model = build.fit(Chapter03::RumaleTree.matrix(x), Numo::Int32.cast(codes))
        self
      end

      # 特徴量ごとのラベルを予測する。
      def predict(x)
        raise "学習してから予測してください" if @model.nil?

        @model.predict(Chapter03::RumaleTree.matrix(x)).to_a.map { |code| @classes[code] }
      end
    end

    # Rumale のロジスティック回帰。既定値は Rumale と同じ。
    # reg_param は L2 正則化の強さ、tol は L-BFGS-B の収束の判定（factr = tol / 機械イプシロン）に使われる。
    class RumaleLogisticRegression
      include RumaleClassifier

      def initialize(reg_param: 1.0, max_iter: 1000, tol: 1e-4)
        @params = { reg_param:, max_iter:, tol: }
        @model = nil
      end

      private

      def build
        Rumale::LinearModel::LogisticRegression.new(**@params)
      end
    end

    # Rumale のランダムフォレスト。max_features は木ごとではなく、節ごとに調べる特徴量の数。
    class RumaleRandomForest
      include RumaleClassifier

      def initialize(n_estimators:, max_features:, max_depth: nil, seed: 0)
        @params = { n_estimators:, max_features:, max_depth:, random_seed: seed }
        @model = nil
      end

      # 特徴量の重要度。列の順は特徴量の列の順のまま。
      def feature_importances
        @model.feature_importances.to_a
      end

      private

      def build
        Rumale::Ensemble::RandomForestClassifier.new(**@params)
      end
    end
  end
end
