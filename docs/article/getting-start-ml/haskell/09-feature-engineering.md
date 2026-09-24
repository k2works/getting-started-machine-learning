---
type: Article
title: "第 9 章: 特徴量エンジニアリング"
description: "ダミー変数・標準化・多項式特徴量・外れ値・表の結合を Haskell の TDD で自作し、標準化を statistics パッケージと突き合わせる。Shift_JIS の CSV を mkTextEncoding の CP932 で読み、文字コードを取り違えたときに Haskell で何が起きるかを実測する。"
tags: [article,getting-start-ml,haskell]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 9 章: 特徴量エンジニアリング

## 9.1 はじめに

モデルの性能は、アルゴリズムよりも「どんな特徴量を渡すか」で大きく変わることがあります。元のデータから、モデルが学びやすい特徴量を作り出す作業を **特徴量エンジニアリング** と呼びます。

この章では、ボストンの住宅価格データを題材に、次の 5 つの技法を TDD で自作します。

- カテゴリ値をダミー変数（0 と 1 の列）にする
- 特徴量を標準化する
- 2 乗の項と交互作用の項（多項式特徴量）を作る
- 外れ値を検出する
- 別の表を結合して特徴量を増やす

標準化は `statistics` パッケージの平均・分散と突き合わせます。最後に、作った特徴量で線形回帰の決定係数がどう変わるかを実データで測ります。

[Python 版の第 9 章](../python/09-feature-engineering.md) と同じ TODO リストで進め、[Java 版](../java/09-feature-engineering.md)・[Scala 版](../scala/09-feature-engineering.md)・[Clojure 版](../clojure/09-feature-engineering.md)・[Elixir 版](../elixir/09-feature-engineering.md)・[PHP 版](../php/09-feature-engineering.md) と数値を対比します。Haskell 版はデータフレームのライブラリを使わず、第 2 章で決めた「列名を鍵にした `Map` の並び」で表を持ちます（[ADR 014](../../../adr/014-haskell-ml-libraries.md)）。

この章で Haskell ならではの点は 4 つです。

- **Shift_JIS が追加の依存なしで読めます。** `System.IO.mkTextEncoding` は iconv の名前をそのまま受け取るので、`mkTextEncoding "CP932"` の 1 行で済みます。C ライブラリを必要とする `text-icu` は要りません。[Elixir 版](../elixir/09-feature-engineering.md) が codepagex という外部ライブラリを足したのとは対照的で、**この章で足した依存は `statistics` だけ** です
- **取り違えたときの振る舞いを、こちらで選べます。** Shift_JIS を `UTF-8` として読むと例外になりますが、`UTF-8//IGNORE` なら黙って落とし、`UTF-8//TRANSLIT` なら U+FFFD に置き換えます。**Java 版（例外）・Clojure 版（U+FFFD）・Elixir 版（不正なバイナリ）・PHP 版（どちらも静かに通る）のどれもを、同じ言語の中で選べたのはシリーズで初めて** です
- **`statistics` の関数名が紛らわしいです。** `variance` が n で割る最尤推定、`stdDev` が n−1 で割る標本標準偏差の平方根です。自作は n で割るので、`sqrt . variance` と一致し、`stdDev` とは一致しません
- **`sum` を書いてよいかを毎回考えることになります。** リストの `sum` は右結合になりうるので、ほかの言語版と浮動小数点数の桁まで合わせたい計算では、左畳み込みを明示します。第 10 章でより重要になります

## 9.2 題材とデータ

学習データの入手と配置は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。この章では 3 つのファイルを使います。

| ファイル | 内容 | 区切り文字 | 文字コード |
|---------|------|----------|-----------|
| `Boston.csv` | 地域ごとの住宅価格。100 件、14 列。CRIME は `high`・`low`・`very_low` のカテゴリ値、NOX と RAD に欠損値 | カンマ | ASCII の範囲だけ（BOM なし） |
| `bike.tsv` | 自転車シェアの日ごとの利用者数（`cnt`）と天気 ID（`weather_id`）。731 件 | **タブ** | ASCII の範囲だけ（BOM なし） |
| `weather.csv` | 天気 ID と天気の名前（晴れ・曇り・雨）。3 件 | カンマ | **Shift_JIS**（UTF-8 としては読めない） |

第 2 章の `loadTable` は「BOM 付きの UTF-8 のカンマ区切り」を読む関数です。`Boston.csv` は BOM が無くても読めるのでそのまま使い、`bike.tsv` と `weather.csv` のために、この章で区切り文字と文字コードを指定できる読み込みを足します。

## 9.3 TODO リストの作成

**TODO リスト**:

- [ ] カテゴリ値をダミー変数にする
  - [ ] 先頭を除いたカテゴリを辞書順に求める
  - [ ] カテゴリごとに 0 と 1 の列を作る
  - [ ] カテゴリに無い値はすべての列を 0 にする
- [ ] 特徴量を標準化する
  - [ ] 平均と標準偏差を訓練データだけから求める
  - [ ] すべて同じ値の列で割り算をあふれさせない
  - [ ] `statistics` の平均・分散と突き合わせる
