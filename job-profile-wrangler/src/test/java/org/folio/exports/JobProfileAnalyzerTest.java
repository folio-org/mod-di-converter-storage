package org.folio.exports;

import org.folio.graph.ProfileDepthFirstIterator;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.*;
import org.folio.http.FolioClient;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class JobProfileAnalyzerTest {

  @Rule
  public TemporaryFolder tempDir = new TemporaryFolder();

  @Mock
  private FolioClient mockFolioClient;

  @Mock
  private MappingRulesProcessor mockMappingRulesProcessor;

  private JobProfileAnalyzer analyzer;
  private Graph<Profile, RegularEdge> simpleJobProfileGraph;
  private Graph<Profile, RegularEdge> complexJobProfileGraph;
  private Graph<Profile, RegularEdge> multiPathJobProfileGraph;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);

    // Configure mock to return some sample inventory fields
    when(mockMappingRulesProcessor.analyzeMappingRules(any()))
      .thenReturn(Set.of("title", "contributors.name", "identifiers.value"));

    analyzer = new JobProfileAnalyzer(mockMappingRulesProcessor);
    setupSimpleJobProfileGraph();
    setupComplexJobProfileGraph();
    setupMultiPathJobProfileGraph();
  }

  private void setupSimpleJobProfileGraph() {
    simpleJobProfileGraph = new DefaultDirectedGraph<>(RegularEdge.class);

    // Create nodes
    JobProfileNode jobProfile = new JobProfileNode("job1", "Simple Import Job", 0);
    MatchProfileNode matchProfile = new MatchProfileNode("match1", "MARC_BIBLIOGRAPHIC", "INSTANCE", 1);
    ActionProfileNode actionProfile = new ActionProfileNode("action1", "CREATE", "INSTANCE", 2);
    MappingProfileNode mappingProfile = new MappingProfileNode("mapping1", "MARC_BIBLIOGRAPHIC", "INSTANCE", 3);

    // Add nodes to graph
    simpleJobProfileGraph.addVertex(jobProfile);
    simpleJobProfileGraph.addVertex(matchProfile);
    simpleJobProfileGraph.addVertex(actionProfile);
    simpleJobProfileGraph.addVertex(mappingProfile);

    // Add edges
    simpleJobProfileGraph.addEdge(jobProfile, matchProfile, new RegularEdge(jobProfile, matchProfile));
    simpleJobProfileGraph.addEdge(matchProfile, actionProfile, new RegularEdge(matchProfile, actionProfile));
    simpleJobProfileGraph.addEdge(actionProfile, mappingProfile, new RegularEdge(actionProfile, mappingProfile));
  }

  private void setupComplexJobProfileGraph() {
    complexJobProfileGraph = new DefaultDirectedGraph<>(RegularEdge.class);

    // Create nodes for a complex workflow: Instance -> Holdings -> Items
    JobProfileNode jobProfile = new JobProfileNode("job2", "Complex Import Job", 0);
    MatchProfileNode instanceMatch = new MatchProfileNode("match1", "MARC_BIBLIOGRAPHIC", "INSTANCE", 1);
    ActionProfileNode instanceAction = new ActionProfileNode("action1", "UPDATE", "INSTANCE", 2);
    MappingProfileNode instanceMapping = new MappingProfileNode("mapping1", "MARC_BIBLIOGRAPHIC", "INSTANCE", 3);

    ActionProfileNode holdingsAction = new ActionProfileNode("action2", "CREATE", "HOLDINGS", 4);
    MappingProfileNode holdingsMapping = new MappingProfileNode("mapping2", "MARC_BIBLIOGRAPHIC", "HOLDINGS", 5);

    ActionProfileNode itemAction = new ActionProfileNode("action3", "CREATE", "ITEM", 6);
    MappingProfileNode itemMapping = new MappingProfileNode("mapping3", "MARC_BIBLIOGRAPHIC", "ITEM", 7);

    // Add nodes
    complexJobProfileGraph.addVertex(jobProfile);
    complexJobProfileGraph.addVertex(instanceMatch);
    complexJobProfileGraph.addVertex(instanceAction);
    complexJobProfileGraph.addVertex(instanceMapping);
    complexJobProfileGraph.addVertex(holdingsAction);
    complexJobProfileGraph.addVertex(holdingsMapping);
    complexJobProfileGraph.addVertex(itemAction);
    complexJobProfileGraph.addVertex(itemMapping);

    // Add edges for sequential workflow
    complexJobProfileGraph.addEdge(jobProfile, instanceMatch, new RegularEdge(jobProfile, instanceMatch));
    complexJobProfileGraph.addEdge(instanceMatch, instanceAction, new RegularEdge(instanceMatch, instanceAction));
    complexJobProfileGraph.addEdge(instanceAction, instanceMapping, new RegularEdge(instanceAction, instanceMapping));
    complexJobProfileGraph.addEdge(instanceMapping, holdingsAction, new RegularEdge(instanceMapping, holdingsAction));
    complexJobProfileGraph.addEdge(holdingsAction, holdingsMapping, new RegularEdge(holdingsAction, holdingsMapping));
    complexJobProfileGraph.addEdge(holdingsMapping, itemAction, new RegularEdge(holdingsMapping, itemAction));
    complexJobProfileGraph.addEdge(itemAction, itemMapping, new RegularEdge(itemAction, itemMapping));
  }

  private void setupMultiPathJobProfileGraph() {
    multiPathJobProfileGraph = new DefaultDirectedGraph<>(RegularEdge.class);

    // Create nodes for branching workflow
    JobProfileNode jobProfile = new JobProfileNode("job3", "Multi-Path Job", 0);
    MatchProfileNode matchProfile = new MatchProfileNode("match1", "MARC_BIBLIOGRAPHIC", "INSTANCE", 1);

    // Branch 1: Update existing instance
    ActionProfileNode updateAction = new ActionProfileNode("action1", "UPDATE", "INSTANCE", 2);
    MappingProfileNode updateMapping = new MappingProfileNode("mapping1", "MARC_BIBLIOGRAPHIC", "INSTANCE", 3);

    // Branch 2: Create new instance
    ActionProfileNode createAction = new ActionProfileNode("action2", "CREATE", "INSTANCE", 4);
    MappingProfileNode createMapping = new MappingProfileNode("mapping2", "MARC_BIBLIOGRAPHIC", "INSTANCE", 5);

    // Common continuation: Create holdings
    ActionProfileNode holdingsAction = new ActionProfileNode("action3", "CREATE", "HOLDINGS", 6);
    MappingProfileNode holdingsMapping = new MappingProfileNode("mapping3", "MARC_BIBLIOGRAPHIC", "HOLDINGS", 7);

    // Add nodes
    multiPathJobProfileGraph.addVertex(jobProfile);
    multiPathJobProfileGraph.addVertex(matchProfile);
    multiPathJobProfileGraph.addVertex(updateAction);
    multiPathJobProfileGraph.addVertex(updateMapping);
    multiPathJobProfileGraph.addVertex(createAction);
    multiPathJobProfileGraph.addVertex(createMapping);
    multiPathJobProfileGraph.addVertex(holdingsAction);
    multiPathJobProfileGraph.addVertex(holdingsMapping);

    // Add edges for branching workflow
    multiPathJobProfileGraph.addEdge(jobProfile, matchProfile, new RegularEdge(jobProfile, matchProfile));

    // Branch 1 path
    multiPathJobProfileGraph.addEdge(matchProfile, updateAction, new RegularEdge(matchProfile, updateAction));
    multiPathJobProfileGraph.addEdge(updateAction, updateMapping, new RegularEdge(updateAction, updateMapping));

    // Branch 2 path
    multiPathJobProfileGraph.addEdge(matchProfile, createAction, new RegularEdge(matchProfile, createAction));
    multiPathJobProfileGraph.addEdge(createAction, createMapping, new RegularEdge(createAction, createMapping));

    // Common continuation from both branches
    multiPathJobProfileGraph.addEdge(updateMapping, holdingsAction, new RegularEdge(updateMapping, holdingsAction));
    multiPathJobProfileGraph.addEdge(createMapping, holdingsAction, new RegularEdge(createMapping, holdingsAction));
    multiPathJobProfileGraph.addEdge(holdingsAction, holdingsMapping, new RegularEdge(holdingsAction, holdingsMapping));
  }

  @Test
  public void testAnalyzeJobProfile_SimpleWorkflow() {
    JobProfileAnalysisResult result = analyzer.analyzeJobProfile(simpleJobProfileGraph);

    assertNotNull("Analysis result should not be null", result);
    assertNotNull("Required inventory fields should not be null", result.requiredInventoryFields());
    assertNotNull("Match criteria should not be null", result.matchCriteria());

    // Should identify actual inventory fields, not record types
    assertFalse("Should not contain record types",
        result.requiredInventoryFields().contains("INSTANCE"));
    assertTrue("Should contain actual inventory fields",
        result.requiredInventoryFields().contains("title") ||
        result.requiredInventoryFields().contains("contributors.name"));

    // Should have at least one match criteria
    assertFalse("Should have match criteria", result.matchCriteria().isEmpty());
  }

  @Test
  public void testAnalyzeJobProfile_ComplexWorkflow() {
    JobProfileAnalysisResult result = analyzer.analyzeJobProfile(complexJobProfileGraph);

    assertNotNull("Analysis result should not be null", result);

    Set<String> requiredFields = result.requiredInventoryFields();

    // Should identify actual inventory fields, not record types
    assertFalse("Should not contain INSTANCE record type", requiredFields.contains("INSTANCE"));
    assertFalse("Should not contain HOLDINGS record type", requiredFields.contains("HOLDINGS"));
    assertFalse("Should not contain ITEM record type", requiredFields.contains("ITEM"));

    // Should contain actual inventory fields
    assertTrue("Should contain title field", requiredFields.contains("title"));
    assertTrue("Should contain contributors.name field", requiredFields.contains("contributors.name"));
  }

  @Test
  public void testAnalyzeJobProfile_MultiPathWorkflow() {
    JobProfileAnalysisResult result = analyzer.analyzeJobProfile(multiPathJobProfileGraph);

    assertNotNull("Analysis result should not be null", result);

    Set<String> requiredFields = result.requiredInventoryFields();

    // Should identify actual inventory fields from all paths
    assertTrue("Should contain actual inventory fields", requiredFields.contains("title"));
    assertTrue("Should contain contributors field", requiredFields.contains("contributors.name"));

    // Should account for all paths
    List<JobProfilePath> paths = result.allPaths();
    assertTrue("Should have multiple paths", paths.size() >= 2);
  }

  @Test
  public void testGetRequiredInventoryFields_ExtractsFromActionProfiles() {
    Set<String> requiredFields = analyzer.getRequiredInventoryFields(complexJobProfileGraph);

    assertNotNull("Required fields should not be null", requiredFields);
    assertFalse("Required fields should not be empty", requiredFields.isEmpty());

    // Should extract actual inventory fields, not record types
    assertTrue("Should include title", requiredFields.contains("title"));
    assertTrue("Should include contributors.name", requiredFields.contains("contributors.name"));
    assertTrue("Should include identifiers.value", requiredFields.contains("identifiers.value"));
  }

  @Test
  public void testGetRequiredInventoryFields_FiltersValidRecordTypes() {
    // Create a graph with both valid and invalid record types
    Graph<Profile, RegularEdge> testGraph = new DefaultDirectedGraph<>(RegularEdge.class);

    JobProfileNode jobProfile = new JobProfileNode("job1", "Test Job", 0);
    ActionProfileNode validAction = new ActionProfileNode("action1", "CREATE", "INSTANCE", 1);
    ActionProfileNode invalidAction = new ActionProfileNode("action2", "CREATE", "INVALID_TYPE", 2);

    testGraph.addVertex(jobProfile);
    testGraph.addVertex(validAction);
    testGraph.addVertex(invalidAction);
    testGraph.addEdge(jobProfile, validAction, new RegularEdge(jobProfile, validAction));
    testGraph.addEdge(jobProfile, invalidAction, new RegularEdge(jobProfile, invalidAction));

    Set<String> requiredFields = analyzer.getRequiredInventoryFields(testGraph);

    // Should include actual inventory fields
    assertTrue("Should include actual inventory fields", requiredFields.contains("title"));

    // Should not include any record types
    assertFalse("Should not include record types", requiredFields.contains("INSTANCE"));
    assertFalse("Should not include invalid record types", requiredFields.contains("INVALID_TYPE"));
  }

  @Test
  public void testGetMatchCriteria_ExtractsFromMatchProfiles() {
    Set<MatchCriteria> matchCriteria = analyzer.getMatchCriteria(simpleJobProfileGraph);

    assertNotNull("Match criteria should not be null", matchCriteria);
    assertFalse("Match criteria should not be empty", matchCriteria.isEmpty());

    // Should have criteria from match profiles
    MatchCriteria criteria = matchCriteria.iterator().next();
    assertNotNull("Match criteria should have incoming record type", criteria.incomingRecordType());
    assertNotNull("Match criteria should have existing record type", criteria.existingRecordType());
  }

  @Test
  public void testGetMatchCriteria_HandlesMultipleMatchProfiles() {
    // Add another match profile to the complex graph with different record types
    MatchProfileNode additionalMatch = new MatchProfileNode("match2", "MARC_BIBLIOGRAPHIC", "HOLDINGS", 8);
    complexJobProfileGraph.addVertex(additionalMatch);

    Set<MatchCriteria> matchCriteria = analyzer.getMatchCriteria(complexJobProfileGraph);

    // Should have exactly 2 match criteria: one for INSTANCE and one for HOLDINGS
    // (The original complex graph has 1 match profile for INSTANCE, we added 1 for HOLDINGS)
    assertEquals("Should have exactly 2 match criteria", 2, matchCriteria.size());
  }

  @Test
  public void testAnalyzeJobProfile_OptimizedFieldsIsPopulated() {
    JobProfileAnalysisResult result = analyzer.analyzeJobProfile(multiPathJobProfileGraph);

    // Should populate optimized fields (currently same as required fields until optimization is implemented)
    Set<String> optimizedFields = result.optimizedRequiredFields();
    assertNotNull("Optimized fields should not be null", optimizedFields);

    // Should include necessary fields for all paths
    assertFalse("Should have optimized fields", optimizedFields.isEmpty());
  }

  @Test
  public void testAnalyzeJobProfile_HandlesEmptyGraph() {
    Graph<Profile, RegularEdge> emptyGraph = new DefaultDirectedGraph<>(RegularEdge.class);

    JobProfileAnalysisResult result = analyzer.analyzeJobProfile(emptyGraph);

    assertNotNull("Result should not be null for empty graph", result);
    assertTrue("Required fields should be empty for empty graph",
        result.requiredInventoryFields().isEmpty());
    assertTrue("Match criteria should be empty for empty graph",
        result.matchCriteria().isEmpty());
  }

  @Test
  public void testAnalyzeJobProfile_UsesProfileDepthFirstIterator() {
    JobProfileAnalysisResult result = analyzer.analyzeJobProfile(simpleJobProfileGraph);

    // Verify that the traversal respects the order defined by ProfileDepthFirstIterator
    List<JobProfilePath> paths = result.allPaths();
    assertFalse("Should have at least one path", paths.isEmpty());

    JobProfilePath path = paths.get(0);
    List<Profile> profiles = path.getProfiles();

    // Should be in order based on the order attribute
    for (int i = 1; i < profiles.size(); i++) {
      assertTrue("Profiles should be in order",
          profiles.get(i-1).getOrder() <= profiles.get(i).getOrder());
    }
  }

  @Test
  public void testAnalyzeJobProfile_IdentifiesCreateUpdateOperations() {
    JobProfileAnalysisResult result = analyzer.analyzeJobProfile(complexJobProfileGraph);

    Map<String, Set<String>> fieldOperations = result.fieldOperations();
    assertNotNull("Field operations should not be null", fieldOperations);

    // Should identify different operations for different record types
    assertTrue("Should have operations for INSTANCE", fieldOperations.containsKey("INSTANCE"));
    assertTrue("Should have operations for HOLDINGS", fieldOperations.containsKey("HOLDINGS"));
    assertTrue("Should have operations for ITEM", fieldOperations.containsKey("ITEM"));

    // Should identify UPDATE operation for INSTANCE
    assertTrue("Should identify UPDATE operation for INSTANCE",
        fieldOperations.get("INSTANCE").contains("UPDATE"));

    // Should identify CREATE operations for HOLDINGS and ITEM
    assertTrue("Should identify CREATE operation for HOLDINGS",
        fieldOperations.get("HOLDINGS").contains("CREATE"));
    assertTrue("Should identify CREATE operation for ITEM",
        fieldOperations.get("ITEM").contains("CREATE"));
  }

  @Test
  public void testAnalyzeJobProfile_HandlesCycles() {
    // Create a graph with a cycle to test that traversal terminates correctly
    Graph<Profile, RegularEdge> cyclicGraph = new DefaultDirectedGraph<>(RegularEdge.class);

    JobProfileNode jobProfile = new JobProfileNode("job1", "Cyclic Job", 0);
    MatchProfileNode matchProfile1 = new MatchProfileNode("match1", "MARC_BIBLIOGRAPHIC", "INSTANCE", 1);
    ActionProfileNode actionProfile = new ActionProfileNode("action1", "UPDATE", "INSTANCE", 2);
    MatchProfileNode matchProfile2 = new MatchProfileNode("match2", "MARC_BIBLIOGRAPHIC", "INSTANCE", 3);

    // Add nodes
    cyclicGraph.addVertex(jobProfile);
    cyclicGraph.addVertex(matchProfile1);
    cyclicGraph.addVertex(actionProfile);
    cyclicGraph.addVertex(matchProfile2);

    // Create a cycle: job -> match1 -> action -> match2 -> match1
    cyclicGraph.addEdge(jobProfile, matchProfile1, new RegularEdge(jobProfile, matchProfile1));
    cyclicGraph.addEdge(matchProfile1, actionProfile, new RegularEdge(matchProfile1, actionProfile));
    cyclicGraph.addEdge(actionProfile, matchProfile2, new RegularEdge(actionProfile, matchProfile2));
    cyclicGraph.addEdge(matchProfile2, matchProfile1, new RegularEdge(matchProfile2, matchProfile1)); // Creates cycle

    JobProfileAnalysisResult result = analyzer.analyzeJobProfile(cyclicGraph);

    // Should terminate without infinite loop and still produce meaningful results
    assertNotNull("Analysis result should not be null for cyclic graph", result);
    Set<String> requiredFields = result.requiredInventoryFields();
    assertTrue("Should identify actual inventory fields", requiredFields.contains("title"));

    // Should detect the cycle and not enter infinite loop
    List<JobProfilePath> paths = result.allPaths();
    // Paths should be empty or finite due to cycle detection
    assertNotNull("Paths should not be null", paths);
    // The exact number of paths depends on cycle detection, but it should be finite
    assertTrue("Should handle cycle gracefully", paths.size() < 100); // Reasonable upper bound
  }

  @Test
  public void testGetRequiredInventoryFields_ReturnsActualInventoryFields() {
    Set<String> fields = analyzer.getRequiredInventoryFields(simpleJobProfileGraph);

    assertNotNull("Fields should not be null", fields);
    assertFalse("Fields should not be empty", fields.isEmpty());

    // Should return actual inventory fields, not record types
    assertTrue("Should include title field", fields.contains("title"));
    assertTrue("Should include contributors.name field", fields.contains("contributors.name"));
    assertTrue("Should include identifiers.value field", fields.contains("identifiers.value"));

    // Should NOT contain record types
    assertFalse("Should not contain record type INSTANCE", fields.contains("INSTANCE"));
  }

  @Test
  public void testGetRequiredInventoryFields_CallsMappingRulesProcessor() {
    analyzer.getRequiredInventoryFields(complexJobProfileGraph);

    // Verify that the mapping rules processor was called with the correct record types
    verify(mockMappingRulesProcessor, times(1)).analyzeMappingRules(any(Set.class));
  }

  private boolean isValidFolioRecordType(String recordType) {
    Set<String> validTypes = Set.of("INSTANCE", "HOLDINGS", "ITEM", "MARC_BIBLIOGRAPHIC",
        "MARC_AUTHORITY", "MARC_HOLDINGS");
    return validTypes.contains(recordType);
  }
}