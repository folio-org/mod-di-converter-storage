package org.folio.support;

import static org.folio.rest.jaxrs.model.ActionProfile.Action.CREATE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.UPDATE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.INSTANCE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.JobProfile.DataType.MARC;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingRule;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.Tags;

/**
 * Canonical static test-data fixtures shared across REST integration test classes.
 * All four profile types share consistent naming (Bla/Boo/Foo/OLA) and tag sets.
 */
public final class ProfileFixtures {

  // ---- ActionProfile -------------------------------------------------------

  public static final String ACTION_PROFILE_UUID = "16449d21-ad7c-4f69-b31e-a521fe4ae893";

  public static final ActionProfileUpdateDto ACTION_PROFILE_1 = new ActionProfileUpdateDto()
    .withProfile(new ActionProfile().withName("Bla")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
      .withAction(CREATE)
      .withFolioRecord(INSTANCE));

  public static final ActionProfileUpdateDto ACTION_PROFILE_2 = new ActionProfileUpdateDto()
    .withProfile(new ActionProfile().withName("Boo")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
      .withAction(CREATE)
      .withFolioRecord(INSTANCE));

  public static final ActionProfileUpdateDto ACTION_PROFILE_3 = new ActionProfileUpdateDto()
    .withProfile(new ActionProfile().withName("Foo")
      .withTags(new Tags().withTagList(Collections.singletonList("lorem")))
      .withAction(CREATE)
      .withFolioRecord(INSTANCE));

  public static final ActionProfileUpdateDto ACTION_PROFILE_4 = new ActionProfileUpdateDto()
    .withProfile(new ActionProfile().withId(ACTION_PROFILE_UUID).withName("OLA")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
      .withAction(UPDATE)
      .withFolioRecord(MARC_BIBLIOGRAPHIC));

