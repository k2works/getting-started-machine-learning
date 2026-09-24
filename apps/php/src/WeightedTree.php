<?php

declare(strict_types=1);

namespace GettingStartedMl;

use InvalidArgumentException;

/**
 * 1 件ごとの重みを通した決定木。
 *
 * 第 3 章の決定木は「件数」で不純度と多数決を決めるが、この木は「重みの合計」で決める。
 * 重みをすべて 1 にすれば第 3 章と同じ木になる。
 *
 * クラスの重みは Rubix ML の分類器にも前処理にも口が無いので、この章では自作する。
 *
 * 同点のときの決め方をほかの言語版とそろえてある。どれか 1 つでもずれると、
 * 深い木で予測が食い違う。
 *
 * | 場面 | 決め方 |
 * |------|-------|
 * | 分割の不純度が同じ | 列の順で前のもの、同じ列なら境界の小さいもの |
 * | 葉の重みの合計が同じ | 先に現れたラベル |
 */
final class WeightedTree
{
    /** 重みを付けない。 */
    public const string NONE = 'none';

    /** クラスの件数に反比例する重みを付ける。 */
    public const string BALANCED = 'balanced';

    /**
     * 重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。
     *
     * @param list<string> $labels
     * @param list<float>  $weights
     */
    public static function gini(array $labels, array $weights): float
    {
        $total = array_sum($weights);
        $impurity = 1.0;

        foreach (self::weightSums($labels, $weights) as $weight) {
            $impurity -= ($weight / $total) ** 2;
        }

        return $impurity;
    }

    /**
     * クラスの件数に反比例する重み（件数 ÷ (クラスの数 × そのクラスの件数)）を 1 件ごとに求める。
     *
     * @param list<string> $t
     *
     * @return list<float>
     */
    public static function balancedWeights(array $t): array
    {
        $counts = array_count_values($t);

        return array_map(
            static fn (string $label): float => count($t) / (count($counts) * $counts[$label]),
            $t,
        );
    }

    /**
     * クラスの重みの付け方から、1 件ごとの重みを求める。
     *
     * @param list<string> $t
     *
     * @return list<float>
     */
    public static function weightsOf(array $t, string $classWeight): array
    {
        return match ($classWeight) {
            self::NONE => array_fill(0, count($t), 1.0),
            self::BALANCED => self::balancedWeights($t),
            default => throw new InvalidArgumentException("知らない重みの付け方です: {$classWeight}"),
        };
    }

    /**
     * 重みの合計が最も大きいラベル。同じなら先に現れたラベルを選ぶ。
     *
     * @param list<string> $labels
     * @param list<float>  $weights
     */
    public static function majority(array $labels, array $weights): string
    {
        $sums = self::weightSums($labels, $weights);
        $best = array_key_first($sums);

        if ($best === null) {
            throw new InvalidArgumentException('ラベルがありません');
        }

        foreach ($sums as $label => $weight) {
            if ($weight > $sums[$best]) {
                $best = $label;
            }
        }

        return (string) $best;
    }

    /**
     * 左右の重み付き不純度の、重みによる平均が最も小さくなる分割を返す。分けられなければ null。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<float>                $w
     * @param list<string>               $columns
     *
     * @return array{feature: string, threshold: float, impurity: float}|null
     */
    public static function bestSplit(array $x, array $t, array $w, array $columns): ?array
    {
        if ($x === [] || self::gini($t, $w) === 0.0) {
            return null;
        }

        $total = array_sum($w);
        $best = null;

        foreach ($columns as $column) {
            foreach (self::candidates($x, $t, $w, $column, $total) as $candidate) {
                // 厳密な不等号なので、同点なら先に見た候補（列の順・境界の小さい順）が残る。
                if ($best === null || $candidate['impurity'] < $best['impurity']) {
                    $best = $candidate;
                }
            }
        }

        return $best;
    }

    /**
     * 訓練データから、クラスの重みを付けた決定木を作る。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     * @param int|null                   $maxDepth 深さの上限。null なら上限なし
     */
    public static function fit(array $x, array $t, array $columns, ?int $maxDepth, string $classWeight): TreeNode
    {
        if ($x === []) {
            throw new InvalidArgumentException('訓練データが空です');
        }

        if (count($x) !== count($t)) {
            throw new InvalidArgumentException(sprintf('件数が違います: %d と %d', count($x), count($t)));
        }

        return self::build($x, $t, self::weightsOf($t, $classWeight), $columns, $maxDepth);
    }

