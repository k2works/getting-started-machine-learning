# frozen_string_literal: true

require_relative "chapter03/decision_tree"
require_relative "chapter03/rumale_tree"

module GettingStartedMl
  # 第 3 章: 決定木による分類。自作の決定木と Rumale の決定木を突き合わせる。
  module Chapter03
    # テストデータの割合。
    TEST_SIZE = 0.3
    # 分割の乱数のシード。
    SEED = 0
    # 最後に木そのものを表示する深さ。
    TREE_DEPTH_TO_SHOW = 2
    # 正解率を比べる深さ。nil は制限なし。
    MAX_DEPTHS = [1, 2, 3, 4, 5, nil].freeze

    # 深さごとの正解率と、深さ 2 の決定木を表示する。
    def self.run(out = $stdout)
      split = Chapter02.prepare_iris(File.join(Dataset.dir, "iris.csv"), test_size: TEST_SIZE, seed: SEED)

      out.puts "深さ\t訓練データ\tテストデータ\tRumale"
      MAX_DEPTHS.each { |max_depth| out.puts accuracy_row(max_depth, split) }
      out.puts
      out.puts "深さ #{TREE_DEPTH_TO_SHOW} の決定木:"
      out.print format(DecisionTree.new(max_depth: TREE_DEPTH_TO_SHOW).fit(split.x_train, split.t_train).tree)
    end

    # 自作と Rumale で学習し、正解率を 1 行にする。
    def self.accuracy_row(max_depth, split)
      [max_depth || "制限なし", *results(max_depth, split).map { |predictions, labels| score(predictions, labels) }]
        .join("\t")
    end

    # 自作の訓練データ・テストデータと、Rumale のテストデータについて、予測と正解ラベルの組を返す。
    def self.results(max_depth, split)
      model = DecisionTree.new(max_depth:).fit(split.x_train, split.t_train)

      [[model.predict(split.x_train), split.t_train],
       [model.predict(split.x_test), split.t_test],
       [RumaleTree.predict(split.x_train, split.t_train, split.x_test, max_depth), split.t_test]]
    end

    # 正解率を小数 4 桁の文字列にする。
    def self.score(predictions, labels)
      Kernel.format("%.4f", Chapter01.accuracy(predictions, labels))
    end
  end
end
