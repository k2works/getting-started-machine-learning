---
type: Article
title: "第 2 章: データの前処理と三角測量"
description: "アヤメのデータを読み込み、欠損値を平均値で補完して訓練データとテストデータに分ける。java.util.Random と同じ線形合同法を純粋関数として書き、欠損を Maybe で表す。テストの中でも失敗を無視できないという Haskell の厳しさを扱う。"
tags: [article,getting-start-ml,haskell]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 2 章: データの前処理と三角測量

## 2.1 はじめに

この章では、機械学習の前に必ず必要になる**前処理**を実装します。題材はアヤメ（iris）のデータで、150 件の測定値から品種を当てる問題です。第 3 章で決定木を学習させるための土台を作ります。

この章の山場は**乱数生成器を自作すること**です。`System.Random` はほかの言語版と並びが合わないので、`java.util.Random` と同じ 48 ビットの線形合同法を書きます。[PHP 版](../php/02-data-preprocessing-and-triangulation.md)では「整数が溢れると float に化ける」という落とし穴のために乗算を分割する必要がありましたが、**Haskell の `Int` は Java と同じく折り返す**ので、仕様をそのまま書けます。

[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md) と同じ題材・同じ TODO リストで進めます。

## 2.2 題材とデータ

### iris.csv

`iris.csv` には、アヤメの花 150 件の測定値と品種が記録されています。

| 列 | 意味 | Haskell 版での読み方 |
|----|------|-------------------|
| がく片長さ | がく片の長さ | `optionalNumber row "がく片長さ"` → `Either String (Maybe Double)` |
| がく片幅 | がく片の幅 | 同上 |
| 花弁長さ | 花弁の長さ | 同上 |
| 花弁幅 | 花弁の幅 | 同上 |
| 種類 | 品種（3 種類が 50 件ずつ） | `text row "種類"` → `Either String Text` |

値は 0.0〜1.0 に収まる形で配布されています。特徴量の 4 列には合わせて 7 件の欠損値があります。

**欠損は `Maybe Double` で表します。** そして読み取りそのものが失敗しうるので、戻り値は `Either String (Maybe Double)` という二重の包みになります。

| 包み | 意味 |
|------|------|
| `Either String` の外側 | **列が無い・数値として読めない**（呼ぶ側の間違い、またはデータの壊れ） |
| `Maybe` の内側 | **値が空欄**（データとして正常で、補完すべきもの） |

この 2 つを分けているのが大事なところです。「列名を間違えた」と「値が空欄だった」は、まったく別のことです。前者は直すべき誤りで、後者は前処理で埋めるべきものです。型が違えば、取り違えようがありません。

ほかの言語版と並べます。

| 言語 | 欠損値の表し方 | 読み取りの失敗 |
|------|--------------|--------------|
| **Haskell** | **`Maybe Double`** | **`Either String`（型が別）** |
| Rust | `Option<f64>` | `Result<_, E>`（型が別） |
| F# | `float option` | `Result<_, _>`（型が別） |
| Scala | `Option[Double]` | 例外 |
| PHP | `?float` | 例外 |
| Ruby・Elixir・Clojure・Python | `nil`／`None` | 例外 |

**失敗と欠損の両方を型で分けている**のは Haskell・Rust・F# だけです。

### 訓練データとテストデータ

学習に使うデータでそのまま性能を測ると、「答えを覚えただけ」のモデルを高く評価してしまいます。そこでデータを 2 つに分けます。この章では 150 件を訓練データ 105 件・テストデータ 45 件に分けます。

### なぜ乱数を自作するのか

分割の前に並べ替えます。並べ替えには乱数が要ります。

Haskell には `System.Random` がありますが、**ほかの言語版と並びが合いません**。一致しなければ訓練データに入る 105 行が変わり、そこから求まる平均値も、第 3 章の決定木の境界も変わります。章をまたいだ突き合わせができなくなります。

