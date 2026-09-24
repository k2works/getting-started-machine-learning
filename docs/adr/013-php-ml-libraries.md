---
type: ADR
title: "013 PHP 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定"
description: "PHP 版のライブラリに Composer・PHPUnit・PHP-CS-Fixer・PHPStan・pcov・Rubix ML・MathPHP を採用し、Rubix ML に素の線形回帰とラッソが無いため第 7 章をリッジの縮退で、第 12 章のラッソを自作で置き換える方針を決める。"
tags: [adr,getting-start-ml,php]
status: stable
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 013 PHP 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定

「機械学習から始めるプログラミング入門」PHP 版で使うライブラリを決める。

日付: 2026-09-24

## ステータス

2026-09-24 提案されました

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) の「第 3 波の執筆計画」では、PHP 版の機械学習ライブラリの第一候補を Rubix ML とし、B65 のステップ 1 で確かめてから確定するとした。PHP 版の対比の相手は Python 版・TypeScript 版（漸進的な型付け）、Ruby 版（動的型付けの仲間）、Elixir 版（ライブラリの成熟度の対照）である。

B65 のステップ 1 で、使い捨ての Composer プロジェクトを `nix develop .#php` の中で動かして次を確かめた。

| 確認したこと | 結果 | 確認方法 |
|------------|------|---------|
| Nix 環境 | **もとの `shell.nix` は `php`（8.4.16）と `php83Packages.composer`（PHP 8.3.29 で動く）を混ぜていた。** Clojure 版・Elixir 版の「道具が別の版で動く」と同じ形だが、こちらは版をそろえられる | `php --version`・`composer --version` |
| カバレッジドライバ | **素の環境には xdebug も pcov も無く、カバレッジを取れなかった。** `php.withExtensions` で pcov を足して解決した | `php -m`、PHPUnit の実行 |
| 機械学習 | **Rubix ML 2.6.0（MIT）。決定木・ランダムフォレスト・ロジスティック回帰・K-means・主成分分析・標準化・ダミー変数化・欠損値の補完・交差検証・混同行列・適合率／再現率／F 値・決定係数・`PersistentModel` がある** | `class_exists` の一覧、`composer install` |
| **Rubix ML に無いもの** | **素の線形回帰（`LinearRegression`）とラッソが無い。** 線形回帰は `Ridge(0.0)` が最小二乗にあたる | 同上 |
| ネイティブ拡張 | `rubix/tensor` は純 PHP で動き、`tensor` 拡張は任意。Nix の中でビルドは起きなかった | `extension_loaded('tensor')` |
| 数学 | MathPHP 2.x（MIT）。行列・統計・回帰を持つ | `class_exists` |
| テスト | PHPUnit 11.5.56。**テスト名（メソッド名）に日本語を使える。** `#[Group('data')]` と `--exclude-group data` で実データのテストを外せる | 使い捨てのプロジェクト |
| CSV | `fgetcsv` は **BOM を取り除かない**（Go 版・Clojure 版・Elixir 版と同じ）。**Shift_JIS は mbstring の `CP932` で標準のまま変換できる**（Elixir 版が codepagex を足したのと違い、追加の依存が要らない） | BOM つき・Shift_JIS のファイルで確認 |
| 乱数 | `mt_srand(0)` の並びはどの言語版とも一致しない。**`PHP_INT_SIZE` が 8 なので `java.util.Random` と同じ 48 ビットの線形合同法を自作できる** | `PHP_INT_SIZE`・`mt_rand` |
| 直列化 | `serialize`／`unserialize` で配列の決定木が `===` で一致して往復した | 使い捨てのプロジェクト |
| 整形・静的解析 | PHP-CS-Fixer 3.x の `check` は違反があると**終了コード 8**（1 ではない）。PHPStan 2.x は 1。**level 8 はテストコードの `assertTrue(true)` も指摘する** | わざと崩したファイル |
| しきい値 | **PHPUnit には最低カバレッジのしきい値の機能が無い。** `--coverage-clover` の XML の `project/metrics` を読んで自分で判定する | clover.xml |

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・ビルド | PHP と Composer（`composer.json`・`composer.lock`） | PHP 8.4 | PHP License / MIT | 第 1 章 |
| テスト | PHPUnit。実データのテストは `#[Group('data')]` で外せるようにする | 11.5 | BSD-3-Clause | 第 1 章 |
| 整形 | PHP-CS-Fixer（`@PSR12`） | 3.x | MIT | 第 1 章（記事での解説は第 5 章） |
| 静的解析 | PHPStan（最高レベル） | 2.x | MIT | 第 1 章（記事での解説は第 5 章） |
| カバレッジ | pcov ＋ PHPUnit の clover 出力。**しきい値は自作のスクリプトで判定する** | pcov 1.0 | BSD-3-Clause | 第 1 章（記事での解説は第 5 章） |
| データの表現 | 素の配列と `readonly class`（値の持ち方は PHPStan の配列の形で表す） | — | — | 第 1 章 |
| CSV | 標準の `fgetcsv`（BOM は自分で取り除く） | 標準 | — | 第 1 章 |
| 文字コード | mbstring の `CP932`（追加の依存なし） | 標準 | — | 第 9 章 |
| 乱数 | **`java.util.Random` と同じ線形合同法を自作する** | — | — | 第 2 章 |
| 機械学習 | Rubix ML | 2.6 | MIT | 第 3 章 |
| 数学 | MathPHP（正規方程式の行列計算） | 2.x | MIT | 第 7 章 |
| 直列化 | `serialize`（学習済みモデル）、`json_encode`（API の JSON） | 標準 | — | 第 8 章 |
| API | 標準の組み込みサーバーと素の PHP のハンドラー | 標準 | — | 第 15 章 |

