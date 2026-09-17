/**
 * インセプションデッキ PowerPoint 生成スクリプト（汎用テンプレート）
 *
 * テンプレート: docs/template/インセプションデッキ.pptx のスライド構成に準拠
 * データソース: docs/analysis/inception-deck.md, docs/analysis/business_architecture.md
 *
 * 使い方:
 *   1. SLIDE_DATA セクションのプレースホルダーをプロジェクト固有の内容に書き換える
 *   2. node .claude/scripts/generate-inception-deck.mjs を実行
 */
import PptxGenJS from "pptxgenjs";
import { writeFileSync } from "fs";
import { resolve } from "path";

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// SLIDE_DATA: プロジェクト固有データ（ここを書き換える）
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
const SLIDE_DATA = {
  // ── メタ情報 ──
  meta: {
    author: "プロジェクトチーム",
    title: "プロジェクト名 インセプションデッキ",
    version: "v0.1.0",
    date: "YYYY-MM-DD",
    outputFileName: "PROJECT_v0.1.0.pptx",
  },

  // ── Slide 1: タイトル ──
  titleSlide: {
    projectName: "プロジェクト名",
    subtitle: "プロジェクトの説明",
    deckLabel: "インセプションデッキ",
    organization: "組織名",
  },

  // ── Slide 2: 我われはなぜここにいるのか ──
  whyAreWeHere: {
    subtitle: "プロジェクトが解決すべき課題の概要",
    bullets: [
      "課題 1: 現状の問題点を記述",
      "課題 2: 現状の問題点を記述",
      "課題 3: 現状の問題点を記述",
    ],
    highlight: "課題を一文で要約するメッセージ",
  },

  // ── Slide 3: エレベーターピッチ ──
  elevatorPitch: {
    want: "〇〇を実現",
    targetUser: "ターゲットユーザーの説明",
    productName: "プロダクト名",
    category: "プロダクトカテゴリ",
    keyBenefit: "主要な機能・利点の説明",
    competitor: "既存の代替手段",
    differentiator: "差別化ポイント",
  },

  // ── Slide 4: どんな価値をもたらすのか？ ──
  values: {
    rows: [
      // [番号, ビジネス目標, 期待される効果]
      ["1", "ビジネス目標 1", "期待される効果 1"],
      ["2", "ビジネス目標 2", "期待される効果 2"],
      ["3", "ビジネス目標 3", "期待される効果 3"],
    ],
  },

  // ── Slide 5: やらないことリスト ──
  scope: {
    inScope: [
      "スコープ内の機能 1",
      "スコープ内の機能 2",
      "スコープ内の機能 3",
    ],
    outOfScope: [
      "スコープ外の項目 1",
      "スコープ外の項目 2",
    ],
    decideLater: [
      "後で決める項目 1",
      "後で決める項目 2",
    ],
  },

  // ── Slide 6: プロジェクトコミュニティ ──
  stakeholders: {
    rows: [
      // [ステークホルダー, 役割, 主な関心事]
      ["ステークホルダー 1", "役割", "関心事"],
      ["ステークホルダー 2", "役割", "関心事"],
      ["ステークホルダー 3", "役割", "関心事"],
    ],
  },

  // ── Slide 7: 技術的な解決策の概要 ──
  technicalSolution: {
    // 上部の外部チャネル（モール、外部 API など）
    externalChannels: [
      { name: "チャネル 1" },
      { name: "チャネル 2" },
      { name: "チャネル 3" },
    ],
    // 中央のシステム
    systemName: "システム名",
    modules: [
      "モジュール 1",
      "モジュール 2",
      "モジュール 3",
      "モジュール 4",
    ],
    // 下部の外部連携先
    externalServices: [
      { name: "外部サービス 1" },
      { name: "外部サービス 2" },
      { name: "外部サービス 3" },
    ],
    techNote: "技術方針の概要を一文で記述",
  },

  // ── Slide 8: 夜も眠れなくなるような問題 ──
  risks: {
    rows: [
      // [番号, リスク, 影響度, 対策]
      ["1", "リスク 1", "高", "対策 1"],
      ["2", "リスク 2", "中", "対策 2"],
      ["3", "リスク 3", "低", "対策 3"],
    ],
  },

  // ── Slide 9: 俺たちの "A チーム" ──
  team: {
    rows: [
      // [役割, 人数, 備考]
      ["役割 1", "N 名", "備考"],
      ["役割 2", "N 名", "備考"],
    ],
    highlight: "チーム体制の特徴やポイントを一文で記述",
  },

  // ── Slide 10: 期間を見極める ──
  timeline: {
    totalWeeks: 12,
    phases: [
      // weeks: ガントバー上の幅（週数）
      {
        name: "Phase 1: フェーズ名",
        desc: "主な作業内容",
        weeksLabel: "N 週間",
        weeks: 3,
      },
      {
        name: "Phase 2: フェーズ名",
        desc: "主な作業内容",
        weeksLabel: "N 週間",
        weeks: 4,
      },
      {
        name: "Phase 3: フェーズ名",
        desc: "主な作業内容",
        weeksLabel: "N 週間",
        weeks: 3,
      },
      {
        name: "Phase 4: フェーズ名",
        desc: "主な作業内容",
        weeksLabel: "N 週間",
        weeks: 2,
      },
    ],
    // MVP マーカーを表示するフェーズ番号（0 始まり。先頭から N フェーズ完了時点）
    // null の場合はマーカーを表示しない
    mvpAfterPhase: 1,
  },

  // ── Slide 11: トレードオフ・スライダー ──
  tradeoffs: {
    // level: 1=MIN 〜 4=MAX
    sliders: [
      { label: "機能をぜんぶ揃える（スコープ）", level: 2 },
      { label: "予算内に収める（予算）", level: 2 },
      { label: "期日を死守する（時間）", level: 2 },
      { label: "高い品質、少ない欠陥（品質）", level: 3 },
    ],
    qualityPriorities: [
      // [優先度, 品質特性, 理由]
      ["1", "品質特性 1", "理由 1"],
      ["2", "品質特性 2", "理由 2"],
      ["3", "品質特性 3", "理由 3"],
    ],
  },

  // ── Slide 12: 初回のリリースに必要なもの ──
  initialRelease: {
    highlight: "MVP の概要を一文で記述",
    mvpScope: [
      "MVP 機能 1",
      "MVP 機能 2",
      "MVP 機能 3",
    ],
    releaseStrategy: [
      "リリース戦略 1",
      "リリース戦略 2",
    ],
  },
};

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// テーマ設定（テンプレートから抽出）
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
const COLORS = {
  black: "000000",
  white: "FFFFFF",
  darkBlue: "333399",
  teal: "009999",
  lightTeal: "BBE0E3",
  paleBlue: "DAEDEF",
  green: "99CC00",
  gray: "808080",
  lightGray: "D0D0D0",
  orange: "FF6600",
  red: "CC3333",
  yellow: "FFCC00",
};

