---
type: Article
title: "第 12 章: エラーハンドリングと型安全性"
tags: [article, getting-start-tdd, go]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 12 章: エラーハンドリングと型安全性

## 12.1 error インターフェースと安全なファクトリ

Go の標準的なエラーハンドリングは `error` インターフェースと多値返却です。第 7-9 章では `panic` を使用していましたが、Go の慣習に従い `error` を返すファクトリメソッドに改善します。

### テスト: 安全なファクトリメソッド

```go
func TestTryNewFizzBuzzType_正常なタイプを生成できる(t *testing.T) {
    fbt, err := TryNewFizzBuzzType(1)
    if err != nil {
        t.Fatalf("unexpected error: %v", err)
    }
    got := fbt.Generate(3)
    if got.Value() != "Fizz" {
        t.Fatalf("got.Value() = %q, want %q", got.Value(), "Fizz")
    }
}

func TestTryNewFizzBuzzType_不正なタイプでエラーを返す(t *testing.T) {
    _, err := TryNewFizzBuzzType(99)
    if err == nil {
        t.Fatal("expected error for invalid type")
    }
}
```

<details>
<summary>実装コード: TryNewFizzBuzzType</summary>

```go
// TryNewFizzBuzzType は安全なファクトリです。不正なタイプでは error を返します。
func TryNewFizzBuzzType(fizzBuzzType int) (FizzBuzzType, error) {
    switch fizzBuzzType {
    case 1:
        return FizzBuzzType01{}, nil
    case 2:
        return FizzBuzzType02{}, nil
    case 3:
        return FizzBuzzType03{}, nil
    default:
        return nil, fmt.Errorf("該当するタイプは存在しません: %d", fizzBuzzType)
    }
}
```

</details>

### テスト: 安全な FizzBuzzValue 生成

```go
func TestTryNewFizzBuzzValue_正の値で生成できる(t *testing.T) {
    v, err := TryNewFizzBuzzValue(1, "1")
    if err != nil {
        t.Fatalf("unexpected error: %v", err)
    }
    if v.Number() != 1 {
        t.Fatalf("Number() = %d, want 1", v.Number())
    }
}

func TestTryNewFizzBuzzValue_負の値でエラーを返す(t *testing.T) {
    _, err := TryNewFizzBuzzValue(-1, "-1")
    if err == nil {
        t.Fatal("expected error for negative number")
    }
}
```

<details>
<summary>実装コード: TryNewFizzBuzzValue</summary>

```go
// TryNewFizzBuzzValue は安全なファクトリです。負の値では error を返します。
func TryNewFizzBuzzValue(number int, value string) (FizzBuzzValue, error) {
    if number < 0 {
        return FizzBuzzValue{}, fmt.Errorf("値は正の値のみ許可します: %d", number)
    }
    return FizzBuzzValue{number: number, value: value}, nil
}
```

</details>

## 12.2 型スイッチによるパターンマッチング

Go の型スイッチ（type switch）はインターフェース値の具体的な型で分岐するパターンマッチングです。

### テスト: 型スイッチで FizzBuzzType の種類を判定する

```go
func TestDescribeFizzBuzzType_タイプ名を返す(t *testing.T) {
    tests := []struct {
        input int
        want  string
    }{
        {1, "Standard"},
        {2, "NumberOnly"},
        {3, "FizzBuzzOnly"},
    }
    for _, tt := range tests {
        fbt := NewFizzBuzzType(tt.input)
        got := DescribeFizzBuzzType(fbt)
        if got != tt.want {
            t.Errorf("DescribeFizzBuzzType(%d) = %q, want %q", tt.input, got, tt.want)
        }
    }
}
```

<details>
<summary>実装コード: DescribeFizzBuzzType</summary>

```go
// DescribeFizzBuzzType は型スイッチで FizzBuzzType の種類を文字列で返します。
func DescribeFizzBuzzType(fbt FizzBuzzType) string {
    switch fbt.(type) {
    case FizzBuzzType01:
        return "Standard"
    case FizzBuzzType02:
        return "NumberOnly"
    case FizzBuzzType03:
        return "FizzBuzzOnly"
    default:
        return "Unknown"
    }
}
```

</details>

## 12.3 型安全な列挙型ファクトリ

マジックナンバーを排除し、型安全なファクトリパターンを導入します。

### テスト: FizzBuzzTypeName で型安全にタイプを指定する

