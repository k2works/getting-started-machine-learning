---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "*.cabal の common によるオプション共有、build-depends の版の範囲、テストスイートの依存をライブラリと別に書くこと、ロックファイルを置かない判断、GHC の -Wall -Werror が網羅していないパターンマッチと let Right x = ... を止めること、fourmolu（終了コード 100）と hlint（1）の守備範囲、HPC と自作のしきい値、そして型を使う／使わないの三者比較を扱う。"
tags: [article,getting-start-ml,haskell]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前の章では、何をコミットし何をコミットしないかを決めました。この章では、**コミットした設定ファイルが何を決めているのか** を見ていきます。

Haskell 版で使う道具は 4 つです。

| 道具 | 役割 | 違反したときの終了コード |
|------|------|----------------------|
| fourmolu | 整形 | **100** |
| GHC の `-Wall -Werror`・hlint | 静的解析 | 1 |
| Hspec（`cabal test`） | テスト | 1 |
| HPC ＋ 自作のスクリプト | カバレッジ | **3**（自分で決めた） |

この章の見どころは 3 つです。

- **`*.cabal` は「書く場所が分かれている」**。`-Wall -Werror` はライブラリとテストの両方に効かせたいので `common` にまとめ、依存は **ライブラリとテストで別々に書く**必要があります。モジュールを足したら名前を手で書き足します
- **静的解析が 2 つに分かれている**。GHC の `-Wall -Werror` が「型と網羅性」を、hlint が「書き方」を見ます。どちらか片方では足りません
- **ロックファイルを置いていない**。`composer.lock`・`mix.lock` に当たるものが無い言語版はこれが初めてです。その代わりに何で版を決めているのかを整理します

## 5.2 `*.cabal` による依存と版の管理

### `getting-started-ml.cabal` の全体

```cabal
cabal-version:      2.4
name:               getting-started-ml
version:            0.1.0.0
synopsis:           「機械学習から始めるプログラミング入門」Haskell 版のサンプル実装
license:            MIT
build-type:         Simple

common warnings
    -- 網羅していないパターンマッチを、テストではなくコンパイルで捕まえる。
    ghc-options: -Wall -Werror

library
    import:           warnings
    exposed-modules:  GettingStartedMl.Chapter01
                    , GettingStartedMl.Chapter02
                    , GettingStartedMl.Csv
                    , GettingStartedMl.Dataset
                    , GettingStartedMl.Random
    hs-source-dirs:   src
    build-depends:    base >=4.17 && <5
                    , bytestring
                    , cassava
                    , containers
                    , directory
                    , filepath
                    , text
                    , unordered-containers
                    , vector
    default-language: Haskell2010

test-suite spec
    import:           warnings
    type:             exitcode-stdio-1.0
    main-is:          Spec.hs
    other-modules:    GettingStartedMl.Chapter01Spec
                    , GettingStartedMl.Chapter02Spec
                    , GettingStartedMl.CsvSpec
                    , GettingStartedMl.DatasetSpec
                    , GettingStartedMl.RandomSpec
    hs-source-dirs:   test
    build-depends:    base
                    , getting-started-ml
                    , hspec
                    , bytestring
                    , cassava
                    , containers
                    , text
                    , vector
                    , directory
    build-tool-depends: hspec-discover:hspec-discover
    default-language: Haskell2010
```

1 つのファイルに `library` と `test-suite` という 2 つの**ターゲット**が並んでいます。`composer.json` や `mix.exs` が「プロジェクトの設定」を 1 枚で書いたのに対し、cabal は **ビルドの単位ごとに設定を書きます**。この構造が、以下のほとんどの話の原因になります。

### `common` でオプションを共有する

```cabal
common warnings
    ghc-options: -Wall -Werror
```

`common` は「名前を付けた設定の塊」で、ターゲットの中で `import: warnings` と書くと取り込まれます。ここでは **ライブラリとテストの両方に `-Wall -Werror` を効かせる**ために使っています。

わざわざ共有するのは、**テストコードも同じ厳しさで検査したいから**です。テストだけ警告を緩めると、テストの中に網羅していないパターンマッチが残ります。第 2 章で触れたとおり、テストの中の `let Right x = ...` を止めているのはこの設定です（5.5 節で実際に止まるところを見ます）。

`common` は cabal 2.2 からの機能です。`cabal-version: 2.4` と書いてあるのはこのためでもあります。**cabal ファイルの 1 行目は「このファイルでどの書き方が使えるか」を決めます。**

### `build-depends` の版の範囲

```cabal
build-depends:    base >=4.17 && <5
                , bytestring
                , cassava
```

`base` だけに範囲が書かれ、ほかには書かれていません。これは cabal の慣習で、`base` の版は GHC の版に対応するので「この GHC より古くては動かない」という宣言になります。

範囲を外すとどうなるかを確かめます。`<5` を `<4.18` に変えてみます（この環境の `base` は 4.20.2.0 です）。

```bash
cabal build lib:getting-started-ml
```

```text
Resolving dependencies...
Error: [Cabal-7107]
Could not resolve dependencies:
[__0] trying: getting-started-ml-0.1.0.0 (user goal)
[__1] next goal: base (dependency of getting-started-ml)
[__1] rejecting: base-4.20.2.0/installed-b5c3 (conflict: getting-started-ml => base>=4.17 && <4.18)
[__1] skipping: base; 4.22.0.0, 4.21.2.0, ... (has the same characteristics that caused the previous version to fail: excluded by constraint '>=4.17 && <4.18' from 'getting-started-ml')
[__1] rejecting: base; 4.17.2.1, 4.17.2.0, ... (constraint from non-reinstallable package requires installed instance)
[__1] fail (backjumping, conflict set: base, getting-started-ml)
```

