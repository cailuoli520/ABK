# Third-Party Notices

## SukiSU-Ultra `ksud`

- Upstream repository: <https://github.com/SukiSU-Ultra/SukiSU-Ultra>
- Upstream component: `userspace/ksud`
- ABK integration: the APK build workflows compile `ksud` from source during the app build and package the generated binaries into the APK assets for supported Android ABIs.
- Prebuilt `ksud` binaries are not committed to this repository.
- The pinned upstream ref used by the APK build workflows is declared in:
  - `.github/workflows/build-abk-app.yml`
  - `.github/workflows/build-abk-app-dev.yml`

Follow the upstream project's license and notice requirements when using, redistributing, or modifying this integration. See the upstream repository for the authoritative license texts and any additional third-party obligations introduced by that project.
