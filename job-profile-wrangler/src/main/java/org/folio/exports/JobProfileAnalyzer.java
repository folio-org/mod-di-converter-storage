package org.folio.exports;

import org.folio.graph.ProfileDepthFirstIterator;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.*;
import org.jgrapht.Graph;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes job profiles to determine required inventory fields and match criteria.
 *
 * This class leverages the existing ProfileDepthFirstIterator to traverse job profile graphs
 * and extract the minimal requirements needed for successful job profile execution.
 */
public class JobProfileAnalyzer {

  private static final Logger LOGGER = LogManager.getLogger(JobProfileAnalyzer.class);

  private static final Set<String> VALID_FOLIO_RECORD_TYPES = Set.of(
    "INSTANCE", "HOLDINGS", "ITEM", "MARC_BIBLIOGRAPHIC", "MARC_AUTHORITY", "MARC_HOLDINGS"
  );

  private static final Set<String> CREATE_UPDATE_ACTIONS = Set.of("CREATE", "UPDATE", "MODIFY", "DELETE");

  private final MappingRulesProcessor mappingRulesProcessor;

  public JobProfileAnalyzer(MappingRulesProcessor mappingRulesProcessor) {
    this.mappingRulesProcessor = Objects.requireNonNull(mappingRulesProcessor, "MappingRulesProcessor cannot be null");
  }

  /**
   * Analyzes a job profile graph to determine all requirements for successful execution.
   *
   * @param jobProfileGraph the job profile graph to analyze
   * @return comprehensive analysis result with all required fields and criteria
   */
  public JobProfileAnalysisResult analyzeJobProfile(Graph<Profile, RegularEdge> jobProfileGraph) {
    if (jobProfileGraph == null || jobProfileGraph.vertexSet().isEmpty()) {
      return createEmptyResult();
    }

    Set<String> requiredInventoryFields = getRequiredInventoryFields(jobProfileGraph);
    Set<MatchCriteria> matchCriteria = getMatchCriteria(jobProfileGraph);
    List<JobProfilePath> allPaths = getAllPaths(jobProfileGraph);
    Set<String> optimizedRequiredFields = optimizeRequiredFields(requiredInventoryFields, allPaths);
    Map<String, Set<String>> fieldOperations = getFieldOperations(jobProfileGraph);

    return new JobProfileAnalysisResult(
      requiredInventoryFields,
      matchCriteria,
      allPaths,
      optimizedRequiredFields,
      fieldOperations
    );
  }

  /**
   * Extracts required inventory fields from action profiles in the job profile graph.
   *
   * This method returns actual inventory fields (e.g., "title", "contributors.name")
   * by analyzing the job profile to determine record types and then using the
   * MappingRulesProcessor to extract target inventory fields.
   *
   * @param jobProfileGraph the job profile graph to analyze
   * @return set of actual inventory fields that will be affected
   */
  public Set<String> getRequiredInventoryFields(Graph<Profile, RegularEdge> jobProfileGraph) {
    // First, get the record types from action profiles
    Set<String> recordTypes = extractRecordTypesFromActionProfiles(jobProfileGraph);

    if (recordTypes.isEmpty()) {
      return Collections.emptySet();
    }

    // Use MappingRulesProcessor to get actual inventory fields
    Set<String> actualFields = mappingRulesProcessor.analyzeMappingRules(recordTypes);
    LOGGER.info("Extracted {} actual inventory fields from {} record types",
               actualFields.size(), recordTypes.size());
    return actualFields;
  }

  /**
   * Extracts record types from action profiles in the job profile graph.
   * This is a helper method used internally to determine which record types are affected.
   *
   * @param jobProfileGraph the job profile graph to analyze
   * @return set of record types found in action profiles
   */
  private Set<String> extractRecordTypesFromActionProfiles(Graph<Profile, RegularEdge> jobProfileGraph) {
    Set<String> recordTypes = new HashSet<>();

    for (Profile profile : jobProfileGraph.vertexSet()) {
      if (profile instanceof ActionProfileNode actionProfile) {
        String folioRecord = actionProfile.folioRecord();
        if (isValidFolioRecordType(folioRecord) && isCreateOrUpdateAction(actionProfile.action())) {
          recordTypes.add(folioRecord);
        }
      }
    }

    return recordTypes;
  }

  /**
   * Extracts match criteria from match profiles in the job profile graph.
   *
   * @param jobProfileGraph the job profile graph to analyze
   * @return set of match criteria for the job profile
   */
  public Set<MatchCriteria> getMatchCriteria(Graph<Profile, RegularEdge> jobProfileGraph) {
    Set<MatchCriteria> matchCriteria = new HashSet<>();

    for (Profile profile : jobProfileGraph.vertexSet()) {
      if (profile instanceof MatchProfileNode matchProfile) {
        MatchCriteria criteria = new MatchCriteria(
          matchProfile.incomingRecordType(),
          matchProfile.existingRecordType()
        );
        matchCriteria.add(criteria);
      }
    }

    return matchCriteria;
  }