**cabal の依存解決は、なぜ失敗したかを 1 行ずつ説明します。** `rejecting`（この版は条件に合わない）、`skipping`（同じ理由でどうせ落ちる版をまとめて飛ばす）、`backjumping`（この分岐を諦めて戻る）。読み方さえ分かれば、どの制約が邪魔をしているかがそのまま書いてあります。`composer` や `mix` の「依存を解決できません」という一行より、直すべき場所に早く辿り着けます。

最後の `rejecting` の理由にも注目してください。`constraint from non-reinstallable package requires installed instance`。**`base` は入れ直せないパッケージなので、GHC に付いてきたもの以外は選べません。** 範囲の書き方で `base` を取り替えることはできない、というのがここに出ています。

### テストスイートの依存はライブラリと別に書く

Haskell 版でいちばん最初につまずくところです。ライブラリの `build-depends` に `bytestring` を書いても、**テストからは見えません**。試しにテスト側の 1 行を消してみます。

```bash
cabal build test:spec
```

```text
test/GettingStartedMl/Chapter01Spec.hs:6:1: error: [GHC-87110]
    Could not load module ‘Data.ByteString.Lazy’.
    It is a member of the hidden package ‘bytestring-0.12.2.0’.
    Perhaps you need to add ‘bytestring’ to the build-depends in your .cabal file.
    Use -v to see a list of the files searched for.
  |
6 | import qualified Data.ByteString.Lazy as BL
  | ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
```

**`hidden package` という言い回しが手がかりです。** 「そのパッケージは環境にあるが、このターゲットからは見えないことになっている」という意味です。パッケージが入っていないときのエラーとは別物で、メッセージも「入れてください」ではなく「`build-depends` に足してください」と言っています。

なぜこうなっているのか。**依存の宣言はターゲットごとの約束だから**です。ライブラリを使う側の人にとって、そのライブラリのテストが何に依存しているかは関係ありません。`hspec` はテストにだけ要るので、ライブラリの `build-depends` に書くと、このライブラリを使う全員が Hspec を取ってくることになります。**「開発時だけの依存」を別に書く仕組み**が、PHP の `require-dev`・Elixir の `only: :test` に当たるものとして、ターゲットの分割で実現されています。

| 言語版 | 開発時だけの依存の書き方 |
|-------|----------------------|
| PHP 版 | `composer.json` の `require-dev` |
| Elixir 版 | `mix.exs` の `only: [:dev, :test]` |
| **Haskell 版** | **`test-suite` の `build-depends`（ターゲットが分かれている）** |

Haskell のやり方は、**本番の依存とテストの依存が同じ名前で並ぶ**という副作用を持ちます。`bytestring`・`cassava`・`containers`・`text`・`vector`・`directory` は両方に書かれています。**重複して見えますが、意味が違います。** 上はライブラリが使うもの、下はテストのコードが直接使うものです。テストが `Data.ByteString.Lazy` を `import` しているなら、テスト側に書かなければいけません。

### `build-tool-depends` と hspec-discover

```cabal
build-tool-depends: hspec-discover:hspec-discover
```

これは「ライブラリ」ではなく「**ビルドのときに実行する道具**」の宣言です。書式は `パッケージ名:実行ファイル名` です。

`test/Spec.hs` の中身は 1 行だけです。

```haskell
{-# OPTIONS_GHC -F -pgmF hspec-discover #-}
```

`-F` は「前処理器を通せ」、`-pgmF` は「使う前処理器はこれだ」という GHC のオプションです。コンパイルの前に `hspec-discover` が走り、`test/` 以下の `*Spec.hs` を集めて `spec` を並べたコードを生成します。**テストファイルを足しても `Spec.hs` を触らなくて済みます。**

ただし **`other-modules` には書き足す必要があります**。`hspec-discover` はファイルを見つけてくれますが、cabal に「このターゲットにはこのモジュールが含まれる」と伝えるのは別の仕事だからです。書き忘れると、テストが 1 本も増えないまま静かに通ります。

**モジュールを足したら `*.cabal` を触る**——これが Haskell 版で `build` 型のコミットが多い理由です（第 4 章）。Ruby 版や Elixir 版のように、ファイルを置けば見つけてもらえるわけではありません。**面倒なぶん、「どのファイルがこのターゲットに属するか」がファイル 1 枚に書いてあります。**

### ロックファイルを置かない

ここまで読んで気づくとおり、**この版には `composer.lock`・`mix.lock`・`Gemfile.lock` に当たるファイルがありません。** シリーズで初めてです。

cabal に仕組みが無いわけではありません。`cabal freeze` を実行すると `cabal.project.freeze` が作られます。

```bash
cabal freeze
wc -l < cabal.project.freeze
head -12 cabal.project.freeze
```

```text
71
active-repositories: hackage.haskell.org:merge
constraints: any.HUnit ==1.6.2.0,
             any.Only ==0.1,
             any.QuickCheck ==2.18.0.0,
             QuickCheck -old-random +templatehaskell,
             any.ansi-terminal ==1.1.5,
             any.ansi-terminal-types ==1.1.3,
             any.array ==0.5.8.0,
             any.attoparsec ==0.14.4,
             any.base ==4.20.2.0,
             any.binary ==0.8.9.3,
             any.bytestring ==0.12.2.0,
```

