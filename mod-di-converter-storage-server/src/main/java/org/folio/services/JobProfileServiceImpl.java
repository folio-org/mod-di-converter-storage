package org.folio.services;

import io.vertx.core.Future;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileCollection;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileCollection;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileCollection;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.OperationType;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ReactToType;
import org.folio.services.snapshot.ProfileSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;

@Component
public class JobProfileServiceImpl extends AbstractProfileService<JobProfile, JobProfileCollection, JobProfileUpdateDto> {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final String LINKED_ACTION_PROFILES_WERE_NOT_FOUND = "Linked ActionProfiles with ids %s were not found";
  private static final String INVALID_ACTION_PROFILE_LINKED_TO_JOB_PROFILE = "ActionProfile with id '%s' and action UPDATE requires linked MatchProfile";
  private static final String MODIFY_ACTION_CANNOT_BE_USED_AS_A_STANDALONE_ACTION = "Modify action cannot be used as a standalone action";
  private static final String MODIFY_ACTION_CANNOT_BE_USED_RIGHT_AFTER_THE_MATCH = "Modify action cannot be used right after a Match";
  private static final String LINKED_MATCH_PROFILES_WERE_NOT_FOUND = "Linked MatchProfiles with ids %s were not found";
  private static final String DEFAULT_CREATE_SRS_MARC_AUTHORITY_JOB_PROFILE_ID = "6eefa4c6-bbf7-4845-ad82-de7fc5abd0e3"; //NOSONAR
  private static final String UPDATE_ACTIONS_CANNOT_BE_USED_UNDER_SAME_MATCH = "More than two update actions cannot be placed under the same match block";
  private static final List<String> DEFAULT_JOB_PROFILES = Arrays.asList(
    "d0ebb7b0-2f0f-11eb-adc1-0242ac120002", //OCLC_CREATE_INSTANCE_JOB_PROFILE_ID,
    "91f9b8d6-d80e-4727-9783-73fb53e3c786", //OCLC_UPDATE_INSTANCE_JOB_PROFILE_ID,
    "fa0262c7-5816-48d0-b9b3-7b7a862a5bc7", //DEFAULT_CREATE_DERIVE_HOLDINGS_JOB_PROFILE_ID
    "6409dcff-71fa-433a-bc6a-e70ad38a9604", //DEFAULT_CREATE_DERIVE_INSTANCE_JOB_PROFILE_ID
    "80898dee-449f-44dd-9c8e-37d5eb469b1d", //DEFAULT_CREATE_HOLDINGS_AND_SRS_MARC_HOLDINGS_JOB_PROFILE_ID
    "1a338fcd-3efc-4a03-b007-394eeb0d5fb9", //DEFAULT_DELETE_MARC_AUTHORITY_JOB_PROFILE_ID
    "cf6f2718-5aa5-482a-bba5-5bc9b75614da", //DEFAULT_QM_MARC_BIB_UPDATE_JOB_PROFILE_ID
    "6cb347c6-c0b0-4363-89fc-32cedede87ba", //DEFAULT_QM_HOLDINGS_UPDATE_JOB_PROFILE_ID
    "c7fcbc40-c4c0-411d-b569-1fc6bc142a92",
    "6eefa4c6-bbf7-4845-ad82-de7fc4abd0e3"//DEFAULT_QM_AUTHORITY_CREATE_JOB_PROFILE_ID
  );

  @Autowired
  private ProfileService<ActionProfile, ActionProfileCollection, ActionProfileUpdateDto> actionProfileService;
  @Autowired
  private ProfileService<MatchProfile, MatchProfileCollection, MatchProfileUpdateDto> matchProfileService;
  @Autowired
  private ProfileSnapshotService profileSnapshotService;

  @Override
  JobProfile setProfileId(JobProfile profile) {
    String profileId = profile.getId();
    return profile.withId(StringUtils.isBlank(profileId) ?
      UUID.randomUUID().toString() : profileId);
  }

  @Override
  Future<JobProfile> setUserInfoForProfile(JobProfile profile, OkapiConnectionParams params) {
    profile.setMetadata(getMetadata(params.getHeaders()));
    return lookupUser(profile.getMetadata().getUpdatedByUserId(), params)
      .compose(userInfo -> Future.succeededFuture(profile.withUserInfo(userInfo)));
  }

  @Override
  public String getProfileName(JobProfile profile) {
    return profile.getName();
  }

  @Override
  public List<ProfileAssociation> getAddedRelations(JobProfileUpdateDto profileUpdateDto) {
    return profileUpdateDto.getAddedRelations();
  }

