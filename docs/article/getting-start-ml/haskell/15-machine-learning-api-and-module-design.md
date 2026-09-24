---
type: Article
title: "第 15 章: 機械学習 API とモジュール設計"
description: "第 7・8 章の学習済みモデルを Scotty と aeson で予測 API として公開し、module で層を分ける。置き場の約束を型クラスではなく関数を詰めたレコードで書いた理由、IO を境界に閉じ込める構造がそのまま層の分離になること、失敗を直和型で表すとステータスコードへの変換が網羅的なパターンマッチになることを扱い、Haskell 版 15 章を締めくくる。"
tags: [article,getting-start-ml,haskell]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 15 章: 機械学習 API とモジュール設計

## 15.1 はじめに

シリーズの最後の章です。ここまでに作ったモデルは、テストと `cabal repl` の中でしか動きませんでした。この章では、第 7 章の線形回帰（映画の興行収入）と第 8 章のパイプライン（乗客の生存）を **HTTP の予測 API** として公開します。

題材は [Java 版の第 15 章](../java/15-machine-learning-api-and-module-design.md)・[Clojure 版](../clojure/15-machine-learning-api-and-module-design.md)・[Elixir 版](../elixir/15-machine-learning-api-and-module-design.md)・[PHP 版](../php/15-machine-learning-api-and-module-design.md) と同じで、3 つのエンドポイントを作ります。

| メソッド | パス | 役割 |
|---------|------|------|
| `GET` | `/health` | モデルを読み込めるかどうかを返す |
| `POST` | `/cinema/sales` | 映画の特徴量から興行収入を予測する |
| `POST` | `/survived` | 乗客の特徴量から生存を予測する |

Haskell 版では **Scotty 0.30 と aeson** を使います（[ADR 014](../../../adr/014-haskell-ml-libraries.md)）。Servant のような型で経路を記述するフレームワークは使いません。この章で見せたいのは層の分け方であって、型レベルの経路記述ではないからです。

この章で Haskell らしいのは次の 4 点です。

1. **約束の書き方が 2 つある** — PHP 版は interface、Elixir 版は behaviour、Clojure 版は protocol と、どの言語も選択肢は 1 つでした。Haskell には **型クラス** と **関数を詰めたレコード** があり、どちらを選ぶかで書き味が変わります。この章ではレコードを選び、その理由を 15.2 節で書きます
2. **`IO` を境界に閉じ込める構造が、そのまま層の分離になる** — 第 1 章から「データの読み込みだけが `IO` で、前処理も学習も純粋関数」という構造を保ってきました。API でも、`IO` が出てくるのはモデルを読み込むところだけです
3. **失敗が直和型なので、ステータスコードへの変換をコンパイラが確かめる** — PHP 版は `catch` の節、Elixir 版は `rescue` の節を並べていました。どちらも節を書き漏らしてもコンパイルは通ります。Haskell では `case` の節が足りなければ `-Wall -Werror` が止めます
4. **aeson の `Value` はオブジェクトと配列を取り違えようがない** — PHP 版が `array_is_list` で配列かどうかを見分けていた判定が、パターン 1 つで済みます

## 15.2 層を分ける

### module と依存の向き

`src/GettingStartedMl/Chapter15/` に、層ごとの module を置きます。

| 層 | module | 役割 |
|----|--------|------|
| プレゼンテーション | `Chapter15.Api`・`Chapter15.Validation` | HTTP の要求を読み、検証し、応答を組み立てる |
| アプリケーション | `Chapter15.Service` | 置き場からモデルを読んで予測する。HTTP を知らない |
| ドメイン | `Chapter15.Domain`・`Chapter15.Store` | 予測の入力・出力と、モデル・置き場の約束 |
| インフラ | `Chapter15.FileStore` | ファイルへの保存と読み込み |
| 組み立て | `Chapter15` | 学習・保存と、サーバーの起動 |

依存は外から内へ一方向です。`Api` は `Service` を知り、`Service` は `ModelStore` という **レコード** だけを知ります。`FileStore` がそのレコードを作り、第 7・8 章のモデルを読み込みます。第 7 章の `LinearModel` も第 8 章の `FittedPipeline` も、この章のために 1 行も書き換えていません。**既存の章に手を入れずに済むのがアダプターの役目** です。

### 置き場の約束を、型クラスで書くかレコードで書くか

Haskell には「約束」の書き方が 2 つあります。まず型クラスで書いてみます。

```haskell
class ModelStore s where
  loadSalesModel :: s -> IO (Either LoadError SalesModel)
  loadSurvivalModel :: s -> IO (Either LoadError SurvivalModel)
```

これは Java 版の `interface`、PHP 版の `interface`、Clojure 版の `defprotocol` にいちばん近い見た目です。Elixir 版の behaviour が「モジュールに対する約束」なので `{モジュール, 状態}` の組を持ち回る必要があったのに比べると、`s` という型変数に状態を持たせられるぶん素直です。

ところが、**テストを書く段になって型クラスは不便になりました**。約束のテストは「モデルを持っている置き場」と「持っていない置き場」の両方に同じものを走らせたい。型クラスでは **1 つの型に 1 つのインスタンス** しか書けないので、この 2 つは別の型になります。

```haskell
-- 偽物その 1: モデルを持っている
data FakeReady = FakeReady

instance ModelStore FakeReady where
  loadSalesModel _ = pure (Right (SalesModel (Right . (* 2))))

-- 偽物その 2: モデルを持っていない。FakeReady と同じ型にはできないので
-- 「同じ偽物を引数で作り分ける」ことができず、型をもう 1 つ作ることになる。
data FakeEmpty = FakeEmpty

instance ModelStore FakeEmpty where
  loadSalesModel _ = pure (Left (ModelNotFound "cinema"))
```

使い捨ての module に書いて実際にコンパイルし、この形になることを確かめました。偽物の振る舞いを 1 つ増やすたびに、型と `instance` が 1 組ずつ増えます。さらに約束のテストそのものも `(ModelStore s) => String -> IO s -> IO s -> Spec` という多相な関数になり、呼ぶ側で型が決まらず注釈が要るようになります。

レコードなら、引数 1 つで作り分けられます。

```haskell
data StoreRecord = StoreRecord {loadSales :: IO (Either LoadError SalesModel)}

-- 引数 1 つで「持っている偽物」と「持っていない偽物」を作り分けられる。
fake :: Bool -> StoreRecord
fake ready =
  StoreRecord
    { loadSales =
        pure (if ready then Right (SalesModel (Right . (* 2))) else Left (ModelNotFound "cinema"))
    }
```

**選んだのはレコードです。**

```haskell
data ModelStore = ModelStore
  { loadSalesModel :: IO (Either LoadError SalesModel)
  , loadSurvivalModel :: IO (Either LoadError SurvivalModel)
  }
```

理由は 2 つです。

