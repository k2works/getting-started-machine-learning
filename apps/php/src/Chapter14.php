<?php

declare(strict_types=1);

namespace GettingStartedMl;

use GettingStartedMl\Chapter09\Standardizer;
use GettingStartedMl\Chapter14\KMeansResult;
use InvalidArgumentException;
use Rubix\ML\Clusterers\KMeans;
use Rubix\ML\Clusterers\Seeders\Preset;
use Rubix\ML\Datasets\Unlabeled;

/**
 * 第 14 章: K-means によるクラスタリング。
 *
 * 点は数値の並び、点の集まりはその並びの並びで表す。中心が変わらなくなるまで
 * 「割り当て」と「中心の更新」を繰り返し、Rubix ML の KMeans と SSE で比べる。
 *
 * Rubix ML の KMeans には Seeders\Preset があり、初期中心そのものを渡せる
 * （Elixir 版の Scholar には無かった口）。ただし学習はミニバッチ K-means なので、
 * 同じ初期中心から始めても同じ中心には落ち着かない。
 *
 * 標準化は第 9 章の Standardizer を使い回す。
 */
final class Chapter14
{
    /** 更新の回数の既定の上限（scikit-learn の KMeans と同じ）。 */
    public const int DEFAULT_MAX_ITERATIONS = 300;

    /** 初期中心を試す回数の既定値（scikit-learn の KMeans の n_init と同じ）。 */
    public const int DEFAULT_N_INIT = 10;

    /** 区分を表す番号で、支出額ではない列。 */
    public const array NON_SPENDING = ['Channel', 'Region'];

    /** 初期中心の乱数のシード。 */
    public const int SEED = 0;

    /** 特徴を読むために選んだクラスタ数。 */
    public const int N_CLUSTERS = 5;

    /** エルボー法で試すクラスタ数の上限。 */
    public const int MAX_CLUSTERS = 10;

    // 14.7 各点を最も近い中心に割り当てる

    /**
     * 2 点間の距離の 2 乗。
     *
     * @param list<float> $a
     * @param list<float> $b
     */
    public static function squaredDistance(array $a, array $b): float
    {
        $sum = 0.0;

        foreach ($a as $i => $value) {
            $sum += ($value - $b[$i]) * ($value - $b[$i]);
        }

        return $sum;
    }

    /**
     * 各点を、最も近い中心のクラスタ番号に割り当てる。
     *
     * 距離が同じなら先に並ぶ中心を選ぶように、厳密な < で比べる。
     *
     * @param list<list<float>> $points
     * @param list<list<float>> $centers
     *
     * @return list<int>
     */
    public static function assignClusters(array $points, array $centers): array
    {
        if ($centers === []) {
            throw new InvalidArgumentException('中心が 1 つもありません');
        }

        return array_map(
            static function (array $point) use ($centers): int {
                $label = 0;
                $best = INF;

                foreach ($centers as $k => $center) {
                    $distance = self::squaredDistance($point, $center);

                    if ($distance < $best) {
                        $label = $k;
                        $best = $distance;
                    }
                }

                return $label;
            },
            $points,
        );
    }

    // 14.8 中心を更新する

    /**
     * クラスタごとに、割り当てられた点の平均を新しい中心にする。
     *
     * 点が 1 つも割り当てられなかったクラスタは、前の中心をそのまま残す。
     *
     * @param list<list<float>> $points
     * @param list<int>         $labels
     * @param list<list<float>> $previous
     *
     * @return list<list<float>>
     */
    public static function updateCenters(array $points, array $labels, array $previous): array
    {
        $members = [];

        foreach ($points as $i => $point) {
            $members[$labels[$i]][] = $point;
        }

        return array_map(
            static fn (int $k): array => array_key_exists($k, $members)
                ? self::meanPoint($members[$k])
                : $previous[$k],
            array_keys($previous),
        );
    }

    // 14.9 SSE を計算する

    /**
     * 各点と、所属するクラスタの中心との距離の 2 乗の合計（誤差平方和）。
     *
     * @param list<list<float>> $points
     * @param list<int>         $labels
     * @param list<list<float>> $centers
     */
    public static function sumOfSquaredErrors(array $points, array $labels, array $centers): float
    {
        $sum = 0.0;

        foreach ($points as $i => $point) {
            $sum += self::squaredDistance($point, $centers[$labels[$i]]);
        }

        return $sum;
    }

    // 14.10 中心が変わらなくなるまで繰り返す

    /**
     * 中心が変わらなくなるか、更新の回数が上限に達するまで、割り当てと中心の更新を繰り返す。
     *
     * @param list<list<float>> $points
     * @param list<list<float>> $initialCenters
     */
    public static function fit(
        array $points,
        array $initialCenters,
        int $maxIterations = self::DEFAULT_MAX_ITERATIONS,
    ): KMeansResult {
        $centers = $initialCenters;

        for ($i = 0; $i < $maxIterations; ++$i) {
            $next = self::updateCenters($points, self::assignClusters($points, $centers), $centers);

            if ($next === $centers) {
                break;
            }

            $centers = $next;
        }

        $labels = self::assignClusters($points, $centers);

        return new KMeansResult(
            $labels,
            $centers,
            self::sumOfSquaredErrors($points, $labels, $centers),
        );
    }