- [ ] 多項式特徴量を作る
- [ ] 外れ値を検出する
- [ ] 表を結合して特徴量を増やす
  - [ ] タブ区切りを読む
  - [ ] Shift_JIS を読む
  - [ ] 鍵で内部結合する
- [ ] 特徴量の組み合わせごとに決定係数を比べる

置き場は `src/GettingStartedMl/Chapter09.hs` 1 つです。PHP 版が「状態を覚えるもの・値を運ぶもの」をクラスに切り出したところは、Haskell ではレコード型 2 つ（`Standardizer` と `BostonSplit`）になります。**型を作るのに新しいファイルは要らない** ので、章ごとのモジュールに同居させます。

```haskell
data Standardizer = Standardizer
  { standardizerColumns :: [Text]
  , standardizerMeans :: M.Map Text Double
  , standardizerStds :: M.Map Text Double
  }
  deriving (Eq, Show)
```

`deriving (Eq, Show)` の 1 行で、テストの `shouldBe` が値を比べて差分を表示できるようになります。PHP 版が `assertEquals` と `assertSame` を使い分けたところが、Haskell では `Eq` が値の等価性だけを意味するので迷いません。

## 9.4 カテゴリ値をダミー変数にする

### カテゴリを求める

`CRIME` は `high`・`low`・`very_low` の 3 つのカテゴリを取ります。これを 0 と 1 の列に開きます。列は 3 つではなく **2 つ** です。`low` でも `very_low` でもなければ `high` だと分かるので、1 つは要りません（pandas の `get_dummies(drop_first=True)` と同じ）。

```haskell
    it "空欄を除いたカテゴリを辞書順に並べ、先頭を落とす" $
      categories ["low", "high", "", "very_low", "low"] `shouldBe` ["low", "very_low"]
```

```haskell
categories :: [Text] -> [Text]
categories values = drop 1 (sort (nub (filter (not . T.null . T.strip) values)))
```

右から左へ「空欄を落とす → 重複を除く → 並べ替える → 先頭を落とす」と読めます。PHP 版が `array_values(array_unique(array_filter(...)))` と、**キーを詰め直す関数で包まなければならなかった** ところが、Haskell のリストには添字が無いので包む必要がありません。

### 表に列を加える

```haskell
    it "カテゴリごとに 0 と 1 の列を作る" $ do
      let table = expectRight $ loadTable (utf8Csv "CRIME,ZN\nlow,1\nhigh,2\n")
          encoded = expectRight $ encode table categoryColumn (categories ["low", "high"])
      tableColumns encoded `shouldBe` ["ZN", "CRIME_low"]
      map (`cell` "CRIME_low") (tableRows encoded) `shouldBe` ["1", "0"]
```

```haskell
encode :: Table -> Text -> [Text] -> Either String Table
encode table col cats = do
  rows <- traverse encodeRow (tableRows table)
  pure
    Table
      { tableColumns = filter (/= col) (tableColumns table) <> dummyColumns
      , tableRows = rows
      }
 where
  dummyColumns = [col <> "_" <> c | c <- cats]

  encodeRow row = do
    value <- text row col
    let dropped = HM.delete (TE.encodeUtf8 col) row
        added = [(TE.encodeUtf8 name, if c == value then "1" else "0") | (c, name) <- zip cats dummyColumns]
    pure (foldr (uncurry HM.insert) dropped added)
```

**値を文字列（バイト列）のままにしておくのが要点です。** こうすると、第 2 章の `splitFeaturesAndTarget`・`columnMeans`・`fillMissing` をダミー変数の列にもそのまま使えます。数値に変換するのは前処理のいちばん最後です。

ここで 1 つ、第 1 章と同じ落とし穴に近づきます。行（`Row`）は `HashMap ByteString ByteString` なので、列名を入れるときに `TE.encodeUtf8` を忘れると **`OverloadedStrings` が日本語を 1 文字 1 バイトに詰めて壊します**。型は合ってしまうのでコンパイルは通ります。この章の列名は ASCII（`CRIME_low`）なので実際には壊れませんが、`Text` で持って境界でだけ符号化する第 2 章の方針は変えません。

**TODO リスト**:

- [x] カテゴリ値をダミー変数にする

## 9.5 特徴量を標準化する

### 標準化とは

**標準化** は、列ごとに「平均を引いて標準偏差で割る」変換です。単位の違う特徴量（部屋数の 6 と、税率の 300）を同じ物差しに乗せます。

### 平均と標準偏差を覚える

標準化で重要なのは、**平均と標準偏差を訓練データだけから求め、テストデータにも同じ値を使う** ことです。テストデータの平均を使うと、本番では知り得ない情報がモデルに漏れます（リーケージ）。

この「覚えておいて、あとで別のデータに当てる」形をレコード型にします。

```haskell
    it "平均 0・標準偏差 1 になる" $ do
      let std = expectRight $ fitStandardizer (features [1, 2, 3]) ["a"]
          scaled = standardizeAll std (features [1, 2, 3])
          values = [v | row <- scaled, Just v <- [M.lookup "a" row]]
      sum values `shouldSatisfy` \total -> abs total < 1e-12
      M.lookup "a" (standardizerMeans std) `shouldBe` Just 2.0
```