1. **置き場は「型」ではなく「値」でよい。** `fileStore "model"` が返すのはただのレコードで、ディレクトリの名前はクロージャが閉じ込めます。型クラス版の `s` という型変数は、上の層に「どの置き場の型か」を伝播させます。`Service` の関数はすべて `(ModelStore s) => s -> ...` になり、`Api` も `Chapter15` も同じ制約を持ち回ることになる。レコードなら `ModelStore -> ...` の 1 語で止まります
2. **偽物を引数で作り分けられる。** 上に書いたとおりです。Clojure 版の `reify`、PHP 版の無名クラスに近い書き味になります

型クラスが向くのは「型ごとに答えが決まっていて、実装を 1 つに固定したい」場面です。第 10 章でモデルを型クラス `Classifier` でそろえたのはその例でした。**置き場は逆で、同じ形の値をいくつも作って差し替えたい。** 同じ言語の同じ章のあいだでも、抽象の道具は使い分けになります。

モデルそのものは `newtype` で包むだけにしました。

```haskell
newtype SalesModel = SalesModel {predictSalesWith :: Movie -> Either String Double}
newtype SurvivalModel = SurvivalModel {predictSurvivalWith :: Passenger -> Either String Bool}
```

Elixir 版は「モデルは特徴量を受け取って予測を返す関数でよい」として無名関数をそのまま渡していました。Haskell の関数も値なので同じことができますが、裸の関数だと引数と戻り値の型が同じ別の関数と取り違えられます。`newtype` は実行時の費用がかからないので、包むのはただの名札です。PHP 版が `interface SalesModel` を書いたところが、`newtype` の 1 行になりました。

### レコードが保証しないこと

レコードが決めるのは **フィールドの名前と型** だけです。「読み込めなければ `ModelNotFound` を返す」は書けません。`IO (Either LoadError SalesModel)` という型は「`Left` を返しうる」ことしか言わず、どういうときにどの `LoadError` を返すかまでは縛れないからです。

この取り決めは、15.5 節の **約束のテスト** で守ります。型と約束のテストの二段構えになるところは、PHP 版（interface ＋ 約束のテスト）・Elixir 版（behaviour ＋ 約束のテスト）とまったく同じ形になりました。**型の道具がいちばん強い言語でも、型で書けることの限界はそう変わりません。**

## 15.3 TODO リストの作成

- [ ] 要求の JSON を読み、型が合わなければ弾く
- [ ] 必須・0 以上・選択肢の検証をし、理由を並べて返す
- [ ] 映画・乗客の特徴量をレコードで表す
- [ ] 置き場の約束をレコードと約束のテストで表す
- [ ] 第 7 章の線形回帰と第 8 章のパイプラインをファイルに保存・読み込みする
- [ ] ファイルが無ければ `ModelNotFound`、読めなければ `ModelUnreadable` を返す
- [ ] サービスが置き場からモデルを読んで予測し、ヘルスチェックを返す
- [ ] `POST /cinema/sales`・`POST /survived` が予測を JSON で返す
- [ ] 入力が不正なら 422、モデルが無ければ 503、それ以外は 500
- [ ] 知らないパスは 404、許していないメソッドは 405
- [ ] 第 7・8 章と同じ条件で学習し、Scotty で API を起動する

## 15.4 要求を読んで検証する

### aeson の Value はオブジェクトと配列を取り違えようがない

aeson は JSON を `Value` という直和型にします。`Object`・`Array`・`String`・`Number`・`Bool`・`Null` の 6 つです。

```haskell
readJson :: M.Map Text FieldType -> BL.ByteString -> Either [Text] A.Object
readJson types body = case A.decode body of
  Just (A.Object fields) | all typed (KM.toList fields) -> Right fields
  _ -> Left [invalidJson]
 where
  typed (key, value) = matches (M.lookup (K.toText key) types) value

  -- null と、表に無い列（型が Nothing）は問わない。
  matches _ A.Null = True
  matches Nothing _ = True
  matches (Just NumberField) (A.Number _) = True
  matches (Just IntegerField) (A.Number n) = isInteger n
  matches (Just StringField) (A.String _) = True
  matches _ _ = False
```

`Just (A.Object fields)` というパターン 1 つで、「読めた」「オブジェクトだった」の両方が言えます。PHP 版はここで苦労していました。`json_decode(..., true)` は JSON のオブジェクトも配列も PHP の配列にするので、`array_is_list` で添字が連番かどうかを見て弾く必要があり、しかも「数字だけの文字列の鍵が整数に変わる」という第 3 章の癖まで絡んでいました。**型が値の形を保つ言語では、その判定そのものが要りません。**

`@FromJSON@` のインスタンスを書けば、読み込みと同時に型を確かめられます。それを使わなかったのは、この API が「型が違えば 422、値が範囲の外でも 422 で理由を並べる」という二段の振る舞いを求めるからです。`FromJSON` の失敗は文字列 1 本になるので、理由を並べるところが書けません。**型の道具があっても、仕様が求める粒度に合わなければ使わない** という判断になりました。

`number` と `integer` を分けているのは、`{"original": 1.5}` を弾きたいからです。`Data.Scientific` の `isInteger` で見ます。JSON の `200` が整数として届いても `NumberField` は通る、という分け方は Elixir 版・PHP 版と同じで、理由も同じでした。

### 理由を集めて、無ければ値を作る

検証の規則は、問題が無ければ `Nothing` を、あれば理由を返す関数にします。

```haskell
required :: Text -> Maybe a -> Maybe Text
required name Nothing = Just (name <> " は必須です")
required _ (Just _) = Nothing

notNegative :: Text -> Maybe Double -> Maybe Text
notNegative name (Just value) | value < 0.0 = Just (name <> " は 0 以上にしてください")
notNegative _ _ = Nothing

oneOf :: Text -> Maybe Text -> [Text] -> Maybe Text
oneOf _ Nothing _ = Nothing
oneOf name (Just value) allowed
  | value `elem` allowed = Nothing
  | otherwise = Just (name <> " は " <> T.intercalate "、" allowed <> " のどれかにしてください")
```

集めるのは 1 つの関数です。

```haskell
validate :: [Maybe Text] -> a -> Either [Text] a
validate reasons value = case catMaybes reasons of
  [] -> Right value
  errors -> Left errors
```

PHP 版と Elixir 版は、値を作る処理をクロージャで受け取っていました。「理由があるときに値を作らせない」ためです。Haskell では **遅延評価のおかげでクロージャが要りません**。`value` は `Right` を返すときにしか評価されないので、第 2 引数にそのまま書けます。

```haskell
movie :: A.Object -> Either [Text] Movie
movie fields =
  validate
    [ required "sns1" sns1
    , required "sns2" sns2
    , required "actor" actor
    , required "original" original
    , notNegative "sns1" sns1
    , notNegative "sns2" sns2
    , notNegative "actor" actor
    , oneOf "original" (showInt <$> original) ["0", "1"]
    ]
    Movie
      { movieSns1 = orZero sns1
      , movieSns2 = orZero sns2
      , movieActor = orZero actor
      , movieOriginal = fromMaybe 0 original
      }
 where
  sns1 = number fields "sns1"
  sns2 = number fields "sns2"
  actor = number fields "actor"
  original = integer fields "original"
  orZero = fromMaybe 0.0
```