  @Override
  public JobProfileUpdateDto withDeletedRelations(JobProfileUpdateDto profileUpdateDto, List<ProfileAssociation> profileAssociations) {
    return profileUpdateDto.withDeletedRelations(profileAssociations);
  }

  @Override
  protected String getProfileId(JobProfile profile) {
    return profile.getId();
  }

  @Override
  protected JobProfileUpdateDto prepareAssociations(JobProfileUpdateDto profileDto) {
    profileDto.getAddedRelations().forEach(association -> {
      if (StringUtils.isEmpty(association.getMasterProfileId())) {
        association.setMasterProfileId(profileDto.getProfile().getId());
      }
      if (StringUtils.isEmpty(association.getDetailProfileId())) {
        association.setDetailProfileId(profileDto.getProfile().getId());
      }
      if (association.getMasterProfileType() == MATCH_PROFILE) {
        association.setJobProfileId(profileDto.getProfile().getId());
      }
    });
    profileDto.getDeletedRelations().forEach(association -> {
      if (association.getMasterProfileType() == MATCH_PROFILE) {
        association.setJobProfileId(profileDto.getProfile().getId());
      }
    });
    return profileDto;
  }

  @Override
  protected ProfileType getProfileContentType() {
    return ProfileType.JOB_PROFILE;
  }

  @Override
  protected List<ProfileSnapshotWrapper> getChildProfiles(JobProfile profile) {
    return profile.getChildProfiles();
  }

  @Override
  protected void setChildProfiles(JobProfile profile, List<ProfileSnapshotWrapper> childProfiles) {
    profile.setChildProfiles(childProfiles);
  }

  @Override
  protected List<ProfileSnapshotWrapper> getParentProfiles(JobProfile profile) {
    return profile.getParentProfiles();
  }

  @Override
  protected void setParentProfiles(JobProfile profile, List<ProfileSnapshotWrapper> parentProfiles) {
    profile.setParentProfiles(parentProfiles);
  }

  @Override
  protected List<JobProfile> getProfilesList(JobProfileCollection profilesCollection) {
    return profilesCollection.getJobProfiles();
  }

  @Override
  protected List<ProfileAssociation> getProfileAssociationToAdd(JobProfileUpdateDto dto) {
    return dto.getAddedRelations();
  }

  @Override
  protected List<ProfileAssociation> getProfileAssociationToDelete(JobProfileUpdateDto dto) {
    return dto.getDeletedRelations();
  }

  @Override
  protected JobProfile getProfile(JobProfileUpdateDto dto) {
    return dto.getProfile();
  }

  @Override
  protected List<String> getDefaultProfiles() {
    return DEFAULT_JOB_PROFILES;
  }

  @Override
  protected Future<Errors> validateProfile(OperationType operationType, JobProfileUpdateDto profileDto, String tenantId) {
    return composeFutureErrors(
      validateJobProfileAssociations(profileDto, tenantId),
      super.validateProfile(operationType, profileDto, tenantId),
      validateJobProfileLinkedActionProfiles(profileDto, tenantId),
      validateJobProfileLinkedMatchProfile(profileDto, tenantId)
    );
  }

  @Override
  protected boolean canDeleteProfile(String profileId) {
    return !DEFAULT_CREATE_SRS_MARC_AUTHORITY_JOB_PROFILE_ID.equals(profileId) && super.canDeleteProfile(profileId);
  }

  private Future<Errors> validateJobProfileAssociations(JobProfileUpdateDto entity, String tenantId) {
    String jobProfileId = entity.getProfile().getId();
    Future<List<ProfileAssociation>> existingJobProfileAssociationsFuture = (jobProfileId != null) ?
      profileSnapshotService.getSnapshotAssociations(jobProfileId, ProfileType.JOB_PROFILE, jobProfileId, tenantId):
      Future.succeededFuture(new ArrayList<>());

    List<Error> errors = new LinkedList<>();
    return existingJobProfileAssociationsFuture
      .map(this::filterProfileAssociations)
      .map(profileAssociations -> removeDeletedProfileAssociations(profileAssociations, entity))
      .compose(profileAssociations -> validateJobProfileAssociations(profileAssociations, errors));
  }

