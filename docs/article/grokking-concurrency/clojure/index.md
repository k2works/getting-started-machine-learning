# Clojure で学ぶ並行処理

## 概要

本シリーズでは、Clojure の並行処理機能を使用して「Grokking Concurrency」の概念を実装します。

Clojure は JVM 上で動作する関数型言語で、不変データ構造と強力な並行処理プリミティブを提供します。

---

## 目次

| Part | タイトル | トピック |
|------|----------|----------|
| [Part I](part-1.md) | 逐次処理 | 基本、SHA-256 |
| [Part II](part-2.md) | スレッド | future、Thread |
| [Part III](part-3.md) | マルチタスキング | atom、イベント同期 |
| [Part IV](part-4.md) | 並列パターン | Fork/Join、Pipeline |
| [Part V](part-5.md) | 同期と排他制御 | STM、ref、dosync |
| [Part VI](part-6.md) | ノンブロッキング I/O | future、promise |
| [Part VII](part-7.md) | 非同期プログラミング | core.async |
| [Part VIII](part-8.md) | 分散並列処理 | pmap、MapReduce |

---

## Clojure の並行処理プリミティブ

### 1. Atom（アトム）

```clojure
;; CAS ベースの単一値更新
(def counter (atom 0))
(swap! counter inc)    ; インクリメント
(reset! counter 0)     ; リセット
@counter               ; 値を取得
```

### 2. Ref + STM（ソフトウェアトランザクショナルメモリ）

```clojure
;; 複数の値を同期的に更新
(def account (ref 1000))
(dosync
  (alter account + 100))  ; トランザクション内で更新
```

### 3. Agent（エージェント）

```clojure
;; 非同期の状態更新
(def logger (agent []))
(send logger conj "message")  ; 非同期で更新
```

### 4. Future / Promise

```clojure
;; 非同期計算
(def result (future (expensive-computation)))
@result  ; 結果を待機

;; Promise
(def p (promise))
(deliver p :done)
@p
```

### 5. core.async

```clojure
(require '[clojure.core.async :refer [chan go >! <!]])

(def ch (chan))
(go (>! ch "hello"))     ; チャネルに送信
(go (println (<! ch)))   ; チャネルから受信
```

---

## 環境構築

### Leiningen プロジェクト

```bash
lein new app grokking-concurrency-clojure
```

### 依存関係（project.clj）

```clojure
:dependencies [[org.clojure/clojure "1.11.1"]
               [org.clojure/core.async "1.6.681"]
               [buddy/buddy-core "1.11.423"]]
```

### テスト実行

```bash
lein test
```

---

## 次のステップ

[Part I](part-1.md) から始めて、逐次処理の基礎を学びます。
