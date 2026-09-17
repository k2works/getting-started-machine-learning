---
type: Article
title: "第18章: 並行処理システム"
description: "並行処理システムは、Elixir の強力な並行処理機能を活用して、イベント駆動、状態マシン、メッセージキューなどのパターンを実装します。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第18章: 並行処理システム

## はじめに

並行処理システムは、Elixir の強力な並行処理機能を活用して、イベント駆動、状態マシン、メッセージキューなどのパターンを実装します。OTP の GenServer と Agent を使用して、信頼性の高い並行処理を実現します。

## 1. イベントバス (EventBus)

### GenServer ベースの実装

```elixir
defmodule EventBus do
  use GenServer

  # クライアント API
  def start_link(opts \\ []) do
    name = Keyword.get(opts, :name, __MODULE__)
    GenServer.start_link(__MODULE__, %{}, name: name)
  end

  def subscribe(topic, handler, bus \\ __MODULE__) do
    GenServer.call(bus, {:subscribe, topic, handler})
  end

  def unsubscribe(topic, handler_id, bus \\ __MODULE__) do
    GenServer.call(bus, {:unsubscribe, topic, handler_id})
  end

  def publish(topic, message, bus \\ __MODULE__) do
    GenServer.call(bus, {:publish, topic, message})
  end

  def publish_async(topic, message, bus \\ __MODULE__) do
    GenServer.cast(bus, {:publish, topic, message})
  end

  # サーバー コールバック
  @impl true
  def init(_opts) do
    {:ok, %{subscriptions: %{}, next_id: 1}}
  end

  @impl true
  def handle_call({:subscribe, topic, handler}, _from, state) do
    id = state.next_id
    subscriptions = Map.update(
      state.subscriptions,
      topic,
      [{id, handler}],
      fn handlers -> [{id, handler} | handlers] end
    )
    {:reply, {:ok, id}, %{state | subscriptions: subscriptions, next_id: id + 1}}
  end

  @impl true
  def handle_call({:publish, topic, message}, _from, state) do
    handlers = Map.get(state.subscriptions, topic, [])
    results = Enum.map(handlers, fn {_id, handler} ->
      try do
        {:ok, handler.(message)}
      rescue
        e -> {:error, e}
      end
    end)
    {:reply, results, state}
  end

  @impl true
  def handle_cast({:publish, topic, message}, state) do
    handlers = Map.get(state.subscriptions, topic, [])
    Enum.each(handlers, fn {_id, handler} ->
      Task.start(fn -> handler.(message) end)
    end)
    {:noreply, state}
  end
end
```

### 使用例

```elixir
# イベントバスの起動
{:ok, _pid} = EventBus.start_link()

# トピックに購読
EventBus.subscribe(:user_created, fn user ->
  IO.puts("User created: #{user.name}")
end)

# イベントを発行
EventBus.publish(:user_created, %{name: "Alice"})
```

## 2. 状態マシン (StateMachine)

### GenServer による実装

```elixir
defmodule StateMachine do
  use GenServer

  defstruct [:state, :transitions, :log]

  def start_link(initial_state, transitions, opts \\ []) do
    name = Keyword.get(opts, :name, __MODULE__)
    GenServer.start_link(__MODULE__, {initial_state, transitions}, name: name)
  end

  def current_state(machine \\ __MODULE__) do
    GenServer.call(machine, :current_state)
  end

  def trigger(event, machine \\ __MODULE__) do
    GenServer.call(machine, {:trigger, event})
  end

  def history(machine \\ __MODULE__) do
    GenServer.call(machine, :history)
  end

  @impl true
  def init({initial_state, transitions}) do
    {:ok, %__MODULE__{
      state: initial_state,
      transitions: transitions,
      log: [{:init, initial_state, DateTime.utc_now()}]
    }}
  end

  @impl true
  def handle_call(:current_state, _from, machine) do
    {:reply, machine.state, machine}
  end

  @impl true
  def handle_call({:trigger, event}, _from, machine) do
    case get_transition(machine, event) do
      nil ->
        {:reply, {:error, :invalid_transition}, machine}

      {next_state, action} ->
        action && action.()
        log_entry = {event, next_state, DateTime.utc_now()}
        updated = %{machine | state: next_state, log: [log_entry | machine.log]}
        {:reply, {:ok, next_state}, updated}
    end
  end

  defp get_transition(machine, event) do
    machine.transitions
    |> Map.get(machine.state, %{})
    |> Map.get(event)
  end
end
```

