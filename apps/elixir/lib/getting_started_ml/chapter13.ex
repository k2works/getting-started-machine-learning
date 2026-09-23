defmodule GettingStartedMl.Chapter13 do
  @moduledoc """
  第 13 章: 主成分分析による次元削減。

  中心化・分散共分散行列・並べ替え・符号・射影・寄与率を自作し、固有値分解だけを
  `Nx.LinAlg.eigh` に任せる。そのうえで `Scholar.Decomposition.PCA` と突き合わせる。

  Scholar の PCA は `explained_variance_ratio` を持っているので、寄与率まで含めて
  全面的に比べられる。ライブラリに主成分分析が無かった Java 版・Scala 版・Clojure 版
  （Tribuo）や、寄与率の無かった Ruby 版（Rumale）と違うところ。

  行列は「長さのそろったリストのリスト」で表し、Nx のテンソルにするのは
  固有値分解と Scholar に渡す境界だけにする。テンソルは必ず `type: :f64` を明示する。
  """

  alias GettingStartedMl.{Chapter02, Chapter09, Dataset}
  alias Scholar.Decomposition.PCA

  @doc "カテゴリ値の列。"
  def category, do: :CRIME

  # 何割のばらつきを説明できれば十分とみなすか。
  @threshold 0.8
  # 主成分ごとに表示する列の数。
  @top_k 3
  # 意味を読む主成分の数。
  @components_to_explain 2

  ## 中心化と分散共分散行列

  @doc "列ごとの平均を返す。"
  def column_means(m) do
    m |> transpose() |> Enum.map(&(Enum.sum(&1) / length(&1)))
  end

  @doc "各列から平均を引く（中心化）。"
  def center(m, means), do: Enum.map(m, &Enum.zip_with(&1, means, fn v, mean -> v - mean end))

  @doc "列ごとの分散と、2 列ずつの共分散を並べた行列。件数から 1 を引いた数で割る。"
  def covariance_matrix(m) when length(m) < 2 do
    raise ArgumentError, "主成分分析には 2 件以上のデータが必要です（#{length(m)} 件）"
  end

  def covariance_matrix(m) do
    centered = center(m, column_means(m))
    n = length(m)

    centered
    |> transpose()
    |> Enum.map(fn row ->
      Enum.map(transpose(centered), fn column -> dot(row, column) / (n - 1) end)
    end)
  end

  ## 固有値分解

  @doc """
  対称行列を固有値分解し、固有値と固有ベクトルを大きい順に返す。

  `Nx.LinAlg.eigh` は上三角だけを見るので、対称でない行列を渡しても黙って
  別の行列の答えを返す。対称かどうかはここで確かめて失敗させる。
  """
  def eigen_decomposition(m) do
    check_symmetric(m)
    {values, vectors} = Nx.LinAlg.eigh(Nx.tensor(m, type: :f64))

    %{
      values: Nx.to_flat_list(values),
      # 固有ベクトルは列に並ぶので、転置して 1 行に 1 つずつにする
      vectors: vectors |> Nx.transpose() |> Nx.to_list()
    }
  end

  @doc "固有ベクトルは符号が逆でも同じ向きを表すので、絶対値が最大の要素が正になるようにそろえる。"
  def normalize_signs(components), do: Enum.map(components, &normalize_sign/1)

  # 絶対値が最大の要素。同じなら前の要素を残すように、厳密な > で畳む。
  defp largest(row), do: Enum.reduce(row, &if(abs(&1) > abs(&2), do: &1, else: &2))

  defp normalize_sign(row) do
    if largest(row) < 0.0, do: Enum.map(row, &(-&1)), else: row
  end

  @doc "分散共分散行列を固有値分解し、寄与率の大きい順に n_components 個の主成分を求める。"
  def fit(m, n_components) do
    %{values: values, vectors: vectors} = m |> covariance_matrix() |> eigen_decomposition()
    check_n_components(n_components, length(values))
    total = Enum.sum(values)
    variances = Enum.take(values, n_components)

    %{
      mean: column_means(m),
      components: vectors |> Enum.take(n_components) |> normalize_signs(),
      explained_variance: variances,
      explained_variance_ratio: Enum.map(variances, &(&1 / total))
    }
  end

  @doc "平均を引いてから、データを主成分の向きに射影する。"
  def transform(%{mean: mean, components: components}, m) do
    Enum.map(center(m, mean), fn row -> Enum.map(components, &dot(row, &1)) end)
  end

  ## 主成分の数と解釈

  @doc "累積寄与率がしきい値に届くまでの主成分の数。届かなければすべての主成分の数。"
  def components_needed(ratios, threshold) do
    cumulative = Enum.scan(ratios, &(&1 + &2))

    case Enum.find_index(cumulative, &(&1 >= threshold)) do
      nil -> length(ratios)
      index -> index + 1
    end
  end

  @doc """
  主成分の係数の絶対値が大きい順に、列名と係数を k 個返す。

  `Enum.sort_by/3` は安定なので、絶対値が同じなら元の列の順が残る。
  """
  def top_loadings(component, columns, k) do
    columns
    |> Enum.zip(component)
    |> Enum.map(fn {column, value} -> %{column: column, value: value} end)
    |> Enum.sort_by(&abs(&1.value), :desc)
    |> Enum.take(k)
  end

  ## Scholar の PCA

  @doc "`Scholar.Decomposition.PCA` の主成分を、符号をそろえずにそのまま返す。"
  def scholar_components(m, n_components) do
    Nx.to_list(PCA.fit(Nx.tensor(m, type: :f64), num_components: n_components).components)
  end

  @doc "`Scholar.Decomposition.PCA` で学習し、自作と同じ形のモデルにする。"
  def scholar_fit(m, n_components) do
    model = PCA.fit(Nx.tensor(m, type: :f64), num_components: n_components)

    %{
      mean: Nx.to_flat_list(model.mean),
      components: model.components |> Nx.to_list() |> normalize_signs(),
      explained_variance: Nx.to_flat_list(model.explained_variance),
      explained_variance_ratio: Nx.to_flat_list(model.explained_variance_ratio)
    }
  end

  @doc "自作と Scholar の、寄与率と主成分の差の絶対値の最大。"
  def scholar_gaps(m, n_components) do
    mine = fit(m, n_components)
    theirs = scholar_fit(m, n_components)

    %{
      ratio: max_gap([mine.explained_variance_ratio], [theirs.explained_variance_ratio]),
      component: max_gap(mine.components, theirs.components)
    }
  end

  ## Boston を前処理する

  @doc "CRIME をダミー変数にし、欠損値を列の平均値で補完してから、すべての列を標準化する。"
  def standardize_table(table) do
    crimes = Enum.map(table.rows, &Chapter02.text(&1, category()))
    encoded = Chapter09.encode(table, category(), Chapter09.categories(crimes))
    means = Chapter02.column_means(encoded.rows, encoded.columns)
    filled = Chapter02.fill_missing(encoded.rows, encoded.columns, means)

    %{
      columns: encoded.columns,
      x: Chapter09.standardize_all(Chapter09.standardizer(filled, encoded.columns), filled)
    }
  end

  @doc "CSV を読み込んで前処理する。"
  def load_boston(csv_file), do: csv_file |> Chapter02.load_table() |> standardize_table()

  @doc """
  特徴量のリストを、1 件を 1 行とする行列にする。

  マップはキーの順を保たないので、列の順は必ずリストで持ち回る。
  """
  def to_matrix(x, columns) do
    Enum.map(x, fn features -> Enum.map(columns, &Map.fetch!(features, &1)) end)
  end

  ## 実行

  @doc "ボストンの住宅価格の 15 列を主成分分析し、寄与率と主成分の意味を表示する。"
  def run do
    %{columns: columns, x: x} = load_boston(Path.join(Dataset.dir(), "Boston.csv"))
    m = to_matrix(x, columns)
    model = fit(m, length(columns))
    needed = components_needed(model.explained_variance_ratio, @threshold)
    cumulative = model.explained_variance_ratio |> Enum.take(needed) |> Enum.sum()
    gaps = scholar_gaps(m, length(columns))

    IO.puts("データ件数: #{length(m)}, 列数: #{length(columns)}")
    IO.puts("寄与率: #{format_ratios(model.explained_variance_ratio, needed)}")
    IO.puts("累積寄与率が #{@threshold} に届く主成分の数: #{needed}（累積寄与率 #{format(cumulative, 4)}）")

    Enum.each(0..(@components_to_explain - 1)//1, fn index ->
      loadings = top_loadings(Enum.at(model.components, index), columns, @top_k)
      IO.puts("第 #{index + 1} 主成分で影響の大きい列: #{format_loadings(loadings)}")
    end)

    IO.puts("Scholar の PCA との差: 寄与率 #{exponent(gaps.ratio)}, 主成分 #{exponent(gaps.component)}")
  end

  defp format_ratios(ratios, count) do
    ratios
    |> Enum.take(count)
    |> Enum.with_index(1)
    |> Enum.map_join(", ", fn {ratio, index} -> "PC#{index} #{format(ratio, 4)}" end)
  end

  defp format_loadings(loadings) do
    Enum.map_join(loadings, ", ", fn %{column: column, value: value} ->
      "#{column} #{format(value, 3)}"
    end)
  end

  defp check_symmetric(m) do
    size = length(m)

    if Enum.any?(m, &(length(&1) != size)) do
      raise ArgumentError, "正方行列ではありません: #{size} 行"
    end

    if m != transpose(m) do
      raise ArgumentError, "固有値分解できません（対称行列ではありません）"
    end
  end

  defp check_n_components(n_components, size) do
    unless n_components >= 1 and n_components <= size do
      raise ArgumentError, "主成分の数は 1 以上 #{size} 以下にしてください: #{n_components}"
    end
  end

  defp transpose(m), do: m |> Enum.zip_with(& &1)

  defp dot(a, b), do: Enum.sum(Enum.zip_with(a, b, &(&1 * &2)))

  defp max_gap(left, right) do
    Enum.zip(left, right)
    |> Enum.flat_map(fn {a, b} -> Enum.zip_with(a, b, fn x, y -> abs(x - y) end) end)
    |> Enum.max()
  end

  defp format(value, digits), do: ~c"~.#{digits}f" |> :io_lib.format([value]) |> to_string()

  defp exponent(value), do: ~c"~.2e" |> :io_lib.format([value]) |> to_string()
end
