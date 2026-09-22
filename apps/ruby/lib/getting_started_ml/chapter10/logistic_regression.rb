# frozen_string_literal: true

require "numo/narray"

module GettingStartedMl
  # 第 10 章のロジスティック回帰。
  module Chapter10
    # 対数の中身が 0 にならないように足す小さな値。
    EPSILON = 1e-12

    module_function

    # スコアを、合計が 1 になる確率に変換する。
    def softmax(scores)
      softmax_rows(Numo::DFloat[scores]).to_a.first
    end

    # 行列の行ごとにソフトマックスを求める。
    # 行ごとの最大値を引いてから exp を求めるので、大きな値でもあふれない。
    def softmax_rows(scores)
      exps = Numo::NMath.exp(scores - scores.max(axis: 1).expand_dims(1))

      exps / exps.sum(axis: 1).expand_dims(1)
    end

    # 交差エントロピー。正解の品種の確率の対数の平均に、マイナスを付けたもの。
    def cross_entropy(probabilities, targets)
      return 0.0 if probabilities.empty?

      -probabilities.zip(targets).sum { |probability, target| Math.log(probability[target] + EPSILON) } /
        probabilities.size
    end

    # ソフトマックスと勾配降下法で学習するロジスティック回帰。
    class LogisticRegression
      attr_reader :classes, :losses

      def initialize(learning_rate: 1.0, epochs: 5000)
        @learning_rate = learning_rate
        @epochs = epochs
        @weights = nil
      end

      # 訓練データで学習する。繰り返しごとの損失を losses に残す。メソッドをつなげられるように自分を返す。
      def fit(x, t)
        @classes = t.uniq.sort
        features = Numo::DFloat.cast(x.map(&:values))
        targets = t.map { |label| @classes.index(label) }
        @weights = Numo::DFloat.zeros(features.shape[1], @classes.size)
        @bias = Numo::DFloat.zeros(@classes.size)
        @losses = Array.new(@epochs) { step(features, targets, one_hot(targets)) }
        self
      end

      # 特徴量ごとのラベルを予測する。
      def predict(x)
        raise "学習してから予測してください" if @weights.nil?

        scores(Numo::DFloat.cast(x.map(&:values))).to_a.map { |row| @classes[row.index(row.max)] }
      end

      private

      # 品種ごとのスコア（特徴量の重み付きの和 + 切片）。
      def scores(features)
        features.dot(@weights) + @bias
      end

      # 全データの勾配を集めてから 1 回だけ重みを動かす（バッチ勾配降下法）。更新前の損失を返す。
      # 勾配は「確率 − 正解（正解の品種だけ 1）」を特徴量で重み付けしたものになる。
      def step(features, targets, expected)
        probabilities = Chapter10.softmax_rows(scores(features))
        errors = probabilities - expected
        count = features.shape[0]
        @weights -= @learning_rate * features.transpose.dot(errors) / count
        @bias -= @learning_rate * errors.sum(axis: 0) / count

        Chapter10.cross_entropy(probabilities.to_a, targets)
      end

      # 正解の品種の位置だけ 1 にした行列。
      def one_hot(targets)
        Numo::DFloat.zeros(targets.size, @classes.size).tap do |matrix|
          targets.each_with_index { |target, row| matrix[row, target] = 1.0 }
        end
      end
    end
  end
end
