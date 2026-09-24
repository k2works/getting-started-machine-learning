<?php

declare(strict_types=1);

namespace GettingStartedMl;

use GettingStartedMl\Chapter15\Domain;
use GettingStartedMl\Chapter15\FileStore;
use GettingStartedMl\Chapter15\Service;

/**
 * 第 15 章: 機械学習 API とモジュール設計。
 *
 * 第 7・8 章のモデルを学習して保存し、標準の組み込みサーバーで予測 API を公開する。
 * 層は `Chapter15\` の中で分けてある（ドメイン・置き場・サービス・HTTP）。
 */
final class Chapter15
{
    /** 予測 API のポート。 */
    public const int PORT = 8015;

    /** 学習済みモデルの保存先（apps/php/model/ は .gitignore の対象）。 */
    public const string MODEL_DIR = 'model';

    /** 組み込みサーバーに渡す、要求を受け取るスクリプト。 */
    public const string SERVER_SCRIPT = 'tools/chapter15-server.php';

    private const float TEST_SIZE = 0.2;
    private const int SEED = 0;
    private const int MAX_DEPTH = 5;

    /** 第 7・8 章と同じ条件でモデルを学習し、置き場に保存する。 */
    public static function trainAndSaveModels(string $dataDir, FileStore $store): void
    {
        $cinema = Chapter07::prepareCinema($dataDir . '/cinema.csv', self::TEST_SIZE, self::SEED);
        $store->saveSalesModel(
            Chapter07::fit($cinema['xTrain'], $cinema['tTrain'], Chapter07::FEATURE_COLUMNS),
        );

        $rows = Chapter02::loadTable($dataDir . '/Survived.csv')->rows;
        $split = Chapter02::splitTrainTest($rows, Chapter08::targetLabels($rows), self::TEST_SIZE, self::SEED);
        $store->saveSurvivalModel(Chapter08::fit(
            Chapter08::featuresTable($split['xTrain']),
            $split['tTrain'],
            self::MAX_DEPTH,
            WeightedTree::BALANCED,
        ));
    }

    /**
     * モデルを学習して保存し、組み込みサーバーの起動のしかたを返す。
     *
     * Elixir の Bandit や Clojure の Jetty と違い、**PHP の組み込みサーバーは
     * プログラムの中から起動するものではない**。`php -S` がサーバーで、要求ごとに
     * スクリプトを走らせる。学習と待ち受けはここで分かれる。
     */
    public static function run(?string $modelDir = null): string
    {
        $store = new FileStore($modelDir ?? self::MODEL_DIR);
        self::trainAndSaveModels(Dataset::dir(), $store);
        $health = (new Service($store))->health();

        $lines = [];

        foreach (Domain::MODEL_NAMES as $model) {
            $lines[] = sprintf('モデル %s: %s', $model, $health[$model] ? 'true' : 'false');
        }

        $lines[] = sprintf(
            'php -S 127.0.0.1:%d %s で待ち受けます（http://127.0.0.1:%d）',
            self::PORT,
            self::SERVER_SCRIPT,
            self::PORT,
        );

        return implode("\n", $lines) . "\n";
    }
}
