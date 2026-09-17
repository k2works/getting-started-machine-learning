---
type: Article
title: "第2章: 関数合成と高階関数"
description: "関数型プログラミングの真髄は、小さな関数を組み合わせて複雑な処理を構築することにあります。"
tags: [article, functional-desgin-ppp, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第2章: 関数合成と高階関数

## はじめに

関数型プログラミングの真髄は、小さな関数を組み合わせて複雑な処理を構築することにあります。Haskell では関数合成が言語の中心的な概念であり、演算子やカリー化により自然に表現できます。

本章では、関数合成のテクニックと高階関数の活用方法を学びます。

## 1. 関数合成の基本 (`.` と `>>>`)

### `.` による関数合成（右から左）

`.` 演算子は数学的な関数合成 `(f ∘ g)(x) = f(g(x))` を表現します：

```haskell
-- | 税金を追加
addTax :: Double -> Double -> Double
addTax rate amount = amount * (1 + rate)

-- | 割引を適用
applyDiscountRate :: Double -> Double -> Double
applyDiscountRate rate amount = amount * (1 - rate)

-- | 円単位に丸める
roundToYen :: Double -> Integer
roundToYen = round

-- | (.) による関数合成（右から左）
calculateFinalPriceCompose :: Double -> Integer
calculateFinalPriceCompose = roundToYen . addTax 0.1 . applyDiscountRate 0.2

-- 使用例
calculateFinalPriceCompose 1000  -- => 880
-- 処理順序: 1000 → 20%割引(800) → 10%税込(880) → 丸め(880)
```

### `>>>` による関数合成（左から右）

`Control.Arrow` の `>>>` はパイプラインのように左から右へ処理を流します：

```haskell
import Control.Arrow ((>>>))

-- | (>>>) による関数合成（左から右、より直感的）
calculateFinalPrice :: Double -> Integer
calculateFinalPrice = applyDiscountRate 0.2 >>> addTax 0.1 >>> roundToYen

-- 使用例
calculateFinalPrice 1000  -- => 880
```

### 関数合成の利点

1. **宣言的な記述**: 処理の流れを関数のチェーンとして表現
2. **再利用性**: 合成した関数を他の場所で再利用可能
3. **テスト容易性**: 各関数を個別にテスト可能
4. **ポイントフリースタイル**: 引数を明示しないエレガントな表現

## 2. カリー化と部分適用

### Haskell のカリー化

Haskell では全ての関数がデフォルトでカリー化されています。複数引数の関数は「1引数を取り関数を返す関数」のチェーンです：

```haskell
-- | カリー化された挨拶関数
greet :: String -> String -> String
greet greeting name = greeting ++ ", " ++ name ++ "!"

-- これは実際には次と等価：
-- greet :: String -> (String -> String)

-- | 部分適用で特化した関数を作成
sayHello :: String -> String
sayHello = greet "Hello"

sayGoodbye :: String -> String
sayGoodbye = greet "Goodbye"

-- 使用例
sayHello "田中"    -- => "Hello, 田中!"
sayGoodbye "鈴木"  -- => "Goodbye, 鈴木!"
```

### 複数引数の部分適用

```haskell
-- | Email レコード
data Email = Email
    { emailFrom    :: String
    , emailTo      :: String
    , emailSubject :: String
    , emailBody    :: String
    } deriving (Show, Eq)

-- | カリー化されたメール作成関数
sendEmail :: String -> String -> String -> String -> Email
sendEmail from to subject body = Email from to subject body

-- | 部分適用でシステムメール送信関数を作成
sendFromSystem :: String -> String -> String -> Email
sendFromSystem = sendEmail "system@example.com"

-- | さらに部分適用
sendNotification :: String -> Email
sendNotification = sendFromSystem "user@example.com" "通知"

-- 使用例
sendNotification "メッセージ本文"
-- => Email { emailFrom = "system@example.com"
--          , emailTo = "user@example.com"
--          , emailSubject = "通知"
--          , emailBody = "メッセージ本文" }
```

## 3. 複数の関数を並列適用 (juxt)

Clojure の `juxt` に相当する機能はタプルで表現します：

```haskell
-- | 2つの関数を同じ入力に適用
juxt2 :: (a -> b) -> (a -> c) -> a -> (b, c)
juxt2 f1 f2 x = (f1 x, f2 x)

-- | 3つの関数を同じ入力に適用
juxt3 :: (a -> b) -> (a -> c) -> (a -> d) -> a -> (b, c, d)
juxt3 f1 f2 f3 x = (f1 x, f2 x, f3 x)

-- 使用例
juxt2 (*2) (+10) 5  -- => (10, 15)
juxt3 head last length [1, 2, 3]  -- => (1, 3, 3)
```

### 実用例：統計情報の取得

```haskell
-- | リストの統計情報を取得
getStats :: [Int] -> (Int, Int, Int, Int, Int)
getStats numbers =
    ( head numbers      -- 最初の値
    , last numbers      -- 最後の値
    , length numbers    -- 要素数
    , minimum numbers   -- 最小値
    , maximum numbers   -- 最大値
    )

getStats [3, 1, 4, 1, 5, 9, 2, 6]  -- => (3, 6, 8, 1, 9)
```

### 実用例：人物分析

```haskell
data Category = Adult | Minor deriving (Show, Eq)

data PersonAnalysis = PersonAnalysis
    { analysisName     :: String
    , analysisAge      :: Int
    , analysisCategory :: Category
    } deriving (Show, Eq)

analyzePerson :: String -> Int -> PersonAnalysis
analyzePerson name age = PersonAnalysis
    { analysisName = name
    , analysisAge = age
    , analysisCategory = if age >= 18 then Adult else Minor
    }

analyzePerson "田中" 25  -- => PersonAnalysis {name="田中", age=25, category=Adult}
analyzePerson "鈴木" 15  -- => PersonAnalysis {name="鈴木", age=15, category=Minor}
```

## 4. 高階関数

高階関数は、関数を引数として受け取るか、関数を返す関数です。

### ログ出力のラッパー

```haskell
-- | プロセスログ
data ProcessLog a b = ProcessLog
    { logInput  :: a
    , logOutput :: b
    } deriving (Show, Eq)

-- | 関数をロギングでラップ（純粋バージョン）
processWithLogging :: (a -> b) -> a -> (b, ProcessLog a b)
processWithLogging f x =
    let result = f x
        logEntry = ProcessLog x result
    in (result, logEntry)

-- 使用例
let (result, logEntry) = processWithLogging (*2) 5
result      -- => 10
logInput logEntry   -- => 5
logOutput logEntry  -- => 10
```

### リトライ機能

```haskell
-- | リトライ結果
data RetryResult a
    = Success a
    | Failure String Int  -- エラーメッセージと試行回数
    deriving (Show, Eq)

-- | 失敗する可能性のある関数をリトライ
withRetry :: Int -> (a -> Either String b) -> a -> RetryResult b
withRetry maxRetries f x = go 0
  where
    go attempts
        | attempts >= maxRetries = Failure "Max retries exceeded" attempts
        | otherwise = case f x of
            Right result -> Success result
            Left _ -> go (attempts + 1)

-- 使用例
let alwaysSucceed x = Right (x * 2)
withRetry 3 alwaysSucceed 5  -- => Success 10

let alwaysFail _ = Left "error" :: Either String Int
withRetry 3 alwaysFail 5  -- => Failure "Max retries exceeded" 3
```

## 5. バリデーター（合成可能な関数）

```haskell
-- | バリデーション結果
data ValidationResult
    = Valid
    | Invalid String
    deriving (Show, Eq)

-- | バリデーター型
type Validator a = a -> ValidationResult

-- | 空でないことを検証
validateNonEmpty :: Validator String
validateNonEmpty s
    | null s    = Invalid "Value cannot be empty"
    | otherwise = Valid

-- | 最小長を検証
validateMinLength :: Int -> Validator String
validateMinLength minLen s
    | length s < minLen = Invalid $ "Must be at least " ++ show minLen ++ " characters"
    | otherwise = Valid

-- | 最大長を検証
validateMaxLength :: Int -> Validator String
validateMaxLength maxLen s
    | length s > maxLen = Invalid $ "Must be at most " ++ show maxLen ++ " characters"
    | otherwise = Valid

-- | メール形式を検証
validateEmail :: Validator String
validateEmail s
    | '@' `elem` s && '.' `elem` s = Valid
    | otherwise = Invalid "Invalid email format"

-- | 2つのバリデーターを結合（AND論理）
combineValidators :: Validator a -> Validator a -> Validator a
combineValidators v1 v2 x = case v1 x of
    Valid -> v2 x
    invalid -> invalid

-- | すべてのバリデーターで検証（エラーを収集）
validateAll :: [Validator a] -> a -> [ValidationResult]
validateAll validators x = filter isInvalid $ map ($ x) validators
  where
    isInvalid Valid = False
    isInvalid (Invalid _) = True

-- 使用例
let combined = combineValidators validateNonEmpty (validateMinLength 3)
combined "hello"  -- => Valid
combined ""       -- => Invalid "Value cannot be empty"
combined "hi"     -- => Invalid "Must be at least 3 characters"
```

## 6. まとめ

| 概念 | Haskell での実現 |
|-----|-----------------|
| 関数合成 | `.` (右→左)、`>>>` (左→右) |
| カリー化 | デフォルトで全関数がカリー化 |
| 部分適用 | 引数を左から順に適用 |
| 高階関数 | 関数を引数/戻り値として扱う |
| 型シグネチャ | `a -> b -> c` は `a -> (b -> c)` |

### ポイントフリースタイル

```haskell
-- ポイントあり（引数を明示）
sumOfSquares xs = sum (map (^2) xs)

-- ポイントフリー（引数を省略）
sumOfSquares = sum . map (^2)
```

### 次のステップ

- 第3章では型クラスによる多態性を学びます