    // 14.11 初期中心をシードで選ぶ

    /**
     * シード付きの乱数で点を並べ替え、先頭から nClusters 個を初期中心にする。
     *
     * GettingStartedMl\Random は java.util.Random と同じ線形合同法なので、
     * Java 版・Scala 版・Clojure 版・Elixir 版と同じ点を選ぶ。
     *
     * @param list<list<float>> $points
     *
     * @return list<list<float>>
     */
    public static function chooseInitialCenters(array $points, int $nClusters, int $seed): array
    {
        if ($nClusters < 1 || $nClusters > count($points)) {
            throw new InvalidArgumentException(
                sprintf('クラスタ数は 1 以上 %d 以下にしてください: %d', count($points), $nClusters),
            );
        }

        return array_map(
            static fn (int $index): array => $points[$index],
            array_slice(Random::shuffle(range(0, count($points) - 1), $seed), 0, $nClusters),
        );
    }

    // 14.12 局所解と複数回の試行

    /**
     * 初期中心の候補ごとにクラスタリングし、SSE が最小の結果を返す。
     *
     * @param list<list<float>>       $points
     * @param list<list<list<float>>> $initialCenterCandidates
     */
    public static function best(array $points, array $initialCenterCandidates): KMeansResult
    {
        if ($initialCenterCandidates === []) {
            throw new InvalidArgumentException('初期中心の候補が 1 つもありません');
        }

        $best = null;

        foreach ($initialCenterCandidates as $candidate) {
            $result = self::fit($points, $candidate);

            // 同じ SSE が並んだら先に見つけたほうを残すように、厳密な < で比べる。
            if ($best === null || $result->sse < $best->sse) {
                $best = $result;
            }
        }

        return $best;
    }

    /**
     * シードを 1 ずつずらして初期中心を nInit 通り選び、SSE が最小の結果を返す。
     *
     * @param list<list<float>> $points
     */
    public static function fitWithRestarts(
        array $points,
        int $nClusters,
        int $seed,
        int $nInit = self::DEFAULT_N_INIT,
    ): KMeansResult {
        return self::best($points, array_map(
            static fn (int $i): array => self::chooseInitialCenters($points, $nClusters, $seed + $i),
            range(0, $nInit - 1),
        ));
    }

    /**
     * クラスタ数ごとに、初期中心を nInit 通り試した最小の SSE を返す。
     *
     * @param list<list<float>> $points
     * @param list<int>         $clusterCounts
     *
     * @return list<array{int, float}>
     */
    public static function sseByClusterCount(
        array $points,
        array $clusterCounts,
        int $seed,
        int $nInit = self::DEFAULT_N_INIT,
    ): array {
        return array_map(
            static fn (int $n): array => [$n, self::fitWithRestarts($points, $n, $seed, $nInit)->sse],
            $clusterCounts,
        );
    }

    // 14.13 Rubix ML の KMeans と比べる

    /**
     * Rubix ML の KMeans に初期中心そのものを渡して学習し、中心を返す。
     *
     * Seeders\Preset が「あらかじめ決めた中心から選ぶ」種まき係なので、初期中心を
     * 指定できる。Elixir 版の Scholar には無かった口。
     *
     * @param list<list<float>> $points
     * @param list<list<float>> $initialCenters
     *
     * @return list<list<float>>
     */
    public static function rubixCentersFrom(array $points, array $initialCenters): array
    {
        $model = new KMeans(count($initialCenters), seeder: new Preset($initialCenters));
        $model->train(Unlabeled::quick($points));

        return array_map(
            static fn (array $center): array => array_map(self::toFloat(...), $center),
            $model->centroids(),
        );
    }

    /**
     * Rubix ML の KMeans を既定の k-means++ で学習し、中心を返す。
     *
     * Rubix ML のモデルはシードを受け取らず大域の mt_rand から引くので、
     * mt_srand で種を固定してから学習する（第 10 章と同じ）。
     *
     * @param list<list<float>> $points
     *
     * @return list<list<float>>
     */
    public static function rubixCenters(array $points, int $nClusters, int $seed): array
    {
        mt_srand($seed);

        $model = new KMeans($nClusters);
        $model->train(Unlabeled::quick($points));

        return array_map(
            static fn (array $center): array => array_map(self::toFloat(...), $center),
            $model->centroids(),
        );
    }

    /**
     * Rubix ML が学習した中心に自作の割り当てをして、自作と同じ定義の SSE を求める。
     *
     * @param list<list<float>> $points
     */
    public static function rubixSse(array $points, int $nClusters, int $seed): float
    {
        $centers = self::rubixCenters($points, $nClusters, $seed);

        return self::sumOfSquaredErrors($points, self::assignClusters($points, $centers), $centers);
    }

    // 14.14 実データでクラスタリングする

