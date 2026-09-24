{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
  # カバレッジのために pcov を足した PHP。composer も同じ PHP から取り、
  # php 本体と composer が動く PHP の版がずれないようにする。
  phpWithPcov = packages.php.withExtensions (
    { enabled, all }: enabled ++ [ all.pcov ]
  );
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ [
    phpWithPcov
    phpWithPcov.packages.composer
    packages.phpactor
  ];
  shellHook = ''
    ${baseShell.shellHook}
    echo "PHP development environment activated"
    echo "  - PHP: $(php --version | head -n 1)"
    echo "  - Composer: $(composer --version --no-interaction 2>/dev/null | head -n 1)"
  '';
}