期待値を手で計算して書き写すより、**性質そのものを確かめる** ほうが、あとで実装を変えても壊れません。

`M.lookup` が返すのは `Maybe Double` なので、`shouldBe Just 2.0` と書くと「列が無い」場合と「値が違う」場合が自然に区別されます。PHP 版が `$std->means['a']` と書いて、列が無ければ通知を出しながら `null` を返すところとの違いです。

### すべて同じ値の列

```haskell
    it "すべて同じ値の列は 0 になる" $ do
      let std = expectRight $ fitStandardizer (features [5, 5]) ["a"]
      M.lookup "a" (standardizerStds std) `shouldBe` Just 1.0
      standardize std (M.fromList [("a", 5.0)]) `shouldBe` M.fromList [("a", 0.0)]
```

標準偏差が 0 だと割り算があふれます。Haskell の `Double` は 0 で割っても例外にならず、`0 / 0` は `NaN`、`1 / 0` は `Infinity` になります。**例外にならないぶん、壊れた値がそのまま計算の奥まで流れます。** そこで標準偏差を 1 に置き換え、標準化した値が 0 になるようにしました。ボストンのデータでは、訓練データの CHAS（川に接しているか）がすべて 0 になる分割があり得ます。

### n で割るか n−1 で割るか

標準偏差には 2 つの定義があります。偏差平方和を件数 n で割る **母標準偏差** と、n−1 で割る **標本標準偏差** です。ライブラリと突き合わせる前に、自作がどちらなのかをテストで固定します。

```haskell
    it "自作の標準偏差は statistics の variance（n で割る）と一致する" $ do
      let std = expectRight $ fitStandardizer (features sample) ["a"]
      M.lookup "a" (standardizerStds std) `shouldBe` Just (statisticsStdDevN sample)

    it "statistics の stdDev は n−1 で割るので自作とは一致しない" $ do
      let std = expectRight $ fitStandardizer (features sample) ["a"]
      M.lookup "a" (standardizerStds std) `shouldNotBe` Just (statisticsStdDevN1 sample)
```

**`shouldNotBe` で「そうではない」ほうも書いています。** n で割る実装を n−1 に取り違えたとき、上の行だけでは「値が少し違う」としか分かりませんが、下の行があれば「取り違えた定義」を名指しで示せます。

### statistics の関数名に注意する

`statistics` パッケージを突き合わせの相手にします。

```haskell
statisticsMean :: [Double] -> Double
statisticsMean = Stat.mean . U.fromList

-- | 件数（n）で割る。
statisticsStdDevN :: [Double] -> Double
statisticsStdDevN = sqrt . Stat.variance . U.fromList

-- | 標本標準偏差（n−1 で割る）。
statisticsStdDevN1 :: [Double] -> Double
statisticsStdDevN1 = Stat.stdDev . U.fromList
```

**名前から定義を推測すると間違えます。** `Statistics.Sample` の `variance` は「最尤推定」で **n で割り**、`varianceUnbiased` と `stdDev` は **n−1 で割ります**。`stdDev` は `varianceUnbiased` の平方根なので、`sqrt variance` とは別物です。

この対応関係もテストで固定しました。

```haskell
    it "n と n−1 の比は sqrt((n-1)/n) になる" $ do
      let n = fromIntegral (length sample) :: Double
      abs (statisticsStdDevN sample / statisticsStdDevN1 sample - sqrt ((n - 1) / n)) < 1e-12
        `shouldBe` True
```

**`sqrt . variance` とは 1e-12 の許容どころか、浮動小数点数として完全に一致しました。** 自作は `statistics` と同じ順序で同じ式を計算しているからです。scikit-learn・Scholar・Rubix ML と同じ側（n で割る）で、Tribuo の n−1 とは違います。第 14 章（K-means）で標準化を使うときも、この一致がそのまま効きます。

`statistics` は `Data.Vector.Unboxed` を受け取るので、リストとの間で 1 回詰め替えます。この詰め替えが、**型の付いた世界と、ベクトルを前提にした数値計算の世界との境界** です。PHP 版で Rubix ML の `mixed` を `is_float` で確かめた場所にあたりますが、Haskell では型が合わなければコンパイルが止まるので、確認のコードは要りません。

**TODO リスト**:

- [x] 特徴量を標準化する

## 9.6 多項式特徴量を作る

### 2 乗の項と交互作用の項

線形回帰は直線しか引けませんが、特徴量に `RM^2` や `RM × LSTAT` を加えれば、曲がった関係も表せます。これを **多項式特徴量** と呼びます。

列の組は、scikit-learn の `PolynomialFeatures` と同じ順（重複を許して 2 つ選ぶ）にそろえます。

```haskell
    it "重複を許して 2 つの列を選ぶ" $
      pairsWithReplacement ["x", "y"] `shouldBe` [("x", "x"), ("x", "y"), ("y", "y")]
```

```haskell
pairsWithReplacement :: [Text] -> [(Text, Text)]
pairsWithReplacement cols =
  [(left, right) | (i, left) <- indexed, (j, right) <- indexed, j >= i]
 where
  indexed = zip [0 :: Int ..] cols
```

