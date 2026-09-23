defmodule GettingStartedMl.Chapter11Test do
  use ExUnit.Case, async: true

  alias GettingStartedMl.Chapter02
  alias GettingStartedMl.Chapter07
  alias GettingStartedMl.Chapter11, as: C
  alias GettingStartedMl.Dataset
  alias Scholar.Metrics.Classification

  defp columns, do: [:x]

  defp features(values), do: Enum.map(values, fn value -> %{x: value * 1.0} end)

  defp survived_data do
    Dataset.dir()
    |> Path.join("Survived.csv")
    |> Chapter02.load_table()
    |> C.prepare_survived()
  end

  describe "混同行列" do
    test "正解と予測を四つに数える" do
      assert C.confusion_matrix(
               ["1", "1", "1", "0", "0", "0"],
               ["1", "1", "0", "1", "0", "0"],
               "1"
             ) == %{tp: 2, fp: 1, fn: 1, tn: 2}
    end

    test "正例の決め方を変えると数え方も変わる" do
      actual = ["1", "1", "1", "0", "0", "0"]
      predicted = ["1", "0", "0", "1", "0", "0"]

      assert C.confusion_matrix(actual, predicted, "1") == %{tp: 1, fp: 1, fn: 2, tn: 2}
      assert C.confusion_matrix(actual, predicted, "0") == %{tp: 2, fp: 2, fn: 1, tn: 1}
    end

    test "三値以上でも正例以外はまとめて負例になる" do
      assert C.confusion_matrix(["a", "a", "b", "c"], ["a", "b", "a", "c"], "a") ==
               %{tp: 1, fp: 1, fn: 1, tn: 1}
    end

    test "件数が違えば失敗する" do
      assert_raise ArgumentError, fn -> C.confusion_matrix(["1"], ["1", "0"], "1") end
    end
  end

  describe "適合率と再現率とF値" do
    setup do
      %{
        cm:
          C.confusion_matrix(
            ["1", "1", "1", "0", "0", "0"],
            ["1", "1", "0", "1", "0", "0"],
            "1"
          )
      }
    end

    test "正例と予測したうち本当に正例だった割合", %{cm: cm} do
      assert_in_delta C.precision(cm), 2 / 3, 1.0e-12
    end

    test "本当の正例のうち正例と予測できた割合", %{cm: cm} do
      assert_in_delta C.recall(cm), 2 / 3, 1.0e-12
    end

    test "適合率と再現率の調和平均", %{cm: cm} do
      assert_in_delta C.f1_score(cm), 2 / 3, 1.0e-12
    end

    test "適合率と再現率が違えば F 値はその間に入る" do
      cm = %{tp: 1, fp: 0, fn: 1, tn: 2}

      assert_in_delta C.precision(cm), 1.0, 1.0e-12
      assert_in_delta C.recall(cm), 0.5, 1.0e-12
      assert_in_delta C.f1_score(cm), 2 / 3, 1.0e-12
    end

    test "正例を一件も予測しなければ零になり NaN にならない" do
      cm = %{tp: 0, fp: 0, fn: 2, tn: 2}

      assert [C.precision(cm), C.recall(cm), C.f1_score(cm)] == [0.0, 0.0, 0.0]
    end
  end

  describe "正解率と平均二乗誤差" do
    test "正解率は一致した割合になる" do
      assert_in_delta C.accuracy(["a", "b", "c", "d"], ["a", "b", "c", "x"]), 0.75, 1.0e-12
    end

    test "正解率は件数が違えば失敗する" do
      assert_raise ArgumentError, fn -> C.accuracy(["a"], ["a", "b"]) end
    end

    test "平均二乗誤差は誤差の二乗の平均になる" do
      assert_in_delta C.mean_squared_error([1.0, 2.0, 3.0, 4.0], [1.5, 2.0, 3.0, 4.5]),
                      0.125,
                      1.0e-12
    end

    test "平均二乗誤差は第 7 章の RMSE の二乗と一致する" do
      t = [1.0, 2.0, 3.0, 4.0]
      y = [1.5, 2.0, 3.0, 4.5]
      rmse = Chapter07.root_mean_squared_error(t, y)

      assert_in_delta C.mean_squared_error(t, y), rmse * rmse, 1.0e-12
    end

    test "平均二乗誤差は外れた予測に敏感で MAE より大きく増える" do
      t = [0.0, 0.0, 0.0, 0.0]
      spread = [1.0, 1.0, 1.0, 1.0]
      concentrated = [0.0, 0.0, 0.0, 4.0]

      # MAE はどちらも 1.0 で同じだが、MSE は外れ値のあるほうが大きい
      assert_in_delta Chapter07.mean_absolute_error(t, spread),
                      Chapter07.mean_absolute_error(t, concentrated),
                      1.0e-12

      assert C.mean_squared_error(t, spread) < C.mean_squared_error(t, concentrated)
    end

    test "平均は値が一つも無ければ失敗する" do
      assert_raise ArgumentError, fn -> C.mean([]) end
    end
  end

  describe "ROC 曲線と AUC" do
    test "先頭はどれも正例と予測しない点になる" do
      [first | _] = C.roc_curve([0.1, 0.4, 0.35, 0.8], [false, false, true, true])

      assert first == %{threshold: :infinity, false_positive_rate: 0.0, true_positive_rate: 0.0}
    end

    test "同じスコアは一つの点にまとまる" do
      curve = C.roc_curve([0.5, 0.5, 0.5, 0.5], [true, true, false, false])

      assert length(curve) == 2
      assert C.auc(curve) == 0.5
    end

    test "完全に分けられるスコアなら AUC は一になる" do
      curve = C.roc_curve([0.9, 0.8, 0.2, 0.1], [true, true, false, false])

      assert_in_delta C.auc(curve), 1.0, 1.0e-12
    end

    test "順が逆なら AUC は零になる" do
      curve = C.roc_curve([0.1, 0.2, 0.8, 0.9], [true, true, false, false])

      assert_in_delta C.auc(curve), 0.0, 1.0e-12
    end

    test "正例か負例の片方しか無ければ失敗する" do
      assert_raise ArgumentError, fn -> C.roc_curve([0.1, 0.2], [true, true]) end
    end
  end

  describe "K 分割交差検証の分け方" do
    test "テストデータは重ならず全体をおおう" do
      folds = C.k_fold(10, 5, 0)
      tests = Enum.flat_map(folds, & &1.test)

      assert Enum.sort(tests) == Enum.to_list(0..9)
      assert length(tests) == 10
    end

    test "訓練データはテストデータの残りになる" do
      for fold <- C.k_fold(10, 5, 0) do
        assert Enum.sort(fold.train ++ fold.test) == Enum.to_list(0..9)
      end
    end

    test "割り切れないときは余りを先頭の分割から一件ずつ配る" do
      assert Enum.map(C.k_fold(11, 4, 0), &length(&1.test)) == [3, 3, 3, 2]
      assert Enum.map(C.k_fold_sequential(7, 2), &length(&1.test)) == [4, 3]
    end

    test "同じシードなら同じ分け方になる" do
      assert C.k_fold(20, 4, 7) == C.k_fold(20, 4, 7)
      refute C.k_fold(20, 4, 7) == C.k_fold(20, 4, 8)
    end

    test "並べ替えなければ位置は昇順のまま入る" do
      assert Enum.map(C.k_fold_sequential(6, 3), & &1.test) == [[0, 1], [2, 3], [4, 5]]
    end

    test "分割の数が二未満か件数より多ければ失敗する" do
      assert_raise ArgumentError, fn -> C.k_fold(10, 1, 0) end
      assert_raise ArgumentError, fn -> C.k_fold(3, 4, 0) end
    end
  end

  describe "交差検証" do
    setup do
      x = features(1..20)
      t = Enum.map(1..20, fn i -> if rem(i, 3) == 0 or i > 12, do: "1", else: "0" end)

      %{x: x, t: t, folds: C.k_fold(20, 4, 0)}
    end

    test "分割ごとのスコアが分割の数だけ返る", %{x: x, t: t, folds: folds} do
      scores =
        C.tree_trainer(columns(), 1)
        |> C.cross_validate(x, t, folds, &C.accuracy/2)
        |> Enum.to_list()

      assert length(scores) == 4
      assert Enum.all?(scores, &(&1 >= 0.0 and &1 <= 1.0))
    end

    test "評価関数を差し替えられる", %{x: x, t: t, folds: folds} do
      trainer = C.tree_trainer(columns(), 1)
      metric = C.classification_metric(&C.precision/1, "1")

      assert length(Enum.to_list(C.cross_validate(trainer, x, t, folds, metric))) == 4
    end

    test "取り出すまで学習しない", %{x: x, t: t, folds: folds} do
      owner = self()

      trainer = fn train_x, train_t ->
        send(owner, :trained)
        C.tree_trainer(columns(), 1).(train_x, train_t)
      end

      scores = C.cross_validate(trainer, x, t, folds, &C.accuracy/2)
      refute_received :trained

      # ストリームから 1 つ取り出すと 1 回だけ学習する（Clojure の遅延シーケンスと違って
      # 32 件ずつまとめて実現することはない）
      assert [_first] = Enum.take(scores, 1)
      assert_received :trained
      refute_received :trained
    end

    test "回帰でも同じ関数で評価できる" do
      x = features(1..12)
      t = Enum.map(1..12, &(&1 * 2.0 + 1.0))
      folds = C.k_fold(12, 3, 0)

      scores =
        C.linear_trainer(columns())
        |> C.cross_validate(x, t, folds, &C.mean_squared_error/2)
        |> Enum.to_list()

      assert length(scores) == 3
      assert Enum.all?(scores, &(&1 < 1.0e-12))
    end
  end

  describe "Scholar の評価指標と突き合わせる" do
    setup do
      actual = ["1", "1", "1", "1", "0", "0", "0", "0", "0", "0"]
      predicted = ["1", "1", "1", "0", "1", "0", "0", "0", "0", "0"]

      %{actual: actual, predicted: predicted, cm: C.confusion_matrix(actual, predicted, "1")}
    end

    test "正解率と適合率と再現率と F 値が一致する", %{actual: a, predicted: p, cm: cm} do
      scores = C.scholar_scores(a, p, "1")

      # Scholar の分類の指標は f32 で返るので、小数 7 桁までしか比べられない
      assert_in_delta scores.accuracy, C.accuracy(a, p), 1.0e-7
      assert_in_delta scores.precision, C.precision(cm), 1.0e-7
      assert_in_delta scores.recall, C.recall(cm), 1.0e-7
      assert_in_delta scores.f1_score, C.f1_score(cm), 1.0e-7
    end

    test "Scholar の分類の指標は f64 を渡しても f32 に落ちる" do
      y_true = Nx.tensor([1.0, 1.0, 0.0], type: :f64)
      y_pred = Nx.tensor([1.0, 0.0, 0.0], type: :f64)

      assert Nx.type(Classification.accuracy(y_true, y_pred)) == {:f, 32}
    end

    test "混同行列は負例が先で行が正解になる", %{actual: a, predicted: p, cm: cm} do
      %{tp: tp, fp: fp, fn: misses, tn: tn} = cm

      assert C.scholar_confusion_matrix(a, p, "1") == [tn, fp, misses, tp]
    end

    test "正例を一件も予測しなければ Scholar も零にする" do
      actual = ["1", "0", "0", "0"]
      predicted = ["0", "0", "0", "0"]
      scores = C.scholar_scores(actual, predicted, "1")

      assert [scores.precision, scores.recall, scores.f1_score] == [0.0, 0.0, 0.0]
    end

    test "回帰の平均二乗誤差は f64 のまま一致する" do
      t = [1.0, 2.0, 3.0, 4.0]
      y = [1.5, 2.0, 3.0, 4.5]

      assert C.scholar_mean_squared_error(t, y) == C.mean_squared_error(t, y)
    end

    test "AUC が一致する" do
      scores = [0.1, 0.4, 0.35, 0.8]
      labels = [false, false, true, true]

      assert_in_delta C.scholar_auc(scores, labels), C.auc(C.roc_curve(scores, labels)), 1.0e-12
    end

    test "同じスコアが並んでも AUC が一致する" do
      scores = [0.5, 0.5, 0.2, 0.9, 0.5]
      labels = [true, false, false, true, true]

      assert_in_delta C.scholar_auc(scores, labels), C.auc(C.roc_curve(scores, labels)), 1.0e-12
    end
  end

  describe "Scholar の交差検証と突き合わせる" do
    test "k_fold_split は余りを捨てるので自作と件数が違う" do
      # 自作は余りを先頭の分割から 1 件ずつ配るが、Scholar は floor(件数 / 分割数) で切りそろえる
      assert Enum.map(C.k_fold_sequential(10, 3), &length(&1.test)) == [4, 3, 3]
      assert C.scholar_k_fold_test_sizes(10, 3) == [3, 3, 3]

      assert Enum.map(C.k_fold_sequential(11, 4), &length(&1.test)) == [3, 3, 3, 2]
      assert C.scholar_k_fold_test_sizes(11, 4) == [2, 2, 2, 2]
    end

    test "割り切れる件数なら自作と件数が一致する" do
      assert Enum.map(C.k_fold_sequential(20, 5), &length(&1.test)) ==
               C.scholar_k_fold_test_sizes(20, 5)
    end

    test "割り切れる件数なら分割ごとの MSE が一致する" do
      x = features(1..12)
      t = Enum.map(1..12, &(&1 * 2.0 + 1.0))
      folds = C.k_fold_sequential(12, 3)

      mine =
        C.linear_trainer(columns())
        |> C.cross_validate(x, t, folds, &C.mean_squared_error/2)
        |> Enum.to_list()

      theirs = C.scholar_cross_validate_mse(x, t, columns(), 3)

      assert length(theirs) == 3

      for {a, b} <- Enum.zip(mine, theirs) do
        assert_in_delta a, b, 1.0e-6
      end
    end
  end

  describe "実データ" do
    @tag :data
    test "実行すると交差検証の平均を表示する" do
      output = ExUnit.CaptureIO.capture_io(&C.run/0)

      assert String.split(output, "\n", trim: true) == [
               "Survived（決定木、5 分割交差検証の平均）",
               "  正解率: 0.7811",
               "  適合率: 0.7759",
               "  再現率: 0.6306",
               "  F値: 0.6833",
               "cinema（線形回帰、5 分割交差検証の平均）",
               "  RMSE: 405.77",
               "  MAE: 321.53",
               "Scholar の k_fold_split のテストデータの件数（5 分割）",
               "  891 件を自作: 179, 178, 178, 178, 178",
               "  891 件を Scholar: 178, 178, 178, 178, 178"
             ]
    end

    @tag :data
    test "Survived の指標は Scholar の指標と一致する" do
      %{x: x, t: t} = survived_data()
      [fold | _] = C.k_fold(length(x), C.n_splits(), C.seed())

      predict =
        C.tree_trainer(C.survived_columns(), 2).(C.pick(x, fold.train), C.pick(t, fold.train))

      actual = C.pick(t, fold.test)
      predicted = predict.(C.pick(x, fold.test))
      cm = C.confusion_matrix(actual, predicted, C.survived())
      scores = C.scholar_scores(actual, predicted, C.survived())

      assert_in_delta scores.accuracy, C.accuracy(actual, predicted), 1.0e-7
      assert_in_delta scores.precision, C.precision(cm), 1.0e-7
      assert_in_delta scores.recall, C.recall(cm), 1.0e-7
      assert_in_delta scores.f1_score, C.f1_score(cm), 1.0e-7
    end

    @tag :data
    test "cinema の分割ごとの MSE は第 7 章の RMSE の二乗と一致する" do
      %{x: x, t: t} =
        Dataset.dir() |> Path.join("cinema.csv") |> Chapter02.load_table() |> C.prepare_cinema()

      folds = C.k_fold(length(x), C.n_splits(), C.seed())
      trainer = C.linear_trainer(Chapter07.feature_columns())
      mse = Enum.to_list(C.cross_validate(trainer, x, t, folds, &C.mean_squared_error/2))

      rmse =
        Enum.to_list(C.cross_validate(trainer, x, t, folds, &Chapter07.root_mean_squared_error/2))

      for {a, b} <- Enum.zip(mse, rmse) do
        assert_in_delta a, b * b, 1.0e-6
      end
    end
  end
end
