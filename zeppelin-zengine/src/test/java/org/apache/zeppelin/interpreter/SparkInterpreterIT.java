package org.apache.zeppelin.interpreter;

import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsRequest;
import org.apache.hadoop.yarn.api.protocolrecords.GetApplicationsResponse;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SparkInterpreterIT {

  private static MiniHadoopCluster hadoopCluster;
  private static MiniZeppelin zeppelin;
  private static InterpreterFactory interpreterFactory;
  private static InterpreterSettingManager interpreterSettingManager;

  @BeforeClass
  public static void setUp() throws IOException {
    hadoopCluster = new MiniHadoopCluster();
    hadoopCluster.start();

    zeppelin = new MiniZeppelin();
    zeppelin.start();
    interpreterFactory = zeppelin.getInterpreterFactory();
    interpreterSettingManager = zeppelin.getInterpreterSettingManager();
  }

  @AfterClass
  public static void tearDown() throws IOException {
    if (hadoopCluster != null) {
      hadoopCluster.stop();
    }
    if (zeppelin != null) {
      zeppelin.stop();
    }
  }

  private void testInterpreterBasics() throws IOException {
    // test SparkInterpreter
    interpreterSettingManager.setInterpreterBinding("user1", "note1", interpreterSettingManager.getInterpreterSettingIds());
    Interpreter sparkInterpreter = interpreterFactory.getInterpreter("user1", "note1", "spark.spark");

    InterpreterContext context = new InterpreterContext.Builder().setNoteId("note1").setParagraphId("paragraph_1").getContext();
    InterpreterResult interpreterResult = sparkInterpreter.interpret("sc.version", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code);
    interpreterResult = sparkInterpreter.interpret("sc.range(1,10).sum()", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code);
    assertTrue(interpreterResult.msg.get(0).getData().contains("45"));

    // test PySparkInterpreter
    Interpreter pySparkInterpreter = interpreterFactory.getInterpreter("user1", "note1", "spark.pyspark");
    interpreterResult = pySparkInterpreter.interpret("sqlContext.createDataFrame([(1,'a'),(2,'b')], ['id','name']).registerTempTable('test')", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code);

    // test SparkSQLInterpreter
    Interpreter sqlInterpreter = interpreterFactory.getInterpreter("user1", "note1", "spark.sql");
    interpreterResult = sqlInterpreter.interpret("select count(1) from test", context);
    assertEquals(InterpreterResult.Code.SUCCESS, interpreterResult.code);
    assertEquals(InterpreterResult.Type.TABLE, interpreterResult.message().get(0).getType());
    assertEquals("count(1)\n2\n", interpreterResult.message().get(0).getData());
  }

  @Test
  public void testLocalMode() throws IOException, YarnException {
    InterpreterSetting sparkInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("spark");
    sparkInterpreterSetting.setProperty("master", "local[*]");
    sparkInterpreterSetting.setProperty("SPARK_HOME", System.getenv("SPARK_HOME"));
    sparkInterpreterSetting.setProperty("zeppelin.spark.useHiveContext", "false");

    testInterpreterBasics();

    // no yarn application launched
    GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(0, response.getApplicationList().size());
  }

  @Test
  public void testYarnClientMode() throws IOException, YarnException {
    InterpreterSetting sparkInterpreterSetting = interpreterSettingManager.getInterpreterSettingByName("spark");
    sparkInterpreterSetting.setProperty("master", "yarn-client");
    sparkInterpreterSetting.setProperty("HADOOP_CONF_DIR", hadoopCluster.getConfigPath());
    sparkInterpreterSetting.setProperty("SPARK_HOME", System.getenv("SPARK_HOME"));
    sparkInterpreterSetting.setProperty("zeppelin.spark.useHiveContext", "false");

    testInterpreterBasics();

    // 1 yarn application launched
    GetApplicationsRequest request = GetApplicationsRequest.newInstance(EnumSet.of(YarnApplicationState.RUNNING));
    GetApplicationsResponse response = hadoopCluster.getYarnCluster().getResourceManager().getClientRMService().getApplications(request);
    assertEquals(1, response.getApplicationList().size());
  }

}
