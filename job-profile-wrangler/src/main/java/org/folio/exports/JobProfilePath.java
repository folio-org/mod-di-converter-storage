package org.folio.exports;

import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.Profile;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single execution path through a job profile graph.
 * Used to analyze different workflow branches and their requirements.
 */
public class JobProfilePath {

  private final List<Profile> profiles;
  private final String pathId;

  public JobProfilePath(List<Profile> profiles, String pathId) {
    this.profiles = Objects.requireNonNull(profiles);
    this.pathId = Objects.requireNonNull(pathId);
  }

  public JobProfilePath(List<Profile> profiles) {
    this(profiles, generatePathId(profiles));
  }

  public List<Profile> getProfiles() {
    return Collections.unmodifiableList(profiles);
  }

  public String getPathId() {
    return pathId;
  }

  public int getPathLength() {
    return profiles.size();
  }

  public boolean containsProfileType(Class<? extends Profile> profileType) {
    return profiles.stream().anyMatch(profileType::isInstance);
  }

  /**
   * Checks if this path contains an action profile that creates HOLDINGS records.
   *
   * @return true if the path creates Holdings
   */
  public boolean createsHoldings() {
    return createsRecordType("HOLDINGS");
  }

  /**
   * Checks if this path contains an action profile that creates ITEM records.
   *
   * @return true if the path creates Items
   */
  public boolean createsItems() {
    return createsRecordType("ITEM");
  }

  /**
   * Checks if this path contains an action profile that creates the specified record type.
   *
   * @param recordType the FOLIO record type to check for (e.g., "INSTANCE", "HOLDINGS", "ITEM")
   * @return true if the path creates the specified record type
   */
  public boolean createsRecordType(String recordType) {
    return profiles.stream()
        .filter(ActionProfileNode.class::isInstance)
        .map(ActionProfileNode.class::cast)
        .anyMatch(action -> "CREATE".equals(action.action()) && recordType.equals(action.folioRecord()));
  }

  private static String generatePathId(List<Profile> profiles) {
    StringBuilder pathId = new StringBuilder();
    for (Profile profile : profiles) {
      if (!pathId.isEmpty()) {
        pathId.append("->");
      }
      pathId.append(ProfileDisplayUtils.getProfileDisplayNameForId(profile));
    }
    return pathId.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JobProfilePath that = (JobProfilePath) o;
    return Objects.equals(profiles, that.profiles) &&
           Objects.equals(pathId, that.pathId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(profiles, pathId);
  }

  @Override
  public String toString() {
    return "JobProfilePath{" +
           "pathId='" + pathId + '\'' +
           ", profiles=" + profiles.size() + " profiles" +
           '}';
  }
}