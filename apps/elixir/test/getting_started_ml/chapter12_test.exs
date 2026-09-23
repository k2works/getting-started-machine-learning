defmodule GettingStartedMl.Chapter12Test do
  use ExUnit.Case, async: true

  alias GettingStartedMl.Chapter07
  alias GettingStartedMl.Chapter12, as: C
  alias GettingStartedMl.Dataset
  alias Scholar.Linear.RidgeRegression

  # 2 列の小さな人工データ。t = 2 * x1 + 1 * x2 + 1 にちょうど乗る。
  defp x, do: [[1.0, 2.0], [2.0, 1.0], [3.0, 5.0], [4.0, 3.0], [5.0, 7.0]]
  defp t, do: [5.0, 6.0, 12.0, 12.0, 18.0]

  defp boston do
    Dataset.dir()
    |> Path.join("Boston.csv")
    |> C.prepare_boston(0.3, 0.3, 0)
  end

  describe "リッジ回帰" do
    test "alpha が零なら最小二乗法と同じ係数と切片になる" do
      fitted = C.ridge_fit(x(), t(), 0.0)

      assert_in_delta Enum.at(fitted.coefficients, 0), 2.0, 1.0e-9
      assert_in_delta Enum.at(fitted.coefficients, 1), 1.0, 1.0e-9
      assert_in_delta fitted.intercept, 1.0, 1.0e-9
    end

    test "alpha が零なら第 7 章の線形回帰と同じ係数になる" do
      columns = [:a, :b]
      rows = Enum.map(x(), fn [a, b] -> %{a: a, b: b} end)
      seventh = Chapter07.fit(rows, t(), columns)
      fitted = C.ridge_fit(x(), t(), 0.0)

      assert_in_delta fitted.intercept, seventh.intercept, 1.0e-9

      for {column, coefficient} <- Enum.zip(columns, fitted.coefficients) do
        assert_in_delta coefficient, Chapter07.coefficient(seventh, column), 1.0e-9
      end
    end

    test "alpha を大きくすると係数が小さくなる" do
      sums =
        Enum.map([0.0, 1.0, 10.0, 100.0], fn alpha ->
          C.coefficient_abs_sum(C.ridge_fit(x(), t(), alpha))
        end)

      assert sums == Enum.sort(sums, :desc)
    end

    test "切片には罰則がかからない" do
      # 正解に 100 を足すと、切片だけが 100 増えて係数は変わらない
      shifted = C.ridge_fit(x(), Enum.map(t(), &(&1 + 100.0)), 10.0)
      base = C.ridge_fit(x(), t(), 10.0)

      assert_in_delta shifted.intercept, base.intercept + 100.0, 1.0e-9

      for {a, b} <- Enum.zip(shifted.coefficients, base.coefficients) do
        assert_in_delta a, b, 1.0e-9
      end
    end

    test "件数が違えば失敗する" do
      assert_raise ArgumentError, fn -> C.ridge_fit(x(), [1.0], 0.0) end
    end
  end

  describe "予測" do
    test "係数と切片から予測する" do
      fitted = C.model([2.0, 1.0], 1.0)

      assert C.predict(fitted, [[1.0, 2.0], [0.0, 0.0]]) == [5.0, 1.0]
    end

    test "列数が係数の数と違えば失敗する" do
      assert_raise ArgumentError, fn -> C.predict(C.model([2.0, 1.0], 1.0), [[1.0]]) end
    end
  end

  describe "ラッソ回帰" do
    test "alpha が零に近ければ最小二乗法とほぼ同じになる" do
      fitted = C.lasso_fit(x(), t(), 1.0e-9)

      assert_in_delta Enum.at(fitted.coefficients, 0), 2.0, 1.0e-6
      assert_in_delta Enum.at(fitted.coefficients, 1), 1.0, 1.0e-6
      assert_in_delta fitted.intercept, 1.0, 1.0e-6
    end

    test "alpha を大きくすると係数がちょうど零になる" do
      fitted = C.lasso_fit(x(), t(), 50.0)

      assert fitted.coefficients == [0.0, 0.0]
      # 係数がすべて 0 なら、予測は正解の平均値になる
      assert_in_delta fitted.intercept, Enum.sum(t()) / length(t()), 1.0e-9
    end

    test "軟しきい値作用素はしきい値の分だけ零に寄せる" do
      assert C.soft_threshold(3.0, 1.0) == 2.0
      assert C.soft_threshold(-3.0, 1.0) == -2.0
      assert C.soft_threshold(0.5, 1.0) == 0.0
      assert C.soft_threshold(-0.5, 1.0) == 0.0
    end

    test "リッジ回帰と違って係数がちょうど零になる" do
      lasso = C.lasso_fit(x(), t(), 20.0)
      ridge = C.ridge_fit(x(), t(), 20.0)

      assert Enum.any?(lasso.coefficients, &(&1 == 0.0))
      refute Enum.any?(ridge.coefficients, &(&1 == 0.0))
    end

    test "件数が違えば失敗する" do
      assert_raise ArgumentError, fn -> C.lasso_fit(x(), [1.0], 1.0) end
    end
  end

  describe "モデル選択" do
    setup do
      data = %{
        x_train: x(),
        t_train: t(),
        x_valid: [[1.5, 2.5], [3.5, 4.5]],
        t_valid: [6.5, 12.5]
      }

      %{data: data, experiments: C.run_ridge_experiments(data, C.alphas())}
    end

    test "alpha ごとに訓練と検証の決定係数と係数の合計を記録する", %{experiments: experiments} do
      assert Enum.map(experiments, & &1.alpha) == C.alphas()

      assert Enum.all?(experiments, fn experiment ->
               Map.has_key?(experiment, :train_score) and
                 Map.has_key?(experiment, :validation_score) and
                 Map.has_key?(experiment, :coefficient_abs_sum)
             end)
    end

    test "検証データの決定係数が最も高い実験を選ぶ", %{experiments: experiments} do
      best = C.best_experiment(experiments)

      assert best.validation_score == Enum.max(Enum.map(experiments, & &1.validation_score))
    end

    test "同じ値なら先の実験を選ぶ" do
      experiments = [
        %{alpha: 1.0, validation_score: 0.5},
        %{alpha: 2.0, validation_score: 0.5}
      ]

      assert C.best_experiment(experiments).alpha == 1.0
    end

    test "実験が一件も無ければ失敗する" do
      assert_raise ArgumentError, fn -> C.best_experiment([]) end
    end
  end

  describe "零になった係数の特徴量名" do
    test "係数がちょうど零の特徴量だけを列の順に返す" do
      assert C.zero_coefficient_names([0.0, 1.0, 0.0], ["a", "b", "c"]) == ["a", "c"]
    end

    test "零が無ければ空になる" do
      assert C.zero_coefficient_names([1.0, 2.0], ["a", "b"]) == []
    end

    test "係数と特徴量名の数が違えば失敗する" do
      assert_raise ArgumentError, fn -> C.zero_coefficient_names([0.0], ["a", "b"]) end
    end
  end

  describe "標準化して二次の項を作る" do
    setup do
      rows = [%{a: 1.0, b: 10.0}, %{a: 3.0, b: 20.0}, %{a: 5.0, b: 30.0}]

      %{rows: rows, scaler: C.scaler_fit(rows, [:a, :b])}
    end

    test "列名は元の列、二乗、積の順になる", %{scaler: scaler} do
      assert C.feature_names(scaler) == ["a", "b", "a^2", "a b", "b^2"]
    end

    test "標準化すると平均が零で標準偏差が一になる", %{rows: rows, scaler: scaler} do
      z = Enum.map(C.scaler_transform(scaler, rows), &Enum.take(&1, 2))

      assert_in_delta Enum.sum(Enum.map(z, &Enum.at(&1, 0))), 0.0, 1.0e-12
      assert_in_delta Enum.at(Enum.at(z, 2), 0), :math.sqrt(1.5), 1.0e-12
    end

    test "二次の項は標準化した値の積になる", %{rows: rows, scaler: scaler} do
      [_, _, [a, b, aa, ab, bb]] = C.scaler_transform(scaler, rows)

      assert_in_delta aa, a * a, 1.0e-12
      assert_in_delta ab, a * b, 1.0e-12
      assert_in_delta bb, b * b, 1.0e-12
    end

    test "検証データにも訓練データの平均と標準偏差を使う", %{rows: rows, scaler: scaler} do
      # 訓練データの平均（a=3.0）をそのまま引くので、a=3.0 の行は 0 になる
      [[a | _]] = C.scaler_transform(scaler, [%{a: 3.0, b: 20.0}])
      assert_in_delta a, 0.0, 1.0e-12
      assert length(rows) == 3
    end
  end

  describe "外れ値を除く" do
    test "z スコアの絶対値がしきい値を超える行を除く" do
      rows = Enum.map([1, 2, 3, 4, 100], fn value -> %{v: to_string(value)} end)
      table = %{columns: [:v], rows: rows}

      assert length(C.remove_outliers(table, [:v], 1.5).rows) == 4
    end

    test "しきい値を超えなければ何も除かない" do
      rows = Enum.map([1, 2, 3, 4, 5], fn value -> %{v: to_string(value)} end)
      table = %{columns: [:v], rows: rows}

      assert length(C.remove_outliers(table, [:v], C.outlier_threshold()).rows) == 5
    end

    test "空欄があれば失敗する" do
      table = %{columns: [:v], rows: [%{v: "1"}, %{v: ""}]}

      assert_raise ArgumentError, fn -> C.remove_outliers(table, [:v], 3.0) end
    end
  end

  describe "Scholar のリッジ回帰と突き合わせる" do
    test "同じ alpha なら係数と切片が一致する" do
      for alpha <- [0.1, 1.0, 10.0, 100.0] do
        mine = C.ridge_fit(x(), t(), alpha)
        theirs = C.scholar_ridge_fit(x(), t(), alpha)

        assert_in_delta mine.intercept, theirs.intercept, 1.0e-8

        for {a, b} <- Enum.zip(mine.coefficients, theirs.coefficients) do
          assert_in_delta a, b, 1.0e-8
        end
      end
    end

    test "Scholar のリッジ回帰も f64 のまま返る" do
      fitted =
        RidgeRegression.fit(
          Nx.tensor(x(), type: :f64),
          Nx.tensor(t(), type: :f64),
          alpha: 1.0
        )

      assert Nx.type(fitted.coefficients) == {:f, 64}
    end
  end

  describe "実データ" do
    @tag :data
    test "実行すると正則化の結果を表示する" do
      output = ExUnit.CaptureIO.capture_io(&C.run/0)

      assert String.split(output, "\n", trim: true) == [
               "データ件数: 98（外れ値 2 件を除外）",
               "訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件",
               "特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2",
               "alpha  訓練 R²  検証 R²  係数の絶対値の合計",
               "  0.0  0.8827  0.7272  14.187",
               "  0.1  0.8827  0.7274  14.104",
               "  1.0  0.8823  0.7288  13.594",
               " 10.0  0.8681  0.7349  11.573",
               "100.0  0.6583  0.5985  5.684",
               "検証データで選んだ alpha: 10.0",
               "テストデータの決定係数: 線形回帰 0.5224, リッジ回帰 0.6243",
               "Scholar のリッジ回帰との係数の最大の差: 5.1e-6",
               "ラッソ回帰（alpha=23.5）で係数が 0 になった特徴量: PTRATIO^2, PTRATIO LSTAT, LSTAT^2"
             ]
    end

    @tag :data
    test "実データでも自作のリッジ回帰は Scholar と一致する" do
      data = boston()
      mine = C.ridge_fit(data.x_train, data.t_train, 10.0)
      theirs = C.scholar_ridge_fit(data.x_train, data.t_train, 10.0)

      # 9 列の多項式特徴量は桁が近く条件数が大きいので、閉形式（自作）と
      # 既定の特異値分解（Scholar）の差は f64 でも 1e-5 の手前までしか詰まらない
      assert_in_delta mine.intercept, theirs.intercept, 1.0e-5

      for {a, b} <- Enum.zip(mine.coefficients, theirs.coefficients) do
        assert_in_delta a, b, 1.0e-5
      end
    end

    @tag :data
    test "solver を cholesky にすると実データでも一ビットも違わない" do
      data = boston()
      mine = C.ridge_fit(data.x_train, data.t_train, 10.0)
      theirs = C.scholar_ridge_fit(data.x_train, data.t_train, 10.0, :cholesky)

      assert mine.coefficients == theirs.coefficients
      assert mine.intercept == theirs.intercept
    end

    @tag :data
    test "検証データで選んだリッジ回帰はテストデータで線形回帰を上回る" do
      data = boston()
      best = C.best_experiment(C.run_ridge_experiments(data, C.alphas()))

      score = fn alpha ->
        Chapter07.r2_score(
          data.t_test,
          C.predict(C.ridge_fit(data.x_train, data.t_train, alpha), data.x_test)
        )
      end

      assert score.(best.alpha) > score.(0.0)
    end
  end
end
