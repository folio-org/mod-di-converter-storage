package org.folio.exports;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.folio.profile.ProfileTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Extracts executable CREATE, UPDATE, DELETE, and unsupported action paths from
 * a normalized profile tree.
 */
public class ProfilePathExtractor {
  private static final Logger LOGGER = LogManager.getLogger(ProfilePathExtractor.class);

  private final boolean verbose;

  public ProfilePathExtractor(boolean verbose) {
    this.verbose = verbose;
  }

  public PathExtractionResult extractAllPaths(ProfileTree tree) {
    List<CategorizedPath> createPaths = new ArrayList<>();
    List<CategorizedPath> updatePaths = new ArrayList<>();
    List<CategorizedPath> deletePaths = new ArrayList<>();
    List<CategorizedPath> unsupportedActionPaths = new ArrayList<>();

    extractPathsWithOutcome(tree.root(), new ArrayList<>(), ReactTo.NONE, null, MatchCriteria.empty(),
      createPaths, updatePaths, deletePaths, unsupportedActionPaths);

    return new PathExtractionResult(createPaths, updatePaths, deletePaths, unsupportedActionPaths);
  }

  public CategorizedPaths categorizePaths(PathExtractionResult pathResult) {
    List<CategorizedPath> createPaths = coalesceSiblingCreateStacks(pathResult.createPaths());
    List<MatchedPathPair> pairs = pairPaths(createPaths, pathResult.updatePaths());

    Set<CategorizedPath> pairedCreatePaths = new HashSet<>();
    Set<CategorizedPath> pairedUpdatePaths = new HashSet<>();

    for (MatchedPathPair pair : pairs) {
      pairedCreatePaths.add(pair.createPath());
      pairedUpdatePaths.add(pair.updatePath());
    }

    List<CategorizedPath> unpairedCreate = createPaths.stream()
      .filter(p -> !pairedCreatePaths.contains(p))
      .toList();

    List<CategorizedPath> unpairedUpdate = pathResult.updatePaths().stream()
      .filter(p -> !pairedUpdatePaths.contains(p))
      .toList();

    return new CategorizedPaths(pairs, unpairedCreate, unpairedUpdate, pathResult.deletePaths());
  }

  private void extractPathsWithOutcome(
      ProfileTree.ProfileTreeNode node,
      List<Profile> currentPath,
      ReactTo currentReactTo,
      String currentMatchProfileId,
      MatchCriteria currentMatchCriteria,
      List<CategorizedPath> createPaths,
      List<CategorizedPath> updatePaths,
      List<CategorizedPath> deletePaths,
      List<CategorizedPath> unsupportedActionPaths) {

    if (verbose) {
      LOGGER.info("Processing profile tree node - contentType: '{}', childCount: {}",
        node.contentType(), node.children().size());
    }

    Optional<Profile> profileOpt = node.toProfile(ProfileTree.ProfileIdSource.CONTENT);
    if (profileOpt.isPresent()) {
      Profile profile = profileOpt.get();
      currentPath.add(profile);
      if (verbose) {
        LOGGER.info("Traversing profile: {} (type: {})", profile.getName(), profile.getClass().getSimpleName());
      }

      if (profile instanceof MatchProfileNode match) {
        currentMatchProfileId = match.id();
        currentMatchCriteria = mergeMatchCriteria(currentMatchCriteria, extractMatchCriteria(node.content()));
        if (verbose && !currentMatchCriteria.isEmpty()) {
          LOGGER.info("Extracted match criteria from match profile {}: {} MARC field(s), {} non-MARC match(es)",
            currentMatchProfileId,
            currentMatchCriteria.matchFields().size(),
            currentMatchCriteria.nonMarcMatches().size());
        }
      }
    }

    if (!node.children().isEmpty()) {
      for (ProfileTree.ProfileTreeNode child : node.children()) {
        ReactTo childReactTo = switch (child.reactTo()) {
          case "MATCH" -> ReactTo.MATCH;
          case "NON_MATCH" -> ReactTo.NON_MATCH;
          default -> currentReactTo;
        };

        extractPathsWithOutcome(child, new ArrayList<>(currentPath), childReactTo, currentMatchProfileId,
          currentMatchCriteria, createPaths, updatePaths, deletePaths, unsupportedActionPaths);
      }
      return;
    }

    Optional<ActionProfileNode> actionOpt = new JobProfilePath(currentPath).lastAction();
    if (actionOpt.isEmpty() || currentPath.isEmpty()) {
      return;
    }

    ActionProfileNode action = actionOpt.get();
    JobProfilePath path = new JobProfilePath(new ArrayList<>(currentPath));
    CategorizedPath categorizedPath = new CategorizedPath(path, currentReactTo, currentMatchProfileId, currentMatchCriteria);

    if ("CREATE".equals(action.action())) {
      createPaths.add(categorizedPath);
    } else if (isUpdateLikeAction(action)) {
      updatePaths.add(categorizedPath);
    } else if ("DELETE".equals(action.action()) && "MARC_AUTHORITY".equals(action.folioRecord())) {
      deletePaths.add(categorizedPath);
    } else {
      unsupportedActionPaths.add(categorizedPath);
    }
  }

