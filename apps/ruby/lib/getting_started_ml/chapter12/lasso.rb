# frozen_string_literal: true

module GettingStartedMl
  # 第 12 章のラッソ回帰（L1 正則化）。
  #
  # L1 の罰則は原点で折れているので、リッジ回帰のように行列を解いて終わりにはできない。
  # 係数を 1 つずつ順に動かし、軟しきい値作用素で 0 に寄せる（座標降下法）。
  module Chapter12
    # 繰り返しの上限。
    MAX_ITERATIONS = 10_000
    # 係数の動きがこれより小さくなったら止める。
    TOLERANCE = 1e-10

    # 座標降下法の途中の状態。係数と残差 r = t - Xw を持ち、係数を 1 つ動かすたびに残差を差分で更新する。
    class CoordinateDescent
      attr_reader :weights

      def initialize(features, residuals, alpha)
        @features = features
        @residuals = residuals
        @alpha = alpha
        # 列ごとの 2 乗和。係数を決め直す割り算の分母になる
        @norms = (features**2).sum(axis: 0).to_a
        @weights = Array.new(features.shape[1], 0.0)
      end

      # 係数がどれも TOLERANCE より動かなくなるまで、すべての係数を 1 回ずつ動かす。
      def run
        MAX_ITERATIONS.times { break if sweep < TOLERANCE }
        self
      end

      # すべての係数を 1 回ずつ動かし、いちばん大きく動いた大きさを返す。
      def sweep
        weights.each_index.map { |index| update(index) }.max
      end

      # index 番目の係数を決め直し、動いた大きさを返す。
      def update(index)
        return 0.0 if @norms[index].zero?

        old = weights[index]
        weights[index] = refit(@features[true, index], old, @norms[index])

        (weights[index] - old).abs
      end

      # いったん列の寄与を残差に戻してから、残差との相関で係数を決め直し、新しい係数の寄与を残差から引く。
      def refit(column, old, norm)
        @residuals += old * column
        weight = Chapter12.soft_threshold(column.dot(@residuals), @alpha) / norm
        @residuals -= weight * column
        weight
      end
    end

    module_function

    # 軟しきい値作用素。|value| が threshold 以下なら 0 にし、そうでなければ 0 のほうへ縮める。
    def soft_threshold(value, threshold)
      return value - threshold if value > threshold
      return value + threshold if value < -threshold

      0.0
    end

    # 平均を引いてから座標降下法で ½‖t - Xw‖² + alpha ‖w‖₁ を最小にする。
    # リッジ回帰と尺度をそろえるため、件数で割らない目的関数にする。
    def fit_lasso(columns, rows, t, alpha)
      check_inputs(rows, t, alpha)
      x, residuals, x_means, t_mean = center(rows, t)
      weights = CoordinateDescent.new(x, residuals, alpha).run.weights

      RegularizedModel.new(columns:, coefficients: weights, intercept: intercept_from(weights, x_means, t_mean))
    end
  end
end
