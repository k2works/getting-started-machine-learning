---
type: Article
title: "パラダイム別 TDD パターン比較"
description: "本章では、OOP と FP それぞれのパラダイムにおける TDD の進め方の違いを比較します。"
tags: [article, getting-start-tdd, integration]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# パラダイム別 TDD パターン比較

本章では、OOP と FP それぞれのパラダイムにおける TDD の進め方の違いを比較します。同じ FizzBuzz 問題でも、パラダイムによってテストの書き方、リファクタリングの方向性、設計パターンの適用方法が大きく異なります。

## OOP のインターフェース / クラスを活用した TDD

OOP 言語では、抽象クラスやインターフェースを定義し、具象クラスで実装を提供するという流れで TDD を進めます。

### TDD サイクルの特徴

1. **Red**: インターフェースに対するテストを書く
2. **Green**: 具象クラスで最小限の実装を提供する
3. **Refactor**: 共通処理を基底クラスに引き上げる

### Java の例

```java
// Step 1: インターフェース（抽象クラス）を定義
public abstract class FizzBuzzType {
    public abstract FizzBuzzValue generate(int number);
}

// Step 2: テストを書く
@Test
void _1を渡したら文字列1を返す() {
    FizzBuzzType type = FizzBuzzType.create(1);
    assertEquals("1", type.generate(1).getValue());
}

// Step 3: 具象クラスで実装
public class FizzBuzzType01 extends FizzBuzzType {
    @Override
    public FizzBuzzValue generate(int number) {
        if (isFizzBuzz(number)) return new FizzBuzzValue(number, "FizzBuzz");
        if (isFizz(number)) return new FizzBuzzValue(number, "Fizz");
        if (isBuzz(number)) return new FizzBuzzValue(number, "Buzz");
        return new FizzBuzzValue(number, String.valueOf(number));
    }
}
```

### C# の例

```csharp
// インターフェースを定義
public interface IFizzBuzzType
{
    FizzBuzzValue Generate(int number);
}

// テストを書く
[Fact]
public void Generate_1を渡すと文字列1を返す()
{
    IFizzBuzzType type = FizzBuzzTypeFactory.Create(FizzBuzzTypeName.Standard);
    Assert.Equal("1", type.Generate(1).Value);
}
```

### PHP の例

```php
// 抽象クラスを定義
abstract class FizzBuzzType
{
    abstract public function generate(int $number): FizzBuzzValue;
}

// テストを書く
public function test_1を渡したら文字列1を返す(): void
{
    $type = FizzBuzz::create(1);
    $this->assertSame('1', $type->generate(1)->getValue());
}
```

## FP の型 / パターンマッチを活用した TDD

FP 言語では、純粋関数とパターンマッチングを中心に TDD を進めます。

### TDD サイクルの特徴

1. **Red**: 純粋関数のテストを書く（入力と出力の対応を検証）
2. **Green**: パターンマッチングで最小限の実装を提供する
3. **Refactor**: 関数の合成や型の洗練で抽象化する

### Haskell の例

```haskell
-- Step 1: テストを書く
it "1 を渡すと '1' を返す" $
  generate 1 `shouldBe` "1"

it "3 の倍数を渡すと 'Fizz' を返す" $
  generate 3 `shouldBe` "Fizz"

-- Step 2: ガード節で実装
generate :: Int -> String
generate n
  | n `mod` 15 == 0 = "FizzBuzz"
  | n `mod` 3 == 0  = "Fizz"
  | n `mod` 5 == 0  = "Buzz"
  | otherwise        = show n
```

### F# の例

```fsharp
// 判別共用体で型を定義
type FizzBuzzType =
    | Standard
    | NumberOnly
    | FizzBuzzOnly

// パターンマッチングで実装
let generate (fizzBuzzType: FizzBuzzType) (number: int) =
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

### Elixir の例

```elixir
# ガード節とパターンマッチング
def generate(number) when is_integer(number) and number > 0 do
  cond do
    rem(number, 15) == 0 -> "FizzBuzz"
    rem(number, 3) == 0 -> "Fizz"
    rem(number, 5) == 0 -> "Buzz"
    true -> Integer.to_string(number)
  end
end
```

### Clojure の例

```clojure
;; 純粋関数として定義
(defn fizzbuzz [n]
  (cond
    (and (zero? (mod n 3)) (zero? (mod n 5))) "FizzBuzz"
    (zero? (mod n 3)) "Fizz"
    (zero? (mod n 5)) "Buzz"
    :else (str n)))

