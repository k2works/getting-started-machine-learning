# frozen_string_literal: true

require "test_helper"
require "stringio"

# 実データ（Survived.csv）で前処理パイプラインを確かめるテスト。学習データが無ければスキップする。
class SurvivedPipelineTest < Minitest::Test
  C = GettingStartedMl::Chapter08

  def rows
    path = File.join(GettingStartedMl::Dataset.dir, "Survived.csv")
    skip "学習データ Survived.csv が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

    GettingStartedMl::Chapter02::Table.load(path).rows
  end

  def split
    data = rows
    GettingStartedMl::Chapter02.split_train_test(data, C::Survived.target(data), test_size: 0.2, seed: 0)
  end

  def fitted(data, class_weight)
    C::Pipeline.build(max_depth: 5, class_weight:).fit(C::Survived.features(data.x_train), data.t_train)
  end

  # 前処理の済んだ訓練データとテストデータの特徴量。
  def prepared(data)
    pipeline = fitted(data, :none)
    [data.x_train, data.x_test].map { |x| pipeline.features(C::Survived.features(x)) }
  end

  def test_実データは八百九十一件で生存は三百四十二人
    data = rows

    assert_equal 891, data.size
    assert_equal 342, C::Survived.target(data).count(1)
  end

  def test_重みを付けると見つかる生存者が増える
    data = split
    none = C.evaluate(fitted(data, :none), data)
    balanced = C.evaluate(fitted(data, :balanced), data)

    assert_equal [81, 61, 63], [none.survivors, none.found_survivors, balanced.found_survivors]
    # 見つかる生存者は増えるが、全体の正解率はわずかに下がる
    assert_operator balanced.test_accuracy, :<, none.test_accuracy
  end

  def test_保存して読み込んだモデルは同じ予測をする
    data = split
    pipeline = fitted(data, :balanced)
    x_test = C::Survived.features(data.x_test)

    Dir.mktmpdir do |dir|
      model_file = File.join(dir, "survived.dump")
      C::ModelFile.save(pipeline, model_file)

      assert_equal pipeline.predict(x_test), C::ModelFile.load(model_file).predict(x_test)
    end
  end

  # Rumale は節ごとに特徴量をランダムな順で調べるので、同じ不純度の分割が並ぶとシードで結果が変わる
  def test_Rumale_の決定木はシードを変えると予測が変わることがある
    data = split
    x_train, x_test = prepared(data)
    by_seed = [0, 2].map { |seed| rumale_predict(x_train, data.t_train, x_test, seed) }

    assert_equal(1, by_seed.transpose.count { |left, right| left != right })
  end

  # 深さ 4 の Rumale の決定木を、シードを指定して学習して予測する。
  def rumale_predict(x_train, t_train, x_test, seed)
    model = Rumale::Tree::DecisionTreeClassifier.new(max_depth: 4, random_seed: seed)
    model.fit(Numo::DFloat.cast(x_train.map(&:values)), Numo::Int32.cast(t_train))
    model.predict(Numo::DFloat.cast(x_test.map(&:values))).to_a
  end

  def test_実行すると重みごとの評価と架空の乗客の予測を表示する
    rows
    out = StringIO.new
    Dir.mktmpdir { |dir| C.run(out, File.join(dir, "survived.dump")) }

    # Ruby の Random の分け方がほかの言語版と違うので、正解率も予測もほかの言語版と一致しない
    assert_equal <<~TEXT, out.string
      データ件数: 891（生存 342, 死亡 549）
      訓練データ: 712 件, テストデータ: 179 件
      classWeight=none: 訓練 0.847, テスト 0.816, 生存者 81 人中 61 人を発見
      classWeight=balanced: 訓練 0.837, テスト 0.810, 生存者 81 人中 63 人を発見
      保存したモデル: survived.dump
      架空の乗客の予測: [1, 0]
    TEXT
  end
end
