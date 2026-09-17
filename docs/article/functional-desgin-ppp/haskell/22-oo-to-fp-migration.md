---
type: Article
title: "第22章 オブジェクト指向から関数型への移行"
description: "この章では、一般的なオブジェクト指向パターンを関数型の同等物に移行する方法を学びます。"
tags: [article, functional-desgin-ppp, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第22章 オブジェクト指向から関数型への移行

## 概要

この章では、一般的なオブジェクト指向パターンを関数型の同等物に移行する方法を学びます。継承からコンポジション、ミュータブルステートからイミュータブル更新、デザインパターンの関数型表現まで、実践的な変換パターンを示します。

## 学習目標

- クラス階層から代数的データ型への変換
- 継承からコンポジションへの移行
- ミュータブルステートのイミュータブル化
- OO パターンの関数型等価物

## クラスからデータ型への移行

### OO: 継承階層 → FP: 直和型

```haskell
-- OO のクラス階層の代わりに直和型を使用
data Animal
  = Dog { dogName :: String, dogBreed :: String }
  | Cat { catName :: String, catColor :: String }
  | Bird { birdName :: String, birdCanFly :: Bool }
  deriving (Show, Eq)

-- ポリモーフィズムはパターンマッチで実現
makeSound :: Animal -> String
makeSound (Dog name _) = name ++ " says: Woof!"
makeSound (Cat name _) = name ++ " says: Meow!"
makeSound (Bird name _) = name ++ " says: Tweet!"

move :: Animal -> String
move (Dog name _) = name ++ " runs on four legs"
move (Cat name _) = name ++ " prowls silently"
move (Bird name canFly)
  | canFly = name ++ " flies through the air"
  | otherwise = name ++ " hops on the ground"

describeAnimal :: Animal -> String
describeAnimal animal = makeSound animal ++ " and " ++ move animal
```

### OO: インターフェース → FP: 型クラス

```haskell
-- OO のインターフェースの代わりに型クラスを使用
class Shape a where
  area :: a -> Double
  perimeter :: a -> Double

-- 各形状の実装
data Circle = Circle { circleRadius :: Double }
  deriving (Show, Eq)

instance Shape Circle where
  area (Circle r) = pi * r * r
  perimeter (Circle r) = 2 * pi * r

data Rectangle = Rectangle { rectWidth :: Double, rectHeight :: Double }
  deriving (Show, Eq)

instance Shape Rectangle where
  area (Rectangle w h) = w * h
  perimeter (Rectangle w h) = 2 * (w + h)
```

## 継承からコンポジションへ

### OO: 継承階層 → FP: コンポジション

```haskell
-- コンポーネント
data Engine
  = NoEngine
  | GasEngine { horsepower :: Int }
  | ElectricEngine { batteryKwh :: Int }
  deriving (Show, Eq)

data Wheels = Wheels { wheelCount :: Int, wheelSize :: Int }
  deriving (Show, Eq)

-- コンポジションによる Vehicle
data Vehicle = Vehicle
  { vehicleName :: String
  , vehicleEngine :: Engine
  , vehicleWheels :: Wheels
  } deriving (Show, Eq)

-- ファクトリー関数
car :: Vehicle
car = Vehicle "Car" (GasEngine 200) (Wheels 4 17)

motorcycle :: Vehicle
motorcycle = Vehicle "Motorcycle" (GasEngine 80) (Wheels 2 18)

bicycle :: Vehicle
bicycle = Vehicle "Bicycle" NoEngine (Wheels 2 26)

-- 振る舞いは関数で定義
describeVehicle :: Vehicle -> String
describeVehicle v = vehicleName v ++ " with " ++ engineDesc ++ " and " ++ wheelsDesc
  where
    engineDesc = case vehicleEngine v of
      NoEngine -> "no engine"
      GasEngine hp -> show hp ++ "hp gas engine"
      ElectricEngine kw -> show kw ++ "kWh electric engine"
    wheelsDesc = show (wheelCount (vehicleWheels v)) ++ " wheels"
```

## ミュータブルステートからイミュータブル更新へ

### OO: ミュータブルオブジェクト → FP: イミュータブルレコード

```haskell
-- イミュータブルな銀行口座
data BankAccount = BankAccount
  { accountId :: String
  , accountBalance :: Double
  , accountOwner :: String
  } deriving (Show, Eq)

-- 作成
makeAccount :: String -> String -> Double -> BankAccount
makeAccount aid owner initial = BankAccount aid initial owner

-- 預金（新しい口座を返す）
deposit :: Double -> BankAccount -> BankAccount
deposit amount account
  | amount > 0 = account { accountBalance = accountBalance account + amount }
  | otherwise = account

-- 引き出し（失敗する可能性をMaybeで表現）
withdraw :: Double -> BankAccount -> Maybe BankAccount
withdraw amount account
  | amount > 0 && accountBalance account >= amount =
      Just account { accountBalance = accountBalance account - amount }
  | otherwise = Nothing

-- 送金
transfer :: Double -> BankAccount -> BankAccount -> Maybe (BankAccount, BankAccount)
transfer amount from to = do
  from' <- withdraw amount from
  let to' = deposit amount to
  return (from', to')

-- 使用例
let alice = makeAccount "A001" "Alice" 1000
    bob = makeAccount "B001" "Bob" 500
case transfer 200 alice bob of
  Just (alice', bob') -> do
    putStrLn $ "Alice: " ++ show (accountBalance alice')  -- 800
    putStrLn $ "Bob: " ++ show (accountBalance bob')      -- 700
  Nothing -> putStrLn "Transfer failed"
```

## Strategy パターンの移行

### OO: Strategy インターフェース → FP: 関数

```haskell
-- Strategy を関数として表現
type PaymentMethod = Double -> String -> Either String String

-- 具体的な Strategy
creditCard :: String -> PaymentMethod
creditCard cardNumber amount merchant =
  Right $ "Paid $" ++ show amount ++ " to " ++ merchant ++ 
          " via credit card " ++ take 4 cardNumber ++ "****"

paypal :: String -> PaymentMethod
paypal email amount merchant =
  Right $ "Paid $" ++ show amount ++ " to " ++ merchant ++ 
          " via PayPal (" ++ email ++ ")"

bitcoin :: String -> PaymentMethod
bitcoin wallet amount merchant =
  Right $ "Paid $" ++ show amount ++ " to " ++ merchant ++ 
          " via Bitcoin wallet " ++ take 8 wallet ++ "..."

-- Strategy を使用
processPayment :: PaymentMethod -> Double -> String -> Either String String
processPayment method amount merchant = method amount merchant

-- 使用例
let strategy = creditCard "1234567890123456"
processPayment strategy 100.0 "Amazon"
-- Right "Paid $100.0 to Amazon via credit card 1234****"
```

## Observer パターンの移行

### OO: Observer インターフェース → FP: 関数リスト

```haskell
-- イベント
data Event
  = UserCreated String
  | UserDeleted String
  | OrderPlaced String Double
  | OrderCancelled String
  deriving (Show, Eq)

-- サブスクリプション（イベントを受け取る関数）
type Subscription = Event -> String

-- サブスクライブ
subscribe :: String -> Subscription
subscribe subscriberName event = case event of
  UserCreated user -> subscriberName ++ " notified: User " ++ user ++ " created"
  OrderPlaced orderId amount -> 
    subscriberName ++ " notified: Order " ++ orderId ++ " placed for $" ++ show amount
  _ -> subscriberName ++ " notified of event"

-- パブリッシュ
publish :: [Subscription] -> Event -> [String]
publish subscribers event = map ($ event) subscribers

-- イベントログ（純粋関数）
type EventLog = [Event]

logEvent :: Event -> EventLog -> EventLog
logEvent event log' = event : log'

-- 使用例
let emailSub = subscribe "EmailService"
    smsSub = subscribe "SMSService"
    notifications = publish [emailSub, smsSub] (OrderPlaced "ORD001" 99.99)
-- ["EmailService notified: Order ORD001 placed for $99.99",
--  "SMSService notified: Order ORD001 placed for $99.99"]
```

## Factory パターンの移行

### OO: Factory クラス → FP: スマートコンストラクタ

```haskell
-- ドキュメント型
data Document
  = PDFDocument String
  | WordDocument String
  | HTMLDocument String
  deriving (Show, Eq)

-- スマートコンストラクタ
createPDF :: String -> Document
createPDF = PDFDocument

createWord :: String -> Document
createWord = WordDocument

createHTML :: String -> Document
createHTML = HTMLDocument

-- レンダリング
renderDocument :: Document -> String
renderDocument (PDFDocument content) = "<PDF>" ++ content ++ "</PDF>"
renderDocument (WordDocument content) = "<WORD>" ++ content ++ "</WORD>"
renderDocument (HTMLDocument content) = "<html><body>" ++ content ++ "</body></html>"
```

## Singleton の移行

### OO: Singleton クラス → FP: モジュールレベル値

```haskell
-- 設定型
data Config = Config
  { configDbHost :: String
  , configDbPort :: Int
  , configLogLevel :: String
  } deriving (Show, Eq)

-- モジュールレベルの値（Singleton の代わり）
defaultConfig :: Config
defaultConfig = Config "localhost" 5432 "INFO"

testConfig :: Config
testConfig = Config "localhost" 5433 "DEBUG"

productionConfig :: Config
productionConfig = Config "prod.db.example.com" 5432 "WARN"

-- 使用時に明示的に渡す
runApp :: Config -> IO ()
runApp config = do
  putStrLn $ "Connecting to " ++ configDbHost config
  -- ...
```

## Null Object の移行

### OO: Null Object パターン → FP: Maybe

```haskell
-- ユーザー検索（Maybe を返す）
findUser :: String -> Maybe SimpleUser
findUser userId = Map.lookup userId userDatabase

-- 安全なナビゲーション
getUserName :: String -> Maybe String
getUserName userId = simpleUserName <$> findUser userId

-- デフォルト値付き
getUserNameOrDefault :: String -> String -> String
getUserNameOrDefault userId defaultName =
  maybe defaultName simpleUserName (findUser userId)

-- 使用例
getUserName "1"                      -- Just "Alice"
getUserName "999"                    -- Nothing
getUserNameOrDefault "999" "Unknown" -- "Unknown"
```

## Template Method の移行

### OO: Template Method → FP: 高階関数

```haskell
-- データプロセッサ（関数として表現）
type DataProcessor = String -> String

-- 具体的なプロセッサ
csvProcessor :: DataProcessor
csvProcessor input = "CSV[" ++ input ++ "]"

jsonProcessor :: DataProcessor
jsonProcessor input = "{\"data\": \"" ++ input ++ "\"}"

xmlProcessor :: DataProcessor
xmlProcessor input = "<data>" ++ input ++ "</data>"

-- テンプレートメソッドの等価物（高階関数）
processData :: DataProcessor -> String -> String
processData processor input =
  let validated = "validated:" ++ input
      transformed = processor validated
      output = "output:" ++ transformed
  in output

-- 使用例
processData csvProcessor "test"   -- "output:CSV[validated:test]"
processData jsonProcessor "test"  -- "output:{\"data\": \"validated:test\"}"
```

## Chain of Responsibility の移行

### OO: Handler チェーン → FP: 関数合成

```haskell
-- リクエスト
data Request = Request
  { reqPath :: String
  , reqMethod :: String
  , reqHeaders :: Map String String
  , reqBody :: String
  } deriving (Show, Eq)

-- ハンドラー（関数として表現）
type Handler = Request -> Either String Request

-- 具体的なハンドラー
authHandler :: Handler
authHandler req =
  case Map.lookup "Authorization" (reqHeaders req) of
    Nothing -> Left "Unauthorized: No auth header"
    Just _ -> Right req

validationHandler :: Handler
validationHandler req
  | null (reqBody req) && reqMethod req == "POST" = Left "Bad Request: Empty body"
  | otherwise = Right req

loggingHandler :: Handler
loggingHandler req = Right req

-- ハンドラーの連結
chainHandlers :: [Handler] -> Handler
chainHandlers [] req = Right req
chainHandlers (h:hs) req = case h req of
  Left err -> Left err
  Right req' -> chainHandlers hs req'

-- 使用例
let handlers = chainHandlers [authHandler, loggingHandler, validationHandler]
    request = Request "/api/users" "POST" 
              (Map.fromList [("Authorization", "Bearer token")]) 
              "{\"name\":\"test\"}"
handlers request  -- Right Request {...}
```

## テスト

```haskell
spec :: Spec
spec = do
  describe "Class to Data Type Migration" $ do
    it "animals make sounds" $ do
      let dog = Dog "Rex" "German Shepherd"
      makeSound dog `shouldBe` "Rex says: Woof!"

  describe "Immutable Updates" $ do
    it "deposit creates new account" $ do
      let account = makeAccount "A001" "Alice" 1000
          account' = deposit 500 account
      getBalance account' `shouldBe` 1500
      getBalance account `shouldBe` 1000  -- 元のデータは変更されない

  describe "Strategy Migration" $ do
    it "uses payment strategy" $ do
      let strategy = creditCard "1234567890123456"
      case processPayment strategy 100.0 "Store" of
        Right msg -> "credit card" `isInfixOf` msg `shouldBe` True
        Left _ -> expectationFailure "Should succeed"

  describe "Chain of Responsibility" $ do
    it "chains handlers" $ do
      let handlers = chainHandlers [authHandler, validationHandler]
          validReq = Request "/api" "POST" 
                     (Map.fromList [("Authorization", "token")]) 
                     "body"
      case handlers validReq of
        Right _ -> return ()
        Left _ -> expectationFailure "Should pass"
```

## 移行のガイドライン

### 1. 継承よりコンポジション

```haskell
-- 悪い例（継承の模倣）
-- 良い例（コンポジション）
data Component = Component { ... }
data Entity = Entity { entityComponents :: [Component] }
```

### 2. ミュータブルステートの排除

```haskell
-- 悪い例：IORef を多用
-- 良い例：純粋関数で状態を変換
update :: State -> Action -> State
```

### 3. 例外より戻り値

```haskell
-- 悪い例：例外をスロー
-- 良い例：Maybe/Either で失敗を表現
findUser :: String -> Maybe User
processOrder :: Order -> Either OrderError Receipt
```

### 4. インターフェースより関数

```haskell
-- 悪い例：型クラスの乱用
-- 良い例：関数を直接渡す
type Strategy a = a -> a
applyStrategy :: Strategy a -> a -> a
```

## まとめ

- 継承階層は直和型で表現
- ポリモーフィズムはパターンマッチまたは型クラス
- ミュータブルステートはイミュータブルレコードの変換
- OO パターンの多くは関数と高階関数で簡潔に表現可能
- 型システムにより、より安全な設計が可能
