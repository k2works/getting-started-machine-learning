---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "この章では Clojure プロジェクトのパッケージ管理と静的コード解析ツールについて学びます。"
tags: [article, getting-start-tdd, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

この章では Clojure プロジェクトのパッケージ管理と静的コード解析ツールについて学びます。

## 5.2 Leiningen によるパッケージ管理

### project.clj

Leiningen のプロジェクト設定ファイル `project.clj` で依存関係やプラグインを管理します。

```clojure
(defproject fizzbuzz "0.1.0-SNAPSHOT"
  :description "FizzBuzz - TDD practice in Clojure"
  :dependencies [[org.clojure/clojure "1.11.1"]]
  :plugins [[lein-cloverage "1.2.4"]
            [lein-kibit "0.1.8"]
            [jonase/eastwood "1.4.3"]]
  :main ^:skip-aot fizzbuzz.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
```

### 依存関係の管理

```bash
# 依存関係の取得
$ lein deps

# 依存関係のツリー表示
$ lein deps :tree
```

Leiningen は Maven リポジトリと Clojars から依存関係を自動的にダウンロードします。

## 5.3 静的コード解析

### Eastwood — 静的コード解析

Eastwood はコードの潜在的な問題を検出する Linter です。

```bash
$ lein eastwood
```

検出する主な問題:

- 未使用の変数や名前空間
- 間違った関数呼び出し
- 非推奨な構文の使用
- リフレクション警告

### Kibit — イディオム検査

Kibit は Clojure らしい書き方を提案するツールです。

```bash
$ lein kibit
```

提案の例:

```clojure
;; Before（Kibit が改善を提案）
(if (= x true) :yes :no)

;; After（Clojure イディオム）
(if x :yes :no)
```

```clojure
;; Before
(not (empty? coll))

;; After
(seq coll)
```

### cljfmt — コードフォーマッター

cljfmt は Clojure コードのフォーマットを統一するツールです。

```bash
# フォーマットチェック
$ lein cljfmt check

# 自動フォーマット
$ lein cljfmt fix
```

## 5.4 コードカバレッジ

静的コード解析による品質の確認ができました。動的なテストに関しては **コードカバレッジ** を確認する必要があります。

> コード網羅率（Code coverage）は、ソフトウェアテストで用いられる尺度の 1 つである。プログラムのソースコードがテストされた割合を意味する。
>
> — ウィキペディア

Cloverage を使ってテストカバレッジを計測します。

```bash
$ lein cloverage
```

`project.clj` にプラグインを追加済みです。

```clojure
:plugins [[lein-cloverage "1.2.4"]]
```

テスト実行後に `target/coverage` フォルダが作成されます。その中の `index.html` を開くとカバレッジ状況を確認できます。

## 5.5 コード複雑度のチェック

静的コード解析では、コーディングスタイルやバグパターンだけでなく、**コードの複雑度** もチェックできます。Clojure プロジェクトでは、Bikeshed による品質チェックと独自の循環複雑度チェッカーを組み合わせて、関数の複雑度を制限します。

### 循環的複雑度（Cyclomatic Complexity）

> 循環的複雑度（サイクロマティック複雑度）とは、ソフトウェア測定法の一つであり、コードがどれぐらい複雑であるかを関数単位で数値にして表す指標。

Clojure では以下のフォームが分岐を生み出し、複雑度を上げます。

| フォーム | 説明 |
|---------|------|
| `cond` | 各分岐（`:else` 除く）で +1 |
| `if` / `if-let` / `if-not` | 条件分岐で +1 |
| `when` / `when-let` / `when-not` | 条件付き実行で +1 |
| `case` | 各分岐（デフォルト除く）で +1 |
| `and` / `or` | 短絡評価による分岐で +1 |
| `loop` | 再帰ループで +1 |
| `try` | 例外処理で +1 |

本プロジェクトでは、循環的複雑度を **7 以下** に制限しています。

| 複雑度の範囲 | 意味 |
|-------------|------|
| 1〜7 | 低複雑度：管理しやすく、問題なし |
| 8〜15 | 中程度の複雑度：リファクタリングを検討 |
| 16 以上 | 高複雑度：関数を分割する必要がある |

```clojure
;; 循環複雑度が低い例（複雑度: 1）
(defn simple-function [x]
  (+ x 1))

;; 循環複雑度が中程度の例（複雑度: 5）
(defn fizzbuzz [n]
  (cond
    (and (zero? (mod n 3)) (zero? (mod n 5))) "FizzBuzz"  ; cond +1, and +1
    (zero? (mod n 3)) "Fizz"                               ; cond +1
    (zero? (mod n 5)) "Buzz"                               ; cond +1
    :else (str n)))                                        ; :else は加算なし
;; 基本 1 + cond 3 分岐 + and 1 = 5
```

### Bikeshed — コード品質チェック

[Bikeshed](https://github.com/dakrone/lein-bikeshed) は Clojure コードの品質を総合的にチェックするツールです。

```bash
$ lein bikeshed
```

チェック項目:

- 行の長さ（100 文字制限）
- 末尾の空白
- ファイル末尾の空行
- clojure.core 関数との名前衝突
- docstring の記述率

`project.clj` での設定:

```clojure
:plugins [[lein-bikeshed "0.5.2"]]
:bikeshed {:verbose true
           :max-line-length 100}
```

### 循環複雑度チェッカー

Clojure には PMD のような循環複雑度チェッカーが標準で提供されていないため、プロジェクト固有のチェッカーを `dev/complexity_checker.clj` に作成しています。

```clojure
;; dev/complexity_checker.clj（抜粋）
(def ^:private branching-forms
  #{'if 'if-let 'if-not 'if-some
    'when 'when-let 'when-not 'when-some 'when-first
    'and 'or 'loop 'try})

(defn- complexity-of-form
  "S 式を再帰的に走査して循環複雑度を計算する"
  [form]
  (cond
    (not (sequential? form)) 0
    (empty? form) 0
    :else
    (let [head (first form)
          children-complexity (reduce + (map complexity-of-form (rest form)))]
      (+ children-complexity
         (cond
           (= head 'cond) (count-cond-branches form)
           (= head 'case) (count-case-branches form)
           (contains? branching-forms head) 1
           :else 0)))))
```

このチェッカーは S 式を再帰的に走査し、分岐フォームの数を数えて循環複雑度を計算します。Clojure のホモイコニシティ（コードがデータ構造である性質）を活かしたアプローチです。

### 複雑度チェックの実行

`project.clj` にエイリアスを定義して簡単に実行できるようにします。

```clojure
:aliases {"complexity" ["run" "-m" "complexity-checker"]}
:profiles {:dev {:source-paths ["dev"]}}
```

```bash
$ lein complexity

=== Clojure 循環複雑度チェック (閾値: 7) ===

  [ok] fizzbuzz (core.clj)                      複雑度: 5
  [ok] create-type (type.clj)                   複雑度: 3
  [ok] fizz-buzz? (model.clj)                   複雑度: 2
  [ok] fizzbuzz-list (core.clj)                 複雑度: 1
  [ok] print-fizzbuzz (core.clj)                複雑度: 1
  ...

関数数: 10, 違反: 0

複雑度チェック: 成功
```

Makefile でも実行できます。

```bash
$ make complexity
```

### 複雑度チェックの効果

コード複雑度の制限により、以下の効果が得られます。

- **可読性向上** — 小さな関数は理解しやすい
- **保守性向上** — 変更の影響範囲が限定される
- **テスト容易性** — 個別機能のテストが簡単
- **自動品質管理** — 複雑なコードの混入を自動防止

現在の FizzBuzz の `fizzbuzz` 関数は循環複雑度が 5 で、制限値 7 以内に収まっています。第 3 部でプロトコルとマルチメソッドによる設計を進める際も、この制限を意識してコードを書いていきます。

## 5.6 名前空間と依存関理

Clojure では `ns` マクロで名前空間を定義し、`:require` で依存関係を宣言します。

```clojure
(ns fizzbuzz.core
  (:require [fizzbuzz.domain.model :as model]
            [fizzbuzz.domain.type :as type])
  (:gen-class))
```

名前空間の命名規則:

| 名前空間 | ファイルパス | 役割 |
|----------|-------------|------|
| `fizzbuzz.core` | `src/fizzbuzz/core.clj` | 公開 API |
| `fizzbuzz.domain.model` | `src/fizzbuzz/domain/model.clj` | 値オブジェクト |
| `fizzbuzz.domain.type` | `src/fizzbuzz/domain/type.clj` | タイプ定義 |
| `fizzbuzz.application.command` | `src/fizzbuzz/application/command.clj` | コマンド |

## 5.7 まとめ

この章では、パッケージ管理と静的コード解析を導入しました。

| ツール | 用途 | コマンド |
|--------|------|---------|
| Leiningen | 依存関係管理・ビルド | `lein deps` |
| Eastwood | 静的コード解析 | `lein eastwood` |
| Kibit | イディオム検査 | `lein kibit` |
| Bikeshed | コード品質チェック | `lein bikeshed` |
| 循環複雑度チェッカー | 関数の複雑度計測 | `lein complexity` |
| cljfmt | コードフォーマット | `lein cljfmt check` |
| Cloverage | テストカバレッジ | `lein cloverage` |

この章では以下のことを学びました。

- **Leiningen** による依存関係管理と `project.clj` の構成
- **Eastwood** による静的コード解析
- **Kibit** による Clojure イディオムの検査
- **cljfmt** によるコードフォーマット
- **Cloverage** によるテストカバレッジ計測
- **Bikeshed** によるコード品質チェック
- **循環複雑度チェッカー** による関数複雑度の自動検出
- `ns` マクロによる名前空間と依存関係の管理

次の章では、これらのタスクをまとめて実行できるタスクランナーと、CI/CD パイプラインの構築について解説します。
