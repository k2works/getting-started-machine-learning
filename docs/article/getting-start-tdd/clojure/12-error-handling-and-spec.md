---
type: Article
title: "第 12 章: エラーハンドリングと Spec"
description: "この章では Clojure のエラーハンドリングと、clojure.spec によるデータバリデーションを学びます。"
tags: [article, getting-start-tdd, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 12 章: エラーハンドリングと Spec

## 12.1 はじめに

この章では Clojure のエラーハンドリングと、**clojure.spec** によるデータバリデーションを学びます。

## 12.2 例外処理

### try / catch / finally

Clojure は JVM 上で動作するため、Java の例外機構を利用できます。

```clojure
(try
  (type/generate-string (type/->FizzBuzzTypeNotDefined)
                        (model/create-fizz-buzz-value 1))
  (catch clojure.lang.ExceptionInfo e
    (println "エラー:" (ex-message e))
    (println "データ:" (ex-data e))))
;; エラー: FizzBuzz タイプが未定義です
;; データ: {:type :not-defined}
```

### ex-info による構造化エラー

`ex-info` は例外にデータマップを付与できる Clojure 固有の関数です。

```clojure
;; 例外の生成
(throw (ex-info "FizzBuzz タイプが未定義です"
                {:type :not-defined
                 :requested-type 99}))

;; 例外データの取得
(try
  (cmd/execute (cmd/->FizzBuzzValueCommand 1 99))
  (catch clojure.lang.ExceptionInfo e
    {:message (ex-message e)
     :data (ex-data e)}))
;=> {:message "FizzBuzz タイプが未定義です"
;    :data {:type :not-defined}}
```

### ex-info vs Java 例外

| 特徴 | ex-info | Java 例外 |
|------|---------|-----------|
| データ付与 | マップで構造化データ | メッセージ文字列のみ |
| 型 | `ExceptionInfo` | 任意の例外クラス |
| 活用 | ビジネスロジック | システムレベル |

## 12.3 条件分岐によるエラー処理

### nil を活用したエラー処理

```clojure
;; some-> マクロ（nil ならショートサーキット）
(some-> 15
        model/create-fizz-buzz-value
        (->> (type/generate-string (type/->FizzBuzzType01))))
;=> "FizzBuzz"

(some-> nil
        model/create-fizz-buzz-value
        (->> (type/generate-string (type/->FizzBuzzType01))))
;=> nil
```

### when-let / if-let

```clojure
(defn safe-fizzbuzz [n]
  (when-let [value (and (pos? n) (model/create-fizz-buzz-value n))]
    (type/generate-string (type/->FizzBuzzType01) value)))

(safe-fizzbuzz 3)   ;=> "Fizz"
(safe-fizzbuzz -1)  ;=> nil
```

## 12.4 clojure.spec によるバリデーション

### Spec の定義

`clojure.spec.alpha` を使って、データの仕様を宣言的に定義します。

```clojure
(require '[clojure.spec.alpha :as s])

;; 基本的な Spec 定義
(s/def ::number pos-int?)
(s/def ::type-num #{1 2 3})
(s/def ::fizzbuzz-string (s/or :number string?
                               :fizz #(= % "Fizz")
                               :buzz #(= % "Buzz")
                               :fizzbuzz #(= % "FizzBuzz")))
```

### バリデーション

```clojure
;; 値の検証
(s/valid? ::number 5)     ;=> true
(s/valid? ::number -1)    ;=> false
(s/valid? ::type-num 1)   ;=> true
(s/valid? ::type-num 99)  ;=> false

;; エラー情報の取得
(s/explain-data ::number -1)
;=> {:problems [{:path [], :pred pos-int?, :val -1, :via [::number], :in []}]}
```

### 関数仕様の定義

```clojure
(s/fdef fizzbuzz
  :args (s/cat :n ::number)
  :ret string?)

;; 仕様に基づくテスト生成
(require '[clojure.spec.test.alpha :as stest])
(stest/check `fizzbuzz)
```

### コマンドの Spec

```clojure
(s/def ::fizzbuzz-value-command
  (s/keys :req-un [::number ::type-num]))

(s/valid? ::fizzbuzz-value-command
          {:number 15 :type-num 1})
;=> true
```

## 12.5 テストでの活用

```clojure
(deftest error-handling-test
  (testing "未定義タイプでコマンド実行すると例外が発生する"
    (is (thrown? clojure.lang.ExceptionInfo
                 (cmd/execute (cmd/->FizzBuzzValueCommand 1 99)))))

  (testing "例外のデータを検証する"
    (try
      (cmd/execute (cmd/->FizzBuzzValueCommand 1 99))
      (catch clojure.lang.ExceptionInfo e
        (is (= {:type :not-defined} (ex-data e)))))))
```

## 12.6 まとめ

この章では以下のことを学びました。

- **try / catch / finally** による例外処理
- **ex-info / ex-data** による構造化エラー情報
- **some->** や **when-let** による nil 安全なエラー処理
- **clojure.spec** によるデータバリデーション
- **s/fdef** による関数仕様の定義
- テストでの例外検証パターン

第 4 部が完了しました。Clojure の関数型プログラミングの全体像を学びました。高階関数、永続データ構造、パイプライン処理、そして Spec によるデータバリデーションが Clojure プログラミングの強力な基盤となります。
