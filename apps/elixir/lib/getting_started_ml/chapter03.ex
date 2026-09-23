defmodule GettingStartedMl.Chapter03 do
  @moduledoc """
  第 3 章: 決定木による分類。

  Scholar には決定木がないので、ここで作る木がそのまま最終実装になる。
  木は葉か節のどちらかで、どちらもマップで表す。葉は `%{label: "setosa"}`、
  節は `%{split: ..., left: ..., right: ...}`。Elixir には判別共用体が無いので、
  鍵があるかどうかで見分ける（網羅性は検査されない）。
  """

  alias GettingStartedMl.{Chapter01, Chapter02, Dataset}

  @max_depths [1, 2, 3, 4, 5, nil]

  @doc "葉かどうかを返す。"
  def leaf?(tree), do: is_map_key(tree, :label)

  @doc "節かどうかを返す。"
  def node?(tree), do: is_map_key(tree, :split)

  @doc "ジニ不純度。ラベルが 1 種類なら 0 で、種類が多く均等なほど大きい。"
  def gini([]), do: 0.0

  def gini(labels) do
    total = length(labels)

    1.0 -
      (labels
       |> Enum.frequencies()
       |> Enum.map(fn {_label, n} -> n / total * (n / total) end)
       |> Enum.sum())
  end

  @doc """
  いちばん多いラベルを返す。同数なら先に現れたほうを選ぶ。

  `Enum.frequencies/1` が返すマップは順を保たないので、最初に現れた順
  （`Enum.uniq/1`）でたどる。`Enum.max_by/2` は同値なら先のものを返すので、
  ここでは Clojure の `max-key`（同値なら後ろ）と逆の心配をしなくてよい。
  """
  def majority(labels) do
    counts = Enum.frequencies(labels)
    labels |> Enum.uniq() |> Enum.max_by(&counts[&1])
  end

  @doc """
  左右の不純度の重み付き平均が最も小さくなる分割を返す。分けられなければ `nil`。

  同じ不純度なら先に見つけた（列の順で前の）分割を選ぶ。
  """
  def best_split([], _t, _columns), do: nil

  def best_split(x, t, columns) do
    if gini(t) == 0.0 do
      nil
    else
      case Enum.flat_map(columns, &candidates(x, t, &1)) do
        [] -> nil
        all -> Enum.min_by(all, & &1.impurity)
      end
    end
  end

  @doc "深さの上限まで分割を繰り返して木を作る。`max_depth` が `nil` なら上限なし。"
  def fit(x, t, columns, max_depth) do
    split = if max_depth == 0, do: nil, else: best_split(x, t, columns)

    if is_nil(split) do
      %{label: majority(t)}
    else
      {left, right} = Enum.split_with(Enum.zip(x, t), &goes_left?(split, elem(&1, 0)))
      next_depth = if max_depth, do: max_depth - 1

      %{
        split: split,
        left:
          fit(Enum.map(left, &elem(&1, 0)), Enum.map(left, &elem(&1, 1)), columns, next_depth),
        right:
          fit(Enum.map(right, &elem(&1, 0)), Enum.map(right, &elem(&1, 1)), columns, next_depth)
      }
    end
  end

  @doc "木をたどって 1 件のラベルを予測する。"
  def predict_one(%{label: label}, _features), do: label

  def predict_one(%{split: split, left: left, right: right}, features) do
    if goes_left?(split, features) do
      predict_one(left, features)
    else
      predict_one(right, features)
    end
  end

  @doc "特徴量ごとのラベルを予測する。"
  def predict(tree, x), do: Enum.map(x, &predict_one(tree, &1))

  @doc "木を字下げ付きの文字列にする。"
  def format_tree(tree, indent \\ "")

  def format_tree(%{label: label}, indent), do: "#{indent}#{label}\n"

  def format_tree(%{split: split, left: left, right: right}, indent) do
    border = format_number(split.threshold)

    "#{indent}#{split.feature} <= #{border}\n" <>
      format_tree(left, indent <> "  ") <>
      "#{indent}#{split.feature} > #{border}\n" <>
      format_tree(right, indent <> "  ")
  end

  @doc "深さごとの正解率と、深さ 2 の決定木を表示する。"
  def run do
    split = Chapter02.prepare_iris(Path.join(Dataset.dir(), "iris.csv"), 0.3, 0)
    columns = feature_columns()

    IO.puts("深さ\t訓練データ\tテストデータ")

    for max_depth <- @max_depths do
      IO.puts(accuracy_row(max_depth, split, columns))
    end

    IO.puts("")
    IO.puts("深さ 2 の決定木:")
    IO.write(format_tree(fit(split.x_train, split.t_train, columns, 2)))
  end

  @doc "アヤメのデータの特徴量の列。正解ラベルの列を除いた順。"
  def feature_columns, do: [:がく片長さ, :がく片幅, :花弁長さ, :花弁幅]

  # 1 つの列で、隣り合う値の中点を境界にした分割の候補をすべて返す。
  # Enum.sort_by は安定なので、同じ値の並びは元の順のまま。
  defp candidates(x, t, feature) do
    sorted = x |> Enum.map(&Map.fetch!(&1, feature)) |> Enum.zip(t) |> Enum.sort_by(&elem(&1, 0))
    values = Enum.map(sorted, &elem(&1, 0))
    labels = Enum.map(sorted, &elem(&1, 1))

    1..(length(sorted) - 1)//1
    |> Enum.filter(fn i -> Enum.at(values, i - 1) != Enum.at(values, i) end)
    |> Enum.map(fn i ->
      {left, right} = Enum.split(labels, i)

      %{
        feature: feature,
        threshold: (Enum.at(values, i - 1) + Enum.at(values, i)) / 2.0,
        impurity: weighted_gini(left, right)
      }
    end)
  end

  defp weighted_gini(left, right) do
    (length(left) * gini(left) + length(right) * gini(right)) / (length(left) + length(right))
  end

  defp goes_left?(split, features), do: Map.fetch!(features, split.feature) <= split.threshold

  defp accuracy_row(max_depth, split, columns) do
    tree = fit(split.x_train, split.t_train, columns, max_depth)

    Enum.join(
      [
        max_depth || "制限なし",
        score(predict(tree, split.x_train), split.t_train),
        score(predict(tree, split.x_test), split.t_test)
      ],
      "\t"
    )
  end

  defp score(predictions, labels) do
    format_number(Chapter01.accuracy(predictions, labels))
  end

  # :io_lib.format はロケールに依らないので、小数点は常に「.」になる。
  defp format_number(value), do: ~c"~.4f" |> :io_lib.format([value]) |> to_string()
end
