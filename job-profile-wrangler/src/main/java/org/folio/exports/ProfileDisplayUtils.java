package org.folio.exports;

import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.Profile;

/**
 * Utility class for generating display names for FOLIO profiles.
 * Provides consistent naming across different parts of the application.
 */
public final class ProfileDisplayUtils {

  private ProfileDisplayUtils() {
  }

  /**
   * Gets a display name for a profile suitable for path IDs and identifiers.
   * For ActionProfiles, returns "action_folioRecord" (e.g., "CREATE_INSTANCE").
   * For MappingProfiles, returns "Map_existingRecordType".
   * For other profiles, returns the name with whitespace removed.
   *
   * @param profile the profile to get the display name for (may be null)
   * @return the display name (whitespace-free for use in identifiers),
   *         or "unknown" if profile is null or has no name
   */
  public static String getProfileDisplayNameForId(Profile profile) {
    if (profile == null) {
      return "unknown";
    }
    if (profile instanceof ActionProfileNode action) {
      return action.action() + "_" + action.folioRecord();
    } else if (profile instanceof MappingProfileNode mapping) {
      return "Map_" + mapping.existingRecordType();
    } else {
      String name = profile.getName();
      return name != null ? name.replaceAll("\\s+", "") : "unknown";
    }
  }

  /**
   * Gets a display name for a profile suitable for logging and human-readable output.
   * For ActionProfiles, returns "action_folioRecord" (e.g., "CREATE_INSTANCE").
   * For MappingProfiles, returns "Map_existingRecordType".
   * For other profiles, returns the name as-is (may contain whitespace).
   *
   * @param profile the profile to get the display name for (may be null)
   * @return the display name (human-readable), or "unknown" if profile is null or has no name
   */
  public static String getProfileDisplayName(Profile profile) {
    if (profile == null) {
      return "unknown";
    }
    if (profile instanceof ActionProfileNode action) {
      return action.action() + "_" + action.folioRecord();
    } else if (profile instanceof MappingProfileNode mapping) {
      return "Map_" + mapping.existingRecordType();
    } else {
      String name = profile.getName();
      return name != null ? name : "unknown";
    }
  }
}