**71 行、68 個の制約です。** `*.cabal` に直接書いたのはライブラリで 9 個、テストで 8 個（重複を除けば 10 個）ですから、推移的に入ってくるものを含めると 68 個になります。

では、なぜ置かないのか。**この版は、版を決めるものが 2 つに分かれているからです。**

| 何が | 何の版を決めるか | どう固定しているか |
|------|---------------|-----------------|
| Nix の環境定義 | GHC・cabal・fourmolu・hlint・hspec-discover・openblas | **Nix の flake が固定する** |
| GHC | `base`・`bytestring`・`containers`・`text`・`directory`・`filepath` など同梱のパッケージ | **GHC の版で決まる** |
| Hackage | cassava・hspec・vector・unordered-containers | **固定していない** |

GHC に同梱されているパッケージは、GHC の版が決まれば決まります。

```bash
ghc-pkg list --simple-output
```

```text
Cabal-3.12.1.0 Cabal-syntax-3.12.1.0 array-0.5.8.0 base-4.20.2.0 binary-0.8.9.3
bytestring-0.12.2.0 containers-0.7 deepseq-1.5.0.0 directory-1.3.8.5 ...
filepath-1.5.4.0 ... mtl-2.3.1 ... text-2.1.3 time-1.12.2 transformers-0.6.1.1
```

**`bytestring`・`containers`・`text`・`directory`・`filepath`・`binary` は最初から入っています。** この版がライブラリの `build-depends` に書いた 9 個のうち、Hackage から取ってくるのは **cassava・unordered-containers・vector の 3 つだけ**です（`vector` は GHC に同梱されていません）。テスト側に `hspec` が加わって 4 つです。

つまり、**固定されていないのは 4 個の直接の依存と、そこからぶら下がる推移的な依存だけ**です。そのうえで、置かない理由は 2 つあります。

1. **`cabal.project.freeze` は `index-state` を固定しません。** Hackage のパッケージ一覧そのものの時刻を止めるには `index-state: 2026-09-24T00:00:00Z` を `cabal.project` に書く必要があり、freeze だけでは「今日のインデックスの中でこの版」としか言えません。**ロックファイルを置くなら 2 つ揃えるべきで、片方だけだと安心が半端になります**
2. **記事の読者が動かす敷居を上げます。** freeze があると、読者の手元の GHC が少し違うだけで解決に失敗します。この版は Nix の環境を前提にしていますが、Nix を使わずに読む人も想定しています

**これは「そのほうが良い」という主張ではなく、**「Nix が上流を固定しているので、その下にもう 1 枚ロックを置く価値がこのプロジェクトでは小さい」という判断です。業務のプロジェクトなら、`cabal.project.freeze` と `index-state` の両方を置くのが普通です。**再現性をどこで担保しているかを説明できることのほうが、ファイルがあることより大事です。**

| 言語版 | ロックファイル | 何が版を決めるか |
|-------|-------------|---------------|
| PHP 版 | `composer.lock`（5761 行・80 パッケージ） | ロックファイル |
| Elixir 版 | `mix.lock` | ロックファイル |
| Ruby 版 | `Gemfile.lock` | ロックファイル |
| **Haskell 版** | **無し** | **Nix（道具と GHC）＋ GHC 同梱のパッケージ。残り 4 個は固定していない** |

### 言語拡張はファイルの先頭に書く

Haskell には「言語拡張」があり、置き場所が 2 つあります。`*.cabal` の `default-extensions` と、ファイルの先頭の `{-# LANGUAGE ... #-}` です。この版は後者を使っています。

```haskell
{-# LANGUAGE OverloadedStrings #-}

{- | CSV を列名つきで読む。
```

`OverloadedStrings` は、文字列のリテラルを `Text` や `ByteString` として扱えるようにする拡張です。cassava を使うモジュールとそのテストに書いてあります。

ファイルの先頭に書くと、**そのファイルを開いただけで、どんな拡張が効いているかが分かります**。`default-extensions` に書くと、見慣れない書き方に出会ったときに `*.cabal` を開かないと理由が分かりません。ファイルの枚数のぶんだけ同じ行を書くことになりますが、**「このファイルを読むのに必要な情報がこのファイルにある」**ほうを取りました。`-Wall -Werror` を `common` で共有したのと逆の判断に見えますが、基準は同じです。**全ファイルに一律で効かせたいものは共有し、ファイルごとに違うものは各ファイルに書きます。**

## 5.3 GHC と道具の版

Nix の環境に入ると版が表示されます。

```bash
nix develop .#haskell
```

```text
Welcome to the Haskell development environment!
The Glorious Glasgow Haskell Compilation System, version 9.10.3
cabal-install version 3.16.0.0
compiled using version 3.16.0.0 of the Cabal library (in-tree)
3.7.1 x86_64 hpack-0.38.1
```

整形と静的解析の道具の版も確かめます。

```bash
fourmolu --version
hlint --version
```

```text
fourmolu 0.19.0.1
using ghc-lib-parser 9.12.2.20250421
HLint v3.10, (C) Neil Mitchell 2006-2025
```

`fourmolu 0.19.0.1` の下の行に注目してください。**fourmolu は GHC 本体ではなく `ghc-lib-parser` という切り出された構文解析器を使っています。** しかも版が 9.12 で、環境の GHC（9.10.3）より新しい。整形の道具が読むソースの文法と、コンパイラが読む文法が、**別の実装で別の版**だということです。ふつうは問題になりませんが、**新しい構文を使ったときに「コンパイルは通るが整形の道具が解析できない」（あるいはその逆）が起こりうる**ことは知っておく価値があります。

