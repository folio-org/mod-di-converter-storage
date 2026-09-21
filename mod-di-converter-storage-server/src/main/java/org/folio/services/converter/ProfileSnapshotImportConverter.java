package org.folio.services.converter;

import java.util.List;
import java.util.Optional;
import org.folio.rest.jaxrs.model.ProfileSnapshotImport;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.springframework.stereotype.Component;

@Component
public class ProfileSnapshotImportConverter {

  public ProfileSnapshotWrapper convert(ProfileSnapshotImport source) {
    if (source == null) {
      return null;
    }
    return new ProfileSnapshotWrapper()
      .withId(source.getId())
      .withProfileId(source.getProfileId())
      .withProfileWrapperId(source.getProfileWrapperId())
      .withContentType(source.getContentType())
      .withReactTo(source.getReactTo())
      .withContent(source.getContent())
      .withOrder(source.getOrder())
      .withChildSnapshotWrappers(Optional.ofNullable(source.getChildSnapshotWrappers()).orElse(List.of()).stream()
        .map(this::convert)
        .toList());
  }
}
