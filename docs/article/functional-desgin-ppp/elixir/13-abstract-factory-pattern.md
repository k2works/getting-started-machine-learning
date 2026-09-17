---
type: Article
title: "第13章: Abstract Factory パターン"
description: "Abstract Factory パターンは、関連するオブジェクトのファミリーを、その具体的なクラスを指定することなく生成するためのインターフェースを提供するパターンです。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第13章: Abstract Factory パターン

## はじめに

Abstract Factory パターンは、関連するオブジェクトのファミリーを、その具体的なクラスを指定することなく生成するためのインターフェースを提供するパターンです。

Elixir では、プロトコルを使用してファクトリインターフェースを定義し、異なる実装で異なる製品ファミリーを生成します。

## 1. Shape Factory

### 図形の定義

```elixir
defmodule Shapes do
  defmodule Circle do
    @enforce_keys [:center, :radius]
    defstruct [:center, :radius, :outline_color, :outline_width, :fill_color]

    def new(center, radius), do: %__MODULE__{center: center, radius: radius}

    def to_string(%__MODULE__{center: {x, y}, radius: r} = c) do
      base = "Circle center: [#{x}, #{y}] radius: #{r}"
      with_style(base, c)
    end

    defp with_style(base, %{fill_color: fc, outline_color: oc, outline_width: ow}) do
      parts = [base]
      parts = if fc, do: parts ++ ["fill: #{fc}"], else: parts
      parts = if oc, do: parts ++ ["outline: #{oc}(#{ow})"], else: parts
      Enum.join(parts, " ")
    end
  end

  defmodule Square do
    @enforce_keys [:top_left, :side]
    defstruct [:top_left, :side, :outline_color, :outline_width, :fill_color]

    def new(top_left, side), do: %__MODULE__{top_left: top_left, side: side}
  end

  defmodule Rectangle do
    @enforce_keys [:top_left, :width, :height]
    defstruct [:top_left, :width, :height, :outline_color, :outline_width, :fill_color]

    def new(top_left, width, height) do
      %__MODULE__{top_left: top_left, width: width, height: height}
    end
  end
end
```

### ShapeFactory プロトコル

```elixir
defprotocol ShapeFactory do
  @doc "円を作成"
  def create_circle(factory, center, radius)

  @doc "正方形を作成"
  def create_square(factory, top_left, side)

  @doc "長方形を作成"
  def create_rectangle(factory, top_left, width, height)
end
```

### 具体的なファクトリ実装

#### StandardShapeFactory（標準の図形）

```elixir
defmodule StandardShapeFactory do
  defstruct []

  def new, do: %__MODULE__{}
end

defimpl ShapeFactory, for: StandardShapeFactory do
  alias Shapes.{Circle, Square, Rectangle}

  def create_circle(_factory, center, radius) do
    Circle.new(center, radius)
  end

  def create_square(_factory, top_left, side) do
    Square.new(top_left, side)
  end

  def create_rectangle(_factory, top_left, width, height) do
    Rectangle.new(top_left, width, height)
  end
end
```

#### OutlinedShapeFactory（輪郭線付き図形）

```elixir
defmodule OutlinedShapeFactory do
  @enforce_keys [:outline_color, :outline_width]
  defstruct [:outline_color, :outline_width]

  def new(outline_color, outline_width) do
    %__MODULE__{outline_color: outline_color, outline_width: outline_width}
  end
end

defimpl ShapeFactory, for: OutlinedShapeFactory do
  alias Shapes.{Circle, Square, Rectangle}

  def create_circle(%OutlinedShapeFactory{outline_color: oc, outline_width: ow}, center, radius) do
    %{Circle.new(center, radius) | outline_color: oc, outline_width: ow}
  end

  def create_square(%OutlinedShapeFactory{outline_color: oc, outline_width: ow}, top_left, side) do
    %{Square.new(top_left, side) | outline_color: oc, outline_width: ow}
  end

  def create_rectangle(%OutlinedShapeFactory{outline_color: oc, outline_width: ow}, top_left, width, height) do
    %{Rectangle.new(top_left, width, height) | outline_color: oc, outline_width: ow}
  end
end
```