[第 1 章](01-machine-learning-and-first-test.md)で書いたとおり、fourmolu と hlint は素の Nix 環境に入っておらず、手元の `/usr/local/bin` のものが見えていました。環境定義に足した今も、手元には別の版が残っています。

```bash
which fourmolu
/usr/local/bin/fourmolu --version | head -1
```

```text
/usr/local/bin/fourmolu
fourmolu 0.20.0.0
```

**Nix の中は 0.19.0.1、外は 0.20.0.0 です。** 整形の道具の版が違えば、整形の結果も変わりえます。どちらが新しいかは問題ではありません。**同じコードに対して全員が同じ判定を得ること**が検査の前提なので、版は環境の側で固定します。

## 5.4 コードスタイル — fourmolu

### 設定ファイル

`apps/haskell/fourmolu.yaml` に書きます。

```yaml
indentation: 2
column-limit: none
function-arrows: leading
comma-style: leading
import-export-style: diff-friendly
indent-wheres: false
record-brace-space: true
newlines-between-decls: 1
haddock-style: multi-line
let-style: auto
in-style: right-align
single-constraint-parens: auto
```

注目したいのは `leading` と `diff-friendly` が並んでいることです。型を複数行に分けて書いたファイルを整形にかけると、違いが見えます。

```haskell
prepare ::
  String ->
  Double ->
  Int ->
  Either String (Map Text Text)
```

```bash
fourmolu --mode stdout src/Sig2.hs
```

```haskell
prepare
  :: String
  -> Double
  -> Int
  -> Either String (Map Text Text)
```

`function-arrows: leading` は、型の矢印を **行の先頭**に置く書き方です。`comma-style: leading` はカンマを行の先頭に置きます（`*.cabal` の `build-depends` と同じ形です）。

なぜこうするのか。**差分を読みやすくするため**です。引数を 1 つ足すとき、末尾に記号を置く流儀だと「前の行の末尾に `->` を足す」変更と「新しい行」の 2 行が差分に出ます。先頭に置く流儀なら、**足した 1 行だけ**が差分に出ます。`import-export-style: diff-friendly` も同じ考えで、`import` の一覧を 1 要素 1 行に並べます。

**整形の設定は「見た目の好み」に見えて、実は「差分の読みやすさ」を決めています。** 機械学習のコードは、引数（ハイパーパラメータ）を足したり減らしたりを繰り返すので、この差は積み上がります。

`column-limit: none` は行の長さを制限しない指定です。日本語のコメントとハドック（`-- |` で始まる文書化コメント）が多いので、幅で折り返されると読みにくくなります。**幅の制限は、全角文字を数える道具と数えない道具で結果が食い違いやすい**ところでもあります。この指定があるので、fourmolu は型を**勝手には**複数行に割りません。1 行に書けば 1 行のまま、割って書けば先頭に矢印を寄せて整えます。**どこで改行するかは書く人が決め、どう揃えるかを道具が決める**という分担です。

設定ファイルの探し方に 1 つ癖があります。**fourmolu は、作業ディレクトリではなく「入力ファイルの置かれたディレクトリ」から上に向かって `fourmolu.yaml` を探します。** プロジェクトの外に置いたファイルを整形すると、設定が読まれず既定（インデント 4・矢印は行末）になります。読み込んだ設定は 1 行目に表示されるので、意図と違う結果が出たらまずここを見ます。

```text
Loaded config from: .../apps/haskell/fourmolu.yaml
```

### 実行と終了コード

```bash
# 検査する（CI 向け）
fourmolu --mode check src test

# 整形する
fourmolu --mode inplace src test
```

崩したファイルを置いて検査します。

```haskell
module GettingStartedMl.Violation (f) where
f    :: Int -> Int
f x =
      x+1
```

```bash
fourmolu --mode check src test; echo "EXIT=$?"
```

```text
src/GettingStartedMl/Violation.hs
@@ -1,4 +1,5 @@
  module GettingStartedMl.Violation (f) where
- f    :: Int -> Int
+
+ f :: Int -> Int
  f x =
-       x+1
+   x + 1
EXIT=100
```

**違反があるときの終了コードは 100 です。** 1 でも 8 でもありません。

| 言語版 | 整形の道具 | 違反の終了コード |
|-------|----------|--------------|
| **Haskell** | **fourmolu** | **100** |
| PHP | PHP-CS-Fixer | 8 |
| Elixir | `mix format` | 1 |
| Rust | `cargo fmt` | 1 |

もう 1 つ、細かいですが引っかかるところがあります。`--mode` に指定できるのは `check`・`inplace`・`stdout` で、**`diff` はありません**。

```bash
fourmolu --mode diff src/GettingStartedMl/Violation.hs
```

```text
option --mode: unknown mode: diff
```

差分は `--mode check` が勝手に表示してくれます。`--mode check` は「検査して、違っていれば差分を見せて 100 で終わる」という 1 つのモードです。PHP-CS-Fixer が `check` と `--diff` に分かれていたのとは設計が違います。**「検査」と「差分を見せる」を分けない**ほうが、使う側としては間違えようがありません。

## 5.5 静的解析 — GHC の `-Wall -Werror` と hlint

### 2 つに分かれている

Haskell の静的解析は 2 つの道具に分かれています。

| 道具 | 見るもの | 例 |
|------|---------|----|
| GHC の `-Wall -Werror` | **型と網羅性** | 場合分けの漏れ、使っていない束縛、輸出していない定義 |
| hlint | **書き方** | `map id` は `id` でよい、`f x = g x` は `f = g` でよい |

