defmodule GettingStartedMl.Chapter09Test do
  use ExUnit.Case, async: true

  alias GettingStartedMl.Chapter09, as: C
  alias GettingStartedMl.Dataset

  @delta 1.0e-9

  defp rm_lstat(rm, lstat), do: %{RM: rm * 1.0, LSTAT: lstat * 1.0}
  defp rm_lstat_columns, do: [:RM, :LSTAT]

  defp temp_file(name, contents) do
    path = Path.join(System.tmp_dir!(), "#{name}-#{System.unique_integer([:positive])}")
    File.write!(path, contents)
    on_exit(fn -> File.rm(path) end)
    path
  end

  describe "カテゴリ値をダミー変数にする" do
    test "先頭を除いたカテゴリを辞書順に返す" do
      assert C.categories(["low", "high", "very_low", "low"]) == ["low", "very_low"]
    end

    test "欠損値は数えない" do
      assert C.categories(["low", "", "high"]) == ["low"]
    end

    test "カテゴリごとに零と一の列を作り元の列を取り除く" do
      table = %{
        columns: [:RM, :CRIME],
        rows: [
          %{RM: "6.0", CRIME: "low"},
          %{RM: "6.0", CRIME: "high"},
          %{RM: "6.0", CRIME: "very_low"}
        ]
      }

      encoded = C.encode(table, :CRIME, ["low", "very_low"])

      assert encoded.columns == [:RM, :CRIME_low, :CRIME_very_low]
      assert Enum.map(encoded.rows, & &1[:CRIME_low]) == ["1", "0", "0"]
      assert Enum.map(encoded.rows, & &1[:CRIME_very_low]) == ["0", "0", "1"]
      assert Enum.all?(encoded.rows, &(not is_map_key(&1, :CRIME)))
    end
  end

  describe "特徴量の標準化" do
    test "訓練データから列ごとの平均と標準偏差を求める" do
      std =
        C.standardizer([rm_lstat(1, 10), rm_lstat(2, 10), rm_lstat(3, 40)], rm_lstat_columns())

      assert_in_delta std.means[:RM], 2.0, @delta
      assert_in_delta std.means[:LSTAT], 20.0, @delta
      assert_in_delta std.stds[:RM], :math.sqrt(2.0 / 3), @delta
      assert_in_delta std.stds[:LSTAT], :math.sqrt(200.0), @delta
    end

    test "値がすべて同じ列の標準偏差は一にして零を返す" do
      std = C.standardizer([rm_lstat(5, 1), rm_lstat(5, 2)], rm_lstat_columns())

      assert_in_delta std.stds[:RM], 1.0, @delta
      assert_in_delta C.standardize(std, rm_lstat(5, 1))[:RM], 0.0, @delta
    end

    test "平均と標準偏差を持たない列はそのまま残す" do
      std = C.standardizer([rm_lstat(1, 10), rm_lstat(3, 10)], [:RM])

      assert C.standardize(std, %{RM: 3.0, LSTAT: 7.0})[:LSTAT] == 7.0
    end

    test "一件も無ければ失敗する" do
      assert_raise ArgumentError, fn -> C.standardizer([], rm_lstat_columns()) end
    end
  end

  describe "Scholar の標準化との突き合わせ" do
    test "ScholarのStandardScalerは件数で割る標準偏差を使う" do
      train = [6.2, 5.8, 7.1, 6.5]
      values = [6.2, 8.0]
      std = C.standardizer(Enum.map(train, &%{RM: &1}), [:RM])
      expected = Enum.map(values, &C.standardize(std, %{RM: &1})[:RM])

      Enum.zip(expected, C.scholar_standardize(train, values))
      |> Enum.each(fn {mine, theirs} -> assert_in_delta mine, theirs, @delta end)
    end

    test "件数から一を引いて割る標準偏差とは一致しない" do
      train = [6.2, 5.8, 7.1, 6.5]
      mean = Enum.sum(train) / length(train)

      sample_std =
        :math.sqrt(Enum.sum(Enum.map(train, &((&1 - mean) * (&1 - mean)))) / (length(train) - 1))

      [first | _] = C.scholar_standardize(train, [8.0])
      refute_in_delta first, (8.0 - mean) / sample_std, 1.0e-6
    end
  end

  describe "多項式特徴量" do
    test "二列なら二乗の列と二つの列の積の列を加える" do
      x = [rm_lstat(2, 5), rm_lstat(3, 7)]
      expanded = C.expand(x, rm_lstat_columns())

      assert C.expanded_columns(rm_lstat_columns()) ==
               [:RM, :LSTAT, :"RM^2", :"RM LSTAT", :"LSTAT^2"]

      assert Enum.map(expanded, & &1[:"RM LSTAT"]) == [10.0, 21.0]
      assert Enum.map(expanded, & &1[:"LSTAT^2"]) == [25.0, 49.0]
    end

    test "三列ならscikit-learnと同じ順の九列になる" do
      assert Enum.map(C.expanded_columns([:RM, :LSTAT, :PTRATIO]), &to_string/1) ==
               [
                 "RM",
                 "LSTAT",
                 "PTRATIO",
                 "RM^2",
                 "RM LSTAT",
                 "RM PTRATIO",
                 "LSTAT^2",
                 "LSTAT PTRATIO",
                 "PTRATIO^2"
               ]
    end

    test "使う項だけを選ぶ" do
      [row] = C.select_columns(C.expand([rm_lstat(2, 5)], rm_lstat_columns()), [:RM, :"RM^2"])

      assert row == %{RM: 2.0, "RM^2": 4.0}
    end
  end

  describe "外れ値の検出" do
    test "四分位数の位置が値の間にあれば前後の値から線形補間する" do
      assert_in_delta C.quantile([4.0, 1.0, 3.0, 2.0], 0.25), 1.75, @delta
    end

    test "第三四分位数からIQRの一点五倍より大きい値を外れ値とする" do
      assert C.iqr_outliers([1.0, 2.0, 3.0, 4.0, 100.0]) == [false, false, false, false, true]
    end

    test "訓練データから外れ値の行を取り除きテストデータは残す" do
      split = %{
        x_train: Enum.map([1, 2, 3, 4, 100], &%{RM: &1 * 1.0}),
        t_train: [1.0, 2.0, 3.0, 4.0, 100.0],
        x_test: [%{RM: 100.0}],
        t_test: [100.0]
      }

      kept = C.remove_target_outliers(split)

      assert kept.t_train == [1.0, 2.0, 3.0, 4.0]
      assert kept.x_train == Enum.map([1, 2, 3, 4], &%{RM: &1 * 1.0})
      assert kept.t_test == [100.0]
    end
  end

  describe "区切り文字と文字コード" do
    test "タブ区切りのファイルを読み込む" do
      path = temp_file("bike", "weather_id\tcnt\n1\t100\n")
      table = C.load_delimited(path, :utf8, "\t")

      assert table.columns == [:weather_id, :cnt]
      assert table.rows == [%{weather_id: "1", cnt: "100"}]
    end

    test "Shift_JISのファイルを文字コードを渡して読み込む" do
      path = temp_file("weather", C.to_cp932("weather_id,weather\n1,晴れ\n"))
      table = C.load_delimited(path, :cp932, ",")

      assert Enum.map(table.rows, & &1.weather) == ["晴れ"]
    end

    test "Shift_JISのファイルをUTF-8として読むと例外を投げずに壊れた文字列になる" do
      path = temp_file("weather", C.to_cp932("weather_id,weather\n1,晴れ\n"))
      table = C.load_delimited(path, :utf8, ",")

      [%{weather: weather}] = table.rows
      refute weather == "晴れ"
      refute String.valid?(weather)
    end

    test "UTF-8のファイルをShift_JISとして読むと失敗する" do
      path = temp_file("weather", "weather_id,weather\n1,晴れ\n")

      assert_raise ArgumentError, fn -> C.load_delimited(path, :cp932, ",") end
    end
  end

  describe "表の結合" do
    defp bike, do: %{columns: [:weather_id, :cnt], rows: [%{weather_id: "1", cnt: "100"}]}

    test "天気IDで二つの表を結合する" do
      weather = %{columns: [:weather_id, :weather], rows: [%{weather_id: "1", weather: "晴れ"}]}
      joined = C.join_weather(bike(), weather)

      assert joined.columns == [:weather_id, :cnt, :weather]
      assert joined.rows == [%{weather_id: "1", cnt: "100", weather: "晴れ"}]
    end

    test "天気の表に無いIDの行は残さない" do
      weather = %{columns: [:weather_id, :weather], rows: [%{weather_id: "2", weather: "雨"}]}

      assert C.join_weather(bike(), weather).rows == []
    end

    test "天気IDが一意でなければ失敗する" do
      weather = %{
        columns: [:weather_id, :weather],
        rows: [%{weather_id: "1", weather: "晴れ"}, %{weather_id: "1", weather: "雨"}]
      }

      assert_raise ArgumentError, fn -> C.join_weather(bike(), weather) end
    end

    test "天気ごとの平均利用者数を多い順に返す" do
      joined = %{
        columns: [:weather_id, :cnt, :weather],
        rows: [
          %{weather_id: "1", cnt: "100", weather: "晴れ"},
          %{weather_id: "1", cnt: "200", weather: "晴れ"},
          %{weather_id: "2", cnt: "10", weather: "雨"}
        ]
      }

      assert C.mean_count_by_weather(joined) == [{"晴れ", 150.0}, {"雨", 10.0}]
    end
  end

  describe "線形回帰と決定係数" do
    test "一次関数を当てきると決定係数が一になる" do
      model = C.linear_fit([[1.0], [2.0], [3.0]], [3.0, 5.0, 7.0])

      assert_in_delta model.intercept, 1.0, @delta
      assert_in_delta hd(model.weights), 2.0, @delta

      assert_in_delta C.r_squared(
                        [3.0, 5.0, 7.0],
                        C.linear_predict(model, [[1.0], [2.0], [3.0]])
                      ),
                      1.0,
                      @delta
    end

    test "同じ値の列が二つあれば失敗する" do
      assert_raise ArgumentError, fn ->
        C.linear_fit([[1.0, 1.0], [2.0, 2.0], [3.0, 3.0]], [3.0, 5.0, 7.0])
      end
    end
  end

  describe "二乗の項を加えると曲線を当てられる" do
    # 価格を 3 × RM² + 1 にした架空のデータ
    defp price(rm), do: 1.0 + 3.0 * rm * rm

    defp squared_split do
      rms = [1.0, 2.0, 3.0, 4.0, 5.0, 6.0]

      %{
        columns: [:RM],
        x_train: Enum.map(rms, &%{RM: &1}),
        t_train: Enum.map(rms, &price/1),
        x_test: Enum.map([1.5, 2.5], &%{RM: &1}),
        t_test: Enum.map([1.5, 2.5], &price/1)
      }
    end

    test "元の列だけでは当てきれない" do
      assert C.score_feature_set(squared_split(), [:RM], [:RM]).train < 0.99
    end

    test "二乗の項を加えると訓練データもテストデータも当てきる" do
      scores = C.score_feature_set(squared_split(), [:RM], [:RM, :"RM^2"])

      assert_in_delta scores.train, 1.0, @delta
      assert_in_delta scores.test, 1.0, @delta
    end
  end

  describe "実データ" do
    @tag :data
    test "実行するとほかの言語版と同じ決定係数を表示する" do
      assert ExUnit.CaptureIO.capture_io(&C.run/0) == """
             訓練データ: 70 件, テストデータ: 30 件
             特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low
             標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00
             決定係数:
               元の特徴量（3 列）: 訓練 0.6056, テスト 0.6950
               2 乗の項を追加（6 列）: 訓練 0.7740, テスト 0.8628
               交互作用の項も追加（9 列）: 訓練 0.7953, テスト 0.8213
             訓練データの PRICE の外れ値: 8 件
               外れ値を除いて 2 乗の項を追加: 訓練 0.6717, テスト 0.7947
             天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
             """
    end

    @tag :data
    test "天気の表はShift_JISで読める" do
      table = C.load_delimited(Path.join(Dataset.dir(), "weather.csv"), :cp932, ",")

      assert Enum.map(table.rows, & &1.weather) == ["晴れ", "曇り", "雨"]
    end
  end
end
