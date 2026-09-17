---
type: Article
title: "Part V: 並行処理"
description: "本章では、関数型プログラミングにおける並行処理を学びます。"
tags: [article, grokkingfp, python]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part V: 並行処理

本章では、関数型プログラミングにおける並行処理を学びます。`asyncio` を使った非同期処理、`Ref`（アトミック参照）による安全な共有状態管理、そして `Fiber`（軽量タスク）によるバックグラウンド処理を習得します。

---

## 第10章: 並行・並列処理

### 10.1 並行処理の課題

従来の並行処理には多くの課題があります:

- デッドロック
- 競合状態（Race Condition）
- 共有状態の管理の複雑さ
- スレッドのオーバーヘッド

関数型プログラミングでは、これらの問題を以下のアプローチで解決します:

- **イミュータブルデータ**: 共有状態の変更による競合を防ぐ
- **Ref（アトミック参照）**: スレッドセーフな状態管理
- **asyncio**: 軽量な非同期処理
- **宣言的な並行処理**: 副作用を明示的に管理

### 10.2 Ref - アトミックな共有状態

**Ref** は、複数の並行処理から安全にアクセスできるアトミックな参照です。Python では `threading.Lock` を使って実装します。

#### 同期版 Ref

```python
import threading
from typing import Generic, TypeVar, Callable

T = TypeVar("T")
U = TypeVar("U")

class Ref(Generic[T]):
    """スレッドセーフなアトミック参照。"""

    def __init__(self, initial: T) -> None:
        self._value = initial
        self._lock = threading.Lock()

    def get(self) -> T:
        """現在の値を取得する。"""
        with self._lock:
            return self._value

    def set(self, value: T) -> None:
        """値を設定する。"""
        with self._lock:
            self._value = value

    def update(self, f: Callable[[T], T]) -> None:
        """アトミックに値を更新する。"""
        with self._lock:
            self._value = f(self._value)

    def modify(self, f: Callable[[T], tuple[T, U]]) -> U:
        """アトミックに値を更新し、結果を返す。"""
        with self._lock:
            new_value, result = f(self._value)
            self._value = new_value
            return result
```

```python
# 使用例
ref = Ref(0)
ref.update(lambda x: x + 1)
ref.update(lambda x: x + 1)
ref.update(lambda x: x + 1)
print(ref.get())  # 3
```

#### 非同期版 AsyncRef

`asyncio` と組み合わせる場合は `asyncio.Lock` を使用します:

```python
import asyncio

class AsyncRef(Generic[T]):
    """非同期コンテキスト用のアトミック参照。"""

    def __init__(self, initial: T) -> None:
        self._value = initial
        self._lock = asyncio.Lock()

    async def get(self) -> T:
        async with self._lock:
            return self._value

    async def set(self, value: T) -> None:
        async with self._lock:
            self._value = value

    async def update(self, f: Callable[[T], T]) -> None:
        async with self._lock:
            self._value = f(self._value)
```

```python
# 使用例
async def example():
    ref = AsyncRef(0)
    await ref.update(lambda x: x + 1)
    return await ref.get()

asyncio.run(example())  # 1
```

### 10.3 チェックインのリアルタイム集計

都市へのチェックインをリアルタイムで集計し、ランキングを更新する例を見ていきます。

```python
from dataclasses import dataclass

@dataclass(frozen=True)
class City:
    """都市を表すデータクラス。"""
    name: str

@dataclass(frozen=True)
class CityStats:
    """都市のチェックイン統計。"""
    city: City
    check_ins: int
```

#### トップ都市の計算（純粋関数）

```python
def top_cities(city_check_ins: dict[City, int], n: int = 3) -> list[CityStats]:
    """チェックイン数上位の都市を取得する。"""
    stats = [CityStats(city, count) for city, count in city_check_ins.items()]
    return sorted(stats, key=lambda s: s.check_ins, reverse=True)[:n]
```