    /**
     * 特徴量ごとのラベルを予測する。
     *
     * @param list<array<string, float>> $x
     *
     * @return list<string>
     */
    public static function predict(TreeNode $tree, array $x): array
    {
        return array_map($tree->predictOne(...), $x);
    }

    /**
     * ラベルごとの重みの合計を、ラベルが先に現れた順に返す。
     *
     * @param list<string> $labels
     * @param list<float>  $weights
     *
     * @return array<string, float>
     */
    private static function weightSums(array $labels, array $weights): array
    {
        if (count($labels) !== count($weights)) {
            throw new InvalidArgumentException(sprintf('件数が違います: %d と %d', count($labels), count($weights)));
        }

        $sums = [];

        foreach ($labels as $index => $label) {
            $sums[$label] = ($sums[$label] ?? 0.0) + $weights[$index];
        }

        return $sums;
    }

    /**
     * 1 つの列で、隣り合う値の中点を境界にした分割の候補をすべて返す。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<float>                $w
     *
     * @return list<array{feature: string, threshold: float, impurity: float}>
     */
    private static function candidates(array $x, array $t, array $w, string $feature, float $total): array
    {
        $triples = [];

        foreach ($x as $index => $features) {
            if (!array_key_exists($feature, $features)) {
                throw new InvalidArgumentException("特徴量がありません: {$feature}");
            }

            $triples[] = [$features[$feature], $t[$index], $w[$index]];
        }

        usort($triples, static fn (array $a, array $b): int => $a[0] <=> $b[0]);

        $candidates = [];

        for ($i = 1; $i < count($triples); ++$i) {
            if ($triples[$i - 1][0] === $triples[$i][0]) {
                continue;
            }

            $left = array_slice($triples, 0, $i);
            $right = array_slice($triples, $i);
            $leftWeights = array_column($left, 2);
            $rightWeights = array_column($right, 2);

            $candidates[] = [
                'feature' => $feature,
                'threshold' => ($triples[$i - 1][0] + $triples[$i][0]) / 2.0,
                'impurity' => (
                    array_sum($leftWeights) * self::gini(array_column($left, 1), $leftWeights)
                    + array_sum($rightWeights) * self::gini(array_column($right, 1), $rightWeights)
                ) / $total,
            ];
        }

        return $candidates;
    }

    /**
     * 深さの上限まで分割を繰り返して木を作る。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<float>                $w
     * @param list<string>               $columns
     */
    private static function build(array $x, array $t, array $w, array $columns, ?int $maxDepth): TreeNode
    {
        $split = $maxDepth === 0 ? null : self::bestSplit($x, $t, $w, $columns);

        if ($split === null) {
            return new Leaf(self::majority($t, $w));
        }

        $leftIndexes = [];
        $rightIndexes = [];

        foreach ($x as $index => $features) {
            if ($features[$split['feature']] <= $split['threshold']) {
                $leftIndexes[] = $index;
            } else {
                $rightIndexes[] = $index;
            }
        }

        $nextDepth = $maxDepth === null ? null : $maxDepth - 1;

        return new Branch(
            $split['feature'],
            $split['threshold'],
            self::buildFrom($leftIndexes, $x, $t, $w, $columns, $nextDepth),
            self::buildFrom($rightIndexes, $x, $t, $w, $columns, $nextDepth),
        );
    }

    /**
     * 添字で選んだ行だけを使って、部分木を作る。
     *
     * @param list<int>                  $indexes
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<float>                $w
     * @param list<string>               $columns
     */
    private static function buildFrom(array $indexes, array $x, array $t, array $w, array $columns, ?int $maxDepth): TreeNode
    {
        return self::build(
            array_map(static fn (int $i): array => $x[$i], $indexes),
            array_map(static fn (int $i): string => $t[$i], $indexes),
            array_map(static fn (int $i): float => $w[$i], $indexes),
            $columns,
            $maxDepth,
        );
    }
}
