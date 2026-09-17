---
type: Article
title: "第21章 関数型プログラミングのベストプラクティス"
description: "この章では、Haskell での関数型プログラミングにおけるベストプラクティスを学びます。"
tags: [article, functional-desgin-ppp, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第21章 関数型プログラミングのベストプラクティス

## 概要

この章では、Haskell での関数型プログラミングにおけるベストプラクティスを学びます。不変性、関数合成、純粋関数、エラーハンドリング、型安全性などの重要な原則を実践的に示します。

## 学習目標

- 不変性を活用したコード設計
- 関数合成によるパイプライン構築
- 純粋関数と参照透過性の維持
- 安全なエラーハンドリング
- 型システムによる堅牢性向上

## 不変性の実践

### レコードの不変更新

```haskell
-- | ユーザーレコード
data User = User
  { userName :: String
  , userEmail :: String
  , userAge :: Int
  } deriving (Show, Eq)

-- | 名前の更新（元のデータは変更しない）
updateUserName :: String -> User -> User
updateUserName newName user = user { userName = newName }

-- | メールの更新
updateUserEmail :: String -> User -> User
updateUserEmail newEmail user = user { userEmail = newEmail }

-- | 年齢の更新
updateUserAge :: Int -> User -> User
updateUserAge newAge user = user { userAge = newAge }

-- 使用例
let alice = User "Alice" "alice@example.com" 30
let bob = updateUserName "Bob" alice
-- alice は変更されない
-- bob は新しい User
```

### 汎用更新関数

```haskell
-- | 任意の更新関数を適用
updateUser :: (User -> User) -> User -> User
updateUser f = f

-- 複数の更新を合成
updateAll :: User -> User
updateAll = updateUserName "Charlie" 
          . updateUserEmail "charlie@example.com"
          . updateUserAge 25
```

## 関数合成

### パイプライン演算子

```haskell
-- | 前方パイプ
(|>) :: a -> (a -> b) -> b
x |> f = f x
infixl 1 |>

-- | 後方パイプ
(<|) :: (a -> b) -> a -> b
f <| x = f x
infixr 0 <|

-- 使用例
result = "  HELLO WORLD  "
       |> trim
       |> toLowerCase
       |> removeSpaces
-- "helloworld"
```

### 関数のパイプライン化

```haskell
-- | パイプラインの作成
pipeline :: [a -> a] -> a -> a
pipeline = foldl' (flip (.)) id

-- 使用例
sanitize = pipeline [trim, toLowerCase, removeSpaces]
result = sanitize "  Hello World  "  -- "helloworld"
```

### 文字列変換関数

```haskell
-- | 空白のトリム
trim :: String -> String
trim = dropWhile isSpace . reverse . dropWhile isSpace . reverse

-- | 小文字化
toLowerCase :: String -> String
toLowerCase = map toLower

-- | 空白除去
removeSpaces :: String -> String
removeSpaces = filter (not . isSpace)

-- | 入力のサニタイズ
sanitizeInput :: String -> String
sanitizeInput = toLowerCase . trim
```

## 純粋関数

### 参照透過性

```haskell
-- | 純粋な加算
pureAdd :: Num a => a -> a -> a
pureAdd x y = x + y

-- | 純粋な乗算
pureMultiply :: Num a => a -> a -> a
pureMultiply x y = x * y

-- | 純粋な計算
pureCompute :: Int -> Int -> Int -> Int
pureCompute x y z = (x + y) * z

-- 同じ入力には常に同じ出力
pureCompute 2 3 4  -- 常に 20
pureCompute 2 3 4  -- 常に 20
```

### 副作用の分離

```haskell
-- | 計算の抽象化
data Computation a
  = Pure a
  | Effect (IO a)

instance Functor Computation where
  fmap f (Pure a) = Pure (f a)
  fmap f (Effect io) = Effect (fmap f io)

-- | 純粋な計算ステップ
pureStep :: a -> Computation a
pureStep = Pure

-- | 計算の実行
runComputation :: Computation a -> IO a
runComputation (Pure a) = return a
runComputation (Effect io) = io
```

## エラーハンドリング

### Result 型

```haskell
-- | 結果型
data Result e a
  = Success a
  | Failure e
  deriving (Show, Eq)

-- | 結果のマップ
mapResult :: (a -> b) -> Result e a -> Result e b
mapResult f (Success a) = Success (f a)
mapResult _ (Failure e) = Failure e

-- | 結果のフラットマップ
flatMapResult :: (a -> Result e b) -> Result e a -> Result e b
flatMapResult f (Success a) = f a
flatMapResult _ (Failure e) = Failure e

-- | Maybe から Result への変換
fromMaybe' :: e -> Maybe a -> Result e a
fromMaybe' _ (Just a) = Success a
fromMaybe' e Nothing = Failure e
```

### バリデーション

```haskell
-- | バリデーション結果
data Validation e a
  = Valid a
  | Invalid [e]
  deriving (Show, Eq)

-- | 単一のバリデーション
validate :: (a -> Bool) -> e -> a -> Validation e a
validate predicate err value =
  if predicate value
  then Valid value
  else Invalid [err]

-- | 複数ルールのバリデーション
validateAll :: [(a -> Bool, e)] -> a -> Validation e a
validateAll rules value =
  let errors = [err | (predicate, err) <- rules, not (predicate value)]
  in if null errors
     then Valid value
     else Invalid errors

-- 使用例
validateAge :: Int -> Validation String Int
validateAge = validateAll 
  [ ((>= 0), "Age must be non-negative")
  , ((<= 150), "Age must be realistic")
  ]

validateAge 25   -- Valid 25
validateAge (-5) -- Invalid ["Age must be non-negative"]
validateAge 200  -- Invalid ["Age must be realistic"]
```

### バリデーションの合成

```haskell
-- | 複数バリデーションの結合
combineValidations :: [Validation e a] -> Validation e [a]
combineValidations validations =
  let (valids, invalids) = partitionValidations validations
  in if null invalids
     then Valid valids
     else Invalid (concat invalids)

-- 使用例
let v1 = validate (> 0) "must be positive" 5
    v2 = validate (< 100) "must be < 100" 50
combineValidations [v1, v2]  -- Valid [5, 50]
```

## データ変換

### Map/Filter/Reduce

```haskell
-- | マップ
mapOver :: (a -> b) -> [a] -> [b]
mapOver = map

-- | フィルター
filterBy :: (a -> Bool) -> [a] -> [a]
filterBy = filter

-- | リデュース
reduceWith :: (b -> a -> b) -> b -> [a] -> b
reduceWith = foldl'

-- | 変換パイプライン
transformPipeline :: (a -> b) -> (b -> Bool) -> (c -> b -> c) -> c -> [a] -> c
transformPipeline mapF filterF reduceF initial xs =
  reduceWith reduceF initial (filterBy filterF (mapOver mapF xs))

-- 使用例：偶数を2倍して合計
result = transformPipeline (*2) even (+) 0 [1..5]  -- 12
```

### 遅延評価

```haskell
-- | 遅延レンジ
lazyRange :: Int -> Int -> [Int]
lazyRange start end = [start..end]

-- | 条件付き取得
takeWhile' :: (a -> Bool) -> [a] -> [a]
takeWhile' = takeWhile

-- | 無限シーケンス
infiniteSequence :: a -> (a -> a) -> [a]
infiniteSequence start next = iterate next start

-- 使用例
take 5 (infiniteSequence 1 (+1))   -- [1, 2, 3, 4, 5]
take 5 (infiniteSequence 2 (*2))   -- [2, 4, 8, 16, 32]
takeWhile' (< 100) (infiniteSequence 1 (*2))  -- [1, 2, 4, 8, 16, 32, 64]
```

## 型安全性

### Newtype パターン

```haskell
-- | Email newtype
newtype Email = Email { unEmail :: String }
  deriving (Show, Eq)

-- | スマートコンストラクタ
mkEmail :: String -> Maybe Email
mkEmail s
  | '@' `elem` s && '.' `elem` dropWhile (/= '@') s = Just (Email s)
  | otherwise = Nothing

-- | Username newtype
newtype Username = Username { unUsername :: String }
  deriving (Show, Eq)

mkUsername :: String -> Maybe Username
mkUsername s
  | length s >= 3 && length s <= 20 = Just (Username s)
  | otherwise = Nothing

-- | Age newtype
newtype Age = Age { unAge :: Int }
  deriving (Show, Eq)

mkAge :: Int -> Maybe Age
mkAge n
  | n >= 0 && n <= 150 = Just (Age n)
  | otherwise = Nothing
```

### 正の整数

```haskell
-- | 正の整数
newtype PositiveInt = PositiveInt { unPositiveInt :: Int }
  deriving (Show, Eq)

mkPositiveInt :: Int -> Maybe PositiveInt
mkPositiveInt n
  | n > 0 = Just (PositiveInt n)
  | otherwise = Nothing

-- | 空でない文字列
newtype NonEmptyString = NonEmptyString { unNonEmptyString :: String }
  deriving (Show, Eq)

mkNonEmptyString :: String -> Maybe NonEmptyString
mkNonEmptyString s
  | null s = Nothing
  | otherwise = Just (NonEmptyString s)
```

## テストのベストプラクティス

### 代数的性質のテスト

```haskell
-- | 結合律のテスト
associative :: Eq a => (a -> a -> a) -> a -> a -> a -> Bool
associative f a b c = f (f a b) c == f a (f b c)

-- | 交換律のテスト
commutative :: Eq b => (a -> a -> b) -> a -> a -> Bool
commutative f a b = f a b == f b a

-- | 単位元のテスト
identity' :: Eq a => (a -> a -> a) -> a -> a -> Bool
identity' f identity a = f a identity == a && f identity a == a

-- | 逆元のテスト
inverse :: Eq a => (a -> a -> a) -> (a -> a) -> a -> a -> Bool
inverse f inv identity a = f a (inv a) == identity
```

### プロパティベーステスト

```haskell
spec :: Spec
spec = do
  it "addition is associative" $ property $
    \(x :: Int) y z -> associative (+) x y z

  it "addition is commutative" $ property $
    \(x :: Int) y -> commutative (+) x y

  it "0 is identity for addition" $ property $
    \(x :: Int) -> identity' (+) 0 x
```

## まとめ

### 不変性の利点

- 予測可能な動作
- 並行処理の安全性
- デバッグの容易さ

### 関数合成の利点

- コードの再利用性
- 読みやすいパイプライン
- テストの容易さ

### 型安全性の利点

- コンパイル時のエラー検出
- 不正な状態の防止
- ドキュメントとしての型

### エラーハンドリングの原則

- 例外より戻り値
- 型でエラーを表現
- バリデーションの合成
