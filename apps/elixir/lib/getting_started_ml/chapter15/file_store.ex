defmodule GettingStartedMl.Chapter15.FileStore do
  @moduledoc """
  第 15 章のインフラ層。学習済みモデルをディレクトリのファイルに保存し、読み込む。

  第 8 章で、学習済みのパイプラインが `:erlang.term_to_binary/1` と
  `:erlang.binary_to_term/2` の往復で戻ることを確かめてある。
  第 7 章の線形回帰のモデルもマップとリストなので、同じやり方で保存できる。

  保存の関数は behaviour に入れない。読み込みは API が使うが、保存は学習のときにしか
  使わないので、API から見える約束を小さく保つ。
  """

  alias GettingStartedMl.Chapter08
  alias GettingStartedMl.Chapter15.{Domain, ModelNotFoundError}

  @behaviour GettingStartedMl.Chapter15.ModelStore

  @doc "ディレクトリを置き場にする。ディレクトリはまだ無くてもよい。"
  def new(model_dir), do: {__MODULE__, model_dir}

  @impl true
  def load_sales_model(model_dir) do
    model_dir
    |> read_model(Domain.sales_model(), &:erlang.binary_to_term(File.read!(&1), [:safe]))
    |> Domain.linear_sales_model()
  end

  @impl true
  def load_survival_model(model_dir) do
    model_dir
    |> read_model(Domain.survival_model(), &Chapter08.load_model/1)
    |> Domain.pipeline_survival_model()
  end

  @doc "第 7 章の線形回帰のモデルを保存する。"
  def save_sales_model({__MODULE__, model_dir}, model) do
    path = model_file(model_dir, Domain.sales_model())
    File.mkdir_p!(Path.dirname(path))
    File.write!(path, :erlang.term_to_binary(model))
  end

  @doc "第 8 章の学習済みパイプラインを、第 8 章の save_model で保存する。"
  def save_survival_model({__MODULE__, model_dir}, pipeline) do
    Chapter08.save_model(pipeline, model_file(model_dir, Domain.survival_model()))
  end

  defp model_file(model_dir, model), do: Path.join(model_dir, "#{model}.model")

  # ファイルを読む。無ければ「モデルが無い」に変える。
  defp read_model(model_dir, model, read) do
    path = model_file(model_dir, model)
    unless File.exists?(path), do: raise(ModelNotFoundError, model: model)
    read.(path)
  end
end
