<?php

declare(strict_types=1);

namespace GettingStartedMl\Tests;

use GettingStartedMl\Chapter03;
use GettingStartedMl\Chapter03\DecisionTree;
use GettingStartedMl\Chapter03\Leaf;
use GettingStartedMl\Chapter03\Node;
use GettingStartedMl\Chapter03\RubixTree;
use GettingStartedMl\Dataset;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\Attributes\TestDox;
use PHPUnit\Framework\TestCase;

final class Chapter03Test extends TestCase
{
    /** 架空のデータの特徴量の列。 */
    private const array COLUMNS = ['たて', 'よこ'];

    /**
     * 架空の三色のデータ。「たて」だけで 3 色に分かれ、「よこ」は色と関係がない。
     *
     * @return array{list<array<string, float>>, list<string>}
     */
    private function threeColors(): array
    {
        return [
            [
                ['たて' => 1.0, 'よこ' => 1.0],
                ['たて' => 1.0, 'よこ' => 2.0],
                ['たて' => 5.0, 'よこ' => 1.0],
                ['たて' => 5.0, 'よこ' => 2.0],
                ['たて' => 9.0, 'よこ' => 1.0],
                ['たて' => 9.0, 'よこ' => 2.0],
            ],
            ['あか', 'あか', 'あお', 'あお', 'きいろ', 'きいろ'],
        ];
    }

    #[TestDox('ラベルが 1 種類ならジニ不純度は 0')]
    public function testラベルが1種類ならジニ不純度は0(): void
    {
        $this->assertSame(0.0, DecisionTree::gini(['きのこ', 'きのこ']));
    }

    #[TestDox('半々に分かれていればジニ不純度は 0.5')]
    public function test半々に分かれていればジニ不純度は0_5(): void
    {
        $this->assertSame(0.5, DecisionTree::gini(['きのこ', 'たけのこ']));
    }

    #[TestDox('ラベルが無ければジニ不純度は 0')]
    public function testラベルが無ければジニ不純度は0(): void
    {
        $this->assertSame(0.0, DecisionTree::gini([]));
    }

    #[TestDox('3 種類が均等ならジニ不純度は 2/3')]
    public function test三種類が均等ならジニ不純度は三分の二(): void
    {
        $this->assertEqualsWithDelta(2 / 3, DecisionTree::gini(['あか', 'あお', 'きいろ']), 1e-12);
    }

    #[TestDox('偏るほどジニ不純度は小さくなる')]
    public function test偏るほどジニ不純度は小さくなる(): void
    {
        $this->assertEqualsWithDelta(0.375, DecisionTree::gini(['あ', 'あ', 'あ', 'い']), 1e-12);
    }

    #[TestDox('いちばん多いラベルを返す')]
    public function testいちばん多いラベルを返す(): void
    {
        $this->assertSame('あお', DecisionTree::majority(['あか', 'あお', 'あお']));
    }

    #[TestDox('同数なら先に現れたラベルを返す')]
    public function test同数なら先に現れたラベルを返す(): void
    {
        $this->assertSame('あか', DecisionTree::majority(['あか', 'あお']));
    }

    #[TestDox('数字だけのラベルでも文字列として返す')]
    public function test数字だけのラベルでも文字列として返す(): void
    {
        // PHP の配列は「1」のような鍵を整数に変えてしまうので、戻り値の型を確かめる。
        $this->assertSame('2', DecisionTree::majority(['1', '2', '2']));
    }

    #[TestDox('ラベルが無ければいちばん多いラベルを求められない')]
    public function testラベルが無ければ多数決できない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('正解ラベルがありません');

