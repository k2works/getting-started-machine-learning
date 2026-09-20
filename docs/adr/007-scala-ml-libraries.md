---
type: ADR
title: "007 Scala 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定"
description: "Scala 版のライブラリに sbt・ScalaTest・scalafmt・scoverage・Tribuo・http4s + circe を採用し、GPL-3.0 の Smile を使わない理由と章ごとの置き換え範囲を決める。"
tags: [adr,getting-start-ml,scala]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T02:20:00Z }
---

# 007 Scala 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定

「機械学習から始めるプログラミング入門」Scala 版で使うライブラリを決める。

日付: 2026-09-20

## ステータス

2026-09-20 提案されました

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) の「対象言語」では、Scala 版の機械学習ライブラリの候補を Smile としていた。しかし Smile は版によってライセンスが違い、採用前に確認することになっていた。

B34 のステップ 1 で、Maven Central の POM と使い捨ての sbt プロジェクトによって、次を確かめた。

| 確認したこと | 結果 | 確認方法 |
|------------|------|---------|
| Smile のライセンス | Scala 3 向けの `smile-scala_3` は 3.0.2 以降しか無く、3.0.2・4.x・5.x・6.x のいずれも GPL-3.0。LGPL-3.0 の 2.6.0 には Scala 3 版が無い | Maven Central の POM |
| Tribuo | 4.3.2、Apache License 2.0。Kotlin 版（[ADR 002](002-kotlin-ml-libraries.md)）・Java 版（[ADR 005](005-java-ml-libraries.md)）で各アルゴリズムの癖を確認済み | Maven Central |
| Tribuo を Scala から呼べるか | `nix develop .#scala` の sbt 1.12.0・Scala 3.3.6 で、`ArrayExample[Label](Label("setosa"), Array("a", "b"), Array(0.1, 0.2))` が動いた（型引数を明示する）。Java の可変な API をそのまま呼べる | 使い捨ての sbt プロジェクト |
| ライセンス（そのほか） | ScalaTest 3.2.20・http4s 0.23.37・circe 0.14.16・sbt-scoverage 2.4.4 はいずれも Apache License 2.0 | Maven Central の POM |
| 安定版 | ScalaTest は 3.2.20（3.3.0 はマイルストーン版のみ）、http4s は 0.23.37（1.0.0 はマイルストーン版のみ）、circe は 0.14.16、sbt-scalafmt は 2.6.2 | Maven Central |
| Nix 環境 | Scala 3.3.6（LTS）、sbt 1.12.0、metals 1.6.4、scala-cli 1.11.0 | `nix eval` |
| 警告をエラーにする | `-Wunused:all -Wvalue-discard -Xfatal-warnings` で、使っていない import（E198）と使っていない値がエラーになる | 使い捨ての sbt プロジェクト |
| 整形とカバレッジ | `scalafmtCheckAll` は崩れた整形でエラーになる（`.scalafmt.conf` に `version` と `runner.dialect = scala3` が要る）。`coverage` → `test` → `coverageReport` でカバレッジが出る | 使い捨てのプロジェクトと `apps/scala` |
| scalafmt の設定（B35） | `.scalafmt.conf` は `version` と `runner.dialect` の両方が必要（どちらを欠いてもエラー）。`-Wvalue-discard` は戻り値の型が `Unit` の定義の中でだけ働き、文の位置で値を捨てた場合は `-Wnonunit-statement` を足さないと止まらない。本シリーズでは `-Wnonunit-statement` は使わず、意図して捨てるときは `val _ =` と書く |
| sbt の版（B34） | sbt-scalafmt 2.6.2 は sbt 1.12.9 以上を求め、Nix の sbt 1.12.0 では「requires sbt 1.12.9+」で失敗する。`project/build.properties` に `sbt.version=1.12.9` と書くと、Nix の sbt のランチャーがその版を取得して動く | `apps/scala` で実行 |
| 標準ライブラリの版の表示 | Scala 3 でも `scala.util.Properties.versionNumberString` は 2.13.16 を返す（Scala 3 は 2.13 の標準ライブラリを使う） | 同上 |

### 各章で確かめた結果

