# frozen_string_literal: true

require "numo/narray"

module GettingStartedMl
  # 第 7 章の正規方程式による線形回帰。
  module Chapter07
    # 学習した線形回帰のモデル。切片と、列名と同じ順に並んだ係数を持つ。
    # Hash でも列の順は保てるが、第 2 章の Features と同じく列名と値を別々の配列で持つ。
    LinearModel = Data.define(:intercept, :columns, :coefficients) do
      def initialize(intercept:, columns:, coefficients:)
        Chapter07.check_size(columns.size, coefficients.size)

        super
      end

      # 列名で係数を読む。列が無ければ KeyError を投げる。
      def coefficient(column)
        index = columns.index(column)
        raise KeyError, "列がありません: #{column}" if index.nil?

        coefficients[index]
      end

      # 1 行分の特徴量の予測値。係数は列名で対応させるので、特徴量の列の並び順は問わない。
      def predict_one(features)
        columns.zip(coefficients).sum(intercept) { |column, weight| weight * features.value(column) }
      end

      # 行ごとの予測値。
      def predict(x)
        x.map { |features| predict_one(features) }
      end
    end

    module_function

    # 先頭に 1 の列を足した特徴量の行列（計画行列）を作る。1 の列の係数が切片になる。
    def design_matrix(x)
      Numo::DFloat.cast(x.map { |features| [1.0, *features.values] })
    end

    # (Xᵀ X) w = Xᵀ t を解いて、切片と係数を求める。列名は先頭の行の特徴量から取る。
    def fit(x, t)
      check_size(x.size, t.size)
      raise ArgumentError, "特徴量がありません" if x.empty?

      weights = normal_equation(design_matrix(x), Numo::DFloat.cast(t))

      LinearModel.new(intercept: weights[0], columns: x.first.columns, coefficients: weights[1..].to_a)
    end

    # 計画行列と実測値から、正規方程式 (Xᵀ X) w = Xᵀ t の解 w を求める。
    def normal_equation(design, t)
      transposed = design.transpose

      solve(transposed.dot(design), transposed.dot(t))
    end
  end
end
