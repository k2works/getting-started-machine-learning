<?php

declare(strict_types=1);

namespace GettingStartedMl\Preprocessing;

use GettingStartedMl\Chapter02;
use GettingStartedMl\Chapter08;
use GettingStartedMl\Table;
use InvalidArgumentException;

/**
 * グループごとの中央値で欠損値を補完する。
 *
 * Rubix ML の `MissingDataImputer` は列ごとに 1 つの値しか持てないので、
 * 「1 等客室の女性の中央値」と「3 等客室の男性の中央値」を使い分けられない。
 * この前処理は自作になる（第 8 章 8.12 節）。
 */
final readonly class GroupMedianImputer implements Step
{
    /**
     * @param list<string>              $by       グループを決める列
     * @param array<string, float>|null $medians  グループごとの中央値（学習前は null）
     */
    public function __construct(
        public string $column,
        public array $by,
        public ?array $medians = null,
        public ?float $overallMedian = null,
    ) {
    }

    public function fit(Table $x): self
    {
        $known = array_values(
            array_filter($x->rows, fn (array $row): bool => !Chapter02::isMissing($row, $this->column)),
        );

        $grouped = [];

        foreach ($known as $row) {
            $grouped[$this->groupOf($row)][] = Chapter02::number($row, $this->column);
        }

        $medians = [];

        foreach ($grouped as $group => $values) {
            /** @var list<float> $values */
            $medians[(string) $group] = Chapter08::median($values);
        }

        return new self(
            $this->column,
            $this->by,
            $medians,
            Chapter08::median(array_map(
                fn (array $row): float => (float) Chapter02::number($row, $this->column),
                $known,
            )),
        );
    }

    public function apply(Table $x): Table
    {
        if ($this->medians === null || $this->overallMedian === null) {
            throw new InvalidArgumentException("学習していません: {$this->column}");
        }

        $medians = $this->medians;
        $overall = $this->overallMedian;

        $rows = array_map(
            function (array $row) use ($medians, $overall): array {
                if (!Chapter02::isMissing($row, $this->column)) {
                    return $row;
                }

                $row[$this->column] = self::asCell($medians[$this->groupOf($row)] ?? $overall);

                return $row;
            },
            $x->rows,
        );

        return new Table($x->columns, $rows);
    }

    /**
     * 行のグループを表すキー。グループを決める列の値をタブでつないだ文字列にする。
     *
     * PHP の配列のキーは文字列か整数しか取れないので、Elixir がリストをそのまま
     * キーにしたところを、区切り文字でつないだ文字列にする。
     *
     * @param array<string, string> $row
     */
    private function groupOf(array $row): string
    {
        return implode("\t", array_map(static fn (string $c): string => Chapter02::text($row, $c), $this->by));
    }

    /**
     * 補完した値を、表のセル（文字列）に戻す。
     *
     * 整数で表せる値は「35」と書く。「35.0」と書くと、CSV から読んだ値と
     * 見た目が食い違ってテストが読みにくくなる。
     */
    private static function asCell(float $value): string
    {
        return $value === floor($value) ? (string) (int) $value : (string) $value;
    }
}
