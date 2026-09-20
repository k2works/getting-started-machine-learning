---
type: ADR
title: "006 C# 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定"
description: "C# 版のライブラリに xUnit v3・coverlet.MTP・dotnet format・.NET アナライザー・ML.NET・ASP.NET Core Minimal API を採用し、章ごとの置き換え範囲を F# 版の ADR 004 を起点に決める。"
tags: [adr,getting-start-ml,csharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T02:10:00Z }
---

# 006 C# 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定

「機械学習から始めるプログラミング入門」C# 版で使うライブラリを決める。

日付: 2026-09-20

## ステータス

2026-09-20 提案されました

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) の「C# 版執筆計画」で、C# 版は F# 版の実装を対比の相手にし、データフレームのライブラリを使わず record と LINQ でデータを表すことを承認した。.NET SDK の版と機械学習のライブラリは F# 版（[ADR 004](004-fsharp-ml-libraries.md)）にそろえる。

B29 のステップ 1 で、NuGet のカタログと使い捨てのプロジェクトによって、次を確かめた。

| 確認したこと | 結果 | 確認方法 |
|------------|------|---------|
| ライセンス | Microsoft.ML 5.0.0・Microsoft.Data.Analysis 0.23.0・coverlet.MTP 10.0.1 は MIT、xunit.v3 4.0.1 は Apache-2.0 | NuGet のカタログ |
| Microsoft.Data.Analysis の保守 | 安定版の最新 0.23.0 は 2025-11-11 の公開で、以後はプレビュー（0.24.0-preview）だけ。1.0 に達していない | NuGet のカタログ |
| `global.json` の版 | `version` 10.0.100 + `rollForward: latestPatch` なら、手元（10.0.100）でも Nix（10.0.101）でも解決できる。F# 版の 10.0.101 固定は手元では解決できない | 使い捨てのプロジェクトで `dotnet --version` |
| アナライザーの水準 | `AnalysisMode: All` は公開メソッドの引数の null 検査（CA1062）まで求める。`Recommended` では CA1304・CA1311（カルチャの指定）・CA1822（static にできる）などが出る | 使い捨てのプロジェクトでビルド |
| 警告をエラーにする | `TreatWarningsAsErrors` により、コンパイラの警告（使っていない変数の CS0219 など）もエラーになる | 同上 |
| `dotnet format` | 対象のソリューションにプロジェクトが登録されていないと、何も検査せずに成功する。登録すると崩れた整形を `WHITESPACE` として指摘する | 同上 |
| `dotnet test` | `apps/csharp` を作業ディレクトリにして実行する。`global.json` の `test.runner`（Microsoft.Testing.Platform）は、作業ディレクトリから上に探した最も近い `global.json` から読まれるので、リポジトリのルートから実行すると古い VSTest の経路に落ちる。.NET 10 の `dotnet test` はパスを位置引数で受け取らず、`--solution`・`--project` を使う | 手元（10.0.100）と Nix（10.0.101）で実行 |
| 0 件と判定された件（B29） | 改名（`apps/dotnet` → `apps/csharp`）の後、古い状態を抱えた .NET のビルドサーバーが残っていると、`dotnet test` がテストを 0 件と判定して終了コード 5 で終わることがあった。`dotnet build-server shutdown` の後は、C# 版（15 件）も F# 版（313 件）も成功する | 手元で再現と解消を確認 |
| テストプロジェクトの設定（B29） | `OutputType` Exe・`IsTestProject`・`<Using Include="Xunit" />` が要る | 実行して確認 |
| 検査が効いているか（B29） | わざと違反を入れたファイルで、ビルドが CS0219（使っていない変数）・CA1822（static にできる）・IDE0055（コードスタイル）をエラーにし、`dotnet format --verify-no-changes` が `WHITESPACE` を指摘した |
| カバレッジ（B29） | `--coverlet --coverlet-include '[MachineLearning]*' --coverlet-output-format cobertura` で cobertura の XML が出る（F# 版の CI と同じオプション） |
| BOM の扱い（B29） | .NET の `File.ReadAllLines`・`ReadAllText` は BOM 付きの UTF-8 を読むと BOM を取り除く（先頭セルは `身長`）。Python 版・Kotlin 版・Java 版で起きた「列名に BOM が残る」落とし穴は C# では起きないので、列名から BOM を消す処理は書かない |
| 分割の一致（B30） | 分割を F# 版と同じ `System.Random(seed)` + Fisher-Yates（後ろから `Next(i + 1)` で交換）にすると、訓練データとテストデータに入る行が F# 版と一致する。iris.csv・テスト 0.3・シード 0 で、訓練データの平均値（がく片長さ 0.424808、がく片幅 0.462286、花弁長さ 0.479135、花弁幅 0.432404）とテストデータの先頭 5 件のラベルが一致した。Java 版は `Collections.shuffle` なので一致しない | C# 版と、F# 版の手順を写したスクリプトの両方で実測 |
| 第 3 章（B30） | ML.NET に単一の決定木（CART）の学習器が無いので、F# 版（ADR 004）と同じく木を 1 本だけ作る `FastTree` を `OneVersusAll` で多クラスにして突き合わせる。C# では ML.NET が求める「引数なしのコンストラクターと書き換えられるプロパティを持つクラス」をそのまま書けるので、F# 版の `[<CLIMutable>]` に当たる工夫が要らない。特徴量の数は実行時に決まるので `SchemaDefinition` でベクトルの長さを指定する点は同じ。iris.csv・テスト 0.3・シード 0 で、深さごとの正解率・ML.NET との一致数・深さ 2 の木の境界が F# 版とすべて一致した |
| `switch` 式の網羅性（B30） | C# には sealed interface が無く、抽象レコードと sealed な派生で閉じても、`switch` 式から `_` の分岐を外すと CS8509（網羅されていない）の警告になり、`TreatWarningsAsErrors` でビルドが止まる。F# の判別共用体や Java の sealed interface のように網羅を証明できないので、最後の分岐を書く |
| 値による比較（B30） | `Equals` を書いて `GetHashCode` を書かないと CS0659 でビルドが止まる（警告をエラーにしているため）。Java 版で Error Prone が止めた「record の成分に配列」に当たるルールは .NET アナライザー（`Recommended`）に無いので、値で比べられることをテストで担保する。`Row`・`Table` も record だが成分が辞書・リストなので参照で比べる。値で比べる必要があるのは `Features` だけ |
| 文字列の分割（B30） | `string.Split(char)` は行末の空欄も保持する（`"0.1,0.2,0.3,,".Split(',')` は 5 要素）。Java の `split(",", -1)` のような上限の指定は要らない |
| 第 9 章（B32） | Shift_JIS は `Encoding.RegisterProvider(CodePagesEncodingProvider.Instance)` だけで読める（`System.Text.Encoding.CodePages` の追加は不要。`InvariantGlobalization: true` の影響も受けない）。Java の `MalformedInputException` と違い、.NET は文字コードが違っても例外を投げず黙って文字化けする。ML.NET の `NormalizeMeanVariance` は既定（`fixZero: true`）では平均を引かず、`fixZero: false` で自作の標準化（母標準偏差）と小数第 5 位まで一致する（ADR 004 と同じ）。コレクション式のスプレッド `..` は `[...]` の中でだけ使える |
| 第 7 章（B32） | `Ols` は Intel MKL が x64 専用なので arm64 では使えない（ADR 004 と同じ）。代わりに ML.NET の SDCA を使う。SDCA は特徴量の大きさに敏感で、正規化しないと決定係数が -0.5124 まで壊れ、`NormalizeMeanVariance` を前に置くと 0.7659 に戻る。既定はマルチスレッドなので `MLContext(seed: 0)` だけでは再現せず、`SdcaRegressionTrainer.Options.NumberOfThreads = 1` で固定する。自作の正規方程式の切片・係数・評価指標は F# 版と表示の桁まで一致した（SDCA は反復解法なので決定係数だけわずかに違う）。決め打ちの値を返す仮実装は CA1822（static にできる）でビルドが止まるので、C# 版の TDD では仮実装の書き方に制約がある |
| 第 8 章（B32） | System.Text.Json の `[JsonPolymorphic]`・`[JsonDerivedType]` で、インターフェースと抽象レコードをそのまま保存・復元できる（F# 版の DTO への詰め替えも、Java 版の `ObjectInputFilter` に当たる対策も要らない）。読み込みを厳しくするには `RespectRequiredConstructorParameters = true`。ML.NET の 2 値分類は `FastTree(exampleWeightColumnName: "Weight", ...)` でクラスの重みを行の重みとして表せ、C# では `[<CLIMutable>]` に当たる橋渡しが要らない。`Microsoft.ML.ITransformer` と章の `ITransformer` が重なるので別名が要る。自作の数値は深さ 1〜10 で F# 版と完全一致し、ML.NET との一致件数だけ深さ 8〜10 で 1 件違う |
| 第 10 章（B32） | `IEstimator<out TTransformer>` は共変なので、F# 版に必要だったアップキャストが要らない。`FastForest` の既定 `minimumExampleCountPerLeaf = 10` は訓練データを分け切らない（1 にすると分け切る）。L-BFGS の L1・L2 の既定値 1 を 0 にすると、自作の勾配降下法と正解率が完全に一致する（ADR 004 と同じ）。同じ乱数・同じ分割でも特徴量の並び順が森の結果を変えるので、言語間の突き合わせでは列の順もそろえる（F# 版は `Map` のキー順、C# 版は CSV の列順）。アナライザーの CA1859 が private メソッドの戻り値を具体的な型にするよう求める |
| 第 15 章（B33） | 予測 API は追加のパッケージが要らない。`<FrameworkReference Include="Microsoft.AspNetCore.App" />`（Minimal API）と、テスト用の `Microsoft.AspNetCore.TestHost` で足りる（F# 版の Giraffe に当たるものは不要）。F# の `Result` に当たる型が無いので `PredictionResult<T>` を抽象レコードと sealed な派生で自作し、`Match` の最後の分岐は到達しないが C# では必要。実データで学習したモデルの予測（興行収入 7883.711445215134）は F# 版と小数点以下まで一致した |

