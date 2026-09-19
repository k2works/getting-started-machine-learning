---
type: Article
title: "執筆計画アウトライン"
description: "「機械学習から始めるプログラミング入門」シリーズの章構成・学習データの扱い・対象言語・Bolt 計画をまとめた執筆計画。"
tags: [article,getting-start-ml]
status: stable
generated: { by: claude-code/claude-opus-5, at: 2026-09-17T10:18:56Z }
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
| 段階的な言語拡大 | 第 1 波の 4 言語（Python・Kotlin・TypeScript・F#）で章構成と記事の型を固め、第 2 波・第 3 波で言語を広げる。F# は当初第 2 波だったが、2026-09-18 に第 1 波へ移した |
| Notebook による可視化は Python・Kotlin・F# のみ | データの探索と可視化は Python（Jupyter Lab）・Kotlin（Kotlin Notebook）・F#（Polyglot Notebooks）の記事だけで扱う。他の言語は可視化を扱わず、自作とライブラリの実装に集中する |
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

- 可視化ライブラリは、Python 版を [ADR 001](../../adr/001-python-ml-libraries.md)（matplotlib・seaborn）、Kotlin 版を [ADR 002](../../adr/002-kotlin-ml-libraries.md)（Kandy 0.8.0）で確定した。
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
| 1 | node | TypeScript | Vitest（npm） | ml.js 系パッケージ（「TypeScript 版執筆計画」を参照） | ML ライブラリが未成熟な言語での自作の価値を示す |
| 1 | dotnet | F# | xUnit | ML.NET, FSharp.Stats（「F# 版執筆計画」を参照） | 判別共用体・パイプライン・型プロバイダ。Polyglot Notebooks による可視化を扱う |
| 2 | java | Java | JUnit 5（Gradle） | Tribuo, Smile | 静的型付け OOP の代表。Kotlin 版の実装と対比する |
| 2 | dotnet | C# | xUnit | ML.NET, Microsoft.Data.Analysis | |
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
│   └── notebooks/        # F# の Polyglot Notebooks（出力セルを消してコミット）
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
| U4 F# | 第 1〜15 章、`apps/fsharp/`（Polyglot Notebooks を含む）、CI | U0、U1（章の節構成・可視化の節構成） |
| U5〜U9 第 2 波 | Java・C#・Scala・Rust・Go の各言語 | U0、U1 |
| U10〜U14 第 3 波 | Ruby・PHP・Elixir・Clojure・Haskell の各言語 | U0、U1 |
| U15 多言語統合解説 | `integration/` | 第 1 波の 4 言語完了後に着手し、波ごとに更新 |

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
| B23（完了） | 多言語統合解説（第 1 波の 4 言語。当初は B13 で 3 言語の予定だったが、F# を第 1 波に移し、Bolt 番号も TypeScript 版・F# 版に使ったため改めた） | 統合解説の各表で 4 言語の行・列が揃っている。数値と事実は各言語版の記事・ADR・実装から取る |

B23（多言語統合解説）は 2026-09-19 に完了した。第 1 波の 4 言語の記事・ADR 001〜004・実装から数値と事実を集め、新しい実測はせずに `integration/` の 5 章と索引を書いた。書いた後に出どころを 1 つずつ確かめ、出どころの無い断定を直した。

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
| Kandy（lets-plot） | 安定版の最新は 0.8.5 だが DataFrame 1.0.0-rc01 に依存する。DataFrame 0.15.0 と組み合わせられるのは 0.8.0（B8 で確認し ADR 002 を訂正）。Apache License 2.0 | Maven Central の POM |
| そのほか | Kotlin 2.4.20、Ktor 3.6.0、kotlinx.serialization 1.11.0、Kover 0.9.9、detekt 1.23.8、ktlint 1.8.0、multik 0.3.1（Apache License 2.0） | Maven Central |

Tribuo の各アルゴリズムの細部（決定木の分割基準をジニ不純度にできるか、クラスの重み付け、ラッソ回帰の有無など）は未検証。B7 の ADR 002 で、章ごとに置き換え可能かを確かめてから確定する。

### ライブラリ方針（ADR 002 で確定する案）

| 用途 | 第一候補 | 理由 | 代替案 |
|------|---------|------|--------|
| データフレーム | Kotlin DataFrame 0.15.0 | JetBrains 製で Kotlin Notebook と統合されている | 標準ライブラリのコレクションと data class のみ |
| 行列演算 | 自作の小さな行列型（`DoubleArray`）と Tribuo の `DenseMatrix` | 正規方程式・固有値分解の仕組みを見せるため。依存を増やさない | multik 0.3.1 |
| 機械学習 | Tribuo 4.3.2 | Apache License 2.0 で、決定木・ランダムフォレスト・線形モデル・K-means がそろう | Smile 2.6.0（LGPL-3.0）。Smile 6.x は GPL-3.0 のため、採用するならリポジトリのライセンスとの整合を先に判断する |
| 可視化 | Kandy 0.8.0 | Kotlin Notebook で表示でき、Apache License 2.0 | lets-plot を直接使う |
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
| Notebook 環境 | `apps/kotlin/notebooks/` に Kotlin Notebook を置く。出力セルの削除（`notebookStrip`）・検査（`notebookVerify`）・IDE なしの実行（`notebookExecute`）を Gradle タスクにする | 完了（B8。`notebookVerify` は `check` に組み込み CI で実行。`notebookExecute` は uv で kotlin-jupyter-kernel を一時取得する） |
| ライブラリ選定 | ADR 002（Kotlin 版のライブラリ）を作成する | 完了（ADR 002） |
| CI | `.github/workflows/kotlin-ci.yml`（Nix → Gradle のビルド・テスト・detekt・Kover）。参照実装の CI を雛形にし、Python CI と同じくキャッシュのパスを実在するものにする | 完了（B7 で追加し、B9 で detekt・Kover を追加。`./gradlew check` でテスト・ktlint・detekt・Notebook の出力検査を行い、`koverLog` でカバレッジを表示する。Gradle のキャッシュは `~/.gradle/caches`・`~/.gradle/wrapper`） |