**リスト内包表記が二重ループと絞り込みをそのまま表します。** PHP 版が `foreach` の中で `array_slice($columns, $i)` を取ったところが、Haskell では「添字の組で絞る」と書けます。`(i, left)` と `(j, right)` の組を取り、`j >= i` だけ残す——数学の書き方に近い形です。

項の名前は scikit-learn の `get_feature_names_out` にそろえます。

```haskell
termName :: (Text, Text) -> Text
termName (left, right)
  | left == right = left <> "^2"
  | otherwise = left <> " " <> right
```

### 展開と選択

```haskell
    it "2 乗の項と交互作用の項を作る" $ do
      let expanded = expectRight $ expand [M.fromList [("x", 2.0), ("y", 3.0)]] ["x", "y"]
      expanded `shouldBe` [M.fromList [("x", 2.0), ("y", 3.0), ("x^2", 4.0), ("x y", 6.0), ("y^2", 9.0)]]
```

`expand` は指定した列と、その 2 乗・交互作用の項だけを持つ特徴量を作ります。`selectColumns` は、そこから使う項だけを選びます。この 2 つを分けておくと、「3 列」「6 列」「9 列」の 3 通りを同じ `expand` の結果から作れます。

どちらも `Either String` を返します。無い列を指定すれば「列がありません: z」で止まり、**その失敗が `traverse` を通って呼び出し元まで伝わります**。PHP 版が例外を投げたところが、Haskell では戻り値の型に現れます。

**TODO リスト**:

- [x] 多項式特徴量を作る

## 9.7 外れ値を検出する

四分位範囲（IQR）から大きく離れた値を外れ値とします。分位数は pandas の既定（`linear`）と同じく、位置が値の間にあれば前後から線形補間します。

```haskell
    it "分位数は前後の値から線形補間する" $ do
      quantile [1, 2, 3, 4] 0.25 `shouldBe` Right 1.75
      quantile [1, 2, 3, 4] 0.5 `shouldBe` Right 2.5

    it "四分位範囲の 1.5 倍から外れた値を外れ値とする" $
      iqrOutliers [1, 2, 3, 4, 100] 1.5 `shouldBe` Right [False, False, False, False, True]
```

```haskell
quantile :: [Double] -> Double -> Either String Double
quantile [] _ = Left "値が 1 件もありません"
quantile values q =
  Right (atLower + (sorted !! upper - atLower) * (position - fromIntegral lower))
 where
  sorted = sort values
  ...
```

**空のリストをパターンで分けています。** PHP 版が `if ($values === [])` と書いて例外を投げたところが、Haskell では関数の定義を 2 行に分けるだけです。`sort` は引数を書き換えず新しいリストを返すので、PHP 版が注意した「`sort()` が引数を並べ替えてしまう」問題そのものがありません。

検出した外れ値は、**訓練データからだけ** 取り除きます。

```haskell
    it "訓練データからだけ外れ値を取り除く" $ do
      let split = ...
          kept = expectRight $ removeTargetOutliers split
      bostonTTrain kept `shouldBe` [1, 2, 3, 4]
      bostonTTest kept `shouldBe` [100]
```

テストデータは「本番で来るデータ」の代わりなので、都合の悪い値を取り除いてしまうと評価の意味がなくなります。この非対称は、実データの節でそのまま決定係数に現れます。

**TODO リスト**:

- [x] 外れ値を検出する

## 9.8 表を結合して特徴量を増やす

### 区切り文字を指定する

第 2 章の `parseTable` はカンマ区切りに決め打ちです。cassava は区切り文字を `DecodeOptions` で変えられるので、この章で選べる読み方を足します。

```haskell
parseTableWith :: Char -> Text -> Either String Table
parseTableWith separator contents =
  case Csv.decodeByNameWith options (BL.fromStrict (TE.encodeUtf8 (T.dropWhile (== '\65279') contents))) of
    ...
 where
  options = Csv.defaultDecodeOptions {Csv.decDelimiter = fromIntegral (ord separator) :: Word8}
```

`decDelimiter` は `Word8`（バイト 1 つ）なので、`Char` から `ord` でバイトに直します。**区切り文字が「文字」ではなく「バイト」である** ことが型に書いてあるので、マルチバイトの区切り文字を渡そうとすればコンパイルで止まります。

BOM は第 2 章と同じく自分で取り除きますが、ここでは文字列（`Text`）として扱っているので **U+FEFF の 1 文字**（`'\65279'`）です。第 2 章がバイト列で扱って `[0xEF, 0xBB, 0xBF]` の 3 バイトを落としたのとは書き方が変わります。同じ BOM でも、バイト列で見るか文字列で見るかで別物になるところは第 1 章でも踏みました。

### Shift_JIS

`weather.csv` は Shift_JIS です。**Haskell には文字コードを変換するライブラリが標準では無いように見えますが、ハンドルに文字コードを設定する仕組みが `base` にあります。**

