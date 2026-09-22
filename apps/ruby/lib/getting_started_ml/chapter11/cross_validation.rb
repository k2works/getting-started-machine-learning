# frozen_string_literal: true

module GettingStartedMl
  # 第 11 章の K 分割交差検証。
  module Chapter11
    # 交差検証の 1 回分の分け方。行の位置（0 始まり）を訓練データとテストデータに分けて持つ。
    Fold = Data.define(:train, :test)

    # 第 7 章の線形回帰を、fit と predict を持つモデルにする。
    # 第 7 章の fit は「学習したモデルを返す関数」なので、学習する前は nil を持つ。
    class LinearRegressionModel
      def initialize
        @model = nil
      end

      # 訓練データで学習する。メソッドをつなげられるように自分を返す。
      def fit(x, t)
        @model = Chapter07.fit(x, t)
        self
      end

      # 特徴量ごとの予測値。
      def predict(x)
        raise "学習してから予測してください" if @model.nil?

        @model.predict(x)
      end
    end

    module_function

    # 並べ替えずに、先頭から順に k 個のかたまりに分ける。余りは先頭の分割から 1 件ずつ配る。
    def k_fold_sequential(n_samples, n_splits)
      check_splits(n_samples, n_splits)
      folds((0...n_samples).to_a, n_splits)
    end

    # シード付きの乱数で行を並べ替えてから k 個のかたまりに分ける。並べ替えは第 2 章の shuffle を使う。
    def k_fold(n_samples, n_splits, seed)
      check_splits(n_samples, n_splits)
      folds(Chapter02.shuffle((0...n_samples).to_a, seed), n_splits)
    end

    # 分割の数が 2 以上、件数以下であることを確かめる。
    def check_splits(n_samples, n_splits)
      return if n_splits.between?(2, n_samples)

      raise ArgumentError, "分割の数は 2 以上 #{n_samples} 以下にしてください: #{n_splits}"
    end

    # 並べた位置を k 個のかたまりに分け、かたまりごとに 1 つをテストデータ、残りを訓練データにする。
    def folds(positions, n_splits)
      sizes = Array.new(n_splits) { |index| (positions.size / n_splits) + (index < positions.size % n_splits ? 1 : 0) }
      tests = sizes.each_with_object([]) { |size, result| result << positions[result.sum(&:size), size] }

      tests.map { |test| Fold.new(train: positions - test, test:) }
    end

    # 分割ごとに新しいモデルを作って訓練データで学習し、テストデータの予測を評価関数で採点する。
    # make_model は呼ぶたびに新しいモデルを返す lambda なので、前の分割で学習した重みが残らない。
    def cross_validate(make_model, x, t, folds, metric)
      folds.map do |fold|
        model = make_model.call.fit(x.values_at(*fold.train), t.values_at(*fold.train))

        metric.call(t.values_at(*fold.test), model.predict(x.values_at(*fold.test)))
      end
    end

    # スコアの平均。
    def mean(scores)
      raise ArgumentError, "スコアが 1 件もありません" if scores.empty?

      scores.sum / scores.size
    end
  end
end