- **型は使う。** `declare(strict_types=1)` で実行時に効く型を付け、PHPStan の最高レベルで配列の形まで検査する。**Ruby 版が RBS・Steep を使わないと決めたのと逆の選択**にし、「同じ動的型付けの言語でも、型の道具が言語本体にどこまで入っているかで開発の手触りが変わる」ことを示す。理由は第 5 章に書く
- **Rubix ML が決定木を持つので、ほぼ全章でライブラリとの突き合わせができる。** Elixir 版が「決定木が無いので自作が最終実装」だったのと正反対で、**第 3 波の 2 つの版が対照をなす**。記事ではこの対照を明示する
- **素の線形回帰は `Ridge(0.0)` で代用する。** Rubix ML に `LinearRegression` が無い。正則化を 0 にしたリッジが最小二乗にあたることを、自作の正規方程式と突き合わせて確かめる（第 7 章）
- **ラッソは自作が最終実装になる。** Rubix ML に無い。Elixir 版と同じく座標降下法を自作する（第 12 章）
- **乱数は `java.util.Random` と同じ線形合同法を自作する。** `mt_rand` はほかの言語版と並びが合わない。Elixir 版で書いた 48 ビットの線形合同法と同じものを PHP で書き、JVM の言語版・Elixir 版と並びをそろえる
- **カバレッジのしきい値は自作する。** PHPUnit に機能が無いので、clover の XML を読んで判定する短いスクリプトを `apps/php/` に置く。Elixir の `mix test --cover` に既定のしきい値があったのとは逆に、**しきい値は言語の標準ではなくプロジェクトの約束である**ことを第 6 章で扱う
- **Laravel・Symfony は使わない。** 第 15 章の主題（層の分離と統合テスト）には素のハンドラーと組み込みサーバーで足りる
- **各章は「自作してから Rubix ML と突き合わせる」構成にする。** 突き合わせられない章では、そのことを明記して自作の妥当性をテストで示す

### 章ごとのライブラリへの置き換え方針

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | `Rubix\ML\Classifiers\ClassificationTree` | 自作のジニ不純度の決定木と突き合わせる。**Elixir 版で突き合わせられなかった章がここで突き合わせられる** |
| 4〜6 | 無し | 道具立ての章。PHP-CS-Fixer・PHPStan・pcov・カバレッジのしきい値・Nix の環境定義を扱う |
| 7 | `Rubix\ML\Regressors\Ridge`（`alpha` を 0 にする） | 正規方程式を自作してから突き合わせる。**素の線形回帰が無いことをここで見せる** |
| 8 | `MissingDataImputer`・`OneHotEncoder` | 足りない口（グループ別の補完・基準列の削除）は自作 |
| 9 | `ZScaleStandardizer` | 自作の標準化と突き合わせる（n で割るか n−1 かを実測で確かめる） |
| 10 | `LogisticRegression`・`RandomForest` | **ランダムフォレストも突き合わせられる**（Elixir 版との違い） |
| 11 | `CrossValidation\Metrics`・`KFold` | 自作の評価指標・交差検証と突き合わせる |
| 12 | `Ridge` | ラッソは無いので自作のまま |
| 13 | `PrincipalComponentAnalysis` | 寄与率を持つかを実測で確かめてから突き合わせる |
| 14 | `Clusterers\KMeans` | 初期中心を渡せるかを実測で確かめる |
| 15 | `PersistentModel` | 学習済みモデルの保存・復元を標準の `serialize` と比べる |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| php-ai/php-ml | 保守がほぼ止まっており、PHP 8.4 での動作保証が無い。Rubix ML のほうが対応範囲も広い |
| xdebug（カバレッジ） | pcov より重い。カバレッジだけが目的なら pcov で足りる |
| Laravel・Symfony（API） | 第 15 章の主題には素のハンドラーで足りる。フレームワークの作法の説明が記事の重心を奪う |
| `mt_rand`（乱数） | ほかの言語版と並びが合わない。線形合同法を自作すれば JVM の言語版・Elixir 版と一致する |
| 型を使わない（Ruby 版と同じ扱い） | PHP は型宣言が言語本体に入っていて、書かないほうが不自然である。**Ruby 版と逆の選択にすることで、動的型付けの言語どうしの違いを見せられる** |
| `php83Packages.composer`（もとの環境のまま） | php 本体（8.4）と composer が動く PHP（8.3）が食い違う。`php.withExtensions` の `packages.composer` にすれば版をそろえられる |

## 影響

- 良い影響: **Rubix ML の対応範囲が広いので、第 3 波で Elixir 版と正反対の版になる。** 「ライブラリの成熟度が記事の重心をどう動かすか」を 2 つの版の対照として書ける
- 良い影響: 型を使う動的型付けの言語として、Ruby 版・Python 版・TypeScript 版との三者比較ができる
- 良い影響: Shift_JIS が標準の mbstring で読めるので、第 9 章の依存が減る
- 悪い影響: **Nix の PHP 環境にカバレッジドライバが無かったので、環境定義に手を入れる必要があった。** ほかの言語版では起きなかった前提の崩れである
- 悪い影響: **カバレッジのしきい値を自作する必要がある。** PHPUnit に機能が無い
- 悪い影響: 素の線形回帰とラッソが無いので、第 7 章と第 12 章で「無いものをどう代用・自作するか」を説明する手間が増える
