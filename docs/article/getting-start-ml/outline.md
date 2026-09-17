---
type: Article
title: "執筆計画アウトライン"
description: "「機械学習から始めるプログラミング入門」シリーズの章構成・学習データの扱い・対象言語・Bolt 計画をまとめた執筆計画。"
tags: [article,getting-start-ml]
status: stable
generated: { by: claude-code/claude-opus-5, at: 2026-09-17T03:24:28Z }
verified:
  - { by: human:kakimomokuri, at: 2026-09-17T01:52:09Z }
  - { by: human:kakimomokuri, at: 2026-09-17T03:09:16Z }
---

# 執筆計画アウトライン

## 概要

「機械学習から始めるプログラミング入門」シリーズの執筆計画。Wiki 記事「テスト駆動開発から始める機械学習入門」（Python 単一言語・全 8 章）の内容を再構成し、[テスト駆動開発から始めるプログラミング入門](../getting-start-tdd/index.md) と同じく、複数の言語で章構成を揃えた記事として執筆する。

「テスト駆動開発から始めるプログラミング入門」が FizzBuzz を題材に言語を学んだのに対し、本シリーズは **機械学習を題材に** 言語を学ぶ。データの読み込み・前処理・アルゴリズム・評価・API 化という具体的な流れの中で、各言語のデータ構造・数値計算・型・モジュール設計・エコシステムの違いを体験する。

### 執筆方針

| 方針 | 内容 |
|------|------|
| 自作からライブラリへ | 各アルゴリズムはまず TDD で自作し、次にその言語の ML ライブラリで置き換えて結果を突き合わせる。自作で原理とその言語の書き方を学び、置き換えでエコシステムを学ぶ |
| 段階的な言語拡大 | 第 1 波の 3 言語（Python・Kotlin・TypeScript）で章構成と記事の型を固め、第 2 波・第 3 波で言語を広げる |
| Notebook による可視化は Python と Kotlin のみ | データの探索と可視化は Python（Jupyter Lab）と Kotlin（Kotlin Notebook）の記事だけで扱う。他の言語は可視化を扱わず、自作とライブラリの実装に集中する |
| 学習データはコミットしない | 学習データは『スッキリわかる Python による機械学習入門』の配布データを使うが、リポジトリには含めない（後述の「学習データ」を参照） |
| 数値は実測値を載せる | 記事に載せる件数・正解率・係数などは、`apps/{env}/` の実装を実データで動かした結果だけを載せる。Wiki 記事の数値は転記しない |

### 参照元

| 参照元 | 場所 | 使い方 |
|--------|------|--------|
| テスト駆動開発から始める機械学習入門（Wiki 記事） | `tmp/テスト駆動開発から始める機械学習入門.md` | 章の題材・TDD のステップ分割・API 化の設計を参照する |
| getting-started-tdd | `tmp/getting-started-tdd/` | シリーズ構成（部・章・言語別ディレクトリ・`outline.md`・`workflow.md`・言語別 CI）の雛形にする |
| スッキリわかる Python による機械学習入門 第 1 版 配布コード | `tmp/sukkiri-ml-codes.zip` | 学習データ（`datafiles/`）と、各章の分析手順（`py/chapNN.py`）を参照する |

`tmp/` は `.gitignore` の対象なので、参照元はリポジトリに含まれない。

## 学習データ

### 入手方法

学習データは書籍『スッキリわかる Python による機械学習入門』（インプレス, 2020）のサポートページで配布されている ZIP ファイルに含まれる。

- 書籍サポートページ: <https://sukkiri.jp/books/sukkiri_ml>
- 配布 ZIP: `sukkiri-ml-codes.zip`（`datafiles/`・`py/`・`ipynb/` を含む）

### ライセンス上の制約

配布物の `LICENSE.txt` は Creative Commons BY-SA 4.0 に加え、次の特約を定めている（特約が優先する）。

- 書籍の購入者（電子版を含み、旧版・中古品を含まない）のみ利用できる
- AS-IS・非保証で提供される

本リポジトリ（`k2works/getting-started-machine-learning`）は PUBLIC なので、データを同梱すると購入者以外にも配布することになる。そのため次のとおり扱う。

| 対象 | 扱い |
|------|------|
| `datafiles/*.csv` などの学習データ | コミットしない。読者が ZIP を入手して所定の場所に展開する |
| `py/`・`ipynb/` の書籍コード | コミットしない。記事にも転記しない（分析手順は各言語の実装として書き起こす） |
| 単体テストのデータ | 数行のフィクスチャをテストコード内または `test/fixtures/` に自作する（配布データの行をコピーしない） |
| 記事中のデータ例 | 列名とデータの形（型・欠損の有無）の説明にとどめ、配布データの行をまとめて転記しない |

### 配置方法

全言語の実装から同じ場所を参照できるように、データは `apps/data/sukkiri-ml/` に展開する。

```text
apps/
├── data/
│   └── sukkiri-ml/          # .gitignore 対象（コミットしない）
│       ├── KvsT.csv
│       ├── iris.csv
│       ├── cinema.csv
│       ├── Survived.csv
│       ├── Boston.csv
│       ├── Wholesale.csv
│       ├── Bank.csv
│       ├── bike.tsv
│       └── weather.csv
└── {env}/                    # 各言語の実装は環境変数 ML_DATA_DIR（既定値 ../data/sukkiri-ml）でデータを参照する
```

リポジトリのルートで次を実行する。

```bash
unzip sukkiri-ml-codes.zip 'datafiles/*' -d tmp/sukkiri-ml
mkdir -p apps/data/sukkiri-ml
cp tmp/sukkiri-ml/datafiles/* apps/data/sukkiri-ml/
```

前提整備で次を行う。

- `.gitignore` に `apps/data/` を追加する
- データの配置を確認する Gulp タスク（例: `gulp data:check`）を `operating-script` スキルで追加し、運用手順書に記載する
- 実データを使うテストは「データが無ければスキップ」にし、CI とデータを持たない読者の環境でも単体テストが通るようにする

