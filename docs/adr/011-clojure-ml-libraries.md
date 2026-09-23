---
type: ADR
title: "011 Clojure 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定"
description: "Clojure 版のライブラリに Clojure CLI・clojure.test・clj-kondo・cljfmt・cloverage・Tribuo 4.3・data.csv・Ring と Jetty を採用し、GPL-3.0 の Smile を使わない理由と表を素のマップとベクタで表す方針を決める。"
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
| Smile | 3.1.1 を Java の相互運用でそのまま呼べ、決定木・重回帰・K-means・主成分分析（寄与率つき）・リッジ・ラッソ・ロジスティック回帰・ランダムフォレスト・評価指標が動いた。**しかし POM のライセンスは GPL-3.0**（2.6.0 は LGPL-3.0）で、[ADR 007](007-scala-ml-libraries.md) が Scala 版で Smile を採用しなかった理由と同じ。採用しない | 使い捨てのプロジェクトで実行、Maven Central の POM |
| Tribuo | 4.3.2、Apache License 2.0。Java 版（[ADR 005](005-java-ml-libraries.md)）・Kotlin 版・Scala 版（[ADR 007](007-scala-ml-libraries.md)）で各アルゴリズムの癖を確認済み。Clojure からは `ArrayExample`・`MutableDataset`・`CARTClassificationTrainer` を Java の相互運用で呼ぶ | Maven Central |
| Smile の DataFrame | `DataFrame/of` に `DoubleVector/of`・`IntVector/of` の配列を渡して作る。`DecisionTree/fit` は `Formula`・`DataFrame`・`SplitRule/GINI`・深さなどを受け取る | 同上 |
| 乱数 | `java.util.Random` と Fisher-Yates で `[4, 8, 9, 6, 3, 5, 2, 1, 7, 0]`。**Java 版・Scala 版と同じ並び** | 同上 |
| CSV | `clojure.data.csv` 1.1.0 は BOM を取り除かない（先頭の列名が `"\uFEFF身長"` になる）。Shift_JIS は `InputStreamReader` に文字コードを渡せば読めるが、UTF-8 として読むと例外を投げずに文字化けする | ヘッダーを表示 |
| `tech.ml.dataset` | 7.032 は動き、BOM も取り除く。ただし依存が大きい（OpenBLAS のネイティブライブラリを含む） | 同上 |
| テスト | `clojure.test` と cognitect-labs/test-runner を `:test` の別名にして `clojure -M:test` で走らせられる | 同上 |
| 整形・静的解析 | **cljfmt も clj-kondo も Nix 環境に入っていない**（環境の起動メッセージの「clj-kondo …」は clojure-lsp の出力の一部）。nixpkgs には cljfmt 0.15.6 と clj-kondo 2025.10.23 があり、字下げの崩れと未使用の束縛をそれぞれ指摘することを確かめた | `nix shell` で試用 |
| カバレッジ | cloverage 1.2.4 が `deps.edn` の別名から動く（`:paths` に `test` を含めないと落ちる） | 使い捨てのプロジェクト |
| 直列化 | EDN（`pr-str`／`read-string`）でマップとベクタをそのまま保存・復元できる | 同上 |

### 各章で確かめた結果

