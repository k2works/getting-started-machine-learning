---
type: Article
title: "第20章 パターンの相互作用 - 複合パターンの活用"
description: "この章では、複数の関数型デザインパターンを組み合わせて、複雑な問題を解決する方法を学びます。"
tags: [article, functional-desgin-ppp, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-27T04:43:33Z }
---

# 第20章 パターンの相互作用 - 複合パターンの活用

## 概要

この章では、複数の関数型デザインパターンを組み合わせて、複雑な問題を解決する方法を学びます。ECサイトのショッピングカートシステムを例に、Strategy、Decorator、Command、Composite、Visitor パターンの統合を示します。

## 学習目標

- 複数パターンの効果的な組み合わせ方
- パターン間の協調動作の設計
- 実用的なシステムでのパターン適用
- 関数型での柔軟なアーキテクチャ構築

## 商品と価格設定

### 商品の定義

```haskell
-- | 商品
data Product = Product
  { productId :: String
  , productName :: String
  , productBasePrice :: Double
  , productCategory :: String
  } deriving (Show, Eq)

-- | 商品の作成
makeProduct :: String -> String -> Double -> String -> Product
makeProduct = Product
```

### 価格設定戦略 (Strategy パターン)

```haskell
-- | 価格設定戦略
newtype PricingStrategy = PricingStrategy
  { applyPricing :: Product -> Int -> Double
  }

-- | 通常価格
regularPricing :: PricingStrategy
regularPricing = PricingStrategy $ \prod qty ->
  productBasePrice prod * fromIntegral qty

-- | 割引価格
discountPricing :: Double -> PricingStrategy
discountPricing percent = PricingStrategy $ \prod qty ->
  productBasePrice prod * fromIntegral qty * (1 - percent / 100)

-- | まとめ買い価格
bulkPricing :: Int -> Double -> PricingStrategy
bulkPricing threshold percent = PricingStrategy $ \prod qty ->
  let basePrice = productBasePrice prod * fromIntegral qty
  in if qty >= threshold
     then basePrice * (1 - percent / 100)
     else basePrice
```

## 割引デコレータ (Decorator パターン)

```haskell
-- | 割引デコレータ
type DiscountDecorator = Double -> Double

-- | パーセント割引
percentageDiscount :: Double -> DiscountDecorator
percentageDiscount percent price = price * (1 - percent / 100)

-- | 固定額割引
fixedDiscount :: Double -> DiscountDecorator
fixedDiscount amount price = max 0 (price - amount)

-- | 季節割引
seasonalDiscount :: Double -> DiscountDecorator
seasonalDiscount multiplier price = price * multiplier

-- | 複数割引の適用
applyDiscounts :: [DiscountDecorator] -> Double -> Double
applyDiscounts decorators price = foldl' (\p d -> d p) price decorators
```

## 注文コマンド (Command パターン)

```haskell
-- | 注文コマンド型クラス
class OrderCommand cmd where
  execute :: cmd -> ShoppingCart -> ShoppingCart

-- | 商品追加コマンド
data AddItem = AddItem Product Int

instance OrderCommand AddItem where
  execute (AddItem prod qty) cart =
    let item = CartItem prod qty (productBasePrice prod)
        newItems = item : scItems cart
    in cart { scItems = newItems }

-- | 商品削除コマンド
data RemoveItem = RemoveItem String

instance OrderCommand RemoveItem where
  execute (RemoveItem prodId) cart =
    let newItems = filter (\item -> productId (ciProduct item) /= prodId) (scItems cart)
    in cart { scItems = newItems }

-- | 割引適用コマンド
data ApplyDiscount = ApplyDiscount DiscountDecorator

instance OrderCommand ApplyDiscount where
  execute (ApplyDiscount discount) cart =
    cart { scDiscount = Just discount }
```

## ショッピングカート (Composite パターン)

```haskell
-- | カートアイテム
data CartItem = CartItem
  { ciProduct :: Product
  , ciQuantity :: Int
  , ciUnitPrice :: Double
  } deriving (Show, Eq)

-- | ショッピングカート
data ShoppingCart = ShoppingCart
  { scItems :: [CartItem]
  , scDiscount :: Maybe DiscountDecorator
  }

-- | 空のカート
emptyCart :: ShoppingCart
emptyCart = ShoppingCart [] Nothing

-- | カート合計
cartTotal :: ShoppingCart -> Double
cartTotal cart =
  let subtotal = sum [ciUnitPrice item * fromIntegral (ciQuantity item) 
                     | item <- scItems cart]
  in case scDiscount cart of
       Nothing -> subtotal
       Just discount -> discount subtotal
```

## 注文処理パイプライン

```haskell
-- | 注文処理結果
data OrderResult
  = OrderSuccess ShoppingCart
  | OrderFailure String

-- | パイプラインステップ
type OrderPipeline = ShoppingCart -> OrderResult

-- | 検証ステップ
validationStep :: OrderPipeline
validationStep cart =
  if null (scItems cart)
  then OrderFailure "Cart is empty"
  else OrderSuccess cart

-- | 割引ステップ
discountStep :: Double -> OrderPipeline
discountStep percent cart =
  OrderSuccess cart { scDiscount = Just (percentageDiscount percent) }

-- | パイプライン実行
runPipeline :: [OrderPipeline] -> ShoppingCart -> OrderResult
runPipeline [] cart = OrderSuccess cart
runPipeline (step:steps) cart =
  case step cart of
    OrderFailure msg -> OrderFailure msg
    OrderSuccess cart' -> runPipeline steps cart'
```

