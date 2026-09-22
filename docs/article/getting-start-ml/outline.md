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
  - { by: human:kakimomokuri, at: 2026-09-19T14:03:49Z }
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
| 2 | scala | Scala | ScalaTest（sbt） | Tribuo（「Scala 版執筆計画」を参照。Smile は Scala 3 版がすべて GPL-3.0 のため使わない） | |
| 2 | rust | Rust | cargo test | linfa, ndarray, polars | 所有権と数値計算 |
| 2 | go | Go | go test | gonum（「Go 版執筆計画」を参照。決定木・K-means は自作） | ライブラリが限定的なので自作の比重が大きい |
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
| U5〜U9 第 2 波 | Java・C#・Scala・Rust・Go の各言語（「第 2 波の執筆計画」を参照） | U0、U1、第 1 波の対比の相手（Java は U2、C# は U4 など） |
| U10〜U14 第 3 波 | Ruby・PHP・Elixir・Clojure・Haskell の各言語（「第 3 波の執筆計画」を参照） | U0、U1 |
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

## 第 2 波の執筆計画

第 2 波は Java・C#・Scala・Rust・Go の 5 言語（U5〜U9）を対象にする。第 1 波の 4 言語で固めた型（5 部 15 章の節構成、記事の体裁、`ML_DATA_DIR` とデータが無いときのスキップ、言語ごとの ADR、言語ごとの CI）をそのまま使う。本節は 2026-09-19 に承認した。各言語の「確認した事実」「ライブラリ方針」「前提整備」「章別執筆計画」をその言語のウォーキングスケルトンの中で書き足す。

### 第 1 波の実績

| 言語 | Bolt | 期間 | 進め方と詰まった点 |
|------|------|------|------------------|
| Python | B1〜B6 | 2026-09-17（1 日） | 参照実装。第 2 章以降は依存の無い章をサブエージェントで並行して書いた |
| Kotlin | B7〜B12 | 2026-09-17（1 日） | detekt が JDK 25 で動かず、Gradle デーモンの JDK を 21 に固定した |
| TypeScript | B13〜B17 | 2026-09-17〜18（約 1.5 日） | ml.js の各パッケージの癖（切片が無い、収束しないまま返すなど）の確認に時間を使った |
| F# | B18〜B22 | 2026-09-18〜19（約 1.5 日） | worktree のサブエージェントが利用上限で止まり、親が未コミットの変更を引き継いだ。`fsharplint.json` の設定で既定のルールが無効になっていた |
| 統合解説 | B23 | 2026-09-19（約 0.5 日） | 新しい実測はせず、各言語版の記事・ADR・実装から数値と事実を集めた |

実績から次のことが分かった。

- 1 言語は 5 Bolt（ウォーキングスケルトン → 第 2〜3 章 → 第 4〜6 章 → 第 7〜14 章 → 第 15 章）で 1〜1.5 日かかる。時間の多くはライブラリの癖の確認に使う
- 静的解析の設定が本当に効いているか（ルールが 1 件以上有効か）を、ウォーキングスケルトンの時点で確かめる
- 並行作業は利用上限で止まることがある。サブエージェントは章ごとに小さくコミットさせ、親が引き継げるようにする

### 言語の順番

既存の言語版の実装を対比の相手として使える言語から順に書く。

| 順 | 言語 | Unit | 対比の相手 | 理由 |
|----|------|------|-----------|------|
| 1 | Java | U5 | Kotlin 版 | Gradle・Tribuo・JUnit を Kotlin 版と共有でき、Kotlin 版の実装を Java に書き直す形で進められる |
| 2 | C# | U6 | F# 版 | .NET SDK・ML.NET・xUnit v3・Nix の `dotnet` 環境を F# 版と共有できる |
| 3 | Scala | U7 | Java 版・Kotlin 版 | JVM と Smile を使う。Smile のライセンスを先に確かめる必要がある |
| 4 | Go | U9 | TypeScript 版 | ライブラリ（gonum）が限られ自作の比重が大きい点で、TypeScript 版と進め方が近い |
| 5 | Rust | U8 | Go 版・Python 版 | 所有権と行列演算を扱う。linfa の対応範囲の確認に最も時間がかかる見込みなので最後にする |

### 共通の方針（案）

| 項目 | 方針 |
|------|------|
| 実装の置き場所 | `apps/java/`・`apps/dotnet/`（C#）・`apps/scala/`・`apps/go/`・`apps/rust/`。C# は F# 版を `apps/fsharp/` に移したので `apps/dotnet/` を使う。ディレクトリ名は Nix の環境名に合わせる |
| 記事の置き場所 | `docs/article/getting-start-ml/{java,csharp,scala,go,rust}/` |
| Notebook と可視化の節 | TypeScript 版と同じく作らず、可視化は Python 版・Kotlin 版へ案内する。Notebook の出力に学習データが残るリスクと、カーネルの保守のコストを避けるため |
| 付録 A | 作らず、Python 版の付録 A へ案内する |
| ライブラリ | 言語ごとに ADR（005 Java、006 C#、007 Scala、008 Go、009 Rust）を作り、ウォーキングスケルトンの中で版・ライセンス・アルゴリズムの有無を確かめてから確定する。ライブラリに無いアルゴリズムは自作を最終実装にする |
| CI | 言語ごとに `.github/workflows/{java,csharp,scala,go,rust}-ci.yml` を追加する（Nix → ビルド・整形の確認・静的解析・テスト・カバレッジの表示） |
| 数値 | 記事に載せる数値は、その言語版の実装で実測した値だけにする |
| 統合解説 | 第 2 波の 5 言語の完了後にまとめて更新する（言語ごとには更新しない） |

### 確認すべき事実

次の点は未確認であり、各言語のウォーキングスケルトンで確かめる。

| 言語 | 確認すること |
|------|------------|
| Java | Nix の `jdk` の版（Kotlin 版は JDK 21 に固定した）。Tribuo の最新版と、Kotlin 版で確かめた癖が Java でも同じか。Smile を併用するかどうか |
| C# | ローカルの .NET SDK は 10.0.100 で、F# 版の `global.json`（10.0.101）と違う。C# 版で版をどう固定するか。Microsoft.Data.Analysis の保守状況 |
| Scala | Smile の版ごとのライセンス（本計画の「対象言語」の注意書き）。Scala 3 と sbt の版。ローカルに `scala` コマンドが無い |
| Go | gonum の対応範囲（線形回帰・PCA は可能、決定木・K-means は自作になる見込み） |
| Rust | linfa の各クレートの対応範囲と保守状況、ndarray・polars の版 |

### Bolt 計画（第 2 波）

第 1 波の TypeScript 版・F# 版と同じく、1 言語を 5 Bolt で進める。言語をまたいだ並行作業はせず、1 言語ずつ完了させる。言語の中では、第 2〜3 章と第 4〜6 章、第 7〜14 章の各章を worktree のサブエージェントで並行して進める。

| Bolt | 言語 | 内容 | 完了条件 |
|------|------|------|---------|
| B24 ウォーキングスケルトン（完了） | Java | Nix の `java` 環境の確認、`apps/java/` の雛形、ADR 005、第 1 章の実装と記事、Java 版トップ、nav、Java CI | 第 1 章のテストが CI でグリーン。記事がサイトで表示される。静的解析のルールが有効になっている |
| B25（完了） | Java | 第 2〜3 章 | 自作の決定木とライブラリの結果を並べて載せられる |
| B26（完了） | Java | 第 4〜6 章 | ビルド・静的解析・カバレッジ・CI が記事どおりに動く |
| B27（完了） | Java | 第 7〜14 章 | 各章のテストが通り、記事がそろっている |
| B28（完了） | Java | 第 15 章 | Java 版の全章完了。Python 版と節構成がそろっている |
| B29〜B33（完了） | C# | B24〜B28 と同じ区切り（ADR 006、`apps/csharp/`） | C# 版の全章完了 |
| B34〜B38（完了） | Scala | 同上（ADR 007、`apps/scala/`） | Scala 版の全章完了 |
| B39〜B43 | Go | 同上（ADR 008、`apps/go/`） | Go 版の全章完了（2026-09-20 完了） |
| B44〜B48 | Rust | 同上（ADR 009、`apps/rust/`） | Rust 版の全章完了（2026-09-21 完了） |
| B49 | 統合解説 | `integration/` の各章と索引に第 2 波の 5 言語を加える | 統合解説の各表で 9 言語の行・列がそろっている（2026-09-21 完了） |

目安は 1 言語 1〜1.5 日、第 2 波全体で 6〜8 日とする。各言語の完了時に、実績をもとに次の言語の見積もりを見直す。第 3 波の Bolt 計画は第 2 波の完了時に作る。

### 承認が必要な事項（第 2 波）

次の点を確認した（2026-09-19 承認）。

- [x] 言語の順番を Java → C# → Scala → Go → Rust とすること
- [x] 第 2 波では Notebook・可視化の節・付録 A を作らず、Python 版・Kotlin 版へ案内すること
- [x] C# の実装を `apps/dotnet/` に置くこと（残っている F# のビルドの中間ファイルは B29 の前に消す）
- [x] 1 言語を 5 Bolt とし、Bolt 番号を B24〜B49 とすること
- [x] 統合解説の更新を第 2 波の完了後にまとめて行うこと（B49）
- [x] B24（Java のウォーキングスケルトン）の範囲

## Java 版執筆計画

Java は第 2 波の最初の言語で、Kotlin 版の実装を対比の相手にする。Python 版と同じ 5 部 15 章の節構成で書き、Notebook・可視化の節・付録 A は作らない（「第 2 波の執筆計画」の承認による）。Kotlin 版との対比の軸は次の 3 つとする。

- **型**: record と sealed interface（Java 17 以降）で、Kotlin の data class と sealed interface と同じことを表す。欠損値は Kotlin の null 許容型に対し `OptionalDouble` か null で表し、コンパイラが null を検査しない違いを見せる
- **データの表現**: Kotlin DataFrame に対し、record のリストと Stream API で表す。データフレームのライブラリは使わない
- **ライブラリ**: Tribuo は Java 製なので、Kotlin 版で書いた橋渡しのコードが Java ではどう見えるかを比べる

### 確認した事実（2026-09-19 時点）

| 項目 | 確認内容 | 確認方法 |
|------|---------|---------|
| ローカル環境 | JDK 25.0.2 | `java -version` |
| Nix 環境 | `ops/nix/environments/java/shell.nix` は `jdk`・`maven`・`gradle`（nixpkgs の既定で JDK 21.0.8、Gradle 8.14.3、Maven 3.9.12）。`flake.nix` の `devShells` に登録済み。本リポジトリの CI で `java` 環境を使うワークフローは無い | ファイルと `nix eval` |
| Kotlin 版の構成 | Gradle Wrapper 9.7.1、`jvmToolchain(21)`、Gradle デーモンの JDK を 21 に固定（`gradle-daemon-jvm.properties`）、`gradle/libs.versions.toml` で版を一元管理 | ファイルを読んだ |
| Tribuo | 最新は 4.3.2 のまま（Maven Central の最終更新は 2025-04-08）。Kotlin 版で確かめた癖は ADR 002 にある | Maven Central |
| そのほか | JUnit 6.1.3、AssertJ 3.27.7（4.0.0 はマイルストーン版のみ）、Spotless の Gradle プラグイン 8.10.2、google-java-format 1.36.1、Error Prone 2.50.0、PMD 7.27.0、Checkstyle 14.1.0、JaCoCo 0.8.15、Javalin 7.2.3 | Maven Central |

| ライセンス | Tribuo・AssertJ・Error Prone・google-java-format・Spotless・Javalin は Apache License 2.0、JUnit と JaCoCo は EPL-2.0、PMD は BSD 系。いずれもライブラリとして使う範囲で本リポジトリに制約を課さない | Maven Central の POM |
| 必要な JDK | JUnit 6.1.3・Javalin 7.2.3 はクラスファイルが Java 17（major 61）、Error Prone 2.50.0・google-java-format 1.36.1 は Java 21（major 65）、Tribuo 4.3.2・PMD 7.27.0 は Java 8（major 52）。JDK 21 のツールチェーンですべて動く | JAR のクラスファイルの版 |
| Gradle プラグイン | Error Prone は `net.ltgt.errorprone` 5.1.1、Spotless は `com.diffplug.spotless` 8.10.2。PMD と JaCoCo は Gradle に組み込みのプラグインで `toolVersion` を指定する | Gradle Plugin Portal |

Tribuo の各アルゴリズムの振る舞いは Kotlin 版で確かめた ADR 002 を起点にし、Java 版の各章で実装しながら ADR 005 に書き足す。

### ライブラリ方針（ADR 005 で確定する案）

| 用途 | 第一候補 | 理由 | 代替案 |
|------|---------|------|--------|
| 言語・ビルド | Java 21（ツールチェーン）、Gradle Wrapper 9.7.1（Kotlin DSL）、`libs.versions.toml` | Kotlin 版と同じ JDK・Gradle にそろえ、デーモンの JDK の問題を同じ方法で避ける | Maven |
| テスト | JUnit 6 + AssertJ 3.27.7 | JUnit は「対象言語」の表のテスト基盤。AssertJ は安定版を使う | JUnit 5 |
| 整形・静的解析 | Spotless（google-java-format）、Error Prone、PMD | 整形・コンパイル時の検査・規約の検査を分ける | Checkstyle |
| カバレッジ | JaCoCo | Gradle に組み込みのプラグインで動く | — |
| データの表現 | record のリストと Stream API | Kotlin 版の data class と対比しやすく、依存を増やさない | Tablesaw |
| 機械学習 | Tribuo 4.3.2 | Kotlin 版と同じライブラリで、Java から直接使える | Smile 2.6.0（LGPL-3.0） |
| API | Javalin 7 | 「言語ごとのバリエーション」の候補。Kotlin 版の Ktor と同じく軽量 | Spring Boot |

### 前提整備（Java）

