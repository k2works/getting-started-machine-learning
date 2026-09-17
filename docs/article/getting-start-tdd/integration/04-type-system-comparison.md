---
type: Article
title: "型システムとエラーハンドリング比較"
description: "本章では、12 言語の型システムとエラーハンドリングのアプローチを比較します。"
tags: [article, getting-start-tdd, integration]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-27T04:43:33Z }
---

# 型システムとエラーハンドリング比較

本章では、12 言語の型システムとエラーハンドリングのアプローチを比較します。型システムの強さはコードの安全性に直結し、エラーハンドリングの方法はプログラムの堅牢性を左右します。

## 静的型付け vs 動的型付け

### 静的型付け言語

コンパイル時（またはトランスパイル時）に型チェックが行われる言語です。

| 言語 | 型宣言 | 型推論 | ジェネリクス |
|------|-------|-------|------------|
| Java | 必須（var で推論可） | ローカル変数 | あり |
| TypeScript | オプション（推論優先） | 強い | あり |
| Go | 必須（`:=` で推論可） | ローカル変数 | あり（1.18+） |
| Rust | オプション（推論優先） | 非常に強い | あり |
| C# | 必須（var で推論可） | ローカル変数 | あり |
| F# | オプション（推論優先） | HM 型推論 | あり |
| Scala | オプション（推論優先） | 強い | あり |
| Haskell | オプション（推論優先） | HM 型推論 | あり（型クラス） |

**メリット**:


- コンパイル時にバグを検出できる
- IDE の補完・リファクタリングが強力
- ドキュメントとしての型情報

**FizzBuzz における恩恵**: FizzBuzzType の create メソッドで不正なタイプ番号を渡した場合、静的型付けでは enum や判別共用体によりコンパイル時に検出できます。

### 動的型付け言語

実行時に型チェックが行われる言語です。

| 言語 | 型ヒント | 静的解析ツール |
|------|---------|-------------|
| Python | 型ヒント（PEP 484） | mypy, Ruff |
| Ruby | RBS / Sorbet | Steep |
| PHP | 型宣言（PHP 7+） | PHPStan, Psalm |
| Clojure | Spec | spec.alpha |
| Elixir | Typespec | Dialyzer |

**メリット**:


- 素早いプロトタイピング
- 柔軟なデータ操作
- REPL での探索的開発

**FizzBuzz における考慮点**: 動的型付けでは実行時エラーを TDD で補完する必要があります。テストカバレッジの重要性が増します。

### 比較コード例

同じ FizzBuzz 値オブジェクトの定義を比較します。

```java
// Java: 静的型付け
public class FizzBuzzValue {
    private final int number;
    private final String value;

    public FizzBuzzValue(int number, String value) {
        this.number = number;
        this.value = value;
    }
}
```

```python
# Python: 動的型付け + 型ヒント
class FizzBuzzValue:
    def __init__(self, number: int, value: str) -> None:
        self._number = number
        self._value = value
```

```haskell
-- Haskell: 静的型付け + 型推論
-- 型は関数シグネチャで宣言
generate :: Int -> String
generate n
  | n `mod` 15 == 0 = "FizzBuzz"
```

```clojure
;; Clojure: 動的型付け
(defn fizzbuzz [n]
  (cond
    (and (zero? (mod n 3)) (zero? (mod n 5))) "FizzBuzz"
    (zero? (mod n 3)) "Fizz"
    (zero? (mod n 5)) "Buzz"
    :else (str n)))
```

## Option / Maybe / Result パターンの比較

null や例外に頼らず、型で「値が存在しない可能性」や「処理が失敗する可能性」を表現するパターンです。

### Rust: Option と Result

Rust は null を持たず、Option と Result で安全にエラーを扱います。

```rust
// Option: 値が存在しない可能性
pub fn create(type_number: i32) -> Result<Box<dyn FizzBuzzType>, String> {
    let type_name = FizzBuzzTypeName::from_number(type_number)?;
    Ok(type_name.create_type())
}

// テスト
#[test]
fn test_create_有効な種別番号で生成できる() {
    let t = create(1).expect("type should be created");
    assert_eq!("Fizz", t.generate(3).value());
}

#[test]
fn test_create_無効な種別番号はエラーを返す() {
    assert!(create(99).is_err());
}
```

### Haskell: Either

Haskell は Either モナドでエラーを型安全に扱います。

```haskell
-- Either: Left がエラー、Right が成功
safeGenerate :: Int -> Either String String
safeGenerate n
  | n <= 0    = Left "正の整数を指定してください"
  | otherwise = Right (generate n)

-- テスト
it "正の整数で成功する" $
  safeGenerate 3 `shouldBe` Right "Fizz"

it "0 以下でエラーを返す" $
  safeGenerate 0 `shouldBe` Left "正の整数を指定してください"
```

### Scala: Option と Either

Scala は Option と Either の両方を提供します。