```haskell
readDecoded :: FilePath -> Encoding -> IO (Either String Text)
readDecoded file enc = do
  encoder <- mkTextEncoding (encodingName enc)
  result <- try (readAll encoder)
  pure (first describe result)
 where
  readAll encoder = withFile file ReadMode $ \handle -> do
    hSetEncoding handle encoder
    T.pack <$> hGetContents' handle
```

`mkTextEncoding` は **iconv の名前をそのまま受け取ります**。`"CP932"` でも `"SHIFT-JIS"` でも通りました。C ライブラリのインストールが要る `text-icu` も、Elixir 版が足した codepagex のようなパッケージも要りません。

```haskell
    it "Shift_JIS の CSV を CP932 として読む" $ do
      withTempFile "weather.csv" Cp932 "weather_id,weather\n1,晴れ\n" $ \file -> do
        table <- loadDelimited file Cp932 ','
        fmap (map (`cell` "weather") . tableRows) table `shouldBe` Right ["晴れ"]
```

テストで Shift_JIS のファイルを用意するときも、同じ `mkTextEncoding` で書き出します。**読む側と書く側で同じ道具を使うので、テストのためだけの変換関数が要りません。**

### 取り違えたときに何が起きるか

シリーズでは言語ごとに違う振る舞いを見てきました。Haskell の実測です。

```haskell
    it "Shift_JIS を UTF-8 として読むと失敗する" $ do
      withTempFile "weather.csv" Cp932 "weather_id,weather\n1,晴れ\n" $ \file -> do
        result <- readDecoded file Utf8
        case result of
          Left err -> err `shouldSatisfy` isInfixOf "cannot decode byte sequence"
          Right value -> expectationFailure ("読めてしまいました: " <> show value)

    it "UTF-8 を CP932 として読むと、正しくは読めない（振る舞いは環境による）" $ do
      -- 取り違えたときに何が起きるかは、同じ Haskell でも iconv の実装で変わる。
      -- Linux（glibc）は不正なバイト列として例外にし、macOS（BSD）は通して
      -- 文字化けした文字列を返す。**どちらにせよ正しくは読めない**ことだけが
      -- 環境によらず言えるので、それをテストにする。
      withTempFile "weather.csv" Utf8 "weather_id,weather\n1,晴れ\n" $ \file -> do
        result <- readDecoded file Cp932
        case result of
          Left err -> err `shouldSatisfy` isInfixOf "cannot decode byte sequence"
          Right value -> do
            value `shouldNotSatisfy` T.isInfixOf "晴れ"
            T.isInfixOf "weather_id" value `shouldBe` True
```

- **Shift_JIS を `UTF-8` として読むと例外になります。** `hGetContents': invalid argument (cannot decode byte sequence starting from 144)` です。Java 版と同じ側で、**バイト位置まで教えてくれます**
- **UTF-8 を `CP932` として読んだときは、環境によって結果が変わりました。** 手元の macOS では例外にならず、UTF-8 の 3 バイトが Shift_JIS の 2 バイト文字として解釈されて黙って文字化けします（改行までが直前の文字に吸い込まれることもありました）。ところが **CI の Linux では例外になります**（`cannot decode byte sequence starting from 140`）

この食い違いは CI が教えてくれました。手元で通ったテストが Linux で落ちたのです。原因は **`mkTextEncoding` が OS の iconv をそのまま使う**ことでした。macOS は BSD の iconv、Linux は glibc の iconv で、**同じ「CP932 として読む」でも不正なバイト列の扱いが違います**。

```haskell
-- 取り違えたときに何が起きるかは、同じ Haskell でも iconv の実装で変わる。
-- Linux（glibc）は不正なバイト列として例外にし、macOS（BSD）は通して
-- 文字化けした文字列を返す。**どちらにせよ正しくは読めない**ことだけが
-- 環境によらず言えるので、それをテストにする。
case result of
  Left err -> err `shouldSatisfy` isInfixOf "cannot decode byte sequence"
  Right value -> do
    value `shouldNotSatisfy` T.isInfixOf "晴れ"
    T.isInfixOf "weather_id" value `shouldBe` True
```

**テストに書ける「確かなこと」が、環境をまたぐと狭くなる**という例です。最初は「例外にならず文字化けする」と書けると思っていましたが、実際に言えるのは「**正しくは読めない**」ことだけでした。文字コードの取り違えを検知したいなら、**言語の既定の振る舞いに頼らず、読む前に自分で確かめる**ほうが確実です

さらに Haskell では、**取り違えたときの振る舞いを名前で選べます**。

| 指定 | Shift_JIS のファイルを読んだ結果 |
|------|--------------------------|
| `UTF-8` | **例外**（`cannot decode byte sequence starting from 144`） |
| `UTF-8//IGNORE` | 読めないバイトを **黙って落とす**（`"1,\n2,\1794\n3,J\n"`） |
| `UTF-8//TRANSLIT` | 読めないバイトを **U+FFFD に置き換える** |

Java 版は例外、Clojure 版は U+FFFD、Elixir 版は不正なバイナリ、PHP 版はどちら向きも静かに通る——シリーズでばらばらだった振る舞いが、**Haskell では 1 つの言語の中で選べます**。既定（接尾辞なし）が例外であることは大事で、**何も考えずに書けば、取り違えは止まります**。

