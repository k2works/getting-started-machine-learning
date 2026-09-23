---
type: ADR
title: "011 Clojure 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定"
description: "Clojure 版のライブラリに Clojure CLI・clojure.test・clj-kondo・cljfmt・cloverage・Smile 3.1・data.csv・Ring と Jetty を採用し、表を素のマップとベクタで表す方針と自作する範囲を決める。"
tags: [adr,getting-start-ml,clojure]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T00:00:00Z }
---

# 011 Clojure 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定

「機械学習から始めるプログラミング入門」Clojure 版で使うライブラリを決める。

日付: 2026-09-23

## ステータス

2026-09-23 提案されました

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) の「第 3 波の執筆計画」では、Clojure 版の機械学習ライブラリの第一候補を Smile（Java の相互運用）とし、`scicloj.ml`・`tech.ml.dataset` の保守状況を B55 のステップ 1 で確かめてから確定するとした。Clojure 版の対比の相手は Scala 版・Java 版（JVM と Smile）、F# 版（不変のデータと関数）、Ruby 版・Python 版（動的型付け）である。

B55 のステップ 1 で、使い捨ての `deps.edn` のプロジェクトを `nix develop .#clojure` の中で動かして次を確かめた。

| 確認したこと | 結果 | 確認方法 |
|------------|------|---------|
| Nix 環境 | Clojure CLI 1.12.3.1577、Leiningen 2.11.2、babashka 1.12.209、clojure-lsp 2025.11.28 | `nix develop .#clojure` |
| JDK | `java` コマンドは 25.0.2 だが、**Clojure も Leiningen も JDK 21.0.8 で動く**（`clojure` のラッパーが JDK 21 を持つ） | `(System/getProperty "java.version")` |
| Smile | 3.1.1 を Java の相互運用でそのまま呼べる。決定木・重回帰・K-means・主成分分析（寄与率つき）・リッジ・ラッソ・ロジスティック回帰・ランダムフォレスト・評価指標（`Accuracy`・`ConfusionMatrix`・`AUC`）がある | 使い捨てのプロジェクトで実行 |
| Smile の DataFrame | `DataFrame/of` に `DoubleVector/of`・`IntVector/of` の配列を渡して作る。`DecisionTree/fit` は `Formula`・`DataFrame`・`SplitRule/GINI`・深さなどを受け取る（`Properties` でも渡せる） | 同上 |
| 乱数 | `java.util.Random` と Fisher-Yates で `[4, 8, 9, 6, 3, 5, 2, 1, 7, 0]`。**Java 版・Scala 版と同じ並び** | 同上 |
| CSV | `clojure.data.csv` 1.1.0 は BOM を取り除かない（先頭の列名が `"﻿身長"` になる）。Shift_JIS は `InputStreamReader` に文字コードを渡せば読めるが、UTF-8 として読むと例外を投げずに文字化けする | ヘッダーを表示 |
| `tech.ml.dataset` | 7.032 は動き、BOM も取り除く。ただし依存が大きい（OpenBLAS のネイティブライブラリを含む） | 同上 |
| テスト | `clojure.test` と cognitect-labs/test-runner を `:test` の別名にして `clojure -M:test` で走らせられる | 同上 |
| 整形・静的解析 | **cljfmt も clj-kondo も Nix 環境に入っていない**（環境の起動メッセージの「clj-kondo …」は clojure-lsp の出力の一部）。nixpkgs には cljfmt 0.15.6 と clj-kondo 2025.10.23 があり、字下げの崩れと未使用の束縛をそれぞれ指摘することを確かめた | `nix shell` で試用 |
| カバレッジ | cloverage 1.2.4 が `deps.edn` の別名から動く（`:paths` に `test` を含めないと落ちる） | 使い捨てのプロジェクト |
| 直列化 | EDN（`pr-str`／`read-string`）でマップとベクタをそのまま保存・復元できる | 同上 |

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・ビルド | Clojure と Clojure CLI（`deps.edn`）。`deps.lock` に当たるものは無いので、依存は固定した版で書く | Clojure 1.12、CLI 1.12.3 | EPL-1.0 | 第 1 章 |
| JDK | Nix の Clojure が持つ JDK 21 | 21 | — | 第 1 章 |
| テスト | `clojure.test` と cognitect-labs/test-runner（`clojure -M:test`） | test-runner v0.5.1 | EPL-1.0 | 第 1 章 |
| 整形 | cljfmt（`cljfmt check`） | 0.15.6 | EPL-1.0 | 第 1 章（記事での解説は第 5 章） |
| 静的解析 | clj-kondo（`clj-kondo --lint src test`） | 2025.10.23 | EPL-1.0 | 第 1 章（記事での解説は第 5 章） |
| カバレッジ | cloverage | 1.2.4 | EPL-1.0 | 第 1 章（記事での解説は第 5 章） |
| データの表現 | 素のマップとベクタ（列名をキーにしたマップの並び）。`tech.ml.dataset` は使わない | — | — | 第 1 章 |
| CSV | `clojure.data.csv`（BOM は自分で取り除く） | 1.1.0 | EPL-1.0 | 第 1 章 |
| 文字コード | `java.io.InputStreamReader` に文字コードを渡す（Shift_JIS の CSV） | JDK と同じ | — | 第 9 章 |
| 乱数 | `java.util.Random` と Fisher-Yates | JDK と同じ | — | 第 2 章 |
| 機械学習 | Smile（`smile-core`。Java の相互運用で呼ぶ） | 3.1.1 | BSD-3-Clause | 第 3 章 |
| 直列化 | EDN（学習済みモデル）、Cheshire（API の JSON） | Cheshire 5.13 | EPL-1.0 / MIT | 第 8 章 |
| API | Ring と Jetty（`ring/ring-jetty-adapter`）。経路の振り分けはハンドラーの関数の中で書く | Ring 1.12 | EPL-1.0 | 第 15 章 |