`fromMaybe 0` の既定値は、理由があるときには読まれません。**「使われないことを型で保証する」のではなく「評価されないことを評価戦略が保証する」** ので、値を作る側は全域関数のまま書けます。

テストは理由の並びまで確かめます。

```haskell
it "負の数と選択肢の外の値は理由を並べて返す" $ do
  request <- expectRight (readJson movieTypes (json "{\"sns1\": -1, \"sns2\": 2000, \"actor\": 300, \"original\": 2}"))
  movie request
    `shouldBe` Left
      [ "sns1 は 0 以上にしてください"
      , "original は 0、1 のどれかにしてください"
      ]
```

## 15.5 置き場の約束をテストで書く

### 約束のテストを本物と偽物の両方に走らせる

レコードが書けない取り決めを、テストに書きます。

```haskell
storeContract :: String -> IO ModelStore -> IO ModelStore -> Spec
storeContract label withModels withoutModels = describe ("置き場の約束: " <> label) $ do
  it "興行収入のモデルを読み込むと、映画から数値を返す" $ do
    store <- withModels
    model <- expectRight =<< loadSalesModel store
    value <- expectRight (predictSalesWith model sampleMovie)
    value `shouldSatisfy` (> 0.0)

  it "同じ入力を二度読み込んでも同じ答えを返す" $ do
    store <- withModels
    first <- expectRight =<< loadSalesModel store
    second <- expectRight =<< loadSalesModel store
    predictSalesWith first sampleMovie `shouldBe` predictSalesWith second sampleMovie

  it "興行収入のモデルが無ければ ModelNotFound を返す" $ do
    store <- withoutModels
    result <- loadSalesModel store
    void result `shouldBe` Left (ModelNotFound salesModelName)
```

同じものを本物と偽物の両方に走らせます。

```haskell
storeContract
  "ファイルの置き場"
  (withFileStore "contract-real" (\(store, fill) -> fill >> pure store))
  (withFileStore "contract-empty" (pure . fst))
storeContract "偽の置き場" fakeStore (pure emptyStore)
```

`void result` と書いているのは、`SalesModel` が関数を包んだ `newtype` で `Eq` を持たないからです。中身を捨てて `Either LoadError ()` にしてから比べます。**「等しさを比べられない値を型に持っている」ことが、テストの書き方にそのまま出ます。**

### 偽物はレコードのリテラルで書ける

偽物は、レコードのフィールドに関数を詰めるだけです。

```haskell
emptyStore :: ModelStore
emptyStore =
  ModelStore
    { loadSalesModel = pure (Left (ModelNotFound salesModelName))
    , loadSurvivalModel = pure (Left (ModelNotFound survivalModelName))
    }
```

予測のときに必ず失敗する偽物も、その場で書けます。500 を出すために使います。

```haskell
brokenStore :: ModelStore
brokenStore =
  ModelStore
    { loadSalesModel = pure (Right (SalesModel (const (Left "係数がありません: SNS1"))))
    , loadSurvivalModel = pure (Right (SurvivalModel (const (Left "特徴量がありません: Age"))))
    }
```

Elixir 版は偽物もモジュールになり、`defmodule` が 3 つ増えました。型クラス版の Haskell なら `data` と `instance` が 3 組です。レコードなら定義 3 つで済みます。

ただし、**「予期しない失敗」のすべてを偽物で作るのは避けました**。壊れたモデルのファイルを読むテストは、偽物ではなく本物の `FileStore` にゴミを書いて確かめます。

```haskell
it "モデルとして読めなければ ModelUnreadable を返す" $
  withFileStore "broken" $ \(store, _) -> do
    let dir = "dist-newstyle" </> "chapter15-test" </> "broken"
    BL.writeFile (modelFile dir salesModelName) garbage
    BL.writeFile (modelFile dir survivalModelName) garbage
    salesResult <- loadSalesModel store
    void salesResult `shouldBe` Left (ModelUnreadable salesModelName)
```

`garbage` をバイト列の数で書いているのは、第 1 章で踏んだ落とし穴を避けるためです。

```haskell
garbage :: BL.ByteString
garbage = BL.pack [0, 1, 2, 3]
```

`OverloadedStrings` のもとで `ByteString` のリテラルに日本語を書くと、1 文字が 1 バイトに詰められて化けます。型は `ByteString` で合っているので型検査では捕まりません。**15 章のいちばん最後まで、この落とし穴は生きていました。**

## 15.6 ドメインとサービス

### モデルが無いことを直和型で表す

PHP 版と Elixir 版は、モデルが無いことを専用の例外で表していました。Haskell では直和型です。

```haskell
data LoadError
  = -- | モデルがまだ保存されていない
    ModelNotFound Text
  | -- | ファイルはあるが、モデルとして読めない
    ModelUnreadable Text
  deriving (Eq, Show)

loadErrorMessage :: LoadError -> Text
loadErrorMessage (ModelNotFound name) = "学習済みモデル " <> name <> " が見つかりません"
loadErrorMessage (ModelUnreadable name) = "学習済みモデル " <> name <> " を読み込めません"
```

説明にファイルの道を入れないのは、503 の応答としてそのまま外に出るからです。PHP 版・Elixir 版と同じ配慮です。

`ModelNotFound` と `ModelUnreadable` を分けたのは、**503 にすべきものと 500 にすべきものが違う** からです。前者は「まだ学習していない」という一時的な状態で、後者は置き場の異常です。例外で表す版では、この区別のために例外クラスをもう 1 つ増やすことになります。直和型なら節を 1 つ増やすだけで、しかも 15.8 節で見るとおり、増やした瞬間に変換側のコンパイルが止まります。

### 既存のモデルをアダプターで約束に合わせる

```haskell
linearSalesModel :: Chapter07.LinearModel -> SalesModel
linearSalesModel model = SalesModel $ \m ->
  Chapter07.predictOne
    model
    ( M.fromList
        [ ("SNS1", movieSns1 m)
        , ("SNS2", movieSns2 m)
        , ("actor", movieActor m)
        , ("original", fromIntegral (movieOriginal m))
        ]
    )
```

乗客のほうは、第 8 章のパイプラインが読む CSV と同じ「セルの文字列の行」に戻します。

```haskell
passengerRow :: Passenger -> M.Map Text Text
passengerRow p =
  M.fromList
    [ ("Pclass", T.pack (show (passengerPclass p)))
    , ("Sex", passengerSex p)
    , ("Age", maybe "" (T.pack . show) (passengerAge p))
    , ("SibSp", T.pack (show (passengerSibSp p)))
    , ("Parch", T.pack (show (passengerParch p)))
    , ("Fare", T.pack (show (passengerFare p)))
    , ("Embarked", fromMaybe "" (passengerEmbarked p))
    ]
```