  public static final ActionProfileUpdateDto ACTION_PROFILE_NOT_EMPTY_CHILD_AND_PARENT =
    new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Action profile with child and parent")
        .withAction(UPDATE)
        .withParentProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString())))
        .withChildProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString())))
        .withFolioRecord(MARC_BIBLIOGRAPHIC));

  // ---- JobProfile ----------------------------------------------------------

  public static final String JOB_PROFILE_UUID = "b81c283c-131d-4470-ab91-e92bb415c000";

  public static final JobProfileUpdateDto JOB_PROFILE_1 = new JobProfileUpdateDto()
    .withProfile(new JobProfile().withName("Bla")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
      .withDataType(MARC));

  public static final JobProfileUpdateDto JOB_PROFILE_2 = new JobProfileUpdateDto()
    .withProfile(new JobProfile().withName("Boo")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
      .withDataType(MARC));

  public static final JobProfileUpdateDto JOB_PROFILE_3 = new JobProfileUpdateDto()
    .withProfile(new JobProfile().withName("Foo")
      .withTags(new Tags().withTagList(Collections.singletonList("lorem")))
      .withDataType(MARC));

  public static final JobProfileUpdateDto JOB_PROFILE_4 = new JobProfileUpdateDto()
    .withProfile(new JobProfile().withId(JOB_PROFILE_UUID).withName("OLA")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
      .withDataType(MARC));

  public static final JobProfileUpdateDto JOB_PROFILE_NOT_EMPTY_CHILD_AND_PARENT =
    new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withName("Job profile with child and parent")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
        .withDataType(MARC)
        .withChildProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString())))
        .withParentProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString()))));

  // ---- MappingProfile -------------------------------------------------------

  public static final String MAPPING_PROFILE_UUID = "608ab35e-5f8b-49c3-bcf1-1fb5e57d5130";

  public static final MappingProfileUpdateDto MAPPING_PROFILE_1 = new MappingProfileUpdateDto()
    .withProfile(new MappingProfile().withName("Bla")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.INSTANCE));

  public static final MappingProfileUpdateDto MAPPING_PROFILE_2 = new MappingProfileUpdateDto()
    .withProfile(new MappingProfile().withName("Boo")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.INSTANCE));

  public static final MappingProfileUpdateDto MAPPING_PROFILE_3 = new MappingProfileUpdateDto()
    .withProfile(new MappingProfile().withName("Foo")
      .withTags(new Tags().withTagList(Collections.singletonList("lorem")))
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.INSTANCE));

  public static final MappingProfileUpdateDto MAPPING_PROFILE_4 = new MappingProfileUpdateDto()
    .withProfile(new MappingProfile().withId(MAPPING_PROFILE_UUID).withName("OLA")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.INSTANCE));

  public static final MappingProfileUpdateDto MAPPING_PROFILE_5 = new MappingProfileUpdateDto()
    .withProfile(new MappingProfile().withName("B'oom")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.INSTANCE));

  public static final MappingProfileUpdateDto MAPPING_PROFILE_NOT_EMPTY_CHILD_AND_PARENT =
    new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withName("Mapping profile with child and parent")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)
        .withChildProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString())))
        .withParentProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString()))));

  public static final MappingProfileUpdateDto MAPPING_PROFILE_WITH_EMPTY_SUBFIELDS_AND_DELETE_EXISTING_ACTION =
    new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withName("Fooooo")
        .withTags(new Tags().withTagList(Collections.singletonList("lorem")))
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)
        .withMappingDetails(new MappingDetail().withMappingFields(Lists.newArrayList(new MappingRule()
          .withName("repeatableField")
          .withPath("instance.repeatableField[]")
          .withValue("")
          .withEnabled("true")
          .withRepeatableFieldAction(MappingRule.RepeatableFieldAction.DELETE_EXISTING)
          .withSubfields(Collections.emptyList())))));

  public static final MappingProfileUpdateDto MAPPING_PROFILE_WITH_EMPTY_SUBFIELDS_AND_EMPTY_ACTION =
    new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withName("Fooooo")
        .withTags(new Tags().withTagList(Collections.singletonList("lorem")))
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)
        .withMappingDetails(new MappingDetail().withMappingFields(Lists.newArrayList(new MappingRule()
          .withName("repeatableField")
          .withPath("instance.repeatableField[]")
          .withValue("")
          .withEnabled("true")
          .withRepeatableFieldAction(null)
          .withSubfields(Collections.emptyList())))));

  public static final MappingProfileUpdateDto MAPPING_PROFILE_WITH_EMPTY_SUBFIELDS_AND_NOT_DELETE_EXISTING_ACTION =
    new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withName("Fooooo")
        .withTags(new Tags().withTagList(Collections.singletonList("lorem")))
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)
        .withMappingDetails(new MappingDetail().withMappingFields(Lists.newArrayList(new MappingRule()
          .withName("repeatableField")
          .withPath("instance.repeatableField[]")
          .withValue("")
          .withEnabled("true")
          .withRepeatableFieldAction(MappingRule.RepeatableFieldAction.EXTEND_EXISTING)
          .withSubfields(Collections.emptyList())))));

  // ---- MatchProfile --------------------------------------------------------

  public static final String MATCH_PROFILE_UUID = "48a54656-8a2c-43c1-96b4-da96a70a0a62";

  public static final MatchProfileUpdateDto MATCH_PROFILE_1 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("Bla")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC));

  public static final MatchProfileUpdateDto MATCH_PROFILE_2 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("Boo")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC));

  public static final MatchProfileUpdateDto MATCH_PROFILE_3 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("Foo")
      .withTags(new Tags().withTagList(Collections.singletonList("lorem")))
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC));

  public static final MatchProfileUpdateDto MATCH_PROFILE_4 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withId(MATCH_PROFILE_UUID).withName("OLA")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC));

  public static final MatchProfileUpdateDto MATCH_PROFILE_NOT_EMPTY_CHILD_AND_PARENT =
    new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withName("Match profile with child and parent")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withChildProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString())))
        .withParentProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString()))));

  private ProfileFixtures() {
  }
}
