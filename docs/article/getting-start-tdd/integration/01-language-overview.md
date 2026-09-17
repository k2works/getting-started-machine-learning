---
type: Article
title: "12 言語の概要と分類"
description: "本章では、12 言語を複数の軸で分類し、それぞれの設計思想と特徴を俯瞰します。"
tags: [article, getting-start-tdd, integration]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 12 言語の概要と分類

本章では、12 言語を複数の軸で分類し、それぞれの設計思想と特徴を俯瞰します。最後に、各言語の FizzBuzz コア実装を比較することで、同一の仕様が言語ごとにどう表現されるかを確認します。

## パラダイム別分類

プログラミング言語はその設計思想に基づいて、大きく以下のパラダイムに分類できます。

### OOP（オブジェクト指向プログラミング）

クラスとオブジェクトを中心に、カプセル化・継承・ポリモーフィズムで設計する言語です。

| 言語 | 特徴 |
|------|------|
| Java | 静的型付け、インターフェース、豊富なエコシステム |
| C# | .NET ランタイム、LINQ、パターンマッチング対応 |
| PHP | Web 特化、漸進的型付け、名前空間 |

### FP（関数型プログラミング）

関数を第一級オブジェクトとし、不変データ・副作用の分離を重視する言語です。

| 言語 | 特徴 |
|------|------|
| Haskell | 純粋関数型、型クラス、モナド、遅延評価 |
| Clojure | LISP 方言、動的型付け、不変データ構造、プロトコル |
| Elixir | BEAM VM、パターンマッチング、パイプライン、OTP |
| F# | .NET ランタイム、判別共用体、パイプライン演算子 |

### マルチパラダイム

OOP と FP の両方を自然にサポートする言語です。

| 言語 | OOP 要素 | FP 要素 |
|------|---------|---------|
| Python | クラス、継承、ABC | デコレータ、ジェネレータ、functools |
| TypeScript | クラス、インターフェース | アロー関数、型ガード、ユニオン型 |
| Ruby | すべてがオブジェクト | ブロック/Proc/Lambda、Enumerable |
| Rust | trait、構造体 | パターンマッチング、イテレータ、Option/Result |
| Scala | クラス、trait | sealed trait/case class、for 内包表記 |
| Go | 構造体、インターフェース | ファーストクラス関数、クロージャ |

## 型システムによる分類

型システムは言語の安全性と表現力を大きく左右します。

### 静的型付け vs 動的型付け

| 分類 | 言語 | 型チェックのタイミング |
|------|------|---------------------|
| 静的型付け | Java, TypeScript, Go, Rust, C#, F#, Scala, Haskell | コンパイル時 |
| 動的型付け | Python, Ruby, PHP, Clojure, Elixir | 実行時 |

### 型の強さのスペクトル

型の強さは「暗黙の型変換をどれだけ許容するか」で測ることができます。

```
弱い型付け ←────────────────────────────→ 強い型付け

PHP  JavaScript  Python  Ruby  Java  Go  C#  F#  Scala  Rust  Haskell
```

| レベル | 言語例 | 特徴 |
|--------|-------|------|
| 非常に強い | Haskell, Rust | 暗黙の変換がほぼない、型推論が強力 |
| 強い | Java, C#, F#, Scala, Go | 明示的なキャストが必要 |
| 中程度 | Python, Ruby, TypeScript | 一部の暗黙変換を許容 |
| 比較的柔軟 | PHP, Clojure, Elixir | 動的だが実行時にチェック |

### 型推論の能力

| レベル | 言語 | 例 |
|--------|------|-----|
| 完全推論（HM 型推論） | Haskell, F# | ほぼすべてのローカル型を推論 |
| 高い推論能力 | Rust, Scala, TypeScript | ローカル変数・ジェネリクスの推論 |
| 中程度 | Java, C#, Go | `var` / `:=` によるローカル推論 |
| 限定的 | Python, Ruby, PHP | 型ヒント/アノテーションで補完 |
| 不要 | Clojure, Elixir | 動的型付けのため型推論が不要 |

## ランタイム環境による分類

言語がどの実行環境上で動作するかは、パフォーマンス特性やエコシステムに影響します。

### JVM（Java Virtual Machine）

| 言語 | JVM 上の特徴 |
|------|-------------|
| Java | JVM のネイティブ言語、最大のエコシステム |
| Scala | JVM + 独自の OOP/FP 融合 |
| Clojure | JVM + LISP、Java ライブラリとの相互運用 |

