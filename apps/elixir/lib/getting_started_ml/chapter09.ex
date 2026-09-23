defmodule GettingStartedMl.Chapter09 do
  @moduledoc """
  第 9 章: 特徴量エンジニアリング。

  ダミー変数・標準化・多項式特徴量・外れ値・表の結合。標準化は
  `Scholar.Preprocessing.StandardScaler` と突き合わせる。

  Boston のデータは特徴量が 14 列あるので、列の順はマップの鍵ではなく
  `:columns` のリストで持ち回る（Elixir のマップはキーの順を保たない）。
  """

  alias GettingStartedMl.{Chapter02, Csv, Dataset}
  alias Scholar.Preprocessing.StandardScaler

  @target :PRICE
  @category :CRIME
  @join_key :weather_id
  @default_k 1.5
  @cp932 :"VENDORS/MICSFT/WINDOWS/CP932"
  @columns_to_expand [:RM, :LSTAT, :PTRATIO]
  @squares [:"RM^2", :"LSTAT^2", :"PTRATIO^2"]
  @zero_tolerance 1.0e-9

  @doc "ボストンの住宅価格のデータの正解の列。"
  def target, do: @target

  @doc "カテゴリ値の列。"
  def category, do: @category

  @doc "多項式特徴量を作る元の列。"
  def columns_to_expand, do: @columns_to_expand

  ## カテゴリ値をダミー変数にする

  @doc """
  欠損値（空欄）を除いたカテゴリを辞書順に並べ、先頭を除いて返す。

  pandas の `get_dummies(drop_first=True)` と同じで、3 つのカテゴリなら 2 列で足りる。
  """
  def categories(values) do
    values
    |> Enum.reject(&(String.trim(&1) == ""))
    |> Enum.uniq()
    |> Enum.sort()
    |> Enum.drop(1)
  end

  @doc """
  列を取り除き、カテゴリごとに「列名_カテゴリ」の列を末尾に加える。

  値が一致すれば `"1"`、それ以外は `"0"`。セルを文字列のままにしておくと、
  第 2 章の `split_features_and_target/2`・`column_means/2`・`fill_missing/3` を
  ダミー変数の列にもそのまま使える。
  """
  def encode(%{columns: columns, rows: rows}, column, categories) do
    dummy_columns = Enum.map(categories, &dummy_column(column, &1))

    %{
      columns: Enum.reject(columns, &(&1 == column)) ++ dummy_columns,
      rows:
        Enum.map(rows, fn row ->
          value = Map.get(row, column)

          categories
          |> Enum.zip(dummy_columns)
          |> Enum.reduce(Map.delete(row, column), fn {c, name}, acc ->
            Map.put(acc, name, if(c == value, do: "1", else: "0"))
          end)
        end)
    }
  end

  ## 標準化

  @doc """
  列ごとの平均と、件数で割る標準偏差を求める。

  すべて同じ値の列は標準偏差を 1 にして、標準化した値が 0 になるようにする。
  """
  def standardizer(x, columns)

  def standardizer([], _columns), do: raise(ArgumentError, "特徴量が 1 件もありません")

  def standardizer(x, columns) do
    stats =
      Map.new(columns, fn column ->
        values = Enum.map(x, &Map.fetch!(&1, column))
        mean = Enum.sum(values) / length(values)
        variance = Enum.sum(Enum.map(values, &((&1 - mean) * (&1 - mean)))) / length(values)
        std = :math.sqrt(variance)
        {column, {mean, if(std == 0.0, do: 1.0, else: std)}}
      end)

    %{
      columns: columns,
      means: Map.new(stats, fn {column, {mean, _}} -> {column, mean} end),
      stds: Map.new(stats, fn {column, {_, std}} -> {column, std} end)
    }
  end

  @doc "1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。"
  def standardize(%{means: means, stds: stds}, features) do
    Map.new(features, fn {column, value} ->
      case Map.fetch(means, column) do
        {:ok, mean} -> {column, (value - mean) / Map.fetch!(stds, column)}
        :error -> {column, value}
      end
    end)
  end

  @doc "特徴量のリストを標準化する。"
  def standardize_all(std, x), do: Enum.map(x, &standardize(std, &1))

  @doc """
  Scholar の `StandardScaler` で、訓練データの値から平均と標準偏差を求めて別の値を標準化する。

  テンソルは `type: :f64` を明示する。既定の f32 のままでは自作の値と 7 桁目からずれる。
  """
  def scholar_standardize(train, values) do
    scaler = train |> to_column() |> StandardScaler.fit()

    scaler
    |> StandardScaler.transform(to_column(values))
    |> Nx.to_flat_list()
  end

  ## 多項式特徴量

  @doc "重複を許して 2 つの列を選ぶ組を、scikit-learn の `PolynomialFeatures` と同じ順に並べる。"
  def pairs_with_replacement(columns) do
    indexed = Enum.with_index(columns)

    for {left, i} <- indexed, {right, j} <- indexed, j >= i, do: {left, right}
  end

  @doc ~S(項の名前。scikit-learn の get_feature_names_out と同じ形（:"RM^2"・:"RM LSTAT"）にする。)
  def term_name({left, left}), do: String.to_atom("#{left}^2")
  def term_name({left, right}), do: String.to_atom("#{left} #{right}")

  @doc "元の列の後ろに、2 乗の項と交互作用の項の列を並べた列名を返す。"
  def expanded_columns(columns) do
    columns ++ Enum.map(pairs_with_replacement(columns), &term_name/1)
  end

  @doc "指定した列と、その 2 乗の項・交互作用の項だけを持つ特徴量にする。"
  def expand(x, columns) do
    pairs = pairs_with_replacement(columns)

    Enum.map(x, fn features ->
      terms =
        Map.new(pairs, fn {left, right} = pair ->
          {term_name(pair), Map.fetch!(features, left) * Map.fetch!(features, right)}
        end)

      Map.merge(Map.take(features, columns), terms)
    end)
  end

  @doc "指定した列だけを選ぶ。"
  def select_columns(x, columns), do: Enum.map(x, &Map.take(&1, columns))

  ## 外れ値

  @doc """
  分位数を求める。

  位置が値の間にあれば前後の値から線形補間する（pandas の `quantile` の既定と同じ）。
  """
  def quantile(values, q) do
    sorted = Enum.sort(values)
    position = (length(sorted) - 1) * q
    lower = trunc(Float.floor(position))
    upper = trunc(Float.ceil(position))
    at_lower = Enum.at(sorted, lower)

    at_lower + (Enum.at(sorted, upper) - at_lower) * (position - lower)
  end

  @doc "第 1 四分位数から IQR の k 倍より小さい値と、第 3 四分位数から k 倍より大きい値を外れ値とする。"
  def iqr_outliers(values, k \\ @default_k) do
    q1 = quantile(values, 0.25)
    q3 = quantile(values, 0.75)
    iqr = q3 - q1

    Enum.map(values, &(&1 < q1 - k * iqr or &1 > q3 + k * iqr))
  end

  @doc """
  訓練データから、正解の値が外れ値の行を取り除く。

  テストデータは「本番で来るデータ」の代わりなので、外れ値を含んでいてもそのまま残す。
  """
  def remove_target_outliers(split) do
    kept =
      [split.x_train, split.t_train, iqr_outliers(split.t_train)]
      |> Enum.zip()
      |> Enum.reject(fn {_features, _value, outlier?} -> outlier? end)

    %{
      split
      | x_train: Enum.map(kept, &elem(&1, 0)),
        t_train: Enum.map(kept, &elem(&1, 1))
    }
  end

  ## 区切り文字と文字コードを指定した読み込みと、表の結合

  @doc "文字列を CP932（Shift_JIS）のバイト列にする。テストでファイルを用意するために使う。"
  def to_cp932(text), do: Codepagex.from_string!(text, @cp932)

  @doc """
  文字コードと区切り文字を指定して読み込み、1 行目を列名にする。

  `:utf8` は何も変換せず、そのままのバイト列を読む。Shift_JIS のファイルを
  `:utf8` で読んでも **例外にはならず**、UTF-8 として不正な文字列になる。
  """
  def load_delimited(path, encoding, separator) do
    {columns, rows} = path |> File.read!() |> decode(encoding) |> Csv.parse_table(separator)

    %{columns: columns, rows: rows}
  end

  @doc """
  天気 ID をキーにしたマップを引いて、天気の列を加える（内部結合）。

  天気の表に無い ID の行は残さない。`Map.new/2` はキーが重複すると静かに上書きするので、
  件数を比べて一意でないことに気付けるようにする。
  """
  def join_weather(bike, weather) do
    by_id = Map.new(weather.rows, &{Map.fetch!(&1, @join_key), &1})
    added = Enum.reject(weather.columns, &(&1 == @join_key))

    if map_size(by_id) != length(weather.rows) do
      raise ArgumentError, "#{@join_key} が一意ではありません"
    end

    %{
      columns: bike.columns ++ added,
      rows:
        Enum.flat_map(bike.rows, fn row ->
          case Map.fetch(by_id, Map.fetch!(row, @join_key)) do
            {:ok, found} -> [Map.merge(row, Map.take(found, added))]
            :error -> []
          end
        end)
    }
  end

  @doc "天気ごとの平均利用者数を、多い順に並べて返す。"
  def mean_count_by_weather(joined) do
    joined.rows
    |> Enum.group_by(& &1.weather)
    |> Enum.map(fn {weather, rows} ->
      {weather, Enum.sum(Enum.map(rows, &Chapter02.number(&1, :cnt))) / length(rows)}
    end)
    |> Enum.sort_by(&elem(&1, 1), :desc)
  end

  ## 線形回帰と決定係数

  @doc """
  先頭に 1 の列を加えた計画行列 X で、正規方程式 XᵀX β = Xᵀt を解く。

  `Nx.LinAlg.solve/2` は列が独立でなくても例外を投げず、`NaN` や `Infinity` を返すので、
  値を確かめてから失敗させる。
  """
  def linear_fit(rows, t) do
    x = Nx.tensor(Enum.map(rows, &[1.0 | &1]), type: :f64)
    transposed = Nx.transpose(x)

    beta =
      transposed
      |> Nx.dot(x)
      |> Nx.LinAlg.solve(Nx.dot(transposed, Nx.tensor(t, type: :f64)))
      |> Nx.to_flat_list()

    unless Enum.all?(beta, &is_float/1) do
      raise ArgumentError, "特徴量の列が互いに独立でないため、正規方程式を解けません"
    end

    [intercept | weights] = beta
    %{intercept: intercept, weights: weights}
  end

  @doc "行ごとに予測する。"
  def linear_predict(%{intercept: intercept, weights: weights}, rows) do
    Enum.map(rows, fn row ->
      intercept + Enum.sum(Enum.map(Enum.zip(weights, row), fn {w, v} -> w * v end))
    end)
  end

  @doc "決定係数。1 から、残差の 2 乗和を平均との差の 2 乗和で割った値を引く。"
  def r_squared(actual, predicted) do
    mean = Enum.sum(actual) / length(actual)

    residual =
      actual |> Enum.zip(predicted) |> Enum.map(fn {a, p} -> (a - p) * (a - p) end) |> Enum.sum()

    total = actual |> Enum.map(&((&1 - mean) * (&1 - mean))) |> Enum.sum()

    1.0 - residual / total
  end

  ## ボストンの住宅価格

  @doc "CRIME をダミー変数にし、分割してから訓練データの平均値で両方の欠損値を補完する。"
  def prepare_boston(path, test_size, seed) do
    table = Chapter02.load_table(path)
    crimes = Enum.map(table.rows, &Chapter02.text(&1, @category))
    encoded = encode(table, @category, categories(crimes))

    %{columns: columns, rows: rows, labels: labels} =
      Chapter02.split_features_and_target(encoded, @target)

    prices = Enum.map(labels, &Chapter02.number(%{@target => &1}, @target))
    split = Chapter02.split_train_test(rows, prices, test_size, seed)
    means = Chapter02.column_means(split.x_train, columns)

    split
    |> Map.put(:columns, columns)
    |> Map.put(:x_train, Chapter02.fill_missing(split.x_train, columns, means))
    |> Map.put(:x_test, Chapter02.fill_missing(split.x_test, columns, means))
  end

  @doc """
  列から多項式特徴量を作って `terms` の項を選び、標準化してから線形回帰で学習し、決定係数を求める。

  平均と標準偏差は訓練データだけで求め、テストデータにも同じ値を使う。
  """
  def score_feature_set(split, columns, terms) do
    train = split.x_train |> expand(columns) |> select_columns(terms)
    test = split.x_test |> expand(columns) |> select_columns(terms)
    std = standardizer(train, terms)
    x_train = std |> standardize_all(train) |> to_rows(terms)
    x_test = std |> standardize_all(test) |> to_rows(terms)
    model = linear_fit(x_train, split.t_train)

    %{
      train: r_squared(split.t_train, linear_predict(model, x_train)),
      test: r_squared(split.t_test, linear_predict(model, x_test))
    }
  end

  @doc "特徴量の組の名前と、使う項。表示する順に並べる。"
  def feature_sets do
    [
      {"元の特徴量", @columns_to_expand},
      {"2 乗の項を追加", @columns_to_expand ++ @squares},
      {"交互作用の項も追加", expanded_columns(@columns_to_expand)}
    ]
  end

  @doc "ボストンの住宅価格で特徴量エンジニアリングを試し、自転車の利用者数に天気を結合して集計する。"
  def run do
    dir = Dataset.dir()
    split = prepare_boston(Path.join(dir, "Boston.csv"), 0.3, 0)

    check =
      split.x_train
      |> standardizer(split.columns)
      |> standardize_all(split.x_train)
      |> standardizer(split.columns)

    IO.puts("訓練データ: #{length(split.x_train)} 件, テストデータ: #{length(split.x_test)} 件")
    IO.puts("特徴量の列: " <> Enum.map_join(split.columns, ", ", &to_string/1))

    IO.puts(
      "標準化した訓練データの RM: 平均 #{format_number(check.means[:RM], 2)}" <>
        ", 標準偏差 #{format_number(check.stds[:RM], 2)}"
    )

    IO.puts("決定係数:")

    for {name, terms} <- feature_sets() do
      scores = score_feature_set(split, @columns_to_expand, terms)
      IO.puts("  #{name}（#{length(terms)} 列）: #{format_scores(scores)}")
    end

    IO.puts("訓練データの PRICE の外れ値: #{Enum.count(iqr_outliers(split.t_train), & &1)} 件")

    without_outliers =
      score_feature_set(
        remove_target_outliers(split),
        @columns_to_expand,
        @columns_to_expand ++ @squares
      )

    IO.puts("  外れ値を除いて 2 乗の項を追加: #{format_scores(without_outliers)}")

    means =
      dir
      |> Path.join("bike.tsv")
      |> load_delimited(:utf8, "\t")
      |> join_weather(load_delimited(Path.join(dir, "weather.csv"), :cp932, ","))
      |> mean_count_by_weather()

    IO.puts(
      "天気ごとの平均利用者数: " <>
        Enum.map_join(means, ", ", fn {weather, mean} ->
          "#{weather}=#{format_number(mean, 1)}"
        end)
    )
  end

  defp dummy_column(column, category_value), do: String.to_atom("#{column}_#{category_value}")

  defp to_column(values), do: Nx.tensor(Enum.map(values, &[&1 * 1.0]), type: :f64)

  defp decode(binary, :utf8), do: binary

  defp decode(binary, :cp932) do
    case Codepagex.to_string(binary, @cp932) do
      {:ok, text} -> text
      {:error, reason} -> raise ArgumentError, "CP932 として読めません: #{reason}"
    end
  end

  # 特徴量のマップを、列の順に並べた数値のリストにする。
  defp to_rows(x, columns) do
    Enum.map(x, fn features -> Enum.map(columns, &Map.fetch!(features, &1)) end)
  end

  defp format_scores(%{train: train, test: test}) do
    "訓練 #{format_number(train, 4)}, テスト #{format_number(test, 4)}"
  end

  # 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす。
  defp format_number(value, digits) do
    value = if abs(value) < @zero_tolerance, do: 0.0, else: value

    ~c"~.#{digits}f" |> :io_lib.format([value]) |> to_string()
  end
end