ML.NET を C# から使うときの癖は、F# 版で確かめた ADR 004 を起点にし、C# 版の各章で確かめて本 ADR に書き足す。

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・実行環境 | C#（`net10.0`）。`global.json` は 10.0.100 + `rollForward: latestPatch` | .NET SDK 10.0.1xx | — | 第 1 章 |
| テスト | xUnit v3（Microsoft.Testing.Platform で実行） | 4.0.1 | Apache-2.0 | 第 1 章 |
| カバレッジ | coverlet.MTP | 10.0.1 | MIT | 第 1 章（記事での解説は第 5 章） |
| 整形 | `dotnet format`（SDK 同梱）と `.editorconfig` | SDK と同じ | — | 第 1 章（記事での解説は第 5 章） |
| 静的解析 | .NET アナライザー（`AnalysisMode: Recommended`）、`TreatWarningsAsErrors`、`EnforceCodeStyleInBuild` | SDK と同じ | — | 第 1 章（記事での解説は第 5 章） |
| データの表現 | record のリストと LINQ（データフレームのライブラリを使わない） | — | — | 第 1 章 |
| 機械学習 | ML.NET（Microsoft.ML・Microsoft.ML.FastTree） | 5.0.0 | MIT | 第 3 章 |
| API | ASP.NET Core Minimal API と統合テスト（`Microsoft.AspNetCore.Mvc.Testing` か `TestHost`） | SDK と同じ | — | 第 15 章 |

