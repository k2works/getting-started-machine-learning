# frozen_string_literal: true

require "rumale"

module GettingStartedMl
  module Chapter03
    # Rumale の決定木で学習して予測する。自作の決定木と結果を突き合わせるために使う。
    module RumaleTree
      module_function

      # 文字列のラベルを、最初に現れた順の番号にする。番号と、番号からラベルへの対応表を返す。
      def encode(labels)
        classes = labels.uniq
        [labels.map { |label| classes.index(label) }, classes]
      end

      # 特徴量の並びを Numo の行列にする。
      def matrix(x)
        Numo::DFloat.cast(x.map(&:values))
      end

      # 訓練データで学習し、テストデータのラベルを予測する。
      def predict(x_train, t_train, x_test, max_depth)
        codes, classes = encode(t_train)
        model = Rumale::Tree::DecisionTreeClassifier.new(criterion: "gini", max_depth:, random_seed: 0)
        model.fit(matrix(x_train), Numo::Int32.cast(codes))

        model.predict(matrix(x_test)).to_a.map { |code| classes[code] }
      end
    end
  end
end
