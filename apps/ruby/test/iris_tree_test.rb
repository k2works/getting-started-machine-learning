# frozen_string_literal: true

require "test_helper"
require "stringio"

# 実データ（iris.csv）で決定木を学習するテスト。学習データが無ければスキップする。
class IrisTreeTest < Minitest::Test
  C = GettingStartedMl::Chapter03

  def split
    path = File.join(GettingStartedMl::Dataset.dir, "iris.csv")
    skip "学習データ iris.csv が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

    GettingStartedMl::Chapter02.prepare_iris(path, test_size: 0.3, seed: 0)
  end

  def test_深さ二の決定木はテストデータの四十五件中四十三件を正しく分類する
    data = split
    predictions = C::DecisionTree.new(max_depth: 2).fit(data.x_train, data.t_train).predict(data.x_test)

    assert_in_delta 43.0 / 45, GettingStartedMl::Chapter01.accuracy(predictions, data.t_test), 1e-12
  end

  # 深さを制限しないと予測が 1 件分かれる。Rumale は節ごとに特徴量をランダムな順で調べるので、
  # 同じ不純度の分割が並ぶと、列の順で先勝ちにする自作とは違う分割を選ぶことがある
  def test_深さ五までは自作とRumaleの予測が一致する
    data = split

    [1, 2, 3, 4, 5].each do |max_depth|
      ours = C::DecisionTree.new(max_depth:).fit(data.x_train, data.t_train).predict(data.x_test)

      assert_equal ours, C::RumaleTree.predict(data.x_train, data.t_train, data.x_test, max_depth), "深さ #{max_depth}"
    end
  end

  def test_実行すると深さごとの正解率と決定木を表示する
    split
    out = StringIO.new
    C.run(out)

    assert_equal <<~TEXT, out.string
      深さ\t訓練データ\tテストデータ\tRumale
      1\t0.6762\t0.6444\t0.6444
      2\t0.9333\t0.9556\t0.9556
      3\t0.9524\t0.9556\t0.9556
      4\t0.9524\t0.9556\t0.9556
      5\t0.9714\t0.9556\t0.9556
      制限なし\t1.0000\t0.9556\t0.9333

      深さ 2 の決定木:
      花弁幅 <= 0.2950
        Iris-setosa
      花弁幅 > 0.2950
        花弁幅 <= 0.6500
          Iris-versicolor
        花弁幅 > 0.6500
          Iris-virginica
    TEXT
  end
end