```python
cities = {City("Tokyo"): 100, City("Osaka"): 50, City("Kyoto"): 75}
result = top_cities(cities, 2)
# [CityStats(city=City(name='Tokyo'), check_ins=100),
#  CityStats(city=City(name='Kyoto'), check_ins=75)]
```

#### チェックインの更新（純粋関数）

```python
def update_check_ins(
    city_check_ins: dict[City, int], city: City
) -> dict[City, int]:
    """チェックインを追加する（新しい辞書を返す）。"""
    new_check_ins = dict(city_check_ins)
    new_check_ins[city] = new_check_ins.get(city, 0) + 1
    return new_check_ins
```

### 10.4 par_sequence - 並列実行

`asyncio.gather` を使用して複数の非同期タスクを並列実行します。

```python
from typing import Any, Coroutine

async def par_sequence(
    tasks: list[Coroutine[Any, Any, T]],
) -> list[T]:
    """複数の非同期タスクを並列実行する。"""
    return list(await asyncio.gather(*tasks))
```

```python
async def example():
    async def task(n: int) -> int:
        await asyncio.sleep(0.1)  # シミュレーション
        return n * 2

    # 並列実行: 約0.1秒（順次実行なら約0.3秒）
    result = await par_sequence([task(1), task(2), task(3)])
    return result  # [2, 4, 6]
```

#### par_traverse - リストへの並列適用

```python
async def par_traverse(
    items: list[T],
    f: Callable[[T], Coroutine[Any, Any, U]],
) -> list[U]:
    """リストの各要素に非同期関数を並列適用する。"""
    return await par_sequence([f(item) for item in items])
```

```python
async def example():
    async def fetch_data(id: int) -> str:
        await asyncio.sleep(0.1)
        return f"data_{id}"

    # 並列にデータを取得
    results = await par_traverse([1, 2, 3, 4, 5], fetch_data)
    # ["data_1", "data_2", "data_3", "data_4", "data_5"]
```

### 10.5 サイコロを並行して振る

```python
import random

async def cast_the_die() -> int:
    """非同期でサイコロを振る。"""
    await asyncio.sleep(0)  # yield control
    return random.randint(1, 6)

async def cast_the_die_twice() -> int:
    """2つのサイコロを並行して振り、合計を返す。"""
    results = await par_sequence([cast_the_die(), cast_the_die()])
    return sum(results)

async def cast_dice_parallel(n: int) -> list[int]:
    """n個のサイコロを並行して振る。"""
    return await par_sequence([cast_the_die() for _ in range(n)])
```

### 10.6 Ref と par_sequence の組み合わせ

複数の非同期タスクから共有状態を安全に更新します:

```python
async def increment_three_times() -> int:
    """カウンターを3回インクリメントする。"""
    counter = AsyncRef(0)
    await counter.update(lambda x: x + 1)
    await counter.update(lambda x: x + 1)
    await counter.update(lambda x: x + 1)
    return await counter.get()  # 3
```

#### 偶数をカウントする例

```python
async def count_evens(tasks: list[Coroutine[Any, Any, int]]) -> int:
    """複数の非同期タスクを並列実行し、偶数の結果をカウントする。"""
    counter = AsyncRef(0)

    async def count_if_even(task: Coroutine[Any, Any, int]) -> None:
        result = await task
        if result % 2 == 0:
            await counter.update(lambda x: x + 1)

    await par_sequence([count_if_even(t) for t in tasks])
    return await counter.get()
```

### 10.7 チェックイン処理の並行版

```python
async def store_check_in(
    stored_check_ins: AsyncRef[dict[City, int]], city: City
) -> None:
    """チェックインを保存する。"""
    await stored_check_ins.update(lambda m: update_check_ins(m, city))

async def update_ranking(
    stored_check_ins: AsyncRef[dict[City, int]],
    stored_ranking: AsyncRef[list[CityStats]],
) -> None:
    """ランキングを更新する。"""
    check_ins = await stored_check_ins.get()
    await stored_ranking.set(top_cities(check_ins))
```

### 10.8 Fiber - 軽量タスク

**Fiber** は `asyncio.Task` のラッパーで、キャンセル可能なバックグラウンドタスクを表現します。

