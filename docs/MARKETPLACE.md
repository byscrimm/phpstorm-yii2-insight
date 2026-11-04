# JetBrains Marketplace submission kit — Yii2 Insight 1.0.0

This is prepared listing content, not evidence that the plugin has been submitted or approved.
The maintainer performs the Marketplace upload.

| Field | Prepared value |
| --- | --- |
| Name | Yii2 Insight |
| Vendor | by_scrimm |
| Plugin ID | io.github.byscrimm.yii2insight |
| Summary | Yii2 completion, relation navigation/refactoring, PHPDoc assistance and widget model inference for PhpStorm. |
| Version | 1.0.0 |
| Compatibility | PhpStorm 2026.2.2 (262.10315.130) through 2026.2.x |
| License | BSD-3-Clause; use the preserved LICENSE.md text |
| Source / homepage | https://github.com/byscrimm/phpstorm-yii2-insight |
| Support | https://github.com/byscrimm/phpstorm-yii2-insight/issues |
| Suggested tags | PHP, Yii2, Code completion, Navigation, Refactoring |
| Description | Copy MARKETPLACE_DESCRIPTION.html; the same HTML is embedded in the plugin descriptor |

## Getting Started text

Install Yii2 Insight and configure Settings → PHP → Yii2 Insight for your project.
Disable conflicting Yii2 Support plugins; legacy settings are not automatically imported.
With Yii2 sources indexed, try completion in `City::find()->with('')`, navigate a relation
segment to its getter, and use Find Usages from the getter or PHPDoc property. Start Rename
from the declaration rather than the string. Use Alt+Enter to add missing relation PHPDoc.
For GridView, configure an ActiveDataProvider with a supported model query; completion uses
that model for columns and the first value callback parameter. Database features need an
IDE datasource, and migration commands must be configured and explicitly invoked.

## Release notes text

Use the 1.0.0 section of CHANGELOG.md. Do not include the historical local-development
version sequence as if it were prior public Yii2 Insight releases.

## Upload materials and checks

- Installable final release ZIP and checksum from the GitHub Release, not a source archive.
- Description HTML, preserved license, public source and issue links above.
- Screenshots from the disposable test project: relation completion, Find Usages/Rename
  preview, PHPDoc quick fix, GridView callback completion. Do not use customer code.
- The packaged icon is inherited Yii artwork. Its provenance is recorded in ASSET_AUDIT.md;
  confirm framework-mark permissions before submitting the Marketplace listing.
- Do not claim Marketplace approval or invent a plugin-page URL before one is assigned.

Official references, consulted 2026-09-09:
- https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html
- https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html
- https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html
