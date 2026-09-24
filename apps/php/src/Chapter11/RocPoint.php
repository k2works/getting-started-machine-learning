<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter11;

/**
 * ROC 曲線の 1 点。
 *
 * 曲線の始まりは「どれも正例と予測しない」点で、そこには閾値が無い。
 * PHP に無限大の定数（`INF`）はあるが、「閾値が無い」ことは `null` のほうが素直に表せる。
 */
final readonly class RocPoint
{
    public function __construct(
        /** この値以上のスコアを正例と予測したときの点。始点は null。 */
        public ?float $threshold,
        /** 偽陽性率。負例のうち、誤って正例と予測した割合。 */
        public float $falsePositiveRate,
        /** 真陽性率（再現率）。正例のうち、正例と予測できた割合。 */
        public float $truePositiveRate,
    ) {
    }
}
