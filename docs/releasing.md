# Releasing

This project's version lives in three places: the `CHANGELOG.md` section
header, an annotated git tag, and `pom.xml`. The git tag is authoritative —
`git-commit-id-maven-plugin` extracts it at build time, and `VersionInfo`
reports it through `-V` / `--version`. `pom.xml` carries the last released
version so that a build made without git (a source tarball, say) can still
name itself; it is the fallback, never the source of truth. There is no
separate jar artifact to publish — the wrapper script runs from
`target/classes`.

Two version strings are derived from this. `VersionInfo.version()` is the
precise build identity (`0.5.0-5-g0f2128e-dirty`) and goes into the
remediation log; `VersionInfo.releaseVersion()` names the release alone
(`0.5.0`) and goes into the `/Creator` entry of stamped PDFs.

## Cutting a release

1. **Review what landed.** Skim `git log <previous-tag>..HEAD` against the
   `## Unreleased` section of `CHANGELOG.md` and make sure every user-visible
   change is represented.

2. **Roll the changelog.** Move everything under `## Unreleased` into a new
   dated section, leaving a fresh empty `## Unreleased` on top, using the
   release date:

   ```
   ## Unreleased

   ## [0.4.0] - 2026-06-04

   ### Added
   ... (former Unreleased contents)
   ```

3. **Bump `pom.xml`** to the same version, so a git-less build still names
   the right release:

   ```xml
   <version>0.4.0</version>
   ```

4. **Commit the changelog and the pom** in the established style:

   ```bash
   git add CHANGELOG.md pom.xml
   git commit -m "docs(CHANGELOG): Cut 0.4.0 release"
   ```

5. **Create an annotated tag**, message `Revision X.Y.Z`. Back-date it to the
   release date if the tag is being created later, so the tag date matches the
   changelog date:

   ```bash
   GIT_COMMITTER_DATE="2026-06-04T16:00:00-07:00" \
     git tag -a v0.4.0 -m "Revision 0.4.0"
   ```

   Drop the `GIT_COMMITTER_DATE` prefix to stamp the tag with the current time;
   only the changelog date is reader-visible.

6. **Push** the commit and the tag:

   ```bash
   git push && git push origin v0.4.0
   ```

## Conventions at a glance

- Changelog section header: `## [X.Y.Z] - YYYY-MM-DD` (the release date).
- `pom.xml` `<version>`: the same `X.Y.Z`, bumped in the changelog commit.
- Tag: annotated (`git tag -a`), named `vX.Y.Z`, message `Revision X.Y.Z`.
- Changelog commit message: `docs(CHANGELOG): Cut X.Y.Z release`.
- Versioning follows Semantic Versioning.
