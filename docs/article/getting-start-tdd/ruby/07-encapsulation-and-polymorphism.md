---
type: Article
title: "第 7 章: カプセル化とポリモーフィズム"
description: "第 1 部では FizzBuzz を TDD で実装し、第 2 部では開発環境を整備しました。"
tags: [article, getting-start-tdd, ruby]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 7 章: カプセル化とポリモーフィズム

## 7.1 はじめに

第 1 部では FizzBuzz を TDD で実装し、第 2 部では開発環境を整備しました。第 3 部では **オブジェクト指向設計** に踏み込み、手続き的なコードをより柔軟で拡張しやすい構造にリファクタリングしていきます。

この章では、**追加仕様** を題材にして **カプセル化** と **ポリモーフィズム** を学びます。

## 7.2 追加仕様

FizzBuzz に 3 つの **タイプ** を導入します。

| タイプ | 仕様 |
|--------|------|
| タイプ 1（通常） | 3 の倍数→Fizz、5 の倍数→Buzz、15 の倍数→FizzBuzz、それ以外→数値 |
| タイプ 2（数値のみ） | すべて数値文字列を返す（Fizz/Buzz 変換なし） |
| タイプ 3（FizzBuzz のみ） | 15 の倍数→FizzBuzz、それ以外→数値 |

**TODO リスト**:

- [ ] タイプ 1: 通常の FizzBuzz（既存の動作）
- [ ] タイプ 2: 数値のみ返す
- [ ] タイプ 3: FizzBuzz のみ返す
- [ ] 未定義のタイプはエラー

## 7.3 手続き的なアプローチ

最初に思いつくのは、`case` 文でタイプを分岐する手続き的なアプローチです。

```ruby
# 手続き的な実装（アンチパターン）
class FizzBuzz
  def self.generate(number, type = 1)
    case type
    when 1
      return 'FizzBuzz' if (number % 15).zero?
      return 'Fizz' if (number % 3).zero?
      return 'Buzz' if (number % 5).zero?
      number.to_s
    when 2
      number.to_s
    when 3
      return 'FizzBuzz' if (number % 15).zero?
      number.to_s
    else
      raise "未定義のタイプ: #{type}"
    end
  end
end
```

この実装には問題があります:

- **単一責任原則の違反**: 1 つのメソッドに複数のアルゴリズムが詰め込まれている
- **開放閉鎖原則の違反**: 新しいタイプを追加するたびに既存のコードを修正する必要がある
- **テストの困難さ**: タイプごとの独立したテストがしにくい

## 7.4 カプセル化

Ruby では `attr_reader` を使ってフィールドを読み取り専用で公開し、カプセル化を実現します。

```ruby
class FizzBuzz
  attr_reader :type

  def initialize(type = nil)
    @type = type || FizzBuzzType01.new
  end

  def generate(number)
    @type.generate(number)
  end
end
```

### Ruby のカプセル化メカニズム

| 機能 | Ruby | Java | TypeScript |
|------|------|------|-----------|
| アクセス修飾子 | `private`, `protected`, `public` | `private`, `protected`, `public` | `private`, `protected`, `public` |
| 不変フィールド | `freeze` | `final` | `readonly` |
| getter | `attr_reader` | `getProp()` | `get prop()` |
| setter | `attr_writer` / `attr_accessor` | `setProp()` | `set prop()` |

- `attr_reader :type`: 読み取り専用アクセサ（getter のみ生成）
- コンストラクタ（`initialize`）でのみ `@type` を設定
- 外部からの直接変更を防止

## 7.5 ポリモーフィズム

### 基底クラスの定義

Ruby ではダックタイピングが基本ですが、明示的な型階層を使って共通インターフェースを定義できます。

```ruby
# frozen_string_literal: true

class FizzBuzzType
  TYPE_01 = 1
  TYPE_02 = 2
  TYPE_03 = 3

  def self.create(type)
    case type
    when TYPE_01 then FizzBuzzType01.new
    when TYPE_02 then FizzBuzzType02.new
    when TYPE_03 then FizzBuzzType03.new
    else raise "未定義のタイプ: #{type}"
    end
  end

  def fizz?(number)
    (number % 3).zero?
  end

  def buzz?(number)
    (number % 5).zero?
  end

  def generate(_number)
    raise NotImplementedError
  end
end
```

### 具体クラスの実装

