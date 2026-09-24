# 機械学習から始める PHP 入門

PHP は Web のために育った言語ですが、いまは型宣言・列挙型・`readonly` クラスを備えた汎用の言語でもあります。本シリーズでは、Python 版と同じ題材の機械学習のアルゴリズムを TDD で自作し、PHP の機械学習ライブラリ Rubix ML と突き合わせながら、PHP の書き方とエコシステムを学びます。

PHP 版は 3 つの軸で対比します。1 つは [Python 版](../python/index.md)・[TypeScript 版](../typescript/index.md) で、漸進的な型付け——実行時に効く型と、静的解析だけが見る型——をどう使い分けるかです。2 つめは [Ruby 版](../ruby/index.md) で、同じスクリプト言語でありながら**型の道具をどこまで使うかが正反対**である点です。3 つめは [Elixir 版](../elixir/index.md) で、機械学習ライブラリの成熟度がまったく違う点です。

## 特徴

- **型を使い切る**: `declare(strict_types=1)` で実行時に型を強制し、PHPStan のレベル 9 で配列の形（`list<Person>`・`array{list<Features>, list<string>}`）まで検査する。[Ruby 版](../ruby/index.md) が RBS・Steep を使わないと決めたのと正反対の選択（[ADR 013](../../../adr/013-php-ml-libraries.md)）
- **実行時の型と静的解析の型が分かれている**: 引数の `string` は実行時に効き、PHPDoc の `list<string>` は静的解析だけが見る。この二層構造が PHP の漸進的な型付けの姿
- **値は `readonly class` で表す**: コンストラクタプロモーションで 1 か所に書く。ただし**比較は既定で参照の同一性**なので、テストでは `assertEquals`（中身）と `assertSame`（同一性）を使い分ける
- **緩い変換をあえて避ける**: `(int) '高い'` は 0 を返して落ちないので、`filter_var(..., FILTER_VALIDATE_INT)` で厳密に検査する。比較も `==` ではなく `===` で書く
- **失敗は例外で表す**: `throw new InvalidArgumentException(...)` を使う。Rust 版の `Result`・Go 版の `error` とは逆の流儀で、Ruby 版・Elixir 版に近い

## ほかの版との違い

- Notebook による探索と可視化の節は設けません。グラフは [Python 版](../python/index.md) と [Kotlin 版](../kotlin/index.md) の可視化の節を参照してください
- 総合演習（付録 A）の解答例は作りません。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に PHP で取り組んでください
- **Rubix ML には決定木もランダムフォレストもあります。** 直前に書いた [Elixir 版](../elixir/index.md) では Scholar に決定木が無く、自作した決定木がそのまま最終実装になりました。同じ第 3 波の 2 つの版が、ライブラリの成熟度という点で正反対の性格を持ちます。PHP 版ではほぼ全章で「自作してからライブラリと突き合わせる」ことができます
- **ただし素の線形回帰とラッソはありません。** 線形回帰は正則化を 0 にしたリッジ（`Ridge(0.0)`）で代用し、ラッソは自作します（[ADR 013](../../../adr/013-php-ml-libraries.md)）
- **乱数は `java.util.Random` と同じ線形合同法を自作します。** `mt_rand` はほかの言語版と並びが合いません。自作すると、第 2 章以降の分割が [Java 版](../java/index.md)・[Kotlin 版](../kotlin/index.md)・[Scala 版](../scala/index.md)・[Clojure 版](../clojure/index.md)・[Elixir 版](../elixir/index.md) と一致します
- CSV は標準の `fgetcsv` を使いますが、**BOM は取り除かれません**。先頭の列名から自分で取り除きます
- **カバレッジには拡張（pcov）が要ります。** 素の Nix 環境には xdebug も pcov も入っておらず、環境定義に手を入れました。**さらに PHPUnit には最低カバレッジのしきい値の機能が無い**ので、clover の XML を読んで判定する短いスクリプトを自作します

## 開発環境

| ツール | 用途 |
|--------|------|
| [PHP](https://www.php.net/) 8.4・[Composer](https://getcomposer.org/) | 言語・依存管理・スクリプト |
| [PHPUnit](https://phpunit.de/) 11 | テスティングフレームワーク（テスト名に日本語を使える） |
| [PHP-CS-Fixer](https://cs.symfony.com/) | 整形（PSR-12。**違反の終了コードは 8**） |
| [PHPStan](https://phpstan.org/) 2 | 静的解析（レベル 9） |
| [pcov](https://github.com/krakjoe/pcov) | カバレッジ（しきい値は自作のスクリプトで判定する） |

Nix の環境（`nix develop .#php`）は PHP 8.4.16（pcov 付き）・Composer 2.9.2・phpactor です。

## ライブラリ

| ライブラリ | 用途 | 初出 |
|-----------|------|------|
| 標準の `fgetcsv` | CSV の読み込み（BOM は自分で取り除く） | 第 1 章 |
| 自作の線形合同法 | 乱数（訓練データとテストデータの分割） | 第 2 章 |
| [Rubix ML](https://rubixml.com/) | 決定木・ランダムフォレスト・ロジスティック回帰・リッジ・K-means・主成分分析・評価指標・前処理 | 第 3 章 |
| [MathPHP](https://github.com/markrogoyski/math-php) | 行列・統計（正規方程式） | 第 7 章 |
| mbstring の `CP932` | Shift_JIS の CSV（追加の依存なし） | 第 9 章 |
| 標準の `serialize` | 学習済みモデルの保存 | 第 8 章 |
| 標準の組み込みサーバー | 予測 API | 第 15 章 |

選定の理由と、章ごとにライブラリへ置き換えられる範囲は [ADR 013](../../../adr/013-php-ml-libraries.md) を参照してください。

## サンプルコード

サンプルコードは `apps/php/` にあります。学習データの配置方法は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。

```bash
nix develop .#php
cd apps/php
composer install
composer test
```

リポジトリのルートで `npx gulp apps:check:php` を実行すると、CI と同じ順（整形・静的解析・テスト・カバレッジ）でまとめて検査できます。

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

総合演習（Bank）の解答例は Python 版のみです。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に PHP で取り組んでください。
