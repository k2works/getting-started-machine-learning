# 機械学習から始める C# 入門

C# は .NET で動くオブジェクト指向と関数型の両方を備えた言語で、record・パターンマッチ・LINQ・null 許容参照型によって、データとモデルを簡潔かつ安全に表せます。本シリーズでは、Python 版と同じ題材の機械学習のアルゴリズムを TDD で自作し、次に .NET の機械学習ライブラリ ML.NET に置き換えて結果を突き合わせながら、C# の書き方とエコシステムを学びます。

C# 版は [F# 版](../fsharp/index.md) と同じ .NET・ML.NET・xUnit v3 を使います。同じ処理を 2 つの言語で書いた違いに注目できるように、各章で F# 版と対比します。

## 特徴

- **record とパターンマッチ**: データや決定木の節を、値として比較できる型で表す。F# のレコードと判別共用体に当たる
- **LINQ**: データフレームのライブラリを使わず、record のリストを LINQ で変換・集計する
- **null 許容参照型**: 欠損値を `null` で表し、`?` の有無をコンパイラに検査させる。F# の `option` に当たる
- **ML.NET は C# 向けの API**: `IDataView`・属性による列の対応づけがそのまま使える。F# 版で必要だった型の橋渡しが不要になる箇所を示す
- **静的解析を最初から効かせる**: .NET アナライザーと `TreatWarningsAsErrors` をビルドに組み込み、第 1 章から指摘を受けながら書く

## ほかの版との違い

- Notebook による探索と可視化の節は設けません。グラフは [Python 版](../python/index.md) と [Kotlin 版](../kotlin/index.md) の可視化の節を参照してください
- 分割の手順は Python 版と同じですが、乱数生成器が異なるため、訓練データとテストデータに入る行は Python 版と一致しません。記事の数値は C# 版の実装で実測した値を載せています

## 開発環境

| ツール | 用途 |
|--------|------|
| [.NET SDK](https://dotnet.microsoft.com/) 10 | ビルド・実行・パッケージ管理・テストの実行（`dotnet` CLI） |
| [xUnit](https://xunit.net/) v3 | テスティングフレームワーク |
| [coverlet](https://github.com/coverlet-coverage/coverlet) | カバレッジ |
| `dotnet format` と `.editorconfig` | 整形 |
| [.NET アナライザー](https://learn.microsoft.com/dotnet/fundamentals/code-analysis/overview) | 静的解析（`AnalysisMode: Recommended`） |

## ライブラリ

| ライブラリ | 用途 | 初出 |
|-----------|------|------|
| [ML.NET](https://dotnet.microsoft.com/apps/ai/ml-dotnet) | 機械学習（自作との突き合わせ） | 第 3 章 |
| [ASP.NET Core](https://learn.microsoft.com/aspnet/core/) Minimal API | 予測 API | 第 15 章 |

選定の理由と、章ごとにライブラリへ置き換えられる範囲は [ADR 006](../../../adr/006-csharp-ml-libraries.md) を参照してください。

## サンプルコード

サンプルコードは `apps/csharp/` にあります。学習データの配置方法は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。

```bash
cd apps/csharp
dotnet restore
dotnet run --project tests/MachineLearning.Tests/MachineLearning.Tests.csproj
dotnet run --project src/MachineLearning -- chapter01
```

## 章構成

### 第 1 部: 機械学習と TDD の基本サイクル

| 章 | テーマ |
|----|--------|
| [第 1 章](01-machine-learning-and-first-test.md) | 機械学習とはじめてのテスト |
| [第 2 章](02-data-preprocessing-and-triangulation.md) | データの前処理と三角測量 |
| [第 3 章](03-decision-tree-and-obvious-implementation.md) | 決定木による分類と明白な実装 |

### 第 2 部: 開発環境と自動化

| 章 | テーマ |
|----|--------|
| [第 4 章](04-version-control-and-data-management.md) | バージョン管理とデータ管理 |
| [第 5 章](05-package-management-and-static-analysis.md) | パッケージ管理と静的解析 |
| [第 6 章](06-task-runner-and-ci-cd.md) | タスクランナーと CI/CD |

### 第 3 部: 回帰と実践的な前処理

| 章 | テーマ |
|----|--------|
| [第 7 章](07-linear-regression.md) | 線形回帰による数値予測 |
| [第 8 章](08-classification-and-preprocessing-pipeline.md) | 実践的な分類と前処理パイプライン |
| [第 9 章](09-feature-engineering.md) | 特徴量エンジニアリング |

### 第 4 部: モデルの改善と評価

| 章 | テーマ |
|----|--------|
| [第 10 章](10-logistic-regression-and-ensemble.md) | ロジスティック回帰とアンサンブル学習 |
| 第 11 章 | 評価指標と交差検証 |
| 第 12 章 | 正則化とモデル選択 |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ |
|----|--------|
| 第 13 章 | 主成分分析による次元削減 |
| 第 14 章 | K-means によるクラスタリング |
| 第 15 章 | 機械学習 API とモジュール設計 |

### 付録

総合演習（Bank）の解答例は Python 版のみです。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に C# で取り組んでください。
