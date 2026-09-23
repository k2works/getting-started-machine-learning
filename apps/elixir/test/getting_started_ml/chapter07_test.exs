defmodule GettingStartedMl.Chapter07Test do
  use ExUnit.Case, async: true

  alias GettingStartedMl.Chapter07, as: C
  alias GettingStartedMl.Dataset

  defp columns, do: [:SNS1, :actor]

  defp outlier_table do
    %{
      columns: [:SNS2, :sales],
      rows: [
        # 両方満たす → 外れ値
        %{SNS2: "1200", sales: "8000"},
        # SNS2 だけ → 残す
        %{SNS2: "1200", sales: "9000"},
        # sales だけ → 残す
        %{SNS2: "500", sales: "8000"},
        # どちらも満たさない → 残す
        %{SNS2: "500", sales: "9000"}
      ]
    }
  end

  describe "評価指標" do
    test "MAE は誤差の絶対値の平均になる" do
      assert_in_delta C.mean_absolute_error([3.0, 1.0, 4.0], [2.0, 2.0, 5.0]), 1.0, 1.0e-12
    end

    test "RMSE は誤差の二乗の平均の平方根になる" do
      # 誤差 3 と 4 → √((9 + 16) / 2) = √12.5
      assert_in_delta C.root_mean_squared_error([0.0, 0.0], [3.0, -4.0]),
                      :math.sqrt(12.5),
                      1.0e-12
    end

    test "R2 は予測がすべて正解なら一になる" do
      assert_in_delta C.r2_score([1.0, 2.0, 3.0], [1.0, 2.0, 3.0]), 1.0, 1.0e-12
    end

    test "R2 は平均値を予測し続けるモデルなら零になる" do
      assert_in_delta C.r2_score([1.0, 2.0, 3.0], [2.0, 2.0, 2.0]), 0.0, 1.0e-12
    end

    test "実測値と予測値の件数が違えば失敗する" do
      assert_raise ArgumentError, fn -> C.mean_absolute_error([1.0], [1.0, 2.0]) end
    end
  end

  describe "モデル" do
    test "列名と係数の数が違えば作れない" do
      assert_raise ArgumentError, fn -> C.model(1.0, columns(), [2.0]) end
    end

    test "無い列の係数は読めない" do
      assert_raise ArgumentError, fn ->
        C.coefficient(C.model(1.0, columns(), [2.0, 3.0]), :none)
      end
    end

    test "列の並び順が違っても列名で係数を対応させる" do
      model = C.model(1.0, columns(), [2.0, 3.0])

      assert C.predict_one(model, %{SNS1: 2.0, actor: 3.0}) ==
               C.predict_one(model, %{actor: 3.0, SNS1: 2.0})
    end

    test "予測値は切片と係数の重み付きの和になる" do
      model = C.model(1.0, columns(), [2.0, 3.0])
      assert_in_delta C.predict_one(model, %{SNS1: 2.0, actor: 3.0}), 14.0, 1.0e-12
      assert C.predict(model, [%{SNS1: 0.0, actor: 0.0}]) == [1.0]
    end
  end

  describe "計画行列" do
    test "先頭には一の列が入る" do
      assert C.design_matrix([%{SNS1: 1.0, actor: 2.0}, %{SNS1: 3.0, actor: 4.0}], columns()) ==
               [[1.0, 1.0, 2.0], [1.0, 3.0, 4.0]]
    end
  end

  describe "正規方程式で学習する" do
    test "直線上の点から切片と係数を求める" do
      model = C.fit([%{x: 0.0}, %{x: 1.0}, %{x: 2.0}], [3.0, 5.0, 7.0], [:x])
      assert_in_delta model.intercept, 3.0, 1.0e-9
      assert_in_delta C.coefficient(model, :x), 2.0, 1.0e-9
    end

    test "複数の特徴量から切片と係数を求める" do
      x = [%{a: 0.0, b: 0.0}, %{a: 1.0, b: 0.0}, %{a: 0.0, b: 1.0}, %{a: 1.0, b: 1.0}]
      model = C.fit(x, [1.0, 3.0, -2.0, 0.0], [:a, :b])
      assert_in_delta model.intercept, 1.0, 1.0e-9
      assert_in_delta C.coefficient(model, :a), 2.0, 1.0e-9
      assert_in_delta C.coefficient(model, :b), -3.0, 1.0e-9
    end

    test "訓練データが空なら失敗する" do
      assert_raise ArgumentError, fn -> C.fit([], [], [:x]) end
    end

    test "特徴量と実測値の件数が違えば失敗する" do
      assert_raise ArgumentError, fn -> C.fit([%{x: 1.0}], [1.0, 2.0], [:x]) end
    end
  end

  describe "テンソルの型" do
    test "既定のテンソルは単精度になる" do
      assert Nx.type(Nx.tensor([1.0, 2.0])) == {:f, 32}
      assert Nx.type(Nx.tensor([1.0, 2.0], type: :f64)) == {:f, 64}
    end

    test "f32 で解くと係数が一の手前でずれる" do
      x = [%{a: 1.0, b: 1.0}, %{a: 2.0, b: 1.0}, %{a: 3.0, b: 2.0}, %{a: 4.0, b: 3.0}]
      t = [2.0, 3.0, 5.0, 7.0]

      assert_in_delta C.coefficient(C.fit(x, t, [:a, :b], :f64), :a), 1.0, 1.0e-12
      refute_in_delta C.coefficient(C.fit(x, t, [:a, :b], :f32), :a), 1.0, 1.0e-12
      assert_in_delta C.coefficient(C.fit(x, t, [:a, :b], :f32), :a), 1.0, 1.0e-5
    end
  end

  describe "Scholar と突き合わせる" do
    test "条件のよいデータなら Scholar の線形回帰と一致する" do
      x = [%{a: 0.0, b: 0.0}, %{a: 1.0, b: 0.0}, %{a: 0.0, b: 1.0}, %{a: 1.0, b: 1.0}]
      t = [1.0, 3.0, -2.0, 0.0]
      mine = C.fit(x, t, [:a, :b])
      theirs = C.scholar_fit(x, t, [:a, :b])

      assert_in_delta theirs.intercept, mine.intercept, 1.0e-9
      assert_in_delta C.coefficient(theirs, :a), C.coefficient(mine, :a), 1.0e-9
      assert_in_delta C.coefficient(theirs, :b), C.coefficient(mine, :b), 1.0e-9
    end

    test "Scholar のモデルでも自作の予測と同じ値になる" do
      x = [%{a: 0.0, b: 0.0}, %{a: 1.0, b: 0.0}, %{a: 0.0, b: 1.0}, %{a: 1.0, b: 1.0}]
      t = [1.0, 3.0, -2.0, 0.0]
      theirs = C.scholar_fit(x, t, [:a, :b])

      assert Enum.zip(C.predict(theirs, x), t)
             |> Enum.all?(fn {y, expected} -> abs(y - expected) < 1.0e-9 end)
    end
  end

  describe "外れ値" do
    test "SNS2 が千を超え売上が八千五百未満の行を取り除く" do
      assert length(C.remove_outliers(outlier_table()).rows) == 3
    end

    test "条件の片方だけを満たす行は残す" do
      rows = C.remove_outliers(outlier_table()).rows

      assert Enum.map(rows, &{&1[:SNS2], &1[:sales]}) ==
               [{"1200", "9000"}, {"500", "8000"}, {"500", "9000"}]
    end

    test "列はそのまま残る" do
      assert C.remove_outliers(outlier_table()).columns == [:SNS2, :sales]
    end
  end

  describe "実データ" do
    @tag :data
    test "実行するとほかの言語版と同じ係数と評価指標を表示する" do
      output = ExUnit.CaptureIO.capture_io(&C.run/0)

      assert String.split(output, "\n", trim: true) == [
               "データ件数: 100",
               "外れ値を除いた件数: 99",
               "訓練データ: 79 件, テストデータ: 20 件",
               "切片: 6114.60",
               "係数: SNS1=1.3804, SNS2=0.5218, actor=0.2900, original=208.8827",
               "f32 で解いた切片: 6114.60 (6114.59716796875)",
               "Scholar の切片: 5752.43, 係数: SNS1=1.4021, SNS2=0.5112, actor=0.3088, original=579.3172",
               "テストデータの評価: R2=0.6184, MAE=302.20, RMSE=376.14"
             ]
    end

    @tag :data
    test "Scholar の残差平方和は自作の正規方程式より大きい" do
      split = C.prepare_cinema(Path.join(Dataset.dir(), "cinema.csv"), 0.2, 0)
      columns = C.feature_columns()
      mine = C.fit(split.x_train, split.t_train, columns)
      theirs = C.scholar_fit(split.x_train, split.t_train, columns)

      assert sum_of_squares(split.t_train, C.predict(mine, split.x_train)) <
               sum_of_squares(split.t_train, C.predict(theirs, split.x_train))
    end

    @tag :data
    test "列をそろえてから渡せば Scholar の係数は自作の係数に近づく" do
      split = C.prepare_cinema(Path.join(Dataset.dir(), "cinema.csv"), 0.2, 0)
      columns = C.feature_columns()
      mine = C.fit(split.x_train, split.t_train, columns)
      theirs = C.scholar_fit_standardized(split.x_train, split.t_train, columns)

      for column <- columns do
        assert_in_delta C.coefficient(theirs, column), C.coefficient(mine, column), 0.05
      end
    end
  end

  defp sum_of_squares(t, y) do
    Enum.zip(t, y) |> Enum.map(fn {a, b} -> (a - b) * (a - b) end) |> Enum.sum()
  end
end
