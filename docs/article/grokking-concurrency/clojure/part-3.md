---
type: Article
title: "Part III - マルチタスキングとスケジューリング"
description: "atom とイベント同期"
tags: [article, grokking-concurrency, clojure, concurrency, atom]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part III: マルチタスキングとスケジューリング

## 概要

本章では、Clojure の atom とイベント同期を学びます。

---

## Atom（アトム）

```clojure
;; 作成
(def counter (atom 0))

;; 更新
(swap! counter inc)        ;; 関数適用
(swap! counter + 10)       ;; 引数付き
(reset! counter 0)         ;; 直接設定

;; 取得
@counter  ;; または (deref counter)
```

---

## イベント同期

### イベントオブジェクト

```clojure
(defn create-event
  "イベントオブジェクトを作成"
  []
  {:ready (atom false)
   :lock (Object.)})

(defn signal-event!
  "イベントをシグナル"
  [event]
  (locking (:lock event)
    (reset! (:ready event) true)
    (.notifyAll (:lock event))))

(defn wait-event!
  "イベントを待機"
  [event]
  (locking (:lock event)
    (while (not @(:ready event))
      (.wait (:lock event)))
    (reset! (:ready event) false)))
```

---

## GameLoop 実装

### ゲーム状態

```clojure
(defn create-game-state
  "ゲーム状態を作成"
  []
  {:event-count (atom 0)
   :rendered-count (atom 0)
   :running (atom true)})

(defn process-event! [state]
  (swap! (:event-count state) inc))

(defn render-frame! [state]
  (swap! (:rendered-count state) inc))
```

---

## ゲームループ

```clojure
(defn run-game-loop!
  "ゲームループを実行"
  [state iterations]
  (let [processor-event (create-event)
        renderer-event (create-event)]
    ;; イベント処理スレッド
    (future
      (dotimes [_ iterations]
        (wait-event! processor-event)
        (process-event! state)
        (signal-event! renderer-event)))
    ;; レンダリングスレッド
    (future
      (dotimes [_ iterations]
        (signal-event! processor-event)
        (wait-event! renderer-event)
        (render-frame! state)))))
```

---

## atom vs ref

| 特徴 | atom | ref |
|------|------|-----|
| 用途 | 単一値 | 複数値の協調更新 |
| 更新 | swap!, reset! | alter, ref-set |
| トランザクション | 不要 | dosync 必須 |

---

## 次のステップ

[Part IV](part-4.md) では、Fork/Join と Pipeline パターンを学びます。