そこで Haskell 版も、[Elixir 版](../elixir/02-data-preprocessing-and-triangulation.md)・[PHP 版](../php/02-data-preprocessing-and-triangulation.md) と同じく **`java.util.Random` と同じ乱数生成器を自作します**。`java.util.Random` は乱数の作り方が仕様として文書に書かれていて、48 ビットの線形合同法という、写し取れる程度に小さなアルゴリズムです。

## 2.3 開発環境の準備

第 1 章で作った `apps/haskell/` にモジュールを足します。

```text
apps/haskell/
├── src/
│   └── GettingStartedMl/
│       ├── Chapter01.hs
│       ├── Chapter02.hs   # この章
│       ├── Csv.hs         # この章で列の順と欠損値の読み取りを足す
│       ├── Dataset.hs
│       └── Random.hs      # この章（自作の乱数）
└── test/
    └── GettingStartedMl/
        ├── Chapter01Spec.hs
        ├── Chapter02Spec.hs
        ├── CsvSpec.hs
        ├── DatasetSpec.hs
        └── RandomSpec.hs
```

`getting-started-ml.cabal` の `exposed-modules` と `other-modules` に足すのを忘れないでください。**足し忘れると「モジュールが見つからない」ではなく「そのモジュールはビルドの対象に入っていない」という形で現れます。**

依存は第 1 章のまま（cassava・containers・text など）で足ります。**乱数を自作するので、この章では依存が 1 つも増えません。**

## 2.4 TODO リストの作成

**TODO リスト**:

- [ ] 乱数生成器を自作する
  - [ ] シードから状態を作る
  - [ ] 範囲を指定して整数を 1 つ返す
  - [ ] Fisher-Yates で並べ替える
  - [ ] シード 0 の並びが JVM の言語版と一致する
- [ ] 表を読み込む
  - [ ] 数値の列を読む。空欄は欠損値にする
  - [ ] 列ごとに欠損値の数を数える
- [ ] 平均値で欠損値を補完する
- [ ] 特徴量と正解ラベルに分ける
- [ ] 訓練データとテストデータに分ける
- [ ] 前処理をまとめる
- [ ] 実データで前処理の結果を表示する

## 2.5 乱数生成器を自作する

### 状態を値として持ち回る

命令型の言語では、乱数生成器は内部に状態を持ちます。`rand()` を呼ぶたびに状態が書き換わり、次の値が変わります。

**Haskell の純粋関数は状態を持てません。** 同じ引数には必ず同じ値を返すからです。そこで、状態を**引数と戻り値で受け渡します**。

```haskell
-- | 乱数生成器の状態。48 ビットに収まる。
newtype Seed = Seed Int
  deriving (Eq, Show)

-- | シードから状態を作る。@java.util.Random@ の @setSeed@ と同じ。
newSeed :: Int -> Seed
newSeed seed = Seed ((seed `xor` multiplier) .&. mask)

-- | 0 以上 @bound@ 未満の整数を 1 つ返し、次の状態と一緒に返す。
nextInt :: Int -> Seed -> (Int, Seed)
```

`nextInt` の戻り値が `(Int, Seed)` の組になっています。「値と、次の状態」です。使う側は次の状態を受け取って次の呼び出しに渡します。

```haskell
takeInts :: Int -> Int -> Int -> [Int]
takeInts bound seed = go (newSeed seed)
  where
    go _ 0 = []
    go s n = let (v, s2) = nextInt bound s in v : go s2 (n - 1)
```

**手間が増えたように見えますが、得るものがあります。** 「同じ状態からは必ず同じ値が出る」ことが型から保証されるので、[PHP 版](../php/10-logistic-regression-and-ensemble.md)で問題になった「ライブラリが大域の `mt_rand` から引くので実行ごとに結果が変わる」ということが起こりません。どこが乱数に依存しているかが、型を見れば分かります。

`newtype Seed = Seed Int` としているのも意味があります。ただの `Int` にすると、シードと普通の整数を取り違えられます。`newtype` なら実行時の負担なしに型だけを分けられます。

### テストファースト: 性質から挟み撃ちにする