        DecisionTree::majority([]);
    }

    #[TestDox('最良の分割は隣り合う値の中点を境界にする')]
    public function test最良の分割は中点を境界にする(): void
    {
        [$x, $t] = $this->threeColors();

        $split = DecisionTree::bestSplit($x, $t, self::COLUMNS);

        $this->assertNotNull($split);
        $this->assertSame('たて', $split->feature);
        $this->assertSame(3.0, $split->threshold);
        $this->assertEqualsWithDelta(1 / 3, $split->impurity, 1e-12);
    }

    #[TestDox('ラベルが 1 種類なら分割しない')]
    public function testラベルが1種類なら分割しない(): void
    {
        $x = [['たて' => 1.0, 'よこ' => 1.0], ['たて' => 2.0, 'よこ' => 2.0]];

        $this->assertNull(DecisionTree::bestSplit($x, ['あか', 'あか'], self::COLUMNS));
    }

    #[TestDox('値がすべて同じなら分割しない')]
    public function test値がすべて同じなら分割しない(): void
    {
        $x = [['たて' => 1.0, 'よこ' => 1.0], ['たて' => 1.0, 'よこ' => 1.0]];

        $this->assertNull(DecisionTree::bestSplit($x, ['あか', 'あお'], self::COLUMNS));
    }

    #[TestDox('データが無ければ分割しない')]
    public function testデータが無ければ分割しない(): void
    {
        $this->assertNull(DecisionTree::bestSplit([], [], self::COLUMNS));
    }

    #[TestDox('同じ不純度なら列の順で前の分割を選ぶ')]
    public function test同じ不純度なら列の順で前の分割を選ぶ(): void
    {
        // 2 つの列がまったく同じ値なので、どちらで分けても不純度は同じになる。
        $x = [['たて' => 1.0, 'よこ' => 1.0], ['たて' => 2.0, 'よこ' => 2.0]];

        $split = DecisionTree::bestSplit($x, ['あか', 'あお'], self::COLUMNS);

        $this->assertNotNull($split);
        $this->assertSame('たて', $split->feature);
    }

    #[TestDox('深さ 1 なら 2 つの葉になる')]
    public function test深さ1なら2つの葉になる(): void
    {
        [$x, $t] = $this->threeColors();

        $tree = DecisionTree::fit($x, $t, self::COLUMNS, 1)->tree;

        $this->assertInstanceOf(Node::class, $tree);
        $this->assertInstanceOf(Leaf::class, $tree->left);
        $this->assertInstanceOf(Leaf::class, $tree->right);
        $this->assertSame('あか', $tree->left->label);
        // 右には「あお」2 件と「きいろ」2 件が残る。同数なので先に現れた「あお」を選ぶ。
        $this->assertSame('あお', $tree->right->label);
    }

    #[TestDox('深さ 0 ならいちばん多いラベルの葉だけになる')]
    public function test深さ0なら葉だけになる(): void
    {
        [$x, $t] = $this->threeColors();

        $tree = DecisionTree::fit($x, $t, self::COLUMNS, 0)->tree;

        $this->assertInstanceOf(Leaf::class, $tree);
        $this->assertSame('あか', $tree->label);
    }

    #[TestDox('深さを制限しなければ訓練データを全部当てる')]
    public function test深さを制限しなければ訓練データを全部当てる(): void
    {
        [$x, $t] = $this->threeColors();

        $this->assertSame($t, DecisionTree::fit($x, $t, self::COLUMNS)->predict($x));
    }

    #[TestDox('学習していない特徴量も木をたどって予測できる')]
    public function test学習していない特徴量も予測できる(): void
    {
        [$x, $t] = $this->threeColors();
        $model = DecisionTree::fit($x, $t, self::COLUMNS);

        $this->assertSame(
            ['あか', 'きいろ'],
            $model->predict([['たて' => 0.5, 'よこ' => 9.0], ['たて' => 100.0, 'よこ' => 9.0]]),
        );
    }

    #[TestDox('特徴量と正解ラベルの件数が違えば学習できない')]
    public function test件数が違えば学習できない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('特徴量と正解ラベルの件数が違います: 1 と 2');

        DecisionTree::fit([['たて' => 1.0]], ['あか', 'あお'], self::COLUMNS);
    }

    #[TestDox('正解ラベルが無ければ学習できない')]
    public function test正解ラベルが無ければ学習できない(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('正解ラベルがありません');

        DecisionTree::fit([], [], self::COLUMNS);
    }

    #[TestDox('特徴量に列が無ければ予測できない')]
    public function test特徴量に列が無ければ予測できない(): void
    {
        [$x, $t] = $this->threeColors();
        $model = DecisionTree::fit($x, $t, self::COLUMNS, 1);

        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('列がありません: たて');

        $model->predict([['よこ' => 1.0]]);
    }

    #[TestDox('木を字下げ付きの文字列にする')]
    public function test木を字下げ付きの文字列にする(): void
    {
        [$x, $t] = $this->threeColors();

        $expected = <<<'TREE'
            たて <= 3.0000
              あか
            たて > 3.0000
              たて <= 7.0000
                あお
              たて > 7.0000
                きいろ

            TREE;

        $this->assertSame($expected, DecisionTree::fit($x, $t, self::COLUMNS)->format());
    }

    #[TestDox('葉だけの木は文字列もラベル 1 行になる')]
    public function test葉だけの木は1行になる(): void
    {
        $this->assertSame("あか\n", DecisionTree::formatTree(new Leaf('あか')));
    }

    #[TestDox('Rubix ML には列の順に並べた数値の配列を渡す')]
    public function testRubixMLには列の順に並べた配列を渡す(): void
    {
        $samples = RubixTree::samples([['よこ' => 2.0, 'たて' => 1.0]], self::COLUMNS);

        $this->assertSame([[1.0, 2.0]], $samples);
    }

    #[TestDox('Rubix ML に渡す特徴量に列が無ければ失敗する')]
    public function testRubixMLに渡す特徴量に列が無ければ失敗する(): void
    {
        $this->expectException(InvalidArgumentException::class);
        $this->expectExceptionMessage('列がありません: よこ');

        RubixTree::samples([['たて' => 1.0]], self::COLUMNS);
    }

    #[TestDox('架空のデータでは Rubix ML と自作の予測が一致する')]
    public function test架空のデータではRubixMLと一致する(): void
    {
        [$x, $t] = $this->threeColors();
        $model = DecisionTree::fit($x, $t, self::COLUMNS);

        $this->assertSame($model->predict($x), RubixTree::predict($x, $t, $x, self::COLUMNS));
    }

    #[TestDox('Rubix ML の決定木は深さを制限できる')]
    public function testRubixMLの決定木は深さを制限できる(): void
    {
        [$x, $t] = $this->threeColors();

        // 深さ 1 では 2 つの葉しか作れないので、3 色のうち 1 色は当てられない。
        $this->assertSame(
            ['あか', 'あか', 'あお', 'あお', 'あお', 'あお'],
            RubixTree::predict($x, $t, $x, self::COLUMNS, 1),
        );
    }

    #[TestDox('深さごとの正解率を 1 行にまとめる')]
    public function test深さごとの正解率を1行にまとめる(): void
    {
        [$x, $t] = $this->threeColors();
        $split = ['xTrain' => $x, 'xTest' => $x, 'tTrain' => $t, 'tTest' => $t];

        $this->assertSame("制限なし\t1.0000\t1.0000\t1.0000", Chapter03::accuracyRow(null, $split, self::COLUMNS));
    }

    #[Group('data')]
    #[TestDox('実データの深さごとの正解率がほかの言語版と一致する')]
    public function test実データの正解率が一致する(): void
    {
        $this->skipWithoutData();

        $output = Chapter03::run();

        // Elixir 版・Java 版・Scala 版・Clojure 版と同じ値。
        $this->assertStringContainsString("1\t0.6762\t0.6444\t0.6444\n", $output);
        $this->assertStringContainsString("2\t0.9333\t0.9556\t0.9556\n", $output);
        $this->assertStringContainsString("5\t0.9810\t0.9333\t0.9333\n", $output);
        // 深さを制限しないときだけ Rubix ML と食い違う（境界の取り方が違うため）。
        $this->assertStringContainsString("制限なし\t1.0000\t0.9333\t0.9111\n", $output);
    }

    #[Group('data')]
    #[TestDox('実データの深さ 2 の木は花弁幅だけで分かれる')]
    public function test実データの深さ2の木(): void
    {
        $this->skipWithoutData();

        $expected = <<<'TREE'
            花弁幅 <= 0.2950
              Iris-setosa
            花弁幅 > 0.2950
              花弁幅 <= 0.6500
                Iris-versicolor
              花弁幅 > 0.6500
                Iris-virginica

            TREE;

        $this->assertStringEndsWith($expected, Chapter03::run());
    }

    private function skipWithoutData(): void
    {
        if (!Dataset::exists('iris.csv')) {
            $this->markTestSkipped('学習データがありません: ' . Dataset::path('iris.csv'));
        }
    }
}
