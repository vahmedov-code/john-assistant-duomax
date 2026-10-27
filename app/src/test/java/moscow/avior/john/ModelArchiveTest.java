package moscow.avior.john;

import java.io.*;
import java.util.zip.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ModelArchiveTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private File zip(String extra, boolean required) throws IOException {
        File zip = temp.newFile();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            if (required) for (String name : ModelArchive.REQUIRED) entry(out,"model/"+name);
            if (extra != null) entry(out,extra);
        }
        return zip;
    }
    private void entry(ZipOutputStream out,String name) throws IOException {
        out.putNextEntry(new ZipEntry(name)); out.write(new byte[]{1,2,3}); out.closeEntry();
    }
    @Test public void installedFilesHaveVerifiedInventory() throws Exception {
        File directory = new File(temp.getRoot(),"installed");
        ModelArchive.extract(zip(null,true),directory,"model");
        assertTrue(ModelArchive.quickReady(directory));
        ModelArchive.verify(directory);
        try (OutputStream out = new FileOutputStream(new File(directory,"am/final.mdl"))) {out.write(9);}
        assertThrows(IOException.class,()->ModelArchive.verify(directory));
    }
    @Test public void refusesZipTraversalAndAbsoluteNames() throws Exception {
        for (String name : new String[]{"model/../../escape","model/../escape","/tmp/escape","model/C:/escape","model/..\\escape"}) {
            File directory = new File(temp.getRoot(),"stage"+System.nanoTime());
            assertThrows(IOException.class,()->ModelArchive.extract(zip(name,true),directory,"model"));
        }
        assertFalse(new File(temp.getRoot(),"escape").exists());
    }
    @Test public void incompleteModelNeverBecomesReady() throws Exception {
        File directory = new File(temp.getRoot(),"incomplete");
        assertThrows(IOException.class,()->ModelArchive.extract(zip("model/README",false),directory,"model"));
        assertFalse(ModelArchive.quickReady(directory));
    }
    @Test public void reservedInventoryCannotBeOverwritten() throws Exception {
        assertThrows(IOException.class,()->ModelArchive.extract(zip("model/.integrity",true),new File(temp.getRoot(),"reserved"),"model"));
    }
    @Test public void validatesDownloadedOfficialModelWhenProvided() throws Exception {
        String path = System.getenv("JOHN_TEST_MODEL_ARCHIVE");
        Assume.assumeTrue(path != null && !path.isEmpty());
        File archive = new File(path);
        assertEquals("961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4",ModelArchive.hash(archive));
        File directory = new File(temp.getRoot(),"official");
        ModelArchive.extract(archive,directory,"vosk-model-small-ru-0.22");
        ModelArchive.verify(directory);
    }
}
