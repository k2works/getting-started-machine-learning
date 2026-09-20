---
type: ADR
title: "008 Go 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定"
description: "Go 版のライブラリに標準の testing・gofmt・go vet・golangci-lint・gonum・net/http を採用し、gonum に無いアルゴリズムを自作する範囲を決める。"
tags: [adr,getting-start-ml,go]
status: stable
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T04:30:00Z }
---

# 008 Go 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定

「機械学習から始めるプログラミング入門」Go 版で使うライブラリを決める。

日付: 2026-09-20

## ステータス

2026-09-20 提案されました

2026-09-20 承認されました（第 1〜15 章の執筆で確かめました）

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) の「対象言語」では、Go 版の機械学習ライブラリの候補を gonum としていた。Go は「ライブラリが限定的なので自作の比重が大きい」言語として選んでおり、どこまでライブラリに任せられるかを確かめる必要があった。

B39 のステップ 1 で、Go module proxy・モジュールのキャッシュ・使い捨てのプロジェクトによって、次を確かめた。

| 確認したこと | 結果 | 確認方法 |
|------------|------|---------|
| gonum の版とライセンス | 最新は v0.17.0（2025-12-29）、BSD 3 条項 | Go module proxy、`LICENSE` |
| gonum にあるもの | `stat` に `LinearRegression`（単回帰）・`CovarianceMatrix`・`PC`（主成分分析）・`ROC`、`mat` に行列 | `stat` のファイル一覧と関数 |
| gonum に無いもの | 決定木・ランダムフォレスト・K-means・ロジスティック回帰 | 同上 |
| `stat.LinearRegression` | 単回帰だけで、切片と傾きを返す。重回帰は `mat` で正規方程式を解く | 使い捨てのプロジェクトで実行 |
| `stat.PC` | `PrincipalComponents` が成功可否を返し、`VarsTo` で分散、`VectorsTo` で固有ベクトルの行列を取れる。寄与率は分散から自分で求める | 同上 |
| GoLearn | 最新が 2022-12-28 のコミット（タグ無し）で、3 年以上更新されていない | Go module proxy |
| 静的解析 | わざと崩したファイルで、`gofmt -l` がファイル名を、`go vet` が「declared and not used」を、`golangci-lint run` が同じ指摘を typecheck として報告した | 使い捨てのプロジェクト |
| Nix 環境 | Go 1.25.5、golangci-lint 2.7.2（`nix eval` が示す nixpkgs の最新は 2.8.0 だが、`flake.lock` で固定された環境に入るのは 2.7.2）、gopls 0.21.0。手元は Go 1.26.5 | `nix eval`、`go version` |
| testify | 最新 v1.12.1（2026-08-17）、MIT | Go module proxy |

### 各章で確かめた結果