### BEAM（Erlang VM）

| 言語 | BEAM 上の特徴 |
|------|-------------|
| Elixir | 軽量プロセス、耐障害性、ホットコードスワップ |

### CLR（.NET Common Language Runtime）

| 言語 | CLR 上の特徴 |
|------|-------------|
| C# | .NET のプライマリ言語、クロスプラットフォーム |
| F# | .NET 上の関数型ファースト言語 |

### ネイティブコンパイル

| 言語 | 特徴 |
|------|------|
| Rust | ゼロコスト抽象化、所有権システム、メモリ安全性 |
| Go | 高速コンパイル、静的リンク、ゴルーチン |
| Haskell | GHC によるネイティブコンパイル、遅延評価 |

### インタプリタ / JIT

| 言語 | 特徴 |
|------|------|
| Python | CPython インタプリタ、豊富な科学計算ライブラリ |
| Ruby | CRuby、DSL 構築に強い |
| PHP | Zend Engine、Web に特化 |
| TypeScript | Node.js (V8 JIT) 上で実行 |

## FizzBuzz コア実装の比較

同一の FizzBuzz 仕様を各言語でどう表現するかを比較します。いずれも「数値を受け取り、3 の倍数なら "Fizz"、5 の倍数なら "Buzz"、両方の倍数なら "FizzBuzz"、それ以外は数値の文字列を返す」という仕様です。

### Java

```java
public abstract class FizzBuzzType {
    protected static final int FIZZ_NUMBER = 3;
    protected static final int BUZZ_NUMBER = 5;

    public abstract FizzBuzzValue generate(int number);

    protected boolean isFizz(int number) {
        return number % FIZZ_NUMBER == 0;
    }

    protected boolean isBuzz(int number) {
        return number % BUZZ_NUMBER == 0;
    }

    protected boolean isFizzBuzz(int number) {
        return isFizz(number) && isBuzz(number);
    }
}
```

### Python

```python
class FizzBuzzType(ABC):
    @abstractmethod
    def generate(self, number: int) -> FizzBuzzValue:
        pass

    def _is_fizz(self, number: int) -> bool:
        return number % 3 == 0

    def _is_buzz(self, number: int) -> bool:
        return number % 5 == 0

    def _is_fizz_buzz(self, number: int) -> bool:
        return number % 15 == 0
```

### TypeScript

```typescript
export abstract class FizzBuzzType {
  static readonly TYPE_01 = 1;
  static readonly TYPE_02 = 2;
  static readonly TYPE_03 = 3;

  abstract generate(number: number): FizzBuzzValue;

  static create(type: number = FizzBuzzType.TYPE_01): FizzBuzzType {
    switch (type) {
      case FizzBuzzType.TYPE_01: return new FizzBuzzType01();
      case FizzBuzzType.TYPE_02: return new FizzBuzzType02();
      case FizzBuzzType.TYPE_03: return new FizzBuzzType03();
      default: throw new Error(`未定義のタイプ: ${type}`);
    }
  }
}
```

### Ruby

FizzBuzzType の継承による型分け（OOP スタイル）を採用しています。

```ruby
class FizzBuzzType
  def generate(number)
    raise NotImplementedError
  end

  private

  def fizz?(number) = (number % 3).zero?
  def buzz?(number) = (number % 5).zero?
  def fizz_buzz?(number) = fizz?(number) && buzz?(number)
end
```

### Go

インターフェースと構造体による構造化プログラミングスタイルです。

```go
func Generate(number int) string {
    switch {
    case number%15 == 0:
        return "FizzBuzz"
    case number%3 == 0:
        return "Fizz"
    case number%5 == 0:
        return "Buzz"
    default:
        return strconv.Itoa(number)
    }
}
```

### PHP

```php
final class FizzBuzz
{
    public function generate(int $number): string
    {
        $type = new FizzBuzzType01();
        return $type->generate($number)->getValue();
    }
}
```

### Rust

パターンマッチングによる簡潔な表現です。

```rust
pub fn generate(number: i32) -> String {
    match (number % 3, number % 5) {
        (0, 0) => "FizzBuzz".to_string(),
        (0, _) => "Fizz".to_string(),
        (_, 0) => "Buzz".to_string(),
        _ => number.to_string(),
    }
}
```

