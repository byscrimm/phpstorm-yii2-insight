# Contributing

Yii2 Insight is maintained through AI-assisted development. Read
[AI_DEVELOPMENT.md](AI_DEVELOPMENT.md) and [AGENTS.md](AGENTS.md) before working on it.
Implementation priorities and current limitations are in [the plan](DEVELOPMENT_PLAN.md).

## Checkout and build

```sh
git clone https://github.com/byscrimm/phpstorm-yii2-insight.git
cd phpstorm-yii2-insight
git switch -c feature/your-topic
```

Use Java 25 and the SDK version in `gradle.properties`. With installed PhpStorm:

```sh
python3 tools/build-offline.py --test --psi-test
python3 tools/verify-offline.py
```

Both tools accept `--ide /path/to/PhpStorm`; `PHPSTORM_HOME` can also select the SDK.
Use `--java-home /path/to/jdk-25` for the offline builder if the bundled runtime
lacks `javac`. For a separate PSI run, set `JAVA_HOME` or use the IDE's bundled JDK.

With access to dependency repositories:

```sh
./gradlew --no-daemon check verifyPlugin buildPlugin
```

`make docker` builds with Java 25, runs Gradle checks and exports the ZIP to
`build/distributions`, with `SHA256SUMS` and HTML/XML test reports under `reports/`.
No host Java or PhpStorm installation is required; the first build downloads its SDK.
Use `make docker OUTPUT_DIR=build/docker-artifacts` for a separate export directory.
For detailed dependency/task logs, use `make docker GRADLE_ARGS=--info`.
Plugin Verifier is a separate Gradle/CI step, not part of that
Docker target. Prepared build paths must be described as unverified until executed.

## Test the behavior being changed

Use `tests/` for pure logic and `psi-tests/` for PHP syntax/reference behavior.
`gradle-tests/` exposes both suites to JUnit. Include an example that failed before
the fix and relevant negative cases. Keep incomplete PHP safe while users type.

The lightweight suites do not test the full PHP index or completion UI. For changes
to index-backed completion, navigation or refactoring, also test a minimal Yii2
project in an activated isolated IDE profile. Record IDE build, scenario and result.
Use a disposable database only if a migration test actually requires execution.

For documentation-only changes, check Markdown links and `git diff --check`.
Do not add tests that merely assert documentation text or mirror implementation.

## Pull requests

Keep changes focused. Explain the user-visible problem, resulting behavior, actual
validation and remaining limitations. Use the PR template. Do not remove upstream
notices or reformat unrelated code. AI-generated code requires the same scrutiny as
any other change; never claim a review or test that did not happen.

For bug reports, include the exact IDE build, plugin/Yii/PHP versions, minimal PHP
example, expected result and actual result. Remove credentials and unrelated client
code from examples and logs.

## Branches, commits and releases

Read [governance](docs/GOVERNANCE.md) and [release operations](docs/RELEASING.md).
Use descriptive branches and Conventional Commit PR titles. Changes enter master
through a checked PR; RCs remain available while IDE verification is pending.
Release workflows create drafts, include actual evidence and never publish to Marketplace.
Stable release preparation requires isolated-IDE evidence for the exact ZIP hash.

Run the policy/tooling/workflow checks in [testing](docs/TESTING.md) when editing
repository automation. See [metrics](docs/METRICS.md) for measured vs unmeasured data.

## Repository services

`.github/dependabot.yml` configures weekly Gradle/Actions dependency PRs once it
reaches the default branch. Automatic merging is disabled.
CI runs tests/verification and stores artifacts. Version tags prepare GitHub release
drafts after eligibility checks; Marketplace uploads are not configured. Repository rules, Issues availability and bot access are
GitHub settings, not effects of adding these files.

The text for the GitHub About field is in [.github/about.txt](.github/about.txt).
Git does not automatically sync that field. An authenticated maintainer can apply
it through the repository UI, or through `gh repo edit --description` using that text.

Remote activation and desired settings: [GitHub setup](docs/GITHUB_SETUP.md).