| 章 | 確かめたこと |
|----|------------|
| 1 | `-Wvalue-discard` は「戻り値の型が `Unit` の定義の中で、非 Unit の値を捨てたとき」に働く。テストの本体で `assume(...)` を書くだけでは働かないが、`Unit` を返すヘルパーの中では働く。`assume` を使うヘルパーは `Assertion` を返し、呼び出し側で `: Unit` と書くのがこの設定での定型。`-Wunused:all` は使っていない import・private の値を捕まえるが、公開メソッドの使っていない引数は指摘しない（Java 版の Error Prone の `UnusedMethod` のようには仮実装を止めない） |
| 2 | 分割を Java 版と同じ `java.util.Random` + Fisher-Yates にすると、並べ替えの結果が Java 版と一致する（シード 0 で `[4, 8, 9, 6, 3, 5, 2, 1, 7, 0]`）。iris.csv の訓練データの平均値（がく片長さ 0.4215384615384616 など）とテストデータの先頭のラベルも Java 版と一致した。`Features` の値を `Vector` で持つと、case class の等価判定がそのまま値の比較になる（Java・C# は配列を包む工夫が要る） |
| 3 | Tribuo の `ArrayExample[Label]` は型引数を明示すれば Scala からそのまま作れる。`MutableDataset` は `ListDataSource` に `asJava` した事例のリストを渡す。決定木は Scala 3 の `enum` で閉じ、`match` の網羅性はコンパイラが検査する（Java の sealed interface・C# の抽象レコードに当たる）。iris.csv・テスト 0.3・シード 0 で、深さごとの正解率と Tribuo との一致（どの深さでも全件一致）が Java 版と同じになった |
| 7 | 回帰には `tribuo-regression-slm` の追加が要る（`tribuo-regression-core`・`tribuo-math` は推移的に入る）。`SLMTrainer(true)`・`LARSTrainer()` の予測は自作の正規方程式と 1e-9 以内で一致し、`SLMTrainer(false)`・`LinearSGDTrainer` は一致しない（ADR 002・005 と同じ）。`SparseLinearModel` の重みは正規化した空間の値で、`getFeatureIDMap.getID(name)` で引く。`SLMTrainer` は学習のたびに標準出力へ進捗を書く。Scala 固有として、重みを見るには `asInstanceOf[SparseLinearModel]` が要り、複数のトレーナーを 1 つの `Vector` に入れるときは型を明示する。自作の予測値は Java 版と最後の桁まで一致した（Kotlin 版とは 1 桁違う） |
| 8 | Java のシリアライズは Scala では使えない。`ObjectInputFilter` を設定すると JDK のフィールドの型検査が走り、Scala の不変コレクションの `DefaultSerializationProxy` が解決される前に `Vector` 型のフィールドへ代入されて `ClassCastException` になる（フィルタ無しなら読める）。そこで Scala 版はモデルをタブ区切りのテキストで保存し、版を `match` で検証して任意のクラスを復元しない形にした。Scala 3 のラムダは `Serializable` だが、クラス名が `X$$$Lambda/0x...` と不安定で許可リストに書けない。Tribuo の CART との不一致は第 3 章と同じ「同じ不純度の分割候補の選び方」由来で、前処理を挟んでも変わらない |
| 9 | `MeanStdDevTransformation` は不偏標準偏差を使い、自作との比は √((n−1)/n)（ADR 002・005 と値まで同じ）。Java 向けの API は Scala からそのまま呼べ、橋渡しは `toArray`・`java.util.Optional` の `.toScala`・`.toVector` の 3 か所だけ。Shift_JIS のファイルを UTF-8 として読むと、JVM なので Java 版と同じく `MalformedInputException` になる。`tribuo-math` と `org.tribuo.transform` は `tribuo-classification-tree` から推移的に入る |
| 10 | 特徴量の重要度は「木ごとに正規化してから平均し、最後にもう一度正規化する」手順にそろえると Java 版と一致する。最初に「全体を合わせてから正規化」と書いたときは値がずれた |

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・ビルド | Scala（LTS）、sbt（`project/build.properties` で指定） | 3.3.6、1.12.9 | Apache License 2.0 | 第 1 章 |
| テスト | ScalaTest | 3.2.20 | Apache License 2.0 | 第 1 章 |
| 整形 | scalafmt（sbt-scalafmt） | 2.6.2 | Apache License 2.0 | 第 1 章（記事での解説は第 5 章） |
| 静的解析 | コンパイラの警告（`-Wunused:all`・`-Wvalue-discard` など）を `-Xfatal-warnings` でエラーにする | Scala と同じ | — | 第 1 章（記事での解説は第 5 章） |
| カバレッジ | sbt-scoverage | 2.4.4 | Apache License 2.0 | 第 1 章（記事での解説は第 5 章） |
| データの表現 | case class と不変のコレクション（`Map`・`Vector`）。データフレームのライブラリを使わない | — | — | 第 1 章 |
| 乱数 | `java.util.Random(seed)` と Fisher-Yates | JDK と同じ | — | 第 2 章 |
| 機械学習 | Tribuo | 4.3.2 | Apache License 2.0 | 第 3 章 |
| 行列 | 自作の小さな不変の型 | — | — | 第 7 章 |
| API | http4s + circe | 0.23.37、0.14.16 | Apache License 2.0 | 第 15 章 |

