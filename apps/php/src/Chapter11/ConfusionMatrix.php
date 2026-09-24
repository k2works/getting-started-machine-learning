<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter11;

/**
 * 混同行列の 4 つのます。
 *
 * PHP では `fn` を無名関数の記法に使うが、**プロパティ名や名前付き引数には使える**。
 * Elixir 版が `cm.fn` と書けずパターンマッチで名前を付け替えたのとは違い、
 * ここは 4 つの名前をそのまま並べられる。
 */
final readonly class ConfusionMatrix
{
    public function __construct(
        /** 正例と予測して、本当に正例だった件数。 */
        public int $tp,
        /** 正例と予測して、本当は負例だった件数。 */
        public int $fp,
        /** 負例と予測して、本当は正例だった件数。 */
        public int $fn,
        /** 負例と予測して、本当に負例だった件数。 */
        public int $tn,
    ) {
    }

    /** 分母が 0 なら 0 を返す割り算。NaN にしない。 */
    public static function ratio(float $numerator, float $denominator): float
    {
        return $denominator === 0.0 ? 0.0 : $numerator / $denominator;
    }

    /** 適合率。正例と予測したうち、本当に正例だった割合。 */
    public function precision(): float
    {
        return self::ratio($this->tp, $this->tp + $this->fp);
    }

    /** 再現率。本当の正例のうち、正例と予測できた割合。 */
    public function recall(): float
    {
        return self::ratio($this->tp, $this->tp + $this->fn);
    }

    /** F 値。適合率と再現率の調和平均。 */
    public function f1Score(): float
    {
        $precision = $this->precision();
        $recall = $this->recall();

        return self::ratio(2.0 * $precision * $recall, $precision + $recall);
    }
}