**重なりません。** GHC は「正しいか」を、hlint は「短く書けるか」を見ます。片方だけでは足りないので、両方走らせます。[PHP 版](../php/05-package-management-and-static-analysis.md)で PHP-CS-Fixer と PHPStan が分かれていたのと似ていますが、**Haskell では型の検査がコンパイラ自身の仕事**なので、別の道具を足す必要がありません。ここが PHP 版・Python 版との決定的な違いです。

### 網羅していないパターンマッチはコンパイルで止まる

`-Wall -Werror` が何を止めるのかを、実際に壊して確かめます。まず、場合分けを 1 つ書き忘れたコードです。

```haskell
module GettingStartedMl.Violation (f) where

data Faction = Kinoko | Takenoko

f :: Faction -> String
f Kinoko = "kinoko"
```

```bash
cabal build lib:getting-started-ml; echo "EXIT=$?"
```

```text
src/GettingStartedMl/Violation.hs:3:25: error: [GHC-40910] [-Wunused-top-binds, Werror=unused-top-binds]
    Defined but not used: data constructor ‘Takenoko’
  |
3 | data Faction = Kinoko | Takenoko
  |                         ^^^^^^^^

src/GettingStartedMl/Violation.hs:6:1: error: [GHC-62161] [-Wincomplete-patterns, Werror=incomplete-patterns]
    Pattern match(es) are non-exhaustive
    In an equation for ‘f’:
        Patterns of type ‘Faction’ not matched: Takenoko
  |
6 | f Kinoko = "kinoko"
  | ^^^^^^^^^^^^^^^^^^^

Error: [Cabal-7125]
Failed to build getting-started-ml-0.1.0.0.
EXIT=1
```

**`Patterns of type 'Faction' not matched: Takenoko`。** どの値を書き忘れたかまで名指しされます。`-Werror` があるので `error:` として扱われ、ビルドが失敗します。

第 1 章で「取りうる値を型で数え上げる」と書きましたが、その効き目がここに出ています。**派閥を文字列で持っていたら、この検査は成り立ちません。** 型が値を数え上げているからこそ、コンパイラが「まだ 1 つ残っている」と言えます。機械学習のコードでは、カテゴリ値（派閥・品種・生存／死亡）を扱うたびにこの恩恵を受けます。

ついでに `Defined but not used: data constructor 'Takenoko'` も出ています。**定義したのに使っていない**という指摘です。これも `-Wall` の一部です。

### 使っていない束縛も止まる

```haskell
f :: Int -> Int
f x =
  let unused = x * 2
   in x + 1
```

```text
src/GettingStartedMl/Violation.hs:5:7: error: [GHC-40910] [-Wunused-local-binds, Werror=unused-local-binds]
    Defined but not used: ‘unused’
  |
5 |   let unused = x * 2
  |       ^^^^^^
```

**書きかけの計算が残ったままコミットされるのを防ぎます。** リファクタリングの途中で式を置き換えたときに、古い束縛を消し忘れる——よくある間違いです。

### `let Right x = ...` が止まる

3 つめがいちばん Haskell らしい指摘です。第 2 章で触れたものを、実際に見ます。

```haskell
f :: Either String Int -> Int
f e =
  let Right x = e
   in x
```

```text
src/GettingStartedMl/Violation.hs:5:7: error: [GHC-62161] [-Wincomplete-uni-patterns, Werror=incomplete-uni-patterns]
    Pattern match(es) are non-exhaustive
    In a pattern binding:
        Patterns of type ‘Either String Int’ not matched: Left _
  |
5 |   let Right x = e
   |       ^^^^^^^^^^^
```

`let Right x = e` は「`e` は `Right` のはずだから、中身を `x` と呼ぶ」という書き方です。**便利ですが、`Left` だったときに何が起こるかを書いていません。** 実行時に `Irrefutable pattern failed` で落ちます。`-Wincomplete-uni-patterns` はこれを止めます。

これが効いてくるのは **テストの中**です。

```haskell
-- これは書けない
it "空欄は欠損値として数える" $ do
  let Right table = loadTable (utf8Csv "a,b\n1,\n,2\n3,4\n")
  countMissing table `shouldBe` Right [("a", 1), ("b", 1)]
```

「テストだから落ちてもいい」と思うかもしれませんが、`common warnings` をテストスイートにも `import` しているので、**テストコードでも止まります**。代わりに、失敗したらその場で落ちることを明示する関数を書きます。

```haskell
-- | Right のはずの値を取り出す。Left なら、その場で落とす。
expectRight :: Either String a -> a
expectRight (Right value) = value
expectRight (Left err) = error ("失敗しました: " <> err)
```

```haskell
it "空欄は欠損値として数える" $ do
  let table = expectRight $ loadTable (utf8Csv "a,b\n1,\n,2\n3,4\n")
  countMissing table `shouldBe` Right [("a", 1), ("b", 1)]
```

書く量はほとんど変わりませんが、得られるものがあります。**`let Right x = ...` で落ちたテストは「パターンマッチに失敗した」としか言いません**が、`expectRight` なら `Left` の中身（つまりエラーメッセージ）が `失敗しました: ...` として表示されます。**失敗したときに何が分かるかを、失敗する前に決めている**わけです。

網羅していないパターンを 1 箇所に閉じ込めた、とも言えます。`expectRight` 自身は `Right` と `Left` の両方を書いているので警告は出ません。**同じ「落ちる」でも、落ちることを意図して書いた 3 行と、書き忘れた 1 行は別物です。**

