# frozen_string_literal: true

require "rumale"

module GettingStartedMl
  module Chapter07
    # Rumale の線形回帰で学習する。自作の正規方程式と結果を突き合わせるために使う。
    module RumaleRegression
      # 最適化を止める許容誤差。既定の 1e-4 では厳密な解の手前で止まる。
      TOLERANCE = 1e-10

      module_function

      # Rumale で学習し、自作と同じ LinearModel にして返す。
      # Rumale は切片を自分で足す（fit_bias: true）ので、計画行列ではなく特徴量をそのまま渡す。
      def fit(x, t)
        raise ArgumentError, "特徴量がありません" if x.empty?

        model = Rumale::LinearModel::LinearRegression.new(tol: TOLERANCE)
        model.fit(Numo::DFloat.cast(x.map(&:values)), Numo::DFloat.cast(t))

        LinearModel.new(intercept: model.bias_term, columns: x.first.columns, coefficients: model.weight_vec.to_a)
      end
    end
  end
end