  private Future<Errors> validateJobProfileLinkedActionProfiles(JobProfileUpdateDto jobProfileUpdateDto, String tenantId) {
    String jobProfileId = jobProfileUpdateDto.getProfile().getId();
    LOGGER.debug("validateJobProfileLinkedActionProfiles:: Validating ActionProfiles added to JobProfile {}", jobProfileId);
    List<Error> errors = new LinkedList<>();
    Future<List<ProfileAssociation>> existingJobProfileAssociationsFuture;
    if (jobProfileId != null) {
      existingJobProfileAssociationsFuture = profileSnapshotService.getSnapshotAssociations(jobProfileId, ProfileType.JOB_PROFILE, jobProfileId, tenantId);
    } else {
      existingJobProfileAssociationsFuture = Future.succeededFuture(new ArrayList<>());
    }

    return existingJobProfileAssociationsFuture
      .map(this::filterProfileAssociations)
      .map(profileAssociations -> removeDeletedProfileAssociations(profileAssociations, jobProfileUpdateDto))
      .compose(profileAssociations -> validateActionProfilesAssociations(profileAssociations, errors, tenantId));
  }

  private Future<Errors> validateJobProfileLinkedMatchProfile(JobProfileUpdateDto jobProfileUpdateDto, String tenantId) {
    String jobProfileId = jobProfileUpdateDto.getProfile().getId();
    LOGGER.debug("validateJobProfileLinkedMatchProfile:: Validating MatchProfiles added to JobProfile {}", jobProfileId);

    List<Error> errors = new LinkedList<>();
    Future<List<ProfileAssociation>> existingJobProfileAssociationsFuture = (jobProfileId != null) ?
      profileSnapshotService.getSnapshotAssociations(jobProfileId, ProfileType.JOB_PROFILE, jobProfileId, tenantId):
      Future.succeededFuture(new ArrayList<>());

    return existingJobProfileAssociationsFuture
      .map(this::filterProfileAssociations)
      .map(profileAssociations -> removeDeletedProfileAssociations(profileAssociations, jobProfileUpdateDto))
      .compose(profileAssociations -> validateJobProfileLinkedMatchProfile(profileAssociations, errors, tenantId));
  }

  private List<ProfileAssociation> filterProfileAssociations(List<ProfileAssociation> profileAssociations) {
    return profileAssociations.stream()
      .filter(profileAssociation -> profileAssociation.getDetailProfileType() != ProfileType.MAPPING_PROFILE &&
        profileAssociation.getDetailProfileType() != ProfileType.JOB_PROFILE).toList();
  }

  private List<ProfileAssociation> removeDeletedProfileAssociations(List<ProfileAssociation> profileAssociations,
                                                                    JobProfileUpdateDto jobProfileUpdateDto) {
    if (!profileAssociations.isEmpty() && !jobProfileUpdateDto.getDeletedRelations().isEmpty()) {
      profileAssociations.removeIf(profileAssociation ->
        jobProfileUpdateDto.getDeletedRelations().stream().anyMatch(deleteAssociation -> {
            if (profileAssociation.getId() != null && deleteAssociation.getId() != null) {
              return Objects.equals(profileAssociation.getId(), deleteAssociation.getId());
            }
            return Objects.equals(profileAssociation.getMasterWrapperId(), deleteAssociation.getMasterWrapperId()) &&
              Objects.equals(profileAssociation.getDetailWrapperId(), deleteAssociation.getDetailWrapperId());
          }
        )
      );
    }
    profileAssociations.addAll(jobProfileUpdateDto.getAddedRelations());
    return profileAssociations;
  }

  private Future<Errors> validateJobProfileAssociations(List<ProfileAssociation> profileAssociations, List<Error> errors) {
    LOGGER.debug("validateJobProfileAssociations:: Validating JobProfile if it contains associations");
    if (CollectionUtils.isEmpty(profileAssociations)) {
      LOGGER.warn("validateJobProfileAssociations:: Job profile does not contain any associations");
      errors.add(new Error().withMessage("Job profile does not contain any associations"));
    }
    return Future.succeededFuture(new Errors().withErrors(errors).withTotalRecords(errors.size()));
  }

