---
type: Article
title: "第17章: レンタルビデオシステム"
description: "本章では、Martin Fowler の「リファクタリング」で有名なレンタルビデオシステムを題材に、関数型プログラミングによる料金計算ロジックの設計を学びます。"
tags: [article, functional-desgin-ppp, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第17章: レンタルビデオシステム

## はじめに

本章では、Martin Fowler の「リファクタリング」で有名なレンタルビデオシステムを題材に、関数型プログラミングによる料金計算ロジックの設計を学びます。

この問題を通じて以下の概念を学びます：

- ADT によるカテゴリ別ポリシーパターン
- 明細書フォーマッターの実装
- データと処理の分離

## 1. ドメインモデル

### 映画のカテゴリ

レンタルビデオシステムでは、以下の3種類の映画カテゴリをサポートします：

- **通常（Regular）**: 2日まで$2.0、以降1日ごとに$1.5追加
- **新作（New Release）**: 1日ごとに$3.0
- **子供向け（Children's）**: 3日まで$1.5、以降1日ごとに$1.5追加

## 2. 型定義

### 映画とカテゴリ

```haskell
-- | Movie category determines pricing rules
data Category
  = Regular       -- ^ 2 days for $2, then $1.5/day
  | NewRelease    -- ^ $3/day
  | Childrens     -- ^ 3 days for $1.5, then $1.5/day
  deriving (Show, Eq)

-- | A movie with title and category
data Movie = Movie
  { movieTitle :: String
  , movieCategory :: Category
  } deriving (Show, Eq)
```

### 映画作成のヘルパー

```haskell
-- | Create a movie
makeMovie :: String -> Category -> Movie
makeMovie = Movie

-- | Create a regular movie
makeRegular :: String -> Movie
makeRegular title = makeMovie title Regular

-- | Create a new release movie
makeNewRelease :: String -> Movie
makeNewRelease title = makeMovie title NewRelease

-- | Create a children's movie
makeChildrens :: String -> Movie
makeChildrens title = makeMovie title Childrens
```

### レンタル

```haskell
-- | A rental of a movie for a number of days
data Rental = Rental
  { rentalMovie :: Movie
  , rentalDays :: Int
  } deriving (Show, Eq)

-- | Create a rental
makeRental :: Movie -> Int -> Rental
makeRental = Rental
```

## 3. 料金計算

### パターンマッチングによる料金計算

```haskell
-- | Calculate the rental amount based on movie category and days
calculateAmount :: Rental -> Double
calculateAmount rental = case movieCategory (rentalMovie rental) of
  Regular ->
    if days > 2
    then 2.0 + fromIntegral (days - 2) * 1.5
    else 2.0
  
  NewRelease ->
    fromIntegral days * 3.0
  
  Childrens ->
    if days > 3
    then 1.5 + fromIntegral (days - 3) * 1.5
    else 1.5
  where
    days = rentalDays rental
```

### ポイント計算

```haskell
-- | Calculate frequent renter points
calculatePoints :: Rental -> Int
calculatePoints rental = case movieCategory (rentalMovie rental) of
  NewRelease -> if rentalDays rental > 1 then 2 else 1
  _ -> 1
```

## 4. 顧客モデル

```haskell
-- | A customer with name and rentals
data Customer = Customer
  { customerName :: String
  , customerRentals :: [Rental]
  } deriving (Show, Eq)

-- | Create a customer with no rentals
makeCustomer :: String -> Customer
makeCustomer name = Customer name []

-- | Add a rental to a customer
addRental :: Rental -> Customer -> Customer
addRental rental customer = customer
  { customerRentals = rental : customerRentals customer
  }
```

### 集計関数

```haskell
-- | Calculate total amount for all rentals
totalAmount :: Customer -> Double
totalAmount = sum . map calculateAmount . customerRentals

-- | Calculate total points for all rentals
totalPoints :: Customer -> Int
totalPoints = sum . map calculatePoints . customerRentals
```

## 5. 明細書フォーマッター

### フォーマット種別

```haskell
-- | Statement format
data StatementFormat = TextFormat | HtmlFormat
  deriving (Show, Eq)

-- | Generate a rental statement in the specified format
generateStatement :: StatementFormat -> Customer -> String
generateStatement format = case format of
  TextFormat -> generateTextStatement
  HtmlFormat -> generateHtmlStatement
```

### テキストフォーマット

```haskell
-- | Generate a plain text statement
generateTextStatement :: Customer -> String
generateTextStatement customer = unlines
  [ "Rental Record for " ++ customerName customer
  , ""
  ] ++ rentalLines ++ unlines
  [ ""
  , "Amount owed is " ++ show (totalAmount customer)
  , "You earned " ++ show (totalPoints customer) ++ " frequent renter points"
  ]
  where
    rentalLines = unlines $ map formatRentalLine (customerRentals customer)
    formatRentalLine rental = 
      "\t" ++ movieTitle (rentalMovie rental) ++ "\t" ++ show (calculateAmount rental)
```

### HTML フォーマット

```haskell
-- | Generate an HTML statement
generateHtmlStatement :: Customer -> String
generateHtmlStatement customer = unlines
  [ "<html>"
  , "<head><title>Rental Statement</title></head>"
  , "<body>"
  , "<h1>Rental Record for " ++ customerName customer ++ "</h1>"
  , "<table>"
  , "<tr><th>Movie</th><th>Amount</th></tr>"
  ] ++ rentalRows ++ unlines
  [ "</table>"
  , "<p>Amount owed is <strong>" ++ show (totalAmount customer) ++ "</strong></p>"
  , "<p>You earned <strong>" ++ show (totalPoints customer) 
    ++ "</strong> frequent renter points</p>"
  , "</body>"
  , "</html>"
  ]
  where
    rentalRows = unlines $ map formatRentalRow (customerRentals customer)
    formatRentalRow rental =
      "<tr><td>" ++ movieTitle (rentalMovie rental) 
      ++ "</td><td>" ++ show (calculateAmount rental) ++ "</td></tr>"
```

## 6. テスト

```haskell
describe "calculateAmount" $ do
  it "charges $2 for 1-2 day regular rental" $ do
    let rental = makeRental (makeRegular "Test") 2
    calculateAmount rental `shouldBe` 2.0
  
  it "charges extra for regular rental over 2 days" $ do
    let rental = makeRental (makeRegular "Test") 5
    -- 2 + 3 * 1.5 = 6.5
    calculateAmount rental `shouldBe` 6.5
  
  it "charges $3/day for new release" $ do
    let rental = makeRental (makeNewRelease "Test") 5
    calculateAmount rental `shouldBe` 15.0

describe "calculatePoints" $ do
  it "earns bonus points for new release over 1 day" $ do
    let rental = makeRental (makeNewRelease "Test") 2
    calculatePoints rental `shouldBe` 2

describe "generateStatement" $ do
  it "generates correct text statement" $ do
    let customer = addRental (makeRental (makeRegular "The Matrix") 3)
                 $ makeCustomer "John"
    let statement = generateTextStatement customer
    statement `shouldContain` "Rental Record for John"
    statement `shouldContain` "The Matrix"
```

## まとめ

レンタルビデオシステムの Haskell 実装のポイント：

1. **ADT でカテゴリを表現**: `Category` 型でビジネスルールを型に埋め込む
2. **パターンマッチングで料金計算**: 各カテゴリの計算ルールを明示的に記述
3. **不変データ構造**: `addRental` は新しい `Customer` を返す
4. **関数合成で集計**: `totalAmount = sum . map calculateAmount . customerRentals`
5. **フォーマッター分離**: ビジネスロジックと表示ロジックを分離
6. **拡張性**: 新しいカテゴリやフォーマットを追加しやすい設計
