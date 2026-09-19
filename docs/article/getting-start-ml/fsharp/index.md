# 機械学習から始める F# 入門

F# は .NET で動く関数型ファーストの言語で、判別共用体・パターンマッチ・パイプライン演算子（`|>`）・型推論・型プロバイダを備えています。本シリーズでは、Python 版・Kotlin 版・TypeScript 版と同じ題材の機械学習のアルゴリズムを TDD で自作し、次に .NET の機械学習ライブラリ（ML.NET・FSharp.Stats）に置き換えて結果を突き合わせながら、F# の書き方とエコシステムを学びます。

## 特徴

- **判別共用体とパターンマッチ**: 「きのこ派かたけのこ派か」「葉か節か」のような場合分けを型で表し、`match` の書き漏れをコンパイラの警告で見つける
- **パイプライン**: `people |> List.map predictByRule` のように、データを関数に順に通す形で処理を書く
- **型推論**: 型を書かなくても推論されるが、関数の引数と戻り値には型を書いて意図を示す
- **C# 向けのライブラリとの橋渡し**: ML.NET は C# 向けの API なので、F# から使うときの工夫も扱う

## Python 版・Kotlin 版・TypeScript 版との違い

分割の手順は他の言語と同じですが、.NET の乱数生成器を使うので、訓練データとテストデータに入る行は一致しません。件数は一致しますが、正解率や係数などの数値は F# 版の実装で実測した値を載せています。

Notebook による探索と可視化は、Kotlin 版と同じ章（第 2・3・7〜14 章）で扱います。Notebook には **Polyglot Notebooks** を使います。

!!! warning "Polyglot Notebooks は廃止されています"
    Polyglot Notebooks と、それを動かす .NET Interactive は、2026 年に Microsoft が廃止しました（[dotnet/interactive#4163](https://github.com/dotnet/interactive/issues/4163)）。インストール済みの拡張機能は動き続けますが、機能追加やバグ修正は無く、将来の VS Code・.NET SDK の更新で動かなくなる可能性があります。本シリーズでは `Microsoft.dotnet-interactive` 1.0.712001 と Plotly.NET.Interactive 5.0.0 で動作を確かめています。Notebook は探索と可視化だけに使い、テストや記事の数値は `apps/fsharp/` のプロジェクトから求めるので、Notebook が動かなくなっても実装とテストは影響を受けません。

## 開発環境

| ツール | 用途 |
|--------|------|
| [.NET SDK](https://dotnet.microsoft.com/) 10 | ビルド・実行・パッケージ管理・テストの実行（`dotnet` CLI） |
| [xUnit](https://xunit.net/) v3 | テスティングフレームワーク |
| [coverlet](https://github.com/coverlet-coverage/coverlet) | カバレッジ |
| [Fantomas](https://fsprojects.github.io/fantomas/)・[FSharpLint](https://fsprojects.github.io/FSharpLint/) | コードの整形・静的解析 |
| [Polyglot Notebooks](https://github.com/dotnet/interactive) | 探索と可視化（廃止済み。上の注意を参照） |

## ライブラリ

| ライブラリ | 用途 | 初出 |
|-----------|------|------|
| [FSharp.Data](https://fsprojects.github.io/FSharp.Data/) | CSV の読み込み（型プロバイダ） | 第 2 章 |
| [Plotly.NET](https://plotly.net/) | Notebook での可視化 | 第 2 章 |
| [ML.NET](https://dotnet.microsoft.com/apps/ai/ml-dotnet) | 機械学習（自作との突き合わせ） | 第 3 章 |
| [FSharp.Stats](https://fslab.org/FSharp.Stats/) | 数値計算・回帰・主成分分析・K-means | 第 7 章 |
| [Giraffe](https://giraffe.wiki/) | 予測 API | 第 15 章 |

選定の理由と、章ごとにライブラリへ置き換えられる範囲は [ADR 004](../../../adr/004-fsharp-ml-libraries.md) を参照してください。

## サンプルコード

サンプルコードは `apps/fsharp/` にあります。学習データの配置方法は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。

```bash
cd apps/fsharp
dotnet tool restore
dotnet test
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
| 第 4 章（未執筆） | バージョン管理とデータ管理 |
| 第 5 章（未執筆） | パッケージ管理と静的解析 |
| 第 6 章（未執筆） | タスクランナーと CI/CD |

### 第 3 部: 回帰と実践的な前処理

| 章 | テーマ |
|----|--------|
| 第 7 章（未執筆） | 線形回帰による数値予測 |
| 第 8 章（未執筆） | 実践的な分類と前処理パイプライン |
| 第 9 章（未執筆） | 特徴量エンジニアリング |

### 第 4 部: モデルの改善と評価

| 章 | テーマ |
|----|--------|
| 第 10 章（未執筆） | ロジスティック回帰とアンサンブル学習 |
| 第 11 章（未執筆） | 評価指標と交差検証 |
| 第 12 章（未執筆） | 正則化とモデル選択 |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ |
|----|--------|
| 第 13 章（未執筆） | 主成分分析による次元削減 |
| 第 14 章（未執筆） | K-means によるクラスタリング |
| 第 15 章（未執筆） | 機械学習 API とモジュール設計 |

### 付録

総合演習（Bank）の解答例は Python 版のみです。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に F# で取り組んでください。