### 電話システムの例

```elixir
defmodule PhoneSystem do
  def create_transitions do
    %{
      :on_hook => %{
        :lift_receiver => {:off_hook, nil}
      },
      :off_hook => %{
        :dial => {:dialing, nil},
        :hang_up => {:on_hook, nil}
      },
      :dialing => %{
        :connected => {:connected, nil},
        :busy => {:off_hook, fn -> IO.puts("Busy tone") end},
        :hang_up => {:on_hook, nil}
      },
      :connected => %{
        :hang_up => {:on_hook, nil},
        :remote_hang_up => {:off_hook, nil}
      }
    }
  end

  def start_phone do
    StateMachine.start_link(:on_hook, create_transitions(), name: :phone)
  end
end
```

## 3. メッセージキュー

### GenServer による実装

```elixir
defmodule MessageQueue do
  use GenServer

  def start_link(opts \\ []) do
    name = Keyword.get(opts, :name, __MODULE__)
    GenServer.start_link(__MODULE__, :queue.new(), name: name)
  end

  def enqueue(message, queue \\ __MODULE__) do
    GenServer.call(queue, {:enqueue, message})
  end

  def dequeue(queue \\ __MODULE__) do
    GenServer.call(queue, :dequeue)
  end

  def peek(queue \\ __MODULE__) do
    GenServer.call(queue, :peek)
  end

  def size(queue \\ __MODULE__) do
    GenServer.call(queue, :size)
  end

  @impl true
  def init(queue) do
    {:ok, queue}
  end

  @impl true
  def handle_call({:enqueue, message}, _from, queue) do
    {:reply, :ok, :queue.in(message, queue)}
  end

  @impl true
  def handle_call(:dequeue, _from, queue) do
    case :queue.out(queue) do
      {{:value, item}, new_queue} -> {:reply, {:ok, item}, new_queue}
      {:empty, _} -> {:reply, {:error, :empty}, queue}
    end
  end

  @impl true
  def handle_call(:peek, _from, queue) do
    case :queue.peek(queue) do
      {:value, item} -> {:reply, {:ok, item}, queue}
      :empty -> {:reply, {:error, :empty}, queue}
    end
  end

  @impl true
  def handle_call(:size, _from, queue) do
    {:reply, :queue.len(queue), queue}
  end
end
```

## 4. ワーカープール

### 並行タスク処理

```elixir
defmodule WorkerPool do
  use GenServer

  def start_link(worker_count, opts \\ []) do
    name = Keyword.get(opts, :name, __MODULE__)
    GenServer.start_link(__MODULE__, worker_count, name: name)
  end

  def submit(task, pool \\ __MODULE__) do
    GenServer.call(pool, {:submit, task})
  end

  def submit_async(task, pool \\ __MODULE__) do
    GenServer.cast(pool, {:submit, task})
  end

  def stats(pool \\ __MODULE__) do
    GenServer.call(pool, :stats)
  end

  @impl true
  def init(worker_count) do
    {:ok, %{
      workers: worker_count,
      active: 0,
      completed: 0,
      pending: :queue.new()
    }}
  end

  @impl true
  def handle_call({:submit, task}, from, state) do
    if state.active < state.workers do
      spawn_worker(task, from, self())
      {:noreply, %{state | active: state.active + 1}}
    else
      pending = :queue.in({task, from}, state.pending)
      {:noreply, %{state | pending: pending}}
    end
  end

  @impl true
  def handle_info({:task_complete, result, from}, state) do
    GenServer.reply(from, result)

    case :queue.out(state.pending) do
      {{:value, {task, pending_from}}, new_pending} ->
        spawn_worker(task, pending_from, self())
        {:noreply, %{state | pending: new_pending, completed: state.completed + 1}}

      {:empty, _} ->
        {:noreply, %{state | active: state.active - 1, completed: state.completed + 1}}
    end
  end

  defp spawn_worker(task, from, pool_pid) do
    Task.start(fn ->
      result = task.()
      send(pool_pid, {:task_complete, result, from})
    end)
  end
end
```