### 本シリーズで使うデータ

| ファイル | 件数 | 列 | 使う章 | 注意点 |
|---------|------|-----|--------|--------|
| `KvsT.csv` | 19 | 身長, 体重, 年代, 派閥 | 1 | BOM 付き UTF-8、列名が日本語 |
| `iris.csv` | 150 | がく片長さ, がく片幅, 花弁長さ, 花弁幅, 種類 | 2, 3, 10 | BOM 付き UTF-8、スケール変換済みの値（scikit-learn 同梱の iris とは値が異なる）、特徴量 4 列に計 7 件の欠損 |
| `cinema.csv` | 100 | cinema_id, SNS1, SNS2, actor, original, sales | 7, 11, 15 | SNS1・actor に欠損、外れ値あり |
| `Survived.csv` | 891 | PassengerId, Survived, Pclass, Sex, Age, SibSp, Parch, Ticket, Fare, Cabin, Embarked | 8, 10, 11, 15 | BOM 付き UTF-8、Age（177 件）・Cabin（687 件）・Embarked（2 件）に欠損、クラス不均衡 |
| `Boston.csv` | 100 | CRIME, ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, PRICE | 9, 12, 13 | CRIME がカテゴリ値（high・low・very_low）、NOX・RAD に欠損 |
| `Wholesale.csv` | 440 | Channel, Region, Fresh, Milk, Grocery, Frozen, Detergents_Paper, Delicassen | 14 | 欠損なし |
| `bike.tsv` / `weather.csv` | 731 / 3 | dteday, holiday, weekday, workingday, weather_id, cnt / weather_id, weather | 9（データ結合） | TSV、`weather.csv` は Shift_JIS |
| `Bank.csv` | 27,128 | id, age, job, …, y | 付録（総合演習） | 文字列の列が多い、duration に欠損 |

件数・欠損数は配布 ZIP のデータをヘッダーを除いて数えた値。**BOM・文字コード・欠損の扱いは言語ごとに差が出るので、各言語の第 2 章で必ず扱う。**

## Notebook による探索と可視化

データの探索と可視化は Python と Kotlin の記事だけで扱う。Wiki 記事の「Jupyter Notebook での探索と視覚化」の節（ペアプロット・ヒートマップ・混同行列・ROC 曲線・残差プロットなど）に相当する内容を、両言語の Notebook で書き起こす。

| 言語 | Notebook 環境 | データフレーム | 可視化ライブラリ候補 | Notebook の配置 |
|------|--------------|--------------|------------------|----------------|
| Python | Jupyter Lab | pandas | matplotlib, seaborn | `apps/python/notebooks/` |
| Kotlin | Kotlin Notebook（IntelliJ IDEA） | Kotlin DataFrame | Kandy | `apps/kotlin/notebooks/` |

- 可視化ライブラリ候補は未検証。Python・Kotlin の第 3 章に着手する前に、ML ライブラリと同じ ADR で確定する。
- Notebook は「探索して分かったことをテストと本番コードに移す」ための場所として位置づける。Notebook で見つけた知見（例: 最適な `max_depth`）は必ずテストに反映し、Notebook だけに残さない。
- Notebook をコミットする前に出力セルを消す。出力に学習データの行が含まれると、データをコミットしないという方針に反するため。
- CI では Notebook を実行しない。Notebook から移したテストと本番コードを CI で検証する。
- 他の言語の記事では可視化の節を設けず、該当章の冒頭で「可視化は Python 版・Kotlin 版を参照」と案内する。

### 可視化の節を設ける章

| 章 | データ | 可視化の内容 |
|----|--------|------------|
| 2 | iris | 欠損値の分布、特徴量の散布図行列 |
| 3 | iris | 決定木の構造、特徴量重要度、`max_depth` ごとの正解率 |
| 7 | cinema | 相関のヒートマップ、散布図と外れ値、実測値と予測値、残差プロット |
| 8 | Survived | クラス分布、性別・客室クラス別の生存率、混同行列 |
| 9 | Boston | 標準化前後の分布、特徴量と価格の散布図 |
| 10 | iris | モデル別の特徴量重要度 |
| 11 | Survived | 混同行列、ROC 曲線 |
| 12 | Boston | 正則化の強さと係数の変化 |
| 13 | Boston | 第 1・第 2 主成分の散布図、寄与率 |
| 14 | Wholesale | エルボー法の SSE 曲線、クラスタ別の散布図 |

## 対象言語

`ops/nix/environments/` に定義された 12 環境に `kotlin` 環境を加えた 13 環境（14 言語）を、3 つの波に分けて対象にする。`kotlin` 環境は本リポジトリに未定義なので、前提整備で追加する。

| 波 | 環境名 | 言語 | テスト基盤 | ML ライブラリ候補 | 備考 |
|----|--------|------|-----------|------------------|------|
| 1 | python | Python | pytest（uv） | pandas, scikit-learn | 参照実装。Wiki 記事と同じ言語 |
| 1 | kotlin | Kotlin | kotlin.test（Gradle） | Kotlin DataFrame, Tribuo（「Kotlin 版執筆計画」を参照） | 静的型付け・OOP と FP の融合。Kotlin Notebook による可視化を扱う |
| 1 | node | TypeScript | Vitest（npm） | danfo.js, ml.js 系パッケージ | ML ライブラリが未成熟な言語での自作の価値を示す |
| 2 | java | Java | JUnit 5（Gradle） | Tribuo, Smile | 静的型付け OOP の代表。Kotlin 版の実装と対比する |
| 2 | dotnet | C# | xUnit | ML.NET, Microsoft.Data.Analysis | |
| 2 | dotnet | F# | xUnit | ML.NET, Deedle | 型プロバイダ・パイプライン |
| 2 | scala | Scala | ScalaTest（sbt） | Smile | |
| 2 | rust | Rust | cargo test | linfa, ndarray, polars | 所有権と数値計算 |
| 2 | go | Go | go test | gonum | ライブラリが限定的なので自作の比重が大きい |
| 3 | ruby | Ruby | Minitest（Bundler） | Rumale, Numo | |
| 3 | php | PHP | PHPUnit（Composer） | Rubix ML | |
| 3 | elixir | Elixir | ExUnit（Mix） | Nx, Explorer, Scholar | |
| 3 | clojure | Clojure | clojure.test（Leiningen） | tablecloth, scicloj.ml | REPL 駆動の探索 |
| 3 | haskell | Haskell | Hspec（Stack） | hmatrix | ライブラリが限定的なので自作の比重が大きい |

