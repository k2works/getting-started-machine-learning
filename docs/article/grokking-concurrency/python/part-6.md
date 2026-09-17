---
type: Article
title: "Part VI - ノンブロッキング I/O"
description: "ノンブロッキング I/O とイベント駆動プログラミングを学ぶ"
tags: [article, grokking-concurrency, python, concurrency, non-blocking, event-loop, reactor]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VI: ノンブロッキング I/O

## 概要

本章では、スレッドを使わずに並行処理を実現するノンブロッキング I/O とイベント駆動プログラミングを学びます。イベントループと Reactor パターンの実装を通じて理解を深めます。

---

## 第10章: ブロッキング vs ノンブロッキング

### ブロッキング I/O の問題

従来のブロッキング I/O では、I/O 操作が完了するまでスレッドがブロックされます。

```python
# ブロッキング: データが届くまで待機
data = socket.recv(1024)  # ← ここでブロック
process(data)
```

### スレッド・パー・コネクション

```plantuml
@startuml
!theme plain
left to right direction

rectangle "クライアント" {
  actor "Client 1" as c1
  actor "Client 2" as c2
  actor "Client 3" as c3
  actor "Client N" as cn
}

rectangle "スレッド" {
  rectangle "Thread 1" as t1
  rectangle "Thread 2" as t2
  rectangle "Thread 3" as t3
  rectangle "Thread N" as tn
}

rectangle "処理" as proc

c1 --> t1
c2 --> t2
c3 --> t3
cn --> tn
t1 --> proc
t2 --> proc
t3 --> proc
tn --> proc
@enduml
```

**問題点:**
- スレッド作成のオーバーヘッド
- メモリ消費（スレッドあたり約1MB）
- コンテキストスイッチのコスト

### ノンブロッキング I/O

I/O 操作が即座に返り、データが準備できていなければ後で再試行します。

```python
socket.setblocking(False)
try:
    data = socket.recv(1024)  # 即座に返る
except BlockingIOError:
    # データがまだない
    pass
```

---

## 第11章: イベントループと Reactor パターン

### イベントループ

イベントの発生を監視し、対応するハンドラを実行するループです。

```python
#!/usr/bin/env python3

"""シンプルなイベントループ実装"""

from __future__ import annotations
from collections import deque
from time import sleep
import typing as T


class Event:
    """イベントループで実行可能なイベント"""

    def __init__(self, name: str, action: T.Callable,
                 next_event: T.Optional[Event] = None) -> None:
        self.name = name
        self._action = action
        self._next_event = next_event

    def execute_action(self) -> None:
        """イベントのアクションを実行"""
        self._action(self)
        if self._next_event:
            event_loop.register_event(self._next_event)


class EventLoop:
    """イベントを管理し実行するループ"""

    def __init__(self) -> None:
        self._events: deque[Event] = deque()

    def register_event(self, event: Event) -> None:
        """イベントを登録"""
        self._events.append(event)

    def run_forever(self) -> None:
        """イベントを永続的に処理"""
        print(f"Queue running with {len(self._events)} events")
        while True:
            try:
                event = self._events.popleft()
            except IndexError:
                continue
            event.execute_action()


def knock(event: Event) -> None:
    print(event.name)
    sleep(1)


def who(event: Event) -> None:
    print(event.name)
    sleep(1)


if __name__ == "__main__":
    event_loop = EventLoop()

    # イベントチェーン: knock → who
    replying = Event("Who's there?", who)
    knocking = Event("Knock-knock", knock, replying)

    for _ in range(2):
        event_loop.register_event(knocking)

    event_loop.run_forever()
```

### 実行結果

```
Queue running with 2 events
Knock-knock
Who's there?
Knock-knock
Who's there?
...
```

---

## Reactor パターン

I/O イベントの多重化を行うデザインパターンです。

### 構成要素

| コンポーネント | 役割 |
|----------------|------|
| Reactor | イベントの多重化と振り分け |
| Handler | 特定イベントの処理 |
| Selector | I/O の準備状態を監視 |

### ピザサーバーの例

```python
#!/usr/bin/env python3

"""Reactor パターンによるピザサーバー"""

import socket
import selectors
from collections import deque
import typing as T

selector = selectors.DefaultSelector()
orders: T.Deque[socket.socket] = deque()


def accept_order(server_socket: socket.socket) -> None:
    """新規接続を受け付け"""
    client, addr = server_socket.accept()
    print(f"Connection from {addr}")
    client.setblocking(False)
    selector.register(client, selectors.EVENT_READ, read_order)


def read_order(client: socket.socket) -> None:
    """注文を読み取り"""
    data = client.recv(1024)
    if data:
        print(f"Order received: {data.decode()}")
        orders.append(client)
        # 書き込み可能になったら通知を受ける
        selector.modify(client, selectors.EVENT_WRITE, send_pizza)
    else:
        selector.unregister(client)
        client.close()


def send_pizza(client: socket.socket) -> None:
    """ピザを送信"""
    if orders:
        orders.popleft()
        client.send(b"Here's your pizza!")
        selector.unregister(client)
        client.close()


def run_server(host: str = 'localhost', port: int = 8000) -> None:
    """サーバーを実行"""
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((host, port))
    server.listen()
    server.setblocking(False)

    selector.register(server, selectors.EVENT_READ, accept_order)
    print(f"Pizza server running on {host}:{port}")

    # イベントループ
    while True:
        events = selector.select()  # I/O イベントを待機
        for key, mask in events:
            callback = key.data
            callback(key.fileobj)


if __name__ == "__main__":
    run_server()
```

### selectors モジュール

`selectors` は、I/O 多重化の高レベル API を提供します。

| メソッド | 説明 |
|----------|------|
| `register(fd, events, data)` | ファイル記述子を監視登録 |
| `unregister(fd)` | 監視を解除 |
| `select(timeout)` | イベントを待機 |
| `modify(fd, events, data)` | 監視設定を変更 |

### イベントタイプ

| イベント | 説明 |
|----------|------|
| `EVENT_READ` | 読み取り可能 |
| `EVENT_WRITE` | 書き込み可能 |

---

## シングルスレッドの利点

| 利点 | 説明 |
|------|------|
| ロック不要 | 共有状態の競合なし |
| 軽量 | スレッドのオーバーヘッドなし |
| スケーラブル | 大量接続を効率的に処理 |
| 予測可能 | 実行順序が明確 |

---

## 次のステップ

Part VII では、Python の `asyncio` を使った非同期プログラミングを学びます。コルーチン、Future、async/await の使い方を理解します。

---

## 参考コード

- `apps/python/Chapter 10/pizza_server.py`
- `apps/python/Chapter 11/event_loop.py`
- `apps/python/Chapter 11/pizza_reactor.py`