**分からない値は空欄にします。** 第 8 章のパイプラインが、学習のときに求めた中央値・最頻値で補完してくれます。`Maybe Double` の `Nothing` が空文字列になり、そこから先は第 8 章の `GroupMedianImputer` の仕事です。`Maybe` で表した欠損が、そのまま CSV の空欄に戻るところが気持ちよく決まりました。

### サービスは HTTP を知らない

```haskell
data PredictError
  = LoadFailed LoadError
  | PredictFailed String
  deriving (Eq, Show)

predictSales :: ModelStore -> Movie -> IO (Either PredictError Double)
predictSales store movie = run (`predictSalesWith` movie) (loadSalesModel store)

run :: (m -> Either String a) -> IO (Either LoadError m) -> IO (Either PredictError a)
run apply load = do
  loaded <- load
  pure $ case loaded of
    Left err -> Left (LoadFailed err)
    Right model -> either (Left . PredictFailed) Right (apply model)
```

`run` が **この章でいちばん大事な 5 行** です。`IO` が出てくるのは `load` だけで、`apply` は純粋関数です。読み込んだあとの計算は一切 `IO` に触れません。第 1 章から「データの読み込みだけが `IO`」という構造を保ってきたので、API でも同じ形になりました。**層の分離のために新しい規律を持ち込む必要がなかった** ということです。

ヘルスチェックはリストで返します。

```haskell
health :: ModelStore -> IO [(Text, Bool)]
health store = do
  sales <- loadSalesModel store
  survival <- loadSurvivalModel store
  pure [(salesModelName, isReady sales), (survivalModelName, isReady survival)]
 where
  isReady = either (const False) (const True)
```

`Data.Map` にしないのは、第 1 章から変わらない理由です。**`Map` はキーの順で並ぶ** ので、表示したい順を保ちたいところはリストにします。もっとも、応答の JSON では aeson がキーを並べ替えるので、結果として `cinema`・`survived` の順になります（15.9 節の実際の出力を見てください）。

## 15.7 モデルをファイルに保存する

第 8 章で、学習済みのパイプラインが `Data.Binary` の往復で戻ることを確かめてあります。第 7 章の線形回帰のモデルも切片とリストなので、同じやり方で保存できます。

```haskell
saveSalesModel :: FilePath -> Chapter07.LinearModel -> IO ()
saveSalesModel dir model = do
  let file = modelFile dir salesModelName
  createDirectoryIfMissing True (takeDirectory file)
  BL.writeFile
    file
    ( encode
        ( salesFormatVersion
        , Chapter07.modelIntercept model
        , map T.unpack (Chapter07.modelColumns model)
        , Chapter07.modelCoefficients model
        )
    )
```

列名を `String` に直しているのは、**`binary` が `text` に依存していないので `Text` の `Binary` の実装が無い** からです。第 8 章で `FittedPipeline` を保存したときと同じ扱いです。

読み込みは、`FileStore` が `ModelStore` のレコードを組み立てるところで使います。

```haskell
fileStore :: FilePath -> ModelStore
fileStore dir =
  ModelStore
    { loadSalesModel = fmap linearSalesModel <$> readModel dir salesModelName decodeSales
    , loadSurvivalModel =
        fmap pipelineSurvivalModel <$> readModel dir survivalModelName decodeSurvival
    }

readModel :: FilePath -> Text -> (BL.ByteString -> Either String a) -> IO (Either LoadError a)
readModel dir name decoder = do
  let file = modelFile dir name
  found <- doesFileExist file
  if not found
    then pure (Left (ModelNotFound name))
    else do
      contents <- BL.readFile file
      pure (either (const (Left (ModelUnreadable name))) Right (decoder contents))
```

`fmap linearSalesModel <$> ...` の `fmap` が 2 つ重なっているのは、`IO` の中の `Either` の中を触るからです。`<$>` が `IO` の皮を、`fmap` が `Either` の皮をはがします。慣れないうちは読みにくい行ですが、**「失敗しうる計算を `IO` に包んだ」という形がそのまま書かれている** とも言えます。

保存の関数を `ModelStore` に入れていないのは、PHP 版・Elixir 版と同じ判断です。読み込みは API が使いますが、保存は学習のときにしか使いません。**API から見える約束を小さく保ちます。**

## 15.8 ハンドラーは純粋な振り分けと IO の境界

### 経路の振り分けを純粋関数にする

Scotty の `get`・`post` を使わず、メソッドとパスを直和型に写します。

```haskell
data Route
  = Health
  | PredictSalesRoute
  | PredictSurvivalRoute
  | MethodNotAllowed Text
  | Unknown
  deriving (Eq, Show)

route :: Text -> Text -> Route
route method path = case path of
  "/health" -> allow "GET" Health
  "/cinema/sales" -> allow "POST" PredictSalesRoute
  "/survived" -> allow "POST" PredictSurvivalRoute
  _ -> Unknown
 where
  allow expected found = if method == expected then found else MethodNotAllowed expected
```

`route` は純粋関数なので、テストは値を比べるだけです。

```haskell
it "知っているパスに違うメソッドなら許すメソッドを添える" $ do
  route "POST" "/health" `shouldBe` MethodNotAllowed "GET"
  route "GET" "/cinema/sales" `shouldBe` MethodNotAllowed "POST"
```

Scotty の `get`・`post` に任せると、404 と 405 の区別が自分の手から離れます。405 で `Allow` ヘッダーを返す仕様を持つ以上、**どのパスがどのメソッドを許すかは自分で持つほうが素直** でした。Elixir 版が `Plug.Router` を使わず `call/2` の関数節で分けたのと同じ判断です。

### 失敗をステータスコードに変えるのは網羅的なパターンマッチ

```haskell
predictFailure :: PredictError -> Response
predictFailure (LoadFailed err@(ModelNotFound _)) = detail 503 (loadErrorMessage err)
predictFailure (LoadFailed (ModelUnreadable _)) = failed
predictFailure (PredictFailed _) = failed

failed :: Response
failed = detail 500 "予測できませんでした"
```

**ここが PHP 版・Elixir 版といちばん違うところです。** PHP 版は `catch (ValidationException)`・`catch (ModelNotFoundException)`・`catch (Throwable)` の節を並べ、Elixir 版は `rescue` で受けていました。どちらも **節を書き漏らしてもコンパイルは通ります**。漏れたぶんは `Throwable` や既定の節に吸い込まれ、本来 503 にすべきものが 500 になっても気づきません。

Haskell では `PredictError` に節を増やせば、`predictFailure` の節が足りないとコンパイルが止まります。`-Wall -Werror` が `-Wincomplete-patterns` を含むからです。**「失敗の種類を増やしたら変換も見直す」が、規律ではなくコンパイルエラーになります。**

`failed` を共有しているのは、**例外のメッセージをそのまま外に出さない** ためです。`PredictFailed` が持つ文字列（`"特徴量がありません: Age"` など）は内部の事情なので、500 の本文には入れません。

