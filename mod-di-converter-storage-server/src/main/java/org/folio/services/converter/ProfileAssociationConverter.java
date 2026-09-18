package org.folio.services.converter;

import com.google.common.base.Converter;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileAssociationRecord;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class ProfileAssociationConverter extends Converter<ProfileAssociationRecord, ProfileAssociation> {

  @Override
  protected ProfileAssociation doForward(ProfileAssociationRecord source) {
    return new ProfileAssociation()
      .withId(source.getId())
      .withMasterProfileId(source.getMasterProfileId())
      .withDetailProfileId(source.getDetailProfileId())
      .withOrder(source.getOrder())
      .withReactTo(source.getReactTo())
      .withDetail(source.getDetail())
      .withTriggered(source.getTriggered())
      .withMasterProfileType(source.getMasterProfileType())
      .withDetailProfileType(source.getDetailProfileType())
      .withMasterWrapperId(source.getMasterWrapperId())
      .withDetailWrapperId(source.getDetailWrapperId())
      .withJobProfileId(source.getJobProfileId());
  }

  @Override
  protected ProfileAssociationRecord doBackward(ProfileAssociation source) {
    return new ProfileAssociationRecord()
      .withId(source.getId())
      .withMasterProfileId(source.getMasterProfileId())
      .withDetailProfileId(source.getDetailProfileId())
      .withOrder(source.getOrder())
      .withReactTo(source.getReactTo())
      .withDetail(source.getDetail())
      .withTriggered(source.getTriggered())
      .withMasterProfileType(source.getMasterProfileType())
      .withDetailProfileType(source.getDetailProfileType())
      .withMasterWrapperId(source.getMasterWrapperId())
      .withDetailWrapperId(source.getDetailWrapperId())
      .withJobProfileId(source.getJobProfileId());
  }
}