いきなり「JVM と同じ並びになること」をテストにはできません。まだ何も書いていないので、期待する並びが分かりません。そこで、**どんな実装でも満たすべき性質**から書きます。

```haskell
    it "範囲の中の値だけを返す" $
      all (\v -> v >= 0 && v < 10) (takeInts 10 7 1000) `shouldBe` True

    it "同じシードなら同じ並びになる" $
      takeInts 100 123 20 `shouldBe` takeInts 100 123 20
```

2 つめのテストは、Haskell では**必ず通ります**。純粋関数なので、同じ引数には同じ値しか返りません。当たり前のことをテストに書く意味は薄い——と思いきや、これが通らなくなる書き方（`unsafePerformIO` や `IORef` で状態を隠す）もあるので、「そうしない」という設計の宣言として残します。

### Green: 48 ビットの線形合同法

`java.util.Random` の仕様はこうです。状態は 48 ビットの整数で、次の状態は

```text
state = (state * 0x5DEECE66D + 0xB) mod 2^48
```

で求めます。そのまま書きます。

```haskell
mask, multiplier, increment :: Int
mask = 0xFFFFFFFFFFFF
multiplier = 0x5DEECE66D
increment = 0xB

-- | 状態を 1 つ進める。
advance :: Seed -> Seed
advance (Seed s) = Seed ((s * multiplier + increment) .&. mask)
```

**これで動きます。** [PHP 版](../php/02-data-preprocessing-and-triangulation.md)では、ここで `state * multiplier` が 83 ビットになり、64 ビットを超えた整数が float に化けて精度を失うという問題が起きました。乗算を上下 24 ビットに分ける必要がありました。

Haskell の `Int` は 64 ビットで、**溢れると折り返します**。

```haskell
print ((maxBound :: Int) + 1)
```

```text
-9223372036854775808
```

Java・Kotlin・Scala・C#・Go と同じ振る舞いです。だから仕様の式をそのまま書けます。

| 言語 | 64 ビットを超えたとき |
|------|-------------------|
| **Haskell（`Int`）** | **折り返す** |
| Java・Kotlin・Scala・C#・Go | 折り返す |
| PHP | **float に化ける（精度を失う）** |
| Elixir・Clojure・Ruby・Python | 多倍長整数になる |
| Rust | デバッグではパニック、リリースでは折り返す |

なお Haskell には `Integer`（多倍長）もあります。こちらを使うと Elixir 版のように正確なまま大きくなり、`.&. mask` で切り落とすことになります。**どちらでも正しく動きますが、`Int` のほうが速く、しかも Java と同じ挙動なので仕様を写しやすい**ので `Int` にしました。「型を選ぶ」という作業が、ここでは「どの数の世界で計算するかを選ぶ」ことになっています。

### 2 の冪のときだけ別式

`java.util.Random` の `nextInt(bound)` には、`bound` が 2 の冪のときだけ通る別の経路があります。

```haskell
nextInt :: Int -> Seed -> (Int, Seed)
nextInt bound s
  | bound <= 0 = error ("bound は正の数でなければなりません: " <> show bound)
  | (bound .&. negate bound) == bound =
      let (bits, next) = nextBits 31 s
       in ((bound * bits) `shiftR` 31, next)
  | otherwise = reject s
```

`bound .&. negate bound` が `bound` に等しいのは、立っているビットが 1 つだけのとき——つまり 2 の冪のときです。**ここを省くと並びが合いません。**

`bound <= 0` で `error` を呼んでいる点に触れておきます。この版は失敗を `Either` で表す方針ですが、ここだけは例外にしました。**「正の数を渡す」のは呼ぶ側が守るべき約束で、データの内容によって起きる失敗ではない**からです。`Either` にすると、正しく使っている側まで `Left` の場合を書かされます。**回復できる失敗は型で、プログラムの誤りは `error` で**——という使い分けです。

### 剰余の偏りを避ける棄却

2 の冪でない場合は剰余を取りますが、そのままでは偏ります。`java.util.Random` は**偏りを生む値を捨てて引き直す**ことで解決しています。

