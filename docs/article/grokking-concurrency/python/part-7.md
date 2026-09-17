---
type: Article
title: "Part VII - 非同期プログラミング"
description: "Python の asyncio を使った非同期プログラミングを学ぶ"
tags: [article, grokking-concurrency, python, concurrency, asyncio, coroutine, future]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VII: 非同期プログラミング

## 概要

本章では、Python の `asyncio` を使った非同期プログラミングを学びます。コルーチン、Future、async/await の仕組みを理解し、効率的な I/O バウンド処理を実装します。

---

## 第12章: コルーチンと非同期

### コルーチンとは

コルーチンは、実行を中断・再開できる関数です。ジェネレータをベースにしています。

### ジェネレータベースのコルーチン

```python
#!/usr/bin/env python3

"""コルーチンの基本実装"""

from collections import deque
import typing as T

Coroutine = T.Generator[None, None, int]


class EventLoop:
    def __init__(self) -> None:
        self.tasks: T.Deque[Coroutine] = deque()

    def add_coroutine(self, task: Coroutine) -> None:
        """タスクをキューに追加"""
        self.tasks.append(task)

    def run_coroutine(self, task: Coroutine) -> None:
        """タスクを1ステップ実行"""
        try:
            task.send(None)  # 次の yield まで実行
            self.add_coroutine(task)  # 未完了なら再登録
        except StopIteration:
            print("Task completed")

    def run_forever(self) -> None:
        """タスクがなくなるまで実行"""
        while self.tasks:
            print("Event loop cycle.")
            self.run_coroutine(self.tasks.popleft())


def fibonacci(n: int) -> Coroutine:
    """フィボナッチ数列をコルーチンで生成"""
    a, b = 0, 1
    for i in range(n):
        a, b = b, a + b
        print(f"Fibonacci({i}): {a}")
        yield  # 制御をイベントループに返す
    return a


if __name__ == "__main__":
    event_loop = EventLoop()
    event_loop.add_coroutine(fibonacci(5))
    event_loop.run_forever()
```

### 実行結果

```
Event loop cycle.
Fibonacci(0): 1
Event loop cycle.
Fibonacci(1): 1
Event loop cycle.
Fibonacci(2): 2
Event loop cycle.
Fibonacci(3): 3
Event loop cycle.
Fibonacci(4): 5
Task completed
```

---

## async/await 構文

Python 3.5 以降では、`async/await` 構文で読みやすくコルーチンを記述できます。

### 基本構文

```python
import asyncio


async def fetch_data(url: str) -> str:
    """非同期でデータを取得"""
    print(f"Fetching {url}...")
    await asyncio.sleep(1)  # I/O 待ちをシミュレート
    return f"Data from {url}"


async def main():
    # 逐次実行
    result1 = await fetch_data("url1")
    result2 = await fetch_data("url2")
    print(result1, result2)


asyncio.run(main())
```

### 並行実行

```python
async def main():
    # 並行実行（gather を使用）
    results = await asyncio.gather(
        fetch_data("url1"),
        fetch_data("url2"),
        fetch_data("url3"),
    )
    print(results)


asyncio.run(main())
```

---

## Future

Future は、まだ完了していない非同期操作の結果を表すオブジェクトです。

### Future の状態

```plantuml
@startuml
!theme plain

state Pending
state Finished
state Cancelled

Pending --> Finished : 成功
Pending --> Cancelled : キャンセル
Finished --> [*] : Result
Cancelled --> [*] : Exception
@enduml
```

### Future の使用例

```python
import asyncio


async def slow_operation() -> str:
    await asyncio.sleep(2)
    return "Operation completed"


async def main():
    # タスクを作成（Futureを返す）
    task = asyncio.create_task(slow_operation())

    print(f"Task done: {task.done()}")  # False

    result = await task

    print(f"Task done: {task.done()}")  # True
    print(f"Result: {result}")


asyncio.run(main())
```

---

## 非同期ピザサーバー

```python
#!/usr/bin/env python3

"""asyncio を使った非同期ピザサーバー"""

import asyncio


async def handle_client(reader: asyncio.StreamReader,
                        writer: asyncio.StreamWriter) -> None:
    """クライアント処理"""
    addr = writer.get_extra_info('peername')
    print(f"Connection from {addr}")

    # 注文を読み取り
    data = await reader.read(1024)
    order = data.decode()
    print(f"Order received: {order}")

    # ピザを作る（非同期で待機）
    await asyncio.sleep(2)

    # ピザを送信
    writer.write(b"Here's your pizza!")
    await writer.drain()

    writer.close()
    await writer.wait_closed()


async def run_server(host: str = 'localhost', port: int = 8000) -> None:
    """サーバーを起動"""
    server = await asyncio.start_server(
        handle_client, host, port)

    addr = server.sockets[0].getsockname()
    print(f"Pizza server running on {addr}")

    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    asyncio.run(run_server())
```

---

## asyncio の主要 API

### タスク管理

| API | 説明 |
|-----|------|
| `asyncio.run(coro)` | イベントループを作成して実行 |
| `asyncio.create_task(coro)` | タスクをスケジュール |
| `asyncio.gather(*coros)` | 複数のコルーチンを並行実行 |
| `asyncio.wait(tasks)` | タスク完了を待機 |

### 同期プリミティブ

| API | 説明 |
|-----|------|
| `asyncio.Lock()` | 非同期ロック |
| `asyncio.Semaphore(n)` | 非同期セマフォ |
| `asyncio.Event()` | 非同期イベント |
| `asyncio.Queue()` | 非同期キュー |

### I/O

| API | 説明 |
|-----|------|
| `asyncio.sleep(delay)` | 非同期スリープ |
| `asyncio.open_connection()` | TCP 接続を開く |
| `asyncio.start_server()` | TCP サーバーを開始 |

---

## 使い分けの指針

| シナリオ | 推奨 |
|----------|------|
| I/O バウンド（ネットワーク、ファイル） | asyncio |
| CPU バウンド | multiprocessing |
| 混合 | asyncio + ProcessPoolExecutor |

### 混合の例

```python
import asyncio
from concurrent.futures import ProcessPoolExecutor


def cpu_bound_task(n: int) -> int:
    """CPU を使う処理"""
    return sum(i * i for i in range(n))


async def main():
    loop = asyncio.get_event_loop()

    with ProcessPoolExecutor() as pool:
        # CPU バウンドタスクをプロセスプールで実行
        result = await loop.run_in_executor(
            pool, cpu_bound_task, 10000000)
        print(f"Result: {result}")


asyncio.run(main())
```

---

## 次のステップ

Part VIII では、MapReduce パターンと分散並列処理を学びます。大規模データを複数のワーカーで並列処理する手法を理解します。

---

## 参考コード

- `apps/python/Chapter 12/coroutine.py`
- `apps/python/Chapter 12/future_burger.py`
- `apps/python/Chapter 12/asynchronous_pizza/`