  public MatchCriteria mergeMatchCriteria(MatchCriteria inherited, MatchCriteria current) {
    if (inherited == null || inherited.isEmpty()) {
      return current == null ? MatchCriteria.empty() : current;
    }
    if (current == null) {
      return inherited;
    }
    if (current.isEmpty()) {
      return new MatchCriteria(current.matchProfileId(), inherited.matchFields(), inherited.nonMarcMatches());
    }

    List<MatchCriteria.MatchFieldSpec> matchFields = new ArrayList<>();
    matchFields.addAll(inherited.matchFields());
    matchFields.addAll(current.matchFields());

    List<MatchCriteria.NonMarcMatchSpec> nonMarcMatches = new ArrayList<>();
    nonMarcMatches.addAll(inherited.nonMarcMatches());
    nonMarcMatches.addAll(current.nonMarcMatches());

    return new MatchCriteria(current.matchProfileId(), matchFields, nonMarcMatches);
  }

  public MatchCriteria extractMatchCriteria(JsonNode matchProfileContent) {
    String matchProfileId = matchProfileContent.path("id").asText();
    List<MatchCriteria.MatchFieldSpec> matchFields = new ArrayList<>();
    List<MatchCriteria.NonMarcMatchSpec> nonMarcMatches = new ArrayList<>();

    JsonNode matchDetails = matchProfileContent.path("matchDetails");
    if (!matchDetails.isArray()) {
      return MatchCriteria.empty();
    }

    for (JsonNode matchDetail : matchDetails) {
      JsonNode incomingExpr = matchDetail.path("incomingMatchExpression");
      JsonNode existingExpr = matchDetail.path("existingMatchExpression");

      String incomingDataType = incomingExpr.path("dataValueType").asText();
      String existingDataType = existingExpr.path("dataValueType").asText();

      if ("VALUE_FROM_RECORD".equals(incomingDataType)) {
        JsonNode fields = incomingExpr.path("fields");
        if (fields.isArray() && !fields.isEmpty()) {
          MatchCriteria.MatchFieldSpec spec = parseFieldsToMatchSpec(fields, null);
          if (spec != null) {
            matchFields.add(spec);
          }
        }
      } else if ("STATIC_VALUE".equals(incomingDataType)) {
        String staticValue = incomingExpr.path("staticValueDetails").path("text").asText(null);
        JsonNode fields = incomingExpr.path("fields");
        if (fields.isArray() && !fields.isEmpty()) {
          MatchCriteria.MatchFieldSpec spec = parseFieldsToMatchSpec(fields, staticValue);
          if (spec != null) {
            matchFields.add(spec);
          }
        }
      }

      if ("VALUE_FROM_RECORD".equals(existingDataType)) {
        JsonNode existingFields = existingExpr.path("fields");
        if (existingFields.isArray() && !existingFields.isEmpty()) {
          String existingField = extractExistingFieldPath(existingFields);
          if (existingField != null && !existingField.startsWith("marc") && !looksLikeMarcField(existingField)) {
            JsonNode incomingFields = incomingExpr.path("fields");
            if (incomingFields.isArray() && !incomingFields.isEmpty()) {
              MatchCriteria.MatchFieldSpec incomingSpec = parseFieldsToMatchSpec(incomingFields, null);
              if (incomingSpec != null) {
                nonMarcMatches.add(new MatchCriteria.NonMarcMatchSpec(
                  existingField,
                  incomingSpec.fieldTag(),
                  incomingSpec.subfieldCode(),
                  incomingSpec.indicator1(),
                  incomingSpec.indicator2()
                ));
              }
            }
          }
        }
      }
    }

    return new MatchCriteria(matchProfileId, matchFields, nonMarcMatches);
  }