| 章 | 確かめたこと |
|----|------------|
| 1 | わざと崩したファイルで、`gofmt -l` がファイル名を、`go vet` が「declared and not used」を、`golangci-lint run` が同じ指摘を typecheck として報告した。`go test ./...` は Nix の Go 1.25.5 でも手元の 1.26.5 でも動く（`go.mod` の `go` 指令は 1.25） |
| 2 | Go の `math/rand` は、Java・Scala の `java.util.Random` とも .NET の `Random` とも乱数列が違う。同じ Fisher-Yates・同じシード 0 でも並びが違い（Go は `[6 8 2 3 7 5 9 1 0 4]`、Java は `[4 8 9 6 3 5 2 1 7 0]`）、分かれる行も訓練データの平均値もほかの言語版と一致しない。件数（105 件と 45 件）は一致する。`Features` はスライスを持つので `==` で比べられず、テストでは `reflect.DeepEqual` を使う。`rand.New(rand.NewSource(seed))` は `gosec` の指摘（G404）を受けるので、再現のための擬似乱数である理由をコメントに書いて `//nolint:gosec` で抑える |
| 3 | 判別共用体が無いので、非公開のメソッドを持つインターフェース（`type Tree interface { isTree() }`）と `Leaf`・`Node` の構造体で決定木を表し、予測は型スイッチにした。網羅性はコンパイラが検査しないので、既定の分岐でエラーを返す。無名の構造体では `float64(count)` の変換がコンパイルを通らず、`labelCount` 型を起こした。gonum に決定木は無いので、ライブラリとの突き合わせの節は省略した。深さ 2 の決定木のテストデータの正解率は 45 件中 43 件で Java 版と同じだが、深さ 1・3・4・5・制限なしの正解率と分割の境界（花弁幅 0.6900、Java 版は 0.6500）は分け方が違うので一致しない |
| 4〜6 | `go test` はパッケージのディレクトリで走るので、既定の `../data/sukkiri-ml` には届かず実データのテストは必ずスキップされる（`ML_DATA_DIR` を渡す）。テストは全 64 件で、データ無し 55 PASS / 9 SKIP、カバレッジはデータ無し 60.6%・データ有り 79.2%。`gofmt` は CRLF のファイルを「未整形」と報告するので `.gitattributes` に `apps/go/** text=auto eol=lf` を足した。golangci-lint の既定 5 検査器で足り、`mnd` は採らない。`errcheck` が `defer file.Close()` を指摘するので `os.ReadFile` か `defer func(){ _ = file.Close() }()` を使う。vendoring と `tool` 指令は使わない |
| 7 | `stat.LinearRegression` は `error` を返さずパニックするので、包む側で件数を検査する。`mat.VecDense.SolveVec` は非正方行列に QR の最小二乗解を返すので正規方程式を組まなくても同じ解が出る（実測で一致）が、ほかの言語版と式をそろえるため正規方程式を明示する。`map` は反復順がランダムなので係数は列名スライス＋値スライスで持つ。単回帰は gonum と 1e-9 で一致するが、重回帰を突き合わせる相手は gonum に無い |
| 8 | `map` のキーにスライスを使えないので、グループキーは `\x1f` 区切りの文字列にした。モデルの保存は `encoding/gob`。インターフェースの具体型を `gob.Register` で登録しないと保存時に失敗し、公開フィールドしか保存されない。Java のシリアライズと違い任意の型を復元しないので `ObjectInputFilter` 相当は不要。第 3 章の `Tree` はラベルが `string` で型引数を後付けできないため、整数ラベルの木を作り直し `Split` だけ共有した |
| 9 | gonum の `stat.StdDev` は標本標準偏差（n−1 で割る）で、scikit-learn の `StandardScaler` やほかの言語版の母標準偏差と食い違う（換算は √((n−1)/n)）。標準化は自作を最終実装にし、gonum は突き合わせに使う。重回帰は `mat.SymDense.SymOuterK` と `mat.Cholesky` で正規方程式を解く（`Factorize` は `bool` 返しなので `error` に変える）。Shift_JIS は標準ライブラリで読めず `golang.org/x/text/encoding/japanese` が要る。デコーダは例外を投げず U+FFFD に置換するので、文字コード違いは実行時に気づけない |
| 10 | ロジスティック回帰もランダムフォレストも gonum に無いので自作が最終実装。特徴量重要度は木ごとに正規化してから平均する（ほかの言語版と同じ） |
| 11 | `stat.ROC` は使えて、自作の ROC・AUC と 1e-12 以内で一致した。ただし入力は昇順必須で、破ると `error` ではなくパニックする（`stat.SortWeightedLabeled` で先に並べる）。正解ラベルは `[]bool` 固定で、AUC は返らないので `integrate.Trapezoidal` を別に呼ぶ。混同行列・適合率・再現率・F 値・K 分割交差検証は gonum に無い。staticcheck の ST1005 は、日本語のエラー文でも先頭が英語の列名だと「大文字で始まる」と判定するので、列名は後ろに回す |
| 12 | 正則化付き回帰は gonum に無いので自作が最終実装。ラッソの目的関数は `‖t − X w‖² + alpha·Σ|w|`（平均で割らない流儀）なので、alpha の値はほかの言語版と比較できない |
| 13 | gonum には主成分分析（`stat.PC`）がある。ただし `VarsTo` は分散までで寄与率は自前計算になり、符号もそろわないので、自作を最終実装として `stat.PC` を検証の相手にした。固有値分解は `mat.EigenSym` で、固有値は小さい順・固有ベクトルは列に並ぶ。`mat.NewSymDense` に非対称な値を渡してもエラーにもパニックにもならず、右上の三角だけが使われる。実データでは `mat.EigenSym` と `stat.PC` が第 2 主成分だけ逆向きのベクトルを返した（値は小数第 13 位まで一致）ので、符号そろえは必須。突き合わせの許容幅は 1e-8 |
| 14 | gonum にクラスタリングは無いので自作が最終実装 |
| 15 | Web フレームワークを入れず標準の `net/http` で足りる。Go 1.22 のルーティングでメソッドとパスを書け、404 と 405 は `ServeMux` が返す。`json.NewDecoder` はストリームを読むので、JSON が 2 つ続いていても 1 つ目だけ読んで成功する（本文の残りが `io.EOF` であることを確かめる）。デコードのエラーのメッセージには内部の型名が入るので、そのまま返さない。`http.ListenAndServe` の 1 行では時間切れが無制限になるので `http.Server` に `ReadHeaderTimeout` を設定する（gosec の G112） |

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・ビルド | Go（`go.mod` の `go` 指令は 1.25）、Go Modules | 1.25 | — | 第 1 章 |
| テスト | 標準の `testing`（表駆動テスト） | Go と同じ | — | 第 1 章 |
| 整形 | `gofmt`（`gofmt -l` で検査） | Go と同じ | — | 第 1 章（記事での解説は第 5 章） |
| 静的解析 | `go vet` と `golangci-lint`（既定の検査器は errcheck・govet・ineffassign・staticcheck・unused。マジックナンバーの `mnd` は既定で無効） | 2.7.2 | — | 第 1 章（記事での解説は第 5 章） |
| カバレッジ | `go test -cover` | Go と同じ | — | 第 1 章（記事での解説は第 5 章） |
| データの表現 | 構造体と `map[string]string`（セルの文字列）。データフレームのライブラリを使わない | — | — | 第 1 章 |
| 乱数 | `math/rand` の `rand.New(rand.NewSource(seed))` と Fisher-Yates | Go と同じ | — | 第 2 章 |
| 機械学習 | gonum（線形回帰・共分散行列・主成分分析・ROC） | v0.17.0 | BSD 3 条項 | 第 7 章 |
| 文字コード | `golang.org/x/text/encoding/japanese`（Shift_JIS の CSV） | v0.40.0 | BSD 3 条項 | 第 9 章 |
| 行列 | gonum の `mat`（自作しない） | v0.17.0 | BSD 3 条項 | 第 7 章 |
| API | 標準の `net/http`（Go 1.22 以降のルーティング） | Go と同じ | — | 第 15 章 |

