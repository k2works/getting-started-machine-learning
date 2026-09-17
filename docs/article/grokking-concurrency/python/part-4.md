---
type: Article
title: "Part IV - タスク分解と並列パターン"
description: "並列処理のデザインパターンを学ぶ"
tags: [article, grokking-concurrency, python, concurrency, fork-join, pipeline]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part IV: タスク分解と並列パターン

## 概要

本章では、複雑な問題を並列化可能なサブタスクに分割する手法を学びます。Fork/Join パターンとパイプラインパターンの実装を通じて、並列処理のデザインパターンを理解します。

---

## 第7章: 並列パターン

### データ並列 vs タスク並列

| パターン | 説明 | 例 |
|----------|------|-----|
| データ並列 | 同じ操作を複数のデータに適用 | 配列の各要素を2倍 |
| タスク並列 | 異なる操作を同時に実行 | 入力・計算・出力 |

---

## Fork/Join パターン

問題を分割し、並列に処理した後、結果を統合するパターンです。

### 投票集計の例

```python
#!/usr/bin/env python3

"""Fork/Join パターンによる投票集計"""

import typing as T
import random
from math import ceil
from threading import Thread


class StaffMember(Thread):
    """投票用紙を集計するスタッフ"""

    def __init__(self, votes: T.List[int]):
        super().__init__()
        self.votes = votes
        self.summary: T.Dict[int, int] = {}

    def run(self) -> None:
        for vote in self.votes:
            if vote in self.summary:
                self.summary[vote] += 1
            else:
                self.summary[vote] = 1


def count_votes(votes: T.List[int], num_workers: int = 3) -> T.Dict[int, int]:
    """投票を複数のワーカーで並列集計"""
    workers = []
    vote_count = len(votes)
    votes_per_worker = ceil(vote_count / num_workers)

    # Fork: 投票用紙をワーカーに分配
    for i in range(num_workers):
        start = i * votes_per_worker
        end = start + votes_per_worker
        pile = votes[start:end]
        worker = StaffMember(pile)
        workers.append(worker)
        worker.start()

    # Join: 全ワーカーの完了を待機
    for worker in workers:
        worker.join()

    # 結果の統合
    total: T.Dict[int, int] = {}
    for worker in workers:
        for candidate, count in worker.summary.items():
            total[candidate] = total.get(candidate, 0) + count

    return total


if __name__ == "__main__":
    # 10万票を生成
    votes = [random.randint(1, 10) for _ in range(100000)]
    result = count_votes(votes)
    print(f"投票結果: {result}")
```

### Fork/Join の流れ

```plantuml
@startuml
!theme plain
left to right direction

rectangle "Input" as input
rectangle "Worker 1" as w1
rectangle "Worker 2" as w2
rectangle "Worker 3" as w3
rectangle "部分結果1" as r1
rectangle "部分結果2" as r2
rectangle "部分結果3" as r3
rectangle "統合結果" as result

input --> w1 : Fork
input --> w2
input --> w3
w1 --> r1
w2 --> r2
w3 --> r3
r1 --> result : Join
r2 --> result
r3 --> result
@enduml
```

---

## パイプラインパターン

処理をステージに分割し、各ステージを並列に実行するパターンです。

### 洗濯パイプラインの例

```python
#!/usr/bin/env python3

"""パイプラインパターンによる洗濯処理"""

import time
from queue import Queue
from threading import Thread

Washload = str


class Washer(Thread):
    """洗濯機を表すスレッド"""

    def __init__(self, in_queue: Queue, out_queue: Queue):
        super().__init__()
        self.in_queue = in_queue
        self.out_queue = out_queue

    def run(self) -> None:
        while True:
            washload = self.in_queue.get()
            print(f"Washer: washing {washload}...")
            time.sleep(4)  # 洗濯に4秒
            self.out_queue.put(washload)
            self.in_queue.task_done()


class Dryer(Thread):
    """乾燥機を表すスレッド"""

    def __init__(self, in_queue: Queue, out_queue: Queue):
        super().__init__()
        self.in_queue = in_queue
        self.out_queue = out_queue

    def run(self) -> None:
        while True:
            washload = self.in_queue.get()
            print(f"Dryer: drying {washload}...")
            time.sleep(2)  # 乾燥に2秒
            self.out_queue.put(washload)
            self.in_queue.task_done()


class Folder(Thread):
    """たたみ処理を表すスレッド"""

    def __init__(self, in_queue: Queue):
        super().__init__()
        self.in_queue = in_queue

    def run(self) -> None:
        while True:
            washload = self.in_queue.get()
            print(f"Folder: folding {washload}...")
            time.sleep(1)  # たたみに1秒
            print(f"Folder: {washload} done!")
            self.in_queue.task_done()


class Pipeline:
    """洗濯パイプライン"""

    def run_concurrently(self) -> None:
        # キューのセットアップ
        to_be_washed: Queue = Queue()
        to_be_dried: Queue = Queue()
        to_be_folded: Queue = Queue()

        # 洗濯物を投入
        for i in range(4):
            to_be_washed.put(f'Washload #{i}')

        # パイプラインを開始
        Washer(to_be_washed, to_be_dried).start()
        Dryer(to_be_dried, to_be_folded).start()
        Folder(to_be_folded).start()

        # 完了を待機
        to_be_washed.join()
        to_be_dried.join()
        to_be_folded.join()
        print("All done!")


if __name__ == "__main__":
    pipeline = Pipeline()
    pipeline.run_concurrently()
```

### パイプラインの流れ

```plantuml
@startuml
!theme plain
title パイプラインパターン

concise "Washer" as washer
concise "Dryer" as dryer
concise "Folder" as folder

@0
washer is "Wash 1"

@4
washer is "Wash 2"
dryer is "Dry 1"

@8
washer is "Wash 3"
dryer is "Dry 2"
folder is "Fold 1"

@12
washer is "Wash 4"
dryer is "Dry 3"
folder is "Fold 2"

@16
washer is {-}
dryer is "Dry 4"
folder is "Fold 3"

@20
dryer is {-}
folder is "Fold 4"

@24
folder is {-}
@enduml
```

### 逐次処理との比較

| 処理方式 | 4つの洗濯物 |
|----------|-------------|
| 逐次処理 | (4+2+1) × 4 = 28秒 |
| パイプライン | 4+4+4+4 + 2 + 1 = 23秒 |

---

## Queue によるスレッド間通信

`queue.Queue` はスレッドセーフなキューです。

| メソッド | 説明 |
|----------|------|
| `put(item)` | アイテムを追加（ブロッキング） |
| `get()` | アイテムを取得（ブロッキング） |
| `task_done()` | タスク完了を通知 |
| `join()` | 全タスク完了を待機 |

---

## 次のステップ

Part V では、同期と排他制御を学びます。レースコンディション、デッドロックなどの問題と、Lock、Semaphore による解決策を理解します。

---

## 参考コード

- `apps/python/Chapter 7/count_votes/count_votes_concurrent.py`
- `apps/python/Chapter 7/pipeline.py`
