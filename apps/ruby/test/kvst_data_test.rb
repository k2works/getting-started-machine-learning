# frozen_string_literal: true

require "test_helper"

# 実データ（KvsT.csv）を使うテスト。学習データが無ければスキップする。
class KvsTDataTest < Minitest::Test
  # module_function のモジュールを include すると、章の run が Minitest の run を上書きするので、
  # include せずに定数と関数をモジュールから呼ぶ。
  C = GettingStartedMl::Chapter01

  def csv_file
    path = File.join(GettingStartedMl::Dataset.dir, "KvsT.csv")
    skip "学習データ KvsT.csv が配置されていない（gulp data:setup）のでスキップする" unless File.exist?(path)

    path
  end

  def test_実データを読み込んで正解率を求める
    people = C.load_people(csv_file)

    assert_equal 19, people.size

    x, t = C.split_features_and_labels(people)
    predictions = x.map { |features| C.predict_by_rule(features) }

    assert_in_delta 14.0 / 19, C.accuracy(predictions, t), 1e-12
  end
end
