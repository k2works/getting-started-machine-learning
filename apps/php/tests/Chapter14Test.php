<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Chapter14;
use GettingStartedMl\Dataset;
use GettingStartedMl\Table;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;

final class Chapter14Test extends TestCase
{
    /** 架空の 4 件。離れた 2 つの群れ。 */
    private const array TWO_GROUPS = [[0.0, 0.0], [0.5, 0.5], [10.0, 10.0], [10.5, 10.5]];

    /** 架空の 6 件。1 次元に 3 組の点が並ぶ。 */
    private const array THREE_PAIRS = [[0.0], [1.0], [10.0], [11.0], [20.0], [21.0]];

    /** 学習データの行は書かない。架空の 3 件で支出額の読み込みを確かめる。 */
    private function wholesaleLike(): Table
    {
        return new Table(
            ['Channel', 'Region', 'Fresh', 'Milk'],
            [
                ['Channel' => '1', 'Region' => '3', 'Fresh' => '100', 'Milk' => '200'],
                ['Channel' => '2', 'Region' => '3', 'Fresh' => '300', 'Milk' => '400'],
                ['Channel' => '1', 'Region' => '1', 'Fresh' => '500', 'Milk' => '600'],
            ],
        );
    }

    // 14.7 各点を最も近い中心に割り当てる

    #[TestDox('2 点間の距離の 2 乗を求める')]
    public function test距離の2乗(): void
    {
        $this->assertSame(25.0, Chapter14::squaredDistance([0.0, 0.0], [3.0, 4.0]));
    }

    #[TestDox('各点を最も近い中心に割り当てる')]
    public function test最も近い中心に割り当てる(): void
    {
        $this->assertSame(
            [0, 0, 1, 1],
            Chapter14::assignClusters(self::TWO_GROUPS, [[0.0, 0.0], [10.0, 10.0]]),
        );
    }

    #[TestDox('距離が同じなら先に並ぶ中心を選ぶ')]
    public function test同距離なら先の中心(): void
    {
        $this->assertSame([0], Chapter14::assignClusters([[0.0]], [[-1.0], [1.0]]));
    }

    #[TestDox('中心が 1 つも無ければ割り当てられない')]
    public function test中心が無い(): void
    {
        $this->expectException(InvalidArgumentException::class);

        Chapter14::assignClusters(self::TWO_GROUPS, []);
    }

    // 14.8 中心を更新する

    #[TestDox('クラスタごとに割り当てられた点の平均を新しい中心にする')]
    public function test中心を更新する(): void
    {
        $this->assertSame(
            [[0.25, 0.25], [10.25, 10.25]],
            Chapter14::updateCenters(self::TWO_GROUPS, [0, 0, 1, 1], [[0.0, 0.0], [10.0, 10.0]]),
        );
    }

    #[TestDox('点が割り当てられなかったクラスタは前の中心をそのまま残す')]
    public function test空のクラスタ(): void
    {
        $this->assertSame(
            [[5.25, 5.25], [99.0, 99.0]],
            Chapter14::updateCenters(self::TWO_GROUPS, [0, 0, 0, 0], [[0.0, 0.0], [99.0, 99.0]]),
        );
    }

    // 14.9 SSE を計算する

    #[TestDox('各点と所属する中心との距離の 2 乗を合計する')]
    public function testSSE(): void
    {
        $this->assertSame(
            1.0,
            Chapter14::sumOfSquaredErrors([[0.0], [2.0]], [0, 0], [[1.0]]) / 2.0,
        );
    }

    // 14.10 中心が変わらなくなるまで繰り返す

    #[TestDox('離れた 2 つの群れなら群れごとの平均に落ち着く')]
    public function test収束する(): void
    {
        $result = Chapter14::fit(self::TWO_GROUPS, [[0.0, 0.0], [10.0, 10.0]]);

        $this->assertSame([[0.25, 0.25], [10.25, 10.25]], $result->centers);
        $this->assertSame([0, 0, 1, 1], $result->labels);
        $this->assertEqualsWithDelta(0.5, $result->sse, 1.0e-12);
    }

    #[TestDox('繰り返しの上限が 0 なら初期中心のまま返す')]
    public function test上限0(): void
    {
        $this->assertSame(
            [[0.0, 0.0], [10.0, 10.0]],
            Chapter14::fit(self::TWO_GROUPS, [[0.0, 0.0], [10.0, 10.0]], 0)->centers,
        );
    }

    // 14.11 初期中心をシードで選ぶ

    #[TestDox('シードが同じなら同じ初期中心を選ぶ')]
    public function test初期中心はシードで決まる(): void
    {
        $this->assertSame(
            Chapter14::chooseInitialCenters(self::THREE_PAIRS, 3, 0),
            Chapter14::chooseInitialCenters(self::THREE_PAIRS, 3, 0),
        );
    }

