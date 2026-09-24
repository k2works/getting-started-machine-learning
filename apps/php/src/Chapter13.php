<?php

declare(strict_types=1);

namespace GettingStartedMl;

use GettingStartedMl\Chapter09\Standardizer;
use GettingStartedMl\Chapter13\Pca;
use InvalidArgumentException;
use MathPHP\LinearAlgebra\Eigenvalue;
use MathPHP\LinearAlgebra\MatrixFactory;
use Rubix\ML\Datasets\Unlabeled;
use Rubix\ML\Transformers\PrincipalComponentAnalysis;

/**
 * 第 13 章: 主成分分析による次元削減。
 *
 * 中心化・分散共分散行列・固有値分解・並べ替え・符号・射影・寄与率を自作し、Rubix ML の
 * PrincipalComponentAnalysis と突き合わせる。固有値だけは MathPHP の Jacobi 法とも比べる。
 *
 * 標準化は第 9 章の Standardizer をそのまま呼ぶ。同じ標準化が 2 つあると、
 * 片方だけ直したときに気づけないため。
 */
final class Chapter13
{
    /** カテゴリ値の列。 */
    public const string CATEGORY = 'CRIME';

    /** 何割のばらつきを説明できれば十分とみなすか。 */
    public const float THRESHOLD = 0.8;

    /** 主成分ごとに表示する列の数。 */
    public const int TOP_K = 3;

    /** 意味を読む主成分の数。 */
    public const int COMPONENTS_TO_EXPLAIN = 2;

    /** Jacobi 法の回転を何巡まで繰り返すか。 */
    private const int MAX_SWEEPS = 100;

    /** 非対角成分の 2 乗和がこれ未満になったら対角化できたとみなす。 */
    private const float OFF_DIAGONAL_TOLERANCE = 1.0e-30;

    // 13.5 分散共分散行列を求める

    /**
     * 列ごとの平均を返す。
     *
     * @param list<list<float>> $m
     *
     * @return list<float>
     */
    public static function columnMeans(array $m): array
    {
        self::checkNotEmpty($m);

        return array_map(
            static fn (array $column): float => array_sum($column) / count($column),
            self::transpose($m),
        );
    }

    /**
     * 各列から平均を引く（中心化）。
     *
     * @param list<list<float>> $m
     * @param list<float>       $means
     *
     * @return list<list<float>>
     */
    public static function center(array $m, array $means): array
    {
        return array_map(
            static fn (array $row): array => array_map(
                static fn (float $value, float $mean): float => $value - $mean,
                $row,
                $means,
            ),
            $m,
        );
    }

    /**
     * 列ごとの分散と、2 列ずつの共分散を並べた行列。件数から 1 を引いた数で割る。
     *
     * Rubix ML の Tensor は件数そのもので割るが、寄与率は割る数で変わらないので
     * 突き合わせには影響しない。ほかの言語版と分散の値をそろえるために n−1 にする。
     *
     * @param list<list<float>> $m
     *
     * @return list<list<float>>
     */
    public static function covarianceMatrix(array $m): array
    {
        if (count($m) < 2) {
            throw new InvalidArgumentException(
                sprintf('主成分分析には 2 件以上のデータが必要です（%d 件）', count($m)),
            );
        }

        $centered = self::transpose(self::center($m, self::columnMeans($m)));
        $n = count($m);

        return array_map(
            static fn (array $left): array => array_map(
                static fn (array $right): float => self::dot($left, $right) / ($n - 1),
                $centered,
            ),
            $centered,
        );
    }

    // 13.6 固有値分解を自作する

