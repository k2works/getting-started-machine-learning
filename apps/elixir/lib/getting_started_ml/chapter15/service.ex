defmodule GettingStartedMl.Chapter15.Service do
  @moduledoc """
  第 15 章のアプリケーション層。置き場からモデルを読み込んで予測する。HTTP を知らない。

  置き場は `ModelStore` の約束を満たすものなら何でもよいので、テストでは偽物を渡せる。
  状態を持たないので、同時に走るハンドラーから呼ばれても困らない。
  """

  alias GettingStartedMl.Chapter15.{Domain, ModelNotFoundError, ModelStore}

  @doc "映画の特徴量から興行収入を予測する。"
  def predict_sales(store, movie), do: ModelStore.load_sales_model(store).(movie)

  @doc "乗客の特徴量から生存するかどうかを予測する。"
  def predict_survival(store, passenger), do: ModelStore.load_survival_model(store).(passenger)

  @doc """
  モデルごとに、読み込めるかどうかを返す。

  Elixir のマップは並びを持たないので、Clojure 版の `array-map` に当たるものは無い。
  並びが要るのは JSON にするときだけで、Jason はキーを並べ替えて書き出すため、
  `cinema`・`survived` の順は結果として保たれる（`Domain.model_names/0` の順とも一致する）。
  """
  def health(store) do
    %{
      Domain.sales_model() => ready?(&ModelStore.load_sales_model/1, store),
      Domain.survival_model() => ready?(&ModelStore.load_survival_model/1, store)
    }
  end

  # モデルを読み込めるかどうか。読み込めない理由がほかにあれば、そのまま投げる。
  defp ready?(load, store) do
    load.(store)
    true
  rescue
    ModelNotFoundError -> false
  end
end
