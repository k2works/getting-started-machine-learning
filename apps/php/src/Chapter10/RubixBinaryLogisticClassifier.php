<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter10;

use GettingStartedMl\Chapter10;
use Rubix\ML\Classifiers\LogisticRegression;
use Rubix\ML\Datasets\Labeled;
use Rubix\ML\Datasets\Unlabeled;
use Rubix\ML\NeuralNet\Optimizers\Stochastic;

/**
 * Rubix ML の LogisticRegression を、同じ Classifier の口に合わせる。
 *
 * 名前は「ロジスティック回帰」だが 2 クラス専用で、3 品種を渡すと
 * 「Number of classes must be 2, 3 given.」で落ちる。アヤメには使えないことを
 * テストで示すためだけに置いている。多クラスは RubixSoftmaxClassifier を使う。
 */
final readonly class RubixBinaryLogisticClassifier implements Classifier
{
    public function __construct(
        private float $l2Penalty = 1e-4,
        private int $epochs = 1000,
        private float $learningRate = 0.01,
        private int $seed = 0,
    ) {
    }

    /**
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     */
    public function fit(array $x, array $t, array $columns): Predictor
    {
        mt_srand($this->seed);

        $model = new LogisticRegression(
            batchSize: count($x),
            optimizer: new Stochastic($this->learningRate),
            l2Penalty: $this->l2Penalty,
            epochs: $this->epochs,
        );
        $model->train(Labeled::quick(Chapter10::toRows($x, $columns), $t));

        return new FunctionPredictor(
            static function (array $x) use ($model, $columns): array {
                /** @var list<string> $predictions */
                $predictions = $model->predict(Unlabeled::quick(Chapter10::toRows($x, $columns)));

                return $predictions;
            },
        );
    }
}
