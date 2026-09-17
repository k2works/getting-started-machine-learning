---
type: Article
title: "Part VI: 実践的なアプリケーション構築とテスト"
description: "本章では、これまで学んだ関数型プログラミングの概念を統合し、実践的なアプリケーションを構築します。"
tags: [article, grokkingfp, python]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VI: 実践的なアプリケーション構築とテスト

本章では、これまで学んだ関数型プログラミングの概念を統合し、実践的なアプリケーションを構築します。また、関数型プログラミングにおけるテスト戦略についても学びます。

---

## 第11章: 実践的なアプリケーション構築

### 11.1 TravelGuide アプリケーション

旅行ガイドアプリケーションを例に、実践的な FP アプリケーションの構築方法を学びます。

### 11.2 ドメインモデルの定義

```python
from dataclasses import dataclass
from typing import NewType

# 値オブジェクト: LocationId
LocationId = NewType("LocationId", str)

@dataclass(frozen=True)
class Location:
    """ロケーション（場所）を表す。"""
    id: LocationId
    name: str
    population: int

@dataclass(frozen=True)
class Attraction:
    """アトラクション（観光地）を表す。"""
    name: str
    description: str | None
    location: Location

@dataclass(frozen=True)
class MusicArtist:
    """ミュージシャンを表す。"""
    name: str

@dataclass(frozen=True)
class Movie:
    """映画を表す。"""
    name: str

@dataclass(frozen=True)
class Hotel:
    """ホテルを表す。"""
    name: str
    rating: float
    location: Location

@dataclass(frozen=True)
class TravelGuide:
    """旅行ガイドを表す。"""
    attraction: Attraction
    subjects: list[str]
```

ポイント:

- `@dataclass(frozen=True)` でイミュータブルなデータクラスを作成
- `NewType` で型エイリアスを定義し、型安全性を向上
- 関連するドメインオブジェクトをまとめて定義

### 11.3 データアクセス層の抽象化

外部データソースへのアクセスを抽象基底クラスで抽象化します。

```python
from abc import ABC, abstractmethod
from enum import Enum, auto
from result import Result

class AttractionOrdering(Enum):
    """アトラクションのソート順。"""
    BY_NAME = auto()
    BY_LOCATION_POPULATION = auto()

class DataAccess(ABC):
    """データアクセス層の抽象インターフェース。"""

    @abstractmethod
    def find_attractions(
        self,
        name: str,
        ordering: AttractionOrdering,
        limit: int,
    ) -> list[Attraction]:
        """アトラクションを検索する。"""
        pass

    @abstractmethod
    def find_artists_from_location(
        self,
        location_id: LocationId,
        limit: int,
    ) -> Result[list[MusicArtist], str]:
        """ロケーションに関連するアーティストを検索する。"""
        pass

    @abstractmethod
    def find_movies_about_location(
        self,
        location_id: LocationId,
        limit: int,
    ) -> Result[list[Movie], str]:
        """ロケーションに関する映画を検索する。"""
        pass

    @abstractmethod
    def find_hotels_near_location(
        self,
        location_id: LocationId,
        limit: int,
    ) -> Result[list[Hotel], str]:
        """ロケーション近くのホテルを検索する。"""
        pass
```

ポイント:

- `ABC` で抽象基底クラスを定義
- `Result` 型でエラーを明示的に扱う
- 戻り値の型を明確に定義

### 11.4 テスト用スタブ実装

```python
from result import Ok, Err

class TestDataAccess(DataAccess):
    """テスト用のデータアクセス実装。"""

    def find_attractions(
        self,
        name: str,
        ordering: AttractionOrdering,
        limit: int,
    ) -> list[Attraction]:
        # テストデータでフィルタリング
        filtered = [
            a for a in TEST_ATTRACTIONS
            if name.lower() in a.name.lower()
        ]
        return filtered[:limit]

    def find_artists_from_location(
        self,
        location_id: LocationId,
        limit: int,
    ) -> Result[list[MusicArtist], str]:
        artists = TEST_ARTISTS.get(location_id, [])
        return Ok(artists[:limit])

    # 他のメソッドも同様に実装...
```

