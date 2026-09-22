# frozen_string_literal: true

module GettingStartedMl
  # 第 3 章の自作の決定木。
  module Chapter03
    # 決定木の分割。特徴量の値が境界以下なら左、境界より大きければ右へ進む。
    Split = Data.define(:feature, :threshold, :impurity)

    # 予測するラベルを持つ葉。
    Leaf = Data.define(:label)

    # 分割と左右の部分木を持つ節。Ruby には判別共用体が無いので、葉と節を別の型にして
    # case/in のパターンマッチで見分ける。網羅しているかは検査されない。
    Node = Data.define(:split, :left, :right)

    module_function

    # ジニ不純度。ラベルが 1 種類なら 0 で、種類が多く均等なほど大きい。
    def gini(labels)
      return 0.0 if labels.empty?

      1.0 - labels.tally.values.sum { |count| count.fdiv(labels.size)**2 }
    end

    # いちばん多いラベルを返す。同数なら先に現れたほうを選ぶ。
    # tally は最初に現れた順を保ち、max_by は同点のとき最初の要素を返す（Rust の max_by_key とは逆）。
    def majority(labels)
      labels.tally.max_by { |_label, count| count }.first
    end

    # 左右の不純度の重み付き平均が最も小さくなる分割を返す。分けられなければ nil。
    # 同じ不純度なら先に見つけた（列の順で前の）分割を選ぶ。
    def best_split(x, t)
      return nil if x.empty? || gini(t).zero?

      x.first.columns.flat_map { |feature| candidates(x, t, feature) }.min_by(&:impurity)
    end

    # 1 つの列で、隣り合う値の中点を境界にした分割の候補をすべて返す。
    def candidates(x, t, feature)
      sorted = sort_by_feature(x, t, feature)

      (1...sorted.size).filter_map { |i| split_at(sorted, i, feature) }
    end

    # 並べた組の i 番目の手前で分けた分割を返す。前後の値が同じなら分けられないので nil。
    def split_at(sorted, index, feature)
      before, after = sorted.values_at(index - 1, index).map(&:first)
      return nil if before == after

      labels = sorted.map(&:last)
      Split.new(feature:, threshold: (before + after) / 2.0,
                impurity: weighted_gini(labels.first(index), labels.drop(index)))
    end

    # 指定した列の値と正解ラベルの組を、値の順に並べる。同じ値なら元の順を保つ（安定な並べ替え）。
    # Ruby の sort_by は安定ではないので、元の位置を 2 つ目の鍵にする。
    def sort_by_feature(x, t, feature)
      x.map { |features| features.value(feature) }.zip(t).each_with_index
       .sort_by { |(value, _label), index| [value, index] }.map(&:first)
    end

    # 左右の不純度の重み付き平均。
    def weighted_gini(left, right)
      ((left.size * gini(left)) + (right.size * gini(right))) / (left.size + right.size)
    end

    # 深さの上限まで分割を繰り返して木を作る。max_depth が nil なら上限なし。
    def build(x, t, max_depth)
      split = max_depth&.zero? ? nil : best_split(x, t)
      return Leaf.new(label: majority(t)) if split.nil?

      left, right = x.zip(t).partition { |features, _| goes_left?(split, features) }
      next_depth = max_depth&.pred

      Node.new(split:, left: build(*left.transpose, next_depth), right: build(*right.transpose, next_depth))
    end

    # 分割の境界以下なら左へ進む。
    def goes_left?(split, features)
      features.value(split.feature) <= split.threshold
    end

    # 木をたどって 1 件のラベルを予測する。
    def predict_one(tree, features)
      case tree
      in Leaf(label:) then label
      in Node(split:, left:, right:) then predict_one(goes_left?(split, features) ? left : right, features)
      end
    end

    # 木を字下げ付きの文字列にする。
    def format(tree, indent = "")
      case tree
      in Leaf(label:) then "#{indent}#{label}\n"
      in Node(split: Split(feature:, threshold:), left:, right:)
        border = Kernel.format("%.4f", threshold)
        "#{indent}#{feature} <= #{border}\n#{format(left, "#{indent}  ")}" \
          "#{indent}#{feature} > #{border}\n#{format(right, "#{indent}  ")}"
      end
    end

    # 自作の決定木の分類器。fit で学習してから predict で予測する。
    class DecisionTree
      attr_reader :tree

      def initialize(max_depth: nil)
        @max_depth = max_depth
        @tree = nil
      end

      # 訓練データから木を作る。メソッドをつなげられるように自分を返す。
      def fit(x, t)
        @tree = Chapter03.build(x, t, @max_depth)
        self
      end

      # 特徴量ごとのラベルを予測する。
      def predict(x)
        raise "学習してから予測してください" if tree.nil?

        x.map { |features| Chapter03.predict_one(tree, features) }
      end
    end
  end
end
