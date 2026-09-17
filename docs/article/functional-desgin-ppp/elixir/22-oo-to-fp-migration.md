---
type: Article
title: "第22章: OO から FP への移行"
description: "オブジェクト指向プログラミング（OOP）から関数型プログラミング（FP）への移行パターンを解説します。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第22章: OO から FP への移行

## はじめに

オブジェクト指向プログラミング（OOP）から関数型プログラミング（FP）への移行パターンを解説します。既存の OOP 概念を Elixir でどのように表現するかを学びます。

## 1. クラスからモジュールへ

### OOP のクラス

```python
# Python での実装
class BankAccount:
    def __init__(self, owner, balance=0):
        self.owner = owner
        self.balance = balance

    def deposit(self, amount):
        self.balance += amount

    def withdraw(self, amount):
        if amount <= self.balance:
            self.balance -= amount
            return True
        return False
```

### Elixir での実装

```elixir
defmodule BankAccount do
  defstruct [:owner, balance: 0]

  def new(owner, balance \\ 0) do
    %__MODULE__{owner: owner, balance: balance}
  end

  def deposit(account, amount) when amount > 0 do
    %{account | balance: account.balance + amount}
  end

  def withdraw(account, amount) when amount > 0 do
    if amount <= account.balance do
      {:ok, %{account | balance: account.balance - amount}}
    else
      {:error, :insufficient_funds}
    end
  end
end
```

### 主な違い

| OOP | FP (Elixir) |
|-----|-------------|
| インスタンスを変更 | 新しい値を返す |
| `self.balance += amount` | `%{account | balance: ...}` |
| `return True/False` | `{:ok, ...}` / `{:error, ...}` |

## 2. 継承からコンポジションへ

### OOP の継承

```python
class Animal:
    def speak(self): pass

class Dog(Animal):
    def speak(self): return "Woof!"

class Cat(Animal):
    def speak(self): return "Meow!"
```

### Elixir のプロトコル

```elixir
defprotocol Speakable do
  def speak(animal)
end

defmodule Dog do
  defstruct [:name]
end

defmodule Cat do
  defstruct [:name]
end

defimpl Speakable, for: Dog do
  def speak(_dog), do: "Woof!"
end

defimpl Speakable, for: Cat do
  def speak(_cat), do: "Meow!"
end
```

### ビヘイビアによるインターフェース

```elixir
defmodule Serializable do
  @callback to_json(term()) :: String.t()
  @callback from_json(String.t()) :: {:ok, term()} | {:error, term()}
end

defmodule User do
  @behaviour Serializable

  @impl true
  def to_json(%__MODULE__{id: id, name: name}) do
    ~s({"id":#{id},"name":"#{name}"})
  end

  @impl true
  def from_json(json) do
    # パース処理
  end
end
```

### テンプレートメソッドの代替: 高階関数

```elixir
defmodule DataProcessor do
  def process(data, opts \\ []) do
    parser = Keyword.get(opts, :parser, &default_parse/1)
    validator = Keyword.get(opts, :validator, &default_validate/1)

    data
    |> parser.()
    |> validator.()
  end
end
```

## 3. ミュータブル状態からイミュータブル変換へ

### OOP のミュータブル状態

```python
class ShoppingCart:
    def __init__(self):
        self.items = []

    def add_item(self, item):
        self.items.append(item)

    def total(self):
        return sum(i.price for i in self.items)
```

### Elixir のイミュータブル

```elixir
defmodule ShoppingCart do
  defstruct items: []

  def new, do: %__MODULE__{}

  def add_item(cart, item) do
    %{cart | items: [item | cart.items]}
  end

  def total(cart) do
    cart.items
    |> Enum.map(&(&1.price * &1.quantity))
    |> Enum.sum()
  end
end
```

### 状態遷移パターン

```elixir
defmodule OrderState do
  def transition(%{status: :pending} = order, :confirm) do
    {:ok, Map.put(order, :status, :confirmed)}
  end

  def transition(%{status: :confirmed} = order, :ship) do
    {:ok, Map.put(order, :status, :shipped)}
  end

  def transition(%{status: status}, action) do
    {:error, {:invalid_transition, status, action}}
  end
end
```

### イベントソーシング

```elixir
defmodule Account do
  defstruct [:id, :balance, :events]

  def apply_event(account, {:deposited, amount, timestamp}) do
    %{account |
      balance: account.balance + amount,
      events: [{:deposited, amount, timestamp} | account.events]
    }
  end

  def deposit(account, amount) when amount > 0 do
    event = {:deposited, amount, DateTime.utc_now()}
    {:ok, apply_event(account, event)}
  end

  def replay(account, events) do
    Enum.reduce(events, account, &apply_event(&2, &1))
  end
end
```

## 4. デザインパターンの変換