;; テスト
(deftest fizzbuzz-test
  (testing "1 を渡したら文字列 1 を返す"
    (is (= "1" (fizzbuzz 1)))))
```

## ポリモーフィズムの実現方法の比較

ポリモーフィズム（多態性）は FizzBuzz の複数タイプ（通常、数値のみ、FizzBuzz のみ）を切り替える際に重要な概念です。各言語でその実現方法が異なります。

### interface（Java, Go, C#, TypeScript, PHP）

```java
// Java: 抽象クラスによる多態性
public abstract class FizzBuzzType {
    public abstract FizzBuzzValue generate(int number);
}
```

```go
// Go: インターフェースによる多態性
type FizzBuzzType interface {
    Generate(number int) FizzBuzzValue
}
```

```csharp
// C#: インターフェースによる多態性
public interface IFizzBuzzType
{
    FizzBuzzValue Generate(int number);
}
```

### trait（Rust, Scala）

```rust
// Rust: trait による多態性
pub trait FizzBuzzType {
    fn generate(&self, number: i32) -> FizzBuzzValue;
}

impl FizzBuzzType for FizzBuzzType01 {
    fn generate(&self, number: i32) -> FizzBuzzValue {
        // 実装
    }
}
```

```scala
// Scala: sealed trait による多態性
sealed trait FizzBuzzType:
  def generate(number: Int): FizzBuzzValue

case object Type01 extends FizzBuzzType:
  def generate(number: Int): FizzBuzzValue = ???
```

### protocol（Clojure, Elixir）

```clojure
;; Clojure: protocol による多態性
(defprotocol FizzBuzzType
  (generate-string [this value]))

(defrecord FizzBuzzType01 []
  FizzBuzzType
  (generate-string [_this value]
    (let [n (:number value)]
      (cond
        (model/fizz-buzz? n) "FizzBuzz"
        (model/fizz? n) "Fizz"
        (model/buzz? n) "Buzz"
        :else (str n)))))
```

### type class（Haskell）

```haskell
-- Haskell: 型クラスによる多態性
class Generatable a where
  generateValue :: a -> Int -> String

-- 型クラスのインスタンス
instance Generatable StandardType where
  generateValue _ n
    | n `mod` 15 == 0 = "FizzBuzz"
    | n `mod` 3 == 0  = "Fizz"
    | n `mod` 5 == 0  = "Buzz"
    | otherwise        = show n
```

### 判別共用体 / ADT（F#, Haskell, Scala, Rust）

```fsharp
// F#: 判別共用体による多態性
type FizzBuzzType =
    | Standard
    | NumberOnly
    | FizzBuzzOnly

let generate fizzBuzzType number =
    match fizzBuzzType with
    | Standard -> ...
    | NumberOnly -> ...
    | FizzBuzzOnly -> ...
```

### ダックタイピング（Python, Ruby）

```python
# Python: ダックタイピング + ABC
class FizzBuzzType(ABC):
    @abstractmethod
    def generate(self, number: int) -> FizzBuzzValue:
        pass
```

```ruby
# Ruby: ダックタイピング
class FizzBuzzType
  def generate(number)
    raise NotImplementedError
  end
end
```

### ポリモーフィズム比較まとめ

| 方式 | 言語 | コンパイル時チェック | 網羅性チェック | 動的ディスパッチ |
|------|------|-------------------|-------------|--------------|
| interface | Java, C#, TypeScript, PHP | あり | なし | あり |
| interface（構造的部分型） | Go | あり | なし | あり |
| trait | Rust | あり | なし | あり（dyn trait） |
| sealed trait | Scala | あり | あり | あり |
| protocol | Clojure | なし | なし | あり |
| protocol（Elixir） | Elixir | なし | なし | あり |
| type class | Haskell | あり | あり（型レベル） | コンパイル時解決 |
| 判別共用体 | F# | あり | あり | パターンマッチ |
| ダックタイピング | Python, Ruby | なし | なし | あり |

## コマンドパターンの各言語実装比較

FizzBuzz プロジェクトでは、コマンドパターンを使って「単一値の生成」と「リストの生成」を統一的に扱っています。

### Java

```java
public interface FizzBuzzCommand {
    String execute();
}

public class FizzBuzzValueCommand implements FizzBuzzCommand {
    private final FizzBuzzType type;
    private final int number;

    @Override
    public String execute() {
        return type.generate(number).getValue();
    }
}
```

### TypeScript

```typescript
interface FizzBuzzCommand {
  execute(): string;
}