  private Future<Errors> validateActionProfilesAssociations(List<ProfileAssociation> profileAssociations, List<Error> errors, String tenantId) {
    var actionProfileAssociations = actionProfileAssociations(profileAssociations);
    var actionProfileIds = actionProfileAssociations.stream().map(ProfileAssociation::getDetailProfileId).toList();
    var getProfileFutures = actionProfileIds.stream()
      .map(id -> actionProfileService.getProfileById(id, false, tenantId))
      .toList();

    return GenericCompositeFuture.all(getProfileFutures)
      .compose(actionProfiles -> {
        List<ActionProfile> existingActionProfiles = actionProfiles.result().<Optional<ActionProfile>>list().stream()
          .filter(Optional::isPresent).map(Optional::get).toList();

        var existingActionProfilesIds = existingActionProfiles.stream().map(ActionProfile::getId).toList();
        var notFoundedIds = actionProfileIds.stream()
          .filter(id -> !existingActionProfilesIds.contains(id))
          .map(notFoundedId -> String.format("'%s'", notFoundedId)).toList();

        if (!notFoundedIds.isEmpty()) {
          var idStr = String.join(", ", notFoundedIds);
          LOGGER.warn("validateActionProfilesAssociations:: Linked ActionProfiles with ids {} not founded", idStr);
          return Future.failedFuture(new NotFoundException((String.format(LINKED_ACTION_PROFILES_WERE_NOT_FOUND, idStr))));
        }
        return Future.succeededFuture(existingActionProfiles);
      })
      .compose(actionProfiles -> {
        actionProfileAssociations.forEach(association -> {
          ActionProfile actionProfile = actionProfiles.stream()
            .filter(profile -> profile.getId().equals(association.getDetailProfileId()))
            .findAny().orElseThrow(() -> new NotFoundException(String.format(LINKED_ACTION_PROFILES_WERE_NOT_FOUND, association.getDetailProfileId())));

          if (actionProfile.getAction() == ActionProfile.Action.UPDATE) {
            validateAddedUpdateActionProfileAssociation(association, actionProfile, errors);
          }
          if (actionProfile.getAction() == ActionProfile.Action.MODIFY) {
            validateAddedModifyActionProfileAssociation(profileAssociations, association, actionProfile, actionProfiles, errors);
          }
        });
        validateAddedUpdateActionProfileAssociationUnderSameMatch(actionProfileAssociations, actionProfiles, errors);
        return Future.succeededFuture(new Errors().withErrors(errors).withTotalRecords(errors.size()));
      });
  }

  private List<ProfileAssociation> actionProfileAssociations(List<ProfileAssociation> profileAssociations) {
    return profileAssociations.stream()
      .filter(association -> association.getDetailProfileType() == ACTION_PROFILE).toList();
  }

  private static void validateAddedUpdateActionProfileAssociation(ProfileAssociation association, ActionProfile actionProfile, List<Error> errors) {
    if (association.getMasterProfileType() != MATCH_PROFILE) {
      LOGGER.warn("validateAddedUpdateActionProfileAssociation:: Missing linked MatchProfile for ActionProfile {} with action UPDATE", actionProfile.getId());
      errors.add(new Error().withMessage(String.format(INVALID_ACTION_PROFILE_LINKED_TO_JOB_PROFILE, actionProfile.getId())));
    }
  }

  private static void validateAddedUpdateActionProfileAssociationUnderSameMatch(List<ProfileAssociation> actionProfileAssociations,
                                                                                List<ActionProfile> actionProfiles,
                                                                                List<Error> errors) {
    var updateActionProfiles = getUpdateProfileAssociations(actionProfileAssociations, actionProfiles);

    if (!updateActionProfiles.isEmpty()) {
      Map<ReactToType, Long> counts = updateActionProfiles.stream()
        .collect(Collectors.groupingBy(ProfileAssociation::getReactTo, Collectors.counting()));

      boolean hasMultipleActionsUnderSameMatch = counts.getOrDefault(ReactToType.MATCH, 0L) >= 2
        || counts.getOrDefault(ReactToType.NON_MATCH, 0L) >= 2;

      if (hasMultipleActionsUnderSameMatch) {
        LOGGER.warn("validateMatchProfilesAssociations:: " + UPDATE_ACTIONS_CANNOT_BE_USED_UNDER_SAME_MATCH);
        errors.add(new Error().withMessage(UPDATE_ACTIONS_CANNOT_BE_USED_UNDER_SAME_MATCH));
      }
    }
  }

  private static void validateAddedModifyActionProfileAssociation(List<ProfileAssociation> profileAssociations, ProfileAssociation association, ActionProfile actionProfile,
                                                                  List<ActionProfile> actionProfiles, List<Error> errors) {
    List<ProfileAssociation> notModifyProfileAssociations = getNotModifyProfileAssociations(profileAssociations, actionProfiles);

    if (association.getMasterProfileType() == ProfileType.JOB_PROFILE && notModifyProfileAssociations.isEmpty()) {
      LOGGER.warn("validateAddedModifyActionProfileAssociation:: Modify profile with id {}, used as standalone action", actionProfile.getId());
      errors.add(new Error().withMessage(MODIFY_ACTION_CANNOT_BE_USED_AS_A_STANDALONE_ACTION));
    }

    if (association.getMasterProfileType() == MATCH_PROFILE && isFirstAtMatchBlock(profileAssociations, association)) {
      LOGGER.warn("validateAddedModifyActionProfileAssociation:: Modify profile with id {}, used right after Match profile", actionProfile.getId());
      errors.add(new Error().withMessage(MODIFY_ACTION_CANNOT_BE_USED_RIGHT_AFTER_THE_MATCH));
    }
  }

