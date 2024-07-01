package org.folio.recordgen;


import lombok.Builder;
import org.folio.http.FolioClient;

@Builder
public record RoundRobinIdentifier(String instanceId, String holdingsId, String itemId, String authorityId,
                                   String sourceRecordId) {

  public String forRecordType(FolioClient.ExportRecordType recordType){
    return switch (recordType){
      case INSTANCE -> instanceId;
      case HOLDINGS -> holdingsId;
      case ITEM -> itemId;
      case AUTHORITY -> authorityId;
    };
  }
}