```python
from dataclasses import dataclass

@dataclass
class Fiber(Generic[T]):
    """軽量タスク（asyncio.Task のラッパー）。"""
    _task: asyncio.Task[T]

    def cancel(self) -> None:
        """タスクをキャンセルする。"""
        self._task.cancel()

    async def join(self) -> T:
        """タスクの完了を待つ。"""
        return await self._task

    @property
    def is_done(self) -> bool:
        """タスクが完了しているか。"""
        return self._task.done()

def start_fiber(coro: Coroutine[Any, Any, T]) -> Fiber[T]:
    """コルーチンを Fiber として起動する。"""
    task = asyncio.create_task(coro)
    return Fiber(task)
```

```python
async def example():
    async def background_task():
        for i in range(10):
            await asyncio.sleep(0.1)
            print(f"Working... {i}")

    fiber = start_fiber(background_task())

    # 他の処理を行う
    await asyncio.sleep(0.35)

    # バックグラウンドタスクをキャンセル
    fiber.cancel()
```

### 10.9 タイムアウト付き収集

指定時間内にデータを収集し、タイムアウト後に結果を返す例:

```python
async def collect_for(
    duration_seconds: float, interval_seconds: float = 0.1
) -> list[int]:
    """指定時間、定期的に乱数を収集する。"""
    collected: AsyncRef[list[int]] = AsyncRef([])

    async def producer() -> None:
        while True:
            await asyncio.sleep(interval_seconds)
            value = random.randint(0, 99)
            await collected.update(lambda lst: lst + [value])

    fiber = start_fiber(producer())

    await asyncio.sleep(duration_seconds)
    fiber.cancel()

    try:
        await fiber.join()
    except asyncio.CancelledError:
        pass

    return await collected.get()
```

### 10.10 バックグラウンド処理

呼び出し元に制御を返しつつ、バックグラウンドで処理を続ける設計:

```python
@dataclass
class ProcessingCheckIns:
    """チェックイン処理の制御ハンドル。"""
    current_ranking: Callable[[], Coroutine[Any, Any, list[CityStats]]]
    stop: Callable[[], None]

async def process_check_ins_background(
    check_ins: list[City],
) -> ProcessingCheckIns:
    """チェックイン処理をバックグラウンドで開始する。"""
    stored_check_ins: AsyncRef[dict[City, int]] = AsyncRef({})
    stored_ranking: AsyncRef[list[CityStats]] = AsyncRef([])

    async def check_in_processor() -> None:
        for city in check_ins:
            await store_check_in(stored_check_ins, city)
            await asyncio.sleep(0)

    async def ranking_updater() -> None:
        while True:
            await update_ranking(stored_check_ins, stored_ranking)
            await asyncio.sleep(0.01)

    check_in_fiber = start_fiber(check_in_processor())
    ranking_fiber = start_fiber(ranking_updater())

    def stop() -> None:
        check_in_fiber.cancel()
        ranking_fiber.cancel()

    async def get_ranking() -> list[CityStats]:
        return await stored_ranking.get()

    return ProcessingCheckIns(current_ranking=get_ranking, stop=stop)
```

```python
# 使用例
async def main():
    cities = [City("Tokyo"), City("Osaka"), City("Tokyo"), City("Kyoto")]
    processing = await process_check_ins_background(cities)

    await asyncio.sleep(0.1)
    ranking = await processing.current_ranking()
    print(ranking)  # [CityStats(city=City(name='Tokyo'), check_ins=2), ...]

    processing.stop()
```

### 10.11 順次 vs 並列の比較

```python
async def sequential_sleep(count: int, seconds: float) -> float:
    """逐次的にスリープする。"""
    for _ in range(count):
        await asyncio.sleep(seconds)
    return count * seconds

async def parallel_sleep(count: int, seconds: float) -> float:
    """並列にスリープする。"""
    await par_sequence([asyncio.sleep(seconds) for _ in range(count)])
    return seconds  # 並列なので最長のタスク時間のみ
```

