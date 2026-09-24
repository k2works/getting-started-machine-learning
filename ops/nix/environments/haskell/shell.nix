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
    # 整形・静的解析・テストの道具。素の環境には無く、手元の PATH のものが
    # 見えてしまっていたので、環境の側で版を固定する。
    haskellPackages.fourmolu
    haskellPackages.hlint
    haskellPackages.hspec-discover
    # hmatrix は BLAS/LAPACK の C ライブラリを要求する。素の環境には無く、
    # cabal が configure の段階で「Missing (or bad) C libraries: blas, lapack」
    # で止まっていた。
    openblas
    pkg-config
  ];

  shellHook = baseShell.shellHook + ''
    # hmatrix のような C ライブラリを要求するパッケージのために、
    # openblas の場所を cabal と GHC に伝える。
    export LIBRARY_PATH="${packages.openblas}/lib:$LIBRARY_PATH"
    export PKG_CONFIG_PATH="${packages.openblas.dev}/lib/pkgconfig:$PKG_CONFIG_PATH"
    echo "Welcome to the Haskell development environment!"
    ghc --version
    cabal --version
    stack --version
  '';
}
