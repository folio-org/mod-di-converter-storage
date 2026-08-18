package org.folio.dao.association;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;

import java.util.Optional;
import java.util.UUID;
import org.folio.dao.ProfileDao;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileCollection;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ProfileWrapper;
import org.folio.support.AbstractUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProfileWrapperDaoTest extends AbstractUnitTest {

  @Autowired
  private ProfileWrapperDao dao;

  @Autowired
  private ProfileDao<ActionProfile, ActionProfileCollection> actionProfileDao;

  @Test
  @DisplayName("should return empty when no wrapper exists for given id")
  void shouldReturnEmptyProfileWrappersIfNoItemsExist() {
    // arrange
    String wrapperId = UUID.randomUUID().toString();

    // act
    Optional<ProfileWrapper> result = await(dao.getProfileWrapperById(wrapperId, TENANT_ID));

    // assert
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("should return newly created profile wrapper by id")
  void shouldReturnNewlyCreatedProfileWrapper() {
    // arrange
    String actionProfileId = UUID.randomUUID().toString();
    String wrapperId = UUID.randomUUID().toString();
    ProfileWrapper wrapper = new ProfileWrapper()
      .withProfileId(actionProfileId)
      .withProfileType(ProfileType.ACTION_PROFILE)
      .withId(wrapperId);

    await(actionProfileDao.saveProfile(new ActionProfile().withId(actionProfileId), TENANT_ID));
    await(dao.save(wrapper, TENANT_ID));

    // act
    Optional<ProfileWrapper> result = await(dao.getProfileWrapperById(wrapperId, TENANT_ID));

    // assert
    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(wrapperId);
  }

  @Test
  @DisplayName("should delete newly created profile wrapper")
  void shouldDeleteNewlyCreatedProfileWrapper() {
    // arrange
    String actionProfileId = UUID.randomUUID().toString();
    String wrapperId = UUID.randomUUID().toString();
    ProfileWrapper wrapper = new ProfileWrapper()
      .withProfileId(actionProfileId)
      .withProfileType(ProfileType.ACTION_PROFILE)
      .withId(wrapperId);

    await(actionProfileDao.saveProfile(new ActionProfile().withId(actionProfileId), TENANT_ID));
    await(dao.save(wrapper, TENANT_ID));

    // act
    boolean deleted = await(dao.deleteById(wrapperId, TENANT_ID));
    Optional<ProfileWrapper> afterDelete = await(dao.getProfileWrapperById(wrapperId, TENANT_ID));

    // assert
    assertThat(deleted).isTrue();
    assertThat(afterDelete).isEmpty();
  }
}
