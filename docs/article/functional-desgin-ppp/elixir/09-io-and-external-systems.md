---
type: Article
title: "Chapter 09: I/O と外部システム"
description: "Elixir における I/O 操作と外部システムとの連携を関数型に扱う方法を学びます。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Chapter 09: I/O と外部システム

Elixir における I/O 操作と外部システムとの連携を関数型に扱う方法を学びます。

## 概要

I/O 操作や外部システムとの連携は副作用を伴います。このチャプターでは、これらを抽象化してテスト可能にするパターンを紹介します。

## 主なトピック

1. **I/O の抽象化**
2. **ファイルシステム操作**
3. **リポジトリパターン**
4. **HTTP クライアントの抽象化**
5. **設定管理**
6. **トランザクション風操作**

## コンソール I/O の抽象化

```elixir
defmodule IO.Console do
  @type console :: %{
    read: (() -> String.t()),
    write: (String.t() -> :ok)
  }

  def standard do
    %{
      read: fn -> IO.gets("") |> String.trim() end,
      write: fn msg -> IO.puts(msg); :ok end
    }
  end

  def test_console(inputs) do
    {:ok, input_agent} = Agent.start_link(fn -> inputs end)
    {:ok, output_agent} = Agent.start_link(fn -> [] end)

    console = %{
      read: fn ->
        Agent.get_and_update(input_agent, fn
          [head | tail] -> {head, tail}
          [] -> {"", []}
        end)
      end,
      write: fn msg ->
        Agent.update(output_agent, fn outputs -> outputs ++ [msg] end)
        :ok
      end
    }

    get_output = fn -> Agent.get(output_agent, & &1) end
    {console, get_output}
  end
end
```

## ファイルシステムの抽象化

```elixir
defmodule IO.FileSystem do
  def in_memory(initial_files \\ %{}) do
    {:ok, agent} = Agent.start_link(fn -> initial_files end)

    fs = %{
      read: fn path ->
        case Agent.get(agent, &Map.get(&1, path)) do
          nil -> {:error, :enoent}
          content -> {:ok, content}
        end
      end,
      write: fn path, content ->
        Agent.update(agent, &Map.put(&1, path, content))
        :ok
      end,
      exists?: fn path -> Agent.get(agent, &Map.has_key?(&1, path)) end,
      delete: fn path -> Agent.update(agent, &Map.delete(&1, path)); :ok end
    }

    get_state = fn -> Agent.get(agent, & &1) end
    {fs, get_state}
  end
end
```

## リポジトリパターン

```elixir
defmodule Repository do
  def in_memory do
    {:ok, agent} = Agent.start_link(fn -> %{} end)

    %{
      find: fn id ->
        case Agent.get(agent, &Map.get(&1, id)) do
          nil -> {:error, :not_found}
          entity -> {:ok, entity}
        end
      end,
      find_all: fn -> Agent.get(agent, &Map.values(&1)) end,
      save: fn entity ->
        Agent.update(agent, &Map.put(&1, entity.id, entity))
        {:ok, entity}
      end,
      delete: fn id ->
        Agent.update(agent, &Map.delete(&1, id))
        :ok
      end,
      exists?: fn id -> Agent.get(agent, &Map.has_key?(&1, id)) end
    }
  end

  def find_by(repo, predicate) do
    repo.find_all.() |> Enum.filter(predicate)
  end
end
```

## HTTP クライアントの抽象化

```elixir
defmodule Http do
  def test_client(responses) do
    make_request = fn url ->
      case Map.get(responses, url) do
        nil -> {:error, :not_found}
        response -> {:ok, response}
      end
    end

    %{
      get: make_request,
      post: fn url, _body -> make_request.(url) end,
      put: fn url, _body -> make_request.(url) end,
      delete: make_request
    }
  end
end

# 使用例
responses = %{
  "https://api.example.com/users/1" => %{
    status: 200,
    body: ~s({"id": "1", "name": "Alice"})
  }
}

client = Http.test_client(responses)
{:ok, response} = client.get.("https://api.example.com/users/1")
```

## 設定管理

```elixir
defmodule Config do
  def from_map(config) do
    %{
      get: &Map.get(config, &1),
      get_all: fn -> config end
    }
  end

  def require(source, key) do
    case source.get.(key) do
      nil -> {:error, {:missing_config, key}}
      value -> {:ok, value}
    end
  end

  def get_or_default(source, key, default) do
    source.get.(key) || default
  end

  def get_integer(source, key, default) do
    case source.get.(key) do
      nil -> default
      value ->
        case Integer.parse(value) do
          {int, ""} -> int
          _ -> default
        end
    end
  end
end
```

## トランザクション風操作

```elixir
defmodule Transaction do
  def execute(operations) do
    do_execute(operations, [], [])
  end

  defp do_execute([], results, _rollbacks), do: {:ok, Enum.reverse(results)}
  defp do_execute([{operation, rollback} | rest], results, rollbacks) do
    case operation.() do
      {:ok, result} ->
        do_execute(rest, [result | results], [rollback | rollbacks])
      {:error, reason} ->
        Enum.each(rollbacks, fn rb -> rb.() end)
        {:error, reason}
    end
  end

  def with_resource(acquire, release, use) do
    case acquire.() do
      {:ok, resource} ->
        try do
          use.(resource)
        after
          release.(resource)
        end
      {:error, _} = error -> error
    end
  end
end
```

## サービス層の例

```elixir
defmodule UserService do
  def create_user(repo, params, id_generator, time_provider) do
    with :ok <- validate_name(params.name),
         :ok <- validate_email(params.email),
         false <- email_exists?(repo, params.email) do
      user = %{
        id: id_generator.(),
        name: params.name,
        email: params.email,
        created_at: time_provider.()
      }
      repo.save.(user)
      {:ok, user}
    else
      {:error, _} = error -> error
      true -> {:error, :email_already_exists}
    end
  end
end
```

## まとめ

- **I/O の抽象化**でテスト可能な設計
- **リポジトリパターン**でデータ永続化を抽象化
- **HTTP クライアントの抽象化**で外部 API をモック
- **設定管理**で環境依存を分離
- **トランザクション風操作**でリソース管理を安全に

## 関連リソース

- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Ports and Adapters](https://www.youtube.com/watch?v=th4AgBcrEHA)
