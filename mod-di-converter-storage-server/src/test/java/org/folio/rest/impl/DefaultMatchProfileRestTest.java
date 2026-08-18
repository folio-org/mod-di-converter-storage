package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.folio.support.ProfileFixtures.MATCH_PROFILE_1;
import static org.folio.support.TestUtil.MATCH_PROFILES_PATH;
import static org.hamcrest.core.Is.is;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.support.AbstractDefaultProfileTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultMatchProfileRestTest extends AbstractDefaultProfileTest {

  private static final String OCLC_INSTANCE_UUID_MATCH_PROFILE_ID = "31dbb554-0826-48ec-a0a4-3c55293d4dee";
  private static final String DEFAULT_DELETE_MARC_AUTHORITY_MATCH_PROFILE_ID = "4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6";

  @DisplayName("should add and remove tags on default match profile")
  @Test
  void shouldAddAndRemoveTagsDefaultProfile() {
    var tags = new Tags().withTagList(Arrays.asList("Lorem", "ipsum"));
    var profile = getMatchProfile(OCLC_INSTANCE_UUID_MATCH_PROFILE_ID);
    // Add tags to default profile
    putRequest(MATCH_PROFILES_PATH + "/" + OCLC_INSTANCE_UUID_MATCH_PROFILE_ID,
      new MatchProfileUpdateDto().withProfile(profile.withTags(tags)))
      .statusCode(HttpStatus.SC_OK)
      .body("tags.tagList", is(tags.getTagList()));

    profile = getMatchProfile(OCLC_INSTANCE_UUID_MATCH_PROFILE_ID);
    // Delete tags from default profile
    putRequest(MATCH_PROFILES_PATH + "/" + OCLC_INSTANCE_UUID_MATCH_PROFILE_ID,
      new MatchProfileUpdateDto().withProfile(profile.withTags(new Tags().withTagList(Collections.emptyList()))))
      .statusCode(HttpStatus.SC_OK)
      .body("tags.tagList", is(Matchers.empty()));
  }

  @DisplayName("should update default delete MARC-Authority match profile on PUT")
  @Test
  void shouldUpdateDefaultDeleteMarcAuthorityMatchProfileOnPut() {
    var profile = getMatchProfile(DEFAULT_DELETE_MARC_AUTHORITY_MATCH_PROFILE_ID);
    profile.setName("Changed name");

    putRequest(MATCH_PROFILES_PATH + "/" + DEFAULT_DELETE_MARC_AUTHORITY_MATCH_PROFILE_ID,
      new MatchProfileUpdateDto().withProfile(profile))
      .statusCode(HttpStatus.SC_OK)
      .log().all()
      .body("name", is(profile.getName()));
  }

  @DisplayName("should return 400 when attempting to PUT a restricted default match profile")
  @ParameterizedTest
  @MethodSource("defaultMatchProfileIdsRestrictedForUpdate")
  void shouldReturnBadRequestOnPutWithDefaultProfiles(String id) {
    putRequest(MATCH_PROFILES_PATH + "/" + id, MATCH_PROFILE_1)
      .statusCode(SC_BAD_REQUEST);
  }

  @DisplayName("should return 400 when attempting to DELETE a restricted default match profile")
  @ParameterizedTest
  @MethodSource("defaultMatchProfileIdsRestrictedForDeletion")
  void shouldReturnBadRequestOnDeleteWithDefaultProfiles(String id) {
    deleteRequest(MATCH_PROFILES_PATH + "/" + id)
      .statusCode(SC_BAD_REQUEST);
  }

  private static Stream<String> defaultMatchProfileIdsRestrictedForUpdate() {
    return Stream.of(
      "d27d71ce-8a1e-44c6-acea-96961b5592c6", //OCLC_MARC_MARC_MATCH_PROFILE_ID
      "31dbb554-0826-48ec-a0a4-3c55293d4dee"  //OCLC_INSTANCE_UUID_MATCH_PROFILE_ID
    );
  }

  private static Stream<String> defaultMatchProfileIdsRestrictedForDeletion() {
    return Stream.of(
      "d27d71ce-8a1e-44c6-acea-96961b5592c6", //OCLC_MARC_MARC_MATCH_PROFILE_ID
      "31dbb554-0826-48ec-a0a4-3c55293d4dee", //OCLC_INSTANCE_UUID_MATCH_PROFILE_ID
      "4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6"  //DEFAULT_DELETE_MARC_AUTHORITY_MATCH_PROFILE_ID
    );
  }
}
