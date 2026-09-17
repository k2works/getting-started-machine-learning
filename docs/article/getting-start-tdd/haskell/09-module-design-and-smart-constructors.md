---
type: Article
title: "第 9 章: モジュール設計とスマートコンストラクタ"
description: "前章までに代数的データ型、型クラス、パターンマッチ、ガード、Either 型を導入しました。"
tags: [article, getting-start-tdd, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 9 章: モジュール設計とスマートコンストラクタ

## 9.1 はじめに

前章までに代数的データ型、型クラス、パターンマッチ、ガード、`Either` 型を導入しました。この章では、これらの要素をモジュールに分割し、スマートコンストラクタとコマンドパターンで設計を仕上げます。

### Before（モノリシック）

第 1 部で作成したコードは単一モジュールにすべてのロジックが集中していました。

```
src/
└── FizzBuzz.hs          (すべてのロジックが集中)
```

### After（モジュール分割）

```
src/
├── FizzBuzz.hs           (公開 API・後方互換性の維持)
└── FizzBuzz/
    ├── Type.hs           (代数的データ型・型クラス・スマートコンストラクタ)
    ├── Model.hs          (値オブジェクト・ファーストクラスコレクション)
    └── Command.hs        (コマンドパターンの実装)
```

## 9.2 モジュールシステムの基礎

### module 宣言

Haskell のモジュールは `module ... where` で宣言します。

```haskell
module FizzBuzz.Type
  ( FizzBuzzType(..)
  , Generatable(..)
  , createType
  ) where
```

この宣言は 3 つの要素で構成されています。

1. **モジュール名** (`FizzBuzz.Type`): ファイルパスに対応する（`src/FizzBuzz/Type.hs`）
2. **エクスポートリスト** (`( ... )`): 外部に公開する名前の一覧
3. **where**: モジュール本体の開始

### エクスポートリスト

エクスポートリストは、モジュールの外部に公開する名前を制御します。

```haskell
module FizzBuzz.Type
  ( FizzBuzzType(..)    -- 型とすべてのコンストラクタを公開
  , Generatable(..)     -- 型クラスとすべてのメソッドを公開
  , createType           -- 関数を公開
  ) where
```

`(..)` は「すべてのコンストラクタ（またはメソッド）を公開する」という意味です。

| 記法 | 意味 | 例 |
|------|------|-----|
| `Type(..)` | 型とすべてのコンストラクタを公開 | `FizzBuzzType(..)` → `Type01`, `Type02`, `Type03` が使える |
| `Type` | 型のみ公開（コンストラクタは非公開） | `FizzBuzzType` → 型注釈に使えるがパターンマッチ不可 |
| `Type(A, B)` | 型と指定したコンストラクタのみ公開 | `FizzBuzzType(Type01, Type02)` → `Type03` は使えない |
| `Class(..)` | 型クラスとすべてのメソッドを公開 | `Generatable(..)` → `generate` が使える |
| `function` | 関数を公開 | `createType` |

エクスポートリストを省略すると、モジュール内のすべての名前が公開されます。情報隠蔽の観点から、明示的にエクスポートリストを記述することが推奨されます。

### import 宣言

他のモジュールを使うには `import` 宣言を記述します。

```haskell
-- モジュール全体をインポート
import FizzBuzz.Type

-- 特定の名前のみインポート
import FizzBuzz.Type (FizzBuzzType, Generatable(..))

-- 修飾付きインポート（名前の衝突を防ぐ）
import qualified FizzBuzz.Type as T
-- T.FizzBuzzType, T.generate のように使う
```

本プロジェクトのインポート例を見てみましょう。

```haskell
-- src/FizzBuzz/Model.hs
import FizzBuzz.Type (FizzBuzzType, Generatable(..))

-- src/FizzBuzz/Command.hs
import FizzBuzz.Type (FizzBuzzType(..), Generatable(..))
import FizzBuzz.Model (FizzBuzzValue(..), FizzBuzzList, createList)
```

`Model.hs` は `FizzBuzzType` の型名と `Generatable` の `generate` メソッドだけが必要です。`Command.hs` は `FizzBuzzType(..)` でコンストラクタ（`Type01` など）にもアクセスする必要があるため、`(..)` を付けています。

## 9.3 モジュール分割の設計

### 各モジュールの責務

| モジュール | 責務 | 公開する名前 |
|-----------|------|-------------|
| `FizzBuzz.Type` | FizzBuzz タイプの定義と生成ルール | `FizzBuzzType(..)`, `Generatable(..)`, `createType` |
| `FizzBuzz.Model` | 値オブジェクトとコレクション | `FizzBuzzValue(..)`, `FizzBuzzList(..)`, `valueToString`, `listCount`, `createList` |
| `FizzBuzz.Command` | コマンドの定義と実行 | `ValueCommand(..)`, `ListCommand(..)`, `executeValue`, `executeList` |

各モジュールは単一の責務を持ち、変更理由が 1 つだけになるよう設計されています。新しい FizzBuzz タイプを追加する場合は `Type.hs` のみ変更し、値オブジェクトの構造を変える場合は `Model.hs` のみ変更します。

### 依存関係の方向

```
Command ──→ Model ──→ Type
```

依存関係は常に一方向です。

```haskell
-- Command.hs は Model と Type に依存
import FizzBuzz.Type (FizzBuzzType(..), Generatable(..))
import FizzBuzz.Model (FizzBuzzValue(..), FizzBuzzList, createList)

-- Model.hs は Type に依存
import FizzBuzz.Type (FizzBuzzType, Generatable(..))

-- Type.hs は他のモジュールに依存しない
```

この方向を守ることで、循環依存を防ぎ、各モジュールを独立にテストできます。Haskell のコンパイラは循環依存を許可しないため（`.hs-boot` ファイルを使う特殊な例外を除く）、依存方向の誤りはコンパイル時にエラーとして検出されます。

### 他言語との比較

| 概念 | Haskell | Java | Rust | Clojure |
|------|---------|------|------|---------|
| モジュール単位 | ファイル = モジュール | ファイル = クラス | ファイル = モジュール | ファイル = 名前空間 |
| 公開制御 | エクスポートリスト | `public`/`private` | `pub` | `defn`/`defn-` |
| インポート | `import Module (...)` | `import pkg.Class` | `use crate::module` | `(:require [...])` |
| パッケージ | Stack/Cabal | Maven/Gradle | Cargo | Leiningen |

## 9.4 スマートコンストラクタ

### スマートコンストラクタとは

`createType` 関数は **スマートコンストラクタ** パターンの実装です。

```haskell
createType :: Int -> Either String FizzBuzzType
createType 1 = Right Type01
createType 2 = Right Type02
createType 3 = Right Type03
createType _ = Left "未定義のタイプです"
```

スマートコンストラクタは、値の生成に **バリデーション** を挟むことで、不正な値の生成を防ぐパターンです。

| 方式 | 例 | 不正な値 |
|------|-----|---------|
| 直接コンストラクタ | `Type01` | 型が正しければ常に成功 |
| スマートコンストラクタ | `createType 1` → `Right Type01` | `createType 4` → `Left "..."` |

`FizzBuzzType` の値コンストラクタ（`Type01`、`Type02`、`Type03`）は型が直接指定されるため不正な値を作れません。しかし、外部からの入力（ユーザー入力、設定ファイル等）でタイプ番号が与えられる場合、その番号がバリデーション対象となります。`createType` はこの変換を型安全に行います。

### カプセル化の強化

もしコンストラクタを非公開にしたい場合は、エクスポートリストを変更します。

```haskell
-- コンストラクタを非公開にする場合
module FizzBuzz.Type
  ( FizzBuzzType        -- (..) を付けない → コンストラクタ非公開
  , Generatable(..)
  , createType
  ) where
```

この場合、外部モジュールは `Type01` などの値コンストラクタに直接アクセスできなくなり、`createType` 経由でのみ `FizzBuzzType` を生成できるようになります。本プロジェクトでは `FizzBuzzType(..)` でコンストラクタを公開していますが、これはテストでのパターンマッチの利便性を優先しているためです。

### Either の活用

`createType` の戻り値 `Either String FizzBuzzType` は、呼び出し側にエラーハンドリングを強制します。

```haskell
-- Either の値を使うには、パターンマッチでエラーケースを処理する必要がある
case createType userInput of
  Right fbType -> generate fbType 15   -- 正常系
  Left errMsg  -> "Error: " ++ errMsg  -- エラー系
```

Go の `(value, error)` パターンと似ていますが、Haskell ではコンパイラが両方のケースの処理を網羅性チェックで検証するため、エラーの無視が困難です。

## 9.5 コマンドパターン

### コマンドの設計

FizzBuzz の実行操作をコマンドとして抽象化します。

```haskell
-- src/FizzBuzz/Command.hs
module FizzBuzz.Command
  ( ValueCommand(..)
  , ListCommand(..)
  , executeValue
  , executeList
  ) where

import FizzBuzz.Type (FizzBuzzType(..), Generatable(..))
import FizzBuzz.Model (FizzBuzzValue(..), FizzBuzzList, createList)
```

### ValueCommand — 単一値の生成

```haskell
data ValueCommand = ValueCommand
  { vcNumber :: Int
  , vcType   :: FizzBuzzType
  } deriving (Show, Eq)

executeValue :: ValueCommand -> FizzBuzzValue
executeValue cmd =
  FizzBuzzValue
    { number = vcNumber cmd
    , value  = generate (vcType cmd) (vcNumber cmd)
    }
```

`ValueCommand` は「何番目の値を、どのタイプで生成するか」を表すデータ型です。`executeValue` はコマンドを受け取って結果を返す純粋関数です。

コマンドパターンの利点は以下の通りです。

1. **操作をデータとして扱える**: コマンドをリストに入れたり、ログに記録したり、後から実行したりできる
2. **引数のグルーピング**: 関連するパラメータを 1 つのデータ型にまとめる
3. **テストが容易**: コマンドを生成してテストに渡すだけでよい

### ListCommand — リストの生成

```haskell
data ListCommand = ListCommand
  { lcCount :: Int
  , lcType  :: FizzBuzzType
  } deriving (Show, Eq)

executeList :: ListCommand -> FizzBuzzList
executeList cmd = createList (lcCount cmd) (lcType cmd)
```

`ListCommand` は「何件のリストを、どのタイプで生成するか」を表します。`executeList` は `createList` に委譲するだけの薄いラッパーですが、コマンドパターンの統一的なインターフェースを提供します。

### Red: コマンドのテスト

`test/FizzBuzz/CommandSpec.hs` を作成します。

```haskell
-- test/FizzBuzz/CommandSpec.hs
module FizzBuzz.CommandSpec (spec) where

import Test.Hspec
import FizzBuzz.Command
import FizzBuzz.Model
import FizzBuzz.Type

spec :: Spec
spec = do
  describe "ValueCommand" $ do
    it "指定した数値で実行できる" $ do
      let cmd = ValueCommand { vcNumber = 3, vcType = Type01 }
          result = executeValue cmd
      value result `shouldBe` "Fizz"

    it "15 の倍数で FizzBuzz を返す" $ do
      let cmd = ValueCommand { vcNumber = 15, vcType = Type01 }
          result = executeValue cmd
      value result `shouldBe` "FizzBuzz"

  describe "ListCommand" $ do
    it "デフォルトで 100 件のリストを生成する" $ do
      let cmd = ListCommand { lcCount = 100, lcType = Type01 }
          result = executeList cmd
      listCount result `shouldBe` 100

    it "指定した件数でリストを生成する" $ do
      let cmd = ListCommand { lcCount = 10, lcType = Type01 }
          result = executeList cmd
      listCount result `shouldBe` 10
```

テストでは `let ... in ...` の代わりに `do` ブロック内の `let` を使って変数を束縛しています。HSpec の `it` ブロック内で `do` を使うと、複数の `let` と `shouldBe` を組み合わせて読みやすいテストを書けます。

### Green: テストの実行

`package.yaml` の `exposed-modules` に新しいモジュールを追加します。

```yaml
library:
  source-dirs: src
  exposed-modules:
    - FizzBuzz
    - FizzBuzz.Type
    - FizzBuzz.Model
    - FizzBuzz.Command
```

```bash
$ stack test
FizzBuzz.CommandSpec
  ValueCommand
    指定した数値で実行できる
    15 の倍数で FizzBuzz を返す
  ListCommand
    デフォルトで 100 件のリストを生成する
    指定した件数でリストを生成する
FizzBuzz.FizzBuzzSpec
  generate
    1 を渡すと '1' を返す
    2 を渡すと '2' を返す
    3 の倍数を渡すと 'Fizz' を返す
    5 の倍数を渡すと 'Buzz' を返す
    15 の倍数を渡すと 'FizzBuzz' を返す
    30 を渡すと 'FizzBuzz' を返す
  generateList
    100 件のリストを生成する
    最初の要素は '1'
    3 番目の要素は 'Fizz'
    5 番目の要素は 'Buzz'
    15 番目の要素は 'FizzBuzz'
FizzBuzz.TypeSpec
  Type01
    1 を渡すと '1' を返す
    3 の倍数を渡すと 'Fizz' を返す
    5 の倍数を渡すと 'Buzz' を返す
    15 の倍数を渡すと 'FizzBuzz' を返す
  Type02
    数値を文字列に変換する
  Type03
    1 を渡すと '1' を返す
    3 を渡すと 'Fizz' を返す
    15 の倍数で 'FizzBuzz' を返す
  createType
    タイプ 1 を生成できる
    タイプ 2 を生成できる
    タイプ 3 を生成できる
    未定義のタイプでエラーを返す

Finished in 0.0002 seconds
23 examples, 0 failures
```

すべてのテストが通りました。`hspec-discover` が `test/` 配下の `*Spec.hs` ファイルを自動検出するため、新しいテストファイルを追加するだけでテストスイートに組み込まれます。

## 9.6 テストのモジュール対応

### テストファイルの構成

テストファイルはソースモジュールに対応する構造で配置します。

```
test/
├── Spec.hs                     (hspec-discover エントリポイント)
└── FizzBuzz/
    ├── FizzBuzzSpec.hs          (FizzBuzz モジュールのテスト)
    ├── TypeSpec.hs              (FizzBuzz.Type モジュールのテスト)
    └── CommandSpec.hs           (FizzBuzz.Command モジュールのテスト)
```

| テストファイル | テスト対象モジュール | テスト内容 |
|--------------|-------------------|-----------|
| `FizzBuzzSpec.hs` | `FizzBuzz` | 基本の `generate`、`generateList` |
| `TypeSpec.hs` | `FizzBuzz.Type` | タイプ別の `generate`、`createType` |
| `CommandSpec.hs` | `FizzBuzz.Command` | `ValueCommand`、`ListCommand` の実行 |

### テストモジュールの命名規則

HSpec の `hspec-discover` は以下のルールでテストファイルを検出します。

1. `test/` ディレクトリ配下にある
2. ファイル名が `Spec.hs` で終わる
3. モジュール名が `Module.XxxSpec` のパターンに従う
4. `spec :: Spec` がエクスポートされている

```haskell
-- test/FizzBuzz/TypeSpec.hs
module FizzBuzz.TypeSpec (spec) where  -- (spec) のエクスポートが必須

import Test.Hspec
import FizzBuzz.Type

spec :: Spec  -- この型シグネチャが必須
spec = do
  ...
```

### テストの独立性

各テストファイルは独立しており、テスト対象モジュールのみをインポートします。`CommandSpec.hs` は `Command`、`Model`、`Type` の 3 つをインポートしていますが、これはコマンドの生成にタイプが、結果の検証にモデルのアクセサが必要なためです。

```haskell
-- test/FizzBuzz/CommandSpec.hs
import FizzBuzz.Command   -- コマンドの定義と実行
import FizzBuzz.Model     -- 結果（FizzBuzzValue）のアクセサ
import FizzBuzz.Type      -- タイプ（Type01）の指定
```

## 9.7 全体の設計を振り返る

### モジュール依存グラフ

```
┌──────────────┐
│   Command    │  コマンドの定義と実行
│  (Command.hs)│
└──────┬───────┘
       │ import
       ▼
┌──────────────┐
│    Model     │  値オブジェクトとコレクション
│  (Model.hs)  │
└──────┬───────┘
       │ import
       ▼
┌──────────────┐
│     Type     │  代数的データ型と型クラス
│  (Type.hs)   │
└──────────────┘
```

### 設計原則の適用

| 原則 | 適用箇所 |
|------|---------|
| **単一責任** | 各モジュールが 1 つの責務のみを持つ |
| **開放閉鎖** | 新しいタイプの追加は `instance` の追加で対応 |
| **依存性逆転** | `Model` は型クラス `Generatable` に依存し、具体実装には依存しない |
| **カプセル化** | エクスポートリストで公開範囲を制御 |
| **ファーストクラスコレクション** | `FizzBuzzList` が生のリストをラップ |
| **スマートコンストラクタ** | `createType` が不正な入力を `Either` で処理 |
| **コマンドパターン** | 操作をデータ型として表現 |

### Haskell らしい設計のポイント

Java や Rust のオブジェクト指向設計と比較して、Haskell の設計には以下の特徴があります。

1. **データと振る舞いの分離**: `data` でデータ構造を定義し、型クラスの `instance` で振る舞いを後付けする。Java のクラスのようにデータと振る舞いを 1 つに束ねない
2. **純粋関数による実行**: `executeValue` や `executeList` は純粋関数であり、IO や状態変更を含まない。テストが副作用なしで実行できる
3. **型による安全性**: `Either` で失敗を型レベルで表現し、コンパイラにエラーハンドリングの漏れを検出させる

## 9.8 まとめ

この章では以下のことを学びました。

- **module 宣言** と **エクスポートリスト** でモジュールの公開範囲を制御する
- **import 宣言** で他のモジュールを利用し、依存関係を明示する
- `FizzBuzz.Type` / `FizzBuzz.Model` / `FizzBuzz.Command` の 3 モジュールに分割し、単一責任を実現する
- **スマートコンストラクタ**（`createType`）で外部入力のバリデーションを型安全に行う
- **コマンドパターン**（`ValueCommand`/`ListCommand`）で操作をデータとして表現する
- 依存関係の方向（`Command` → `Model` → `Type`）を一方向に保つ
- テストファイルをソースモジュールに対応させ、`hspec-discover` で自動検出する

第 3 部を通じて、Haskell の型システム（代数的データ型、型クラス、パターンマッチ、`Either`）を活用した構造化設計を体験しました。型レベルでの安全性の保証、データと振る舞いの分離、純粋関数によるテスタビリティの高さが、Haskell のソフトウェア設計の特徴です。

次の第 4 部では、高階関数、関数合成、モナドといった関数型プログラミングの本格的なテクニックに進みます。
