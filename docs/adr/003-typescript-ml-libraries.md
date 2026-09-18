---
type: ADR
title: "003 TypeScript 版の機械学習・データ・API ライブラリの選定"
description: "TypeScript 版のライブラリに TypeScript 6.0・Node.js 22・Vitest・ml.js 系・Hono + zod を採用し、ml.js で確認した機能と癖に基づいて章ごとの置き換え範囲を決める。"
tags: [adr,getting-start-ml,typescript]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-17T10:18:56Z }
---

# 003 TypeScript 版の機械学習・データ・API ライブラリの選定

「機械学習から始めるプログラミング入門」TypeScript 版で使うライブラリを決める。

日付: 2026-09-17

## ステータス

2026-09-17 提案されました

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) の「TypeScript 版執筆計画」で、データフレームのライブラリを使わず型付きレコードの配列で表すこと、TypeScript を 6.0 系・Node.js を 22 に固定すること、機械学習のライブラリを ml.js 系とすること、API を Hono + zod で作ることを承認した。ただし、ml.js の各パッケージが ESM・`strict` の TypeScript から使えるか、各章の「ライブラリへの置き換え」に使えるかは未検証だった。

そこで B13 で、npm レジストリの情報・パッケージのソースの確認と、使い捨てのプロジェクト（Node.js 22.18.0、TypeScript 6.0.3、`module: nodenext`・`strict`・`verbatimModuleSyntax`、Node.js の型除去で実行）での実行によって、次を確かめた。

| 確認したこと | 結果 | 確認方法 |
|------------|------|---------|
| TypeScript の版 | 最新は 7.0.2。typescript-eslint 8.70.0 の peerDependencies は `typescript >=4.8.4 <6.1.0` なので、6.0.3 を使う | npm レジストリ |
| Node.js の型除去 | Node.js 22.18.0 で `node main.ts` が警告なしに実行でき、`.ts` 拡張子付きの import も解決できた | 実行 |
| Nix の Node.js | 本リポジトリの `flake.lock` が固定する nixpkgs では `nodejs_22` が 22.21.1 | nixpkgs のソース |
| 型定義 | ml-matrix・ml-random-forest・ml-pca・ml-regression-multivariate-linear・ml-confusion-matrix・ml-kmeans・csv-parse・Hono・zod は型定義を同梱する（ml-regression-lasso も `package.json` の `typings` で同梱していた。B16 で訂正）。ml-cart・ml-logistic-regression・ml-cross-validation は同梱せず、`strict` では `TS7016` になる | `tsc --noEmit` |
| ESM からの読み込み | ml-kmeans・csv-parse・Hono・zod は ESM。ほかは CommonJS で、名前付き export（`DecisionTreeClassifier`・`RandomForestClassifier`・`PCA`・`EVD` など）とデフォルト export（ml-logistic-regression・ml-regression-multivariate-linear）で読み込めた。ml-regression-lasso はデフォルト export がオブジェクトで、名前付きの `LassoRegression` を使う | 実行 |
| ml-matrix | `solve` で連立方程式を解ける。`EVD` の `realEigenvalues` は小さい順に並ぶ | 実行 |
| ml-cart 2.1.1 | 分割基準は `gini` だけ、境界は平均値（`splitFunction: 'mean'`）だけ。`minNumSamples` の既定値は 3、`maxDepth` を指定できる。クラスの重み付けは無い。正解ラベルは 0 始まりの整数でなければならず、文字列を渡すと `RangeError: Invalid array length` になる。`toJSON` で木を JSON にできる | ソースと実行 |
| ml-random-forest 2.1.0 | `seed`（乱数は random-js の MersenneTwister19937）で結果を再現できる。`nEstimators`・`maxFeatures`・`replacement`・`useSampleBagging` を指定できる。木の数が少ないと、すべての木の学習に使われた標本の袋外の予測が空になり、`TypeError: input must not be empty` で学習が失敗する（40 件で 5 本は失敗、50 本は成功） | ソースと実行 |
| ml-logistic-regression 2.0.0 | 多クラスは 2 クラスの分類器を組み合わせる方式（one-vs-rest）で、ソフトマックスではない。`numSteps`・`learningRate` を指定する | ソースと実行 |
| ml-kmeans 7.0.1 | `initialization` に初期中心（`number[][]`）を渡せ、その場合の結果（割り当て・中心・反復回数）を取り出せる | 実行 |
| ml-pca 4.1.1 | `getExplainedVariance` で寄与率を取り出せる | 実行 |
| ml-regression-multivariate-linear 2.0.4 | `weights` は係数の後ろに切片を並べる | 実行 |
| ml-regression-lasso 0.1.2（MIT、2023-07 以降更新なし） | `lambda` で L1 の罰則を指定する座標降下法。`lambda: 0` で最小二乗解と一致した。リッジ回帰のパッケージは見つからなかった | 実行・npm の検索 |
| ml-confusion-matrix 2.0.0・ml-cross-validation 1.3.0 | `ConfusionMatrix.fromLabels` で正解率などを求められる。ml-cross-validation は `kFold`・`leaveOneOut` などを export する | 実行 |
| csv-parse 7.0.2 | `csv-parse/sync` の `parse` で `bom: true`・`columns: true` を指定すると BOM を除いて列名付きのオブジェクトにできる。値はすべて文字列で、空欄は `""` になる | 実行 |
| Shift_JIS | Node.js 22 の `TextDecoder('shift_jis')` で読める | 実行 |
| Hono 4.13.8 + zod 4.6.5 | `app.request` でサーバーを起動せずにリクエストを送れ、zod の `safeParse` の結果で 200 と 422 を返し分けられた | 実行 |
| Node.js の下限（B15） | ESLint 10 関連のパッケージが engines に `^20.19.0 \|\| ^22.13.0 \|\| >=24` を求めるので、`engine-strict` のもとでは Node.js 22.12.0 で `npm ci` が EBADENGINE になる。`engines` の下限を 22.13.0 にした。Nix の node 環境の TypeScript（5.9.3）はプロジェクトの 6.0.3 と違うが、npm scripts は `node_modules/.bin` の版を使う | 実行・CI のログ |
| danfojs-node 1.2.0 | 2025-04 以降更新が無く、`@tensorflow/tfjs-node` 3 系（ネイティブのバイナリ）と非推奨の `request` に依存する | npm レジストリ |

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・型チェック | TypeScript（`strict`・`noUncheckedIndexedAccess`・`erasableSyntaxOnly`） | 6.0.3 | Apache-2.0 | 第 1 章 |
| 実行環境 | Node.js（型除去で `.ts` を直接実行） | 22 | MIT | 第 1 章 |
| テスト・カバレッジ | Vitest、@vitest/coverage-v8 | 5.0.1 | MIT | 第 1 章（カバレッジは第 5 章） |
| 静的解析・整形 | ESLint、typescript-eslint、eslint-config-prettier、Prettier | 10.10.0、8.70.0、10.1.8、3.9.7 | MIT | 第 1 章（詳細は第 5 章） |
| CSV | csv-parse | 7.0.2 | MIT | 第 2 章 |
| 行列 | ml-matrix | 6.15.0 | MIT | 第 7 章 |
| 機械学習 | ml-cart、ml-random-forest、ml-logistic-regression、ml-kmeans、ml-pca、ml-regression-multivariate-linear、ml-regression-lasso、ml-confusion-matrix、ml-cross-validation | 2.1.1、2.1.0、2.0.0、7.0.1、4.1.1、2.0.4、0.1.2、2.0.0、1.3.0 | MIT | 第 3 章以降 |
| API | Hono、@hono/node-server、zod | 4.13.8、2.1.1、4.6.5 | MIT | 第 15 章 |