    #[TestDox('初期中心はデータの点から重複なく選ぶ')]
    public function test初期中心は重複しない(): void
    {
        $centers = Chapter14::chooseInitialCenters(self::THREE_PAIRS, 3, 0);

        $this->assertCount(3, $centers);
        $this->assertCount(3, array_unique(array_map(strval(...), array_column($centers, 0))));

        foreach ($centers as $center) {
            $this->assertContains($center, self::THREE_PAIRS);
        }
    }

    #[TestDox('クラスタ数が点の数を超えると初期中心を選べない')]
    public function testクラスタ数が多すぎる(): void
    {
        $this->expectException(InvalidArgumentException::class);

        Chapter14::chooseInitialCenters(self::TWO_GROUPS, 5, 0);
    }

    // 14.12 局所解と複数回の試行

    #[TestDox('初期中心によっては局所解に陥る')]
    public function test局所解(): void
    {
        $this->assertEqualsWithDelta(
            101.0,
            Chapter14::fit(self::THREE_PAIRS, [[0.0], [1.0], [10.0]])->sse,
            1.0e-9,
        );
    }

    #[TestDox('複数の初期中心の候補のうち SSE が最小の結果を返す')]
    public function test最小のSSEを選ぶ(): void
    {
        $result = Chapter14::best(self::THREE_PAIRS, [
            [[0.0], [1.0], [10.0]],
            [[0.0], [10.0], [20.0]],
        ]);

        $this->assertEqualsWithDelta(1.5, $result->sse, 1.0e-9);
        $this->assertSame([[0.5], [10.5], [20.5]], $result->centers);
    }

    #[TestDox('候補が 1 つも無ければ選べない')]
    public function test候補が無い(): void
    {
        $this->expectException(InvalidArgumentException::class);

        Chapter14::best(self::THREE_PAIRS, []);
    }

    #[TestDox('クラスタ数ごとの SSE をクラスタ数の順に返す')]
    public function testクラスタ数ごとのSSE(): void
    {
        $sse = Chapter14::sseByClusterCount(self::THREE_PAIRS, [1, 2, 3], 0);

        $this->assertSame([1, 2, 3], array_column($sse, 0));
        $this->assertGreaterThan($sse[1][1], $sse[0][1]);
        $this->assertGreaterThan($sse[2][1], $sse[1][1]);
    }

    // 14.13 Rubix ML の KMeans と比べる

    #[TestDox('Rubix ML には初期中心を渡す口がある')]
    public function testRubixに初期中心を渡せる(): void
    {
        $centers = Chapter14::rubixCentersFrom(self::TWO_GROUPS, [[0.0, 0.0], [10.0, 10.0]]);

        $this->assertCount(2, $centers);
        // 同じ初期中心から始めても、ミニバッチの更新なので自作と同じ中心にはならない
        $this->assertNotSame(Chapter14::fit(self::TWO_GROUPS, [[0.0, 0.0], [10.0, 10.0]])->centers, $centers);
    }

    #[TestDox('Rubix ML の k-means++ はシードを固定すれば同じ中心を返す')]
    public function testRubixはシードで決まる(): void
    {
        $this->assertSame(
            Chapter14::rubixCenters(self::TWO_GROUPS, 2, 0),
            Chapter14::rubixCenters(self::TWO_GROUPS, 2, 0),
        );
    }

    #[TestDox('離れた 2 つの群れなら Rubix ML も同じ 2 つに分ける')]
    public function testRubixも同じ2つに分ける(): void
    {
        $this->assertSame(
            [0, 0, 1, 1],
            Chapter14::assignClusters(
                self::TWO_GROUPS,
                Chapter14::rubixCentersFrom(self::TWO_GROUPS, [[0.0, 0.0], [10.0, 10.0]]),
            ),
        );
    }

    #[TestDox('Rubix ML の中心にも自作と同じ定義の SSE を当てる')]
    public function testRubixのSSE(): void
    {
        $this->assertGreaterThan(
            Chapter14::fitWithRestarts(self::TWO_GROUPS, 2, 0)->sse,
            Chapter14::rubixSse(self::TWO_GROUPS, 2, 0),
        );
    }

    // 14.14 実データでクラスタリングする

    #[TestDox('Channel と Region を除いた支出額の列だけを読む')]
    public function test支出額の列(): void
    {
        ['columns' => $columns, 'x' => $x] = Chapter14::spending($this->wholesaleLike());

        $this->assertSame(['Fresh', 'Milk'], $columns);
        $this->assertSame([['Fresh' => 100.0, 'Milk' => 200.0]], array_slice($x, 0, 1));
    }

