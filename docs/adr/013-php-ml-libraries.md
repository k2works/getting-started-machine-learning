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

2026-09-24 承認されました（第 1〜15 章の執筆で確かめました）

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

### 各章で確かめた結果

| 章 | 確かめたこと |
|----|------------|
| 1 | `fgetcsv` は BOM を取り除かないので、先頭の列名から自分で取り除く。`escape: ''` を明示しないと RFC 4180 と違う解釈になる（PHP 8.4 で非推奨）。`(int) '高い'` は 0 を返して落ちないので `filter_var(..., FILTER_VALIDATE_INT)` で厳密に検査する。**素の Nix 環境にカバレッジドライバが無かった**ので pcov を足し、composer も同じ PHP から取って版（8.4 と 8.3）をそろえた。**PHPUnit に最低カバレッジのしきい値の機能が無い**ので clover の XML を読む判定を自作した（終了コードは 3 と自分で決めた）。`failOnDeprecation="true"` が `string $csvFile = null` の非推奨を見つけた。終了コードは PHP-CS-Fixer が **8**、PHPStan が 1、PHPUnit が 1 |
| 2 | **`java.util.Random` と同じ 48 ビットの線形合同法を自作した。** `shuffle(0..9, 0)` が `[4, 8, 9, 6, 3, 5, 2, 1, 7, 0]` で Java 版・Kotlin 版・Scala 版・Clojure 版・Elixir 版と一致し、訓練データのがく片長さの平均（0.4215384615384616）も 1e-15 まで一致した。**PHP の整数は溢れると float に化けて静かに精度を失う**（Java のように折り返さず、Elixir のように多倍長にもならない。`Deprecated: Implicit conversion from float ... loses precision` が出るだけ）ので、state を上下 24 ビットに分けて掛ける。**`?:` と `if ($value)` は 0.0 を偽とする**ので、0.0〜1.0 に正規化されたデータでは欠損値の判定に使えない（`!== null` で書く）。配列は値としてコピーされるので `shuffle` の中で書き換えても元の配列は変わらない。`array_filter` は添字を保つので `list<T>` には `array_values` が要る |
| 3 | 自作の木の深さごとの正解率と深さ 2 の境界（0.2950・0.6500）が Elixir 版・Java 版・Scala 版・Clojure 版と一致した。**`ClassificationTree` は既定値のままでは決定的でない。** `CART::split()` が使う列を `array_rand` で毎回選び（`maxFeatures` の既定は `round(sqrt(n))`）、**シードを渡す口が無い**（既定のまま 5 回走らせて `0.8889 0.8889 0.8889 0.8889 0.8222` と揺れた）。連続値の候補も分位点で丸める（`maxBins` の既定は `1 + round(log2(m))`）。`maxLeafSize: 1`・`minPurityIncrease: 0.0`・`maxFeatures: 列数`・`maxBins: 大きい値` を明示して初めて比べられ、深さ 1〜5 で完全一致した。**「ライブラリにある」と「そのまま比べられる」は別である。** 深さ制限なしのときだけ 0.9333 対 0.9111 で食い違い、原因は**境界の取り方**（自作＝隣り合う値の中点、Rubix ML＝データに実在する値）だった。**PHP の配列は「数字だけの文字列」の鍵を整数に変える**ので、`@return array<string, int>` と書いても PHPStan レベル 9 は通してしまい、テストで初めて見つかった（型の道具をどれだけ使っても言語の癖は型では守れない） |
| 7 | 切片 6114.60・係数・R² 0.6184・MAE 302.20・RMSE 376.14 が Java 版・Scala 版・Clojure 版・Elixir 版と一致。**`Ridge` は自作とまったく同じ正規方程式を解いていた**（1 の列を `augmentLeft` で足して `XᵀX` を作る）。違うのは最後の一手だけで、Rubix ML は `inverse()`、自作は MathPHP の LU 分解。**切片が 6114.5955056944 対 6114.5955056945 と 12〜13 桁まで一致した。** Elixir 版で Scholar の `pinv`（SVD）が最小二乗解に届かず係数が 209 と 579 に分かれた場所が、ここでは一致する（アルゴリズムの理論的な安定性より、その実装の精度のほうが効く場合がある）。**`new Ridge()` の `l2Penalty` は既定 1.0** なので、省くと別のモデルになる。**MathPHP の `solve()` は行列の中身で解き方を変える**（2×2 なら逆行列、RREF があればそれ、なければ LU、失敗したら QR）ので `NumericMatrix::LU` を明示して固定した |
| 8 | 891（342/549）・712/179 件・深さ 5 の正解率と人数・学習した前処理の値が Java 版・Scala 版・Clojure 版・Elixir 版と一致。**Rubix ML に足りない口が 3 つ**。`MissingDataImputer` は列ごとに 1 値しか持てずグループ別に使い分けられない（`Percentile(50)` の値自体は自作の中央値と一致）。`OneHotEncoder` は基準列を落とさず、カテゴリの順が「最初に現れた順」（自作は値の順）。`KMostFrequent` は `arsort` なので**同点なら先に現れたほうが残る**。**クラスの重みは分類器にも前処理にも口が無い**ので、第 3 章の決定木を「1 件ごとの重みを通す」形に書き直したものが最終実装。**`unserialize()` に `allowed_classes` は必須**（既定でどんなクラスでも作る）。**`unserialize()` も `mkdir()` も失敗を例外ではなく警告で知らせる**ので、`failOnWarning="true"` の下では `@` で抑えないと「壊れたファイルを読む」テストが書けない。**ラベルを配列のキーにすると型が往復しない**（`'1'` は整数 1 になる）。**`ksort` の既定は数字らしい文字列を数値として比べる**ので `SORT_STRING` を明示する |
| 9 | 決定係数 8 個（0.6056/0.6950、0.7740/0.8628、0.7953/0.8213、0.6717/0.7947）と外れ値 8 件が Java 版・Scala 版・Clojure 版・Elixir 版と一致。**`ZScaleStandardizer` は 1e-12 で自作と一致**（n で割る母標準偏差。Tribuo の n−1 とは違い Scholar と同じ側）。ただし `transform()` は**引数を参照で書き換える** API である。**Shift_JIS の取り違えはどちら向きでも例外にならない**。Shift_JIS を UTF-8 として読むと不正なバイト列がそのまま入り、UTF-8 を CP932 として読むと**化けたうえで UTF-8 としては正しい**文字列になる（後者は `mb_check_encoding` でも見抜けない）。読み込む前に `mb_check_encoding($bytes, 'UTF-8')` で判定はできる。洗浄したときの置換文字は U+FFFD ではなく `?`（0x3F）。Java の例外・Clojure の U+FFFD・Elixir の片方向に続く 4 つめの振る舞い。**mbstring は標準なので依存は 1 つも増えない** |
| 10 | 自作モデルの正解率 8 個が Java 版・Clojure 版・Elixir 版と一致。**特徴量の重要度 4 個（0.1882/0.1271/0.2708/0.4140）が Java 版・Clojure 版と完全に一致した**（Elixir 版だけ 4 桁目がずれた原因＝ジニ不純度の和を取る順で同点に 1 ulp の差が付く、を PHP は踏まない。**PHP の連想配列が挿入順を保つ**ため）。**`Rubix\ML\Classifiers\LogisticRegression` は 2 クラス専用**で、3 品種を渡すと `Number of classes must be 2, 3 given.` で落ちる（多クラスは `SoftmaxClassifier`）。既定の学習率 0.01 では訓練 0.6762 と自作（0.9143）に遠く、1.0 にすると 0.9333。**Elixir 版で犯人だった正則化はここでは効いていない**（`l2Penalty` の既定が 1e-4 で、Scholar の `alpha: 1.0` より 4 桁小さい）。`RandomForest` の正解率はテストデータ 0.9333 で自作と一致したが（`ratio` の既定 0.2、`maxFeatures` が「節ごと」対 自作「木ごと」を揃えたうえで）、**重要度は一致しない**（森の作り方と重要度の定義が違う）。**Rubix ML のモデルはシードを受け取らず大域の `mt_rand` から引く**ので、種を付けないと実行のたびに正解率が変わる（softmax で 0.4889〜0.7556）。**`exp(1000)` も `log(0)` も落ちず `INF`/`-INF` を返す**ので、静かに NaN が後段に混ざる（Elixir の `ArithmeticError` と逆） |
| 15 | **置き場の約束は interface で表せる。** Elixir 版が behaviour のために `{モジュール, 状態}` の組と振り分け関数を必要とした回り道が消え、偽物のモデルは無名クラスでその場に書ける（Clojure の `reify` に近い）。本物と偽物の両方に同じ約束のテストを走らせる。**予測値は 7730.457421687019 で、Java 版・Scala 版・Clojure 版（…023）とも Elixir 版（…016）とも一致しなかった**（分割は自作の乱数で完全一致しており、差は正規方程式を解く実装の丸めの順。第 7 章の 13 桁目のずれが、特徴量を掛けて足すぶん 12 桁目に出た）。**保存・読み込みの往復は実データでも 1 ビット一致する**（`serialize_precision` の既定 `-1` が最短往復表記を選ぶ）。**PHP は JSON のオブジェクトと配列を区別しない**ので `json_decode(..., true)` の結果を `array_is_list` で弾く（しかも `{"0": 1}` はリスト扱いになる）。**`json_decode` は失敗を `null` で返す**ので「読めなかった」と「本文が null」を区別できず、`JSON_THROW_ON_ERROR` が必須。**組み込みサーバーはプログラムから起動できない**（`php -S` 自体がサーバーで、要求ごとにスクリプトを最初から走らせる）ので、学習・保存と待ち受けがコマンドとして分かれ、結果として組み立てのクラスまで 100% テストできた。プロセスに状態が残らず置き場は毎回ファイルから読むが、実測 0.12 ミリ秒。**型で守れるものを約束のテストに書くと PHPStan に「常に真である」と叱られる**ので、「同じ入力には同じ答え」「値が有限」という型では書けない約束に書き換えた |
| 11 | 6 指標（正解率 0.7811・適合率 0.7759・再現率 0.6306・F 値 0.6833・RMSE 405.77・MAE 321.53）が Java 版・Scala 版・Clojure 版・Elixir 版と一致。`Accuracy`・`MulticlassBreakdown` の正例の行・誤差指標は自作と 1e-12 で一致した（ただし **Rubix の `Metric` は「大きいほどよい」に統一されているので誤差指標は負値を返す**）。**`FBeta` は 2 値でもマクロ平均**（クラスごとの適合率・再現率を平均してから調和平均）なので自作 0.8 対 Rubix 0.7895 と食い違う。どちらも正しい F 値で定義が違う。**`Labeled::fold()` は `floor(件数/分割数)` で余りを捨てる**（891 件 5 分割で自作 179,178,178,178,178 対 Rubix 178×5。**Elixir 版の Scholar `k_fold_split/2` と完全に同じ振る舞い**）。**`KFold::test()` は `randomize()` を呼ぶがシードを渡す口が無い**（5 回で 0.7718〜0.7819 と揺れる）。**`Reports\ConfusionMatrix` は行が予測・列が正解**（教科書や Scholar とは逆向き）。**ROC 曲線・AUC は Rubix ML に無い**ので自作が最終実装。**`stratifiedFold()` はラベルを配列のキーにするので `'0'`/`'1'` が整数に化けて落ちる**（第 3 章で自分のコードに見つけた癖に、ライブラリのほうが落ちた）。**配列のキーに float は使えない**ので ROC のグループ化は `usort` で書く |
| 12 | 実験表全体・選んだ alpha 10.0・テスト R² 0.5224/0.6243・ラッソが 0 にした 3 列が Java 版・Scala 版・Clojure 版・Elixir 版と一致。**`Ridge` は実データ（9 列 47 件）でも係数差 4.4e-15〜1.6e-13 で自作と一致した**（中心化せず 1 の列を足して切片の罰則だけ 0 にしており、数学的に同じ。違いは `inverse()` 対 LU 分解のみ）。**ラッソは Rubix ML に無い**（`Regressors` は 10 個で Lasso も ElasticNet も無い）ので座標降下法を自作。**PHP 8 では `1.0 / 0.0` も `DivisionByZeroError`**（第 10 章の「`exp(1000)`・`log(0)` は静かに INF を返す」と対照的）。**関数を返す関数の PHPDoc は戻り値側を括弧で包まないと構文誤りになり**、返す `Closure` の引数には `list<string>` を書けない（型を使うと決めた版の代償がいちばん出た場所） |
| 13 | 寄与率（PC1 0.4110、累積 0.8427）・必要な主成分の数 6・主成分の係数が Java 版・Scala 版・Clojure 版・Elixir 版と一致。**`PrincipalComponentAnalysis` が公開しているのは `lossiness()` と `transform()` だけで、固有ベクトルも平均も `protected`、寄与率のプロパティは無い**（Scholar より狭く、Rumale よりも狭い）。それでも 2 つとも復元できた——主成分は `transform()` が線形変換であることを使って「平均＋単位ベクトル」を通し、寄与率は次元数を 1 ずつ増やして `1 - lossiness()` の差分を取る（リフレクションを使わず内部の名前に依存しない）。実データ 15 列での差は寄与率 2.78e-16、主成分 1.97e-14。**Rubix ML の共分散は n で割り自作は n−1 だが、寄与率は定数倍で約分されるので一致する**。**MathPHP が実データで落ちた**——`Eigenvalue::jacobiMethod()` が返したばかりの固有値を同じ行列とともに `Eigenvector::eigenvectors()` に渡すと `BadDataException: ... is not an eigenvalue of this matrix`（掃き出し法で零空間を探す実装が 15×15 の浮動小数点に耐えない）。Jacobi 回転による固有値分解を自作して解決し、MathPHP の固有値は突き合わせの相手として残した（実データでの差 3.98e-6。MathPHP の `isDiagonal()` の判定がゆるく自作より早く止まる） |
| 14 | 自作の SSE 列・シード別の局所解の表・クラスタごとの件数と平均支出額が Java 版・Scala 版・Clojure 版・Elixir 版と一致。**初期中心は `Seeders\Preset` で渡せた**（シリーズ初。Scholar は渡せなかった）。ただし **`KMeans` はミニバッチ K-means** なので同じ初期中心から始めても一致せず（`n_init` にあたる引数も無い）、同じ定義の SSE で比べた。**10 のクラスタ数すべてで Rubix ML のほうが SSE が大きい**（Elixir 版で k=4 以降 Scholar が小さかったのと逆）。最も明快なのは k=1 で、答えが全点の平均に決まっているのに 2640.00 対 2664.04。**`sprintf('%.0f')` と `round()` で 0.5 の丸め方向が違う**（`Milk` の平均がちょうど 34708.5 で、`sprintf` は偶数側の 34708、Java・Elixir の `round` は 34709）。**配列の `===` が入れ子を再帰的に比べる**ので K-means の収束判定がそのまま書ける（Java の `deepEquals` ＋ `clone` が要らない）。**`<=>` が配列どうしを比べる**ので「件数の降順、同数なら番号の昇順」が 1 行。`usort()` が安定ソートになったのは PHP 8.0 から |

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・ビルド | PHP と Composer（`composer.json`・`composer.lock`） | PHP 8.4 | PHP License / MIT | 第 1 章 |
| テスト | PHPUnit。実データのテストは `#[Group('data')]` で外せるようにする | 11.5 | BSD-3-Clause | 第 1 章 |
| 整形 | PHP-CS-Fixer（`@PSR12`） | 3.x | MIT | 第 1 章（記事での解説は第 5 章） |
| 静的解析 | PHPStan（レベル 9。上限は 10） | 2.x | MIT | 第 1 章（記事での解説は第 5 章） |
| カバレッジ | pcov ＋ PHPUnit の clover 出力。**しきい値は自作のスクリプトで判定する** | pcov 1.0 | BSD-3-Clause | 第 1 章（記事での解説は第 5 章） |
| データの表現 | 素の配列と `readonly class`（値の持ち方は PHPStan の配列の形で表す） | — | — | 第 1 章 |
| CSV | 標準の `fgetcsv`（BOM は自分で取り除く） | 標準 | — | 第 1 章 |
| 文字コード | mbstring の `CP932`（追加の依存なし） | 標準 | — | 第 9 章 |
| 乱数 | **`java.util.Random` と同じ線形合同法を自作する** | — | — | 第 2 章 |
| 機械学習 | Rubix ML | 2.6 | MIT | 第 3 章 |
| 数学 | MathPHP（正規方程式の行列計算） | 2.x | MIT | 第 7 章 |
| 直列化 | `serialize`（学習済みモデル）、`json_encode`（API の JSON） | 標準 | — | 第 8 章 |
| API | 標準の組み込みサーバーと素の PHP のハンドラー | 標準 | — | 第 15 章 |

- **型は使う。** `declare(strict_types=1)` で実行時に効く型を付け、PHPStan のレベル 9（上限は 10）で配列の形まで検査する。**Ruby 版が RBS・Steep を使わないと決めたのと逆の選択**にし、「同じ動的型付けの言語でも、型の道具が言語本体にどこまで入っているかで開発の手触りが変わる」ことを示す。理由は第 5 章に書く
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