### 結合と集計

結合は `Map` を引くだけです。

```haskell
joinWeather :: Table -> Table -> Either String Table
joinWeather bike weather = do
  keys <- traverse (`text` joinKey) (tableRows weather)
  let byId = M.fromList (zip keys (tableRows weather))
      added = filter (/= joinKey) (tableColumns weather)
  if M.size byId /= length (tableRows weather)
    then Left (T.unpack joinKey <> " が一意ではありません")
    else ...
```

**`M.fromList` は鍵が重複すると静かに後勝ちで上書きします。** 件数を比べて、一意でないことに気付けるようにしました。PHP 版が配列の鍵の上書きで同じ注意をしたところですが、Haskell には **もう 1 つ落とし穴がありません**。PHP では `"1"` という鍵が整数の `1` に化けますが、`Map Text` は文字列のままです。

引けない行は残しません（内部結合）。

```haskell
    it "引けない行は残さない" $ do
      let bike = expectRight $ loadTable (utf8Csv "weather_id,cnt\n1,100\n9,200\n")
          weather = expectRight $ loadTable (utf8Csv "weather_id,weather\n1,晴れ\n")
      fmap (length . tableRows) (joinWeather bike weather) `shouldBe` Right 1
```

集計は天気ごとの平均利用者数を、多い順に並べます。

```haskell
  pure (sortBy (comparing (Down . snd)) means)
```

`Down` で「降順」を型に書きます。比較関数を自分で書かずに済むので、大小を逆にする間違いが起きません。

**TODO リスト**:

- [x] 表を結合して特徴量を増やす

## 9.9 特徴量の効果を測る

### 線形回帰と決定係数

作った特徴量の効果は、線形回帰の **決定係数**（R²）で測ります。先頭に 1 の列を加えた計画行列 X で、正規方程式 XᵀX β = Xᵀt を解きます。

```haskell
linearFit :: [[Double]] -> [Double] -> Either String LinearModel
linearFit [] _ = Left "特徴量が 1 件もありません"
linearFit rows t =
  case LA.linearSolve (transposed LA.<> design) (LA.asColumn (transposed LA.#> LA.vector t)) of
    Nothing -> Left notIndependent
    Just solution -> case LA.toList (LA.flatten solution) of
      (intercept : weights)
        | all sane (intercept : weights) -> Right (LinearModel intercept weights)
      _ -> Left notIndependent
```

第 7 章で使った hmatrix の `linearSolve`（LU 分解）をそのまま使います。**`Maybe` を返すので、解けなかったことが型に現れます。** Elixir 版の `Nx.LinAlg.solve` が例外を投げず `NaN` を返したところとは違い、まず `Nothing` で止まります。それでも値が `NaN` や `Infinity` になっていないかは確かめます。分解が通っても、桁落ちで壊れた値が出ることはあるからです。

```haskell
    it "同じ列を 2 つ渡すと解けない" $
      linearFit [[1, 1], [2, 2], [3, 3]] [3, 5, 7]
        `shouldBe` Left "特徴量の列が互いに独立でないため、正規方程式を解けません"
```

### 特徴量の組を比べる

3 通りを同じ `scoreFeatureSet` で測ります。

```haskell
featureSets :: [(Text, [Text])]
featureSets =
  [ ("元の特徴量", columnsToExpand)
  , ("2 乗の項を追加", columnsToExpand <> squareTerms)
  , ("交互作用の項も追加", expandedColumns columnsToExpand)
  ]
```

`scoreFeatureSet` の中で「展開 → 選択 → 標準化 → 学習 → 評価」を順に行います。すべてが `Either String` を返すので、`do` 記法で 1 本につながります。

```haskell
scoreFeatureSet :: BostonSplit -> [Text] -> [Text] -> Either String (Double, Double)
scoreFeatureSet split cols terms = do
  train <- expand (bostonXTrain split) cols >>= (`selectColumns` terms)
  test <- expand (bostonXTest split) cols >>= (`selectColumns` terms)
  std <- fitStandardizer train terms
  trainRows <- toRows (standardizeAll std train) terms
  testRows <- toRows (standardizeAll std test) terms
  model <- linearFit trainRows (bostonTTrain split)
  trainScore <- rSquared (bostonTTrain split) (linearPredict model trainRows)
  testScore <- rSquared (bostonTTest split) (linearPredict model testRows)
  pure (trainScore, testScore)
```

**`do` 記法の 9 行のどこで失敗しても、同じ `Left` が呼び出し元に返ります。** 途中に `if` も `try` も書いていないのに、失敗が全部つながっています。これが例外を使わない言語で「例外のような」書き方ができる仕組みです。

### 実データで測る

```haskell
main :: IO ()
main = GettingStartedMl.Chapter09.run >>= either putStrLn putStr
```

```text
訓練データ: 70 件, テストデータ: 30 件
特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low
標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00
決定係数:
  元の特徴量（3 列）: 訓練 0.6056, テスト 0.6950
  2 乗の項を追加（6 列）: 訓練 0.7740, テスト 0.8628
  交互作用の項も追加（9 列）: 訓練 0.7953, テスト 0.8213
訓練データの PRICE の外れ値: 8 件
  外れ値を除いて 2 乗の項を追加: 訓練 0.6717, テスト 0.7947
天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
```