```go
func TestFizzBuzzTypeName_型安全にタイプを生成する(t *testing.T) {
    fbt := CreateFizzBuzzType(FizzBuzzTypeStandard)
    got := fbt.Generate(3)
    if got.Value() != "Fizz" {
        t.Fatalf("got.Value() = %q, want %q", got.Value(), "Fizz")
    }
}

func TestFizzBuzzTypeName_全てのタイプを生成できる(t *testing.T) {
    types := []FizzBuzzTypeName{
        FizzBuzzTypeStandard,
        FizzBuzzTypeNumberOnly,
        FizzBuzzTypeFizzBuzzOnly,
    }
    for _, tn := range types {
        fbt := CreateFizzBuzzType(tn)
        if fbt == nil {
            t.Fatalf("CreateFizzBuzzType(%v) should not return nil", tn)
        }
    }
}
```

<details>
<summary>実装コード: FizzBuzzTypeName</summary>

```go
// FizzBuzzTypeName は FizzBuzz タイプの型安全な識別子です。
type FizzBuzzTypeName int

const (
    FizzBuzzTypeStandard     FizzBuzzTypeName = iota + 1 // 1
    FizzBuzzTypeNumberOnly                                // 2
    FizzBuzzTypeFizzBuzzOnly                              // 3
)

// CreateFizzBuzzType は FizzBuzzTypeName から FizzBuzzType を生成します。
func CreateFizzBuzzType(name FizzBuzzTypeName) FizzBuzzType {
    return NewFizzBuzzType(int(name))
}
```

</details>

## 12.4 FizzBuzzList の検索メソッド

### テスト: FindFirst — 条件に合致する最初の要素を返す

```go
func TestFizzBuzzList_FindFirst_最初のFizzBuzzを見つける(t *testing.T) {
    fbt := FizzBuzzType01{}
    cmd := NewFizzBuzzListCommand(fbt, 100)
    list := cmd.Execute().(*FizzBuzzList)

    isFizzBuzz := MakeValuePredicate("FizzBuzz")
    v, found := list.FindFirst(isFizzBuzz)

    if !found {
        t.Fatal("should find FizzBuzz")
    }
    if v.Number() != 15 {
        t.Fatalf("Number() = %d, want 15", v.Number())
    }
}

func TestFizzBuzzList_FindFirst_見つからない場合(t *testing.T) {
    values := []FizzBuzzValue{NewFizzBuzzValue(1, "1")}
    list := NewFizzBuzzList(values)

    isFizzBuzz := MakeValuePredicate("FizzBuzz")
    _, found := list.FindFirst(isFizzBuzz)

    if found {
        t.Fatal("should not find FizzBuzz")
    }
}
```

<details>
<summary>実装コード: FindFirst</summary>

```go
// FindFirst は条件に合致する最初の要素を返します。
// 見つからない場合は found が false になります。
func (l *FizzBuzzList) FindFirst(pred Predicate) (FizzBuzzValue, bool) {
    for _, v := range l.value {
        if pred(v) {
            return v, true
        }
    }
    return FizzBuzzValue{}, false
}
```

</details>

### テスト: AnyMatch / AllMatch — 存在チェック

```go
func TestFizzBuzzList_AnyMatch_Fizzが存在する(t *testing.T) {
    fbt := FizzBuzzType01{}
    cmd := NewFizzBuzzListCommand(fbt, 15)
    list := cmd.Execute().(*FizzBuzzList)

    if !list.AnyMatch(MakeValuePredicate("Fizz")) {
        t.Fatal("should contain Fizz")
    }
}

func TestFizzBuzzList_AllMatch_全て数値ではない(t *testing.T) {
    fbt := FizzBuzzType01{}
    cmd := NewFizzBuzzListCommand(fbt, 15)
    list := cmd.Execute().(*FizzBuzzList)

    isNumber := func(v FizzBuzzValue) bool {
        return v.Value() != "Fizz" && v.Value() != "Buzz" && v.Value() != "FizzBuzz"
    }
    if list.AllMatch(isNumber) {
        t.Fatal("not all values should be numbers")
    }
}
```

<details>
<summary>実装コード: AnyMatch / AllMatch</summary>

```go
// AnyMatch は条件に合致する要素が 1 つでもあれば true を返します。
func (l *FizzBuzzList) AnyMatch(pred Predicate) bool {
    for _, v := range l.value {
        if pred(v) {
            return true
        }
    }
    return false
}

// AllMatch は全要素が条件に合致すれば true を返します。
func (l *FizzBuzzList) AllMatch(pred Predicate) bool {
    for _, v := range l.value {
        if !pred(v) {
            return false
        }
    }
    return true
}
```