```python
import time

# 順次実行: 約0.3秒
start = time.time()
asyncio.run(sequential_sleep(3, 0.1))
print(f"Sequential: {time.time() - start:.2f}s")  # ~0.30s

# 並列実行: 約0.1秒
start = time.time()
asyncio.run(parallel_sleep(3, 0.1))
print(f"Parallel: {time.time() - start:.2f}s")  # ~0.10s
```

### 10.12 スレッドベースの並列処理

CPU バウンドな処理にはスレッドを使用します:

```python
import threading

def run_in_threads(tasks: list[Callable[[], T]]) -> list[T]:
    """複数のタスクをスレッドで並列実行する。"""
    results: list[T] = []
    lock = threading.Lock()

    def run_task(task: Callable[[], T]) -> None:
        result = task()
        with lock:
            results.append(result)

    threads = [threading.Thread(target=run_task, args=(task,)) for task in tasks]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    return results

def parallel_map(items: list[T], f: Callable[[T], U]) -> list[U]:
    """リストの各要素に関数を並列適用する。"""
    results: list[tuple[int, U]] = []
    lock = threading.Lock()

    def process(index: int, item: T) -> None:
        result = f(item)
        with lock:
            results.append((index, result))

    threads = [
        threading.Thread(target=process, args=(i, item))
        for i, item in enumerate(items)
    ]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    return [r for _, r in sorted(results)]
```

```python
# 使用例
result = parallel_map([1, 2, 3, 4, 5], lambda x: x * x)
# [1, 4, 9, 16, 25]
```

---

## まとめ

### Part V で学んだこと

| コンポーネント | 用途 |
|----------------|------|
| `Ref` | スレッドセーフな共有状態（同期版） |
| `AsyncRef` | 非同期コンテキストでの共有状態 |
| `par_sequence` | 非同期タスクの並列実行 |
| `par_traverse` | リストへの並列関数適用 |
| `Fiber` | キャンセル可能なバックグラウンドタスク |
| `start_fiber` | Fiber の起動 |
| `asyncio.sleep` | 非同期スリープ（スレッドをブロックしない） |

### Python と Scala の対応

| Scala | Python |
|-------|--------|
| `Ref[IO, A]` | `AsyncRef[T]` |
| `parSequence` | `par_sequence` / `asyncio.gather` |
| `Fiber` | `Fiber` (asyncio.Task のラッパー) |
| `fiber.start` | `start_fiber()` |
| `fiber.cancel` | `fiber.cancel()` |
| `IO.sleep` | `asyncio.sleep` |
| `foreverM` | `while True: ...` |

### キーポイント

1. **Ref/AsyncRef**: 複数の並行処理から安全にアクセスできるアトミックな参照
2. **par_sequence**: 非同期タスクのリストを並列実行して結果を集約
3. **Fiber**: asyncio.Task のラッパーで、キャンセル可能なバックグラウンド処理
4. **start_fiber**: Fiber をバックグラウンドで起動し、すぐに制御を返す
5. **asyncio.sleep**: Fiber をスリープさせ、スレッドは解放する

### 設計パターン

#### パターン1: 並列集約

```python
async def aggregate_data(sources: list[str]) -> list[Data]:
    return await par_traverse(sources, fetch_from_source)
```

#### パターン2: 共有状態の更新

```python
async def process_items(items: list[Item]) -> int:
    counter = AsyncRef(0)

    async def process(item: Item) -> None:
        if is_valid(item):
            await counter.update(lambda x: x + 1)

    await par_sequence([process(item) for item in items])
    return await counter.get()
```

#### パターン3: バックグラウンド処理

```python
async def start_background_service() -> ServiceHandle:
    state = AsyncRef(initial_state())

    async def worker():
        while True:
            await do_work(state)
            await asyncio.sleep(interval)

    fiber = start_fiber(worker())

    return ServiceHandle(
        get_state=state.get,
        stop=fiber.cancel
    )
```

---

## 演習問題

### 問題 1: Ref の基本

カウンターを 0 から始めて、3回インクリメントした結果を返す関数を実装してください。

