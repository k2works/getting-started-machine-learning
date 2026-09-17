---
type: Article
title: "Part I - 並行処理の基礎"
description: "逐次処理とパスワードクラッカーの実装"
tags: [article, grokking-concurrency, haskell, concurrency, sequential]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part I: 並行処理の基礎

## 概要

本章では、並行処理の基礎として逐次処理を学びます。

---

## パスワードクラッカーの実装

### SHA-256 ハッシュ計算

```haskell
{-# LANGUAGE OverloadedStrings #-}

module Ch02.PasswordCracker where

import Crypto.Hash.SHA256 (hash)
import Data.ByteString.Builder (byteStringHex, toLazyByteString)
import qualified Data.ByteString.Char8 as C8
import qualified Data.ByteString.Lazy as BL

-- | Compute SHA-256 hash of a password
getCryptoHash :: String -> String
getCryptoHash password =
    let hashBytes = hash (C8.pack password)
        hexBytes = toLazyByteString (byteStringHex hashBytes)
    in C8.unpack (BL.toStrict hexBytes)
```

---

### ブルートフォース探索

```haskell
-- | Crack password by brute force (sequential)
crackPassword :: String -> String -> Int -> Maybe String
crackPassword _ _ 0 = Nothing
crackPassword cryptoHash alphabet len =
    crackRecursive cryptoHash alphabet "" len

crackRecursive :: String -> String -> String -> Int -> Maybe String
crackRecursive cryptoHash alphabet prefix 0 =
    if getCryptoHash prefix == cryptoHash
        then Just prefix
        else Nothing
crackRecursive cryptoHash alphabet prefix remaining =
    foldr tryChar Nothing alphabet
  where
    tryChar c acc =
        case crackRecursive cryptoHash alphabet (prefix ++ [c]) (remaining - 1) of
            Just result -> Just result
            Nothing -> acc
```

---

## 使用例

```haskell
main :: IO ()
main = do
    let alphabet = "ab"
    let targetHash = getCryptoHash "ab"

    case crackPassword targetHash alphabet 2 of
        Just password -> putStrLn $ "Found: " ++ password
        Nothing -> putStrLn "Not found"
```

---

## Haskell の特徴

| 概念 | 説明 |
|------|------|
| Maybe a | 値が存在しない可能性を表現 |
| 純粋関数 | 副作用なし、参照透過性 |
| 遅延評価 | 必要になるまで計算しない |
| foldr | 右畳み込みによる短絡評価 |

---

## 次のステップ

[Part II](part-2.md) では、スレッドを使った並行処理を学びます。
