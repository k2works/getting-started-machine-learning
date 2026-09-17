{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    dotnet-sdk
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    echo ".NET development environment activated"
    echo "  - .NET SDK: $(dotnet --version)"
  '';
}
