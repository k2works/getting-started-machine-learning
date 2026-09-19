---
type: ADR
title: "004 F# 版の機械学習・データ・可視化・API ライブラリの選定"
description: "F# 版のライブラリに .NET SDK 10・xUnit v3・ML.NET・FSharp.Stats・FSharp.Data・Polyglot Notebooks + Plotly.NET・Giraffe を採用し、Polyglot Notebooks の廃止への対応と章ごとの置き換え範囲を決める。"
tags: [adr,getting-start-ml,fsharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-18T04:54:55Z }
---

# 004 F# 版の機械学習・データ・可視化・API ライブラリの選定

「機械学習から始めるプログラミング入門」F# 版で使うライブラリとツールを決める。

日付: 2026-09-18

## ステータス

2026-09-18 提案されました

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) の「F# 版執筆計画」で、F# を第 1 波に移すこと、Notebook に Polyglot Notebooks を使い廃止のリスクを明記すること、ライブラリの第一候補を ML.NET・FSharp.Stats・FSharp.Data・Plotly.NET・Giraffe とすることを承認した。ただし、各ライブラリが F# からどこまで使えるか、各章の「ライブラリへの置き換え」に使えるか、廃止された .NET Interactive が .NET SDK 10 で動くかは未検証だった。

そこで B18 で、NuGet の情報の確認と、使い捨てのプロジェクト（.NET SDK 10.0.101、`net10.0`）・Notebook での実行、`apps/fsharp/` の雛形と CI での実行によって、次を確かめた。

