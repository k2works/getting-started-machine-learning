# frozen_string_literal: true

require "test_helper"

class Chapter01Test < Minitest::Test
  # module_function のモジュールを include すると、章の run が Minitest の run を上書きするので、
  # include せずに定数と関数をモジュールから呼ぶ。
  C = GettingStartedMl::Chapter01

  def features(height, weight, age_group)
    C::Features.new(height:, weight:, age_group:)
  end

  def test_二十代はきのこ派と判定する
    assert_equal C::KINOKO, C.predict_by_rule(features(170, 60, 20))
  end

  def test_二十代以外はたけのこ派と判定する
    [10, 30, 40, 50].each do |age_group|
      assert_equal C::TAKENOKO, C.predict_by_rule(features(170, 60, age_group))
    end
  end

  def test_全部当たれば正解率は一になる
    assert_in_delta 1.0, C.accuracy([C::KINOKO, C::TAKENOKO], [C::KINOKO, C::TAKENOKO])
  end

  def test_半分当たれば正解率は零点五になる
    assert_in_delta 0.5, C.accuracy([C::KINOKO, C::KINOKO], [C::KINOKO, C::TAKENOKO])
  end

  def test_件数が違えば正解率を求められない
    error = assert_raises(ArgumentError) { C.accuracy([C::KINOKO], [C::KINOKO, C::TAKENOKO]) }

    assert_equal "予測と正解ラベルの件数が違います: 1 と 2", error.message
  end

  def test_人物のリストを特徴量と正解ラベルに分ける
    people = [
      C::Person.new(height: 170, weight: 60, age_group: 20, faction: C::KINOKO),
      C::Person.new(height: 160, weight: 50, age_group: 30, faction: C::TAKENOKO)
    ]

    x, t = C.split_features_and_labels(people)

    assert_equal [features(170, 60, 20), features(160, 50, 30)], x
    assert_equal [C::KINOKO, C::TAKENOKO], t
  end

  def test_BOM_付きの_CSV_を列名で読み込む
    Dir.mktmpdir do |dir|
      path = File.join(dir, "people.csv")
      File.write(path, "\uFEFF身長,体重,年代,派閥\n170,60,20,きのこ\n")

      assert_equal [C::Person.new(height: 170, weight: 60, age_group: 20, faction: C::KINOKO)], C.load_people(path)
    end
  end

  def test_数値でない値があれば読み込めない
    Dir.mktmpdir do |dir|
      path = File.join(dir, "people.csv")
      File.write(path, "身長,体重,年代,派閥\n高い,60,20,きのこ\n")

      error = assert_raises(ArgumentError) { C.load_people(path) }

      assert_equal "身長 を数値として読めません: 高い", error.message
    end
  end

  def test_列が無ければ読み込めない
    Dir.mktmpdir do |dir|
      path = File.join(dir, "people.csv")
      File.write(path, "身長,体重,年代\n170,60,20\n")

      error = assert_raises(KeyError) { C.load_people(path) }

      assert_equal "列がありません: 派閥", error.message
    end
  end
end
