package org.folio.profile;

import com.fasterxml.jackson.databind.JsonNode;
import org.folio.graph.edges.MatchRelationshipEdge;
import org.folio.graph.edges.NonMatchRelationshipEdge;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Normalized view of a FOLIO job-profile snapshot.
 *
 * <p>The live snapshot stores profile nodes as wrappers with content, reactTo, and
 * childSnapshotWrappers fields. This module keeps that interface in one place so
 * import, validation, and generation code can talk about profile trees instead
 * of repeating raw JsonNode traversal.
 */
public record ProfileTree(ProfileTreeNode root) {
  public ProfileTree {
    if (root == null) {
      throw new IllegalArgumentException("root is required");
    }
  }

  public static ProfileTree fromSnapshot(JsonNode snapshot) {
    return new ProfileTree(ProfileTreeNode.fromSnapshot(snapshot));
  }

  public String profileId(String fallback) {
    String contentId = root.content().path("id").asText(null);
    if (contentId != null && !contentId.isBlank()) {
      return contentId;
    }
    String profileId = root.snapshot().path("profileId").asText(null);
    return profileId == null || profileId.isBlank() ? fallback : profileId;
  }

  public String profileName() {
    return root.content().path("name").asText(null);
  }

  public Graph<Profile, RegularEdge> toGraph(ProfileIdSource idSource) {
    Graph<Profile, RegularEdge> graph = new SimpleDirectedGraph<>(RegularEdge.class);
    addToGraph(graph, root, idSource);
    return graph;
  }

  public static JsonNode children(JsonNode node) {
    JsonNode childSnapshotWrappers = node.path("childSnapshotWrappers");
    if (!childSnapshotWrappers.isMissingNode()) {
      return childSnapshotWrappers;
    }
    return node.path("childrenWrappers");
  }

  public static List<JsonNode> orderedChildren(JsonNode nodeOrChildren) {
    JsonNode rawChildren = nodeOrChildren.isArray() ? nodeOrChildren : children(nodeOrChildren);
    if (!rawChildren.isArray()) {
      return List.of();
    }

    List<JsonNode> ordered = new ArrayList<>();
    rawChildren.forEach(ordered::add);
    // Some callers already hold the children array; others hold the wrapper node.
    ordered.sort(Comparator.comparingInt(ProfileTree::order));
    return ordered;
  }

  private static int order(JsonNode node) {
    return node.path("content").path("order").asInt(node.path("order").asInt(0));
  }

  public static String text(JsonNode node, String... fieldNames) {
    for (String fieldName : fieldNames) {
      JsonNode value = node.path(fieldName);
      if (!value.isMissingNode() && !value.isNull()) {
        return value.asText();
      }
    }
    return "";
  }

  private Optional<Profile> addToGraph(
      Graph<Profile, RegularEdge> graph,
      ProfileTreeNode node,
      ProfileIdSource idSource) {
    Optional<Profile> profile = node.toProfile(idSource);
    profile.ifPresent(parent -> {
      graph.addVertex(parent);
      if (parent instanceof MappingProfileNode && !node.children().isEmpty()) {
        return;
      }
      for (ProfileTreeNode child : node.children()) {
        if (parent instanceof MatchProfileNode && !isValidMatchReaction(child.reactTo())) {
          continue;
        }
        addToGraph(graph, child, idSource)
          .ifPresent(childProfile -> addEdge(graph, parent, childProfile, child.reactTo()));
      }
    });
    return profile;
  }

  private void addEdge(Graph<Profile, RegularEdge> graph, Profile parent, Profile child, String reactTo) {
    switch (reactTo) {
      case "MATCH" -> graph.addEdge(parent, child, new MatchRelationshipEdge());
      case "NON_MATCH" -> graph.addEdge(parent, child, new NonMatchRelationshipEdge());
      default -> graph.addEdge(parent, child, new RegularEdge());
    }
  }

  private boolean isValidMatchReaction(String reactTo) {
    return "MATCH".equals(reactTo) || "NON_MATCH".equals(reactTo);
  }

  public enum ProfileIdSource {
    CONTENT,
    WRAPPER
  }

  public record ProfileTreeNode(JsonNode snapshot, String contentType, JsonNode content, String reactTo,
                                List<ProfileTreeNode> children) {
    public ProfileTreeNode {
      children = List.copyOf(children);
      contentType = contentType == null ? "" : contentType;
      reactTo = reactTo == null ? "" : reactTo;
    }

    public static ProfileTreeNode fromSnapshot(JsonNode snapshot) {
      if (snapshot == null || snapshot.isMissingNode() || snapshot.isNull()) {
        throw new IllegalArgumentException("snapshot node is required");
      }

      List<ProfileTreeNode> children = new ArrayList<>();
      JsonNode rawChildren = ProfileTree.children(snapshot);
      if (rawChildren.isArray()) {
        rawChildren.forEach(child -> children.add(fromSnapshot(child)));
        children.sort(Comparator.comparingInt(ProfileTreeNode::order));
      }

      return new ProfileTreeNode(
        snapshot,
        ProfileTree.text(snapshot, "contentType", "profileType"),
        snapshot.path("content"),
        ProfileTree.text(snapshot, "reactTo", "reactionStatus"),
        children
      );
    }

    public int order() {
      JsonNode contentOrder = content.path("order");
      if (contentOrder.isInt()) {
        return contentOrder.asInt();
      }
      return snapshot.path("order").asInt(0);
    }

    public Optional<Profile> toProfile(ProfileIdSource idSource) {
      String id = idSource == ProfileIdSource.WRAPPER
        ? ProfileTree.text(snapshot, "profileWrapperId")
        : ProfileTree.text(content, "id");
      if (id.isBlank()) {
        id = ProfileTree.text(content, "id");
      }

      int order = order();
      return switch (contentType) {
        case "JOB_PROFILE" -> Optional.of(new JobProfileNode(id, ProfileTree.text(content, "dataType"), order));
        case "MATCH_PROFILE" -> Optional.of(new MatchProfileNode(id,
          ProfileTree.text(content, "incomingRecordType"), ProfileTree.text(content, "existingRecordType"), order));
        case "ACTION_PROFILE" -> Optional.of(new ActionProfileNode(id,
          ProfileTree.text(content, "action"), ProfileTree.text(content, "folioRecord"), order));
        case "MAPPING_PROFILE" -> Optional.of(new MappingProfileNode(id,
          ProfileTree.text(content, "incomingRecordType"), ProfileTree.text(content, "existingRecordType"), order));
        default -> Optional.empty();
      };
    }
  }
}
