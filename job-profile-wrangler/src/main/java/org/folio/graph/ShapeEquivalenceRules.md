# Shape Equivalence Rules

Repository identity is graph-shape identity. It deliberately ignores tenant-specific UUIDs,
profile display names, match expression details, and mapping profile field content.

Two graphs are shape-equivalent when all of these rules hold:

- Vertex count is the same. Adding or removing a profile changes the shape.
- Edge count is the same. Adding or removing a relationship changes the shape.
- Each vertex has a matching vertex of the same Java node class.
- Type-level vertex fields match:
  - `JobProfileNode`: `dataType`, `order`
  - `MatchProfileNode`: `incomingRecordType`, `existingRecordType`, `order`
  - `ActionProfileNode`: `action`, `folioRecord`, `order`
  - `MappingProfileNode`: `incomingRecordType`, `existingRecordType`, `order`
- Each edge has a matching edge with the same edge class and equivalent source and target
  vertices. `MATCH`, `NON_MATCH`, and regular links are different shapes.

Examples:

- Same CREATE INSTANCE -> mapping shape with different profile names: equivalent.
- Same MATCH/CREATE nodes but one branch uses `MATCH` and the other uses `NON_MATCH`: not
  equivalent.
- Same nodes and edges but a CREATE ITEM action has a different `order`: not equivalent.
