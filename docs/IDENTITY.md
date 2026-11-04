# Yii2 Insight identity and transition

The maintainer authorized this independent identity on 2026-09-08.

| Item | Value |
| --- | --- |
| Plugin name | Yii2 Insight |
| Maintainer / vendor | by_scrimm |
| Plugin ID and Java package | io.github.byscrimm.yii2insight |
| GitHub repository | byscrimm/phpstorm-yii2-insight |
| Distribution prefix | yii2-insight |
| Project settings component / file | Yii2 Insight / yii2-insight.xml |
| First stable version being prepared | 1.0.0 |

The GitHub account spelling is byscrimm; the public vendor name is by_scrimm.
The namespace follows reverse-domain Java naming. The ID is distinct from the
upstream IDs; Marketplace registration/approval has not been performed.

## Installation and settings

This is a separate plugin, not an update of com.yii2supportExtended. The descriptor
declares incompatibility with that ID, com.yii2support and the historical com.nvlad.yii2-framework
ID. Do not enable the original and this fork together: their PHP type providers
and completion behavior overlap. No code automatically disables/uninstalls plugins.

Settings start independently. The plugin does not read, migrate or overwrite
legacy yii2settings.xml. Configure Yii2 Insight explicitly in the test project.
Its settings page, actions, index identifiers, inspections, descriptions and view
templates have separate names. Legacy inspection profile choices/suppressions
and custom keyboard mappings are not automatically transferred.

Yii2 Insight starts its own public version sequence at 1.0.0-rc.1, followed by
1.0.0 after release validation. The upstream version sequence is independent.
The earlier 2.0.0-rc.1 identity build was never published; its verification report
remains historical evidence for those exact bytes, not for the renumbered candidate.

The working PhpStorm and its settings are not modified by the build. Installation,
incompatibility handling and settings persistence still require isolated-IDE tests
before being described as verified runtime behavior.

## Authorship and publication

Package ownership does not change authorship. Preserve LICENSE.md, AUTHORS.md,
NOTICE.md, original source credits and Git history. Vxdy and NVlad remain credited;
by_scrimm maintains this fork with AI assistance. No original author's email is
used as the new vendor contact. GitHub is the maintainer's public contact URL.

The inherited icon and other assets still need an origin/license audit before
Marketplace publication. A new ID is not a Marketplace approval or a trademark
clearance. No Marketplace upload or release publication is part of this change.

Descriptor reference: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html