## カート分析ビジター (Visitor パターン)

```haskell
-- | カートビジター
newtype CartVisitor a = CartVisitor
  { runVisitor :: ShoppingCart -> a
  }

-- | アイテム数カウント
itemCountVisitor :: CartVisitor Int
itemCountVisitor = CartVisitor $ \cart ->
  sum [ciQuantity item | item <- scItems cart]

-- | 合計金額
totalValueVisitor :: CartVisitor Double
totalValueVisitor = CartVisitor cartTotal

-- | 平均単価
averageItemPriceVisitor :: CartVisitor Double
averageItemPriceVisitor = CartVisitor $ \cart ->
  let items = scItems cart
      count = length items
  in if count == 0
     then 0
     else sum [ciUnitPrice item | item <- items] / fromIntegral count

-- | ビジターの適用
visitCart :: CartVisitor a -> ShoppingCart -> a
visitCart visitor cart = runVisitor visitor cart
```

## 使用例

### 完全な注文フロー

```haskell
-- 商品の定義
laptop = makeProduct "P001" "Laptop" 1000.0 "Electronics"
mouse = makeProduct "P002" "Mouse" 50.0 "Electronics"

-- コマンドを使ってカートを構築
cart1 = executeCommand (AddItem laptop 1) emptyCart
cart2 = executeCommand (AddItem mouse 2) cart1
cart3 = executeCommand (ApplyDiscount (percentageDiscount 5)) cart2

-- パイプラインで処理
pipeline = [validationStep]
result = runPipeline pipeline cart3

-- ビジターで分析
case result of
  OrderSuccess cart -> do
    putStrLn $ "Items: " ++ show (visitCart itemCountVisitor cart)
    putStrLn $ "Total: $" ++ show (visitCart totalValueVisitor cart)
  OrderFailure msg -> putStrLn $ "Error: " ++ msg

-- 出力:
-- Items: 3
-- Total: $1045.0 (1100 * 0.95)
```

### パターンの協調

```haskell
-- Strategy: 価格戦略を選択
strategy = if isBulkOrder then bulkPricing 10 15 else regularPricing

-- Decorator: 割引を積み重ね
discounts = [percentageDiscount 10, fixedDiscount 50]
finalPrice = applyDiscounts discounts basePrice

-- Command: 操作をデータ化
commands = [AddItem prod1 1, AddItem prod2 2, ApplyDiscount (percentageDiscount 5)]
cart = foldl (\c cmd -> executeCommand cmd c) emptyCart commands

-- Visitor: カートを分析
stats = (visitCart itemCountVisitor cart, visitCart totalValueVisitor cart)
```

## テスト

```haskell
spec :: Spec
spec = do
  describe "Pattern Integration" $ do
    it "processes complete order with multiple patterns" $ do
      let product1 = makeProduct "P001" "Laptop" 1000.0 "Electronics"
          product2 = makeProduct "P002" "Mouse" 50.0 "Electronics"
          
          -- コマンドでカートを構築
          cart1 = executeCommand (AddItem product1 1) emptyCart
          cart2 = executeCommand (AddItem product2 2) cart1
          cart3 = executeCommand (ApplyDiscount (percentageDiscount 5)) cart2
          
          -- パイプラインで処理
          pipeline' = [validationStep]
      
      case runPipeline pipeline' cart3 of
        OrderSuccess cart' -> do
          visitCart itemCountVisitor cart' `shouldBe` 3
          cartTotal cart' `shouldBe` 1045.0
        OrderFailure _ -> expectationFailure "Order should succeed"
```

## パターン組み合わせの原則

### 1. 責任の分離

各パターンは単一の責任を持つ：


- Strategy: 価格計算方法
- Decorator: 価格修飾
- Command: 操作のカプセル化
- Composite: 構造の管理
- Visitor: 横断的な処理

### 2. 合成可能性

パターンは互いに干渉せず、組み合わせ可能：

```haskell
-- どのパターンも独立して使用可能
price1 = applyPricing regularPricing product 1
price2 = percentageDiscount 10 price1
cart = executeCommand (AddItem product 1) emptyCart
count = visitCart itemCountVisitor cart
```

### 3. 型による安全性

型システムが不正な組み合わせを防止：

```haskell
-- 型クラスで操作を統一
class OrderCommand cmd where
  execute :: cmd -> ShoppingCart -> ShoppingCart

-- 新しいコマンドの追加が安全
data NewCommand = ...
instance OrderCommand NewCommand where
  execute = ...
```

## まとめ

- 複数のパターンを組み合わせることで、複雑な問題を分割統治できる
- 各パターンは独立して進化・テスト可能
- 関数型アプローチにより、パターンの合成が自然になる
- 型システムにより、安全なパターンの組み合わせが保証される