#### FilledShapeFactory（塗りつぶし付き図形）

```elixir
defmodule FilledShapeFactory do
  @enforce_keys [:fill_color]
  defstruct [:fill_color]

  def new(fill_color), do: %__MODULE__{fill_color: fill_color}
end

defimpl ShapeFactory, for: FilledShapeFactory do
  alias Shapes.{Circle, Square, Rectangle}

  def create_circle(%FilledShapeFactory{fill_color: fc}, center, radius) do
    %{Circle.new(center, radius) | fill_color: fc}
  end

  def create_square(%FilledShapeFactory{fill_color: fc}, top_left, side) do
    %{Square.new(top_left, side) | fill_color: fc}
  end

  def create_rectangle(%FilledShapeFactory{fill_color: fc}, top_left, width, height) do
    %{Rectangle.new(top_left, width, height) | fill_color: fc}
  end
end
```

#### StyledShapeFactory（輪郭線と塗りつぶしの組み合わせ）

```elixir
defmodule StyledShapeFactory do
  @enforce_keys [:outline_color, :outline_width, :fill_color]
  defstruct [:outline_color, :outline_width, :fill_color]

  def new(outline_color, outline_width, fill_color) do
    %__MODULE__{
      outline_color: outline_color,
      outline_width: outline_width,
      fill_color: fill_color
    }
  end
end
```

### 使用例

```elixir
# 標準ファクトリで図形を作成
standard_factory = StandardShapeFactory.new()
circle = ShapeFactory.create_circle(standard_factory, {0, 0}, 5)
# => %Circle{center: {0, 0}, radius: 5}

# 輪郭線付きファクトリで図形を作成
outlined_factory = OutlinedShapeFactory.new("black", 2)
outlined_circle = ShapeFactory.create_circle(outlined_factory, {0, 0}, 5)
# => %Circle{center: {0, 0}, radius: 5, outline_color: "black", outline_width: 2}

# 同じコードで異なるスタイルの図形を生成
def draw_scene(factory) do
  [
    ShapeFactory.create_circle(factory, {100, 100}, 50),
    ShapeFactory.create_square(factory, {200, 200}, 80),
    ShapeFactory.create_rectangle(factory, {300, 300}, 100, 60)
  ]
end
```

## 2. UI Factory

### UI コンポーネントの定義

```elixir
defmodule UIComponents do
  defmodule Button do
    defstruct [:label, :platform, :style]

    def new(label, platform, style \\ %{}) do
      %__MODULE__{label: label, platform: platform, style: style}
    end

    def render(%__MODULE__{label: label, platform: :windows, style: style}) do
      bg = Map.get(style, :background, "#e1e1e1")
      "[Windows Button: #{label}] bg=#{bg}"
    end

    def render(%__MODULE__{label: label, platform: :macos, style: style}) do
      bg = Map.get(style, :background, "#007aff")
      "(Mac Button: #{label}) bg=#{bg}"
    end

    def render(%__MODULE__{label: label, platform: :web, style: style}) do
      bg = Map.get(style, :background, "#4285f4")
      "<button>#{label}</button> bg=#{bg}"
    end
  end

  defmodule TextField do
    defstruct [:placeholder, :platform, :style]

    def new(placeholder, platform, style \\ %{}), do: %__MODULE__{placeholder: placeholder, platform: platform, style: style}
  end

  defmodule Checkbox do
    defstruct [:label, :checked, :platform, :style]

    def new(label, checked, platform, style \\ %{}), do: %__MODULE__{label: label, checked: checked, platform: platform, style: style}
  end
end
```

### UIFactory プロトコル

```elixir
defprotocol UIFactory do
  @doc "ボタンを作成"
  def create_button(factory, label)

  @doc "テキストフィールドを作成"
  def create_text_field(factory, placeholder)

  @doc "チェックボックスを作成"
  def create_checkbox(factory, label, checked)
end
```

### プラットフォーム別ファクトリ

