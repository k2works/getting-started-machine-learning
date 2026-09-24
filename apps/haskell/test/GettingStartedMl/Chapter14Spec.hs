{-# LANGUAGE OverloadedStrings #-}

module GettingStartedMl.Chapter14Spec (spec) where

import Control.Monad (unless)
import qualified Data.ByteString.Lazy as BL
import qualified Data.Map.Strict as M
import Data.Text (Text)
import qualified Data.Text.Encoding as TE
import GettingStartedMl.Chapter02 (loadTable)
import GettingStartedMl.Chapter14 (
  ClusterSummary (..),
  KMeans (..),
  assignClusters,
  chooseInitialCenters,
  clusterCounts,
  defaultNInit,
  fitCenters,
  fitWithRestarts,
  loadSpending,
  nClusters,
  report,
  roundHalfUp,
  spending,
  squaredDistance,
  sseByClusterCount,
  standardizePoints,
  sumOfSquaredErrors,
  summarizeClusters,
  updateCenters,
 )
import qualified GettingStartedMl.Dataset as Dataset
import Test.Hspec

spec :: Spec
spec = do
  describe "割り当てと中心の更新" $ do
    it "2 点間の距離の 2 乗を求める" $
      squaredDistance [0.0, 0.0] [3.0, 4.0] `shouldBe` 25.0

    it "各点を最も近い中心に割り当てる" $
      assignClusters [[0.0], [10.0], [4.0]] [[0.0], [10.0]] `shouldBe` [0, 1, 0]

    it "距離が同じなら先に並ぶ中心を選ぶ" $
      assignClusters [[5.0]] [[0.0], [10.0]] `shouldBe` [0]

    it "クラスタごとの平均を新しい中心にする" $
      updateCenters [[0.0], [2.0], [10.0]] [0, 0, 1] [[0.0], [0.0]]
        `shouldBe` [[1.0], [10.0]]

    it "点が割り当てられなかったクラスタは前の中心を残す" $
      updateCenters [[0.0], [2.0]] [0, 0] [[0.0], [7.0]] `shouldBe` [[1.0], [7.0]]

    it "各点と所属する中心との距離の 2 乗を合計する" $
      sumOfSquaredErrors [[0.0], [2.0]] [0, 0] [[1.0]] `shouldBe` 2.0

  describe "繰り返し" $ do
    it "離れた 2 つのかたまりを分ける" $ do
      let result = fitCenters twoBlobs [[0.0, 0.0], [10.0, 10.0]] 300
      kmeansLabels result `shouldBe` [0, 0, 1, 1]
      kmeansCenters result `shouldBe` [[0.5, 0.5], [10.5, 10.5]]
      kmeansSse result `shouldBe` 2.0

    it "更新の回数の上限に達したらそこで打ち切る" $ do
      let result = fitCenters twoBlobs [[0.0, 0.0], [10.0, 10.0]] 0
      kmeansCenters result `shouldBe` [[0.0, 0.0], [10.0, 10.0]]

    it "初期中心の置き方で結果が変わる（局所解）" $ do
      -- 手前のかたまりを 2 つの中心で分け合うと、奥の 2 つを 1 つの中心が抱えたまま動かなくなる
      let bad = fitCenters threeBlobs [[0.0], [1.0], [10.0]] 300
          good = fitCenters threeBlobs [[0.5], [10.5], [20.5]] 300
      kmeansSse good `shouldBe` 1.5
      kmeansSse bad `shouldBe` 101.0
      kmeansLabels bad `shouldBe` [0, 1, 2, 2, 2, 2]

  describe "初期中心" $ do
    it "シード付きの乱数で並べ替えて先頭から選ぶ" $
      chooseInitialCenters [[0.0], [1.0], [2.0], [3.0]] 2 0
        `shouldBe` Right [[3.0], [0.0]]

    it "点より多くの中心は選べない" $
      chooseInitialCenters [[0.0]] 2 0
        `shouldBe` Left "点は 1 件しかないので 2 個の中心を選べません"

    it "初期中心を何通りか試して SSE が最小の結果を返す" $ do
      let best' = fitWithRestarts twoBlobs 2 0 defaultNInit
      fmap kmeansSse best' `shouldBe` Right 2.0

    it "クラスタ数を増やすと SSE が小さくなる" $ do
      let sses = expectRight (sseByClusterCount twoBlobs [1, 2, 3] 0 defaultNInit)
      map fst sses `shouldBe` [1, 2, 3]
      and (zipWith (>=) (map snd sses) (drop 1 (map snd sses))) `shouldBe` True

  describe "支出額の列" $ do
    it "Channel と Region を除いた列だけを読む" $ do
      let table = expectRight (loadTable (utf8Csv wholesaleLike))
          (columns, x) = expectRight (spending table)
      columns `shouldBe` ["Fresh", "Milk"]
      map length x `shouldBe` [2, 2]

    it "空欄があれば失敗する" $ do
      let table = expectRight (loadTable (utf8Csv "Channel,Region,Fresh,Milk\n1,3,10,\n"))
      spending table `shouldBe` Left "Milk を数値として読めません: "

    it "列ごとに平均 0・標準偏差 1 にそろえる" $ do
      let table = expectRight (loadTable (utf8Csv wholesaleLike))
          (columns, x) = expectRight (spending table)
          points = expectRight (standardizePoints x columns)
      points `shouldBe` [[-1.0, -1.0], [1.0, 1.0]]

  describe "表示" $ do
    it "ちょうど 0.5 は 0 から遠いほうへ丸める（round は偶数側に丸める）" $ do
      roundHalfUp 34708.5 `shouldBe` 34709
      round (34708.5 :: Double) `shouldBe` (34708 :: Integer)

  describe "クラスタごとの特徴" $ do
    it "件数の多い順に並べ、元の単位で平均を出す" $ do
      let table = expectRight (loadTable (utf8Csv wholesaleLike3))
          (columns, x) = expectRight (spending table)
          summaries = expectRight (summarizeClusters x columns [1, 0, 1])
      map summaryCluster summaries `shouldBe` [1, 0]
      map summaryCount summaries `shouldBe` [2, 1]
      map summaryMeans summaries `shouldBe` [[20.0, 200.0], [30.0, 300.0]]

  describe "実データ" $ do
    it "卸売業者の顧客は 440 件 6 列になる" $ do
      loaded <- loadWholesale
      case loaded of
        Nothing -> pendingWith "学習データがありません"
        Just (columns, x) -> (length x, length columns) `shouldBe` (440, 6)

    it "クラスタ数ごとの SSE がほかの言語版と一致する" $ do
      loaded <- loadWholesale
      case loaded of
        Nothing -> pendingWith "学習データがありません"
        Just (columns, x) -> do
          let points = expectRight (standardizePoints x columns)
              sses = expectRight (sseByClusterCount points clusterCounts 0 defaultNInit)
          map (rounded 2 . snd) sses
            `shouldBe` [ 2640.00
                       , 1954.18
                       , 1614.52
                       , 1334.36
                       , 1085.27
                       , 947.20
                       , 888.22
                       , 775.24
                       , 690.81
                       , 618.17
                       ]

    it "クラスタごとの件数と平均支出額がほかの言語版と一致する" $ do
      loaded <- loadWholesale
      case loaded of
        Nothing -> pendingWith "学習データがありません"
        Just (columns, x) -> do
          let points = expectRight (standardizePoints x columns)
              labels = kmeansLabels (expectRight (fitWithRestarts points nClusters 0 defaultNInit))
              summaries = expectRight (summarizeClusters x columns labels)
          map summaryCluster summaries `shouldBe` [2, 1, 0, 3, 4]
          map summaryCount summaries `shouldBe` [265, 96, 65, 10, 4]
          map (map roundHalfUp . summaryMeans) summaries
            `shouldBe` [ [8909, 2967, 3804, 2248, 989, 962]
                       , [5509, 10556, 16478, 1420, 7199, 1659]
                       , [31117, 4260, 5374, 7225, 849, 2286]
                       , [15965, 34709, 48537, 3055, 24875, 2943]
                       , [52022, 31696, 18491, 29826, 2699, 19656]
                       ]

    it "クラスタリングの結果を表示する" $ do
      found <- Dataset.exists "Wholesale.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readDataset "Wholesale.csv"
      fmap (take 4 . lines) (report contents)
        `shouldBe` Right
          [ "データ件数: 440（支出額 6 列）"
          , "クラスタ数ごとの SSE（初期中心 10 通りの最小値）:"
          , "クラスタ数\tSSE"
          , "1\t2640.00"
          ]

-- | 離れた 2 つのかたまり。どちらも 2 点ずつで、中心は (0.5, 0.5) と (10.5, 10.5)。
twoBlobs :: [[Double]]
twoBlobs = [[0.0, 0.0], [1.0, 1.0], [10.0, 10.0], [11.0, 11.0]]

-- | 1 次元に並んだ 3 つのかたまり。クラスタ数 3 で局所解を再現するために使う。
threeBlobs :: [[Double]]
threeBlobs = [[0.0], [1.0], [10.0], [11.0], [20.0], [21.0]]

-- | 卸売業者のデータに似せた架空の 2 件。
wholesaleLike :: Text
wholesaleLike = "Channel,Region,Fresh,Milk\n1,3,10,100\n2,3,30,300\n"

-- | 同じ形の 3 件。クラスタごとの平均を確かめるために使う。
wholesaleLike3 :: Text
wholesaleLike3 = "Channel,Region,Fresh,Milk\n1,3,10,100\n2,3,30,300\n1,1,30,300\n"

-- | 実データを読み込む。学習データが無ければ 'Nothing'。
loadWholesale :: IO (Maybe ([Text], [M.Map Text Double]))
loadWholesale = do
  found <- Dataset.exists "Wholesale.csv"
  if not found
    then pure Nothing
    else do
      file <- Dataset.path "Wholesale.csv"
      loaded <- loadSpending file
      pure (Just (expectRight loaded))

-- | 小数点以下を丸める。記事に載せる桁で比べる。
rounded :: Int -> Double -> Double
rounded digits value = fromIntegral (round (value * scale) :: Integer) / scale
 where
  scale = 10 ^^ digits

-- | テストの中で「成功しているはず」の値を取り出す。
expectRight :: Either String a -> a
expectRight (Right value) = value
expectRight (Left err) = error ("失敗しました: " <> err)

-- | UTF-8 の CSV をテストのために組み立てる。
utf8Csv :: Text -> BL.ByteString
utf8Csv = BL.fromStrict . TE.encodeUtf8

-- | 学習データを読む。
readDataset :: String -> IO BL.ByteString
readDataset name = Dataset.path name >>= BL.readFile