| 確認したこと | 結果 | 確認方法 |
|------------|------|---------|
| .NET SDK | 手元は 10.0.101。本リポジトリの `flake.lock` が固定する nixpkgs では、`dotnet-sdk` は 8.0 を指し、`dotnet-sdk_10` が 10.0.101 | nixpkgs のソース、CI |
| テストの実行 | .NET SDK 10 の `dotnet test` は、VSTest で動くテストプロジェクトを `Testing with VSTest target is no longer supported by Microsoft.Testing.Platform on .NET 10 SDK and later` で止める。`global.json` の `test.runner` に `Microsoft.Testing.Platform` を指定し、xUnit v3 を Microsoft.Testing.Platform で動かす | 実行 |
| 中央パッケージ管理と FSharp.Core | `Directory.Packages.props` で版を管理すると、FSharp.Core の暗黙の参照が効かず、テストが `Could not load file or assembly 'FSharp.Core, Version=10.0.0.0'` で失敗した。暗黙の参照を無効にし、`FSharp.Core` 10.0.101 を明示して参照する | 実行 |
| .NET SDK 同梱の FSharp.Core | .NET SDK は同梱の FSharp.Core（`library-packs`）をパッケージソースに加える。Nix の SDK に同梱された版は `packages.lock.json` のハッシュと一致せず、CI の `dotnet restore --locked-mode` が `NU1403` で失敗した。`DisableImplicitLibraryPacksFolder` で無効にし、nuget.org からだけ取る | CI |
| カバレッジ | coverlet.MTP 10.0.1（MIT）で取れる。既定では FSharp.Core まで計測しようとし、Windows では実行中のファイルを書き換えられずに計測全体が失敗した（診断ログで確認）。`--coverlet-include "[MachineLearning]*"` で本体だけに絞る | 実行 |
| 整形・静的解析 | Fantomas 8.0.0 の既定の書式はレコードの波かっこを別の行に置く。Windows では CRLF で書き出すので、`.editorconfig` で LF にそろえる。FSharpLint 0.27.0 は警告があると 0 以外の終了コードを返し、`obj/` の自動生成ファイル（xUnit のエントリポイント）にも警告を出すので、`fsharplint.json` の `ignoreFiles` で除く | 実行 |
| ML.NET 5.0.0（MIT） | 1 本だけの FastTree（`numberOfTrees = 1`）、`OneVersusAll`、ソフトマックスのロジスティック回帰（`LbfgsMaximumEntropy`）、最小二乗（`Ols`）、K-means、モデルの保存と読み込み（zip）が F# から動いた。単一の決定木（CART）の学習器は無い。K-means の初期値は `InitializationAlgorithm`（k-means++・ランダム・Yinyang）から選ぶだけで、初期中心は渡せない。`NormalizeMeanVariance` の既定（`fixZero`）は平均を引かず、変換後の平均が 0 にならなかった。学習器は `IDataView` と可変なクラス（`[<CLIMutable>]` のレコード）でデータを受け取る | 実行 |
| ML.NET を F# から組み立てる | `ml.Transforms.Concatenate(...).Append(...)` は、C# では書けるが F# では `FS0193: 型の制約が一致しません` になる（`Append` の拡張メソッドが `IEstimator<ITransformer>` に対して定義されているため）。`EstimatorChain<ITransformer>().Append(...).Append(...)` と書けば組み立てられる | 実行 |
| FSharp.Stats 0.6.0（Apache-2.0） | `Fitting.LinearRegression.fit` で重回帰の係数を求められ、ML.NET の `Ols` と一致した。リッジ回帰（`RidgeRegression`）・PCA（`PCA.compute`、寄与率は `VarExplainedByComponentIndividual`）がある。K-means（`IterativeClustering.kmeans`）は初期中心を返す関数を引数に取るので、初期中心を渡せる | 実行・リフレクション |
| FSharp.Data 8.2.0（Apache-2.0） | `CsvProvider` は架空の値のサンプル文字列から型を作れ、日本語の列名がそのままプロパティ名になった。空欄の数値は `nan` になり、小数を含む列の型は `decimal` と推論された | 実行 |
| Polyglot Notebooks | dotnet ツール `Microsoft.dotnet-interactive` 1.0.712001 は `net10.0` 向けで、.NET 10 の実行環境だけで動いた。`dotnet interactive jupyter install --path` でカーネルの定義を指定したディレクトリに入れ、Jupyter の `nbconvert --execute` で F# の Notebook を画面なしで実行できた。Plotly.NET 5.1.0 と Plotly.NET.Interactive 5.0.0（MIT）でグラフが `text/html` の出力になった。カーネルの F# は 9.0（`language_info`）で、プロジェクトの F# 10 と違う | 実行 |
| Notebook の出力の除去 | Python 版で使っている nbstripout で、Polyglot Notebooks の形式（`polyglot_notebook` のメタデータ）を保ったまま出力を消せた | 実行 |
| ML.NET の最小二乗の依存 | `Ols` は Microsoft.ML.Mkl.Components（MIT）が必要で、それが依存する Microsoft.ML.Mkl.Redist 5.0.0 は Intel MKL のネイティブライブラリを Intel Simplified Software License で配布する。含まれるのは x64 の Windows・Linux・macOS 向けだけで、arm64 向けは無い | NuGet のパッケージの中身 |
| ライセンス | Giraffe 8.3.0・Fantomas 8.0.0・FSharp.Stats 0.6.0・FSharp.Data 8.2.0・xunit.v3 4.0.1 は Apache-2.0、Deedle 8.1.0 は BSD-2-Clause、FSharpLint 0.27.0・Plotly.NET・`Microsoft.dotnet-interactive` は MIT | NuGet・GitHub |

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・実行環境 | F#（.NET SDK、`net10.0`、警告をエラーにする） | SDK 10.0.101（`global.json`）、FSharp.Core 10.0.101 | MIT | 第 1 章 |
| テスト | xUnit v3（Microsoft.Testing.Platform で実行） | 4.0.1 | Apache-2.0 | 第 1 章 |
| カバレッジ | coverlet.MTP | 10.0.1 | MIT | 第 1 章（詳細は第 5 章） |
| 整形・静的解析 | Fantomas、FSharpLint（ローカルツール） | 8.0.0、0.27.0 | Apache-2.0、MIT | 第 1 章（詳細は第 5 章） |
| CSV | FSharp.Data の `CsvProvider` | 8.2.0 | Apache-2.0 | 第 2 章 |
| 機械学習 | ML.NET（Microsoft.ML・Microsoft.ML.FastTree） | 5.0.0 | MIT | 第 3 章以降 |
| 数値計算 | FSharp.Stats | 0.6.0 | Apache-2.0 | 第 7 章 |
| 可視化 | Polyglot Notebooks（`Microsoft.dotnet-interactive`）、Plotly.NET、Plotly.NET.Interactive | 1.0.712001、5.1.0、5.0.0 | MIT | 第 2 章 |
| API | Giraffe | 8.3.0 | Apache-2.0 | 第 15 章 |

ライブラリは使う章に入ってから `apps/fsharp/Directory.Packages.props` に正確な版で追加し、`packages.lock.json` をコミットする。Deedle のデータフレームは使わず、レコード型のリストで表す。

### Polyglot Notebooks の扱い