| 項目 | 内容 | 状態 |
|------|------|------|
| Nix 環境 | `nix develop .#java` で JDK・Gradle が使えることを CI で確かめる | 完了（手元の Nix は JDK 21.0.8、Java CI は 21.0.9。どちらでも `./gradlew check` が成功。CI のカバレッジは実データのテストがスキップされるので 85.2%、手元は 99.2%） |
| アプリ雛形 | `apps/java/`（`settings.gradle.kts`・`build.gradle.kts`・`gradle/libs.versions.toml`・Gradle Wrapper・`src/main/java`・`src/test/java`）にテストが 1 本通る最小構成 | 完了（Gradle Wrapper・デーモンの JDK の設定は Kotlin 版から写した） |
| 学習データ | `ML_DATA_DIR`（既定 `../data/sukkiri-ml`）で参照する。実データのテストは JUnit の `Assumptions` でデータが無ければスキップする | 完了 |
| 静的解析 | Spotless・Error Prone・PMD を `./gradlew check` に組み込み、わざと違反を入れて `check` が失敗することを確かめる | 完了（3 つとも失敗を確かめた。結果は ADR 005） |
| ライブラリ選定 | ADR 005（Java 版のライブラリ）を作成する | 完了（ADR 005） |
| CI | `.github/workflows/java-ci.yml`（Nix → `./gradlew check` → カバレッジの表示）。Gradle のキャッシュは Kotlin CI と同じパス | 完了（キャッシュのキーは `gradle-java-` で Kotlin CI と分けた） |
| タスク | `ops/scripts/apps.js` に Java を加え、`apps:check:java` で手元の検査を実行できるようにする | 完了 |

### 章別執筆計画（Java）

| 章 | テーマ | Java での焦点 | ライブラリへの置き換え |
|----|--------|--------------|--------------------|
| 1 | 機械学習とはじめてのテスト | JUnit 6、record、`Files.readAllLines` と BOM、データが無いときのスキップ | — |
| 2 | データの前処理と三角測量 | null と `OptionalDouble` による欠損値、シード付きの `Random` による分割、ジェネリクス | — |
| 3 | 決定木による分類と明白な実装 | sealed interface と record による木、`switch` のパターンマッチと網羅性の検査 | Tribuo の CART |
| 4 | バージョン管理とデータ管理 | Git フロー（言語共通）、`build/` の除外 | — |
| 5 | パッケージ管理と静的解析 | Gradle とバージョンカタログ、依存の固定、Spotless・Error Prone・PMD、JaCoCo | — |
| 6 | タスクランナーと CI/CD | Gradle のタスクと Gulp の分担、GitHub Actions と Nix | — |
| 7 | 線形回帰による数値予測 | `double[][]` を包む不変の `Matrix` と正規方程式、`LinkedHashMap` で列の順を保つ係数 | Tribuo の `SLMTrainer(true)`・`LARSTrainer` |
| 8 | 実践的な分類と前処理パイプライン | `Transformer` と `FittedTransformer` のインターフェースによるパイプライン、Java のシリアライズと `ObjectInputFilter` の許可リスト | Tribuo の CART（重み付けは自作） |
| 9 | 特徴量エンジニアリング | `Standardizer` の record、TSV と Shift_JIS の読み込み（`MalformedInputException`） | Tribuo の `MeanStdDevTransformation` |
| 10 | ロジスティック回帰とアンサンブル学習 | モデル共通の `Classifier` インターフェースとアダプター、第 3 章の決定木を再利用したランダムフォレスト | Tribuo の `LogisticRegressionTrainer`・`RandomForestTrainer` |
| 11 | 評価指標と交差検証 | 評価関数を `java.util.function` の関数型インターフェースで渡す、K 分割交差検証 | Tribuo の評価器・`KFoldSplitter` |
| 12 | 正則化とモデル選択 | 閉形式のリッジ回帰（第 7 章の `Matrix` の `solve`）、座標降下法のラッソ回帰 | Tribuo の `ElasticNetCDTrainer` |
| 13 | 主成分分析による次元削減 | 分散共分散行列と Tribuo の固有値分解、固有ベクトルの符号 | なし（Tribuo に PCA が無い） |
| 14 | K-means によるクラスタリング | 初期中心を引数で渡せる設計、空のクラスタの扱い、エルボー法 | Tribuo の `KMeansTrainer` |
| 15 | 機械学習 API とモジュール設計 | Javalin、パッケージによる層の分離、統合テスト | — |

第 7〜14 章の Java での焦点は、各章の実装を終えた 2026-09-20 に書き足した。

### B24 のステップ計画（Java のウォーキングスケルトン）

各ステップは TDD で進め、ステップごとにコミットする。

| ステップ | 内容 | 完了条件 |
|---------|------|---------|
| 1 | ライブラリの事実確認：ライセンス、JUnit 6・Error Prone・Javalin 7 の要求する JDK、Tribuo を Java から使うときの依存。結果を本節の「確認した事実」に書き足す | 「未検証」の項目が無くなる |
| 2 | ADR 005 を書く（ステップ 1 の結果にもとづく。章ごとの置き換えの範囲は ADR 002 を起点にする） | ADR 005 が `docs/adr/` と索引・nav にある |
| 3 | `apps/java/` の雛形：Gradle Wrapper 9.7.1、`jvmToolchain(21)`、デーモンの JDK 21、バージョンカタログ、`SetupTest` が 1 本通る | 手元と `nix develop .#java` の両方で `./gradlew test` が成功する |
| 4 | 整形・静的解析・カバレッジ：Spotless・Error Prone・PMD・JaCoCo を `check` に組み込む。わざと違反を入れて `check` が失敗することを確かめてから戻す（F# 版でルールが無効だった教訓） | 違反を入れると `check` が失敗し、戻すと成功する |
| 5 | 第 1 章の実装：Python 版・Kotlin 版と同じ TODO リストを TDD で進める。実データのテストは `Assumptions` でスキップする | データありで全テストが通り、データなしでは実データのテストがスキップされる。正解率が Kotlin 版と一致する |
| 6 | 記事：第 1 章、Java 版トップ（`java/index.md`）、シリーズ索引の言語一覧、`mkdocs.yml` の nav | ローカルのプレビューで表示される。記事の数値が実装の実測値と一致する |
| 7 | CI とタスク：`.github/workflows/java-ci.yml`、`ops/scripts/apps.js` への Java の追加 | push 後に Java CI がグリーン。`apps:check:java` が手元で成功する |
| 8 | 仕上げ：学習データの行の混入・BOM・絶対パスの検査、記事への OKF の適用、本計画の前提整備の状態と Bolt の完了、`docs/log.md` の更新 | 検査に指摘が無く、`okf:check` が ERROR 0 |

B24（Java のウォーキングスケルトン）は 2026-09-19 に完了した。静的解析は、わざと違反を入れたファイルで Spotless・Error Prone・PMD のそれぞれが `check` を失敗させることを確かめてから第 1 章に入った。Error Prone の警告も `-Werror` でエラーになるので、仮実装の段階に本実装の `stripBom` が残っていると `UnusedMethod` でコンパイルが止まった。PMD の quickstart は名前の無いパッケージ（`NoPackage`）を指摘するので、`SetupTest` も `setup` パッケージに置いた。ツールで書いたソースと記事の `\uFEFF` のエスケープが BOM の文字そのものに置き換わっていたので、エスケープに戻し、BOM の文字の混入を仕上げの検査に加えた。第 1 章の正解率は Python 版・Kotlin 版と同じ 0.7368。Java CI の初回は、カバレッジを表示するステップの `run:` に「`: `」を含む値をそのまま書いて YAML として不正になり、ブロック形式に直した。

### 承認が必要な事項（Java）

次の点を確認した（2026-09-19 承認）。

- [x] Java 版の対比の軸（型・データの表現・ライブラリ）と、データフレームのライブラリを使わないこと
- [x] ライブラリの第一候補（JUnit 6・AssertJ・Spotless・Error Prone・PMD・JaCoCo・Tribuo・Javalin 7）を ADR 005 で確定すること
- [x] Gradle の設定を Kotlin DSL で書き、JDK・Gradle の版を Kotlin 版にそろえること
- [x] B24 のステップ 1〜8

### B25 のステップ計画（第 2〜3 章）

#### データの表し方（第 2 章以降のすべての章に効く判断）

Kotlin 版は Kotlin DataFrame の表（列名で値を引く）でデータを持ち、決定木も列名で特徴量を引く。Java 版はデータフレームのライブラリを使わないので、次の形にする案とする。

| 対象 | 表し方（案） | 理由 |
|------|------------|------|
| 読み込んだ行 | `record Row(Map<String, String> cells)`。CSV のセルの文字列をそのまま持ち、`OptionalDouble number(String column)`（空欄なら空）と `String text(String column)` で読み出す（2026-09-20 に `Map<String, Double>` から改めた。正解ラベルや第 8 章の文字列の特徴量を持てないため） | 第 7〜14 章では列の違う CSV を何種類も読むので、データセットごとに record を作るより、列名で引く形のほうが章をまたいで使い回せる。F# 版の `Map<string, float option>` と同じ考え方。空欄は `OptionalDouble` の空として呼び出し側に見せる |
| 補完した後の特徴量 | `record Features(List<String> columns, double[] values)`。欠損値を持てない | 補完するまでモデルに渡せないことを型で分ける。Kotlin 版の `Double?` と `Double` の区別に当たる。Tribuo の `ArrayExample`（特徴量名の配列と `double` の配列）にもそのまま渡せる |
| 分割の結果 | `record TrainTestSplit<T>(List<Features> xTrain, List<Features> xTest, List<T> tTrain, List<T> tTest)` | Kotlin 版と同じ形。正解ラベルの型を型引数にして、第 7 章の数値の正解ラベルにも使う |
| 決定木 | `sealed interface Tree permits Leaf, Node` と record の `Leaf`・`Node`。予測は `switch` のパターンマッチで書く | 網羅性をコンパイラが検査する。Kotlin 版の sealed interface と `when` に当たる |

代替案は、iris 専用の record（`IrisRow(Double sepalLength, ...)`）を作る形。型は強くなるが、章ごとにデータセット専用の型と、列名で特徴量を選ぶ仕組みの両方が要るので採らない。

#### 乱数と数値

分割は `java.util.Random(seed)` と `Collections.shuffle` で行う。Kotlin 版の `kotlin.random.Random` とは乱数列が違うので、訓練データとテストデータに入る行は Kotlin 版と一致しない。件数（105 件と 45 件）は一致させ、正解率などの数値は Java 版の実測値を載せる。Tribuo との突き合わせは Java 版の中で完結させる。

#### ステップ

| ステップ | 内容 | 完了条件 |
|---------|------|---------|
| 1 | 第 2 章の読み込み：`Row` と、BOM 付きで空欄を含む CSV の読み込み、列ごとの欠損値の数 | 架空の値の CSV で、BOM が列名に残らず、空欄が欠損値になるテストが通る |
| 2 | 第 2 章の前処理：欠損値を除いた列ごとの平均値、補完して `Features` にする、特徴量と正解ラベルに分ける | 元の行を変更しないことを含めてテストが通る |
| 3 | 第 2 章の分割：`splitTrainTest` を三角測量で進める（割合どおりの件数、重複の無さ、特徴量とラベルの対応、同じシードで同じ分け方、数値のラベル） | Kotlin 版と同じ 8 つの観点のテストが通る |
| 4 | 第 2 章の実データ：iris.csv の欠損値の数（2・1・2・2）、105 件と 45 件への分割と補完、`Main` の表示 | データありで通り、データなしでスキップされる |
| 5 | 第 3 章の決定木：ジニ不純度、最良の分割、`Tree` の構築と予測、深さの制限、学習前の予測のエラー、木の表示 | Kotlin 版と同じ観点のテストが通る |
| 6 | 第 3 章の Tribuo：`tribuo-classification-tree` を依存に加え、`Features` を `ArrayExample` に変える橋渡し、CART との突き合わせ。一致しない場合の理由（同数の多数決・同じ不純度の分割候補）を ADR 002 の結果と照らしてテストに残す | 突き合わせのテストが通る。Kotlin 版と違う振る舞いが出たら ADR 005 に書く |
| 7 | 第 3 章の実データ：深さごとの正解率と深さ 2 の木を表示する `Main` | 記事に載せる数値をテストで固定する |
| 8 | 記事：第 2 章・第 3 章を書き、Java 版トップ・シリーズ索引・nav に登録する。途中段階の出力は、その段階を再現して実測する | 記事の数値・出力が実測と一致する。サイトのビルドでリンク切れが無い |
| 9 | 仕上げ：学習データの行・BOM の文字・絶対パスの検査、OKF、執筆計画の B25 の完了、`docs/log.md`、push して Java CI とサイトの公開を確かめる | 検査に指摘が無く、CI がグリーン |

第 2 章と第 3 章は、第 3 章が第 2 章の `Features` と分割に依存するので、並行させずに順に進める。

### 承認が必要な事項（B25）

次の点を確認した（2026-09-20 承認）。

- [x] データの表し方（読み込んだ行をセルの文字列の `Map<String, String>` を包む `Row`（当初の `Map<String, Double>` から 2026-09-20 に改めた）、補完後を欠損値を持てない `Features`、決定木を sealed interface と record）
- [x] 乱数に `java.util.Random` を使い、数値は Java 版の実測値を載せること
- [x] B25 のステップ 1〜9

### B25〜B28 の進め方（2026-09-20、目標「Java 執筆完了」による）

2026-09-20 に人から「Java 執筆完了」を目標として受け取ったので、B25 の途中から B28 までを続けて進める。各 Bolt のステップは B25 と同じ形（TDD で実装 → 実データで確認 → 記事 → 検査）とし、並行作業の割り当てを次のとおりにする。

| 作業 | 担当 | 依存 |
|------|------|------|
| 第 2〜3 章の実装 | 親 | — |
| 第 2〜3 章の記事 | サブエージェント A | 第 2〜3 章の実装 |
| 第 4〜6 章（実装と記事） | サブエージェント B | 第 1〜3 章 |
| 第 7〜8 章・第 9〜10 章・第 11〜12 章・第 13〜14 章（実装と記事） | サブエージェント C〜F | 第 1〜3 章 |
| 第 15 章 | 親 | 第 7・8 章 |
| 索引・nav・執筆計画・ログ・ADR 005 の統合 | 親 | 各章の取り込み |

- 各サブエージェントは隔離した worktree で作業し、章ごとにコミットする。利用上限で止まっても親が引き継げるように、実装と記事は章ごとに分けてコミットする
- 依存（Tribuo の各モジュール）は親が先に加えた。サブエージェントは `build.gradle.kts`・`libs.versions.toml`・`mkdocs.yml`・シリーズ索引・Java 版トップ・執筆計画・`docs/log.md` を変更せず、必要なら報告に書く。ADR 005 に書くべき結果も報告に書き、親が書き足す
- 親は章ごとに取り込み、`./gradlew check`（データあり）・データなしのテスト・学習データの行の混入・BOM の文字・絶対パスを検査してから記事に OKF を適用する

### Java 版の完了（2026-09-20）

