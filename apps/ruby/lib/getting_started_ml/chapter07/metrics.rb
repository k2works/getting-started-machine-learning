# frozen_string_literal: true

module GettingStartedMl
  # 第 7 章の回帰の評価指標。t は実測値、y は予測値。第 11・12 章でも使う。
  module Chapter07
    module_function

    # 実測値と予測値の差を返す。件数が違えば ArgumentError を投げる。
    def residuals(t, y)
      raise ArgumentError, "件数が違います: #{t.size} と #{y.size}" unless t.size == y.size

      t.zip(y).map { |actual, predicted| actual - predicted }
    end

    # 平均絶対誤差（MAE）。誤差の絶対値の平均。
    def mean_absolute_error(t, y)
      residuals(t, y).sum(&:abs) / t.size
    end

    # 平均二乗誤差の平方根（RMSE）。
    def root_mean_squared_error(t, y)
      Math.sqrt(sum_of_squares(residuals(t, y)) / t.size)
    end

    # 決定係数（R²）。1 から「残差の 2 乗の合計 ÷ 平均との差の 2 乗の合計」を引いた値。
    def r2_score(t, y)
      residual = sum_of_squares(residuals(t, y))
      mean = t.sum / t.size

      1.0 - (residual / sum_of_squares(t.map { |value| value - mean }))
    end

    # 2 乗の合計。
    def sum_of_squares(values)
      values.sum { |value| value * value }
    end
  end
end
