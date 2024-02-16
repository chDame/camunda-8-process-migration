package org.example.camunda.facade;

import io.camunda.operate.exception.OperateException;
import io.camunda.operate.model.FlowNodeInstance;
import io.camunda.operate.model.ProcessInstance;
import io.camunda.operate.model.ProcessInstanceState;
import java.util.List;
import org.example.camunda.dto.ProcessInstanceMigration;
import org.example.camunda.service.MigrationService;
import org.example.camunda.service.OperateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/process")
public class ProcessController {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessController.class);
  private final OperateService operateService;
  private final MigrationService migrationService;

  public ProcessController(MigrationService migrationService, OperateService operateService) {
    this.migrationService = migrationService;
    this.operateService = operateService;
  }

  @GetMapping("/instances/{state}")
  public List<ProcessInstance> instances(@PathVariable ProcessInstanceState state)
      throws OperateException {
    return operateService.listInstances(state);
  }

  @GetMapping("/instances/{key}/flowNodes")
  public List<FlowNodeInstance> activeFlowNode(@PathVariable Long key) throws OperateException {
    return operateService.listActiveFlowNodes(key);
  }

  @PostMapping("/instances/duplicate")
  public List<ProcessInstanceMigration> duplicateInstances(
      @RequestBody List<Long> processInstanceKeys) throws OperateException {
    return migrationService.copyMainInstances(processInstanceKeys);
  }
}