const FONT = {
  title: "Yu Gothic",
  body: "Yu Gothic",
  code: "Courier New",
};

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// ヘルパー関数
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
function addTitle(slide, text) {
  slide.addText(text, {
    x: 0.5,
    y: 0.2,
    w: 9.0,
    h: 1.0,
    fontSize: 28,
    fontFace: FONT.title,
    bold: true,
    color: COLORS.darkBlue,
  });
}

function addSubtitle(slide, text) {
  slide.addText(text, {
    x: 0.5,
    y: 1.1,
    w: 9.0,
    h: 0.5,
    fontSize: 14,
    fontFace: FONT.body,
    color: COLORS.gray,
  });
}

function addBullets(slide, items, opts = {}) {
  const top = opts.y ?? 1.6;
  const textRows = items.map((item) => ({
    text: item,
    options: {
      fontSize: opts.fontSize ?? 14,
      fontFace: FONT.body,
      color: opts.color ?? COLORS.black,
      bullet: { type: "bullet" },
      paraSpaceAfter: 6,
    },
  }));
  slide.addText(textRows, {
    x: opts.x ?? 0.7,
    y: top,
    w: opts.w ?? 8.6,
    h: opts.h ?? 5.5 - (top - 1.0),
    valign: "top",
  });
}

function addTable(slide, header, rows, opts = {}) {
  const top = opts.y ?? 1.8;
  const tableData = [
    header.map((h) => ({
      text: h,
      options: {
        bold: true,
        fontSize: 11,
        fontFace: FONT.body,
        color: COLORS.white,
        fill: { color: COLORS.darkBlue },
        align: "left",
        valign: "middle",
      },
    })),
    ...rows.map((row) =>
      row.map((cell) => ({
        text: cell,
        options: {
          fontSize: 10,
          fontFace: FONT.body,
          color: COLORS.black,
          align: "left",
          valign: "top",
        },
      }))
    ),
  ];
  slide.addTable(tableData, {
    x: opts.x ?? 0.5,
    y: top,
    w: opts.w ?? 9.0,
    colW: opts.colW,
    border: { type: "solid", pt: 0.5, color: COLORS.lightGray },
    rowH: opts.rowH,
    autoPage: false,
  });
}

