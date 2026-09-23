defmodule GettingStartedMl.Chapter14 do
  @moduledoc """
  第 14 章: K-means によるクラスタリング。

  点は数値のリスト、点の集まりはそのリストのリストで表す。中心が変わらなくなるまで
  「割り当て」と「中心の更新」を繰り返し、`Scholar.Cluster.KMeans` と SSE で比べる。

  Scholar の KMeans に初期中心を渡す口は無い（`:init` は `:k_means_plus_plus` と
  `:random` の 2 つだけ）ので、ほかの言語版と同じく同じ定義の SSE の大きさで比べる。

  標準化は第 13 章の `standardizer/2`・`standardize_all/2` を使い回す。
  """

  alias GettingStartedMl.{Chapter02, Chapter09, Chapter13, Dataset}
  alias Scholar.Cluster.KMeans

  # 更新の回数の既定の上限（scikit-learn の KMeans と同じ）。
  @default_max_iterations 300
  # 初期中心を試す回数の既定値（scikit-learn の KMeans の n_init と同じ）。
  @default_n_init 10
  # 区分を表す番号で、支出額ではない列。
  @non_spending [:Channel, :Region]
  # 初期中心の乱数のシード。
  @seed 0
  # エルボー法で試すクラスタ数。
  @cluster_counts 1..10
  # 特徴を読むために選んだクラスタ数。
  @n_clusters 5

  @doc "更新の回数の既定の上限。"
  def default_max_iterations, do: @default_max_iterations

  @doc "初期中心を試す回数の既定値。"
  def default_n_init, do: @default_n_init

  ## 割り当てと中心の更新

  @doc "2 点間の距離の 2 乗。"
  def squared_distance(a, b) do
    Enum.sum(Enum.zip_with(a, b, fn x, y -> (x - y) * (x - y) end))
  end

  @doc """
  各点を、最も近い中心のクラスタ番号に割り当てる。

  距離が同じなら先に並ぶ中心を選ぶように、厳密な < で畳む。
  """
  def assign_clusters(points, centers) do
    Enum.map(points, fn point -> nearest(point, centers) end)
  end

  @doc """
  クラスタごとに、割り当てられた点の平均を新しい中心にする。

  点が 1 つも割り当てられなかったクラスタは、前の中心をそのまま残す。
  """
  def update_centers(points, labels, previous) do
    members = points |> Enum.zip(labels) |> Enum.group_by(&elem(&1, 1), &elem(&1, 0))

    previous
    |> Enum.with_index()
    |> Enum.map(fn {center, k} -> mean_point(Map.get(members, k), center) end)
  end

  @doc "各点と、所属するクラスタの中心との距離の 2 乗の合計（誤差平方和）。"
  def sum_of_squared_errors(points, labels, centers) do
    points
    |> Enum.zip(labels)
    |> Enum.map(fn {point, label} -> squared_distance(point, Enum.at(centers, label)) end)
    |> Enum.sum()
  end

  @doc "中心が変わらなくなるか、更新の回数が上限に達するまで、割り当てと中心の更新を繰り返す。"
  def fit(points, initial_centers, max_iterations \\ @default_max_iterations) do
    centers = converge(points, initial_centers, max_iterations)
    labels = assign_clusters(points, centers)

    %{
      labels: labels,
      centers: centers,
      sse: sum_of_squared_errors(points, labels, centers)
    }
  end

  ## 初期中心と繰り返し

  @doc """
  シード付きの乱数で点を並べ替え、先頭から n_clusters 個を初期中心にする。

  `GettingStartedMl.Random` は `java.util.Random` と同じ線形合同法なので、
  Java 版・Scala 版・Clojure 版と同じ点を選ぶ。
  """
  def choose_initial_centers(points, n_clusters, seed) do
    0..(length(points) - 1)//1
    |> Enum.to_list()
    |> GettingStartedMl.Random.shuffle(seed)
    |> Enum.take(n_clusters)
    |> Enum.map(&Enum.at(points, &1))
  end

  @doc "初期中心の候補ごとにクラスタリングし、SSE が最小の結果を返す。"
  def best(points, initial_center_candidates) do
    initial_center_candidates
    |> Enum.map(&fit(points, &1))
    |> Enum.reduce(fn result, best -> if result.sse < best.sse, do: result, else: best end)
  end

  @doc "シードを 1 ずつずらして初期中心を n_init 通り選び、SSE が最小の結果を返す。"
  def fit_with_restarts(points, n_clusters, seed, n_init \\ @default_n_init) do
    best(
      points,
      Enum.map(0..(n_init - 1)//1, &choose_initial_centers(points, n_clusters, seed + &1))
    )
  end

  @doc """
  クラスタ数ごとに、初期中心を n_init 通り試した最小の SSE を返す。

  マップはキーの順を保たないので、`{クラスタ数, SSE}` の組のリストにする。
  """
  def sse_by_cluster_count(points, cluster_counts, seed, n_init \\ @default_n_init) do
    Enum.map(cluster_counts, fn n -> {n, fit_with_restarts(points, n, seed, n_init).sse} end)
  end

  ## Scholar の KMeans

  @doc "`Scholar.Cluster.KMeans` を k-means++ で学習し、中心を 1 行に 1 つずつ並べて返す。"
  def scholar_centers(points, n_clusters, seed, n_init \\ @default_n_init) do
    model =
      KMeans.fit(Nx.tensor(points, type: :f64),
        num_clusters: n_clusters,
        num_runs: n_init,
        key: Nx.Random.key(seed)
      )

    Nx.to_list(model.clusters)
  end

  @doc "Scholar が学習した中心に自作の割り当てをして、自作と同じ定義の SSE を求める。"
  def scholar_sse(points, n_clusters, seed, n_init \\ @default_n_init) do
    centers = scholar_centers(points, n_clusters, seed, n_init)

    sum_of_squared_errors(points, assign_clusters(points, centers), centers)
  end

  ## 卸売業者の顧客ごとの支出額

  @doc "Channel と Region を除いた支出額の列を読み込む。欠損値があれば失敗する。"
  def spending(table) do
    columns = Enum.reject(table.columns, &(&1 in @non_spending))

    %{
      columns: columns,
      x: Enum.map(table.rows, fn row -> Map.new(columns, &{&1, amount(row, &1)}) end)
    }
  end

  @doc "CSV を読み込んで支出額の列だけにする。"
  def load_spending(csv_file), do: csv_file |> Chapter02.load_table() |> spending()

  @doc "第 13 章の標準化（件数で割る標準偏差）で列ごとにそろえ、1 件を 1 つの点にする。"
  def standardize(x, columns) do
    x
    |> Chapter09.standardizer(columns)
    |> Chapter09.standardize_all(x)
    |> Chapter13.to_matrix(columns)
  end

  @doc "クラスタごとの件数と、元の単位での列ごとの平均を、件数の多い順に並べる。"
  def summarize_clusters(x, columns, labels) do
    x
    |> Enum.zip(labels)
    |> Enum.group_by(&elem(&1, 1), &elem(&1, 0))
    |> Enum.map(fn {cluster, rows} -> summary(cluster, rows, columns) end)
    |> Enum.sort_by(&{-&1.count, &1.cluster})
  end

  ## 実行

  @doc "卸売業者の顧客を支出額で K-means にかけ、エルボー法の SSE とクラスタごとの特徴を表示する。"
  def run do
    %{columns: columns, x: x} = load_spending(Path.join(Dataset.dir(), "Wholesale.csv"))
    points = standardize(x, columns)

    IO.puts("データ件数: #{length(x)}（支出額 #{length(columns)} 列）")
    IO.puts("クラスタ数ごとの SSE（初期中心 #{default_n_init()} 通りの最小値）:")
    IO.puts("クラスタ数\t自作\tScholar（k-means++）")

    Enum.each(sse_by_cluster_count(points, @cluster_counts, @seed), fn {n, sse} ->
      IO.puts("#{n}\t#{format(sse, 2)}\t#{format(scholar_sse(points, n, @seed), 2)}")
    end)

    IO.puts("")
    IO.puts("クラスタ数 #{@n_clusters} のクラスタごとの件数と平均支出額:")
    IO.puts(Enum.join(["クラスタ", "件数" | Enum.map(columns, &to_string/1)], "\t"))

    labels = fit_with_restarts(points, @n_clusters, @seed).labels

    Enum.each(summarize_clusters(x, columns, labels), fn summary ->
      IO.puts(format_summary(summary, columns))
    end)
  end

  defp format_summary(summary, columns) do
    Enum.join(
      [
        "#{summary.cluster}",
        # :io_lib.format は精度 0 を受け付けないので、整数に丸めてから文字列にする
        "#{summary.count}" | Enum.map(columns, &"#{round(summary.means[&1])}")
      ],
      "\t"
    )
  end

  defp summary(cluster, rows, columns) do
    %{
      cluster: cluster,
      count: length(rows),
      means: Map.new(columns, fn column -> {column, average(rows, column)} end)
    }
  end

  defp average(rows, column) do
    Enum.sum(Enum.map(rows, &Map.fetch!(&1, column))) / length(rows)
  end

  defp amount(row, column) do
    case Chapter02.number(row, column) do
      nil -> raise ArgumentError, "空欄があります: #{column}"
      value -> value
    end
  end

  # 最も近い中心の番号。距離が同じなら先に並ぶ中心を選ぶ。
  defp nearest(point, centers) do
    {label, _distance} =
      centers
      |> Enum.with_index()
      |> Enum.reduce({0, :infinity}, fn {center, k}, {label, best} ->
        distance = squared_distance(point, center)
        if distance < best, do: {k, distance}, else: {label, best}
      end)

    label
  end

  defp mean_point(nil, previous), do: previous

  defp mean_point(assigned, _previous) do
    assigned
    |> Enum.zip_with(fn values -> Enum.sum(values) / length(assigned) end)
  end

  # 中心が動かなくなるまで繰り返す。上限に達したらそこで打ち切る。
  defp converge(_points, centers, 0), do: centers

  defp converge(points, centers, remaining) do
    next = update_centers(points, assign_clusters(points, centers), centers)

    if next == centers, do: centers, else: converge(points, next, remaining - 1)
  end

  defp format(value, digits), do: ~c"~.#{digits}f" |> :io_lib.format([value]) |> to_string()
end