#### エラーを返すスタブ

```python
class FailingDataAccess(DataAccess):
    """エラーを返すテスト用データアクセス。"""

    def __init__(
        self,
        artists_error: str | None = "Network error",
        movies_error: str | None = "Timeout",
    ) -> None:
        self._artists_error = artists_error
        self._movies_error = movies_error

    def find_artists_from_location(
        self,
        location_id: LocationId,
        limit: int,
    ) -> Result[list[MusicArtist], str]:
        if self._artists_error:
            return Err(self._artists_error)
        return Ok([])

    # ...
```

### 11.5 Resource - 安全なリソース管理

Python の `contextlib` を関数型スタイルでラップした `Resource` クラス:

```python
from contextlib import contextmanager
from typing import Callable, Generator, Generic, TypeVar

T = TypeVar("T")
U = TypeVar("U")

class Resource(Generic[T]):
    """安全なリソース管理を提供するクラス。"""

    def __init__(
        self,
        acquire: Callable[[], T],
        release: Callable[[T], None],
    ) -> None:
        self._acquire = acquire
        self._release = release

    @contextmanager
    def use(self) -> Generator[T, None, None]:
        """リソースを取得し、使用後に解放する。"""
        resource = self._acquire()
        try:
            yield resource
        finally:
            self._release(resource)

    def map(self, f: Callable[[T], U]) -> "Resource[U]":
        """リソースの値を変換する。"""
        # 実装略

def make_resource(
    acquire: Callable[[], T],
    release: Callable[[T], None],
) -> Resource[T]:
    """リソースを作成する。"""
    return Resource(acquire, release)
```

```python
# 使用例
def open_file(path: str) -> Resource[TextIO]:
    return make_resource(
        lambda: open(path, "r"),
        lambda f: f.close()
    )

with open_file("data.txt").use() as f:
    content = f.read()
# ファイルは自動的に閉じられる
```

### 11.6 キャッシュの実装

```python
class CachedDataAccess(DataAccess):
    """キャッシュ付きのデータアクセス。"""

    def __init__(self, data_access: DataAccess) -> None:
        self._data_access = data_access
        self._attractions_cache: dict[str, list[Attraction]] = {}

    def _make_key(self, *args: object) -> str:
        return "-".join(str(arg) for arg in args)

    def find_attractions(
        self,
        name: str,
        ordering: AttractionOrdering,
        limit: int,
    ) -> list[Attraction]:
        key = self._make_key(name, ordering.name, limit)
        if key not in self._attractions_cache:
            self._attractions_cache[key] = self._data_access.find_attractions(
                name, ordering, limit
            )
        return self._attractions_cache[key]

    def clear_cache(self) -> None:
        """キャッシュをクリアする。"""
        self._attractions_cache.clear()
```

```python
# 使用例
base = TestDataAccess()
cached = CachedDataAccess(base)

# 最初の呼び出し: 実際に検索
r1 = cached.find_attractions("Tokyo", AttractionOrdering.BY_NAME, 2)

# 2回目の呼び出し: キャッシュから取得
r2 = cached.find_attractions("Tokyo", AttractionOrdering.BY_NAME, 2)
```

### 11.7 アプリケーションの組み立て

```python
def travel_guide(
    data: DataAccess,
    attraction_name: str,
) -> TravelGuide | None:
    """旅行ガイドを生成する。"""
    attractions = data.find_attractions(
        attraction_name,
        AttractionOrdering.BY_LOCATION_POPULATION,
        1,
    )

    if not attractions:
        return None

    attraction = attractions[0]
    location_id = attraction.location.id

    # アーティストと映画を取得
    artists_result = data.find_artists_from_location(location_id, 2)
    movies_result = data.find_movies_about_location(location_id, 2)

    # 成功した結果のみを使用
    artists = artists_result.ok() if artists_result.is_ok() else []
    movies = movies_result.ok() if movies_result.is_ok() else []

    subjects = [a.name for a in (artists or [])] + [m.name for m in (movies or [])]

    return TravelGuide(attraction, subjects)
```