- **GoLearn は使わない。** 3 年以上更新されておらず、保守されているとは言えない
- **gonum に無いアルゴリズムは自作を最終実装とする。** 決定木（第 3 章）・ロジスティック回帰とランダムフォレスト（第 10 章）・K-means（第 14 章）が該当し、置き換えの節は「ライブラリ未対応」と理由を書いて省略する。TypeScript 版（ml.js が未成熟）と同じ扱い
- **行列は gonum の `mat` を使う。** ほかの言語版は行列を自作したが、Go には標準の数値計算ライブラリとして `mat` があり、正規方程式を解く手順を見せるうえでも使いやすい。自作との違いは第 7 章で説明する
- 依存は `go.mod` にまとめ、使う章に入ってから追加する

### 章ごとのライブラリへの置き換え方針

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | なし | 決定木が無いので自作を最終実装とし、置き換えの節を省略する |
| 7 | gonum の `mat`（正規方程式）と `stat.LinearRegression`（単回帰） | 重回帰は `mat` で解き、単回帰の結果を `stat.LinearRegression` と突き合わせる |
| 8 | なし | 決定木が無いので自作のみ |
| 9 | gonum の `stat.Mean`・`stat.StdDev` | 自作の標準化と突き合わせる（標本標準偏差か母標準偏差かを第 9 章で確かめる） |
| 10 | なし | ロジスティック回帰・ランダムフォレストが無いので自作のみ |
| 11 | gonum の `stat.ROC` と `integrate.Trapezoidal` | ROC・AUC は突き合わせ、混同行列と交差検証は自作のみ |
| 12 | なし | 正則化付き回帰が無いので自作のみ |
| 13 | gonum の `stat.PC` | 自作の固有値分解と突き合わせ、寄与率は分散から求める。符号はそろえる |
| 14 | なし | K-means が無いので自作のみ |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| GoLearn | 2022 年 12 月から更新されていない |
| gota（データフレーム） | ほかの言語版と同じく、構造体と map で表す。保守も緩やか |
| testify | 標準の `testing` と表駆動テストで足りる。依存を増やさない |
| gofumpt | `gofmt` で足りる |
| Echo・Gin（API） | 第 15 章の主題（層の分離と統合テスト）には標準の `net/http` で足りる |
| 行列の自作 | gonum の `mat` が標準的で、正規方程式・固有値分解のどちらにも使える。自作はほかの言語版で示している |

## 影響

- 良い影響: 依存が gonum だけになり、標準ライブラリの比重が大きい Go らしい構成になる
- 良い影響: 自作の比重が大きいので、第 3・8・10・12・14 章は「ライブラリが無いときにどう作るか」を示せる。TypeScript 版と並べて読める
- 悪い影響: ライブラリとの突き合わせができる章が第 7・9・11・13 章に限られ、ほかの言語版より「自作とライブラリを比べる」節が少ない
- 悪い影響: 行列だけ gonum を使うので、ほかの言語版の「行列も自作する」構成とは違う。その理由を第 7 章に明記する

## コンプライアンス

- `apps/go/go.mod` に gonum と `golang.org/x/text` だけが記載されている
- Go CI（`.github/workflows/go-ci.yml`）がグリーンである
- わざと違反を入れると `gofmt -l`・`go vet`・`golangci-lint run` が失敗する（B39 で確かめる）

## 備考

- 著者: claude-code/claude-opus-5
