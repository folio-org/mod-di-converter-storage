package org.folio.services.fieldprotectionsettings;

import io.vertx.core.Future;
import org.folio.dao.fieldprotectionsettings.MarcFieldProtectionSettingsDao;
import org.folio.rest.jaxrs.model.MarcFieldProtectionSetting;
import org.folio.rest.jaxrs.model.MarcFieldProtectionSettingsCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.util.Optional;
import java.util.UUID;

@Service
public class MarcFieldProtectionSettingsServiceImpl implements MarcFieldProtectionSettingsService {
  private static final String MARC_PROTECTION_SETTING_NOT_FOUND = "MARC field protection with id '%s' was not found";
  private static final String CANNOT_PERFORM_OPERATION_ON_SYSTEM_FIELD = "MARC field protection setting with source SYSTEM cannot be %s";

  @Autowired
  private MarcFieldProtectionSettingsDao fieldProtectionSettingsDao;

  @Override
  public Future<MarcFieldProtectionSettingsCollection> getMarcFieldProtectionSettings(String query, int offset, int limit, String tenantId) {
    return fieldProtectionSettingsDao.getAll(query, offset, limit, tenantId);
  }

  @Override
  public Future<Optional<MarcFieldProtectionSetting>> getMarcFieldProtectionSettingById(String id, String tenantId) {
    return fieldProtectionSettingsDao.getById(id, tenantId);
  }

  @Override
  public Future<MarcFieldProtectionSetting> addMarcFieldProtectionSetting(MarcFieldProtectionSetting marcFieldProtectionSetting, String tenantId) {
    marcFieldProtectionSetting.setId(UUID.randomUUID().toString());
    return fieldProtectionSettingsDao.save(marcFieldProtectionSetting, tenantId).map(marcFieldProtectionSetting);
  }

  @Override
  public Future<MarcFieldProtectionSetting> updateMarcFieldProtectionSetting(MarcFieldProtectionSetting marcFieldProtectionSetting, String tenantId) {
    return getMarcFieldProtectionSettingById(marcFieldProtectionSetting.getId(), tenantId)
      .compose(optionalSetting -> {
        if (optionalSetting.isPresent()) {
          if (MarcFieldProtectionSetting.Source.SYSTEM == optionalSetting.get().getSource()) {
            return Future.failedFuture(new BadRequestException(String.format(CANNOT_PERFORM_OPERATION_ON_SYSTEM_FIELD, "updated")));
          }
          return fieldProtectionSettingsDao.update(marcFieldProtectionSetting, tenantId);
        }
        return Future.failedFuture(new NotFoundException(String.format(MARC_PROTECTION_SETTING_NOT_FOUND, marcFieldProtectionSetting.getId())));
      });
  }

  @Override
  public Future<Boolean> deleteMarcFieldProtectionSetting(String id, String tenantId) {
    return getMarcFieldProtectionSettingById(id, tenantId)
      .compose(optionalSetting -> {
        if (optionalSetting.isPresent()) {
          if (MarcFieldProtectionSetting.Source.SYSTEM == optionalSetting.get().getSource()) {
            return Future.failedFuture(new BadRequestException(String.format(CANNOT_PERFORM_OPERATION_ON_SYSTEM_FIELD, "deleted")));
          }
          return fieldProtectionSettingsDao.delete(id, tenantId);
        }
        return Future.failedFuture(new NotFoundException(String.format(MARC_PROTECTION_SETTING_NOT_FOUND, id)));
      });
  }
}