Polyglot Notebooks と .NET Interactive は 2026 年に廃止され、リポジトリはアーカイブされた（[dotnet/interactive#4163](https://github.com/dotnet/interactive/issues/4163)）。上の表の版に固定し、F# 版の記事に廃止されていることを明記する。Notebook は探索と可視化だけに使い、実装・テスト・記事の数値は `apps/fsharp/` のプロジェクトから求める。将来の VS Code・.NET SDK の更新で動かなくなったら、F# スクリプト（`.fsx`）と Plotly.NET の HTML 出力に移すかを判断する。

### 章ごとのライブラリへの置き換え方針

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | ML.NET の FastTree（1 本） | 単一の決定木の学習器が無いので、勾配ブースティングの 1 本目の木（正解ラベルへの回帰木）と自作の決定木を比べる。多クラスは `OneVersusAll` で組む。特徴量を区間に分けてから分割するので、境界の値は自作と一致しない前提で、予測を突き合わせる |
| 7 | FSharp.Stats の `LinearRegression.fit` | 係数と決定係数を突き合わせる。ML.NET の `Ols` は x64 専用のネイティブライブラリ（Intel MKL）に依存するので使わない |
| 8 | ML.NET | クラスの重みは `ExampleWeightColumnName`（行の重み）で表せるかを確かめる。モデルは zip で保存する |
| 9 | ML.NET の正規化 | `NormalizeMeanVariance` の既定が平均を引かないことを学習用テストで確かめてから、自作の標準化と突き合わせる |
| 10 | ML.NET の `LbfgsMaximumEntropy`・FastForest | ソフトマックスの自作と正解率を比べる。FastForest は 2 クラス用なので、多クラスは `OneVersusAll` で組む |
| 11 | ML.NET の評価・交差検証 | 自作の評価指標・K 分割と突き合わせる |
| 12 | FSharp.Stats の `RidgeRegression`、ML.NET の正則化 | 自作のリッジ回帰と係数を突き合わせる。罰則の尺度の違いを確かめる |
| 13 | FSharp.Stats の `PCA.compute` | 寄与率と主成分を突き合わせる |
| 14 | FSharp.Stats の `IterativeClustering.kmeans` | 同じ初期中心を渡して結果を突き合わせる。ML.NET の K-means は初期中心を渡せないので、比べるだけにする |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| xUnit 2 系（参照実装） | .NET SDK 10 の `dotnet test` では VSTest の実行が止められる。xUnit v3 は Microsoft.Testing.Platform で動く |
| coverlet.collector | VSTest 用のデータコレクターで、Microsoft.Testing.Platform では使えない |
| Deedle | F# 版ではレコード型のリストと型プロバイダで表し、データフレームの API を覚える負担を増やさない |
| Microsoft.ML.Mkl.Components（ML.NET の `Ols`） | Intel MKL を Intel Simplified Software License で配布し、arm64 では動かない。最小二乗は FSharp.Stats で足りる |
| Microsoft.Testing.Extensions.CodeCoverage | Microsoft のソフトウェアライセンス条項で配布され、オープンソースのライセンスではない |
| F# スクリプトと Plotly.NET の HTML 出力（可視化） | 承認した方針で Polyglot Notebooks を使う。Notebook が動かなくなったときの移行先として残す |

## 影響

- 良い影響: 実装・テスト・CI が `dotnet` CLI だけで完結し、Nix と手元で同じ SDK（10.0.101）を使う
- 良い影響: FSharp.Stats の K-means は初期中心を渡せるので、第 14 章で自作と完全に突き合わせられる
- 悪い影響: Polyglot Notebooks は廃止されており、将来の更新で Notebook が動かなくなる可能性がある
- 悪い影響: ML.NET は C# 向けの API で、F# からは型の制約（`Append`）や可変なクラスの扱いに工夫が要る。単一の決定木が無いので、第 3 章の突き合わせは近似になる
- 悪い影響: 中央パッケージ管理・Microsoft.Testing.Platform・coverlet の組み合わせで、既定の設定のままでは動かない箇所がある（FSharp.Core の参照、同梱パッケージのハッシュ、カバレッジの対象）

## コンプライアンス

- `apps/fsharp/Directory.Packages.props` と `.config/dotnet-tools.json` に上記のライブラリとツールの版だけが記載されている
- F# CI（`.github/workflows/fsharp-ci.yml`）がグリーンである
- F# 版の記事に Polyglot Notebooks が廃止されていることが書かれている

## 備考

- 著者: claude-code/claude-opus-5
- 確認に使った使い捨てのプロジェクトと Notebook はスクラッチパッドに置き、リポジトリには含めていない
- Giraffe と ASP.NET Core の TestHost による統合テストは、第 15 章（B22）で確かめる

### B19 で確かめたこと（2026-09-19）

- FSharp.Data の `CsvProvider` は、`Schema` を指定しないと小数の列を `decimal`、空欄のある列を `string`（空欄は `""`）と推論する。`Schema="float option,..."` で空欄を `None` にできる。日本語の列名はそのままプロパティ名になる
- ML.NET の FastTree を `numberOfTrees = 1`・`minimumExampleCountPerLeaf = 1`（既定は 10）・`numberOfLeaves = 2^深さ` にし、`OneVersusAll` で多クラスにした。iris のテストデータでは、深さ 2 で自作の決定木と 45 件すべて予測が一致し、深さ 1 では 32 件だった（OneVersusAll はクラスごとに木を作るので、葉が 2 つでも 3 クラスを予測できる）
- ML.NET の特徴量ベクトルの長さは、属性（`VectorType`）ではなく `SchemaDefinition` で実行時に指定できる。これで特徴量の数によらないアダプターを書ける
- .NET Interactive は F# のリストを表示すると内部の構造（`Head`・`Tail`）まで展開し、空のリストの `Head` の例外まで表示する。Notebook で表を見せるときは匿名レコードの配列にする