| 章 | 確かめたこと |
|----|------------|
| 1 | `clojure.data.csv` は BOM を取り除かないので、先頭の列名から自分で取り除く。`clojure.test` にスキップが無いので、実データのテストは理由を標準エラーに出して早く戻る（テストの数は変わらず、表明の数が 18→16 と動く）。Rust 版のようにカバレッジの差でスキップを見分ける手は使えない（第 1 章では実データの経路を一時ファイルのテストが覆うため、カバレッジが変わらない） |
| 2 | `java.util.Random` と Fisher-Yates の並び（シード 0 で `[4, 8, 9, 6, 3, 5, 2, 1, 7, 0]`）が Java 版・Scala 版と一致し、**訓練データの平均値も一致した**（がく片長さ 0.4215384615384616 ほか）。`get` の既定値は先に評価されるので、「無ければ例外」は `when-not` で書く。標準の `shuffle` はシードを渡せず再現しない |
| 3 | 深さごとの正解率・深さ 2 の木の境界（0.2950・0.6500）・Tribuo の CART との全件一致まで、Java 版・Scala 版と同じになった。`max-key` は同値のとき**後ろ**を返すので（Scala の `maxBy`・Kotlin の `maxByOrNull` と逆）、最頻値・分割・葉のラベルは厳密な `>` で畳む。`frequencies` は順を保たないので、`distinct` の順でたどる |
| 4〜6 | cljfmt（違反で終了コード 1）・clj-kondo（警告で終了コード 2。`--fail-level error` にすると警告が出ても 0）・cloverage（`:extra-paths ["test"]` が無いと `FileNotFoundException`）。**cljfmt も clj-kondo も改行コードを検査しない**ので、`.gitattributes` の `apps/clojure/** text=auto eol=lf` が唯一の防御になる（Ruby 版の `Layout/EndOfLine` に当たる層が無い）。カバレッジは学習データの有無で 64.72%↔75.04%（forms）と動くので下限は設けない |
| 7 | `SLMTrainer(true)` と `LARSTrainer` の予測は自作の正規方程式と小数第 13 位まで一致し、`SLMTrainer(false)` は一致しない（Java 版・Scala 版と同じ癖）。架空データの予測値は **Java 版・Scala 版と 1 ビットも違わない**。`SparseLinearModel.getWeights` は正規化した空間の重みなので、中心化ノルムの比で自作の係数に戻す。`SLMTrainer` は学習のたびに `java.util.logging` に「Feature selected: …」を出す |
| 8 | Tribuo の CART にクラスの重みを渡す口が無いので、重み付きの決定木は自作が最終実装。自作と CART の相違件数（深さ 1〜10 で `0,0,0,0,2,0,0,0,1,0`）まで Java 版・Scala 版と一致した。学習済みパイプラインがマップ・ベクタ・キーワードだけなので、**EDN の `pr-str`／`read-string` で往復でき、値として `=` で比べられる**（Scala 版が必要とした自作のテキスト形式は要らない） |
| 9 | 決定係数 8 個と天気ごとの平均利用者数が Java 版・Scala 版と完全に一致。Tribuo の標準化は**不偏標準偏差**（n−1 で割る）。**`java.io.InputStreamReader` は文字コードを間違えても例外を投げず、置換文字に置き換えて読み進む**（Java 版・Scala 版の `Files.readAllLines` は `MalformedInputException` を投げる）。**Clojure のマップは 9 件目からハッシュマップになり挿入順を保たない**ので、列の順は `:columns` のベクタで持ち回る |
| 10 | 10 個の数値が Java 版と完全に一致。Tribuo のロジスティック回帰の既定は **5 エポック**で、500 エポックにすると自作と一致する。ランダムフォレストの `minChildWeight` の既定 5 が木の深さを抑えている（1.0 にすると訓練 1.0000・テスト 0.9333）。**乱数は `mapv` で即座に評価する**（`repeatedly` の遅延シーケンスと `java.util.Random` を混ぜると呼ぶ順が変わり、Java 版と並びが合わなくなる）。分類器は「学習して予測する関数を返す関数」で表し、`defprotocol` もアダプターも使わなかった |

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
| 機械学習 | Tribuo（Java の相互運用で呼ぶ） | 4.3.2 | Apache-2.0 | 第 3 章 |
| 直列化 | EDN（学習済みモデル）、Cheshire（API の JSON） | Cheshire 5.13 | EPL-1.0 / MIT | 第 8 章 |
| API | Ring と Jetty（`ring/ring-jetty-adapter`）。経路の振り分けはハンドラーの関数の中で書く | Ring 1.12 | EPL-1.0 | 第 15 章 |

- **`tech.ml.dataset` を使わない。** BOM を取り除いてくれる利点はあるが、データフレームのライブラリを使わないのはシリーズ全体の方針であり、依存に OpenBLAS のネイティブライブラリが入る。表は素のマップとベクタで表し、Smile に渡すところだけ `DataFrame` に変換する
- **機械学習は Tribuo にする。** Smile は Clojure から問題なく呼べたが、3.x のライセンスが GPL-3.0 で、記事のサンプルコードとして配るには合わない。Tribuo は Apache-2.0 で、Java 版・Kotlin 版・Scala 版が同じ 4.3.2 を使っている。**同じライブラリを同じ設定で呼ぶことが、この版の対比の軸である**
- **`scicloj.ml` も使わない。** Tribuo を直に呼べるので、包み直す層を増やす理由が無い
- **Nix の環境定義に clj-kondo と cljfmt を足す。** 検査に使う道具が環境に無いままでは、CI と手元で同じ検査ができない。環境定義を変えた経緯は第 5〜6 章の題材にする（Ruby 版の `RUBYLIB` と同じ扱い）
- **JDK 21 で動くことを前提にする。** `java` コマンドの版（25）とは違うので、記事の環境構築の節に書く
- **各章は「自作してから Tribuo と突き合わせる」構成にする。** Java 版・Scala 版と同じ流れ。数値が Java 版・Scala 版と一致するかを各章で確かめ、一致しない場合は理由を書く
- **Tribuo に無いものだけを自作の最終実装とする。** Java 版・Scala 版の ADR 005・007 に、章ごとの対応範囲が記録されている

