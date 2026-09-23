defmodule GettingStartedMl.Chapter02Test do
  use ExUnit.Case, async: true

  alias GettingStartedMl.Chapter02, as: C
  alias GettingStartedMl.Random

  defp table do
    {columns, rows} =
      GettingStartedMl.Csv.parse_table(
        "がく片長さ,がく片幅,花弁長さ,花弁幅,種類\n5.1,3.5,1.4,0.2,setosa\n,3.0,1.4,0.2,setosa\n7.0,3.2,4.7,1.4,versicolor\n"
      )

    %{columns: columns, rows: rows}
  end

  describe "自作の乱数" do
    test "シード 0 の並びが Java 版・Scala 版・Clojure 版と一致する" do
      assert Random.shuffle(Enum.to_list(0..9), 0) == [4, 8, 9, 6, 3, 5, 2, 1, 7, 0]
    end

    test "同じシードなら何度でも同じ並びになる" do
      assert Random.shuffle(Enum.to_list(1..20), 42) == Random.shuffle(Enum.to_list(1..20), 42)
    end

    test "シードが違えば並びが変わる" do
      refute Random.shuffle(Enum.to_list(0..9), 0) == Random.shuffle(Enum.to_list(0..9), 1)
    end

    test "元の要素は増えも減りもしない" do
      assert Enum.sort(Random.shuffle(Enum.to_list(0..9), 7)) == Enum.to_list(0..9)
    end

    test "空のリストと一要素のリストはそのまま返る" do
      assert Random.shuffle([], 0) == []
      assert Random.shuffle([:only], 0) == [:only]
    end

    test "二の冪の範囲でも偏りのない値を返す" do
      {value, _} = Random.next_int(Random.new(0), 8)
      assert value in 0..7
    end
  end

  describe "列を読む" do
    test "文字列の列を読む" do
      assert C.text(hd(table().rows), :種類) == "setosa"
    end

    test "列が無ければ読めない" do
      assert_raise ArgumentError, "列がありません: 産地", fn -> C.text(hd(table().rows), :産地) end
    end

    test "数値の列を読む" do
      assert C.number(hd(table().rows), :がく片長さ) == 5.1
    end

    test "空欄は nil になる" do
      assert C.number(Enum.at(table().rows, 1), :がく片長さ) == nil
    end

    test "数値として読めなければ失敗する" do
      row = %{がく片長さ: "たかい"}

      assert_raise ArgumentError, "がく片長さ を数値として読めません: たかい", fn ->
        C.number(row, :がく片長さ)
      end
    end

    test "空欄かどうかを判定する" do
      assert C.missing?(Enum.at(table().rows, 1), :がく片長さ)
      refute C.missing?(hd(table().rows), :がく片長さ)
    end
  end

  describe "表" do
    test "列の順は CSV の順のまま" do
      assert table().columns == [:がく片長さ, :がく片幅, :花弁長さ, :花弁幅, :種類]
    end

    test "列ごとに欠損値を数える" do
      assert C.count_missing(table()) == [
               {:がく片長さ, 1},
               {:がく片幅, 0},
               {:花弁長さ, 0},
               {:花弁幅, 0},
               {:種類, 0}
             ]
    end

    test "正解ラベルの列を取り出す" do
      result = C.split_features_and_target(table(), :種類)
      assert result.columns == [:がく片長さ, :がく片幅, :花弁長さ, :花弁幅]
      assert result.labels == ["setosa", "setosa", "versicolor"]
    end
  end

  describe "欠損値の補完" do
    test "欠損値を除いて平均を求める" do
      means = C.column_means(table().rows, [:がく片長さ])
      assert_in_delta means.がく片長さ, 6.05, 1.0e-12
    end

    test "値がすべて空欄なら平均を求められない" do
      rows = [%{がく片長さ: ""}, %{がく片長さ: " "}]

      assert_raise ArgumentError, "値がすべて空欄です: がく片長さ", fn ->
        C.column_means(rows, [:がく片長さ])
      end
    end

    test "指定した値で補完する" do
      filled = C.fill_missing(table().rows, [:がく片長さ], %{がく片長さ: 6.05})
      assert Enum.map(filled, & &1.がく片長さ) == [5.1, 6.05, 7.0]
    end

    test "補完する値が無ければ失敗する" do
      assert_raise ArgumentError, "補完する値がありません: がく片長さ", fn ->
        C.fill_missing(table().rows, [:がく片長さ], %{})
      end
    end
  end

  describe "訓練データとテストデータに分ける" do
    test "テストデータの割合は切り上げる" do
      x = Enum.map(1..10, &%{値: &1})
      t = Enum.map(1..10, &"ラベル#{&1}")
      split = C.split_train_test(x, t, 0.3, 0)

      assert length(split.x_train) == 7
      assert length(split.x_test) == 3
      assert length(split.t_train) == 7
      assert length(split.t_test) == 3
    end

    test "特徴量とラベルの対応が崩れない" do
      x = Enum.map(1..10, &%{値: &1})
      t = Enum.map(1..10, &"ラベル#{&1}")
      split = C.split_train_test(x, t, 0.3, 0)

      for {features, label} <- Enum.zip(split.x_train, split.t_train) do
        assert label == "ラベル#{features.値}"
      end
    end

    test "件数が違えば分けられない" do
      assert_raise ArgumentError, "件数が違います: 2 と 1", fn ->
        C.split_train_test([%{}, %{}], ["a"], 0.3, 0)
      end
    end
  end

  describe "実データ" do
    @tag :data
    test "アヤメのデータを分割して補完する" do
      path = Path.join(GettingStartedMl.Dataset.dir(), "iris.csv")
      table = C.load_table(path)
      split = C.prepare_iris(path, 0.3, 0)

      assert length(table.rows) == 150
      assert length(split.x_train) == 105
      assert length(split.x_test) == 45

      # 補完した後の訓練データの平均値は Java 版・Scala 版・Clojure 版と一致する
      values = Enum.map(split.x_train, & &1.がく片長さ)
      assert_in_delta Enum.sum(values) / length(values), 0.4215384615384616, 1.0e-15
    end
  end
end
