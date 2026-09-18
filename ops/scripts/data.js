'use strict';

import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';

// ============================================
// 設定
// ============================================

/** 学習データの配置先（.gitignore 対象） */
const DATA_DIR = path.join('apps', 'data', 'sukkiri-ml');

/** 配布 ZIP の展開用一時ディレクトリ（tmp/ は .gitignore 対象） */
const EXTRACT_DIR = path.join('tmp', 'sukkiri-ml');

/** 書籍サポートページ（配布 ZIP の入手先） */
const SUPPORT_URL = 'https://sukkiri.jp/books/sukkiri_ml';

/** 記事で使う学習データ */
const REQUIRED_FILES = [
  'KvsT.csv',
  'iris.csv',
  'cinema.csv',
  'Survived.csv',
  'Boston.csv',
  'Wholesale.csv',
  'Bank.csv',
  'bike.tsv',
  'weather.csv',
];

// ============================================
// ヘルパー関数
// ============================================

/** 配布 ZIP の既定の置き場所（先に見つかったものを使う） */
const DEFAULT_ZIPS = [
  path.join('tmp', 'sukkiri-ml-codes.zip'),
  path.join('apps', 'data', 'sukkiri-ml-codes.zip'),
];

/**
 * 配布 ZIP のパス（ML_DATA_ZIP → tmp/ → apps/data/ の順）
 * @returns {string}
 */
function zipPath() {
  return process.env.ML_DATA_ZIP || DEFAULT_ZIPS.find((zip) => fs.existsSync(zip)) || DEFAULT_ZIPS[0];
}

/**
 * ZIP を展開する（Windows は標準の bsdtar、それ以外は unzip を使う）
 * Windows では PATH 上の tar が Git for Windows の GNU tar（zip 非対応）になることがあるため、System32 の tar.exe を指定する
 * @param {string} zip - ZIP ファイルのパス
 * @param {string} dest - 展開先ディレクトリ
 */
function extractZip(zip, dest) {
  fs.mkdirSync(dest, { recursive: true });
  const windowsTar = path.join(process.env.SystemRoot || 'C:\\Windows', 'System32', 'tar.exe');
  const cmd = process.platform === 'win32'
    ? `"${windowsTar}" -xf "${zip}" -C "${dest}"`
    : `unzip -oq "${zip}" -d "${dest}"`;
  execSync(cmd, { stdio: 'inherit' });
}

/**
 * 配置先に無い学習データのファイル名を返す
 * @returns {string[]}
 */
function missingFiles() {
  return REQUIRED_FILES.filter((name) => !fs.existsSync(path.join(DATA_DIR, name)));
}

// ============================================
// Gulp タスク
// ============================================

/**
 * 学習データタスクを gulp に登録する
 * @param {import('gulp').Gulp} gulp - Gulp インスタンス
 */
export default function (gulp) {
  gulp.task('data:setup', (done) => {
    const zip = zipPath();
    if (!fs.existsSync(zip)) {
      done(new Error(`配布 ZIP が見つかりません: ${zip}\n${SUPPORT_URL} から入手し、ML_DATA_ZIP でパスを指定してください。`));
      return;
    }
    extractZip(zip, EXTRACT_DIR);
    fs.mkdirSync(DATA_DIR, { recursive: true });
    for (const name of REQUIRED_FILES) {
      fs.copyFileSync(path.join(EXTRACT_DIR, 'datafiles', name), path.join(DATA_DIR, name));
    }
    console.log(`学習データを ${DATA_DIR} に配置しました（${REQUIRED_FILES.length} ファイル）。`);
    done();
  });

  gulp.task('data:setup:ifmissing', (done) => {
    if (missingFiles().length === 0) {
      console.log(`学習データは配置済みです（${DATA_DIR}）。`);
      done();
      return;
    }
    gulp.series('data:setup')(done);
  });

  gulp.task('data:check', (done) => {
    const missing = missingFiles();
    if (missing.length > 0) {
      done(new Error(`学習データが不足しています（${DATA_DIR}）: ${missing.join(', ')}\ngulp data:setup で配置してください。`));
      return;
    }
    console.log(`学習データは揃っています（${DATA_DIR}、${REQUIRED_FILES.length} ファイル）。`);
    done();
  });

  gulp.task('data:help', (done) => {
    console.log(`
学習データ（スッキリわかる Python による機械学習入門 配布データ）

  gulp data:setup   配布 ZIP を展開し、${DATA_DIR} に学習データを配置する
  gulp data:setup:ifmissing  学習データが未配置の場合のみ data:setup を実行する
  gulp data:check   ${DATA_DIR} に学習データが揃っているか確認する
  gulp data:help    このヘルプを表示する

環境変数:
  ML_DATA_ZIP       配布 ZIP のパス（既定 tmp/ → apps/data/ の sukkiri-ml-codes.zip）

配布 ZIP は書籍購入者のみ利用できます。入手先: ${SUPPORT_URL}
学習データはリポジトリにコミットしないでください（apps/data/ は .gitignore 対象）。
`);
    done();
  });
}
