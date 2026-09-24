<?php

declare(strict_types=1);

namespace GettingStartedMl;

use InvalidArgumentException;

/**
 * 第 2 章: データの前処理。
 *
 * 表の読み込み・欠損値の補完・訓練データとテストデータへの分割。
 */
final class Chapter02
{
    /** アヤメのデータの正解ラベルの列。 */
    public const string TARGET = '種類';

    /**
     * 文字列の列を読む。列が無ければ失敗する。
     *
     * @param array<string, string> $row
     */
    public static function text(array $row, string $column): string
    {
        if (!array_key_exists($column, $row)) {
            throw new InvalidArgumentException("列がありません: {$column}");
        }

        return (string) $row[$column];
    }

    /**
     * セルが空欄かどうかを返す。
     *
     * @param array<string, string> $row
     */
    public static function isMissing(array $row, string $column): bool
    {
        return trim(self::text($row, $column)) === '';
    }

    /**
     * 数値の列を読む。空欄なら null を返す。数値として読めなければ失敗する。
     *
     * PHP には Option が無いので、欠損値は null で表す。
     *
     * @param array<string, string> $row
     */
    public static function number(array $row, string $column): ?float
    {
        $cell = trim(self::text($row, $column));

        if ($cell === '') {
            return null;
        }

        if (!is_numeric($cell)) {
            throw new InvalidArgumentException("{$column} を数値として読めません: {$cell}");
        }

        return (float) $cell;
    }

    /** CSV を読み込んで表にする。列の順は CSV の順のまま。 */
    public static function loadTable(string $path): Table
    {
        return Csv::readTable($path);
    }

    /**
     * 列ごとに欠損値の数を数える。列の順は表の列の順のまま。
     *
     * @return array<string, int>
     */
    public static function countMissing(Table $table): array
    {
        $counts = [];

        foreach ($table->columns as $column) {
            $counts[$column] = count(
                array_filter($table->rows, static fn (array $row): bool => self::isMissing($row, $column)),
            );
        }

        return $counts;
    }

    /**
     * 欠損値を除いて、列ごとの平均値を求める。
     *
     * @param list<array<string, string>> $rows
     * @param list<string>                $columns
     *
     * @return array<string, float>
     */
    public static function columnMeans(array $rows, array $columns): array
    {
        $means = [];

        foreach ($columns as $column) {
            $values = [];

            foreach ($rows as $row) {
                $value = self::number($row, $column);

                if ($value !== null) {
                    $values[] = $value;
                }
            }

            if ($values === []) {
                throw new InvalidArgumentException("値がすべて空欄です: {$column}");
            }

            $means[$column] = array_sum($values) / count($values);
        }

        return $means;
    }

    /**
     * 欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変えない。
     *
     * @param list<array<string, string>> $rows
     * @param list<string>                $columns
     * @param array<string, float>        $fillValues
     *
     * @return list<array<string, float>>
     */
    public static function fillMissing(array $rows, array $columns, array $fillValues): array
    {
        return array_map(
            static function (array $row) use ($columns, $fillValues): array {
                $filled = [];

                foreach ($columns as $column) {
                    $value = self::number($row, $column);

                    if ($value === null) {
                        if (!array_key_exists($column, $fillValues)) {
                            throw new InvalidArgumentException("補完する値がありません: {$column}");
                        }

                        $value = $fillValues[$column];
                    }

                    $filled[$column] = $value;
                }

                return $filled;
            },
            $rows,
        );
    }

    /**
     * 正解ラベルの列を取り出し、残りの列を特徴量の列にする。
     *
     * @return array{list<string>, list<array<string, string>>, list<string>}
     */
    public static function splitFeaturesAndTarget(Table $table, string $targetColumn): array
    {
        $columns = array_values(
            array_filter($table->columns, static fn (string $c): bool => $c !== $targetColumn),
        );
        $labels = array_map(
            static fn (array $row): string => self::text($row, $targetColumn),
            $table->rows,
        );

        return [$columns, $table->rows, $labels];
    }

    /**
     * 並べ替えてから、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。
     *
     * @template TRow
     *
     * @param list<TRow>   $x
     * @param list<string> $t
     *
     * @return array{xTrain: list<TRow>, xTest: list<TRow>, tTrain: list<string>, tTest: list<string>}
     */
    public static function splitTrainTest(array $x, array $t, float $testSize, int $seed): array
    {
        if (count($x) !== count($t)) {
            throw new InvalidArgumentException(sprintf('件数が違います: %d と %d', count($x), count($t)));
        }

        $pairs = [];

        foreach ($x as $i => $row) {
            $pairs[] = [$row, $t[$i]];
        }

        $shuffled = Random::shuffle($pairs, $seed);
        $trainCount = count($shuffled) - (int) ceil(count($shuffled) * $testSize);
        $train = array_slice($shuffled, 0, $trainCount);
        $test = array_slice($shuffled, $trainCount);

        return [
            'xTrain' => array_column($train, 0),
            'xTest' => array_column($test, 0),
            'tTrain' => array_map(static fn (array $p): string => (string) $p[1], $train),
            'tTest' => array_map(static fn (array $p): string => (string) $p[1], $test),
        ];
    }

    /**
     * iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。
     *
     * @return array{xTrain: list<array<string, float>>, xTest: list<array<string, float>>, tTrain: list<string>, tTest: list<string>}
     */
    public static function prepareIris(string $path, float $testSize, int $seed): array
    {
        [$columns, $rows, $labels] = self::splitFeaturesAndTarget(self::loadTable($path), self::TARGET);
        $split = self::splitTrainTest($rows, $labels, $testSize, $seed);
        $means = self::columnMeans($split['xTrain'], $columns);

        return [
            'xTrain' => self::fillMissing($split['xTrain'], $columns, $means),
            'xTest' => self::fillMissing($split['xTest'], $columns, $means),
            'tTrain' => $split['tTrain'],
            'tTest' => $split['tTest'],
        ];
    }

    /** アヤメのデータの前処理の結果を表示する。 */
    public static function run(?string $path = null): string
    {
        $path ??= Dataset::path('iris.csv');
        $table = self::loadTable($path);
        $split = self::prepareIris($path, 0.3, 0);

        $missing = [];

        foreach (self::countMissing($table) as $column => $count) {
            $missing[] = "{$column}={$count}";
        }

        $features = array_filter($table->columns, static fn (string $c): bool => $c !== self::TARGET);

        return sprintf(
            "データ件数: %d\n欠損値の数: %s\n訓練データ: %d 件, テストデータ: %d 件\n特徴量: %s\n",
            count($table->rows),
            implode(', ', $missing),
            count($split['xTrain']),
            count($split['xTest']),
            implode(', ', $features),
        );
    }
}
