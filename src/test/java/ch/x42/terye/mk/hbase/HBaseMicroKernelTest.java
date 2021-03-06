package ch.x42.terye.mk.hbase;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

/**
 * Base class for tests testing the HBase MicroKernel.
 * 
 * An instance of Apache HBase 0.94.2 is expected to run at the address
 * configured in 'src/main/resources/hbase-site.xml'.
 */
public abstract class HBaseMicroKernelTest {

    public static final int WAIT_TIMEOUT = HBaseMicroKernel.JOURNAL_UPDATE_TIMEOUT + 100;
    private static Configuration config;
    protected HBaseMicroKernel mk;
    protected HBaseMicroKernelTestScenario scenario;

    @BeforeClass
    public static void setUpClass() throws MasterNotRunningException,
            ZooKeeperConnectionException {
        config = HBaseConfiguration.create();
    }

    /**
     * Initializes a new HBaseMicroKernel and HBaseMicroKernelTestScenario. An
     * empty database is expected.
     */
    @Before
    public void setUp() throws Exception {
        HBaseAdmin admin = new HBaseAdmin(config);
        mk = new HBaseMicroKernel(admin);
        scenario = new HBaseMicroKernelTestScenario(admin);
    }

    /**
     * Clears the database and disposes of the HBaseMicroKernel.
     */
    @After
    public void tearDown() throws IOException {
        mk.dispose(true);
    }

    /**
     * Asserts that the JSON trees represented by the string arguments are
     * equal. Object and property order does not matter but the ordering of
     * array elements does.
     */
    protected void assertJSONEquals(String expected, String actual)
            throws JsonProcessingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(mapper.readTree(expected), mapper.readTree(actual));
    }

}
