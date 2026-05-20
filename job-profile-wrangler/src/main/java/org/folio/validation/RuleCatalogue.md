# Profile Shape Validator Rule Catalogue

The v1 validator walks live job-profile snapshot JSON, not repository DOT files. DOT files are useful for planning sweeps, but generation and import validation must evaluate the same snapshot structure consumed by `JpWranglerCli`: wrapper nodes with `contentType`, `content`, `reactTo`, and `childSnapshotWrappers`.

## v1 Block List

### `match-instance-create-item-without-holdings`

- Predicate: block a path where a `MATCH_PROFILE` for `existingRecordType = INSTANCE` is followed on a `MATCH` branch by an `ACTION_PROFILE` with `action = CREATE` and `folioRecord = ITEM` before any `CREATE HOLDINGS` action or `MATCH HOLDINGS` profile appears in that same branch context.
- Allowed variants: `MATCH INSTANCE -> CREATE HOLDINGS -> CREATE ITEM` and `MATCH INSTANCE -> MATCH HOLDINGS -> CREATE ITEM`.
- Source citations: `job-profile-wrangler/JIRA-ITEM-CREATION-LIMITATION.md` and `mod-di-converter-storage/.claude/docs/job-profiles.md`.
- Planning sweep note: a lightweight DOT scan flagged `jp-002.dot`, `jp-008.dot`, and `jp-032.dot` as containing direct `MATCH INSTANCE -> CREATE ITEM` edges. The validator intentionally does not consume DOT; those findings only informed this catalogue.

### `match-profile-empty-match-details`

- Predicate: block any `MATCH_PROFILE` snapshot whose `content.matchDetails` array is present but empty.
- Stack behavior: `data-import-processing-core` expects `matchProfile.matchDetails[0]` when matching, so an empty array fails at runtime with `NoSuchElementException`.
- Sweep examples: `jp-021`, `jp-027`, `jp-029`, `jp-039`, and `jp-054`.

### `marc-mapping-option-missing`

- Predicate: block any MARC-targeting `MAPPING_PROFILE` snapshot whose `content.mappingDetails.recordType` or `content.existingRecordType` is a `MARC_*` record type and whose `content.mappingDetails.marcMappingOption` is absent, null, or blank.
- Allowed variants: inventory mapping profiles may consume MARC input without a `marcMappingOption`; the target record must be MARC for this rule to fire.
- Stack behavior: `MarcRecordModifier` switches on `mappingDetails.marcMappingOption`, so missing options fail at runtime with a null-pointer error.
- Sweep examples: `jp-009`, `jp-022`, `jp-028`, `jp-040`, `jp-052`, and `jp-060`.

### `create-holdings-without-instance-context`

- Predicate: block `ACTION_PROFILE action = CREATE, folioRecord = HOLDINGS` unless the same execution branch already has `CREATE INSTANCE` or a `MATCH INSTANCE` match branch.
- Allowed variants: `CREATE INSTANCE -> CREATE HOLDINGS` and `MATCH INSTANCE` match branches that create holdings after an instance match.
- Stack behavior: `mod-inventory` needs an Instance id from event context or MARC additional subfield `$i` before creating Holdings.
- Sweep examples: `jp-035` and `jp-036`.