- テスト基盤は既存シリーズ「テスト駆動開発から始めるプログラミング入門」の各言語の選択に揃える（Kotlin は `tmp/getting-started-tdd/` の Kotlin 版に揃える）。
- **ML ライブラリ候補は未検証**。各言語の第 3 章に着手する前に、決定木・線形回帰・ロジスティック回帰・ランダムフォレスト・PCA・K-means の対応状況、保守状況、ライセンスを確認し、ADR（`creating-adr`）で確定する。特に Smile はバージョンによってライセンスが異なるため、採用前に確認する。
- ライブラリに無いアルゴリズムは、自作版をその言語の「最終実装」とし、置き換えの節は「ライブラリ未対応」と理由を書いて省略する。

## 章構成

Wiki 記事の全 8 章と、スッキリわかる機械学習入門の第 4〜15 章の題材を、5 部 15 章に再構成する。

### Wiki 記事・書籍との対応

| Wiki 記事 | 書籍の章（題材） | 本シリーズ |
|-----------|-----------------|-----------|
| 1 章 機械学習とは / 3 章 機械学習の基礎理論 | 4 章（KvsT） | 第 1 章 |
| 2 章 開発環境のセットアップ | — | 第 4〜6 章 |
| 4 章 Iris 分類モデル | 5 章（iris） | 第 2〜3 章 |
| 5 章 Cinema 興行収入予測モデル | 6 章（cinema） | 第 7 章 |
| 6 章 Survived 生存予測モデル | 7 章（Survived） | 第 8 章 |
| 7 章 Boston 住宅価格予測モデル | 8 章（Boston）、10 章（bike・データ結合） | 第 9 章 |
| — | 12 章（ロジスティック回帰・アンサンブル） | 第 10 章 |
| — | 11 章（正則化）、13 章（評価指標・交差検証） | 第 11〜12 章 |
| — | 14 章（PCA）、15 章（K-means） | 第 13〜14 章 |
| 8 章 機械学習 API の構築 | — | 第 15 章 |
| — | 9 章（Bank・総合演習） | 付録 |

### 第 1 部: 機械学習と TDD の基本サイクル

データを読み込み、前処理し、最初のモデルで分類するまでを TDD で進める。テスティングフレームワークの導入から「動作するきれいなコード」までを体験する。

| 章 | テーマ | データ | 内容 |
|----|--------|--------|------|
| 1 | 機械学習とはじめてのテスト | KvsT | 機械学習とルールベースの違い、機械学習のワークフロー、分類と回帰、TODO リスト、テスティングフレームワーク導入、CSV 読み込みのテストファースト |
| 2 | データの前処理と三角測量 | iris | 欠損値の検出と平均値補完、特徴量と正解ラベルの分離、訓練・テスト分割（乱数シード固定）、仮実装と三角測量、文字コード・BOM の扱い |
| 3 | 決定木による分類と明白な実装 | iris | ジニ不純度、最良分割の探索、木の構築と予測（自作）、正解率、過学習と `max_depth`、ライブラリへの置き換えと結果の突き合わせ |

### 第 2 部: 開発環境と自動化

Wiki 記事 2 章（uv・Ruff・mypy・Jupyter Lab）の内容を、各言語のツールチェーンに置き換えて 3 章に分割する。

| 章 | テーマ | 内容 |
|----|--------|------|
| 4 | バージョン管理とデータ管理 | Git フロー、Conventional Commits、学習データとモデルファイルをコミットしない運用（`.gitignore`・入手手順の文書化）、乱数シードによる再現性 |
| 5 | パッケージ管理と静的解析 | パッケージマネージャーによる ML ライブラリの導入、リンター、フォーマッター、型検査、カバレッジ |
| 6 | タスクランナーと CI/CD | タスクランナー、フィクスチャで検証する CI、実データでの検証をローカルに分ける設計、REPL での探索を本番コードとテストに移す流れ（Python・Kotlin は Notebook 環境の導入と出力セルの除去を含む） |

### 第 3 部: 回帰と実践的な前処理

回帰問題と、欠損・カテゴリ値・外れ値を含む現実的なデータを扱う。前処理を小さな関数に分けてテストする設計を学ぶ。

| 章 | テーマ | データ | 内容 |
|----|--------|--------|------|
| 7 | 線形回帰による数値予測 | cinema | 最小二乗法（正規方程式）の自作、外れ値の除去、決定係数（R²）・MAE・RMSE、係数の解釈、ライブラリへの置き換え |
| 8 | 実践的な分類と前処理パイプライン | Survived | グループ別の欠損値補完、ダミー変数化、クラス不均衡と `class_weight`、前処理パイプラインの設計、モデルの保存と読み込み |
| 9 | 特徴量エンジニアリング | Boston、bike / weather | 標準化、カテゴリ値の変換、多項式特徴量と交互作用、データの結合、外れ値の検出 |

### 第 4 部: モデルの改善と評価

複数のモデルを同じインターフェースで扱い、評価指標で比較する。抽象化・ポリモーフィズム・高階関数を、モデルと評価の設計として学ぶ。

