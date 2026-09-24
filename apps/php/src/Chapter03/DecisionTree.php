<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter03;

use InvalidArgumentException;

/**
 * 第 3 章の自作の決定木。
 *
 * ジニ不純度がいちばん小さくなる分割を探し、深さの上限まで繰り返して木を作る。
 * 木は `Leaf|Node` の共用型で表す。
 */
final readonly class DecisionTree
{
    private function __construct(public Leaf|Node $tree)
    {
    }

    /**
     * 訓練データから木を作る。`$maxDepth` が null なら深さの上限なし。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     */
    public static function fit(array $x, array $t, array $columns, ?int $maxDepth = null): self
    {
        if (count($x) !== count($t)) {
            throw new InvalidArgumentException(
                sprintf('特徴量と正解ラベルの件数が違います: %d と %d', count($x), count($t)),
            );
        }

        if ($t === []) {
            throw new InvalidArgumentException('正解ラベルがありません');
        }

        return new self(self::build($x, $t, $columns, $maxDepth));
    }

    /**
     * ジニ不純度。ラベルが 1 種類なら 0 で、種類が多く均等なほど大きい。
     *
     * @param list<string> $labels
     */
    public static function gini(array $labels): float
    {
        $counts = self::countLabels($labels);

        if ($counts === []) {
            return 0.0;
        }

        $total = count($labels);
        $sum = 0.0;

        foreach ($counts as $count) {
            $sum += ($count / $total) ** 2;
        }

        return 1.0 - $sum;
    }

    /**
     * いちばん多いラベルを返す。同数なら先に現れたほうを選ぶ。
     *
     * @param list<string> $labels
     */
    public static function majority(array $labels): string
    {
        if ($labels === []) {
            throw new InvalidArgumentException('正解ラベルがありません');
        }

        $best = $labels[0];
        $bestCount = 0;

        // countLabels は最初に現れた順を保つので、先頭からたどれば同数のとき先が残る。
        // 数字だけのラベルは鍵が int になっているので、文字列に戻してから返す。
        foreach (self::countLabels($labels) as $label => $count) {
            if ($count > $bestCount) {
                $best = (string) $label;
                $bestCount = $count;
            }
        }

        return $best;
    }

    /**
     * 左右の不純度の重み付き平均が最も小さくなる分割を返す。分けられなければ null。
     *
     * 同じ不純度なら先に見つけた（列の順で前の）分割を選ぶ。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     */
    public static function bestSplit(array $x, array $t, array $columns): ?Split
    {
        if ($x === [] || self::gini($t) === 0.0) {
            return null;
        }

        $best = null;

        foreach ($columns as $column) {
            foreach (self::candidates($x, $t, $column) as $candidate) {
                if ($best === null || $candidate->impurity < $best->impurity) {
                    $best = $candidate;
                }
            }
        }

        return $best;
    }

    /**
     * 特徴量ごとのラベルを予測する。
     *
     * @param list<array<string, float>> $x
     *
     * @return list<string>
     */
    public function predict(array $x): array
    {
        return array_map(fn (array $features): string => self::predictOne($this->tree, $features), $x);
    }

    /**
     * 木をたどって 1 件のラベルを予測する。
     *
     * @param array<string, float> $features
     */
    public static function predictOne(Leaf|Node $tree, array $features): string
    {
        // 共用型なので、葉でなければ節であることが静的解析にも伝わる。
        if ($tree instanceof Leaf) {
            return $tree->label;
        }

        $value = self::feature($features, $tree->split->feature);

        return self::predictOne($tree->split->goesLeft($value) ? $tree->left : $tree->right, $features);
    }

    /** 木を字下げ付きの文字列にする。 */
    public function format(): string
    {
        return self::formatTree($this->tree, '');
    }

    /** 木を字下げ付きの文字列にする。 */
    public static function formatTree(Leaf|Node $tree, string $indent = ''): string
    {
        if ($tree instanceof Leaf) {
            return "{$indent}{$tree->label}\n";
        }

        // PHP 8.0 以降の sprintf はロケールに依らないので、小数点は常に「.」になる。
        $border = sprintf('%.4f', $tree->split->threshold);
        $feature = $tree->split->feature;

        return "{$indent}{$feature} <= {$border}\n"
            . self::formatTree($tree->left, $indent . '  ')
            . "{$indent}{$feature} > {$border}\n"
            . self::formatTree($tree->right, $indent . '  ');
    }

    /**
     * 深さの上限まで分割を繰り返して木を作る。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     */
    private static function build(array $x, array $t, array $columns, ?int $maxDepth): Leaf|Node
    {
        $split = $maxDepth === 0 ? null : self::bestSplit($x, $t, $columns);

        if ($split === null) {
            return new Leaf(self::majority($t));
        }

        $leftX = $rightX = $leftT = $rightT = [];

        foreach ($x as $i => $features) {
            if ($split->goesLeft(self::feature($features, $split->feature))) {
                $leftX[] = $features;
                $leftT[] = $t[$i];
            } else {
                $rightX[] = $features;
                $rightT[] = $t[$i];
            }
        }

        $nextDepth = $maxDepth === null ? null : $maxDepth - 1;

        return new Node(
            $split,
            self::build($leftX, $leftT, $columns, $nextDepth),
            self::build($rightX, $rightT, $columns, $nextDepth),
        );
    }

    /**
     * 1 つの列で、隣り合う値の中点を境界にした分割の候補をすべて返す。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     *
     * @return list<Split>
     */
    private static function candidates(array $x, array $t, string $feature): array
    {
        $pairs = [];

        foreach ($x as $i => $features) {
            $pairs[] = [self::feature($features, $feature), $t[$i]];
        }

        // PHP 8.0 以降の usort は安定なので、同じ値の並びは元の順のまま。
        usort($pairs, static fn (array $a, array $b): int => $a[0] <=> $b[0]);

        $values = array_column($pairs, 0);
        $labels = array_column($pairs, 1);
        $splits = [];

        for ($i = 1, $n = count($pairs); $i < $n; ++$i) {
            if ($values[$i - 1] === $values[$i]) {
                continue;
            }

            $splits[] = new Split(
                $feature,
                ($values[$i - 1] + $values[$i]) / 2.0,
                self::weightedGini(array_slice($labels, 0, $i), array_slice($labels, $i)),
            );
        }

        return $splits;
    }

    /**
     * 左右のジニ不純度の重み付き平均。
     *
     * @param list<string> $left
     * @param list<string> $right
     */
    private static function weightedGini(array $left, array $right): float
    {
        return (count($left) * self::gini($left) + count($right) * self::gini($right))
            / (count($left) + count($right));
    }

    /**
     * ラベルごとの件数を、最初に現れた順で返す。
     *
     * PHP の配列は「数字だけの文字列」の鍵を勝手に整数に変えるので、戻り値の鍵は
     * string とは限らない。array_count_values を使っても同じことが起きるため、
     * 型を偽らずに array-key で受け、使う側で文字列に戻す。
     *
     * @param list<string> $labels
     *
     * @return array<array-key, int>
     */
    private static function countLabels(array $labels): array
    {
        $counts = [];

        foreach ($labels as $label) {
            $counts[$label] = ($counts[$label] ?? 0) + 1;
        }

        return $counts;
    }

    /**
     * 特徴量から列の値を読む。列が無ければ失敗する。
     *
     * @param array<string, float> $features
     */
    private static function feature(array $features, string $column): float
    {
        if (!array_key_exists($column, $features)) {
            throw new InvalidArgumentException("列がありません: {$column}");
        }

        return $features[$column];
    }
}
