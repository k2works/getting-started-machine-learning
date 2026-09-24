<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

use JsonException;

/**
 * 要求の JSON を読み、検証してドメインの値にするプレゼンテーション層。
 *
 * `json_decode` は JSON の値をそのまま PHP の配列・数値・文字列にするので、
 * 「読み込みと同時に型が確かめられる」ことはない。型の確認は自分で書く。
 */
final class Validation
{
    /** JSON として読めないか、列の型が合わないときの理由。 */
    public const string INVALID_JSON = 'JSON の形式または値の型が正しくありません';

    /** 興行収入の予測の要求の列と型。原作の有無は整数で受け取る（1.5 を弾くため）。 */
    public const array MOVIE_TYPES = [
        'sns1' => 'number',
        'sns2' => 'number',
        'actor' => 'number',
        'original' => 'integer',
    ];

    /** 生存の予測の要求の列と型。年齢と乗船港は省略できる。 */
    public const array PASSENGER_TYPES = [
        'pclass' => 'integer',
        'sex' => 'string',
        'age' => 'number',
        'sib_sp' => 'integer',
        'parch' => 'integer',
        'fare' => 'number',
        'embarked' => 'string',
    ];

    private const array ORIGINAL_VALUES = [0, 1];
    private const array PASSENGER_CLASSES = [1, 2, 3];
    private const array SEXES = ['female', 'male'];
    private const array PORTS = ['C', 'Q', 'S'];

    /**
     * 本文を JSON のオブジェクトとして読み、列の型を確かめる。
     *
     * @param array<string, string> $types 列と型の表
     *
     * @return array<string, mixed>
     */
    public static function readJson(string $body, array $types): array
    {
        try {
            $decoded = json_decode($body, true, 512, JSON_THROW_ON_ERROR);
        } catch (JsonException) {
            throw new ValidationException([self::INVALID_JSON]);
        }

        // 配列でなければオブジェクトではない。`{}` は空の配列になるので、そこだけ許す。
        if (!is_array($decoded) || (array_is_list($decoded) && $decoded !== [])) {
            throw new ValidationException([self::INVALID_JSON]);
        }

        /** @var array<string, mixed> $decoded */
        foreach ($decoded as $field => $value) {
            if (!self::typed($types[$field] ?? null, $value)) {
                throw new ValidationException([self::INVALID_JSON]);
            }
        }

        return $decoded;
    }

    /** 値が型に合うかどうか。null と、表に無い列（型が null）は問わない。 */
    private static function typed(?string $type, mixed $value): bool
    {
        return match (true) {
            $value === null, $type === null => true,
            $type === 'number' => is_int($value) || is_float($value),
            $type === 'integer' => is_int($value),
            default => is_string($value),
        };
    }

    // ---- 検証の規則。問題が無ければ null を、あれば理由を返す ----

    /** 必須の列が空なら理由を返す。 */
    public static function required(string $field, mixed $value): ?string
    {
        return $value === null ? "{$field} は必須です" : null;
    }

    /** 負の数なら理由を返す。 */
    public static function notNegative(string $field, mixed $value): ?string
    {
        return (is_int($value) || is_float($value)) && $value < 0 ? "{$field} は 0 以上にしてください" : null;
    }

    /**
     * 選択肢の外の値なら理由を返す。選択肢は並べ替えて表示する。
     *
     * @param list<int|string> $allowed
     */
    public static function oneOf(string $field, mixed $value, array $allowed): ?string
    {
        if ($value === null || in_array($value, $allowed, true)) {
            return null;
        }

        sort($allowed);

        return "{$field} は " . implode('、', array_map(strval(...), $allowed)) . ' のどれかにしてください';
    }

    /**
     * 理由（null は問題なし）を集め、1 つでもあれば ValidationException を投げる。
     *
     * @param list<string|null> $reasons
     */
    public static function check(array $reasons): void
    {
        $errors = array_values(array_filter($reasons, static fn (?string $r): bool => $r !== null));

        if ($errors !== []) {
            throw new ValidationException($errors);
        }
    }

    /**
     * 検証して、正しければ映画の特徴量にする。
     *
     * @param array<string, mixed> $request
     */
    public static function movie(array $request): Movie
    {
        $sns1 = $request['sns1'] ?? null;
        $sns2 = $request['sns2'] ?? null;
        $actor = $request['actor'] ?? null;
        $original = $request['original'] ?? null;

        self::check([
            self::required('sns1', $sns1),
            self::required('sns2', $sns2),
            self::required('actor', $actor),
            self::required('original', $original),
            self::notNegative('sns1', $sns1),
            self::notNegative('sns2', $sns2),
            self::notNegative('actor', $actor),
            self::oneOf('original', $original, self::ORIGINAL_VALUES),
        ]);

        return new Movie(self::num($sns1), self::num($sns2), self::num($actor), self::int($original));
    }

    /**
     * 検証して、正しければ乗客の特徴量にする。年齢と乗船港は省略できる。
     *
     * @param array<string, mixed> $request
     */
    public static function passenger(array $request): Passenger
    {
        $pclass = $request['pclass'] ?? null;
        $sex = $request['sex'] ?? null;
        $age = $request['age'] ?? null;
        $sibSp = $request['sib_sp'] ?? null;
        $parch = $request['parch'] ?? null;
        $fare = $request['fare'] ?? null;
        $embarked = $request['embarked'] ?? null;

        self::check([
            self::required('pclass', $pclass),
            self::required('sex', $sex),
            self::required('sib_sp', $sibSp),
            self::required('parch', $parch),
            self::required('fare', $fare),
            self::oneOf('pclass', $pclass, self::PASSENGER_CLASSES),
            self::oneOf('sex', $sex, self::SEXES),
            self::notNegative('age', $age),
            self::notNegative('sib_sp', $sibSp),
            self::notNegative('parch', $parch),
            self::notNegative('fare', $fare),
            self::oneOf('embarked', $embarked, self::PORTS),
        ]);

        return new Passenger(
            self::int($pclass),
            self::text($sex),
            $age === null ? null : self::num($age),
            self::int($sibSp),
            self::int($parch),
            self::num($fare),
            $embarked === null ? null : self::text($embarked),
        );
    }

    // ---- 型を確かめてから取り出す。readJson を通っていれば必ず通る ----

    private static function num(mixed $value): float
    {
        return is_int($value) || is_float($value) ? (float) $value : throw new ValidationException([self::INVALID_JSON]);
    }

    private static function int(mixed $value): int
    {
        return is_int($value) ? $value : throw new ValidationException([self::INVALID_JSON]);
    }

    private static function text(mixed $value): string
    {
        return is_string($value) ? $value : throw new ValidationException([self::INVALID_JSON]);
    }
}
