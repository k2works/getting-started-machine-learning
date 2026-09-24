---
type: ADR
title: "014 Haskell 版のビルド・テスト・静的解析・線形代数・API ライブラリの選定"
description: "Haskell 版のライブラリに cabal・Hspec・fourmolu・hlint・HPC・hmatrix・cassava・statistics・Scotty を採用し、hmatrix のために Nix の環境に openblas を足す方針を決める。機械学習のアルゴリズムは自作が最終実装になる。"
tags: [adr,getting-start-ml,haskell]
status: proposed
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 014 Haskell 版のビルド・テスト・静的解析・線形代数・API ライブラリの選定

「機械学習から始めるプログラミング入門」Haskell 版で使うライブラリを決める。

日付: 2026-09-24

## ステータス

2026-09-24 提案されました

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) の「第 3 波の執筆計画」では、Haskell を「型の検査が最も厳しく、ライブラリが最も少ない見込み」として最後に書くとした。対比の相手は Rust 版・F# 版（型でエラーを表す）、F# 版・Clojure 版・Elixir 版（純粋関数）、Rust 版・Scala 版（型クラスによる抽象）である。

B70 のステップ 1 で、使い捨ての cabal プロジェクトを `nix develop .#haskell` の中で動かして次を確かめた。

| 確認したこと | 結果 | 確認方法 |
|------------|------|---------|
| Nix 環境 | GHC 9.10.3、cabal 3.16.0.0、stack 3.7.1 | `nix develop .#haskell` |
| GHC 同梱 | `array`・`bytestring`・`containers`・`text`・`deepseq` はあるが **`vector` は無い** | `ghc-pkg list` |
| 整形・静的解析 | **素の環境に `fourmolu` も `hlint` も無く、手元の `/usr/local/bin` が見えていた** | `which` |
| 検査の終了コード | **fourmolu の `--mode check` は違反があると 100**。hlint は 1 | わざと崩したファイル |
| テスト | Hspec 2.11。**テスト名に日本語を使える**。`hspec-discover` で自動収集できる。**テストスイートの `build-depends` はライブラリと別に書く** | 使い捨てのプロジェクト |
| カバレッジ | HPC（`cabal test --enable-coverage`）。GHC 組み込みで追加の依存が要らない | 同上 |
| 線形代数 | **hmatrix 0.20.2 は BLAS/LAPACK を要求し、素の環境では configure で止まる**（`Missing (or bad) C libraries: blas, lapack`）。`openblas` を環境に足し `LIBRARY_PATH`・`PKG_CONFIG_PATH` を通すとビルドできた | 同上 |
| CSV | **cassava は BOM を取り除かない**（Go 版・Clojure 版・Elixir 版・PHP 版と同じ）。`decodeByName` で列名つきに読める | BOM つきのファイルで確認 |
| 統計 | statistics 0.16.5.0。純 Haskell で入る | 使い捨てのプロジェクト |
| 整数 | **`Int` は 64 ビットで溢れると折り返す**（`maxBound + 1` が `minBound`）。PHP のように float に化けない | `cabal repl` |
| 乱数 | `java.util.Random` と同じ線形合同法を素直に書ける。**シード 0 の並びが `[60, 48, 29, 47, 15]` で PHP 版と一致した** | 同上 |
| 直列化 | `Data.Binary` の `encode`／`decode` で往復した | 同上 |
| API | Scotty 0.30 と aeson 2.3.2.0 | ビルドの成功を確認 |

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・ビルド | GHC と cabal（`*.cabal`・`cabal.project`） | GHC 9.10・cabal 3.16 | BSD-3-Clause | 第 1 章 |
| テスト | Hspec（`hspec-discover` で収集）。実データのテストはデータが無ければスキップする | 2.11 | MIT | 第 1 章 |
| 整形 | fourmolu（`--mode check`。**違反の終了コードは 100**） | 0.19 | BSD-3-Clause | 第 1 章（記事での解説は第 5 章） |
| 静的解析 | hlint と GHC の `-Wall`（警告をエラーにする） | hlint 3.10 | BSD-3-Clause | 第 1 章（記事での解説は第 5 章） |
| カバレッジ | HPC（`cabal test --enable-coverage`） | GHC 組み込み | BSD-3-Clause | 第 1 章（記事での解説は第 5 章） |
| データの表現 | レコード（`data Person = Person { ... }`）と `Map`・リスト | — | — | 第 1 章 |
| CSV | cassava（BOM は自分で取り除く） | 0.5 | BSD-3-Clause | 第 1 章 |
| 乱数 | **`java.util.Random` と同じ線形合同法を自作する** | — | — | 第 2 章 |
| 線形代数 | **hmatrix（`openblas` を環境に足す）** | 0.20.2 | BSD-3-Clause | 第 7 章 |
| 統計 | statistics | 0.16 | BSD-2-Clause | 第 9 章 |
| 直列化 | `Data.Binary`（学習済みモデル）、aeson（API の JSON） | binary 0.8・aeson 2.3 | BSD-3-Clause | 第 8 章 |
| API | Scotty | 0.30 | BSD-3-Clause | 第 15 章 |

