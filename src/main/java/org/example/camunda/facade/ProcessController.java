package org.example.camunda.facade;

import io.camunda.operate.exception.OperateException;
import io.camunda.operate.model.FlowNodeInstance;
import io.camunda.operate.model.ProcessInstance;
import io.camunda.operate.model.ProcessInstanceState;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.example.camunda.dto.ProcessInstanceMigration;
import org.example.camunda.service.OperateService;
import org.example.camunda.utils.BpmnUtils;
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
  private final ZeebeClient zeebe;
  private final OperateService operateService;

  public ProcessController(ZeebeClient client, OperateService operateService) {
    this.zeebe = client;
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
    List<ProcessInstanceMigration> migrations = new ArrayList<>();
    Map<String, Map<Long, List<ProcessInstanceMigration>>> processInstanceMigrations =
        new HashMap<>();
    for (Long processInstanceKey : processInstanceKeys) {
      List<FlowNodeInstance> flowNodes = operateService.listActiveFlowNodes(processInstanceKey);
      if (!flowNodes.isEmpty()) {
        ProcessInstance instance = operateService.getInstance(processInstanceKey);
        Map<String, Object> variables = operateService.getVariables(processInstanceKey);
        variables.put("originalProcessInstanceKey", processInstanceKey);

        if (!processInstanceMigrations.containsKey(instance.getBpmnProcessId())) {
          processInstanceMigrations.put(instance.getBpmnProcessId(), new HashMap<>());
        }
        if (!processInstanceMigrations
            .get(instance.getBpmnProcessId())
            .containsKey(instance.getProcessVersion())) {
          processInstanceMigrations
              .get(instance.getBpmnProcessId())
              .put(instance.getProcessVersion(), new ArrayList<>());
        }
        ProcessInstanceMigration migration =
            new ProcessInstanceMigration()
                .setOriginalProcessInstanceKey(processInstanceKey)
                .setOriginalProcessInstance(instance)
                .setActiveFlowNodes(flowNodes)
                .setVariables(variables);
        processInstanceMigrations
            .get(instance.getBpmnProcessId())
            .get(instance.getProcessVersion())
            .add(migration);
      }
    }
    for (String bpmnProcessId : processInstanceMigrations.keySet()) {
      for (Long version : processInstanceMigrations.get(bpmnProcessId).keySet()) {
        // we will replicate XML definition from existing instance to the new cluster to ensure
        // similarity
        ProcessInstanceMigration firstMigration =
            processInstanceMigrations.get(bpmnProcessId).get(version).get(0);
        String xmlDef =
            operateService.getProcessDefinitionXmlByKey(
                firstMigration.getOriginalProcessInstance().getProcessDefinitionKey());
        String processName =
            BpmnUtils.getProcessName(
                xmlDef, firstMigration.getOriginalProcessInstance().getBpmnProcessId());
        DeploymentEvent deployment =
            zeebe
                .newDeployResourceCommand()
                .addResourceString(xmlDef, StandardCharsets.UTF_8, processName + ".bpmn")
                .send()
                .join();
        int newVersion = deployment.getProcesses().get(0).getVersion();

        for (ProcessInstanceMigration migration :
            processInstanceMigrations.get(bpmnProcessId).get(version)) {
          ProcessInstanceEvent result =
              zeebe
                  .newCreateInstanceCommand()
                  .bpmnProcessId(migration.getOriginalProcessInstance().getBpmnProcessId())
                  .version(newVersion)
                  .startBeforeElement(migration.getActiveFlowNodes().get(0).getFlowNodeId())
                  .variables(migration.getVariables())
                  .send()
                  .join();
          for (int i = 1; i < migration.getActiveFlowNodes().size(); i++) {
            zeebe
                .newModifyProcessInstanceCommand(result.getProcessInstanceKey())
                .activateElement(migration.getActiveFlowNodes().get(i).getFlowNodeId())
                .send()
                .join();
          }
          migration.setNewProcessInstanceKey(result.getProcessInstanceKey());
          migrations.add(migration);
        }
      }
    }
    return migrations;
  }
}
