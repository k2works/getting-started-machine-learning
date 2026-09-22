# frozen_string_literal: true

module GettingStartedMl
  # 第 8 章のクラスの重みを付けた決定木。第 3 章の決定木に 1 件ごとの重みを足したもの。
  # 木の型（Leaf・Node・Split）と予測は第 3 章のものをそのまま使う。ラベルが整数でも型を変えずに済む。
  module Chapter08
    # クラスの重みの付け方。
    CLASS_WEIGHTS = %i[none balanced].freeze

    module_function

    # ラベルごとの重みの合計。Hash は先に現れたラベルの順を保つ。
    def weight_sums(labels, weights)
      labels.zip(weights).each_with_object(Hash.new(0.0)) { |(label, weight), sums| sums[label] += weight }
    end

    # 重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。
    def weighted_gini(labels, weights)
      total = weights.sum
      return 0.0 if total.zero?

      1.0 - weight_sums(labels, weights).values.sum { |weight| (weight / total)**2 }
    end

    # クラスの件数に反比例する重み（件数 ÷（クラスの数 × そのクラスの件数））を 1 件ごとに求める。
    def balanced_weights(t)
      counts = t.tally
      t.map { |label| t.size.fdiv(counts.size * counts[label]) }
    end

    # クラスの重みの付け方から、1 件ごとの重みを求める。
    def weights_of(t, class_weight)
      case class_weight
      when :none then Array.new(t.size, 1.0)
      when :balanced then balanced_weights(t)
      else raise ArgumentError, "クラスの重みの付け方が違います: #{class_weight}"
      end
    end

    # 重みの合計が最も大きいラベル。同じなら先に現れたラベルを選ぶ。
    def weighted_majority(labels, weights)
      weight_sums(labels, weights).max_by { |_label, weight| weight }.first
    end

    # 左右の重み付き不純度の、重みによる平均が最も小さくなる分割を返す。分けられなければ nil。
    def best_split(x, t, weights)
      return nil if x.empty? || weighted_gini(t, weights).zero?

      x.first.columns.flat_map { |feature| candidates(x, t.zip(weights), feature) }.min_by(&:impurity)
    end

    # 1 つの列で、隣り合う値の中点を境界にした分割の候補をすべて返す。
    # 第 3 章の sort_by_feature に（ラベル, 重み）の組を渡して、重みも一緒に並べ替える。
    def candidates(x, labeled, feature)
      sorted = Chapter03.sort_by_feature(x, labeled, feature)

      (1...sorted.size).filter_map { |index| split_at(sorted, index, feature) }
    end

    # 並べた組の index 番目の手前で分けた分割を返す。前後の値が同じなら nil。
    def split_at(sorted, index, feature)
      before, after = sorted.values_at(index - 1, index).map(&:first)
      return nil if before == after

      labels, weights = sorted.map(&:last).transpose
      Chapter03::Split.new(feature:, threshold: (before + after) / 2.0,
                           impurity: split_impurity(labels, weights, index))
    end

    # index の手前と後ろに分けたときの、重みによる不純度の平均。
    def split_impurity(labels, weights, index)
      left = weights.first(index)
      right = weights.drop(index)

      ((left.sum * weighted_gini(labels.first(index), left)) +
        (right.sum * weighted_gini(labels.drop(index), right))) / weights.sum
    end

    # 深さの上限まで分割を繰り返して木を作る。max_depth が nil なら上限なし。
    def build(x, t, weights, max_depth)
      split = max_depth&.zero? ? nil : best_split(x, t, weights)
      return Chapter03::Leaf.new(label: weighted_majority(t, weights)) if split.nil?

      left, right = x.zip(t, weights).partition { |features, _, _| Chapter03.goes_left?(split, features) }
      Chapter03::Node.new(split:, left: build(*left.transpose, max_depth&.pred),
                          right: build(*right.transpose, max_depth&.pred))
    end

    # クラスの重みを付けられる決定木の分類器。fit で学習してから predict で予測する。
    class DecisionTreeClassifier
      attr_reader :tree

      def initialize(max_depth: nil, class_weight: :none)
        @max_depth = max_depth
        @class_weight = class_weight
        @tree = nil
      end

      # 訓練データから木を作る。メソッドをつなげられるように自分を返す。
      def fit(x, t)
        Chapter07.check_size(x.size, t.size)
        @tree = Chapter08.build(x, t, Chapter08.weights_of(t, @class_weight), @max_depth)
        self
      end

      # 特徴量ごとのラベルを予測する。
      def predict(x)
        raise "学習してから予測してください" if tree.nil?

        x.map { |features| Chapter03.predict_one(tree, features) }
      end

      def ==(other)
        other.is_a?(DecisionTreeClassifier) && tree == other.tree
      end
    end
  end
end