`-Wall -Werror` の窮屈さは、ここに集約されます。**「うまくいく場合だけ書く」ことを許さない。** うまくいかない場合をどう扱うかを、必ず書かせます。

### hlint は書き方を見る

```bash
hlint src test
```

```text
No hints
```

指摘があるとこうなります。

```haskell
f :: [Int] -> [Int]
f xs = map id xs
```

```text
src/GettingStartedMl/Violation.hs:4:1-16: Warning: Eta reduce
Found:
  f xs = map id xs
Perhaps:
  f = map id

src/GettingStartedMl/Violation.hs:4:8-13: Warning: Redundant map
Found:
  map id
Perhaps:
  id

2 hints
EXIT=1
```

**「今こう書いてある」「こう書ける」を並べて見せます。** `Eta reduce` は「両辺の末尾に同じ引数があるなら消せる」、`Redundant map` は「`map id` は何もしないのと同じ」。どちらも **意味を変えずに短くなる** 書き換えです。

hlint が教えてくれるのは、**その言語で慣用的とされる書き方**です。Haskell を書き始めたときにいちばん助けになる道具でもあります。「もっと短く書けるはずだが書き方を知らない」という状態を、機械が埋めてくれます。

### `.hlint.yaml` と、名前が違うと黙って無視される落とし穴

`apps/haskell/.hlint.yaml` で、既定では無効になっている指摘を有効にできます。

```yaml
# 既定の規則に加えて、この版で守りたいものを足す。
- warn: {name: Use explicit module export list}
```

有効にしたいのは「モジュールに輸出一覧を書け」という指摘です。`module M where` と書くと中身が全部外から見えるので、`module M (f, g) where` と書いて公開するものを選べ、という趣旨です。この版のモジュールがすべて輸出一覧を持っているのは、この方針によります。

ところが、**この書き方には落とし穴があります。** 輸出一覧の無いモジュールを置いて確かめます。

```haskell
module M where

g :: [Int] -> [Int]
g = reverse
```

```bash
hlint M.hs; echo "EXIT=$?"
```

```text
No hints
EXIT=0
```

**何も出ません。** 名前を `Use module export list` にすると出ます。

```text
M.hs:1:1: Warning: Use module export list
Found:
  module M where
Perhaps:
  module M (
          module M
      ) where
Note: an explicit list is usually better

1 hint
EXIT=1
```

**hlint は、知らない名前の指摘を有効にしようとしても、黙って無視します。** エラーも警告も出ません。「設定を書いたから効いているはずだ」と思い込むと、何年でも気づきません。`Use explicit module export list` という名前は hlint の既定の設定テンプレートにコメントとして載っているものですが、実際に発火する指摘の名前は `Use module export list` です。**名前は、道具が出力する文字列をそのまま写すのが確実です。**

これは第 4 章で `.gitignore` を `git check-ignore -v` で確かめたのと同じ話です。**設定は、書いただけでは効いている証拠になりません。** 有効にしたつもりの規則は、違反するコードを一度置いて、本当に指摘されるかを確かめます。この記事を書く過程でこの 1 行を確かめ、効いていないことが分かりました。

### 型を使う／使わない——三者の比較

ここで、動的型付けの言語版との対比を整理しておきます。[Ruby 版](../ruby/index.md)と[Elixir 版](../elixir/index.md)は型検査の道具を**使わない**と決めました。[PHP 版](../php/05-package-management-and-static-analysis.md)は PHPStan のレベル 9 で**漸進的に型を足す**と決めました。Haskell 版は**最初から型で表す**ことになります。3 つを並べると、「型を使うか」が 2 択ではないことが見えます。

| | Ruby 版 | PHP 版 | **Haskell 版** |
|---|---------|--------|---------------|
| 型を書くか | 書かない | **書く**（引数・戻り値・プロパティ） | **書く**（すべての最上位の定義） |
| 型を検査するのは | （しない） | PHP のランタイム＋PHPStan | **GHC**（コンパイル時） |
| 型の道具は言語の一部か | 別（RBS＋Steep） | **言語の構文＋PHPDoc の二層** | **言語そのもの** |
| 「`list<T>`」に当たる表現 | — | PHPDoc（静的解析だけが見る） | **型（`[Text]`）。二層に分かれない** |
| 検査を通さずに実行できるか | できる | **できる**（PHPStan を飛ばせば動く） | **できない**（コンパイルが通らない） |
| 安全網は | **テストだけ** | 型とテストの両方 | 型とテストの両方 |
| 網羅していない場合分け | テストで見つける | 見つからないことがある | **コンパイルで止まる** |

いちばん効くのは最後の 2 行です。

**PHP 版は「検査を飛ばせば動く」**ので、PHPStan を通していないコードも実行できます。だからこそ「抑制しない」という規律が要りました。**Haskell 版は飛ばせません。** `-Wall -Werror` を入れた時点で、警告が 1 つでも残っているコードは実行ファイルになりません。規律を守る意志ではなく、**仕組みが守っています**。

そのかわり、**Ruby 版が持っていた「すぐ動かして試す」自由は失われます**。書きかけのコードは動きません。`let unused = x * 2` を置いたまま「とりあえず走らせる」ことができません。**これは値段です。** 「型を使えば良い」のではなく、**何と引き換えに何を得るか**という取り引きが 3 通りある、と読むのが正しい読み方です。