    /**
     * 対称行列を Jacobi 法で固有値分解し、固有値と固有ベクトルを大きい順に返す。
     *
     * 上三角の非対角成分を順に 2 次元の回転で 0 にしていき、全体が対角行列に近づくまで
     * 何巡もする。回転行列を掛け合わせたものが固有ベクトルになる。
     *
     * MathPHP は固有値（Eigenvalue::jacobiMethod）を持っているが、固有ベクトル
     * （Eigenvector::eigenvectors）は掃き出し法で零空間を探す実装で、実データの
     * 15×15 では「これは固有値ではない」と失敗する。固有値と固有ベクトルは
     * Jacobi 法なら同時に求まるので、回転をここで回す。
     *
     * @param list<list<float>> $m
     *
     * @return array{values: list<float>, vectors: list<list<float>>}
     */
    public static function eigenDecomposition(array $m): array
    {
        self::checkSymmetric($m);

        $a = $m;
        $size = count($a);
        $v = array_map(
            static fn (int $i): array => array_map(
                static fn (int $j): float => $i === $j ? 1.0 : 0.0,
                range(0, $size - 1),
            ),
            range(0, $size - 1),
        );

        for ($sweep = 0; $sweep < self::MAX_SWEEPS && self::offDiagonal($a) > self::OFF_DIAGONAL_TOLERANCE; ++$sweep) {
            for ($p = 0; $p < $size - 1; ++$p) {
                for ($q = $p + 1; $q < $size; ++$q) {
                    if ($a[$p][$q] === 0.0) {
                        continue;
                    }

                    [$a, $v] = self::rotate($a, $v, $p, $q);
                }
            }
        }

        return self::sortByValueDesc(
            array_map(static fn (int $i): float => $a[$i][$i], range(0, $size - 1)),
            // 固有ベクトルは列に並ぶので、転置して 1 行に 1 つずつにする。
            self::transpose($v),
        );
    }

    /**
     * MathPHP の Jacobi 法で固有値だけを求める。自作の固有値と突き合わせるために使う。
     *
     * @param list<list<float>> $m
     *
     * @return list<float>
     */
    public static function mathPhpEigenvalues(array $m): array
    {
        self::checkSymmetric($m);

        /** @var list<float> $values */
        $values = Eigenvalue::jacobiMethod(MatrixFactory::createNumeric($m));

        return $values;
    }

    // 13.7 主成分を求める

    /**
     * 固有ベクトルは符号が逆でも同じ向きを表すので、絶対値が最大の要素が正になるようにそろえる。
     *
     * @param list<list<float>> $components
     *
     * @return list<list<float>>
     */
    public static function normalizeSigns(array $components): array
    {
        return array_map(self::normalizeSign(...), $components);
    }

    /**
     * 分散共分散行列を固有値分解し、寄与率の大きい順に nComponents 個の主成分を求める。
     *
     * @param list<list<float>> $m
     */
    public static function fit(array $m, int $nComponents): Pca
    {
        ['values' => $values, 'vectors' => $vectors] =
            self::eigenDecomposition(self::covarianceMatrix($m));

        if ($nComponents < 1 || $nComponents > count($values)) {
            throw new InvalidArgumentException(
                sprintf('主成分の数は 1 以上 %d 以下にしてください: %d', count($values), $nComponents),
            );
        }

        $total = array_sum($values);
        $variances = array_slice($values, 0, $nComponents);

        return new Pca(
            self::columnMeans($m),
            self::normalizeSigns(array_slice($vectors, 0, $nComponents)),
            $variances,
            array_map(static fn (float $v): float => $v / $total, $variances),
        );
    }

    // 13.8 データを主成分の向きに射影する

    /**
     * 平均を引いてから、データを主成分の向きに射影する。
     *
     * @param list<list<float>> $m
     *
     * @return list<list<float>>
     */
    public static function transform(Pca $model, array $m): array
    {
        return array_map(
            static fn (array $row): array => array_map(
                static fn (array $component): float => self::dot($row, $component),
                $model->components,
            ),
            self::center($m, $model->mean),
        );
    }

    // 13.9 必要な主成分の数と、影響が大きい列

    /**
     * 累積寄与率がしきい値に届くまでの主成分の数。届かなければすべての主成分の数。
     *
     * @param list<float> $ratios
     */
    public static function componentsNeeded(array $ratios, float $threshold): int
    {
        $cumulative = 0.0;

        foreach ($ratios as $index => $ratio) {
            $cumulative += $ratio;

            if ($cumulative >= $threshold) {
                return $index + 1;
            }
        }

        return count($ratios);
    }

