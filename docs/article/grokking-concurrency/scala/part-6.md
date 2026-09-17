---
type: Article
title: "Part VI - ノンブロッキング I/O"
description: "NIO と Selector パターン"
tags: [article, grokking-concurrency, scala, concurrency, nio, nonblocking]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VI: ノンブロッキング I/O

## 概要

本章では、Java NIO を使用したノンブロッキング I/O を学びます。

---

## ブロッキング vs ノンブロッキング

### ブロッキング I/O

```scala
// スレッドは I/O 完了まで待機
val socket = new Socket("localhost", 8080)
val input = socket.getInputStream
val data = input.read() // ブロック
```

### ノンブロッキング I/O

```scala
import java.nio.channels._
import java.nio.ByteBuffer

val channel = SocketChannel.open()
channel.configureBlocking(false)
channel.connect(new InetSocketAddress("localhost", 8080))

// 接続が完了していなければすぐに戻る
while !channel.finishConnect() do
  // 他の作業ができる
```

---

## Selector パターン

```scala
import java.nio.channels.{Selector, SelectionKey}

val selector = Selector.open()
channel.register(selector, SelectionKey.OP_READ)

// イベントを待機
while true do
  selector.select() // ブロック（イベントがあるまで）

  val keys = selector.selectedKeys().iterator()
  while keys.hasNext do
    val key = keys.next()
    keys.remove()

    if key.isReadable then
      // 読み取り処理
      handleRead(key)
```

---

## メリット

| 項目 | ブロッキング | ノンブロッキング |
|------|-------------|------------------|
| スレッド数 | 接続数に比例 | 少数で対応可能 |
| メモリ | 多い | 少ない |
| 複雑さ | 低い | 高い |

---

## 次のステップ

[Part VII](part-7.md) では、Scala の Future を使った非同期プログラミングを学びます。
