<div align="center">

# ABK

**AnyBase Kernel**

An automation repository and Android app for building, distributing, and managing GKI KernelSU / SUSFS kernels.

[![Release](https://img.shields.io/github/v/release/xingguangcuican6666/ABK?label=Release&style=flat-square&logo=github&logoColor=white&color=2ea44f)](https://github.com/xingguangcuican6666/ABK/releases)
[![ABK App](https://img.shields.io/github/actions/workflow/status/xingguangcuican6666/ABK/build-abk-app.yml?label=ABK%20App&style=flat-square&logo=android&logoColor=white)](https://github.com/xingguangcuican6666/ABK/actions/workflows/build-abk-app.yml)
[![KernelSU](https://img.shields.io/badge/KernelSU-Supported-5AA300?style=flat-square)](https://kernelsu.org/)
[![SUSFS](https://img.shields.io/badge/SUSFS-Integrated-E67E22?style=flat-square)](https://gitlab.com/simonpunk/susfs4ksu)

[简体中文](README.md) | English

</div>

## Purpose

ABK exists to turn the manual workflow of forking, enabling Actions, filling GKI parameters, starting builds, downloading artifacts, and flashing/installing outputs into a more direct process.

The repository provides GitHub Actions kernel build workflows. The Android app handles root checks, GitHub authorization, fork checks/sync, build dispatch, progress notifications, artifact downloads, and flashing/install entry points.

## Quick Links

- Repository: https://github.com/xingguangcuican6666/ABK
- Releases: https://github.com/xingguangcuican6666/ABK/releases
- Actions: https://github.com/xingguangcuican6666/ABK/actions
- Pages: https://xingguangcuican6666.github.io/ABK/
- ABK App CI: https://github.com/xingguangcuican6666/ABK/actions/workflows/build-abk-app.yml

## Scope

- Android 12 / 13 / 14 / 15 / 16 GKI build workflows.
- KernelSU Official, KernelSU Next, SukiSU, and ReSukiSU variants.
- Optional SUSFS, ZRAM, BBG, KPM, Re-Kernel, and OnePlus 8E support.
- Artifact handling for AnyKernel3 packages, kernel images, KernelSU managers, and SUSFS modules.

Actual compatibility depends on the device, kernel version, upstream branch state, and current patch compatibility.

## Usage

1. Fork this repository to your own GitHub account.
2. Open the Actions page in your fork and enable workflows once.
3. Use the ABK app to sign in to GitHub and authorize access.
4. Let the app check your fork and upstream sync state.
5. Confirm or adjust the recommended build parameters on the Build tab.
6. Dispatch the build and monitor progress from notifications or the app.
7. Download the required img, AnyKernel3 package, manager, or SUSFS module.
8. Flash or install only after confirming the risk.

You can also run the workflows manually from GitHub Actions.

## Risk Notice

- Flashing kernels is high-risk and may cause boot failure, data loss, or require restoring a stock boot image.
- Do not build or flash if you are unsure about the target partition, kernel version, Android version, or security patch level.
- OnePlus ColorOS 14 / 15 compatibility still needs device-side validation and may require data wiping in failure cases.
- If a build fails, first check whether SukiSU / SUSFS / ReSukiSU upstream branches have recently changed and are temporarily out of sync.

## Custom Commit Pinning

[`config/config`](config/config) can pin SUSFS and SukiSU commits. This is useful when the latest upstream commit is temporarily broken and you need a known stable revision.

```ini
custom=true

gki-android12-5.10=
gki-android13-5.15=
gki-android14-6.1=
gki-android15-6.6=

sukisu=
```

An empty value means the latest commit of that branch will be used.

## Stock Config

To make `/proc/config.gz` in the built kernel closer to your stock kernel configuration, export the stock kernel config from your device, decompress it, rename it to `stock_defconfig`, and commit it under [`config/`](config/).

The build workflow auto-detects and applies this file. If the file is absent, the step is skipped.

## App

The ABK app follows a Material 3 Expressive design direction and targets an end-to-end mobile build flow:

- Check root permission on startup.
- Use GitHub Device Flow and ask the user to confirm authorization.
- Check whether the user has forked this repository and create the fork if needed.
- Check whether the fork is behind upstream and prompt for sync.
- Detect the current kernel version and prefill recommended build parameters.
- Dispatch GitHub Actions workflows and sync progress.
- Download artifacts after successful builds and provide flashing/install entry points.

The app is built by the [`Build ABK App`](.github/workflows/build-abk-app.yml) workflow.

## Acknowledgements

ABK continues development on top of the following projects, repositories, and community work. This section centralizes the major repositories and projects referenced by the README, website, workflows, or release notes:

- Upstream repository: [zzh20188/GKI_KernelSU_SUSFS](https://github.com/zzh20188/GKI_KernelSU_SUSFS)
- KernelSU: [tiann/KernelSU](https://github.com/tiann/KernelSU)
- KernelSU Next: [KernelSU-Next/KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next)
- SukiSU Ultra: [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)
- ReSukiSU: [ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU)
- SUSFS: [simonpunk/susfs4ksu](https://gitlab.com/simonpunk/susfs4ksu)
- SUSFS GitHub mirror / patch source: [ShirkNeko/susfs4ksu](https://github.com/ShirkNeko/susfs4ksu)
- SukiSU patch: [ShirkNeko/SukiSU_patch](https://github.com/ShirkNeko/SukiSU_patch)
- AnyKernel3: [WildKernels/AnyKernel3](https://github.com/WildKernels/AnyKernel3)
- Kernel patches: [WildKernels/kernel_patches](https://github.com/WildKernels/kernel_patches)
- Action-Build: [Numbersf/Action-Build](https://github.com/Numbersf/Action-Build)
- SUSFS module build source: [sidex15/susfs4ksu-module](https://github.com/sidex15/susfs4ksu-module)
- GCC prebuilts: [LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1](https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1)
- Baseband Guard: [vc-teahouse/Baseband-guard](https://github.com/vc-teahouse/Baseband-guard)
- Re-Kernel: [Sakion-Team/Re-Kernel](https://github.com/Sakion-Team/Re-Kernel)
- KernelSU website: https://kernelsu.org/

## License

This repository references multiple third-party projects, patches, and generated artifacts. Before using, redistributing, or modifying them, follow the license and terms of each upstream project.
