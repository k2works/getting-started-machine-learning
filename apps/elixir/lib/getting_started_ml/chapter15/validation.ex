defmodule GettingStartedMl.Chapter15.Validation do
  @moduledoc """
  第 15 章のプレゼンテーション層。要求の JSON を読み、検証してドメインの値にする。

  Jason は JSON の値をそのまま Elixir のマップ・数値・文字列にするので、
  Java 版のように「読み込みと同時に型が確かめられる」ことはない。型の確認は自分で書く。
  """

  @movie_types %{"sns1" => :number, "sns2" => :number, "actor" => :number, "original" => :integer}

  @passenger_types %{
    "pclass" => :integer,
    "sex" => :string,
    "age" => :number,
    "sib_sp" => :integer,
    "parch" => :integer,
    "fare" => :number,
    "embarked" => :string
  }

  @original_values [0, 1]
  @passenger_classes [1, 2, 3]
  @sexes ["female", "male"]
  @ports ["C", "Q", "S"]

  @doc "興行収入の予測の要求の列と型。原作の有無は整数で受け取る（1.5 を弾くため）。"
  def movie_types, do: @movie_types

  @doc "生存の予測の要求の列と型。年齢と乗船港は省略できる。"
  def passenger_types, do: @passenger_types

  @doc """
  本文を JSON のオブジェクトとして読み、列の型を確かめる。

  読めれば `{:ok, マップ}`、読めないか型が合わなければ `:error` を返す。
  """
  def read_json(body, types) do
    with {:ok, request} <- Jason.decode(body),
         true <- is_map(request),
         true <- Enum.all?(request, fn {field, value} -> typed?(Map.get(types, field), value) end) do
      {:ok, request}
    else
      _other -> :error
    end
  end

  # 値が型に合うかどうか。null と、表に無い列（型が nil）は問わない。
  defp typed?(_type, nil), do: true
  defp typed?(nil, _value), do: true
  defp typed?(:number, value), do: is_number(value)
  defp typed?(:integer, value), do: is_integer(value)
  defp typed?(:string, value), do: is_binary(value)

  ## 検証の規則。問題が無ければ nil を、あれば理由を返す

  @doc "必須の列が空なら理由を返す。"
  def required(field, nil), do: "#{field} は必須です"
  def required(_field, _value), do: nil

  @doc "負の数なら理由を返す。"
  def not_negative(field, value) when is_number(value) and value < 0,
    do: "#{field} は 0 以上にしてください"

  def not_negative(_field, _value), do: nil

  @doc "選択肢の外の値なら理由を返す。選択肢は並べ替えて表示する。"
  def one_of(_field, nil, _allowed), do: nil

  def one_of(field, value, allowed) do
    if value in allowed do
      nil
    else
      "#{field} は #{Enum.map_join(Enum.sort(allowed), "、", &to_string/1)} のどれかにしてください"
    end
  end

  @doc """
  理由（nil は問題なし）を集め、1 つも無ければ `build` で値を作る。

  Java 版の `sealed interface Validated` と違い、値と理由を両方持つマップで表す。
  `build` を関数で受け取るのは、理由があるときに値を作らせないためである。
  """
  def validate(reasons, build) do
    case Enum.reject(reasons, &is_nil/1) do
      [] -> %{value: build.(), errors: []}
      errors -> %{value: nil, errors: errors}
    end
  end

  @doc "検証の結果が正しいかどうか。"
  def valid?(%{errors: errors}), do: errors == []

  @doc "検証して、正しければ映画の特徴量にする。"
  def movie(request) do
    %{"sns1" => sns1, "sns2" => sns2, "actor" => actor, "original" => original} =
      Map.merge(%{"sns1" => nil, "sns2" => nil, "actor" => nil, "original" => nil}, request)

    validate(
      [
        required("sns1", sns1),
        required("sns2", sns2),
        required("actor", actor),
        required("original", original),
        not_negative("sns1", sns1),
        not_negative("sns2", sns2),
        not_negative("actor", actor),
        one_of("original", original, @original_values)
      ],
      fn ->
        %{
          sns1: sns1 / 1,
          sns2: sns2 / 1,
          actor: actor / 1,
          original: original
        }
      end
    )
  end

  @doc "検証して、正しければ乗客の特徴量にする。年齢と乗船港は省略できる。"
  def passenger(request) do
    %{
      "pclass" => pclass,
      "sex" => sex,
      "age" => age,
      "sib_sp" => sib_sp,
      "parch" => parch,
      "fare" => fare,
      "embarked" => embarked
    } = Map.merge(Map.new(Map.keys(@passenger_types), &{&1, nil}), request)

    validate(
      [
        required("pclass", pclass),
        required("sex", sex),
        required("sib_sp", sib_sp),
        required("parch", parch),
        required("fare", fare),
        one_of("pclass", pclass, @passenger_classes),
        one_of("sex", sex, @sexes),
        not_negative("age", age),
        not_negative("sib_sp", sib_sp),
        not_negative("parch", parch),
        not_negative("fare", fare),
        one_of("embarked", embarked, @ports)
      ],
      fn ->
        %{
          pclass: pclass,
          sex: sex,
          age: age && age / 1,
          sib_sp: sib_sp,
          parch: parch,
          fare: fare / 1,
          embarked: embarked
        }
      end
    )
  end
end
