defmodule GettingStartedMl.Chapter08Test do
  use ExUnit.Case, async: true

  alias GettingStartedMl.{Chapter02, Chapter03, Dataset}
  alias GettingStartedMl.Chapter08, as: C
  alias Scholar.Impute.SimpleImputer
  alias Scholar.Preprocessing.OneHotEncoder

  defp table(columns, rows) do
    %{columns: columns, rows: Enum.map(rows, &Map.new(Enum.zip(columns, &1)))}
  end

  defp values(t, column), do: Enum.map(t.rows, &Map.fetch!(&1, column))

  defp ages do
    table([:Pclass, :Sex, :Age], [
      ["1", "female", "30"],
      ["1", "female", "40"],
      ["3", "male", "10"],
      ["3", "male", "20"],
      ["3", "male", ""]
    ])
  end

  defp ports do
    table([:Embarked], [["S"], ["C"], ["S"], [""]])
  end

  defp sexes do
    table([:Sex, :Embarked], [["male", "S"], ["female", "C"], ["female", "Q"]])
  end

  defp train_x do
    C.features_table(
      Enum.map(
        [
          ["1", "female", "35", "0", "0", "70", "C"],
          ["1", "female", "", "1", "0", "80", "S"],
          ["3", "male", "20", "0", "0", "8", "S"],
          ["3", "male", "", "0", "0", "7", ""]
        ],
        &Map.new(Enum.zip(C.feature_columns(), &1))
      )
    )
  end

  defp train_t, do: [1, 1, 0, 0]

  defp new_passengers do
    C.features_table(
      Enum.map(
        [
          ["1", "female", "", "0", "0", "50", "C"],
          ["3", "male", "", "0", "0", "8", "S"]
        ],
        &Map.new(Enum.zip(C.feature_columns(), &1))
      )
    )
  end

  defp in_tmp(name, fun) do
    path =
      Path.join(System.tmp_dir!(), "#{name}-#{System.unique_integer([:positive])}.model")

    try do
      fun.(path)
    after
      File.rm(path)
    end
  end

  describe "グループ別の中央値で補完する" do
    test "グループの中央値で欠損値を補完する" do
      fitted = C.fit_step(C.group_median_imputer(:Age, [:Pclass, :Sex]), ages())
      assert values(C.apply_step(fitted, ages()), :Age) == ["30", "40", "10", "20", "15.0"]
    end

    test "訓練データで求めた中央値を別のデータに使う" do
      fitted = C.fit_step(C.group_median_imputer(:Age, [:Pclass, :Sex]), ages())
      other = table([:Pclass, :Sex, :Age], [["1", "female", ""]])
      assert values(C.apply_step(fitted, other), :Age) == ["35.0"]
    end

    test "訓練データに無いグループは全体の中央値で補完する" do
      fitted = C.fit_step(C.group_median_imputer(:Age, [:Pclass, :Sex]), ages())
      other = table([:Pclass, :Sex, :Age], [["2", "male", ""]])
      # 欠けていない年齢は 30, 40, 10, 20 なので全体の中央値は 25.0
      assert values(C.apply_step(fitted, other), :Age) == ["25.0"]
    end

    test "中央値は件数が偶数なら中央の二つの平均になる" do
      assert C.median([3.0, 1.0, 2.0]) == 2.0
      assert C.median([4.0, 1.0, 2.0, 3.0]) == 2.5
    end
  end

  describe "最頻値で補完する" do
    test "いちばん多い値で欠損値を補完する" do
      fitted = C.fit_step(C.most_frequent_imputer(:Embarked), ports())
      assert values(C.apply_step(fitted, ports()), :Embarked) == ["S", "C", "S", "S"]
    end

    test "同数なら値の順で前のものを最頻値にする" do
      fitted = C.fit_step(C.most_frequent_imputer(:Embarked), table([:Embarked], [["S"], ["C"]]))
      assert fitted.most_frequent == "C"
    end
  end

  describe "ダミー変数にする" do
    test "最初のカテゴリを除いたダミー変数にする" do
      fitted = C.fit_step(C.dummy_encoder([:Sex, :Embarked]), sexes())
      encoded = C.apply_step(fitted, sexes())

      assert encoded.columns == [:Sex_male, :Embarked_Q, :Embarked_S]
      assert values(encoded, :Sex_male) == ["1", "0", "0"]
      assert values(encoded, :Embarked_Q) == ["0", "0", "1"]
      assert values(encoded, :Embarked_S) == ["1", "0", "0"]
    end

    test "別のデータにも訓練データと同じダミー変数の列を作る" do
      fitted = C.fit_step(C.dummy_encoder([:Sex, :Embarked]), sexes())
      encoded = C.apply_step(fitted, table([:Sex, :Embarked], [["female", "S"]]))

      assert encoded.columns == [:Sex_male, :Embarked_Q, :Embarked_S]

      # 大文字で始まる鍵は `row.Sex_male` と書けない（別名として解釈される）ので Map で引く
      assert Enum.map(encoded.rows, &{&1[:Sex_male], &1[:Embarked_Q], &1[:Embarked_S]}) ==
               [{"0", "0", "1"}]
    end
  end

  describe "クラスの重みを付けた決定木" do
    test "重みがすべて一なら第三章のジニ不純度と同じになる" do
      labels = [0, 0, 1, 1, 1]

      assert_in_delta C.weighted_gini(labels, List.duplicate(1.0, 5)),
                      Chapter03.gini(labels),
                      1.0e-12
    end

    test "重みの合計が同じなら先に現れたラベルを選ぶ" do
      assert C.weighted_majority([0, 1], [1.0, 1.0]) == 0
      assert C.weighted_majority([1, 0], [1.0, 1.0]) == 1
    end

    test "balanced の重みはクラスの件数に反比例する" do
      assert C.weights_of([0, 0, 0, 1], :balanced) == [
               4 / 6,
               4 / 6,
               4 / 6,
               4 / 2
             ]
    end

    test "重み付けなしの重みはすべて一になる" do
      assert C.weights_of([0, 1], :none) == [1.0, 1.0]
    end

    test "知らない重みの付け方は使えない" do
      assert_raise ArgumentError, fn -> C.weights_of([0, 1], :unknown) end
    end

    test "重み付けなしなら第三章の決定木と同じ木になる" do
      x = [%{a: 1.0, b: 1.0}, %{a: 2.0, b: 1.0}, %{a: 3.0, b: 2.0}, %{a: 4.0, b: 2.0}]
      t = [0, 0, 1, 1]
      ours = C.fit_tree(x, t, [:a, :b], 2, :none)
      theirs = Chapter03.fit(x, Enum.map(t, &to_string/1), [:a, :b], 2)

      assert Enum.map(C.predict_tree(ours, x), &to_string/1) == Chapter03.predict(theirs, x)
    end

    test "balanced にすると少数派のラベルを予測しやすくなる" do
      # 1 が 2 件、0 が 5 件。深さ 1 の木では、重みを付けないとどの葉も多数派の 0 になる
      x = Enum.map(1..7, &%{a: &1 * 1.0})
      t = [0, 0, 1, 0, 1, 0, 0]

      assert C.predict_tree(C.fit_tree(x, t, [:a], 1, :none), x) == [0, 0, 0, 0, 0, 0, 0]
      assert C.predict_tree(C.fit_tree(x, t, [:a], 1, :balanced), x) == [1, 1, 1, 1, 1, 0, 0]
    end
  end

  describe "パイプライン" do
    test "前処理の順に学習して欠損値の残らない特徴量にする" do
      fitted = C.fit(C.build_pipeline(3, :none), train_x(), train_t())

      assert fitted.columns == [:Pclass, :Age, :SibSp, :Parch, :Fare, :Sex_male, :Embarked_S]

      assert Enum.all?(C.features(fitted, train_x()), fn row ->
               Enum.all?(fitted.columns, &is_number(Map.fetch!(row, &1)))
             end)
    end

    test "学習済みのパイプラインで欠損値を含むデータを予測する" do
      fitted = C.fit(C.build_pipeline(3, :none), train_x(), train_t())
      assert C.predict(fitted, new_passengers()) == [1, 0]
    end

    test "欠損値が残っていれば特徴量にできない" do
      assert_raise ArgumentError, fn -> C.to_features(%{columns: [:Age], rows: [%{Age: ""}]}) end
    end
  end

  describe "モデルの保存と読み込み" do
    test "保存して読み込んだパイプラインは同じ予測をする" do
      fitted = C.fit(C.build_pipeline(3, :balanced), train_x(), train_t())

      in_tmp("survived", fn path ->
        C.save_model(fitted, path)
        assert C.load_model(path) == fitted

        assert C.predict(C.load_model(path), new_passengers()) ==
                 C.predict(fitted, new_passengers())
      end)
    end

    test "形式の版が違うモデルは読み込めない" do
      in_tmp("broken", fn path ->
        File.write!(path, :erlang.term_to_binary(%{format: 999}))
        assert_raise ArgumentError, fn -> C.load_model(path) end
      end)
    end

    test "壊れたファイルは読み込めない" do
      in_tmp("garbage", fn path ->
        File.write!(path, "これはモデルではありません")
        assert_raise ArgumentError, fn -> C.load_model(path) end
      end)
    end
  end

  describe "評価" do
    test "正解率と見つけた生存者の数を求める" do
      fitted = C.fit(C.build_pipeline(3, :none), train_x(), train_t())

      split = %{
        x_train: train_x().rows,
        t_train: train_t(),
        x_test: new_passengers().rows,
        t_test: [1, 1]
      }

      assert C.evaluate(fitted, split) == %{
               train_accuracy: 1.0,
               test_accuracy: 0.5,
               found_survivors: 1,
               survivors: 2
             }
    end
  end

  describe "Scholar の前処理と突き合わせる" do
    test "SimpleImputer の中央値は自作の全体の中央値と一致する" do
      fitted = C.fit_step(C.group_median_imputer(:Age, [:Pclass, :Sex]), ages())

      imputer =
        SimpleImputer.fit(
          Nx.tensor([[30.0], [40.0], [10.0], [20.0], [:nan]], type: :f64),
          strategy: :median
        )

      assert_in_delta Nx.to_number(imputer.statistics),
                      fitted.overall_median,
                      1.0e-12
    end

    test "SimpleImputer にはグループ別の補完が無いので値が変わる" do
      imputer =
        SimpleImputer.fit(
          Nx.tensor([[30.0], [40.0], [10.0], [20.0], [:nan]], type: :f64),
          strategy: :median
        )

      imputed =
        SimpleImputer.transform(
          imputer,
          Nx.tensor([[30.0], [40.0], [10.0], [20.0], [:nan]], type: :f64)
        )

      # 自作は 3 等客室の男性の中央値 15.0 で埋めるが、SimpleImputer は全体の中央値 25.0 で埋める
      assert Nx.to_number(imputed[4][0]) == 25.0
    end

    test "SimpleImputer の最頻値は同数なら小さいほうを選ぶ" do
      imputer =
        SimpleImputer.fit(
          Nx.tensor([[0.0], [1.0], [:nan]], type: :f64),
          strategy: :mode
        )

      # 自作の most_frequent_imputer も、同数ならカテゴリの順で前のものを選ぶ
      assert Nx.to_number(imputer.statistics) == 0.0
    end

    test "OneHotEncoder は最初のカテゴリを落とさない" do
      fitted = C.fit_step(C.dummy_encoder([:Sex]), sexes())
      mine = values(C.apply_step(fitted, sexes()), :Sex_male)

      # male を 1、female を 0 に符号化してから渡す
      codes = Nx.tensor([1, 0, 0])
      encoder = OneHotEncoder.fit(codes, num_categories: 2)
      theirs = OneHotEncoder.transform(encoder, codes)

      assert Nx.to_flat_list(theirs[[.., 1]]) == Enum.map(mine, &String.to_integer/1)
      assert Nx.shape(theirs) == {3, 2}
    end
  end

  describe "実データ" do
    @tag :data
    test "実行するとほかの言語版と同じ正解率と生存者の数を表示する" do
      output =
        in_tmp("run", fn path ->
          ExUnit.CaptureIO.capture_io(fn -> C.run(path) end)
        end)

      assert String.split(output, "\n", trim: true) == [
               "データ件数: 891（生存 342, 死亡 549）",
               "訓練データ: 712 件, テストデータ: 179 件",
               "classWeight=none: 訓練 0.854, テスト 0.799, 生存者 79 人中 59 人を発見",
               "classWeight=balanced: 訓練 0.848, テスト 0.804, 生存者 79 人中 65 人を発見",
               "保存したモデル: 読み込めました",
               "架空の乗客の予測: [1, 0]"
             ]
    end

    @tag :data
    test "深さ二では balanced にすると見つけられる生存者が四十一人から六十八人に増える" do
      split = survived_split()

      assert C.evaluate(fit_pipeline(split, 2, :none), split).found_survivors == 41
      assert C.evaluate(fit_pipeline(split, 2, :balanced), split).found_survivors == 68
    end

    @tag :data
    test "重み付けなしなら前処理後のテストデータで第三章の決定木と予測が一致する" do
      split = survived_split()

      for max_depth <- 1..10 do
        pipeline = fit_pipeline(split, max_depth, :none)
        x_train = C.features(pipeline, C.features_table(split.x_train))
        x_test = C.features(pipeline, C.features_table(split.x_test))

        theirs =
          Chapter03.predict(
            Chapter03.fit(
              x_train,
              Enum.map(split.t_train, &to_string/1),
              pipeline.columns,
              max_depth
            ),
            x_test
          )

        mine = Enum.map(C.predict(pipeline, C.features_table(split.x_test)), &to_string/1)
        assert mine == theirs, "深さ #{max_depth}"
      end
    end

    defp survived_split do
      rows = Chapter02.load_table(Path.join(Dataset.dir(), "Survived.csv")).rows
      Chapter02.split_train_test(rows, C.target_labels(rows), 0.2, 0)
    end

    defp fit_pipeline(split, max_depth, class_weight) do
      C.fit(
        C.build_pipeline(max_depth, class_weight),
        C.features_table(split.x_train),
        split.t_train
      )
    end
  end
end
