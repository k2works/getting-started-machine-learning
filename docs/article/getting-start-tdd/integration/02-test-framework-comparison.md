---
type: Article
title: "テストフレームワーク比較"
description: "本章では、12 言語のテストフレームワークを比較し、テスト構造、アサーション方法、テスト実行コマンドの違いを解説します。"
tags: [article, getting-start-tdd, integration]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# テストフレームワーク比較

本章では、12 言語のテストフレームワークを比較し、テスト構造、アサーション方法、テスト実行コマンドの違いを解説します。TDD を実践する上で、テストフレームワークの特性を理解することは重要です。

## テストフレームワーク一覧

| 言語 | テスト FW | バージョン | 特徴 |
|------|----------|-----------|------|
| Java | JUnit 5 | 5.x | アノテーションベース、パラメータ化テスト |
| Python | pytest | 8.x | 関数ベース、フィクスチャ、パラメータ化 |
| TypeScript | Vitest | 2.x | ESM ネイティブ、Jest 互換 API |
| Ruby | Minitest | 5.x | 標準ライブラリ、シンプル、高速 |
| Go | testing | 標準 | 標準ライブラリ、テーブル駆動テスト |
| PHP | PHPUnit | 11.x | xUnit スタイル、モック内蔵 |
| Rust | cargo test | 標準 | 言語組み込み、モジュール内テスト |
| C# | xUnit | 2.x | .NET 標準、Fact/Theory アトリビュート |
| F# | xUnit | 2.x | .NET 標準、F# フレンドリー |
| Clojure | clojure.test | 標準 | 標準ライブラリ、REPL 統合 |
| Scala | ScalaTest | 3.x | 複数スタイル対応、マッチャー豊富 |
| Elixir | ExUnit | 標準 | 標準ライブラリ、describe/test 構造 |
| Haskell | HSpec | 2.x | BDD スタイル、describe/it 構造 |

## テスト構造の比較

テストの構造化方法は、大きく 3 つのスタイルに分類できます。

### スタイル 1: アノテーション / アトリビュートベース

テストメソッドにアノテーション（アトリビュート）を付与してテストを定義するスタイルです。

#### Java（JUnit 5）

```java
class FizzBuzzTypeTest {
    @Test
    void 数を文字列にして返す_1を渡したら文字列1を返す() {
        FizzBuzzType type = FizzBuzzType.create(1);
        assertEquals("1", type.generate(1).getValue());
    }

    @Nested
    class 三の倍数の場合 {
        @Test
        void _3を渡したらFizzを返す() {
            FizzBuzzType type = FizzBuzzType.create(1);
            assertEquals("Fizz", type.generate(3).getValue());
        }
    }
}
```

#### C#（xUnit）

```csharp
public class FizzBuzzTest
{
    [Fact]
    public void Generate_1を渡すと文字列1を返す()
    {
        Assert.Equal("1", FizzBuzzRunner.Generate(1));
    }

    [Theory]
    [InlineData(3, "Fizz")]
    [InlineData(5, "Buzz")]
    [InlineData(15, "FizzBuzz")]
    public void Generate_倍数に応じた文字列を返す(int input, string expected)
    {
        Assert.Equal(expected, FizzBuzzRunner.Generate(input));
    }
}
```

#### PHP（PHPUnit）

```php
class FizzBuzzTest extends TestCase
{
    public function test_1を渡したら文字列1を返す(): void
    {
        $fizzbuzz = new FizzBuzz();
        $this->assertSame('1', $fizzbuzz->generate(1));
    }
}
```

### スタイル 2: describe/it（BDD スタイル）

テストをネストされたブロックで構造化する BDD スタイルです。

#### TypeScript（Vitest）

```typescript
describe("FizzBuzzType", () => {
  describe("タイプ1の場合", () => {
    const type = FizzBuzzType.create(1);

    it("1を渡したら文字列'1'を返す", () => {
      expect(type.generate(1).value).toBe("1");
    });

    it("3を渡したら'Fizz'を返す", () => {
      expect(type.generate(3).value).toBe("Fizz");
    });
  });
});
```

#### Ruby（Minitest）

```ruby
class FizzBuzzTypeTest < Minitest::Test
  describe "FizzBuzzType" do
    describe "タイプ1の場合" do
      it "1を渡したら文字列'1'を返す" do
        type = FizzBuzzType01.new
        assert_equal "1", type.generate(1).value
      end
    end
  end
end
```

#### Scala（ScalaTest）

```scala
class FizzBuzzSpec extends AnyFunSuite:
  test("1 を渡すと文字列 1 を返す") {
    assert(FizzBuzz.generate(1) == "1")
  }

  test("3 の倍数を渡すと Fizz を返す") {
    assert(FizzBuzz.generate(3) == "Fizz")
  }
```

#### Elixir（ExUnit）

