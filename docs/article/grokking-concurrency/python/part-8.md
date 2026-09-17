---
type: Article
title: "Part VIII - 分散並列処理"
description: "MapReduce パターンと分散処理を学ぶ"
tags: [article, grokking-concurrency, python, concurrency, mapreduce, distributed]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VIII: 分散並列処理

## 概要

本章では、MapReduce パターンを使った分散並列処理を学びます。行列乗算とワードカウントの実装を通じて、大規模データの並列処理手法を理解します。

---

## 第13章: MapReduce パターン

### MapReduce とは

MapReduce は、大規模データを並列処理するためのプログラミングモデルです。

### 処理フロー

```plantuml
@startuml
!theme plain

rectangle "Input Data" as input
rectangle "Map" as map #LightBlue
rectangle "Shuffle" as shuffle #LightYellow
rectangle "Reduce" as reduce #LightGreen
rectangle "Output Data" as output

input --> map
map --> shuffle
shuffle --> reduce
reduce --> output

note right of map : 各データに関数を適用
note right of shuffle : キーでグループ化
note right of reduce : グループごとに集約
@enduml
```

---

## 行列乗算の並列化

### 逐次処理版

```python
#!/usr/bin/env python3

"""行列乗算（逐次処理版）"""

from typing import List

Row = List[int]
Matrix = List[Row]


def matrix_multiply(matrix_a: Matrix, matrix_b: Matrix) -> Matrix:
    """2つの行列を乗算"""
    num_rows_a = len(matrix_a)
    num_cols_a = len(matrix_a[0])
    num_cols_b = len(matrix_b[0])

    result = [[0] * num_cols_b for _ in range(num_rows_a)]

    for i in range(num_rows_a):
        for j in range(num_cols_b):
            for k in range(num_cols_a):
                result[i][j] += matrix_a[i][k] * matrix_b[k][j]

    return result
```

### 並列処理版

```python
#!/usr/bin/env python3

"""行列乗算（並列処理版）"""

from typing import List, Tuple
import random
from multiprocessing import Pool

Row = List[int]
Column = List[int]
Matrix = List[Row]


def process_row(args: Tuple[Matrix, Matrix, int]) -> Column:
    """1行分の計算を実行"""
    matrix_a, matrix_b, row_idx = args
    num_cols_a = len(matrix_a[0])
    num_cols_b = len(matrix_b[0])

    result_row = [0] * num_cols_b
    for j in range(num_cols_b):
        for k in range(num_cols_a):
            result_row[j] += matrix_a[row_idx][k] * matrix_b[k][j]
    return result_row


def matrix_multiply_parallel(matrix_a: Matrix, matrix_b: Matrix) -> Matrix:
    """行列を並列で乗算"""
    num_rows_a = len(matrix_a)
    num_cols_a = len(matrix_a[0])
    num_rows_b = len(matrix_b)

    if num_cols_a != num_rows_b:
        raise ArithmeticError(
            f"Invalid dimensions: {num_rows_a}x{num_cols_a} * "
            f"{num_rows_b}x{len(matrix_b[0])}")

    # 各行の計算を並列実行
    with Pool() as pool:
        args = [(matrix_a, matrix_b, i) for i in range(num_rows_a)]
        results = pool.map(process_row, args)

    return results


if __name__ == "__main__":
    rows, cols = 100, 100
    A = [[random.randint(0, 10) for _ in range(cols)]
         for _ in range(rows)]
    B = [[random.randint(0, 10) for _ in range(rows)]
         for _ in range(cols)]

    C = matrix_multiply_parallel(A, B)
    print(f"Result matrix shape: {len(C)}x{len(C[0])}")
```

### パフォーマンス比較

```python
import time
from multiprocessing import Pool


def time_sequential(A, B):
    start = time.perf_counter()
    matrix_multiply(A, B)
    return time.perf_counter() - start


def time_parallel(A, B):
    start = time.perf_counter()
    matrix_multiply_parallel(A, B)
    return time.perf_counter() - start


# 500x500 行列の場合
# Sequential: ~15秒
# Parallel (4 cores): ~5秒
```

---

## ワードカウント

### MapReduce によるワードカウント

