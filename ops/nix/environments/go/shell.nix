{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    go
    gopls
    gotools
    delve
    golangci-lint
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    echo "Go development environment activated"
    echo "  - Go: $(go version)"
    echo "  - gopls: $(gopls version | head -n 1)"
  '';
}
