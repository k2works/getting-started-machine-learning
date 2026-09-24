<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter10;

use GettingStartedMl\Chapter10;
use Rubix\ML\Classifiers\ClassificationTree;
use Rubix\ML\Classifiers\RandomForest as RubixRandomForest;
use Rubix\ML\Datasets\Labeled;
use Rubix\ML\Datasets\Unlabeled;

/**
 * Rubix ML のランダムフォレストを、同じ Classifier の口に合わせる。
 *
 * Elixir 版は Scholar にランダムフォレストが無く突き合わせられなかった。PHP 版では比べられる。
 * ただし maxFeatures は「節ごとに見る列の数」で、自作の「木ごとに使う列の数」とは意味が違う。
 * ratio も既定が 0.2（標本の 2 割）なので、ブートストラップに近づけるには 1.0 にする。
 */
final readonly class RubixForestClassifier implements Classifier
{
    public function __construct(
        private int $nEstimators,
        private int $maxFeatures,
        private ?int $maxDepth = null,
        private int $seed = 0,
    ) {
    }

    /**
     * 特徴量の重要度を、列名を鍵にして返す。
     *
     * Rubix ML は列の番号を鍵にした配列を返すので、列名に付け替える。
     * Elixir 版は Scholar にランダムフォレストが無く、ここを比べられなかった。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     *
     * @return array<string, float>
     */
    public function importances(array $x, array $t, array $columns): array
    {
        $model = $this->model($x, $t, $columns);
        $importances = [];

        foreach ($model->featureImportances() as $index => $value) {
            $importances[$columns[$index]] = (float) $value;
        }

        return $importances;
    }

    /**
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     */
    public function fit(array $x, array $t, array $columns): Predictor
    {
        $model = $this->model($x, $t, $columns);

        return new FunctionPredictor(
            static function (array $x) use ($model, $columns): array {
                /** @var list<string> $predictions */
                $predictions = $model->predict(Unlabeled::quick(Chapter10::toRows($x, $columns)));

                return $predictions;
            },
        );
    }

    /**
     * 学習した Rubix ML の森を返す。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     */
    private function model(array $x, array $t, array $columns): RubixRandomForest
    {
        // Rubix ML のモデルはシードを受け取らない。大域の乱数を種付けして揃える。
        mt_srand($this->seed);

        $model = new RubixRandomForest(
            base: new ClassificationTree(
                maxHeight: $this->maxDepth ?? PHP_INT_MAX,
                maxFeatures: $this->maxFeatures,
            ),
            estimators: $this->nEstimators,
            ratio: 1.0,
        );
        $model->train(Labeled::quick(Chapter10::toRows($x, $columns), $t));

        return $model;
    }
}