### 章ごとのライブラリへの置き換え方針

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | `org.tribuo.classification.dtree.CARTClassificationTrainer`（`GiniIndex`） | 自作のジニ不純度の決定木と突き合わせる。深さの指定と、同じ不純度の分割候補の選び方の違いを見る |
| 7 | `org.tribuo.regression.slm` の線形回帰 | 正規方程式を自作してから、Smile の重回帰と突き合わせる |
| 8 | Tribuo の CART。前処理は自作 | 欠損値の補完・ダミー変数化・クラスの重みは Tribuo の対応範囲を確かめ、無ければ自作 |
| 9 | Tribuo の標準化 | 自作の標準化と突き合わせる（母標準偏差か標本標準偏差かを実測で確かめる） |
| 10 | Tribuo のロジスティック回帰・ランダムフォレスト | 自作と突き合わせる。正則化の既定値を確かめる |
| 11 | Tribuo の評価指標 | 自作の評価指標・ROC・交差検証と突き合わせる |
| 12 | Tribuo の正則化つき線形モデル | 自作のリッジ・ラッソと突き合わせる（目的関数の流儀の違いに注意する） |
| 13 | Tribuo の主成分分析（対応範囲を確かめる） | 自作の固有値分解と突き合わせる。Tribuo に無ければ自作が最終実装 |
| 14 | `org.tribuo.clustering.kmeans` の K-means | 自作の K-means と突き合わせる |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| Smile 3.1.1（機械学習） | POM のライセンスが GPL-3.0。LGPL-3.0 の 2.6.0 は古い。Scala 版（ADR 007）と同じ判断で採用しない |
| Leiningen（ビルド） | Clojure CLI の `deps.edn` のほうが依存の指定が素直で、別名でテスト・カバレッジを分けられる。Nix には両方ある |
| `tech.ml.dataset`・`tablecloth`（データフレーム） | シリーズの方針でデータフレームのライブラリを使わない。依存も大きい |
| `scicloj.ml`（機械学習の包み） | Tribuo を直に呼べる。包む層を増やすと Java 版・Scala 版との対比がぼやける |
| kaocha（テスト） | `clojure.test` と test-runner で足りる。ほかの言語版も標準のテストの仕組みを使っている |
| reitit・compojure（経路の振り分け） | 第 15 章の主題（層の分離と統合テスト）には Ring のハンドラーだけで足りる。Ring では「ハンドラー＝要求のマップを受け取り応答のマップを返す関数」であることを示したい |
| nippy（直列化） | EDN で学習済みモデルのマップをそのまま保存できる。読める形式のほうが記事の題材にしやすい |

## 影響

- 良い影響: Java 版・Kotlin 版・Scala 版と同じ Tribuo を同じ JDK で呼ぶので、数値が一致するかどうかを言語をまたいで確かめられる。乱数の並びも一致する（第 2 章の訓練データの平均値が Java 版・Scala 版と一致することを実測で確かめた）
- 良い影響: 表を素のマップとベクタで表すので、Clojure のデータ操作（`map`・`filter`・スレッディングマクロ）がそのまま記事の題材になる
- 悪い影響: `deps.edn` には lock ファイルが無いので、再現性は版を固定して書くことに頼る
- 悪い影響: 検査の道具（clj-kondo・cljfmt）が Nix 環境に無いため、環境定義に手を入れる必要がある
- 悪い影響: Tribuo の API は可変なオブジェクト（`MutableDataset`）と Java の配列が中心で、Clojure の不変のデータとの間で変換が要る。その変換自体は第 3 章以降の題材にする