- **2 乗の項** を加えると、テストデータの決定係数が 0.6950 から 0.8628 に上がりました
- **交互作用の項** も加えると、訓練データでは上がる（0.7740 → 0.7953）のに、テストデータでは下がりました（0.8628 → 0.8213）。列を増やすと訓練データには合わせやすくなりますが、未知のデータへの当てはまりが良くなるとは限りません
- **外れ値** を除いて学習すると、テストデータの決定係数は 0.7947 に下がりました。テストデータには外れ値が残っているので、外れ値を学ばなかったモデルはそれを当てられません
- 天気ごとの平均利用者数は、分割に関係しないのでどの版でも同じ値です

**決定係数の 8 つの数値は、[Java 版](../java/09-feature-engineering.md)・[Scala 版](../scala/09-feature-engineering.md)・[Clojure 版](../clojure/09-feature-engineering.md)・[Elixir 版](../elixir/09-feature-engineering.md)・[PHP 版](../php/09-feature-engineering.md) の記事とすべて一致しました。** 第 2 章で `java.util.Random` と同じ線形合同法を自作したので、同じ行が訓練データとテストデータに入ります。そのあとの標準化・多項式特徴量・正規方程式まで含めて、4 桁の表示では差が出ませんでした。

一致したことには、もう 1 つ意味があります。連立方程式の解き方は、Java 版・Clojure 版が Tribuo の **コレスキー分解**、Elixir 版が Nx の **LU 分解**、PHP 版が MathPHP の **LU 分解**、そして Haskell 版が第 7 章で自作した **ガウス・ジョルダン法** と、3 種類に分かれています。それでも表示する 4 桁では差が出ません。

Kotlin 版は `kotlin.random.Random(0)` を使うので分け方が違い、別の値になります（[Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md) を参照）。

この出力は、学習データが無ければスキップするテストで固定しています。

```haskell
    it "特徴量エンジニアリングの結果を表示する" $ do
      found <- Dataset.exists "Boston.csv"
      foundWeather <- Dataset.exists "weather.csv"
      unless (found && foundWeather) $ pendingWith "学習データがありません"
      ...
      report contents bike weather
        `shouldBe` Right
          ( unlines
              [ "訓練データ: 70 件, テストデータ: 30 件"
              , ...
              ]
          )
```

**`unlines` でリストとして書けるので、期待する出力が読めます。** PHP 版が `assertStringContainsString` で一部だけを確かめたところが、Haskell では `Either String String` の完全一致で書けました。`report` が文字列を返す純粋関数なので、標準出力を横取りする仕掛けも要りません。

`pendingWith` で飛ばしたテストは、Hspec の結果に「pending」として残ります。

**TODO リスト**:

- [x] 特徴量の組み合わせごとに決定係数を比べる

## 9.10 Notebook による探索と可視化

Haskell 版では Notebook と可視化の節を設けません。標準化の前後の分布、特徴量と価格の関係、外れ値、天気ごとの利用者数のグラフは、[Python 版の第 9 章](../python/09-feature-engineering.md) と [Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md) の「Notebook による探索と可視化」の節を参照してください。

## 9.11 リファクタリング

TODO リストをすべて終えてから、第 5 章・第 6 章で整えた検査（`apps:check:haskell`）をかけました。fourmolu → hlint → `cabal test` → カバレッジの順です。

fourmolu と hlint の指摘は、どちらも「書き方をそろえる」種類のものでした。

- **`sortOn id` は `sort` である** — hlint に言われて直しました。「並べ替えの鍵は自分自身」と書くくらいなら `sort` と書け、という指摘です
- **`foldl'` はもう `Prelude` にある** — GHC 9.10 では `Data.List (foldl')` の import が `-Wunused-imports` で **エラー** になります（`-Werror` なので）。標準ライブラリが動くと、import の行がそのまま壊れます
- **`where` の中の名前が外の関数を隠していた** — `-Wname-shadowing` がエラーにしました。局所的な補助関数に短い名前を付ける癖は、`-Wall -Werror` では通りません

そしてこの章で、**環境のほうの問題を 1 つ見つけました**。

```text
calling linearSolve
Nothing
calling <\>
linearSolveSVDR: code -7
 ** On entry to  DGESV parameter number  1 had an illegal value
```

`[[2,1],[1,3]]` という、特異でもなんでもない 2×2 の連立方程式です。**この環境の hmatrix は LAPACK を正しく呼べていませんでした。** 原因は環境の BLAS/LAPACK と hmatrix で整数の幅が食い違っていたことで、詳しくは [第 7 章](07-linear-regression.md) を参照してください。

ここで大事なのは、**原因が何であれ `Nothing` からは「列が独立でない」としか読めなかった** ことです。`linearSolve` の型は `Maybe (Matrix Double)` で、`Nothing` は本来「特異行列だった」という意味です。実際に起きていたのは環境の壊れですが、**型が用意した語彙にその選択肢はありません**。そのまま使えば「特徴量の列が互いに独立でない」という **間違った診断** になります。

