# frozen_string_literal: true

module GettingStartedMl
  # 第 12 章の正則化した線形回帰のモデル。
  module Chapter12
    # 列名の付いた係数と切片を持つモデル。行は値だけの配列（列の並びは学習したときと同じ）で受け取る。
    RegularizedModel = Data.define(:columns, :coefficients, :intercept) do
      def initialize(columns:, coefficients:, intercept:)
        Chapter07.check_size(columns.size, coefficients.size)

        super
      end

      # 1 行分の予測値。
      def predict_one(row)
        Chapter07.check_size(coefficients.size, row.size)

        row.zip(coefficients).sum(intercept) { |value, coefficient| value * coefficient }
      end

      # 行ごとの予測値。
      def predict(rows)
        rows.map { |row| predict_one(row) }
      end

      # 係数の絶対値の合計。正則化が強いほど小さくなる。
      def coefficient_abs_sum
        coefficients.sum(&:abs)
      end

      # 係数がちょうど 0 になった列の名前を、列の順に返す。
      def zero_columns
        columns.zip(coefficients).select { |_, coefficient| coefficient.zero? }.map(&:first)
      end
    end
  end
end