class FizzBuzzValueCommand implements FizzBuzzCommand {
  constructor(
    private readonly type: FizzBuzzType,
    private readonly number: number
  ) {}

  execute(): string {
    return this.type.generate(this.number).value;
  }
}
```

### Go

```go
type FizzBuzzCommand interface {
    Execute() string
}

type FizzBuzzValueCommand struct {
    FizzBuzzType FizzBuzzType
    Number       int
}

func (c *FizzBuzzValueCommand) Execute() string {
    return c.FizzBuzzType.Generate(c.Number).Value()
}
```

### Rust

```rust
pub trait FizzBuzzCommand {
    fn execute(&self) -> String;
}

pub struct FizzBuzzValueCommand {
    pub fizz_buzz_type: Box<dyn FizzBuzzType>,
    pub number: i32,
}

impl FizzBuzzCommand for FizzBuzzValueCommand {
    fn execute(&self) -> String {
        self.fizz_buzz_type.generate(self.number).value().to_string()
    }
}
```

### F#（関数で表現）

```fsharp
// F# ではコマンドパターンを関数で表現
let executeValue (fizzBuzzType: FizzBuzzType) (number: int) : FizzBuzzValue =
    generate fizzBuzzType number

let executeList (fizzBuzzType: FizzBuzzType) (count: int) : FizzBuzzList =
    [1..count]
    |> List.map (generate fizzBuzzType)
    |> createList
```

### Clojure（関数で表現）

```clojure
;; Clojure ではコマンドを高階関数で表現
(defn execute-value [fizz-buzz-type number]
  (generate-string fizz-buzz-type (->FizzBuzzValue number)))

(defn execute-list [fizz-buzz-type count]
  (mapv #(execute-value fizz-buzz-type %) (range 1 (inc count))))
```

### Haskell（関数で表現）

```haskell
-- Haskell ではコマンドを関数適用で表現
executeValue :: Int -> String
executeValue = generate

executeList :: Int -> [String]
executeList n = map generate [1..n]
```

### コマンドパターンの比較

| 言語 | 実現方法 | 特徴 |
|------|---------|------|
| Java | interface + class | 型安全、明示的なコマンドオブジェクト |
| TypeScript | interface + class | 同上、ジェネリクス対応 |
| Python | ABC + class | ダックタイピングで柔軟 |
| Ruby | class + method | シンプルな OOP スタイル |
| Go | interface + struct | 構造的部分型で暗黙的に実装 |
| PHP | interface + class | 型宣言で安全 |
| Rust | trait + struct | 所有権を考慮した設計が必要 |
| C# | interface + class | .NET エコシステムと統合 |
| F# | 関数 | 関数適用で自然に表現 |
| Clojure | 関数 | 高階関数で柔軟に組み合わせ |
| Scala | trait + case class / 関数 | OOP/FP 両方で表現可能 |
| Elixir | 関数 | パイプラインで連鎖 |
| Haskell | 関数 | 関数合成で自然に表現 |

## OOP vs FP: TDD アプローチの対比

| 観点 | OOP アプローチ | FP アプローチ |
|------|-------------|-------------|
| テスト対象 | オブジェクトの振る舞い | 関数の入出力 |
| テストの構造化 | クラス / ネストクラス | describe / テストモジュール |
| 仮実装 | ハードコーディング値を返す | 定数を返す |
| 三角測量 | テストケース追加で一般化 | 同左 |
| リファクタリング | 継承/委譲の導入 | 関数の合成/パターンの追加 |
| 多態性の導入 | interface/abstract class | 判別共用体/型クラス/プロトコル |
| デザインパターン | GoF パターンが直接適用 | 関数の組み合わせで表現 |
| モック/スタブ | インターフェースを差し替え | 関数を差し替え |

## まとめ

1. **OOP 言語**では、インターフェースと具象クラスの階層を TDD で段階的に構築します。コマンドパターンなどの GoF パターンが直接適用できます
2. **FP 言語**では、純粋関数のテストを書き、パターンマッチングで実装します。コマンドパターンは関数適用で自然に表現されます
3. **マルチパラダイム言語**では、状況に応じて OOP と FP のアプローチを使い分けることができます
4. **ポリモーフィズム**の実現方法は言語によって大きく異なりますが、すべて「同じインターフェースで異なる振る舞いを提供する」という本質は共通です

次章では、型システムとエラーハンドリングの違いを比較します。
