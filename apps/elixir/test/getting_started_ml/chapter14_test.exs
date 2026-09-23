defmodule GettingStartedMl.Chapter14Test do
  use ExUnit.Case, async: true

  alias GettingStartedMl.Chapter14, as: C
  alias GettingStartedMl.Dataset
  alias Scholar.Cluster.KMeans

  # 2 次元に離れた 2 つの群れ。
  defp two_groups do
    [[0.0, 0.0], [1.0, 0.0], [0.0, 1.0], [10.0, 10.0], [11.0, 10.0], [10.0, 11.0]]
  end

  # 1 次元に 3 組の点。最適な 3 分割の SSE は 0.5 × 3 = 1.5。
  defp three_pairs, do: [[0.0], [1.0], [10.0], [11.0], [20.0], [21.0]]

  defp spending_table do
    %{
      columns: [:Channel, :Region, :Fresh, :Milk],
      rows: [
        %{Channel: "1", Region: "3", Fresh: "100", Milk: "200"},
        %{Channel: "2", Region: "3", Fresh: "300", Milk: "400"}
      ]
    }
  end

  describe "距離と割り当て" do
    test "2 点間の距離の 2 乗を求める" do
      assert C.squared_distance([0.0, 0.0], [3.0, 4.0]) == 25.0
    end

    test "1 次元の点を最も近い中心に割り当てる" do
      assert C.assign_clusters([[0.0], [1.0], [10.0], [11.0]], [[0.5], [10.5]]) == [0, 0, 1, 1]
    end

    test "2 次元の点をユークリッド距離で割り当てる" do
      assert C.assign_clusters(two_groups(), [[0.0, 0.0], [10.0, 10.0]]) == [0, 0, 0, 1, 1, 1]
    end

    test "距離が同じなら先に並ぶ中心を選ぶ" do
      assert C.assign_clusters([[1.0]], [[0.0], [2.0]]) == [0]
    end
  end

  describe "中心の更新" do
    test "平均が新しい中心になる" do
      assert C.update_centers([[0.0], [1.0], [10.0], [11.0]], [0, 0, 1, 1], [[0.0], [10.0]]) ==
               [[0.5], [10.5]]
    end

    test "点が 1 つも割り当てられなかったクラスタは中心を変えない" do
      assert C.update_centers([[0.0], [1.0]], [0, 0], [[0.0], [99.0]]) == [[0.5], [99.0]]
    end
  end

  describe "SSE" do
    test "各点と中心の距離の 2 乗を合計する" do
      assert C.sum_of_squared_errors([[0.0], [2.0]], [0, 0], [[1.0]]) == 2.0
    end
  end

  describe "繰り返し" do
    test "中心が変わらなくなるまで繰り返す" do
      result = C.fit(two_groups(), [[0.0, 0.0], [10.0, 10.0]])

      assert result.labels == [0, 0, 0, 1, 1, 1]
      assert_in_delta result.sse, 8 / 3, 1.0e-9
    end

    test "最大反復回数に達したら収束していなくても打ち切る" do
      once = C.fit(three_pairs(), [[0.0], [2.0]], 1)
      none = C.fit(three_pairs(), [[0.0], [2.0]], 0)

      assert once.centers == [[0.5], [15.5]]
      assert none.centers == [[0.0], [2.0]]
      assert none.sse > once.sse
    end

    test "初期中心によっては局所解に陥る" do
      assert_in_delta C.fit(three_pairs(), [[0.0], [1.0], [10.0]]).sse, 101.0, 1.0e-9
    end

    test "複数の初期中心の候補のうち SSE が最小の結果を返す" do
      result = C.best(three_pairs(), [[[0.0], [1.0], [10.0]], [[0.0], [10.0], [20.0]]])

      assert_in_delta result.sse, 1.5, 1.0e-9
      assert result.centers == [[0.5], [10.5], [20.5]]
    end
  end

  describe "初期中心" do
    test "シードで選んだ点そのものを初期中心にする" do
      centers = C.choose_initial_centers(three_pairs(), 3, 0)

      assert length(centers) == 3
      assert Enum.all?(centers, &(&1 in three_pairs()))
      assert Enum.uniq(centers) == centers
    end

    test "同じシードなら同じ初期中心を選ぶ" do
      assert C.choose_initial_centers(three_pairs(), 3, 7) ==
               C.choose_initial_centers(three_pairs(), 3, 7)
    end
  end

  describe "エルボー法" do
    test "クラスタ数ごとの SSE をクラスタ数の順に返す" do
      result = C.sse_by_cluster_count(three_pairs(), 1..3, 0)

      assert Enum.map(result, &elem(&1, 0)) == [1, 2, 3]
      assert_in_delta elem(Enum.at(result, 2), 1), 1.5, 1.0e-9
    end

    test "クラスタ数を増やすほど SSE が小さくなる" do
      sse = Enum.map(C.sse_by_cluster_count(three_pairs(), 1..4, 0), &elem(&1, 1))

      assert Enum.zip(sse, tl(sse)) |> Enum.all?(fn {a, b} -> b < a end)
    end
  end

  describe "Scholar の KMeans" do
    test "初期中心そのものは渡せない" do
      # :init に渡せるのは :k_means_plus_plus と :random の 2 つだけ
      assert_raise NimbleOptions.ValidationError, fn ->
        KMeans.fit(Nx.tensor(two_groups(), type: :f64),
          num_clusters: 2,
          init: [[0.0, 0.0], [10.0, 10.0]]
        )
      end
    end

    test "離れた 2 つの群れなら自作と同じ中心にたどり着く" do
      theirs = Enum.sort(C.scholar_centers(two_groups(), 2, 0))
      mine = Enum.sort(C.fit_with_restarts(two_groups(), 2, 0).centers)

      assert Enum.zip(mine, theirs)
             |> Enum.all?(fn {a, b} -> C.squared_distance(a, b) < 1.0e-9 end)
    end

    test "同じ定義の SSE で比べる" do
      assert_in_delta C.scholar_sse(two_groups(), 2, 0), 8 / 3, 1.0e-9
    end
  end

  describe "支出額の読み込み" do
    test "Channel と Region を除いた列を読み込む" do
      %{columns: columns, x: x} = C.spending(spending_table())

      assert columns == [:Fresh, :Milk]
      assert x == [%{Fresh: 100.0, Milk: 200.0}, %{Fresh: 300.0, Milk: 400.0}]
    end

    test "空欄があれば失敗する" do
      table = %{columns: [:Fresh], rows: [%{Fresh: ""}]}

      assert_raise ArgumentError, fn -> C.spending(table) end
    end

    test "標準化すると列ごとの平均が 0・標準偏差が 1 になる" do
      %{columns: columns, x: x} = C.spending(spending_table())
      points = C.standardize(x, columns)

      assert points == [[-1.0, -1.0], [1.0, 1.0]]
    end
  end

  describe "クラスタごとの要約" do
    test "件数の多い順に、元の単位での平均を並べる" do
      x = [%{Fresh: 10.0}, %{Fresh: 20.0}, %{Fresh: 100.0}]

      assert C.summarize_clusters(x, [:Fresh], [1, 1, 0]) == [
               %{cluster: 1, count: 2, means: %{Fresh: 15.0}},
               %{cluster: 0, count: 1, means: %{Fresh: 100.0}}
             ]
    end

    test "件数が同じならクラスタ番号の昇順に並べる" do
      x = [%{Fresh: 10.0}, %{Fresh: 20.0}]

      assert Enum.map(C.summarize_clusters(x, [:Fresh], [1, 0]), & &1.cluster) == [0, 1]
    end
  end

  describe "実データ" do
    @tag :data
    test "440 件 6 列を読み込む" do
      %{columns: columns, x: x} = wholesale()

      assert length(x) == 440
      assert columns == [:Fresh, :Milk, :Grocery, :Frozen, :Detergents_Paper, :Delicassen]
    end

    @tag :data
    test "標準化したデータのクラスタ数 1 の SSE は件数と列数の積になる" do
      # 件数で割る標準偏差で標準化したので、列ごとの分散は 1 になる
      %{columns: columns, x: x} = wholesale()
      [{1, sse}] = C.sse_by_cluster_count(C.standardize(x, columns), [1], 0)

      assert_in_delta sse, 440.0 * 6, 1.0e-6
    end

    @tag :data
    test "クラスタ数を増やすほど SSE が小さくなる" do
      %{columns: columns, x: x} = wholesale()
      sse = Enum.map(C.sse_by_cluster_count(C.standardize(x, columns), 1..10, 0), &elem(&1, 1))

      assert Enum.zip(sse, tl(sse)) |> Enum.all?(fn {a, b} -> b < a end)
    end

    # Scholar の KMeans を 10 通りのクラスタ数で学習するので、既定の 60 秒では足りない
    @tag :data
    @tag timeout: 600_000
    test "実行するとエルボー法の SSE とクラスタごとの特徴を表示する" do
      output = ExUnit.CaptureIO.capture_io(&C.run/0)

      assert String.split(output, "\n", trim: true) == [
               "データ件数: 440（支出額 6 列）",
               "クラスタ数ごとの SSE（初期中心 10 通りの最小値）:",
               "クラスタ数\t自作\tScholar（k-means++）",
               "1\t2640.00\t2640.00",
               "2\t1954.18\t1954.73",
               "3\t1614.52\t1632.87",
               "4\t1334.36\t1329.41",
               "5\t1085.27\t1075.51",
               "6\t947.20\t923.05",
               "7\t888.22\t829.72",
               "8\t775.24\t753.01",
               "9\t690.81\t676.57",
               "10\t618.17\t602.40",
               "クラスタ数 5 のクラスタごとの件数と平均支出額:",
               "クラスタ\t件数\tFresh\tMilk\tGrocery\tFrozen\tDetergents_Paper\tDelicassen",
               "2\t265\t8909\t2967\t3804\t2248\t989\t962",
               "1\t96\t5509\t10556\t16478\t1420\t7199\t1659",
               "0\t65\t31117\t4260\t5374\t7225\t849\t2286",
               "3\t10\t15965\t34709\t48537\t3055\t24875\t2943",
               "4\t4\t52022\t31696\t18491\t29826\t2699\t19656"
             ]
    end
  end

  defp wholesale, do: C.load_spending(Path.join(Dataset.dir(), "Wholesale.csv"))
end