</details>

## 12.5 ジェネリクス（Go 1.18+）

Go 1.18 で導入された型パラメータにより、型安全な汎用関数を書けるようになりました。

### テスト: ジェネリック Map 関数

```go
func TestGenericMap_FizzBuzzValueを文字列に変換する(t *testing.T) {
    values := []FizzBuzzValue{
        NewFizzBuzzValue(1, "1"),
        NewFizzBuzzValue(3, "Fizz"),
    }

    result := MapSlice(values, func(v FizzBuzzValue) string {
        return v.Value()
    })

    if result[0] != "1" || result[1] != "Fizz" {
        t.Fatalf("MapSlice result = %v", result)
    }
}
```

<details>
<summary>実装コード: MapSlice（ジェネリクス）</summary>

```go
// MapSlice は任意の型のスライスに関数を適用し、変換結果のスライスを返します。
func MapSlice[T any, R any](slice []T, fn func(T) R) []R {
    result := make([]R, len(slice))
    for i, v := range slice {
        result[i] = fn(v)
    }
    return result
}
```

</details>

### テスト: ジェネリック Filter 関数

```go
func TestGenericFilter_正の値だけを抽出する(t *testing.T) {
    numbers := []int{-2, -1, 0, 1, 2, 3}
    positives := FilterSlice(numbers, func(n int) bool { return n > 0 })

    if len(positives) != 3 {
        t.Fatalf("len(positives) = %d, want 3", len(positives))
    }
}
```

<details>
<summary>実装コード: FilterSlice（ジェネリクス）</summary>

```go
// FilterSlice は条件に合致する要素だけを含む新しいスライスを返します。
func FilterSlice[T any](slice []T, pred func(T) bool) []T {
    var result []T
    for _, v := range slice {
        if pred(v) {
            result = append(result, v)
        }
    }
    return result
}
```

</details>

### テスト: ジェネリック Reduce 関数

```go
func TestGenericReduce_合計を計算する(t *testing.T) {
    numbers := []int{1, 2, 3, 4, 5}
    sum := ReduceSlice(numbers, 0, func(acc, n int) int { return acc + n })

    if sum != 15 {
        t.Fatalf("ReduceSlice sum = %d, want 15", sum)
    }
}
```

<details>
<summary>実装コード: ReduceSlice（ジェネリクス）</summary>

```go
// ReduceSlice は初期値から始めて各要素に関数を適用し、単一の値に畳み込みます。
func ReduceSlice[T any, R any](slice []T, initial R, fn func(R, T) R) R {
    acc := initial
    for _, v := range slice {
        acc = fn(acc, v)
    }
    return acc
}
```

</details>

## 12.6 各言語のエラーハンドリング比較

| 機能 | Go | Java | Ruby | TypeScript |
|------|-----|------|------|------------|
| エラー型 | `error` インターフェース | 例外（`Exception`） | 例外（`StandardError`） | 例外（`Error`） |
| null 安全 | 多値返却 `(T, error)` | `Optional<T>` | `nil` / `try_create` | `T \| undefined` |
| パターンマッチング | 型スイッチ | `instanceof` / `switch` | `case/in` | `typeof` / `switch` |
| ジェネリクス | 型パラメータ `[T any]` | `<T>` | なし（動的型付け） | `<T>` |
| 型安全な列挙 | `iota` + 型定義 | `enum` | 定数モジュール | `enum` / Union 型 |

Go のエラーハンドリングは例外を使わず、明示的な `error` 返却により呼び出し側にエラー処理を強制します。これは冗長に見えますが、エラーの流れを追跡しやすい利点があります。

## 12.7 まとめ

本章では以下を学びました:

- **error インターフェース**: `panic` から `error` 返却への改善（`TryNewFizzBuzzType`、`TryNewFizzBuzzValue`）
- **型スイッチ**: インターフェース値の具体的な型で分岐するパターンマッチング
- **型安全な列挙型**: `iota` を使ったマジックナンバーの排除（`FizzBuzzTypeName`）
- **検索メソッド**: `FindFirst`、`AnyMatch`、`AllMatch`
- **ジェネリクス**: `MapSlice[T, R]`、`FilterSlice[T]`、`ReduceSlice[T, R]` による汎用関数

Go のエラーハンドリングと型システムは Java の `Optional` や Ruby のパターンマッチングとは異なるアプローチですが、同じ目的（安全性と明確さ）を実現しています。ジェネリクスの導入により、型安全な汎用プログラミングが可能になりました。
