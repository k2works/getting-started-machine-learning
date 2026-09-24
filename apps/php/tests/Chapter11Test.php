<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Chapter02;
use GettingStartedMl\Chapter07;
use GettingStartedMl\Chapter11;
use GettingStartedMl\Chapter11\ConfusionMatrix;
use GettingStartedMl\Dataset;
use GettingStartedMl\Table;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;
use Rubix\ML\Exceptions\InvalidArgumentException as RubixInvalidArgumentException;

final class Chapter11Test extends TestCase
{
    /** 学習データが無ければテストを外す。実データのテストはすべてこれを先に呼ぶ。 */
    private function requireData(string ...$names): void
    {
        foreach ($names as $name) {
            if (!Dataset::exists($name)) {
                $this->markTestSkipped('学習データがありません: ' . Dataset::path($name));
            }
        }
    }

    /**
     * 答えの分かっている 6 件。正例は「はい」。
     *
     * 正解 はい はい はい いいえ いいえ いいえ
     * 予測 はい はい いいえ はい いいえ いいえ
     * → tp=2, fn=1, fp=1, tn=2
     *
     * @return list<string>
     */
    private function actual(): array
    {
        return ['はい', 'はい', 'はい', 'いいえ', 'いいえ', 'いいえ'];
    }

    /** @return list<string> */
    private function predicted(): array
    {
        return ['はい', 'はい', 'いいえ', 'はい', 'いいえ', 'いいえ'];
    }

    // ---- 混同行列 ----

    #[TestDox('混同行列は正解と予測の組を 4 つのますに数える')]
    public function test混同行列は正解と予測の組を4つのますに数える(): void
    {
        $cm = Chapter11::confusionMatrix($this->actual(), $this->predicted(), 'はい');

        $this->assertSame([2, 1, 1, 2], [$cm->tp, $cm->fp, $cm->fn, $cm->tn]);
    }

    #[TestDox('正例が 1 件も無ければ混同行列の tp と fn は 0 になる')]
    public function test正例が1件も無ければ混同行列のTpとFnは0になる(): void
    {
        $cm = Chapter11::confusionMatrix(['a', 'b'], ['a', 'b'], 'c');

        $this->assertSame([0, 0, 0, 2], [$cm->tp, $cm->fp, $cm->fn, $cm->tn]);
    }

    #[TestDox('正解と予測の件数が違えば混同行列を作れない')]
    public function test正解と予測の件数が違えば混同行列を作れない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('正解と予測の件数が違います: 2 と 1');