```ruby
# frozen_string_literal: true

class FizzBuzzType01 < FizzBuzzType
  def generate(number)
    return FizzBuzzValue.new('FizzBuzz', number) if fizz?(number) && buzz?(number)
    return FizzBuzzValue.new('Fizz', number) if fizz?(number)
    return FizzBuzzValue.new('Buzz', number) if buzz?(number)

    FizzBuzzValue.new(number.to_s, number)
  end
end

class FizzBuzzType02 < FizzBuzzType
  def generate(number)
    FizzBuzzValue.new(number.to_s, number)
  end
end

class FizzBuzzType03 < FizzBuzzType
  def generate(number)
    return FizzBuzzValue.new('FizzBuzz', number) if fizz?(number) && buzz?(number)

    FizzBuzzValue.new(number.to_s, number)
  end
end
```

### Strategy パターン

この設計は **Strategy パターン** です。`FizzBuzz` クラスが Context、`FizzBuzzType` が Strategy に相当します。

```
FizzBuzz (Context)
  └── FizzBuzzType (Strategy - 基底クラス)
        ├── FizzBuzzType01 (通常の FizzBuzz)
        ├── FizzBuzzType02 (数値のみ)
        └── FizzBuzzType03 (FizzBuzz のみ)
```

**利点**:

- タイプの追加は新しいクラスを作るだけ（開放閉鎖原則）
- 各タイプを独立してテストできる
- 実行時にアルゴリズムを切り替え可能

## 7.6 テストの更新

テストをタイプ別の `describe` ブロックに整理します。

```ruby
# frozen_string_literal: true

require_relative '../../../test_helper'
require_relative '../../../../lib/fizz_buzz/fizz_buzz'

class FizzBuzzTypeTest < Minitest::Test
  describe 'タイプ1の場合' do
    def setup
      @type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
    end

    def test_1を渡したら文字列1を返す
      assert_equal '1', @type.generate(1).to_s
    end

    def test_3を渡したらFizzを返す
      assert_equal 'Fizz', @type.generate(3).to_s
    end

    def test_5を渡したらBuzzを返す
      assert_equal 'Buzz', @type.generate(5).to_s
    end

    def test_15を渡したらFizzBuzzを返す
      assert_equal 'FizzBuzz', @type.generate(15).to_s
    end
  end

  describe 'タイプ2の場合' do
    def setup
      @type = FizzBuzzType.create(FizzBuzzType::TYPE_02)
    end

    def test_3を渡したら文字列3を返す
      assert_equal '3', @type.generate(3).to_s
    end

    def test_15を渡したら文字列15を返す
      assert_equal '15', @type.generate(15).to_s
    end
  end

  describe 'タイプ3の場合' do
    def setup
      @type = FizzBuzzType.create(FizzBuzzType::TYPE_03)
    end

    def test_3を渡したら文字列3を返す
      assert_equal '3', @type.generate(3).to_s
    end

    def test_15を渡したらFizzBuzzを返す
      assert_equal 'FizzBuzz', @type.generate(15).to_s
    end
  end

  describe 'ファクトリメソッド' do
    def test_TYPE_01を指定するとFizzBuzzType01が返る
      type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
      assert_instance_of FizzBuzzType01, type
    end

    def test_未定義のタイプを指定するとエラーが発生する
      assert_raises RuntimeError do
        FizzBuzzType.create(99)
      end
    end
  end
end
```

### テスト実行結果

```bash
$ bundle exec rake test
Started with run options --seed 1052

22 tests, 27 assertions, 0 failures, 0 errors, 0 skips
```

## 7.7 各言語の OOP 比較

| 概念 | Ruby | Java | TypeScript | Python |
|------|------|------|-----------|--------|
| 抽象クラス | 規約（`raise NotImplementedError`） | `abstract class` | `abstract class` | `abc.ABC` + `@abstractmethod` |
| 継承 | `class Sub < Base` | `extends` | `extends` | `class Sub(Base)` |
| アクセス修飾子 | `private`, `protected`, `public` | `private`, `protected`, `public` | `private`, `protected`, `public` | `_` 命名規約 |
| 不変フィールド | `freeze` | `final` | `readonly` | `@property`（setter なし） |
| インスタンス判定 | `is_a?` / `instance_of?` | `instanceof` | `instanceof` | `isinstance()` |
| ダックタイピング | ネイティブ対応 | なし（interface） | 構造的部分型 | ネイティブ対応 |

## 7.8 まとめ

この章で学んだこと:

1. **カプセル化**: `attr_reader` でフィールドを読み取り専用にし、オブジェクトの内部状態を保護
2. **ポリモーフィズム**: 基底クラスとサブクラスによる型階層で条件分岐を排除
3. **Strategy パターン**: 実行時にアルゴリズムを切り替える設計パターン
4. **ファクトリメソッド**: `FizzBuzzType.create()` による生成の集約

次の章では、値オブジェクト、ファーストクラスコレクション、コマンドパターンなど、さらに多くのデザインパターンを適用していきます。