- **`tech.ml.dataset` を使わない。** BOM を取り除いてくれる利点はあるが、データフレームのライブラリを使わないのはシリーズ全体の方針であり、依存に OpenBLAS のネイティブライブラリが入る。表は素のマップとベクタで表し、Smile に渡すところだけ `DataFrame` に変換する
- **`scicloj.ml` も使わない。** Smile を直に呼べるので、包み直す層を増やす理由が無い。Java 版・Scala 版と同じライブラリを同じ設定で呼べることが、この版の対比の軸である
- **Nix の環境定義に clj-kondo と cljfmt を足す。** 検査に使う道具が環境に無いままでは、CI と手元で同じ検査ができない。環境定義を変えた経緯は第 5〜6 章の題材にする（Ruby 版の `RUBYLIB` と同じ扱い）
- **JDK 21 で動くことを前提にする。** `java` コマンドの版（25）とは違うので、記事の環境構築の節に書く
- **各章は「自作してから Smile と突き合わせる」構成にする。** Java 版・Scala 版と同じ流れ。数値が Java 版・Scala 版と一致するかを各章で確かめ、一致しない場合は理由を書く
- **Smile に無いものだけを自作の最終実装とする。** 章に入ってから確かめる（Java 版・Scala 版の ADR 005・007 の記述が手がかりになる）

### 章ごとのライブラリへの置き換え方針

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | `smile.classification.DecisionTree` | 自作のジニ不純度の決定木と突き合わせる。`SplitRule/GINI` と深さの指定、`.importance` も比べる |
| 7 | `smile.regression.OLS` | 正規方程式を自作してから、Smile の重回帰と突き合わせる |
| 8 | Smile の決定木。前処理は自作 | 欠損値の補完・ダミー変数化・クラスの重みは Smile の対応範囲を確かめ、無ければ自作 |
| 9 | Smile の標準化（`smile.feature.transform`） | 自作の標準化と突き合わせる（母標準偏差か標本標準偏差かを実測で確かめる） |
| 10 | `smile.classification.LogisticRegression`・`RandomForest` | 自作と突き合わせる。正則化の既定値を確かめる |
| 11 | `smile.validation.metric` の `Accuracy`・`ConfusionMatrix`・`AUC` | 自作の評価指標・ROC・交差検証と突き合わせる |
| 12 | `smile.regression.RidgeRegression`・`LASSO` | 自作のリッジ・ラッソと突き合わせる（目的関数の流儀の違いに注意する） |
| 13 | `smile.feature.extraction.PCA` | 自作の固有値分解と突き合わせる。寄与率は `.varianceProportion` で取れる |
| 14 | `smile.clustering.KMeans` | 自作の K-means と突き合わせる。SSE は `.distortion` で取れる |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| Leiningen（ビルド） | Clojure CLI の `deps.edn` のほうが依存の指定が素直で、別名でテスト・カバレッジを分けられる。Nix には両方ある |
| `tech.ml.dataset`・`tablecloth`（データフレーム） | シリーズの方針でデータフレームのライブラリを使わない。依存も大きい |
| `scicloj.ml`（機械学習の包み） | Smile を直に呼べる。包む層を増やすと Java 版・Scala 版との対比がぼやける |
| kaocha（テスト） | `clojure.test` と test-runner で足りる。ほかの言語版も標準のテストの仕組みを使っている |
| reitit・compojure（経路の振り分け） | 第 15 章の主題（層の分離と統合テスト）には Ring のハンドラーだけで足りる。Ring では「ハンドラー＝要求のマップを受け取り応答のマップを返す関数」であることを示したい |
| nippy（直列化） | EDN で学習済みモデルのマップをそのまま保存できる。読める形式のほうが記事の題材にしやすい |

## 影響

- 良い影響: Java 版・Scala 版と同じ Smile を同じ JDK で呼ぶので、数値が一致するかどうかを言語をまたいで確かめられる。乱数の並びも一致する
- 良い影響: 表を素のマップとベクタで表すので、Clojure のデータ操作（`map`・`filter`・スレッディングマクロ）がそのまま記事の題材になる
- 悪い影響: `deps.edn` には lock ファイルが無いので、再現性は版を固定して書くことに頼る
- 悪い影響: 検査の道具（clj-kondo・cljfmt）が Nix 環境に無いため、環境定義に手を入れる必要がある
- 悪い影響: Smile の API は Java の静的メソッドと配列が中心で、Clojure の不変のデータとの間で変換が要る。その変換自体は第 3 章以降の題材にする
