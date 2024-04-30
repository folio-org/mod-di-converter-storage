package org.folio.hydration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.graph.ProfileDepthFirstIterator;
import org.folio.graph.edges.MatchRelationshipEdge;
import org.folio.graph.edges.NonMatchRelationshipEdge;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ReactToType;
import org.jgrapht.Graph;
import org.jgrapht.traverse.DepthFirstIterator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.folio.Constants.OBJECT_MAPPER;

/**
 * The ProfileHydration class is responsible for hydrating profiles in FOLIO based on a graph representation.
 * It creates match, action, mapping, and job profiles in FOLIO, establishing the necessary associations between them.
 */
public class ProfileHydration {
  private final static Logger LOGGER = LogManager.getLogger();

  private final FolioClient client;

  public ProfileHydration(FolioClient client) {
    this.client = client;
  }

  /**
   * Hydrates the profiles in FOLIO based on the provided graph.
   *
   * @param repoId The repository ID.
   * @param graph  The graph representing the profiles and their relationships.
   */
  public Optional<Object> hydrate(int repoId, Graph<Profile, RegularEdge> graph) {
    // Generate a unique epoch for the profile names
    LocalDateTime currentDateTime = LocalDateTime.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    String formattedDateTime = currentDateTime.format(formatter);
    @SuppressWarnings("java:S2245")
    String randomLetters = RandomStringUtils.random(5, 'A', 'Z', true, false);
    final String EPOCH = formattedDateTime + "-" + randomLetters;

    // Create match, action, and mapping profiles individually
    Map<Profile, Object> createdObjectsInFolio = new HashMap<>();
    List<Profile> vertexSet = new ArrayList<>(graph.vertexSet());
    vertexSet.sort((v1, v2) -> {
      // Sort vertices based on their type in a specific order
      if (v1.getClass().equals(v2.getClass())) {
        return 0;
      } else if (v1 instanceof MappingProfileNode) {
        return -1;
      } else if (v1 instanceof ActionProfileNode && !(v2 instanceof MappingProfileNode)) {
        return -1;
      } else if (v1 instanceof MatchProfileNode && !(v2 instanceof MappingProfileNode) && !(v2 instanceof ActionProfileNode)) {
        return -1;
      }
      return 1;
    });

    // Create profiles in FOLIO based on their type
    vertexSet.forEach(node -> {
      if (node instanceof MappingProfileNode) {
        MappingProfile mappingProfile = new MappingProfile()
          .withName(String.format("jp-%03d %s %s", repoId, EPOCH, ((MappingProfileNode) node).id()))
          .withIncomingRecordType(EntityType.fromValue(node.getAttributes().get("incomingRecordType")))
          .withExistingRecordType(EntityType.fromValue(node.getAttributes().get("existingRecordType")));
        createProfileInFolio(node, new MappingProfileUpdateDto().withProfile(mappingProfile),
          MappingProfileUpdateDto.class,
          client::createMappingProfile, createdObjectsInFolio);
      } else if (node instanceof ActionProfileNode) {
        ActionProfile actionProfile = new ActionProfile()
          .withName(String.format("jp-%03d %s %s", repoId, EPOCH, ((ActionProfileNode) node).id()))
          .withAction(ActionProfile.Action.fromValue(node.getAttributes().get("action")))
          .withFolioRecord(ActionProfile.FolioRecord.fromValue(node.getAttributes().get("folioRecord")));

        Optional<RegularEdge> edge = graph.edgesOf(node)
          .stream().filter(e -> e.getSource().equals(node))
          .findFirst();
        ActionProfileUpdateDto actionProfileUpdateDto = new ActionProfileUpdateDto()
          .withProfile(actionProfile);
        if (edge.isPresent()) {
          // Add relation to mapping profile if it exists
          Profile target = (Profile) edge.get().getTarget();
          MappingProfileUpdateDto mappingProfile = (MappingProfileUpdateDto) createdObjectsInFolio.get(target);
          actionProfileUpdateDto = actionProfileUpdateDto
            .withAddedRelations(List.of(new ProfileAssociation()
              .withMasterProfileType(ProfileType.ACTION_PROFILE)
              .withDetailProfileType(ProfileType.MAPPING_PROFILE)
              .withDetailProfileId(mappingProfile.getId())));
        }

        createProfileInFolio(node, actionProfileUpdateDto,
          ActionProfileUpdateDto.class,
          client::createActionProfile, createdObjectsInFolio);
      } else if (node instanceof MatchProfileNode) {
        MatchProfile matchProfile = new MatchProfile()
          .withName(String.format("jp-%03d %s %s", repoId, EPOCH, ((MatchProfileNode) node).id()))
          .withIncomingRecordType(EntityType.fromValue(node.getAttributes().get("incomingRecordType")))
          .withExistingRecordType(EntityType.fromValue(node.getAttributes().get("existingRecordType")));
        createProfileInFolio(node, new MatchProfileUpdateDto().withProfile(matchProfile), MatchProfileUpdateDto.class,
          client::createMatchProfile, createdObjectsInFolio);
      }
    });

    // Create job profile with relations
    JobProfileUpdateDto jobProfileUpdateDto = new JobProfileUpdateDto();
    Optional<Profile> jobProfile = graph.vertexSet().stream()
      .filter(key -> graph.incomingEdgesOf(key).isEmpty())
      .findFirst();

    jobProfile.ifPresent(profile -> jobProfileUpdateDto.setProfile(
      new JobProfile()
        .withDataType(JobProfile.DataType.fromValue(profile.getAttributes().get("dataType")))
        .withName(String.format("jp-%03d %s", repoId, EPOCH))
    ));

    if (jobProfile.isEmpty() || jobProfileUpdateDto.getProfile() == null) {
      LOGGER.error("No Job Profile found");
      return Optional.empty();
    }

    List<ProfileAssociation> profileAssociations = new ArrayList<>();
    // Profile associations have to be created in the collection in the right order, utilize depth-first search
    DepthFirstIterator<Profile, RegularEdge> dfsIterator = new ProfileDepthFirstIterator(graph, jobProfile.get());
    while (dfsIterator.hasNext()) {
      Profile vertex = dfsIterator.next();
      LOGGER.debug("Visited vertex: {}", vertex);
      Set<RegularEdge> incomingEdges = graph.incomingEdgesOf(vertex);
      incomingEdges.forEach(edge -> {
        Profile source = (Profile) edge.getSource();
        Profile target = (Profile) edge.getTarget();
        if (source instanceof JobProfileNode) {
          Object targetObjInFolio = createdObjectsInFolio.get(target);
          String targetProfileId = invokeGetId(targetObjInFolio);
          ProfileType targetProfileType = getProfileType(target);
          profileAssociations.add(new ProfileAssociation()
            .withMasterProfileType(ProfileType.JOB_PROFILE)
            .withDetailProfileId(targetProfileId)
            .withDetailProfileType(targetProfileType));
        } else if (target instanceof MappingProfileNode) {
          // Don't create the association between the action profile and the mapping profile
          LOGGER.debug("Skipping profile association for mapping profile: {}", edge);
        } else {
          Object sourceObjInFolio = createdObjectsInFolio.get(source);
          Object targetObjInFolio = createdObjectsInFolio.get(target);
          String sourceProfileId = invokeGetId(sourceObjInFolio);
          String targetProfileId = invokeGetId(targetObjInFolio);
          ProfileType sourceProfileType = getProfileType(source);
          ProfileType targetProfileType = getProfileType(target);
          ProfileAssociation profileAssociation = new ProfileAssociation()
            .withMasterProfileId(sourceProfileId)
            .withMasterProfileType(sourceProfileType)
            .withDetailProfileId(targetProfileId)
            .withDetailProfileType(targetProfileType);
          if (edge instanceof MatchRelationshipEdge) {
            profileAssociation.setReactTo(ReactToType.MATCH);
          } else if (edge instanceof NonMatchRelationshipEdge) {
            profileAssociation.setReactTo(ReactToType.NON_MATCH);
          }
          profileAssociations.add(profileAssociation);
        }
      });
    }

    jobProfileUpdateDto.setAddedRelations(profileAssociations);

    createProfileInFolio(jobProfile.get(), jobProfileUpdateDto, JobProfileUpdateDto.class,
      client::createJobProfile,
      createdObjectsInFolio);

    return Optional.of(createdObjectsInFolio.get(jobProfile.get()));
  }