    /**
     * 主成分の係数の絶対値が大きい順に、列名と係数を k 個返す。
     *
     * usort は安定なので（PHP 8.0 から）、絶対値が同じなら元の列の順が残る。
     *
     * @param list<float>  $component
     * @param list<string> $columns
     *
     * @return list<array{string, float}>
     */
    public static function topLoadings(array $component, array $columns, int $k): array
    {
        $loadings = array_map(
            static fn (string $column, float $value): array => [$column, $value],
            $columns,
            $component,
        );
        usort($loadings, static fn (array $a, array $b): int => abs($b[1]) <=> abs($a[1]));

        return array_slice($loadings, 0, $k);
    }

    // 13.10 Rubix ML の PCA と突き合わせる

    /**
     * Rubix ML の PCA が学習した主成分を取り出す。
     *
     * PrincipalComponentAnalysis は固有ベクトルを protected で持っていて、読み出す口が無い。
     * transform() が「平均を引いてから固有ベクトルを掛ける」ことだけは分かっているので、
     * 「平均 + 単位ベクトル」を変換させて、固有ベクトルの行を 1 本ずつ取り出す。
     *
     * @param list<list<float>> $m
     *
     * @return list<list<float>>
     */
    public static function rubixComponents(array $m, int $nComponents): array
    {
        $dimensions = count(self::columnMeans($m));
        $pca = new PrincipalComponentAnalysis($nComponents);
        $pca->fit(Unlabeled::quick($m));

        $mean = self::columnMeans($m);
        $probes = array_map(
            static fn (int $i): array => array_map(
                static fn (float $value, int $j): float => $i === $j ? $value + 1.0 : $value,
                $mean,
                array_keys($mean),
            ),
            range(0, $dimensions - 1),
        );

        $pca->transform($probes);

        // 行が元の列、列が主成分になっているので、転置して 1 行に 1 つの主成分にする。
        return self::normalizeSigns(self::transpose(array_map(
            static fn (array $row): array => array_map(self::toFloat(...), $row),
            $probes,
        )));
    }

    /**
     * Rubix ML の PCA から、主成分ごとの寄与率を復元する。
     *
     * lossiness() は「捨てた主成分の分散の割合」なので、1 から引けば累積寄与率になる。
     * 主成分の数を 1 つずつ増やして学習し、累積寄与率の差を取れば主成分ごとの寄与率が出る。
     * ライブラリに explained_variance_ratio が無くても、公開されている 1 つの数から
     * ここまでは復元できる。
     *
     * @param list<list<float>> $m
     *
     * @return list<float>
     */
    public static function rubixExplainedVarianceRatio(array $m): array
    {
        $dimensions = count(self::columnMeans($m));
        $ratios = [];
        $previous = 0.0;

        foreach (range(1, $dimensions) as $nComponents) {
            $pca = new PrincipalComponentAnalysis($nComponents);
            $pca->fit(Unlabeled::quick($m));
            $cumulative = 1.0 - (float) $pca->lossiness();

            $ratios[] = $cumulative - $previous;
            $previous = $cumulative;
        }

        return $ratios;
    }

    /**
     * 自作と Rubix ML の、寄与率と主成分の差の絶対値の最大。
     *
     * @param list<list<float>> $m
     *
     * @return array{ratio: float, component: float}
     */
    public static function rubixGaps(array $m, int $nComponents): array
    {
        $mine = self::fit($m, $nComponents);

        return [
            'ratio' => self::maxGap(
                [$mine->explainedVarianceRatio],
                [array_slice(self::rubixExplainedVarianceRatio($m), 0, $nComponents)],
            ),
            'component' => self::maxGap($mine->components, self::rubixComponents($m, $nComponents)),
        ];
    }

    // 13.11 Boston を前処理する

    /**
     * CRIME をダミー変数にし、欠損値を列の平均値で補完してから、すべての列を標準化する。
     *
     * @return array{columns: list<string>, x: list<array<string, float>>}
     */
    public static function standardizeTable(Table $table): array
    {
        $crimes = array_map(
            static fn (array $row): string => Chapter02::text($row, self::CATEGORY),
            $table->rows,
        );
        $encoded = Chapter09::encode($table, self::CATEGORY, Chapter09::categories($crimes));
        $means = Chapter02::columnMeans($encoded->rows, $encoded->columns);
        $filled = Chapter02::fillMissing($encoded->rows, $encoded->columns, $means);

        return [
            'columns' => $encoded->columns,
            'x' => Standardizer::fit($filled, $encoded->columns)->transformAll($filled),
        ];
    }

