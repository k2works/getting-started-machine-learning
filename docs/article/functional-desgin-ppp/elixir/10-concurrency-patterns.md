---
type: Article
title: "Chapter 10: 並行処理パターン"
description: "Elixir における並行処理パターンを学びます。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Chapter 10: 並行処理パターン

Elixir における並行処理パターンを学びます。アクターモデルと関数型プログラミングを組み合わせた安全な並行処理を実装します。

## 概要

Elixir は Erlang VM 上で動作し、軽量プロセスによる並行処理をサポートします。このチャプターでは、プロセス、Agent、GenServer、Task を使った並行処理パターンを紹介します。

## 主なトピック

1. **プロセスとメッセージパッシング**
2. **Agent（状態管理）**
3. **GenServer（汎用サーバー）**
4. **Task（非同期処理）**
5. **並列処理パターン**
6. **同期パターン**

## 基本的なプロセス

```elixir
defmodule BasicProcess do
  def start_echo do
    spawn(fn -> echo_loop() end)
  end

  defp echo_loop do
    receive do
      {:echo, from, message} ->
        send(from, {:echoed, message})
        echo_loop()
      :stop -> :ok
    end
  end
end

# 使用例
pid = BasicProcess.start_echo()
send(pid, {:echo, self(), "hello"})
receive do
  {:echoed, msg} -> msg  # => "hello"
end
```

## Agent（状態管理）

```elixir
defmodule Counter do
  def start(initial_value \\ 0) do
    Agent.start_link(fn -> initial_value end)
  end

  def get(counter), do: Agent.get(counter, & &1)
  def increment(counter), do: Agent.update(counter, &(&1 + 1))
  def decrement(counter), do: Agent.update(counter, &(&1 - 1))
  def add(counter, value), do: Agent.update(counter, &(&1 + value))
  def reset(counter), do: Agent.update(counter, fn _ -> 0 end)
end

# 並行アクセスでも安全
{:ok, counter} = Counter.start(0)
tasks = for _ <- 1..100 do
  Task.async(fn -> Counter.increment(counter) end)
end
Enum.each(tasks, &Task.await/1)
Counter.get(counter)  # => 100
```

## GenServer

```elixir
defmodule BankAccount do
  use GenServer

  # Client API
  def open(account_id, initial_balance \\ 0) do
    GenServer.start_link(__MODULE__, %{id: account_id, balance: initial_balance})
  end

  def balance(account), do: GenServer.call(account, :balance)
  def deposit(account, amount), do: GenServer.call(account, {:deposit, amount})
  def withdraw(account, amount), do: GenServer.call(account, {:withdraw, amount})

  # Server Callbacks
  @impl true
  def init(state), do: {:ok, state}

  @impl true
  def handle_call(:balance, _from, state) do
    {:reply, state.balance, state}
  end

  @impl true
  def handle_call({:deposit, amount}, _from, state) when amount > 0 do
    new_balance = state.balance + amount
    {:reply, {:ok, new_balance}, %{state | balance: new_balance}}
  end

  @impl true
  def handle_call({:withdraw, amount}, _from, state) do
    if state.balance >= amount do
      new_balance = state.balance - amount
      {:reply, {:ok, new_balance}, %{state | balance: new_balance}}
    else
      {:reply, {:error, "残高不足"}, state}
    end
  end
end
```

## Task（非同期処理）

```elixir
defmodule AsyncTasks do
  def parallel_execute(functions) do
    functions
    |> Enum.map(&Task.async/1)
    |> Enum.map(&Task.await/1)
  end

  def parallel_map(list, fun) do
    list
    |> Enum.map(fn item -> Task.async(fn -> fun.(item) end) end)
    |> Enum.map(&Task.await/1)
  end

  def with_timeout(fun, timeout_ms) do
    task = Task.async(fun)
    case Task.yield(task, timeout_ms) || Task.shutdown(task) do
      {:ok, result} -> {:ok, result}
      nil -> {:error, :timeout}
    end
  end

  def race(functions) do
    parent = self()
    pids = Enum.map(functions, fn fun ->
      spawn(fn ->
        result = fun.()
        send(parent, {:result, self(), result})
      end)
    end)

    receive do
      {:result, _pid, result} ->
        Enum.each(pids, fn pid ->
          if Process.alive?(pid), do: Process.exit(pid, :kill)
        end)
        result
    end
  end
end
```

## 並列処理パターン

### Map-Reduce

```elixir
def map_reduce(data, mapper, reducer) do
  data
  |> Task.async_stream(mapper, ordered: false)
  |> Enum.map(fn {:ok, result} -> result end)
  |> reducer.()
end

# 使用例
data = [1, 2, 3, 4, 5]
map_reduce(data, &(&1 * 2), &Enum.sum/1)  # => 30
```

### ファンアウト・ファンイン

```elixir
def fan_out_fan_in(data, workers) do
  workers
  |> Enum.map(fn worker -> Task.async(fn -> worker.(data) end) end)
  |> Enum.map(&Task.await/1)
end
```

### バッチ処理

```elixir
def batch_process(data, batch_size, processor) do
  data
  |> Enum.chunk_every(batch_size)
  |> Task.async_stream(processor)
  |> Enum.map(fn {:ok, result} -> result end)
end
```

## 同期パターン

### セマフォ

```elixir
def semaphore(max_permits) do
  Agent.start_link(fn -> max_permits end)
end

def acquire(sem) do
  case Agent.get_and_update(sem, fn
    0 -> {0, 0}
    n -> {n, n - 1}
  end) do
    0 -> Process.sleep(10); acquire(sem)
    _ -> :ok
  end
end

def release(sem) do
  Agent.update(sem, &(&1 + 1))
end
```

### リソースプール

```elixir
def resource_pool(resources) do
  Agent.start_link(fn -> resources end)
end

def borrow(pool) do
  Agent.get_and_update(pool, fn
    [] -> {{:error, :empty}, []}
    [head | tail] -> {{:ok, head}, tail}
  end)
end

def return(pool, resource) do
  Agent.update(pool, fn resources -> [resource | resources] end)
end
```

## イベントバス

```elixir
defmodule EventBus do
  def start, do: Agent.start_link(fn -> %{} end)

  def subscribe(bus, event_type, subscriber) do
    Agent.update(bus, fn subscriptions ->
      current = Map.get(subscriptions, event_type, [])
      Map.put(subscriptions, event_type, [subscriber | current])
    end)
  end

  def publish(bus, event_type, payload) do
    subscribers = Agent.get(bus, fn subs -> Map.get(subs, event_type, []) end)
    Enum.each(subscribers, fn sub -> send(sub, {:event, event_type, payload}) end)
    :ok
  end
end
```

## まとめ

- **プロセス**で軽量な並行処理
- **Agent**で状態を安全に管理
- **GenServer**でステートフルなサーバーを実装
- **Task**で非同期処理を簡潔に
- **並列パターン**（Map-Reduce、ファンアウト・ファンイン）で効率的な処理
- **同期パターン**（セマフォ、リソースプール）で競合を制御

## 関連リソース

- [Elixir GenServer](https://hexdocs.pm/elixir/GenServer.html)
- [Elixir Task](https://hexdocs.pm/elixir/Task.html)
- [The Actor Model](https://www.youtube.com/watch?v=7erJ1DV_Tlo)