```haskell
    -- 剰余の偏りを避けるため、範囲をはみ出す値は捨てて引き直す。
    reject current =
      let (bits, next) = nextBits 31 current
          value = bits `mod` bound
       in if bits - value + (bound - 1) >= 0x80000000
            then reject next
            else (value, next)
```

`where` の中に再帰する補助関数を書いています。状態を引数で受け渡しているので、「引き直す」が素直に「次の状態で自分を呼ぶ」になります。

### Fisher-Yates——不変のリストでどう交換するか

ここで Haskell ならではの問題にぶつかります。**Fisher-Yates は「`i` 番目と `j` 番目を交換する」操作の繰り返しですが、不変のリストには添字での書き換えがありません。**

`list !! i` で読むことはできますが、`list !! i = x` とは書けません。しかも `!!` は先頭から数えるので、繰り返すと計算量が増えます。

`Map` に写してから交換します。

```haskell
-- | Fisher-Yates で並べ替える。
--
-- 後ろから順に、まだ選んでいない範囲から 1 つ選んで交換する。
-- 不変のリストでは添字の書き換えができないので、'M.Map' に写してから交換する。
shuffle :: [a] -> Int -> [a]
shuffle items seed
  | count <= 1 = items
  | otherwise = M.elems (go (count - 1) (newSeed seed) indexed)
  where
    count = length items
    indexed = M.fromList (zip [0 ..] items)

    go i s table
      | i < 1 = table
      | otherwise =
          let (j, next) = nextInt (i + 1) s
           in go (i - 1) next (swap i j table)

    swap i j table =
      case (M.lookup i table, M.lookup j table) of
        (Just atI, Just atJ) -> M.insert i atJ (M.insert j atI table)
        _ -> table
```

`M.insert` は新しい `Map` を返しますが、**内部で木の大部分を共有する**ので、要素をすべて複製するわけではありません。不変のデータ構造が実用的な速さで動くのはこの仕組みのおかげです。

最後の `M.elems` で、キー（0 から始まる添字）の順に値を取り出します。`Map` がキーの順で並ぶという性質を、ここでは逆に利用しています。

`swap` の `_ -> table` が気になるかもしれません。「どちらかが見つからなかったら何もしない」という枝です。添字は必ず範囲内なので起きませんが、**`Maybe` を返す `M.lookup` を使う以上、書かないとコンパイルが通りません**（`-Wall -Werror`）。起きない場合を書かされるのは型の厳しさの代償ですが、「本当に起きないか」を一度考える機会でもあります。

### 山場のテスト: JVM の言語版と一致する

```haskell
  describe "線形合同法" $ do
    it "java.util.Random と同じ並びを返す" $
      -- Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版と同じ並び。
      takeInts 100 0 5 `shouldBe` [60, 48, 29, 47, 15]

  describe "Fisher-Yates の並べ替え" $ do
    it "ほかの言語版と一致する" $
      shuffle [0 .. 9 :: Int] 0 `shouldBe` [4, 8, 9, 6, 3, 5, 2, 1, 7, 0]
```

```text
44 examples, 0 failures
```

**通りました。** JVM を持たない Haskell から、`java.util.Random` とビット単位で同じ数列が出ています。[Java 版](../java/02-data-preprocessing-and-triangulation.md)・[Elixir 版](../elixir/02-data-preprocessing-and-triangulation.md)・[PHP 版](../php/02-data-preprocessing-and-triangulation.md) と同じ並びです。

## 2.6 欠損を `Maybe` で読む

```haskell
-- | 列名で小数を読む。空欄なら 'Nothing' を返す。
--
-- 「値が無いかもしれない」ことを 'Maybe' で表す。呼ぶ側は取り出す前に
-- 場合分けを書くことになり、欠損の扱いを忘れられない。
optionalNumber :: Row -> Text -> Either String (Maybe Double)
optionalNumber row name = do
  cell <- column row name
  if T.null (T.strip cell)
    then Right Nothing
    else case reads (T.unpack cell) of
      [(value, rest)] | all (== ' ') rest -> Right (Just value)
      _ -> Left (T.unpack name <> " を数値として読めません: " <> T.unpack cell)
```

