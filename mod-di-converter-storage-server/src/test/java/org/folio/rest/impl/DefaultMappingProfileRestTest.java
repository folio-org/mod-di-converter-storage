package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.folio.support.ProfileFixtures.MAPPING_PROFILE_1;
import static org.folio.support.TestUtil.MAPPING_PROFILES_PATH;
import static org.hamcrest.core.Is.is;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.support.AbstractDefaultProfileTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultMappingProfileRestTest extends AbstractDefaultProfileTest {

  private static final String DEFAULT_CREATE_AUTHORITIES_MAPPING_PROFILE_ID = "6a0ec1de-68eb-4833-bdbf-0741db25c314";

  @DisplayName("should add and remove tags on default mapping profile")
  @Test
  void shouldAddAndRemoveTagsDefaultProfile() {
    Tags tags = new Tags().withTagList(Arrays.asList("Lorem", "ipsum"));
    var profile = getMappingProfile(DEFAULT_CREATE_AUTHORITIES_MAPPING_PROFILE_ID);
    // Add tags to default profile
    putRequest(MAPPING_PROFILES_PATH + "/" + DEFAULT_CREATE_AUTHORITIES_MAPPING_PROFILE_ID,
      new MappingProfileUpdateDto().withProfile(profile.withTags(tags)))
      .statusCode(HttpStatus.SC_OK)
      .body("tags.tagList", is(tags.getTagList()));

    profile = getMappingProfile(DEFAULT_CREATE_AUTHORITIES_MAPPING_PROFILE_ID);
    // Delete tags from default profile
    putRequest(MAPPING_PROFILES_PATH + "/" + DEFAULT_CREATE_AUTHORITIES_MAPPING_PROFILE_ID,
      new MappingProfileUpdateDto().withProfile(profile.withTags(new Tags().withTagList(Collections.emptyList()))))
      .statusCode(HttpStatus.SC_OK)
      .body("tags.tagList", is(Matchers.empty()));
  }

  @DisplayName("should return 400 when attempting to PUT a default mapping profile")
  @ParameterizedTest
  @MethodSource("defaultMappingProfileIds")
  void shouldReturnBadRequestOnPutWithDefaultProfiles(String id) {
    putRequest(MAPPING_PROFILES_PATH + "/" + id, MAPPING_PROFILE_1)
      .statusCode(SC_BAD_REQUEST);
  }

  @DisplayName("should return 400 when attempting to DELETE a default mapping profile")
  @ParameterizedTest
  @MethodSource("defaultMappingProfileIds")
  void shouldReturnBadRequestOnDeleteWithDefaultProfiles(String id) {
    deleteRequest(MAPPING_PROFILES_PATH + "/" + id)
      .statusCode(SC_BAD_REQUEST);
  }

  private static Stream<String> defaultMappingProfileIds() {
    return Stream.of(
      "d0ebbc2e-2f0f-11eb-adc1-0242ac120002", //OCLC_CREATE_MAPPING_PROFILE_ID
      "862000b9-84ea-4cae-a223-5fc0552f2b42", //OCLC_UPDATE_MAPPING_PROFILE_ID
      "f90864ef-8030-480f-a43f-8cdd21233252", //OCLC_UPDATE_MARC_BIB_MAPPING_PROFILE_ID
      "13cf7adf-c7a7-4c2e-838f-14d0ac36ec0a", //DEFAULT_CREATE_HOLDINGS_MAPPING_PROFILE_ID
      "6a0ec1de-68eb-4833-bdbf-0741db85c314", //DEFAULT_CREATE_AUTHORITIES_MAPPING_PROFILE_ID
      "ff029a0a-82ff-486d-b2b1-7a4ef4cb7988"  //DEFAULT_DELETE_MARC_AUTHORITY_MAPPING_PROFILE_ID
    );
  }
}
