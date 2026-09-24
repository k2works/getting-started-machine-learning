<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

use GettingStartedMl\Chapter08;
use GettingStartedMl\FittedPipeline;

/**
 * 第 8 章の学習済みパイプラインを、生存予測のモデルの約束に合わせるアダプター。
 *
 * パイプラインは CSV から読んだ「セルの文字列の行」を受け取るので、`Passenger` を
 * その形に戻す。分からない値は空欄にすると、学習のときに求めた中央値・最頻値で補完される。
 */
final readonly class PipelineSurvivalModel implements SurvivalModel
{
    public function __construct(private FittedPipeline $pipeline)
    {
    }

    public function predict(Passenger $passenger): bool
    {
        $predictions = Chapter08::predict(
            $this->pipeline,
            Chapter08::featuresTable([self::row($passenger)]),
        );

        return $predictions[0] === Chapter08::SURVIVED;
    }

    /** @return array<string, string> */
    private static function row(Passenger $passenger): array
    {
        return [
            'Pclass' => (string) $passenger->pclass,
            'Sex' => $passenger->sex,
            'Age' => self::cell($passenger->age),
            'SibSp' => (string) $passenger->sibSp,
            'Parch' => (string) $passenger->parch,
            'Fare' => (string) $passenger->fare,
            'Embarked' => $passenger->embarked ?? '',
        ];
    }

    private static function cell(?float $value): string
    {
        return $value === null ? '' : (string) $value;
    }
}