```python
# 使用例
da = TestDataAccess()
guide = travel_guide(da, "Tokyo Tower")

if guide:
    print(f"Attraction: {guide.attraction.name}")
    print(f"Subjects: {guide.subjects}")
```

### 11.8 ユーティリティ関数

```python
def filter_popular_locations(
    locations: list[Location],
    min_population: int,
) -> list[Location]:
    """指定人口以上のロケーションをフィルタリングする。"""
    return [loc for loc in locations if loc.population >= min_population]

def sort_attractions_by_popularity(
    attractions: list[Attraction],
) -> list[Attraction]:
    """アトラクションを人気度でソートする。"""
    return sorted(
        attractions,
        key=lambda a: a.location.population,
        reverse=True,
    )

def group_attractions_by_location(
    attractions: list[Attraction],
) -> dict[str, list[Attraction]]:
    """アトラクションをロケーションごとにグループ化する。"""
    result: dict[str, list[Attraction]] = {}
    for attraction in attractions:
        location_name = attraction.location.name
        if location_name not in result:
            result[location_name] = []
        result[location_name].append(attraction)
    return result
```

---

## 第12章: テスト戦略

### 12.1 関数型プログラミングのテスト

関数型プログラミングでは、純粋関数のおかげでテストが非常に簡単になります。

- **単体テスト**: 純粋関数のテスト（高速・独立）
- **プロパティベーステスト**: ランダム入力で不変条件を検証
- **統合テスト**: コンポーネント連携のテスト

### 12.2 SearchReport の導入

テスト可能性と可観測性を高めるため、`SearchReport` を導入します。

```python
@dataclass(frozen=True)
class SearchReport:
    """検索結果のメタデータ。"""
    attractions_searched: int
    errors: list[str] = field(default_factory=list)

    @property
    def has_errors(self) -> bool:
        return len(self.errors) > 0

    @property
    def error_count(self) -> int:
        return len(self.errors)

@dataclass(frozen=True)
class TravelGuideWithReport:
    """SearchReport 付きの旅行ガイド。"""
    attraction: Attraction
    subjects: list[str]
    search_report: SearchReport
```

```python
def travel_guide_with_report(
    data: DataAccess,
    attraction_name: str,
    limit: int = 3,
) -> TravelGuideWithReport | None:
    """SearchReport 付きの旅行ガイドを生成する。"""
    attractions = data.find_attractions(
        attraction_name,
        AttractionOrdering.BY_LOCATION_POPULATION,
        limit,
    )

    if not attractions:
        return None

    attraction = attractions[0]
    location_id = attraction.location.id

    artists_result = data.find_artists_from_location(location_id, 2)
    movies_result = data.find_movies_about_location(location_id, 2)

    # エラーを収集
    errors: list[str] = []
    if artists_result.is_err():
        errors.append(artists_result.err())
    if movies_result.is_err():
        errors.append(movies_result.err())

    artists = artists_result.ok() if artists_result.is_ok() else []
    movies = movies_result.ok() if movies_result.is_ok() else []

    subjects = [a.name for a in (artists or [])] + [m.name for m in (movies or [])]
    search_report = SearchReport(len(attractions), errors)

    return TravelGuideWithReport(attraction, subjects, search_report)
```

### 12.3 プロパティベーステスト用のジェネレータ