### 章別執筆計画（Kotlin）

| 章 | テーマ | Kotlin での焦点 | ライブラリへの置き換え |
|----|--------|----------------|--------------------|
| 1 | 機械学習とはじめてのテスト | `kotlin.test`、data class、`File.readLines` と BOM（`U+FEFF`）の除去、`Assumptions` による実データテストのスキップ | — |
| 2 | データの前処理と三角測量 | Kotlin DataFrame の CSV 読み込み、欠損値を `Double?` で表す null 安全、`kotlin.random.Random(seed)` による分割 | DataFrame の `fillNulls` と自作補完の突き合わせ |
| 3 | 決定木による分類と明白な実装 | sealed interface による `Leaf`／`Node`、`when` の網羅性、再帰、浮動小数点数の比較（`assertEquals` の許容誤差） | Tribuo の CART（同数の多数決と同じ不純度の分割候補の選び方が違い、深さ 3 以上で 1 件の予測が違う。原因をテストで記録） |
| 4 | バージョン管理とデータ管理 | Git フロー（言語共通）、`build/`・`.gradle/`・`.kotlin/` の除外、乱数シード | — |
| 5 | パッケージ管理と静的解析 | Gradle Kotlin DSL、バージョンカタログ（`libs.versions.toml`）、Gradle Wrapper、detekt・ktlint、Kover | — |
| 6 | タスクランナーと CI/CD | Gradle タスク、GitHub Actions と Nix、JDK ツールチェーン、Kotlin Notebook の導入と出力セルの削除 | — |
| 7 | 線形回帰による数値予測 | 自作の行列型と演算子オーバーロード（`times`・`plus`）、正規方程式、拡張関数による評価指標 | Tribuo の線形回帰 |
| 8 | 実践的な分類と前処理パイプライン | DataFrame の `groupBy` による補完、ダミー変数化、前処理を `interface Transformer` で合成、モデルの保存と読み込み | Tribuo の CART（重み付けなしで突き合わせ。Tribuo に無いクラスの重み付けは自作の木で示した） |
| 9 | 特徴量エンジニアリング | 標準化・多項式特徴量の自作、DataFrame の `join`、Shift_JIS の読み込み（`Charsets`） | Tribuo の `MeanStdDevTransformation`（不偏標準偏差を使うことを確かめて突き合わせ） |
| 10 | ロジスティック回帰とアンサンブル学習 | ソフトマックスと勾配降下、第 3 章の決定木を再利用したランダムフォレスト、`interface Classifier` による共通化 | Tribuo のロジスティック回帰・`RandomForestTrainer` |
| 11 | 評価指標と交差検証 | 関数型（`(List<T>, List<T>) -> Double`）で評価関数を渡す、K 分割、`Sequence` | Tribuo の評価器 |
| 12 | 正則化とモデル選択 | リッジ回帰の閉形式、data class の `copy` による実験結果の記録 | Tribuo の `ElasticNetCDTrainer`（`l1Ratio` でラッソ回帰とリッジ回帰の両方を表せた） |
| 13 | 主成分分析による次元削減 | 分散共分散行列と Tribuo の固有値分解、固有ベクトルの符号の扱い | ライブラリ未対応（理由を記事に書く） |
| 14 | K-means によるクラスタリング | 初期中心を引数で渡せる設計、エルボー法、`generateSequence` による反復 | Tribuo の `KMeansTrainer`（初期中心を渡せないため SSE を比較） |
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
| B10 | 第 7〜9 章 | 完了 |
| B11 | 第 10〜12 章 | 完了 |
| B12 | 第 13〜15 章 | Kotlin 版の全章完了。Python 版と節構成がそろっている（完了） |

Python 版と同じく、B7・B8 で型（プロジェクト構成・テストの書き方・Notebook の運用・記事の体裁）を固めてから、B9 以降は依存関係の無い章をサブエージェントで並行して進める。第 10 章は第 3 章、第 15 章は第 7・8 章の実装に依存するので、依存先の完了後に着手する。

### 承認が必要な事項（Kotlin）

次の点を確認した（2026-09-17 承認）。

- [x] ライブラリの第一候補を Tribuo（Apache License 2.0）とし、GPL-3.0 の Smile 6.x を採用しないこと
- [x] PCA のライブラリへの置き換えを省略すること
- [x] 乱数生成器の違いにより、数値が Python 版と一致しないことを受け入れること
- [x] JDK 21 のツールチェーンと Gradle Wrapper でビルド環境を固定すること
- [x] 付録 A の Kotlin 版を作らないこと
- [x] B7（ウォーキングスケルトン）の範囲

B7〜B8（Kotlin 版の第 1 部）と B9（第 2 部）は 2026-09-17 に完了した。B9 では、detekt 1.23.8 が JDK 25 で動かないため Gradle デーモンの JDK を 21 に固定し（ADR 002）、MagicNumber の指摘のうち `MAX_DEPTHS` の値は設定（`ignorePropertyDeclaration`）で対象外にした。あわせて、Python CI の効いていなかった Nix ストアのキャッシュのステップを削除した。

