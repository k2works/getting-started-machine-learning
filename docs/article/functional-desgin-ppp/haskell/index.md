# Haskell による関数型デザイン

## 概要

本セクションでは、Robert C. Martin の「Functional Design: Principles, Patterns, and Practices」で紹介されている関数型プログラミングの原則とパターンを Haskell で実装します。

Haskell は純粋関数型言語として、関数型プログラミングの概念を最も直接的に表現できる言語の一つです。遅延評価、強い型システム、型クラスなどの特徴により、抽象度の高いコードを安全に記述できます。

## 構成

### 第1部：基礎原則（Chapters 1-3）

1. [不変性とデータ変換](01-immutability-and-data-transformation.md) - レコード、構造共有、パイプライン
2. [関数合成と高階関数](02-function-composition.md) - (.)、(>>>)、カリー化、部分適用
3. [多態性とディスパッチ](03-polymorphism.md) - ADT、型クラス、パターンマッチ

### 第2部：仕様とテスト（Chapters 4-6）

4. [データバリデーション](04-data-validation.md) - カスタムバリデータ、Applicative スタイル
5. [プロパティベーステスト](05-property-based-testing.md) - QuickCheck
6. [TDD と関数型](06-tdd-functional.md) - HSpec によるテスト駆動開発

### 第3部：構造パターン（Chapters 7-9）

7. [Composite パターン](07-composite-pattern.md) - 再帰的データ構造
8. [Decorator パターン](08-decorator-pattern.md) - 関数ラッパー
9. [Adapter パターン](09-adapter-pattern.md) - 型変換、newtypes

### 第4部：振る舞いパターン（Chapters 10-14）

10. [Strategy パターン](10-strategy-pattern.md) - 高階関数による戦略切り替え
11. [Command パターン](11-command-pattern.md) - データとしてのコマンド
12. [Visitor パターン](12-visitor-pattern.md) - Foldable/Traversable
13. [Abstract Factory パターン](13-abstract-factory-pattern.md) - 型クラスによる抽象化
14. [Abstract Server パターン](14-abstract-server-pattern.md) - モジュラー設計

### 第5部：実践アプリケーション（Chapters 15-19）

15. [ゴシップ好きなバスの運転手](15-gossiping-bus-drivers.md) - シミュレーション
16. [給与計算システム](16-payroll-system.md) - ビジネスロジック
17. [レンタルビデオシステム](17-video-rental-system.md) - ドメインモデリング
18. [並行処理システム](18-concurrency-system.md) - STM、async
19. [Wa-Tor シミュレーション](19-wator-simulation.md) - セルオートマトン

### 第6部：ベストプラクティス（Chapters 20-22）

20. [パターン間の相互作用](20-pattern-interactions.md) - パターンの組み合わせ
21. [ベストプラクティス](21-best-practices.md) - イディオム、スタイル
22. [OO から FP への移行](22-oo-to-fp-migration.md) - リファクタリング戦略

## Haskell の特徴

### 純粋性と参照透過性

```haskell
-- 純粋関数：同じ入力には常に同じ出力
calculateArea :: Double -> Double -> Double
calculateArea width height = width * height

-- IO はモナドで明示的に扱う
main :: IO ()
main = do
    putStrLn "Enter width:"
    w <- readLn
    putStrLn "Enter height:"
    h <- readLn
    print (calculateArea w h)
```

### 代数的データ型（ADT）

```haskell
-- 直和型（Sum Type）
data Shape
    = Circle Double
    | Rectangle Double Double
    | Triangle Double Double
    deriving (Show, Eq)

-- パターンマッチで網羅的に処理
area :: Shape -> Double
area (Circle r) = pi * r * r
area (Rectangle w h) = w * h
area (Triangle b h) = b * h / 2
```

### 型クラス

```haskell
-- アドホック多相性を実現
class Drawable a where
    draw :: a -> String
    boundingBox :: a -> (Double, Double, Double, Double)

instance Drawable Shape where
    draw (Circle r) = "Circle with radius " ++ show r
    draw (Rectangle w h) = "Rectangle " ++ show w ++ "x" ++ show h
    draw (Triangle b h) = "Triangle with base " ++ show b
    
    boundingBox (Circle r) = (-r, -r, r, r)
    boundingBox (Rectangle w h) = (0, 0, w, h)
    boundingBox (Triangle b h) = (0, 0, b, h)
```

### 関数合成

```haskell
-- (.) で右から左へ合成
process :: String -> Int
process = length . filter isDigit . map toLower

-- (>>>) で左から右へ合成（Control.Arrow）
processArrow :: String -> Int
processArrow = map toLower >>> filter isDigit >>> length

-- パイプライン演算子（& from Data.Function）
result = "Hello123"
       & map toLower
       & filter isDigit
       & length
```

## 開発環境

### 必要なツール

- GHC 9.4 以上
- Cabal または Stack
- HLS（Haskell Language Server）

### Nix を使用する場合

```bash
nix develop .#haskell
```

### プロジェクト構造

```
apps/haskell/
├── part1/                    # 第1部：基礎原則
│   ├── functional-design-part1.cabal
│   ├── src/
│   │   ├── Immutability.hs
│   │   ├── FunctionComposition.hs
│   │   └── Polymorphism.hs
│   └── test/
│       ├── Spec.hs
│       ├── ImmutabilitySpec.hs
│       ├── FunctionCompositionSpec.hs
│       └── PolymorphismSpec.hs
├── part2/                    # 第2部：仕様とテスト
├── part3/                    # 第3部：構造パターン
├── part4/                    # 第4部：振る舞いパターン
├── part5/                    # 第5部：実践
├── part6/                    # 第6部：並行処理
└── part7/                    # 第7部：ベストプラクティス
```

### ビルドとテスト

```bash
cd apps/haskell/part1
cabal build
cabal test --test-show-details=streaming
```

## 参考リソース

- [Haskell公式サイト](https://www.haskell.org/)
- [Learn You a Haskell](http://learnyouahaskell.com/)
- [Real World Haskell](http://book.realworldhaskell.org/)
- [Typeclassopedia](https://wiki.haskell.org/Typeclassopedia)
- [Haskell Design Patterns](https://www.fpcomplete.com/haskell/tutorial/all-about-strictness/)
