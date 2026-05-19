# Job Profile Wrangler

Job Profile Wrangler is a CLI for stewarding portable FOLIO Data Import job-profile
shapes and generating deterministic MARC records for testing those shapes.

The repository artifact is a DOT graph (`src/main/resources/repository/jp-NNN.dot`).
Generated MARC files and run reports are per-run outputs; they are not repository
metadata.

## Commands

| Command | Purpose |
| --- | --- |
| `import` | Pull job-profile shapes from a FOLIO tenant into the local DOT repository. |
| `export` | Hydrate a repository shape into a FOLIO tenant. |
| `list` | List repository profile IDs. |
| `visualize` | Render a repository profile as SVG. |
| `generate` | Generate MARC records and a JSON report for a live job profile UUID. |
| `enrich` | Populate generated import records with Instance identifiers/HRIDs after foundation import. |
| `delete` | Delete tenant profiles by name pattern. |

## Generate Outcomes

`generate` always writes `<output>-report.json` after it has fetched the snapshot,
reference data, and mapping rules. MARC files are written only when the outcome allows
them.

| Outcome | Exit | MARC files | Meaning |
| --- | ---: | --- | --- |
| `generated` | 0 | yes | Records were generated and are ready for the documented testing loop. |
| `needs-enrichment` | 2 | yes | Records were generated, but the import file must be enriched before final import. |
| `blocked-unsupported-workflow` | 3 | no | The profile matches a known FOLIO runtime limitation, such as `MATCH INSTANCE -> CREATE ITEM` without Holdings context. |
| `generator-gap` | 4 | no | Wrangler cannot safely translate the profile, usually because required reference data or mapping rules are unavailable. |
| `invalid-profile-shape` | 5 | no | The snapshot has no CREATE/UPDATE execution path Wrangler can generate for. |

Exit `1` is reserved for setup, usage, authentication, network, and unexpected failures.

## Report Shape

The report is JSON with stable outcome labels:

```json
{
  "profileId": "...",
  "profileName": "...",
  "runTimestamp": "2026-05-19T20:00:00Z",
  "overallOutcome": { "type": "generated" },
  "paths": [],
  "referenceData": {
    "locations": { "id": "...", "name": "..." },
    "materialTypes": { "id": "...", "name": "..." },
    "loanTypes": { "id": "...", "name": "..." }
  }
}
```

Consumers should branch on `overallOutcome.type`. Path entries include `pathIndex`,
`pathId`, `reactTo`, `matchProfileId`, `destinationFiles`, `fieldsWritten`, and a
per-path `outcome`.

## Import Reports

`import` writes `import-report-<UTC timestamp>.json` in the repository directory and
prints a tally of added, duplicate, blocked, and error outcomes. The report distinguishes
new shapes from duplicates and refuses known unsupported shapes.

## Workflow

1. Export/import repository shapes as needed.
2. Run `generate <job-profile-uuid> -o test-records ...`.
3. Inspect `test-records-report.json`.
4. If the outcome is `needs-enrichment`, import the foundation file, then run `enrich`
   using the hint in the report.
5. Follow `TESTING-LOOP.md` for the manual verification loop.

Do not run concurrent `generate` invocations against the same `--output` prefix. Use a
unique output prefix per job in CI.

## References

- Plan: `../../docs/plans/2026-05-19-001-feat-job-profile-wrangler-v1-plan.md`
- Brainstorm: `../../docs/brainstorms/Job Profile Wrangler Brainstorm.md`
- Known Item limitation: `JIRA-ITEM-CREATION-LIMITATION.md`
