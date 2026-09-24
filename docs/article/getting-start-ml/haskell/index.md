# 機械学習から始める Haskell 入門

Haskell は純粋関数型の言語です。副作用は `IO` として型に現れ、失敗は例外ではなく `Either` として値で表されます。本シリーズでは、Python 版と同じ題材の機械学習のアルゴリズムを TDD で自作し、Haskell の書き方とエコシステムを学びます。

Haskell 版はシリーズの最後、14 番目の言語版です。3 つの軸で対比します。1 つは [Rust 版](../rust/index.md)・[F# 版](../fsharp/index.md) で、**失敗を型で表す**点が同じです。2 つめは [F# 版](../fsharp/index.md)・[Clojure 版](../clojure/index.md)・[Elixir 版](../elixir/index.md) で、純粋関数でデータを変換していく流儀が近いところです。3 つめは [PHP 版](../php/index.md)・[Ruby 版](../ruby/index.md) で、**例外を投げる言語との正反対の設計**です。

## 特徴

- **失敗を型で表す**: `Either String a` を返し、例外を投げない。呼ぶ側は失敗を無視できず、テストは「例外が飛ぶこと」ではなく「どんな値が返るか」を確かめる
- **欠損を `Maybe` で表す**: 値が無いことを型に書く。`null` チェックの漏れがコンパイルで止まる
- **取りうる値を型で数え上げる**: `data Faction = Kinoko | Takenoko` と書けば、綴りの間違いも場合分けの漏れも `-Wall -Werror` が止める
- **`IO` を境界に閉じ込める**: **データの読み込みだけが `IO` で、前処理も学習も評価もすべて純粋関数**になる。この分け方が型で保証される
- **失敗を演算子で連ねる**: `<$>`・`<*>`・`traverse` で「どれか 1 つでも失敗したら失敗」が書ける

## ほかの版との違い

- Notebook による探索と可視化の節は設けません。グラフは [Python 版](../python/index.md) と [Kotlin 版](../kotlin/index.md) の可視化の節を参照してください
- 総合演習（付録 A）の解答例は作りません。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Haskell で取り組んでください
- **機械学習のライブラリがありません。** scikit-learn にあたるものが無く（`hlearn` は保守が止まっています）、決定木・ランダムフォレスト・ロジスティック回帰・K-means・主成分分析はすべて自作します。**突き合わせられるのは線形代数の層だけ**で、これは [Elixir 版](../elixir/index.md)（Scholar に決定木が無い）よりさらに狭い範囲です（[ADR 014](../../../adr/014-haskell-ml-libraries.md)）
- **hmatrix のために C ライブラリ（BLAS/LAPACK）が要ります。** 素の環境では configure の段階で止まるので、Nix の環境定義に `openblas` を足しました。第 7・12・13 章で自作と突き合わせるために必要な判断です
- **乱数は `java.util.Random` と同じ線形合同法を自作します。** `System.Random` はほかの言語版と並びが合いません。**Haskell の `Int` は溢れると折り返す**ので、[PHP 版](../php/index.md)で必要だった乗算の分割が要らず、仕様をそのまま書けます
- CSV は cassava を使いますが、**BOM は取り除かれません**。先頭から自分で取り除きます
- **`ByteString` のリテラルに日本語を書くと壊れます。** 列名と値は `Text` で持ち、境界でだけ UTF-8 として符号化・復号します

## 開発環境

| ツール | 用途 |
|--------|------|
| [GHC](https://www.haskell.org/ghc/) 9.10・[cabal](https://www.haskell.org/cabal/) 3.16 | 言語・ビルド・依存管理 |
| [Hspec](https://hspec.github.io/) 2.11・hspec-discover | テスティングフレームワーク（テスト名に日本語を使える） |
| [fourmolu](https://github.com/fourmolu/fourmolu) 0.19 | 整形（**違反の終了コードは 100**） |
| [hlint](https://github.com/ndmitchell/hlint) 3.10・GHC の `-Wall -Werror` | 静的解析（hlint が書き方、GHC が型と網羅性） |
| HPC（GHC 組み込み） | カバレッジ（しきい値は自作のスクリプトで判定する） |

Nix の環境（`nix develop .#haskell`）は GHC 9.10.3・cabal 3.16.0.0・stack 3.7.1・haskell-language-server と、環境の側で版を固定した fourmolu・hlint・hspec-discover、そして hmatrix のための openblas です。

## ライブラリ

| ライブラリ | 用途 | 初出 |
|-----------|------|------|
| [cassava](https://github.com/haskell-hvr/cassava) | CSV の読み込み（BOM は自分で取り除く） | 第 1 章 |
| 自作の線形合同法 | 乱数（訓練データとテストデータの分割） | 第 2 章 |
| [hmatrix](https://github.com/haskell-numerics/hmatrix) | 線形代数（正規方程式・固有値分解） | 第 7 章 |
| [statistics](https://github.com/haskell/statistics) | 平均・標準偏差 | 第 9 章 |
| `Data.Binary` | 学習済みモデルの保存 | 第 8 章 |
| [Scotty](https://github.com/scotty-web/scotty)・[aeson](https://github.com/haskell/aeson) | 予測 API | 第 15 章 |

選定の理由と、章ごとにライブラリへ置き換えられる範囲は [ADR 014](../../../adr/014-haskell-ml-libraries.md) を参照してください。

## サンプルコード

サンプルコードは `apps/haskell/` にあります。学習データの配置方法は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。

```bash
nix develop .#haskell
cd apps/haskell
cabal build
cabal test
```

リポジトリのルートで `npx gulp apps:check:haskell` を実行すると、CI と同じ順（整形・静的解析・テスト・カバレッジ）でまとめて検査できます。

## 章構成

全 15 章を公開しています。

### 第 1 部: 機械学習と TDD の基本サイクル

| 章 | テーマ | 状態 |
|----|--------|------|
| [第 1 章](01-machine-learning-and-first-test.md) | 機械学習とはじめてのテスト | 公開済み |
| [第 2 章](02-data-preprocessing-and-triangulation.md) | データの前処理と三角測量 | 公開済み |
| [第 3 章](03-decision-tree-and-obvious-implementation.md) | 決定木による分類と明白な実装 | 公開済み |

### 第 2 部: 開発環境と自動化

| 章 | テーマ | 状態 |
|----|--------|------|
| [第 4 章](04-version-control-and-data-management.md) | バージョン管理とデータ管理 | 公開済み |
| [第 5 章](05-package-management-and-static-analysis.md) | パッケージ管理と静的解析 | 公開済み |
| [第 6 章](06-task-runner-and-ci-cd.md) | タスクランナーと CI/CD | 公開済み |

### 第 3 部: 回帰と実践的な前処理

| 章 | テーマ | 状態 |
|----|--------|------|
| [第 7 章](07-linear-regression.md) | 線形回帰による数値予測 | 公開済み |
| [第 8 章](08-classification-and-preprocessing-pipeline.md) | 実践的な分類と前処理パイプライン | 公開済み |
| [第 9 章](09-feature-engineering.md) | 特徴量エンジニアリング | 公開済み |

### 第 4 部: モデルの改善と評価

| 章 | テーマ | 状態 |
|----|--------|------|
| [第 10 章](10-logistic-regression-and-ensemble.md) | ロジスティック回帰とアンサンブル学習 | 公開済み |
| [第 11 章](11-evaluation-metrics-and-cross-validation.md) | 評価指標と交差検証 | 公開済み |
| [第 12 章](12-regularization-and-model-selection.md) | 正則化とモデル選択 | 公開済み |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ | 状態 |
|----|--------|------|
| [第 13 章](13-principal-component-analysis.md) | 主成分分析による次元削減 | 公開済み |
| [第 14 章](14-k-means-clustering.md) | K-means によるクラスタリング | 公開済み |
| [第 15 章](15-machine-learning-api-and-module-design.md) | 機械学習 API とモジュール設計 | 公開済み |

### 付録

総合演習（Bank）の解答例は Python 版のみです。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Haskell で取り組んでください。
