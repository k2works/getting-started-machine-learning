<?php

declare(strict_types=1);

namespace GettingStartedMl;

/** 学習データのディレクトリを求める。 */
final class Dataset
{
    /** 実データの置き場をテストや CI から差し替えるための環境変数の名前。 */
    public const string ENV_NAME = 'ML_DATA_DIR';

    /** 既定の置き場。テストは apps/php で走るので、相対パスで apps/data に届く。 */
    public const string DEFAULT_DIR = '../data/sukkiri-ml';

    /**
     * 学習データのディレクトリを返す。
     *
     * 環境変数はテストで差し替えられるように引数で受け取る。
     *
     * @param array<string, string>|null $env
     */
    public static function dir(?array $env = null): string
    {
        $env ??= getenv();
        $value = $env[self::ENV_NAME] ?? '';

        return $value === '' ? self::DEFAULT_DIR : $value;
    }

    /** 学習データのファイルへの道を返す。 */
    public static function path(string $name): string
    {
        return self::dir() . '/' . $name;
    }

    /** 学習データがあるかを返す。実データのテストはこれで外す。 */
    public static function exists(string $name): bool
    {
        return is_file(self::path($name));
    }
}
