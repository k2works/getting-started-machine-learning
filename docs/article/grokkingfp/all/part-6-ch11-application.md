---
type: Article
title: "第11章: 実践アプリケーション — 11言語比較"
description: "第 10 章までで、関数型プログラミングの基礎から並行処理まで幅広い概念を学んできました。"
tags: [article, grokkingfp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第11章: 実践アプリケーション — 11言語比較

## 11.1 はじめに

第 10 章までで、関数型プログラミングの基礎から並行処理まで幅広い概念を学んできました。本章では、これまでのすべての概念を統合し、**実践的なアプリケーション**を構築します。

題材は **TravelGuide**（旅行ガイド）アプリケーションです。外部データソースからアトラクション、アーティスト、映画の情報を取得し、旅行ガイドを生成します。この過程で、**DataAccess の抽象化（DI パターン）**、**Resource によるリソース管理**、**キャッシュ**、**SearchReport による可観測性**という、FP アプリケーション設計の 4 つの柱を 11 言語で比較します。

---

## 11.2 共通の本質 — FP アプリケーション設計の 4 つの柱

### 柱 1: イミュータブルなドメインモデル

すべての言語で、ドメインモデルはイミュータブルなデータ構造として定義されます。`Location`、`Attraction`、`TravelGuide` といった型は一度作成されたら変更できず、関数で新しい値を返す設計です。

### 柱 2: DataAccess の抽象化（関数型 DI）

外部依存をインターフェースで抽象化し、本番実装とテスト用スタブを差し替え可能にします。これにより、ビジネスロジックが外部システムから独立し、テスト容易性が飛躍的に向上します。

### 柱 3: Resource による安全なリソース管理

データベース接続やファイルハンドルなどのリソースを、例外が発生しても確実に解放する仕組みです。`acquire` → `use` → `release` のパターンを型で保証します。

### 柱 4: SearchReport による可観測性

検索結果だけでなく、「何件検索したか」「どのエラーが発生したか」といったメタデータを `SearchReport` として返します。部分的な失敗があっても処理を続行し、失敗情報をレポートに含める設計です。

---

## 11.3 ドメインモデル — 全 11 言語比較

### 代表 3 言語の詳細比較

**Scala — case class:**

```scala
opaque type LocationId = String
case class Location(id: LocationId, name: String, population: Int)
case class Attraction(name: String, description: Option[String], location: Location)
case class TravelGuide(attraction: Attraction, subjects: List[String])
```

**Haskell — data + record syntax:**

```haskell
newtype LocationId = LocationId { unLocationId :: String }

data Location = Location
    { locId :: LocationId, locName :: String, locPopulation :: Int }

data Attraction = Attraction
    { attrName :: String, attrDescription :: Maybe String, attrLocation :: Location }

data TravelGuide = TravelGuide
    { tgAttraction :: Attraction, tgSubjects :: [String], tgSearchReport :: SearchReport }
```

**Rust — struct + Option:**

```rust
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct LocationId(pub String);

#[derive(Debug, Clone)]
pub struct Location { pub id: LocationId, pub name: String, pub population: i32 }

#[derive(Debug, Clone)]
pub struct Attraction { pub name: String, pub description: Option<String>, pub location: Location }

#[derive(Debug, Clone)]
pub struct TravelGuide { pub attraction: Attraction, pub subjects: Vec<String>, pub search_report: SearchReport }
```

### 全 11 言語のドメインモデル定義方式

<details>
<summary>Java — record + sealed interface</summary>

```java
public record LocationId(String value) {}
public record Location(LocationId id, String name, int population) {}
public record Attraction(String name, Option<String> description, Location location) {}
public sealed interface PopCultureSubject permits Artist, Movie { String name(); }
public record Guide(Attraction attraction, List<PopCultureSubject> subjects) {}
```

</details>

<details>
<summary>Python — frozen dataclass</summary>

```python
LocationId = NewType("LocationId", str)

@dataclass(frozen=True)
class Location:
    id: LocationId
    name: str
    population: int

@dataclass(frozen=True)
class Attraction:
    name: str
    description: str | None
    location: Location
```

</details>

<details>
<summary>TypeScript — readonly interface + ブランド型</summary>

```typescript
type LocationId = string & { readonly _brand: unique symbol }

interface Location {
  readonly id: LocationId
  readonly name: string
  readonly population: number
}

interface Attraction {
  readonly name: string
  readonly description: Option<string>
  readonly location: Location
}
```

</details>

<details>
<summary>F# — レコード型 + 判別共用体</summary>

```fsharp
type LocationId = LocationId of string

type Location = { Id: LocationId; Name: string; Population: int }
type Attraction = { Name: string; Description: string option; Location: Location }
type TravelGuide = { Attraction: Attraction; Subjects: string list }
```

</details>

<details>
<summary>C# — record</summary>

```csharp
public readonly record struct LocationId(string Value);
public record Location(LocationId Id, string Name, int Population);
public record Attraction(string Name, Option<string> Description, Location Location);
public record TravelGuide(Attraction Attraction, Seq<string> Subjects);
```

</details>

<details>
<summary>Clojure — defrecord</summary>

```clojure
(defrecord Location [id name population])
(defrecord Attraction [name description location])
(defrecord TravelGuide [attraction subjects search-report])
```

</details>

<details>
<summary>Elixir — defstruct + @type</summary>

```elixir
defmodule Location do
  defstruct [:id, :name, :population]
  @type t :: %__MODULE__{id: LocationId.t(), name: String.t(), population: non_neg_integer()}
end

defmodule Attraction do
  defstruct [:name, :description, :location]
  @type t :: %__MODULE__{name: String.t(), description: String.t() | nil, location: Location.t()}
end
```

</details>

<details>
<summary>Ruby — Struct</summary>

```ruby
class LocationId
  attr_reader :value
  def initialize(value) = @value = value
end

Location = Struct.new(:id, :name, :population, keyword_init: true)
Attraction = Struct.new(:name, :description, :location, keyword_init: true)
TravelGuide = Struct.new(:attraction, :subjects, :search_report, keyword_init: true)
```

</details>

### イミュータブル性の保証レベル

| 言語 | 手段 | 保証レベル |
|------|------|-----------|
| Scala | `case class` (デフォルトで `val`) | コンパイル時 |
| Haskell | すべての値がイミュータブル | 言語レベル |
| Rust | デフォルトがイミュータブル (`mut` 明示) | コンパイル時 |
| Java | `record` | コンパイル時 |
| Python | `@dataclass(frozen=True)` | ランタイム |
| TypeScript | `readonly` 修飾子 | コンパイル時（型レベル） |
| F# | レコード型（デフォルトでイミュータブル） | コンパイル時 |
| C# | `record` + `readonly` | コンパイル時 |
| Clojure | すべての値がイミュータブル | 言語レベル |
| Elixir | すべての値がイミュータブル | 言語レベル |
| Ruby | `freeze` / 慣習 | 規約ベース |

---

## 11.4 DataAccess の抽象化 — 関数型 DI の全 11 言語比較

### 代表 3 言語の詳細比較

FP における DI は、OOP のコンストラクタインジェクションとは異なり、**インターフェースを引数として受け取る**シンプルなパターンです。

**Scala — trait:**

```scala
trait DataAccess {
  def findAttractions(name: String, ordering: AttractionOrdering, limit: Int): IO[List[Attraction]]
  def findArtistsFromLocation(locationId: LocationId, limit: Int): IO[List[MusicArtist]]
  def findMoviesAboutLocation(locationId: LocationId, limit: Int): IO[List[Movie]]
}
```

**Haskell — レコード型（関数の集合）:**

```haskell
data DataAccess = DataAccess
    { findAttractions         :: String -> AttractionOrdering -> Int -> IO [Attraction]
    , findArtistsFromLocation :: LocationId -> Int -> IO (Either String [MusicArtist])
    , findMoviesAboutLocation :: LocationId -> Int -> IO (Either String [Movie])
    }
```

**Rust — async_trait:**

```rust
#[async_trait]
pub trait DataAccess: Send + Sync {
    async fn find_attractions(&self, name: &str, ordering: AttractionOrdering, limit: usize)
        -> Vec<Attraction>;
    async fn find_artists_from_location(&self, location_id: &LocationId, limit: usize)
        -> Result<Vec<MusicArtist>, String>;
    async fn find_movies_about_location(&self, location_id: &LocationId, limit: usize)
        -> Result<Vec<Movie>, String>;
}
```

### 全 11 言語の DataAccess 抽象化

<details>
<summary>Java — interface</summary>

```java
public interface DataAccess {
    IO<List<Attraction>> findAttractions(String name, AttractionOrdering ordering, int limit);
    IO<List<Artist>> findArtistsFromLocation(LocationId locationId, int limit);
    IO<List<Movie>> findMoviesAboutLocation(LocationId locationId, int limit);
}
```

</details>

<details>
<summary>Python — ABC（抽象基底クラス）</summary>

```python
class DataAccess(ABC):
    @abstractmethod
    def find_attractions(self, name: str, ordering: AttractionOrdering, limit: int) -> list[Attraction]:
        pass

    @abstractmethod
    def find_artists_from_location(self, location_id: LocationId, limit: int) -> Result[list[MusicArtist], str]:
        pass
```

</details>

<details>
<summary>TypeScript — interface（関数フィールド）</summary>

```typescript
interface DataAccess {
  readonly findAttractions: (name: string, ordering: AttractionOrdering, limit: number)
    => Task<readonly Attraction[]>
  readonly findArtistsFromLocation: (locationId: LocationId, limit: number)
    => Task<Either<string, readonly MusicArtist[]>>
  readonly findMoviesAboutLocation: (locationId: LocationId, limit: number)
    => Task<Either<string, readonly Movie[]>>
}
```

</details>

<details>
<summary>F# — abstract type</summary>

```fsharp
type IDataAccess =
    abstract member FindAttractions:
        name: string * ordering: AttractionOrdering * limit: int -> Async<Attraction list>
    abstract member FindArtistsFromLocation:
        locationId: LocationId * limit: int -> Async<MusicArtist list>
```

</details>

<details>
<summary>C# — interface</summary>

```csharp
public interface IDataAccess
{
    Task<Seq<Attraction>> FindAttractions(string name, AttractionOrdering ordering, int limit);
    Task<Seq<MusicArtist>> FindArtistsFromLocation(LocationId locationId, int limit);
    Task<Seq<Movie>> FindMoviesAboutLocation(LocationId locationId, int limit);
}
```

</details>

<details>
<summary>Clojure — defprotocol</summary>

```clojure
(defprotocol DataAccess
  (find-attractions [this name ordering limit])
  (find-artists-from-location [this location-id limit])
  (find-movies-about-location [this location-id limit]))
```

</details>

<details>
<summary>Elixir — @behaviour</summary>

```elixir
defmodule DataAccess do
  @callback find_attractions(String.t(), ordering(), pos_integer()) ::
              {:ok, [Attraction.t()]} | {:error, String.t()}
  @callback find_artists_from_location(LocationId.t(), pos_integer()) ::
              {:ok, [MusicArtist.t()]} | {:error, String.t()}
end
```

</details>

<details>
<summary>Ruby — module（duck typing）</summary>

```ruby
module DataAccess
  def find_attractions(name, ordering, limit)
    raise NotImplementedError
  end

  def find_artists_from_location(location_id, limit)
    raise NotImplementedError
  end
end
```

</details>

### DI 抽象化パターンの比較

| 言語 | 抽象化手段 | エフェクト型 | エラー表現 |
|------|-----------|-------------|-----------|
| Scala | `trait` | `IO[A]` | `IO[List[A]]`（IO 内で例外） |
| Haskell | レコード型 | `IO a` | `IO (Either String [a])` |
| Rust | `#[async_trait] trait` | `async fn -> T` | `Result<Vec<A>, String>` |
| Java | `interface` | `IO<A>` | `IO<List<A>>` |
| Python | `ABC` | 直接値 / `Result` | `Result[list[A], str]` |
| TypeScript | `interface`（関数フィールド） | `Task<A>` | `Task<Either<string, A[]>>` |
| F# | `abstract type` | `Async<'a>` | `Async<'a list>` |
| C# | `interface` | `Task<T>` | `Task<Seq<T>>` |
| Clojure | `defprotocol` | 直接値 / `{:ok v}` | `{:ok v}` / `{:error msg}` |
| Elixir | `@behaviour` | 直接値 | `{:ok, v}` / `{:error, msg}` |
| Ruby | `module`（duck typing） | `IO[A]` | `{success: bool, value: A}` |

**注目すべき相違点**: Haskell はレコード型の関数フィールドとしてインターフェースを表現し、TypeScript も同様のアプローチを取ります。一方、Scala、Java、Rust、F#、C# は伝統的なインターフェース/トレイト、Clojure はプロトコル、Elixir はビヘイビアを使用します。表現形式は異なりますが、**「実装の詳細を隠蔽し、テスト時に差し替え可能にする」** という本質は全言語で共通です。

---

## 11.5 Resource — 安全なリソース管理の全 11 言語比較

### 代表 3 言語の詳細比較

**Scala — cats-effect Resource:**

```scala
def execQuery(query: String): Resource[IO, List[QuerySolution]] = {
  val connection: Resource[IO, RDFConnection] =
    Resource.make(
      IO.blocking(RDFConnectionRemote.create().destination(endpoint).build())
    )(conn => IO.blocking(conn.close()))

  for {
    conn   <- connection
    result <- Resource.eval(IO.blocking { conn.query(query).execSelect().asScala.toList })
  } yield result
}
```

**Java — Resource（自作）:**

```java
public final class Resource<A> {
    private final IO<A> acquire;
    private final Consumer<A> release;

    public static <A extends AutoCloseable> Resource<A> fromAutoCloseable(IO<A> acquire) {
        return new Resource<>(acquire, a -> { try { a.close(); } catch (Exception e) { throw new RuntimeException(e); } });
    }

    public <B> IO<B> use(Function<A, IO<B>> f) {
        return acquire.flatMap(resource ->
            f.apply(resource).guarantee(IO.effect(() -> release.accept(resource))));
    }
}
```

**Python — contextlib ベース:**

```python
class Resource(Generic[T]):
    def __init__(self, acquire: Callable[[], T], release: Callable[[T], None]) -> None:
        self._acquire = acquire
        self._release = release

    @contextmanager
    def use(self) -> Generator[T, None, None]:
        resource = self._acquire()
        try:
            yield resource
        finally:
            self._release(resource)
```

### Resource パターンの言語別対応

| 言語 | Resource 型 | ネイティブサポート | 特徴 |
|------|------------|-------------------|------|
| Scala | `Resource[IO, A]` | cats-effect ライブラリ | for 内包表記で合成可能 |
| Haskell | `bracket` / `ResourceT` | 標準ライブラリ | `bracket acquire release use` |
| Rust | `Drop` trait（RAII） | 言語レベル | 所有権でスコープ管理 |
| Java | `Resource<A>` (自作) | `try-with-resources` | `AutoCloseable` との統合 |
| Python | `Resource[T]` (自作) | `with` 文 / `contextlib` | コンテキストマネージャ |
| TypeScript | `Resource<A>` (自作) | なし | Promise ベースの保証 |
| F# | `Resource<'a>` (自作) | `use` キーワード | `IDisposable` との統合 |
| C# | `Resource<T>` (自作) | `using` 文 | `IDisposable` / `IAsyncDisposable` |
| Clojure | `with-open` | 標準マクロ | Java の AutoCloseable を活用 |
| Elixir | `Agent` + `try/after` | OTP プロセス | プロセス終了時に自動解放 |
| Ruby | `Resource` (自作) | `ensure` | ブロック + ensure パターン |

**注目点**: Rust は RAII（Resource Acquisition Is Initialization）により、所有権がスコープを外れた時点で自動的にリソースが解放されるため、明示的な Resource 型が不要です。これは Rust の所有権システムが並行処理だけでなくリソース管理でも強力に機能することを示しています。

---

## 11.6 テスト用スタブ — DI の恩恵

### 代表 3 言語のスタブ実装

DataAccess を抽象化した最大の恩恵は、テスト時にスタブを差し替えられることです。

**Scala — 匿名 trait 実装:**

```scala
def cachedAttractions(dataAccess: DataAccess): IO[DataAccess] = for {
  cache <- Ref.of[IO, Map[String, List[Attraction]]](Map.empty)
} yield new DataAccess {
  def findAttractions(name: String, ordering: AttractionOrdering, limit: Int): IO[List[Attraction]] = {
    val key = s"$name-$ordering-$limit"
    for {
      cached <- cache.get.map(_.get(key))
      result <- cached match {
        case Some(v) => IO.pure(v)
        case None    => dataAccess.findAttractions(name, ordering, limit)
                          .flatTap(r => cache.update(_ + (key -> r)))
      }
    } yield result
  }
  // ...
}
```

**Haskell — レコード値の差し替え:**

```haskell
mkTestDataAccess :: IO DataAccess
mkTestDataAccess = return DataAccess
    { findAttractions = \name _ limit ->
        return $ take limit [Attraction "Test" (Just "desc") testLocation | name /= ""]
    , findArtistsFromLocation = \_ limit -> return $ Right $ take limit [MusicArtist "Artist"]
    , findMoviesAboutLocation = \_ limit -> return $ Right $ take limit [Movie "Movie"]
    }

mkFailingDataAccess :: IO DataAccess
mkFailingDataAccess = return DataAccess
    { findAttractions = \_ _ limit -> return $ take limit [testAttraction]
    , findArtistsFromLocation = \_ _ -> return $ Left "Network error"
    , findMoviesAboutLocation = \_ _ -> return $ Left "Timeout"
    }
```

**Rust — Builder パターンのスタブ:**

```rust
pub struct StubDataAccess {
    attractions: Vec<Attraction>,
    artists_error: Option<String>,
    movies_error: Option<String>,
}

impl StubDataAccess {
    pub fn new() -> Self { /* ... */ }
    pub fn with_attractions(mut self, v: Vec<Attraction>) -> Self { self.attractions = v; self }
    pub fn with_artists_error(mut self, e: &str) -> Self { self.artists_error = Some(e.into()); self }
}

#[async_trait]
impl DataAccess for StubDataAccess { /* ... */ }
```

### スタブパターンの比較

| 言語 | スタブ実装方式 | 特徴 |
|------|-------------|------|
| Scala | 匿名 trait / テスト用 object | `new DataAccess { ... }` |
| Haskell | レコード値の直接構築 | 関数フィールドを差し替え |
| Rust | Builder パターン + impl | `.with_attractions(...)` チェーン |
| Java | 匿名クラス / ラムダ | `new DataAccess() { ... }` |
| Python | テスト用クラス | `TestDataAccess(DataAccess)` |
| TypeScript | オブジェクトリテラル | `{ findAttractions: ... }` |
| F# | オブジェクト式 | `{ new IDataAccess with ... }` |
| C# | クラス実装 | `class TestDataAccess : IDataAccess` |
| Clojure | defrecord + protocol 実装 | `(->TestDataAccess ...)` |
| Elixir | @behaviour 実装モジュール | `defmodule TestDataAccess do` |
| Ruby | include DataAccess | `class InMemoryDataAccess` |

**注目点**: Haskell と TypeScript は DataAccess がレコード/オブジェクトリテラルのため、フィールドを直接差し替えるだけでスタブが作れます。これは「DI コンテナ」のような重い仕組みが不要であることを示しています。

---

## 11.7 SearchReport — 部分的失敗への対処

### 共通パターン

TravelGuide アプリケーションでは、アーティスト検索や映画検索が失敗しても、アトラクション情報だけで旅行ガイドを生成します。失敗情報は `SearchReport` に記録されます。

**Scala のアプリケーションロジック（概要）:**

```scala
def travelGuide(da: DataAccess, name: String): IO[Option[TravelGuide]] = for {
  attractions <- da.findAttractions(name, ByLocationPopulation, 3)
  guide       <- attractions.headOption.traverse { attraction =>
    val locId = attraction.location.id
    for {
      artists <- da.findArtistsFromLocation(locId, 2).attempt
      movies  <- da.findMoviesAboutLocation(locId, 2).attempt
    } yield TravelGuide(attraction, collectSubjects(artists, movies))
  }
} yield guide
```

**Haskell のアプリケーションロジック（概要）:**

```haskell
travelGuideWithReport :: DataAccess -> String -> IO (Maybe TravelGuide)
travelGuideWithReport da name = do
    attractions <- findAttractions da name AttrByLocationPopulation 3
    case attractions of
        []    -> return Nothing
        (a:_) -> do
            artists <- findArtistsFromLocation da (locId $ attrLocation a) 2
            movies  <- findMoviesAboutLocation da (locId $ attrLocation a) 2
            let errors = collectErrors [artists, movies]
            return $ Just TravelGuide { tgAttraction = a, tgSubjects = ..., tgSearchReport = ... }
```

このパターンの共通構造は以下です。

1. アトラクションを検索（失敗 → `None` / `Nothing` を返す）
2. アーティストと映画を検索（**失敗しても続行**）
3. エラーを `SearchReport` に収集
4. 成功した結果だけで `TravelGuide` を組み立て

---

## 11.8 比較分析

### DI パターンの 3 つのアプローチ

11 言語の DataAccess 抽象化は、以下の 3 つのアプローチに分類できます。

#### アプローチ 1: 型システム統合型

言語の型システムと深く統合され、コンパイル時に実装の完全性が検証されます。

**採用言語**: Scala（trait）、Rust（trait + async_trait）、Java（interface）、F#（abstract type）、C#（interface）

**利点**: 実装漏れをコンパイル時に検出、IDE サポートが充実

#### アプローチ 2: レコード/オブジェクト型

インターフェースを関数のレコード（またはオブジェクトリテラル）として表現します。

**採用言語**: Haskell（data record）、TypeScript（interface with function fields）

**利点**: 軽量でスタブ作成が容易、部分的な差し替えが可能

#### アプローチ 3: プロトコル/ビヘイビア型

言語固有のポリモーフィズム機構を使用します。

**採用言語**: Clojure（defprotocol）、Elixir（@behaviour）、Ruby（module/duck typing）

**利点**: 動的言語の柔軟性を活かしつつ構造を提供

### リソース管理の安全性スペクトラム

| 安全性レベル | 言語 | 仕組み |
|-------------|------|-------|
| **所有権ベース（最高）** | Rust | RAII + Drop trait。コンパイル時にリソースリークを防止 |
| **型レベル（高）** | Scala | `Resource[IO, A]` が確実な解放を型で保証 |
| **言語構文（中〜高）** | Java, Python, F#, C# | try-with-resources / with / use / using |
| **プロセスベース（中〜高）** | Elixir | プロセス終了時に自動クリーンアップ |
| **規約ベース（低〜中）** | TypeScript, Ruby, Clojure, Haskell | 開発者が明示的に bracket / ensure を使用 |

### テスト容易性の比較

すべての言語で DataAccess の抽象化によりテスト容易性が確保されていますが、スタブ作成の容易さには差があります。

| 容易さ | 言語 | 理由 |
|--------|------|------|
| **最も容易** | Haskell, TypeScript | レコード/オブジェクトのフィールドを直接構築 |
| **容易** | Scala, Clojure | 匿名実装 / defrecord |
| **標準** | Java, Rust, F#, C#, Elixir | クラス/モジュール実装が必要 |
| **やや煩雑** | Python, Ruby | ABC / module の実装 + ボイラープレート |

---

## 11.9 言語固有の特徴

### Scala — for 内包表記による Resource の合成

Scala の `Resource` は for 内包表記で合成可能であり、複数のリソースを宣言的に扱えます。データベース接続とクエリ実行を一つの式で安全に結合できます。

### Haskell — レコード型 DI の優雅さ

Haskell の DataAccess はレコード型の関数フィールドであり、テスト時にはレコード値を直接構築するだけです。DI フレームワークが一切不要で、純粋な関数合成だけで依存性の注入が完結します。

### Rust — 所有権による二重の安全性

Rust では `trait DataAccess: Send + Sync` により、DataAccess 実装がスレッド安全であることをコンパイル時に保証します。さらに RAII によるリソース管理は、Resource 型を明示的に使わなくても安全です。

### Clojure — プロトコルと REPL 駆動開発

Clojure のプロトコルは実行時にも差し替え可能であり、REPL でインタラクティブにスタブを切り替えながら開発できます。

### Elixir — @behaviour + OTP の統合

Elixir の @behaviour はコンパイル時の警告を提供しつつ、Agent によるキャッシュ実装で OTP のプロセスモデルを自然に活用できます。

### Java — record + sealed interface による FP 表現

Java 17 の record と sealed interface により、Scala の case class と ADT に近い表現力が得られます。`AutoCloseable` との統合で、既存の Java エコシステムとの互換性も維持しています。

---

## 11.10 選択指針

### プロジェクト特性別の推奨

| プロジェクト特性 | 推奨アプローチ | 適切な言語 |
|----------------|-------------|-----------|
| 大規模エンタープライズ | 型システム統合型 DI | Scala, Java, C#, F# |
| マイクロサービス | プロセスベース DI | Elixir, Clojure |
| 高性能システム | 所有権ベースリソース管理 | Rust |
| 学術/研究 | レコード型 DI | Haskell |
| Web フロントエンド | オブジェクト型 DI | TypeScript |
| プロトタイプ/スクリプト | duck typing DI | Python, Ruby |

### FP アプリケーション設計のベストプラクティス

1. **ドメインモデルはイミュータブルに** — すべての言語で `case class` / `record` / `defstruct` 等を使用
2. **外部依存はインターフェースで抽象化** — テスト容易性と差し替え可能性を確保
3. **リソースは型で管理** — `Resource` / `bracket` / RAII で確実な解放を保証
4. **部分的失敗に対処** — `SearchReport` パターンで失敗情報を収集しつつ処理を続行
5. **純粋関数とエフェクトを分離** — ビジネスロジックは純粋に、IO は境界に

---

## 11.11 まとめ

本章では、TravelGuide アプリケーションを題材に、11 言語の FP アプリケーション設計パターンを比較しました。

**共通する本質**: すべての言語が「イミュータブルなドメインモデル」「DataAccess の抽象化による DI」「安全なリソース管理」「部分的失敗への対処」という 4 つの柱を共有しています。FP の原則に従えば、どの言語でも同じ設計思想でアプリケーションを構築できます。

**根本的な違い**: DI の表現形式に最大の差があります。Haskell のレコード型 DI は DI フレームワーク不要の最軽量アプローチ、Scala/Java/C# の trait/interface は IDE サポートと型安全性が充実、Clojure/Elixir のプロトコル/ビヘイビアは動的言語の柔軟性を活かします。

**実践的な教訓**: FP アプリケーション設計では、DI コンテナや複雑なフレームワークは不要です。「インターフェースを引数として受け取る」というシンプルなパターンだけで、テスト容易性、保守性、拡張性のすべてが得られます。これが 11 言語を通じて見えてくる FP の最も重要な実践的価値です。

本シリーズの全 12 章を通じて、関数型プログラミングの基礎から実践まで、11 言語の視点から包括的に比較してきました。各言語には固有の強みがありますが、**純粋関数、イミュータブルデータ、型による安全性、宣言的な合成**という FP の核心は普遍です。
