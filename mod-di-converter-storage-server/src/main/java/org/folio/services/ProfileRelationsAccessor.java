package org.folio.services;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.folio.rest.jaxrs.model.ProfileAssociationRecord;

/**
 * Bridges a generated {@code *UpdateDto}'s {@code addedRelations}/{@code deletedRelations} accessors to
 * {@link AbstractProfileService}. The four {@code *UpdateDto} types (job/match/mapping/action) are
 * independently generated RAML/JSON-schema classes with no shared interface, so {@code AbstractProfileService}
 * can't call {@code dto.getAddedRelations()} generically without one of these. Each subclass builds one via
 * {@link #of} from method references to its own DTO type, so a future profile type only needs that one line
 * instead of re-implementing {@code getAddedRelations}/{@code withDeletedRelations}/{@code withAddedRelations}/
 * {@code getProfileAssociationToAdd}/{@code getProfileAssociationToDelete}.
 *
 * @param <D> type of the profile update dto
 */
public interface ProfileRelationsAccessor<D> {

  List<ProfileAssociationRecord> getAddedRelations(D dto);

  List<ProfileAssociationRecord> getDeletedRelations(D dto);

  D withAddedRelations(D dto, List<ProfileAssociationRecord> addedRelations);

  D withDeletedRelations(D dto, List<ProfileAssociationRecord> deletedRelations);

  static <D> ProfileRelationsAccessor<D> of(Function<D, List<ProfileAssociationRecord>> addedRelationsGetter,
                                            Function<D, List<ProfileAssociationRecord>> deletedRelationsGetter,
                                            BiFunction<D, List<ProfileAssociationRecord>, D> addedRelationsSetter,
                                            BiFunction<D, List<ProfileAssociationRecord>, D> deletedRelationsSetter) {
    return new ProfileRelationsAccessor<>() {
      @Override
      public List<ProfileAssociationRecord> getAddedRelations(D dto) {
        return addedRelationsGetter.apply(dto);
      }

      @Override
      public List<ProfileAssociationRecord> getDeletedRelations(D dto) {
        return deletedRelationsGetter.apply(dto);
      }

      @Override
      public D withAddedRelations(D dto, List<ProfileAssociationRecord> addedRelations) {
        return addedRelationsSetter.apply(dto, addedRelations);
      }

      @Override
      public D withDeletedRelations(D dto, List<ProfileAssociationRecord> deletedRelations) {
        return deletedRelationsSetter.apply(dto, deletedRelations);
      }
    };
  }
}