B25〜B28 は 2026-09-20 に完了し、Java 版の全 15 章がそろった。親が第 2〜3 章と第 15 章を実装し、第 2〜3 章の記事・第 4〜6 章・第 7〜14 章（5 つに分けて）・第 15 章の記事を worktree のサブエージェントで並行して書いた。第 11〜14 章は第 7・9 章の取り込み後に着手した。親は章ごとに取り込み、`./gradlew check`（データあり）・データなしのテスト（実データのテスト 70 件がスキップされる）・学習データの行の混入・BOM の文字・絶対パスを検査した。各章で Tribuo・Javalin について確かめた結果は ADR 005 の「各章で確かめた結果」に記録した。

- B25 の着手時に、`Row` の中身を `Map<String, Double>` から、セルの文字列を持つ `Map<String, String>` に改めた（人の判断による）。正解ラベルや第 8 章の文字列の特徴量を持てないため
- `Features` は `double[]` を持つので、Error Prone の `ArrayRecordComponent` に従い record ではなくクラスにした
- 分割に `java.util.Random` を使うので、訓練データとテストデータの行は Kotlin 版と一致しない。その結果、第 9 章の交互作用の項・第 12 章のリッジ回帰・第 8 章のクラスの重み付けの結論が Kotlin 版と分かれた章がある。記事ではそれを、分割しだいで結論が変わる例として扱った
- 第 15 章の統合テストが、6 つのサブエージェントが同時に Gradle を動かしていた間に 1 回だけ 503 のはずが 400 で失敗した。その後の単独実行 3 回と全体の `check` 5 回では再現せず、原因は分かっていない
- `verifyNoBomCharacter` タスクを `check` に加え、ソースに BOM の文字がそのまま入ると失敗するようにした。Kotlin CI のキャッシュのキーの接頭辞を `gradle-kotlin-` にして、Java CI と分けた

## C# 版執筆計画

C# は第 2 波の 2 番目の言語で、F# 版の実装を対比の相手にする。Python 版と同じ 5 部 15 章の節構成で書き、Notebook・可視化の節・付録 A は作らない（「第 2 波の執筆計画」の承認による）。F# 版と同じ .NET・ML.NET・xUnit v3 を使うので、違いは言語の書き方と API の見え方に絞られる。対比の軸は次の 3 つとする。

- **型**: F# の判別共用体とパターンマッチに対し、C# は record と sealed interface、`switch` 式のパターンマッチで表す。網羅性の検査の効き方（C# は既定では警告）を比べる。欠損値は F# の `option` に対し null 許容参照型（NRT）で表す
- **データの表現**: F# の型プロバイダ（`CsvProvider`）に対し、C# は record のリストと LINQ で表す。Java 版と同じく、列名で引く自作の表を使うかは B29 で決める
- **ライブラリ**: ML.NET は C# 向けの API（`IDataView`・可変なクラス・属性による列の対応づけ）なので、F# 版が書いた「型の橋渡し」が C# では不要になる箇所を示す

### 確認した事実（2026-09-20 時点）

| 項目 | 確認内容 | 確認方法 |
|------|---------|---------|
| ローカル環境 | .NET SDK は 8.0.416・9.0.308・10.0.100。F# 版の `global.json` は 10.0.101 を `rollForward: latestPatch` で指定しているので、手元の 10.0.100 では解決できない（F# 版は Nix の 10.0.101 で動かしている） | `dotnet --list-sdks`、`global.json` |
| Nix 環境 | `nix develop .#dotnet` の .NET SDK は 10.0.101。Java 版と同じく CI もこの環境で動かす | `dotnet --version` |
| F# 版の構成 | ソリューション、`Directory.Build.props`（`TargetFramework` net10.0、`TreatWarningsAsErrors`、`RestorePackagesWithLockFile`）、`Directory.Packages.props`（中央パッケージ管理）、`.config/dotnet-tools.json`（fantomas・dotnet-fsharplint）、`.editorconfig` | ファイルを読んだ |
| ライブラリの最新版 | Microsoft.ML 5.0.0、Microsoft.Data.Analysis 0.23.0（0.24.0 はプレビュー）、xunit.v3 4.0.1、coverlet.MTP 10.0.1、Roslynator.Analyzers 5.0.0、SonarAnalyzer.CSharp 10.34.0.3385、Microsoft.CodeAnalysis.NetAnalyzers 10.0.401、StyleCop.Analyzers は安定版が 1.1.118 で 1.2.0 はベータ | NuGet |
| 実装の置き場所 | `apps/dotnet/` は空になっていた（F# 版を `apps/fsharp/` に移した後に残っていたビルドの中間ファイル）。C# 版は `apps/csharp/` に置く（2026-09-20、人の指示による。F# 版の `apps/fsharp/` と対になる） | ファイルを確認 |

| ライセンス | Microsoft.ML 5.0.0・Microsoft.Data.Analysis 0.23.0・coverlet.MTP 10.0.1 は MIT、xunit.v3 4.0.1 は Apache-2.0 | NuGet のカタログ |
| Microsoft.Data.Analysis の保守（B29） | 安定版の最新 0.23.0 は 2025-11-11 の公開で、以後はプレビュー（0.24.0-preview）だけ。1.0 に達していない | NuGet のカタログ |
| `global.json` の版（B29） | `version` を 10.0.100・`rollForward` を `latestPatch` にすると、手元（10.0.100）でも Nix（10.0.101）でも解決できた。F# 版は 10.0.101 固定なので手元では解決できない | 使い捨てのプロジェクトで `dotnet --version` |
| アナライザーの水準（B29） | `AnalysisMode` を `All` にすると、公開メソッドの引数の null 検査（CA1062）まで求められて記事のコードが読みにくくなる。`Recommended` では CA1304・CA1311（カルチャの指定）・CA1822（static にできる）などが出る。`TreatWarningsAsErrors` により、コンパイラの警告（CS0219 など）もエラーになる | 使い捨てのプロジェクトでビルド |
| `dotnet format`（B29） | 対象のソリューションにプロジェクトが登録されていないと、何も検査せずに成功する。登録すると `WHITESPACE` の指摘が出る。`.editorconfig` を置いて規則を明示する | 同上 |
| `dotnet test`（B29） | `apps/csharp` を作業ディレクトリにして実行する。`global.json` の `test.runner` は最も近い `global.json` から読まれるので、リポジトリのルートから実行すると古い VSTest の経路に落ちる。改名（`apps/dotnet` → `apps/csharp`）の後、古い状態のビルドサーバーが残っていると 0 件と判定されることがあり、`dotnet build-server shutdown` で解消した | 手元（10.0.100）と Nix（10.0.101）で実行、F# 版（313 件）とも比較 |

ML.NET を C# から使うときの癖は、F# 版の ADR 004 を起点に各章で確かめる。

### ライブラリ方針（ADR 006 で確定する案）

| 用途 | 第一候補 | 理由 | 代替案 |
|------|---------|------|--------|
| 言語・実行環境 | C#（.NET SDK、`net10.0`）。`global.json` は 10.0.100 + `rollForward: latestPatch` にして、手元と Nix の両方で動くようにする | F# 版と同じ環境にそろえる | .NET 8 |
| テスト・カバレッジ | xUnit v3（Microsoft.Testing.Platform）+ coverlet.MTP | F# 版と同じ | NUnit、MSTest |
| 整形 | `dotnet format`（SDK 同梱）と `.editorconfig` | 追加の依存が要らない | CSharpier |
| 静的解析 | .NET アナライザー（`AnalysisMode: Recommended`）+ 警告をエラーにする（`TreatWarningsAsErrors`）。追加のアナライザーは使わない（B29 で判断） | SDK 同梱で、C# の標準的な解析 | Roslynator、SonarAnalyzer.CSharp、StyleCop（安定版が 1.1.118 で古い） |
| データの表現 | record のリストと LINQ。列名で引く表が要るかは B29 で決める | Java 版と同じ考え方で、ライブラリを増やさない | Microsoft.Data.Analysis の `DataFrame`（0.x） |
| 乱数 | `System.Random(seed)` | F# 版と同じ。分割の結果も F# 版と一致するはず（B29 で確かめる） | 自作 |
| 機械学習 | ML.NET（Microsoft.ML・Microsoft.ML.FastTree） | .NET の標準的な機械学習ライブラリ。F# 版で癖を確かめてある（ADR 004） | 自作のみ |
| API | ASP.NET Core Minimal API と `Microsoft.AspNetCore.Mvc.Testing`（または `TestHost`） | 「言語ごとのバリエーション」で挙げた候補 | Giraffe（F# 版） |

### 前提整備（C#）

| 項目 | 内容 | 状態 |
|------|------|------|
| Nix 環境 | `nix develop .#dotnet` で .NET SDK 10.0.101 が使えることを CI で確かめる（F# 版と共用） | 完了（C# CI で確認） |
| アプリ雛形 | `apps/csharp/`（ソリューション、C# のライブラリとテストのプロジェクト、`Directory.Build.props`・`Directory.Packages.props`・`global.json`・`.editorconfig`・`.gitignore`）にテストが 1 本通る最小構成 | 完了（`OutputType` Exe・`IsTestProject`・`<Using Include="Xunit" />` が要る） |
| 学習データ | `ML_DATA_DIR`（既定 `../data/sukkiri-ml`）で参照する。実データのテストは xUnit v3 の `Assert.SkipUnless` でデータが無ければスキップする | 完了（既定の場所は `[CallerFilePath]` から求める） |
| 静的解析 | `dotnet format --verify-no-changes`・アナライザー・`TreatWarningsAsErrors` を検査に組み込み、わざと違反を入れて失敗することを確かめる | 完了（CS0219・CA1822・IDE0055・WHITESPACE の 4 つで失敗を確認） |
| ライブラリ選定 | ADR 006（C# 版のライブラリ）を作成する | 完了（ADR 006） |
| CI | `.github/workflows/csharp-ci.yml`（Nix → `dotnet restore --locked-mode` → 整形の確認・ビルド・テスト・カバレッジの表示）。NuGet のキャッシュを使う | 完了（B29。キャッシュのキーは `nuget-csharp-` で F# 版と分けた） |
| タスク | `ops/scripts/apps.js` に `csharp`（`apps/csharp/`）を加え、`apps:check:csharp` で手元の検査を実行できるようにする | 完了 |

### B29 のステップ計画（C# のウォーキングスケルトン）

各ステップは TDD で進め、ステップごとにコミットする。

| ステップ | 内容 | 完了条件 |
|---------|------|---------|
| 1 | ライブラリの事実確認：ライセンス、Microsoft.Data.Analysis の保守状況、アナライザーの選定（SDK 同梱だけで足りるか）、`global.json` の版をどうするか（手元の 10.0.100 と Nix の 10.0.101 の差の扱い）。結果を本節の「確認した事実」に書き足す | 「未検証」の項目が無くなる |
| 2 | ADR 006 を書く（章ごとの置き換えの範囲は ADR 004（F# 版）を起点にする） | ADR 006 が `docs/adr/` と索引・nav にある |
| 3 | `apps/csharp/` の雛形：ソリューション、ライブラリとテストのプロジェクト、中央パッケージ管理、`packages.lock.json`、最初のテストが 1 本通る | 手元と `nix develop .#dotnet` の両方で `dotnet test` が成功する |
| 4 | 整形・静的解析・カバレッジ：`dotnet format`・アナライザー・`TreatWarningsAsErrors`・coverlet を検査に組み込む。わざと違反を入れて失敗することを確かめてから戻す（Java 版・F# 版の教訓） | 違反を入れると検査が失敗し、戻すと成功する |
| 5 | 第 1 章の実装：Python 版・Java 版と同じ TODO リストを TDD で進める。実データのテストは `Assert.SkipUnless` でスキップする | データありで全テストが通り、データなしではスキップされる。正解率が F# 版・Java 版と一致する |
| 6 | 記事：第 1 章、C# 版トップ（`csharp/index.md`）、シリーズ索引の言語一覧、`mkdocs.yml` の nav | ローカルのプレビューで表示される。記事の数値が実装の実測値と一致する |
| 7 | CI とタスク：`.github/workflows/csharp-ci.yml`、`ops/scripts/apps.js` への `csharp` の追加 | push 後に C# CI がグリーン。`apps:check:csharp` が手元で成功する |
| 8 | 仕上げ：学習データの行の混入・BOM の文字・絶対パスの検査、記事への OKF の適用、本計画の前提整備の状態と Bolt の完了、`docs/log.md` の更新 | 検査に指摘が無く、`okf:check` が ERROR 0 |

B29（C# のウォーキングスケルトン）は 2026-09-20 に完了した。`global.json` を 10.0.100 + `rollForward: latestPatch` にしたので、手元（10.0.100）でも Nix（10.0.101）でも動く（F# 版は 10.0.101 固定で手元では動かない）。整形（`dotnet format`）・アナライザー（CA・IDE・CS）・カバレッジ（coverlet）は、わざと違反を入れて失敗することを確かめてから第 1 章に入った。第 1 章の正解率は 0.7368 で、Python 版・Kotlin 版・Java 版・F# 版と一致した。

- 実装の置き場所は、人の指示により `apps/dotnet/` から `apps/csharp/` に改めた（F# 版の `apps/fsharp/` と対になる）
- .NET の `File.ReadAllLines` は BOM を取り除くので、ほかの言語版で起きた「列名に BOM が残る」落とし穴は C# では起きない。保険で入れていた除去の処理を外し、その振る舞いをテストで固定した
- `dotnet test` は `apps/csharp` を作業ディレクトリにして実行する（`global.json` の `test.runner` は最も近い `global.json` から読まれる）。改名の直後に 0 件と判定されたのは、古い状態の .NET のビルドサーバーが残っていたためで、`dotnet build-server shutdown` で解消した。この経緯は ADR 006 に記録した

### 承認が必要な事項（C#）

次の点を確認した（2026-09-20 承認）。

- [x] C# 版の対比の軸（型・データの表現・ライブラリ）と、データフレームのライブラリ（Microsoft.Data.Analysis）を使わずに record と LINQ で表すこと
- [x] ライブラリの第一候補（xUnit v3・coverlet.MTP・`dotnet format`・.NET アナライザー・ML.NET・ASP.NET Core Minimal API）を ADR 006 で確定すること
- [x] 実装を `apps/csharp/`（2026-09-20 に `apps/dotnet/` から変更。人の指示による）、記事を `docs/article/getting-start-ml/csharp/` に置き、.NET SDK の版を F# 版にそろえること
- [x] B29 のステップ 1〜8

### B30 のステップ計画（第 2〜3 章）

#### データの表し方（第 2 章以降のすべての章に効く判断）

