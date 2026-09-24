<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter12;

/**
 * ボストンの住宅価格を訓練・検証・テストの 3 つに分け、特徴量を作り終えた結果。
 *
 * 第 9 章の BostonSplit は 2 つに分けた結果だったが、この章は検証データが加わり、
 * 特徴量の名前も持ち回るので別の型にする。
 */
final readonly class BostonData
{
    /**
     * @param list<array<string, float>> $xTrain
     * @param list<float>                $tTrain
     * @param list<array<string, float>> $xValid
     * @param list<float>                $tValid
     * @param list<array<string, float>> $xTest
     * @param list<float>                $tTest
     * @param list<string>               $featureNames 元の列・2 乗の項・交互作用の項の順
     * @param int                        $kept         外れ値を除いたあとの件数
     */
    public function __construct(
        public array $xTrain,
        public array $tTrain,
        public array $xValid,
        public array $tValid,
        public array $xTest,
        public array $tTest,
        public array $featureNames,
        public int $kept,
    ) {
    }
}
