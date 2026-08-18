package org.folio.rest.impl;

import static org.folio.support.ProfileFixtures.ACTION_PROFILE_1;
import static org.folio.support.TestUtil.ACTION_PROFILES_PATH;
import static org.hamcrest.core.Is.is;

import io.restassured.response.ValidatableResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.support.AbstractDefaultProfileTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultActionProfileRestTest extends AbstractDefaultProfileTest {

  private static final String DEFAULT_CREATE_AUTHORITIES_ACTION_PROFILE_ID = "7915c72e-c6af-4962-969d-403c7238b051";
  private static final String DEFAULT_CREATE_AUTHORITIES_PROFILE_PATH =
    ACTION_PROFILES_PATH + "/" + DEFAULT_CREATE_AUTHORITIES_ACTION_PROFILE_ID;

  @DisplayName("should add and remove tags on default action profile")
  @Test
  void shouldAddAndRemoveTagsDefaultProfile() {
    var tags = new Tags().withTagList(Arrays.asList("Lorem", "ipsum"));
    var profile = getActionProfile(DEFAULT_CREATE_AUTHORITIES_ACTION_PROFILE_ID);
    // Add tags to default profile
    updateActionProfile(new ActionProfileUpdateDto().withProfile(profile.withTags(tags)))
      .statusCode(HttpStatus.SC_OK)
      .body("tags.tagList", is(tags.getTagList()));

    profile = getActionProfile(DEFAULT_CREATE_AUTHORITIES_ACTION_PROFILE_ID);
    // Delete tags from default profile
    updateActionProfile(new ActionProfileUpdateDto()
      .withProfile(profile.withTags(new Tags().withTagList(Collections.emptyList()))))
      .body("tags.tagList", is(Matchers.empty()));
  }

  @DisplayName("should return 400 when attempting to PUT a default profile")
  @ParameterizedTest
  @MethodSource("defaultActionProfileIds")
  void shouldReturnBadRequestOnPutWithDefaultProfiles(String id) {
    putRequest(ACTION_PROFILES_PATH + "/" + id, ACTION_PROFILE_1)
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @DisplayName("should return 400 when attempting to DELETE a default profile")
  @ParameterizedTest
  @MethodSource("defaultActionProfileIds")
  void shouldReturnBadRequestOnDeleteWithDefaultProfiles(String id) {
    deleteRequest(ACTION_PROFILES_PATH + "/" + id)
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  private static Stream<String> defaultActionProfileIds() {
    return Stream.of(
      "d0ebba8a-2f0f-11eb-adc1-0242ac120002", //OCLC_CREATE_INSTANCE_ACTION_PROFILE_ID
      "cddff0e1-233c-47ba-8be5-553c632709d9", //OCLC_UPDATE_INSTANCE_ACTION_PROFILE_ID
      "6aa8e98b-0d9f-41dd-b26f-15658d07eb52", //OCLC_UPDATE_MARC_BIB_ACTION_PROFILE_ID
      "8aa0b850-9182-4005-8435-340b704b2a19", //DEFAULT_CREATE_HOLDINGS_ACTION_PROFILE_ID
      "7915c72e-c6af-4962-969d-403c7238b051", //DEFAULT_CREATE_AUTHORITIES_ACTION_PROFILE_ID
      "fabd9a3e-33c3-49b7-864d-c5af830d9990"  //DEFAULT_DELETE_MARC_AUTHORITY_ACTION_PROFILE_ID
    );
  }

  private ValidatableResponse updateActionProfile(ActionProfileUpdateDto profileUpdateDto) {
    return putRequest(DEFAULT_CREATE_AUTHORITIES_PROFILE_PATH, profileUpdateDto)
      .statusCode(HttpStatus.SC_OK);
  }
}