| 章 | テーマ | データ | 内容 |
|----|--------|--------|------|
| 10 | ロジスティック回帰とアンサンブル学習 | iris | 勾配降下法によるロジスティック回帰の自作、第 3 章の決定木を再利用したランダムフォレスト（バギング）、特徴量重要度、モデル共通インターフェース |
| 11 | 評価指標と交差検証 | Survived、cinema | 混同行列、適合率・再現率・F 値、MSE・RMSE・MAE、K 分割交差検証、評価関数を高階関数として渡す設計 |
| 12 | 正則化とモデル選択 | Boston | 過学習と正則化、リッジ回帰（自作）、ラッソ回帰（ライブラリ）、ハイパーパラメータ探索、不変データによる実験結果の記録 |

### 第 5 部: 教師なし学習と実運用

正解ラベルの無いデータを扱い、最後に学習済みモデルを API として公開する。

| 章 | テーマ | データ | 内容 |
|----|--------|--------|------|
| 13 | 主成分分析による次元削減 | Boston | 分散共分散行列と固有値分解、主成分と寄与率、ライブラリへの置き換え |
| 14 | K-means によるクラスタリング | Wholesale | K-means の自作、SSE とエルボー法、クラスタの解釈 |
| 15 | 機械学習 API とモジュール設計 | cinema、Survived | レイヤードアーキテクチャ、モデルの永続化と読み込み、入力バリデーション、HTTP API、統合テスト、エラーハンドリング |

### 付録

| 付録 | テーマ | 内容 |
|------|--------|------|
| A | 総合演習（Bank） | 第 1〜12 章の手法を組み合わせて、訓練・検証・テストの 3 分割でモデルを作る演習。解答は Python のみとし、他言語は演習問題として提示する |

**書かないと決めた内容**:

- Notebook による可視化は Python と Kotlin 以外の言語では書かない。各言語で等価な Notebook 環境と可視化ライブラリが揃わず、記事とコードの同期を保てないため
- 書籍 0 章（Python の文法練習）は、第 1 部の各言語の文法解説で代替する
- 書籍 12 章の AdaBoost は、第 10 章でバギングを自作するため、ライブラリ紹介の 1 節にとどめる

## 言語ごとのバリエーション

| 言語 | 本シリーズでの焦点 | 第 15 章の API フレームワーク候補 |
|------|------------------|-------------------------------|
| Python | pandas／NumPy のベクトル演算と自作ループの対比、型ヒントと mypy | FastAPI |
| Kotlin | data class・sealed interface によるデータとモデルの表現、拡張関数、Kotlin DataFrame と Kandy による探索 | Ktor |
| Java | record・sealed interface によるデータとモデルの表現、Stream API（Kotlin 版との対比） | Javalin, Spring Boot |
| TypeScript | 型による列スキーマの表現、ライブラリ未成熟領域での自作 | Hono, Fastify |
| C# | LINQ、ML.NET の `IDataView` とパイプライン | ASP.NET Core Minimal API |
| F# | 判別共用体、パイプライン演算子、型プロバイダによる CSV 読み込み | Giraffe |
| Scala | case class、コレクション API、Smile との連携 | http4s |
| Rust | 所有権と行列演算、`Result` による前処理エラー | axum |
| Go | スライスによる行列表現、インターフェースによるモデル抽象 | net/http |
| Ruby | Enumerable、ブロック、Rumale | Sinatra |
| PHP | 配列関数、Rubix ML | Slim |
| Elixir | Nx のテンソル、パイプライン演算子、Explorer のデータフレーム | Plug / Phoenix |
| Clojure | シーケンス操作とスレッディングマクロ、REPL 駆動の探索 | Ring |
| Haskell | 純粋関数による前処理、型クラスによるモデル抽象、`Either` | Scotty |

フレームワーク候補も未検証であり、第 15 章の着手前に ADR で確定する。

## ファイル構成

```text
docs/article/getting-start-ml/
├── index.md              # シリーズトップ（言語別一覧・全章構成）
├── outline.md            # 本ファイル（執筆計画）
├── workflow.md           # 執筆ワークフロー（参照元・データ配置・同期チェックリスト）
├── python/               # Python
│   ├── index.md
│   ├── 01-machine-learning-and-first-test.md
│   ├── 02-data-preprocessing-and-triangulation.md
│   ├── 03-decision-tree-and-obvious-implementation.md
│   ├── 04-version-control-and-data-management.md
│   ├── 05-package-management-and-static-analysis.md
│   ├── 06-task-runner-and-ci-cd.md
│   ├── 07-linear-regression.md
│   ├── 08-classification-and-preprocessing-pipeline.md
│   ├── 09-feature-engineering.md
│   ├── 10-logistic-regression-and-ensemble.md
│   ├── 11-evaluation-metrics-and-cross-validation.md
│   ├── 12-regularization-and-model-selection.md
│   ├── 13-principal-component-analysis.md
│   ├── 14-k-means-clustering.md
│   ├── 15-machine-learning-api-and-module-design.md
│   └── appendix-a-bank-exercise.md
├── kotlin/               # Kotlin
├── typescript/           # TypeScript
├── java/                 # Java
├── csharp/               # C#
├── fsharp/               # F#
├── scala/                # Scala
├── rust/                 # Rust
├── go/                   # Go
├── ruby/                 # Ruby
├── php/                  # PHP
├── elixir/               # Elixir
├── clojure/              # Clojure
├── haskell/              # Haskell
└── integration/          # 多言語統合解説
    ├── index.md
    ├── 01-language-and-library-overview.md
    ├── 02-data-structure-comparison.md
    ├── 03-algorithm-implementation-comparison.md
    ├── 04-library-ecosystem-comparison.md
    └── 05-learning-roadmap.md
```

記事ディレクトリ名は既存シリーズ（`getting-start-tdd` の `csharp`・`fsharp` 分割）に合わせ、言語名にする。

## 実装コードの配置

