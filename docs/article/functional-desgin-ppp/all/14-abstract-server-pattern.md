---
type: Article
title: "第14章: Abstract Server パターン — 6言語統合ガイド"
description: "Abstract Server パターンは、SOLID 原則の依存性逆転の原則（DIP）を実現するパターンです。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第14章: Abstract Server パターン — 6言語統合ガイド

## 1. はじめに

Abstract Server パターンは、SOLID 原則の**依存性逆転の原則（DIP）**を実現するパターンです。高レベルモジュールが低レベルモジュールに直接依存するのではなく、**抽象（インターフェース）**に依存することで、実装の差し替えを可能にします。関数型プログラミングでは、型クラス・trait・プロトコル・高階関数がこの抽象化を提供します。

## 2. 共通の本質

### DIP の構造

```
高レベルモジュール（Client）
    ↓ 依存
  抽象（Interface）
    ↑ 実装
低レベルモジュール（Server）
```

- **Client**: 抽象に依存し、具体的な実装を知らない
- **Interface**: 操作の契約を定義
- **Server**: 抽象を実装する具体的なモジュール

### 典型的なユースケース

- **Switchable デバイス**: Switch（Client）→ Switchable（Interface）→ Light/Fan/Motor（Server）
- **リポジトリ**: Service → Repository → InMemory / Database
- **ロガー**: Application → Logger → Console / File / Test

## 3. 言語別実装比較

### 3.1 抽象インターフェースの表現

| 言語 | 抽象の表現 | DIP の実現 |
|------|----------|----------|
| Clojure | プロトコル（`defprotocol`） | プロトコル実装の差し替え |
| Scala | trait / given | 型クラスインスタンスの選択 |
| Elixir | プロトコル / `@behaviour` | `defimpl` / `@behaviour` 実装 |
| F# | インターフェース | インターフェース実装の差し替え |
| Haskell | 型クラス（`class`） | インスタンスの選択 |
| Rust | trait | trait 実装の差し替え |

### 3.2 Switchable パターン

すべての言語で共通する典型例：Switch（Client）が Switchable（Interface）に依存し、Light / Fan / Motor（Server）が Switchable を実装します。

<details>
<summary>Clojure: プロトコル + レコード</summary>

```clojure
(defprotocol Switchable
  (turn-on [this])
  (turn-off [this])
  (is-on? [this]))

(defrecord Light [state]
  Switchable
  (turn-on [this] (assoc this :state :on))
  (turn-off [this] (assoc this :state :off))
  (is-on? [this] (= (:state this) :on)))

(defrecord Fan [state speed]
  Switchable
  (turn-on [this] (assoc this :state :on :speed :low))
  (turn-off [this] (assoc this :state :off :speed nil))
  (is-on? [this] (= (:state this) :on)))

;; Client: Switch は Switchable にのみ依存
(defn toggle [device]
  (if (is-on? device) (turn-off device) (turn-on device)))
```

</details>

<details>
<summary>Scala: given + 型クラス</summary>

```scala
trait Switchable[A]:
  def turnOn(device: A): A
  def turnOff(device: A): A
  def isOn(device: A): Boolean

case class Light(state: LightState = LightState.Off)

given Switchable[Light] with
  def turnOn(light: Light): Light = light.copy(state = LightState.On)
  def turnOff(light: Light): Light = light.copy(state = LightState.Off)
  def isOn(light: Light): Boolean = light.state == LightState.On

// Client: 型クラス制約で抽象に依存
def toggle[A: Switchable](device: A): A =
  val sw = summon[Switchable[A]]
  if sw.isOn(device) then sw.turnOff(device) else sw.turnOn(device)
```

</details>

<details>
<summary>Haskell: 型クラス</summary>

