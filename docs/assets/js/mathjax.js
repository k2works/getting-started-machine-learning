// MathJax 3 の設定。pymdownx.arithmatex の generic モードが出力する
// \(...\)（インライン）と \[...\]（ブロック）を読み取る。
window.MathJax = {
  tex: {
    inlineMath: [["\\(", "\\)"]],
    displayMath: [["\\[", "\\]"]],
    processEscapes: true,
    processEnvironments: true,
  },
  options: {
    ignoreHtmlClass: ".*|",
    processHtmlClass: "arithmatex",
  },
};

// Material for MkDocs の instant loading（ページ遷移で再読み込みしない仕組み）に対応する。
// 遷移のたびに数式を組み直さないと、2 ページ目以降で数式が生のまま残る。
if (typeof document$ !== "undefined") {
  document$.subscribe(() => {
    if (window.MathJax && window.MathJax.typesetPromise) {
      window.MathJax.startup.output.clearCache();
      window.MathJax.typesetClear();
      window.MathJax.texReset();
      window.MathJax.typesetPromise();
    }
  });
}
