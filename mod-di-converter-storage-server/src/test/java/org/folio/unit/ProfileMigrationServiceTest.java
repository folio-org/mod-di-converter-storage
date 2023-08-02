package org.folio.unit;

import io.vertx.core.Context;
import org.folio.dao.snapshot.ProfileSnapshotItem;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.services.migration.ProfileMigrationService;
import org.folio.spring.SpringContextUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.MAPPING_PROFILE;
import static org.folio.unit.AbstractUnitTest.vertx;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Component
public class ProfileMigrationServiceTest {
  @Autowired
  private ProfileMigrationService profileMigrationService;

  @Before
  public void setUp() {
    Context vertxContext = vertx.getOrCreateContext();
    SpringContextUtil.init(vertx, vertxContext, ApplicationTestConfig.class);
    SpringContextUtil.autowireDependencies(this, vertxContext);
  }

  @Test
  public void shouldSortProfileInValidOrder() {
    ProfileSnapshotItem firstMappingProfile = new ProfileSnapshotItem();
    firstMappingProfile.setAssociationId("9f87631c-c5a7-425e-8e06-132dae55eac4");
    firstMappingProfile.setMasterId("2f31c3c7-1935-41d9-9f92-94a50124285c");
    firstMappingProfile.setDetailId("61e7ff47-69cd-484f-be69-15549aabe0c1");
    firstMappingProfile.setDetailType(MAPPING_PROFILE);
    firstMappingProfile.setDetail("name: name -> Test Create Item");
    firstMappingProfile.setOrder(0);

    ProfileSnapshotItem secondMappingProfile = new ProfileSnapshotItem();
    secondMappingProfile.setAssociationId("b8ab68be-2ac0-4264-9ec1-3f0734611c7c");
    secondMappingProfile.setMasterId("1d812bdf-b47d-4d24-a2b7-e12b6b805fa6");
    secondMappingProfile.setDetailId("68b3cdeb-37f7-4a91-9ab6-46fb7fc665ed");
    secondMappingProfile.setDetailType(MAPPING_PROFILE);
    secondMappingProfile.setDetail("name: name -> Test Create Instance");
    secondMappingProfile.setOrder(0);

    ProfileSnapshotItem firstActionProfile = new ProfileSnapshotItem();
    firstActionProfile.setAssociationId("bef4a0e6-4d3c-4acf-8d4f-c8619e49180c");
    firstActionProfile.setMasterId("717f3b5c-6447-44a9-89f4-212b24c8d86e");
    firstActionProfile.setDetailId("1d812bdf-b47d-4d24-a2b7-e12b6b805fa6");
    firstActionProfile.setDetailType(ACTION_PROFILE);
    firstActionProfile.setDetail("name: name -> Test Create Instance");
    firstActionProfile.setOrder(0);

    ProfileSnapshotItem jobProfile = new ProfileSnapshotItem();
    jobProfile.setAssociationId("717f3b5c-6447-44a9-89f4-212b24c8d86e");
    jobProfile.setDetailId("717f3b5c-6447-44a9-89f4-212b24c8d86e");
    jobProfile.setDetailType(JOB_PROFILE);
    jobProfile.setDetail("name: name -> TestItem");
    jobProfile.setOrder(0);

    ProfileSnapshotItem thirdMappingProfile = new ProfileSnapshotItem();
    thirdMappingProfile.setAssociationId("81fa1532-c15d-441f-9de9-9b74190d70f7");
    thirdMappingProfile.setMasterId("fc4716fc-a005-43ed-8cdf-22b88c00ff74");
    thirdMappingProfile.setDetailId("8ab410b2-abef-44d4-930e-3b051f4098b4");
    thirdMappingProfile.setDetailType(MAPPING_PROFILE);
    thirdMappingProfile.setDetail("name: name -> Test Create Holdings");
    thirdMappingProfile.setOrder(0);

    ProfileSnapshotItem secondActionProfile = new ProfileSnapshotItem();
    secondActionProfile.setAssociationId("ea5fc755-386c-4ea1-8fb7-8d35a6feadf6");
    secondActionProfile.setMasterId("717f3b5c-6447-44a9-89f4-212b24c8d86e");
    secondActionProfile.setDetailId("fc4716fc-a005-43ed-8cdf-22b88c00ff74");
    secondActionProfile.setDetailType(ACTION_PROFILE);
    secondActionProfile.setDetail("name: name -> Test Create Holdings");
    secondActionProfile.setOrder(1);

    ProfileSnapshotItem thirdActionProfile = new ProfileSnapshotItem();
    thirdActionProfile.setAssociationId("953e9110-906c-4b84-9187-893a1100be14");
    thirdActionProfile.setMasterId("717f3b5c-6447-44a9-89f4-212b24c8d86e");
    thirdActionProfile.setDetailId("2f31c3c7-1935-41d9-9f92-94a50124285c");
    thirdActionProfile.setDetailType(ACTION_PROFILE);
    thirdActionProfile.setDetail("name: name -> Test Create Item");
    thirdActionProfile.setOrder(2);

    List<ProfileSnapshotItem> unsortedSnapshot = new ArrayList<>();
    unsortedSnapshot.add(firstMappingProfile);
    unsortedSnapshot.add(secondMappingProfile);
    unsortedSnapshot.add(firstActionProfile);
    unsortedSnapshot.add(jobProfile);
    unsortedSnapshot.add(thirdMappingProfile);
    unsortedSnapshot.add(secondActionProfile);
    unsortedSnapshot.add(thirdActionProfile);

    List<ProfileAssociation> profileAssociations = profileMigrationService.sortSnapshots(unsortedSnapshot);
    assertNotNull(profileAssociations);
    assertEquals(profileAssociations.get(0).getDetailProfileType(), ProfileType.JOB_PROFILE);
    assertEquals(profileAssociations.get(1).getDetailProfileType(), ProfileType.ACTION_PROFILE);
    assertEquals(profileAssociations.get(2).getDetailProfileType(), ProfileType.MAPPING_PROFILE);
    assertEquals(profileAssociations.get(3).getDetailProfileType(), ProfileType.ACTION_PROFILE);
    assertEquals(profileAssociations.get(4).getDetailProfileType(), ProfileType.MAPPING_PROFILE);
    assertEquals(profileAssociations.get(5).getDetailProfileType(), ProfileType.ACTION_PROFILE);
    assertEquals(profileAssociations.get(6).getDetailProfileType(), ProfileType.MAPPING_PROFILE);
  }
}