- **hmatrix を使う。** 素の環境では BLAS/LAPACK が無くて止まるので、`openblas` を Nix の環境定義に足し、`LIBRARY_PATH` と `PKG_CONFIG_PATH` を `shellHook` で通す。Elixir 版で EXLA（巨大な XLA のバイナリ）を避けたのとは判断が違う。**BLAS/LAPACK は数値計算の標準的な土台で、Nix でも軽く入る**ためである。これにより第 7・12・13 章でライブラリと突き合わせられ、[Elixir 版](../article/getting-start-ml/elixir/index.md) の決定木のように「突き合わせる相手がいない」状態を避けられる
- **機械学習のアルゴリズムは自作が最終実装になる。** Haskell には scikit-learn にあたるものが無い（`hlearn` は保守が止まっている）。決定木・ランダムフォレスト・ロジスティック回帰・K-means・主成分分析はすべて自作する。**突き合わせられるのは線形代数の層（正規方程式・固有値分解）だけ**で、これは Elixir 版（Scholar に決定木が無い）よりさらに狭い
- **乱数は `java.util.Random` と同じ線形合同法を自作する。** `System.Random` はほかの言語版と並びが合わない。**Haskell の `Int` は溢れると折り返す**ので、PHP 版で必要だった乗算の分割が要らず、仕様をそのまま書ける
- **失敗は型で表す。** `Either String a` で失敗を、`Maybe a` で欠損を表す。例外を投げる Ruby 版・Elixir 版・PHP 版とは正反対で、[Rust 版](../article/getting-start-ml/rust/index.md)・[F# 版](../article/getting-start-ml/fsharp/index.md) と同じ流儀になる
- **`-Wall` を警告ではなくエラーにする。** 網羅していないパターンマッチを、テストではなくコンパイルで捕まえる
- **Stack ではなく cabal を使う。** Nix の環境が GHC を与えているので、Stack の「GHC を自分で管理する」働きが要らない
- **各章は「自作してから、突き合わせられる部分だけ hmatrix と突き合わせる」構成にする。** 突き合わせられない章では、そのことを明記して自作の妥当性をテストで示す

### 章ごとのライブラリへの置き換え方針

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | **無し（決定木のライブラリが無い）** | 自作のジニ不純度の決定木が最終実装 |
| 4〜6 | 無し | 道具立ての章。cabal・fourmolu・hlint・HPC・Nix の環境定義を扱う |
| 7 | hmatrix（正規方程式を解く） | 自作のガウス・ジョルダン法と hmatrix の `<\>` を突き合わせる |
| 8 | 無し | 前処理も決定木も自作 |
| 9 | statistics（平均・標準偏差） | 自作の標準化と突き合わせる |
| 10 | **無し** | ロジスティック回帰もランダムフォレストも自作 |
| 11 | **無し** | 評価指標・交差検証も自作 |
| 12 | hmatrix（リッジ回帰の正規方程式） | ラッソは座標降下法を自作 |
| 13 | hmatrix（`eigSH` による固有値分解） | 自作の Jacobi 法と突き合わせる。**この章がいちばんライブラリの恩恵を受ける** |
| 14 | 無し | K-means は自作 |
| 15 | Scotty・aeson | 予測 API |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| 線形代数も自作する（hmatrix を使わない） | 「ライブラリが少ない言語」という位置づけには合うが、**突き合わせる相手が 1 つも無くなる**。第 13 章の 15×15 の固有値分解を自作だけで済ませると、正しさを確かめる方法がテストの中の小さな行列に限られる。hmatrix はビルドできることを確認済みで、Nix でも軽く入る |
| Stack | Nix が GHC を与えているので、Stack の GHC 管理が二重になる |
| hlearn（機械学習） | 保守が止まっており、現在の GHC でビルドできない |
| `System.Random`（乱数） | ほかの言語版と並びが合わない。線形合同法を自作すれば JVM の言語版・Elixir 版・PHP 版と一致する |
| 例外（`throwIO`）で失敗を表す | Haskell で純粋関数が失敗を返すなら `Either` が自然。型でエラーを表す軸が Rust 版・F# 版との対比になる |
| ormolu（整形） | fourmolu は ormolu の派生で、設定を変えられる。どちらでもよいが、設定を書ける側にした |

## 影響

- 良い影響: 型でエラーを表す流儀が、Rust 版・F# 版との三者比較になる。例外で表す Ruby 版・Elixir 版・PHP 版との対比も取れる
- 良い影響: 副作用が `IO` として型に現れるので、「データの読み込みだけが `IO` で、前処理から学習までは純粋関数」という構造を型で示せる
- 良い影響: `-Wall` をエラーにすることで、網羅していないパターンマッチをコンパイルで捕まえられる
- 悪い影響: **hmatrix のために C ライブラリ（BLAS/LAPACK）への依存が環境に増える。** 素の環境では configure で止まるので、記事の環境構築の節に明記する必要がある
- 悪い影響: **機械学習のアルゴリズムに突き合わせる相手がほとんど無い。** 線形代数の層だけが hmatrix と比べられ、第 3・8・10・11・14 章は自作だけで完結する。これは Elixir 版よりさらに狭い範囲である
- 悪い影響: 整形と静的解析の道具が素の Nix 環境に無く、環境定義に足す必要があった（PHP 版の pcov と同じ形で、第 3 波で 2 回続いた）