各言語の実装コードは `apps/{env}/` に配置する。ディレクトリ名は `ops/nix/environments/` の環境名と一致させる。例外として `apps/data/` は全言語で共有する学習データの置き場とする。

```text
apps/
├── data/                 # 学習データ（.gitignore 対象、「学習データ」の配置方法を参照）
├── python/               # Python（uv プロジェクト）
│   └── notebooks/        # Jupyter Lab の Notebook（出力セルを消してコミット）
├── kotlin/               # Kotlin（Gradle プロジェクト）
│   └── notebooks/        # Kotlin Notebook（出力セルを消してコミット）
├── node/                 # TypeScript（npm プロジェクト）
├── java/                 # Java（Gradle プロジェクト）
├── dotnet/               # C# / F#（.NET ソリューション）
├── scala/                # Scala（sbt プロジェクト）
├── rust/                 # Rust（Cargo プロジェクト）
├── go/                   # Go（Go Modules プロジェクト）
├── ruby/                 # Ruby（Bundler プロジェクト）
├── php/                  # PHP（Composer プロジェクト）
├── elixir/               # Elixir（Mix プロジェクト）
├── clojure/              # Clojure（Leiningen プロジェクト）
└── haskell/              # Haskell（Stack プロジェクト）
```

各プロジェクトは章ごとにパッケージ（モジュール）を分け、章の途中の状態ではなく **章の完成状態** を保持する。後の章が前の章のコード（第 3 章の決定木を第 10 章で再利用するなど）に依存する場合は、依存先を明記する。

## 開発計画（AI-DLC）

### Intent

機械学習を題材に、複数の言語で「データを読み、前処理し、学習・評価し、API として届ける」までを TDD で再現できる記事シリーズを提供する。

### Unit

| Unit | 範囲 | 依存 |
|------|------|------|
| U0 シリーズ基盤 | `index.md`・`workflow.md`・`mkdocs.yml` の nav・`apps/data/` の配置と `data:check` タスク・`.gitignore` | なし |
| U1 Python | 第 1〜15 章・付録 A、`apps/python/`、CI | U0 |
| U2 Kotlin | 第 1〜15 章、`apps/kotlin/`（Notebook を含む）、`kotlin` の Nix 環境、CI | U0、U1（章の節構成・可視化の節構成） |
| U3 TypeScript | 第 1〜15 章、`apps/node/`、CI | U0、U1（章の節構成） |
| U4〜U9 第 2 波 | Java・C#・F#・Scala・Rust・Go の各言語 | U0、U1 |
| U10〜U14 第 3 波 | Ruby・PHP・Elixir・Clojure・Haskell の各言語 | U0、U1 |
| U15 多言語統合解説 | `integration/` | 第 1 波の 3 言語完了後に着手し、波ごとに更新 |

### Bolt 計画（第 1 波）

| Bolt | 内容 | 完了条件 |
|------|------|---------|
| B1 ウォーキングスケルトン | U0 と Python 第 1 章。データ配置 → CSV 読み込みのテスト → 記事 → nav → CI までを 1 本通す | `apps/python/` の最小テストが CI でグリーン、記事がローカルプレビューで表示される |
| B2 | Python 第 2〜3 章（ML・可視化ライブラリ選定 ADR を含む） | 自作決定木とライブラリ版の正解率を記事に並べて載せられる。Notebook の可視化が記事と一致する |
| B3 | Python 第 4〜6 章 | CI がフィクスチャで検証し、実データのテストがスキップされることを確認 |
| B4 | Python 第 7〜9 章 | |
| B5 | Python 第 10〜12 章 | |
| B6 | Python 第 13〜15 章・付録 A | Python の全章完了。章の節構成を他言語の雛形として確定 |
| B7〜B12 | Kotlin・TypeScript を B1〜B6 と同じ区切りで進める。Kotlin の B7 で `kotlin` の Nix 環境と Kotlin Notebook の動作を確認する | 各言語の全章完了。Kotlin は可視化の節が Python 版と揃っている |
| B13 | 多言語統合解説（第 1 波の 3 言語） | 統合解説の各表で 3 言語の行・列が揃っている |

B1〜B6（Python 版の全章）は 2026-09-17 に完了した。第 2 章以降は、依存関係の無い章をサブエージェントで並行して実装・執筆し、親が検査（テスト・カバレッジ・lint・型・Notebook の出力・学習データ行の混入）してから章ごとにコミットした。

各 Bolt の着手前にステップ計画を作り、承認を得てから進める。第 2 波・第 3 波の Bolt 計画は、第 1 波の実績（1 章あたりの所要時間、ライブラリ選定で詰まった点）を踏まえて第 1 波の完了時に作る。

### 前提整備

| 項目 | 内容 | 状態 |
|------|------|------|
| 学習データ | `apps/data/sukkiri-ml/` への配置手順、`.gitignore` への `apps/data/` 追加、`data:setup`・`data:check` タスク | 完了 |
| シリーズ骨子 | `index.md`・`workflow.md`、`docs/article/index.md` のシリーズ一覧、`mkdocs.yml` の nav | 完了 |
| Nix 環境 | `python`・`node` は既存。`kotlin`（JDK 21 + kotlin + gradle）は `tmp/getting-started-tdd/ops/nix/environments/kotlin/` を雛形に追加し flake に登録する。ML ライブラリの導入に必要なネイティブ依存（BLAS 等）の有無を確認 | 進行中（`python` は CI で動作確認済み。`kotlin` は未着手） |
| Notebook 環境 | Python は Jupyter Lab を開発依存に追加。Kotlin は IntelliJ IDEA の Kotlin Notebook で Kotlin DataFrame・Kandy が読み込めることを確認。両言語とも出力セルを消す仕組み（pre-commit フックやタスク）を用意 | 進行中（Python は `tools/notebooks.py` と tox で完了。Kotlin は未着手） |
| アプリ雛形 | `apps/python/`・`apps/kotlin/`・`apps/node/` にテストが 1 本通る最小構成 | 進行中（`apps/python/` 完了） |
| ライブラリ選定 | 第 1 波 3 言語の ML ライブラリと、Python・Kotlin の可視化ライブラリを ADR で確定 | 進行中（Python は ADR 001 で確定。Kotlin・TypeScript は未着手） |

