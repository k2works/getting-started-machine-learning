defmodule GettingStartedMl.Dataset do
  @moduledoc "学習データのディレクトリを求める。"

  @env_name "ML_DATA_DIR"
  @default_dir "../data/sukkiri-ml"

  @doc """
  学習データのディレクトリを返す。

  環境変数はテストで差し替えられるように引数で受け取る。
  """
  def dir(env \\ System.get_env()) do
    case Map.get(env, @env_name) do
      nil -> @default_dir
      "" -> @default_dir
      value -> value
    end
  end
end