    /**
     * CSV を読み込んで前処理する。
     *
     * @return array{columns: list<string>, x: list<array<string, float>>}
     */
    public static function loadBoston(string $path): array
    {
        return self::standardizeTable(Chapter02::loadTable($path));
    }

    /**
     * 特徴量の並びを、1 件を 1 行とする行列にする。列の順は必ずリストで持ち回る。
     *
     * @param list<array<string, float>> $x
     * @param list<string>               $columns
     *
     * @return list<list<float>>
     */
    public static function toMatrix(array $x, array $columns): array
    {
        return Standardizer::toRows($x, $columns);
    }

    // 13.12 実データで要約する

    /** ボストンの住宅価格の 15 列を主成分分析し、寄与率と主成分の意味を表示する。 */
    public static function run(?string $path = null): string
    {
        ['columns' => $columns, 'x' => $x] = self::loadBoston($path ?? Dataset::path('Boston.csv'));
        $m = self::toMatrix($x, $columns);
        $model = self::fit($m, count($columns));
        $needed = self::componentsNeeded($model->explainedVarianceRatio, self::THRESHOLD);
        $cumulative = array_sum(array_slice($model->explainedVarianceRatio, 0, $needed));

        $lines = [
            sprintf('データ件数: %d, 列数: %d', count($m), count($columns)),
            '寄与率: ' . self::formatRatios($model->explainedVarianceRatio, $needed),
            sprintf(
                '累積寄与率が %s に届く主成分の数: %d（累積寄与率 %.4f）',
                self::THRESHOLD,
                $needed,
                $cumulative,
            ),
        ];

        foreach (range(0, self::COMPONENTS_TO_EXPLAIN - 1) as $index) {
            $loadings = self::topLoadings($model->components[$index], $columns, self::TOP_K);
            $lines[] = sprintf('第 %d 主成分で影響の大きい列: %s', $index + 1, self::formatLoadings($loadings));
        }

        $gaps = self::rubixGaps($m, count($columns));
        $lines[] = sprintf(
            'Rubix ML の PCA との差: 寄与率 %.2e, 主成分 %.2e',
            $gaps['ratio'],
            $gaps['component'],
        );

        return implode("\n", $lines) . "\n";
    }

    /** @param list<float> $ratios */
    private static function formatRatios(array $ratios, int $count): string
    {
        return implode(', ', array_map(
            static fn (int $index, float $ratio): string => sprintf('PC%d %.4f', $index + 1, $ratio),
            range(0, $count - 1),
            array_slice($ratios, 0, $count),
        ));
    }

    /** @param list<array{string, float}> $loadings */
    private static function formatLoadings(array $loadings): string
    {
        return implode(', ', array_map(
            static fn (array $loading): string => sprintf('%s %.3f', $loading[0], $loading[1]),
            $loadings,
        ));
    }

    /**
     * @param list<float> $row
     *
     * @return list<float>
     */
    private static function normalizeSign(array $row): array
    {
        $largest = 0.0;

        foreach ($row as $value) {
            // 絶対値が同じなら前の要素を残すように、厳密な > で比べる。
            if (abs($value) > abs($largest)) {
                $largest = $value;
            }
        }

        return $largest < 0.0
            ? array_map(static fn (float $v): float => -$v, $row)
            : $row;
    }

    /** @param list<list<float>> $m */
    private static function checkSymmetric(array $m): void
    {
        $size = count($m);
        self::checkNotEmpty($m);

        foreach ($m as $row) {
            if (count($row) !== $size) {
                throw new InvalidArgumentException(sprintf('正方行列ではありません: %d 行', $size));
            }
        }

        foreach ($m as $i => $row) {
            foreach ($row as $j => $value) {
                if ($value !== $m[$j][$i]) {
                    throw new InvalidArgumentException('固有値分解できません（対称行列ではありません）');
                }
            }
        }
    }

