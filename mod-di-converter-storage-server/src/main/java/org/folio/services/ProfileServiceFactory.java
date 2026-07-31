package org.folio.services;

import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileCollection;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileCollection;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileCollection;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileCollection;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Provides lazily resolved {@link ProfileService} beans.
 *
 * <p>{@link ActionProfileServiceImpl} and {@link MappingProfileServiceImpl} depend on each other,
 * so this factory breaks that circular dependency: it is injected into both services and, in turn,
 * uses {@link ObjectProvider} (which defers actual bean lookup until {@code getObject()} is called)
 * to obtain the other service only when it is actually needed at runtime.
 */
@Component
public class ProfileServiceFactory {

  private final ObjectProvider<ActionProfileServiceImpl> actionProfileServiceProvider;
  private final ObjectProvider<MappingProfileServiceImpl> mappingProfileServiceProvider;
  private final ObjectProvider<MatchProfileServiceImpl> matchProfileServiceProvider;
  private final ObjectProvider<JobProfileServiceImpl> jobProfileServiceProvider;

  public ProfileServiceFactory(ObjectProvider<ActionProfileServiceImpl> actionProfileServiceProvider,
                               ObjectProvider<MappingProfileServiceImpl> mappingProfileServiceProvider,
                               ObjectProvider<MatchProfileServiceImpl> matchProfileServiceProvider,
                               ObjectProvider<JobProfileServiceImpl> jobProfileServiceProvider) {
    this.actionProfileServiceProvider = actionProfileServiceProvider;
    this.mappingProfileServiceProvider = mappingProfileServiceProvider;
    this.matchProfileServiceProvider = matchProfileServiceProvider;
    this.jobProfileServiceProvider = jobProfileServiceProvider;
  }

  public ProfileService<ActionProfile, ActionProfileCollection, ActionProfileUpdateDto> getActionProfileService() {
    return actionProfileServiceProvider.getObject();
  }

  public ProfileService<MappingProfile, MappingProfileCollection, MappingProfileUpdateDto> getMappingProfileService() {
    return mappingProfileServiceProvider.getObject();
  }

  public ProfileService<MatchProfile, MatchProfileCollection, MatchProfileUpdateDto> getMatchProfileService() {
    return matchProfileServiceProvider.getObject();
  }

  public ProfileService<JobProfile, JobProfileCollection, JobProfileUpdateDto> getJobProfileService() {
    return jobProfileServiceProvider.getObject();
  }
}
