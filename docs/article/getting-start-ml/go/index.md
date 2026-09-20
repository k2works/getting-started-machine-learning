# 機械学習から始める Go 入門

Go は、少ない言語機能・明示的なエラー処理・充実した標準ライブラリを特徴とするコンパイル言語です。本シリーズでは、Python 版と同じ題材の機械学習のアルゴリズムを TDD で自作し、数値計算ライブラリ gonum で置き換えられる部分だけを突き合わせながら、Go の書き方とエコシステムを学びます。

Go は、機械学習のライブラリが成熟していない環境の代表として扱います。この点では [TypeScript 版](../typescript/index.md)（ml.js が未成熟）と同じ立場で、決定木・ランダムフォレスト・K-means・ロジスティック回帰は自作が最終実装になります。一方、型と記法の対比では [Java 版](../java/index.md) を相手にします。同じ「クラスも例外もある/ない」を比べると、Go が何を削ったのかが見えます。

## 特徴

- **構造体と `reflect.DeepEqual`**: データを構造体で表し、テストでは値どうしを `reflect.DeepEqual` で比べる。Java の record・Kotlin の data class に当たる役割を、等価性の自動生成なしで果たす
- **例外が無い**: 失敗しうる処理は `(値, error)` の多値返却で表す。Java の検査例外・Scala の `Either` に当たるものを、呼び出しのたびに `if err != nil` で受け止める
- **標準の `testing` と表駆動テスト**: テスティングフレームワークを足さず、標準の `testing` でテストの表を書き、`t.Run` で 1 件ずつ名前を付ける。`t.Parallel` で並行に走らせる
- **ライブラリが限られる**: gonum にあるのは線形回帰・共分散行列・主成分分析まで。無いものは自作し、「ライブラリが無いときにどう作るか」を示す

## ほかの版との違い

- Notebook による探索と可視化の節は設けません。グラフは [Python 版](../python/index.md) と [Kotlin 版](../kotlin/index.md) の可視化の節を参照してください
- 総合演習（付録 A）の解答例は作りません。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Go で取り組んでください
- 分割の手順は Python 版と同じですが、乱数生成器が異なるため、訓練データとテストデータに入る行は Python 版と一致しません。記事の数値は Go 版の実装で実測した値を載せています
- gonum に無いアルゴリズム（決定木・ランダムフォレスト・K-means・ロジスティック回帰）は、自作が最終実装です。該当する章ではライブラリへの置き換えの節を設けず、理由を明記します
- 機械学習ライブラリの [GoLearn](https://github.com/sjwhitworth/golearn) は使いません。最新のコミットが 2022 年 12 月で 3 年以上更新されておらず、保守されているとは言えないためです

## 開発環境

| ツール | 用途 |
|--------|------|
| [Go](https://go.dev/) 1.25（Go Modules） | 言語・ビルド・依存管理 |
| 標準の [`testing`](https://pkg.go.dev/testing) | テスティングフレームワーク（表駆動テスト） |
| [`gofmt`](https://pkg.go.dev/cmd/gofmt) | 整形（`gofmt -l` で検査） |
| [`go vet`](https://pkg.go.dev/cmd/vet)・[golangci-lint](https://golangci-lint.run/) | 静的解析 |
| `go test -cover` | カバレッジ |

`go.mod` の `go` 指令は 1.25 にしています。Nix の環境（`nix develop .#go`）は Go 1.25.5、手元の環境は Go 1.26.5 で、どちらでも `go test ./...` が動くことを確かめています。

## ライブラリ

| ライブラリ | 用途 | 初出 |
|-----------|------|------|
| [gonum](https://www.gonum.org/) | 行列（`mat`）・線形回帰・共分散行列・主成分分析（`stat`） | [第 7 章](07-linear-regression.md) |
| 標準の [`net/http`](https://pkg.go.dev/net/http) | 予測 API | [第 15 章](15-machine-learning-api-and-module-design.md) |

選定の理由と、章ごとにライブラリへ置き換えられる範囲は [ADR 008](../../../adr/008-go-ml-libraries.md) を参照してください。

## サンプルコード

サンプルコードは `apps/go/` にあります。学習データの配置方法は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。

```bash
cd apps/go
go test ./... -cover
go run ./cmd/chapters chapter01
```

リポジトリのルートで `npx gulp apps:check:go` を実行すると、CI と同じ順（整形・`go vet`・golangci-lint・テスト）でまとめて検査できます。

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
| [第 11 章](11-evaluation-metrics-and-cross-validation.md) | 評価指標と交差検証 |
| [第 12 章](12-regularization-and-model-selection.md) | 正則化とモデル選択 |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ |
|----|--------|
| [第 13 章](13-principal-component-analysis.md) | 主成分分析による次元削減 |
| [第 14 章](14-k-means-clustering.md) | K-means によるクラスタリング |
| [第 15 章](15-machine-learning-api-and-module-design.md) | 機械学習 API とモジュール設計 |

### 付録

総合演習（Bank）の解答例は Python 版のみです。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Go で取り組んでください。
