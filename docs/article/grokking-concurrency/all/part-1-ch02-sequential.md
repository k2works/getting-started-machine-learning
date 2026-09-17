---
type: Article
title: "Part I: 並行処理の基礎"
description: "並行処理を理解するためには、まず逐次処理（Sequential Processing）の特徴と限界を知る必要があります。"
tags: [article, grokking-concurrency, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part I: 並行処理の基礎

## 1.1 はじめに

並行処理を理解するためには、まず**逐次処理（Sequential Processing）**の特徴と限界を知る必要があります。本章では、パスワードクラッキングという計算量の多い問題を題材に、逐次処理がどのように動作し、なぜ並行処理が必要になるのかを 8 つの言語で実装しながら解説します。

### パスワードクラッキングとは

SHA-256 ハッシュから元のパスワードを逆算することは計算上不可能です。そのため、ブルートフォース（総当たり）で候補を生成し、ハッシュ値を比較するアプローチを取ります。このアルゴリズムは以下の 3 ステップで構成されます：

1. **候補生成** — パスワードの全組み合わせを生成する
2. **ハッシュ計算** — 各候補の SHA-256 ハッシュを計算する
3. **照合** — ターゲットのハッシュと一致するか確認する

## 1.2 共通の本質

### 逐次処理の構造

すべての言語で共通する逐次処理の構造は以下のとおりです：

```
入力: ターゲットハッシュ、探索空間（文字種 × 長さ）
出力: 一致するパスワード（見つからなければ None/null）

for each candidate in 探索空間:
    hash = SHA256(candidate)
    if hash == ターゲットハッシュ:
        return candidate
return None
```

### なぜ逐次処理では不十分か

8 桁のパスワード（数字のみ）の場合、探索空間は **1 億通り** です。逐次処理では CPU コアを 1 つしか使えないため、4 コア CPU でも **25% の利用率** にとどまります。残りの 75% の計算能力は無駄になります。

これが並行処理を学ぶ動機です。探索空間を分割し、複数のコアで同時に処理すれば、理論上はコア数に比例した高速化が可能になります。

### 共通の処理フロー

| ステップ | 関数名 | 説明 |
|---------|--------|------|
| 1 | `getCombinations` / `generate` | 候補パスワードの生成 |
| 2 | `getCryptoHash` | SHA-256 ハッシュの計算 |
| 3 | `checkPassword` | ハッシュの照合 |
| 4 | `crackPassword` | 逐次探索の実行 |

## 1.3 言語別実装比較

### 候補生成のアプローチ

8 言語の実装は、候補生成の方法で大きく 2 つに分かれます：

| アプローチ | 言語 | 探索空間 | 方法 |
|-----------|------|---------|------|
| 数値ゼロパディング | Python, Java, C# | `00000000`〜`99999999` | 数値をゼロ埋めして文字列化 |
| アルファベット直積 | Scala, F#, Rust, Haskell, Clojure | `aa`〜`zz`（26^n 通り） | 文字集合の直積で候補を再帰生成 |

### SHA-256 ハッシュ計算

#### 関数型ファースト言語

<details>
<summary>Haskell 実装</summary>

```haskell
module Ch02.PasswordCracker (getCryptoHash, crackPassword) where

import qualified Crypto.Hash.SHA256 as SHA256
import qualified Data.ByteString.Char8 as BS
import qualified Data.ByteString as BSW
import Data.ByteString (ByteString)

getCryptoHash :: String -> String
getCryptoHash password =
    let hash = SHA256.hash (BS.pack password)
    in concatMap (\w -> let hex = showHex w ""
                        in if length hex == 1 then '0' : hex else hex)
                 (BSW.unpack hash)
```

**特徴**:

- 純粋関数として定義（副作用なし）
- `ByteString` を使った効率的なバイト操作
- `concatMap` による関数型的なバイト→16 進数変換

</details>

<details>
<summary>Clojure 実装</summary>

```clojure
(ns grokking-concurrency.ch02.password-cracker
  (:require [buddy.core.hash :as hash]
            [buddy.core.codecs :as codecs]))

(defn sha256 [s]
  (-> (hash/sha256 s)
      (codecs/bytes->hex)))
```

**特徴**:

- スレッディングマクロ `->` によるデータフローの明示
- `buddy-core` ライブラリによる簡潔なハッシュ計算
- 関数型スタイルの合成

</details>

#### マルチパラダイム言語

<details>
<summary>Scala 実装</summary>

```scala
import java.security.MessageDigest

object PasswordCracker:
  def getCryptoHash(password: String): String =
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(password.getBytes("UTF-8"))
    bytes.map(b => f"$b%02x").mkString
```

**特徴**:

- `f"$b%02x"` 文字列補間による簡潔な 16 進数変換
- `map` + `mkString` の関数型パイプライン
- Java の `MessageDigest` を直接利用（JVM 互換）

</details>

<details>
<summary>Rust 実装</summary>

```rust
use sha2::{Sha256, Digest};

pub fn get_crypto_hash(password: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(password.as_bytes());
    let result = hasher.finalize();
    format!("{:x}", result)
}
```

**特徴**:

- `&str` 参照による所有権を移動しないインターフェース
- `sha2` クレートのビルダーパターン
- `format!` マクロによる 16 進数フォーマット

</details>

<details>
<summary>F# 実装</summary>

```fsharp
module PasswordCracker =
    open System.Security.Cryptography
    open System.Text

    let getCryptoHash (password: string) : string =
        let bytes = Encoding.UTF8.GetBytes(password)
        let hash = SHA256.HashData(bytes)
        hash
        |> Array.map (fun b -> b.ToString("x2"))
        |> String.concat ""
```

**特徴**:

- パイプ演算子 `|>` によるデータ変換パイプライン
- `Array.map` + `String.concat` の関数型合成
- .NET の `SHA256.HashData()` を直接利用

</details>

#### OOP + 並行処理ライブラリ言語

<details>
<summary>Java 実装</summary>

```java
import java.security.MessageDigest;
import java.util.HexFormat;

public class PasswordCracker {
    public static String getCryptoHash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

**特徴**:

- `MessageDigest` による標準的なハッシュ API
- Java 17+ の `HexFormat` による簡潔な 16 進数変換
- チェック例外のラップ処理

</details>

<details>
<summary>C# 実装</summary>

```csharp
using System.Security.Cryptography;
using System.Text;

public static class PasswordCracker
{
    public static string GetCryptoHash(string password)
    {
        var bytes = Encoding.UTF8.GetBytes(password);
        var hash = SHA256.HashData(bytes);
        return Convert.ToHexString(hash).ToLowerInvariant();
    }
}
```

**特徴**:

- `SHA256.HashData()` のワンライナー API
- `Convert.ToHexString()` による .NET 5+ スタイルの変換
- PascalCase の .NET 命名規則

</details>

<details>
<summary>Python 実装</summary>

```python
import hashlib

def get_crypto_hash(password: str) -> str:
    return hashlib.sha256(password.encode("utf-8")).hexdigest()
```

**特徴**:

- `hashlib` 標準ライブラリによるワンライナー
- 型ヒント付きの簡潔な定義
- 最も短いコード量

</details>

### 候補生成と探索

#### 数値ゼロパディング方式（Python, Java, C#）

<details>
<summary>Python 実装</summary>

```python
def get_combinations(*, length: int, min_number: int = 0,
                     max_number: T.Optional[int] = None) -> T.List[str]:
    if max_number is None:
        max_number = int("9" * length)
    combinations = []
    for i in range(min_number, max_number + 1):
        str_num = str(i)
        zeros = "0" * (length - len(str_num))
        combinations.append("".join((zeros, str_num)))
    return combinations
```

</details>

<details>
<summary>Java 実装</summary>

```java
public static List<String> getCombinations(int length, int minNumber, int maxNumber) {
    List<String> combinations = new ArrayList<>();
    String format = "%0" + length + "d";
    for (int i = minNumber; i <= maxNumber; i++) {
        combinations.add(String.format(format, i));
    }
    return combinations;
}
```

</details>

<details>
<summary>C# 実装</summary>

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

</details>

#### アルファベット直積方式（Scala, F#, Rust, Haskell, Clojure）

<details>
<summary>Scala 実装（末尾再帰 + for-yield）</summary>

```scala
private val Letters = ('a' to 'z').toList

def getCombinations(length: Int): List[String] =
  @tailrec
  def generate(current: List[String], remaining: Int): List[String] =
    if remaining <= 0 then current
    else
      val next = for
        prefix <- current
        letter <- Letters
      yield prefix + letter
      generate(next, remaining - 1)

  generate(List(""), length)
```

</details>

<details>
<summary>F# 実装（リスト内包表記 + パイプ）</summary>

```fsharp
let getCombinations (length: int) : string list =
    let letters = [ 'a' .. 'z' ]

    let rec generate (current: string list) (remaining: int) =
        if remaining <= 0 then current
        else
            let next =
                [ for prefix in current do
                  for letter in letters do
                      yield prefix + string letter ]
            generate next (remaining - 1)

    generate [ "" ] length
```

</details>

<details>
<summary>Rust 実装（再帰 + clone）</summary>

```rust
fn crack_recursive(
    crypto_hash: &str,
    alphabet: &[char],
    prefix: String,
    remaining: usize,
) -> Option<String> {
    if remaining == 0 {
        return if get_crypto_hash(&prefix) == crypto_hash {
            Some(prefix)
        } else {
            None
        };
    }
    for &c in alphabet {
        let mut candidate = prefix.clone();
        candidate.push(c);
        if let Some(result) = crack_recursive(crypto_hash, alphabet, candidate, remaining - 1) {
            return Some(result);
        }
    }
    None
}
```

</details>

<details>
<summary>Haskell 実装（foldr + Maybe）</summary>

```haskell
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
            Nothing     -> acc
```

</details>

<details>
<summary>Clojure 実装（loop/recur + 基数変換）</summary>

```clojure
(def alphabet "abcdefghijklmnopqrstuvwxyz")

(defn generate-password [index length]
  (let [base (count alphabet)]
    (loop [idx index
           acc '()]
      (if (= (count acc) length)
        (apply str acc)
        (recur (quot idx base)
               (cons (nth alphabet (mod idx base)) acc))))))

(defn crack-password [target-hash length]
  (let [total (long (Math/pow (count alphabet) length))]
    (loop [i 0]
      (when (< i total)
        (let [password (generate-password i length)]
          (if (= (sha256 password) target-hash)
            password
            (recur (inc i))))))))
```

</details>

### 戻り値の型表現

「パスワードが見つからない」ケースの表現方法は、言語の型システムに大きく依存します：

| 言語 | 戻り値型 | 「見つからない」の表現 |
|------|---------|---------------------|
| Python | `None`（暗黙） | 関数が何も返さない |
| Java | `String` (nullable) | `null` を返す |
| C# | `string?` | `null` を返す |
| Scala | `Option[String]` | `None` を返す |
| F# | `string option` | `None` を返す |
| Rust | `Option<String>` | `None` を返す |
| Haskell | `Maybe String` | `Nothing` を返す |
| Clojure | 動的（`nil`） | `nil` を返す |

**安全性の階層**:

- **コンパイル時保証**: Rust (`Option<T>`), Haskell (`Maybe`), Scala (`Option`), F# (`option`)
  - パターンマッチを強制し、`null` 参照エラーを防止
- **型ヒント**: Python (`Optional[T]`), C# (`string?`)
  - IDE とリンターが警告を出すが、ランタイムでは強制されない
- **規約ベース**: Java (`null`), Clojure (`nil`)
  - 呼び出し側の注意に依存

## 1.4 比較分析

### ランタイムとハッシュライブラリ

| 言語 | ハッシュライブラリ | 依存関係 |
|------|-----------------|---------|
| Python | `hashlib`（標準） | なし |
| Java | `MessageDigest`（標準） | なし |
| C# | `SHA256`（標準） | なし |
| Scala | `MessageDigest`（JVM 標準） | なし |
| F# | `SHA256`（.NET 標準） | なし |
| Rust | `sha2` クレート | 外部依存 |
| Haskell | `cryptohash-sha256` | 外部依存 |
| Clojure | `buddy-core` | 外部依存 |

Python, Java, C#, Scala, F# は標準ライブラリだけで SHA-256 を計算できます。一方、Rust, Haskell, Clojure は外部ライブラリに依存しますが、より洗練された API を提供しています。

### コードの簡潔さ

SHA-256 ハッシュ計算のコード行数を比較すると、言語の表現力の違いが明確になります：

| 言語 | 行数 | 特徴 |
|------|------|------|
| Python | 1 行 | `hashlib` のワンライナー |
| Clojure | 2 行 | スレッディングマクロ |
| C# | 3 行 | `SHA256.HashData()` + `Convert.ToHexString()` |
| Rust | 4 行 | ビルダーパターン |
| Scala | 3 行 | `map` + `mkString` |
| F# | 4 行 | パイプ演算子チェーン |
| Java | 5 行 | `MessageDigest` + 例外処理 |
| Haskell | 4 行 | `ByteString` 操作 + `concatMap` |

### 探索アルゴリズムの設計思想

| アプローチ | 言語 | メリット | デメリット |
|-----------|------|---------|-----------|
| 命令型ループ | Python, Java, C# | 直感的、デバッグしやすい | 並列化の際にインデックス分割が必要 |
| 末尾再帰 | Scala, F# | スタックオーバーフロー防止、FP らしい | 末尾再帰最適化が必要 |
| 再帰 + 早期リターン | Rust, Haskell | 探索木の自然な表現 | スタック消費（深い探索では問題） |
| loop/recur | Clojure | JVM 上でスタックセーフ | Lisp 構文に慣れが必要 |

## 1.5 実践的な選択指針

### ユースケース別の推奨

**プロトタイピング・学習用途**:

- **Python** — 最も簡潔で、標準ライブラリだけで完結
- **Clojure** — REPL 駆動開発で素早く実験可能

**型安全性を重視する場合**:

- **Rust** — コンパイル時にメモリ安全性と型安全性を保証
- **Haskell** — 純粋関数型で副作用を型レベルで管理

**エンタープライズ環境**:

- **Java / C#** — 豊富なエコシステムと標準ライブラリ
- **Scala / F#** — 既存の JVM/.NET 資産を活用しつつ関数型の利点を享受

### 並行化への準備度

次章（Part II）で探索を並列化する際、各言語の逐次実装がどれだけスムーズに並行版に移行できるかは重要な指標です：

| 言語 | 並行化の容易さ | 理由 |
|------|-------------|------|
| Rust | 高い | 所有権システムが並行安全性をコンパイル時に保証 |
| Haskell | 高い | 純粋関数は副作用がなく、安全に並列実行可能 |
| Clojure | 高い | 不変データ構造により共有状態の問題が発生しない |
| Scala | 高い | `Future` や `.par` で容易に並列化可能 |
| F# | 高い | `Async` ワークフローで自然に並列化 |
| Java | 中程度 | `ExecutorService` で並列化可能だが、共有状態の管理は手動 |
| C# | 中程度 | `Parallel.ForEach` や `Task` で並列化可能 |
| Python | 低い | GIL の制約により `threading` では真の並列化ができず、`multiprocessing` が必要 |

## 1.6 まとめ

### 言語横断的な学び

1. **逐次処理の限界は普遍的** — どの言語で書いても、逐次処理では 1 コアしか活用できない
2. **候補生成の設計が並行化を左右する** — 探索空間の分割しやすさが重要
3. **型安全な戻り値は保守性を高める** — `Option`/`Maybe` を使う言語は `null` 参照エラーを防止
4. **言語の思想が実装に現れる** — 命令型ループ vs 再帰 vs パイプラインの違い

### 次のステップ

[Part II: プロセスとスレッド](./part-2-ch04-05-threads.md) では、この逐次処理をマルチスレッドで並列化し、CPU の全コアを活用する方法を学びます。

### 各言語の個別記事

| 言語 | 個別記事 |
|------|---------|
| Python | [Part I - 並行処理の基礎](../python/part-1.md) |
| Java | [Part I - 並行処理の基礎](../java/part-1.md) |
| C# | [Part I - 並行処理の基礎](../csharp/part-1.md) |
| Scala | [Part I - 逐次処理](../scala/part-1.md) |
| F# | [Part I - 逐次処理](../fsharp/part-1.md) |
| Rust | [Part I - 並行処理の基礎](../rust/part-1.md) |
| Haskell | [Part I - 並行処理の基礎](../haskell/part-1.md) |
| Clojure | [Part I - 逐次処理](../clojure/part-1.md) |
