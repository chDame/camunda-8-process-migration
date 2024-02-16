package org.example.camunda.utils;

import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import io.camunda.operate.exception.OperateException;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.inspections.InspectionUtility;
import io.camunda.zeebe.process.test.inspections.model.InspectedProcessInstance;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.apache.commons.io.IOUtils;
import org.dom4j.DocumentException;
import org.example.camunda.service.MigrationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@ZeebeSpringTest
public class BpmnUtilsTest {

  @MockBean private MigrationService migrationService;

  @Autowired private ZeebeClient client;
  @Autowired private ZeebeTestEngine engine;

  @Test
  public void insertMigrationStep()
      throws IOException,
          DocumentException,
          InterruptedException,
          TimeoutException,
          OperateException {
    when(migrationService.restoreInstanceState(any(), any())).thenReturn(true);

    String result =
        MigrationUtils.insertMigrationStep(
            IOUtils.toString(
                this.getClass().getClassLoader().getResourceAsStream("testSubProcess.bpmn"),
                StandardCharsets.UTF_8));
    client
        .newDeployResourceCommand()
        .addResourceString(result, StandardCharsets.UTF_8, "process.bpmn")
        .send()
        .join();
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("humanTaskProcess")
        .latestVersion()
        .send()
        .join();
    engine.waitForIdleState(Duration.ofSeconds(1));
    InspectedProcessInstance processInstance =
        InspectionUtility.findProcessInstances().findLastProcessInstance().get();
    assertThat(processInstance).isStarted();
    Mockito.verify(migrationService).restoreInstanceState(any(), any());
    assertThat(processInstance).isNotWaitingAtElements("migrate_evt");
  }
}
