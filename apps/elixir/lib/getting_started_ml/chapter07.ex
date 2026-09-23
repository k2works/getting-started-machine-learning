defmodule GettingStartedMl.Chapter07 do
  @moduledoc """
  第 7 章: 線形回帰による数値予測。

  正規方程式 `(Xᵀ X) w = Xᵀ t` を Nx のテンソルで自分で解いてから、
  `Scholar.Linear.LinearRegression` と突き合わせる。

  テンソルは必ず `type: :f64` を明示する。Nx の既定は単精度（f32）なので、
  そのままだと係数が `0.999998927116394` のようにずれ、ほかの言語版と比べられない。

  モデルは `%{intercept: 切片, columns: [列名], coefficients: [係数]}` で表す。
  Elixir のマップはキーの順を保たないので、列の順は必ずリストで持ち回る。
  """

  alias GettingStartedMl.{Chapter02, Dataset}
  alias Scholar.Linear.LinearRegression

  @feature_columns [:SNS1, :SNS2, :actor, :original]
  @target :sales

  # SNS2 がこの値を超え、かつ興行収入が @outlier_sales 未満の映画を外れ値とする。
  @outlier_sns2 1000.0
  @outlier_sales 8500.0

  @test_size 0.2
  @seed 0

  @doc "映画のデータの特徴量の列。cinema_id は映画を区別する番号なので使わない。"
  def feature_columns, do: @feature_columns

  @doc "映画のデータの正解ラベルの列。"
  def target, do: @target

  ## 評価指標

  @doc "平均絶対誤差（MAE）。誤差の絶対値の平均。"
  def mean_absolute_error(t, y) do
    r = residuals(t, y)
    Enum.sum(Enum.map(r, &abs/1)) / length(r)
  end

  @doc "平均二乗誤差の平方根（RMSE）。"
  def root_mean_squared_error(t, y) do
    r = residuals(t, y)
    :math.sqrt(sum_of_squares(r) / length(r))
  end

  @doc "決定係数（R²）。1 から「残差の 2 乗の合計 ÷ 平均との差の 2 乗の合計」を引いた値。"
  def r2_score(t, y) do
    mean = Enum.sum(t) / length(t)
    1.0 - sum_of_squares(residuals(t, y)) / sum_of_squares(Enum.map(t, &(&1 - mean)))
  end

  ## モデル

  @doc "列名と、同じ順に並んだ係数からモデルを作る。"
  def model(intercept, columns, coefficients) do
    if length(columns) != length(coefficients) do
      raise ArgumentError,
            "列名と係数の数が違います: #{length(columns)} と #{length(coefficients)}"
    end

    %{intercept: intercept, columns: columns, coefficients: coefficients}
  end

  @doc "列名で係数を読む。無ければ失敗する。"
  def coefficient(model, column) do
    case Enum.find_index(model.columns, &(&1 == column)) do
      nil -> raise ArgumentError, "係数がありません: #{column}"
      index -> Enum.at(model.coefficients, index)
    end
  end

  @doc "1 行分の特徴量の予測値。係数は列名で対応させるので、列の並び順は問わない。"
  def predict_one(model, features) do
    model.columns
    |> Enum.zip(model.coefficients)
    |> Enum.reduce(model.intercept, fn {column, weight}, sum ->
      sum + weight * Map.fetch!(features, column)
    end)
  end

  @doc "行ごとの予測値。"
  def predict(model, x), do: Enum.map(x, &predict_one(model, &1))

  ## 学習

  @doc "先頭に 1 の列を足した特徴量の行列（計画行列）を作る。1 の列の係数が切片になる。"
  def design_matrix(x, columns) do
    Enum.map(x, fn features -> [1.0 | Enum.map(columns, &Map.fetch!(features, &1))] end)
  end

  @doc """
  `(Xᵀ X) w = Xᵀ t` を解いて、切片と係数を求める。

  型を引数で受け取れるようにしてあるのは、f32 と f64 の違いをテストで見せるため。
  既定は f64 で、ほかの言語版と数値を突き合わせられるのはこちらだけ。
  """
  def fit(x, t, columns, type \\ :f64)

  def fit([], _t, _columns, _type), do: raise(ArgumentError, "訓練データが空です")

  def fit(x, t, columns, type) do
    if length(x) != length(t) do
      raise ArgumentError, "特徴量と実測値の件数が違います: #{length(x)} と #{length(t)}"
    end

    design = Nx.tensor(design_matrix(x, columns), type: type)
    transposed = Nx.transpose(design)
    values = Nx.new_axis(Nx.tensor(t, type: type), 1)

    [intercept | coefficients] =
      Nx.dot(transposed, design)
      |> Nx.LinAlg.solve(Nx.dot(transposed, values))
      |> Nx.squeeze(axes: [1])
      |> Nx.to_flat_list()

    model(intercept, columns, coefficients)
  end

  ## Scholar

  @doc "`Scholar.Linear.LinearRegression` で学習し、自作と同じ形のモデルにする。"
  def scholar_fit(x, t, columns) do
    trained =
      LinearRegression.fit(
        Nx.tensor(feature_matrix(x, columns), type: :f64),
        Nx.tensor(t, type: :f64)
      )

    model(Nx.to_number(trained.intercept), columns, Nx.to_flat_list(trained.coefficients))
  end

  @doc """
  列ごとに平均を引いて標準偏差で割ってから Scholar に渡し、係数を元の単位に戻す。

  Scholar の線形回帰は `Nx.LinAlg.pinv`（特異値分解）で解くので、
  桁の違う列が混ざったデータでは最小二乗解に届かない。尺度をそろえると近づく。
  """
  def scholar_fit_standardized(x, t, columns) do
    features = Nx.tensor(feature_matrix(x, columns), type: :f64)
    means = Nx.mean(features, axes: [0])
    deviations = Nx.standard_deviation(features, axes: [0])

    trained =
      LinearRegression.fit(
        Nx.divide(Nx.subtract(features, means), deviations),
        Nx.tensor(t, type: :f64)
      )

    coefficients = Nx.divide(trained.coefficients, deviations)

    model(
      Nx.to_number(Nx.subtract(trained.intercept, Nx.dot(coefficients, means))),
      columns,
      Nx.to_flat_list(coefficients)
    )
  end

  ## 前処理

  @doc "外れ値の行を除いた表を返す。列はそのまま残す。"
  def remove_outliers(table), do: %{table | rows: Enum.reject(table.rows, &outlier?/1)}

  @doc "cinema.csv を読み込み、外れ値を除き、分割してから訓練データの平均値で両方の欠損値を補完する。"
  def prepare_cinema(path, test_size, seed) do
    table = remove_outliers(Chapter02.load_table(path))
    t = Enum.map(table.rows, &Chapter02.number(&1, @target))
    split = Chapter02.split_train_test(table.rows, t, test_size, seed)
    means = Chapter02.column_means(split.x_train, @feature_columns)

    %{
      split
      | x_train: Chapter02.fill_missing(split.x_train, @feature_columns, means),
        x_test: Chapter02.fill_missing(split.x_test, @feature_columns, means)
    }
  end

  ## 実行

  @doc "映画の興行収入を線形回帰で予測し、係数と評価指標を表示する。"
  def run do
    path = Path.join(Dataset.dir(), "cinema.csv")
    table = Chapter02.load_table(path)
    split = prepare_cinema(path, @test_size, @seed)
    model = fit(split.x_train, split.t_train, @feature_columns)
    single = fit(split.x_train, split.t_train, @feature_columns, :f32)
    library = scholar_fit(split.x_train, split.t_train, @feature_columns)
    y = predict(model, split.x_test)
    t = split.t_test

    IO.puts("データ件数: #{length(table.rows)}")
    IO.puts("外れ値を除いた件数: #{length(remove_outliers(table).rows)}")
    IO.puts("訓練データ: #{length(split.x_train)} 件, テストデータ: #{length(split.x_test)} 件")
    IO.puts("切片: #{format(model.intercept, 2)}")
    IO.puts("係数: #{format_coefficients(model)}")
    IO.puts("f32 で解いた切片: #{format(single.intercept, 2)} (#{single.intercept})")
    IO.puts("Scholar の切片: #{format(library.intercept, 2)}, 係数: #{format_coefficients(library)}")

    IO.puts(
      "テストデータの評価: R2=#{format(r2_score(t, y), 4)}, " <>
        "MAE=#{format(mean_absolute_error(t, y), 2)}, " <>
        "RMSE=#{format(root_mean_squared_error(t, y), 2)}"
    )
  end

  defp feature_matrix(x, columns) do
    Enum.map(x, fn features -> Enum.map(columns, &Map.fetch!(features, &1)) end)
  end

  defp outlier?(row) do
    Chapter02.number(row, :SNS2) > @outlier_sns2 and
      Chapter02.number(row, @target) < @outlier_sales
  end

  defp residuals(t, y) do
    if length(t) != length(y) do
      raise ArgumentError, "実測値と予測値の件数が違います: #{length(t)} と #{length(y)}"
    end

    Enum.zip_with(t, y, &(&1 - &2))
  end

  defp sum_of_squares(values), do: Enum.sum(Enum.map(values, &(&1 * &1)))

  defp format_coefficients(model) do
    model.columns
    |> Enum.zip(model.coefficients)
    |> Enum.map_join(", ", fn {column, weight} -> "#{column}=#{format(weight, 4)}" end)
  end

  # :io_lib.format はロケールに依らないので、小数点は常に「.」になる。
  defp format(value, digits) do
    ~c"~.#{digits}f" |> :io_lib.format([value]) |> to_string()
  end
end
