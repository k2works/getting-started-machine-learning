# frozen_string_literal: true

require "test_helper"

# 第 8 章のパイプラインと、学習済みモデルの保存のテスト。
class Chapter08PipelineTest < Minitest::Test
  C = GettingStartedMl::Chapter08
  Row = GettingStartedMl::Chapter02::Row
  Table = GettingStartedMl::Chapter02::Table

  # 架空の乗客のデータ。運賃が高い女性は生存、安い男性は死亡。値は特徴量の列の順、末尾は生死。
  def passengers
    [["1", "female", "30", "0", "0", "80", "C", "1"],
     ["1", "female", "", "0", "0", "90", "S", "1"],
     ["3", "male", "20", "0", "0", "8", "", "0"],
     ["3", "male", "40", "0", "0", "7", "S", "0"]]
      .map { |values| Row.new([*C::Survived::FEATURES, C::Survived::TARGET].zip(values).to_h) }
  end

  def fitted
    rows = passengers
    C::Pipeline.build(max_depth: 2, class_weight: :none)
               .fit(C::Survived.features(rows), C::Survived.target(rows))
  end

  def test_行の正解ラベルを整数にする
    assert_equal [1, 1, 0, 0], C::Survived.target(passengers)
  end

  def test_正解ラベルが数値でなければ失敗する
    error = assert_raises(ArgumentError) { C::Survived.target([Row.new({ "Survived" => "生存" })]) }

    assert_equal "Survived を数値として読めません: 生存", error.message
  end

  def test_値の数が特徴量の列と違えば乗客の行を作れない
    error = assert_raises(ArgumentError) { C::Survived.passenger(["1"]) }

    assert_equal "件数が違います: 7 と 1", error.message
  end

  def test_パイプラインは前処理をしてから学習する
    assert_equal [1, 1, 0, 0], fitted.predict(C::Survived.features(passengers))
  end

  def test_パイプラインは欠損値もダミー変数も残さない
    prepared = fitted.transform(C::Survived.features(passengers))

    refute_includes prepared.columns, "Sex"
    assert_includes prepared.columns, "Sex_male"
    assert(prepared.rows.none? { |row| row.missing?("Age") })
  end

  def test_学習した前処理は新しいデータにも同じ変換をする
    new_passenger = C::Survived.passenger(["1", "female", "", "0", "0", "85", "C"])

    assert_equal [1], fitted.predict(C::Survived.features([new_passenger]))
  end

  def test_同じパイプラインで学び直しても前の学習済みモデルは変わらない
    rows = passengers
    pipeline = C::Pipeline.build(max_depth: 2, class_weight: :none)
    first = pipeline.fit(C::Survived.features(rows), C::Survived.target(rows))
    tree = first.model.tree
    pipeline.fit(C::Survived.features(rows.first(2)), [1, 1])

    assert_equal tree, first.model.tree
  end

  def test_欠損値が残った表は特徴量にできない
    x = Table.new(columns: ["Age"], rows: [Row.new({ "Age" => "" })])
    error = assert_raises(ArgumentError) { C.to_features(x) }

    assert_equal "値が空欄です: Age", error.message
  end

  def test_保存して読み込むと同じパイプラインになる
    Dir.mktmpdir do |dir|
      model_file = File.join(dir, "model", "survived.dump")
      C::ModelFile.save(fitted, model_file)

      assert_equal fitted, C::ModelFile.load(model_file)
    end
  end

  def test_パイプラインでないものは読み込めない
    Dir.mktmpdir do |dir|
      model_file = File.join(dir, "survived.dump")
      File.binwrite(model_file, Marshal.dump([1, 2, 3]))
      error = assert_raises(TypeError) { C::ModelFile.load(model_file) }

      assert_equal "学習済みのパイプラインではありません: Array", error.message
    end
  end
end