| | 得るもの | 失うもの |
|---|---------|---------|
| Ruby 版 | 書いたらすぐ動く。テストに集中できる | 場合分けの漏れは実行するまで分からない |
| PHP 版 | 段階的に厳しくできる。既存のコードに後から入れられる | 二層の型（言語と PHPDoc）を使い分ける必要がある。抑制の誘惑がある |
| **Haskell 版** | **漏れがコンパイルで止まる。抑制の誘惑がそもそも無い** | **書きかけのコードが動かない。厳しさを後から足すのが難しい** |

第 4 章で「厳しい設定は守るコードが 1 ファイルのうちに入れる」と書いたのは、右下の「後から足すのが難しい」に対する答えです。

### 指摘を抑制しない

GHC にも抑制の仕組みはあります。`{-# OPTIONS_GHC -Wno-incomplete-patterns #-}` をファイルの先頭に書けば、そのファイルだけ検査を緩められます。hlint にも `{-# ANN module ("HLint: ignore ..." :: String) #-}` があります。

本シリーズでは、抑制ではなく **直す** ことにしています。とくに `-Wno-incomplete-patterns` は危険です。**その 1 行で、このプロジェクトが `-Werror` を入れた理由のほとんどが消えます。** ファイル単位の抑制は「そのファイル全体の、すべての場合分け」に効くので、抑制したかった 1 箇所以外も一緒に見逃されます。

`-Wall -Werror` が窮屈だと感じたときの正しい対処は、**抑制ではなく型の設計を変えること**です。場合分けが多すぎるなら、型が値を数え上げすぎているか、1 つの関数が受け持ちすぎています。**警告は、直すべき場所を指しています。**

## 5.6 コードカバレッジ — HPC と自作のしきい値

### 標準の仕組みがある

カバレッジは、テストがコードのどこを通ったかの割合です。Haskell では **HPC（Haskell Program Coverage）が GHC に組み込まれています**。

```bash
cabal test --enable-coverage
```

```text
Writing: getting-started-ml-0.1.0.0-inplace/GettingStartedMl.Chapter01.hs.html
Writing: hpc_index.html
Writing: hpc_index_fun.html
Writing: hpc_index_alt.html
Writing: hpc_index_exp.html
Package coverage report written to
.../dist-newstyle/build/.../hpc/vanilla/html/hpc_index.html
```

**追加の依存が要りません。** PHP がカバレッジのために pcov という拡張を入れる必要があったのとは対照的です。Elixir の `mix test --cover`（Erlang の `:cover`）と同じく、言語の側が持っています。

HPC が数えるのは **式**です。行でも分岐でもなく、式の単位で「通ったか」を数えます。`hpc_index_alt.html`（分岐）、`hpc_index_fun.html`（最上位の定義）、`hpc_index_exp.html`（式）と、3 通りの見方の HTML が出ます。行を数える PHP・Elixir より **細かい粒度**です。`f x = if p x then g x else h x` の 1 行は、行のカバレッジなら「通った」で終わりますが、HPC は `g x` と `h x` を別に数えます。

### しきい値は自分で判定する

そして、**cabal にも「カバレッジが N% を下回ったら失敗させる」機能がありません。**

第 3 波の 3 つの言語版を並べると、状況が全部違います。

| 言語版 | 計測の仕組み | しきい値の機能 |
|-------|------------|-------------|
| Elixir 版 | 標準（`mix test --cover`） | **ある**（`summary` の `threshold`） |
| PHP 版 | **拡張が要る**（pcov） | **無い**（自作） |
| **Haskell 版** | **標準（HPC）** | **無い**（自作） |

**「計測できること」と「失敗させられること」は別です。** Haskell は前者を持っていて後者を持っていません。PHP はどちらも持っていませんでした。Elixir は両方持っていました。

`apps/haskell/tools/coverage-threshold.sh` で判定します。

```bash
#!/usr/bin/env bash
# HPC の結果を読み、式のカバレッジがしきい値を下回ったら失敗する。
set -euo pipefail

threshold="${1:-80}"

index=$(find dist-newstyle -path '*/hpc/vanilla/html/hpc_index.html' | head -1)

if [ -z "$index" ]; then
  echo "カバレッジの結果がありません。先に cabal test --enable-coverage を実行してください" >&2
  exit 1
fi

# 総計から「式のカバレッジ」を取る。HPC の index は総計の見出しの直後に、
# 分岐・トップレベル定義・式の順で 3 組の「率」と「実数/総数」を並べる。
# 見出しと数値が別の行にあるので、改行を落としてから取り出す。
summary=$(tr -d '\n' < "$index" | grep -o 'Program Coverage Total.*' | head -1)
rate=$(echo "$summary" | grep -oE '<td align="right">[0-9]+%</td><td>[0-9]+/[0-9]+</td>' | sed -n '3p')
```

**HTML を読んでいます。** ここが弱いところです。PHP 版が読んだ `clover.xml` は機械が読むための形式でしたが、**HPC が出すのは人が読むための HTML だけ**です。`.tix` という生の記録はありますが、これはバイナリに近い独自の形式で、`hpc report` コマンドの出力を解析するにしても形が保証されているわけではありません。

`sed -n '3p'` で 3 つめを取っているのは、総計の行に分岐・最上位の定義・式の 3 組が並ぶからです。**HPC の HTML の書式が変われば壊れます。** コメントに「なぜ 3 つめなのか」を書いてあるのはそのためです。壊れたときに直せるように、**読み取りの根拠をコードの隣に残します**。

```bash
./tools/coverage-threshold.sh 80
```

```text
式カバレッジ: 94% (626/663), しきい値: 80%
```

しきい値を割ると終了コード 3 で落ちます。

```bash
./tools/coverage-threshold.sh 99; echo "EXIT=$?"
```

