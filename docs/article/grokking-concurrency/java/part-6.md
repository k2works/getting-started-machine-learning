---
type: Article
title: "Part VI - ノンブロッキング I/O"
description: "ノンブロッキング I/O とイベント駆動プログラミングを学ぶ"
tags: [article, grokking-concurrency, java, concurrency, non-blocking, event-loop, nio]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VI: ノンブロッキング I/O

## 概要

本章では、スレッドを使わずに並行処理を実現するノンブロッキング I/O とイベント駆動プログラミングを学びます。Java NIO を使った実装を通じて理解を深めます。

---

## Java NIO の主要クラス

| クラス | 説明 |
|--------|------|
| `Selector` | I/O 多重化セレクタ |
| `Channel` | I/O チャネル |
| `ByteBuffer` | バイトバッファ |
| `SelectionKey` | セレクションキー |

### ノンブロッキング I/O の例

```java
// ノンブロッキングモードに設定
channel.configureBlocking(false);

// セレクタに登録
selector.register(channel, SelectionKey.OP_READ);

// イベントループ
while (true) {
    selector.select();
    for (SelectionKey key : selector.selectedKeys()) {
        if (key.isReadable()) {
            // 読み取り処理
        }
    }
}
```

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

Part VII では、Java の CompletableFuture と Virtual Threads を使った非同期プログラミングを学びます。

---

## 参考資料

- [Java NIO Tutorial](https://docs.oracle.com/javase/tutorial/essential/io/fileio.html)
