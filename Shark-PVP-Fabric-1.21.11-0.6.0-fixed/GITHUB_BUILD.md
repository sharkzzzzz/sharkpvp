# Building Shark PVP on GitHub

This repository includes GitHub Actions workflows that build the Fabric mod with Java 21 and Gradle 9.6.1 on GitHub-hosted runners.

## Build without installing Java locally

1. Create a new GitHub repository.
2. Upload/push the contents of this project to the repository.
3. Open **Actions**.
4. Select **Build Shark PVP**.
5. Choose **Run workflow**.
6. When the job finishes, open the workflow run and download the `shark-pvp-...` artifact.

The artifact contains the compiled JAR from `build/libs/`.

## Releases

Push a tag such as `v0.6.0` to trigger the release workflow. GitHub will build the mod and attach the compiled JAR to a GitHub Release.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.1
- Fabric API 0.141.4+1.21.11
- Java 21
- Gradle 9.6.1 (provided by the GitHub Actions runner setup)