```elixir
describe "generate/1" do
  test "1 を渡すと文字列 1 を返す" do
    assert FizzBuzz.generate(1) == "1"
  end

  test "3 の倍数を渡すと Fizz を返す" do
    assert FizzBuzz.generate(3) == "Fizz"
  end
end
```

#### Haskell（HSpec）

```haskell
spec :: Spec
spec = do
  describe "generate" $ do
    it "1 を渡すと '1' を返す" $
      generate 1 `shouldBe` "1"

    it "3 の倍数を渡すと 'Fizz' を返す" $
      generate 3 `shouldBe` "Fizz"
```

### スタイル 3: 関数ベース

テストを関数として定義するシンプルなスタイルです。

#### Python（pytest）

```python
def test_1を渡したら文字列1を返す():
    type_ = FizzBuzzType.create(1)
    assert type_.generate(1).value == "1"

def test_3を渡したらFizzを返す():
    type_ = FizzBuzzType.create(1)
    assert type_.generate(3).value == "Fizz"
```

#### Go（testing）

```go
func TestGenerate_1を渡したら文字列1を返す(t *testing.T) {
    result := Generate(1)
    if result != "1" {
        t.Errorf("expected '1', got '%s'", result)
    }
}

// テーブル駆動テスト
func TestGenerate(t *testing.T) {
    tests := []struct {
        input    int
        expected string
    }{
        {1, "1"},
        {3, "Fizz"},
        {5, "Buzz"},
        {15, "FizzBuzz"},
    }
    for _, tt := range tests {
        t.Run(fmt.Sprintf("input=%d", tt.input), func(t *testing.T) {
            if got := Generate(tt.input); got != tt.expected {
                t.Errorf("Generate(%d) = %s, want %s", tt.input, got, tt.expected)
            }
        })
    }
}
```

#### Rust（cargo test）

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_1を渡したら文字列1を返す() {
        assert_eq!("1", generate(1));
    }

    mod 三の倍数の場合 {
        use super::*;

        #[test]
        fn test_3を渡したらfizzを返す() {
            assert_eq!("Fizz", generate(3));
        }
    }
}
```

#### Clojure（clojure.test）

```clojure
(deftest fizzbuzz-test
  (testing "数を文字列にして返す"
    (testing "1 を渡したら文字列 1 を返す"
      (is (= "1" (fizzbuzz 1)))))

  (testing "3 の倍数のときは Fizz と返す"
    (is (= "Fizz" (fizzbuzz 3)))))
```

## アサーション方法の比較

| 言語 | 等値比較 | 真偽チェック | 例外検証 |
|------|---------|------------|---------|
| Java | `assertEquals(expected, actual)` | `assertTrue(expr)` | `assertThrows(Class, () -> ...)` |
| Python | `assert value == expected` | `assert condition` | `with pytest.raises(Exception):` |
| TypeScript | `expect(actual).toBe(expected)` | `expect(cond).toBeTruthy()` | `expect(() => ...).toThrow()` |
| Ruby | `assert_equal expected, actual` | `assert condition` | `assert_raises(Error) { ... }` |
| Go | `if got != want { t.Errorf(...) }` | `if !cond { t.Error(...) }` | `defer/recover` パターン |
| PHP | `$this->assertSame(expected, actual)` | `$this->assertTrue(cond)` | `$this->expectException(Class)` |
| Rust | `assert_eq!(expected, actual)` | `assert!(condition)` | `#[should_panic]` |
| C# | `Assert.Equal(expected, actual)` | `Assert.True(condition)` | `Assert.Throws<T>(() => ...)` |
| F# | `Assert.Equal(expected, actual)` | `Assert.True(condition)` | `Assert.Throws<T>(fun () -> ...)` |
| Clojure | `(is (= expected actual))` | `(is condition)` | `(is (thrown? Exception (expr)))` |
| Scala | `assert(actual == expected)` | `assert(condition)` | `assertThrows[T] { ... }` |
| Elixir | `assert actual == expected` | `assert condition` | `assert_raise Error, fn -> ... end` |
| Haskell | `` actual `shouldBe` expected `` | `shouldSatisfy` | `shouldThrow` |

## テスト実行コマンドの比較

