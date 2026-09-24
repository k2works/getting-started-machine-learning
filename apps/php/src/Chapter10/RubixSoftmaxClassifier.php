<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter10;

use GettingStartedMl\Chapter10;
use Rubix\ML\Classifiers\SoftmaxClassifier;
use Rubix\ML\Datasets\Labeled;
use Rubix\ML\Datasets\Unlabeled;
use Rubix\ML\NeuralNet\Optimizers\Stochastic;

/**
 * Rubix ML の多クラスのロジスティック回帰を、同じ Classifier の口に合わせる。
 *
 * Rubix ML の LogisticRegression は 2 クラス専用で、アヤメの 3 品種を渡すと
 * 「Number of classes must be 2, 3 given.」で落ちる。多クラスを扱うのは
 * SoftmaxClassifier のほうで、これは自作した softmax + 交差エントロピーと同じものである。
 *
 * 既定は l2Penalty = 1e-4 のミニバッチ学習（batchSize 128）で、自作のバッチ勾配降下法とは
 * 学習の仕方そのものが違う。比べるときは名前ではなく設定をそろえる。
 */
final readonly class RubixSoftmaxClassifier implements Classifier
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
        // Rubix ML のモデルはシードを受け取らない。重みの初期値も標本の抽出も
        // PHP の大域の乱数（mt_rand）から引くので、揃えたければ処理系の側を種付けする。
        mt_srand($this->seed);

        $model = new SoftmaxClassifier(
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