    #[TestDox('支出額に空欄があれば失敗する')]
    public function test空欄があれば失敗する(): void
    {
        $this->expectException(InvalidArgumentException::class);

        Chapter14::spending(new Table(['Fresh'], [['Fresh' => '']]));
    }

    #[TestDox('標準化すると列ごとの平均が 0・標準偏差が 1 になる')]
    public function test標準化(): void
    {
        ['columns' => $columns, 'x' => $x] = Chapter14::spending($this->wholesaleLike());
        $points = Chapter14::standardize($x, $columns);

        foreach ([0, 1] as $i) {
            $values = array_column($points, $i);
            $mean = array_sum($values) / count($values);

            $this->assertEqualsWithDelta(0.0, $mean, 1.0e-12);
        }
    }

    #[TestDox('クラスタごとの件数と元の単位の平均を件数の多い順に並べる')]
    public function testクラスタの要約(): void
    {
        $summaries = Chapter14::summarizeClusters(
            [['Fresh' => 10.0], ['Fresh' => 20.0], ['Fresh' => 60.0]],
            ['Fresh'],
            [1, 1, 0],
        );

        $this->assertSame([1, 0], array_column($summaries, 'cluster'));
        $this->assertSame([2, 1], array_column($summaries, 'count'));
        $this->assertSame(15.0, $summaries[0]['means']['Fresh']);
    }

    #[TestDox('件数が同じならクラスタ番号の昇順に並べる')]
    public function test同数ならクラスタ番号順(): void
    {
        $summaries = Chapter14::summarizeClusters(
            [['Fresh' => 10.0], ['Fresh' => 20.0]],
            ['Fresh'],
            [1, 0],
        );

        $this->assertSame([0, 1], array_column($summaries, 'cluster'));
    }

    #[Group('data')]
    #[TestDox('標準化したデータのクラスタ数 1 の SSE は件数と列数の積になる')]
    public function testクラスタ数1のSSE(): void
    {
        $this->skipWithoutData();

        // 件数で割る標準偏差で標準化したので、列ごとの分散は 1 になる
        ['columns' => $columns, 'x' => $x] = Chapter14::loadSpending(Dataset::path('Wholesale.csv'));
        $sse = Chapter14::sseByClusterCount(Chapter14::standardize($x, $columns), [1], 0);

        $this->assertEqualsWithDelta(440.0 * 6, $sse[0][1], 1.0e-6);
    }

    #[Group('data')]
    #[TestDox('クラスタ数を増やすほど SSE が小さくなる')]
    public function testSSEは単調に減る(): void
    {
        $this->skipWithoutData();

        ['columns' => $columns, 'x' => $x] = Chapter14::loadSpending(Dataset::path('Wholesale.csv'));
        $sse = array_column(
            Chapter14::sseByClusterCount(Chapter14::standardize($x, $columns), range(1, 10), 0),
            1,
        );

        foreach (array_slice($sse, 1) as $i => $value) {
            $this->assertLessThan($sse[$i], $value);
        }
    }

    #[Group('data')]
    #[TestDox('実行するとエルボー法の SSE とクラスタごとの特徴を表示する')]
    public function test実行すると表示する(): void
    {
        $this->skipWithoutData();

        $this->assertSame(
            implode("\n", [
                'データ件数: 440（支出額 6 列）',
                'クラスタ数ごとの SSE（初期中心 10 通りの最小値）:',
                "クラスタ数\t自作\tRubix ML（k-means++）",
                "1\t2640.00\t2664.04",
                "2\t1954.18\t2296.29",
                "3\t1614.52\t1705.52",
                "4\t1334.36\t1605.84",
                "5\t1085.27\t1373.32",
                "6\t947.20\t1296.48",
                "7\t888.22\t970.24",
                "8\t775.24\t795.53",
                "9\t690.81\t836.08",
                "10\t618.17\t694.81",
                '',
                'クラスタ数 5 のクラスタごとの件数と平均支出額:',
                "クラスタ\t件数\tFresh\tMilk\tGrocery\tFrozen\tDetergents_Paper\tDelicassen",
                "2\t265\t8909\t2967\t3804\t2248\t989\t962",
                "1\t96\t5509\t10556\t16478\t1420\t7199\t1659",
                "0\t65\t31117\t4260\t5374\t7225\t849\t2286",
                "3\t10\t15965\t34709\t48537\t3055\t24875\t2943",
                "4\t4\t52022\t31696\t18491\t29826\t2699\t19656",
                '',
            ]),
            Chapter14::run(),
        );
    }

    private function skipWithoutData(): void
    {
        if (!Dataset::exists('Wholesale.csv')) {
            $this->markTestSkipped('学習データがありません');
        }
    }
}