### ハンドラーは Response という値を返す

```haskell
handle :: ModelStore -> Text -> Text -> BL.ByteString -> IO Response
handle store method path body = case route method path of
  Health -> do
    models <- health store
    pure $
      jsonResponse
        200
        []
        ( A.object
            [ "status" A..= (if all snd models then "ok" else "degraded" :: Text)
            , "models" A..= A.object [K.fromText name A..= ready | (name, ready) <- models]
            ]
        )
  PredictSalesRoute ->
    predictWith body movieTypes movie (predictSales store) (\value -> A.object ["sales" A..= value])
  ...
```

本文を読んで検証し、正しければ予測するところは 1 つの関数にまとめます。

```haskell
predictWith body types check predict toBody =
  case readJson types body >>= check of
    Left reasons -> pure (unprocessable reasons)
    Right input -> either predictFailure (jsonResponse 200 [] . toBody) <$> predict input
```

`readJson types body >>= check` の 1 行で、「JSON が読めなかった」と「検証に落ちた」の両方が `Left [Text]` にまとまります。`Either` のモナドがそのまま使えるので、Elixir 版の `with` に当たるものを書かずに済みました。

`Response` は Scotty にも WAI にも依存しないただのレコードです。

```haskell
data Response = Response
  { responseStatus :: Int
  , responseHeaders :: [(Text, Text)]
  , responseBody :: BL.ByteString
  }
  deriving (Eq, Show)
```

Scotty はいちばん外側で `handle` を呼ぶだけの薄い層になります。

```haskell
application :: ModelStore -> Scotty.ScottyM ()
application store =
  Scotty.matchAny (Scotty.function (const (Just []))) $ do
    request <- Scotty.request
    body <- Scotty.body
    response <-
      liftIO $
        handle
          store
          (TE.decodeUtf8Lenient (Wai.requestMethod request))
          (TE.decodeUtf8Lenient (Wai.rawPathInfo request))
          body
    Scotty.status (statusOf (responseStatus response))
    mapM_
      (\(name, value) -> Scotty.setHeader (TL.fromStrict name) (TL.fromStrict value))
      (responseHeaders response)
    Scotty.raw (responseBody response)
```

`Scotty.function (const (Just []))` は「どのパスにも当たる」経路です。振り分けは `route` が済ませているので、Scotty には「全部よこせ」とだけ言います。

### サーバーを起動しない統合テスト

`handle` はただの関数なので、テストは呼ぶだけです。

```haskell
call :: ModelStore -> Text -> Text -> BL.ByteString -> IO (Int, A.Value)
call store method path body = do
  response <- handle store method path body
  lookup "Content-Type" (responseHeaders response)
    `shouldBe` Just "application/json; charset=utf-8"
  decoded <- expectRight (A.eitherDecode (responseBody response))
  pure (responseStatus response, decoded)
```

置き場は引数で差し替えます。

```haskell
it "モデルが無ければ 503" $ do
  (status, body) <-
    call emptyStore "POST" "/cinema/sales" "{\"sns1\": 200, \"sns2\": 500, \"actor\": 3000, \"original\": 1}"
  status `shouldBe` 503
  body `shouldBe` A.object ["detail" A..= ("学習済みモデル cinema が見つかりません" :: Text)]
```

応答の本文を `A.Value` に戻してから比べているのは、**aeson がキーを並べ替えて書き出す** からです。文字列で比べるとキーの順に縛られますが、`A.Value` の `Object` は `KeyMap` なので順を問いません。「比べたいのは中身であって並びではない」を型で表せた形です。

## 15.9 実データで学習したモデルで動かす

### 学習と保存

第 7・8 章と同じ条件（`testSize` 0.2、シード 0、深さ 5、`Balanced` の重み）で学習します。

```haskell
trainModels
  :: BL.ByteString
  -> BL.ByteString
  -> Either String (Chapter07.LinearModel, Chapter08.FittedPipeline)
trainModels cinema survived = do
  cinemaSplit <- Chapter07.prepareCinema cinema testSize seed
  target <- Chapter07.targetValues (tTrain cinemaSplit)
  linear <- Chapter07.fit (xTrain cinemaSplit) target Chapter07.featureColumns
  survivedSplit <- Chapter08.prepareSurvived survived testSize seed
  labels <- Chapter08.labelValues (tTrain survivedSplit)
  pipeline <-
    Chapter08.fitPipeline
      (Chapter08.buildPipeline maxDepth Chapter08.Balanced)
      (Chapter08.Frame Chapter08.featureColumns (xTrain survivedSplit))
      labels
  pure (linear, pipeline)
```

**学習そのものは純粋関数です。** `IO` は「ファイルを読む」「ファイルに書く」の外側だけにあります。

```haskell
trainAndSaveModels :: FilePath -> FilePath -> IO (Either String ())
trainAndSaveModels dataDir dir = do
  cinema <- BL.readFile (dataDir </> "cinema.csv")
  survived <- BL.readFile (dataDir </> "Survived.csv")
  case trainModels cinema survived of
    Left err -> pure (Left err)
    Right (linear, pipeline) -> do
      saveSalesModel dir linear
      saveSurvivalModel dir pipeline
      pure (Right ())
```

既存の章の公開された関数を呼んでいるだけで、第 7 章にも第 8 章にも手を入れていません。

### 予測値はほかの言語版と一致するか

実データで学習したモデルをつなぐテストは、既存の章と同じく `Dataset.exists` が偽なら `pendingWith` で飛ばします。期待値は、まず Java 版の値を書いて走らせ、実際に測った値と突き合わせました。

**結果は「ほぼ一致、完全一致ではない」でした。**

| 言語版 | 予測値（`sns1=200, sns2=500, actor=3000, original=1`） |
|-------|--------------------------------------------|
| Java・Scala・Clojure | 7730.457421687023 |
| **Haskell** | **7730.457421687022** |
| PHP | 7730.457421687019 |
| Elixir | 7730.457421687016 |

差は Java 版に対して約 1e-12 で、**第 3 波の 3 つの版のなかでは Haskell 版がいちばん JVM の言語版に近い** 結果になりました。

原因は分割ではありません。**どの行が訓練データに入るかは完全に一致しています。** 第 2 章で `java.util.Random` と同じ 48 ビットの線形合同法を自作し、`shuffle [0..9] 0` の並びが Java 版・Elixir 版・PHP 版と一致することを確かめてあるからです。係数を求める手順（正規方程式 `(Xᵀ X) w = Xᵀ t` を解く）も同じです。

違うのは **解く実装** だけです。Haskell 版の `Chapter07.fit` は自作のガウス・ジョルダン法（部分ピボット選択）で解いています。PHP 版は MathPHP の LU 分解、Elixir 版は `Nx.LinAlg.solve/2`、Java・Scala・Clojure 版はそれぞれの行列ライブラリです。ピボットの選び方や、積を足し合わせる順が変われば、最後の 1〜2 桁は動きます。

