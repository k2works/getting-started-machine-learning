{- | 乱数生成器を自作する。

@System.Random@ はほかの言語版と並びが合わない。そこで @java.util.Random@ と
同じ 48 ビットの線形合同法をそのまま書く。こうすると訓練データとテストデータの
分割が Java 版・Kotlin 版・Scala 版・Clojure 版・Elixir 版・PHP 版と一致するので、
章をまたいで数値を突き合わせられる。

Haskell の 'Int' は 64 ビットで、溢れると折り返す（PHP のように float に
化けない）ので、仕様の式をそのまま書ける。
-}
module GettingStartedMl.Random (
  Seed,
  newSeed,
  nextInt,
  shuffle,
) where

import Data.Bits (shiftR, xor, (.&.))
import qualified Data.Map.Strict as M

-- | 乱数生成器の状態。48 ビットに収まる。
newtype Seed = Seed Int
  deriving (Eq, Show)

mask, multiplier, increment :: Int
mask = 0xFFFFFFFFFFFF
multiplier = 0x5DEECE66D
increment = 0xB

-- | シードから状態を作る。@java.util.Random@ の @setSeed@ と同じ。
newSeed :: Int -> Seed
newSeed seed = Seed ((seed `xor` multiplier) .&. mask)

-- | 状態を 1 つ進める。
advance :: Seed -> Seed
advance (Seed s) = Seed ((s * multiplier + increment) .&. mask)

-- | 上位ビットだけを使う。48 ビットの状態から欲しいビット数を取り出す。
nextBits :: Int -> Seed -> (Int, Seed)
nextBits bits s =
  let next@(Seed value) = advance s
   in (value `shiftR` (48 - bits), next)

{- | 0 以上 @bound@ 未満の整数を 1 つ返し、次の状態と一緒に返す。

@bound@ が 2 の冪のときだけ別の式を使うところまで @java.util.Random@ に合わせる。
状態を引数と戻り値で受け渡すので、同じ状態からは必ず同じ値が出る。
-}
nextInt :: Int -> Seed -> (Int, Seed)
nextInt bound s
  | bound <= 0 = error ("bound は正の数でなければなりません: " <> show bound)
  | (bound .&. negate bound) == bound =
      let (bits, next) = nextBits 31 s
       in ((bound * bits) `shiftR` 31, next)
  | otherwise = reject s
 where
  -- 剰余の偏りを避けるため、範囲をはみ出す値は捨てて引き直す。
  reject current =
    let (bits, next) = nextBits 31 current
        value = bits `mod` bound
     in if bits - value + (bound - 1) >= 0x80000000
          then reject next
          else (value, next)

{- | Fisher-Yates で並べ替える。

後ろから順に、まだ選んでいない範囲から 1 つ選んで交換する。
不変のリストでは添字の書き換えができないので、'M.Map' に写してから交換する。
-}
shuffle :: [a] -> Int -> [a]
shuffle items seed
  | count <= 1 = items
  | otherwise = M.elems (go (count - 1) (newSeed seed) indexed)
 where
  count = length items
  indexed = M.fromList (zip [0 ..] items)

  go i s table
    | i < 1 = table
    | otherwise =
        let (j, next) = nextInt (i + 1) s
         in go (i - 1) next (swap i j table)

  swap i j table =
    case (M.lookup i table, M.lookup j table) of
      (Just atI, Just atJ) -> M.insert i atJ (M.insert j atI table)
      _ -> table