`reads` は「読めた値と、残りの文字列」の候補をリストで返します。`[(value, rest)]` と 1 つだけのパターンで受け、`rest` が空白だけであることを確かめています。**候補が 0 個（読めない）でも 2 個以上（あいまい）でも `Left` になります。**

## 2.7 平均値で欠損値を補完する

### 欠損を除いて平均を求める

```haskell
columnMeans :: [Row] -> [Text] -> Either String (M.Map Text Double)
columnMeans rows columns = M.fromList <$> traverse mean columns
  where
    mean column = do
      values <- traverse (`optionalNumber` column) rows
      case catMaybes values of
        [] -> Left ("値がすべて空欄です: " <> T.unpack column)
        found -> Right (column, sum found / fromIntegral (length found))
```

`traverse (`optionalNumber` column) rows` の型は `Either String [Maybe Double]` です。**「全部の行の読み取りが成功したら、`Maybe Double` のリスト」**という意味になります。読み取りの失敗（`Either`）はここで全体に伝わり、欠損（`Maybe`）はリストの中に残ります。

そして `catMaybes` で `Just` の中身だけを取り出します。**「欠損を除く」が関数 1 つで書けます。**

最初は `[v | Just v <- values]` とリスト内包表記で書いていました。hlint が `catMaybes` を教えてくれました。

```text
Suggestion: Use catMaybes
Found:
  [v | Just v <- values]
Perhaps:
  catMaybes values
```

どちらも同じ意味ですが、`catMaybes` のほうが「何をしているか」が名前で分かります。

### 補完する

```haskell
fillMissing :: [Row] -> [Text] -> M.Map Text Double -> Either String [M.Map Text Double]
fillMissing rows columns fillValues = traverse fillRow rows
  where
    fillRow row = M.fromList <$> traverse (fillColumn row) columns

    fillColumn row column = do
      value <- optionalNumber row column
      case value of
        Just v -> Right (column, v)
        Nothing -> case M.lookup column fillValues of
          Just v -> Right (column, v)
          Nothing -> Left ("補完する値がありません: " <> T.unpack column)
```

`case value of` で `Just` と `Nothing` の両方を書いています。**書かないとコンパイルが通りません。**

[PHP 版](../php/02-data-preprocessing-and-triangulation.md)では、ここで `?? 0.0` と書いてしまう危険を説明しました。列名を間違えたときに黙って 0.0 で埋まる、という間違いです。Haskell では `Nothing` の場合に何をするかを必ず書くので、**「うっかり既定値で埋める」コードを書くにも、それを明示的に書く必要があります**。

戻り値の型が `[M.Map Text Double]` になっている点にも注目してください。**補完の前は `Maybe Double`、後は `Double`** です。型が変わることで、「補完済みのデータ」と「まだ欠損があるデータ」を取り違えられません。

## 2.8 訓練データとテストデータに分ける

```haskell
splitTrainTest :: [a] -> [Text] -> Double -> Int -> Either String (Split a)
splitTrainTest x t testSize seed
  | length x /= length t =
      Left (printf "件数が違います: %d と %d" (length x) (length t))
  | otherwise =
      let pairs = shuffle (zip x t) seed
          total = length pairs
          trainCount = total - ceiling (fromIntegral total * testSize :: Double)
          (train, test) = splitAt trainCount pairs
       in Right
            Split
              { xTrain = map fst train
              , xTest = map fst test
              , tTrain = map snd train
              , tTest = map snd test
              }
```

`zip x t` で組にしてから並べ替えるので、**対応が崩れません**。`zip` は「2 つのリストを組のリストにする」だけの関数ですが、ここでは「対応を保つ」という設計上の意図を担っています。

型変数 `a` に注目してください。特徴量の型が何であれ動きます。第 2 章では `Row`（読み込んだ生の行）を渡し、第 3 章以降では補完済みの `Map Text Double` を渡します。**「並べ替えて分ける」という操作は中身に依存しない**ので、型で抽象しておけば書き直さずに済みます。

