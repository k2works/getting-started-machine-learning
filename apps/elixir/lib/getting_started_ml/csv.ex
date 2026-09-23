defmodule GettingStartedMl.Csv do
  @moduledoc """
  CSV を列名つきで読む。

  NimbleCSV は BOM を取り除かないので、先頭の列名から自分で取り除く。
  """

  NimbleCSV.define(GettingStartedMl.Csv.Parser, separator: ",", escape: "\"")
  NimbleCSV.define(GettingStartedMl.Csv.TabParser, separator: "\t", escape: "\"")

  alias GettingStartedMl.Csv.Parser
  alias GettingStartedMl.Csv.TabParser

  @bom "\uFEFF"

  @doc """
  CSV を読み、1 行目を列名にしたマップのリストを返す。

  列名はアトムにする。値は文字列のまま返し、数値への変換は章ごとに行う。
  """
  def read(path) do
    path |> File.read!() |> parse()
  end

  @doc "文字列を列名つきのマップのリストにする。"
  def parse(contents), do: contents |> parse_table() |> elem(1)

  @doc """
  CSV を読み、列名の並びと行のリストを返す。

  Elixir のマップはキーの順を保たない（大きくなると並びが崩れる）ので、
  列の順を保ちたいときはこちらを使って列名のリストを持ち回る。
  """
  def read_table(path), do: path |> File.read!() |> parse_table()

  @doc """
  文字列を「列名の並び」と「行のリスト」にする。

  区切り文字はカンマ（`","`）とタブ（`"\\t"`）を選べる。NimbleCSV のパーサーは
  マクロで作るので、区切り文字は実行時ではなくコンパイル時に決まる。
  """
  def parse_table(contents, separator \\ ",") do
    [header | rows] = parser(separator).parse_string(contents, skip_headers: false)
    columns = header |> strip_bom() |> Enum.map(&String.to_atom/1)

    rows =
      rows
      |> Enum.reject(fn row -> Enum.all?(row, &(String.trim(&1) == "")) end)
      |> Enum.map(fn row -> columns |> Enum.zip(row) |> Map.new() end)

    {columns, rows}
  end

  defp parser(","), do: Parser
  defp parser("\t"), do: TabParser
  defp parser(separator), do: raise(ArgumentError, "区切り文字に対応していません: #{separator}")

  defp strip_bom([first | rest]), do: [String.replace_prefix(first, @bom, "") | rest]
end
