<?php

declare(strict_types=1);

namespace GettingStartedMl;

use GettingStartedMl\Chapter03\DecisionTree;
use GettingStartedMl\Chapter03\RubixTree;

/**
 * 第 3 章: 決定木による分類。
 *
 * 自作の決定木を作り、Rubix ML の ClassificationTree と突き合わせる。
 */
final class Chapter03
{
    /** テストデータの割合。 */
    public const float TEST_SIZE = 0.3;

    /** 分割の乱数のシード。 */
    public const int SEED = 0;

    /** 最後に木そのものを表示する深さ。 */
    public const int TREE_DEPTH_TO_SHOW = 2;

    /**
     * 正解率を比べる深さ。null は制限なし。
     *
     * @var list<int|null>
     */
    public const array MAX_DEPTHS = [1, 2, 3, 4, 5, null];

    /**
     * アヤメのデータの特徴量の列。正解ラベルの列を除いた順。
     *
     * @return list<string>
     */
    public static function featureColumns(): array
    {
        return ['がく片長さ', 'がく片幅', '花弁長さ', '花弁幅'];
    }

    /**
     * 自作と Rubix ML で学習し、正解率を 1 行にする。
     *
     * @param array{xTrain: list<array<string, float>>, xTest: list<array<string, float>>, tTrain: list<string>, tTest: list<string>} $split
     * @param list<string>                                                                                                          $columns
     */
    public static function accuracyRow(?int $maxDepth, array $split, array $columns): string
    {
        $model = DecisionTree::fit($split['xTrain'], $split['tTrain'], $columns, $maxDepth);
        $rubix = RubixTree::predict($split['xTrain'], $split['tTrain'], $split['xTest'], $columns, $maxDepth);

        return implode("\t", [
            $maxDepth === null ? '制限なし' : (string) $maxDepth,
            self::score($model->predict($split['xTrain']), $split['tTrain']),
            self::score($model->predict($split['xTest']), $split['tTest']),
            self::score($rubix, $split['tTest']),
        ]);
    }

    /**
     * 正解率を小数 4 桁の文字列にする。
     *
     * @param list<string> $predictions
     * @param list<string> $labels
     */
    public static function score(array $predictions, array $labels): string
    {
        return sprintf('%.4f', Chapter01::accuracy($predictions, $labels));
    }

    /** 深さごとの正解率と、深さ 2 の決定木を表示する。 */
    public static function run(?string $path = null): string
    {
        $split = Chapter02::prepareIris($path ?? Dataset::path('iris.csv'), self::TEST_SIZE, self::SEED);

        $lines = ["深さ\t訓練データ\tテストデータ\tRubix ML"];

        foreach (self::MAX_DEPTHS as $maxDepth) {
            $lines[] = self::accuracyRow($maxDepth, $split, self::featureColumns());
        }

        $tree = DecisionTree::fit(
            $split['xTrain'],
            $split['tTrain'],
            self::featureColumns(),
            self::TREE_DEPTH_TO_SHOW,
        );

        return implode("\n", $lines)
            . sprintf("\n\n深さ %d の決定木:\n", self::TREE_DEPTH_TO_SHOW)
            . $tree->format();
    }
}
