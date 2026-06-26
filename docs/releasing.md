# Releasing

This project's version lives in two places only: the `CHANGELOG.md` section
header and an annotated git tag. `pom.xml` stays at `1.0-SNAPSHOT` and is not
bumped per release (nothing reads it yet; see the `--version` task in
`TODO.org`). There is no separate jar artifact to publish — the wrapper script
runs from `target/classes`.

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

3. **Commit the changelog** in the established style:

   ```bash
   git add CHANGELOG.md
   git commit -m "docs(CHANGELOG): Cut 0.4.0 release"
   ```

4. **Create an annotated tag**, message `Revision X.Y.Z`. Back-date it to the
   release date if the tag is being created later, so the tag date matches the
   changelog date:

   ```bash
   GIT_COMMITTER_DATE="2026-06-04T16:00:00-07:00" \
     git tag -a v0.4.0 -m "Revision 0.4.0"
   ```

   Drop the `GIT_COMMITTER_DATE` prefix to stamp the tag with the current time;
   only the changelog date is reader-visible.

5. **Push** the commit and the tag:

   ```bash
   git push && git push origin v0.4.0
   ```

## Conventions at a glance

- Changelog section header: `## [X.Y.Z] - YYYY-MM-DD` (the release date).
- Tag: annotated (`git tag -a`), named `vX.Y.Z`, message `Revision X.Y.Z`.
- Changelog commit message: `docs(CHANGELOG): Cut X.Y.Z release`.
- Versioning follows Semantic Versioning.