```elixir
defmodule WindowsUIFactory do
  defstruct style: %{}
  def new(style \\ %{}), do: %__MODULE__{style: style}
end

defimpl UIFactory, for: WindowsUIFactory do
  alias UIComponents.{Button, TextField, Checkbox}

  def create_button(%WindowsUIFactory{style: style}, label), do: Button.new(label, :windows, style)
  def create_text_field(%WindowsUIFactory{style: style}, placeholder), do: TextField.new(placeholder, :windows, style)
  def create_checkbox(%WindowsUIFactory{style: style}, label, checked), do: Checkbox.new(label, checked, :windows, style)
end

defmodule MacOSUIFactory do
  defstruct style: %{}
  def new(style \\ %{}), do: %__MODULE__{style: style}
end

defimpl UIFactory, for: MacOSUIFactory do
  alias UIComponents.{Button, TextField, Checkbox}

  def create_button(%MacOSUIFactory{style: style}, label), do: Button.new(label, :macos, style)
  def create_text_field(%MacOSUIFactory{style: style}, placeholder), do: TextField.new(placeholder, :macos, style)
  def create_checkbox(%MacOSUIFactory{style: style}, label, checked), do: Checkbox.new(label, checked, :macos, style)
end

defmodule WebUIFactory do
  defstruct style: %{}
  def new(style \\ %{}), do: %__MODULE__{style: style}
end

defimpl UIFactory, for: WebUIFactory do
  alias UIComponents.{Button, TextField, Checkbox}

  def create_button(%WebUIFactory{style: style}, label), do: Button.new(label, :web, style)
  def create_text_field(%WebUIFactory{style: style}, placeholder), do: TextField.new(placeholder, :web, style)
  def create_checkbox(%WebUIFactory{style: style}, label, checked), do: Checkbox.new(label, checked, :web, style)
end
```

### 使用例

```elixir
# プラットフォームに依存しない UI 構築コード
def create_login_form(factory) do
  [
    UIFactory.create_text_field(factory, "Username"),
    UIFactory.create_text_field(factory, "Password"),
    UIFactory.create_checkbox(factory, "Remember me", false),
    UIFactory.create_button(factory, "Login")
  ]
end

# Windows 用
windows_form = create_login_form(WindowsUIFactory.new())

# macOS 用
macos_form = create_login_form(MacOSUIFactory.new())

# Web 用
web_form = create_login_form(WebUIFactory.new())
```

## 3. Theme Factory

### テーマコンポーネントの定義

```elixir
defmodule ThemeComponents do
  defmodule Colors do
    defstruct [:primary, :secondary, :background, :text, :accent]
    def new(attrs), do: struct(__MODULE__, attrs)
  end

  defmodule Typography do
    defstruct [:font_family, :base_size, :heading_scale]
    def new(attrs), do: struct(__MODULE__, attrs)
  end

  defmodule Spacing do
    defstruct [:unit, :scale]
    def new(attrs), do: struct(__MODULE__, attrs)
  end
end
```

### ThemeFactory プロトコル

```elixir
defprotocol ThemeFactory do
  def create_colors(factory)
  def create_typography(factory)
  def create_spacing(factory)
end
```

### テーマ別ファクトリ

```elixir
defmodule LightThemeFactory do
  defstruct []
  def new, do: %__MODULE__{}
end

defimpl ThemeFactory, for: LightThemeFactory do
  alias ThemeComponents.{Colors, Typography, Spacing}

  def create_colors(_factory) do
    Colors.new(%{
      primary: "#007aff",
      secondary: "#5856d6",
      background: "#ffffff",
      text: "#000000",
      accent: "#ff9500"
    })
  end

  def create_typography(_factory) do
    Typography.new(%{font_family: "San Francisco", base_size: 16, heading_scale: 1.25})
  end

  def create_spacing(_factory) do
    Spacing.new(%{unit: 8, scale: [0.5, 1, 2, 3, 4, 6, 8]})
  end
end

defmodule DarkThemeFactory do
  defstruct []
  def new, do: %__MODULE__{}
end

defimpl ThemeFactory, for: DarkThemeFactory do
  alias ThemeComponents.{Colors, Typography, Spacing}

  def create_colors(_factory) do
    Colors.new(%{
      primary: "#0a84ff",
      secondary: "#5e5ce6",
      background: "#1c1c1e",
      text: "#ffffff",
      accent: "#ff9f0a"
    })
  end

  def create_typography(_factory), do: Typography.new(%{font_family: "San Francisco", base_size: 16, heading_scale: 1.25})
  def create_spacing(_factory), do: Spacing.new(%{unit: 8, scale: [0.5, 1, 2, 3, 4, 6, 8]})
end
```

