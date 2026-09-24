<?php

declare(strict_types=1);

namespace GettingStartedMl;

use InvalidArgumentException;

/**
 * CSV を列名つきで読む。
 *
 * PHP の fgetcsv は BOM を取り除かないので、先頭の列名から自分で取り除く。
 */
final class Csv
{
    /** UTF-8 の BOM。記事とコードに実物を混ぜないようエスケープで書く。 */
    private const string BOM = "\u{FEFF}";

    /**
     * CSV を読み、1 行目を列名にした連想配列のリストを返す。
     *
     * 値は文字列のまま返し、数値への変換は章ごとに行う。
     *
     * @return list<array<string, string>>
     */
    public static function read(string $path, string $separator = ','): array
    {
        return self::readTable($path, $separator)->rows;
    }

    /**
     * CSV を読み、列名の並びと行のリストを返す。
     *
     * PHP の連想配列はキーの順を保つが、列の順を明示して持ち回るほうが
     * ほかの言語版と同じ形になるので、表としてまとめて返す。
     */
    public static function readTable(string $path, string $separator = ','): Table
    {
        $contents = @file_get_contents($path);

        if ($contents === false) {
            throw new InvalidArgumentException("CSV を開けません: {$path}");
        }

        return self::parseTable($contents, $separator);
    }

    /** 文字列を「列名の並び」と「行のリスト」にする。 */
    public static function parseTable(string $contents, string $separator = ','): Table
    {
        if ($separator !== ',' && $separator !== "\t") {
            throw new InvalidArgumentException("区切り文字に対応していません: {$separator}");
        }

        $handle = fopen('php://memory', 'r+');

        if ($handle === false) {
            throw new InvalidArgumentException('メモリ上のファイルを開けません');
        }

        try {
            fwrite($handle, $contents);
            rewind($handle);

            $header = fgetcsv($handle, separator: $separator, escape: '');

            if ($header === false) {
                throw new InvalidArgumentException('CSV が空です');
            }

            /** @var list<string> $columns */
            $columns = array_map(self::stripBom(...), array_map(strval(...), $header));

            $rows = [];

            while (($cells = fgetcsv($handle, separator: $separator, escape: '')) !== false) {
                /** @var list<string> $values */
                $values = array_map(strval(...), $cells);

                // 全部の欄が空の行は読み飛ばす。
                if (array_filter($values, static fn (string $v): bool => trim($v) !== '') === []) {
                    continue;
                }

                if (count($values) !== count($columns)) {
                    throw new InvalidArgumentException(
                        sprintf('列の数が違います: %d と %d', count($values), count($columns)),
                    );
                }

                $rows[] = array_combine($columns, $values);
            }

            return new Table($columns, $rows);
        } finally {
            fclose($handle);
        }
    }

    /** UTF-8 の BOM を取り除く。文字列はバイト列なので 3 バイト分を落とす。 */
    private static function stripBom(string $text): string
    {
        return str_starts_with($text, self::BOM) ? substr($text, 3) : $text;
    }
}
