---
type: Article
title: "第5章: プロパティベーステスト"
description: "従来の単体テストでは特定の入力に対する出力を検証しますが、プロパティベーステストでは、すべての入力に対して成り立つべき「性質（プロパティ）」を定義し、ランダムに生成された多数のテストケースで検証します。"
tags: [article, functional-desgin-ppp, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第5章: プロパティベーステスト

## はじめに

従来の単体テストでは特定の入力に対する出力を検証しますが、**プロパティベーステスト**では、すべての入力に対して成り立つべき「性質（プロパティ）」を定義し、ランダムに生成された多数のテストケースで検証します。

Haskell では QuickCheck ライブラリを使用してプロパティベーステストを行います。

## 1. プロパティベーステストとは

### 従来のテストとの違い

```haskell
-- 従来のテスト：特定の入力に対する出力を検証
spec :: Spec
spec = do
    it "reverses 'hello' to 'olleh'" $
        reverseString "hello" `shouldBe` "olleh"
    it "reverses empty string" $
        reverseString "" `shouldBe` ""

-- プロパティベーステスト：性質を検証
spec :: Spec
spec = do
    it "reversing twice returns original" $ property $
        \s -> reverseString (reverseString s) == (s :: String)
```

### プロパティベーステストの利点

1. **網羅性**: 手動では思いつかないエッジケースを発見
2. **ドキュメント性**: コードの性質を明確に表現
3. **回帰防止**: リファクタリング時の安全網
4. **シュリンキング**: 失敗時に最小の反例を提示

## 2. QuickCheck の基本

### 基本的なプロパティ

```haskell
import Test.QuickCheck

-- 文字列の反転
prop_reverseInvolutory :: String -> Bool
prop_reverseInvolutory s = reverse (reverse s) == s

-- 長さの保存
prop_reversePreservesLength :: String -> Bool
prop_reversePreservesLength s = length (reverse s) == length s

-- テスト実行
main = do
    quickCheck prop_reverseInvolutory
    -- +++ OK, passed 100 tests.
```

### 条件付きプロパティ（==>）

```haskell
-- 正の数に対してのみテスト
prop_sqrtPositive :: Int -> Property
prop_sqrtPositive n = n > 0 ==> sqrt (fromIntegral n) >= 0

-- FizzBuzz: 3の倍数のみ
prop_fizzForMultiplesOf3Only :: Positive Int -> Property
prop_fizzForMultiplesOf3Only (Positive n) =
    let num = n * 3
    in num `mod` 5 /= 0 ==> fizzBuzz num == "Fizz"
```

## 3. リスト関数のプロパティ

### ソートのプロパティ

```haskell
-- ソート結果は整列済み
prop_sortedIsSorted :: [Int] -> Bool
prop_sortedIsSorted xs = isSorted (sort xs)
  where
    isSorted [] = True
    isSorted [_] = True
    isSorted (x:y:rest) = x <= y && isSorted (y:rest)

-- ソート結果は同じ長さ
prop_sortPreservesLength :: [Int] -> Bool
prop_sortPreservesLength xs = length (sort xs) == length xs

-- ソートは冪等
prop_sortIdempotent :: [Int] -> Bool
prop_sortIdempotent xs = sort (sort xs) == sort xs

-- ソート結果は同じ要素を持つ
prop_sortSameElements :: [Int] -> Bool
prop_sortSameElements xs = sort (sort xs) == sort xs
```

### フィルターのプロパティ

```haskell
-- フィルター結果はすべて述語を満たす
prop_filterAllSatisfy :: [Int] -> Bool
prop_filterAllSatisfy xs = all even (filter even xs)

-- const True でフィルターは恒等
prop_filterTrue :: [Int] -> Bool
prop_filterTrue xs = filter (const True) xs == xs

-- const False でフィルターは空
prop_filterFalse :: [Int] -> Bool
prop_filterFalse xs = null (filter (const False) xs)
```

## 4. 数学関数のプロパティ

### 絶対値

```haskell
-- 絶対値は非負
prop_absNonNegative :: Int -> Bool
prop_absNonNegative n = abs n >= 0

-- 絶対値は冪等
prop_absIdempotent :: Int -> Bool
prop_absIdempotent n = abs (abs n) == abs n

-- 負数の絶対値
prop_absOfNegative :: Positive Int -> Bool
prop_absOfNegative (Positive n) = abs (-n) == n
```

### 階乗

```haskell
-- 階乗は正（非負入力に対して）
prop_factorialPositive :: NonNegative Int -> Bool
prop_factorialPositive (NonNegative n) =
    let n' = min n 20  -- 大きい数を避ける
    in factorial n' > 0

-- フィボナッチの漸化式
prop_fibRecurrence :: NonNegative Int -> Bool
prop_fibRecurrence (NonNegative n) =
    let n' = min n 20
    in n' < 2 || fibonacci n' == fibonacci (n' - 1) + fibonacci (n' - 2)
```

## 5. 代数的性質

### Money 型の演算

```haskell
data Money = Money { amount :: Int, currency :: String }
    deriving (Show, Eq)

-- 加算の可換性
prop_addCommutative :: Int -> Int -> Bool
prop_addCommutative a1 a2 =
    let m1 = Money a1 "JPY"
        m2 = Money a2 "JPY"
    in addMoney m1 m2 == addMoney m2 m1

-- 加算の結合性
prop_addAssociative :: Int -> Int -> Int -> Bool
prop_addAssociative a1 a2 a3 =
    let m1 = Money a1 "JPY"
        m2 = Money a2 "JPY"
        m3 = Money a3 "JPY"
        left = addMoney m1 m2 >>= \s -> addMoney s m3
        right = addMoney m2 m3 >>= \s -> addMoney m1 s
    in left == right

-- 乗算の単位元
prop_multiplyIdentity :: Int -> Bool
prop_multiplyIdentity a =
    let m = Money a "JPY"
    in multiplyMoney m 1 == m

-- 乗算の零元
prop_multiplyZero :: Int -> Bool
prop_multiplyZero a =
    let m = Money a "JPY"
    in multiplyMoney m 0 == Money 0 "JPY"
```

## 6. スタック操作のプロパティ

```haskell
-- push して pop すると元の要素が返る
prop_pushPop :: Int -> Bool
prop_pushPop x =
    let s = push x emptyStack
    in pop s == Just (x, emptyStack)

-- push するとサイズが増える
prop_pushIncreasesSize :: Int -> [Int] -> Bool
prop_pushIncreasesSize x xs =
    let s = foldr push emptyStack xs
        s' = push x s
    in size s' == size s + 1

-- isEmpty は空スタックでのみ True
prop_isEmptyAfterPush :: Int -> Bool
prop_isEmptyAfterPush x = not (isEmpty (push x emptyStack))

-- peek は pop と同じ要素を返す
prop_peekEqualsPopFirst :: Int -> Bool
prop_peekEqualsPopFirst x =
    let s = push x emptyStack
    in peek s == fmap fst (pop s)
```

## 7. ラウンドトリッププロパティ

```haskell
-- Roman numerals: toRoman と fromRoman の往復
prop_romanRoundtrip :: Positive Int -> Bool
prop_romanRoundtrip (Positive n) =
    let num = (n `mod` 3999) + 1  -- 有効範囲 1-3999
    in fromRoman (toRoman num) == num

-- 素因数分解: 積は元の数
prop_primeFactorsProduct :: Positive Int -> Bool
prop_primeFactorsProduct (Positive n) =
    let n' = max 2 (n `mod` 1000)
    in product (primeFactors n') == n'
```

## 8. まとめ

| プロパティの種類 | 例 |
|----------------|-----|
| 冪等性 | `f (f x) == f x` |
| 恒等性 | `reverse (reverse x) == x` |
| 可換性 | `f x y == f y x` |
| 結合性 | `f (f x y) z == f x (f y z)` |
| 長さ保存 | `length (f xs) == length xs` |
| 範囲制約 | `f x >= 0` |
| ラウンドトリップ | `decode (encode x) == x` |

### QuickCheck のヒント

```haskell
-- カスタム生成器
genAge :: Gen Int
genAge = choose (0, 150)

-- サイズ制限
genSmallList :: Gen [Int]
genSmallList = resize 10 (listOf arbitrary)

-- NonNegative, Positive などの型ラッパー
prop_example :: NonNegative Int -> Positive Int -> Bool
prop_example (NonNegative n) (Positive p) = ...
```