## 4. Database Factory

### データベースコンポーネント

```elixir
defmodule DatabaseComponents do
  defmodule Connection do
    defstruct [:driver, :host, :port, :database, :username, :password, :options]

    def new(attrs), do: struct(__MODULE__, attrs)

    def connection_string(%__MODULE__{driver: :mysql} = conn) do
      "mysql://#{conn.username}:#{conn.password}@#{conn.host}:#{conn.port}/#{conn.database}"
    end

    def connection_string(%__MODULE__{driver: :postgresql} = conn) do
      "postgresql://#{conn.username}:#{conn.password}@#{conn.host}:#{conn.port}/#{conn.database}"
    end

    def connection_string(%__MODULE__{driver: :sqlite} = conn) do
      "sqlite:#{conn.database}"
    end
  end

  defmodule QueryBuilder do
    defstruct [:driver, :table, :columns, :conditions, :order, :limit]

    def new(driver), do: %__MODULE__{driver: driver, columns: ["*"], conditions: []}

    def from(%__MODULE__{} = qb, table), do: %{qb | table: table}
    def select(%__MODULE__{} = qb, columns), do: %{qb | columns: columns}
    def where(%__MODULE__{conditions: conds} = qb, condition), do: %{qb | conditions: conds ++ [condition]}
    def order_by(%__MODULE__{} = qb, column, direction \\ :asc), do: %{qb | order: {column, direction}}
    def limit(%__MODULE__{} = qb, n), do: %{qb | limit: n}

    def build(%__MODULE__{} = qb) do
      columns = Enum.join(qb.columns, ", ")
      sql = "SELECT #{columns} FROM #{qb.table}"
      sql = if qb.conditions != [], do: sql <> " WHERE " <> Enum.join(qb.conditions, " AND "), else: sql
      sql = if qb.order, do: sql <> " ORDER BY #{elem(qb.order, 0)} #{elem(qb.order, 1) |> to_string() |> String.upcase()}", else: sql
      sql = if qb.limit, do: sql <> " LIMIT #{qb.limit}", else: sql
      %{driver: qb.driver, sql: sql}
    end
  end
end
```

### DatabaseFactory プロトコル

```elixir
defprotocol DatabaseFactory do
  def create_connection(factory, config)
  def create_query_builder(factory)
end
```

### データベース別ファクトリ

```elixir
defmodule MySQLFactory do
  defstruct []
  def new, do: %__MODULE__{}
end

defimpl DatabaseFactory, for: MySQLFactory do
  alias DatabaseComponents.{Connection, QueryBuilder}

  def create_connection(_factory, config) do
    Connection.new(%{
      driver: :mysql,
      host: Map.get(config, :host, "localhost"),
      port: Map.get(config, :port, 3306),
      database: Map.fetch!(config, :database),
      username: Map.fetch!(config, :username),
      password: Map.fetch!(config, :password)
    })
  end

  def create_query_builder(_factory), do: QueryBuilder.new(:mysql)
end

defmodule PostgreSQLFactory do
  defstruct []
  def new, do: %__MODULE__{}
end

defmodule SQLiteFactory do
  defstruct []
  def new, do: %__MODULE__{}
end
```

## 5. Factory Provider（Factory of Factories）