B10〜B12（第 3〜5 部）も 2026-09-17 に完了し、Kotlin 版の全 15 章がそろった。着手前に、第 2 章の分割に正解ラベルの型引数を持たせ、各章で使う Tribuo のモジュールと Ktor を依存に追加した。そのうえで、第 1〜3 章にしか依存しない第 7〜14 章は章ごとに隔離した worktree のサブエージェントで並行して実装・執筆し、第 7・8 章に依存する第 15 章は両章の取り込み後に着手した。親は章ごとにコミットを取り込み、`./gradlew check`（データあり）・データなしのテスト・学習データの行の混入・BOM・絶対パス・画像・Notebook の出力を検査してから記事に OKF を適用した。各章で Tribuo について確かめた結果は ADR 002 に記録した。

## TypeScript 版執筆計画

第 1 波の最後の言語として、TypeScript 版を Python 版・Kotlin 版と同じ 5 部 15 章の節構成で書き起こす。TypeScript は、機械学習のライブラリが Python・JVM ほど成熟していない環境の代表として扱う。Python 版・Kotlin 版との対比の軸は次の 3 つとする。

- **型**: 構造的型付けと判別可能なユニオン（`type Tree = Leaf | Node`）、`number | null` による欠損値、`strict` モードの型チェック。型は実行時には消えるので、外部から来るデータ（CSV・API の入力）は実行時に検証する
- **データの表現**: データフレームのライブラリを使わず、1 行を `interface` で型付けしたレコードの配列と、列を取り出す小さな関数で表す。行列は自作の型と ml-matrix で扱う
- **自作の価値**: ライブラリが無い・古い領域（乱数のシード、正則化など）を自作で埋め、ライブラリがある領域は ml.js 系のパッケージと突き合わせる

Notebook による探索と可視化は、執筆計画の方針どおり TypeScript 版では扱わない。可視化の節がある章（第 2・3・7〜14 章）では、章の冒頭で Python 版・Kotlin 版の該当する節へ案内する。付録 A（総合演習）は Kotlin 版と同じく作らず、TypeScript 版トップから Python 版の付録 A へ案内する。

### 確認した事実（2026-09-17 時点）

| 項目 | 確認内容 | 確認方法 |
|------|---------|---------|
| ローカル環境 | Node.js 22.18.0、npm 10.9.3 | コマンドの `--version` |
| Nix 環境 | `ops/nix/environments/node/shell.nix` は `nodejs_20`・npm・TypeScript。本リポジトリの CI で `node` 環境を使うワークフローは無い | ファイルと `.github/workflows/` を読んだ |
| 参照実装 | `tmp/getting-started-tdd/apps/node/` は ESM（`"type": "module"`）、Vitest・ESLint（typescript-eslint）・Prettier・`tsc --noEmit`、npm scripts の `check` で品質チェックをまとめている | `package.json` を読んだ |
| TypeScript | 最新は 7.0.2、6 系の最新は 6.0.3、Apache-2.0。typescript-eslint 8.70.0 の peerDependencies は `typescript >=4.8.4 <6.1.0` で、7 系に対応していない | npm レジストリ |
| テスト・静的解析 | Vitest 5.0.1（MIT、`engines.node` は `^22.12.0 \|\| ^24.0.0 \|\| >=26.0.0`）、@vitest/coverage-v8 5.0.1、ESLint 10.10.0（`^20.19.0 \|\| ^22.13.0 \|\| >=24`）、Prettier 3.9.7 | npm レジストリ |
| データフレーム | danfojs-node 1.2.0（MIT）は 2025-04 以降更新が無く、`@tensorflow/tfjs-node` 3 系（ネイティブのバイナリ）と非推奨の `request` に依存する | npm レジストリ |
| 機械学習（ml.js 系、すべて MIT） | ml-matrix 6.15.0（2026-08 更新）、ml-regression-multivariate-linear 2.0.4、ml-cart 2.1.1、ml-random-forest 2.1.0、ml-logistic-regression 2.0.0、ml-kmeans 7.0.1（2026-06 更新）、ml-pca 4.1.1、ml-confusion-matrix 2.0.0、ml-cross-validation 1.3.0。多くは 2022 年から更新が無い。型定義を同梱しないパッケージがある（ml-cart・ml-logistic-regression は `types` の指定が無い） | npm レジストリ |
| K-means の初期中心 | ml-kmeans の `initialization` に初期中心（`number[][]`）を渡せる | パッケージの README |
| CSV・文字コード | csv-parse 7.0.2（MIT）。Node.js 22 の `TextDecoder('shift_jis')` で Shift_JIS を読める | npm レジストリ、実行して確認 |
| API | Hono 4.13.8、@hono/node-server 2.1.1、Fastify 5.12.5、zod 4.6.5（いずれも MIT） | npm レジストリ |

ml.js の各パッケージの細部（ml-cart の分割基準とクラスの重み、ml-logistic-regression の最適化手法、正則化付き回帰の有無、ESM からの読み込み方）は未検証。B13 の ADR 003 で、章ごとに置き換え可能かを確かめてから確定する。

### ライブラリ方針（ADR 003 で確定する案）