### 章別執筆計画（Python）

| 章 | テーマ | Python での焦点 |
|----|--------|----------------|
| 1 | 機械学習とはじめてのテスト | pytest の導入、`csv` モジュールと pandas の読み込みの対比 |
| 2 | データの前処理と三角測量 | pandas の欠損値処理、`encoding="utf-8-sig"`、自作の分割関数とシード |
| 3 | 決定木による分類と明白な実装 | `dataclass` による木のノード、再帰、scikit-learn `DecisionTreeClassifier` との突き合わせ |
| 4 | バージョン管理とデータ管理 | Git フロー（言語共通）、`.gitignore`、`random_state` |
| 5 | パッケージ管理と静的解析 | uv、Ruff、mypy、pytest-cov |
| 6 | タスクランナーと CI/CD | tox によるタスク、GitHub Actions、`pytest.mark.skipif` による実データテストの分離、`nix develop` が設定する `PYTHONPATH` と uv の仮想環境の衝突の回避、Jupyter Lab の導入と出力セルの除去 |
| 7 | 線形回帰による数値予測 | NumPy による正規方程式、`LinearRegression` との突き合わせ |
| 8 | 実践的な分類と前処理パイプライン | `groupby` による補完、`get_dummies`、`Pipeline`、`pickle`／joblib |
| 9 | 特徴量エンジニアリング | `StandardScaler`、`PolynomialFeatures`、`merge` |
| 10 | ロジスティック回帰とアンサンブル学習 | NumPy による勾配降下、`Protocol` によるモデル共通インターフェース、`RandomForestClassifier` |
| 11 | 評価指標と交差検証 | 評価関数の受け渡し、`KFold`・`cross_validate` |
| 12 | 正則化とモデル選択 | `Ridge`・`Lasso`、`frozen dataclass` による実験結果の記録 |
| 13 | 主成分分析による次元削減 | `numpy.linalg.eigh`、`PCA` |
| 14 | K-means によるクラスタリング | NumPy による自作、`KMeans`、エルボー法 |
| 15 | 機械学習 API とモジュール設計 | FastAPI、Pydantic、レイヤードアーキテクチャ、`TestClient` による統合テスト |

## Kotlin 版執筆計画

Kotlin は JVM 上で動く静的型付けの言語で、data class・sealed interface・null 安全・拡張関数・高階関数を備える。Python 版で確定した 5 部 15 章の節構成に合わせ、同じ題材を Kotlin で書き起こす。Python 版との対比の軸は次の 3 つとする。

- **型**: Python の型ヒント（実行時には検査されない）に対し、Kotlin はコンパイル時に型と null を検査する。欠損値は `Double?` のような null 許容型で表し、補完するまでモデルに渡せないことを型で保証する
- **データの表現**: pandas・NumPy に対し、Kotlin DataFrame と `DoubleArray`・行列で表す。列名を文字列で扱う箇所と、data class で型付けする箇所の使い分けを見せる
- **探索**: Jupyter Lab に対し、IntelliJ IDEA の Kotlin Notebook と Kandy で探索する

付録 A（総合演習）は、計画どおり解答例を Python のみとし、Kotlin 版の記事は作らない（Kotlin 版トップから Python 版の付録 A へ案内する）。

### 確認した事実（2026-09-17 時点）

| 項目 | 確認内容 | 確認方法 |
|------|---------|---------|
| ローカル環境 | JDK 25.0.2、Gradle 8.11.1（scoop）。Kotlin コンパイラは未導入 | コマンドの `--version` |
| 参照実装 | `tmp/getting-started-tdd/apps/kotlin/` は Kotlin 2.1.0（Gradle プラグイン）、`jvmToolchain(21)`、`kotlin("test")` + JUnit Platform、CI は `nix develop .#kotlin` で `gradle build` / `gradle test` | ファイルを読んだ |
| Smile | 最新 6.3.0 のライセンスは GPL-3.0、2.6.0 は LGPL-3.0 | Maven Central の POM |
| Tribuo | 最新 4.3.2、Apache License 2.0。決定木・ランダムフォレスト（`RandomForestTrainer`）・K-means・線形モデルのモジュールがあり、行列の固有値分解（`DenseMatrix.EigenDecomposition`）を持つ。PCA のモジュールは見当たらない | Maven Central のモジュール一覧・JAR の中身 |
| Kotlin DataFrame | 安定版の最新 0.15.0（1.0.0 は開発版のみ）、Apache License 2.0 | Maven Central |
| Kandy（lets-plot） | 安定版の最新 0.8.5、Apache License 2.0 | Maven Central |
| そのほか | Kotlin 2.4.20、Ktor 3.6.0、kotlinx.serialization 1.11.0、Kover 0.9.9、detekt 1.23.8、ktlint 1.8.0、multik 0.3.1（Apache License 2.0） | Maven Central |

Tribuo の各アルゴリズムの細部（決定木の分割基準をジニ不純度にできるか、クラスの重み付け、ラッソ回帰の有無など）は未検証。B7 の ADR 002 で、章ごとに置き換え可能かを確かめてから確定する。

### ライブラリ方針（ADR 002 で確定する案）