    /**
     * Channel と Region を除いた支出額の列を読み込む。欠損値があれば失敗する。
     *
     * @return array{columns: list<string>, x: list<array<string, float>>}
     */
    public static function spending(Table $table): array
    {
        $columns = array_values(array_filter(
            $table->columns,
            static fn (string $c): bool => !in_array($c, self::NON_SPENDING, true),
        ));

        return [
            'columns' => $columns,
            'x' => array_map(
                static function (array $row) use ($columns): array {
                    $features = [];

                    foreach ($columns as $column) {
                        $features[$column] = self::amount($row, $column);
                    }

                    return $features;
                },
                $table->rows,
            ),
        ];
    }

    /**
     * CSV を読み込んで支出額の列だけにする。
     *
     * @return array{columns: list<string>, x: list<array<string, float>>}
     */
    public static function loadSpending(string $path): array
    {
        return self::spending(Chapter02::loadTable($path));
    }

    /**
     * 第 9 章の標準化（件数で割る標準偏差）で列ごとにそろえ、1 件を 1 つの点にする。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $columns
     *
     * @return list<list<float>>
     */
    public static function standardize(array $x, array $columns): array
    {
        return Standardizer::toRows(Standardizer::fit($x, $columns)->transformAll($x), $columns);
    }

    /**
     * クラスタごとの件数と、元の単位での列ごとの平均を、件数の多い順に並べる。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $columns
     * @param list<int>                  $labels
     *
     * @return list<array{cluster: int, count: int, means: array<string, float>}>
     */
    public static function summarizeClusters(array $x, array $columns, array $labels): array
    {
        $members = [];

        foreach ($x as $i => $features) {
            $members[$labels[$i]][] = $features;
        }

        $summaries = [];

        foreach ($members as $cluster => $rows) {
            $means = [];

            foreach ($columns as $column) {
                $means[$column] = array_sum(array_column($rows, $column)) / count($rows);
            }

            $summaries[] = ['cluster' => $cluster, 'count' => count($rows), 'means' => $means];
        }

        // 件数の降順、同数ならクラスタ番号の昇順。
        usort(
            $summaries,
            static fn (array $a, array $b): int => [$b['count'], $a['cluster']] <=> [$a['count'], $b['cluster']],
        );

        return $summaries;
    }

    /** 卸売業者の顧客を支出額で K-means にかけ、エルボー法の SSE とクラスタごとの特徴を表示する。 */
    public static function run(?string $path = null): string
    {
        ['columns' => $columns, 'x' => $x] =
            self::loadSpending($path ?? Dataset::path('Wholesale.csv'));
        $points = self::standardize($x, $columns);

        $lines = [
            sprintf('データ件数: %d（支出額 %d 列）', count($x), count($columns)),
            sprintf('クラスタ数ごとの SSE（初期中心 %d 通りの最小値）:', self::DEFAULT_N_INIT),
            "クラスタ数\t自作\tRubix ML（k-means++）",
        ];

        foreach (self::sseByClusterCount($points, range(1, self::MAX_CLUSTERS), self::SEED) as [$n, $sse]) {
            $lines[] = sprintf("%d\t%.2f\t%.2f", $n, $sse, self::rubixSse($points, $n, self::SEED));
        }

        $lines[] = '';
        $lines[] = sprintf('クラスタ数 %d のクラスタごとの件数と平均支出額:', self::N_CLUSTERS);
        $lines[] = implode("\t", ['クラスタ', '件数', ...$columns]);

        $labels = self::fitWithRestarts($points, self::N_CLUSTERS, self::SEED)->labels;

        foreach (self::summarizeClusters($x, $columns, $labels) as $summary) {
            $lines[] = implode("\t", [
                (string) $summary['cluster'],
                (string) $summary['count'],
                ...array_map(
                    // sprintf('%.0f') はちょうど 0.5 のとき偶数側に丸めるので、
                    // ほかの言語版（0.5 を切り上げる round）と食い違う。round を通してから整数にする。
                    static fn (string $c): string => (string) (int) round($summary['means'][$c]),
                    $columns,
                ),
            ]);
        }

        return implode("\n", $lines) . "\n";
    }

    /**
     * 点の集まりの、列ごとの平均。
     *
     * @param list<list<float>> $points
     *
     * @return list<float>
     */
    private static function meanPoint(array $points): array
    {
        return array_map(
            static fn (int $i): float => array_sum(array_column($points, $i)) / count($points),
            range(0, count($points[0]) - 1),
        );
    }

    /** @param array<string, string> $row */
    private static function amount(array $row, string $column): float
    {
        $value = Chapter02::number($row, $column);

        if ($value === null) {
            throw new InvalidArgumentException("空欄があります: {$column}");
        }

        return $value;
    }

    /** Rubix ML の中心は mixed の配列なので、数値であることを確かめてから float にする。 */
    private static function toFloat(mixed $value): float
    {
        if (!is_int($value) && !is_float($value)) {
            throw new InvalidArgumentException('中心の値が数値ではありません');
        }

        return (float) $value;
    }
}