  public String extractExistingFieldPath(JsonNode fields) {
    StringBuilder path = new StringBuilder();
    for (JsonNode field : fields) {
      String value = field.path("value").asText("");
      if (!value.isEmpty()) {
        if (path.length() > 0) {
          path.append(".");
        }
        path.append(value);
      }
    }
    return path.length() > 0 ? path.toString() : null;
  }

  private boolean looksLikeMarcField(String field) {
    return field != null && field.matches("\\d{3}([$.].*)?");
  }

  private MatchCriteria.MatchFieldSpec parseFieldsToMatchSpec(JsonNode fields, String staticValue) {
    String fieldTag = null;
    String indicator1 = null;
    String indicator2 = null;
    String subfieldCode = null;

    for (JsonNode field : fields) {
      String label = field.path("label").asText("");
      String value = field.path("value").asText("");

      switch (label) {
        case "field" -> fieldTag = value;
        case "indicator1" -> indicator1 = value;
        case "indicator2" -> indicator2 = value;
        case "recordSubfield" -> subfieldCode = value;
      }
    }

    if (fieldTag == null || fieldTag.isBlank()) {
      return null;
    }

    return new MatchCriteria.MatchFieldSpec(fieldTag, indicator1, indicator2, subfieldCode, staticValue);
  }

  private List<MatchedPathPair> pairPaths(List<CategorizedPath> createPaths, List<CategorizedPath> updatePaths) {
    List<MatchedPathPair> pairs = new ArrayList<>();

    for (CategorizedPath createCatPath : createPaths) {
      if (createCatPath.reactTo() == ReactTo.NON_MATCH && createCatPath.matchProfileId() != null) {
        String matchProfileId = createCatPath.matchProfileId();
        for (CategorizedPath updateCatPath : updatePaths) {
          if (updateCatPath.reactTo() == ReactTo.MATCH && matchProfileId.equals(updateCatPath.matchProfileId())) {
            pairs.add(new MatchedPathPair(createCatPath, updateCatPath, matchProfileId));
            break;
          }
        }
      }
    }

    return pairs;
  }

  private List<CategorizedPath> coalesceSiblingCreateStacks(List<CategorizedPath> createPaths) {
    Map<String, List<CategorizedPath>> groups = new java.util.LinkedHashMap<>();
    List<CategorizedPath> passthrough = new ArrayList<>();
    for (CategorizedPath path : createPaths) {
      if ((path.reactTo() == ReactTo.MATCH || path.reactTo() == ReactTo.NON_MATCH)
          && path.matchProfileId() != null && !path.matchProfileId().isBlank()) {
        groups.computeIfAbsent(path.reactTo() + ":" + path.matchProfileId(), key -> new ArrayList<>()).add(path);
      } else {
        passthrough.add(path);
      }
    }

    List<CategorizedPath> coalesced = new ArrayList<>();
    for (List<CategorizedPath> group : groups.values()) {
      if (group.size() == 1) {
        coalesced.add(group.get(0));
      } else {
        coalesced.add(consolidateCategorizedCreatePaths(group));
      }
    }
    coalesced.addAll(passthrough);
    return coalesced;
  }

  private CategorizedPath consolidateCategorizedCreatePaths(List<CategorizedPath> paths) {
    CategorizedPath first = paths.get(0);
    List<Profile> consolidatedProfiles = new ArrayList<>();
    Set<String> seenProfileIds = new HashSet<>();
    for (CategorizedPath path : paths) {
      for (Profile profile : path.path().getProfiles()) {
        if (seenProfileIds.add(profileKey(profile))) {
          consolidatedProfiles.add(profile);
        }
      }
    }
    return new CategorizedPath(
      new JobProfilePath(consolidatedProfiles),
      first.reactTo(),
      first.matchProfileId(),
      first.matchCriteria());
  }

  private String profileKey(Profile profile) {
    if (profile instanceof JobProfileNode jobProfile) {
      return "JobProfile-" + jobProfile.id();
    }
    if (profile instanceof MatchProfileNode matchProfile) {
      return "MatchProfile-" + matchProfile.id();
    }
    if (profile instanceof ActionProfileNode actionProfile) {
      return "ActionProfile-" + actionProfile.id();
    }
    if (profile instanceof MappingProfileNode mappingProfile) {
      return "MappingProfile-" + mappingProfile.id();
    }
    return profile.getClass().getSimpleName() + "-" + profile.getName();
  }

  private boolean isUpdateLikeAction(ActionProfileNode action) {
    return "UPDATE".equals(action.action())
      || ("MODIFY".equals(action.action()) && "MARC_BIBLIOGRAPHIC".equals(action.folioRecord()));
  }

}
