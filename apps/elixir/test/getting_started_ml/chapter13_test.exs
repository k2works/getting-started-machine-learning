defmodule GettingStartedMl.Chapter13Test do
  use ExUnit.Case, async: true

  alias GettingStartedMl.Chapter13, as: C
  alias GettingStartedMl.Dataset

  @tolerance 1.0e-9

  defp close_to?(expected, actual, tolerance \\ @tolerance),
    do: abs(expected - actual) < tolerance

  # 2 列目が 1 列目のちょうど 2 倍。点は 1 本の直線に並ぶ。
  defp correlated, do: [[1.0, 2.0], [2.0, 4.0], [3.0, 6.0], [4.0, 8.0]]

  # 架空の Boston。CRIME は 3 種類、RM に欠損値が 1 件。
  defp boston_like do
    %{
      columns: [:CRIME, :RM, :PRICE],
      rows: [
        %{CRIME: "low", RM: "6.0", PRICE: "20.0"},
        %{CRIME: "very_low", RM: "7.0", PRICE: "30.0"},
        %{CRIME: "high", RM: "", PRICE: "10.0"},
        %{CRIME: "low", RM: "5.0", PRICE: "15.0"}
      ]
    }
  end

  describe "分散共分散行列" do
    test "分散と共分散を並べる" do
      c = C.covariance_matrix([[1.0, 2.0], [2.0, 4.0], [3.0, 6.0]])

      assert close_to?(1.0, c |> Enum.at(0) |> Enum.at(0))
      assert close_to?(4.0, c |> Enum.at(1) |> Enum.at(1))
      assert close_to?(2.0, c |> Enum.at(0) |> Enum.at(1))
      assert Enum.at(Enum.at(c, 0), 1) == Enum.at(Enum.at(c, 1), 0)
    end

    test "1 件しかなければ失敗する" do
      assert_raise ArgumentError, fn -> C.covariance_matrix([[1.0, 2.0]]) end
    end
  end

  describe "Nx の固有値分解" do
    test "固有値を大きい順に返す" do
      %{values: values} = C.eigen_decomposition([[2.0, 0.0], [0.0, 5.0]])

      assert values == [5.0, 2.0]
    end

    test "固有ベクトルは掛けても向きが変わらず固有値倍になる" do
      m = [[2.0, 1.0], [1.0, 2.0]]
      %{values: values, vectors: vectors} = C.eigen_decomposition(m)

      Enum.zip(values, vectors)
      |> Enum.each(fn {value, vector} ->
        multiplied = Enum.map(m, fn row -> Enum.sum(Enum.zip_with(row, vector, &(&1 * &2))) end)

        assert Enum.all?(Enum.zip(multiplied, vector), fn {left, right} ->
                 close_to?(left, value * right)
               end)
      end)
    end

    test "対称でない行列は失敗する" do
      assert_raise ArgumentError, fn ->
        C.eigen_decomposition([[1.0, 2.0], [3.0, 4.0]])
      end
    end

    test "正方行列でなければ失敗する" do
      assert_raise ArgumentError, fn -> C.eigen_decomposition([[1.0, 2.0]]) end
    end

    test "Nx.LinAlg.eigh は上三角だけを見るので、対称かどうかは自分で確かめる" do
      # [[1, 2], [3, 4]] の固有値は 5.37 と -0.37 だが、上三角から作った
      # [[1, 2], [2, 4]] の固有値 5 と 0 が黙って返る
      {values, _vectors} = Nx.LinAlg.eigh(Nx.tensor([[1.0, 2.0], [3.0, 4.0]], type: :f64))

      assert Nx.to_flat_list(values) == [5.0, 0.0]
    end
  end

  describe "主成分" do
    test "完全に相関する 2 列なら第 1 主成分だけで分散をすべて説明する" do
      model = C.fit(correlated(), 2)

      assert close_to?(1.0, Enum.at(model.explained_variance_ratio, 0))
      assert close_to?(0.0, Enum.at(model.explained_variance_ratio, 1))
    end

    test "長さ 1 で互いに直交する" do
      components =
        C.fit(
          [[1.0, 2.0, 0.5], [2.0, 4.0, 0.1], [3.0, 6.0, 0.9], [4.0, 8.0, 0.2]],
          3
        ).components

      Enum.each(components, fn row ->
        assert close_to?(1.0, :math.sqrt(Enum.sum(Enum.map(row, &(&1 * &1)))))
      end)

      assert close_to?(0.0, dot(Enum.at(components, 0), Enum.at(components, 1)))
      assert close_to?(0.0, dot(Enum.at(components, 0), Enum.at(components, 2)))
    end

    test "主成分の数が範囲の外なら失敗する" do
      assert_raise ArgumentError, fn -> C.fit(correlated(), 0) end
      assert_raise ArgumentError, fn -> C.fit(correlated(), 3) end
    end
  end

  describe "符号の規則" do
    test "絶対値が最大の要素が負なら符号を反転する" do
      assert C.normalize_signs([[-0.8, 0.6]]) == [[0.8, -0.6]]
    end

    test "絶対値が最大の要素がすでに正ならそのまま" do
      assert C.normalize_signs([[-0.3, 0.4, 0.9]]) == [[-0.3, 0.4, 0.9]]
    end

    test "絶対値が同じなら前の要素を見る" do
      assert C.normalize_signs([[-0.5, 0.5]]) == [[0.5, -0.5]]
    end
  end

  describe "射影" do
    test "平均を引いてから主成分の向きに射影する" do
      x = correlated()
      model = C.fit(x, 1)
      projected = C.transform(model, x)

      assert length(projected) == 4
      assert length(hd(projected)) == 1
      assert close_to?(0.0, Enum.sum(Enum.map(projected, &hd/1)))
    end
  end

  describe "必要な主成分の数" do
    test "3 つで 0.8 に届く" do
      assert C.components_needed([0.5, 0.2, 0.15, 0.1, 0.05], 0.8) == 3
    end

    test "しきい値を上げると必要な主成分の数が増える" do
      assert C.components_needed([0.5, 0.2, 0.15, 0.1, 0.05], 0.9) == 4
    end

    test "どこまで足しても届かなければすべての主成分を使う" do
      assert C.components_needed([0.5, 0.2], 0.99) == 2
    end
  end

  describe "主成分への影響が大きい列" do
    test "係数の絶対値が大きい順に列名と係数を返す" do
      assert C.top_loadings([0.5, -0.9, 0.1], [:RM, :LSTAT, :ZN], 3) == [
               %{column: :LSTAT, value: -0.9},
               %{column: :RM, value: 0.5},
               %{column: :ZN, value: 0.1}
             ]
    end

    test "k 個だけ返す" do
      assert C.top_loadings([0.5, -0.9, 0.1], [:RM, :LSTAT, :ZN], 1) == [
               %{column: :LSTAT, value: -0.9}
             ]
    end

    test "絶対値が同じなら列の順を保つ" do
      assert C.top_loadings([0.5, -0.5], [:RM, :LSTAT], 2) == [
               %{column: :RM, value: 0.5},
               %{column: :LSTAT, value: -0.5}
             ]
    end
  end

  describe "Scholar の PCA" do
    test "寄与率も主成分も自作と一致する" do
      # 固有値が重なると固有ベクトルの向きが一意に決まらないので、3 列とも独立に動くデータにする
      x = [[2.5, 2.4, 0.5], [0.5, 0.7, 1.2], [2.2, 2.9, 0.3], [1.9, 2.2, 2.1], [3.1, 3.0, 0.7]]
      mine = C.fit(x, 3)
      theirs = C.scholar_fit(x, 3)

      assert Enum.all?(
               Enum.zip(mine.explained_variance_ratio, theirs.explained_variance_ratio),
               fn
                 {a, b} -> close_to?(a, b, 1.0e-8)
               end
             )

      assert close_to?(0.0, max_gap(mine.components, theirs.components), 1.0e-8)
      assert close_to?(0.0, Enum.max(Enum.map(Enum.zip(mine.mean, theirs.mean), &gap/1)), 1.0e-8)
    end

    test "符号をそろえないと主成分の向きが逆になることがある" do
      x = [[1.0, 2.0], [2.0, 4.0], [3.0, 6.1], [4.0, 8.0]]
      raw = C.scholar_components(x, 2)

      # Scholar は左特異ベクトル（U）の絶対値が最大の行で符号を決めるので、
      # 「主成分の絶対値が最大の要素が正」という自作の規則とは限らない
      assert C.normalize_signs(raw) != raw
    end
  end

  describe "Boston の前処理" do
    test "CRIME をダミー変数の列に置き換える" do
      assert C.standardize_table(boston_like()).columns ==
               [:RM, :PRICE, :CRIME_low, :CRIME_very_low]
    end

    test "欠損値を補完してから各列を平均 0・標準偏差 1 にそろえる" do
      %{columns: columns, x: x} = C.standardize_table(boston_like())

      Enum.each(columns, fn column ->
        values = Enum.map(x, &Map.fetch!(&1, column))
        mean = Enum.sum(values) / length(values)
        variance = Enum.sum(Enum.map(values, &((&1 - mean) * (&1 - mean)))) / length(values)

        assert close_to?(0.0, mean), "#{column} の平均"
        assert close_to?(1.0, :math.sqrt(variance)), "#{column} の標準偏差"
      end)
    end

    test "列の順どおりに行列にする" do
      assert C.to_matrix([%{PRICE: 2.0, RM: 1.0}], [:RM, :PRICE]) == [[1.0, 2.0]]
    end
  end

  describe "実データ" do
    @tag :data
    test "100 件 15 列を主成分分析する" do
      %{columns: columns, x: x} = boston()
      m = C.to_matrix(x, columns)

      assert length(m) == 100
      assert length(columns) == 15
      assert Enum.all?(m, &(length(&1) == 15))
    end

    @tag :data
    test "寄与率の合計は 1 になる" do
      %{columns: columns, x: x} = boston()
      model = C.fit(C.to_matrix(x, columns), 15)

      assert close_to?(1.0, Enum.sum(model.explained_variance_ratio))
    end

    @tag :data
    test "Scholar の PCA と寄与率も主成分も一致する" do
      %{columns: columns, x: x} = boston()
      gaps = C.scholar_gaps(C.to_matrix(x, columns), 15)

      assert gaps.ratio < 1.0e-12
      assert gaps.component < 1.0e-9
    end

    @tag :data
    test "実行すると寄与率と主成分の解釈を表示する" do
      output = ExUnit.CaptureIO.capture_io(&C.run/0)

      assert String.split(output, "\n", trim: true) == [
               "データ件数: 100, 列数: 15",
               "寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581",
               "累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）",
               "第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328",
               "第 2 主成分で影響の大きい列: PRICE 0.444, CRIME_low 0.423, RM 0.405",
               "Scholar の PCA との差: 寄与率 6.9e-17, 主成分 1.1e-14"
             ]
    end
  end

  defp boston, do: C.load_boston(Path.join(Dataset.dir(), "Boston.csv"))

  defp dot(a, b), do: Enum.sum(Enum.zip_with(a, b, &(&1 * &2)))

  defp gap({a, b}), do: abs(a - b)

  defp max_gap(left, right) do
    Enum.zip(left, right)
    |> Enum.flat_map(fn {a, b} -> Enum.map(Enum.zip(a, b), &gap/1) end)
    |> Enum.max()
  end
end