```scala
// Option: 値の有無
def safeGenerate(number: Int): Option[String] =
  if number > 0 then Some(generate(number))
  else None

// Either: エラー情報付き
def generateEither(number: Int): Either[String, String] =
  if number > 0 then Right(generate(number))
  else Left(s"正の整数が必要です: $number")
```

### F#: Option と Result

F# は Option と Result を標準で提供します。

```fsharp
// Option
let tryGenerate number =
    if number > 0 then Some (generate Standard number)
    else None

// Result
let safeGenerate number =
    if number > 0 then Ok (generate Standard number)
    else Error "正の整数を指定してください"
```

### Elixir: {:ok, value} / {:error, reason}

Elixir はタプルでエラーを表現する慣習があります。

```elixir
def safe_generate(number) when is_integer(number) and number > 0 do
  {:ok, generate(number)}
end

def safe_generate(_number) do
  {:error, "正の整数を指定してください"}
end

# テスト
test "正の整数で {:ok, value} を返す" do
  assert FizzBuzz.safe_generate(1) == {:ok, "1"}
  assert FizzBuzz.safe_generate(3) == {:ok, "Fizz"}
end

test "0 以下で {:error, reason} を返す" do
  assert FizzBuzz.safe_generate(0) == {:error, "正の整数を指定してください"}
end
```

### Clojure: ex-info / 条件付き戻り値

Clojure は Map や例外で柔軟にエラーを扱います。

```clojure
;; 安全な生成（Map で結果を表現）
(defn safe-fizzbuzz [n]
  (if (pos? n)
    {:ok (fizzbuzz n)}
    {:error "正の整数を指定してください"}))
```

### Java: Optional

Java 8 以降は Optional でnull 安全を提供します。

```java
public static Optional<FizzBuzzType> createOptional(int type) {
    switch (type) {
        case TYPE_CODE_01: return Optional.of(new FizzBuzzType01());
        case TYPE_CODE_02: return Optional.of(new FizzBuzzType02());
        case TYPE_CODE_03: return Optional.of(new FizzBuzzType03());
        default: return Optional.empty();
    }
}
```

### Go: 多値返却

Go は多値返却で error を返す慣習があります。

```go
func TryNewFizzBuzzType(typeNum int) (FizzBuzzType, error) {
    switch typeNum {
    case 1:
        return &FizzBuzzType01{}, nil
    case 2:
        return &FizzBuzzType02{}, nil
    case 3:
        return &FizzBuzzType03{}, nil
    default:
        return nil, fmt.Errorf("該当するタイプは存在しません: %d", typeNum)
    }
}
```

### TypeScript: ユニオン型

TypeScript はユニオン型でエラーを型安全に表現できます。

```typescript
static tryCreate(typeName: FizzBuzzTypeName): FizzBuzzType | undefined {
  const typeMap: Record<FizzBuzzTypeName, () => FizzBuzzType> = {
    [FizzBuzzTypeName.TYPE_01]: () => new FizzBuzzType01(),
    [FizzBuzzTypeName.TYPE_02]: () => new FizzBuzzType02(),
    [FizzBuzzTypeName.TYPE_03]: () => new FizzBuzzType03(),
  };
  return typeMap[typeName]?.();
}
```

### Option/Result パターン比較表

| 言語 | 値の不在 | エラー付き失敗 | チェーン方法 |
|------|---------|-------------|------------|
| Rust | `Option<T>` | `Result<T, E>` | `?` 演算子、`and_then` |
| Haskell | `Maybe a` | `Either e a` | `do` 記法、`>>=` |
| Scala | `Option[T]` | `Either[E, T]` | `for` 内包表記、`flatMap` |
| F# | `Option<'T>` | `Result<'T, 'E>` | `|>` パイプライン |
| Elixir | `:ok` / `:error` タプル | `:ok` / `:error` タプル | `with` 構文 |
| Clojure | `nil` | `ex-info` / Map | スレッディングマクロ |
| Java | `Optional<T>` | 例外 / `Optional` | `map` / `flatMap` |
| Go | ゼロ値 | `(T, error)` | `if err != nil` |
| TypeScript | `T \| undefined` | `T \| Error` | Optional chaining `?.` |
| Python | `None` | 例外 | `try/except` |
| Ruby | `nil` | 例外 | `rescue` |
| PHP | `null` | 例外 | `try/catch` |
| C# | `null` / `T?` | 例外 | `?.` null 条件演算子 |

## エラーハンドリングの各言語アプローチ

エラーハンドリングは大きく 4 つのアプローチに分類できます。

### アプローチ 1: 例外ベース

伝統的な try/catch によるエラーハンドリングです。

| 言語 | 構文 | チェック例外 |
|------|------|------------|
| Java | `try/catch/finally` | あり |
| Python | `try/except/finally` | なし |
| TypeScript | `try/catch/finally` | なし |
| Ruby | `begin/rescue/ensure` | なし |
| PHP | `try/catch/finally` | なし |
| C# | `try/catch/finally` | なし |
| Scala | `try/catch/finally` | なし |

