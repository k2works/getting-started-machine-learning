{ packages ? import <nixpkgs> { } }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inputsFrom = [ baseShell ];
  buildInputs = with packages; [
    ghc
    stack
    cabal-install
    haskell-language-server
  ];

  shellHook = baseShell.shellHook + ''
    echo "Welcome to the Haskell development environment!"
    ghc --version
    cabal --version
    stack --version
  '';
}