`Split` も型引数を取ります。

```haskell
data Split a = Split
  { xTrain :: [a]
  , xTest :: [a]
  , tTrain :: [Text]
  , tTest :: [Text]
  }
  deriving (Eq, Show)
```

## 2.9 前処理をまとめる

```haskell
prepareIris :: BL.ByteString -> Double -> Int -> Either String (Split (M.Map Text Double))
prepareIris contents testSize seed = do
  table <- loadTable contents
  (columns, rows, labels) <- splitFeaturesAndTarget table targetColumn
  split <- splitTrainTest rows labels testSize seed
  means <- columnMeans (xTrain split) columns
  filledTrain <- fillMissing (xTrain split) columns means
  filledTest <- fillMissing (xTest split) columns means
  pure split {xTrain = filledTrain, xTest = filledTest}
```

`do` 記法で 6 つの手順を並べています。**どれか 1 つでも `Left` を返せば、そこで止まって全体が `Left` になります。** 失敗の伝播を書く必要がありません。

順番が大事です。**分割してから、訓練データだけで平均値を求め、それでテストデータも補完します。** 先に全体の平均で補完してから分割すると、テストデータの情報が訓練データに混ざります（**リーク**）。

`split {xTrain = filledTrain, xTest = filledTest}` はレコードの更新構文です。**元の `split` は変わらず、指定したフィールドだけを差し替えた新しい値ができます。** 型も `Split Row` から `Split (M.Map Text Double)` に変わっています。「補完した」ことが型に現れます。

## 2.10 実データで前処理の結果を表示する

### テストの中でも失敗を無視できない

テストを書いていて、Haskell の厳しさに正面からぶつかりました。

```haskell
-- これは書けない。
let Right table = loadTable (utf8Csv "a,b\n1,\n,2\n3,4\n")
```

```text
error: [GHC-62161] [-Wincomplete-uni-patterns, Werror=incomplete-uni-patterns]
```

`let Right table = ...` は「`Right` だったらその中身を `table` にする」という意味ですが、**`Left` だった場合を書いていません**。実行時に `Left` が来ればその場で落ちます。GHC はこれを警告し、`-Werror` を入れてあるのでエラーになります。

テストコードなのだから多少雑でもよい、とは言わせてもらえません。そこで、意図を明示する関数を書きました。

```haskell
-- | テストの中で「成功しているはず」の値を取り出す。
--
-- @let Right table = ...@ と書くと、'Left' の場合を書いていないので
-- @-Wincomplete-uni-patterns@ が止める。テストの中でも失敗の場合を
-- 無視できないので、失敗したらその場で落ちる関数にして明示する。
expectRight :: Either String a -> a
expectRight (Right value) = value
expectRight (Left err) = error ("失敗しました: " <> err)
```

**やっていることは同じです。** `Left` なら落ちます。違うのは、**落ちることを自分で書いた**という点だけです。名前が付いているので、読む側も「ここは成功しているはず」という前提だと分かります。

これは Haskell を使ううえで何度も出会う形です。**言語は「雑に書く」ことを禁じるのではなく、「雑に書くなら、そう書け」と要求します。**

### データが無ければテストを外す

```haskell
    it "前処理の結果がほかの言語版と一致する" $ do
      found <- Dataset.exists "iris.csv"
      unless found $ pendingWith "学習データがありません"
      file <- Dataset.path "iris.csv"
      contents <- BL.readFile file
      let split = expectRight $ prepareIris contents 0.3 0
          values = [v | row <- xTrain split, Just v <- [M.lookup "がく片長さ" row]]
          mean = sum values / fromIntegral (length values)
      (length (xTrain split), length (xTest split)) `shouldBe` (105, 45)
      -- Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版と一致する。
      abs (mean - 0.4215384615384616) < 1e-15 `shouldBe` True
```

```bash
ML_DATA_DIR=/nonexistent cabal test --test-show-details=direct
```

```text
44 examples, 0 failures, 3 pending
```

### 結果