F# 版は型プロバイダ（`CsvProvider`）で読み、特徴量を `Map<string, float option>` で持つ。C# 版は型プロバイダが無いので、Java 版と同じ考え方で、セルの文字列を列名で引く自作の表にする。

| 対象 | 表し方（案） | 理由 |
|------|------------|------|
| 読み込んだ行 | `record Row(IReadOnlyDictionary<string, string> Cells)`。`double? Number(string column)`（空欄なら null）と `string Text(string column)` で読み出す | Java 版と同じ形。C# の `double?`（null 許容値型）が F# の `float option` に当たることを示せる。無い列は列名を示す例外にする |
| 表 | `record Table(IReadOnlyList<string> Columns, IReadOnlyList<Row> Rows)`。`Load(path)` と `CountMissing()` を持つ | 列の順を保つ。`Dictionary` は順を保証しないので、列の順は `Columns` で持つ |
| 補完した後の特徴量 | `sealed class Features`（列名の並びと `double[]`）。値で比べる `Equals`・`GetHashCode` を書く | record は配列の成分を参照で比べるので、Java 版と同じくクラスにする。C# ではアナライザーが record の配列を止めないぶん、自分で気づく必要があることを記事の題材にする |
| 分割の結果 | `record TrainTestSplit<TX, TT>(IReadOnlyList<TX> XTrain, IReadOnlyList<TX> XTest, IReadOnlyList<TT> TTrain, IReadOnlyList<TT> TTest)` | F# 版の型と同じ形 |
| 決定木 | `abstract record Tree` と `sealed record Leaf`・`sealed record Node`。予測は `switch` 式のパターンマッチ | C# には sealed interface が無いので、抽象レコードと sealed な派生で閉じる。網羅していない `switch` 式は CS8509 の警告になり、`TreatWarningsAsErrors` によってエラーになることを実測して示す |

#### 乱数と数値

分割は F# 版と同じ `System.Random(seed)` と Fisher–Yates のシャッフル（後ろから `Next(i + 1)` で交換）にする。同じ .NET の乱数なので、訓練データとテストデータに入る行は F# 版と一致するはず。これを第 2 章のテストで確かめ、一致すれば第 3 章の正解率も F# 版と同じ値になる（Java 版は `Collections.shuffle` なので一致しない）。一致しなければ、その事実と理由を記事に書く。

#### ステップ

| ステップ | 内容 | 完了条件 |
|---------|------|---------|
| 1 | 第 2 章の読み込み：`Row`・`Table`、BOM 付きで空欄を含む CSV、列ごとの欠損値の数 | 架空の値の CSV でテストが通る。`double?` で空欄を表せている |
| 2 | 第 2 章の前処理：欠損値を除いた列ごとの平均値、補完して `Features` にする、特徴量と正解ラベルに分ける | 元の行を変更しないことを含めてテストが通る。`Features` の値による比較のテストがある |
| 3 | 第 2 章の分割：`Shuffle`（Fisher–Yates）と `SplitTrainTest` を三角測量で進める | 割合どおりの件数・重複の無さ・対応の保持・同じシードで同じ分け方・数値のラベルのテストが通る |
| 4 | 第 2 章の実データ：iris.csv の欠損値の数（2・1・2・2）、105 件と 45 件への分割と補完、章の実行の表示。F# 版と行が一致するかを確かめる | データありで通り、データなしでスキップされる。F# 版との一致の有無を記録する |
| 5 | 第 3 章の決定木：ジニ不純度、最良の分割、木の構築と予測、深さの制限、学習前の予測のエラー、木の表示 | F# 版・Java 版と同じ観点のテストが通る |
| 6 | 第 3 章の ML.NET：`Microsoft.ML` と `Microsoft.ML.FastTree` を依存に加え、`Features` を ML.NET の入力に変える橋渡し、`FastTree` を `OneVersusAll` で多クラスにして突き合わせ（ADR 004 の結果を C# で確かめる） | 突き合わせのテストが通る。F# 版と違う振る舞いがあれば ADR 006 に書く |
| 7 | 第 3 章の実データ：深さごとの正解率と深さ 2 の木を表示する章の実行 | 記事に載せる数値をテストで固定する |
| 8 | 記事：第 2 章・第 3 章を書き、C# 版トップ・シリーズ索引・nav に登録する。途中段階の出力は再現して実測する | 記事の数値・出力が実測と一致する。サイトのビルドでリンク切れが無い |
| 9 | 仕上げ：学習データの行・BOM の文字・絶対パスの検査、OKF、執筆計画の B30 の完了、`docs/log.md`、push して C# CI とサイトの公開を確かめる | 検査に指摘が無く、CI がグリーン |

第 3 章は第 2 章の `Features` と分割に依存するので、順に進める。第 2〜3 章は親が実装し、記事はサブエージェントに任せる（Java 版の B25 と同じ進め方）。

### 承認が必要な事項（B30）

次の点を確認した（2026-09-20 承認）。

- [x] データの表し方（セルの文字列を持つ `Row` と `Table`、欠損値を持てない `Features` をクラスにすること、決定木を抽象レコードと sealed な派生で表すこと）
- [x] 分割を F# 版と同じ `System.Random` + Fisher–Yates にし、F# 版と行が一致するかを確かめること
- [x] B30 のステップ 1〜9

### B30〜B33 の進め方（2026-09-20、目標「C# 執筆完成」による）

2026-09-20 に人から「C# 執筆完成」を目標として受け取ったので、B30 の途中から B33 までを続けて進める。割り当ては Java 版（B25〜B28）と同じ形にする。

| 作業 | 担当 | 依存 |
| :--- | :--- | :--- |
| 第 2〜3 章の実装 | 親 | — |
| 第 2〜3 章の記事 | サブエージェント | 第 2〜3 章の実装 |
| 第 4〜6 章（実装と記事） | サブエージェント | 第 1〜3 章 |
| 第 7 章・第 8 章・第 9 章・第 10 章（実装と記事） | サブエージェント（章ごと） | 第 1〜3 章 |
| 第 11〜12 章・第 13〜14 章（実装と記事） | サブエージェント | 第 7 章・第 9 章の取り込み後 |
| 第 15 章 | 親 | 第 7・8 章 |
| 索引・nav・執筆計画・ログ・ADR 006 の統合 | 親 | 各章の取り込み |

サブエージェントへの共通の指示は Java 版と同じで、変更してはいけないファイル（`Directory.Build.props`・`Directory.Packages.props`・`global.json`・`mkdocs.yml`・索引・C# 版トップ・執筆計画・`docs/log.md`・ADR）と、依存の追加・ADR に書くべき結果を報告に書く決まりも同じ。分割が F# 版と一致するので、各章の数値が F# 版と一致するかを確かめ、一致しない場合は理由を記事に書く。

### C# 版の完了（2026-09-20）

B29〜B33 は 2026-09-20 に完了し、C# 版の全 15 章がそろった。親が第 2〜3 章と第 15 章の実装を書き、記事と第 4〜14 章は worktree のサブエージェントで並行して進めた（第 11〜14 章は第 7・9 章の取り込み後に着手）。テストは 344 件で、学習データが無い環境では 63 件がスキップされる。

- 分割を F# 版と同じ `System.Random` + Fisher-Yates にしたので、訓練データとテストデータに入る行が F# 版と一致する。その結果、第 3・7・9・11・12・13・14・15 章の数値は F# 版と表示の桁まで一致した。一致しなかったのは、特徴量の並び順に依存する処理（第 10 章のランダムフォレストと ML.NET の FastForest、第 8 章の ML.NET との一致件数の一部）だけで、理由を記事に書いた
- C# には F# の `Result` が無いので、第 15 章で `PredictionResult<T>` を抽象レコードと sealed な派生で自作した（Java 版は検査例外）。判別共用体の代わりに enum と抽象レコードを使い、`switch` 式は網羅を証明できないので最後の分岐が要る（CS8509）
- ML.NET は C# 向けの API なので、F# 版が必要とした `[<CLIMutable>]` や DTO への詰め替えが要らない。一方で PCA は座標しか返さず、K-means に初期中心を渡せないなど、ライブラリ側の制約は F# 版と同じ
- 実装の置き場所は、人の指示により `apps/dotnet/` ではなく `apps/csharp/` にした

## Scala 版執筆計画

Scala は第 2 波の 3 番目の言語で、JVM の Java 版・Kotlin 版と、関数型の F# 版の両方を対比の相手にする。Python 版と同じ 5 部 15 章の節構成で書き、Notebook・可視化の節・付録 A は作らない（「第 2 波の執筆計画」の承認による）。対比の軸は次の 3 つとする。

- **型**: case class と enum（Scala 3）で、F# のレコードと判別共用体に当たるものを表す。`Option` は F# の `option` と同じ形。網羅していない `match` は警告になる（コンパイラの設定でエラーにできる）
- **データの表現**: 不変のコレクション（`Map`・`Vector`）で表す。Java 版・C# 版が配列を包むクラスで苦労した「値で比べる」は、Scala の `Vector` なら既定でできる
- **ライブラリ**: JVM の機械学習ライブラリ（Tribuo）を Scala から使う。Java 向けの API（可変なオブジェクト・配列）を Scala の不変なコレクションとどう橋渡しするかを題材にする

### 確認した事実（2026-09-20 時点）

| 項目 | 確認内容 | 確認方法 |
|------|---------|---------|
| Nix 環境 | `ops/nix/environments/scala/shell.nix` は `scala_3`・`sbt`・`metals`・`scala-cli`。版は Scala 3.3.6（LTS）、sbt 1.12.0、metals 1.6.4、scala-cli 1.11.0 | `nix eval` |
| ローカル環境 | `scala` コマンドは未導入。JDK は 25.0.2 | コマンドの確認 |
| Smile のライセンス | Scala 3 向け（`smile-scala_3`）は 3.0.2 以降しか無く、3.0.2・4.x・5.x・6.x のいずれも GPL-3.0。LGPL-3.0 の 2.6.0 には Scala 3 版が無い | Maven Central の POM |
| Tribuo | 4.3.2、Apache License 2.0。Kotlin 版（ADR 002）・Java 版（ADR 005）で癖を確認済み | Maven Central |
| そのほかのライブラリ | ScalaTest 3.2.20（安定版。3.3.0 はマイルストーン版のみ）、MUnit 1.3.6、Breeze 2.1.0（Apache License 2.0）、http4s 0.23.37（1.0.0 はマイルストーン版のみ）、circe 0.14.16、sbt-scoverage 2.4.4 | Maven Central |

| ライセンス（B34） | ScalaTest 3.2.20・http4s 0.23.37・circe 0.14.16・sbt-scoverage 2.4.4 はいずれも Apache License 2.0。sbt-scalafmt の最新は 2.6.2 | Maven Central の POM |
| Tribuo を Scala から呼べるか（B34） | `nix develop .#scala` の sbt 1.12.0・Scala 3.3.6 で、`ArrayExample[Label](Label("setosa"), Array("a", "b"), Array(0.1, 0.2))` が動いた（型引数を明示する）。Java の可変な API をそのまま呼べる | 使い捨ての sbt プロジェクトで実行 |
| 標準ライブラリの版の表示（B34） | Scala 3 でも `scala.util.Properties.versionNumberString` は 2.13.16 を返す（Scala 3 は 2.13 の標準ライブラリを使うため） | 同上 |
| 警告をエラーにする（B34） | `-Wunused:all -Wvalue-discard -Xfatal-warnings` で、使っていない import（E198）と使っていない値がエラーになる | 同上 |
| 整形とカバレッジ（B34） | `scalafmtCheckAll` は崩れた整形でエラーになる（`.scalafmt.conf` に `version` と `runner.dialect = scala3` が要る）。`coverage` → `compile` → `coverageReport` でカバレッジが出る | 同上 |

Tribuo の各アルゴリズムの振る舞いは、Kotlin 版（ADR 002）・Java 版（ADR 005）で確かめた結果を起点にし、Scala 版の各章で確かめて ADR 007 に書き足す。

### ライブラリ方針（ADR 007 で確定する案）

| 用途 | 第一候補 | 理由 | 代替案 |
|------|---------|------|--------|
| 言語・ビルド | Scala 3.3.6（LTS）、sbt 1.12.0 | Nix の環境と一致し、長期サポート版 | scala-cli、Gradle |
| テスト | ScalaTest 3.2.20 | 「対象言語」の表のテスト基盤。安定版を使う | MUnit |
| 整形 | scalafmt（sbt-scalafmt） | Scala の標準的な整形 | — |
| 静的解析 | コンパイラの警告（`-Wunused:all`・`-Wvalue-discard` など）を `-Xfatal-warnings` でエラーにする | 追加の依存が要らない。ほかの言語版と同じく「警告をエラーにする」方針 | scalafix、WartRemover |
| カバレッジ | sbt-scoverage 2.4.4 | sbt の標準的なカバレッジ | — |
| データの表現 | case class と不変のコレクション（`Map`・`Vector`）。データフレームのライブラリは使わない | Java 版・C# 版と同じ考え方。`Vector` は値で比べられる | Spark の DataFrame（重い） |
| 乱数 | `java.util.Random(seed)` と Fisher-Yates | Java 版と同じ乱数・同じ手順にすれば、分かれる行が Java 版と一致するはず（B34 で確かめる） | `scala.util.Random.shuffle` |
| 機械学習 | Tribuo 4.3.2 | Apache License 2.0。Smile は Scala 3 版がすべて GPL-3.0 なので使わない | 自作のみ |
| 行列 | 自作の小さな不変の型 | ほかの言語版と同じく、正規方程式の仕組みを見せる | Breeze 2.1.0 |
| API | http4s 0.23.37 + circe 0.14.16 | 「言語ごとのバリエーション」で挙げた候補 | Play Framework |

### 前提整備（Scala）

| 項目 | 内容 | 状態 |
|------|------|------|
| Nix 環境 | `nix develop .#scala` で Scala 3・sbt が使えることを CI で確かめる | 未着手 |
| アプリ雛形 | `apps/scala/`（`build.sbt`・`project/build.properties`・`project/plugins.sbt`・`src/main/scala`・`src/test/scala`・`.gitignore`）にテストが 1 本通る最小構成 | 完了（`go.mod` の `go` 指令は 1.25。Nix の 1.25.5 でも手元の 1.26.5 でも動く） |
| 学習データ | `ML_DATA_DIR`（既定 `../data/sukkiri-ml`）で参照する。実データのテストは ScalaTest の `assume` でデータが無ければスキップする | 未着手 |
| 静的解析 | scalafmt・コンパイラの警告をエラーにする設定・scoverage を検査に組み込み、わざと違反を入れて失敗することを確かめる | 完了（gofmt・go vet・golangci-lint の 3 つで失敗を確認） |
| ライブラリ選定 | ADR 007（Scala 版のライブラリ）を作成する | 未着手 |
| CI | `.github/workflows/scala-ci.yml`（Nix → `sbt scalafmtCheckAll test coverageReport`）。sbt と Coursier のキャッシュを使う | 未着手 |
| タスク | `ops/scripts/apps.js` に `scala` を加え、`apps:check:scala` で手元の検査を実行できるようにする | 未着手 |

