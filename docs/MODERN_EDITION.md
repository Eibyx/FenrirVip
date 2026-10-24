# FenrirVip Modern Edition

This work continues PR #3 on `fix/vk-topic-comments-pagination`. It does not merge into main.

## Interface and appearance

The existing Material 3 Expressive implementation is retained. Shared cards now use 16 dp corners, component shapes use a 12/16/24 dp hierarchy, horizontal card spacing is 8 dp, typography uses the system sans-serif family, and the light navigation bar follows the theme background. Existing light/dark/AMOLED modes and preset palettes remain available.

Appearance adds an optional custom tonal accent palette using Material DynamicColors on supported Android 12+ devices. Material generates coordinated light/dark roles from the chosen seed instead of applying an arbitrary accent to every surface. Legacy Fenrir icon, divider and message-bubble attributes are bound to the same Material color roles when a custom palette is enabled, avoiding mixed preset/custom accents. Turning it off restores the existing palette. Android wallpaper palettes remain available through the existing Dynamic theme.

The rune strip and looping animal/symbol in wall headers are local `runes_container`/`pagan_symbol` decorations, populated by `setupPaganContent`; they are separate from `CommunityDetails.cover`, fetched from VK. No remote content is deleted. Appearance can hide local decorations (default), retain/re-enable them, show/hide covers using the existing switch, or use compact community headers with 64 dp avatars and 8 dp padding. Names, avatars, status and content remain accessible.

## Spanish

`values-es/strings.xml` contains 1,450 manually reviewed translations of the main translatable string catalog. `arrays.xml` supplies all 21 translatable option lists; new appearance labels and the previously added topic navigation labels are also localized. The resource check validates 1,520 base resources in total, including printf arguments and array sizes. English and Russian remain available; the existing language selector adds Spanish as value 3. `localeFilters` includes es, so the resources survive APK packaging. Material already includes its own Spanish resources.

This is source-resource coverage, not a claim that every screen has been visually reviewed on a device. VK content, external web pages, diagnostic/server messages and proper product names are not translated by the client.

## Motion and image work

Appearance → Animations offers smooth, reduced and off modes. Android's system animator-disable switch takes precedence. Shared RecyclerViews disable change cross-fades to avoid flashing/rebinding animations; item durations are 180 ms or 80 ms. Off removes list/layout animators, reduced removes layout cascades, and the common MVP fragment uses reduced fades or zero-duration transitions as requested. Native decorative loops stop when hidden or motion is reduced/off.

Community covers are resized to at most the display width before the existing Picasso blur transformation. Hiding a cover cancels its request and clears its background. Compact headers also avoid loading the cover. The shared SVG play/pause animator is created lazily on first use instead of during theme-object initialization. These remove specific unnecessary allocations/animation work; no measured FPS, battery or startup improvement is claimed. No KVM/device is available for representative Android profiling. Reopen other screens after changing motion settings.

## Existing fixes and sessions

DirectAuthIdVKPresenter remains byte-identical to the integrated login fix (SHA256 `fed19e70056512dbf49863323f267d423d27c4877a500bd0c4e77d0dd502ccb4`). Topic pagination, direct page navigation, offset navigation, staged cursor handling, VK execute error handling and their tests are retained. Account/token storage remains in the existing persistent SharedPreferences; this change adds no logout/clear-on-exit behavior. Real VK login, session restoration after process death, and end-to-end LOAD MORE still require an authenticated device test.

## Build, signing and publication

Run `bash gradlew -p pagination-tests test`, `python3 pagination-tests/check-vkscript.py` and `python3 pagination-tests/check-modern-resources.py`.

Run `bash gradlew :app_fenrir:assembleFenrirRelease` with the native AAR prepared, or enable the README's source-native variant. Release keeps R8 minification and resource shrinking enabled. CI builds the pinned native sources with the repository's SDK/NDK/CMake versions; it validates ZIP integrity, application ID, Spanish locale, ABI libraries and R8 mapping output. Without signing credentials the output is **unsigned**, unsuitable for installation until signed; it is an artifact, not a published release.

The `Fenrir Modern Release` workflow supports push and workflow_dispatch (the existing Build workflow checks PRs). Publication requires all four repository Actions secrets:

- `FENRIR_KEYSTORE_BASE64`: base64 of the existing persistent keystore.
- `FENRIR_KEYSTORE_PASSWORD`: keystore password.
- `FENRIR_KEY_ALIAS`: existing signing alias.
- `FENRIR_KEY_PASSWORD`: key password.

Use repository Settings → Secrets and variables → Actions. Do not put values in chat, source files, logs, or PR descriptions. Keep a secure backup of the original key. No release key is generated by this workflow. A signed build must pass apksigner verification before the workflow can publish a pre-release; PR events never publish. Signing material is temporary and removed even on failure.

The current integration cannot list repository secret metadata (HTTP 403), so their absence is not inferred. The workflow tests presence without printing values and explicitly skips publication when incomplete. The existing debug workflow remains separate and does not represent a persistent release identity. Installing over an existing app requires the same signing certificate and a compatible versionCode; do not uninstall merely to bypass a signing mismatch if session/data preservation matters.

## Validation limits

No device screenshots, real VK integration, instrumentation navigation tests, frame-time traces, startup benchmarks or battery measurements are claimed. Review on narrow/wide screens, both themes, Android accessibility font scales and Android 12+ custom palettes before promoting the pre-release to stable. Signing/publication readiness is determined by the actual workflow result, not by a successful unsigned build.
