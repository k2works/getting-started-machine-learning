<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter09;

use InvalidArgumentException;

/**
 * 列ごとの平均と標準偏差を覚えて、同じ物差しで標準化する。
 *
 * 標準偏差は件数 n で割る母標準偏差を使う。Rubix ML の ZScaleStandardizer と
 * 同じ定義なので、突き合わせたときに一致する（Tribuo の n−1 とは違う）。
 */
final readonly class Standardizer
{
    /**
     * @param list<string>         $columns
     * @param array<string, float> $means
     * @param array<string, float> $stds
     */
    public function __construct(
        public array $columns,
        public array $means,
        public array $stds,
    ) {
    }

    /**
     * 特徴量の並びから、列ごとの平均と標準偏差を求める。
     *
     * すべて同じ値の列は標準偏差を 1 にして、標準化した値が 0 になるようにする。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $columns
     */
    public static function fit(array $x, array $columns): self
    {
        if ($x === []) {
            throw new InvalidArgumentException('特徴量が 1 件もありません');
        }

        $means = [];
        $stds = [];

        foreach ($columns as $column) {
            $values = array_map(
                static fn (array $features): float => self::value($features, $column),
                $x,
            );
            $mean = array_sum($values) / count($values);
            $variance = array_sum(
                array_map(static fn (float $v): float => ($v - $mean) * ($v - $mean), $values),
            ) / count($values);
            $std = sqrt($variance);

            $means[$column] = $mean;
            $stds[$column] = $std === 0.0 ? 1.0 : $std;
        }

        return new self($columns, $means, $stds);
    }

    /**
     * 1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。
     *
     * @param array<string, float> $features
     *
     * @return array<string, float>
     */
    public function transform(array $features): array
    {
        $standardized = [];

        foreach ($features as $column => $value) {
            $standardized[$column] = array_key_exists($column, $this->means)
                ? ($value - $this->means[$column]) / $this->stds[$column]
                : $value;
        }

        return $standardized;
    }

    /**
     * 特徴量の並びを標準化する。
     *
     * @param list<array<string, float>> $x
     *
     * @return list<array<string, float>>
     */
    public function transformAll(array $x): array
    {
        return array_map($this->transform(...), $x);
    }

    /**
     * 特徴量を列の順に並べた数値の行にする。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $columns
     *
     * @return list<list<float>>
     */
    public static function toRows(array $x, array $columns): array
    {
        return array_map(
            static fn (array $features): array => array_map(
                static fn (string $c): float => self::value($features, $c),
                $columns,
            ),
            $x,
        );
    }

    /**
     * 列の値を読む。列が無ければ失敗する。
     *
     * @param array<string, float> $features
     */
    private static function value(array $features, string $column): float
    {
        if (!array_key_exists($column, $features)) {
            throw new InvalidArgumentException("列がありません: {$column}");
        }

        return $features[$column];
    }
}
