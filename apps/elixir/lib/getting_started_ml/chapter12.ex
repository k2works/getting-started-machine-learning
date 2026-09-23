defmodule GettingStartedMl.Chapter12 do
  @moduledoc """
  第 12 章: 正則化とモデル選択。

  リッジ回帰を Nx で自作し、検証データで正則化の強さを選び、
  `Scholar.Linear.RidgeRegression` と突き合わせる。
  **Scholar にラッソ回帰は無い**ので、座標降下法で自作したものがそのまま最終実装になる。

  行列は「数のリストのリスト」。正則化したモデルは
  `%{coefficients: [列の順に並んだ係数], intercept: 切片}` のマップで表す。
  Elixir のマップはキーの順を保たないので、列の順は必ずリストで持ち回る。
  """

  alias GettingStartedMl.{Chapter02, Chapter07, Dataset}
  alias Scholar.Linear.RidgeRegression

  @feature_columns [:RM, :PTRATIO, :LSTAT]
  @target :PRICE
  @outlier_threshold 3.0

  @test_size 0.3
  @validation_size 0.3
  @seed 0
  @alphas [0.0, 0.1, 1.0, 10.0, 100.0]
  # ラッソ回帰の正則化の強さ。自作の目的関数は件数で割らないので、件数で割る実装
  # （Clojure 版・Java 版が使う Tribuo の ElasticNetCDTrainer）の alpha=0.5 は
  # 訓練データ 47 件では 0.5 * 47 = 23.5 にあたる。
  @lasso_alpha 23.5

  # 座標降下法の繰り返しの上限と、係数の動きの打ち切り。
  @max_iterations 10_000
  @tolerance 1.0e-10

  @doc "特徴量の列。"
  def feature_columns, do: @feature_columns

  @doc "正解の列。"
  def target, do: @target

  @doc "z スコアの絶対値がこの値を超える値を持つ行を外れ値とする。"
  def outlier_threshold, do: @outlier_threshold

  @doc "試す正則化の強さ。"
  def alphas, do: @alphas

  ## リッジ回帰

  @doc """
  特徴量と正解から平均を引いてから `(Xᵀ X + alpha I) w = Xᵀ t` を解いて係数を求め、
  切片は平均値から求める。

  平均を引くのは、切片に罰則をかけないため。`alpha` が 0 なら最小二乗法と同じ解になる。
  """
  def ridge_fit(x, t, alpha) do
    if length(x) != length(t) do
      raise ArgumentError, "特徴量と正解の件数が違います: #{length(x)} と #{length(t)}"
    end

    x_means = column_means(x)
    t_mean = Enum.sum(t) / length(t)
    centered = Nx.tensor(center(x, x_means), type: :f64)
    transposed = Nx.transpose(centered)
    residuals = Nx.new_axis(Nx.tensor(Enum.map(t, &(&1 - t_mean)), type: :f64), 1)

    penalized =
      Nx.add(
        Nx.dot(transposed, centered),
        Nx.multiply(Nx.eye(length(x_means), type: :f64), alpha)
      )

    coefficients =
      penalized
      |> Nx.LinAlg.solve(Nx.dot(transposed, residuals))
      |> Nx.squeeze(axes: [1])
      |> Nx.to_flat_list()

    model(coefficients, t_mean - dot(x_means, coefficients))
  end

  @doc "係数と切片からモデルを作る。"
  def model(coefficients, intercept), do: %{coefficients: coefficients, intercept: intercept}

  @doc "行ごとの予測値。行列の列数は係数の数と同じでなければならない。"
  def predict(%{coefficients: coefficients, intercept: intercept}, x) do
    Enum.map(x, fn row ->
      if length(row) != length(coefficients) do
        raise ArgumentError,
              "特徴量の列数 #{length(row)} と係数の数 #{length(coefficients)} が違います"
      end

      intercept + dot(row, coefficients)
    end)
  end

  @doc "係数の絶対値の合計。正則化が強いほど小さくなる。"
  def coefficient_abs_sum(%{coefficients: coefficients}) do
    coefficients |> Enum.map(&abs/1) |> Enum.sum()
  end

  ## ラッソ回帰（Scholar に無いので自作が最終実装）

  @doc "軟しきい値作用素。`|value|` が `threshold` 以下なら 0 にし、そうでなければ 0 のほうへ縮める。"
  def soft_threshold(value, threshold) when value > threshold, do: value - threshold
  def soft_threshold(value, threshold) when value < -threshold, do: value + threshold
  def soft_threshold(_value, _threshold), do: 0.0

  @doc """
  平均を引いてから座標降下法で `½‖t - Xw‖² + alpha ‖w‖₁` を最小にする。

  L1 の罰則は原点で折れているので、リッジ回帰のように行列を解いて終わりにはできない。
  係数を 1 つずつ順に動かし、軟しきい値作用素で 0 に寄せる。
  """
  def lasso_fit(x, t, alpha) do
    if length(x) != length(t) do
      raise ArgumentError, "特徴量と正解の件数が違います: #{length(x)} と #{length(t)}"
    end

    x_means = column_means(x)
    t_mean = Enum.sum(t) / length(t)
    columns = transpose(center(x, x_means))
    norms = Enum.map(columns, fn column -> dot(column, column) end)
    weights = Tuple.duplicate(0.0, length(columns))

    {fitted, _residuals} =
      descend(
        Enum.zip([columns, norms, 0..(length(columns) - 1)]),
        alpha,
        weights,
        Enum.map(t, &(&1 - t_mean)),
        @max_iterations
      )

    coefficients = Tuple.to_list(fitted)
    model(coefficients, t_mean - dot(x_means, coefficients))
  end

  ## 実験の記録とモデル選択

  @doc "`alpha` ごとにリッジ回帰を訓練データで学習し、訓練データと検証データの決定係数を記録する。"
  def run_ridge_experiments(data, alphas) do
    Enum.map(alphas, fn alpha ->
      fitted = ridge_fit(data.x_train, data.t_train, alpha)

      %{
        alpha: alpha,
        train_score: Chapter07.r2_score(data.t_train, predict(fitted, data.x_train)),
        validation_score: Chapter07.r2_score(data.t_valid, predict(fitted, data.x_valid)),
        coefficient_abs_sum: coefficient_abs_sum(fitted)
      }
    end)
  end

  @doc """
  検証データの決定係数が最も高い実験。同じ値なら先の実験を選ぶ。

  `Enum.max_by/2` は同値なら先のものを返すので、Clojure の `max-key`（同値なら後ろ）と
  逆の心配をしなくてよい。
  """
  def best_experiment([]), do: raise(ArgumentError, "実験結果が 1 件もありません")
  def best_experiment(experiments), do: Enum.max_by(experiments, & &1.validation_score)

  @doc "係数がちょうど 0 になった特徴量の名前を、列の順に返す。"
  def zero_coefficient_names(coefficients, names) do
    if length(coefficients) != length(names) do
      raise ArgumentError, "係数と特徴量名の数が違います"
    end

    coefficients
    |> Enum.zip(names)
    |> Enum.filter(fn {value, _name} -> value == 0.0 end)
    |> Enum.map(&elem(&1, 1))
  end

  ## 標準化して多項式特徴量にする

  @doc "訓練データの列ごとの平均と、件数 n で割る標準偏差を求める。"
  def scaler_fit(x, columns) do
    stats =
      Enum.map(columns, fn column ->
        values = Enum.map(x, &Map.fetch!(&1, column))
        mean = Enum.sum(values) / length(values)
        {mean, :math.sqrt(sum_of_squares(Enum.map(values, &(&1 - mean))) / length(values))}
      end)

    %{
      columns: columns,
      means: Enum.map(stats, &elem(&1, 0)),
      stds: Enum.map(stats, &elem(&1, 1))
    }
  end

  @doc ~S"""
  変換後の列名。元の列、2 乗の列（`"RM^2"`）、積の列（`"RM LSTAT"`）の順。
  """
  def feature_names(%{columns: columns}) do
    names = Enum.map(columns, &to_string/1)

    names ++
      Enum.map(pairs(length(columns)), fn
        {i, i} -> "#{Enum.at(names, i)}^2"
        {i, j} -> "#{Enum.at(names, i)} #{Enum.at(names, j)}"
      end)
  end

  @doc "標準化した値と、その 2 次の項を並べた行列にする。"
  def scaler_transform(scaler, x) do
    %{columns: columns, means: means, stds: stds} = scaler
    combinations = pairs(length(columns))

    Enum.map(x, fn features ->
      z =
        Enum.zip([columns, means, stds])
        |> Enum.map(fn {column, mean, std} -> (Map.fetch!(features, column) - mean) / std end)

      z ++ Enum.map(combinations, fn {i, j} -> Enum.at(z, i) * Enum.at(z, j) end)
    end)
  end

  ## 外れ値を除く

  @doc """
  列ごとの z スコア（標準偏差は件数 n - 1 で割る標本標準偏差）の絶対値が
  `threshold` を超える値を 1 つでも持つ行を除く。
  """
  def remove_outliers(table, columns, threshold) do
    stats =
      Enum.map(columns, fn column ->
        values = Enum.map(table.rows, &required_number(&1, column))
        mean = Enum.sum(values) / length(values)

        {column, mean,
         :math.sqrt(sum_of_squares(Enum.map(values, &(&1 - mean))) / (length(values) - 1))}
      end)

    %{table | rows: Enum.reject(table.rows, &outlier?(&1, stats, threshold))}
  end

  ## Scholar のリッジ回帰

  @doc """
  `Scholar.Linear.RidgeRegression` で学習し、自作と同じ形のモデルにする。

  Scholar のリッジ回帰が最小にするのは `‖y - Xw‖² + alpha ‖w‖²` で、自作と同じ尺度。
  `fit_intercept?` は既定で `true` なので、Scholar も平均を引いてから解き、切片に罰則をかけない。

  `solver` は既定の `:svd`（特異値分解）と `:cholesky`（`Nx.LinAlg.solve` による閉形式）を選べる。
  自作と同じ解き方は `:cholesky` のほうで、そちらなら 1 ビットも違わない。
  """
  def scholar_ridge_fit(x, t, alpha, solver \\ :svd) do
    fitted =
      RidgeRegression.fit(Nx.tensor(x, type: :f64), Nx.tensor(t, type: :f64),
        alpha: alpha,
        solver: solver
      )

    model(
      Nx.to_flat_list(fitted.coefficients),
      Nx.to_number(Nx.squeeze(fitted.intercept))
    )
  end

  ## ボストンの住宅価格

  @doc """
  外れ値を除き、全体を訓練用とテスト用に、訓練用をさらに訓練データと検証データに分ける。

  標準化の平均と標準偏差は訓練データだけから求め、3 つとも同じ値で変換する。
  """
  def prepare_boston(path, test_size, validation_size, seed) do
    table =
      remove_outliers(
        Chapter02.load_table(path),
        [@target | @feature_columns],
        @outlier_threshold
      )

    x = Enum.map(table.rows, &feature_map/1)
    t = Enum.map(table.rows, &required_number(&1, @target))
    outer = Chapter02.split_train_test(x, t, test_size, seed)
    inner = Chapter02.split_train_test(outer.x_train, outer.t_train, validation_size, seed)
    scaler = scaler_fit(inner.x_train, @feature_columns)

    %{
      x_train: scaler_transform(scaler, inner.x_train),
      t_train: inner.t_train,
      x_valid: scaler_transform(scaler, inner.x_test),
      t_valid: inner.t_test,
      x_test: scaler_transform(scaler, outer.x_test),
      t_test: outer.t_test,
      feature_names: feature_names(scaler),
      kept: length(table.rows)
    }
  end

  ## 実データでの実行

  @doc "正則化の強さごとにリッジ回帰を学習し、検証データで選んだモデルとラッソ回帰の結果を表示する。"
  def run do
    path = Path.join(Dataset.dir(), "Boston.csv")
    total = length(Chapter02.load_table(path).rows)
    data = prepare_boston(path, @test_size, @validation_size, @seed)
    experiments = run_ridge_experiments(data, @alphas)
    best = best_experiment(experiments)

    print_summary(data, total)
    print_experiments(experiments)
    IO.puts("検証データで選んだ alpha: #{best.alpha}")
    print_test_scores(data, best.alpha)
    print_lasso(data)
  end

  defp print_summary(data, total) do
    IO.puts("データ件数: #{data.kept}（外れ値 #{total - data.kept} 件を除外）")

    IO.puts(
      "訓練データ: #{length(data.t_train)} 件, 検証データ: #{length(data.t_valid)} 件, " <>
        "テストデータ: #{length(data.t_test)} 件"
    )

    IO.puts("特徴量: #{Enum.join(data.feature_names, ", ")}")
  end

  defp print_experiments(experiments) do
    IO.puts("alpha  訓練 R²  検証 R²  係数の絶対値の合計")

    Enum.each(experiments, fn experiment ->
      IO.puts(
        "#{pad(format(experiment.alpha, 1), 5)}  #{format(experiment.train_score, 4)}  " <>
          "#{format(experiment.validation_score, 4)}  #{format(experiment.coefficient_abs_sum, 3)}"
      )
    end)
  end

  defp print_test_scores(data, alpha) do
    linear = ridge_fit(data.x_train, data.t_train, 0.0)
    ridge = ridge_fit(data.x_train, data.t_train, alpha)
    library = scholar_ridge_fit(data.x_train, data.t_train, alpha)

    IO.puts(
      "テストデータの決定係数: 線形回帰 #{format(test_score(linear, data), 4)}, " <>
        "リッジ回帰 #{format(test_score(ridge, data), 4)}"
    )

    IO.puts("Scholar のリッジ回帰との係数の最大の差: #{exponent(max_difference(ridge, library))}")
  end

  defp print_lasso(data) do
    lasso = lasso_fit(data.x_train, data.t_train, @lasso_alpha)
    zeros = zero_coefficient_names(lasso.coefficients, data.feature_names)

    IO.puts("ラッソ回帰（alpha=#{@lasso_alpha}）で係数が 0 になった特徴量: #{Enum.join(zeros, ", ")}")
  end

  defp test_score(fitted, data), do: Chapter07.r2_score(data.t_test, predict(fitted, data.x_test))

  defp max_difference(left, right) do
    left.coefficients
    |> Enum.zip_with(right.coefficients, fn a, b -> abs(a - b) end)
    |> Enum.max()
  end

  defp feature_map(row) do
    Map.new(@feature_columns, fn column -> {column, required_number(row, column)} end)
  end

  defp required_number(row, column) do
    Chapter02.number(row, column) || raise(ArgumentError, "値が空欄です: #{column}")
  end

  defp outlier?(row, stats, threshold) do
    Enum.any?(stats, fn {column, mean, std} ->
      abs((required_number(row, column) - mean) / std) > threshold
    end)
  end

  # 係数がどれも @tolerance より動かなくなるまで、すべての係数を 1 回ずつ動かす。
  defp descend(_columns, _alpha, weights, residuals, 0), do: {weights, residuals}

  defp descend(columns, alpha, weights, residuals, iterations) do
    {weights, residuals, delta} = sweep(columns, alpha, weights, residuals)

    if delta < @tolerance do
      {weights, residuals}
    else
      descend(columns, alpha, weights, residuals, iterations - 1)
    end
  end

  # すべての係数を 1 回ずつ動かし、いちばん大きく動いた大きさを一緒に返す。
  defp sweep(columns, alpha, weights, residuals) do
    Enum.reduce(columns, {weights, residuals, 0.0}, fn entry, acc ->
      update(entry, alpha, acc)
    end)
  end

  defp update({_column, +0.0, _index}, _alpha, acc), do: acc

  defp update({column, norm, index}, alpha, {weights, residuals, delta}) do
    old = elem(weights, index)

    # いったん列の寄与を残差に戻してから、残差との相関で係数を決め直す
    restored = Enum.zip_with(residuals, column, fn r, value -> r + old * value end)
    weight = soft_threshold(dot(column, restored), alpha) / norm
    removed = Enum.zip_with(restored, column, fn r, value -> r - weight * value end)

    {put_elem(weights, index, weight), removed, max(delta, abs(weight - old))}
  end

  # 2 次の項を作る列の組（i <= j）。
  defp pairs(n), do: for(i <- 0..(n - 1), j <- i..(n - 1), do: {i, j})

  defp column_means([first | _] = x) do
    Enum.map(0..(length(first) - 1), fn j ->
      Enum.sum(Enum.map(x, &Enum.at(&1, j))) / length(x)
    end)
  end

  defp center(x, means), do: Enum.map(x, fn row -> Enum.zip_with(row, means, &(&1 - &2)) end)

  defp transpose(x), do: Enum.zip_with(x, & &1)

  defp dot(a, b), do: Enum.zip_with(a, b, &(&1 * &2)) |> Enum.sum()

  defp sum_of_squares(values), do: Enum.sum(Enum.map(values, &(&1 * &1)))

  defp pad(text, width), do: String.pad_leading(text, width)

  # :io_lib.format はロケールに依らないので、小数点は常に「.」になる。
  defp format(value, digits) do
    ~c"~.#{digits}f" |> :io_lib.format([value]) |> to_string()
  end

  defp exponent(value) do
    ~c"~.2e" |> :io_lib.format([value]) |> to_string()
  end
end
