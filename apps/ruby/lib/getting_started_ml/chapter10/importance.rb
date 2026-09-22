# frozen_string_literal: true

module GettingStartedMl
  # 第 10 章の特徴量の重要度。
  module Chapter10
    # 訓練データとテストデータの正解率。
    Score = Data.define(:train, :test) do
      # モデルを訓練データで学習させてから、訓練データとテストデータの正解率を求める。
      # fit と predict を持つものなら何でも受け取る（ダックタイピング）。
      def self.evaluate(model, split)
        model.fit(split.x_train, split.t_train)

        new(train: Chapter01.accuracy(model.predict(split.x_train), split.t_train),
            test: Chapter01.accuracy(model.predict(split.x_test), split.t_test))
      end
    end

    module_function

    # 決定木 1 本の特徴量の重要度。分割ごとに減った不純度（件数で重み付け）を足し、合計が 1 になるように割る。
    # 列の順は特徴量の列の順のまま、[列名, 重要度] の組で返す。
    def tree_importances(tree, x, t)
      totals = x.first.columns.to_h { |column| [column, 0.0] }
      accumulate(tree, x, t, totals)

      normalize(totals).to_a
    end

    # 森の重要度。木ごとに正規化してから平均する。木が使わなかった特徴量は、その木では 0 として扱う。
    # 葉だけの木（標本が 1 種類のラベルだけ）は重要度が全部 0 なので、最後にもう一度合計が 1 になるように割る。
    def forest_importances(forest, x, t)
      totals = x.first.columns.to_h { |column| [column, 0.0] }

      forest.trees.each do |fitted|
        fitted_importances(fitted, x, t).each { |column, value| totals[column] += value / forest.trees.size }
      end

      normalize(totals).to_a
    end

    # 森の 1 本分の重要度。その木が学習した標本（ブートストラップ標本と選んだ列）で求める。
    def fitted_importances(fitted, x, t)
      tree_importances(fitted.model.tree, select_columns(x.values_at(*fitted.rows), fitted.columns),
                       t.values_at(*fitted.rows))
    end

    # 木をたどって、分割ごとに減った不純度（件数で重み付け）を足し込む。葉では何もしない。
    def accumulate(tree, x, t, totals)
      return unless tree in Chapter03::Node(split:, left:, right:)

      totals[split.feature] += t.size * (Chapter03.gini(t) - split.impurity)
      goes_left, goes_right = x.zip(t).partition { |features, _| Chapter03.goes_left?(split, features) }
      accumulate(left, *goes_left.transpose, totals)
      accumulate(right, *goes_right.transpose, totals)
    end

    # 合計が 1 になるように割る。合計が 0（分割が無い）ならそのまま返す。
    def normalize(totals)
      sum = totals.values.sum
      sum.zero? ? totals : totals.transform_values { |value| value / sum }
    end
  end
end
