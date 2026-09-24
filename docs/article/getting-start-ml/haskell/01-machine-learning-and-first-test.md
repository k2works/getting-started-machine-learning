---
type: Article
title: "第 1 章: 機械学習とはじめてのテスト"
description: "機械学習とルールベースの違いを確認し、きのこ派・たけのこ派の判定を Haskell の TDD で実装して正解率を測る。失敗を Either で表し、副作用を IO に閉じ込める書き方を Rust 版・F# 版と対比し、ByteString リテラルに日本語を書くと壊れる落とし穴を扱う。"
tags: [article,getting-start-ml,haskell]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 1 章: 機械学習とはじめてのテスト

## 1.1 はじめに

この章では、機械学習とは何かを確認したうえで、テスト駆動開発（TDD）で最初のプログラムを Haskell で作ります。題材は「きのこの山派か、たけのこの里派か」を身長・体重・年代から判定する問題です。

この章ではまだ機械学習のアルゴリズムを使いません。人間が考えたルールで判定し、その正解率を測るところまで進みます。

[Python 版の第 1 章](../python/01-machine-learning-and-first-test.md) と同じ題材・同じ TODO リストで進めます。Haskell 版では次の 3 つと対比します。1 つは [Rust 版](../rust/01-machine-learning-and-first-test.md) と [F# 版](../fsharp/01-machine-learning-and-first-test.md) で、**失敗を例外ではなく型で表す**点です。2 つめは [Elixir 版](../elixir/01-machine-learning-and-first-test.md)・[Clojure 版](../clojure/01-machine-learning-and-first-test.md) で、純粋関数でデータを変換していく流儀が近いところです。3 つめは [PHP 版](../php/01-machine-learning-and-first-test.md)・[Ruby 版](../ruby/01-machine-learning-and-first-test.md) で、**例外を投げる言語との正反対の設計**です（[ADR 014](../../../adr/014-haskell-ml-libraries.md)）。

Haskell 版はシリーズの最後の言語版です。**機械学習のライブラリが無い**ので、アルゴリズムはすべて自作になります。突き合わせられるのは線形代数の層（第 7・12・13 章の hmatrix）だけで、これは [Elixir 版](../elixir/index.md)（Scholar に決定木が無い）よりさらに狭い範囲です。

## 1.2 機械学習とは

### ルールを書くか、データに学ばせるか

従来のプログラミングでは、人間が判定のルールをコードとして書きます。

```haskell
predictByRule :: Features -> Faction
predictByRule features
  | featuresAgeGroup features == kinokoAgeGroup = Kinoko
  | otherwise = Takenoko
```

この書き方では、ルールの良し悪しは人間の観察力に依存します。特徴量が 3 つなら何とかなりますが、20 個・100 個になると人間には手に負えません。

機械学習は、この「ルール」をデータから自動で作ります。人間が与えるのは「入力（特徴量）」と「正解（ラベル）」の組で、ルールそのものはアルゴリズムが決めます。

| | 従来のプログラミング | 機械学習 |
|---|---|---|
| 人間が書くもの | ルール | データと、学習のさせ方 |
| 出力 | 判定結果 | ルール（モデル）と、それを使った判定結果 |
| 得意なこと | 仕様がはっきりしている問題 | 仕様を言葉にしにくい問題 |
| 説明のしやすさ | コードを読めば分かる | モデルによる（決定木は読める、ニューラルネットは難しい） |

### 機械学習のワークフロー

本シリーズを通して、次の流れを繰り返します。

1. **データを集める・読み込む** — この章で CSV を読み込みます
2. **前処理する** — 欠損値を埋め、訓練データとテストデータに分けます（第 2 章）
3. **モデルを学習させる** — 決定木・回帰などを自作します（第 3 章以降）
4. **評価する** — 正解率などの指標で測ります（この章で正解率から始めます）
5. **改善する** — 特徴量を作り直し、ハイパーパラメータを調整します（第 9 章以降）

### 分類と回帰

教師あり学習は、予測するものの型で 2 つに分かれます。

| 種類 | 予測するもの | 例 | 本シリーズで扱う章 |
|------|------------|-----|-----------------|
| 分類 | どのグループに属するか（離散値） | きのこ派／たけのこ派、アヤメの種類、生存／死亡 | 第 1・3・8・10・11 章 |
| 回帰 | 数値（連続値） | 映画の興行収入、住宅価格 | 第 7・9・12 章 |

この章は分類です。正解ラベルが「きのこ」「たけのこ」の 2 つなので、二値分類にあたります。

## 1.3 題材とデータ

### データの入手と配置

本シリーズの学習データは、書籍『スッキリわかる Python による機械学習入門』（インプレス, 2020）の配布データを使います。配布データは書籍購入者のみ利用できるため、リポジトリには含まれていません。[書籍サポートページ](https://sukkiri.jp/books/sukkiri_ml) から `sukkiri-ml-codes.zip` を入手し、リポジトリの `tmp/` に置いてから、リポジトリのルートで次を実行してください。

```bash
npx gulp data:setup
npx gulp data:check
```

`apps/data/sukkiri-ml/` に学習データが配置されます。このディレクトリは `.gitignore` の対象なので、誤ってコミットされることはありません。すべての言語版が同じデータを参照します。

### KvsT.csv

この章で使うのは `KvsT.csv` です。19 人分のデータが次の 4 列で記録されています。

| 列 | 意味 | 値 |
|----|------|-----|
| 身長 | 身長（cm） | 整数 |
| 体重 | 体重（kg） | 整数 |
| 年代 | 年代 | 10, 20, 30, 40 のいずれか |
| 派閥 | きのこの山派かたけのこの里派か | `きのこ` または `たけのこ` |

「身長」「体重」「年代」が特徴量、「派閥」が正解ラベルです。列名が日本語であることと、ファイルの先頭に BOM（バイトオーダーマーク）が付いていることに注意してください。**cassava も BOM を取り除きません。** 後で確かめます。

## 1.4 TODO リストの作成

仕様をそのままコードにするには大きすぎるので、まず TODO リストに分解します。

> TODO リスト
>
> 何をテストすべきだろうか——着手する前に、必要になりそうなテストをリストに書き出しておこう。
>
> — テスト駆動開発

**TODO リスト**:

- [ ] CSV を読み込む
  - [ ] BOM 付き CSV を列名で読み込む
  - [ ] 数値でない値・列の不足を弾く
- [ ] 特徴量と正解ラベルに分ける
- [ ] ルールで派閥を判定する
  - [ ] 20 代ならきのこ派と判定する
  - [ ] 20 代以外ならたけのこ派と判定する
- [ ] 正解率を計算する
- [ ] 実データで正解率を表示する

## 1.5 開発環境の準備

### プロジェクトの構成

`apps/haskell/` に cabal のプロジェクトを作ります。

```text
apps/haskell/
├── getting-started-ml.cabal
├── cabal.project
├── fourmolu.yaml
├── .hlint.yaml
├── src/
│   └── GettingStartedMl/
│       ├── Chapter01.hs
│       ├── Csv.hs
│       └── Dataset.hs
├── test/
│   ├── Spec.hs
│   └── GettingStartedMl/
│       ├── Chapter01Spec.hs
│       ├── CsvSpec.hs
│       └── DatasetSpec.hs
└── tools/
    └── coverage-threshold.sh
```

`getting-started-ml.cabal` はこうします。

```cabal
cabal-version:      2.4
name:               getting-started-ml
version:            0.1.0.0

common warnings
    -- 網羅していないパターンマッチを、テストではなくコンパイルで捕まえる。
    ghc-options: -Wall -Werror

library
    import:           warnings
    exposed-modules:  GettingStartedMl.Chapter01
                    , GettingStartedMl.Csv
                    , GettingStartedMl.Dataset
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
                    , GettingStartedMl.CsvSpec
                    , GettingStartedMl.DatasetSpec
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

最初に 2 つ、つまずきやすい点があります。

1 つめは **`-Wall -Werror` を `common` に書いて両方から取り込んでいる**ことです。この版では警告をエラーとして扱います。網羅していないパターンマッチや使っていない束縛を、テストではなくコンパイルで捕まえるためです。

2 つめは **テストスイートの `build-depends` をライブラリと別に書く必要がある**ことです。ライブラリに `bytestring` を書いても、テストからは見えません。「`Could not load module 'Data.ByteString.Lazy'. It is a member of the hidden package`」というエラーが出たら、テスト側に足し忘れています。

`test/Spec.hs` は 1 行だけです。

```haskell
{-# OPTIONS_GHC -F -pgmF hspec-discover #-}
```

これで `hspec-discover` が `test/` 以下の `*Spec.hs` を集めて、`spec` という名前の値を実行してくれます。テストファイルを足すたびに登録する手間が要りません（ただし `other-modules` には書きます）。

### Nix 環境と、道具が見えていた落とし穴

環境の準備で、[PHP 版](../php/01-machine-learning-and-first-test.md) と同じことが起きました。**整形の道具（fourmolu）も静的解析の道具（hlint）も、Nix の環境に入っていなかった**のです。

```bash
nix develop .#haskell
which fourmolu hlint
```

```text
/usr/local/bin/fourmolu
/usr/local/bin/hlint
```

`/usr/local/bin` は Nix の外、つまり**手元の機械に入っているもの**です。これが見えていると、検査が通るかどうかが機械ごとに変わります。版が違えば整形の結果も変わります。環境を切り替える意味がありません。

`ops/nix/environments/haskell/shell.nix` に足しました。

```nix
buildInputs = with packages; [
  ghc
  stack
  cabal-install
  haskell-language-server
  # 整形・静的解析・テストの道具。素の環境には無く、手元の PATH のものが
  # 見えてしまっていたので、環境の側で版を固定する。
  haskellPackages.fourmolu
  haskellPackages.hlint
  haskellPackages.hspec-discover
  # hmatrix は BLAS/LAPACK の C ライブラリを要求する。
  openblas
  pkg-config
];
```

`openblas` は第 7 章で使う hmatrix のためです。hmatrix は BLAS と LAPACK という C のライブラリを要求し、**素の環境では configure の段階で止まります**。

```text
Error: [Cabal-4345]
Missing dependencies on foreign libraries:
* Missing (or bad) C libraries: blas, lapack
```

`shellHook` で場所を伝えると通ります。

```nix
export LIBRARY_PATH="${packages.openblas}/lib:$LIBRARY_PATH"
export PKG_CONFIG_PATH="${packages.openblas.dev}/lib/pkgconfig:$PKG_CONFIG_PATH"
```

**C ライブラリへの依存を環境に足すかどうかは、判断の要る選択です。** [Elixir 版](../elixir/index.md) では EXLA（巨大な XLA のバイナリ）を「記事の『動かしてみる』の敷居が上がる」という理由で避けました。今回は足す側に倒しています。BLAS と LAPACK は数値計算の標準的な土台で Nix でも軽く入ること、そして**足さなければ突き合わせる相手が 1 つも無くなる**ことが理由です（[ADR 014](../../../adr/014-haskell-ml-libraries.md)）。

### 最初のテストを走らせる

```bash
cd apps/haskell
cabal build
cabal test --test-show-details=direct
```

```text
GettingStartedMl.Chapter01
  ルールによる判定
    二十代はきのこ派と判定する [✔]
```

**Hspec のテスト名には日本語をそのまま書けます。** `it "二十代はきのこ派と判定する"` のように、確かめたいことを文として書きます。

## 1.6 派閥を型で表す

### 取りうる値を数え上げる

まず「派閥」をどう表すかを決めます。文字列で持つこともできますが、この版では**型で数え上げます**。

```haskell
-- | 派閥。取りうる値を型で数え上げるので、綴りの間違いはコンパイルで止まる。
data Faction
  = Kinoko
  | Takenoko
  deriving (Eq, Show)
```

これは代数的データ型（ADT）で、[Rust 版](../rust/01-machine-learning-and-first-test.md) の `enum`、[F# 版](../fsharp/01-machine-learning-and-first-test.md) の判別共用体、[Scala 版](../scala/01-machine-learning-and-first-test.md) の `enum` にあたります。

文字列で持つ版（[Ruby 版](../ruby/01-machine-learning-and-first-test.md)・[Elixir 版](../elixir/01-machine-learning-and-first-test.md)・[PHP 版](../php/01-machine-learning-and-first-test.md)）と比べたときの違いは 2 つあります。

1 つは、**綴りを間違えるとコンパイルが止まる**ことです。`Kinoco` と書けばその場で分かります。文字列なら実行してみるまで分かりません。

もう 1 つは、**場合分けの網羅をコンパイラが確かめてくれる**ことです。`-Wall -Werror` を入れてあるので、`Kinoko` の場合だけ書いて `Takenoko` を忘れると、警告がエラーになって止まります。これは[PHP 版](../php/01-machine-learning-and-first-test.md)の共用型（`Leaf|Node`）が「網羅性までは強制しない」と書いたところの、ちょうど強い版です。

### レコードでデータを表す

```haskell
-- | 学習データ KvsT.csv の 1 行。身長・体重・年代と、正解ラベルの派閥を持つ。
data Person = Person
  { personHeight :: Int
  , personWeight :: Int
  , personAgeGroup :: Int
  , personFaction :: Faction
  }
  deriving (Eq, Show)

-- | 判定の手がかりになる特徴量。正解ラベルを持たない。
data Features = Features
  { featuresHeight :: Int
  , featuresWeight :: Int
  , featuresAgeGroup :: Int
  }
  deriving (Eq, Show)
```

フィールド名に接頭辞（`person`・`features`）を付けているのは、**Haskell のレコードのフィールド名がモジュール全体で 1 つの関数になる**からです。`Person` と `Features` の両方に `height` と書くと、名前がぶつかってコンパイルが通りません（`DuplicateRecordFields` を使えば書けますが、この版では素直に接頭辞を付けます）。

`deriving (Eq, Show)` で、値の比較と表示が自動で作られます。**中身で比較されるので、テストで `shouldBe` がそのまま使えます**。[PHP 版](../php/01-machine-learning-and-first-test.md)が `assertEquals`（中身）と `assertSame`（同一性）を使い分けたのに対して、Haskell では区別そのものがありません。値に同一性という概念が無いからです。

## 1.7 ルールで派閥を判定する

### Red: まだ無いものを呼ぶ

TDD の最初の一歩は、まだ存在しないものを呼ぶテストです。

> テスト駆動開発は次の 3 つのステップで進む。
>
> 1. レッド: 動作しないテストを 1 つ書く。おそらくは最初、コンパイルできない
> 2. グリーン: そのテストを迅速に動作させる。そのためには罪を犯してもよい
> 3. リファクタリング: テストを通すために発生した重複をすべて除去する
>
> — テスト駆動開発

```haskell
spec :: Spec
spec = do
  describe "ルールによる判定" $ do
    it "二十代はきのこ派と判定する" $
      predictByRule (Features 170 60 20) `shouldBe` Kinoko
```

「おそらくは最初、コンパイルできない」という但し書きが、Haskell ではそのまま当てはまります。関数が無いので型検査の段階で止まります。

### Green: ガードで書く

```haskell
-- | 人間が決めたルールで派閥を判定する。
predictByRule :: Features -> Faction
predictByRule features
  | featuresAgeGroup features == kinokoAgeGroup = Kinoko
  | otherwise = Takenoko
```

`|` で始まる行はガードです。条件を上から試して、最初に真になったものの右辺を返します。`if ... then ... else` でも書けますが、条件が増えたときに素直に並べられるのでガードを使います。

### 三角測量: 20 代以外も確かめる

```haskell
    it "二十代以外はたけのこ派と判定する" $
      map (predictByRule . Features 170 60) [10, 30, 40, 50]
        `shouldBe` [Takenoko, Takenoko, Takenoko, Takenoko]
```

> 三角測量では、2 つ以上の例があるときのみ、一般化を行う。
>
> — テスト駆動開発

`predictByRule . Features 170 60` という書き方に触れておきます。`Features` は 3 引数のコンストラクタですが、2 つだけ渡すと「残り 1 つを受け取る関数」になります（部分適用）。それを `.` で `predictByRule` とつなぐと、「年代を受け取って派閥を返す関数」ができます。

最初は `\age -> predictByRule (Features 170 60 age)` と書いていました。**hlint がこれを指摘しました。**

```text
Warning: Avoid lambda
Found:
  \ age -> predictByRule (Features 170 60 age)
Perhaps:
  predictByRule . Features 170 60
```

静的解析が「同じ意味でもっと短く書ける」と教えてくれる例です。指摘に従うかどうかは場合によりますが、ここは素直なので従いました。

## 1.8 失敗を型で表す

正解率を計算するとき、予測と正解ラベルの件数が違ったらどうするか。**この版では例外を投げません。型で表します。**

```haskell
-- | 予測が正解ラベルと一致した割合を返す。件数が違えば 'Left' を返す。
accuracy :: [Faction] -> [Faction] -> Either String Double
accuracy predictions labels
  | length predictions /= length labels =
      Left (printf "予測と正解ラベルの件数が違います: %d と %d" (length predictions) (length labels))
  | null labels = Left "正解ラベルがありません"
  | otherwise =
      Right (fromIntegral hits / fromIntegral (length labels))
  where
    hits = length (filter id (zipWith (==) predictions labels))
```

戻り値の型が `Double` ではなく `Either String Double` になっています。「`String` の失敗か、`Double` の成功か、どちらか」という意味です。

```haskell
    it "件数が違えば正解率を求められない" $
      accuracy [Kinoko] [Kinoko, Takenoko]
        `shouldBe` Left "予測と正解ラベルの件数が違います: 1 と 2"
```

**テストが「例外が飛ぶこと」ではなく「どんな値が返るか」を確かめている**点に注目してください。失敗は特別な仕組みではなく、ただの値です。

| 言語 | 失敗の表し方 | 呼ぶ側が無視できるか |
|------|------------|------------------|
| **Haskell** | **`Either String a`** | **できない（型が違うので使えない）** |
| Rust | `Result<T, E>` | できない（`#[must_use]`） |
| F# | `Result<'T, 'TError>` | できない |
| Go | `error` の戻り値 | **できる（`_` で捨てられる）** |
| Ruby・Elixir・PHP・Python | 例外 | できる（捕まえなければ落ちる） |

Haskell と Rust と F# は「失敗を無視できない」側です。`Either String Double` を `Double` として使おうとすれば型が合わず、コンパイルが止まります。

### 失敗を連ねる

CSV の 1 行を `Person` にするとき、4 つの列すべてが読めて初めて成功します。どれか 1 つでも失敗したら全体が失敗です。

```haskell
-- | 1 行を人物にする。どれか 1 つでも失敗すれば全体が 'Left' になる。
toPerson :: Row -> Either String Person
toPerson row =
  Person
    <$> number row "身長"
    <*> number row "体重"
    <*> number row "年代"
    <*> (column row "派閥" >>= parseFaction)
```

`<$>` と `<*>` は、「包みの中の値に関数を適用する」ための演算子です。ここでは `Either` が包みで、**どれか 1 つでも `Left` なら、最初の `Left` がそのまま全体の答えになります**。`if` を 4 回書く代わりに、演算子でつなぐだけで「どれか 1 つでも失敗したら失敗」が表せます。

Rust の `?` 演算子が同じ役割を果たしますが、あちらは早期リターン、こちらは**式の合成**という違いがあります。

リストに対しても同じことができます。

```haskell
-- | CSV を読み込み、列名で値を取り出して人物のリストにする。
loadPeople :: BL.ByteString -> Either String [Person]
loadPeople contents = parseRows contents >>= traverse toPerson
```

`traverse toPerson` は「リストの各要素に `toPerson` を適用し、**全部成功したら結果のリストを、1 つでも失敗したら最初の失敗を返す**」という意味です。`map` してから失敗を集める、という 2 段階が 1 つの関数になっています。

## 1.9 CSV を読み込む

### cassava も BOM を取り除かない

`KvsT.csv` の先頭には BOM が付いています。cassava はこれを取り除きません。確かめてみます。

```haskell
print (headerOf csv)
```

```text
Right ["\239\187\191shincho","taijyu"]
```

先頭の列名に BOM の 3 バイト（`\239\187\191`）が残っています。**cassava は値をバイト列として返す**ので、ここでは UTF-8 の符号化そのものが見えています。[Go 版](../go/01-machine-learning-and-first-test.md)・[Clojure 版](../clojure/01-machine-learning-and-first-test.md)・[Elixir 版](../elixir/01-machine-learning-and-first-test.md)・[PHP 版](../php/01-machine-learning-and-first-test.md) と同じ落とし穴です。

自分で取り除きます。

```haskell
-- | UTF-8 の BOM。記事とコードに実物を混ぜないようバイト列で書く。
bom :: BS.ByteString
bom = BS.pack [0xEF, 0xBB, 0xBF]

-- | 先頭の BOM を取り除く。
stripBom :: BL.ByteString -> BL.ByteString
stripBom contents
  | BL.fromStrict bom `BL.isPrefixOf` contents = BL.drop 3 contents
  | otherwise = contents
```

### この章いちばんの落とし穴: ByteString のリテラルに日本語を書くと壊れる

ここで、Haskell を使ううえで避けて通れない問題にぶつかります。最初、列名の比較をこう書いていました。

```haskell
-- これは動かない。
column :: Row -> BS.ByteString -> Either String BS.ByteString
column row name =
  case HM.lookup name row of
    Nothing -> Left ("列がありません: " <> BSC.unpack name)
    Just value -> Right value
```

テストはこう落ちました。

```text
expected: Right [Left "列がありません: 体重"]
 but got: Right [Left "列がありません: SÍ"]
```

原因は 2 つ重なっています。

**1 つめ。`Data.ByteString.Char8` の `unpack` は、1 バイトを 1 文字として読みます。** UTF-8 の「体重」は 6 バイトですが、これを 6 文字として解釈するので化けます。`Char8` という名前のとおり、このモジュールは「文字が 1 バイトに収まる」前提で書かれています。ASCII 専用だと思ってください。

**2 つめ。`OverloadedStrings` を使っているとき、`ByteString` のリテラルに日本語を書くと、同じように 1 文字が 1 バイトに詰められます。** つまり `"体重" :: ByteString` は、UTF-8 の 6 バイトではなく、壊れた 2 バイトになります。

```text
expected: Right [Right "170"]
 but got: Right [Left "列がありません: 身長"]
```

エラーメッセージは正しく表示されているのに列が見つからない、という不可解な状態になりました。メッセージを作る側は直したが、**探している列名のほうが壊れていた**のです。

対処は、**境界でだけ符号化・復号する**ことです。

```haskell
-- | 列名で値を読む。列が無ければ 'Left' を返す。
column :: Row -> Text -> Either String Text
column row name =
  case HM.lookup (TE.encodeUtf8 name) row of
    Nothing -> Left ("列がありません: " <> T.unpack name)
    Just value -> Right (TE.decodeUtf8Lenient (trim value))
```

列名と値は `Text` で扱い、cassava に渡すときだけ `encodeUtf8` でバイト列にします。`Text` は文字の列を表す型で、**リテラルに日本語を書いても壊れません**。

テストのデータを作るときも同じ罠があります。

```haskell
-- | UTF-8 の CSV をテストのために組み立てる。
--
-- 'Data.ByteString.Lazy.Char8' の @pack@ は 1 文字を 1 バイトに詰めるので、
-- 日本語をそのまま渡すと壊れたバイト列になる。テストのデータでも必ず
-- UTF-8 として符号化する。
utf8Csv :: T.Text -> BL.ByteString
utf8Csv = BL.fromStrict . TE.encodeUtf8
```

**同じ間違いを、実装で 1 回、テストで 1 回踏みました。** 型が `ByteString` であることは合っていて、中身の解釈だけが違うので、型検査では捕まりません。「バイト列」と「文字列」を区別する言語では、どこで変換するかを決めておく必要があります。

ちなみに、BOM をテストで書くときにも落とし穴があります。

```haskell
-- 誤り: Text では U+00EF U+00BB U+00BF の 3 文字になる
let csv = utf8Csv "\239\187\191身長,体重\n170,60\n"

-- 正しい: BOM は U+FEFF の 1 文字
let csv = utf8Csv "\65279身長,体重\n170,60\n"
```

`\239\187\191` は「UTF-8 で符号化したときのバイト列」で、`Text` のリテラルとしては別物です。`Text` に書くなら U+FEFF（10 進で 65279）の 1 文字です。**バイト列と文字列を行き来するときは、どちらの世界の話をしているかを毎回確かめる必要があります。**

### 数値を読む

```haskell
-- | 列名で整数を読む。読めなければ 'Left' を返す。
number :: Row -> Text -> Either String Int
number row name = do
  cell <- column row name
  case BSC.readInt (TE.encodeUtf8 cell) of
    Just (value, rest) | BS.null rest -> Right value
    _ -> Left (T.unpack name <> " を数値として読めません: " <> T.unpack cell)
```

`BSC.readInt` は「読めた整数と、残りのバイト列」を返します。`BS.null rest` で**残りが空であること**を確かめているのが大事です。これを忘れると `"170cm"` が 170 として通ってしまいます。テストで確かめます。

```haskell
    it "途中までしか数値でない値も弾く" $ do
      let csv = utf8Csv "身長\n170cm\n"
      fmap (fmap (`number` "身長")) (parseRows csv)
        `shouldBe` Right [Left "身長 を数値として読めません: 170cm"]
```

[PHP 版](../php/01-machine-learning-and-first-test.md)が `(int) '170cm'` を 170 にしてしまう `(int)` キャストを避けて `filter_var` を選んだのと、同じ判断です。**緩い変換が用意されていても、厳しいほうを選びます。**

`do` 記法を使っている点にも触れておきます。`Either` のような「失敗するかもしれない計算」を順に並べるとき、`do` で書くと途中の失敗が自動的に全体の失敗になります。命令型の見た目ですが、実際には `>>=` の連なりです。

## 1.10 実データで正解率を表示する

### IO を境界に閉じ込める

実データを読むには、ファイルを読む必要があります。ファイルの読み込みは副作用なので、Haskell では型に現れます。

```haskell
-- | 実データでルールによる判定の正解率を表示する。
--
-- ファイルを読むところだけが 'IO' で、そこから先は純粋関数になっている。
run :: IO (Either String String)
run = do
  file <- Dataset.path "KvsT.csv"
  contents <- BL.readFile file
  pure (report contents)

-- | 読み込んだ中身から報告の文字列を作る。純粋関数なのでテストで確かめられる。
report :: BL.ByteString -> Either String String
report contents = do
  people <- loadPeople contents
  let (x, t) = splitFeaturesAndLabels people
  rate <- accuracy (map predictByRule x) t
  pure (printf "データ件数: %d\nルールによる判定の正解率: %.4f\n" (length people) rate)
```

**`run` と `report` を分けている**のがこの節の要点です。`run` は `IO` の中にいてファイルを読みますが、`report` は純粋関数です。バイト列を受け取って文字列を返すだけで、外の世界に触れません。

この分け方には実利があります。**`report` はテストで直接確かめられます。** ファイルを用意する必要も、後片付けも要りません。[PHP 版](../php/01-machine-learning-and-first-test.md)が「`echo` せずに文字列を返す」ことでテスト可能にしたのと同じ考え方ですが、Haskell では**型がその区別を強制します**。`report` の中で誤ってファイルを読もうとすれば、型が合わずコンパイルが止まります。

本シリーズを通して、この構造は変わりません。**データの読み込みだけが `IO` で、前処理も学習も評価もすべて純粋関数です。** 第 15 章で API を書くときに、この分け方がそのまま層の分離になります。

### データが無ければテストを外す

実データは配布物なのでリポジトリに含まれません。CI にも置きません。

```haskell
  describe "実データ" $ do
    it "ルールによる判定の正解率がほかの言語版と一致する" $ do
      -- 学習データは配布物なのでリポジトリに無い。無ければこのテストは外す。
      found <- Dataset.exists "KvsT.csv"
      unless found $ pendingWith "学習データがありません"
      actual <- run
      actual `shouldBe` Right "データ件数: 19\nルールによる判定の正解率: 0.7368\n"
```

Hspec では `pendingWith` で「保留」にします。

```bash
ML_DATA_DIR=/nonexistent cabal test --test-show-details=direct
```

```text
      # PENDING: 学習データがありません
23 examples, 0 failures, 1 pending
```

**保留の数がはっきり表示されます。** [PHP 版](../php/01-machine-learning-and-first-test.md)の `Skipped: 34`、[Elixir 版](../elixir/01-machine-learning-and-first-test.md)の「1 excluded」と同じで、「テストが減っていることに気づかない」事故を避けられます。

### 結果

```bash
cabal repl lib:getting-started-ml
```

```text
データ件数: 19
ルールによる判定の正解率: 0.7368
```

**0.7368 は [Python 版](../python/01-machine-learning-and-first-test.md) をはじめとするほかの 13 言語版とすべて一致します。** 19 人のうち 14 人を正しく判定できた、ということです。

## 1.11 リファクタリング

### 整形

fourmolu で整形を検査します。

```bash
fourmolu --mode check src test
```

**違反があるときの終了コードは 100 です。** 1 でも 8 でもありません。

| 言語版 | 整形の道具 | 違反の終了コード |
|-------|----------|--------------|
| **Haskell** | **fourmolu** | **100** |
| PHP | PHP-CS-Fixer | 8 |
| Elixir | `mix format` | 1 |
| Rust | `cargo fmt` | 1 |

終了コードの癖は、壊してみるまで分かりません。CI で「失敗したのに気づかない」ことを避けるために、この版でも 4 つの検査すべてを実際に壊して確かめました。

### 静的解析

hlint は「もっと短く書ける」を指摘します。

```bash
hlint src test
```

```text
No hints
```

指摘があると終了コードは 1 です。

加えて、GHC 自身の `-Wall -Werror` があります。**hlint が「書き方」を、GHC が「型と網羅性」を見ます。** 守備範囲が違うので両方走らせます。[PHP 版](../php/05-package-management-and-static-analysis.md)で PHP-CS-Fixer と PHPStan が分かれていたのと同じ構図です。

### カバレッジ——ここでも自分で判定する

GHC には HPC というカバレッジの仕組みが組み込まれています。

```bash
cabal test --enable-coverage
```

そして、**cabal にも「カバレッジが N% を下回ったら失敗させる」機能がありません。** PHP 版と同じ状況です。HPC が出す HTML の総計を読んで判定するスクリプトを書きました。

```bash
./tools/coverage-threshold.sh 80
```

```text
式カバレッジ: 98% (242/246), しきい値: 80%
```

しきい値を割ると終了コード 3 で落ちます。この数字に決まりはなく、プロジェクトの約束です。

```text
式カバレッジ: 98% (242/246), しきい値: 99%
カバレッジがしきい値を下回りました: 98% < 99%
```

**第 3 波で 3 回続けて、カバレッジの仕組みに手を入れることになりました。** Elixir には既定のしきい値があり、PHP にはドライバすら無く、Haskell には仕組みはあるがしきい値が無い。「カバレッジをどう測り、どこで失敗させるか」は、言語が決めてくれることもあれば、プロジェクトが決めることもあります。

### まとめて検査する

リポジトリのルートから次で走ります。

```bash
npx gulp apps:check:haskell
```

```text
式カバレッジ: 98% (242/246), しきい値: 80%
```

4 つとも、わざと壊して落ちることを確かめました。

| 検査 | 違反したときの終了コード |
|------|----------------------|
| fourmolu（整形） | **100** |
| hlint（静的解析） | 1 |
| cabal test（テスト） | 1 |
| カバレッジのしきい値（自作） | 3（自分で決めた） |

## 1.12 まとめ

この章では、ルールによる判定と正解率を TDD で実装し、実データで 0.7368 という値を得ました。Haskell に固有の論点は次のとおりです。

1. **失敗を型で表す** — `Either String a` を返す。呼ぶ側は無視できず、テストは「返る値」を確かめる。例外を投げる Ruby 版・Elixir 版・PHP 版とは正反対で、Rust 版・F# 版と同じ流儀
2. **失敗は演算子で連ねられる** — `<$>`・`<*>` で「どれか 1 つでも失敗したら失敗」が書け、`traverse` でリスト全体に広げられる
3. **取りうる値を型で数え上げる** — `data Faction = Kinoko | Takenoko` と書けば、綴りの間違いも場合分けの漏れもコンパイルで止まる（`-Wall -Werror`）
4. **`ByteString` のリテラルに日本語を書くと壊れる** — `OverloadedStrings` も `Char8.unpack` も 1 文字を 1 バイトとして扱う。**実装で 1 回、テストで 1 回、同じ間違いを踏んだ**。列名と値は `Text` で持ち、境界でだけ符号化・復号する
5. **BOM を書くときはバイト列か文字列かを区別する** — `Text` のリテラルでは U+FEFF の 1 文字（`\65279`）。`\239\187\191` は別物
6. **`IO` を境界に閉じ込める** — ファイルを読む `run` と、純粋関数の `report` を分ける。分けたことが型で保証され、`report` はテストで直接確かめられる
7. **`readInt` は残りを確かめる** — `BS.null rest` を忘れると `"170cm"` が 170 として通る
8. **テストスイートの依存はライブラリと別に書く** — ライブラリに書いた `build-depends` はテストから見えない
9. **終了コードの癖は壊してみるまで分からない** — fourmolu は **100**、hlint は 1、cabal test は 1
10. **道具は環境の側で固定する** — fourmolu も hlint も素の Nix 環境に無く、手元の `/usr/local/bin` が見えていた（PHP 版の pcov と同じ形）

**TODO リスト（この章の完了時点）**:

- [x] CSV を読み込む
- [x] 特徴量と正解ラベルに分ける
- [x] ルールで派閥を判定する
- [x] 正解率を計算する
- [x] 実データで正解率を表示する

次の章では、アヤメのデータを読み込み、欠損値を補完して、訓練データとテストデータに分けます。Haskell 版も **`java.util.Random` と同じ線形合同法を自作します**。[PHP 版](../php/02-data-preprocessing-and-triangulation.md)では「整数が溢れると float に化ける」ために乗算を分割する必要がありましたが、**Haskell の `Int` は Java と同じく折り返す**ので、仕様をそのまま書けます。

Haskell 版のほかの章は [Haskell 版のトップ](index.md) から辿れます。