### 使用例

```elixir
# プールを起動（3ワーカー）
{:ok, _} = WorkerPool.start_link(3)

# タスクを投入
results = Enum.map(1..10, fn i ->
  WorkerPool.submit(fn ->
    Process.sleep(100)
    i * 2
  end)
end)

# 結果: [2, 4, 6, 8, 10, 12, 14, 16, 18, 20]
```

## 5. カウンター (Agent)

### シンプルな状態管理

```elixir
defmodule Counter do
  use Agent

  def start_link(initial \\ 0, opts \\ []) do
    name = Keyword.get(opts, :name, __MODULE__)
    Agent.start_link(fn -> initial end, name: name)
  end

  def value(counter \\ __MODULE__) do
    Agent.get(counter, & &1)
  end

  def increment(counter \\ __MODULE__) do
    Agent.update(counter, &(&1 + 1))
  end

  def increment_by(amount, counter \\ __MODULE__) do
    Agent.update(counter, &(&1 + amount))
  end

  def decrement(counter \\ __MODULE__) do
    Agent.update(counter, &(&1 - 1))
  end

  def reset(counter \\ __MODULE__) do
    Agent.update(counter, fn _ -> 0 end)
  end
end
```

## 6. 安全な停止処理

### Process.alive? によるチェック

並行プロセスを停止する際は、プロセスが生きているか確認が必要です：

```elixir
def stop(pid) do
  if Process.alive?(pid) do
    GenServer.stop(pid)
  end
end

# Agent の場合
def stop_counter(counter \\ __MODULE__) do
  pid = case counter do
    name when is_atom(name) -> Process.whereis(name)
    pid when is_pid(pid) -> pid
  end

  if pid && Process.alive?(pid) do
    Agent.stop(pid)
  end
end
```

### テストでのクリーンアップ

```elixir
setup do
  {:ok, pid} = EventBus.start_link(name: :test_bus)
  on_exit(fn ->
    if Process.alive?(pid), do: GenServer.stop(pid)
  end)
  %{bus: pid}
end
```

## 7. パターンの比較

### GenServer vs Agent

| GenServer | Agent |
|-----------|-------|
| 複雑な状態管理 | シンプルな状態管理 |
| 非同期メッセージ処理 | 同期的な更新 |
| タイムアウト、継続など | 状態の取得・更新のみ |
| コールバック関数を実装 | 匿名関数で操作 |

### 使い分けの指針

- **Agent**: 単純なキー値ストア、カウンター、キャッシュ
- **GenServer**: イベント処理、状態マシン、プロトコル実装

## 8. テストの例

```elixir
test "EventBus publish and subscribe" do
  {:ok, _} = EventBus.start_link(name: :test_bus)

  messages = Agent.start_link(fn -> [] end) |> elem(1)

  EventBus.subscribe(:test, fn msg ->
    Agent.update(messages, &[msg | &1])
  end, :test_bus)

  EventBus.publish(:test, "hello", :test_bus)
  EventBus.publish(:test, "world", :test_bus)

  assert Agent.get(messages, & &1) == ["world", "hello"]
end

test "StateMachine transitions" do
  transitions = %{
    :idle => %{start: {:running, nil}},
    :running => %{stop: {:idle, nil}}
  }

  {:ok, _} = StateMachine.start_link(:idle, transitions, name: :test_sm)

  assert StateMachine.current_state(:test_sm) == :idle
  assert StateMachine.trigger(:start, :test_sm) == {:ok, :running}
  assert StateMachine.current_state(:test_sm) == :running
end
```

## まとめ

Elixir の並行処理システムは、OTP の抽象化によって以下を実現します：

1. **信頼性**: GenServer のスーパービジョンツリーによる障害復旧
2. **スケーラビリティ**: 軽量プロセスによる大量の並行処理
3. **明確なプロトコル**: call/cast による同期/非同期の明示的な区別
4. **テスタビリティ**: 名前付きプロセスによるテスト容易性

これらのパターンを組み合わせることで、複雑な並行システムを安全に構築できます。
