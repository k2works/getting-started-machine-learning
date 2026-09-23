defmodule GettingStartedMl.Csv do
  @moduledoc """
  CSV を列名つきで読む。

  NimbleCSV は BOM を取り除かないので、先頭の列名から自分で取り除く。
  """

  NimbleCSV.define(GettingStartedMl.Csv.Parser, separator: ",", escape: "\"")

  alias GettingStartedMl.Csv.Parser

  @bom "\uFEFF"

  @doc """
  CSV を読み、1 行目を列名にしたマップのリストを返す。

  列名はアトムにする。値は文字列のまま返し、数値への変換は章ごとに行う。
  """
  def read(path) do
    path |> File.read!() |> parse()
  end

  @doc "文字列を列名つきのマップのリストにする。"
  def parse(contents) do
    [header | rows] = Parser.parse_string(contents, skip_headers: false)
    columns = header |> strip_bom() |> Enum.map(&String.to_atom/1)

    Enum.map(rows, fn row -> columns |> Enum.zip(row) |> Map.new() end)
  end

  defp strip_bom([first | rest]), do: [String.replace_prefix(first, @bom, "") | rest]
end