| 用途 | 第一候補 | 理由 | 代替案 |
|------|---------|------|--------|
| 言語・型チェック | TypeScript 6.0.3（`strict`） | typescript-eslint が 7 系に対応していないため 6 系に固定する | TypeScript 7 系（typescript-eslint の対応を待つ） |
| 実行環境 | Node.js 22（Nix の `node` 環境を `nodejs_22` に上げる） | Vitest 5・ESLint 10 の要件を満たし、手元の 22.18.0 と一致する。Node.js 20 はサポートが終了している | Node.js 24 |
| テスト・カバレッジ | Vitest 5 + @vitest/coverage-v8 | TypeScript を変換なしで扱え、参照実装と同じ | Node.js の `node:test` |
| 静的解析・整形 | ESLint 10 + typescript-eslint、Prettier | 参照実装と同じ | Biome |
| データの表現 | 型付きレコードの配列と自作の列関数、csv-parse | データフレームのライブラリが古くネイティブ依存を持つため。型で列スキーマを表す題材になる | danfojs-node |
| 乱数 | シード付きの疑似乱数生成器を自作する | `Math.random` はシードを指定できないため。第 2 章の TDD の題材にする | seedrandom |
| 機械学習 | ml.js 系（ml-matrix・ml-cart・ml-random-forest・ml-logistic-regression・ml-kmeans・ml-pca など） | MIT で、決定木・ランダムフォレスト・K-means・PCA がそろう | 自作のみ（置き換えの節を省略） |
| API | Hono + @hono/node-server + zod | `app.request` でサーバーを起動せずに統合テストを書け、依存が小さい | Fastify |
| 可視化 | なし | 執筆計画の方針（可視化は Python・Kotlin のみ） | — |

### 前提整備（TypeScript）

| 項目 | 内容 | 状態 |
|------|------|------|
| Nix 環境 | `ops/nix/environments/node/shell.nix` の Node.js を 22 に上げ、`nix develop .#node` で Node.js・npm が使えることを CI で確かめる | 完了（`nodejs_22` に更新し、CI で Node.js 22.21.1・npm 10.9.4 を確認） |
| アプリ雛形 | `apps/node/`（`package.json`・`package-lock.json`・`tsconfig.json`・`vitest.config.ts`・`eslint.config.mjs`・`src/`・`test/`）にテストが 1 本通る最小構成。`.nvmrc` か `engines` で Node.js の版を明示する | 完了（`.nvmrc` と `engines` の両方。`.npmrc` の `save-exact` で正確な版を記録） |
| 学習データ | `ML_DATA_DIR`（既定 `../data/sukkiri-ml`）で参照する。実データのテストは Vitest の `it.skipIf`（`describe.skipIf`）でデータが無ければスキップする | 完了（`describe.skipIf`） |
| ライブラリ選定 | ADR 003（TypeScript 版のライブラリ）を作成する | 完了（ADR 003） |
| CI | `.github/workflows/node-ci.yml`（Nix → `npm ci` → `npm run check`、カバレッジの表示）。キャッシュは npm のキャッシュの実在するパスにする | 完了（B13。Nix の Node.js 22.21.1 で `npm run check` とカバレッジの表示が成功。キャッシュは `~/.npm`） |

### 章別執筆計画（TypeScript）

| 章 | テーマ | TypeScript での焦点 | ライブラリへの置き換え |
|----|--------|--------------------|--------------------|
| 1 | 機械学習とはじめてのテスト | Vitest、`interface` による行の型、`readFileSync` と BOM の除去、`describe.skipIf` による実データテストのスキップ | — |
| 2 | データの前処理と三角測量 | CSV を型付きレコードに変換、欠損値を `number \| null` で表す、シード付き疑似乱数生成器の自作、ジェネリクスの `splitTrainTest<T>` | — |
| 3 | 決定木による分類と明白な実装 | 判別可能なユニオン（`kind`）による `Leaf`／`Node`、`never` による網羅性の検査、再帰、浮動小数点数の比較（`toBeCloseTo`） | ml-cart（境界ちょうどの値の左右・同数の多数決の選び方・既定の利得の下限が違い、実データでは深さ 3 で 1 件の予測が違う。原因をテストで記録） |
| 4 | バージョン管理とデータ管理 | Git フロー（言語共通）、`node_modules/`・`coverage/`・`dist/` の除外、`package-lock.json` のコミット、乱数シード | — |
| 5 | パッケージ管理と静的解析 | npm と `package-lock.json`、`tsconfig.json` の `strict`、ESLint（typescript-eslint）・Prettier、Vitest のカバレッジ、型定義の無いパッケージの扱い | — |
| 6 | タスクランナーと CI/CD | npm scripts による `check` の集約、GitHub Actions と Nix、Node.js の版の固定 | — |
| 7 | 線形回帰による数値予測 | 自作の行列クラス（演算子オーバーロードが無いのでメソッドで表す）、正規方程式、評価指標 | ml-matrix の解法・ml-regression-multivariate-linear |
| 8 | 実践的な分類と前処理パイプライン | グループ別の補完、ダミー変数化、`interface Transformer` によるパイプライン、JSON によるモデルの保存と読み込み | ml-cart（クラスの重み付けの可否は ADR 003 で確認） |
| 9 | 特徴量エンジニアリング | 標準化・多項式特徴量の自作、`Map` による表の結合、`TextDecoder('shift_jis')` による読み込み | 自作の標準化との突き合わせ（ライブラリの有無は ADR 003 で確認） |
| 10 | ロジスティック回帰とアンサンブル学習 | ソフトマックスと勾配降下、第 3 章の決定木を再利用したランダムフォレスト、`interface Classifier` による共通化 | ml-logistic-regression・ml-random-forest |
| 11 | 評価指標と交差検証 | 関数型（`(actual: T[], predicted: T[]) => number`）で評価関数を渡す、K 分割、ジェネレーター（`function*`） | ml-confusion-matrix・ml-cross-validation |
| 12 | 正則化とモデル選択 | リッジ回帰の閉形式、`Readonly` とスプレッド構文による実験結果の記録 | 正則化付き回帰のパッケージの有無は ADR 003 で確認（無ければ置き換えの節を省略） |
| 13 | 主成分分析による次元削減 | 分散共分散行列と ml-matrix の固有値分解、固有ベクトルの符号の扱い | ml-pca |
| 14 | K-means によるクラスタリング | 初期中心を引数で渡せる設計、エルボー法、ジェネレーターによる反復 | ml-kmeans（同じ初期中心を渡して結果を突き合わせる） |
| 15 | 機械学習 API とモジュール設計 | Hono と zod による入力の検証、レイヤードアーキテクチャ、`app.request` による統合テスト、判別可能なユニオンの `Result` 型によるエラー表現 | — |