```haskell
class Switchable a where
    turnOn  :: a -> a
    turnOff :: a -> a
    isOn    :: a -> Bool

data Light = Light { lightState :: SwitchState, lightName :: String }
    deriving (Show, Eq)

instance Switchable Light where
    turnOn light  = light { lightState = On }
    turnOff light = light { lightState = Off }
    isOn light    = lightState light == On

data Fan = Fan { fanState :: SwitchState, fanSpeed :: Maybe Speed }
    deriving (Show, Eq)

instance Switchable Fan where
    turnOn fan  = fan { fanState = On, fanSpeed = Just Low }
    turnOff fan = fan { fanState = Off, fanSpeed = Nothing }
    isOn fan    = fanState fan == On

-- Client: 型クラス制約で抽象に依存
toggle :: Switchable a => a -> a
toggle device = if isOn device then turnOff device else turnOn device
```

</details>

<details>
<summary>Rust: trait</summary>

```rust
pub trait Switchable {
    fn turn_on(&self) -> Self;
    fn turn_off(&self) -> Self;
    fn is_on(&self) -> bool;
}

pub struct Light { pub state: bool }

impl Switchable for Light {
    fn turn_on(&self) -> Self { Light { state: true } }
    fn turn_off(&self) -> Self { Light { state: false } }
    fn is_on(&self) -> bool { self.state }
}

pub struct Fan { pub state: bool, pub speed: Option<Speed> }

impl Switchable for Fan {
    fn turn_on(&self) -> Self {
        Fan { state: true, speed: Some(Speed::Low) }
    }
    fn turn_off(&self) -> Self {
        Fan { state: false, speed: None }
    }
    fn is_on(&self) -> bool { self.state }
}

// Client: ジェネリクス + trait 境界
pub fn toggle<T: Switchable>(device: &T) -> T {
    if device.is_on() { device.turn_off() } else { device.turn_on() }
}
```

</details>

<details>
<summary>F#: インターフェース</summary>

```fsharp
type ISwitchable =
    abstract member TurnOn: unit -> ISwitchable
    abstract member TurnOff: unit -> ISwitchable
    abstract member IsOn: bool

type Light = { State: bool }
    with
    interface ISwitchable with
        member this.TurnOn() = { State = true } :> ISwitchable
        member this.TurnOff() = { State = false } :> ISwitchable
        member this.IsOn = this.State

// Client
let toggle (device: ISwitchable) =
    if device.IsOn then device.TurnOff() else device.TurnOn()
```

</details>

<details>
<summary>Elixir: プロトコル</summary>

```elixir
defprotocol Switchable do
  def turn_on(device)
  def turn_off(device)
  def on?(device)
end

defmodule Light do
  defstruct state: :off, brightness: 100
end

defimpl Switchable, for: Light do
  def turn_on(light), do: %{light | state: :on}
  def turn_off(light), do: %{light | state: :off}
  def on?(%Light{state: state}), do: state == :on
end

# Client
defmodule Switch do
  def toggle(device) do
    if Switchable.on?(device),
      do: Switchable.turn_off(device),
      else: Switchable.turn_on(device)
  end
end
```

</details>

### 3.3 リポジトリパターン（DIP の実践）

データ永続化の抽象化は DIP の代表的な活用例です。

<details>
<summary>リポジトリの抽象化比較</summary>

```haskell
-- Haskell: 型クラス
class Repository m where
    save   :: Entity -> m ()
    findById :: String -> m (Maybe Entity)
    findAll  :: m [Entity]

-- インメモリ実装
instance Repository (State (Map String Entity)) where
    save entity = modify (Map.insert (entityId entity) entity)
    findById id = gets (Map.lookup id)
    findAll = gets Map.elems
```

```rust
// Rust: trait
pub trait Repository<T> {
    fn save(&mut self, entity: T) -> Result<(), String>;
    fn find_by_id(&self, id: &str) -> Option<&T>;
    fn find_all(&self) -> Vec<&T>;
}

pub struct InMemoryRepository<T> {
    store: HashMap<String, T>,
}

impl<T: HasId> Repository<T> for InMemoryRepository<T> {
    fn save(&mut self, entity: T) -> Result<(), String> {
        self.store.insert(entity.id().to_string(), entity);
        Ok(())
    }
    fn find_by_id(&self, id: &str) -> Option<&T> {
        self.store.get(id)
    }
    fn find_all(&self) -> Vec<&T> {
        self.store.values().collect()
    }
}
```