    /** @param list<list<float>> $m */
    private static function checkNotEmpty(array $m): void
    {
        if ($m === []) {
            throw new InvalidArgumentException('データが 1 件もありません');
        }
    }

    /**
     * (p, q) の非対角成分が 0 になる回転を、行列と回転の積の両方に施す。
     *
     * @param list<list<float>> $a
     * @param list<list<float>> $v
     *
     * @return array{list<list<float>>, list<list<float>>}
     */
    private static function rotate(array $a, array $v, int $p, int $q): array
    {
        $theta = ($a[$q][$q] - $a[$p][$p]) / (2.0 * $a[$p][$q]);
        $sign = $theta >= 0.0 ? 1.0 : -1.0;
        // tan の小さいほうの根を選ぶと、回転が小さくなって桁落ちしにくい。
        $tangent = $sign / (abs($theta) + sqrt($theta * $theta + 1.0));
        $cosine = 1.0 / sqrt($tangent * $tangent + 1.0);
        $sine = $tangent * $cosine;

        foreach (array_keys($a) as $k) {
            [$a[$k][$p], $a[$k][$q]] = [
                $cosine * $a[$k][$p] - $sine * $a[$k][$q],
                $sine * $a[$k][$p] + $cosine * $a[$k][$q],
            ];
            [$v[$k][$p], $v[$k][$q]] = [
                $cosine * $v[$k][$p] - $sine * $v[$k][$q],
                $sine * $v[$k][$p] + $cosine * $v[$k][$q],
            ];
        }

        foreach (array_keys($a) as $k) {
            [$a[$p][$k], $a[$q][$k]] = [
                $cosine * $a[$p][$k] - $sine * $a[$q][$k],
                $sine * $a[$p][$k] + $cosine * $a[$q][$k],
            ];
        }

        // 添字を書き換えたので、PHPStan に list であることを示すために並べ直す。
        return [array_map(array_values(...), $a), array_map(array_values(...), $v)];
    }

    /**
     * 非対角成分の 2 乗和。0 に近づくほど対角化できている。
     *
     * @param list<list<float>> $a
     */
    private static function offDiagonal(array $a): float
    {
        $sum = 0.0;

        foreach ($a as $i => $row) {
            foreach ($row as $j => $value) {
                $sum += $i === $j ? 0.0 : $value * $value;
            }
        }

        return $sum;
    }

    /**
     * 固有値の大きい順に、固有値と固有ベクトルを並べ替える。
     *
     * @param list<float>       $values
     * @param list<list<float>> $vectors
     *
     * @return array{values: list<float>, vectors: list<list<float>>}
     */
    private static function sortByValueDesc(array $values, array $vectors): array
    {
        $order = array_keys($values);
        usort($order, static fn (int $a, int $b): int => $values[$b] <=> $values[$a]);

        return [
            'values' => array_map(static fn (int $i): float => $values[$i], $order),
            'vectors' => array_map(static fn (int $i): array => $vectors[$i], $order),
        ];
    }

    /**
     * 行と列を入れ替える。
     *
     * @param list<list<float>> $m
     *
     * @return list<list<float>>
     */
    private static function transpose(array $m): array
    {
        self::checkNotEmpty($m);

        return array_map(
            static fn (int $j): array => array_column($m, $j),
            range(0, count($m[0]) - 1),
        );
    }

    /**
     * @param list<float> $a
     * @param list<float> $b
     */
    private static function dot(array $a, array $b): float
    {
        $sum = 0.0;

        foreach ($a as $i => $value) {
            $sum += $value * $b[$i];
        }

        return $sum;
    }

    /**
     * @param list<list<float>> $left
     * @param list<list<float>> $right
     */
    private static function maxGap(array $left, array $right): float
    {
        $gap = 0.0;

        foreach ($left as $i => $row) {
            foreach ($row as $j => $value) {
                $gap = max($gap, abs($value - $right[$i][$j]));
            }
        }

        return $gap;
    }

    /** Rubix ML の標本は mixed の配列なので、数値であることを確かめてから float にする。 */
    private static function toFloat(mixed $value): float
    {
        if (!is_int($value) && !is_float($value)) {
            throw new InvalidArgumentException('変換した値が数値ではありません');
        }

        return (float) $value;
    }
}