### Python 版・Kotlin 版との数値の違い

分割は Python 版・Kotlin 版と同じ手順（シード付きでシャッフルし、テスト件数を切り上げる）で行うが、TypeScript 版では疑似乱数生成器を自作するので、**どの行が訓練データ・テストデータに入るかは Python 版・Kotlin 版と一致しない**。数値の扱いは Kotlin 版と同じとする。

- 件数（例: iris の 105 件と 45 件）は一致させる
- 記事の数値は TypeScript 版の実装で実測したものだけを載せる
- 自作とライブラリの突き合わせは TypeScript 版の中で完結させる

### Bolt 計画（TypeScript）

Kotlin 版で、第 1〜3 章の型を固めた後は依存関係の無い章を worktree のサブエージェントで並行して進められることを確かめた。TypeScript 版もこの進め方を使い、Bolt を 5 つにまとめる。

| Bolt | 内容 | 完了条件 |
|------|------|---------|
| B13 ウォーキングスケルトン（完了） | Nix の `node` 環境の更新、`apps/node/` の雛形、ADR 003、第 1 章の実装と記事、TypeScript 版トップ、nav、Node CI | `apps/node/` の第 1 章のテストが CI でグリーン。記事がサイトで表示される |
| B14（完了） | 第 2〜3 章（型付きレコード、シード付き乱数、ml.js の導入） | 自作の決定木と ml-cart の結果を並べて載せられる |
| B15（完了） | 第 4〜6 章 | npm scripts・ESLint・Prettier・カバレッジ・CI が記事どおりに動く |
| B16（完了） | 第 7〜14 章（依存関係の無い章を並行して進める） | 各章のテストが通り、記事がそろっている |
| B17（完了） | 第 15 章 | TypeScript 版の全章完了。Python 版と節構成がそろっている |

B16 の前に、共有するファイル（`package.json` の依存、第 2 章の分割の型など）を親が整えてから並行作業に入る。第 15 章は第 7・8 章の実装に依存するので、B16 の取り込み後に着手する。

### 承認が必要な事項（TypeScript）

次の点を確認した（2026-09-17 承認）。

- [x] データフレームのライブラリを使わず、型付きレコードの配列で表すこと
- [x] TypeScript を 6.0 系に、Node.js を 22 に固定し、Nix の `node` 環境を `nodejs_22` に上げること
- [x] 機械学習のライブラリを ml.js 系とし、置き換えの範囲を ADR 003 で章ごとに確かめること
- [x] API を Hono + zod で作ること
- [x] 可視化の節と付録 A を TypeScript 版では作らず、Python 版・Kotlin 版へ案内すること
- [x] Bolt を B13〜B17 の 5 つにまとめ、B16 で第 7〜14 章を並行して進めること
- [x] B13（ウォーキングスケルトン）の範囲

B15〜B17（第 4〜15 章）は 2026-09-18 に完了し、TypeScript 版の全 15 章がそろった。着手前に第 7〜15 章で使う ml.js 系・Hono・zod を `package.json` に追加し、第 4〜6 章と第 7〜14 章を worktree のサブエージェントで並行して書き、第 7・8 章の取り込み後に第 15 章を書いた。B15 では ESLint 10 関連の依存が Node.js 22.13.0 以上を求めることが分かり、`engines` の下限を上げた。並行して書いた章の記事に載っていたリポジトリ全体のテスト件数は統合後の値と合わないので、章ごとの実測値に置き換えた。ml.js の各パッケージで確かめた癖（ml-logistic-regression に切片が無い、ml-regression-lasso の `lambda` の尺度と収束しないまま返す挙動、ml-cross-validation の余りの行の扱いなど）は ADR 003 に記録した。

## F# 版執筆計画

F# は当初第 2 波の言語だったが、2026-09-18 に第 1 波へ移し、第 1 波の 4 番目の言語として書き起こす。Python 版と同じ 5 部 15 章の節構成で、Kotlin 版と同じく Notebook による探索と可視化の節を設ける。Notebook には Polyglot Notebooks（.NET Interactive の F# カーネル）を使う。Python 版・Kotlin 版・TypeScript 版との対比の軸は次の 3 つとする。

- **型**: 判別共用体（`type Tree = Leaf of string | Node of Split * Tree * Tree`）と網羅性の検査、`option` による欠損値、レコード型と型推論。Kotlin の sealed interface、TypeScript の判別可能なユニオンと比べる
- **データの表現**: 型プロバイダ（FSharp.Data の `CsvProvider`）で CSV の列を型にする。学習データはコミットしないので、型の元にするサンプルは架空の値で作る。処理は `|>` のパイプラインと `List`・`Array`・`Seq` のモジュール関数で組み立てる
- **ライブラリ**: .NET の機械学習ライブラリ（ML.NET）は C# 向けの API（`IDataView`・可変なクラス）なので、F# から使うときの型の橋渡しを題材にする。数値計算は F# 向けの FSharp.Stats と比べる