        Chapter11::confusionMatrix(['a', 'b'], ['a'], 'a');
    }

    // ---- 混同行列から求める指標 ----

    #[TestDox('適合率は正例と予測したうち本当に正例だった割合')]
    public function test適合率は正例と予測したうち本当に正例だった割合(): void
    {
        $this->assertEqualsWithDelta(2.0 / 3.0, (new ConfusionMatrix(2, 1, 1, 2))->precision(), 1e-12);
    }

    #[TestDox('再現率は本当の正例のうち正例と予測できた割合')]
    public function test再現率は本当の正例のうち正例と予測できた割合(): void
    {
        $this->assertEqualsWithDelta(2.0 / 3.0, (new ConfusionMatrix(2, 1, 1, 2))->recall(), 1e-12);
    }

    #[TestDox('F 値は適合率と再現率の調和平均')]
    public function testF値は適合率と再現率の調和平均(): void
    {
        // 適合率 1.0、再現率 0.5 なら調和平均は 2/3
        $this->assertEqualsWithDelta(2.0 / 3.0, (new ConfusionMatrix(1, 0, 1, 2))->f1Score(), 1e-12);
    }

    #[TestDox('分母が 0 になる指標は NaN ではなく 0 を返す')]
    public function test分母が0になる指標はNanではなく0を返す(): void
    {
        $empty = new ConfusionMatrix(0, 0, 0, 3);

        $this->assertSame([0.0, 0.0, 0.0], [$empty->precision(), $empty->recall(), $empty->f1Score()]);
    }

    // ---- 正解と予測から直接求める指標 ----

    #[TestDox('正解率は正解と予測が一致した割合')]
    public function test正解率は正解と予測が一致した割合(): void
    {
        $this->assertEqualsWithDelta(4.0 / 6.0, Chapter11::accuracy($this->actual(), $this->predicted()), 1e-12);
    }

    #[TestDox('正解率は 2 値でなくても求められる')]
    public function test正解率は2値でなくても求められる(): void
    {
        $this->assertEqualsWithDelta(
            1.0 / 3.0,
            Chapter11::accuracy(['a', 'b', 'c'], ['a', 'c', 'b']),
            1e-12,
        );
    }

    #[TestDox('MSE は誤差の 2 乗の平均')]
    public function testMseは誤差の2乗の平均(): void
    {
        $this->assertEqualsWithDelta(2.5, Chapter11::meanSquaredError([3.0, 1.0], [2.0, 3.0]), 1e-12);
    }

    #[TestDox('MSE は大きく外れた 1 件に敏感で MAE より大きくなる')]
    public function testMseは大きく外れた1件に敏感でMaeより大きくなる(): void
    {
        $actual = [0.0, 0.0, 0.0, 0.0];
        $small = [1.0, 1.0, 1.0, 1.0];
        $large = [0.0, 0.0, 0.0, 4.0];

        // MAE はどちらも 1.0 で区別が付かないが、MSE は外れた 1 件を重く見る
        $this->assertEqualsWithDelta(1.0, Chapter07::meanAbsoluteError($actual, $small), 1e-12);
        $this->assertEqualsWithDelta(1.0, Chapter07::meanAbsoluteError($actual, $large), 1e-12);
        $this->assertEqualsWithDelta(1.0, Chapter11::meanSquaredError($actual, $small), 1e-12);
        $this->assertEqualsWithDelta(4.0, Chapter11::meanSquaredError($actual, $large), 1e-12);
    }

    #[TestDox('MSE の平方根は第 7 章の RMSE と一致する')]
    public function testMseの平方根は第7章のRmseと一致する(): void
    {
        $actual = [1.0, 2.0, 3.0, 4.0];
        $predicted = [1.5, 1.0, 4.0, 3.0];

        $this->assertEqualsWithDelta(
            Chapter07::rootMeanSquaredError($actual, $predicted),
            sqrt(Chapter11::meanSquaredError($actual, $predicted)),
            1e-12,
        );
    }

    #[TestDox('平均は値が 1 つも無ければ求められない')]
    public function test平均は値が1つも無ければ求められない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('平均を求める値が 1 つもありません');

        Chapter11::mean([]);
    }

    // ---- ROC 曲線と AUC ----

    #[TestDox('完全に分けられるスコアの AUC は 1 になる')]
    public function test完全に分けられるスコアのAucは1になる(): void
    {
        $curve = Chapter11::rocCurve([0.9, 0.8, 0.2, 0.1], [true, true, false, false]);

        $this->assertEqualsWithDelta(1.0, Chapter11::auc($curve), 1e-12);
    }

    #[TestDox('順が逆のスコアの AUC は 0 になる')]
    public function test順が逆のスコアのAucは0になる(): void
    {
        $curve = Chapter11::rocCurve([0.9, 0.8, 0.2, 0.1], [false, false, true, true]);

        $this->assertEqualsWithDelta(0.0, Chapter11::auc($curve), 1e-12);
    }

    #[TestDox('同じスコアの正例と負例は 1 つの点にまとめて AUC が 0.5 になる')]
    public function test同じスコアの正例と負例は1つの点にまとめてAucが05になる(): void
    {
        $curve = Chapter11::rocCurve([0.5, 0.5, 0.5, 0.5], [true, true, false, false]);

        $this->assertCount(2, $curve);
        $this->assertEqualsWithDelta(0.5, Chapter11::auc($curve), 1e-12);
    }

    #[TestDox('ROC 曲線は偽陽性率も真陽性率も 0 の点から始まり 1 の点で終わる')]
    public function testRoc曲線は偽陽性率も真陽性率も0の点から始まり1の点で終わる(): void
    {
        $curve = Chapter11::rocCurve([0.9, 0.5, 0.4, 0.1], [true, false, true, false]);
        $first = $curve[0];
        $last = $curve[count($curve) - 1];

        $this->assertSame([null, 0.0, 0.0], [$first->threshold, $first->falsePositiveRate, $first->truePositiveRate]);
        $this->assertSame([1.0, 1.0], [$last->falsePositiveRate, $last->truePositiveRate]);
        $this->assertEqualsWithDelta(0.75, Chapter11::auc($curve), 1e-12);
    }

    #[TestDox('正例か負例のどちらかが無ければ ROC 曲線を描けない')]
    public function test正例か負例のどちらかが無ければRoc曲線を描けない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('正例と負例が両方ないと ROC 曲線を描けません');

        Chapter11::rocCurve([0.9, 0.1], [true, true]);
    }

    // ---- K 分割交差検証 ----

    #[TestDox('K 分割はすべての行をちょうど 1 回テストデータにする')]
    public function testK分割はすべての行をちょうど1回テストデータにする(): void
    {
        $folds = Chapter11::kFold(10, 3, 0);
        $tested = [];

        foreach ($folds as $fold) {
            $tested = [...$tested, ...$fold->test];
        }

        sort($tested);
        $this->assertSame(range(0, 9), $tested);
    }

    #[TestDox('K 分割は余りを先頭の分割から 1 件ずつ配る')]
    public function testK分割は余りを先頭の分割から1件ずつ配る(): void
    {
        $sizes = array_map(
            static fn (Chapter11\Fold $fold): int => count($fold->test),
            Chapter11::kFold(10, 3, 0),
        );

        $this->assertSame([4, 3, 3], $sizes);
    }

    #[TestDox('K 分割の訓練データはテストデータの残り全部')]
    public function testK分割の訓練データはテストデータの残り全部(): void
    {
        foreach (Chapter11::kFold(10, 5, 0) as $fold) {
            $this->assertSame([], array_intersect($fold->train, $fold->test));
            $this->assertCount(8, $fold->train);
        }
    }

    #[TestDox('並べ替えない K 分割は先頭から順に切り分ける')]
    public function test並べ替えないK分割は先頭から順に切り分ける(): void
    {
        $folds = Chapter11::kFoldSequential(7, 3);

        $this->assertSame([0, 1, 2], $folds[0]->test);
        $this->assertSame([3, 4], $folds[1]->test);
        $this->assertSame([5, 6], $folds[2]->test);
    }

    #[TestDox('分割の数は 2 以上で件数以下でなければならない')]
    public function test分割の数は2以上で件数以下でなければならない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('分割の数は 2 以上 5 以下にしてください: 6');

        Chapter11::kFold(5, 6, 0);
    }

    #[TestDox('行の位置で値を選べる')]
    public function test行の位置で値を選べる(): void
    {
        $this->assertSame(['c', 'a'], Chapter11::pick(['a', 'b', 'c'], [2, 0]));
    }

    #[TestDox('無い行の位置を指定すれば失敗する')]
    public function test無い行の位置を指定すれば失敗する(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('行がありません: 3');

        Chapter11::pick(['a', 'b', 'c'], [3]);
    }

    // ---- 交差検証 ----

    /**
     * 答えの分かっている 6 件。a が 0.5 より小さければ「小」、そうでなければ「大」。
     *
     * @return list<array<string, float>>
     */
    private function toyX(): array
    {
        return [
            ['a' => 0.0],
            ['a' => 0.1],
            ['a' => 0.2],
            ['a' => 0.8],
            ['a' => 0.9],
            ['a' => 1.0],
        ];
    }

    /** @return list<string> */
    private function toyT(): array
    {
        return ['小', '小', '小', '大', '大', '大'];
    }

    #[TestDox('分けられるデータなら決定木の交差検証はどの分割でも正解率 1.0 になる')]
    public function test分けられるデータなら決定木の交差検証はどの分割でも正解率1になる(): void
    {
        $scores = Chapter11::crossValidate(
            Chapter11::treeTrainer(['a'], 1),
            $this->toyX(),
            $this->toyT(),
            Chapter11::kFoldSequential(6, 3),
            Chapter11::accuracy(...),
        );

        $this->assertEqualsWithDelta([1.0, 1.0, 1.0], $scores, 1e-12);
    }

    #[TestDox('評価関数を差し替えれば同じ分割で別の指標を測れる')]
    public function test評価関数を差し替えれば同じ分割で別の指標を測れる(): void
    {
        $folds = Chapter11::kFoldSequential(6, 3);
        $trainer = Chapter11::treeTrainer(['a'], 1);
        $recall = Chapter11::classificationMetric(
            static fn (ConfusionMatrix $cm): float => $cm->recall(),
            '大',
        );

        // 並べ替えない分割では、最初のテストデータが「小」2 件だけになる。
        // 正例が 1 件も無いので再現率は 0 で、正解率が 1.0 でも指標によって値が変わる
        $this->assertEqualsWithDelta(
            [0.0, 1.0, 1.0],
            Chapter11::crossValidate($trainer, $this->toyX(), $this->toyT(), $folds, $recall),
            1e-12,
        );
    }

    #[TestDox('回帰でも同じ交差検証の関数を使える')]
    public function test回帰でも同じ交差検証の関数を使える(): void
    {
        // t = 1 + 2a の直線に乗るので、どの分割でも誤差は 0 になる
        $x = [['a' => 0.0], ['a' => 1.0], ['a' => 2.0], ['a' => 3.0], ['a' => 4.0], ['a' => 5.0]];
        $t = [1.0, 3.0, 5.0, 7.0, 9.0, 11.0];

        $scores = Chapter11::crossValidate(
            Chapter11::linearTrainer(['a']),
            $x,
            $t,
            Chapter11::kFoldSequential(6, 3),
            Chapter11::meanSquaredError(...),
        );

        $this->assertEqualsWithDelta([0.0, 0.0, 0.0], $scores, 1e-9);
    }

    // ---- Rubix ML との突き合わせ ----

    #[TestDox('Rubix ML の Accuracy は自作の正解率と一致する')]
    public function testRubixMlのAccuracyは自作の正解率と一致する(): void
    {
        $this->assertEqualsWithDelta(
            Chapter11::accuracy($this->actual(), $this->predicted()),
            Chapter11::rubixAccuracy($this->actual(), $this->predicted()),
            1e-12,
        );
    }

    #[TestDox('Rubix ML の MulticlassBreakdown の正例の行は自作の適合率・再現率・F 値と一致する')]
    public function testRubixMlのMulticlassBreakdownの正例の行は自作の指標と一致する(): void
    {
        $cm = Chapter11::confusionMatrix($this->actual(), $this->predicted(), 'はい');
        $theirs = Chapter11::rubixClassScores($this->actual(), $this->predicted(), 'はい');

        $this->assertEqualsWithDelta($cm->precision(), $theirs['precision'], 1e-12);
        $this->assertEqualsWithDelta($cm->recall(), $theirs['recall'], 1e-12);
        $this->assertEqualsWithDelta($cm->f1Score(), $theirs['f1Score'], 1e-12);
    }

    #[TestDox('Rubix ML の FBeta は 2 値でもクラスごとの平均なので自作の F 値と一致しない')]
    public function testRubixMlのFBetaは2値でもクラスごとの平均なので自作のF値と一致しない(): void
    {
        $mine = Chapter11::confusionMatrix($this->actual(), $this->predicted(), 'はい')->f1Score();
        $theirs = Chapter11::rubixFBeta($this->actual(), $this->predicted());

        // 自作は正例だけを見るが、FBeta は「はい」と「いいえ」の両方の適合率・再現率を平均する
        $this->assertEqualsWithDelta(2.0 / 3.0, $mine, 1e-12);
        $this->assertEqualsWithDelta(2.0 / 3.0, $theirs, 1e-12);

        // 正例と負例の件数が偏ると差が出る
        $actual = ['はい', 'はい', 'はい', 'いいえ'];
        $predicted = ['はい', 'はい', 'いいえ', 'いいえ'];
        $binary = Chapter11::confusionMatrix($actual, $predicted, 'はい')->f1Score();

        // 自作は「はい」の tp=2, fp=0, fn=1 だけを見て 0.8。FBeta は「いいえ」の
        // 適合率 0.5・再現率 1.0 も平均に入れるので 0.7895 になる
        $this->assertEqualsWithDelta(0.8, $binary, 1e-12);
        $this->assertEqualsWithDelta(0.7894736842105263, Chapter11::rubixFBeta($actual, $predicted), 1e-12);
    }

    #[TestDox('MulticlassBreakdown に無いクラスを正例に指定すれば失敗する')]
    public function testMulticlassBreakdownに無いクラスを正例に指定すれば失敗する(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('正例のクラスがありません: たぶん');

        Chapter11::rubixClassScores($this->actual(), $this->predicted(), 'たぶん');
    }

    #[TestDox('客室クラスが空欄なら前処理は失敗する')]
    public function test客室クラスが空欄なら前処理は失敗する(): void
    {
        $columns = ['Pclass', 'Age', 'Sex', 'Survived'];
        $rows = [
            ['Pclass' => '1', 'Age' => '30', 'Sex' => 'male', 'Survived' => '1'],
            ['Pclass' => '', 'Age' => '40', 'Sex' => 'female', 'Survived' => '0'],
        ];

        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('値が空欄です: Pclass');

        Chapter11::prepareSurvived(new Table($columns, $rows));
    }

    #[TestDox('興行収入が空欄なら前処理は失敗する')]
    public function test興行収入が空欄なら前処理は失敗する(): void
    {
        $columns = [...Chapter07::FEATURE_COLUMNS, Chapter07::TARGET];
        $rows = [
            ['SNS1' => '1', 'SNS2' => '2', 'actor' => '3', 'original' => '0', 'sales' => '100'],
            ['SNS1' => '1', 'SNS2' => '2', 'actor' => '3', 'original' => '0', 'sales' => ''],
        ];

        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('興行収入が空欄です');

        Chapter11::prepareCinema(new Table($columns, $rows));
    }

    #[TestDox('Rubix ML の混同行列は行が予測で列が正解')]
    public function testRubixMlの混同行列は行が予測で列が正解(): void
    {
        $cm = Chapter11::confusionMatrix($this->actual(), $this->predicted(), 'はい');
        $theirs = Chapter11::rubixConfusionMatrix($this->actual(), $this->predicted());

        // [予測]['はい'] の行に、正解が「はい」だった件数（tp）と「いいえ」だった件数（fp）が並ぶ
        $this->assertSame($cm->tp, $theirs['はい']['はい']);
        $this->assertSame($cm->fp, $theirs['はい']['いいえ']);
        $this->assertSame($cm->fn, $theirs['いいえ']['はい']);
        $this->assertSame($cm->tn, $theirs['いいえ']['いいえ']);
    }

    #[TestDox('Rubix ML の回帰の指標は符号を反転すると自作と一致する')]
    public function testRubixMlの回帰の指標は符号を反転すると自作と一致する(): void
    {
        $actual = [1.0, 2.0, 3.0, 4.0];
        $predicted = [1.5, 1.0, 4.0, 3.0];
        $theirs = Chapter11::rubixRegressionScores($actual, $predicted);

        $this->assertEqualsWithDelta(Chapter11::meanSquaredError($actual, $predicted), $theirs['mse'], 1e-12);
        $this->assertEqualsWithDelta(Chapter07::rootMeanSquaredError($actual, $predicted), $theirs['rmse'], 1e-12);
        $this->assertEqualsWithDelta(Chapter07::meanAbsoluteError($actual, $predicted), $theirs['mae'], 1e-12);
    }

    #[TestDox('Rubix ML の fold は余りの行をどの分割のテストデータにも入れない')]
    public function testRubixMlのFoldは余りの行をどの分割のテストデータにも入れない(): void
    {
        $mine = array_map(
            static fn (Chapter11\Fold $fold): int => count($fold->test),
            Chapter11::kFold(10, 3, 0),
        );

        $this->assertSame([4, 3, 3], $mine);
        $this->assertSame([3, 3, 3], Chapter11::rubixFoldTestSizes(10, 3));
    }

    #[TestDox('Rubix ML の KFold は同じ入力でも毎回同じ値を返すとは限らない')]
    public function testRubixMlのKFoldは同じ入力でも毎回同じ値を返すとは限らない(): void
    {
        // randomize() を呼ぶのでシードを渡す口が無い。分割の中身は確かめず、範囲だけを確かめる
        $score = Chapter11::rubixKFoldAccuracy($this->toyX(), $this->toyT(), ['a'], 1, 3);

        $this->assertGreaterThanOrEqual(0.0, $score);
        $this->assertLessThanOrEqual(1.0, $score);
    }

    // ---- 実データ ----

    /** @return array{x: list<array<string, float>>, t: list<string>} */
    private function survivedData(): array
    {
        return Chapter11::prepareSurvived(Chapter02::loadTable(Dataset::path('Survived.csv')));
    }

    #[Group('data')]
    #[TestDox('Survived の指標は Rubix ML の指標と一致する')]
    public function testSurvivedの指標はRubixMlの指標と一致する(): void
    {
        $this->requireData('Survived.csv', 'cinema.csv');

        ['x' => $x, 't' => $t] = $this->survivedData();
        $fold = Chapter11::kFold(count($x), Chapter11::N_SPLITS, Chapter11::SEED)[0];
        $predict = (Chapter11::treeTrainer(Chapter11::SURVIVED_COLUMNS, Chapter11::TREE_DEPTH))(
            Chapter11::pick($x, $fold->train),
            Chapter11::pick($t, $fold->train),
        );

        $actual = Chapter11::pick($t, $fold->test);
        $predicted = array_map($predict, Chapter11::pick($x, $fold->test));
        $cm = Chapter11::confusionMatrix($actual, $predicted, Chapter11::SURVIVED);
        $theirs = Chapter11::rubixClassScores($actual, $predicted, Chapter11::SURVIVED);

        $this->assertEqualsWithDelta(
            Chapter11::accuracy($actual, $predicted),
            Chapter11::rubixAccuracy($actual, $predicted),
            1e-12,
        );
        $this->assertEqualsWithDelta($cm->precision(), $theirs['precision'], 1e-12);
        $this->assertEqualsWithDelta($cm->recall(), $theirs['recall'], 1e-12);
        $this->assertEqualsWithDelta($cm->f1Score(), $theirs['f1Score'], 1e-12);
    }

    #[Group('data')]
    #[TestDox('cinema の分割ごとの MSE は第 7 章の RMSE の 2 乗と一致する')]
    public function testCinemaの分割ごとのMseは第7章のRmseの2乗と一致する(): void
    {
        $this->requireData('Survived.csv', 'cinema.csv');

        ['x' => $x, 't' => $t] = Chapter11::prepareCinema(
            Chapter02::loadTable(Dataset::path('cinema.csv')),
        );
        $folds = Chapter11::kFold(count($x), Chapter11::N_SPLITS, Chapter11::SEED);
        $trainer = Chapter11::linearTrainer(Chapter07::FEATURE_COLUMNS);

        $mse = Chapter11::crossValidate($trainer, $x, $t, $folds, Chapter11::meanSquaredError(...));
        $rmse = Chapter11::crossValidate($trainer, $x, $t, $folds, Chapter07::rootMeanSquaredError(...));

        foreach ($mse as $index => $value) {
            $this->assertEqualsWithDelta($value, $rmse[$index] ** 2, 1e-6);
        }
    }

    #[Group('data')]
    #[TestDox('Rubix ML の KFold は 0 と 1 のラベルを連続値と見なして落ちる')]
    public function testRubixMlのKFoldは0と1のラベルを連続値と見なして落ちる(): void
    {
        $this->requireData('Survived.csv', 'cinema.csv');

        ['x' => $x, 't' => $t] = $this->survivedData();

        // stratifiedFold がラベルを配列のキーにするので、'0' と '1' が整数 0 と 1 に化ける。
        // 整数のラベルは連続値と判定され、分類器が受け取れなくなる
        $this->expectException(RubixInvalidArgumentException::class);
        $this->expectExceptionMessage('Classifiers require categorical labels, continuous given.');

        Chapter11::rubixKFoldAccuracy($x, $t, Chapter11::SURVIVED_COLUMNS, Chapter11::TREE_DEPTH, 5);
    }

    #[Group('data')]
    #[TestDox('数字でないラベルに付け替えれば Rubix ML の KFold も動くが値は毎回変わる')]
    public function test数字でないラベルに付け替えればRubixMlのKFoldも動くが値は毎回変わる(): void
    {
        $this->requireData('Survived.csv', 'cinema.csv');

        ['x' => $x, 't' => $t] = $this->survivedData();
        $named = array_map(static fn (string $label): string => $label === '1' ? '生存' : '死亡', $t);

        $scores = [];

        for ($i = 0; $i < 5; ++$i) {
            $scores[] = Chapter11::rubixKFoldAccuracy(
                $x,
                $named,
                Chapter11::SURVIVED_COLUMNS,
                Chapter11::TREE_DEPTH,
                5,
            );
        }

        // シードを渡す口が無いので、値そのものは固定できない。自作の 0.7811 の近くに
        // 散らばることだけを確かめる
        foreach ($scores as $score) {
            $this->assertGreaterThan(0.75, $score);
            $this->assertLessThan(0.80, $score);
        }
    }

    #[Group('data')]
    #[TestDox('実行すると交差検証の平均を表示する')]
    public function test実行すると交差検証の平均を表示する(): void
    {
        $this->requireData('Survived.csv', 'cinema.csv');

        $this->assertSame(
            "Survived（決定木、5 分割交差検証の平均）\n"
            . "  正解率: 0.7811\n"
            . "  適合率: 0.7759\n"
            . "  再現率: 0.6306\n"
            . "  F値: 0.6833\n"
            . "cinema（線形回帰、5 分割交差検証の平均）\n"
            . "  RMSE: 405.77\n"
            . "  MAE: 321.53\n"
            . "Rubix ML の fold のテストデータの件数（5 分割）\n"
            . "  891 件を自作: 179, 178, 178, 178, 178\n"
            . "  891 件を Rubix ML: 178, 178, 178, 178, 178\n",
            Chapter11::run(),
        );
    }
}
