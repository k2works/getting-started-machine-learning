# frozen_string_literal: true

module GettingStartedMl
  # 第 9 章の多項式特徴量。
  module Chapter09
    # 2 つの列の組。左と右が同じなら 2 乗の項を表す。
    Pair = Data.define(:left, :right) do
      # 項の名前。scikit-learn の get_feature_names_out と同じ形（"RM^2"・"RM LSTAT"）にする。
      def name
        left == right ? "#{left}^2" : "#{left} #{right}"
      end

      # 項の値。2 つの列の値の積。
      def value(features)
        features.value(left) * features.value(right)
      end
    end

    module_function

    # 重複を許して 2 つの列を選ぶ組を、scikit-learn の PolynomialFeatures と同じ順に並べる。
    # Array#repeated_combination が、ちょうどこの順で組を返す。
    def pairs_with_replacement(columns)
      columns.repeated_combination(2).map { |left, right| Pair.new(left:, right:) }
    end

    # 指定した列から作った 2 乗の項と交互作用の項を、特徴量の末尾に加える。
    def expand(x, columns)
      pairs = pairs_with_replacement(columns)

      x.map do |features|
        Chapter02::Features.new(columns: features.columns + pairs.map(&:name),
                                values: features.values + pairs.map { |pair| pair.value(features) })
      end
    end

    # 使う項だけを、指定した順に選ぶ。無い項を選ぶと第 2 章の KeyError になる。
    def select(x, terms)
      x.map do |features|
        Chapter02::Features.new(columns: terms, values: terms.map { |term| features.value(term) })
      end
    end
  end
end