[第 7 章](07-linear-regression.md) では、自作と hmatrix の `<\>` が 12〜13 桁まで一致し、表示の桁（小数第 2 位・第 4 位）では完全に一致していました。**その 13 桁目のずれが、8 章あとの予測値で 12 桁目のずれとして顔を出した** わけです。予測は係数に映画の特徴量（最大 3000）を掛けて足すので、誤差も一緒に拡大されます。

テストには両方を書きました。1e-6 の許容でほかの言語版と突き合わせ、Haskell 版の値そのものも固定します。

```haskell
-- Java 版・Scala 版・Clojure 版は 7730.457421687023、PHP 版は …019、
-- Elixir 版は …016。正規方程式を解くのが自作のガウス・ジョルダン法か
-- 各言語の行列ライブラリかの違いで、丸めの順が変わる。
-- ほかの言語版とは許容を決めて突き合わせ、Haskell 版の値そのものも固定する。
abs (sales - 7730.457421687023) `shouldSatisfy` (< 1.0e-6)
sales `shouldBe` 7730.457421687022
```

一方、**保存と読み込みで桁が落ちないことは、実データでも完全な一致で確かめられます**。

```haskell
it "保存と読み込みを挟んでも予測値は変わらない" $ do
  ready <- Dataset.exists "cinema.csv"
  if not ready
    then pendingWith "学習データがありません"
    else withFileStore "roundtrip" $ \(store, _) -> do
      ...
      direct <- expectRight (predictSalesWith (linearSalesModel linear) sampleMovie)
      saveSalesModel ("dist-newstyle" </> "chapter15-test" </> "roundtrip") linear
      loaded <- expectRight =<< loadSalesModel store
      predictSalesWith loaded sampleMovie `shouldBe` Right direct
```

**「ほかの言語版と一致しない」と「自分の保存が値を壊す」は別のことです。** 前者は許容を決めて突き合わせ、後者は 1 ビットの一致を要求する、と分けて書けるのがテストの効きどころでした。`Data.Binary` は `Double` を IEEE 754 のビット列のまま書くので、往復で 1 ビットも落ちません。PHP 版が十進の表記を経由しながら `serialize_precision` の既定で往復を保証していたのとは、そもそも仕組みが違います。

### Scotty で待ち受ける

`127.0.0.1` を指定して、自分のマシンからだけ接続できるようにします。

```haskell
serve :: FilePath -> Int -> IO ()
serve dir p = do
  printf "http://%s:%d で待ち受けます\n" host p
  Scotty.scottyOpts options (application (fileStore dir))
 where
  options =
    Scotty.defaultOptions
      { Scotty.settings =
          Warp.setHost (fromString host) (Warp.setPort p (Scotty.settings Scotty.defaultOptions))
      }
```

Scotty の `scotty` はすべてのインターフェースで待ち受けるので、`scottyOpts` に Warp の設定を渡してループバックに絞ります。学習・保存からサーバーの起動までを 1 つの関数にまとめました。

```text
モデル cinema: True
モデル survived: True
http://127.0.0.1:8015 で待ち受けます
Setting phasers to stun... (port 8015) (ctrl-c to quit)
```

最後の 1 行は Scotty が出します。Elixir の Bandit や Clojure の Jetty と同じく、**プログラムの中からサーバーを起動できます**。PHP 版だけは `php -S` そのものがサーバーで、学習と待ち受けが 2 つのコマンドに分かれていました。

別の端末から呼びます。

```text
$ curl -s http://127.0.0.1:8015/health
{"models":{"cinema":true,"survived":true},"status":"ok"}

$ curl -s -X POST http://127.0.0.1:8015/cinema/sales \
    -H "Content-Type: application/json" \
    -d '{"sns1": 200, "sns2": 500, "actor": 3000, "original": 1}'
{"sales":7730.457421687022}

$ curl -s -X POST http://127.0.0.1:8015/survived \
    -H "Content-Type: application/json" \
    -d '{"pclass": 1, "sex": "female", "sib_sp": 0, "parch": 0, "fare": 50.0}'
{"survived":true}

$ curl -s -X POST http://127.0.0.1:8015/survived \
    -H "Content-Type: application/json" \
    -d '{"pclass": 3, "sex": "male", "sib_sp": 0, "parch": 0, "fare": 8.0, "embarked": "S"}'
{"survived":false}
```

1 等客室の女性は生存、3 等客室の男性は死亡という予測です。**どちらも年齢を送っていません。** 空欄のまま第 8 章のパイプラインに渡り、訓練データから求めた中央値で補完されています。

ヘルスチェックの応答で `models` が `status` より先に出ているのは、**aeson がキーを並べ替えて書き出す** からです。コードでは `status` を先に書いています。並びを気にする仕様なら、`Data.Aeson.Encoding` で自分で組み立てるか、`toEncoding` を書くことになります。

不正な入力は 422 です。

```text
$ curl -s -X POST http://127.0.0.1:8015/cinema/sales \
    -H "Content-Type: application/json" \
    -d '{"sns1": -1, "sns2": 2000, "actor": 300, "original": 2}'
{"detail":["sns1 は 0 以上にしてください","original は 0、1 のどれかにしてください"]}

$ curl -s -X POST http://127.0.0.1:8015/cinema/sales \
    -H "Content-Type: application/json" \
    -d '{"sns1": "たくさん"}'
{"detail":["JSON の形式または値の型が正しくありません"]}
```

日本語がそのまま出ています。aeson は既定で非 ASCII をエスケープしません。PHP 版が `JSON_UNESCAPED_UNICODE` を明示していたところが、何もしなくて済みました。

知らないパスは 404、許していないメソッドは 405 です。

```text
$ curl -s -i http://127.0.0.1:8015/unknown
HTTP/1.1 404 Not Found
Transfer-Encoding: chunked
Date: Thu, 24 Sep 2026 09:59:52 GMT
Server: Warp/3.4.16
Content-Type: application/json; charset=utf-8

{"detail":"見つかりません"}

$ curl -s -i http://127.0.0.1:8015/cinema/sales
HTTP/1.1 405 Method Not Allowed
Transfer-Encoding: chunked
Date: Thu, 24 Sep 2026 09:59:52 GMT
Server: Warp/3.4.16
Allow: POST
Content-Type: application/json; charset=utf-8

{"detail":"許していないメソッドです"}
```

ここで 2 つ気づくことがあります。

1 つめは、**自分が書いていない `Server`・`Date`・`Transfer-Encoding` が付いている** ことです。Warp が足しています。PHP 版では `X-Powered-By` が勝手に付いていました。どの版でも、ハンドラーが返す応答とクライアントに届く応答は同じではありません。

2 つめは、**ヘッダーの名前が送ったとおりの大文字小文字で届く** ことです。`Allow` と `Content-Type` はコードに書いたままの綴りで出ています。Elixir 版は Bandit が `content-type` と小文字に正規化していました。同じコードの見た目でも、届く応答はサーバーが決めます。