| 用途 | 第一候補 | 理由 | 代替案 |
|------|---------|------|--------|
| データフレーム | Kotlin DataFrame 0.15.0 | JetBrains 製で Kotlin Notebook と統合されている | 標準ライブラリのコレクションと data class のみ |
| 行列演算 | 自作の小さな行列型（`DoubleArray`）と Tribuo の `DenseMatrix` | 正規方程式・固有値分解の仕組みを見せるため。依存を増やさない | multik 0.3.1 |
| 機械学習 | Tribuo 4.3.2 | Apache License 2.0 で、決定木・ランダムフォレスト・線形モデル・K-means がそろう | Smile 2.6.0（LGPL-3.0）。Smile 6.x は GPL-3.0 のため、採用するならリポジトリのライセンスとの整合を先に判断する |
| 可視化 | Kandy 0.8.5 | Kotlin Notebook で表示でき、Apache License 2.0 | lets-plot を直接使う |
| API | Ktor 3.6.0 + kotlinx.serialization | Kotlin 製で、`testApplication` による統合テストがある | Spring Boot |
| 静的解析・カバレッジ | detekt、ktlint、Kover | Kotlin の標準的な組み合わせ | — |

PCA（第 13 章）は Tribuo にモジュールが無いので、自作（Tribuo の固有値分解を利用）を最終実装とし、ライブラリへの置き換えの節は「ライブラリ未対応」と理由を書いて省略する。

### 前提整備（Kotlin）

| 項目 | 内容 | 状態 |
|------|------|------|
| Nix 環境 | `ops/nix/environments/kotlin/shell.nix`（JDK 21 + kotlin + gradle）を参照実装から追加し、`flake.nix` の `devShells` に登録する | 完了（CI での動作確認は B7 のステップ 7） |
| JDK とビルド | ローカルは JDK 25、Nix は JDK 21 なので、Gradle Wrapper でバージョンを固定し、`jvmToolchain(21)` で JDK を自動取得させる。ローカルの Gradle 8.11.1 で JDK 25 から Wrapper を動かせるかを最初に確認する | 完了（Gradle 8.11.1 は JDK 25 で Kotlin DSL を扱えないため、Wrapper の生成だけ JDK 21 で行った。Gradle 9.7.1 は JDK 25 で動作） |
| アプリ雛形 | `apps/kotlin/`（`settings.gradle.kts`・`build.gradle.kts`・`gradle/libs.versions.toml`・`src/main/kotlin`・`src/test/kotlin`）にテストが 1 本通る最小構成 | 完了 |
| 学習データ | `ML_DATA_DIR`（既定 `../data/sukkiri-ml`）で参照する。実データのテストは JUnit の `Assumptions` でデータが無ければスキップする | 完了 |
| Notebook 環境 | `apps/kotlin/notebooks/` に Kotlin Notebook を置く。出力セルは `nbstripout` で消し、検査は Python 版の `tools/notebooks.py` と同じく Gradle タスクから呼ぶ（呼び出し方は B7 で決める） | 未着手 |
| ライブラリ選定 | ADR 002（Kotlin 版のライブラリ）を作成する | 完了（ADR 002） |
| CI | `.github/workflows/kotlin-ci.yml`（Nix → Gradle のビルド・テスト・detekt・Kover）。参照実装の CI を雛形にし、Python CI と同じくキャッシュのパスを実在するものにする | 未着手 |

### 章別執筆計画（Kotlin）

| 章 | テーマ | Kotlin での焦点 | ライブラリへの置き換え |
|----|--------|----------------|--------------------|
| 1 | 機械学習とはじめてのテスト | `kotlin.test`、data class、`File.readLines` と BOM（`U+FEFF`）の除去、`Assumptions` による実データテストのスキップ | — |
| 2 | データの前処理と三角測量 | Kotlin DataFrame の CSV 読み込み、欠損値を `Double?` で表す null 安全、`kotlin.random.Random(seed)` による分割 | DataFrame の `fillNulls` と自作補完の突き合わせ |
| 3 | 決定木による分類と明白な実装 | sealed interface による `Leaf`／`Node`、`when` の網羅性、再帰、浮動小数点数の比較（`assertEquals` の許容誤差） | Tribuo の CART |
| 4 | バージョン管理とデータ管理 | Git フロー（言語共通）、`build/`・`.gradle/`・`.kotlin/` の除外、乱数シード | — |
| 5 | パッケージ管理と静的解析 | Gradle Kotlin DSL、バージョンカタログ（`libs.versions.toml`）、Gradle Wrapper、detekt・ktlint、Kover | — |
| 6 | タスクランナーと CI/CD | Gradle タスク、GitHub Actions と Nix、JDK ツールチェーン、Kotlin Notebook の導入と出力セルの削除 | — |
| 7 | 線形回帰による数値予測 | 自作の行列型と演算子オーバーロード（`times`・`plus`）、正規方程式、拡張関数による評価指標 | Tribuo の線形回帰 |
| 8 | 実践的な分類と前処理パイプライン | DataFrame の `groupBy` による補完、ダミー変数化、前処理を `interface Transformer` で合成、モデルの保存と読み込み | Tribuo の決定木とクラスの重み付け（可否は ADR 002 で確認） |
| 9 | 特徴量エンジニアリング | 標準化・多項式特徴量の自作、DataFrame の `join`、Shift_JIS の読み込み（`Charsets`） | 自作と Tribuo の標準化の突き合わせ（可否は ADR 002 で確認） |
| 10 | ロジスティック回帰とアンサンブル学習 | ソフトマックスと勾配降下、第 3 章の決定木を再利用したランダムフォレスト、`interface Classifier` による共通化 | Tribuo のロジスティック回帰・`RandomForestTrainer` |
| 11 | 評価指標と交差検証 | 関数型（`(List<T>, List<T>) -> Double`）で評価関数を渡す、K 分割、`Sequence` | Tribuo の評価器 |
| 12 | 正則化とモデル選択 | リッジ回帰の閉形式、data class の `copy` による実験結果の記録 | Tribuo の正則化付き線形モデル（ラッソの有無は ADR 002 で確認） |
| 13 | 主成分分析による次元削減 | 分散共分散行列と Tribuo の固有値分解、固有ベクトルの符号の扱い | ライブラリ未対応（理由を記事に書く） |
| 14 | K-means によるクラスタリング | 初期中心を引数で渡せる設計、エルボー法、`generateSequence` による反復 | Tribuo の K-means（同じ初期中心を渡せるかは ADR 002 で確認） |
| 15 | 機械学習 API とモジュール設計 | Ktor・kotlinx.serialization、レイヤードアーキテクチャ、`testApplication` による統合テスト、`Result`・sealed class によるエラー表現 | — |

