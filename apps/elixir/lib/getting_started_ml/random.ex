defmodule GettingStartedMl.Random do
  @moduledoc """
  乱数生成器を自作する。

  Nx の `Nx.Random`（Threefry）も Erlang の `:rand` も、ほかの言語版と並びが合わない。
  そこで `java.util.Random` と同じ 48 ビットの線形合同法をそのまま書く。
  こうすると訓練データとテストデータの分割が Java 版・Kotlin 版・Scala 版・Clojure 版と
  一致するので、章をまたいで数値を突き合わせられる。
  """

  import Bitwise

  @mask 0xFFFFFFFFFFFF
  @multiplier 0x5DEECE66D
  @increment 0xB

  @doc "シードから状態を作る。`java.util.Random` の `setSeed` と同じ。"
  def new(seed), do: bxor(seed, @multiplier) &&& @mask

  @doc """
  0 以上 `bound` 未満の整数を 1 つ返し、次の状態と一緒に返す。

  `bound` が 2 の冪のときだけ別の式を使うところまで `java.util.Random` に合わせる。
  """
  def next_int(state, bound) when bound > 0 do
    if (bound &&& -bound) == bound do
      {bits, next} = next_bits(state, 31)
      {(bound * bits) >>> 31, next}
    else
      reject(state, bound)
    end
  end

  @doc """
  Fisher-Yates で並べ替える。

  後ろから順に、まだ選んでいない範囲から 1 つ選んで交換する。
  """
  def shuffle(items, seed) do
    array = List.to_tuple(items)
    last = tuple_size(array) - 1

    if last < 1 do
      items
    else
      {shuffled, _state} =
        Enum.reduce(last..1//-1, {array, new(seed)}, fn i, {acc, state} ->
          {j, next} = next_int(state, i + 1)
          {swap(acc, i, j), next}
        end)

      Tuple.to_list(shuffled)
    end
  end

  # 上位ビットだけを使う。48 ビットの状態から欲しいビット数を取り出す。
  defp next_bits(state, bits) do
    next = state * @multiplier + @increment &&& @mask
    {next >>> (48 - bits), next}
  end

  # 剰余の偏りを避けるため、範囲をはみ出す値は捨てて引き直す。
  defp reject(state, bound) do
    {bits, next} = next_bits(state, 31)
    value = rem(bits, bound)

    if bits - value + (bound - 1) >= 0x80000000 do
      reject(next, bound)
    else
      {value, next}
    end
  end

  defp swap(tuple, i, j) do
    at_i = elem(tuple, i)
    at_j = elem(tuple, j)
    tuple |> put_elem(i, at_j) |> put_elem(j, at_i)
  end
end