**「ハンドラーの応答」と「クライアントに届く応答」は同じではありません。** これは、関数として直接呼ぶ統合テストの限界です。ヘッダーの追加・正規化・接続の扱いはサーバーの仕事なので、1 度は本物を起動して `curl -i` で見ておく価値があります。

## 15.10 品質チェック

```bash
npx gulp apps:check:haskell
```

fourmolu → hlint → `cabal test` → カバレッジの判定が順に走ります。

この章のテストは 57 件（実データを使う 3 件を含む）です。学習データが無い環境では、その 3 件が `pendingWith` で飛びます。Haskell 版の全体では 375 件・0 失敗、式カバレッジは 93%（7676/8206）でした。

この章の module ごとの式カバレッジは次のとおりです。

| module | カバレッジ |
|--------|-----------|
| `Chapter15.Service` | 100%（48/48） |
| `Chapter15.Store` | 100%（12/12） |
| `Chapter15.Validation` | 95%（266/280） |
| `Chapter15.Domain` | 94%（101/107） |
| `Chapter15.FileStore` | 91%（103/113） |
| `Chapter15.Api` | 71%（138/192） |
| `Chapter15` | 51%（59/114） |

下の 2 つが低いのは、**サーバーを起動する部分をテストしていない** からです。`Chapter15.Api` の `application`（Scotty への橋渡し）と `statusOf`、`Chapter15` の `run`・`serve` がそれに当たります。Elixir 版の `Chapter15` が 50% だったのと同じ理由で、PHP 版だけが 100% でした（`php -S` がコードではなくコマンドだったため）。**実行モデルの違いが、テストできる範囲の違いとして出ます。**

その代わりに、15.9 節で実際にサーバーを起動して `curl -i` まで通しました。カバレッジの数字が届かないところを、手で 1 度確かめておくという分担です。

hlint の指摘は無視せず、コードのほうを直しました。この章で出たのは `Use fromMaybe`（`maybe 0 id` を書いていた）・`Use catMaybes`（`mapMaybe id`）・`Use void`（`fmap (const ())`）・`Use section`（`` predictSalesWith `flip` movie ``）・`Use fewer imports`（同じ module を 2 回 import していた）の 5 種類です。どれも「標準の関数があるのに手で書いた」類で、**言語を 15 章書いてもまだ拾われる** ものでした。

## 15.11 まとめ

この章では、第 7・8 章のモデルを Scotty と aeson で HTTP API にしました。Haskell に固有の論点は次のとおりです。

1. **約束は型クラスでもレコードでも書けるが、差し替えたいならレコード** — 型クラスは 1 つの型に 1 つのインスタンスなので、偽物を「持っている／持っていない」で作り分けるのに型が 2 つ要る。レコードなら引数 1 つ。しかも型クラスの制約（`(ModelStore s) =>`）は上の層すべてに伝播するが、レコードは `ModelStore ->` の 1 語で止まる
2. **`IO` を境界に閉じ込める構造が、そのまま層の分離になった** — `run` の 5 行で「読み込みだけが `IO`、予測は純粋」が書けた。第 1 章から保ってきた形なので、API のために新しい規律を持ち込まずに済んだ
3. **失敗が直和型なので、ステータスコードへの変換をコンパイラが確かめる** — `LoadError` に節を増やせば `predictFailure` が止まる。PHP 版の `catch`、Elixir 版の `rescue` は節を漏らしても通る
4. **遅延評価がクロージャの代わりになる** — 検証で「理由があるときに値を作らせない」ために、PHP 版と Elixir 版はクロージャを渡していた。Haskell では第 2 引数にそのまま書ける
5. **aeson の `Value` はオブジェクトと配列を取り違えようがない** — PHP 版が `array_is_list` と「数字だけの文字列の鍵」で苦労した判定が、パターン 1 つ。ただし **aeson はキーを並べ替える** ので、応答の並びを仕様にするなら別の手が要る
6. **`ByteString` のリテラルの落とし穴は最後まで生きていた** — 壊れたモデルのファイルを作るテストで、危うく日本語を書くところだった。`BL.pack [0, 1, 2, 3]` と数で書いた

**TODO リスト（この章の完了時点）**:

- [x] 要求の JSON を読み、型が合わなければ弾く
- [x] 必須・0 以上・選択肢の検証をし、理由を並べて返す
- [x] 映画・乗客の特徴量をレコードで表す
- [x] 置き場の約束をレコードと約束のテストで表す
- [x] 第 7 章の線形回帰と第 8 章のパイプラインをファイルに保存・読み込みする
- [x] ファイルが無ければ `ModelNotFound`、読めなければ `ModelUnreadable` を返す
- [x] サービスが置き場からモデルを読んで予測し、ヘルスチェックを返す
- [x] `POST /cinema/sales`・`POST /survived` が予測を JSON で返す
- [x] 入力が不正なら 422、モデルが無ければ 503、それ以外は 500
- [x] 知らないパスは 404、許していないメソッドは 405
- [x] 第 7・8 章と同じ条件で学習し、Scotty で API を起動する

## 15.12 Haskell 版のまとめ

シリーズを 15 章走りきったので、Haskell 版で分かったことをまとめます。

Haskell 版を最後に書いたのは、**型の検査が最も厳しく、ライブラリが最も少ない** 見込みだったからです（[ADR 014](../../../adr/014-haskell-ml-libraries.md)）。結果として、この 2 つが Haskell 版の背骨になりました。

### 失敗を型で表す

