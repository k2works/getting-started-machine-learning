# frozen_string_literal: true

require "numo/narray"

module GettingStartedMl
  # 第 12 章のリッジ回帰（L2 正則化）。
  module Chapter12
    module_function

    # 正則化の強さと、行と正解の件数を確かめる。
    def check_inputs(rows, t, alpha)
      raise ArgumentError, "正則化の強さは 0 以上にしてください: #{alpha}" if alpha.negative?

      Chapter07.check_size(rows.size, t.size)
    end

    # 行と正解から列ごとの平均を引く。切片に罰則をかけないための下ごしらえ。
    # 中心化した行列（Numo）・中心化した正解・列の平均・正解の平均を返す。
    def center(rows, t)
      x = Numo::DFloat.cast(rows)
      x_means = x.mean(axis: 0)
      t_mean = t.sum / t.size

      [x - x_means, Numo::DFloat.cast(t) - t_mean, x_means.to_a, t_mean]
    end

    # 切片を平均から求める。中心化した解に、引いた平均を戻す。
    def intercept_from(coefficients, x_means, t_mean)
      t_mean - coefficients.zip(x_means).sum { |coefficient, mean| coefficient * mean }
    end

    # 平均を引いてから (XᵀX + alpha I) w = Xᵀ t を解き、切片を平均から求める。
    # 目的関数は ‖t - Xw‖² + alpha ‖w‖² で、件数で割らない。alpha が 0 なら第 7 章の最小二乗法と同じ解になる。
    def fit_ridge(columns, rows, t, alpha)
      check_inputs(rows, t, alpha)
      x, residuals, x_means, t_mean = center(rows, t)
      transposed = x.transpose
      # XᵀX の対角に alpha を足す。対角を大きくするほど解が小さいほうへ引き戻される
      normal = transposed.dot(x) + (Numo::DFloat.eye(columns.size) * alpha)
      coefficients = Chapter07.solve(normal, transposed.dot(residuals)).to_a

      RegularizedModel.new(columns:, coefficients:, intercept: intercept_from(coefficients, x_means, t_mean))
    end
  end
end