### C#

Factory パターンによる型安全な生成です。

```csharp
public static class FizzBuzzRunner
{
    public static string Generate(int number)
    {
        var type = FizzBuzzTypeFactory.Create(FizzBuzzTypeName.Standard);
        var value = type.Generate(number);
        return value.Value;
    }
}
```

### F#

判別共用体とパイプラインによる関数型アプローチです。

```fsharp
type FizzBuzzType =
    | Standard
    | NumberOnly
    | FizzBuzzOnly

let generate (fizzBuzzType: FizzBuzzType) (number: int) : FizzBuzzValue =
    match fizzBuzzType with
    | Standard ->
        if isFizzBuzz number then createValue number "FizzBuzz"
        elif isFizz number then createValue number "Fizz"
        elif isBuzz number then createValue number "Buzz"
        else createValue number (string number)
    | NumberOnly -> createValue number (string number)
    | FizzBuzzOnly ->
        if number % 15 = 0 then createValue number "FizzBuzz"
        else createValue number (string number)
```

### Clojure

プロトコルと defrecord による多態性です。

```clojure
(defn fizzbuzz [n]
  (cond
    (and (zero? (mod n 3)) (zero? (mod n 5))) "FizzBuzz"
    (zero? (mod n 3)) "Fizz"
    (zero? (mod n 5)) "Buzz"
    :else (str n)))
```

### Scala

パターンマッチングとガード節による表現です。

```scala
object FizzBuzz:
  def generate(number: Int): String =
    number match
      case n if n % 15 == 0 => "FizzBuzz"
      case n if n % 3 == 0  => "Fizz"
      case n if n % 5 == 0  => "Buzz"
      case n                => n.toString
```

### Elixir

パターンマッチングとガード節、パイプラインによる関数型スタイルです。

```elixir
def generate(number) when is_integer(number) and number > 0 do
  cond do
    rem(number, 15) == 0 -> "FizzBuzz"
    rem(number, 3) == 0 -> "Fizz"
    rem(number, 5) == 0 -> "Buzz"
    true -> Integer.to_string(number)
  end
end
```

### Haskell

ガード節による宣言的な定義です。

```haskell
generate :: Int -> String
generate n
  | n `mod` 15 == 0 = "FizzBuzz"
  | n `mod` 3 == 0  = "Fizz"
  | n `mod` 5 == 0  = "Buzz"
  | otherwise        = show n
```

## 実装パターンの比較まとめ

| 言語 | 分岐方法 | 型の安全性 | コード量 |
|------|---------|-----------|---------|
| Java | if/switch + 抽象クラス | コンパイル時チェック | 多い |
| Python | if/elif + ABC | 型ヒントによる補完 | 中程度 |
| TypeScript | switch + abstract class | コンパイル時チェック | 中程度 |
| Ruby | if/case + 継承 | 動的ダックタイピング | 少ない |
| Go | switch + interface | コンパイル時チェック | 中程度 |
| PHP | match + 抽象クラス | 実行時チェック | 中程度 |
| Rust | match（タプル） | コンパイル時 + 所有権 | 少ない |
| C# | Factory + interface | コンパイル時チェック | 中程度 |
| F# | match + 判別共用体 | コンパイル時 + 網羅性 | 少ない |
| Clojure | cond + protocol | 実行時チェック | 少ない |
| Scala | match + ガード節 | コンパイル時チェック | 少ない |
| Elixir | cond + ガード節 | 実行時チェック | 少ない |
| Haskell | ガード節 | コンパイル時 + 型クラス | 非常に少ない |

## まとめ

12 言語の概要を俯瞰すると、以下の傾向が見えてきます。

1. **OOP 言語**（Java, C#, PHP）は、クラス階層とインターフェースにより構造化された FizzBuzz 実装となります
2. **FP 言語**（Haskell, Clojure, Elixir, F#）は、パターンマッチングや条件式で簡潔に表現できます
3. **マルチパラダイム言語**（Rust, Scala, TypeScript, Python, Ruby, Go）は、状況に応じて OOP と FP を使い分けることができます
4. **パターンマッチング**を持つ言語（Rust, Scala, F#, Haskell, Elixir）は、FizzBuzz のような条件分岐を特に簡潔に書けます

次章では、これらの言語のテストフレームワークを詳しく比較します。
