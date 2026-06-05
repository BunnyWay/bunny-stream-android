# Contributing to Bunny Stream Android

Thanks for helping improve Bunny Stream Android. This SDK is used by developers who need reliable video playback, uploads, camera recording, and Bunny Stream API access in Android apps, so small improvements to clarity, stability, and developer experience matter a lot.

## Good First Contributions

Good places to start:

- README and documentation improvements
- Small bug fixes with clear reproduction steps
- Sample app fixes
- Test coverage for API, uploader, player, recording, or TV behavior
- Better error messages or edge-case handling
- Improvements to CI reliability

If you are unsure whether a change fits, open an issue first and describe the problem you want to solve.

## Development Setup

Requirements:

- Android Studio
- JDK 17
- Android SDK matching the project configuration
- Gradle wrapper from this repository

Useful commands:

```bash
./gradlew testDebugUnitTest --no-daemon --stacktrace
./gradlew :app:assembleDebug --no-daemon
./gradlew detekt --no-daemon
```

For release and publishing behavior, see the existing GitHub Actions workflows and Gradle publishing configuration.

## Project Areas

- `api`: Bunny Stream REST API integration, authentication, generated OpenAPI client, upload helpers
- `player`: Media3/ExoPlayer-based Bunny Stream player
- `recording`: camera recording and upload UI/components
- `tv`: Android TV optimized playback experience
- `app`: demo app and integration examples

Try to keep changes scoped to the package or module they affect.

## Pull Request Guidelines

Before opening a pull request:

- Make sure the change solves one clear problem.
- Add or update tests when behavior changes.
- Update README or module docs when public APIs or setup steps change.
- Keep generated files separate from handwritten logic when practical.
- Avoid unrelated formatting-only changes in large files.
- Verify the sample app still builds if your change touches UI, player, recording, or initialization.

Pull requests should include:

- What changed
- Why the change is needed
- How it was tested
- Any migration notes for SDK users

## API and Compatibility

This repository publishes Android libraries used by external applications. Please be careful with:

- Public API renames or removals
- Behavior changes in upload, playback, authentication, or recording flows
- Minimum SDK changes
- Dependency version upgrades
- Changes to Maven coordinates or publishing metadata

If a change may be breaking, call it out clearly in the PR description.

## Using AI Tools

AI tools are welcome when they help you work faster or improve quality, but contributors remain responsible for the final contribution.

When using AI:

- Review and understand all generated code before submitting it.
- Do not paste access keys, signing keys, customer data, private logs, or other secrets into AI tools.
- Prefer small, reviewable changes over large generated rewrites.
- Mention substantial AI assistance in the PR description when it materially shaped the implementation.
- Make sure generated code follows the existing style and architecture.


## Reporting Bugs

Please use the bug report template and include:

- SDK version
- Android version and device/emulator
- Module affected (`api`, `player`, `recording`, `tv`, or `app`)
- Minimal reproduction steps
- Expected and actual behavior
- Logs, stack traces, or screenshots when useful

Never include Bunny access keys or other secrets in issues.

## Security Issues

Please do not report security vulnerabilities in public issues. See `SECURITY.md` for the private reporting process.

## License

By contributing, you agree that your contribution will be licensed under the MIT License used by this repository.
