---
type: Article
title: "第 9 章: 名前空間とモジュール設計"
description: "この章ではモノリシックなコードを名前空間に分割し、ドメインモデルに基づいたモジュール設計を行います。"
tags: [article, getting-start-tdd, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 9 章: 名前空間とモジュール設計

## 9.1 はじめに

この章ではモノリシックなコードを名前空間に分割し、ドメインモデルに基づいたモジュール設計を行います。

## 9.2 名前空間による分割

### Before（モノリシック）

```
fizzbuzz/
└── core.clj          (すべてのロジックが集中)
```

### After（モジュール分割）

```
fizzbuzz/
├── core.clj                    (公開 API・エントリーポイント)
├── domain/
│   ├── model.clj               (値オブジェクト・コレクション)
│   └── type.clj                (FizzBuzz タイプ — プロトコル実装)
└── application/
    └── command.clj             (コマンド関数)
```

## 9.3 名前空間の設計原則

### 単一責任の原則

各名前空間は 1 つの責務のみを持ちます。

| 名前空間 | 責務 |
|----------|------|
| `fizzbuzz.core` | 公開 API とメインエントリーポイント |
| `fizzbuzz.domain.model` | FizzBuzzValue と FizzBuzzList の定義 |
| `fizzbuzz.domain.type` | タイプごとの文字列生成ロジック |
| `fizzbuzz.application.command` | コマンドパターンの実装 |

### 依存方向

依存関係は常に上位レイヤーから下位レイヤーへ向かいます。

```
application/command → domain/type → domain/model
                   ↗
     core ────────
```

## 9.4 名前空間の require

```clojure
;; エイリアスを使った require
(ns fizzbuzz.domain.type
  (:require [fizzbuzz.domain.model :as model]))

;; model/fizz? のようにエイリアス経由でアクセス
(model/fizz? 3)  ;=> true
```

### require のベストプラクティス

1. **`:as` エイリアス** を使う（名前の衝突を防ぐ）
2. **`:refer :all`** は避ける（テストファイルでは許容）
3. **循環依存** を作らない

## 9.5 アクセス制御

Clojure では `defn-` でプライベート関数を定義します。

```clojure
;; パブリック関数
(defn fizzbuzz [n] ...)

;; プライベート関数（同一名前空間内でのみアクセス可能）
(defn- divisible-by? [n d]
  (zero? (mod n d)))
```

## 9.6 レコードとプロトコルの配置

### 推奨パターン

- **プロトコルの定義**: 独立した名前空間に配置
- **レコードの実装**: プロトコルを require して実装
- **ファクトリ関数**: プロトコルと同じ名前空間に配置

```clojure
;; domain/type.clj にプロトコル定義とレコード実装を配置
(defprotocol FizzBuzzType
  (generate-string [this value]))

(defrecord FizzBuzzType01 []
  FizzBuzzType
  (generate-string [_this value] ...))

;; ファクトリ関数も同じ名前空間に
(defn create-type [type-num] ...)
```

## 9.7 テスト名前空間の構成

テストは対応するプロダクトコードと同じ構造で配置します。

```
test/
└── fizzbuzz/
    ├── core_test.clj
    ├── domain/
    │   └── type_test.clj
    └── application/
        └── command_test.clj
```

## 9.8 まとめ

この章では以下のことを学びました。

- **ns** マクロによる名前空間の定義と管理
- **:require** と **:as** エイリアスによる依存関係の宣言
- **単一責任の原則** に基づくモジュール設計
- **defn-** によるアクセス制御
- ドメインモデルに基づいた名前空間の分割パターン
- テスト名前空間の構成

第 3 部が完了しました。プロトコルとマルチメソッドによるポリモーフィズムと、名前空間によるモジュール設計を学びました。第 4 部では関数型プログラミングの本質を探ります。