### アプローチ 2: 値ベース（Result / Either）

エラーを戻り値として型で表現するアプローチです。

| 言語 | 型 | 特徴 |
|------|-----|------|
| Rust | `Result<T, E>` | `?` 演算子で伝播 |
| Haskell | `Either e a` | モナドとして合成可能 |
| F# | `Result<'T, 'E>` | パイプラインで連鎖 |
| Scala | `Either[E, T]` | `for` 内包表記で合成 |

### アプローチ 3: タプルベース

多値返却でエラーを表現するアプローチです。

| 言語 | パターン | 特徴 |
|------|---------|------|
| Go | `(value, error)` | `if err != nil` で明示的チェック |
| Elixir | `{:ok, value}` / `{:error, reason}` | パターンマッチで分岐 |

### アプローチ 4: 条件式ベース

Clojure のように、動的に条件を判定するアプローチです。

```clojure
;; Clojure: ex-info で構造化されたエラー
(try
  (generate-string (->FizzBuzzTypeNotDefined) value)
  (catch Exception e
    (ex-data e)))
```

## 型安全性のスペクトル

各言語の型安全性を、FizzBuzz の実装で遭遇するリスク別に比較します。

### null / nil 安全性

| レベル | 言語 | 特徴 |
|--------|------|------|
| null なし | Rust, Haskell | Option/Maybe で明示的に扱う |
| null 安全機能あり | Kotlin, F#, Scala | Nullable アノテーション / Option |
| null 条件演算子あり | C#, TypeScript | `?.` で安全にアクセス |
| null 可能 | Java, Go, Python, Ruby, PHP, Clojure, Elixir | 実行時に NullPointerException の可能性 |

### 網羅性チェック（Exhaustiveness Checking）

パターンマッチングで全ケースを網羅しているかをコンパイラが検証する機能です。

| レベル | 言語 | 特徴 |
|--------|------|------|
| 厳密 | Rust, Haskell, F#, Scala | すべてのケースを網羅しないとコンパイルエラー |
| 警告 | TypeScript（narrowing） | 制御フロー分析で未処理ケースを警告 |
| なし | Java, Go, Python, Ruby, PHP, Clojure, Elixir, C# | 実行時にデフォルトケースに到達 |

### FizzBuzz における型安全性の例

```rust
// Rust: 網羅性チェックあり
// FizzBuzzTypeName の全バリアントを処理しないとコンパイルエラー
match type_name {
    FizzBuzzTypeName::Standard => Box::new(FizzBuzzType01),
    FizzBuzzTypeName::NumberOnly => Box::new(FizzBuzzType02),
    FizzBuzzTypeName::FizzBuzzOnly => Box::new(FizzBuzzType03),
    // ここを忘れるとコンパイルエラー
}
```

```fsharp
// F#: 判別共用体の網羅性チェック
match fizzBuzzType with
| Standard -> ...
| NumberOnly -> ...
| FizzBuzzOnly -> ...
// 新しいケースを追加すると、全 match 式で警告
```

```java
// Java: 網羅性チェックなし
switch (type) {
    case 1: return new FizzBuzzType01();
    case 2: return new FizzBuzzType02();
    case 3: return new FizzBuzzType03();
    default: throw new IllegalArgumentException("該当するタイプは存在しません");
    // default がないと実行時エラーの可能性
}
```

## 型システムとテストの関係

型の強さとテストの必要性は反比例の関係にあります。

```
テストの必要性: 高い ←──────────────────────→ 低い
型の安全性:    低い ←──────────────────────→ 高い

PHP  Ruby  Python  Clojure  Elixir  Java  Go  TS  C#  Scala  F#  Rust  Haskell
```

| 型安全性レベル | テスト戦略 | 言語例 |
|-------------|----------|-------|
| 非常に高い | 型で多くのバグを防止、プロパティベーステスト | Haskell, Rust |
| 高い | 型 + 単体テストで十分 | F#, Scala, TypeScript |
| 中程度 | 単体テスト + 統合テスト | Java, C#, Go |
| 低い | テストカバレッジが重要、TDD が特に有効 | Python, Ruby, PHP, Clojure, Elixir |

## まとめ

1. **静的型付け言語**はコンパイル時にバグを検出でき、IDE サポートが強力です。特に Rust, Haskell, F# の型システムは非常に強力です
2. **Option/Result パターン**は例外に頼らない安全なエラーハンドリングを提供します。Rust と Haskell がこの分野のリーダーです
3. **動的型付け言語**では TDD によるテストカバレッジが型安全性を補完する重要な役割を果たします
4. **網羅性チェック**を持つ言語（Rust, Haskell, F#, Scala）では、新しいバリアントの追加時にすべての処理箇所を更新することが保証されます
5. **Go の多値返却**と **Elixir のタプルパターン**は、言語の哲学に合った独自のエラーハンドリングアプローチです

次章では、開発環境と CI/CD の統一アプローチを比較します。