### B34 のステップ計画（Scala のウォーキングスケルトン）

各ステップは TDD で進め、ステップごとにコミットする。

| ステップ | 内容 | 完了条件 |
|---------|------|---------|
| 1 | ライブラリの事実確認：ライセンス（ScalaTest・http4s・circe・sbt のプラグイン）、Tribuo を Scala から呼べること、警告をエラーにする設定でどれだけ指摘が出るか。結果を本節の「確認した事実」に書き足す | 「未検証」の項目が無くなる |
| 2 | ADR 007 を書く（章ごとの置き換えの範囲は ADR 002（Kotlin 版・Tribuo）を起点にする） | ADR 007 が `docs/adr/` と索引・nav にある |
| 3 | `apps/scala/` の雛形：sbt のプロジェクト、Scala 3.3.6、ScalaTest、最初のテストが 1 本通る | 手元（Nix）と `nix develop .#scala` で `sbt test` が成功する |
| 4 | 整形・静的解析・カバレッジ：scalafmt・`-Xfatal-warnings`・scoverage を検査に組み込む。わざと違反を入れて失敗することを確かめてから戻す（Java 版・C# 版の教訓） | 違反を入れると検査が失敗し、戻すと成功する |
| 5 | 第 1 章の実装：ほかの言語版と同じ TODO リストを TDD で進める。実データのテストは `assume` でスキップする | データありで全テストが通り、データなしではスキップされる。正解率が 0.7368 でほかの言語版と一致する |
| 6 | 記事：第 1 章、Scala 版トップ（`scala/index.md`）、シリーズ索引の言語一覧、`mkdocs.yml` の nav | ローカルのプレビューで表示される。記事の数値が実装の実測値と一致する |
| 7 | CI とタスク：`.github/workflows/scala-ci.yml`、`ops/scripts/apps.js` への `scala` の追加 | push 後に Scala CI がグリーン。`apps:check:scala` が手元で成功する |
| 8 | 仕上げ：学習データの行の混入・BOM の文字・絶対パスの検査、記事への OKF の適用、本計画の前提整備の状態と Bolt の完了、`docs/log.md` の更新 | 検査に指摘が無く、`okf:check` が ERROR 0 |

### 承認が必要な事項（Scala）

次の点を確認した（2026-09-20 承認）。

- [x] 機械学習のライブラリを Smile ではなく Tribuo にすること（Scala 3 向けの Smile はすべて GPL-3.0 で、LGPL の 2.6.0 に Scala 3 版が無いため。「対象言語」の表の候補を改める）
- [x] Scala 版の対比の軸（型・データの表現・ライブラリ）と、データフレームのライブラリを使わずに case class と不変のコレクションで表すこと
- [x] ライブラリの第一候補（Scala 3.3.6・sbt・ScalaTest・scalafmt・`-Xfatal-warnings`・scoverage・Tribuo・http4s + circe）を ADR 007 で確定すること
- [x] 実装を `apps/scala/`、記事を `docs/article/getting-start-ml/scala/` に置くこと
- [x] B34 のステップ 1〜8

### B34〜B38 の進め方（2026-09-20、目標「Scala 執筆完成」による）

2026-09-20 に人から「Scala 執筆完成」を目標として受け取ったので、B34 の途中から B38 までを続けて進める。割り当ては Java 版（B25〜B28）・C# 版（B30〜B33）と同じ形にする。

| 作業 | 担当 | 依存 |
| :--- | :--- | :--- |
| 第 2〜3 章の実装 | 親 | — |
| 第 1 章の記事・Scala 版トップ | サブエージェント | 第 1 章の実装 |
| 第 2〜3 章の記事 | サブエージェント | 第 2〜3 章の実装 |
| 第 4〜6 章（実装と記事） | サブエージェント | 第 1〜3 章 |
| 第 7 章・第 8 章・第 9 章・第 10 章（実装と記事） | サブエージェント（章ごと） | 第 1〜3 章 |
| 第 11〜12 章・第 13〜14 章（実装と記事） | サブエージェント | 第 7 章・第 9 章の取り込み後 |
| 第 15 章 | 親 | 第 7・8 章 |
| 索引・nav・執筆計画・ログ・ADR 007 の統合 | 親 | 各章の取り込み |

分割は Java 版と同じ乱数・同じ手順なので、各章の数値が Java 版と一致するかを確かめ、一致しない場合は理由を記事に書く。サブエージェントは `build.sbt`・`project/`・`.scalafmt.conf`・`mkdocs.yml`・索引・Scala 版トップ・執筆計画・`docs/log.md`・ADR を変更せず、依存の追加や ADR に書くべき結果は報告に書く。

### Scala 版の完了（2026-09-20）

B34〜B38 は 2026-09-20 に完了し、Scala 版の全 15 章がそろった。親が第 1〜3 章・第 10 章・第 15 章の実装を書き、記事と第 4〜9 章・第 11〜14 章は worktree のサブエージェントで並行して進めた。テストは 306 件で、学習データが無い環境では 35 件がスキップ（ScalaTest の canceled）になる。

- 機械学習のライブラリは、Scala 3 向けの Smile がすべて GPL-3.0 だったため Tribuo に改めた（人の承認による）。JVM の 3 言語（Kotlin・Java・Scala）が同じ Tribuo を使う形になった
- 分割を Java 版と同じ `java.util.Random` + Fisher-Yates にしたので、訓練データとテストデータに入る行が Java 版と一致する。その結果、第 2・3・7・9・10・11・12・13・14・15 章のすべての数値が Java 版と一致した（第 15 章の API が返す興行収入は小数点以下まで同じ）
- `Features` の値を `Vector` で持つと、case class の等価判定がそのまま値の比較になる。Java 版・C# 版で必要だった「配列を包んで equals を書く」工夫が要らない
- `Either` があるので、第 15 章のドメインは F# 版の `Result` と同じ形で書けた（Java 版は検査例外、C# 版は自作の型）
- 第 8 章では、Java のシリアライズが Scala の不変コレクションと `ObjectInputFilter` の組み合わせで使えないことが分かり、タブ区切りのテキストで保存する形にした
- 検査は `-Wunused:all`・`-Wvalue-discard`・`-Xfatal-warnings`・scalafmt・scoverage。`-Wvalue-discard` は戻り値の型が `Unit` の定義の中でだけ働く

## Go 版執筆計画

Go は第 2 波の 4 番目の言語で、TypeScript 版（ライブラリが限られる環境での自作）を進め方の対比の相手にする。Python 版と同じ 5 部 15 章の節構成で書き、Notebook・可視化の節・付録 A は作らない（「第 2 波の執筆計画」の承認による）。対比の軸は次の 3 つとする。

- **型**: 構造体とインターフェースで表す。ジェネリクス（Go 1.18 以降）を分割や評価の関数で使う。判別共用体・sealed interface に当たるものが無いので、決定木は「葉と節を 1 つの構造体で表す」「インターフェースと型スイッチで表す」のどちらかを選び、その判断を記事に書く
- **エラーの扱い**: 例外が無く、`error` を戻り値で返す。ほかの言語版が例外・`Result`・`Either` で表したものを、Go では多値返却でどう表すかを示す
- **ライブラリ**: gonum には線形回帰・共分散行列・PCA はあるが、決定木・ランダムフォレスト・K-means は無い。自作の比重が大きくなる点を TypeScript 版（ml.js が未成熟）と対比する

### 確認した事実（2026-09-20 時点）

| 項目 | 確認内容 | 確認方法 |
|------|---------|---------|
| Nix 環境 | `ops/nix/environments/go/shell.nix` は `go`・`gopls`・`gotools`・`delve`・`golangci-lint`。実際に入る版は Go 1.25.5・golangci-lint 2.7.2・gopls 0.21.0（`nix eval` は nixpkgs の最新 2.8.0 を示すが、`flake.lock` で固定された環境は 2.7.2） | `nix eval`、`nix develop .#go` |
| ローカル環境 | Go 1.26.5（Nix の 1.25.5 と違う。`go.mod` の `go` 指令は 1.25 にして、どちらでも動くようにする） | `go version` |
| gonum | 最新は v0.17.0（2025-12-29）、BSD 3 条項。`stat` に `LinearRegression`・`CovarianceMatrix`・`PC`（主成分分析）・`ROC` がある。決定木・ランダムフォレスト・K-means・ロジスティック回帰は無い（`stat` の一覧で確認）。行列は `mat` パッケージ | Go module proxy とモジュールキャッシュ |
| GoLearn | 最新が 2022-12-28 のコミット（タグ無し）で、3 年以上更新されていない | Go module proxy |
| テスト | 標準の `testing` パッケージ。表駆動テストが慣習。testify（MIT）は最新 v1.12.1 | 標準ライブラリ、Go module proxy |

| gonum の関数（B39） | `stat.LinearRegression` は単回帰だけ（切片と傾きを返す）。重回帰は `mat` で正規方程式を解くか `optimize` を使う。`stat.PC` は `PrincipalComponents` で成功可否を返し、`VarsTo` で分散、`VectorsTo` で固有ベクトルの行列を取れる（寄与率は分散から自分で求める） | 使い捨てのプロジェクトで実行 |
| 静的解析（B39） | わざと崩したファイルで、`gofmt -l` がファイル名を、`go vet` が「declared and not used」を、`golangci-lint run` が同じ指摘を typecheck として報告した。`go vet` と `golangci-lint` の既定は重なる部分があるので、CI では両方を走らせるかを B39 で決める | 同上 |

gonum に無いアルゴリズム（決定木・ランダムフォレスト・K-means・ロジスティック回帰）は、ほかの言語版の自作の実装を Go に書き直して最終実装とする。

### ライブラリ方針（ADR 008 で確定する案）

| 用途 | 第一候補 | 理由 | 代替案 |
|------|---------|------|--------|
| 言語・ビルド | Go 1.25（`go.mod` の `go` 指令）。ビルドは `go build`、依存は Go Modules | Nix の版に合わせ、手元の 1.26 でも動く | — |
| テスト | 標準の `testing` と表駆動テスト | Go の標準的な書き方。依存を増やさない | testify |
| 整形 | `gofmt`（`gofmt -l` で検査） | 標準 | gofumpt |
| 静的解析 | `go vet` と `golangci-lint`（既定の検査器） | Nix の環境に入っている | staticcheck 単体 |
| カバレッジ | `go test -cover`（`-coverprofile` で詳細） | 標準 | — |
| データの表現 | 構造体と `map[string]string`（セルの文字列）。データフレームのライブラリは使わない | ほかの言語版と同じ考え方 | gota（保守が緩やか） |
| 乱数 | `math/rand` の `rand.New(rand.NewSource(seed))` と Fisher-Yates | シードを指定できる。Java 版と一致するかは B39 で確かめる（一致しない見込み） | — |
| 機械学習 | gonum v0.17.0（線形回帰・共分散行列・PCA）。決定木・ランダムフォレスト・K-means・ロジスティック回帰は自作を最終実装にする | BSD 3 条項で、Go で標準的な数値計算ライブラリ。GoLearn は更新が止まっている | GoLearn（採らない） |
| API | 標準の `net/http`（Go 1.22 以降のルーティング） | 「言語ごとのバリエーション」で挙げた候補。依存を増やさない | Echo、Gin |

### 前提整備（Go）

| 項目 | 内容 | 状態 |
|------|------|------|
| Nix 環境 | `nix develop .#go` で Go・golangci-lint が使えることを CI で確かめる | 完了（Go CI で確認） |
| アプリ雛形 | `apps/go/`（`go.mod`・`cmd/`・`internal/`・`.gitignore`）にテストが 1 本通る最小構成 | 完了（`go.mod` の `go` 指令は 1.25。Nix の 1.25.5 でも手元の 1.26.5 でも動く） |
| 学習データ | `ML_DATA_DIR`（既定 `../data/sukkiri-ml`）で参照する。実データのテストは `t.Skip` でデータが無ければスキップする | 完了 |
| 静的解析 | `gofmt -l`・`go vet`・`golangci-lint run` を検査に組み込み、わざと違反を入れて失敗することを確かめる | 完了（gofmt・go vet・golangci-lint の 3 つで失敗を確認） |
| ライブラリ選定 | ADR 008（Go 版のライブラリ）を作成する | 完了（ADR 008） |
| CI | `.github/workflows/go-ci.yml`（Nix → `gofmt -l` → `go vet` → `golangci-lint run` → `go test -cover`）。モジュールのキャッシュを使う | 完了（B39。キャッシュは `~/go/pkg/mod` と `~/.cache/go-build`） |
| タスク | `ops/scripts/apps.js` に `go` を加え、`apps:check:go` で手元の検査を実行できるようにする | 完了 |

### B39 のステップ計画（Go のウォーキングスケルトン）

各ステップは TDD で進め、ステップごとにコミットする。

| ステップ | 内容 | 完了条件 |
|---------|------|---------|
| 1 | ライブラリの事実確認：gonum の `LinearRegression`・`PC` で何ができるか（単回帰か重回帰か、寄与率を取れるか）、`golangci-lint` の既定の指摘、`go vet` との重なり、ライセンス。結果を本節の「確認した事実」に書き足す | 「未検証」の項目が無くなる |
| 2 | ADR 008 を書く（章ごとの置き換えの範囲は、gonum に無いものを自作と決める） | ADR 008 が `docs/adr/` と索引・nav にある |
| 3 | `apps/go/` の雛形：`go.mod`、`internal/` のパッケージ構成、最初のテストが 1 本通る | 手元と `nix develop .#go` の両方で `go test ./...` が成功する |
| 4 | 整形・静的解析・カバレッジ：`gofmt -l`・`go vet`・`golangci-lint run`・`go test -cover` を検査に組み込む。わざと違反を入れて失敗することを確かめてから戻す（Java 版・C# 版・Scala 版の教訓） | 違反を入れると検査が失敗し、戻すと成功する |
| 5 | 第 1 章の実装：ほかの言語版と同じ TODO リストを TDD で進める。実データのテストは `t.Skip` でスキップする。エラーは `error` の戻り値で返す | データありで全テストが通り、データなしではスキップされる。正解率が 0.7368 でほかの言語版と一致する |
| 6 | 記事：第 1 章、Go 版トップ（`go/index.md`）、シリーズ索引の言語一覧、`mkdocs.yml` の nav | ローカルのプレビューで表示される。記事の数値が実装の実測値と一致する |
| 7 | CI とタスク：`.github/workflows/go-ci.yml`、`ops/scripts/apps.js` への `go` の追加 | push 後に Go CI がグリーン。`apps:check:go` が手元で成功する |
| 8 | 仕上げ：学習データの行の混入・BOM の文字・絶対パスの検査、記事への OKF の適用、本計画の前提整備の状態と Bolt の完了、`docs/log.md` の更新 | 検査に指摘が無く、`okf:check` が ERROR 0 |

