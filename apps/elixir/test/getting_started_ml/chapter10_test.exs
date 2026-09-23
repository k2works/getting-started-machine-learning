defmodule GettingStartedMl.Chapter10Test do
  use ExUnit.Case, async: true

  alias GettingStartedMl.Chapter03
  alias GettingStartedMl.Chapter10, as: C
  alias GettingStartedMl.Random

  @delta 1.0e-9

  defp columns, do: [:がく片長さ, :花弁幅]

  # setosa と virginica を花弁幅で分けられるようにした小さなデータ
  defp two_species do
    setosa = for w <- [0.2, 0.3, 0.1, 0.4], do: {%{がく片長さ: 5.0 + w, 花弁幅: w}, "setosa"}
    virginica = for w <- [2.0, 2.1, 2.2, 2.3], do: {%{がく片長さ: 6.0 + w, 花弁幅: w}, "virginica"}
    pairs = setosa ++ virginica

    {Enum.map(pairs, &elem(&1, 0)), Enum.map(pairs, &elem(&1, 1))}
  end

  describe "ソフトマックス関数" do
    test "合計が一になる確率にする" do
      p = C.softmax([1.0, 2.0, 3.0])

      assert_in_delta Enum.sum(p), 1.0, @delta
      assert Enum.all?(p, &(&1 > 0))
      assert p == Enum.sort(p)
    end

    test "同じスコアなら同じ確率になる" do
      assert Enum.all?(C.softmax([2.0, 2.0, 2.0]), &(abs(&1 - 1.0 / 3) < @delta))
    end

    test "大きな値でもあふれない" do
      p = C.softmax([1000.0, 1001.0])

      assert_in_delta Enum.sum(p), 1.0, @delta
      assert Enum.all?(p, &is_float/1)
    end
  end

  describe "交差エントロピー" do
    test "正解の確率が一なら零になる" do
      assert_in_delta C.cross_entropy([[1.0, 0.0], [0.0, 1.0]], [0, 1]), 0.0, 1.0e-6
    end

    test "正解の確率が小さいほど大きくなる" do
      assert C.cross_entropy([[0.9, 0.1]], [0]) < C.cross_entropy([[0.6, 0.4]], [0])
    end
  end

  describe "ロジスティック回帰" do
    test "分けられるデータを正しく予測する" do
      {x, t} = two_species()
      predict = C.logistic_trainer().(x, t, columns())

      assert predict.(x) == t
    end

    test "学習した品種は名前の順に並ぶ" do
      {x, t} = two_species()

      assert C.logistic_fit(x, t, columns()).classes == ["setosa", "virginica"]
    end

    test "繰り返すほど損失が小さくなる" do
      {x, t} = two_species()
      losses = C.logistic_fit(x, t, columns(), 1.0, 50).losses

      assert length(losses) == 50
      assert List.last(losses) < hd(losses)
    end

    test "学習率が零なら重みは変わらず損失も変わらない" do
      {x, t} = two_species()
      model = C.logistic_fit(x, t, columns(), 0.0, 3)

      assert Enum.all?(List.flatten(model.weights), &(&1 == 0.0))
      assert Enum.uniq(model.losses) == [hd(model.losses)]
    end
  end

  describe "多数決とブートストラップ標本" do
    test "サンプルごとに最も多い予測を選ぶ" do
      assert C.majority_vote([["a", "b"], ["a", "c"], ["b", "b"]]) == ["a", "b"]
    end

    test "同数なら先に現れた予測を選ぶ" do
      assert C.majority_vote([["a"], ["b"]]) == ["a"]
    end

    test "行番号を重複を許して件数と同じだけ選ぶ" do
      {sample, _state} = C.bootstrap_sample(5, Random.new(0))

      assert length(sample) == 5
      assert Enum.all?(sample, &(&1 >= 0 and &1 < 5))
    end

    test "同じシードなら同じ標本になる" do
      assert C.bootstrap_sample(10, Random.new(0)) == C.bootstrap_sample(10, Random.new(0))
    end

    test "シードが違えば別の標本になる" do
      {a, _} = C.bootstrap_sample(10, Random.new(0))
      {b, _} = C.bootstrap_sample(10, Random.new(1))

      assert a != b
    end
  end

  describe "ランダムフォレスト" do
    test "同じシードなら同じ森になる" do
      {x, t} = two_species()

      assert C.forest_fit(x, t, columns(), 5, 1, nil, 0) ==
               C.forest_fit(x, t, columns(), 5, 1, nil, 0)
    end

    test "分けられるデータを正しく予測する" do
      {x, t} = two_species()
      predict = C.forest_trainer(10, 1, nil, 0).(x, t, columns())

      assert predict.(x) == t
    end

    test "木ごとに使う特徴量を絞る" do
      {x, t} = two_species()
      forest = C.forest_fit(x, t, columns(), 5, 1, nil, 0)

      assert Enum.all?(forest.trees, &(length(&1.columns) == 1))
    end
  end

  describe "特徴量の重要度" do
    test "一つの列だけで分ける木は、その列の重要度が一になる" do
      {x, t} = two_species()
      tree = Chapter03.fit(x, t, [:花弁幅], nil)

      assert C.tree_importances(tree, x, t, [:花弁幅]) == %{花弁幅: 1.0}
    end

    test "使わなかった列の重要度は零になる" do
      {x, t} = two_species()
      tree = Chapter03.fit(x, t, columns(), 1)
      importances = C.tree_importances(tree, x, t, columns())

      assert_in_delta Enum.sum(Map.values(importances)), 1.0, @delta
      assert Enum.count(Map.values(importances), &(&1 > 0)) == 1
    end

    test "森の重要度は合計が一になり、すべての列を持つ" do
      {x, t} = two_species()
      forest = C.forest_fit(x, t, columns(), 10, 1, nil, 0)
      importances = C.forest_importances(forest, x, t, columns())

      assert Map.keys(importances) |> Enum.sort() == Enum.sort(columns())
      assert_in_delta Enum.sum(Map.values(importances)), 1.0, @delta
    end
  end

  describe "モデル共通の約束" do
    test "どの分類器も同じscoreで評価できる" do
      {x, t} = two_species()
      split = %{x_train: x, t_train: t, x_test: x, t_test: t}

      for trainer <- [
            C.tree_trainer(1),
            C.logistic_trainer(),
            C.forest_trainer(10, 1, nil, 0),
            C.scholar_logistic_trainer()
          ] do
        assert C.score(trainer, split, columns()) == %{train: 1.0, test: 1.0}
      end
    end
  end

  describe "実データ" do
    @tag :data
    test "実行するとモデルごとの正解率と特徴量の重要度を表示する" do
      # 自作の 8 個の数値は Java 版・Clojure 版と一致する（分割も乱数も同じ手順のため）
      assert ExUnit.CaptureIO.capture_io(&C.run/0) == """
             モデル\t訓練データ\tテストデータ
             決定木（深さ 2）\t0.9333\t0.9556
             ロジスティック回帰\t0.9143\t0.9111
             ランダムフォレスト（100 本）\t1.0000\t0.9333
             ランダムフォレスト（100 本・深さ 2）\t0.9429\t0.9556
             Scholar ロジスティック回帰\t0.6571\t0.6444

             ランダムフォレスト（100 本）の特徴量の重要度:
             がく片長さ\t0.1882
             がく片幅\t0.1265
             花弁長さ\t0.2713
             花弁幅\t0.4140
             """
    end

    @tag :data
    test "Scholarの正則化を外すと自作のロジスティック回帰と同じ正解率になる" do
      split =
        GettingStartedMl.Chapter02.prepare_iris(
          Path.join(GettingStartedMl.Dataset.dir(), "iris.csv"),
          0.3,
          0
        )

      columns = Chapter03.feature_columns()

      # Scholar の alpha（L2 正則化の強さ）の既定は 1.0。0.0 にすると、
      # 自作のバッチ勾配降下法を 1000 回繰り返したときと同じ正解率になる
      assert C.score(C.scholar_logistic_trainer(alpha: 0.0), split, columns) ==
               C.score(C.logistic_trainer(1.0, 1000), split, columns)
    end
  end
end
