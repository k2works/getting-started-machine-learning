# frozen_string_literal: true

module GettingStartedMl
  # 第 10 章のランダムフォレスト。第 3 章の決定木を束ねる。
  module Chapter10
    # ランダムフォレストの 1 本分。使った特徴量の列、ブートストラップ標本の行番号、学習した決定木。
    FittedTree = Data.define(:columns, :rows, :model)

    module_function

    # サンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ（第 3 章の majority と同じ）。
    def majority_vote(votes)
      return [] if votes.empty?

      votes.transpose.map { |labels| Chapter03.majority(labels) }
    end

    # 0 から size - 1 までの行番号を、重複を許して size 個選ぶ。
    def bootstrap_sample(size, rng)
      Array.new(size) { rng.rand(size) }
    end

    # 重複なしで count 個の列を選ぶ。選んだ列は元の順に並べ直す。
    def choose(columns, count, rng)
      columns & columns.sample(count, random: rng)
    end

    # 指定した列だけを取り出した特徴量にする。
    def select_columns(x, columns)
      x.map { |features| Chapter02::Features.new(columns:, values: columns.map { |column| features.value(column) }) }
    end

    # 自作のランダムフォレスト。木ごとにブートストラップ標本と特徴量の部分集合を選び、第 3 章の決定木を学習する。
    class RandomForest
      attr_reader :trees

      # max_depth が nil なら木の深さを制限しない。
      def initialize(n_estimators:, max_features:, max_depth: nil, seed: 0)
        @n_estimators = n_estimators
        @max_features = max_features
        @max_depth = max_depth
        @seed = seed
        @trees = nil
      end

      # 乱数の生成器を 1 つだけ作り、森全体で使い回す（木ごとに作り直すと全部の木が同じ標本になる）。
      def fit(x, t)
        rng = Random.new(@seed)
        @trees = Array.new(@n_estimators) { plant(x, t, rng) }
        self
      end

      # 全部の木の予測の多数決で予測する。
      def predict(x)
        raise "学習してから予測してください" if trees.nil?

        Chapter10.majority_vote(trees.map { |tree| tree.model.predict(Chapter10.select_columns(x, tree.columns)) })
      end

      private

      # 1 本分の標本と列を選び、決定木を学習する。
      def plant(x, t, rng)
        rows = Chapter10.bootstrap_sample(x.size, rng)
        columns = Chapter10.choose(x.first.columns, @max_features, rng)
        sample_x = Chapter10.select_columns(x.values_at(*rows), columns)
        model = Chapter03::DecisionTree.new(max_depth: @max_depth).fit(sample_x, t.values_at(*rows))

        FittedTree.new(columns:, rows:, model:)
      end
    end
  end
end