B39（Go のウォーキングスケルトン）は 2026-09-20 に完了した。`go.mod` の `go` 指令を 1.25 にしたので、Nix（1.25.5）でも手元（1.26.5）でも動く。整形（gofmt）・静的解析（go vet・golangci-lint）・カバレッジ（go test -cover）は、わざと違反を入れて失敗することを確かめてから第 1 章に入った。第 1 章の正解率は 0.7368 で、ほかの言語版と一致した。

- 例外が無いので、読み込みや正解率の計算は `error` を戻り値で返す形にした。BOM を取り除かないと「列がありません: 身長」という分かりやすいエラーになる（Java 版の NullPointerException より読める）
- `golangci-lint` の版は、`nix eval` が示す nixpkgs の最新（2.8.0）ではなく、`flake.lock` で固定された 2.7.2 が環境に入る。記事を書いたサブエージェントの指摘で気づき、ADR 008 と本計画を実測値に直した
- 仮実装の段階では、使っていない定数を `golangci-lint` の `unused` が指摘する（Java 版の Error Prone と同じ役割）

### 承認が必要な事項（Go）

次の点を確認した（2026-09-20 承認）。

- [x] Go 版の対比の軸（型・エラーの扱い・ライブラリ）と、データフレームのライブラリを使わずに構造体と map で表すこと
- [x] 機械学習は gonum（線形回帰・共分散行列・PCA）だけを使い、決定木・ランダムフォレスト・K-means・ロジスティック回帰は自作を最終実装とすること（GoLearn は更新が止まっているので使わない）
- [x] ライブラリの第一候補（標準の testing・gofmt・go vet・golangci-lint・gonum・net/http）を ADR 008 で確定すること
- [x] 実装を `apps/go/`、記事を `docs/article/getting-start-ml/go/` に置くこと
- [x] B39 のステップ 1〜8

### B40〜B43 の進め方（2026-09-20、目標「Go 執筆完成」による）

2026-09-20 に人から「Go 執筆完成」を目標として受け取ったので、B39 の完了後から B43 までを続けて進める。割り当ては Java 版（B25〜B28）・C# 版（B30〜B33）・Scala 版（B34〜B38）と同じ形にする。

| 作業 | 担当 | 依存 |
| :--- | :--- | :--- |
| 第 2〜3 章の実装 | 親 | — |
| 第 2〜3 章の記事 | サブエージェント | 第 2〜3 章の実装 |
| 第 4〜6 章（実装と記事） | サブエージェント | 第 1〜3 章 |
| 第 7 章・第 8 章・第 9 章・第 10 章（実装と記事） | サブエージェント（章ごと） | 第 1〜3 章 |
| 第 11〜12 章・第 13〜14 章（実装と記事） | サブエージェント | 第 7 章・第 9 章の取り込み後 |
| 第 15 章 | 親 | 第 7・8 章 |
| 索引・nav・執筆計画・ログ・ADR 008 の統合 | 親 | 各章の取り込み |

#### データの表し方と決定木（第 2 章以降のすべての章に効く判断）

| 対象 | 表し方 | 理由 |
| :--- | :--- | :--- |
| 読み込んだ行 | `type Row struct { cells map[string]string }`。`Number(column) (float64, bool)`（空欄なら false）と `Text(column) string` で読み出す | ほかの言語版と同じ考え方。Go には Option が無いので「値と ok」の多値返却で表す |
| 表 | `type Table struct { Columns []string; Rows []Row }` | 列の順を保つ |
| 補完した後の特徴量 | `type Features struct { Columns []string; Values []float64 }`。スライスは比較できないので、テストでは `reflect.DeepEqual` を使う | Java 版・C# 版と同じく、欠損値を持てない型として分ける |
| 分割の結果 | `type TrainTestSplit[X, T any] struct { XTrain, XTest []X; TTrain, TTest []T }`（ジェネリクス） | Go 1.18 以降のジェネリクスを使う。正解ラベルが文字列でも数値でも使える |
| 決定木 | `type Tree interface { isTree() }` と `Leaf`・`Node` の構造体、予測は型スイッチ | Go に判別共用体・sealed interface は無い。非公開のメソッドを持たせて、このパッケージの外で実装を足せないようにする。網羅性はコンパイラが検査しないので、既定の分岐でエラーを返す |

乱数は `math/rand` の `rand.New(rand.NewSource(seed))` と Fisher-Yates を使う。Java 版・Scala 版とは乱数の実装が違うので、分かれる行は一致しない見込み。各章で数値がほかの言語版と一致するかを確かめ、一致しない場合は理由を記事に書く。

#### B40〜B43 の完了記録（2026-09-20）

Go 版の全 15 章が完成した（B39〜B43）。実装は `apps/go/internal/chapter01〜15`、記事は `docs/article/getting-start-ml/go/`、検査は `npx gulp apps:check:go`（`gofmt -l`・`go vet`・`golangci-lint run`・`go test ./... -cover`）で 0 issues。

| 決めたこと | 結果 |
| :--- | :--- |
| 依存 | gonum v0.17.0（BSD 3 条項）と `golang.org/x/text` v0.40.0（Shift_JIS の CSV、第 9 章）だけ。GoLearn は不採用のまま |
| ライブラリと突き合わせた章 | 第 7 章（`stat.LinearRegression`）・第 9 章（`stat.Mean`・`stat.StdDev`）・第 11 章（`stat.ROC` と `integrate.Trapezoidal`）・第 13 章（`stat.PC`）。第 3・8・10・12・14 章は自作が最終実装で、置き換えの節は省略した |
| 数値の一致 | 第 13 章（主成分分析）は乱数を使わないので Java 版と完全一致した。`math/rand` を使う章はほかの言語版と一致しない（件数だけ一致する）。第 3 章の深さ 2 の正解率は 45 件中 43 件で Java 版と同じだが、分割の境界は違う |
| 第 15 章 | Web フレームワークを入れず標準の `net/http`（Go 1.22 のルーティング）で作った。JSON の読み書き・入力の検証・エラーの変換・応答の書き出しは自作 |
| ほかの言語版への波及 | Go の整形検査で崩れたファイル名が出るように `.github/workflows/go-ci.yml` と `ops/scripts/apps.js` を直した。`.gitattributes` に `apps/go/** text=auto eol=lf` を足した |

各章で確かめたライブラリの癖は [ADR 008](../../adr/008-go-ml-libraries.md) の「各章で確かめた結果」に記録した。

残るは Rust 版（第 2 波の最後）。

## Rust 版執筆計画

第 2 波の最後の言語。実装は `apps/rust/`、記事は `docs/article/getting-start-ml/rust/`、ライブラリの選定は ADR 009 に記録する。

### 対比の軸