  /**
   * Creates a profile in FOLIO using the provided update DTO and creator function.
   *
   * @param node                         The profile node.
   * @param updateDto                    The update DTO for the profile.
   * @param updateDtoClassType           The class type of the update DTO.
   * @param creator                      The function to create the profile in FOLIO.
   * @param correspondingObjectsInFolio  The map to store the created objects in FOLIO.
   * @param <U>                          The type of the update DTO.
   */
  private <U> void createProfileInFolio(Profile node, U updateDto, Class<U> updateDtoClassType,
                                        Function<String, Optional<JsonNode>> creator,
                                        Map<Profile, Object> correspondingObjectsInFolio) {
    try {
      String bodyAsString = OBJECT_MAPPER.writeValueAsString(updateDto);
      Optional<JsonNode> jsonNodeOptional = creator.apply(bodyAsString);
      if (jsonNodeOptional.isEmpty()) return;

      U createdUpdateDto = OBJECT_MAPPER.treeToValue(jsonNodeOptional.get(), updateDtoClassType);
      correspondingObjectsInFolio.put(node, createdUpdateDto);
    } catch (JsonProcessingException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  /**
   * Invokes the getId() method on the given object using reflection.
   *
   * @param obj The object to invoke the method on.
   * @return The ID returned by the getId() method.
   */
  public static String invokeGetId(Object obj) {
    try {
      Class<?> clazz = obj.getClass();
      Method getProfileMethod = clazz.getMethod("getId");
      return getProfileMethod.invoke(obj).toString();
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      LOGGER.error(e.getMessage(), e);
      return null;
    }
  }

  /**
   * Returns the ProfileType based on the given Profile object.
   *
   * @param profile The Profile object.
   * @return The corresponding ProfileType.
   */
  public static ProfileType getProfileType(Profile profile) {
    if (profile instanceof MappingProfileNode) {
      return ProfileType.MAPPING_PROFILE;
    } else if (profile instanceof MatchProfileNode) {
      return ProfileType.MATCH_PROFILE;
    } else if (profile instanceof ActionProfileNode) {
      return ProfileType.ACTION_PROFILE;
    } else if (profile instanceof JobProfileNode) {
      return ProfileType.JOB_PROFILE;
    }
    return null;
  }
}