```elixir
defmodule FactoryProvider do
  @moduledoc """
  ファクトリのプロバイダー。
  設定に基づいて適切なファクトリを提供する。
  """

  # 図形ファクトリ
  def get_shape_factory(:standard), do: StandardShapeFactory.new()
  def get_shape_factory(:outlined), do: OutlinedShapeFactory.new("black", 1)
  def get_shape_factory({:outlined, color, width}), do: OutlinedShapeFactory.new(color, width)
  def get_shape_factory(:filled), do: FilledShapeFactory.new("gray")
  def get_shape_factory({:filled, color}), do: FilledShapeFactory.new(color)
  def get_shape_factory({:styled, outline_color, outline_width, fill_color}) do
    StyledShapeFactory.new(outline_color, outline_width, fill_color)
  end

  # UI ファクトリ
  def get_ui_factory(:windows), do: WindowsUIFactory.new()
  def get_ui_factory(:macos), do: MacOSUIFactory.new()
  def get_ui_factory(:web), do: WebUIFactory.new()
  def get_ui_factory({platform, style}), do: get_ui_factory_with_style(platform, style)

  defp get_ui_factory_with_style(:windows, style), do: WindowsUIFactory.new(style)
  defp get_ui_factory_with_style(:macos, style), do: MacOSUIFactory.new(style)
  defp get_ui_factory_with_style(:web, style), do: WebUIFactory.new(style)

  # テーマファクトリ
  def get_theme_factory(:light), do: LightThemeFactory.new()
  def get_theme_factory(:dark), do: DarkThemeFactory.new()

  # データベースファクトリ
  def get_database_factory(:mysql), do: MySQLFactory.new()
  def get_database_factory(:postgresql), do: PostgreSQLFactory.new()
  def get_database_factory(:sqlite), do: SQLiteFactory.new()
end
```

### 使用例

```elixir
# 設定に基づいてファクトリを選択
config = %{
  shape_style: :outlined,
  platform: :web,
  theme: :dark,
  database: :postgresql
}

shape_factory = FactoryProvider.get_shape_factory(config.shape_style)
ui_factory = FactoryProvider.get_ui_factory(config.platform)
theme_factory = FactoryProvider.get_theme_factory(config.theme)
db_factory = FactoryProvider.get_database_factory(config.database)

# 一貫したファミリーで製品を生成
shapes = [
  ShapeFactory.create_circle(shape_factory, {0, 0}, 50),
  ShapeFactory.create_square(shape_factory, {100, 100}, 80)
]

ui_components = [
  UIFactory.create_button(ui_factory, "Submit"),
  UIFactory.create_text_field(ui_factory, "Enter name")
]

theme = %{
  colors: ThemeFactory.create_colors(theme_factory),
  typography: ThemeFactory.create_typography(theme_factory),
  spacing: ThemeFactory.create_spacing(theme_factory)
}
```

## 設計のポイント

### プロトコルによる抽象化

Elixir のプロトコルは、Abstract Factory パターンの抽象ファクトリインターフェースを自然に表現します。

```elixir
defprotocol ShapeFactory do
  def create_circle(factory, center, radius)
  def create_square(factory, top_left, side)
  def create_rectangle(factory, top_left, width, height)
end
```

### 製品ファミリーの一貫性

同じファクトリから生成された製品は一貫したスタイルを持ちます。これにより、アプリケーション全体で統一されたルック＆フィールを保証できます。

### 拡張性

新しいファクトリを追加するには：

1. 新しいファクトリ構造体を定義
2. プロトコル実装を追加
3. FactoryProvider に新しいパターンマッチを追加

既存のコードを変更せずに新しいスタイルやプラットフォームを追加できます。

## まとめ

Abstract Factory パターンの利点：

1. **製品ファミリーの一貫性**: 関連する製品が常に互いに適合することを保証
2. **具体クラスの分離**: クライアントコードは抽象インターフェースのみに依存
3. **製品ファミリーの切り替え**: ファクトリを交換するだけで全体のスタイルを変更可能
4. **単一責任**: 製品の生成コードを一箇所に集約

Elixir では、プロトコルと構造体を使用して、型安全で拡張可能な Abstract Factory パターンを実装できます。
