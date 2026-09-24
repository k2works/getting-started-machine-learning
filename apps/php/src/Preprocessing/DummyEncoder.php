<?php

declare(strict_types=1);

namespace GettingStartedMl\Preprocessing;

use GettingStartedMl\Chapter02;
use GettingStartedMl\Table;
use InvalidArgumentException;

/**
 * カテゴリの列を 0 と 1 の列に広げる（ダミー変数化）。
 *
 * カテゴリを並べ替え、**先頭を基準にして落とす**。落とさないと列どうしの和が
 * 必ず 1 になり、線形回帰では解が 1 つに定まらなくなる。Rubix ML の
 * `OneHotEncoder` は全部のカテゴリを残すので、この口が無い（第 8 章 8.12 節）。
 */
final readonly class DummyEncoder implements Step
{
    /**
     * @param list<string>                     $columns
     * @param array<string, list<string>>|null $categories 列ごとの、基準を落としたカテゴリの並び
     */
    public function __construct(
        public array $columns,
        public ?array $categories = null,
    ) {
    }

    public function fit(Table $x): self
    {
        $categories = [];

        foreach ($this->columns as $column) {
            $values = [];

            foreach ($x->rows as $row) {
                if (!Chapter02::isMissing($row, $column)) {
                    $values[] = Chapter02::text($row, $column);
                }
            }

            $unique = array_values(array_unique($values));
            sort($unique, SORT_STRING);
            // 先頭が基準。残りがダミー変数の列になる。
            $categories[$column] = array_slice($unique, 1);
        }

        return new self($this->columns, $categories);
    }

    public function apply(Table $x): Table
    {
        if ($this->categories === null) {
            throw new InvalidArgumentException('学習していません: ' . implode(', ', $this->columns));
        }

        $columns = $x->columns;

        foreach ($this->categories as $column => $categories) {
            $columns = array_values(array_filter($columns, static fn (string $c): bool => $c !== $column));

            foreach ($categories as $category) {
                $columns[] = self::dummyColumn($column, $category);
            }
        }

        return new Table($columns, array_map($this->encodeRow(...), $x->rows));
    }

    /** ダミー変数の列の名前。「元の列名_カテゴリ」にする。 */
    public static function dummyColumn(string $column, string $category): string
    {
        return "{$column}_{$category}";
    }

    /**
     * 1 行を、学習したカテゴリごとの 0 と 1 の列に広げる。元の列はそのまま残る。
     *
     * 学習に無いカテゴリや欠損値は、すべての列が 0 になる。
     *
     * @param array<string, string> $row
     *
     * @return array<string, string>
     */
    private function encodeRow(array $row): array
    {
        foreach ($this->categories ?? [] as $column => $categories) {
            $value = Chapter02::text($row, $column);

            foreach ($categories as $category) {
                $row[self::dummyColumn($column, $category)] = $value === $category ? '1' : '0';
            }
        }

        return $row;
    }
}
