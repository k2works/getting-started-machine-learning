---
type: Article
title: "Part I - 並行処理の基礎"
description: "逐次処理の基本と並行処理の必要性を学ぶ"
tags: [article, grokking-concurrency, csharp, concurrency, sequential]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part I: 並行処理の基礎

## 概要

本章では、逐次処理の基本概念を理解し、なぜ並行処理が必要になるのかを学びます。パスワードクラッカーの例を通じて、逐次処理の限界を体験します。

---

## 逐次処理の例: パスワードクラッカー

### SHA-256 ハッシュの計算

```csharp
public static string GetCryptoHash(string password)
{
    var bytes = Encoding.UTF8.GetBytes(password);
    var hash = SHA256.HashData(bytes);
    return Convert.ToHexString(hash).ToLowerInvariant();
}
```

### パスワード候補の生成

```csharp
public static List<string> GetCombinations(int length, int minNumber, int maxNumber)
{
    var combinations = new List<string>();
    var format = new string('0', length);

    for (var i = minNumber; i <= maxNumber; i++)
    {
        combinations.Add(i.ToString(format));
    }

    return combinations;
}
```

### ブルートフォース探索

```csharp
public static string? CrackPassword(string cryptoHash, int length)
{
    int maxNumber = (int)Math.Pow(10, length) - 1;
    var combinations = GetCombinations(length, 0, maxNumber);

    foreach (var combination in combinations)
    {
        if (CheckPassword(cryptoHash, combination))
        {
            return combination;
        }
    }

    return null;
}
```

---

## 逐次処理の特徴

| 特徴 | 説明 |
|------|------|
| 予測可能性 | 実行順序が明確 |
| デバッグ容易 | 問題の特定が簡単 |
| リソース効率 | オーバーヘッドなし |

---

## 逐次処理の限界

- CPU バウンドタスクでは 1 コアしか使用できない
- I/O 待機中に CPU がアイドル状態になる
- 大規模データ処理に時間がかかる

---

## 次のステップ

Part II では、スレッドを使った並列処理を学び、パスワードクラッカーを高速化します。

---

## 参考資料

- [System.Security.Cryptography](https://docs.microsoft.com/ja-jp/dotnet/api/system.security.cryptography)
