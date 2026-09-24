<?php

declare(strict_types=1);

namespace GettingStartedMl;

use InvalidArgumentException;

/**
 * 学習した線形回帰のモデル。切片と、列名と同じ順に並んだ係数を持つ。
 *
 * 連想配列でも列の順は保てるが、列名と係数を別々のリストで持つほうが
 * 「順があること」がコードから読み取れる。第 12 章のリッジ・ラッソでも使う。
 */
final readonly class LinearModel
{
    /**
     * @param list<string> $columns
     * @param list<float>  $coefficients
     */
    public function __construct(
        public float $intercept,
        public array $columns,
        public array $coefficients,
    ) {
        if (count($columns) !== count($coefficients)) {
            throw new InvalidArgumentException(
                sprintf('列名と係数の数が違います: %d と %d', count($columns), count($coefficients)),
            );
        }
    }

    /** 列名で係数を読む。無ければ失敗する。 */
    public function coefficient(string $column): float
    {
        $index = array_search($column, $this->columns, true);

        if ($index === false) {
            throw new InvalidArgumentException("係数がありません: {$column}");
        }

        return $this->coefficients[$index];
    }

    /**
     * 1 行分の特徴量の予測値。係数は列名で対応させるので、特徴量の列の並び順は問わない。
     *
     * @param array<string, float> $features
     */
    public function predictOne(array $features): float
    {
        $sum = $this->intercept;

        foreach ($this->columns as $index => $column) {
            if (!array_key_exists($column, $features)) {
                throw new InvalidArgumentException("特徴量がありません: {$column}");
            }

            $sum += $this->coefficients[$index] * $features[$column];
        }

        return $sum;
    }

    /**
     * 行ごとの予測値。
     *
     * @param list<array<string, float>> $x
     *
     * @return list<float>
     */
    public function predict(array $x): array
    {
        return array_map($this->predictOne(...), $x);
    }
}