```text
データ件数: 150
欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
訓練データ: 105 件, テストデータ: 45 件
特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅
```

**訓練データのがく片長さの平均は 0.4215384615384616 で、[Java 版](../java/02-data-preprocessing-and-triangulation.md)・[Scala 版](../scala/02-data-preprocessing-and-triangulation.md)・[Clojure 版](../clojure/02-data-preprocessing-and-triangulation.md)・[Elixir 版](../elixir/02-data-preprocessing-and-triangulation.md)・[PHP 版](../php/02-data-preprocessing-and-triangulation.md) と 1e-15 まで一致しました。**

同じ 105 行が訓練データに入り、同じ順で足し合わされている、ということです。自作の乱数が効いています。

なお `run` と `report` を分ける構造は第 1 章と同じです。ファイルを読む `run` だけが `IO` で、`report` は純粋関数です。だからこの章の実データのテストも、`report` に読み込んだ中身を渡して確かめられます。

## 2.11 可視化について

Haskell 版には Notebook による探索と可視化の節を設けません。グラフは [Python 版](../python/02-data-preprocessing-and-triangulation.md) と [Kotlin 版](../kotlin/02-data-preprocessing-and-triangulation.md) の可視化の節を参照してください。

## 2.12 まとめ

この章では前処理を TDD で実装し、訓練データの平均値をほかの 5 言語版（Java・Scala・Clojure・Elixir・PHP）と一致させました。Haskell に固有の論点は次のとおりです。

1. **乱数の状態を値として持ち回る** — 純粋関数は状態を持てないので `(Int, Seed)` を返す。手間は増えるが、**どこが乱数に依存しているかが型に現れ**、大域の状態による「実行ごとに結果が変わる」問題が起きない
2. **`Int` は Java と同じく折り返す** — PHP 版で必要だった乗算の分割が要らず、仕様の式をそのまま書ける。`Integer`（多倍長）を選ぶこともでき、**型を選ぶことが「どの数の世界で計算するか」を選ぶことになる**
3. **不変のリストには添字の書き換えが無い** — Fisher-Yates は `Map` に写してから交換する。`M.insert` は木の大部分を共有するので、要素をすべて複製するわけではない
4. **失敗と欠損を別の型で表す** — `Either String (Maybe Double)` の二重の包み。「列名を間違えた」と「値が空欄だった」は別のことなので、型でも別にする
5. **`traverse` が失敗を集約する** — `[Row]` に対する読み取りが `Either String [Maybe Double]` になり、1 つでも失敗すれば全体が失敗する
6. **補完すると型が変わる** — `Maybe Double` から `Double` へ。「補完済み」と「まだ欠損がある」を取り違えられない
7. **テストの中でも失敗を無視できない** — `let Right x = ...` は `-Wincomplete-uni-patterns` が止める。`expectRight` で「落ちること」を自分で書く。**言語は雑に書くことを禁じるのではなく、雑に書くならそう書けと要求する**
8. **起きない場合も書かされる** — `M.lookup` が `Maybe` を返す以上、`Nothing` の枝を書く。代償ではあるが、「本当に起きないか」を一度考える機会でもある
9. **回復できる失敗は型で、プログラムの誤りは `error` で** — `nextInt` に 0 以下を渡すのは呼ぶ側の間違いなので `Either` にしない

**TODO リスト（この章の完了時点）**:

- [x] 乱数生成器を自作する
- [x] 表を読み込む
- [x] 平均値で欠損値を補完する
- [x] 特徴量と正解ラベルに分ける
- [x] 訓練データとテストデータに分ける
- [x] 前処理をまとめる
- [x] 実データで前処理の結果を表示する

次の章では、いよいよ機械学習のアルゴリズムを実装します。ジニ不純度による決定木を自作します。**Haskell には決定木のライブラリが無い**ので、ここで書いたものがそのまま最終実装になります。木を代数的データ型で表すと、**場合分けの網羅をコンパイラが確かめてくれる**——型がいちばん効く章です。

Haskell 版のほかの章は [Haskell 版のトップ](index.md) から辿れます。
