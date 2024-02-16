package org.example.camunda.worker;

import io.camunda.operate.exception.OperateException;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.camunda.zeebe.spring.client.annotation.Variable;
import org.example.camunda.service.MigrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MigrationWorker {

  @Autowired MigrationService migrationService;
  @Autowired private ZeebeClient zeebeClient;

  @JobWorker(autoComplete = false)
  public void migrate(ActivatedJob job, @Variable Long childInstanceKey) throws OperateException {
    if (migrationService.restoreInstanceState(job, childInstanceKey)) {
      zeebeClient
          .newModifyProcessInstanceCommand(job.getProcessInstanceKey())
          .terminateElement(job.getElementInstanceKey())
          .send();
    }
  }
}
