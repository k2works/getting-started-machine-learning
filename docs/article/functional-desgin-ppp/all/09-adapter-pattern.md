---
type: Article
title: "第9章: Adapter パターン — 6言語統合ガイド"
description: "Adapter パターンは、互換性のないインターフェースを接続する GoF パターンです。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第9章: Adapter パターン — 6言語統合ガイド

## 1. はじめに

Adapter パターンは、互換性のないインターフェースを接続する GoF パターンです。OOP ではラッパークラスを作成しますが、関数型プログラミングでは**変換関数**がアダプターの役割を果たします。データ形式の変換、API の適応、単位変換など、関数型ではデータ変換パイプラインとして自然に表現されます。

> **Elixir の読者へ**: Elixir 版は本章で「I/O と外部システム」を扱っています。Elixir 固有のアプローチについては[コラム](#elixir-コラムio-と外部システム)を参照してください。

## 2. 共通の本質

### Adapter の構造

```
adapt: A → B
  where A = 既存のインターフェース
        B = 期待されるインターフェース
```

- **インターフェース適応**: 既存の型を期待される型に変換
- **双方向変換**: `A → B` と `B → A` の両方を提供
- **透過的利用**: アダプターを通じて既存コードを再利用

### 典型的なユースケース

- **デバイス制御**: VariableLight（調光） → Switchable（オン/オフ）
- **データ形式変換**: 旧ユーザー形式 → 新ユーザー形式
- **API レスポンス変換**: 外部 API のレスポンスを内部モデルに変換
- **単位変換**: 摂氏 ↔ 華氏、通貨変換

## 3. 言語別実装比較

### 3.1 アダプターの実現方法

| 言語 | アダプター表現 | インターフェース |
|------|-------------|---------------|
| Clojure | 変換関数 + マルチメソッド | プロトコル |
| Scala | trait 実装 / 関数変換 | trait |
| F# | 関数 + インターフェース | ISwitchable 等 |
| Haskell | newtype + 型クラスインスタンス | 型クラス |
| Rust | trait 実装 / 変換関数 | trait |

### 3.2 デバイスアダプター（VariableLight → Switchable）

調光可能なライト（VariableLight）を単純なオン/オフスイッチ（Switchable）として使うアダプターです。

<details>
<summary>Clojure: プロトコル + 変換関数</summary>

```clojure
;; VariableLight は明るさ（0-100）を持つ
(defn make-variable-light [brightness]
  {:type :variable-light :brightness brightness})

;; Switchable プロトコルへの適応
(defmulti turn-on :type)
(defmulti turn-off :type)
(defmulti is-on? :type)

(defmethod turn-on :variable-light [light]
  (assoc light :brightness 100))

(defmethod turn-off :variable-light [light]
  (assoc light :brightness 0))

(defmethod is-on? :variable-light [light]
  (> (:brightness light) 0))
```

</details>

<details>
<summary>Scala: trait 実装</summary>

```scala
trait Switchable:
  def turnOn: Switchable
  def turnOff: Switchable
  def isOn: Boolean

case class VariableLight(brightness: Int)

case class VariableLightAdapter(light: VariableLight) extends Switchable:
  def turnOn: Switchable = VariableLightAdapter(light.copy(brightness = 100))
  def turnOff: Switchable = VariableLightAdapter(light.copy(brightness = 0))
  def isOn: Boolean = light.brightness > 0
```

</details>

<details>
<summary>Haskell: newtype + 型クラスインスタンス</summary>

```haskell
class Switchable a where
    turnOn  :: a -> a
    turnOff :: a -> a
    isOn    :: a -> Bool

data VariableLight = VariableLight { brightness :: Int }

newtype VariableLightAdapter = VariableLightAdapter VariableLight

instance Switchable VariableLightAdapter where
    turnOn (VariableLightAdapter _) =
        VariableLightAdapter (VariableLight 100)
    turnOff (VariableLightAdapter _) =
        VariableLightAdapter (VariableLight 0)
    isOn (VariableLightAdapter (VariableLight b)) = b > 0
```

`newtype` で既存の型をラップし、型クラスインスタンスを実装することで、コンパイル時のオーバーヘッドなしにアダプターを実現します。

</details>

<details>
<summary>Rust: trait 実装</summary>

```rust
pub trait Switchable {
    fn turn_on(&self) -> Box<dyn Switchable>;
    fn turn_off(&self) -> Box<dyn Switchable>;
    fn is_on(&self) -> bool;
}

pub struct VariableLight { pub brightness: u8 }

pub struct VariableLightAdapter {
    light: VariableLight,
}

impl Switchable for VariableLightAdapter {
    fn turn_on(&self) -> Box<dyn Switchable> {
        Box::new(VariableLightAdapter {
            light: VariableLight { brightness: 100 },
        })
    }

    fn turn_off(&self) -> Box<dyn Switchable> {
        Box::new(VariableLightAdapter {
            light: VariableLight { brightness: 0 },
        })
    }

    fn is_on(&self) -> bool { self.light.brightness > 0 }
}
```

</details>

### 3.3 データ形式変換アダプター

旧形式のユーザーデータを新形式に変換するアダプターです。

<details>
<summary>データ変換アダプターの比較</summary>

```clojure
;; Clojure: 変換関数
(defn adapt-old-to-new [old-user]
  {:id (:user-id old-user)
   :full-name (str (:first-name old-user) " " (:last-name old-user))
   :email (:email-address old-user)})
```

```scala
// Scala: case class 変換
case class OldUser(userId: String, firstName: String, lastName: String, emailAddress: String)
case class NewUser(id: String, fullName: String, email: String)

def adaptOldToNew(old: OldUser): NewUser =
  NewUser(old.userId, s"${old.firstName} ${old.lastName}", old.emailAddress)
```

```fsharp
// F#: レコード変換
let adaptOldToNew (old: OldUser) : NewUser =
    { Id = old.UserId
      FullName = sprintf "%s %s" old.FirstName old.LastName
      Email = old.EmailAddress }
```

```haskell
-- Haskell: 変換関数
adaptOldToNew :: OldUser -> NewUser
adaptOldToNew old = NewUser
    { userId   = oldUserId old
    , fullName = oldFirstName old ++ " " ++ oldLastName old
    , email    = oldEmailAddress old
    }
```

```rust
// Rust: From trait
impl From<OldUser> for NewUser {
    fn from(old: OldUser) -> Self {
        NewUser {
            id: old.user_id,
            full_name: format!("{} {}", old.first_name, old.last_name),
            email: old.email_address,
        }
    }
}

// 使用: NewUser::from(old_user) or old_user.into()
```

Rust の `From` trait は標準ライブラリが提供するアダプターパターンの典型です。

</details>

### 3.4 双方向アダプター

`A → B` と `B → A` の両方を提供するアダプターです。

<details>
<summary>双方向アダプターの比較</summary>

```haskell
-- Haskell
data Adapter a b = Adapter
    { adapt   :: a -> b
    , unadapt :: b -> a
    }

celsiusFahrenheit :: Adapter Double Double
celsiusFahrenheit = Adapter
    { adapt   = \c -> c * 9 / 5 + 32
    , unadapt = \f -> (f - 32) * 5 / 9
    }
```

```scala
// Scala
case class Adapter[A, B](adapt: A => B, unadapt: B => A)

val celsiusFahrenheit = Adapter[Double, Double](
  adapt   = c => c * 9.0 / 5 + 32,
  unadapt = f => (f - 32) * 5.0 / 9
)
```

```fsharp
// F#
type Adapter<'A, 'B> = { Adapt: 'A -> 'B; Unadapt: 'B -> 'A }

let celsiusFahrenheit = {
    Adapt = fun c -> c * 9.0 / 5.0 + 32.0
    Unadapt = fun f -> (f - 32.0) * 5.0 / 9.0
}
```

</details>

## 4. 比較分析

### 4.1 OOP Adapter vs 関数型 Adapter

| 観点 | OOP | 関数型 |
|------|-----|--------|
| アダプター | ラッパークラス | 変換関数 |
| インターフェース | Java/C# のインターフェース | 型クラス / trait / プロトコル |
| ボイラープレート | 多い（委譲メソッド） | 少ない（関数のみ） |
| 双方向 | 2 つのクラス | 関数のペア |
| 合成 | 複雑 | `compose` / `>>` で合成 |

### 4.2 Rust の From/Into trait

Rust は標準ライブラリに `From` / `Into` trait を持ち、型変換のアダプターパターンを言語レベルでサポートしています。`From<A> for B` を実装すると、`Into<B> for A` が自動的に得られます。

### 4.3 言語間のアダプター表現力

| 基準 | 最適な言語 | 理由 |
|------|----------|------|
| 型安全な変換 | Haskell | newtype + 型クラスでゼロコスト |
| 標準ライブラリ統合 | Rust | From/Into が標準提供 |
| 柔軟な変換 | Clojure | マップ変換が自由自在 |
| 既存型への後付け | Scala | extension メソッド / given |

## 5. Elixir コラム：I/O と外部システム

Elixir の第 9 章は Adapter パターンではなく、**I/O と外部システムの抽象化**を扱っています。

### リポジトリパターン

```elixir
defmodule Repository do
  use Agent

  def start_link(_opts) do
    Agent.start_link(fn -> %{} end, name: __MODULE__)
  end

  def save(id, entity) do
    Agent.update(__MODULE__, &Map.put(&1, id, entity))
  end

  def find(id) do
    Agent.get(__MODULE__, &Map.get(&1, id))
  end
end
```

### HTTP クライアントの抽象化

```elixir
defmodule HttpClient do
  @callback get(String.t()) :: {:ok, map()} | {:error, term()}
  @callback post(String.t(), map()) :: {:ok, map()} | {:error, term()}
end

defmodule RealHttpClient do
  @behaviour HttpClient
  def get(url), do: HTTPoison.get(url)
  def post(url, body), do: HTTPoison.post(url, Jason.encode!(body))
end

defmodule MockHttpClient do
  @behaviour HttpClient
  def get(_url), do: {:ok, %{status: 200, body: "mock"}}
  def post(_url, _body), do: {:ok, %{status: 201}}
end
```

Adapter パターンの「インターフェース適応」は、Elixir では `@behaviour` と `@callback` による**振る舞いの契約**として表現されます。テスト時にモック実装を差し替えることで、外部システムへの依存を分離します。

## 6. まとめ

Adapter パターンは、関数型プログラミングで**変換関数**として最も簡潔に表現されます：

1. **関数がアダプター**: 型 A から型 B への変換関数
2. **型クラス / trait / プロトコル**: 既存の型に新しいインターフェースを後付け
3. **合成可能性**: 複数のアダプターをパイプラインで連鎖

## 言語別個別記事

- [Clojure](../clojure/09-adapter-pattern.md) | [Scala](../scala/09-adapter-pattern.md) | [Elixir](../elixir/09-io-and-external-systems.md) | [F#](../fsharp/09-adapter-pattern.md) | [Haskell](../haskell/09-adapter-pattern.md) | [Rust](../rust/09-adapter-pattern.md)