ライブラリは使う章に入ってから `apps/node/package.json` に正確な版で追加する。型定義を同梱しないパッケージには、プロジェクトの中に使う範囲だけの型宣言（`declare module`）を書く。

データフレームのライブラリは使わず、1 行を `interface` で型付けしたレコードの配列で表す。`Math.random` はシードを指定できないので、シード付きの疑似乱数生成器を第 2 章で自作する。

### 章ごとのライブラリへの置き換え方針

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | ml-cart | 正解ラベルを整数に変換し、`minNumSamples: 1`・`gainThreshold: 0` で自作の決定木と予測を突き合わせる（B14 で確認。既定の設定には README に無い `gainThreshold: 0.01` が含まれる。境界ちょうどの値は `<` で右に進み、多数決が同数のときは訓練データ全体で先に現れたラベルを選ぶ。iris では深さ 3 だけ 1 件の予測が違う） |
| 7 | ml-regression-multivariate-linear、ml-matrix の `solve` | 係数（切片が最後に並ぶ点に注意）と決定係数を突き合わせる |
| 8 | ml-cart | クラスの重み付けが無いので、重み付けの効果は自作で示し、突き合わせは重み付けなしで行う。モデルは JSON で保存する |
| 9 | なし | 標準化のライブラリを採用しないので、自作の標準化を最終実装とする（置き換えの節は理由を書いて省略する） |
| 10 | ml-logistic-regression、ml-random-forest | 正解率を比べる。ml-logistic-regression は one-vs-rest なので、ソフトマックスの自作とは予測の完全一致を求めない。ml-random-forest は木の数が少ないと学習が失敗することを学習用テストで記録する |
| 11 | ml-confusion-matrix、ml-cross-validation | 自作の評価指標・K 分割と突き合わせる |
| 12 | ml-regression-lasso | 自作のリッジ回帰は閉形式で書く。ラッソ回帰は ml-regression-lasso の `lambda` の定義（罰則の尺度）を第 12 章で確かめてから突き合わせる。リッジ回帰の置き換えは省略する |
| 13 | ml-pca、ml-matrix の `EVD` | 寄与率を突き合わせる。固有値の並び順（`EVD` は小さい順）と固有ベクトルの符号の違いを扱う |
| 14 | ml-kmeans | 同じ初期中心を渡して、割り当てと中心を突き合わせる |