- 依存の版は F# 版と同じく中央パッケージ管理（`Directory.Packages.props`）にまとめ、`packages.lock.json` で固定する
- 整形・静的解析・カバレッジは B29 から検査に組み込む。Java 版・F# 版の教訓から、わざと違反を入れて検査が失敗することを確かめる
- 機械学習と API のライブラリは、使う章に入ってから `Directory.Packages.props` に追加する

### 章ごとのライブラリへの置き換え方針

ADR 004（F# 版）の方針をそのまま使う。C# 版の各章で実装しながら確かめ、F# 版と違う結果になったときは本 ADR に書き足す。ML.NET は C# 向けの API なので、F# 版で必要だった型の橋渡しの一部は不要になる見込み。

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | ML.NET の決定木系（`FastTree` を `OneVersusAll` で多クラスにする） | 自作の決定木と予測を突き合わせる |
| 7 | ML.NET・自作の正規方程式 | 係数と決定係数を突き合わせる |
| 8 | ML.NET | クラスの重み付けの可否は第 8 章で確かめる |
| 9 | ML.NET の正規化 | 自作の標準化と突き合わせる |
| 10 | ML.NET のロジスティック回帰（SDCA）・FastForest | 正解率を比べる |
| 11 | ML.NET の評価・交差検証 | 自作の評価指標と突き合わせる |
| 12 | ML.NET の正則化（SDCA の L2） | ADR 004 の「L2 の尺度」を C# で確かめる |
| 13 | なし | FSharp.Stats を使わないので、固有値分解を含めて自作を最終実装とする |
| 14 | ML.NET の K-means | 初期中心を渡せるかを第 14 章で確かめる |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| Microsoft.Data.Analysis の `DataFrame` | 安定版が 0.23.0 で 1.0 に達しておらず、2025-11 以降はプレビューだけ。Java 版と同じく record と LINQ で表す |
| `AnalysisMode: All` | 公開メソッドの引数の null 検査（CA1062）まで求められ、記事に載せるコードが本題から離れる |
| StyleCop.Analyzers | 安定版が 1.1.118 と古く、1.2.0 はベータのまま。整形は `dotnet format` と `.editorconfig` で足りる |
| Roslynator・SonarAnalyzer.CSharp | SDK 同梱のアナライザーで足りるかを先に確かめる。必要になった章で再検討する |
| NUnit・MSTest | F# 版と同じ xUnit v3 にそろえる |
| FSharp.Stats（数値計算） | F# 向けの API で、C# から使う題材にならない。行列は自作する |

## 影響

- 良い影響: F# 版と同じ .NET・ML.NET・xUnit を使うので、記事の違いが言語の書き方の違いに絞られる
- 良い影響: `global.json` を 10.0.100 + `latestPatch` にしたので、手元でも Nix でも同じ手順で動かせる
- 悪い影響: データフレームを使わないので、第 2・8・9 章の前処理は記述が長くなる。その長さ自体を対比の題材にする
- 悪い影響: 第 13 章は FSharp.Stats を使わないため、F# 版より自作の範囲が広い

## コンプライアンス

- `apps/csharp/Directory.Packages.props` に上記のライブラリと版だけが記載されている
- C# CI（`.github/workflows/csharp-ci.yml`）がグリーンである
- わざと違反を入れると整形・静的解析の検査が失敗する（B29 で確かめる）

## 備考

- 著者: claude-code/claude-opus-5
