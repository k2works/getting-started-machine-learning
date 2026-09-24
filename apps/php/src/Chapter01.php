<?php

declare(strict_types=1);

namespace GettingStartedMl;

use InvalidArgumentException;

/** 第 1 章: 人間が決めたルールできのこ派・たけのこ派を判定する。 */
final class Chapter01
{
    /** 「20 代ならきのこ派」というルールの年代。 */
    public const int KINOKO_AGE_GROUP = 20;

    /** きのこ派の呼び名。 */
    public const string KINOKO = 'きのこ';

    /** たけのこ派の呼び名。 */
    public const string TAKENOKO = 'たけのこ';

    /**
     * CSV を読み込み、列名で値を取り出して人物のリストにする。
     *
     * PHP の fgetcsv は BOM を取り除かないので、先頭の列名から自分で取り除く。
     *
     * @return list<Person>
     */
    public static function loadPeople(string $csvFile): array
    {
        $handle = @fopen($csvFile, 'r');

        if ($handle === false) {
            throw new InvalidArgumentException("CSV を開けません: {$csvFile}");
        }

        try {
            $header = fgetcsv($handle, escape: '');

            if ($header === false) {
                throw new InvalidArgumentException("CSV が空です: {$csvFile}");
            }

            /** @var list<string> $columns */
            $columns = array_map(self::stripBom(...), array_map(strval(...), $header));

            $people = [];

            while (($cells = fgetcsv($handle, escape: '')) !== false) {
                // 空行は読み飛ばす。
                if ($cells === [null] || $cells === ['']) {
                    continue;
                }

                /** @var array<string, string> $row */
                $row = array_combine($columns, array_map(strval(...), $cells));

                $people[] = new Person(
                    self::number($row, '身長'),
                    self::number($row, '体重'),
                    self::number($row, '年代'),
                    self::text($row, '派閥'),
                );
            }

            return $people;
        } finally {
            fclose($handle);
        }
    }

    /**
     * 列名で数値を読む。整数として読めなければ例外を投げる。
     *
     * @param array<string, string> $row
     */
    public static function number(array $row, string $column): int
    {
        $cell = self::text($row, $column);

        if (filter_var($cell, FILTER_VALIDATE_INT) === false) {
            throw new InvalidArgumentException("{$column} を数値として読めません: {$cell}");
        }

        return (int) $cell;
    }

    /**
     * 列名でセルを読む。列が無ければ例外を投げる。
     *
     * @param array<string, string> $row
     */
    public static function text(array $row, string $column): string
    {
        if (!array_key_exists($column, $row)) {
            throw new InvalidArgumentException("列がありません: {$column}");
        }

        return $row[$column];
    }

    /**
     * 人物のリストを特徴量と正解ラベルに分ける。
     *
     * @param list<Person> $people
     *
     * @return array{list<Features>, list<string>}
     */
    public static function splitFeaturesAndLabels(array $people): array
    {
        $features = array_map(
            static fn (Person $person): Features => new Features($person->height, $person->weight, $person->ageGroup),
            $people,
        );
        $labels = array_map(static fn (Person $person): string => $person->faction, $people);

        return [$features, $labels];
    }

    /** 人間が決めたルールで派閥を判定する。 */
    public static function predictByRule(Features $features): string
    {
        return $features->ageGroup === self::KINOKO_AGE_GROUP ? self::KINOKO : self::TAKENOKO;
    }

    /**
     * 予測が正解ラベルと一致した割合を返す。件数が違えば例外を投げる。
     *
     * @param list<string> $predictions
     * @param list<string> $labels
     */
    public static function accuracy(array $predictions, array $labels): float
    {
        if (count($predictions) !== count($labels)) {
            throw new InvalidArgumentException(
                sprintf('予測と正解ラベルの件数が違います: %d と %d', count($predictions), count($labels)),
            );
        }

        if ($labels === []) {
            throw new InvalidArgumentException('正解ラベルがありません');
        }

        $hits = 0;

        foreach ($labels as $i => $label) {
            if ($predictions[$i] === $label) {
                ++$hits;
            }
        }

        return $hits / count($labels);
    }

    /** 実データでルールによる判定の正解率を表示する。 */
    public static function run(?string $csvFile = null): string
    {
        $people = self::loadPeople($csvFile ?? Dataset::path('KvsT.csv'));
        [$x, $t] = self::splitFeaturesAndLabels($people);
        $predictions = array_map(self::predictByRule(...), $x);

        return sprintf(
            "データ件数: %d\nルールによる判定の正解率: %.4f\n",
            count($people),
            self::accuracy($predictions, $t),
        );
    }

    /** UTF-8 の BOM を取り除く。 */
    private static function stripBom(string $text): string
    {
        return str_starts_with($text, "\u{FEFF}") ? substr($text, 3) : $text;
    }
}
