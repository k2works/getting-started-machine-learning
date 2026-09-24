<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter03;

use InvalidArgumentException;
use Rubix\ML\Classifiers\ClassificationTree;
use Rubix\ML\Datasets\Labeled;
use Rubix\ML\Datasets\Unlabeled;

/**
 * Rubix ML の決定木で学習して予測する。自作の決定木と突き合わせるために使う。
 *
 * 既定のままでは自作の木と比べられない。ClassificationTree は
 *
 * - 葉の最小件数が 3、不純度の最小の改善が 1e-7 で、純粋になる前に分割をやめる
 * - 使う列を毎回 `array_rand` で √列数 だけ選ぶので、結果が実行ごとに変わる
 * - 連続値の候補を分位点で丸める（既定の箱の数は 1 + round(log2(件数))）
 *
 * ので、ここで 4 つの引数をすべて指定して、自作の木と同じ条件にそろえる。
 */
final class RubixTree
{
    /** 分位点で丸めさせないために渡す箱の数。件数より大きければ丸めは起きない。 */
    public const int MAX_BINS = 1000000;

    /**
     * 訓練データで学習し、テストデータのラベルを予測する。
     *
     * @param list<array<string, float>> $xTrain
     * @param list<string>               $tTrain
     * @param list<array<string, float>> $xTest
     * @param list<string>               $columns
     *
     * @return list<string>
     */
    public static function predict(
        array $xTrain,
        array $tTrain,
        array $xTest,
        array $columns,
        ?int $maxDepth = null,
    ): array {
        $tree = new ClassificationTree(
            $maxDepth ?? PHP_INT_MAX,
            maxLeafSize: 1,
            minPurityIncrease: 0.0,
            maxFeatures: count($columns),
            maxBins: self::MAX_BINS,
        );

        $tree->train(new Labeled(self::samples($xTrain, $columns), $tTrain));

        /** @var list<string> $predictions */
        $predictions = $tree->predict(new Unlabeled(self::samples($xTest, $columns)));

        return $predictions;
    }

    /**
     * 特徴量の連想配列を、列の順に並べた数値の配列にする。
     *
     * Rubix ML のデータセットは列を名前ではなく位置で扱うので、ここで並べ替える。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $columns
     *
     * @return list<list<float>>
     */
    public static function samples(array $x, array $columns): array
    {
        return array_map(
            static fn (array $features): array => array_map(
                static function (string $column) use ($features): float {
                    if (!array_key_exists($column, $features)) {
                        throw new InvalidArgumentException("列がありません: {$column}");
                    }

                    return $features[$column];
                },
                $columns,
            ),
            $x,
        );
    }
}
