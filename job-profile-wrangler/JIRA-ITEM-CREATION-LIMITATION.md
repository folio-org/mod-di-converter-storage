# JIRA Ticket: Item Creation Fails Without Holdings in Event Context

## Summary

**CREATE ITEM action fails with "Failed to extract holdingsRecord from payload" when preceded only by MATCH INSTANCE**

## Component

`mod-source-record-manager` (CreateItemEventHandler)

## Affects Version

All current versions

## Description

When a job profile is structured as:

```
MATCH INSTANCE → CREATE ITEM
```

The CREATE ITEM action fails because Holdings are not present in the event payload context.

### Technical Root Cause

1. `MatchInstanceEventHandler` successfully matches an Instance
2. Handler only adds the Instance to the event context: `context.put(EntityType.INSTANCE.value(), instance)`
3. Handler does **not** load related Holdings from the database
4. `CreateItemEventHandler` requires Holdings: `context.get(EntityType.HOLDINGS.value())`
5. Returns `null` → throws error

### Expected Behavior

One of:
- `MatchInstanceEventHandler` loads related Holdings into context
- `CreateItemEventHandler` loads Holdings when Instance is available but Holdings is not
- Clear validation/error message at profile definition time indicating this pattern is unsupported

### Actual Behavior

Import job fails at runtime with error:
```
Failed to extract holdingsRecord from payload
```

## Steps to Reproduce

1. Create a job profile with structure:
   - MATCH INSTANCE (using any valid match criteria)
   - CREATE ITEM (on MATCH path)

2. Import MARC records where:
   - Instance exists in FOLIO
   - Instance has existing Holdings
   - MARC record should trigger the MATCH path

3. Observe error in job execution journal

## Test Profile Example

```
Job Profile: "jp-002 - MATCH INSTANCE → CREATE ITEM"
└── MATCH INSTANCE (on 999ff$i)
    └── [MATCH] → CREATE ITEM
```

## Workaround

Add an intermediate step to populate Holdings in context:

**Option A: Add MATCH HOLDINGS step**
```
MATCH INSTANCE → MATCH HOLDINGS → CREATE ITEM
```

**Option B: Add CREATE HOLDINGS step**
```
MATCH INSTANCE → CREATE HOLDINGS → CREATE ITEM
```

Both options ensure Holdings are present in context before CREATE ITEM executes.

## Proposed Solutions

### Option 1: Cascade-load Holdings on Instance Match (Recommended)

Modify `MatchInstanceEventHandler` to optionally load related Holdings when the next action in the profile is CREATE ITEM or requires Holdings context.

**Pros:**
- Intuitive behavior for users
- Matches user expectation that "matching Instance gives access to its Holdings"

**Cons:**
- May load unnecessary data in some workflows
- Increases complexity of match handler

### Option 2: Lazy-load Holdings in CreateItemEventHandler

If Holdings not in context but Instance is, query for Holdings related to that Instance.

**Pros:**
- Surgical fix in one location
- Doesn't affect other handlers

**Cons:**
- Multiple database calls if creating multiple Items
- May mask other issues where Holdings genuinely should not exist

### Option 3: Profile Validation at Definition Time

Add validation when saving job profiles that warns/prevents patterns that will fail at runtime.

**Pros:**
- Fails fast with clear error
- Educates users about valid patterns

**Cons:**
- Doesn't fix the underlying limitation
- May be overly restrictive for edge cases

## Related Documentation

- Profile Design Constraints: `.claude/docs/job-profiles.md#profile-design-constraints`

## Labels

- data-import
- job-profiles
- event-handlers
- holdings
- items
