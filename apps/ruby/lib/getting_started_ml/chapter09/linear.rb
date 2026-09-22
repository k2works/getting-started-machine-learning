# frozen_string_literal: true

module GettingStartedMl
  # 第 9 章の線形回帰と決定係数。
  module Chapter09
    # 正規方程式を解けない（列が互いに独立でない）ときの失敗。
    class SingularError < StandardError
      def initialize(message = "特徴量の列が互いに独立でないため、正規方程式を解けません")
        super
      end
    end

    # 決定係数を測るための、正規方程式で解く最小の線形回帰。
    LinearModel = Data.define(:intercept, :weights) do
      # 先頭に切片の列（すべて 1）を足し、正規方程式 XᵀX β = Xᵀt を解く。
      def self.fit(rows, t)
        design = rows.map { |row| [1.0, *row] }
        transposed = design.transpose
        normal = transposed.map { |left| transposed.map { |right| Chapter09.dot(left, right) } }
        beta = Chapter09.solve_cholesky(normal, transposed.map { |column| Chapter09.dot(column, t) })

        new(intercept: beta.first, weights: beta.drop(1))
      end

      # 並びを予測する。
      def predict(rows)
        rows.map { |row| intercept + Chapter09.dot(weights, row) }
      end
    end

    module_function

    # 2 つの並びの内積。
    def dot(left, right)
      left.zip(right).sum { |a, b| a * b }
    end

    # 対称正定値の連立方程式 A x = b を、コレスキー分解 A = L Lᵀ で解く。
    def solve_cholesky(matrix, vector)
      lower = cholesky(matrix)

      backward_substitution(lower, forward_substitution(lower, vector))
    end

    # コレスキー分解。対角が 0 以下になれば「列が互いに独立でない」として失敗する。
    def cholesky(matrix)
      lower = Array.new(matrix.size) { Array.new(matrix.size, 0.0) }

      matrix.each_index do |row|
        (0..row).each { |col| lower[row][col] = cholesky_entry(matrix, lower, row, col) }
      end

      lower
    end

    # L の (row, col) 成分。
    def cholesky_entry(matrix, lower, row, col)
      rest = matrix[row][col] - dot(lower[row].first(col), lower[col].first(col))
      return rest / lower[col][col] unless row == col
      raise SingularError if rest <= 0.0

      Math.sqrt(rest)
    end

    # L y = b を前から解く。
    def forward_substitution(lower, vector)
      vector.each_index.with_object([]) do |row, solved|
        solved << ((vector[row] - dot(lower[row].first(row), solved)) / lower[row][row])
      end
    end

    # Lᵀ x = y を後ろから解く。Lᵀ の行と列を逆順にすると下三角になるので、前から解いて逆順に戻す。
    def backward_substitution(lower, vector)
      reversed = lower.transpose.reverse.map(&:reverse)

      forward_substitution(reversed, vector.reverse).reverse
    end

    # 決定係数。1 − 残差の平方和 / 平均からの平方和。
    def r_squared(actual, predicted)
      raise ArgumentError, "値が 1 件もありません" if actual.empty?

      mean = actual.sum / actual.size
      residual = actual.zip(predicted).sum { |value, guess| (value - guess)**2 }

      1.0 - (residual / actual.sum { |value| (value - mean)**2 })
    end
  end
end