付録 A（総合演習）は Kotlin 版・TypeScript 版と同じく作らず、F# 版トップから Python 版の付録 A へ案内する。

### 確認した事実（2026-09-18 時点）

| 項目 | 確認内容 | 確認方法 |
|------|---------|---------|
| Polyglot Notebooks | Microsoft は 2026-02-11 に Polyglot Notebooks と .NET Interactive の廃止を告知した。拡張機能は 2026-03-27、.NET Interactive は 2026-04-24 に廃止され、リポジトリはアーカイブされた。インストール済みの拡張機能は動き続け、.NET Interactive は他の Jupyter のフロントエンドのカーネルとしても動くが、機能追加・バグ修正は無く、将来の VS Code・.NET SDK の更新で動かなくなる可能性がある。Microsoft は代替として VS Code の Jupyter 拡張機能と別のカーネルを挙げている | [dotnet/interactive#4163](https://github.com/dotnet/interactive/issues/4163) |
| .NET Interactive の版 | dotnet ツール `Microsoft.dotnet-interactive` の最新は 1.0.712001。Plotly.NET.Interactive（Notebook でグラフを表示する拡張）の最新は 5.0.0 | NuGet |
| ローカル環境 | .NET SDK 10.0.101 | `dotnet --list-sdks` |
| Nix 環境 | `ops/nix/environments/dotnet/shell.nix` は `dotnet-sdk`（版は nixpkgs の既定）。本リポジトリの CI で `dotnet` 環境を使うワークフローは無い | ファイルと `.github/workflows/` を読んだ |
| 参照実装 | `tmp/getting-started-tdd/apps/fsharp/` は F# のライブラリとテストのプロジェクトを 1 つのソリューションにまとめ、xUnit 2 系・coverlet、`fsharplint.json` を使う（`net8.0`） | ファイルを読んだ |
| ライブラリの最新版 | ML.NET（Microsoft.ML・Microsoft.ML.FastTree）5.0.0、FSharp.Stats 0.6.0、FSharp.Data 8.2.0、Deedle 8.1.0、Plotly.NET 5.1.0、Giraffe 8.3.0、xunit.v3 4.0.1、Fantomas 8.0.0、FSharpLint 0.27.0 | NuGet |

ライセンス、F# からの使いやすさ、各アルゴリズムの有無（ML.NET に単一の決定木があるか、正則化付き回帰・PCA・K-means の初期中心の指定など）、.NET Interactive が .NET SDK 10 で動くかは未検証。B18 の ADR 004 で確かめてから確定する。

### ライブラリ方針（ADR 004 で確定する案）

| 用途 | 第一候補 | 理由 | 代替案 |
|------|---------|------|--------|
| 言語・実行環境 | F#（.NET SDK 10、`net10.0`） | 手元の SDK と一致し、長期サポート版 | .NET 8 |
| テスト・カバレッジ | xUnit（v3）+ coverlet | 参照実装と同じ系統で、`dotnet test` で動く | Expecto、FsUnit |
| 静的解析・整形 | Fantomas（整形）、FSharpLint、コンパイラの警告をエラーにする（`TreatWarningsAsErrors`） | 参照実装で FSharpLint を使っている | — |
| データの表現 | FSharp.Data の `CsvProvider`（架空の値のサンプルから型を作る）とレコード型のリスト | 型プロバイダは F# 固有の題材になる | Deedle のデータフレーム |
| 乱数 | `System.Random(seed)` | シードを指定できる。.NET の版で乱数列が変わりうることを第 4 章で扱う | 自作 |
| 機械学習 | ML.NET（決定木系・ロジスティック回帰・K-means など）、FSharp.Stats（線形回帰・PCA など） | ML.NET は .NET の標準的な機械学習ライブラリ。F# 向けの API は FSharp.Stats が持つ | 自作のみ（置き換えの節を省略） |
| API | Giraffe（ASP.NET Core）、`Microsoft.AspNetCore.TestHost` による統合テスト | 本計画の「言語ごとの焦点」で挙げた候補 | ASP.NET Core Minimal API |
| 可視化 | Polyglot Notebooks + Plotly.NET（Plotly.NET.Interactive） | 2026-09-18 の方針変更による。廃止のリスクは下記のとおり扱う | F# スクリプト（`.fsx`）と Plotly.NET の HTML 出力 |

### Polyglot Notebooks の廃止への対応

- 使う版（VS Code の拡張機能、`Microsoft.dotnet-interactive`、Plotly.NET.Interactive）を ADR 004 と記事に明記し、固定する
- ADR 004 と F# 版の記事に、廃止されていることと、将来の VS Code・.NET SDK の更新で動かなくなる可能性があることを明記する
- Notebook は探索と可視化に限り、テスト・記事の数値は `apps/fsharp/` のプロジェクトのコードから求める。Notebook が動かなくなっても、実装・テスト・記事の数値は影響を受けない
- 動かなくなった時点で、F# スクリプトと Plotly.NET の HTML 出力（代替案）に移すかを判断する

### 前提整備（F#）

| 項目 | 内容 | 状態 |
|------|------|------|
| Nix 環境 | `nix develop .#dotnet` の .NET SDK の版を CI で確かめ、`global.json` で SDK の版を固定するか決める | 完了（nixpkgs の `dotnet-sdk` は 8.0 だったので `dotnet-sdk_10` に変更。CI で 10.0.101 を確認し、`global.json` で 10.0.101 に固定） |
| アプリ雛形 | `apps/fsharp/`（ソリューション、F# のライブラリとテストのプロジェクト、`Directory.Build.props`・`Directory.Packages.props` による版の一元管理）にテストが 1 本通る最小構成 | 完了（xUnit v3 を Microsoft.Testing.Platform で実行。FSharp.Core を明示して参照） |
| 学習データ | `ML_DATA_DIR`（既定 `../data/sukkiri-ml`）で参照する。実データのテストは、データが無ければスキップする（xUnit v3 の `Assert.Skip` など） | 完了（`Assert.SkipUnless`。既定の場所は `__SOURCE_DIRECTORY__` から求める） |
| Notebook 環境 | VS Code の Polyglot Notebooks で F# のカーネルが動き、Plotly.NET のグラフを表示できることを確かめる。出力セルを消す仕組みは Python 版・Kotlin 版と同じものを使う | 確認済み（`Microsoft.dotnet-interactive` 1.0.712001 が .NET 10 で動き、Jupyter から画面なしで実行できた。nbstripout で出力を消せる。Notebook の追加は B19） |
| ライブラリ選定 | ADR 004（F# 版のライブラリ）を作成する | 完了（ADR 004） |
| CI | `.github/workflows/fsharp-ci.yml`（Nix → `dotnet restore` → 整形の確認・静的解析・`dotnet test`、カバレッジの表示）。NuGet のキャッシュを使う | 完了（B18。整形の確認・FSharpLint・`dotnet test` とカバレッジの表示が成功） |

### 章別執筆計画（F#）

| 章 | テーマ | F# での焦点 | ライブラリへの置き換え |
|----|--------|------------|--------------------|
| 1 | 機械学習とはじめてのテスト | xUnit、レコード型、`File.ReadAllLines` と BOM、データが無いときのスキップ | — |
| 2 | データの前処理と三角測量 | `CsvProvider` と架空のサンプル、`option` による欠損値、`System.Random(seed)` による分割、ジェネリックな関数 | — |
| 3 | 決定木による分類と明白な実装 | 判別共用体による木、パターンマッチと網羅性の警告、再帰 | ML.NET の決定木系（単一の決定木が無ければ、置き換えの範囲を ADR 004 で決める） |
| 4 | バージョン管理とデータ管理 | Git フロー（言語共通）、`bin/`・`obj/` の除外、乱数と .NET の版 | — |
| 5 | パッケージ管理と静的解析 | NuGet と中央パッケージ管理、`packages.lock.json`、Fantomas・FSharpLint、警告をエラーにする設定、coverlet | — |
| 6 | タスクランナーと CI/CD | `dotnet` CLI と Gulp の分担、GitHub Actions と Nix、Notebook の出力を消す仕組み | — |
| 7 | 線形回帰による数値予測 | 配列による行列と正規方程式、評価指標 | FSharp.Stats・ML.NET の線形回帰 |
| 8 | 実践的な分類と前処理パイプライン | グループ別の補完、ダミー変数化、関数の合成（`>>`）によるパイプライン、モデルの保存と読み込み | ML.NET（クラスの重み付けの可否は ADR 004 で確認） |
| 9 | 特徴量エンジニアリング | 標準化・多項式特徴量の自作、`Map` による表の結合、Shift_JIS の読み込み（`CodePagesEncodingProvider`） | ML.NET の正規化 |
| 10 | ロジスティック回帰とアンサンブル学習 | ソフトマックスと勾配降下、第 3 章の決定木を再利用したランダムフォレスト | ML.NET のロジスティック回帰・FastForest |
| 11 | 評価指標と交差検証 | 評価関数を関数の型で渡す、K 分割、`seq` 式による遅延評価 | ML.NET の評価・交差検証 |
| 12 | 正則化とモデル選択 | リッジ回帰の閉形式、不変なレコードによる実験結果の記録 | FSharp.Stats・ML.NET の正則化（有無は ADR 004 で確認） |
| 13 | 主成分分析による次元削減 | 分散共分散行列と固有値分解、固有ベクトルの符号 | FSharp.Stats の PCA |
| 14 | K-means によるクラスタリング | 初期中心を引数で渡せる設計、エルボー法 | ML.NET・FSharp.Stats の K-means（初期中心を渡せるかは ADR 004 で確認） |
| 15 | 機械学習 API とモジュール設計 | Giraffe、レイヤードアーキテクチャ、`Result` 型によるエラー表現、TestHost による統合テスト | — |

Notebook は Kotlin 版と同じ章（第 2・3・7〜14 章）に作り、`apps/fsharp/notebooks/` に置く。Notebook から `apps/fsharp/` のライブラリを読み込み、記事の可視化の節と同じグラフを描く。

### Python 版・Kotlin 版・TypeScript 版との数値の違い

分割は他の言語と同じ手順で行うが、乱数生成器が違うので、訓練データ・テストデータに入る行は他の言語と一致しない。件数は一致させ、記事の数値は F# 版の実装で実測したものだけを載せ、自作とライブラリの突き合わせは F# 版の中で完結させる。

### Bolt 計画（F#）

TypeScript 版と同じく、第 1〜3 章で型を固めてから、依存関係の無い章を worktree のサブエージェントで並行して進める。

| Bolt | 内容 | 完了条件 |
|------|------|---------|
| B18 ウォーキングスケルトン（完了） | Nix の `dotnet` 環境の確認、`apps/fsharp/` の雛形、ADR 004、第 1 章の実装と記事、F# 版トップ、nav、F# CI、Polyglot Notebooks の動作確認（Plotly.NET のグラフを 1 つ表示する） | `apps/fsharp/` の第 1 章のテストが CI でグリーン。記事がサイトで表示される。Polyglot Notebooks が手元で動く |
| B19（完了） | 第 2〜3 章と Notebook（型プロバイダ、乱数、ML.NET の導入） | 自作の決定木とライブラリの結果を並べて載せられる。Notebook の可視化が記事と一致する |
| B20（完了） | 第 4〜6 章 | NuGet・Fantomas・FSharpLint・カバレッジ・CI・Notebook の出力の除去が記事どおりに動く |
| B21（完了） | 第 7〜14 章と Notebook（依存関係の無い章を並行して進める） | 各章のテストが通り、記事と Notebook がそろっている |
| B22（完了） | 第 15 章 | F# 版の全章完了。Python 版と節構成がそろっている |

### 承認が必要な事項（F#）

次の点を確認した（2026-09-18 承認）。

- [x] F# を第 2 波から第 1 波へ移し、第 1 波を Python・Kotlin・TypeScript・F# の 4 言語にすること（多言語統合解説は 4 言語の完了後に着手する）
- [x] F# 版の Notebook に Polyglot Notebooks を使い、廃止のリスクを ADR 004 と記事に明記すること（Polyglot Notebooks を使うのは F# 版だけとし、Python 版・Kotlin 版の Notebook と、Notebook を作らない TypeScript 版はそのままにする）
- [x] ライブラリの第一候補を ML.NET・FSharp.Stats・FSharp.Data・Plotly.NET・Giraffe とし、置き換えの範囲を ADR 004 で章ごとに確かめること
- [x] 実装を `apps/fsharp/`、記事を `docs/article/getting-start-ml/fsharp/` に置くこと
- [x] Bolt を B18〜B22 の 5 つにまとめ、B21 で第 7〜14 章を並行して進めること
- [x] B18（ウォーキングスケルトン）の範囲

B19（第 2〜3 章と Notebook）は 2026-09-19 に完了した。特徴量は列名から値への `Map<string, float option>` で表し、型プロバイダの型は読み込みの入り口だけで使う。`CsvProvider` は `Schema` を指定しないと小数の列を `decimal`、空欄のある列を `string` と推論するので、`Schema` で `float option` を指定した。第 3 章は 1 本だけの FastTree を `OneVersusAll` で多クラスにして突き合わせ、深さ 2 でテストデータの予測が 45 件すべて一致した。Notebook の表示とライブラリの癖は ADR 004 に記録した。

B20（第 4〜6 章）は 2026-09-19 に完了した。B19 と並行して worktree のサブエージェントで書いた。第 5 章の執筆で、`fsharplint.json` が既定の設定を置き換える仕様のため、B18 以来 FSharpLint のルールが 1 件も有効になっていなかったことが分かり、既定の設定を写して 42 ルールを有効にした。第 6 章で Notebook の出力を検査・除去する F# スクリプト（`tools/notebooks.fsx`）を作り、CI と `apps:check:fsharp` に組み込んだ。同じ日に、C# 版と場所が重ならないよう実装を `apps/dotnet/` から `apps/fsharp/` に移し、CI も `fsharp-ci.yml` に改めた。

B21（第 7〜14 章と Notebook）と B22（第 15 章）は 2026-09-19 に完了し、F# 版の全 15 章がそろった。第 7〜14 章は 4 つの worktree のサブエージェントで並行して書き始めたが、第 7・8・10・13 章をコミットしたところで利用上限により止まり、途中だった第 9・11・12・14 章は親が worktree の未コミットの変更を引き継いで仕上げた。第 15 章のドメイン・予測サービス・入力の検証・API は第 7・8 章を待たずに先に書き、第 7・8 章の取り込み後にモデルの置き場と学習を加えた。取り込みでは、`apps/dotnet/` への新規ファイルと `.fsproj`・`Program.fs` の衝突を、章の順に並べ直して解消した。ライブラリで確かめた癖（FSharp.Stats 0.6.0 のリッジ回帰が使えない、ML.NET の SDCA の L2 の尺度、型プロバイダの `AssumeMissingValues` など）は ADR 004 に記録した。

## リスクと対応

| リスク | 影響 | 対応 |
|--------|------|------|
| 学習データのライセンス特約（書籍購入者のみ利用可） | データを同梱すると PUBLIC リポジトリで再配布になる | データ・書籍コードをコミットしない。単体テストは自作フィクスチャで行う |
| CI で実データを使えない | 実データでの数値（正解率など）の劣化を CI で検知できない | 実データの検証はローカルのタスクで行い、記事に載せる数値は Bolt 完了時に再計測する。CI での実データ取得の可否は別途判断する |
| 言語による ML ライブラリの成熟度の差 | 章によってはライブラリへの置き換えができない | 自作を主とし、置き換えは任意の節にする。ADR で言語ごとの対応範囲を記録する |
| Notebook の出力に学習データが残る | 出力セル経由でデータを PUBLIC リポジトリに再配布してしまう | 出力セルを消してからコミットする仕組みを前提整備で用意し、レビューで確認する |
| Polyglot Notebooks が廃止されている | 将来の VS Code・.NET SDK の更新で F# 版の Notebook が動かなくなる | 使う版を固定して記事に明記する。Notebook は探索と可視化に限り、実装・テスト・記事の数値を Notebook に依存させない。動かなくなったら F# スクリプトと Plotly.NET の HTML 出力への移行を判断する |
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
