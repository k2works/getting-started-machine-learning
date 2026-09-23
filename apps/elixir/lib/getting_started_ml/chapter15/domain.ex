defmodule GettingStartedMl.Chapter15.ModelNotFoundError do
  @moduledoc """
  学習済みモデルが無いことを表す例外。

  この章のほかの失敗は `ArgumentError` のままだが、これだけは別の例外にする。
  API が 503 に変えるために、ほかの失敗と見分けられなければならないからである。
  メッセージにファイルのパスを含めないのは、503 の応答としてそのまま外に出るため。
  """

  defexception [:model]

  @impl true
  def message(%{model: model}), do: "学習済みモデル #{model} が見つかりません"
end

defmodule GettingStartedMl.Chapter15.Domain do
  @moduledoc """
  第 15 章のドメイン層。予測の入力と、既存のモデルを約束に合わせるアダプター。

  HTTP にも保存の形式にも依存しない。

  モデルそのものには behaviour を作らない。第 8 章の前処理と同じく、モデルは
  「特徴量を受け取って予測を返す関数」でよい。Elixir の無名関数はそのまま値なので、
  包むモジュールを作らずにアダプターが書ける。
  """

  alias GettingStartedMl.{Chapter07, Chapter08}

  @sales_model "cinema"
  @survival_model "survived"
  @survived 1

  @doc "興行収入のモデルの名前。"
  def sales_model, do: @sales_model

  @doc "生存予測のモデルの名前。"
  def survival_model, do: @survival_model

  @doc "モデルの名前を、置き場の順で並べたリスト。ヘルスチェックの並びもこれに従う。"
  def model_names, do: [@sales_model, @survival_model]

  @doc "第 7 章の線形回帰のモデルを、興行収入のモデルの約束（映画 → 数値）に合わせる。"
  def linear_sales_model(model) do
    fn movie ->
      Chapter07.predict_one(model, %{
        SNS1: movie.sns1,
        SNS2: movie.sns2,
        actor: movie.actor,
        original: movie.original
      })
    end
  end

  @doc "第 8 章の学習済みパイプラインを、生存予測のモデルの約束（乗客 → 真偽値）に合わせる。"
  def pipeline_survival_model(pipeline) do
    fn passenger ->
      pipeline
      |> Chapter08.predict(Chapter08.features_table([passenger_row(passenger)]))
      |> hd()
      |> Kernel.==(@survived)
    end
  end

  # 乗客を、第 8 章のパイプラインが読む CSV と同じセルの文字列の行にする。
  # 分からない値は空欄にすると、学習のときに求めた中央値・最頻値で補完される。
  defp passenger_row(passenger) do
    %{
      Pclass: to_string(passenger.pclass),
      Sex: passenger.sex,
      Age: cell(passenger.age),
      SibSp: to_string(passenger.sib_sp),
      Parch: to_string(passenger.parch),
      Fare: to_string(passenger.fare),
      Embarked: cell(passenger.embarked)
    }
  end

  defp cell(nil), do: ""
  defp cell(value), do: to_string(value)
end