```text
式カバレッジ: 83% (554/663), しきい値: 99%
カバレッジがしきい値を下回りました: 83% < 99%
EXIT=3
```

**3 という値に決まりはありません。プロジェクトの約束です。** 道具の作者が決めた 100・1・1 の隣に、自分で決めた 3 が並びます。

### 数字の読み方

しきい値を 80% にしてある理由は第 4 章で見たとおりです。**実データのテストを外すと 94% が 83% に落ちる**ので、データの無い CI でも通る値にしてあります。

もう 1 つ、**HPC が式を数えることの効き目**があります。`Either` で失敗を表すコードは、成功の道と失敗の道の両方に式があります。

```haskell
report contents = do
  table <- loadTable contents
  missing <- countMissing table
  ...
```

`loadTable` が `Left` を返したときの流れは `do` 記法に隠れていて、行としては見えません。しかし **HPC は式として数えます**。失敗の場合のテストを書いていなければ、カバレッジに穴として出ます。**「失敗を型で表す」設計と「式を数えるカバレッジ」は相性がよい**、ということです。行を数える道具では、この穴は見えません。

## 5.7 検査の 4 つを 1 行にする

```bash
fourmolu --mode check src test && hlint src test && cabal test --enable-coverage && ./tools/coverage-threshold.sh 80
```

`&&` でつないであるので、前が失敗したらそこで止まります。4 つの並びは CI（`.github/workflows/haskell-ci.yml`）とも、Gulp のタスク（`ops/scripts/apps.js`）とも同じです。

`cabal test` の中に、**5 つめの検査が隠れています**。`-Wall -Werror` によるコンパイルです。テストを走らせるにはライブラリとテストをコンパイルする必要があるので、警告が 1 つでもあればここで止まります。

```text
Error: [Cabal-7125]
Failed to build getting-started-ml-0.1.0.0.
```

**ビルドの失敗とテストの失敗が、同じ `cabal test` の中で同じ終了コード 1 になります。** どちらで落ちたかは出力を読まないと分かりません。CI では出力が画面に残るので困りませんが、手元で `&&` の 1 行を走らせているときは「テストが落ちた」と早合点しやすいところです。

第 6 章で、この 1 行を Composer や Mix に当たるまとめ役に入れられるか、そして CI でどう分けるかを見ます。

## 5.8 まとめ

この章では、依存の管理と検査の道具を整えました。

1. **`*.cabal` はターゲットごとに書く** — `library` と `test-suite` が別のターゲットで、依存も別々に書く。ライブラリに書いた `bytestring` はテストから見えず、`hidden package` というエラーになる。これが PHP の `require-dev`・Elixir の `only: :test` に当たる仕組み
2. **`common` で共有する** — `-Wall -Werror` をライブラリとテストの両方に効かせる。**テストコードも同じ厳しさで検査する**ためで、第 2 章の `let Right x = ...` を止めているのはこの設定
3. **モジュールを足したら `*.cabal` を触る** — `exposed-modules`・`other-modules` に名前を手で書く。`hspec-discover` がファイルを集めてくれても、cabal への登録は別の仕事。書き忘れるとテストが静かに増えない
4. **ロックファイルを置いていない** — シリーズで初めて。`cabal freeze` は 71 行・68 の制約を作れるが、`index-state` とセットでないと半端なので置かない判断にした。版は **Nix（道具と GHC）＋ GHC 同梱のパッケージ**が決めていて、固定していない直接の依存は **cassava・unordered-containers・vector・hspec の 4 個**だけ
5. **fourmolu の設定は差分の読みやすさを決める** — `function-arrows: leading`・`comma-style: leading`・`import-export-style: diff-friendly` は、引数を 1 つ足したときに **1 行だけ**を差分に出すための選択。終了コードは **100**。`--mode diff` は無く、`check` が差分も見せる
6. **静的解析が 2 つに分かれている** — GHC が型と網羅性、hlint が書き方。重ならないので両方走らせる。**型の検査がコンパイラ自身の仕事**なので、PHP 版の PHPStan に当たる別の道具が要らない
7. **`-Wall -Werror` は 3 つとも実際に止めた** — 場合分けの漏れ（`not matched: Takenoko` と名指し）、使っていない束縛、`let Right x = ...`（`-Wincomplete-uni-patterns`）。**「うまくいく場合だけ書く」ことを許さない**
8. **設定は書いただけでは効かない** — `.hlint.yaml` の `Use explicit module export list` は発火せず、`Use module export list` が正しい名前。**hlint は知らない名前を黙って無視する**。有効にしたつもりの規則は、違反するコードを置いて確かめる
9. **型を使うか使わないかは 2 択ではない** — Ruby 版（書かない）・PHP 版（書くが飛ばせる）・Haskell 版（書かないと動かない）。Haskell 版は抑制の誘惑がそもそも無い代わりに、**書きかけのコードが動かない**。厳しさを後から足すのも難しい
10. **カバレッジは計測できるが失敗させられない** — HPC は GHC 組み込みで追加の依存が要らないが、しきい値の機能が無いので自作する（終了コード **3**）。**HTML を読むしかない**のが弱点。式を数えるので、`Either` の失敗の道が通っていないことが穴として見える

次の章では、この 4 つの検査をタスクランナーと CI に載せます。`npx gulp apps:check:haskell` が **常に Nix の中で走る**理由と、**`cabal repl` の中で例外が起きても終了コードが 0 になる**という落とし穴を扱います。

Haskell 版のほかの章は [Haskell 版のトップ](index.md) から辿れます。
