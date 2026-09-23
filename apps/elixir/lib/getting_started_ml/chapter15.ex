defmodule GettingStartedMl.Chapter15 do
  @moduledoc """
  第 15 章: 機械学習 API とモジュール設計。

  第 7・8 章のモデルを学習して保存し、Plug と Bandit で予測 API を起動する。
  """

  alias GettingStartedMl.{Chapter02, Chapter07, Chapter08, Dataset}
  alias GettingStartedMl.Chapter15.{Api, Domain, FileStore, Service}

  @model_dir "model"
  @port 8015

  # 自分のマシンからだけ接続できるループバックのアドレス。
  @host {127, 0, 0, 1}

  @test_size 0.2
  @seed 0
  @max_depth 5

  @doc "学習済みモデルの保存先（apps/elixir/model/ は .gitignore の対象）。"
  def model_dir, do: @model_dir

  @doc "予測 API のポート。"
  def port, do: @port

  @doc "第 7・8 章と同じ条件でモデルを学習し、置き場に保存する。"
  def train_and_save_models(data_dir, store) do
    cinema = Chapter07.prepare_cinema(Path.join(data_dir, "cinema.csv"), @test_size, @seed)

    FileStore.save_sales_model(
      store,
      Chapter07.fit(cinema.x_train, cinema.t_train, Chapter07.feature_columns())
    )

    FileStore.save_survival_model(
      store,
      survival_pipeline(Path.join(data_dir, "Survived.csv"))
    )
  end

  @doc "モデルを学習して保存し、予測 API を起動する。Ctrl-C で止まるまで戻らない。"
  def run(model_dir \\ @model_dir) do
    store = FileStore.new(model_dir)
    train_and_save_models(Dataset.dir(), store)

    for model <- Domain.model_names() do
      IO.puts("モデル #{model}: #{Map.fetch!(Service.health(store), model)}")
    end

    {:ok, _pid} = Bandit.start_link(plug: {Api, store}, ip: @host, port: @port)
    IO.puts("http://127.0.0.1:#{@port} で待ち受けます")
    Process.sleep(:infinity)
  end

  # 第 8 章と同じ条件で、生存予測のパイプラインを学習する。
  defp survival_pipeline(path) do
    rows = Chapter02.load_table(path).rows
    t = Chapter08.target_labels(rows)
    split = Chapter02.split_train_test(rows, t, @test_size, @seed)

    Chapter08.fit(
      Chapter08.build_pipeline(@max_depth, :balanced),
      Chapter08.features_table(split.x_train),
      split.t_train
    )
  end
end
