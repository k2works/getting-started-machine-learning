---
type: Article
title: "Part I - 逐次処理"
description: "パスワードクラッキングで学ぶ逐次処理の基礎"
tags: [article, grokking-concurrency, fsharp, concurrency, sequential]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part I: 逐次処理

## 概要

本章では、パスワードクラッキングを例に逐次処理の基礎を学びます。

---

## パスワードクラッカーの実装

### 組み合わせ生成

```fsharp
module PasswordCracker =
    let private letters = "abcdefghijklmnopqrstuvwxyz"

    /// Generate all combinations of lowercase letters of the given length
    let getCombinations (length: int) : string list =
        let rec generate (current: string list) (remaining: int) : string list =
            if remaining = 0 then
                current
            else
                let next =
                    [ for prefix in current do
                      for letter in letters do
                          yield prefix + string letter ]
                generate next (remaining - 1)
        generate [""] length
```

### ハッシュ計算

```fsharp
open System.Security.Cryptography
open System.Text

/// Calculate SHA-256 hash of the given password
let getCryptoHash (password: string) : string =
    let bytes = Encoding.UTF8.GetBytes(password)
    let hash = SHA256.HashData(bytes)
    hash |> Array.map (fun b -> sprintf "%02x" b) |> String.concat ""
```

### パスワード解析

```fsharp
/// Check if the password matches the given hash
let checkPassword (password: string) (cryptoHash: string) : bool =
    getCryptoHash password = cryptoHash

/// Crack password by brute force
let crackPassword (cryptoHash: string) (length: int) : string option =
    getCombinations length
    |> List.tryFind (fun p -> checkPassword p cryptoHash)
```

---

## F# の特徴

- **パイプライン演算子 (`|>`)**: データの変換を読みやすく表現
- **リスト内包表記**: `[ for x in xs do ... ]`
- **Option 型**: null の代わりに `Option<string>` で結果を表現
- **再帰関数**: `let rec` で再帰的に組み合わせを生成

---

## 次のステップ

[Part II](part-2.md) では、スレッドと Async を使った並列処理を学びます。
