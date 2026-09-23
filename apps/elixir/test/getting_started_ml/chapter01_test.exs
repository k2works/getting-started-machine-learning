defmodule GettingStartedMl.Chapter01Test do
  use ExUnit.Case, async: true

  alias GettingStartedMl.Chapter01, as: C

  defp features(height, weight, age_group) do
    %{身長: height, 体重: weight, 年代: age_group}
  end

  defp with_csv(contents, fun) do
    path = Path.join(System.tmp_dir!(), "people-#{System.unique_integer([:positive])}.csv")
    File.write!(path, contents)

    try do
      fun.(path)
    after
      File.rm(path)
    end
  end

  describe "ルールによる判定" do
    test "二十代はきのこ派と判定する" do
      assert C.predict_by_rule(features(170, 60, 20)) == C.kinoko()
    end

    test "二十代以外はたけのこ派と判定する" do
      for age_group <- [10, 30, 40, 50] do
        assert C.predict_by_rule(features(170, 60, age_group)) == C.takenoko()
      end
    end
  end

  describe "正解率" do
    test "全部当たれば正解率は一になる" do
      assert C.accuracy([C.kinoko(), C.takenoko()], [C.kinoko(), C.takenoko()]) == 1.0
    end

    test "半分当たれば正解率は零点五になる" do
      assert C.accuracy([C.kinoko(), C.kinoko()], [C.kinoko(), C.takenoko()]) == 0.5
    end

    test "件数が違えば正解率を求められない" do
      assert_raise ArgumentError, "予測と正解ラベルの件数が違います: 1 と 2", fn ->
        C.accuracy([C.kinoko()], [C.kinoko(), C.takenoko()])
      end
    end
  end

  describe "特徴量と正解ラベルに分ける" do
    test "人物のリストを特徴量と正解ラベルに分ける" do
      people = [
        %{身長: 170, 体重: 60, 年代: 20, 派閥: C.kinoko()},
        %{身長: 160, 体重: 50, 年代: 30, 派閥: C.takenoko()}
      ]

      assert C.split_features_and_labels(people) ==
               {[features(170, 60, 20), features(160, 50, 30)], [C.kinoko(), C.takenoko()]}
    end
  end

  describe "CSV の読み込み" do
    test "BOM 付きの CSV を列名で読み込む" do
      with_csv("\uFEFF身長,体重,年代,派閥\n170,60,20,きのこ\n", fn path ->
        assert C.load_people(path) == [%{身長: 170, 体重: 60, 年代: 20, 派閥: C.kinoko()}]
      end)
    end

    test "数値でない値があれば読み込めない" do
      with_csv("身長,体重,年代,派閥\n高い,60,20,きのこ\n", fn path ->
        assert_raise ArgumentError, "身長 を数値として読めません: 高い", fn -> C.load_people(path) end
      end)
    end

    test "列が無ければ読み込めない" do
      with_csv("身長,体重,年代\n170,60,20\n", fn path ->
        assert_raise ArgumentError, "列がありません: 派閥", fn -> C.load_people(path) end
      end)
    end
  end

  @tag :data
  test "実データでルールによる判定の正解率を求める" do
    people = C.load_people(Path.join(GettingStartedMl.Dataset.dir(), "KvsT.csv"))
    {x, t} = C.split_features_and_labels(people)
    assert length(people) == 19
    assert_in_delta C.accuracy(Enum.map(x, &C.predict_by_rule/1), t), 0.7368, 0.0001
  end
end