| 言語 | テスト実行 | 特定テスト実行 | カバレッジ |
|------|----------|--------------|-----------|
| Java | `gradle test` | `gradle test --tests "TestClass"` | JaCoCo |
| Python | `pytest` | `pytest test_file.py::test_name` | `pytest --cov` |
| TypeScript | `npx vitest` | `npx vitest -t "test name"` | `npx vitest --coverage` |
| Ruby | `rake test` | `ruby -Ilib:test test_file.rb` | SimpleCov |
| Go | `go test ./...` | `go test -run TestName` | `go test -cover` |
| PHP | `vendor/bin/phpunit` | `phpunit --filter testName` | `phpunit --coverage-text` |
| Rust | `cargo test` | `cargo test test_name` | `cargo tarpaulin` |
| C# | `dotnet test` | `dotnet test --filter "Name"` | `dotnet test --collect:"XPlat Code Coverage"` |
| F# | `dotnet test` | `dotnet test --filter "Name"` | `dotnet test --collect:"XPlat Code Coverage"` |
| Clojure | `lein test` | `lein test :only ns/test-name` | cloverage |
| Scala | `sbt test` | `sbt "testOnly *Spec"` | scoverage |
| Elixir | `mix test` | `mix test test/file_test.exs:10` | `mix test --cover` |
| Haskell | `stack test` | `stack test --ta "-m pattern"` | HPC |

## テスト構造の比較まとめ

### グルーピング機能

| 言語 | ネスト方法 | 深さ制限 |
|------|----------|---------|
| Java | `@Nested` クラス | 制限なし |
| Python | クラス / モジュール | 制限なし |
| TypeScript | `describe` ブロック | 制限なし |
| Ruby | `describe` ブロック | 制限なし |
| Go | `t.Run` サブテスト | 制限なし |
| PHP | テストクラス | クラス単位 |
| Rust | `mod` ブロック | 制限なし |
| C# | テストクラス | クラス単位 |
| F# | テストクラス / モジュール | モジュール単位 |
| Clojure | `testing` ブロック | 制限なし |
| Scala | `describe` / `test` | 制限なし |
| Elixir | `describe` ブロック | 1 レベル |
| Haskell | `describe` ブロック | 制限なし |

### セットアップ / ティアダウン

| 言語 | セットアップ | ティアダウン |
|------|------------|------------|
| Java | `@BeforeEach` / `@BeforeAll` | `@AfterEach` / `@AfterAll` |
| Python | `@pytest.fixture` | `yield` フィクスチャ |
| TypeScript | `beforeEach` / `beforeAll` | `afterEach` / `afterAll` |
| Ruby | `setup` / `before` | `teardown` / `after` |
| Go | `TestMain` / ヘルパー関数 | `t.Cleanup` |
| PHP | `setUp` / `setUpBeforeClass` | `tearDown` / `tearDownAfterClass` |
| Rust | なし（関数呼び出し） | `Drop` trait |
| C# | コンストラクタ / `IClassFixture` | `IDisposable` |
| F# | コンストラクタ / `IClassFixture` | `IDisposable` |
| Clojure | `use-fixtures` | `use-fixtures` |
| Scala | `BeforeAndAfterAll` / `BeforeAndAfterEach` | 同左 |
| Elixir | `setup` / `setup_all` | `on_exit` |
| Haskell | `before` / `beforeAll` | `after` / `afterAll` |

## パラメータ化テスト

複数の入力に対して同じテストロジックを実行するパラメータ化テストの対応状況です。

| 言語 | 方法 | 例 |
|------|-----|-----|
| Java | `@ParameterizedTest` + `@CsvSource` | `@CsvSource({"1,1", "3,Fizz"})` |
| Python | `@pytest.mark.parametrize` | `@pytest.mark.parametrize("n,expected", [(1,"1")])` |
| TypeScript | `it.each` / `describe.each` | `it.each([[1,"1"],[3,"Fizz"]])` |
| Ruby | データ駆動テスト | ループ + テストメソッド |
| Go | テーブル駆動テスト | `[]struct{ input, expected }` |
| PHP | `@dataProvider` | メソッドでデータを提供 |
| Rust | マクロ | カスタムマクロ |
| C# | `[Theory]` + `[InlineData]` | `[InlineData(1, "1")]` |
| F# | `[<Theory>]` + `[<InlineData>]` | `[<InlineData(1, "1")>]` |
| Clojure | `are` マクロ | `(are [n expected] ...)` |
| Scala | `Table` / `forAll` | `Table(("n","expected"), (1,"1"))` |
| Elixir | ループ + テストマクロ | `for {input, expected} <- data` |
| Haskell | `mapM_` / QuickCheck | `mapM_ (\(n, e) -> ...) cases` |

## まとめ

テストフレームワークの選択は言語の設計思想と密接に関連しています。

1. **アノテーション / アトリビュートベース**（Java, C#, PHP）は OOP 言語に多く、クラスベースの構造化が特徴です
2. **describe/it スタイル**（TypeScript, Ruby, Scala, Elixir, Haskell）は BDD の影響を受けており、テストの意図が読みやすくなります
3. **関数ベース**（Python, Go, Rust, Clojure）はシンプルさを重視し、テストを通常の関数として扱います
4. **パラメータ化テスト**はすべての言語で何らかの形で実現可能ですが、Go のテーブル駆動テストと Clojure の `are` マクロが特に洗練されています

次章では、パラダイムの違いが TDD パターンにどう影響するかを比較します。