```python
import random
import string

def generate_location_id() -> LocationId:
    """ランダムな LocationId を生成する。"""
    return LocationId(f"Q{random.randint(1, 1000000)}")

def generate_location(
    min_population: int = 0,
    max_population: int = 10000000,
) -> Location:
    """ランダムな Location を生成する。"""
    name = "".join(random.choices(string.ascii_letters, k=random.randint(3, 10)))
    return Location(
        id=generate_location_id(),
        name=name,
        population=random.randint(min_population, max_population),
    )

def generate_attraction(location: Location | None = None) -> Attraction:
    """ランダムな Attraction を生成する。"""
    if location is None:
        location = generate_location()

    name = "".join(random.choices(string.ascii_letters, k=random.randint(5, 20)))
    description = (
        "".join(random.choices(string.ascii_letters + " ", k=random.randint(10, 50)))
        if random.random() > 0.3
        else None
    )

    return Attraction(name=name, description=description, location=location)
```

### 12.4 プロパティ検証関数

```python
def check_filter_result_size(
    locations: list[Location],
    min_population: int,
    filtered: list[Location],
) -> bool:
    """フィルタ結果のサイズが入力以下であることを検証する。"""
    return len(filtered) <= len(locations)

def check_filter_all_meet_condition(
    filtered: list[Location],
    min_population: int,
) -> bool:
    """フィルタ結果のすべての要素が条件を満たすことを検証する。"""
    return all(loc.population >= min_population for loc in filtered)

def check_filter_no_false_negatives(
    locations: list[Location],
    min_population: int,
    filtered: list[Location],
) -> bool:
    """条件を満たす要素がすべて結果に含まれることを検証する。"""
    expected = {loc for loc in locations if loc.population >= min_population}
    actual = set(filtered)
    return expected == actual
```

### 12.5 アサーションヘルパー

```python
class AssertionResult:
    """アサーション結果を表す。"""

    def __init__(self, success: bool, message: str = "") -> None:
        self.success = success
        self.message = message

def assert_equals(actual: T, expected: T, name: str = "value") -> AssertionResult:
    if actual == expected:
        return AssertionResult(True)
    return AssertionResult(False, f"{name}: expected {expected}, got {actual}")

def assert_true(condition: bool, message: str = "") -> AssertionResult:
    if condition:
        return AssertionResult(True)
    return AssertionResult(False, message or "Condition was False")

def assert_list_not_empty(lst: list[T], name: str = "list") -> AssertionResult:
    if len(lst) > 0:
        return AssertionResult(True)
    return AssertionResult(False, f"{name}: expected non-empty list")
```

### 12.6 テストスイート

```python
@dataclass
class TestResult:
    """テスト結果。"""
    name: str
    passed: bool
    message: str = ""

class TestSuite:
    """テストスイート。"""

    def __init__(self, name: str) -> None:
        self.name = name
        self._tests: list[tuple[str, Callable[[], AssertionResult]]] = []

    def add_test(self, name: str, test_fn: Callable[[], AssertionResult]) -> None:
        self._tests.append((name, test_fn))

    def run(self) -> list[TestResult]:
        results: list[TestResult] = []
        for name, test_fn in self._tests:
            try:
                result = test_fn()
                results.append(TestResult(name, result.success, result.message))
            except Exception as e:
                results.append(TestResult(name, False, str(e)))
        return results

    def run_and_report(self) -> tuple[int, int]:
        results = self.run()
        passed = sum(1 for r in results if r.passed)
        failed = sum(1 for r in results if not r.passed)
        return passed, failed
```

```python
# 使用例
suite = TestSuite("TravelGuide Tests")

suite.add_test(
    "find attractions returns results",
    lambda: assert_list_not_empty(
        TestDataAccess().find_attractions("Tokyo", AttractionOrdering.BY_NAME, 10)
    )
)

passed, failed = suite.run_and_report()
print(f"Passed: {passed}, Failed: {failed}")
```

### 12.7 プロパティベーステストランナー

```python
def run_property_test(
    name: str,
    property_fn: Callable[[], bool],
    iterations: int = 100,
) -> TestResult:
    """プロパティベーステストを実行する。"""
    for i in range(iterations):
        try:
            if not property_fn():
                return TestResult(name, False, f"Failed at iteration {i + 1}")
        except Exception as e:
            return TestResult(name, False, f"Exception at iteration {i + 1}: {e}")
    return TestResult(name, True, f"Passed {iterations} iterations")
```

