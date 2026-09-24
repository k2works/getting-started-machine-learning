<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter10;

use GettingStartedMl\Chapter10;
use InvalidArgumentException;

/**
 * バッチ勾配降下法で学習するロジスティック回帰。
 *
 * 重みは weights[特徴量の番号][品種の番号] で持ち、ループの順（特徴量が外、品種が内）を
 * Java 版・Clojure 版・Elixir 版にそろえる。浮動小数点の足し算は順によって結果が変わるので、
 * 数値をほかの言語版と一致させるには順まで合わせる。
 */
final readonly class LogisticRegression implements Predictor
{
    /** 学習率の既定値。 */
    public const float DEFAULT_LEARNING_RATE = 1.0;

    /** 繰り返す回数の既定値。Python 版・Java 版と同じ。 */
    public const int DEFAULT_EPOCHS = 5000;

    /**
     * @param list<string>      $columns
     * @param list<string>      $classes
     * @param list<list<float>> $weights
     * @param list<float>       $bias
     * @param list<float>       $losses
     */
    public function __construct(
        public array $columns,
        public array $classes,
        public array $weights,
        public array $bias,
        public array $losses,
    ) {
    }

    /**
     * 重みと切片を学習する。品種は名前の順に並べる。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $t
     * @param list<string>               $columns
     */
    public static function fit(
        array $x,
        array $t,
        array $columns,
        float $learningRate = self::DEFAULT_LEARNING_RATE,
        int $epochs = self::DEFAULT_EPOCHS,
    ): self {
        if ($x === []) {
            throw new InvalidArgumentException('特徴量が 1 件もありません');
        }

        $classes = Chapter10::classesOf($t);
        $targets = array_map(
            static fn (string $label): int => (int) array_search($label, $classes, true),
            $t,
        );
        $rows = Chapter10::toRows($x, $columns);

        $weights = array_fill(0, count($columns), array_fill(0, count($classes), 0.0));
        $bias = array_fill(0, count($classes), 0.0);
        $losses = [];
        $n = count($rows);

        for ($epoch = 0; $epoch < $epochs; ++$epoch) {
            $probabilities = array_map(
                static fn (array $row): array => Chapter10::softmax(self::scores($row, $weights, $bias)),
                $rows,
            );
            $losses[] = Chapter10::crossEntropy($probabilities, $targets);

            // 誤差は「確率 − 正解」。正解の品種だけ 1 を引く。
            $errors = [];

            foreach ($probabilities as $i => $probability) {
                $probability[$targets[$i]] -= 1.0;
                // 添字を書き換えたあとは list として扱えないので、包み直す。
                $errors[] = array_values($probability);
            }

            $weights = self::updateWeights($weights, $rows, $errors, $learningRate, $n);
            $bias = self::updateBias($bias, $errors, $learningRate, $n);
        }

        return new self($columns, $classes, $weights, $bias, $losses);
    }

    /**
     * スコアが最大の品種を予測する。同じ値なら先に現れたほうを選ぶ。
     *
     * @param list<array<string, float>> $x
     *
     * @return list<string>
     */
    public function predict(array $x): array
    {
        return array_map(
            function (array $row): string {
                $scores = self::scores($row, $this->weights, $this->bias);
                $best = 0;

                foreach ($scores as $i => $score) {
                    if ($score > $scores[$best]) {
                        $best = $i;
                    }
                }

                return $this->classes[$best];
            },
            Chapter10::toRows($x, $this->columns),
        );
    }

    /**
     * 1 行のスコア（品種ごと）。特徴量を外、品種を内にして足し込む。
     *
     * @param list<float>       $row
     * @param list<list<float>> $weights
     * @param list<float>       $bias
     *
     * @return list<float>
     */
    private static function scores(array $row, array $weights, array $bias): array
    {
        $scores = $bias;

        foreach ($row as $feature => $value) {
            foreach ($weights[$feature] as $class => $weight) {
                $scores[$class] += $value * $weight;
            }
        }

        return array_values($scores);
    }

    /**
     * @param list<list<float>> $weights
     * @param list<list<float>> $rows
     * @param list<list<float>> $errors
     *
     * @return list<list<float>>
     */
    private static function updateWeights(array $weights, array $rows, array $errors, float $rate, int $n): array
    {
        $gradient = array_map(
            static fn (array $forFeature): array => array_fill(0, count($forFeature), 0.0),
            $weights,
        );

        foreach ($rows as $i => $row) {
            foreach ($row as $feature => $value) {
                foreach ($errors[$i] as $class => $error) {
                    $gradient[$feature][$class] += $value * $error;
                }
            }
        }

        foreach ($weights as $feature => $forFeature) {
            foreach ($forFeature as $class => $weight) {
                $weights[$feature][$class] = $weight - $rate * $gradient[$feature][$class] / $n;
            }
        }

        return $weights;
    }

    /**
     * @param list<float>       $bias
     * @param list<list<float>> $errors
     *
     * @return list<float>
     */
    private static function updateBias(array $bias, array $errors, float $rate, int $n): array
    {
        $gradient = array_fill(0, count($bias), 0.0);

        foreach ($errors as $error) {
            foreach ($error as $class => $value) {
                $gradient[$class] += $value;
            }
        }

        foreach ($bias as $class => $value) {
            $bias[$class] = $value - $rate * $gradient[$class] / $n;
        }

        return $bias;
    }
}
