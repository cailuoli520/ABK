<div align="center">

# ABK

**AnyBase Kernel**

用于构建、分发和管理 GKI KernelSU / SUSFS 内核的自动化仓库与 Android 应用。

[![Release](https://img.shields.io/github/v/release/xingguangcuican6666/ABK?label=Release&style=flat-square&logo=github&logoColor=white&color=2ea44f)](https://github.com/xingguangcuican6666/ABK/releases)
[![ABK App](https://img.shields.io/github/actions/workflow/status/xingguangcuican6666/ABK/build-abk-app.yml?label=ABK%20App&style=flat-square&logo=android&logoColor=white)](https://github.com/xingguangcuican6666/ABK/actions/workflows/build-abk-app.yml)
[![KernelSU](https://img.shields.io/badge/KernelSU-Supported-5AA300?style=flat-square)](https://kernelsu.org/)
[![SUSFS](https://img.shields.io/badge/SUSFS-Integrated-E67E22?style=flat-square)](https://gitlab.com/simonpunk/susfs4ksu)

简体中文 | [English](README-EN.md)

</div>

## 项目定位

ABK 的目标是把手动 fork、启用 Actions、填写 GKI 参数、触发构建、下载产物和刷写安装这些步骤收敛到一个更顺手的流程里。

仓库侧提供 GitHub Actions 构建工作流；App 侧提供 Root 检查、GitHub 授权、fork 检查/同步、构建提交、进度通知、产物下载和刷写/安装入口。

## 快速入口

- 仓库主页：https://github.com/xingguangcuican6666/ABK
- Releases：https://github.com/xingguangcuican6666/ABK/releases
- Actions：https://github.com/xingguangcuican6666/ABK/actions
- Pages：https://xingguangcuican6666.github.io/ABK/
- ABK App CI：https://github.com/xingguangcuican6666/ABK/actions/workflows/build-abk-app.yml

## 支持范围

- Android 12 / 13 / 14 / 15 / 16 GKI 构建流程。
- KernelSU Official、KernelSU Next、SukiSU、ReSukiSU 构建分支。
- SUSFS、ZRAM、BBG、KPM、Re-Kernel、一加 8E 支持等可选功能。
- AnyKernel3 包、kernel img、KernelSU 管理器和 SUSFS 模块产物整理。

实际可用性取决于目标设备、内核版本、上游分支状态和当前补丁兼容性。

## 使用方式

1. Fork 本仓库到自己的 GitHub 账号。
2. 首次进入 fork 仓库的 Actions 页面并启用工作流。
3. 使用 ABK App 登录 GitHub，授权后让 App 检查 fork 与上游同步状态。
4. 在 App 的“构建内核”页确认或调整设备推荐参数。
5. 提交构建后等待通知栏和 App 内进度更新。
6. 构建完成后下载需要的 img、AnyKernel3、管理器或 SUSFS 模块。
7. 在确认风险后按需刷写 boot 镜像或安装模块/APK。

也可以直接在 GitHub Actions 中手动运行对应工作流。

## 风险提示

- 刷写内核属于高风险操作，可能导致无法开机、数据损坏或需要恢复出厂 boot 镜像。
- 不建议在不确定设备分区、内核版本、Android 版本和安全补丁级别时强行构建或刷写。
- 一加 ColorOS 14 / 15 等设备兼容性仍需自行验证，异常情况下可能需要清除数据。
- 如果构建失败，优先检查 SukiSU / SUSFS / ReSukiSU 等上游分支是否刚更新且尚未互相适配。

## 自定义提交固定

[`config/config`](config/config) 可用于固定 SUSFS 和 SukiSU 的 commit，适合在上游最新提交临时不可用时回退到稳定版本。

```ini
custom=true

gki-android12-5.10=
gki-android13-5.15=
gki-android14-6.1=
gki-android15-6.6=

sukisu=
```

留空表示使用对应分支的最新提交。

## Stock Config

如果需要让构建产物中的 `/proc/config.gz` 更接近官方内核配置，可以将设备官方内核导出的配置解压并命名为 `stock_defconfig`，提交到 [`config/`](config/) 目录。

构建流程会自动检测并应用该文件；不存在时会跳过，不需要额外开关。

## App

ABK App 使用 Material 3 Expressive 风格设计，面向手机端完成完整构建闭环：

- 启动后检查 Root 权限。
- 使用 GitHub Device Flow 登录并请求用户确认授权。
- 检查用户是否 fork 了本仓库，必要时创建 fork。
- 检查 fork 是否落后上游，并提示同步。
- 根据当前内核版本生成推荐构建参数。
- 触发 GitHub Actions 工作流并同步进度。
- 构建完成后下载产物并提供刷写/安装入口。

App 编译由 [`Build ABK App`](.github/workflows/build-abk-app.yml) 工作流完成。

## 致谢

ABK 基于以下项目、仓库和社区工作继续开发。这里集中列出所有在 README、网页、工作流或产物说明中引用到的主要仓库和项目：

- 上游仓库：[zzh20188/GKI_KernelSU_SUSFS](https://github.com/zzh20188/GKI_KernelSU_SUSFS)
- KernelSU：[tiann/KernelSU](https://github.com/tiann/KernelSU)
- KernelSU Next：[KernelSU-Next/KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next)
- SukiSU Ultra：[SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)
- ReSukiSU：[ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU)
- SUSFS：[simonpunk/susfs4ksu](https://gitlab.com/simonpunk/susfs4ksu)
- SUSFS GitHub 镜像/补丁来源：[ShirkNeko/susfs4ksu](https://github.com/ShirkNeko/susfs4ksu)
- SukiSU patch：[ShirkNeko/SukiSU_patch](https://github.com/ShirkNeko/SukiSU_patch)
- AnyKernel3：[WildKernels/AnyKernel3](https://github.com/WildKernels/AnyKernel3)
- Kernel patches：[WildKernels/kernel_patches](https://github.com/WildKernels/kernel_patches)
- Action-Build：[Numbersf/Action-Build](https://github.com/Numbersf/Action-Build)
- SUSFS 模块构建来源：[sidex15/susfs4ksu-module](https://github.com/sidex15/susfs4ksu-module)
- GCC prebuilts：[LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1](https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1)
- Baseband Guard：[vc-teahouse/Baseband-guard](https://github.com/vc-teahouse/Baseband-guard)
- Re-Kernel：[Sakion-Team/Re-Kernel](https://github.com/Sakion-Team/Re-Kernel)
- KernelSU 官方站点：https://kernelsu.org/

## License

本仓库包含多个第三方项目、补丁和构建产物引用。使用、分发或修改前请分别遵守对应上游项目的许可证和使用条款。