```python
async def increment_three_times() -> int:
    ...

# 期待される動作
asyncio.run(increment_three_times())  # 3
```

<details>
<summary>解答</summary>

```python
async def increment_three_times() -> int:
    counter = AsyncRef(0)
    await counter.update(lambda x: x + 1)
    await counter.update(lambda x: x + 1)
    await counter.update(lambda x: x + 1)
    return await counter.get()
```

</details>

### 問題 2: 並列実行

3つの非同期タスクを並列実行し、結果の合計を返す関数を実装してください。

```python
async def sum_parallel(
    task1: Coroutine[Any, Any, int],
    task2: Coroutine[Any, Any, int],
    task3: Coroutine[Any, Any, int],
) -> int:
    ...

# 期待される動作
async def pure(n):
    return n

asyncio.run(sum_parallel(pure(1), pure(2), pure(3)))  # 6
```

<details>
<summary>解答</summary>

```python
async def sum_parallel(
    task1: Coroutine[Any, Any, int],
    task2: Coroutine[Any, Any, int],
    task3: Coroutine[Any, Any, int],
) -> int:
    results = await par_sequence([task1, task2, task3])
    return sum(results)
```

</details>

### 問題 3: 並行カウント

複数の非同期タスクを並行実行し、偶数を返した回数をカウントする関数を実装してください。

```python
async def count_evens(tasks: list[Coroutine[Any, Any, int]]) -> int:
    ...

# 使用例
async def pure(n):
    return n

tasks = [pure(1), pure(2), pure(3), pure(4)]
asyncio.run(count_evens(tasks))  # 2
```

<details>
<summary>解答</summary>

```python
async def count_evens(tasks: list[Coroutine[Any, Any, int]]) -> int:
    counter = AsyncRef(0)

    async def count_if_even(task: Coroutine[Any, Any, int]) -> None:
        result = await task
        if result % 2 == 0:
            await counter.update(lambda x: x + 1)

    await par_sequence([count_if_even(t) for t in tasks])
    return await counter.get()
```

</details>

### 問題 4: タイムアウト付き実行

指定時間後に Fiber をキャンセルし、それまでに蓄積された結果を返す関数を実装してください。

```python
async def collect_for(duration: float) -> list[int]:
    ...

# 期待される動作
# 0.5秒間、100msごとに乱数を生成してリストに追加
# 約5個の要素が返される
asyncio.run(collect_for(0.5))
```

<details>
<summary>解答</summary>

```python
async def collect_for(duration: float) -> list[int]:
    collected: AsyncRef[list[int]] = AsyncRef([])

    async def producer() -> None:
        while True:
            await asyncio.sleep(0.1)
            value = random.randint(0, 99)
            await collected.update(lambda lst: lst + [value])

    fiber = start_fiber(producer())
    await asyncio.sleep(duration)
    fiber.cancel()

    try:
        await fiber.join()
    except asyncio.CancelledError:
        pass

    return await collected.get()
```

</details>

### 問題 5: 並行マップ更新

複数の更新を並行して Map に適用し、最終的な Map を返す関数を実装してください。

```python
@dataclass(frozen=True)
class Update:
    key: str
    value: int

async def apply_updates(updates: list[Update]) -> dict[str, int]:
    ...

# 期待される動作
updates = [
    Update("a", 1),
    Update("b", 2),
    Update("a", 3),  # "a" を上書き
    Update("c", 4)
]
asyncio.run(apply_updates(updates))  # {"a": 3, "b": 2, "c": 4} (順序不定)
```

<details>
<summary>解答</summary>

```python
async def apply_updates(updates: list[Update]) -> dict[str, int]:
    map_ref: AsyncRef[dict[str, int]] = AsyncRef({})

    async def apply_update(update: Update) -> None:
        await map_ref.update(
            lambda m: {**m, update.key: update.value}
        )

    await par_sequence([apply_update(u) for u in updates])
    return await map_ref.get()
```

注意: 並行実行のため、同じキーへの複数の更新がある場合、最終的な値は実行順序に依存します。

</details>
