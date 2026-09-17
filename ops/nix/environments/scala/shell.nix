{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    scala_3
    sbt
    metals
    scala-cli
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    echo "Scala development environment activated"
    echo "  - Scala: $(scala -version 2>&1 | head -n 1)"
    echo "  - sbt: $(sbt --version | head -n 1)"
    echo "  - Metals: $(metals --version 2>&1 | head -n 1)"
    echo "  - Scala CLI: $(scala-cli --version 2>&1 | head -n 1)"
  '';
}
