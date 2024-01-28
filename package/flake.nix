{
  description = "Sample Nix ts-node build";
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    gitignore = {
      url = "github:hercules-ci/gitignore.nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    android-nixpkgs = {
      url = "github:tadfisher/android-nixpkgs";
    };
  };
  outputs = {
    self,
    nixpkgs,
    flake-utils,
    gitignore,
    android-nixpkgs,
    ...
  }:
    flake-utils.lib.eachDefaultSystem (system: let
      pkgs = import nixpkgs {inherit system;};
      nodejs = pkgs.nodejs-18_x;
      # NOTE: this does not work
      appBuild = pkgs.stdenv.mkDerivation {
        name = "example-ts-node";
        version = "0.1.0";
        src = gitignore.lib.gitignoreSource ./.; # uses the gitignore in the repo to only copy files git would see
        buildInputs = [nodejs];
        # https://nixos.org/manual/nixpkgs/stable/#sec-stdenv-phases
        buildPhase = ''
          # each phase has pre/postHooks. When you make your own phase be sure to still call the hooks
          runHook preBuild
          npm ci
          npm run build
          runHook postBuild
        '';
        installPhase = ''
          runHook preInstall
          cp -r node_modules $out/node_modules
          cp package.json $out/package.json
          cp -r dist $out/dist
          runHook postInstall
        '';
      };
      android-sdk = android-nixpkgs.sdk.${system} (sdkPkgs:
        with sdkPkgs; [
          cmdline-tools-latest
          build-tools-30-0-3
          build-tools-33-0-0
          build-tools-33-0-1
          build-tools-34-0-0
          platform-tools
          platforms-android-33
          platforms-android-34
          emulator
          ndk-23-1-7779620
          cmake-3-22-1
          system-images-android-33-google-apis-x86-64
          system-images-android-34-google-apis-x86-64
        ]);
    in
      with pkgs; {
        defaultPackage = appBuild;
        devShell = mkShell {
          buildInputs = [nodejs yarn watchman gradle_7 alejandra nodePackages.prettier ktlint kotlin-language-server];
          ANDROID_SDK_BIN = android-sdk;
          shellHook = ''
            export JAVA_HOME=${pkgs.jdk17.home}
            source ${android-sdk.out}/nix-support/setup-hook
            export PATH=${android-sdk}/bin:$PATH
            ORG_GRADLE_PROJECT_ANDROID_HOME="$ANDROID_HOME"
          '';
        };
      });
}
