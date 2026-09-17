---
type: Article
title: "第12章: テスト戦略とプロパティベーステスト — 11言語比較"
description: "第 11 章までで、関数型プログラミングの基礎から並行処理まで幅広い概念を学んできました。"
tags: [article, grokkingfp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第12章: テスト戦略とプロパティベーステスト — 11言語比較

## 12.1 はじめに

第 11 章までで、関数型プログラミングの基礎から並行処理まで幅広い概念を学んできました。本章では、FP の最大の恩恵の一つである**テスト容易性**に焦点を当てます。

純粋関数は同じ入力に対して常に同じ出力を返すため、テストが驚くほど簡単になります。しかし、特定のテストケースを手で書くだけでは、見落としているエッジケースがあるかもしれません。そこで登場するのが**プロパティベーステスト（PBT）** です。ランダムな入力を自動生成し、関数が満たすべき**不変条件（プロパティ）** を検証します。

本章では、11 言語それぞれの PBT ライブラリを比較し、ジェネレータの合成、プロパティの定義、Shrinking（最小反例の探索）といった共通概念がどのように表現されるかを見ていきます。

---

## 12.2 共通の本質 — テストの 3 層構造

### FP テストの基本戦略

すべての言語に共通する FP テストのアプローチは、3 つの層で構成されます。

**第 1 層: 純粋関数の単体テスト** — 入力と出力だけを検証すればよく、セットアップもモックも不要です。FP では関数の大部分が純粋なため、この層が最も厚くなります。

**第 2 層: プロパティベーステスト** — ランダムな入力を生成し、「結果のサイズは入力以下」「すべての結果が条件を満たす」といった不変条件を検証します。手書きのテストケースでは発見できないエッジケースを自動的に発見します。

**第 3 層: 統合テスト（スタブ活用）** — DataAccess のような外部依存をスタブに差し替え、コンポーネント間の連携をテストします。FP では副作用が明示的に分離されているため、スタブの作成が容易です。

### PBT の 3 要素

プロパティベーステストは、どの言語でも以下の 3 要素で構成されます。

1. **ジェネレータ（Generator）** — テスト入力をランダムに生成する仕組み
2. **プロパティ（Property）** — 入力に対して常に成り立つべき不変条件
3. **Shrinking** — テスト失敗時に最小の反例を見つける仕組み

---

## 12.3 PBT ライブラリの実装方式

11 言語の PBT ライブラリは、以下の 3 つのアプローチに分類できます。

### アプローチ 1: 専用 PBT フレームワーク（型クラス/プロトコル統合）

言語の型システムと深く統合された専用 PBT フレームワークです。ジェネレータの自動導出や Shrinking の自動化が特徴です。

| 言語 | ライブラリ | ジェネレータ型 | 特徴 |
|------|-----------|--------------|------|
| Haskell | QuickCheck | `Arbitrary a` | PBT の元祖。型クラスで自動導出 |
| Scala | ScalaCheck | `Gen[A]` | for 内包表記でジェネレータ合成 |
| F# | FsCheck | `Gen<'a>` | .NET 向け QuickCheck ポート |
| C# | FsCheck | `Gen<T>` | F# と共通の FsCheck エコシステム |
| Clojure | test.check | `gen/fmap` | Clojure 版 QuickCheck |
| Elixir | StreamData | `StreamData.t()` | Stream ベースのジェネレータ |

### アプローチ 2: マクロ/DSL ベースの PBT

マクロや DSL を使って、ジェネレータとプロパティを宣言的に記述します。

| 言語 | ライブラリ | 記述方式 | 特徴 |
|------|-----------|---------|------|
| Rust | proptest | `proptest!` マクロ | 範囲式でジェネレータを記述 |
| Python | Hypothesis | `@given` デコレータ | 戦略（Strategy）ベース |
| TypeScript | fast-check | `fc.property()` | Arbitrary ベースのジェネレータ |

### アプローチ 3: 手動ジェネレータ + 反復テスト

専用 PBT ライブラリを使わず、手動でランダムデータを生成して反復テストします。

| 言語 | 方式 | 記述方式 | 特徴 |
|------|------|---------|------|
| Java | `@RepeatedTest` + 手動 | JUnit 5 の反復テスト | 標準機能のみで PBT 的テスト |
| Ruby | 手動ジェネレータ | カスタム関数 | 軽量で依存なし |

---

## 12.4 ジェネレータの合成 — 全 11 言語比較

PBT の核心は**ジェネレータの合成**です。基本型のジェネレータを組み合わせて、ドメインオブジェクトのジェネレータを構築します。ここでは `Location` のジェネレータを各言語で比較します。

### 代表 3 言語の詳細比較

**Haskell — QuickCheck の Arbitrary 型クラス:**

```haskell
instance Arbitrary LocationId where
    arbitrary = LocationId <$> listOf1 (elements ['a'..'z'])

instance Arbitrary Location where
    arbitrary = Location
        <$> arbitrary           -- LocationId を自動生成
        <*> listOf1 (elements ['A'..'Z'])  -- name
        <*> (abs <$> arbitrary)            -- population (非負)
```

Haskell では `Arbitrary` 型クラスのインスタンスを定義するだけで、QuickCheck が自動的にランダムデータを生成します。`<$>` と `<*>` で Applicative に合成するパターンが特徴的です。

**Scala — ScalaCheck の for 内包表記:**

```scala
val locationGen: Gen[Location] = for {
  id         <- Gen.alphaNumStr.map(LocationId.apply)
  name       <- Gen.alphaStr
  population <- Gen.posNum[Int]
} yield Location(id, name, population)
```

Scala では `for` 内包表記でジェネレータを合成します。`Gen.posNum` や `Gen.alphaStr` といった便利なビルトインジェネレータが豊富です。

**Rust — proptest の範囲式:**

```rust
proptest! {
    #[test]
    fn sort_preserves_elements(
        populations in prop::collection::vec(0i32..10_000_000, 0..10)
    ) {
        let locations: Vec<Location> = populations.iter().enumerate()
            .map(|(i, &pop)| Location::new(
                LocationId::new(&format!("loc{}", i)),
                &format!("City{}", i),
                pop,
            ))
            .collect();
        let sorted = sort_by_population(locations.clone());
        prop_assert_eq!(sorted.len(), locations.len());
    }
}
```

Rust の proptest では `0i32..10_000_000` のような範囲式でジェネレータを簡潔に記述できます。

### 全 11 言語のジェネレータ比較

<details>
<summary>Haskell — QuickCheck</summary>

```haskell
instance Arbitrary LocationId where
    arbitrary = LocationId <$> listOf1 (elements ['a'..'z'])

instance Arbitrary Location where
    arbitrary = Location
        <$> arbitrary
        <*> listOf1 (elements ['A'..'Z'])
        <*> (abs <$> arbitrary)
```

</details>

<details>
<summary>Scala — ScalaCheck</summary>

```scala
val locationIdGen: Gen[LocationId] =
  Gen.alphaNumStr.map(LocationId.apply)

val locationGen: Gen[Location] = for {
  id         <- locationIdGen
  name       <- Gen.alphaStr
  population <- Gen.posNum[Int]
} yield Location(id, name, population)
```

</details>

<details>
<summary>Rust — proptest</summary>

```rust
// 範囲式で直接記述
proptest! {
    #[test]
    fn test_filter(
        populations in prop::collection::vec(0i32..10_000_000, 0..10)
    ) {
        // populations をもとに Location を構築
    }
}
```

</details>

<details>
<summary>Python — Hypothesis</summary>

```python
# Hypothesis の Strategy を使う場合
from hypothesis import given, strategies as st

@given(
    locations=st.lists(st.builds(
        Location,
        id=st.from_type(LocationId),
        name=st.text(min_size=1),
        population=st.integers(min_value=0, max_value=10_000_000)
    )),
    min_pop=st.integers(min_value=0, max_value=10_000_000)
)
def test_filter_result_size(locations, min_pop):
    filtered = filter_popular_locations(locations, min_pop)
    assert len(filtered) <= len(locations)
```

手動ジェネレータ方式（本書の実装）:

```python
def generate_location(min_population=0, max_population=10000000):
    name = "".join(random.choices(string.ascii_letters, k=random.randint(3, 10)))
    return Location(
        id=LocationId(f"Q{random.randint(1, 1000000)}"),
        name=name,
        population=random.randint(min_population, max_population),
    )
```

</details>

<details>
<summary>TypeScript — fast-check / カスタム Gen</summary>

```typescript
// カスタム Gen（本書の実装）
const locationGen = Gen.location()
const location = locationGen()  // ランダムな Location を生成

// fast-check を使う場合
import * as fc from 'fast-check'
const locationArb = fc.record({
  id: fc.string().map(LocationId.of),
  name: fc.string({ minLength: 1 }),
  population: fc.nat({ max: 10_000_000 }),
})
```

</details>

<details>
<summary>Java — 手動ジェネレータ</summary>

```java
static Location randomLocation() {
    return new Location(
        new LocationId("Q" + nonNegativeInt(1000000)),
        randomString(),
        nonNegativeInt(10_000_000)
    );
}
```

</details>

<details>
<summary>F# — FsCheck</summary>

```fsharp
let locationGen = gen {
    let! id = Arb.generate<string> |> Gen.filter (not << String.IsNullOrEmpty)
    let! name = Arb.generate<string> |> Gen.filter (not << String.IsNullOrEmpty)
    let! population = Gen.choose(0, 10_000_000)
    return { Id = LocationId id; Name = name; Population = population }
}
```

</details>

<details>
<summary>C# — FsCheck</summary>

```csharp
var locationGen = from id in Arb.Generate<string>().Where(s => !string.IsNullOrEmpty(s))
                  from name in Arb.Generate<string>().Where(s => !string.IsNullOrEmpty(s))
                  from pop in Gen.Choose(0, 10_000_000)
                  select new Location(LocationId.Create(id), name, pop);
```

</details>

<details>
<summary>Clojure — test.check</summary>

```clojure
(def location-gen
  (gen/fmap (fn [[id name pop]]
              (location id name pop))
    (gen/tuple
      (gen/fmap #(str "Q" %) gen/nat)
      gen/string-alphanumeric
      (gen/choose 0 10000000))))
```

</details>

<details>
<summary>Elixir — StreamData</summary>

```elixir
location_gen =
  gen all id <- StreamData.string(:alphanumeric, min_length: 1),
          name <- StreamData.string(:alphanumeric, min_length: 1),
          population <- StreamData.integer(0..10_000_000) do
    Location.new(LocationId.new(id), name, population)
  end
```

</details>

<details>
<summary>Ruby — 手動ジェネレータ</summary>

```ruby
def self.random_location
  Location.new(
    id: LocationId.new("Q#{rand(1..1_000_000)}"),
    name: ('A'..'Z').to_a.sample(rand(3..10)).join,
    population: rand(0..10_000_000)
  )
end
```

</details>

---

## 12.5 プロパティの定義 — filterPopularLocations を例に

PBT の最も重要な要素は**プロパティの定義**です。`filterPopularLocations` という純粋関数を題材に、3 つの標準的なプロパティを 11 言語で比較します。

```
filterPopularLocations(locations, minPopulation)
  → locations のうち population >= minPopulation のものだけを返す
```

### 3 つの標準プロパティ

| プロパティ | 意味 |
|-----------|------|
| 結果サイズ ≤ 入力サイズ | フィルタは要素を増やさない |
| 全結果が条件充足 | 返された要素はすべて条件を満たす |
| 偽陰性なし | 条件を満たす要素はすべて結果に含まれる |

### 代表 3 言語での実装

**Haskell:**

```haskell
it "result size <= input size" $
    property $ \(locs :: [Location]) (minPop :: Int) ->
        length (filterPopularLocations locs (abs minPop)) <= length locs

it "all results meet minimum" $
    property $ \(locs :: [Location]) (minPop :: Int) ->
        let filtered = filterPopularLocations locs (abs minPop)
        in all (\loc -> locPopulation loc >= abs minPop) filtered
```

**Scala:**

```scala
property("result size <= input size") =
  forAll(locationsGen, Gen.posNum[Int]) {
    (locations: List[Location], minPop: Int) =>
      filterPopularLocations(locations, minPop).size <= locations.size
  }

property("all results meet minimum population") =
  forAll(locationsGen, Gen.posNum[Int]) {
    (locations: List[Location], minPop: Int) =>
      filterPopularLocations(locations, minPop)
        .forall(_.population >= minPop)
  }
```

**Rust:**

```rust
proptest! {
    #[test]
    fn filter_preserves_or_reduces_size(
        populations in prop::collection::vec(0i32..10_000_000, 0..20),
        min_pop in 0i32..10_000_000
    ) {
        let locations = make_locations(populations);
        let filtered = filter_popular_locations(locations.clone(), min_pop);
        prop_assert!(filtered.len() <= locations.len());
    }
}
```

---

## 12.6 DataAccess スタブ — テスト可能な設計

FP のテスト戦略において、外部依存の分離は重要です。すべての言語で「DataAccess インターフェースを定義し、テスト時にスタブに差し替える」パターンが共通しています。

### スタブ実装の 3 パターン

| パターン | 言語例 | 特徴 |
|---------|--------|------|
| trait/interface + 匿名実装 | Scala, Haskell, F# | 型安全、インターフェースベース |
| Builder パターン | Java, Rust | 柔軟な設定、エラー注入が容易 |
| レコード/マップ | Clojure, Elixir, Ruby | 軽量、動的型付けの利点を活用 |

**Scala のスタブ実装:**

```scala
val testDataAccess: DataAccess = new DataAccess {
  def findAttractions(name: String, ordering: AttractionOrdering, limit: Int) =
    IO.pure(List(Attraction("Test", Some("desc"), testLocation)))
  def findArtistsFromLocation(locationId: LocationId, limit: Int) =
    IO.pure(Right(List(MusicArtist("Test Artist"))))
  def findMoviesAboutLocation(locationId: LocationId, limit: Int) =
    IO.pure(Right(List(Movie("Test Movie"))))
}
```

**Java の Builder パターン:**

```java
DataAccess dataAccess = TestDataAccess.builder()
    .withAttractions(TOWER_BRIDGE)
    .withArtists(QUEEN)
    .failOnMovies("Timeout")
    .build();
```

**Clojure のレコード型:**

```clojure
(defrecord TestDataAccess [attractions artists movies hotels]
  DataAccess
  (find-attractions [_ name ordering limit]
    {:ok (take limit (filter #(str/includes? (:name %) name) attractions))})
  (find-artists-from-location [_ location-id limit]
    {:ok (take limit artists)}))
```

---

## 12.7 SearchReport — テスト可観測性の向上

テスト可能性を高めるために導入される `SearchReport` は、検索の統計情報とエラー情報を保持する構造です。これにより、「検索は成功したが結果が不十分だった」ケースと「エラーが発生した」ケースを区別できます。

すべての言語で共通するパターン:

```
SearchReport {
    attractionsSearched: Int   -- 検索したアトラクション数
    errors: List[String]       -- 発生したエラーのリスト
}
```

エラーハンドリングとの組み合わせにより、**部分的な成功**を表現できます。

```scala
// Scala: エラーがあっても結果を返す
val errors = List(artistsResult, moviesResult).collect { case Left(e) => e }
val artists = artistsResult.getOrElse(Nil)
TravelGuide(attraction, subjects, SearchReport(attractions.size, errors))
```

```haskell
-- Haskell: Either からエラーを収集
let errors = collectErrors [artistsResult, moviesResult]
let artists = either (const []) id artistsResult
```

```rust
// Rust: Result からエラーを収集
let mut errors = Vec::new();
if let Err(e) = &artists_result { errors.push(e.clone()); }
let artists = artists_result.unwrap_or_default();
```

---

## 12.8 比較分析 — 3 つの発見

### 発見 1: PBT 表現力の 3 段階

ジェネレータの合成と Shrinking の自動化度合いで、PBT ライブラリは 3 段階に分かれます。

| 段階 | 特徴 | 言語 |
|------|------|------|
| **完全自動** | 型からジェネレータ自動導出 + Shrinking 自動 | Haskell (QuickCheck), F#/C# (FsCheck) |
| **半自動** | ビルトインジェネレータ豊富 + Shrinking 対応 | Scala (ScalaCheck), Rust (proptest), Python (Hypothesis), Clojure (test.check), Elixir (StreamData), TypeScript (fast-check) |
| **手動** | ジェネレータ手書き + Shrinking なし | Java (@RepeatedTest), Ruby (手動) |

Haskell の QuickCheck は `Arbitrary` 型クラスにより、新しい型のジェネレータを定義するだけで自動的に Shrinking まで提供されます。一方、Java では JUnit 5 の `@RepeatedTest` と手動ジェネレータの組み合わせで PBT 的なテストを実現しますが、Shrinking はありません。

### 発見 2: テスト容易性と型システムの関係

テストのしやすさは、言語の型システムと副作用の管理方法に強く依存します。

| 型システム | 副作用管理 | テスト容易性 | 言語 |
|-----------|-----------|-------------|------|
| 強い型 + 純粋性保証 | IO モナド | 最高 | Haskell |
| 強い型 + エフェクト型 | IO[A] / Task<A> | 高 | Scala, F#, Rust |
| 漸進的型付け | 慣習的分離 | 中〜高 | TypeScript, Python |
| 動的型付け | パターンマッチ | 中 | Clojure, Elixir, Ruby |
| 静的型付け + 例外 | try-catch | 中 | Java, C# |

Haskell では型システムが純粋関数と IO を厳密に分離するため、テスト対象の関数が純粋であることが型レベルで保証されます。

### 発見 3: DataAccess 抽象化の共通性

11 言語すべてで、外部依存をインターフェース/プロトコル/レコードで抽象化し、テスト時にスタブに差し替えるパターンが確認できました。

| 抽象化手段 | 言語 |
|-----------|------|
| trait / interface | Scala, Java, Rust, C# |
| 型クラス + レコード型 | Haskell |
| abstract class (ABC) | Python |
| object expression | F# |
| protocol / behaviour | Clojure, Elixir |
| module (mixin) | Ruby |
| interface (構造的型) | TypeScript |

FP の「副作用の明示的分離」原則が、テスト可能な設計を自然に導くことが、すべての言語で確認できます。

---

## 12.9 言語固有の特徴

### Haskell — QuickCheck: PBT の原点

QuickCheck は 2000 年に発表された PBT の元祖です。`Arbitrary` 型クラスによる自動導出と、`shrink` 関数による最小反例の探索が最大の特徴です。

```haskell
instance Arbitrary Attraction where
    arbitrary = Attraction <$> arbitrary <*> arbitrary <*> arbitrary
    shrink (Attraction n d l) =
        [Attraction n' d l | n' <- shrink n] ++
        [Attraction n d' l | d' <- shrink d]
```

### Rust — proptest: 安全性と人間工学の両立

proptest はマクロベースの DSL により、範囲式でジェネレータを簡潔に記述できます。`prop_assert!` マクロでアサーションを行い、失敗時には自動的に Shrinking が行われます。

```rust
proptest! {
    #[test]
    fn validate_rejects_negative_population(
        name in "[a-zA-Z]+",
        population in i32::MIN..-1i32
    ) {
        let location = Location::new(LocationId::new("test"), &name, population);
        prop_assert!(!validate_location(location).is_valid());
    }
}
```

### Python — Hypothesis: 実用性重視のデコレータ方式

Hypothesis は `@given` デコレータで戦略（Strategy）を指定する方式です。pytest との統合がシームレスで、実プロジェクトでの採用率が高いライブラリです。

### Clojure — test.check: データとしてのジェネレータ

Clojure の test.check では、ジェネレータ自体がデータ（値）として扱われます。`gen/fmap`, `gen/bind`, `gen/such-that` でジェネレータを合成します。

### Elixir — StreamData: Stream とジェネレータの統合

StreamData は Elixir の Stream メタファーとジェネレータを統合しています。`gen all` マクロでモナド的な合成が可能です。

### Java — @RepeatedTest: 標準機能での PBT 近似

専用 PBT ライブラリを使わず、JUnit 5 の `@RepeatedTest(100)` で繰り返しテストを行い、手動ジェネレータでランダム入力を生成するアプローチです。軽量で追加依存が不要です。

---

## 12.10 選択指針

### PBT ライブラリの選択

```
PBT が必須か？
├─ はい → 言語に最適なライブラリを選択
│   ├─ Haskell → QuickCheck（標準的）
│   ├─ Scala → ScalaCheck
│   ├─ Rust → proptest
│   ├─ Python → Hypothesis
│   ├─ TypeScript → fast-check
│   ├─ F# / C# → FsCheck
│   ├─ Clojure → test.check
│   └─ Elixir → StreamData
└─ いいえ → 手動ジェネレータ + 反復テスト
    ├─ Java → @RepeatedTest + ランダム生成関数
    └─ Ruby → カスタムジェネレータ + 反復実行
```

### テスト戦略の選択

| プロジェクト特性 | 推奨アプローチ |
|----------------|--------------|
| 数学的な不変条件が明確 | PBT を主軸に |
| ドメインロジックが複雑 | PBT + ドメインオブジェクトジェネレータ |
| 外部依存が多い | DataAccess スタブ + 統合テスト |
| CI 時間に制約あり | PBT の反復回数を調整 |
| チーム PBT 経験なし | 手動ジェネレータから段階的に導入 |

---

## 12.11 まとめ

本章では、11 言語のテスト戦略と PBT ライブラリを比較しました。

**PBT ライブラリ一覧:**

| 言語 | ライブラリ | ジェネレータ合成 | Shrinking |
|------|-----------|----------------|-----------|
| Haskell | QuickCheck | `<$>`, `<*>` (Applicative) | 自動 |
| Scala | ScalaCheck | `for` 内包表記 | 自動 |
| Rust | proptest | `proptest!` マクロ + 範囲式 | 自動 |
| Python | Hypothesis | `@given` + `st.builds` | 自動 |
| TypeScript | fast-check | `fc.record` / カスタム `Gen` | 自動 |
| F# | FsCheck | `gen { }` 計算式 | 自動 |
| C# | FsCheck | LINQ クエリ式 | 自動 |
| Clojure | test.check | `gen/fmap`, `gen/bind` | 自動 |
| Elixir | StreamData | `gen all` マクロ | 自動 |
| Java | @RepeatedTest | 手動関数 | なし |
| Ruby | 手動 | 手動関数 | なし |

**テスト戦略の共通原則:**

1. **純粋関数を最大化** — テスト容易性の根本は純粋性にあります
2. **プロパティで不変条件を検証** — 手書きテストケースの限界を PBT で補います
3. **DataAccess で外部依存を分離** — スタブによるテストを可能にします
4. **SearchReport で可観測性を確保** — 部分的な成功/失敗を区別できます
5. **段階的に導入** — 手動ジェネレータから始め、PBT ライブラリに移行できます
