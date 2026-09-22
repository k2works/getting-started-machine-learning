# frozen_string_literal: true

require_relative "chapter08/preprocessing"
require_relative "chapter08/weighted_tree"
require_relative "chapter08/survived"
require_relative "chapter08/pipeline"
require_relative "chapter08/model_file"
require_relative "chapter08/evaluation"

module GettingStartedMl
  # 第 8 章: 実践的な分類と前処理パイプライン。
  #
  # 欠損値の補完（グループ中央値・最頻値）とダミー変数化は Rumale に無いので自作が最終的な実装になる。
  # Rumale の決定木はクラスの重みを受け取らないので、突き合わせは重みを付けない木だけにする。
  module Chapter08
    # テストデータの割合。
    TEST_SIZE = 0.2
    # 分割の乱数のシード。
    SEED = 0
    # 決定木の深さの上限。
    MAX_DEPTH = 5
    # 学習済みのパイプラインの保存先（apps/ruby/model/ は .gitignore の対象）。
    MODEL_FILE = File.join("model", "survived.dump")
    # 架空の乗客。1 等客室の女性（運賃 50、C 港）と 3 等客室の男性（運賃 8、S 港）。どちらも年齢が分からない。
    NEW_PASSENGERS = [["1", "female", "", "0", "0", "50", "C"], ["3", "male", "", "0", "0", "8", "S"]].freeze

    # クラスの重みごとの評価結果を表示し、学習済みのパイプラインを保存して読み込む。
    def self.run(out = $stdout, model_file = MODEL_FILE)
      rows = Chapter02::Table.load(File.join(Dataset.dir, "Survived.csv")).rows
      t = Survived.target(rows)
      split = Chapter02.split_train_test(rows, t, test_size: TEST_SIZE, seed: SEED)

      out.puts summary(rows, t, split)
      balanced = CLASS_WEIGHTS.map { |class_weight| evaluate_with(out, class_weight, split) }.last
      out.puts predict_with_saved(balanced, model_file)
    end

    # 件数を表示用の 2 行にする。
    def self.summary(rows, t, split)
      survivors = t.count(Survived::SURVIVED)

      ["データ件数: #{rows.size}（生存 #{survivors}, 死亡 #{t.size - survivors}）",
       "訓練データ: #{split.x_train.size} 件, テストデータ: #{split.x_test.size} 件"]
    end

    # クラスの重みを指定して学習し、評価を 1 行表示する。学習済みのパイプラインを返す。
    def self.evaluate_with(out, class_weight, split)
      fitted = Pipeline.build(max_depth: MAX_DEPTH, class_weight:)
                       .fit(Survived.features(split.x_train), split.t_train)
      result = evaluate(fitted, split)
      out.puts Kernel.format("classWeight=%<name>s: 訓練 %<train>.3f, テスト %<test>.3f, " \
                             "生存者 %<survivors>d 人中 %<found>d 人を発見",
                             name: class_weight, train: result.train_accuracy, test: result.test_accuracy,
                             survivors: result.survivors, found: result.found_survivors)
      fitted
    end

    # 保存して読み込んだパイプラインで、架空の乗客を予測する。
    def self.predict_with_saved(pipeline, model_file)
      ModelFile.save(pipeline, model_file)
      loaded = ModelFile.load(model_file)
      passengers = Survived.features(NEW_PASSENGERS.map { |values| Survived.passenger(values) })

      ["保存したモデル: #{File.basename(model_file)}", "架空の乗客の予測: #{loaded.predict(passengers)}"]
    end
  end
end
