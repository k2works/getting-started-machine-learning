defmodule GettingStartedMl.Chapter01 do
  @moduledoc "第 1 章: 人間が決めたルールできのこ派・たけのこ派を判定する。"

  alias GettingStartedMl.{Csv, Dataset}

  @kinoko "きのこ"
  @takenoko "たけのこ"
  @kinoko_age_group 20
  @feature_keys [:身長, :体重, :年代]

  @doc "きのこ派の呼び名。"
  def kinoko, do: @kinoko

  @doc "たけのこ派の呼び名。"
  def takenoko, do: @takenoko

  @doc "判定の手がかりになる列。"
  def feature_keys, do: @feature_keys

  @doc "CSV を読み込み、列名で値を取り出して人物のリストにする。"
  def load_people(path) do
    path |> Csv.read() |> Enum.map(&to_person/1)
  end

  @doc "人物のリストを特徴量と正解ラベルに分ける。"
  def split_features_and_labels(people) do
    {Enum.map(people, &Map.take(&1, @feature_keys)), Enum.map(people, & &1.派閥)}
  end

  @doc "人間が決めたルールで派閥を判定する。"
  def predict_by_rule(%{年代: @kinoko_age_group}), do: @kinoko
  def predict_by_rule(%{年代: _}), do: @takenoko

  @doc "予測が正解ラベルと一致した割合を返す。件数が違えば例外を投げる。"
  def accuracy(predictions, labels) do
    if length(predictions) != length(labels) do
      raise ArgumentError,
            "予測と正解ラベルの件数が違います: #{length(predictions)} と #{length(labels)}"
    end

    hits = predictions |> Enum.zip(labels) |> Enum.count(fn {p, t} -> p == t end)
    hits / length(labels)
  end

  @doc "実データでルールによる判定の正解率を表示する。"
  def run do
    people = load_people(Path.join(Dataset.dir(), "KvsT.csv"))
    {x, t} = split_features_and_labels(people)
    predictions = Enum.map(x, &predict_by_rule/1)

    IO.puts("データ件数: #{length(people)}")
    IO.puts("ルールによる判定の正解率: #{:io_lib.format(~c"~.4f", [accuracy(predictions, t)])}")
  end

  defp to_person(row) do
    for column <- [:派閥 | @feature_keys], not is_map_key(row, column) do
      raise ArgumentError, "列がありません: #{column}"
    end

    %{
      身長: number(row, :身長),
      体重: number(row, :体重),
      年代: number(row, :年代),
      派閥: row.派閥
    }
  end

  defp number(row, column) do
    cell = Map.fetch!(row, column)

    case Integer.parse(cell) do
      {value, ""} -> value
      _ -> raise ArgumentError, "#{column} を数値として読めません: #{cell}"
    end
  end
end