### Strategy → 高階関数

```elixir
# OOP: Strategyインターフェースと実装クラス
# FP: 関数を直接渡す
def sort(items, comparator \\ &<=/2) do
  Enum.sort(items, comparator)
end

# 事前定義された戦略
def by_name, do: fn a, b -> a.name <= b.name end
def by_price, do: fn a, b -> a.price <= b.price end
```

### Observer → メッセージパッシング

```elixir
defmodule EventNotifier do
  use GenServer

  def subscribe(pid, subscriber) do
    GenServer.call(pid, {:subscribe, subscriber})
  end

  def notify(pid, event) do
    GenServer.cast(pid, {:notify, event})
  end

  @impl true
  def handle_cast({:notify, event}, state) do
    Enum.each(state.subscribers, fn {_id, sub} ->
      send(sub, {:event, event})
    end)
    {:noreply, state}
  end
end
```

### Decorator → 関数合成

```elixir
def log(message), do: "[LOG] #{message}"

def with_timestamp(log_fn) do
  fn message ->
    timestamp = DateTime.utc_now() |> DateTime.to_string()
    log_fn.("[#{timestamp}] #{message}")
  end
end

def with_level(log_fn, level) do
  fn message ->
    log_fn.("[#{level}] #{message}")
  end
end

# 合成
logger = (&log/1) |> with_timestamp() |> with_level(:info)
```

### Command → データ

```elixir
def execute({:create_user, name, email}) do
  {:ok, %{id: generate_id(), name: name, email: email}}
end

def execute({:delete_user, id}) do
  {:ok, {:deleted, id}}
end

# バッチ実行
def execute_batch(commands) do
  Enum.map(commands, &execute/1)
end
```

### Visitor → パターンマッチ

```elixir
# 式の評価
def eval({:number, n}), do: n
def eval({:add, a, b}), do: eval(a) + eval(b)
def eval({:mul, a, b}), do: eval(a) * eval(b)

# 式の最適化
def optimize({:add, {:number, 0}, b}), do: optimize(b)
def optimize({:mul, {:number, 1}, b}), do: optimize(b)
def optimize({:mul, {:number, 0}, _}), do: {:number, 0}
def optimize(expr), do: expr
```

## 5. 実践的な移行例

### ユーザー管理システム

```elixir
# データ定義
defmodule User do
  defstruct [:id, :name, :email, :role, :active, created_at: nil]

  def new(attrs) do
    Map.merge(%__MODULE__{
      id: generate_id(),
      active: true,
      created_at: DateTime.utc_now()
    }, Map.new(attrs))
  end
end

# 変換関数（純粋）
defmodule Transformations do
  def activate(user), do: %{user | active: true}
  def deactivate(user), do: %{user | active: false}

  def change_role(user, role) when role in [:admin, :user, :guest] do
    {:ok, %{user | role: role}}
  end
end

# クエリ関数（純粋）
defmodule Queries do
  def active_users(users), do: Enum.filter(users, & &1.active)
  def by_role(users, role), do: Enum.filter(users, &(&1.role == role))
end

# リポジトリ（状態あり）
defmodule Repository do
  use Agent

  def add(user), do: Agent.update(__MODULE__, &Map.put(&1, user.id, user))
  def get(id), do: Agent.get(__MODULE__, &Map.get(&1, id))
end

# サービス層
defmodule Service do
  def create_user(attrs) do
    user = User.new(attrs)
    Repository.add(user)
    {:ok, user}
  end

  def promote_to_admin(user_id) do
    with user when not is_nil(user) <- Repository.get(user_id),
         {:ok, updated} <- Transformations.change_role(user, :admin) do
      Repository.update(updated)
      {:ok, updated}
    else
      nil -> {:error, :not_found}
      error -> error
    end
  end
end
```

## 6. 移行のガイドライン

| OOP 概念 | FP 代替 |
|---------|---------|
| クラス | モジュール + 構造体 |
| インスタンス変数 | 構造体のフィールド |
| メソッド | モジュール関数 |
| 継承 | プロトコル、ビヘイビア |
| ポリモーフィズム | パターンマッチ |
| ミュータブル状態 | 変換関数、GenServer/Agent |
| 例外 | タグ付きタプル |
| null | `nil` + パターンマッチ |

## まとめ

OOP から FP への移行で重要なポイント：

1. **データと振る舞いの分離**: 構造体はデータのみ、関数は変換
2. **不変性の受け入れ**: 変更ではなく新しい値を生成
3. **明示的な状態管理**: GenServer/Agent で状態を局所化
4. **パターンマッチの活用**: 条件分岐をデータ構造で表現
5. **関数の合成**: 小さな純粋関数を組み合わせる

最初は違和感がありますが、慣れると FP の方が理解しやすく、テストしやすいコードになります。
