defmodule GettingStartedMl.Chapter02 do
  @moduledoc """
  第 2 章: データの前処理。

  表の読み込み・欠損値の補完・訓練データとテストデータへの分割。
  """

  alias GettingStartedMl.{Csv, Dataset, Random}

  @target :種類

  @doc "アヤメのデータの正解ラベルの列。"
  def target, do: @target

  @doc "文字列の列を読む。列が無ければ失敗する。"
  def text(row, column) do
    case Map.fetch(row, column) do
      {:ok, value} -> value
      :error -> raise ArgumentError, "列がありません: #{column}"
    end
  end

  @doc "セルが空欄かどうかを返す。"
  def missing?(row, column), do: row |> text(column) |> String.trim() == ""

  @doc """
  数値の列を読む。空欄なら `nil` を返す。数値として読めなければ失敗する。

  Elixir には Option が無いので、欠損値は `nil` で表す。
  """
  def number(row, column) do
    cell = row |> text(column) |> String.trim()

    if cell == "" do
      nil
    else
      case Float.parse(cell) do
        {value, ""} -> value
        _ -> raise ArgumentError, "#{column} を数値として読めません: #{cell}"
      end
    end
  end

  @doc "CSV を読み込んで表にする。列の順は CSV の順のまま。"
  def load_table(path) do
    {columns, rows} = Csv.read_table(path)
    %{columns: columns, rows: rows}
  end

  @doc "列ごとに欠損値の数を数える。列の順は表の列の順のまま。"
  def count_missing(%{columns: columns, rows: rows}) do
    Enum.map(columns, fn column -> {column, Enum.count(rows, &missing?(&1, column))} end)
  end

  @doc "欠損値を除いて、列ごとの平均値を求める。"
  def column_means(rows, columns) do
    Map.new(columns, fn column ->
      values = rows |> Enum.map(&number(&1, column)) |> Enum.reject(&is_nil/1)

      if values == [] do
        raise ArgumentError, "値がすべて空欄です: #{column}"
      end

      {column, Enum.sum(values) / length(values)}
    end)
  end

  @doc "欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変えない。"
  def fill_missing(rows, columns, fill_values) do
    Enum.map(rows, fn row ->
      Map.new(columns, fn column ->
        {column, number(row, column) || fill_value(fill_values, column)}
      end)
    end)
  end

  @doc "正解ラベルの列を取り出し、残りの列を特徴量の列にする。"
  def split_features_and_target(%{columns: columns, rows: rows}, target_column) do
    %{
      columns: Enum.reject(columns, &(&1 == target_column)),
      rows: rows,
      labels: Enum.map(rows, &text(&1, target_column))
    }
  end

  @doc "並べ替えてから、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。"
  def split_train_test(x, t, test_size, seed) do
    if length(x) != length(t) do
      raise ArgumentError, "件数が違います: #{length(x)} と #{length(t)}"
    end

    shuffled = x |> Enum.zip(t) |> Random.shuffle(seed)
    train_count = length(shuffled) - ceil(length(shuffled) * test_size)
    {train, test} = Enum.split(shuffled, train_count)

    %{
      x_train: Enum.map(train, &elem(&1, 0)),
      x_test: Enum.map(test, &elem(&1, 0)),
      t_train: Enum.map(train, &elem(&1, 1)),
      t_test: Enum.map(test, &elem(&1, 1))
    }
  end

  @doc "iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。"
  def prepare_iris(path, test_size, seed) do
    %{columns: columns, rows: rows, labels: labels} =
      path |> load_table() |> split_features_and_target(@target)

    split = split_train_test(rows, labels, test_size, seed)
    means = column_means(split.x_train, columns)

    %{
      split
      | x_train: fill_missing(split.x_train, columns, means),
        x_test: fill_missing(split.x_test, columns, means)
    }
  end

  @doc "アヤメのデータの前処理の結果を表示する。"
  def run do
    path = Path.join(Dataset.dir(), "iris.csv")
    table = load_table(path)
    split = prepare_iris(path, 0.3, 0)

    IO.puts("データ件数: #{length(table.rows)}")

    IO.puts(
      "欠損値の数: " <>
        Enum.map_join(count_missing(table), ", ", fn {column, n} -> "#{column}=#{n}" end)
    )

    IO.puts("訓練データ: #{length(split.x_train)} 件, テストデータ: #{length(split.x_test)} 件")
    IO.puts("特徴量: " <> Enum.map_join(feature_columns(table), ", ", &to_string/1))
  end

  defp feature_columns(table), do: Enum.reject(table.columns, &(&1 == @target))

  # || では補完する値が nil のときに落ちないので、無いことを明示して確かめる。
  defp fill_value(fill_values, column) do
    case Map.fetch(fill_values, column) do
      {:ok, value} -> value
      :error -> raise ArgumentError, "補完する値がありません: #{column}"
    end
  end
end