function addHighlightBox(slide, text, opts = {}) {
  slide.addText(text, {
    x: opts.x ?? 0.5,
    y: opts.y ?? 1.5,
    w: opts.w ?? 9.0,
    h: opts.h ?? 1.0,
    fontSize: opts.fontSize ?? 16,
    fontFace: FONT.body,
    color: COLORS.darkBlue,
    bold: true,
    fill: { color: COLORS.paleBlue },
    align: "center",
    valign: "middle",
  });
}

function addSliderBar(slide, label, level, y) {
  const barX = 3.5;
  const barW = 5.0;
  const segW = barW / 4;

  slide.addText(label, {
    x: 0.5,
    y,
    w: 3.0,
    h: 0.45,
    fontSize: 11,
    fontFace: FONT.body,
    color: COLORS.black,
    valign: "middle",
  });

  for (let i = 0; i < 4; i++) {
    const isFilled = i < level;
    slide.addShape("rect", {
      x: barX + i * segW,
      y: y + 0.05,
      w: segW - 0.05,
      h: 0.35,
      fill: { color: isFilled ? COLORS.darkBlue : COLORS.lightGray },
      line: { color: COLORS.gray, width: 0.5 },
    });
  }

  slide.addText("MIN", {
    x: barX - 0.05,
    y: y + 0.38,
    w: 0.5,
    h: 0.2,
    fontSize: 7,
    fontFace: FONT.body,
    color: COLORS.gray,
  });
  slide.addText("MAX", {
    x: barX + barW - 0.45,
    y: y + 0.38,
    w: 0.5,
    h: 0.2,
    fontSize: 7,
    fontFace: FONT.body,
    color: COLORS.gray,
  });
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// スライド生成
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
async function main() {
  const { meta } = SLIDE_DATA;

  const pptx = new PptxGenJS();
  pptx.defineLayout({ name: "SCREEN_4x3", width: 10, height: 7.5 });
  pptx.layout = "SCREEN_4x3";
  pptx.author = meta.author;
  pptx.title = meta.title;

  // ─── Slide 1: タイトル ───
  {
    const d = SLIDE_DATA.titleSlide;
    const slide = pptx.addSlide();
    slide.background = { color: COLORS.darkBlue };
    slide.addText(
      [
        {
          text: d.projectName,
          options: {
            fontSize: 48,
            fontFace: FONT.title,
            bold: true,
            color: COLORS.white,
            breakLine: true,
          },
        },
        {
          text: d.subtitle,
          options: {
            fontSize: 28,
            fontFace: FONT.title,
            color: COLORS.lightTeal,
            breakLine: true,
          },
        },
        {
          text: d.deckLabel,
          options: {
            fontSize: 24,
            fontFace: FONT.title,
            color: COLORS.white,
            breakLine: true,
          },
        },
      ],
      { x: 1.0, y: 1.5, w: 8.0, h: 3.5, align: "center", valign: "middle" }
    );
    slide.addText(d.organization, {
      x: 1.0,
      y: 5.5,
      w: 8.0,
      h: 0.5,
      fontSize: 16,
      fontFace: FONT.body,
      color: COLORS.lightTeal,
      align: "center",
    });
    slide.addText(`${meta.version} | ${meta.date}`, {
      x: 1.0,
      y: 6.2,
      w: 8.0,
      h: 0.4,
      fontSize: 12,
      fontFace: FONT.body,
      color: COLORS.gray,
      align: "center",
    });
  }

  // ─── Slide 2: 我われはなぜここにいるのか ───
  {
    const d = SLIDE_DATA.whyAreWeHere;
    const slide = pptx.addSlide();
    addTitle(slide, "我われはなぜここにいるのか");
    addSubtitle(slide, d.subtitle);
    addBullets(slide, d.bullets);
    addHighlightBox(slide, d.highlight, {
      y: 5.8,
      h: 0.7,
      fontSize: 14,
    });
  }

  // ─── Slide 3: エレベーターピッチ ───
  {
    const d = SLIDE_DATA.elevatorPitch;
    const slide = pptx.addSlide();
    addTitle(slide, "エレベーターピッチ");

    const bold = {
      fontSize: 14,
      fontFace: FONT.body,
      color: COLORS.darkBlue,
      bold: true,
    };
    const normal = {
      fontSize: 14,
      fontFace: FONT.body,
      color: COLORS.black,
    };

    const pitchParts = [
      { text: d.want, options: bold },
      { text: " したい\n", options: normal },
      { text: d.targetUser, options: bold },
      { text: " 向けの、\n", options: normal },
      { text: d.productName, options: bold },
      { text: " というプロダクトは、\n", options: normal },
      { text: d.category, options: bold },
      { text: " です。\nこれは ", options: normal },
      { text: d.keyBenefit, options: bold },
      { text: " ができ、\n", options: normal },
      { text: d.competitor, options: bold },
      { text: " とは違って、\n", options: normal },
      { text: d.differentiator, options: bold },
      { text: " が備わっている。", options: normal },
    ];

    slide.addText(pitchParts, {
      x: 0.8,
      y: 1.5,
      w: 8.4,
      h: 4.5,
      fill: { color: COLORS.paleBlue },
      valign: "middle",
      paraSpaceAfter: 8,
    });
  }

  // ─── Slide 4: どんな価値をもたらすのか？ ───
  {
    const d = SLIDE_DATA.values;
    const slide = pptx.addSlide();
    addTitle(slide, "どんな価値をもたらすのか？");
    addTable(
      slide,
      ["#", "ビジネス目標", "期待される効果"],
      d.rows,
      { colW: [0.4, 3.0, 5.6] }
    );
  }

  // ─── Slide 5: やらないことリスト ───
  {
    const d = SLIDE_DATA.scope;
    const slide = pptx.addSlide();
    addTitle(slide, "やらないことリスト");
    addSubtitle(slide, "スコープの範囲");

    const colW = 2.85;
    const colGap = 0.15;
    const cols = [
      { title: "やる（スコープ内）", color: COLORS.teal, items: d.inScope },
      { title: "やらない（スコープ外）", color: COLORS.red, items: d.outOfScope },
      { title: "あとで決める", color: COLORS.orange, items: d.decideLater },
    ];

    cols.forEach((col, i) => {
      const x = 0.5 + i * (colW + colGap);
      slide.addText(col.title, {
        x,
        y: 1.7,
        w: colW,
        h: 0.45,
        fontSize: 13,
        fontFace: FONT.body,
        bold: true,
        color: COLORS.white,
        fill: { color: col.color },
        align: "center",
        valign: "middle",
      });
      const bullets = col.items.map((item) => ({
        text: item,
        options: {
          fontSize: 11,
          fontFace: FONT.body,
          color: COLORS.black,
          bullet: { type: "bullet" },
          paraSpaceAfter: 4,
        },
      }));
      slide.addText(bullets, {
        x,
        y: 2.2,
        w: colW,
        h: 4.8,
        valign: "top",
      });
    });
  }

  // ─── Slide 6: プロジェクトコミュニティ ───
  {
    const d = SLIDE_DATA.stakeholders;
    const slide = pptx.addSlide();
    addTitle(slide, "プロジェクトコミュニティ");
    addSubtitle(slide, "主なステークホルダーと関心事");
    addTable(
      slide,
      ["ステークホルダー", "役割", "主な関心事"],
      d.rows,
      { y: 1.7, colW: [2.0, 2.2, 4.8] }
    );
  }

  // ─── Slide 7: 技術的な解決策の概要 ───
  {
    const d = SLIDE_DATA.technicalSolution;
    const slide = pptx.addSlide();
    addTitle(slide, "技術的な解決策の概要");

    const boxH = 0.5;

    // 上部: 外部チャネル
    const channelCount = d.externalChannels.length;
    const channelW = Math.min(2.0, (9.0 - 0.2 * (channelCount - 1)) / channelCount);
    const channelGap = channelCount > 1 ? (9.0 - channelW * channelCount) / (channelCount - 1) : 0;
    d.externalChannels.forEach((ch, i) => {
      const x = 0.5 + i * (channelW + channelGap);
      slide.addShape("rect", {
        x,
        y: 1.5,
        w: channelW,
        h: boxH,
        fill: { color: COLORS.lightTeal },
        line: { color: COLORS.teal, width: 1 },
      });
      slide.addText(ch.name, {
        x,
        y: 1.5,
        w: channelW,
        h: boxH,
        fontSize: 10,
        fontFace: FONT.body,
        color: COLORS.black,
        align: "center",
        valign: "middle",
      });
    });

    // 接続ラベル
    slide.addText("API", {
      x: 4.0,
      y: 2.05,
      w: 1.0,
      h: 0.3,
      fontSize: 9,
      fontFace: FONT.body,
      color: COLORS.gray,
      align: "center",
    });

    // 中央: メインシステム
    slide.addShape("rect", {
      x: 0.5,
      y: 2.5,
      w: 9.0,
      h: 2.8,
      fill: { color: "F8F8FF" },
      line: { color: COLORS.darkBlue, width: 2 },
    });
    slide.addText(d.systemName, {
      x: 0.5,
      y: 2.5,
      w: 9.0,
      h: 0.4,
      fontSize: 12,
      fontFace: FONT.body,
      bold: true,
      color: COLORS.darkBlue,
      align: "center",
    });

    // 内部モジュール
    d.modules.forEach((name, i) => {
      const row = Math.floor(i / 4);
      const col = i % 4;
      slide.addShape("roundRect", {
        x: 0.8 + col * 2.15,
        y: 3.0 + row * 0.7,
        w: 1.95,
        h: 0.55,
        fill: { color: COLORS.darkBlue },
        rectRadius: 0.05,
      });
      slide.addText(name, {
        x: 0.8 + col * 2.15,
        y: 3.0 + row * 0.7,
        w: 1.95,
        h: 0.55,
        fontSize: 10,
        fontFace: FONT.body,
        color: COLORS.white,
        align: "center",
        valign: "middle",
      });
    });

    // 下部: 外部連携先
    const svcCount = d.externalServices.length;
    const svcW = Math.min(2.0, (9.0 - 0.2 * (svcCount - 1)) / svcCount);
    const svcGap = svcCount > 1 ? (9.0 - svcW * svcCount) / (svcCount - 1) : 0;
    d.externalServices.forEach((svc, i) => {
      const x = 0.5 + i * (svcW + svcGap);
      slide.addShape("rect", {
        x,
        y: 5.6,
        w: svcW,
        h: boxH,
        fill: { color: "FFF3E0" },
        line: { color: COLORS.orange, width: 1 },
      });
      slide.addText(svc.name, {
        x,
        y: 5.6,
        w: svcW,
        h: boxH,
        fontSize: 10,
        fontFace: FONT.body,
        color: COLORS.black,
        align: "center",
        valign: "middle",
      });
    });

    // 技術方針ノート
    slide.addText(d.techNote, {
      x: 0.5,
      y: 6.4,
      w: 9.0,
      h: 0.4,
      fontSize: 10,
      fontFace: FONT.body,
      color: COLORS.gray,
      align: "center",
    });
  }

  // ─── Slide 8: 夜も眠れなくなるような問題 ───
  {
    const d = SLIDE_DATA.risks;
    const slide = pptx.addSlide();
    addTitle(slide, "夜も眠れなくなるような問題は何だろう？");
    addTable(
      slide,
      ["#", "リスク", "影響度", "対策"],
      d.rows,
      { colW: [0.4, 3.0, 0.8, 4.8] }
    );
  }

  // ─── Slide 9: 俺たちの "A チーム" ───
  {
    const d = SLIDE_DATA.team;
    const slide = pptx.addSlide();
    addTitle(slide, '俺たちの "A チーム"');
    addTable(
      slide,
      ["役割", "人数", "備考"],
      d.rows,
      { y: 1.8, colW: [2.5, 1.5, 5.0] }
    );
    addHighlightBox(slide, d.highlight, { y: 4.0, h: 0.8, fontSize: 14 });
  }

  // ─── Slide 10: 期間を見極める ───
  {
    const d = SLIDE_DATA.timeline;
    const slide = pptx.addSlide();
    addTitle(slide, "期間を見極める");

    const barStartX = 0.5;
    const barMaxW = 9.0;
    const phaseWeeks = d.phases.map((p) => p.weeks);

    d.phases.forEach((phase, i) => {
      const y = 1.8 + i * 1.0;
      const startWeek = phaseWeeks.slice(0, i).reduce((a, b) => a + b, 0);
      const x = barStartX + (startWeek / d.totalWeeks) * barMaxW;
      const w = (phaseWeeks[i] / d.totalWeeks) * barMaxW;
      const fillColor = i % 2 === 0 ? COLORS.lightTeal : COLORS.teal;

      slide.addShape("rect", {
        x,
        y,
        w,
        h: 0.45,
        fill: { color: fillColor },
        line: { color: COLORS.teal, width: 1 },
      });
      slide.addText(phase.name, {
        x,
        y,
        w,
        h: 0.45,
        fontSize: 10,
        fontFace: FONT.body,
        bold: true,
        color: COLORS.darkBlue,
        align: "center",
        valign: "middle",
      });
      slide.addText(`${phase.desc}（${phase.weeksLabel}）`, {
        x,
        y: y + 0.45,
        w,
        h: 0.35,
        fontSize: 8,
        fontFace: FONT.body,
        color: COLORS.gray,
        align: "center",
      });
    });

    // MVP マーカー
    if (d.mvpAfterPhase != null) {
      const mvpWeeks = phaseWeeks
        .slice(0, d.mvpAfterPhase + 1)
        .reduce((a, b) => a + b, 0);
      const mvpX = barStartX + (mvpWeeks / d.totalWeeks) * barMaxW;
      slide.addShape("line", {
        x: mvpX,
        y: 1.5,
        w: 0,
        h: 5.0,
        line: { color: COLORS.red, width: 2, dashType: "dash" },
      });
      slide.addText("MVP\nリリース", {
        x: mvpX - 0.5,
        y: 6.5,
        w: 1.2,
        h: 0.5,
        fontSize: 10,
        fontFace: FONT.body,
        bold: true,
        color: COLORS.red,
        align: "center",
      });
    }

    slide.addText("あくまで推測であって、確約するものではありません。", {
      x: 0.5,
      y: 7.0,
      w: 9.0,
      h: 0.3,
      fontSize: 9,
      fontFace: FONT.body,
      color: COLORS.gray,
      align: "right",
    });
  }

  // ─── Slide 11: トレードオフ・スライダー ───
  {
    const d = SLIDE_DATA.tradeoffs;
    const slide = pptx.addSlide();
    addTitle(slide, "トレードオフ・スライダー");

    d.sliders.forEach((s, i) => {
      addSliderBar(slide, s.label, s.level, 1.8 + i * 0.7);
    });

    const qualityY = 1.8 + d.sliders.length * 0.7 + 0.3;
    slide.addText("品質特性の優先順位", {
      x: 0.5,
      y: qualityY,
      w: 9.0,
      h: 0.4,
      fontSize: 14,
      fontFace: FONT.body,
      bold: true,
      color: COLORS.darkBlue,
    });

    addTable(
      slide,
      ["優先度", "品質特性", "理由"],
      d.qualityPriorities,
      { y: qualityY + 0.4, colW: [0.8, 1.8, 6.4] }
    );
  }

  // ─── Slide 12: 初回のリリースに必要なもの ───
  {
    const d = SLIDE_DATA.initialRelease;
    const slide = pptx.addSlide();
    addTitle(slide, "初回のリリースに必要なもの");

    addHighlightBox(slide, d.highlight, { y: 1.5, h: 0.7, fontSize: 16 });

    slide.addText("MVP スコープ", {
      x: 0.5,
      y: 2.5,
      w: 9.0,
      h: 0.4,
      fontSize: 14,
      fontFace: FONT.body,
      bold: true,
      color: COLORS.darkBlue,
    });

    addBullets(slide, d.mvpScope, { y: 2.9, h: 2.5, fontSize: 13 });

    slide.addText("リリース戦略", {
      x: 0.5,
      y: 5.2,
      w: 9.0,
      h: 0.4,
      fontSize: 14,
      fontFace: FONT.body,
      bold: true,
      color: COLORS.darkBlue,
    });

    addBullets(slide, d.releaseStrategy, { y: 5.6, h: 1.5, fontSize: 12 });
  }

  // ─── 保存 ───
  const outputPath = resolve(`docs/analysis/slide/${meta.outputFileName}`);
  const dataBuffer = await pptx.write({ outputType: "nodebuffer" });
  writeFileSync(outputPath, dataBuffer);
  console.log("Generated:", outputPath);
}

main().catch(console.error);