| 軸 | 相手 | 見どころ |
| :--- | :--- | :--- |
| ライブラリが揃った静的型付け言語 | [Java 版](java/index.md)（Tribuo）・[C# 版](csharp/index.md)（ML.NET） | linfa は決定木からリッジ／ラッソ・交差検証まで揃っている。自作 → linfa で突き合わせる流れをほぼ全章で書く |
| エラーを型で表す | [F# 版](fsharp/index.md)（`Result`）・[Scala 版](scala/index.md)（`Either`）・[Go 版](go/index.md)（`error` の戻り値） | `Result<T, E>` と `?` 演算子。Go の `if err != nil` との違い |
| 所有権と借用 | ほかのすべての言語版 | データを渡すときに複製するか借用するか。行列演算での効き方 |
| クレートの版が型を分ける | ほかのすべての言語版 | ndarray 0.16/0.17、rand 0.8/0.9 を混ぜると「同じ名前の別の型」になる。Rust 固有の論点 |

B44 のステップ 1 で linfa の対応範囲を確かめた結果、当初の見込み（Go 版・TypeScript 版と同じ「ライブラリが限られる環境」）は外れたので、対比の軸を差し替えた（2026-09-20 承認）。

### 確認した事実（B44 のステップ 1 で埋める）

B44 のステップ 1（2026-09-20）で、使い捨ての Cargo プロジェクトによって次を確かめた。

| 項目 | 結果 | 確認方法 |
| :--- | :--- | :--- |
| linfa の版とライセンス | 0.8.1、`MIT OR Apache-2.0`。各クレート（trees・linear・logistic・clustering・reduction・preprocessing・elasticnet）が同じ 0.8.1 でそろっている | crates.io、`Cargo.toml` の `license` |
| linfa にあるもの | **ほぼ全部ある**。決定木（`DecisionTree`、特徴量重要度つき）・重回帰（`LinearRegression`）・ロジスティック回帰・K-means・主成分分析（`Pca`、寄与率つき）・リッジとラッソ（`ElasticNet`）・混同行列と適合率/再現率/F 値・ROC と AUC・交差検証（`cross_validate_single`） | 使い捨てのプロジェクトで全部実行 |
| linfa に無いもの | ランダムフォレストは linfa 0.8.1 の公開 API に見当たらない（要再確認） | 同上 |
| 行列 | ndarray。**linfa 0.8.1 が使うのは ndarray 0.16 で、ndarray の最新は 0.17**。0.17 を直接入れると「同じクレートの版違いは別の型」になり linfa に渡せない。0.16 に固定する | `cargo build` の型エラー |
| BLAS | 不要。linfa-linalg（純 Rust）で `cargo build` が通る | 同上 |
| 乱数 | **linfa は rand 0.8 系**（`linfa` が rand 0.8、`linfa-clustering` が rand_xoshiro 0.6）。rand 0.9・0.10 の `SmallRng` を `KMeans::params_with_rng` に渡すと「two types coming from two different versions of the same crate」で落ちる。rand 0.8 と rand_xoshiro 0.6 に固定する | 同上 |
| 乱数の並び | `StdRng::seed_from_u64(0)` と Fisher-Yates で `[9, 3, 6, 4, 8, 1, 5, 2, 0, 7]`。Java（`[4 8 9 6 3 5 2 1 7 0]`）とも Go（`[6 8 2 3 7 5 9 1 0 4]`）とも違う | 使い捨てのプロジェクトで実行 |
| CSV | csv 1.4。**BOM を自動で取り除く**（`"\u{feff}身長"` を読んで `"身長"` になる）。ほかの言語版で毎回書いた BOM の除去が要らない | ヘッダーのバイト列を表示して確認 |
| Shift_JIS | encoding_rs 0.8。`SHIFT_JIS.decode` で読める。誤って UTF-8 として読むと `String::from_utf8` が `Err` になる（Go の `japanese` デコーダが黙って U+FFFD に置換するのと対照的） | 同上 |
| API | axum 0.8.9 と tokio 1.53.1。`Router` を作ってポートを開けることを確認 | 同上 |
| 直列化 | serde 1.0 と serde_json 1.0。`#[derive(Serialize, Deserialize)]` で構造体をそのまま JSON にできる | 同上 |
| 整形・静的解析 | `cargo fmt --check` は差分をファイル名と行番号つきで出す。clippy の既定で「unused variable」「the loop variable `i` is only used to index」「binary comparison to literal `Option::None`」を検出。`-D warnings` で警告がエラーになる | わざと崩したファイル |
| カバレッジ | `flake.lock` の nixpkgs に cargo-llvm-cov 0.6.20 と cargo-tarpaulin 0.35.0 がある。`ops/nix/environments/rust/shell.nix` への追加が要る | `nix eval` |
| Nix 環境 | rustc 1.91.1、cargo 1.91.0、rustfmt 1.8.0、clippy 0.1.91、rust-analyzer。edition は 2024 | `nix develop .#rust` |

### B44 のステップ計画（Rust のウォーキングスケルトン）

各ステップは TDD で進め、ステップごとにコミットする。

| ステップ | 内容 | 完了条件 |
|---------|------|---------|
| 1 | ライブラリの事実確認：linfa の各クレート（trees・linear・logistic・clustering・reduction・preprocessing）の版・ライセンス・最終更新・対応アルゴリズム、ndarray と ndarray-linalg、csv・serde・rand・axum、clippy の既定の lint、カバレッジの取り方。使い捨ての Cargo プロジェクトで実際に動かす。結果を上の「確認した事実」に書き足す | 「未検証」の項目が無くなる |
| 2 | ADR 009 を書く（章ごとの置き換えの範囲は、linfa に無いもの・保守が止まっているものを自作と決める） | ADR 009 が `docs/adr/` と索引・nav にある |
| 3 | `apps/rust/` の雛形：Cargo の構成、`src/chapterNN` のモジュール構成、最初のテストが 1 本通る | 手元と `nix develop .#rust` の両方で `cargo test` が成功する |
| 4 | 整形・静的解析・カバレッジ：`cargo fmt --check`・`cargo clippy -- -D warnings`・テスト・カバレッジを検査に組み込む。わざと違反を入れて失敗することを確かめてから戻す | 違反を入れると検査が失敗し、戻すと成功する |
| 5 | 第 1 章の実装：ほかの言語版と同じ TODO リストを TDD で進める。実データのテストはデータが無ければスキップする。エラーは `Result` で返す | データありで全テストが通り、データなしではスキップされる。正解率が 0.7368 でほかの言語版と一致する |
| 6 | 記事：第 1 章、Rust 版トップ（`rust/index.md`）、シリーズ索引の言語一覧、`mkdocs.yml` の nav | ローカルのプレビューで表示される。記事の数値が実装の実測値と一致する |
| 7 | CI とタスク：`.github/workflows/rust-ci.yml`、`ops/scripts/apps.js` への `rust` の追加（cargo のレジストリとビルドのキャッシュ） | push 後に Rust CI がグリーン。`apps:check:rust` が手元で成功する |
| 8 | 仕上げ：学習データの行の混入・BOM の文字・絶対パスの検査、記事への OKF の適用、本計画の状態と Bolt の完了、`docs/log.md` の更新 | 検査に指摘が無く、`okf:check` が ERROR 0 |

B44（Rust のウォーキングスケルトン）は 2026-09-20 に完了した。第 1 章の正解率は 0.7368 で、ほかの言語版と一致した。

- **ステップ 1 で前提が覆った。** linfa 0.8.1 は決定木・重回帰・ロジスティック回帰・K-means・主成分分析・リッジ／ラッソ・混同行列・ROC・交差検証まで揃っていた。「Rust はライブラリが限られる」という第 2 波の計画の見込みは外れたので、対比の軸を Java 版・C# 版に差し替えた（人の承認を得て変更）
- **クレートの版が型を分ける。** linfa 0.8.1 が使うのは ndarray 0.16・rand 0.8 系で、最新の 0.17・0.9 を混ぜると「同じ名前の別の型」になってコンパイルが通らない。Rust 固有の論点として第 2 章・第 7 章で扱う
- **csv クレートが BOM を自動で取り除く。** シリーズを通して書いてきた BOM の除去が Rust 版だけ要らない。BOM の混入の検査（`git grep`）は引き続き行う
- **標準のテストにスキップが無い。** `#[ignore]` は静的な印なので「データがあるときだけ走らせる」には使えず、早期に戻って理由を標準エラーに出す形にした。走ったかどうかはカバレッジの差（データあり 70.09%、データなし 51.96%）で分かる
- **cargo-llvm-cov は rustup の llvm-tools-preview を探す。** Nix の環境には無いので、`LLVM_COV`・`LLVM_PROFDATA` に nixpkgs の LLVM を教える 2 行を `ops/nix/environments/rust/shell.nix` に足した
- Cargo のテストはパッケージのルートで走るので、既定の相対パス `../data/sukkiri-ml` が届く（Go 版はパッケージのディレクトリで走るため `ML_DATA_DIR` が要った）

### B45〜B48 の進め方（2026-09-20、目標「Rust 執筆完成」による）

2026-09-20 に人から「Rust 執筆完成」を目標として受け取ったので、B44 の完了後から B48 までを続けて進める。割り当ては Go 版（B40〜B43）と同じ形にする。

| 範囲 | 担当 | 前提 |
| :--- | :--- | :--- |
| 第 2〜3 章の実装 | 親 | — |
| 第 2〜3 章の記事 | サブエージェント | 第 2〜3 章の実装 |
| 第 4〜6 章（実装と記事） | サブエージェント | 第 1〜3 章 |
| 第 7〜8 章（実装と記事） | サブエージェント | 第 1〜3 章 |
| 第 9〜10 章（実装と記事） | サブエージェント | 第 1〜3 章 |
| 第 11〜12 章・第 13〜14 章（実装と記事） | サブエージェント | 第 7 章・第 9 章の取り込み後 |
| 第 15 章 | 親 | 第 7・8 章 |
| 索引・nav・執筆計画・ログ・ADR 009 の統合 | 親 | 各章の取り込み |

#### データの表し方と決定木（第 2 章以降のすべての章に効く判断）

| 対象 | 表し方 | 理由 |
| :--- | :--- | :--- |
| 読み込んだ行 | `struct Row { cells: HashMap<String, String> }`。`number(column) -> Result<Option<f64>>`（空欄なら `None`）と `text(column) -> Result<&str>` で読み出す | Rust には `Option` があるので、Go 版の「値と ok」より素直に書ける |
| 表 | `struct Table { columns: Vec<String>, rows: Vec<Row> }` | 列の順を保つ |
| 補完した後の特徴量 | `struct Features { columns: Vec<String>, values: Vec<f64> }`。`#[derive(PartialEq)]` でそのまま比べられる | 欠損値を持てない型として分ける。Go 版の `reflect.DeepEqual` は要らない |
| 分割の結果 | `struct TrainTestSplit<X, T> { x_train, x_test: Vec<X>, t_train, t_test: Vec<T> }` | ジェネリクス。正解ラベルが文字列でも数値でも使える |
| 決定木 | `enum Tree { Leaf { label: String }, Node { split: Split, left: Box<Tree>, right: Box<Tree> } }` | Rust には判別共用体があるので、Go 版のようなインターフェースの工夫は要らない。`match` の網羅性はコンパイラが検査する。再帰する型なので `Box` で包む |
| linfa への受け渡し | `Dataset::new(Array2<f64>, Array1<L>)`。ndarray 0.16・rand 0.8 系にそろえる | 版を混ぜると「同じ名前の別の型」になる |

乱数は `rand` 0.8 の `StdRng::seed_from_u64(seed)` と Fisher-Yates を使う。`StdRng::seed_from_u64(0)` の並びは `[9, 3, 6, 4, 8, 1, 5, 2, 0, 7]` で、Java 版・Go 版とは違う。各章で数値がほかの言語版と一致するかを確かめ、一致しない場合は理由を記事に書く。

#### B45〜B48 の完了記録（2026-09-21）

Rust 版の全 15 章が完成した（B44〜B48）。実装は `apps/rust/src/{chapter01〜15}`、記事は `docs/article/getting-start-ml/rust/`、検査は `npx gulp apps:check:rust`（`cargo fmt --check`・`cargo clippy --all-targets -- -D warnings`・`cargo test`・`cargo llvm-cov`）。

| 決めたこと | 結果 |
| :--- | :--- |
| 依存 | linfa 0.8.1 系（trees・linear・logistic・preprocessing・elasticnet・reduction・clustering）、ndarray 0.16、rand 0.8＋rand_xoshiro 0.6、csv、encoding_rs、serde・serde_json、axum・tokio。**すべて linfa が依存する版にそろえる**（混ぜると「同じ名前の別の型」になる） |
| ライブラリと突き合わせた章 | 第 3・7・9・10・11・12・13・14 章。linfa に無い**ランダムフォレスト（第 10 章）・欠損値の補完とダミー変数化（第 8 章）**だけが自作の最終実装 |
| 数値の一致 | 第 13 章（主成分分析）は乱数を使わないので寄与率がほかの言語版と一致する。`rand` を使う章は一致しない（件数だけ一致） |
| 想定外だったこと | **linfa-trees は Survived.csv のようなデータで非決定的**で、実行ごとに正解率が揺れる（自作のほうが再現性が高い）。**`rand` 0.8 の `StdRng` は「再現可能と考えるべきではない」とドキュメントが明言**しており、Rust 版の再現性は `Cargo.lock` の固定に依存する |
| 第 15 章 | axum 0.8＋tokio。`Json` 抽出器の既定の 400 を 422 に置き換え、状態は `Arc` で共有し、置き場の trait に `+ Send + Sync` を課した |
| 進め方 | 第 11〜14 章を担当したサブエージェントが利用上限で停止したため、実装と第 11〜13 章の記事はコミット済みのものを回収し、**第 14 章の記事は親が書いた** |

各章で確かめたライブラリの癖は [ADR 009](../../adr/009-rust-ml-libraries.md) の「各章で確かめた結果」に記録した。

**第 2 波（Java・C#・Scala・Go・Rust）が完了した。** シリーズは 9 言語になった。残るは B49（多言語統合解説に第 2 波の 5 言語を加える）。

### B49 の完了記録（2026-09-21）

多言語統合解説（`integration/`）の索引と全 5 章を 9 言語に拡張した。

| 章 | 主な更新 |
| :--- | :--- |
| 索引 | 対象言語一覧を波ごとに 9 行へ |
| 1 言語とライブラリの概要 | 道具立て・パッケージ管理・ライブラリの表を波ごとに分けて拡張。「Rust は見込みを外して linfa が揃っていた」ことと、整形・静的解析が言語に同梱されているのは Go と Rust だけであることを追記 |
| 2 データ構造の比較 | **乱数の節を書き直した。** 9 言語で 7 通りの結果になり、JVM の 2 言語（Java・Scala）と .NET の 2 言語（C#・F#）は正解率が 6 行すべて一致することを示した。再現性の保証のされ方を 4 段階に整理し、BOM と Shift_JIS の扱いも 9 言語に拡張 |
| 3 アルゴリズムの実装の比較 | 木の型の網羅性の検査を 4 段階に整理（Go は検査されない、Rust は `Box` が要る）。行列の自作の有無、`trait`／`interface` が構造的か名前的か、遅延評価の有無、失敗の表し方を 9 言語で比較 |
| 4 ライブラリのエコシステムの比較 | 章ごとの突き合わせの表を第 2 波ぶん追加。**「決定的でない」という節を新設**（linfa-trees の揺れ）。同じライブラリを使う言語は癖も同じだと分かった |
| 5 学習ロードマップ | 目的別の読み方に 5 言語を追加。「同じ実行環境の 2 言語（Java と Scala、C# と F#）は数値が一致するので書き方の違いだけを読み比べられる」という読み方を追加。第 2 波を完了に更新 |

**第 2 波のすべての Bolt（B24〜B49）が完了した。** 次は第 3 波（Ruby・PHP・Elixir・Clojure・Haskell）の計画から始める。

### 承認が必要な事項（Rust）

次の点を確認した（2026-09-20 承認）。

- [x] Rust 版の対比の軸（Go 版・TypeScript 版＝自作の比重、F# 版・Scala 版＝エラーを型で表す、所有権と借用）
- [x] polars を使わず、`csv` クレートと構造体・`HashMap<String, String>` でデータを表すこと
- [x] 機械学習は linfa を第一候補とし、保守状況と対応範囲をステップ 1 で確かめてから ADR 009 で確定すること。linfa に無いもの・保守が止まっているものは自作を最終実装にすること
- [x] カバレッジは `cargo-llvm-cov` が Nix 環境で使えるかを確かめ、使えなければ任意扱いにすること
- [x] 第 15 章の API は axum（+ tokio）を使うこと（Go 版と違い、標準ライブラリだけでは HTTP サーバーを書けないため）
- [x] 実装を `apps/rust/`、記事を `docs/article/getting-start-ml/rust/` に置くこと
- [x] B44 のステップ 1〜8

## 第 3 波の執筆計画

第 3 波は Ruby・PHP・Elixir・Clojure・Haskell の 5 言語（U10〜U14）を対象にする。第 1 波・第 2 波で固めた型（5 部 15 章の節構成、記事の体裁、`ML_DATA_DIR` とデータが無いときのスキップ、言語ごとの ADR、言語ごとの CI）をそのまま使う。本節は 2026-09-22 に承認した。各言語の「確認した事実」「ライブラリ方針」「前提整備」「章別執筆計画」は、その言語のウォーキングスケルトンの中で書き足す。

### 第 2 波の実績

| 言語 | Bolt | 期間 | 進め方と詰まった点 |
|------|------|------|------------------|
| Java | B24〜B28 | 2026-09-19〜20 | Kotlin 版の実装を書き直す形で進めた |
| C# | B29〜B33 | 2026-09-20 | `global.json` を `rollForward: latestPatch` にして、手元と Nix の SDK の版の違いを吸収した |
| Scala | B34〜B38 | 2026-09-20 | JVM と Smile を使った。親が第 1〜3・10・15 章を書き、残りをサブエージェントで並行して進めた |
| Go | B39〜B43 | 2026-09-20 | `go.mod` の `go` 指令を Nix の版に合わせた |
| Rust | B44〜B48 | 2026-09-20〜21 | linfa の対応範囲が見込みより広く、対比の軸を差し替えた。第 11〜14 章のサブエージェントが利用上限で止まり、親が引き継いだ |
| 統合解説 | B49 | 2026-09-21 | 9 言語に拡張した |

実績から次のことが分かった。

- 第 2 波は 5 言語と統合解説を約 3 日で終えた。見込み（6〜8 日）より速かった。既存の言語版を対比の相手にでき、ウォーキングスケルトンの型が固まっていたためである
- ライブラリの対応範囲の見込みは外れることがある（Rust）。対比の軸は、ステップ 1 で確かめてから確定する
- 手元と Nix で処理系の版が違うことを前提にし、版の指定は両方で動く書き方にする
- 同じ実行環境の言語は数値が一致する（Java と Scala、C# と F#）。統合解説では、これを読み比べの軸として使える

### 言語の順番

参照実装（Python 版）に近く、既存の言語版を対比の相手として使える言語から順に書く。型の検査が最も厳しく、ライブラリが最も少ない見込みの Haskell は最後にする。

| 順 | 言語 | Unit | 対比の相手 | 理由 |
|----|------|------|-----------|------|
| 1 | Ruby | U10 | Python 版 | 動的型付けで、書き方が Python 版に近い。Rumale（scikit-learn に似た API）と Numo::NArray で置き換えを確かめられる見込み |
| 2 | Clojure | U13 | Scala 版・Java 版 | JVM で動くので、Smile を Scala 版と共有できる見込み。数値が Scala 版・Java 版と一致するかを確かめられる |
| 3 | Elixir | U12 | F# 版・Scala 版 | 関数型の言語。Nx・Scholar・Explorer は Python の NumPy・scikit-learn・pandas にあたる |
| 4 | PHP | U11 | TypeScript 版 | Web 系の言語で ML ライブラリが限られ、TypeScript 版と進め方が近い見込み（Rubix ML・PHP-ML は保守状況を確かめる必要がある） |
| 5 | Haskell | U14 | Rust 版・F# 版 | 純粋関数型で、型でエラーを表す。ML ライブラリが少なく、自作の比重が最も大きい見込みなので最後にする |

### 共通の方針（案）

| 項目 | 方針 |
|------|------|
| 実装の置き場所 | `apps/ruby/`・`apps/php/`・`apps/elixir/`・`apps/clojure/`・`apps/haskell/`。ディレクトリ名は Nix の環境名に合わせる |
| 記事の置き場所 | `docs/article/getting-start-ml/{ruby,php,elixir,clojure,haskell}/` |
| Notebook と可視化の節 | 第 2 波と同じく作らず、可視化は Python 版・Kotlin 版へ案内する（Elixir の Livebook も使わない） |
| 付録 A | 作らず、Python 版の付録 A へ案内する |
| ライブラリ | 言語ごとに ADR（010 Ruby、011 Clojure、012 Elixir、013 PHP、014 Haskell。書く順に番号を振る）を作る。ウォーキングスケルトンの中で版・ライセンス・保守状況・アルゴリズムの有無を確かめてから確定する。ライブラリに無いもの・保守が止まっているものは、自作を最終実装にする |
| 処理系 | 検査と CI は Nix の環境で行う。手元の `ruby` は macOS の 2.6 で古く、`clojure`・`ghc` は手元に無いので、記事の環境構築の節は Nix を前提に書く |
| CI | 言語ごとに `.github/workflows/{ruby,php,elixir,clojure,haskell}-ci.yml` を追加する（Nix → ビルド・整形の確認・静的解析・テスト・カバレッジの表示） |
| タスク | `ops/scripts/apps.js` に 5 言語を加え、`apps:check:<言語>` で手元の検査を実行できるようにする |
| 数値 | 記事に載せる数値は、その言語版の実装で実測した値だけにする |
| 統合解説 | 第 3 波の 5 言語の完了後にまとめて更新する（B75） |

### 確認すべき事実

次の点は未確認であり、各言語のウォーキングスケルトンのステップ 1 で確かめる。

| 言語 | 確認すること |
|------|------------|
| Ruby | Nix の `ruby` の版（環境は `rubyPackages_3_3` の solargraph を使っている）。Rumale の最新版と対応範囲（決定木・ランダムフォレスト・PCA・K-means）、Numo::NArray の保守状況。整形・静的解析（RuboCop）とカバレッジ（SimpleCov） |
| Clojure | Clojure CLI（`deps.edn`）と Leiningen のどちらを使うか。Smile を Scala 版と同じ版で使えるか（Smile の版ごとのライセンスは ADR 007 を参照）。tech.ml.dataset・tablecloth・scicloj.ml の保守状況。静的解析（clj-kondo）とカバレッジ（cloverage） |
| Elixir | Nx・Scholar・Explorer の版と対応範囲（Scholar に決定木・ランダムフォレストがあるか）。Explorer が Nix 環境で Rust のネイティブ拡張を取得できるか。整形（`mix format`）・静的解析（Credo）・カバレッジ |
| PHP | Nix の `php` の版（環境は `php83Packages.composer` を使う）。Rubix ML・PHP-ML の保守状況と対応範囲。静的解析（PHPStan）・整形（PHP-CS-Fixer）とカバレッジ（Xdebug か PCOV が Nix で使えるか） |
| Haskell | Nix の GHC の版と、cabal と stack のどちらを使うか。hmatrix（BLAS・LAPACK への依存）が Nix で動くか。CSV（cassava）と ML ライブラリの有無。静的解析（HLint）・整形（ormolu か fourmolu）・カバレッジ（hpc） |

### Bolt 計画（第 3 波）

第 2 波と同じく、1 言語を 5 Bolt で進める。言語をまたいだ並行作業はせず、1 言語ずつ完了させる。言語の中では、第 2〜3 章・第 4〜6 章・第 7〜14 章の各章を worktree のサブエージェントで並行して進める。サブエージェントは章ごとに小さくコミットし、利用上限で止まっても親が引き継げるようにする。

| Bolt | 言語 | 内容 | 完了条件 |
|------|------|------|---------|
| B50 ウォーキングスケルトン | Ruby | Nix の `ruby` 環境の確認、`apps/ruby/` の雛形、ADR 010、第 1 章の実装と記事、Ruby 版のトップページ、nav、Ruby CI | 第 1 章のテストが CI でグリーン。記事がサイトで表示される。静的解析のルールが有効になっている（わざと違反を入れて失敗することを確かめる） |
| B51 | Ruby | 第 2〜3 章 | 自作の決定木とライブラリの結果を並べて載せられる |
| B52 | Ruby | 第 4〜6 章 | ビルド・静的解析・カバレッジ・CI が記事どおりに動く |
| B53 | Ruby | 第 7〜14 章 | 各章のテストが通り、記事がそろっている |
| B54 | Ruby | 第 15 章 | Ruby 版の全章完了。Python 版と節構成がそろっている |
| B55〜B59 | Clojure | B50〜B54 と同じ区切り（ADR 011、`apps/clojure/`） | Clojure 版の全章完了 |
| B60〜B64 | Elixir | 同上（ADR 012、`apps/elixir/`） | Elixir 版の全章完了 |
| B65〜B69 | PHP | 同上（ADR 013、`apps/php/`） | PHP 版の全章完了 |
| B70〜B74 | Haskell | 同上（ADR 014、`apps/haskell/`） | Haskell 版の全章完了 |
| B75 | 統合解説 | `integration/` の各章と索引に第 3 波の 5 言語を加える | 統合解説の各表で 14 言語の行・列がそろっている |

目安は第 2 波の実績をもとに 1 言語 0.5〜1 日とし、Haskell は自作の比重が大きいので 1〜1.5 日とする。第 3 波全体では 4〜6 日とする。各言語の完了時に、実績をもとに次の言語の見積もりを見直す。

### リスク（第 3 波）

| リスク | 影響 | 対応 |
|--------|------|------|
| ライブラリの保守が止まっている（PHP-ML、Numo など） | 読者の環境で動かない、脆弱性が放置される | ステップ 1 で最終リリース日と未対応の Issue を確かめ、止まっていれば自作を最終実装にして、ADR に理由を記録する |
| ネイティブ拡張が Nix で動かない（Explorer・hmatrix・Numo） | CI と手元で環境がずれる | ウォーキングスケルトンで Nix の CI を先に通してから第 2 章に入る |
| Haskell の自作の比重が大きい | 1 言語の所要時間が延びる | 最後の言語にし、第 2 波の Go 版・Rust 版の自作の実装を対比の相手として使う |
| Clojure と Scala の数値の一致を前提にしてしまう | 一致しない場合に記事の説明が崩れる | 一致は仮説として扱い、実測してから記事と統合解説に書く |

### 承認が必要な事項（第 3 波）

次の点を確認した（2026-09-22 承認）。

- [x] 言語の順番を Ruby → Clojure → Elixir → PHP → Haskell とすること
- [x] 第 3 波では Notebook（Livebook を含む）・可視化の節・付録 A を作らず、Python 版・Kotlin 版へ案内すること
- [x] 検査と CI は Nix の環境で行い、記事の環境構築の節を Nix 前提で書くこと
- [x] ADR の番号を書く順（010 Ruby、011 Clojure、012 Elixir、013 PHP、014 Haskell）に振ること
- [x] 1 言語を 5 Bolt とし、Bolt 番号を B50〜B75 とすること
- [x] 統合解説の更新を第 3 波の完了後にまとめて行うこと（B75）
- [x] B50（Ruby のウォーキングスケルトン）の範囲

## Ruby 版執筆計画

第 3 波の最初の言語。実装は `apps/ruby/`、記事は `docs/article/getting-start-ml/ruby/`、ライブラリの選定は ADR 010 に記録する。

### 対比の軸

| 軸 | 相手 | 見どころ |
| :--- | :--- | :--- |
| 動的型付けのスクリプト言語 | [Python 版](python/index.md) | 同じ動的型付けで書き方が近い。ブロックと `Enumerable`（`map`・`select`・`each_slice`）による書き方の違い |
| scikit-learn に似た API | [Python 版](python/index.md)（scikit-learn） | Rumale の `fit`・`predict` が scikit-learn とどこまで同じか。自作 → Rumale で突き合わせる |
| 実行時に型を検査しない | [TypeScript 版](typescript/index.md)・[Kotlin 版](kotlin/index.md) | 型の誤りをテストで捕まえる。RBS・Steep は使わず、使わない理由を記事に書く |

Rumale の対応範囲はステップ 1 で確かめ、見込みが外れたら Rust 版と同じく承認を得て軸を差し替える。

### 確認した事実（B50 のステップ 1 で埋める）

B50 のステップ 1（2026-09-22）で、使い捨ての Bundler プロジェクトを `nix develop .#ruby` の中で動かして次を確かめた。

| 項目 | 結果 | 確認方法 |
| :--- | :--- | :--- |
| Nix 環境 | Ruby 3.3.10、Bundler 2.7.2、RubyGems 3.7.2。手元の `ruby` は macOS の 2.6.10 で対象外 | `nix develop .#ruby` |
| Rumale | 2.2.0（2026-07-05）、BSD-3-Clause。`rumale-tree`・`rumale-ensemble`・`rumale-linear_model` などの gem に分かれ、すべて 2.2.0 でそろう | rubygems.org の API、`bundle list` |
| Rumale にあるもの | **ほぼ全部ある**。決定木（特徴量重要度つき）・ランダムフォレスト・線形回帰・ロジスティック回帰・リッジ・ラッソ・K-means・PCA・標準化・混同行列（`EvaluationMeasure.confusion_matrix`）・適合率／再現率／F 値（`Precision`・`Recall`・`FScore`）・ROC AUC（`ROCAUC`）・交差検証（`CrossValidation` と `KFold`） | 使い捨てのプロジェクトで全部実行 |
| Rumale に無いもの | **PCA の寄与率**（`explained_variance_ratio` にあたるものが無い。持っているのは `components` と `mean` だけ）。第 13 章では寄与率を自作する | `instance_methods` と `instance_variables` |
| 行列 | Rumale 2.x は **`numo-narray-alt`（0.11.2、2026-08-12）** に依存する。本家の `numo-narray` は 0.9.2.1（2022-08-20）で止まっており、両方を入れると衝突の警告が出る。`numo-narray` は直接入れない。ネイティブ拡張は Nix の中でビルドできた | `bundle install` の警告、`Gemfile.lock` |
| 乱数 | `(0..9).to_a.shuffle(random: Random.new(0))` は `[2, 8, 4, 9, 1, 6, 7, 3, 0, 5]`。Python（MT19937）とも Rust とも違う並び | 使い捨てのプロジェクトで実行 |
| CSV | csv 3.3.6（Ruby 3.4 から標準の gem から外れるので `Gemfile` に書く）。**`CSV.read`（ファイルを開く）は BOM を取り除く**が、`CSV.parse(File.read(...))`（文字列を渡す）は先頭の見出しに BOM が残る。文字列を渡すときは `encoding: "bom\|utf-8"` で読む。Shift_JIS は `encoding: "Shift_JIS:UTF-8"` で読め、指定しないと `CSV::InvalidEncodingError` になる | ヘッダーを表示して確認 |
| API | Sinatra 4.2.1 と Puma 8.0.2、`rackup`。`Sinatra::Base` のモジュール形式でアプリを作れる | 同上 |
| 直列化 | `Marshal.dump`／`Marshal.load` で学習済みの決定木を保存・復元して同じ予測が出る | 同上 |
| 整形・静的解析 | RuboCop 1.91.0。既定の設定で「未使用の変数」「`nil` との比較」「frozen string literal のコメントが無い」など 6 件を検出する。`.rubocop.yml` に `NewCops: enable` を書かないと、新しいルールごとの警告が大量に出る | わざと崩したファイル |
| テスト・カバレッジ | Minitest 6.0.6（`skip` がある）、SimpleCov 1.3.0。SimpleCov の最低カバレッジの設定はステップ 4 で確かめる | `bundle list` |

### B50 のステップ計画（Ruby のウォーキングスケルトン）

各ステップは TDD で進め、ステップごとにコミットする。

| ステップ | 内容 | 完了条件 |
|---------|------|---------|
| 1 | ライブラリの事実確認：上の表の各項目を、スクラッチパッドの使い捨ての Bundler プロジェクトで実際に動かして確かめ、「確認した事実」に書き足す | 「未検証」の項目が無くなる |
| 2 | ADR 010 を書く（章ごとの置き換えの範囲。Rumale に無いもの・保守が止まっているものは自作とする） | ADR 010 が `docs/adr/` と索引・nav にある |
| 3 | `apps/ruby/` の雛形：`Gemfile`・`Gemfile.lock`・`.ruby-version`・`lib/`・`test/`・`Rakefile`・`.gitignore`、最初のテストが 1 本通る | `nix develop .#ruby` でテストが成功する（手元の Ruby 2.6 は対象外と記事に書く） |
| 4 | 整形・静的解析・カバレッジ：RuboCop・テスト・SimpleCov を検査に組み込む。わざと違反を入れて失敗することを確かめてから戻す | 違反を入れると検査が失敗し、戻すと成功する |
| 5 | 第 1 章の実装：ほかの言語版と同じ TODO リストを TDD で進める。実データのテストはデータが無ければスキップする（Minitest の `skip`） | データありで全テストが通り、データなしではスキップされる。正解率が 0.7368 でほかの言語版と一致する |
| 6 | 記事：第 1 章、Ruby 版トップ（`ruby/index.md`）、シリーズ索引の言語一覧、`mkdocs.yml` の nav | ローカルのプレビューで表示される。記事の数値が実装の実測値と一致する |
| 7 | CI とタスク：`.github/workflows/ruby-ci.yml`（Nix → RuboCop → テスト → カバレッジ。gem のキャッシュ）、`ops/scripts/apps.js` への `ruby` の追加 | push 後に Ruby CI がグリーン。`apps:check:ruby` が手元で成功する |
| 8 | 仕上げ：学習データの行の混入・BOM の文字・絶対パスの検査、記事への OKF の適用、本計画の状態と Bolt の完了、`docs/log.md` の更新 | 検査に指摘が無く、`okf:check` が ERROR 0 |

B50（Ruby のウォーキングスケルトン）は 2026-09-22 に完了した。第 1 章の正解率は 0.7368 で、ほかの言語版と一致した。

- **Rumale はほぼ全部そろっていた。** 無いのは PCA の寄与率だけで、Python 版と同じ「自作してからライブラリと突き合わせる」流れを書ける
- **Rumale 2.x は `numo-narray-alt` に依存する。** 本家の `numo-narray` は 2022 年で止まっており、一緒に入れると衝突する
- **`CSV.read`／`CSV.foreach` はファイルを開くときに BOM を取り除く。** 文字列を `CSV.parse` に渡すと BOM が残る。読み方しだいで結果が変わることを第 1 章で扱った
- **`module_function` のモジュールをテストに `include` すると、章の `run` が `Minitest::Test#run` を上書きする。** `NoMethodError` で全テストが落ちた。動的言語ではメソッド名の衝突を実行するまで検出できない例として、記事の見どころにした
- 手元の `ruby` は macOS の 2.6 なので、`apps:check:ruby` は「Ruby 3.3 以上か」を確かめ、満たさなければ Nix の環境で実行する
- テストは 13 件（データなしでは 1 件がスキップ）、行カバレッジは 88.88%（40/45）

### 承認が必要な事項（Ruby）

次の点を確認した（2026-09-22 承認）。

- [x] Ruby 版の対比の軸（Python 版＝動的型付けと scikit-learn に似た API、TypeScript 版・Kotlin 版＝型の検査の有無）
- [x] 機械学習は Rumale を第一候補とし、ステップ 1 で確かめてから ADR 010 で確定すること
- [x] 型注釈（RBS・Steep）は使わないこと
- [x] 実装を `apps/ruby/`、記事を `docs/article/getting-start-ml/ruby/` に置くこと
- [x] B50 のステップ 1〜8

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
