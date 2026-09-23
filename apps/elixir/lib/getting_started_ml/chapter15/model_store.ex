defmodule GettingStartedMl.Chapter15.ModelStore do
  @moduledoc """
  学習済みモデルの置き場の約束。Java 版の `interface ModelStore`、
  Clojure 版の `defprotocol ModelStore` に当たる。

  Elixir で「約束」を表すのは **behaviour**（`@callback` を並べたモジュール）である。
  behaviour はモジュールに対する約束なので、置き場そのものは
  `{モジュール, 状態}` の組で表し、この組を受け取る関数で振り分ける。
  Plug が `{Plug のモジュール, 初期化した値}` を持ち回るのと同じ形である。

  behaviour が決めるのは **関数の名前と引数の数** だけで、
  「読み込めなければ ModelNotFoundError を投げる」という取り決めは書けない
  （テストの `StoreContract.check/2` で、本物と偽物の両方に確かめる）。
  """

  @typedoc "置き場。behaviour を実装したモジュールと、その状態の組。"
  @type t :: {module(), term()}

  @doc "映画の特徴量から興行収入を返す関数を読み込む。無ければ ModelNotFoundError を投げる。"
  @callback load_sales_model(state :: term()) :: (map() -> number())

  @doc "乗客の特徴量から生存するかどうかを返す関数を読み込む。無ければ ModelNotFoundError を投げる。"
  @callback load_survival_model(state :: term()) :: (map() -> boolean())

  @doc "置き場から、興行収入のモデルを読み込む。"
  def load_sales_model({module, state}), do: module.load_sales_model(state)

  @doc "置き場から、生存予測のモデルを読み込む。"
  def load_survival_model({module, state}), do: module.load_survival_model(state)
end
