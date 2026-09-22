# frozen_string_literal: true

module GettingStartedMl
  # 第 9 章の標準化。
  module Chapter09
    # 列ごとの平均と標準偏差。訓練データで fit し、同じ値で訓練データとテストデータの両方を
    # transform する。列の順は特徴量の列の順のまま。
    # 標準偏差は件数で割る（母標準偏差）。すべて同じ値の列は 1 にして、標準化した値が 0 になるようにする。
    Standardizer = Data.define(:columns, :means, :stds) do
      # 特徴量のすべての列について、平均と標準偏差を求める。
      def self.fit(x)
        raise ArgumentError, "特徴量が 1 件もありません" if x.empty?

        columns = x.first.columns
        stats = columns.map { |column| Chapter09.mean_and_std(x.map { |features| features.value(column) }) }

        new(columns:, means: stats.map(&:first), stds: stats.map(&:last))
      end

      # 列名で平均を読む。
      def mean(column)
        means.fetch(index(column))
      end

      # 列名で標準偏差を読む。
      def std(column)
        stds.fetch(index(column))
      end

      # 並びを標準化する。
      def transform(x)
        x.map { |features| transform_one(features) }
      end

      # 1 件を標準化する。平均を持たない列はそのまま残す。
      def transform_one(features)
        values = features.columns.zip(features.values).map do |column, value|
          position = columns.index(column)
          position.nil? ? value : (value - means[position]) / stds[position]
        end

        features.with(values:)
      end

      private

      def index(column)
        columns.index(column) || raise(KeyError, "列がありません: #{column}")
      end
    end

    module_function

    # 平均と、件数で割る標準偏差の組。標準偏差が 0 なら 1 に置き換える。
    def mean_and_std(values)
      mean = values.sum / values.size
      std = Math.sqrt(values.sum { |value| (value - mean)**2 } / values.size)

      [mean, std.zero? ? 1.0 : std]
    end
  end
end
