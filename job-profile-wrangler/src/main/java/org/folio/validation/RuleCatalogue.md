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

### `match-instance-update-marc-bib`

- Predicate: block a path where a `MATCH_PROFILE` matches incoming `MARC_BIBLIOGRAPHIC` records to existing `INSTANCE` records and a direct child `ACTION_PROFILE` updates `MARC_BIBLIOGRAPHIC` with a `MARC_BIBLIOGRAPHIC -> MARC_BIBLIOGRAPHIC` mapping.
- Stack behavior: `mod-source-record-manager` rejects this shape in `AbstractChunkProcessingService.isNotSupportedJobProfileExists` before raw record chunk processing starts.
- Sweep examples: `jp-019`.

### `marc-mapping-option-missing`

- Predicate: block any MARC-targeting `MAPPING_PROFILE` snapshot whose `content.mappingDetails.recordType` or `content.existingRecordType` is a `MARC_*` record type and whose `content.mappingDetails.marcMappingOption` is absent, null, or blank.
- Allowed variants: inventory mapping profiles may consume MARC input without a `marcMappingOption`; `DELETE MARC_AUTHORITY` mapping profiles also omit it because the stack enum has no delete mapping option and rejects `UPDATE` on delete actions.
- Stack behavior: `MarcRecordModifier` switches on `mappingDetails.marcMappingOption`, so missing options fail at runtime with a null-pointer error.
- Sweep examples: `jp-009`, `jp-022`, `jp-028`, `jp-040`, `jp-052`, and `jp-060`.

### `match-modify-marc-bib`

- Predicate: block a direct `MATCH_PROFILE -> ACTION_PROFILE` edge where the action is `MODIFY` and the target record is `MARC_BIBLIOGRAPHIC`.
- Allowed variants: root-level `MODIFY MARC_BIBLIOGRAPHIC` profiles and non-direct modify branches that DICS accepts.
- Stack behavior: `mod-di-converter-storage` rejects job-profile creation with `Modify action cannot be used right after a Match`.
- Sweep examples: `jp-030`.

### `create-holdings-without-instance-context`

- Predicate: block `ACTION_PROFILE action = CREATE, folioRecord = HOLDINGS` unless the same execution branch already has `CREATE INSTANCE` or a `MATCH INSTANCE` match branch.
- Allowed variants: `CREATE INSTANCE -> CREATE HOLDINGS` and `MATCH INSTANCE` match branches that create holdings after an instance match.
- Stack behavior: `mod-inventory` needs an Instance id from event context or MARC additional subfield `$i` before creating Holdings.
- Sweep examples: `jp-035` and `jp-036`.

### `paired-authority-update-create`

- Predicate: block a `MATCH_PROFILE` for `MARC_AUTHORITY -> MARC_AUTHORITY` that has both a `MATCH -> UPDATE MARC_AUTHORITY` child and a `NON_MATCH -> CREATE AUTHORITY` child.
- Stack behavior: update records must carry `999 ff $s/$i` so the MARC authority matcher can find the foundation SRS record, but `mod-source-record-manager` rejects any incoming authority record with `999 ff $s` or `$i` when the profile contains a `CREATE AUTHORITY` action anywhere in the snapshot.
- Sweep examples: `jp-006` and `jp-042`.

### `multiple-root-update-branches`

- Predicate: block a job profile whose root job-profile node has more than one child branch containing an update-like action (`UPDATE`, or `MODIFY MARC_BIBLIOGRAPHIC`).
- Stack behavior: root branches are not isolated by generated record path; the stack can run multiple root update branches against each incoming record, which can surface as duplicate source-record errors instead of a deterministic branch result.
- Sweep examples: `jp-051`.

### `authority-nonmatch-create-with-999-s-match`

- Predicate: block `MATCH MARC_AUTHORITY -> NON_MATCH CREATE AUTHORITY` when the incoming match expression uses `999 ff $s`.
- Stack behavior: adding `999 ff $s` to exercise the matcher makes authority create invalid, while omitting it causes the generated record to miss the intended match shape and can produce opaque multiple-match runtime errors.
- Sweep examples: `jp-011`.