第 1 章から最後まで、失敗は `Either String a`、欠損は `Maybe a` で表しました。例外を投げたところは 1 か所もありません。これは [Rust 版](../rust/index.md)・[F# 版](../fsharp/index.md) と同じ流儀で、例外を投げる [Ruby 版](../ruby/index.md)・[Elixir 版](../elixir/index.md)・[PHP 版](../php/index.md) とは正反対です。

効いた場面を並べます。

- **場合分けの網羅をコンパイラが確かめる。** 第 3 章で決定木を `data Tree = Leaf Text | Branch Split Tree Tree` と書いたら、節を書き漏らせばコンパイルが止まるようになりました。この章の `predictFailure` も同じです。PHP 版の共用型（`A|B`）は網羅性までは強制しません
- **`head` が使えないことが、そのまま仕様になった。** 第 3 章で `head` が `-Wx-partial` でコンパイルエラーになり、空の場合をパターンで書くことになりました。それがそのまま `Left "正解ラベルがありません"` という仕様になっています。**型が「書けない」と言ったところが、考え落としていた場合だった**
- **どこを抽象すべきかを型が教えた。** 第 3 章で、第 1 章の `accuracy` が `[Faction]` に固定されていて再利用できませんでした。型を `(Eq a) => [a] -> [a] -> ...` に広げて 1 つに寄せることで、同じ計算を 2 か所に増やさずに済みました。型が邪魔をしたように見えて、設計の穴を指していた形です

一方で、**型では守れないことも 15 章分たまりました**。

- **`ByteString` のリテラルに日本語を書くと壊れる**（第 1 章）。型は `ByteString` で合っていて、中身の解釈だけが違うので型検査では捕まりません。実装で 1 回、テストのデータで 1 回踏み、この章でも危うく 3 回目でした
- **`Int` は溢れると折り返す**（第 2 章）。Java と同じ性質で、線形合同法を素直に書けたのは利点でしたが、溢れそのものは型が止めません
- **`exp 1000` は `Infinity`、`log 0` は `-Infinity`、`0/0` は `NaN`**（第 10 章）。どれも落ちないので、ソフトマックスで最大値を引くことと、対数にごく小さい値を足すことが必須になりました
- **`Data.List.maximumBy` は同値なら後ろを返す**（第 3 章）。`minimumBy` と `sortOn` は先を残します。多数決の同点の倒し方が版間の一致を分けるので、畳み込みを自作しました

**型の道具がどれだけ強くても、言語の癖と数値の性質は型では守れません。** そこを埋めたのはテストでした。PHP 版が「型で守れるところは型で、残りをテストで」という二段構えになったのとまったく同じ結論です。**型が強いほどテストが減るわけではなく、テストが言語の癖に寄る** ということでした。

### ライブラリが無いので自作が最終実装

Haskell には scikit-learn にあたるものがありません（`hlearn` は保守が止まっています）。突き合わせられたのは線形代数の層だけです。

| 章 | 突き合わせた相手 |
|----|------------------|
| 7 | hmatrix の `<\>`（最小二乗解） |
| 9 | statistics（平均・標準偏差） |
| 12 | hmatrix（リッジ回帰の正規方程式） |
| 13 | hmatrix の `eigSH`（固有値分解） |
| 3・8・10・11・14・15 | **無し** |

これは [Elixir 版](../elixir/index.md)（Scholar に決定木が無い）よりさらに狭い範囲です。[PHP 版](../php/index.md) が Rubix ML のおかげでほぼ全章で突き合わせられたのとは正反対でした。

突き合わせる相手がいない章で、自作の正しさを何で担保したか。答えは **ほかの言語版の数値** でした。第 2 章で `java.util.Random` と同じ 48 ビットの線形合同法を自作したので、**どの行が訓練データに入るかが全章で JVM の言語版・Elixir 版・PHP 版と一致します**。その土台の上で、第 3 章の深さごとの正解率、第 8 章の境界、第 10 章の損失の推移といった数値を毎章突き合わせました。

**14 の言語版があることが、ライブラリの代わりになった** わけです。1 つの言語だけで書いていたら、自作の決定木が正しいことを示す手立ては、テストの中の小さな例に限られていたはずです。

そのうえで、この章の予測値は 12 桁目でずれました。分割が一致していても、線形代数の実装が違えば最後の桁は動きます。対処は難しくありません。**「許容を決めて突き合わせる値」と「1 ビットの一致を要求する値」を分ける** ことです。

### 2 つの軸が交わったところ

「失敗を型で表す」と「ライブラリが無いので自作が最終実装」は、別々の軸に見えて、実は同じところで効きました。

**自作するということは、失敗しうる場所を自分で全部持つということです。** 正規方程式が特異行列だったら。補完する値が学習データから求まらなかったら。ラベルが 1 件も無かったら。ライブラリを呼んでいれば、これらはライブラリの中の例外です。自作するなら、自分の関数の戻り値の型に現れます。

`Either String a` を返す関数が 15 章分積み上がった結果、**「どこで失敗しうるか」がコードを読むだけで分かる** 状態になりました。第 15 章で層を分けるときに、`Service` の `run` が 5 行で書けたのはそのおかげです。読み込みは `IO (Either LoadError m)`、予測は `m -> Either String a`。型を見れば、どちらがどの失敗を持つかが分かります。

逆に言えば、**ライブラリが豊富な言語版では、この構造は作りにくかった** はずです。PHP 版が「ライブラリを読む力が自作する力と同じくらい要る」と書いたのと、ちょうど裏表の関係になります。

### 道具立ては言語の標準ではなくプロジェクトの約束

Haskell 版の道具立てでも、前提が崩れました。

- **素の Nix 環境に fourmolu も hlint も無く、手元の `/usr/local/bin` が見えていた**（第 1 章）。環境定義の側で版を固定しました
- **`cabal` に「カバレッジが N% を下回ったら失敗させる」機能が無かった**（第 6 章）。HPC の HTML を読んで判定する短いスクリプトを自作しました。PHP 版が clover の XML を読んだのと同じ形です
- **hmatrix が BLAS/LAPACK の整数幅まで合わせないと実行時に壊れた**（第 7 章）。`openblas` は nixpkgs の既定が ILP64 で、32 ビット整数で呼ぶ hmatrix と ABI が噛み合いません。**ビルドが通っただけでは足りず、2×2 の連立方程式を実際に解いて初めて分かりました**

3 つめは、この版でいちばん高くついた落とし穴でした。**C のライブラリに依存するパッケージでは、リンクが通っても実行時に壊れうる。** しかも `cabal` の store に残った成果物は環境定義を直しても作り直されません（`LIBRARY_PATH` がパッケージのハッシュに入らないため）。「確認は 3 段ある——ビルドできるか、リンクできるか、呼び出せるか」が [ADR 014](../../../adr/014-haskell-ml-libraries.md) に残った知見です。

引けるのは、PHP 版と同じ結論です。**しきい値も品質ゲートも、言語が与えるものではなくプロジェクトが決める約束である。** 言語が持っていれば借りればよく、持っていなければ 30 行書けば済みます。借りられるかどうかで約束の中身を変えるべきではありません。

### 最後に

第 1 章の「20 代ならきのこ派」という手書きのルールから始めて、この章で学習したモデルを HTTP で届けるところまで来ました。

最後まで支えになったのは、**表もモデルも学習済みのパイプラインも、すべて代数的データ型とリスト・`Map` でできていた** ことです。比べるのは `deriving Eq` の `==`、保存するのは `Data.Binary`、失敗を表すのは `Either` と、道具はずっと同じでした。第 15 章で API を足すときに第 7 章と第 8 章に 1 行も触らずに済んだのは、そこで作った値が最初から「ただの値」だったからです。

**変更を楽に安全にできること。** 15 章を通して効いたのは、賢い抽象ではなく、値が値のままであること、失敗が型に現れていること、約束が型かテストのどちらかに書いてあること、そして毎章の数値をほかの言語版と突き合わせ続けたことでした。

型が強い言語は、正しさを型で買えるように見えます。実際に買えたのは「場合分けの網羅」と「失敗の所在」で、それは確かに大きな買い物でした。買えなかったのは「言語の癖」と「数値の一致」で、そこはどの言語版でもテストが守るしかありませんでした。**型はテストを減らしませんが、テストが守るべきものを絞ってくれます。** それが、14 の言語版の最後に Haskell を書いて分かったことです。

Haskell 版のほかの章は [Haskell 版のトップ](index.md) から辿れます。