- **Smile は使わない。** Scala 3 で使える版がすべて GPL-3.0 で、PUBLIC リポジトリで配るサンプルコードの利用条件に影響する（Kotlin 版の ADR 002 と同じ判断）。代わりに Kotlin 版・Java 版と同じ Tribuo を Scala から使い、Java 向けの API を Scala の不変なコレクションへ橋渡しすることを題材にする
- 依存の版は `build.sbt` にまとめる。ライブラリは使う章に入ってから追加する
- 整形・静的解析・カバレッジは B34 から検査に組み込む。Java 版・C# 版の教訓から、わざと違反を入れて検査が失敗することを確かめる

### 章ごとのライブラリへの置き換え方針

ADR 002（Kotlin 版）の方針をそのまま使う。Tribuo は同じ 4.3.2 なので、アルゴリズムの有無と癖は同じはず。Scala 版の各章で確かめ、違う結果になったときは本 ADR に書き足す。

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | Tribuo の CART（ジニ不純度） | 自作の決定木と予測を突き合わせる |
| 7 | Tribuo の `SLMTrainer(true)`・`LARSTrainer` | 係数と決定係数を突き合わせる |
| 8 | Tribuo の決定木 | クラスの重み付けは自作の木で示す |
| 9 | Tribuo の `MeanStdDevTransformation` | 不偏標準偏差の違いを踏まえて突き合わせる |
| 10 | Tribuo の `LogisticRegressionTrainer`・`RandomForestTrainer` | 正解率を比べる |
| 11 | Tribuo の評価器・`KFoldSplitter` | 自作の評価指標と突き合わせる |
| 12 | Tribuo の `ElasticNetCDTrainer` | ラッソ回帰とリッジ回帰を突き合わせる |
| 13 | なし | PCA のモジュールが無いので、Tribuo の固有値分解を使った自作を最終実装とする |
| 14 | Tribuo の `KMeansTrainer` | 初期中心を渡せないので、SSE の大きさを比べる |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| Smile（`smile-scala_3`） | Scala 3 で使える版がすべて GPL-3.0 |
| Smile 2.6.0（LGPL-3.0） | Scala 3 向けの成果物が無い |
| Spark MLlib | クラスタ計算の仕組みごと持ち込むことになり、本シリーズの題材に対して大きすぎる |
| Breeze 2.1.0（行列） | 正規方程式や固有値分解の仕組みを見せるため、行列は自作する。必要になった章で再検討する |
| MUnit | 「対象言語」の表のテスト基盤は ScalaTest。ほかの言語版と同じく、その言語で標準的なものを選ぶ |
| scalafix・WartRemover | コンパイラの警告をエラーにする設定で足りるかを先に確かめる |
| Play Framework（API） | 第 15 章の主題（層の分離と統合テスト）に対して依存が大きい |

## 影響

- 良い影響: すべてのライブラリが Apache License 2.0 で、サンプルコードの利用条件が単純になる
- 良い影響: Kotlin 版・Java 版と同じ Tribuo を使うので、JVM の 3 言語で同じライブラリの使い方を比べられる
- 悪い影響: Scala で広く使われる Smile を使わないので、「Scala の機械学習ライブラリ」の紹介としては物足りない。その理由（ライセンス）を第 3 章と本 ADR に明記する
- 悪い影響: Tribuo は Java 向けの API なので、Scala の不変なコレクションとの橋渡しが各章で要る

## コンプライアンス

- `apps/scala/build.sbt` に上記のライブラリと版だけが記載されている
- Scala CI（`.github/workflows/scala-ci.yml`）がグリーンである
- わざと違反を入れると整形・静的解析の検査が失敗する（B34 で確かめる）

## 備考

- 著者: claude-code/claude-opus-5