```python
#!/usr/bin/env python3

"""MapReduce によるワードカウント"""

from collections import defaultdict
from multiprocessing import Pool
import typing as T


def map_function(text: str) -> T.List[T.Tuple[str, int]]:
    """Map: テキストを (word, 1) のペアに変換"""
    words = text.lower().split()
    return [(word, 1) for word in words]


def reduce_function(word_counts: T.List[T.Tuple[str, int]]) -> T.Dict[str, int]:
    """Reduce: 同じ単語のカウントを集約"""
    result = defaultdict(int)
    for word, count in word_counts:
        result[word] += count
    return dict(result)


def word_count_mapreduce(texts: T.List[str]) -> T.Dict[str, int]:
    """MapReduce でワードカウント"""

    # Map フェーズ（並列）
    with Pool() as pool:
        mapped = pool.map(map_function, texts)

    # Shuffle: 全ての結果をフラット化
    shuffled = [pair for sublist in mapped for pair in sublist]

    # Reduce フェーズ
    result = reduce_function(shuffled)

    return result


if __name__ == "__main__":
    texts = [
        "Hello world hello",
        "World of programming",
        "Hello programming world",
    ]

    counts = word_count_mapreduce(texts)
    for word, count in sorted(counts.items(), key=lambda x: -x[1]):
        print(f"{word}: {count}")
```

### 実行結果

```
world: 3
hello: 3
programming: 2
of: 1
```

---

## 分散処理アーキテクチャ

### マスター・ワーカー構成

```plantuml
@startuml
!theme plain

rectangle "Scheduler" as sched #LightBlue
rectangle "Master" as master #LightYellow
rectangle "Worker 1" as w1 #LightGreen
rectangle "Worker 2" as w2 #LightGreen
rectangle "Worker 3" as w3 #LightGreen

sched --> master : タスク分配
master --> w1 : コーディネーション
master --> w2
master --> w3

note right of sched : タスク分配
note right of master : コーディネーション
note bottom of w1 : ワーカー
@enduml
```

### ワーカーの実装例

```python
#!/usr/bin/env python3

"""分散ワードカウントのワーカー"""

import socket
import pickle
from collections import defaultdict


def count_words(text: str) -> dict:
    """テキスト内の単語をカウント"""
    counts = defaultdict(int)
    for word in text.lower().split():
        counts[word] += 1
    return dict(counts)


def worker(host: str = 'localhost', port: int = 9000) -> None:
    """ワーカープロセス"""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect((host, port))
        print("Connected to master")

        while True:
            # タスクを受信
            data = s.recv(4096)
            if not data:
                break

            text = pickle.loads(data)
            print(f"Processing: {text[:50]}...")

            # 処理を実行
            result = count_words(text)

            # 結果を送信
            s.send(pickle.dumps(result))


if __name__ == "__main__":
    worker()
```

---

## スケーリングの考慮事項

### 水平スケーリング

| 要素 | 説明 |
|------|------|
| データ分割 | データを均等に分配 |
| 負荷分散 | ワーカー間で作業量を均等化 |
| 耐障害性 | ワーカー障害時のリカバリ |
| 通信コスト | ネットワークオーバーヘッド |

### Amdahl の法則

並列化による高速化の限界を示す法則:

```
Speedup = 1 / (S + P/N)

S: 逐次部分の割合
P: 並列部分の割合 (P = 1 - S)
N: プロセッサ数
```

---

## まとめ

本シリーズで学んだ内容:

| Part | トピック | キーポイント |
|------|----------|-------------|
| I | 逐次処理 | 基本概念、パフォーマンス測定 |
| II | プロセス/スレッド | GIL、スレッドプール |
| III | マルチタスキング | タイムシェアリング |
| IV | 並列パターン | Fork/Join、パイプライン |
| V | 同期 | Lock、Semaphore、デッドロック |
| VI | ノンブロッキング I/O | イベントループ、Reactor |
| VII | 非同期 | asyncio、コルーチン |
| VIII | 分散処理 | MapReduce |

---

## 参考コード

- `apps/python/Chapter 13/matmul/`
- `apps/python/Chapter 13/wordcount/`

---

## 参考資料

- [Grokking Concurrency](https://www.manning.com/books/grokking-concurrency) - 原著
- [Python 公式ドキュメント - concurrent.futures](https://docs.python.org/3/library/concurrent.futures.html)
- [Python 公式ドキュメント - asyncio](https://docs.python.org/3/library/asyncio.html)
- [MapReduce: Simplified Data Processing on Large Clusters](https://research.google/pubs/pub62/) - Google 論文