```python
# プロパティテストの例
def property_filter_result_size() -> bool:
    locations = generate_locations(random.randint(0, 20))
    min_pop = random.randint(0, 10000000)
    filtered = [loc for loc in locations if loc.population >= min_pop]
    return check_filter_result_size(locations, min_pop, filtered)

result = run_property_test(
    "filter result size <= input size",
    property_filter_result_size,
    iterations=100
)
print(f"{result.name}: {'PASSED' if result.passed else 'FAILED'}")
```

### 12.8 統合テスト用のヘルパー

```python
def create_test_scenario(
    attraction_count: int = 3,
    artist_count: int = 2,
    movie_count: int = 2,
) -> DataAccess:
    """テストシナリオ用のデータアクセスを作成する。"""

    class ScenarioDataAccess(DataAccess):
        def __init__(self) -> None:
            self._attractions = generate_attractions(attraction_count)

        def find_attractions(self, name, ordering, limit) -> list[Attraction]:
            return self._attractions[:limit]

        def find_artists_from_location(self, location_id, limit):
            artists = [MusicArtist(f"Artist{i}") for i in range(artist_count)]
            return Ok(artists[:limit])

        def find_movies_about_location(self, location_id, limit):
            movies = [Movie(f"Movie{i}") for i in range(movie_count)]
            return Ok(movies[:limit])

        def find_hotels_near_location(self, location_id, limit):
            return Ok([])

    return ScenarioDataAccess()

def verify_travel_guide(guide: TravelGuideWithReport) -> list[AssertionResult]:
    """旅行ガイドを検証する。"""
    results: list[AssertionResult] = []

    results.append(
        assert_true(
            len(guide.attraction.name) > 0,
            "Attraction name should not be empty"
        )
    )

    results.append(
        assert_true(
            guide.search_report.attractions_searched > 0,
            "Attractions searched should be positive"
        )
    )

    results.append(
        assert_true(
            guide.search_report.error_count <= 2,
            "Error count should be at most 2"
        )
    )

    return results
```

---

## まとめ

### Part VI で学んだこと

| トピック | 内容 |
|----------|------|
| ドメインモデル | イミュータブルなデータクラス |
| DataAccess 抽象化 | ABC で外部依存を抽象化 |
| Resource 管理 | contextmanager で安全なリソース管理 |
| キャッシュ実装 | デコレータパターンでキャッシュ |
| SearchReport | テスト可能性と可観測性の向上 |
| スタブ/モック | 外部依存を差し替えてテスト |
| プロパティベーステスト | ランダム入力で不変条件を検証 |

### Python と Scala の対応

| Scala | Python |
|-------|--------|
| `case class` | `@dataclass(frozen=True)` |
| `trait DataAccess` | `class DataAccess(ABC)` |
| `Resource[IO, A]` | `Resource[T]` + `contextmanager` |
| `Either[String, A]` | `Result[A, str]` |
| `Ref[IO, Map[K, V]]` | `dict` + メソッド |
| ScalaCheck Gen | `generate_*` 関数 |

### キーポイント

1. **抽象化の重要性**: DataAccess で外部依存を抽象化し、テスト可能に
2. **Resource でリソース管理**: `with` 文で安全なリソースの取得と解放
3. **キャッシュでパフォーマンス向上**: デコレータパターンで透過的にキャッシュ
4. **Result でエラー処理**: 明示的なエラーハンドリング
5. **SearchReport**: テスト可能性と可観測性の向上
6. **スタブ**: 外部依存を差し替えてテスト
7. **プロパティベーステスト**: ランダム入力で不変条件を検証

---

## 演習問題

### 問題 1: DataAccess の拡張

以下の要件で `DataAccess` を拡張してください:

- 新しいメソッド `find_restaurants_near_location` を追加
- 戻り値は `Result[list[Restaurant], str]`

<details>
<summary>解答</summary>

