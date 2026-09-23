defmodule GettingStartedMl.Chapter15.Api do
  @moduledoc """
  第 15 章のプレゼンテーション層。Plug の予測 API。

  Plug では、ハンドラーは `init/1` と `call/2` を持つモジュールである。
  `call/2` は `%Plug.Conn{}` を受け取って `%Plug.Conn{}` を返すただの関数なので、
  テストはこの関数を直に呼べばよく、サーバーを起動しなくてよい。

  経路の振り分けのライブラリ（`Plug.Router`・Phoenix）は使わず、
  `call/2` の関数節のパターンマッチでメソッドとパスを分ける。
  """

  import Plug.Conn

  alias GettingStartedMl.Chapter15.{ModelNotFoundError, Service, Validation}

  @behaviour Plug

  @invalid_json "JSON の形式または値の型が正しくありません"

  # パスごとに許しているメソッド。知っているパスに違うメソッドが来たら 405 にする。
  @allowed_methods %{"/health" => "GET", "/cinema/sales" => "POST", "/survived" => "POST"}

  @impl true
  def init(store), do: store

  @impl true
  def call(%Plug.Conn{method: "GET", request_path: "/health"} = conn, store) do
    models = Service.health(store)
    status = if Enum.all?(models, fn {_model, ready} -> ready end), do: "ok", else: "degraded"
    json(conn, 200, %{status: status, models: models})
  end

  def call(%Plug.Conn{method: "POST", request_path: "/cinema/sales"} = conn, store) do
    predict(conn, Validation.movie_types(), &Validation.movie/1, fn movie ->
      %{sales: Service.predict_sales(store, movie)}
    end)
  end

  def call(%Plug.Conn{method: "POST", request_path: "/survived"} = conn, store) do
    predict(conn, Validation.passenger_types(), &Validation.passenger/1, fn passenger ->
      %{survived: Service.predict_survival(store, passenger)}
    end)
  end

  def call(conn, _store) do
    case Map.fetch(@allowed_methods, conn.request_path) do
      {:ok, allow} ->
        conn
        |> put_resp_header("allow", allow)
        |> json(405, %{detail: "許していないメソッドです"})

      :error ->
        json(conn, 404, %{detail: "見つかりません"})
    end
  end

  # 本文を読んで検証し、正しければ build で予測する。失敗はステータスコードに変える。
  defp predict(conn, types, validate, build) do
    {:ok, body, conn} = read_body(conn)

    with {:ok, request} <- Validation.read_json(body, types),
         %{value: value, errors: []} <- validate.(request) do
      json(conn, 200, build.(value))
    else
      :error -> json(conn, 422, %{detail: [@invalid_json]})
      %{errors: errors} -> json(conn, 422, %{detail: errors})
    end
  rescue
    e in ModelNotFoundError -> json(conn, 503, %{detail: Exception.message(e)})
    # 例外のメッセージには内部の事情が入るので、応答には出さない
    _other -> json(conn, 500, %{detail: "予測できませんでした"})
  end

  defp json(conn, status, body) do
    conn
    |> put_resp_content_type("application/json")
    |> send_resp(status, Jason.encode!(body))
  end
end