**型は「何が起きうるか」を宣言しますが、「宣言したこと以外は起きない」とは保証しません。** `Maybe` で包まれていても、中身の正しさまでは保証されない——C ライブラリとの境界は、型の付いた世界の端です。

この章では、**hmatrix をやめて第 7 章で自作したガウス・ジョルダン法（`C7.solveLinearSystem`）を使うことにしました**。自作が最終実装になるという [ADR 014](../../../adr/014-haskell-ml-libraries.md) の見立ては、線形代数の層にまで及んだことになります。

カバレッジ（HPC の式のカバレッジ）は `Chapter09` が 95%（933/978）、`Chapter10` が 96%（848/878）でした。到達しないのは、学習データが無い環境や引数が壊れている場合に `Left` を返す分岐です。

### 第 14 章から使う API

第 14 章（K-means）でも標準化を使うので、次の形で公開しています。

| API | 内容 |
|-----|------|
| `fitStandardizer x columns` | 指定した列の平均と標準偏差（n で割る）を覚えた `Standardizer` を返す |
| `standardize std features` | 1 件を標準化する。平均を持たない列はそのまま残す |
| `standardizeAll std x` | 並びを標準化する |
| `toRows x columns` | 特徴量を列の順に並べた数値の行にする |
| `statisticsStdDevN values` | `statistics` の `variance` から求めた標準偏差（突き合わせ用） |

`statistics` も自作も同じ定義（n で割る）なので、第 14 章ではどちらを使っても結果が変わりません。JVM 系の版が「Tribuo ではなく自作を使うこと」を明記しなければならなかったのとは、事情が違います。

## 9.12 まとめ

この章では、特徴量エンジニアリングの 5 つの技法を Haskell の TDD で自作し、標準化を `statistics` と突き合わせました。

| 技法 | 自作したもの | 突き合わせたライブラリ | 落とし穴 |
|------|------------|-------------------|---------|
| ダミー変数 | `categories`・`encode` | — | 行がバイト列なので、列名は境界で符号化する |
| 標準化 | `Standardizer` | `statistics` の `variance`（完全一致） | 分散 0 の列、`variance` と `stdDev` で割る数が違う |
| 多項式特徴量 | `expand`・`termName` | — | 列数が急に増えて過学習しやすい |
| 外れ値の検出 | `quantile`・`iqrOutliers` | — | 検出はできても除くかはデータの意味で決める |
| 表の結合 | `loadDelimited`・`joinWeather` | — | 文字コードの取り違え、`M.fromList` の静かな上書き |

実データでは、2 乗の項を加えるとテストデータの決定係数が 0.6950 から 0.8628 に上がり、交互作用の項を加えると 0.8213 に、外れ値を除くと 0.7947 に下がりました。これらの値は Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版と完全に一致しました。

Haskell 版ならではの学びは 6 つです。

1. **Shift_JIS のために依存が増えない** — `mkTextEncoding` が iconv の名前をそのまま受け取るので、`base` だけで読める。C ライブラリが要る `text-icu` も、Elixir 版の codepagex のようなパッケージも要らなかった。**「標準ライブラリの守備範囲」が、ここでは PHP の mbstring と同じくらい広い**
2. **取り違えたときの振る舞いを名前で選べる** — 既定（`UTF-8`）は例外、`//IGNORE` は黙って落とす、`//TRANSLIT` は U+FFFD。Java 版・Clojure 版・Elixir 版・PHP 版でばらばらだった 4 つの振る舞いが、1 つの言語の中に揃っている。**既定が例外である**ことが効いていて、何も考えずに書けば取り違えは止まる
3. **`statistics` の関数名から定義を推測すると間違える** — `variance` は n で割る最尤推定、`stdDev` は n−1 で割る標本標準偏差の平方根。`sqrt . variance` とだけ完全一致した。scikit-learn・Scholar・Rubix ML と同じ側で、Tribuo の n−1 とは違う
4. **失敗が `do` 記法で 1 本につながる** — 展開・選択・標準化・学習・評価の 9 行に `if` も `try` も無いのに、どこで失敗しても同じ `Left` が返る。例外を使わない言語で「例外のような」書き方ができる仕組み
5. **`Maybe` で包まれていても、中身の正しさは保証されない** — hmatrix の `linearSolve` が壊れた LAPACK に対して `Nothing` を返し、「列が独立でない」という間違った診断になりかけた。**C ライブラリとの境界は、型の付いた世界の端である**
6. **標準ライブラリが動くと import が壊れる** — GHC 9.10 で `foldl'` が `Prelude` に入り、`Data.List (foldl')` が `-Werror` でエラーになった。警告をエラーにする設定は、こういう変化を見逃さないかわりに、必ず手を入れさせる

次の章では、分類のモデルを増やし、ロジスティック回帰と、第 3 章の決定木を組み合わせたランダムフォレストを実装します。**Haskell にはどちらのライブラリも無いので、自作したものがそのまま最終実装になります。**

Haskell 版のほかの章は [Haskell 版のトップ](index.md) から辿れます。