  /**
   * Analyzes field operations for each record type in the job profile.
   *
   * @param jobProfileGraph the job profile graph to analyze
   * @return map of record types to their operations
   */
  public Map<String, Set<String>> getFieldOperations(Graph<Profile, RegularEdge> jobProfileGraph) {
    Map<String, Set<String>> fieldOperations = new HashMap<>();

    for (Profile profile : jobProfileGraph.vertexSet()) {
      if (profile instanceof ActionProfileNode actionProfile) {
        String folioRecord = actionProfile.folioRecord();
        String action = actionProfile.action();

        if (isValidFolioRecordType(folioRecord)) {
          fieldOperations.computeIfAbsent(folioRecord, k -> new HashSet<>()).add(action);
        }
      }
    }

    return fieldOperations;
  }

  /**
   * Discovers all possible execution paths through the job profile graph.
   * Uses recursive traversal to find all paths from job profile starting points.
   *
   * @param jobProfileGraph the job profile graph to analyze
   * @return list of all possible paths through the graph
   */
  private List<JobProfilePath> getAllPaths(Graph<Profile, RegularEdge> jobProfileGraph) {
    List<JobProfilePath> paths = new ArrayList<>();

    // Find all job profile nodes (starting points)
    Set<Profile> jobProfiles = jobProfileGraph.vertexSet().stream()
      .filter(profile -> profile instanceof JobProfileNode)
      .collect(Collectors.toSet());

    // Generate all possible paths from each job profile
    for (Profile jobProfile : jobProfiles) {
      List<Profile> currentPath = new ArrayList<>();
      Set<Profile> visited = new HashSet<>();
      discoverAllPaths(jobProfileGraph, jobProfile, currentPath, paths, visited);
    }

    return paths;
  }

  /**
   * Recursively discovers all possible paths through the graph using efficient backtracking.
   * This approach properly manages visited nodes and path state.
   */
  private void discoverAllPaths(Graph<Profile, RegularEdge> graph, Profile currentNode,
                               List<Profile> currentPath, List<JobProfilePath> allPaths,
                               Set<Profile> visited) {
    if (visited.contains(currentNode)) {
      return; // Avoid cycles
    }

    // Add current node to path and visited set
    visited.add(currentNode);
    currentPath.add(currentNode);

    Set<RegularEdge> outgoingEdges = graph.outgoingEdgesOf(currentNode);
    if (outgoingEdges.isEmpty()) {
      // End of path - create JobProfilePath with copy of current path
      allPaths.add(new JobProfilePath(new ArrayList<>(currentPath)));
    } else {
      // Traverse all outgoing edges (handles both single and multiple paths)
      for (RegularEdge edge : outgoingEdges) {
        Profile nextNode = (Profile) edge.getTarget();
        discoverAllPaths(graph, nextNode, currentPath, allPaths, visited);
      }
    }

    // Backtrack: remove current node from path and visited set
    currentPath.remove(currentPath.size() - 1);
    visited.remove(currentNode);
  }

  /**
   * Analyzes profile types in the job profile graph using ProfileDepthFirstIterator.
   *
   * @param jobProfileGraph the job profile graph to analyze
   * @return map of profile types to their counts
   */
  public Map<String, Integer> getProfileTypeCounts(Graph<Profile, RegularEdge> jobProfileGraph) {
    Map<String, Integer> profileTypeCounts = new HashMap<>();

    // Find all job profile nodes (starting points)
    Set<Profile> jobProfiles = jobProfileGraph.vertexSet().stream()
      .filter(profile -> profile instanceof JobProfileNode)
      .collect(Collectors.toSet());

    // Analyze profile types using ProfileDepthFirstIterator
    for (Profile jobProfile : jobProfiles) {
      ProfileDepthFirstIterator iterator = new ProfileDepthFirstIterator(jobProfileGraph, jobProfile);

      while (iterator.hasNext()) {
        Profile profile = iterator.next();
        String profileType = profile.getName();
        profileTypeCounts.merge(profileType, 1, Integer::sum);
      }
    }

    return profileTypeCounts;
  }

  /**
   * Optimizes required fields by determining the minimal set needed across all paths.
   *
   * @param requiredFields the full set of required fields
   * @param allPaths all discovered paths through the job profile
   * @return optimized set of required fields
   */
  private Set<String> optimizeRequiredFields(Set<String> requiredFields, List<JobProfilePath> allPaths) {
    // For now, return all required fields
    // In future iterations, this could implement more sophisticated optimization
    // such as determining fields that are required by all paths vs. optional fields
    return new HashSet<>(requiredFields);
  }

  /**
   * Validates if a record type is a valid FOLIO record type.
   */
  private boolean isValidFolioRecordType(String recordType) {
    return recordType != null && VALID_FOLIO_RECORD_TYPES.contains(recordType);
  }

  /**
   * Checks if an action is a create, update, or delete operation.
   */
  private boolean isCreateOrUpdateAction(String action) {
    return action != null && CREATE_UPDATE_ACTIONS.contains(action.toUpperCase());
  }

  /**
   * Creates an empty analysis result for empty or null graphs.
   */
  private JobProfileAnalysisResult createEmptyResult() {
    return new JobProfileAnalysisResult(
      Collections.emptySet(),
      Collections.emptySet(),
      Collections.emptyList(),
      Collections.emptySet(),
      Collections.emptyMap()
    );
  }
}