---
type: Article
title: "Rust で学ぶ関数型プログラミング Part VI: 実践的アプリケーション構築"
description: "Part V では並行処理（Arc、Mutex、チャネル）を学びました。"
tags: [article, grokkingfp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Rust で学ぶ関数型プログラミング Part VI: 実践的アプリケーション構築

## はじめに

Part V では並行処理（Arc、Mutex、チャネル）を学びました。Part VI では、これまで学んだ FP の概念を総動員して**実践的なアプリケーション**を構築します。

Scala では trait による抽象化、Resource によるリソース管理、そしてテスト戦略を学びますが、Rust でも同様のパターンを async-trait、RAII パターン、proptest を使って実現します。

## 第12章: 実践的なアプリケーション構築

### 12.1 ドメインモデルの定義

実際のアプリケーション開発では、ドメインモデルを適切に定義することが重要です。TravelGuide アプリケーションを例に見ていきます。

```rust
/// Newtype パターンで型安全な ID を定義
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct LocationId(pub String);

impl LocationId {
    pub fn new(id: &str) -> Self {
        Self(id.to_string())
    }
}

/// ロケーション
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Location {
    pub id: LocationId,
    pub name: String,
    pub population: i32,
}

/// アトラクション（観光地）
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Attraction {
    pub name: String,
    pub description: Option<String>,
    pub location: Location,
}

/// 旅行ガイド - 最終的な出力モデル
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TravelGuide {
    pub attraction: Attraction,
    pub subjects: Vec<String>,       // 関連するアーティストや映画
    pub search_report: SearchReport, // 検索メタデータ
}
```

Scala と比較すると：

| Scala | Rust |
|-------|------|
| `case class LocationId(value: String)` | `struct LocationId(pub String)` |
| `Option[String]` | `Option<String>` |
| `List[String]` | `Vec<String>` |

### 12.2 DataAccess トレイト - 依存性の抽象化

外部データソースへのアクセスをトレイトで抽象化します。これにより、テスト時にスタブ実装に差し替えることができます。

```rust
use async_trait::async_trait;

/// データアクセス層のトレイト
#[async_trait]
pub trait DataAccess: Send + Sync {
    /// アトラクションを検索
    async fn find_attractions(
        &self,
        name: &str,
        ordering: AttractionOrdering,
        limit: usize,
    ) -> Vec<Attraction>;

    /// ロケーションからアーティストを検索
    async fn find_artists_from_location(
        &self,
        location_id: &LocationId,
        limit: usize,
    ) -> Result<Vec<MusicArtist>, String>;

    /// ロケーションに関する映画を検索
    async fn find_movies_about_location(
        &self,
        location_id: &LocationId,
        limit: usize,
    ) -> Result<Vec<Movie>, String>;
}
```

Scala の trait と比較：

| Scala | Rust |
|-------|------|
| `trait DataAccess[F[_]]` | `#[async_trait] trait DataAccess` |
| `def findAttractions(...): F[List[Attraction]]` | `async fn find_attractions(...) -> Vec<Attraction>` |
| `F[Either[String, List[A]]]` | `Result<Vec<A>, String>` |

### 12.3 テスト用スタブ実装

Builder パターンを使って柔軟にテストデータを設定できるスタブ実装を作成します。

```rust
pub struct StubDataAccess {
    attractions: Vec<Attraction>,
    artists: HashMap<LocationId, Vec<MusicArtist>>,
    movies: HashMap<LocationId, Vec<Movie>>,
    artists_error: Option<String>,
    movies_error: Option<String>,
}

impl StubDataAccess {
    pub fn new() -> Self {
        Self {
            attractions: vec![],
            artists: HashMap::new(),
            movies: HashMap::new(),
            artists_error: None,
            movies_error: None,
        }
    }

    // Builder パターンでメソッドチェーン
    pub fn with_attractions(mut self, attractions: Vec<Attraction>) -> Self {
        self.attractions = attractions;
        self
    }

    pub fn with_artists(mut self, location_id: LocationId, artists: Vec<MusicArtist>) -> Self {
        self.artists.insert(location_id, artists);
        self
    }

    pub fn with_artists_error(mut self, error: &str) -> Self {
        self.artists_error = Some(error.to_string());
        self
    }
}
```

使用例：

```rust
#[tokio::test]
async fn test_travel_guide_with_error() {
    let data = Arc::new(
        StubDataAccess::new()
            .with_attractions(vec![create_test_attraction()])
            .with_artists_error("Artist service unavailable")
    );

    let guide = travel_guide(data.as_ref(), "Test").await;

    assert!(guide.is_some());
    assert!(!guide.unwrap().search_report.errors.is_empty());
}
```

### 12.4 キャッシュ付き DataAccess

デコレーターパターンを使って、既存の DataAccess にキャッシュ機能を追加します。

```rust
pub struct CachedDataAccess<D: DataAccess> {
    inner: Arc<D>,
    attractions_cache: Arc<RwLock<HashMap<String, Vec<Attraction>>>>,
}

impl<D: DataAccess> CachedDataAccess<D> {
    pub fn new(inner: Arc<D>) -> Self {
        Self {
            inner,
            attractions_cache: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    fn cache_key(name: &str, ordering: AttractionOrdering, limit: usize) -> String {
        format!("{}-{:?}-{}", name, ordering, limit)
    }
}

#[async_trait]
impl<D: DataAccess + 'static> DataAccess for CachedDataAccess<D> {
    async fn find_attractions(
        &self,
        name: &str,
        ordering: AttractionOrdering,
        limit: usize,
    ) -> Vec<Attraction> {
        let key = Self::cache_key(name, ordering, limit);

        // キャッシュを確認（読み取りロック）
        {
            let cache = self.attractions_cache.read().await;
            if let Some(cached) = cache.get(&key) {
                return cached.clone();
            }
        }

        // キャッシュにない場合は取得
        let result = self.inner.find_attractions(name, ordering, limit).await;

        // キャッシュに保存（書き込みロック）
        {
            let mut cache = self.attractions_cache.write().await;
            cache.insert(key, result.clone());
        }

        result
    }

    // 他のメソッドは inner に委譲
}
```

### 12.5 TravelGuide アプリケーション

トレイトを使った依存性注入により、ビジネスロジックをテスト可能な形で実装します。

```rust
/// 旅行ガイドを生成
pub async fn travel_guide(
    data: &dyn DataAccess,
    attraction_name: &str,
) -> Option<TravelGuide> {
    // アトラクションを検索
    let attractions = data
        .find_attractions(attraction_name, AttractionOrdering::ByLocationPopulation, 1)
        .await;

    let attraction = attractions.into_iter().next()?;

    // 関連情報を並行して取得
    let artists_result = data
        .find_artists_from_location(&attraction.location.id, 2)
        .await;
    let movies_result = data
        .find_movies_about_location(&attraction.location.id, 2)
        .await;

    // エラーを収集（失敗しても継続）
    let mut errors = Vec::new();
    if let Err(e) = &artists_result {
        errors.push(e.clone());
    }
    if let Err(e) = &movies_result {
        errors.push(e.clone());
    }

    // 成功した結果を結合
    let artists = artists_result.unwrap_or_default();
    let movies = movies_result.unwrap_or_default();

    let subjects: Vec<String> = artists
        .into_iter()
        .map(|a| a.name)
        .chain(movies.into_iter().map(|m| m.name))
        .collect();

    Some(TravelGuide::new(
        attraction,
        subjects,
        SearchReport::new(1, errors),
    ))
}
```

### 12.6 純粋関数ユーティリティ

副作用のない純粋関数は、単体テストが容易で再利用性が高いです。

```rust
/// 人口でロケーションをフィルタリング
pub fn filter_popular_locations(locations: Vec<Location>, min_population: i32) -> Vec<Location> {
    locations
        .into_iter()
        .filter(|loc| loc.population >= min_population)
        .collect()
}

/// 人口順でソート
pub fn sort_by_population(mut locations: Vec<Location>) -> Vec<Location> {
    locations.sort_by(|a, b| b.population.cmp(&a.population));
    locations
}

/// 複数のサブジェクトを結合
pub fn combine_subjects(subjects_list: Vec<Vec<String>>) -> Vec<String> {
    subjects_list.into_iter().flatten().collect()
}

/// 複数の SearchReport を集約
pub fn aggregate_reports(reports: Vec<SearchReport>) -> SearchReport {
    let total_searched = reports.iter().map(|r| r.attractions_searched).sum();
    let all_errors: Vec<String> = reports.into_iter().flat_map(|r| r.errors).collect();
    SearchReport::new(total_searched, all_errors)
}
```

### 12.7 リソース管理パターン

Scala の Resource に相当する概念を Rust で実装します。

```rust
/// リソース管理のトレイト
pub trait Resource {
    type Item;

    fn use_resource<F, R>(&self, f: F) -> R
    where
        F: FnOnce(&Self::Item) -> R;
}

/// ファイルリソース
pub struct FileResource {
    path: String,
}

impl Resource for FileResource {
    type Item = Result<String, std::io::Error>;

    fn use_resource<F, R>(&self, f: F) -> R
    where
        F: FnOnce(&Self::Item) -> R,
    {
        let content = std::fs::read_to_string(&self.path);
        f(&content)
    }
}
```

Rust の強みは、RAII（Resource Acquisition Is Initialization）パターンにより、リソースの解放が自動的に行われることです。

### 12.8 バリデーション

独自のバリデーション型を定義して、型安全なバリデーションを実装します。

```rust
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Validation<T> {
    Valid(T),
    Invalid(Vec<String>),
}

impl<T> Validation<T> {
    pub fn valid(value: T) -> Self {
        Validation::Valid(value)
    }

    pub fn invalid(errors: Vec<String>) -> Self {
        Validation::Invalid(errors)
    }

    pub fn is_valid(&self) -> bool {
        matches!(self, Validation::Valid(_))
    }

    pub fn map<U, F>(self, f: F) -> Validation<U>
    where
        F: FnOnce(T) -> U,
    {
        match self {
            Validation::Valid(v) => Validation::Valid(f(v)),
            Validation::Invalid(e) => Validation::Invalid(e),
        }
    }

    pub fn and_then<U, F>(self, f: F) -> Validation<U>
    where
        F: FnOnce(T) -> Validation<U>,
    {
        match self {
            Validation::Valid(v) => f(v),
            Validation::Invalid(e) => Validation::Invalid(e),
        }
    }
}

/// Location のバリデーション
pub fn validate_location(location: Location) -> Validation<Location> {
    let mut errors = Vec::new();

    if location.name.trim().is_empty() {
        errors.push("Name cannot be empty".to_string());
    }
    if location.population < 0 {
        errors.push("Population cannot be negative".to_string());
    }

    if errors.is_empty() {
        Validation::valid(location)
    } else {
        Validation::invalid(errors)
    }
}
```

### 12.9 プロパティベーステスト

proptest を使って、ランダムな入力に対する不変条件をテストします。

```rust
use proptest::prelude::*;

proptest! {
    /// ソートは要素を保持する
    #[test]
    fn sort_by_population_preserves_elements(
        populations in prop::collection::vec(0i32..10_000_000, 0..10)
    ) {
        let locations: Vec<Location> = populations
            .iter()
            .enumerate()
            .map(|(i, &pop)| Location::new(
                LocationId::new(&format!("loc{}", i)),
                &format!("City{}", i),
                pop,
            ))
            .collect();

        let sorted = sort_by_population(locations.clone());

        prop_assert_eq!(sorted.len(), locations.len());
    }

    /// ソート結果は降順
    #[test]
    fn sort_by_population_is_sorted(
        populations in prop::collection::vec(0i32..10_000_000, 0..10)
    ) {
        let locations: Vec<Location> = populations
            .iter()
            .enumerate()
            .map(|(i, &pop)| Location::new(
                LocationId::new(&format!("loc{}", i)),
                &format!("City{}", i),
                pop,
            ))
            .collect();

        let sorted = sort_by_population(locations);

        for window in sorted.windows(2) {
            prop_assert!(window[0].population >= window[1].population);
        }
    }

    /// 空の名前は無効
    #[test]
    fn validate_location_empty_name_is_invalid(
        population in 0i32..10_000_000
    ) {
        let location = Location::new(LocationId::new("test"), "", population);
        let result = validate_location(location);
        prop_assert!(!result.is_valid());
    }

    /// 負の人口は無効
    #[test]
    fn validate_location_negative_population_is_invalid(
        name in "[a-zA-Z]+",
        population in i32::MIN..-1i32
    ) {
        let location = Location::new(LocationId::new("test"), &name, population);
        let result = validate_location(location);
        prop_assert!(!result.is_valid());
    }
}
```

Scala の ScalaCheck と比較：

| ScalaCheck | proptest |
|------------|----------|
| `forAll { (n: Int) => ... }` | `proptest! { fn test(n in any::<i32>()) { ... } }` |
| `Gen.choose(0, 100)` | `0i32..100` |
| `Gen.listOfN(10, gen)` | `prop::collection::vec(gen, 0..10)` |
| `Prop.passed` | `prop_assert!(true)` |

## まとめ

Part VI で学んだ重要なポイント：

1. **ドメインモデル**: Newtype パターンで型安全性を確保
2. **トレイト抽象化**: `async_trait` で非同期メソッドを持つトレイトを定義
3. **依存性注入**: トレイトオブジェクト (`&dyn DataAccess`) で実装を差し替え可能に
4. **デコレーターパターン**: キャッシュなどの横断的関心事を分離
5. **純粋関数**: テスタブルで再利用可能なユーティリティ
6. **バリデーション**: 独自型でエラーを収集
7. **プロパティベーステスト**: ランダム入力で不変条件を検証

Scala と Rust の対応表：

| 概念 | Scala | Rust |
|------|-------|------|
| 非同期トレイト | `trait DataAccess[F[_]]` | `#[async_trait] trait DataAccess` |
| テストスタブ | `Stub extends DataAccess` | `impl DataAccess for StubDataAccess` |
| リソース管理 | `Resource[IO, A]` | `trait Resource` / RAII |
| バリデーション | `Validated[E, A]` | `enum Validation<T>` |
| プロパティテスト | ScalaCheck | proptest |

## 参考リンク

- [async-trait crate](https://docs.rs/async-trait/)
- [proptest crate](https://docs.rs/proptest/)
- [Rust Design Patterns](https://rust-unofficial.github.io/patterns/)