可視化の節は、Python 版と同じ第 2・3・7〜14 章に設ける（Kandy で書き起こす）。

### Python 版との数値の違い

分割は Python 版と同じ手順（シード付きでシャッフルし、テスト件数を切り上げる）で自作するが、乱数生成器が NumPy と Kotlin で異なるため、**どの行が訓練データ・テストデータに入るかは Python 版と一致しない**。そのため正解率や係数などの数値は Python 版と一致しない。

- 件数（例: iris の 105 件と 45 件）は一致させる
- 記事の数値は Kotlin 版の実装で実測したものだけを載せる
- 同じ章で「自作」と「Tribuo」の結果を突き合わせる検証は、Kotlin 版の中で完結させる
- Python 版との比較は、傾向（深さと過学習の関係など）の比較にとどめ、多言語統合解説でまとめる

### Bolt 計画（Kotlin）

| Bolt | 内容 | 完了条件 |
|------|------|---------|
| B7 ウォーキングスケルトン | Kotlin の前提整備（Nix 環境・Gradle Wrapper・雛形・データ参照）、ADR 002、第 1 章の実装と記事、Kotlin 版トップ、nav、Kotlin CI | `apps/kotlin/` の第 1 章のテストが CI でグリーン。記事がサイトで表示される |
| B8 | 第 2〜3 章（Kotlin DataFrame・Tribuo の導入、最初の Kotlin Notebook と出力削除の仕組み） | 自作決定木と Tribuo の結果を並べて載せられる。Notebook の出力が CI の検査で残っていない |
| B9 | 第 4〜6 章 | Gradle タスク・detekt・Kover・CI が記事どおりに動く |
| B10 | 第 7〜9 章 | |
| B11 | 第 10〜12 章 | |
| B12 | 第 13〜15 章 | Kotlin 版の全章完了。Python 版と節構成がそろっている |

Python 版と同じく、B7・B8 で型（プロジェクト構成・テストの書き方・Notebook の運用・記事の体裁）を固めてから、B9 以降は依存関係の無い章をサブエージェントで並行して進める。第 10 章は第 3 章、第 15 章は第 7・8 章の実装に依存するので、依存先の完了後に着手する。

### 承認が必要な事項（Kotlin）

次の点を確認した（2026-09-17 承認）。

- [x] ライブラリの第一候補を Tribuo（Apache License 2.0）とし、GPL-3.0 の Smile 6.x を採用しないこと
- [x] PCA のライブラリへの置き換えを省略すること
- [x] 乱数生成器の違いにより、数値が Python 版と一致しないことを受け入れること
- [x] JDK 21 のツールチェーンと Gradle Wrapper でビルド環境を固定すること
- [x] 付録 A の Kotlin 版を作らないこと
- [x] B7（ウォーキングスケルトン）の範囲

TypeScript 版の章別執筆計画は、Kotlin 版の B8 の完了後に本ファイルへ追加する。

## リスクと対応

| リスク | 影響 | 対応 |
|--------|------|------|
| 学習データのライセンス特約（書籍購入者のみ利用可） | データを同梱すると PUBLIC リポジトリで再配布になる | データ・書籍コードをコミットしない。単体テストは自作フィクスチャで行う |
| CI で実データを使えない | 実データでの数値（正解率など）の劣化を CI で検知できない | 実データの検証はローカルのタスクで行い、記事に載せる数値は Bolt 完了時に再計測する。CI での実データ取得の可否は別途判断する |
| 言語による ML ライブラリの成熟度の差 | 章によってはライブラリへの置き換えができない | 自作を主とし、置き換えは任意の節にする。ADR で言語ごとの対応範囲を記録する |
| Notebook の出力に学習データが残る | 出力セル経由でデータを PUBLIC リポジトリに再配布してしまう | 出力セルを消してからコミットする仕組みを前提整備で用意し、レビューで確認する |
| Kotlin Notebook が IntelliJ IDEA に依存する | IDE を使わない読者が Kotlin の可視化を再現できない | 記事の環境構築の節に IntelliJ IDEA の導入を明記する。IDE 以外での実行方法は ADR の検討事項とする |
| 言語・ライブラリ間で結果が一致しない | 乱数・浮動小数点・分割アルゴリズムの違いで数値がずれ、読者が混乱する | 分割は自作関数で揃え、比較は許容誤差付きで行う。ずれの理由を記事に書く |
| Wiki 記事の数値が配布データと合わない | 件数・評価値をそのまま載せると読者の手元で再現しない | Wiki 記事の数値は使わず、実装の実測値のみ載せる |
| 章数が多く（15 章 × 14 言語）完走しにくい | シリーズが途中で止まる | 波ごとにリリースし、各波の完了時点でシリーズとして読める状態にする |

## 承認が必要な事項

本計画の承認とあわせて、次の点を確認した（2026-09-17 承認）。

- [x] 5 部 15 章＋付録の章構成と、書かないと決めた内容
- [x] 記事ディレクトリ名 `docs/article/getting-start-ml/` とファイル名
- [x] 学習データの配置先 `apps/data/sukkiri-ml/` と、実データテストをスキップする方針
- [x] 第 1 波を Python・Kotlin・TypeScript とし、Java を第 2 波に移すこと
- [x] Notebook による可視化を Python（Jupyter Lab）と Kotlin（Kotlin Notebook）だけで扱い、可視化の節を設ける章
- [x] B1（ウォーキングスケルトン）の範囲
