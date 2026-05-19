# Profile Shape Validator Rule Catalogue

The v1 validator walks live job-profile snapshot JSON, not repository DOT files. DOT files are useful for planning sweeps, but generation and import validation must evaluate the same snapshot structure consumed by `JpWranglerCli`: wrapper nodes with `contentType`, `content`, `reactTo`, and `childSnapshotWrappers`.

## v1 Block List

### `match-instance-create-item-without-holdings`

- Predicate: block a path where a `MATCH_PROFILE` for `existingRecordType = INSTANCE` is followed on a `MATCH` branch by an `ACTION_PROFILE` with `action = CREATE` and `folioRecord = ITEM` before any `CREATE HOLDINGS` action or `MATCH HOLDINGS` profile appears in that same branch context.
- Allowed variants: `MATCH INSTANCE -> CREATE HOLDINGS -> CREATE ITEM` and `MATCH INSTANCE -> MATCH HOLDINGS -> CREATE ITEM`.
- Source citations: `job-profile-wrangler/JIRA-ITEM-CREATION-LIMITATION.md` and `mod-di-converter-storage/.claude/docs/job-profiles.md`.
- Planning sweep note: a lightweight DOT scan flagged `jp-002.dot`, `jp-008.dot`, and `jp-032.dot` as containing direct `MATCH INSTANCE -> CREATE ITEM` edges. The validator intentionally does not consume DOT; those findings only informed this catalogue.