```scala
// Scala: trait
trait Repository[T]:
  def save(entity: T): Unit
  def findById(id: String): Option[T]
  def findAll: List[T]

class InMemoryRepository[T] extends Repository[T]:
  private val store = scala.collection.mutable.Map.empty[String, T]
  def save(entity: T): Unit = store += (entity.id -> entity)
  def findById(id: String): Option[T] = store.get(id)
  def findAll: List[T] = store.values.toList
```

</details>

### 3.4 ロガーパターン

テスト可能なロギングの抽象化です。

| 言語 | 本番用 | テスト用 |
|------|--------|---------|
| Clojure | `println` を呼ぶ実装 | `atom` に蓄積する実装 |
| Scala | `ConsoleLogger` | `TestLogger`（`var` で蓄積） |
| Elixir | `ConsoleLogger` | `TestLogger`（Agent で蓄積） |
| F# | `ConsoleLogger` | `TestLogger`（`ResizeArray` で蓄積） |
| Haskell | `IO` ベース | `Writer` モナドベース |
| Rust | `ConsoleLogger` | `TestLogger`（`Vec` で蓄積） |

## 4. 比較分析

### 4.1 DIP の実現レベル

| 言語 | DIP の強制力 | コンパイル時チェック |
|------|-----------|-----------------|
| Clojure | 規約ベース | なし |
| Scala | 型クラス / given で強制 | 完全 |
| Elixir | プロトコルで強制 | なし（動的） |
| F# | インターフェースで強制 | 完全 |
| Haskell | 型クラスで強制 | 最も厳密 |
| Rust | trait 境界で強制 | 完全 |

### 4.2 高階関数による簡易 DIP

型クラスや trait を使わなくても、**高階関数**で簡易的に DIP を実現できます：

```clojure
;; Clojure: 関数を渡すだけ
(defn process [data save-fn log-fn]
  (log-fn "Processing...")
  (save-fn (transform data))
  (log-fn "Done"))
```

```haskell
-- Haskell: 関数を渡す
process :: (String -> IO ()) -> (a -> IO ()) -> a -> IO ()
process logger saver data = do
    logger "Processing..."
    saver (transform data)
    logger "Done"
```

この方法は小規模なケースでは十分ですが、操作が多数ある場合は型クラス / trait / プロトコルの方が整理しやすくなります。

### 4.3 Elixir 版のボリューム

Elixir 版は 729 行と全言語中最長です。これは以下を詳細に扱っているためです：

- Light / Fan / Motor の 3 種類のデバイス実装
- Repository パターン（User, Product）の完全実装
- Logger インターフェースの複数実装
- 複合サービスの統合例
- 各パターンのテストコード

## 5. 実践的な選択指針

| 要件 | 推奨言語 | 理由 |
|------|---------|------|
| 最も厳密な DIP | Haskell | 型クラスによるコンパイル時強制 |
| 実用的な DIP | Rust, Scala | trait / given で型安全に抽象化 |
| 動的な実装切り替え | Clojure | プロトコル + 高階関数で柔軟 |
| アクターモデルとの統合 | Elixir | プロトコル + GenServer で状態管理 |
| .NET エコシステム統合 | F# | インターフェースで C# と相互運用 |

## 6. まとめ

Abstract Server パターンは、SOLID の DIP を関数型で実現する基盤です：

1. **型クラス / trait / プロトコル**: 抽象インターフェースを言語の仕組みで表現
2. **実装の差し替え**: テスト用モック、本番用実装を同じインターフェースで切り替え
3. **高階関数による簡易 DIP**: 小規模なケースでは関数を渡すだけでも十分

## 言語別個別記事

- [Clojure](../clojure/14-abstract-server-pattern.md) | [Scala](../scala/14-abstract-server-pattern.md) | [Elixir](../elixir/14-abstract-server-pattern.md) | [F#](../fsharp/14-abstract-server-pattern.md) | [Haskell](../haskell/14-abstract-server-pattern.md) | [Rust](../rust/14-abstract-server-pattern.md)
