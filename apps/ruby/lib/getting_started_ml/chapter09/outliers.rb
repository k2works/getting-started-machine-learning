# frozen_string_literal: true

module GettingStartedMl
  # 第 9 章の外れ値の検出。
  module Chapter09
    # 外れ値とみなす範囲の、四分位範囲に掛ける倍率。
    DEFAULT_K = 1.5

    module_function

    # 分位数を求める。位置が値の間にあれば前後の値から線形補間する
    # （pandas の quantile の既定と同じ）。
    def quantile(values, ratio)
      raise ArgumentError, "値が 1 件もありません" if values.empty?

      sorted = values.sort
      position = (sorted.size - 1) * ratio
      lower = sorted[position.floor]

      lower + ((sorted[position.ceil] - lower) * (position - position.floor))
    end

    # 四分位範囲（IQR）の factor 倍より外側にある値を true にした並びを返す。
    def iqr_outliers(values, factor = DEFAULT_K)
      q1 = quantile(values, 0.25)
      q3 = quantile(values, 0.75)
      range = (q1 - (factor * (q3 - q1)))..(q3 + (factor * (q3 - q1)))

      values.map { |value| !range.cover?(value) }
    end

    # 訓練データから、正解の値が外れ値の行を取り除く。テストデータはそのまま残す。
    def remove_target_outliers(split)
      outliers = iqr_outliers(split.t_train)
      kept = split.x_train.zip(split.t_train).reject.with_index { |_, index| outliers[index] }
      x_train, t_train = kept.transpose

      split.with(x_train: x_train || [], t_train: t_train || [])
    end
  end
end
