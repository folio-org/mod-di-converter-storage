package org.folio.exports;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.marc4j.MarcStreamWriter;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class MarcCircularStreamTest {
  private static final MarcFactory MARC_FACTORY = MarcFactory.newInstance();

  @Rule
  public TemporaryFolder tempDir = new TemporaryFolder();

  @Test
  public void singleArgumentFileConstructorInitializesReader() throws Exception {
    Path marcFile = tempDir.newFile("records.mrc").toPath();
    Record record = MARC_FACTORY.newRecord();
    record.addVariableField(MARC_FACTORY.newControlField("001", "one"));
    try (OutputStream outputStream = Files.newOutputStream(marcFile)) {
      MarcStreamWriter writer = new MarcStreamWriter(outputStream, "UTF-8");
      writer.write(record);
      writer.close();
    }

    try (MarcCircularStream stream = new MarcCircularStream(marcFile.toString())) {
      Record next = stream.nextRecord();

      assertEquals("one", next.getControlNumber());
    }
  }
}