### B15〜B17 で確かめた結果

| 章 | 確かめた結果 |
|----|------------|
| 7 | ml-regression-multivariate-linear 2.0.4 は特徴量の行列の最後に 1 の列を足すので `weights` の切片が最後に並び、`Xᵀ X` の逆行列を SVD で求める（ソース）。自作の正規方程式とノイズ付きの架空データ 30 件で係数・予測値が小数第 9 位まで、実データで決定係数が一致した。ml-matrix の `solve` は正方行列を部分ピボット選択付きの LU 分解で解き、対角成分が 0 の連立方程式でも自作と同じ解を返した。型定義を同梱するので `declare module` は不要 |
| 13 | ml-matrix の `EVD` は `isSymmetric()` で対称と判定すると `tred2`・`tql2` で計算し、最後に固有値を小さい順に並べ替える（非対称なら並べ替えず、虚部は `imaginaryEigenvalues`）。ml-pca 4.1.1 は型定義を同梱し、既定は SVD。`getExplainedVariance()`・`getEigenvalues()`（n − 1 で割った分散）は自作と小数第 9 位まで一致した。`getEigenvectors()` は列に主成分が並び符号はそろえない（`method: "covarianceMatrix"` では第 1 主成分の符号が SVD と逆）。向きをそろえると Boston の 15 主成分すべてが一致した |
| 12 | ml-regression-lasso 0.1.2 は特徴量と正解の両方を標準化してから `(1/2n)‖ts − Xs w‖² + λ‖w‖₁` を最小化する（特徴量が 1 つなら係数は「相関係数 − λ」を 0 で切ったもの）ので、scikit-learn・Tribuo の alpha とは尺度が違う。既定の `maxIter` 200・`tolerance` 1e-5 では相関の強い特徴量で収束せず、例外にならずに `converged: false` のまま係数を返す（実データの λ=0.01 でも発生）。`fitLasso` は tolerance 1e-10・上限 10 万回で学習し、収束しなければ例外にする。λ=0 は自作の最小二乗と 6 桁で、alpha=0 の自作リッジ回帰は ml-regression-multivariate-linear と 9 桁で一致した。ml-matrix の `add`・`mul`・`subRowVector` は呼び出した行列を書き換える |
| 14 | ml-kmeans 7.0.1 に同じ初期中心を渡すと、実データの k = 1〜10 × シード 10 通りの 100 通りすべてでクラスタ番号・中心・SSE・反復回数が自作と一致した。ml-kmeans は中心の移動が `1e-6` 以下で止まり（自作は中心が変わらなくなるまで）、返す `clusters` は更新前の中心への割り当てなので、反復を打ち切ると自作と違う。`maxIterations: 0` は上限なしを意味し（既定値 100）、アダプターでは 300 を明示した。k-means++ の k = 5 の SSE は 1058.77 で scikit-learn・Tribuo と同じ。データを `number[][]` で受け取るので、`readonly` の点は写してから渡す |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| TypeScript 7 系 | typescript-eslint が対応していない |
| danfojs-node | 更新が止まっており、ネイティブのバイナリと非推奨のパッケージに依存する |
| tsx（TypeScript の実行） | Node.js 22 の型除去で足り、依存を増やさない |
| seedrandom | シード付き乱数の仕組みを TDD で作る題材にするため |
| Fastify（API） | Hono の `app.request` で、サーバーを起動せずに統合テストを書ける |
| TensorFlow.js | 深層学習向けで、決定木・線形回帰などの古典的な手法を扱わない |

## 影響

- 良い影響: すべてのライブラリが MIT・Apache-2.0 で、サンプルコードの利用条件が単純になる
- 良い影響: ml-kmeans に初期中心を渡せ、ml-pca もあるので、第 13・14 章は Kotlin 版より置き換えの範囲が広い
- 悪い影響: ml.js の多くのパッケージは 2022〜2023 年から更新が無く、型定義の無いものは型宣言を自分で書く必要がある
- 悪い影響: ml-cart は正解ラベルを整数でしか受け取らず、ml-random-forest は木が少ないと学習に失敗するなど、API の癖をテストで押さえる必要がある
- 悪い影響: TypeScript を 6.0 系にとどめる必要がある。typescript-eslint が 7 系に対応したら移行を検討する

## コンプライアンス

- `apps/node/package.json` に上記のライブラリと版だけが記載されている
- Node CI（`.github/workflows/node-ci.yml`）がグリーンである
- 置き換えを省略した章の記事に、省略した理由が書かれている

## 備考

- 著者: claude-code/claude-opus-5
- 確認に使った使い捨てのプロジェクトはスクラッチパッドに置き、リポジトリには含めていない
