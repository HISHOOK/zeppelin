/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zeppelin.flink;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class FlinkStreamSqlInterpreterTest extends SqlInterpreterTest {

  @Override
  protected FlinkSqlInterrpeter createFlinkSqlInterpreter(Properties properties) {
    return new FlinkStreamSqlInterpreter(properties);
  }

  @Test
  public void testSingleStreamSql() throws IOException, InterpreterException {
    String initStreamScalaScript = IOUtils.toString(getClass().getResource("/init_stream.scala"));
    InterpreterContext context = getInterpreterContext();
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript, context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = getInterpreterContext();
    context.getLocalProperties().put("type", "single");
    context.getLocalProperties().put("template", "Total Count: {1} <br/> {0}");
    result = sqlInterpreter.interpret("select max(rowtime), count(1) " +
            "from log", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.HTML, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("Total Count"));
  }

  @Test
  public void testUpdateStreamSql() throws IOException, InterpreterException {
    String initStreamScalaScript = IOUtils.toString(getClass().getResource("/init_stream.scala"));
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("type", "update");
    result = sqlInterpreter.interpret("select url, count(1) as pv from " +
            "log group by url", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("url\tpv\n"));
  }

  @Test
  public void testAppendStreamSql() throws IOException, InterpreterException {
    String initStreamScalaScript = IOUtils.toString(getClass().getResource("/init_stream.scala"));
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("type", "append");
    result = sqlInterpreter.interpret("select TUMBLE_START(rowtime, INTERVAL '5' SECOND) as " +
            "start_time, url, count(1) as pv from log group by " +
            "TUMBLE(rowtime, INTERVAL '5' SECOND), url", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    List<InterpreterResultMessage> resultMessages = context.out.toInterpreterResultMessage();
    assertEquals(InterpreterResult.Type.TABLE, resultMessages.get(0).getType());
    assertTrue(resultMessages.toString(),
            resultMessages.get(0).getData().contains("url\tpv\n"));
  }

  @Test
  public void testStreamUDF() throws IOException, InterpreterException {
    String initStreamScalaScript = IOUtils.toString(getClass().getResource("/init_stream.scala"));
    InterpreterResult result = flinkInterpreter.interpret(initStreamScalaScript,
            getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = flinkInterpreter.interpret(
            "class MyUpper extends ScalarFunction {\n" +
                    "  def eval(a: String): String = a.toUpperCase()\n" +
                    "}\n" + "stenv.registerFunction(\"myupper\", new MyUpper())", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("type", "update");
    result = sqlInterpreter.interpret("select myupper(url), count(1) as pv from " +
            "log group by url", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
//    assertEquals(InterpreterResult.Type.TABLE,
//            updatedOutput.toInterpreterResultMessage().getType());
//    assertTrue(updatedOutput.toInterpreterResultMessage().getData(),
//            !updatedOutput.toInterpreterResultMessage().getData().isEmpty());
  }

  @Test
  public void testInsertInto() throws InterpreterException, IOException {
    hiveShell.execute("create table source_table (id int, name string)");

    File destDir = Files.createTempDirectory("flink_test").toFile();
    FileUtils.deleteDirectory(destDir);
    InterpreterResult result = sqlInterpreter.interpret(
            "CREATE TABLE dest_table (\n" +
                    "id int,\n" +
                    "name string" +
                    ") WITH (\n" +
                    "'format.field-delimiter'=',',\n" +
                    "'connector.type'='filesystem',\n" +
                    "'format.derive-schema'='true',\n" +
                    "'connector.path'='" + destDir.getAbsolutePath() + "',\n" +
                    "'format.type'='csv'\n" +
                    ");", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = sqlInterpreter.interpret(
            "insert into dest_table select * from source_table",
            getInterpreterContext());

    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
  }
}
