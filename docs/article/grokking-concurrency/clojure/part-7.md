---
type: Article
title: "Part VII - 非同期プログラミング"
description: "core.async の詳細"
tags: [article, grokking-concurrency, clojure, concurrency, core.async]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VII: 非同期プログラミング

## 概要

本章では、Clojure の core.async ライブラリを詳しく学びます。

---

## チャネル基礎

```clojure
(require '[clojure.core.async :as async
           :refer [chan go >! <! >!! <!! close!]])

;; チャネル作成
(def ch (chan))           ;; バッファなし
(def ch (chan 10))        ;; バッファサイズ 10

;; go ブロック内での操作
(go (>! ch "hello"))      ;; 非ブロッキング送信
(go (println (<! ch)))    ;; 非ブロッキング受信

;; 通常スレッドでの操作
(>!! ch "hello")          ;; ブロッキング送信
(<!! ch)                  ;; ブロッキング受信
```

---

## go ブロック

```clojure
;; 軽量スレッド（IOC スレッド）
(go
  (let [result (<! (async/timeout 1000))]
    (println "1秒経過")))

;; 複数の go ブロック
(let [ch (chan)]
  (go (>! ch 1))
  (go (>! ch 2))
  (go (println "受信:" (<! ch) (<! ch))))
```

---

## alt! と alts!

```clojure
;; 複数チャネルから選択
(go
  (alt!
    ch1 ([v] (println "ch1 から" v))
    ch2 ([v] (println "ch2 から" v))
    (async/timeout 1000) (println "タイムアウト")))
```

---

## パイプライン

```clojure
;; async/pipeline
(let [in (chan 10)
      out (chan 10)]
  (async/pipeline 4 out (map inc) in)
  (async/onto-chan! in [1 2 3 4 5])
  (<!! (async/into [] out)))
;; => [2 3 4 5 6]
```

---

## core.async 関数一覧

| 関数 | 説明 |
|------|------|
| chan | チャネル作成 |
| go | 軽量スレッド |
| >! / <! | go 内での送受信 |
| >!! / <!! | ブロッキング送受信 |
| alt! | 複数チャネル選択 |
| pipeline | 並列変換 |
| timeout | タイムアウトチャネル |

---

## 次のステップ

[Part VIII](part-8.md) では、MapReduce パターンを学びます。