```python
@dataclass(frozen=True)
class Restaurant:
    name: str
    cuisine: str
    rating: float
    location: Location

class DataAccess(ABC):
    # 既存のメソッド...

    @abstractmethod
    def find_restaurants_near_location(
        self,
        location_id: LocationId,
        limit: int,
    ) -> Result[list[Restaurant], str]:
        pass
```

</details>

### 問題 2: プロパティベーステスト

以下の関数に対するプロパティベーステストを書いてください:

```python
def filter_popular_locations(
    locations: list[Location],
    min_population: int,
) -> list[Location]:
    return [loc for loc in locations if loc.population >= min_population]
```

<details>
<summary>解答</summary>

```python
def property_filter_result_size() -> bool:
    locations = generate_locations(random.randint(0, 20))
    min_pop = random.randint(0, 10000000)
    filtered = filter_popular_locations(locations, min_pop)
    return len(filtered) <= len(locations)

def property_filter_all_meet_condition() -> bool:
    locations = generate_locations(random.randint(0, 20))
    min_pop = random.randint(0, 10000000)
    filtered = filter_popular_locations(locations, min_pop)
    return all(loc.population >= min_pop for loc in filtered)

def property_filter_no_false_negatives() -> bool:
    locations = generate_locations(random.randint(0, 20))
    min_pop = random.randint(0, 10000000)
    filtered = filter_popular_locations(locations, min_pop)
    expected = [loc for loc in locations if loc.population >= min_pop]
    return set(filtered) == set(expected)

# 実行
for prop_fn in [
    property_filter_result_size,
    property_filter_all_meet_condition,
    property_filter_no_false_negatives,
]:
    result = run_property_test(prop_fn.__name__, prop_fn, 100)
    print(f"{result.name}: {'PASSED' if result.passed else 'FAILED'}")
```

</details>

### 問題 3: Resource の実装

ファイルを安全に読み取る `Resource` を実装してください。

<details>
<summary>解答</summary>

```python
from typing import TextIO

def file_resource(path: str) -> Resource[TextIO]:
    return make_resource(
        lambda: open(path, "r"),
        lambda f: f.close()
    )

def read_lines(path: str) -> list[str]:
    with file_resource(path).use() as f:
        return f.readlines()

# 使用例
lines = read_lines("data.txt")
print(f"Read {len(lines)} lines")
```

</details>

---

## シリーズ全体の総括

本シリーズでは、「Grokking Functional Programming」の内容に沿って、関数型プログラミングの基礎から実践的なアプリケーション構築までを学びました。

### 学んだ主な概念

| Part | 章 | 主な概念 |
|------|-----|----------|
| I | 1-2 | 純粋関数、参照透過性 |
| II | 3-5 | イミュータブルデータ、高階関数、flatMap |
| III | 6-7 | Option、Either (Result)、ADT |
| IV | 8-9 | IO モナド (LazyIO)、Stream (Generator) |
| V | 10 | 並行処理、Ref、Fiber |
| VI | 11-12 | 実践アプリケーション、テスト |

### 関数型プログラミングの利点

1. **予測可能性**: 純粋関数は同じ入力に対して常に同じ出力
2. **テスト容易性**: 副作用がないためテストが簡単
3. **合成可能性**: 小さな関数を組み合わせて複雑な処理を構築
4. **並行安全性**: イミュータブルデータは競合状態を防ぐ
5. **型安全性**: Option、Result で null や例外を型で表現

### Python での FP 実践

Python は純粋な関数型言語ではありませんが、以下のテクニックで FP スタイルを実践できます:

- `@dataclass(frozen=True)` でイミュータブルなデータ
- `result` ライブラリで `Result` 型
- ジェネレータで遅延評価
- `asyncio` で非同期処理
- ABC で抽象化
- 型ヒントで型安全性

### 次のステップ

- `returns` ライブラリの高度な機能を学ぶ
- `toolz` や `funcy` などの関数型ライブラリを探索
- プロパティベーステストに `hypothesis` を活用
- 実際のプロジェクトで FP を適用する
