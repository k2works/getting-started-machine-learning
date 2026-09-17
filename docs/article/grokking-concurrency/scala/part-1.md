---
type: Article
title: "Part I - 逐次処理"
description: "パスワードクラッキングで学ぶ逐次処理の基礎"
tags: [article, grokking-concurrency, scala, concurrency, sequential]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part I: 逐次処理

## 概要

本章では、パスワードクラッキングを例に逐次処理の基礎を学びます。

---

## パスワードクラッカーの実装

### 組み合わせ生成

```scala
object PasswordCracker:
  private val Letters: String = "abcdefghijklmnopqrstuvwxyz"

  /** Generate all combinations of lowercase letters of the given length */
  def getCombinations(length: Int): List[String] =
    @tailrec
    def generate(current: List[String], remaining: Int): List[String] =
      if remaining == 0 then current
      else
        val next = for
          prefix <- current
          letter <- Letters
        yield prefix + letter
        generate(next, remaining - 1)

    generate(List(""), length)
```

### ハッシュ計算

```scala
import java.security.MessageDigest

/** Calculate SHA-256 hash of the given password */
def getCryptoHash(password: String): String =
  val digest = MessageDigest.getInstance("SHA-256")
  val bytes = digest.digest(password.getBytes("UTF-8"))
  bytes.map(b => f"$b%02x").mkString
```

### パスワード解析

```scala
/** Check if the password matches the given hash */
def checkPassword(password: String, cryptoHash: String): Boolean =
  getCryptoHash(password) == cryptoHash

/** Crack password by brute force */
def crackPassword(cryptoHash: String, length: Int): Option[String] =
  getCombinations(length).find(checkPassword(_, cryptoHash))
```

---

## 特徴

- **末尾再帰**: `@tailrec` アノテーションでスタックオーバーフローを防止
- **for 内包表記**: 宣言的な組み合わせ生成
- **Option 型**: null の代わりに `Option[String]` で結果を表現

---

## 次のステップ

[Part II](part-2.md) では、スレッドを使った並列処理を学びます。
