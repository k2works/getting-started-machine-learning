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

- `apps/dotnet/Directory.Packages.props` に上記のライブラリと版だけが記載されている
- C# CI（`.github/workflows/csharp-ci.yml`）がグリーンである
- わざと違反を入れると整形・静的解析の検査が失敗する（B29 で確かめる）

## 備考

- 著者: claude-code/claude-opus-5