  private static List<ProfileAssociation> getUpdateProfileAssociations(List<ProfileAssociation> profileAssociations,
                                                                  List<ActionProfile> actionProfiles) {
    List<String> updateActionProfileIds = actionProfiles.stream().filter(a -> a.getAction() == ActionProfile.Action.UPDATE).map(ActionProfile::getId).toList();
    return profileAssociations.stream()
      .filter(p -> updateActionProfileIds.contains(p.getDetailProfileId()))
      .filter(profileAssociation -> profileAssociation.getReactTo() != null)
      .toList();
  }

  private static List<ProfileAssociation> getNotModifyProfileAssociations(List<ProfileAssociation> profileAssociations, List<ActionProfile> actionProfiles) {
    List<String> modifyActionProfileIds = actionProfiles.stream().filter(a -> a.getAction() == ActionProfile.Action.MODIFY).map(ActionProfile::getId).toList();
    return profileAssociations.stream().filter(p -> !modifyActionProfileIds.contains(p.getDetailProfileId())).toList();
  }

  private static boolean isFirstAtMatchBlock(List<ProfileAssociation> profileAssociations, ProfileAssociation association) {
    if (association.getOrder() == 0) return true;
    if (association.getMasterWrapperId() != null) {
      List<ProfileAssociation> associationsAtMatchBlock = profileAssociations.stream()
        .filter(a -> Objects.equals(a.getMasterWrapperId(), association.getMasterWrapperId())).toList();

      ProfileAssociation associationWithLowestOrder = Collections.min(associationsAtMatchBlock,
        Comparator.comparingInt(ProfileAssociation::getOrder));

      return associationWithLowestOrder == association;
    }
    return false;
  }

  private Future<Errors> validateJobProfileLinkedMatchProfile(List<ProfileAssociation> profileAssociations,
                                                              List<Error> errors, String tenantId) {
    var childActionProfileAssociations = actionProfileAssociations(profileAssociations);
    validateMatchProfilesAssociations(childActionProfileAssociations, errors);

    var matchProfileAssociations = matchProfileAssociations(profileAssociations);
    var matchProfileIds = matchProfileAssociations.stream().map(ProfileAssociation::getDetailProfileId).toList();

    var futures = matchProfileIds.stream()
      .map(id -> matchProfileService.getProfileById(id, false, tenantId))
      .toList();

    return GenericCompositeFuture.all(futures)
      .compose(matchProfiles -> {
        List<MatchProfile> existingMatchProfiles = matchProfiles.result().<Optional<MatchProfile>>list().stream()
          .filter(Optional::isPresent).map(Optional::get).toList();

        var existingMatchProfilesIds = existingMatchProfiles.stream().map(MatchProfile::getId).toList();
        var notFoundIds = matchProfileIds.stream()
          .filter(id -> !existingMatchProfilesIds.contains(id))
          .map(notFoundedId -> String.format("'%s'", notFoundedId)).toList();

        if (!notFoundIds.isEmpty()) {
          var idStr = String.join(", ", notFoundIds);
          LOGGER.warn("validateJobProfileLinkedMatchProfile:: Linked MatchProfiles with ids {} not founded", idStr);
          return Future.failedFuture(new NotFoundException((String.format(LINKED_MATCH_PROFILES_WERE_NOT_FOUND, idStr))));
        }
        return Future.succeededFuture(new Errors().withErrors(errors).withTotalRecords(errors.size()));
      });
  }

  private void validateMatchProfilesAssociations(List<ProfileAssociation> actionProfileAssociations,
                                                           List<Error> errors) {
    LOGGER.debug("validateMatchProfilesAssociations:: Validating JobProfile if its MatchProfile contains ActionProfile");
    if (CollectionUtils.isEmpty(actionProfileAssociations)) {
      LOGGER.warn("validateMatchProfilesAssociations:: Job profile does not contain any associations");
      errors.add(new Error().withMessage("Linked ActionProfile was not found after MatchProfile"));
    }
  }

  private List<ProfileAssociation> matchProfileAssociations(List<ProfileAssociation> profileAssociations) {
    return profileAssociations.stream()
      .filter(profileAssociation -> profileAssociation.getDetailProfileType() == MATCH_PROFILE).toList();
  }
}
