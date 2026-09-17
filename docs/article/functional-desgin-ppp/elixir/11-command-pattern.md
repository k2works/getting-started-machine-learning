---
type: Article
title: "第11章: Command パターン"
description: "Command パターンは、リクエストをオブジェクト（データ）としてカプセル化し、異なるリクエストでクライアントをパラメータ化したり、操作の履歴を記録したり、Undo/Redo 機能を実装できるようにするパターンです。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第11章: Command パターン

## はじめに

Command パターンは、リクエストをオブジェクト（データ）としてカプセル化し、異なるリクエストでクライアントをパラメータ化したり、操作の履歴を記録したり、Undo/Redo 機能を実装できるようにするパターンです。

Elixir では、プロトコルを使用してコマンドインターフェースを定義し、構造体でコマンドを表現します。

## 1. Command プロトコル

```elixir
defprotocol Command do
  @doc "コマンドを実行し、新しい状態を返す"
  def execute(command, state)

  @doc "コマンドを取り消し、以前の状態を返す"
  def undo(command, state)
end
```

## 2. テキスト操作コマンド

### InsertCommand

```elixir
defmodule InsertCommand do
  defstruct [:position, :text]

  def new(position, text), do: %__MODULE__{position: position, text: text}
end

defimpl Command, for: InsertCommand do
  def execute(%InsertCommand{position: pos, text: text}, document) do
    before = String.slice(document, 0, pos)
    after_text = String.slice(document, pos..-1//1)
    before <> text <> after_text
  end

  def undo(%InsertCommand{position: pos, text: text}, document) do
    len = String.length(text)
    before = String.slice(document, 0, pos)
    after_text = String.slice(document, (pos + len)..-1//1)
    before <> after_text
  end
end
```

### 使用例

```elixir
cmd = InsertCommand.new(5, " World")
result = Command.execute(cmd, "Hello")
# => "Hello World"

restored = Command.undo(cmd, "Hello World")
# => "Hello"
```

## 3. コマンド実行器

```elixir
defmodule CommandExecutor do
  defstruct [:state, undo_stack: [], redo_stack: []]

  def new(initial_state), do: %__MODULE__{state: initial_state}

  def execute(%__MODULE__{state: state, undo_stack: undo} = executor, command) do
    new_state = Command.execute(command, state)
    %{executor |
      state: new_state,
      undo_stack: [command | undo],
      redo_stack: []
    }
  end

  def undo(%__MODULE__{undo_stack: []} = executor), do: executor
  def undo(%__MODULE__{state: state, undo_stack: [cmd | rest], redo_stack: redo} = executor) do
    new_state = Command.undo(cmd, state)
    %{executor |
      state: new_state,
      undo_stack: rest,
      redo_stack: [cmd | redo]
    }
  end

  def redo(%__MODULE__{redo_stack: []} = executor), do: executor
  def redo(%__MODULE__{state: state, undo_stack: undo, redo_stack: [cmd | rest]} = executor) do
    new_state = Command.execute(cmd, state)
    %{executor |
      state: new_state,
      undo_stack: [cmd | undo],
      redo_stack: rest
    }
  end
end
```

## 4. マクロコマンド

複数のコマンドを1つにまとめて実行：

```elixir
defmodule MacroCommand do
  defstruct [:commands]

  def new(commands), do: %__MODULE__{commands: commands}
end

defimpl Command, for: MacroCommand do
  def execute(%MacroCommand{commands: commands}, state) do
    Enum.reduce(commands, state, &Command.execute(&1, &2))
  end

  def undo(%MacroCommand{commands: commands}, state) do
    Enum.reduce(Enum.reverse(commands), state, &Command.undo(&1, &2))
  end
end
```

## 5. キャンバス操作

図形の追加・移動・削除もコマンドで表現：

```elixir
defmodule AddShapeCommand do
  defstruct [:shape]
end

defimpl Command, for: AddShapeCommand do
  def execute(%{shape: shape}, %{shapes: shapes} = canvas) do
    %{canvas | shapes: shapes ++ [shape]}
  end

  def undo(%{shape: shape}, %{shapes: shapes} = canvas) do
    %{canvas | shapes: Enum.reject(shapes, &(&1.id == shape.id))}
  end
end
```

## まとめ

Elixir での Command パターンのポイント：

- **Protocol** でコマンドインターフェースを定義
- **Struct** でコマンドデータを表現
- **不変性** を活かした状態管理
- **Undo/Redo** スタックによる履歴管理
- **MacroCommand** で複合操作をサポート
