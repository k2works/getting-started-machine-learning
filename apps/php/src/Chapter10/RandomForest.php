<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter10;

use GettingStartedMl\Chapter03\DecisionTree;
use GettingStartedMl\Chapter10;
use GettingStartedMl\Random;

/**
 * 第 3 章の決定木を、ブートストラップ標本と特徴量の部分集合で何本も育てた森。
 *
 * 予測は木ごとの多数決。Rubix ML にもランダムフォレストがあるので、
 * Elixir 版と違って「自作しかない」章にはならない。
 */
final readonly class RandomForest implements Predictor
{
    /** @param list<ForestTree> $trees */
    public function __construct(public array $trees)
    {
    }

    /**
     * 木を n_estimators 本学習する。maxDepth が null なら深さの上限なし。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     */
    public static function fit(
        array $x,
        array $t,
        array $columns,
        int $nEstimators,
        int $maxFeatures,
        ?int $maxDepth,
        int $seed,
    ): self {
        $random = new Random($seed);
        $trees = [];

        for ($i = 0; $i < $nEstimators; ++$i) {
            $rows = Chapter10::bootstrapSample(count($x), $random);
            $chosen = array_slice(self::shuffleWith($columns, $random), 0, $maxFeatures);
            // 列の順は元のまま残す。
            $treeColumns = array_values(
                array_filter($columns, static fn (string $c): bool => in_array($c, $chosen, true)),
            );

            $sampleX = [];
            $sampleT = [];

            foreach ($rows as $row) {
                $sampleX[] = Chapter10::takeColumns($x[$row], $treeColumns);
                $sampleT[] = $t[$row];
            }

            $trees[] = new ForestTree(
                $treeColumns,
                $rows,
                DecisionTree::fit($sampleX, $sampleT, $treeColumns, $maxDepth),
            );
        }

        return new self($trees);
    }

    /**
     * 木ごとの予測を多数決でまとめる。
     *
     * @param list<array<string, float>> $x
     *
     * @return list<string>
     */
    public function predict(array $x): array
    {
        $votes = array_map(
            static fn (ForestTree $tree): array => $tree->tree->predict(
                array_map(
                    static fn (array $features): array => Chapter10::takeColumns($features, $tree->columns),
                    $x,
                ),
            ),
            $this->trees,
        );

        return Chapter10::majorityVote($votes);
    }

    /**
     * 渡した乱数生成器で Fisher-Yates の並べ替えをする。
     *
     * 第 2 章の Random::shuffle はシードから始めるが、森を作る途中では
     * 状態を引き継ぐ必要があるので、生成器そのものを受け取る。
     *
     * @param list<string> $items
     *
     * @return list<string>
     */
    private static function shuffleWith(array $items, Random $random): array
    {
        for ($i = count($items) - 1; $i >= 1; --$i) {
            $j = $random->nextInt($i + 1);
            [$items[$i], $items[$j]] = [$items[$j], $items[$i]];
        }

        return array_values($items);
    }
}
