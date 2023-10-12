package org.folio.dao.dto;

public class ProfileIdAndName {
  private final String jobProfileId;
  private final String jobProfileName;

  public ProfileIdAndName(String jobProfileId, String jobProfileName) {
    this.jobProfileId = jobProfileId;
    this.jobProfileName = jobProfileName;
  }

  public String getJobProfileId() {
    return jobProfileId;
  }

  public String getJobProfileName() {
    return jobProfileName;
  }
}
