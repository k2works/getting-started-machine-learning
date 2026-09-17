---
type: Article
title: "Part V - 同期と排他制御"
description: "並行処理における同期問題と解決策を学ぶ"
tags: [article, grokking-concurrency, python, concurrency, lock, semaphore, deadlock]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part V: 同期と排他制御

## 概要

本章では、並行処理における同期問題（レースコンディション、デッドロック、ライブロック、スタベーション）と、その解決策（Lock、Semaphore）を学びます。

---

## 第8章: レースコンディションと Lock

### レースコンディションとは

複数のスレッドが共有リソースに同時にアクセスし、結果が実行順序に依存してしまう問題です。

### 銀行口座の例（問題あり）

```python
#!/usr/bin/env python3

"""レースコンディションが発生する銀行口座"""

import time
from threading import Thread


class UnsyncedBankAccount:
    """同期なしの銀行口座（危険）"""

    def __init__(self, balance: float = 0):
        self.balance = balance

    def deposit(self, amount: float) -> None:
        # この3行の間に他のスレッドが割り込む可能性あり
        current = self.balance
        time.sleep(0.001)  # 処理時間をシミュレート
        self.balance = current + amount

    def withdraw(self, amount: float) -> None:
        current = self.balance
        time.sleep(0.001)
        self.balance = current - amount


class ATM(Thread):
    """ATMスレッド"""

    def __init__(self, bank_account):
        super().__init__()
        self.bank_account = bank_account

    def run(self) -> None:
        for _ in range(100):
            self.bank_account.deposit(10)
            self.bank_account.withdraw(10)


if __name__ == "__main__":
    account = UnsyncedBankAccount(balance=1000)

    atms = [ATM(account) for _ in range(5)]
    for atm in atms:
        atm.start()
    for atm in atms:
        atm.join()

    # 期待: 1000、実際: 予測不能
    print(f"Final balance: {account.balance}")
```

### 解決策: Lock を使用

```python
from threading import Lock


class SyncedBankAccount:
    """Lock で同期した銀行口座"""

    def __init__(self, balance: float = 0):
        self.balance = balance
        self.lock = Lock()

    def deposit(self, amount: float) -> None:
        with self.lock:  # クリティカルセクション
            current = self.balance
            time.sleep(0.001)
            self.balance = current + amount

    def withdraw(self, amount: float) -> None:
        with self.lock:
            current = self.balance
            time.sleep(0.001)
            self.balance = current - amount
```

---

## Semaphore（セマフォ）

Semaphore は、同時にアクセスできるスレッド数を制限します。

### 駐車場の例

```python
#!/usr/bin/env python3

"""セマフォによる駐車場の実装"""

import time
import random
import typing as T
from threading import Thread, Semaphore, Lock

TOTAL_SPOTS = 3  # 駐車スペース数


class Garage:
    """駐車場"""

    def __init__(self) -> None:
        self.semaphore = Semaphore(TOTAL_SPOTS)
        self.cars_lock = Lock()
        self.parked_cars: T.List[str] = []

    def enter(self, car_name: str) -> None:
        """駐車場に入る"""
        self.semaphore.acquire()  # スペースを確保
        with self.cars_lock:
            self.parked_cars.append(car_name)
            print(f"{car_name} parked")

    def exit(self, car_name: str) -> None:
        """駐車場を出る"""
        with self.cars_lock:
            self.parked_cars.remove(car_name)
            print(f"{car_name} leaving")
        self.semaphore.release()  # スペースを解放


def park_car(garage: Garage, car_name: str) -> None:
    garage.enter(car_name)
    time.sleep(random.uniform(1, 2))
    garage.exit(car_name)


if __name__ == "__main__":
    garage = Garage()
    threads = []

    for i in range(10):
        t = Thread(target=park_car, args=(garage, f"Car #{i}"))
        threads.append(t)
        t.start()

    for t in threads:
        t.join()
```

---

## 第9章: デッドロック

### デッドロックとは

複数のスレッドが互いに相手の持つリソースを待ち続け、永遠に進行しない状態です。

### 食事する哲学者問題

```python
#!/usr/bin/env python3

"""デッドロックが発生する哲学者問題"""

import time
from threading import Thread, Lock

dumplings = 20


class LockWithName:
    """名前付きLock"""
    def __init__(self, name: str):
        self.name = name
        self.lock = Lock()

    def acquire(self):
        self.lock.acquire()

    def release(self):
        self.lock.release()


class Philosopher(Thread):
    def __init__(self, name: str, left: LockWithName, right: LockWithName):
        super().__init__()
        self.name = name
        self.left_chopstick = left
        self.right_chopstick = right

    def run(self) -> None:
        global dumplings

        while dumplings > 0:
            # 左の箸を取る
            self.left_chopstick.acquire()
            print(f"{self.left_chopstick.name} grabbed by {self.name}")

            # 右の箸を取ろうとする（デッドロックの原因）
            self.right_chopstick.acquire()
            print(f"{self.right_chopstick.name} grabbed by {self.name}")

            dumplings -= 1
            print(f"{self.name} eats. Left: {dumplings}")

            self.right_chopstick.release()
            self.left_chopstick.release()
            time.sleep(0.1)


if __name__ == "__main__":
    chopstick_a = LockWithName("chopstick_a")
    chopstick_b = LockWithName("chopstick_b")

    # 両方の哲学者が異なる順序で箸を取る → デッドロック
    p1 = Philosopher("Philosopher #1", chopstick_a, chopstick_b)
    p2 = Philosopher("Philosopher #2", chopstick_b, chopstick_a)

    p1.start()
    p2.start()
```

### デッドロックの条件（4つ全て必要）

1. **相互排除** - リソースは一度に1つのスレッドのみ使用
2. **保持と待機** - リソースを保持しながら他を待つ
3. **横取り不可** - 他者からリソースを奪えない
4. **循環待ち** - 循環的な待ち関係が存在

### 解決策1: リソース順序付け

```python
if __name__ == "__main__":
    chopstick_a = LockWithName("chopstick_a")
    chopstick_b = LockWithName("chopstick_b")

    # 両方の哲学者が同じ順序で箸を取る
    p1 = Philosopher("Philosopher #1", chopstick_a, chopstick_b)
    p2 = Philosopher("Philosopher #2", chopstick_a, chopstick_b)
```

### 解決策2: 仲裁者パターン

```python
class Waiter:
    """仲裁者"""
    def __init__(self):
        self.mutex = Lock()

    def ask_for_chopsticks(self, left, right):
        with self.mutex:
            left.acquire()
            right.acquire()

    def release_chopsticks(self, left, right):
        right.release()
        left.release()
```

---

## ライブロックとスタベーション

### ライブロック

スレッドが状態を変化させ続けるが、進行しない状態。

### スタベーション

特定のスレッドが長期間リソースを取得できない状態。公平なスケジューリングで解決します。

---

## 同期プリミティブの比較

| プリミティブ | 用途 | 同時アクセス数 |
|--------------|------|----------------|
| Lock | 排他制御 | 1 |
| RLock | 再帰的ロック | 1（同一スレッドは再取得可） |
| Semaphore | リソース数制限 | N |
| Event | スレッド間シグナル | N/A |
| Condition | 条件待機 | N/A |

---

## 次のステップ

Part VI では、ノンブロッキング I/O とイベント駆動プログラミングを学びます。スレッドを使わずに並行処理を実現する方法を理解します。

---

## 参考コード

- `apps/python/Chapter 8/race_condition/`
- `apps/python/Chapter 8/semaphore.py`
- `apps/python/Chapter 9/deadlock/`
