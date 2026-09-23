{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    clojure
    leiningen
    babashka
    clojure-lsp
    # 検査に使う道具。環境に無いと CI と手元で同じ検査ができない
    clj-kondo
    cljfmt
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    echo "Clojure development environment activated"
    echo "  - Clojure: $(clojure --version)"
    echo "  - Leiningen: $(lein --version | head -n 1)"
    echo "  - Babashka: $(bb --version)"
    echo "  - Clojure LSP: $(clojure-lsp --version | head -n 1)"
    echo "  - clj-kondo: $(clj-kondo --version)"
    echo "  - cljfmt: $(cljfmt --version 2>&1 | head -n 1)"
  '';
}
