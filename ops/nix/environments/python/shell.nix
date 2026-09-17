{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    (python3.withPackages (ps: with ps; [
      uv
      mkdocs
      mkdocs-material
      pymdown-extensions
      # plantuml-markdown and others might need to be checked if available
    ]))
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    echo "Python/MkDocs development environment activated"
  '';
}
