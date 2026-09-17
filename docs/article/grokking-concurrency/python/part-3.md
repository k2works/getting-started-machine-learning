---
type: Article
title: "Part III - マルチタスキングとスケジューリング"
description: "マルチタスキングとタイムシェアリングの概念を学ぶ"
tags: [article, grokking-concurrency, python, concurrency, multitasking, scheduling]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part III: マルチタスキングとスケジューリング

## 概要

本章では、OS がどのように複数のタスクを同時に実行しているように見せるかを学びます。マルチタスキングとタイムシェアリングの仕組みを、ゲームループの実装を通じて理解します。

---

## 第6章: マルチタスキング

### マルチタスキングとは

マルチタスキングは、複数のタスクを切り替えながら実行することで、あたかも同時に実行しているように見せる技術です。

### タイムシェアリング

CPU 時間を小さなスライスに分割し、各タスクに順番に割り当てます。

```plantuml
@startuml
!theme plain
title タイムシェアリング

concise "CPU" as cpu

@0
cpu is "T1"

@1
cpu is "T2"

@2
cpu is "T3"

@3
cpu is "T1"

@4
cpu is "T2"

@5
cpu is "T3"

@1 <-> @2 : コンテキストスイッチ
@enduml
```

---

### ゲームループの実装

アーケードゲーム（パックマン）を例に、マルチタスキングを実装します。

#### ゲームの3つのタスク

1. **入力処理** - ユーザーの操作を受け付ける
2. **ゲーム計算** - ゲーム世界の状態を更新
3. **描画処理** - 画面を更新

#### シンプルな実装（イベントベース）

```python
#!/usr/bin/env python3

"""イベントベースのゲームループ"""

import typing as T
from threading import Thread, Event

from pacman import get_user_input, compute_game_world, render_next_screen

# プロセッサが空いているかを示すイベント
processor_free = Event()
processor_free.set()


class Task(Thread):
    def __init__(self, func: T.Callable[..., None]):
        super().__init__()
        self.func = func

    def run(self) -> None:
        while True:
            processor_free.wait()   # シグナルを待つ
            processor_free.clear()  # 他のタスクをブロック
            self.func()             # タスクを実行
            processor_free.set()    # 次のタスクを許可


def arcade_machine() -> None:
    """アーケードマシンのメイン機能"""
    get_user_input_task = Task(get_user_input)
    compute_game_world_task = Task(compute_game_world)
    render_next_screen_task = Task(render_next_screen)

    get_user_input_task.start()
    compute_game_world_task.start()
    render_next_screen_task.start()


if __name__ == "__main__":
    arcade_machine()
```

### Event オブジェクトの役割

`threading.Event` は、スレッド間の同期に使用されます。

| メソッド | 説明 |
|----------|------|
| `set()` | フラグを True に設定 |
| `clear()` | フラグを False に設定 |
| `wait()` | フラグが True になるまでブロック |
| `is_set()` | フラグの状態を確認 |

### パックマンの実装

```python
"""パックマンゲームの実装"""

import sys
import time
import select
from random import randrange

# グローバルなゲーム状態
PACMAN_POSITION = {}


def get_user_input() -> None:
    """コントローラからの入力を取得"""
    global PACMAN_POSITION
    i, o, e = select.select([sys.stdin], [], [], 1)
    if i:
        user_input = sys.stdin.readline().strip()
        print(f"user input: {user_input}")
        PACMAN_POSITION["user_input"] = user_input


def compute_game_world() -> None:
    """ゲームルールに従って世界を計算"""
    global PACMAN_POSITION
    print(f"Pacman doing: {PACMAN_POSITION.get('user_input', 'N/A')}")
    print("computing")
    PACMAN_POSITION["position"] = randrange(10)
    time.sleep(1)


def render_next_screen() -> None:
    """次のフレームを描画"""
    global PACMAN_POSITION
    print(f"Rendering pacman at position: "
          f"{PACMAN_POSITION.get('position', 0)}")
    print("rendering")
    time.sleep(0.5)
```

---

## コンテキストスイッチ

タスクを切り替える際、現在のタスクの状態を保存し、次のタスクの状態を復元します。

### コンテキストスイッチのコスト

- レジスタの保存と復元
- メモリキャッシュの無効化
- TLB（Translation Lookaside Buffer）のフラッシュ

```plantuml
@startuml
!theme plain
title タスク切り替えのオーバーヘッド

rectangle "コンテキストスイッチ" {
  card "状態保存" as save #LightBlue
  card "スケジューラ実行" as sched #LightYellow
  card "状態復元" as restore #LightGreen
}

note bottom of save : ~数マイクロ秒
note bottom of sched : ~数マイクロ秒
note bottom of restore : ~数マイクロ秒

save --> sched
sched --> restore
@enduml
```

---

## プリエンプティブ vs 協調的

### プリエンプティブ・マルチタスキング

- OS がタスクを強制的に中断
- タイムスライスで制御
- 公平なリソース配分

### 協調的マルチタスキング

- タスクが自発的に制御を譲る
- `yield` で制御を返す
- 効率的だが、行儀の悪いタスクに注意

---

## 次のステップ

Part IV では、タスク分解と並列パターン（Fork/Join、パイプライン）を学びます。複雑な問題を並列化可能なサブタスクに分割する手法を理解します。

---

## 参考コード

- `apps/python/Chapter 6/arcade_machine.py`
- `apps/python/Chapter 6/pacman.py`
