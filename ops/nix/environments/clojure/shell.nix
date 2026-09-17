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
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    echo "Clojure development environment activated"
    echo "  - Clojure: $(clojure --version)"
    echo "  - Leiningen: $(lein --version | head -n 1)"
    echo "  - Babashka: $(bb --version)"
    echo "  - Clojure LSP: $(clojure-lsp --version)"
  '';
}
