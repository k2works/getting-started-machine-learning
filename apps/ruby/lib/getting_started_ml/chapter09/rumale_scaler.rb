# frozen_string_literal: true

require "rumale"

module GettingStartedMl
  module Chapter09
    # Rumale の標準化を呼ぶ。自作の標準化と突き合わせるために使う。
    module RumaleScaler
      module_function

      # 訓練データの値から平均と標準偏差を求め、別の値を標準化する。
      # Rumale は行列を受け取るので、1 列の行列にしてから渡す。
      def standardize(train, values)
        scaler = Rumale::Preprocessing::StandardScaler.new.fit(column(train))

        scaler.transform(column(values)).to_a.flatten
      end

      # 値の並びを 1 列の行列にする。
      def column(values)
        Numo::DFloat.cast(values.map { |value| [value] })
      end
    end
  end
end
