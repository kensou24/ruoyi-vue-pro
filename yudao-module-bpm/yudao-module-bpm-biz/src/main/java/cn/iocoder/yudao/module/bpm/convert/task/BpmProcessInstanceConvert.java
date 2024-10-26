package cn.iocoder.yudao.module.bpm.convert.task;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.MapUtils;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.process.BpmProcessDefinitionRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceBpmnModelViewRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmCategoryDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceApproveReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceRejectReqDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;

/**
 * 流程实例 Convert
 *
 * @author 芋道源码
 */
@Mapper
public interface BpmProcessInstanceConvert {

    BpmProcessInstanceConvert INSTANCE = Mappers.getMapper(BpmProcessInstanceConvert.class);

    default PageResult<BpmProcessInstanceRespVO> buildProcessInstancePage(PageResult<HistoricProcessInstance> pageResult,
                                                                          Map<String, ProcessDefinition> processDefinitionMap,
                                                                          Map<String, BpmCategoryDO> categoryMap,
                                                                          Map<String, List<Task>> taskMap,
                                                                          Map<Long, AdminUserRespDTO> userMap,
                                                                          Map<Long, DeptRespDTO> deptMap) {
        PageResult<BpmProcessInstanceRespVO> vpPageResult = BeanUtils.toBean(pageResult, BpmProcessInstanceRespVO.class);
        for (int i = 0; i < pageResult.getList().size(); i++) {
            BpmProcessInstanceRespVO respVO = vpPageResult.getList().get(i);
            respVO.setStatus(FlowableUtils.getProcessInstanceStatus(pageResult.getList().get(i)));
            MapUtils.findAndThen(processDefinitionMap, respVO.getProcessDefinitionId(),
                    processDefinition -> respVO.setCategory(processDefinition.getCategory())
                            .setProcessDefinition(BeanUtils.toBean(processDefinition, BpmProcessDefinitionRespVO.class)));
            MapUtils.findAndThen(categoryMap, respVO.getCategory(), category -> respVO.setCategoryName(category.getName()));
            respVO.setTasks(BeanUtils.toBean(taskMap.get(respVO.getId()), BpmProcessInstanceRespVO.Task.class));
            // user
            if (userMap != null) {
                AdminUserRespDTO startUser = userMap.get(NumberUtils.parseLong(pageResult.getList().get(i).getStartUserId()));
                if (startUser != null) {
                    respVO.setStartUser(BeanUtils.toBean(startUser, BpmProcessInstanceRespVO.User.class));
                    MapUtils.findAndThen(deptMap, startUser.getDeptId(), dept -> respVO.getStartUser().setDeptName(dept.getName()));
                }
            }
        }
        return vpPageResult;
    }

    default BpmProcessInstanceRespVO buildProcessInstance(HistoricProcessInstance processInstance,
                                                          ProcessDefinition processDefinition,
                                                          BpmProcessDefinitionInfoDO processDefinitionExt,
                                                          AdminUserRespDTO startUser,
                                                          DeptRespDTO dept) {
        BpmProcessInstanceRespVO respVO = BeanUtils.toBean(processInstance, BpmProcessInstanceRespVO.class);
        respVO.setStatus(FlowableUtils.getProcessInstanceStatus(processInstance));
        respVO.setFormVariables(FlowableUtils.getProcessInstanceFormVariable(processInstance));
        // definition
        respVO.setProcessDefinition(BeanUtils.toBean(processDefinition, BpmProcessDefinitionRespVO.class));
        copyTo(processDefinitionExt, respVO.getProcessDefinition());
        // user
        if (startUser != null) {
            respVO.setStartUser(BeanUtils.toBean(startUser, BpmProcessInstanceRespVO.User.class));
            if (dept != null) {
                respVO.getStartUser().setDeptName(dept.getName());
            }
        }
        return respVO;
    }

    @Mapping(source = "from.id", target = "to.id", ignore = true)
    void copyTo(BpmProcessDefinitionInfoDO from, @MappingTarget BpmProcessDefinitionRespVO to);

    default BpmProcessInstanceStatusEvent buildProcessInstanceStatusEvent(Object source, ProcessInstance instance, Integer status) {
        return new BpmProcessInstanceStatusEvent(source).setId(instance.getId()).setStatus(status)
                .setProcessDefinitionKey(instance.getProcessDefinitionKey()).setBusinessKey(instance.getBusinessKey());
    }

    default BpmMessageSendWhenProcessInstanceApproveReqDTO buildProcessInstanceApproveMessage(ProcessInstance instance) {
        return new BpmMessageSendWhenProcessInstanceApproveReqDTO()
                .setStartUserId(NumberUtils.parseLong(instance.getStartUserId()))
                .setProcessInstanceId(instance.getId())
                .setProcessInstanceName(instance.getName());
    }

    default BpmMessageSendWhenProcessInstanceRejectReqDTO buildProcessInstanceRejectMessage(ProcessInstance instance, String reason) {
        return new BpmMessageSendWhenProcessInstanceRejectReqDTO()
            .setProcessInstanceName(instance.getName())
            .setProcessInstanceId(instance.getId())
            .setReason(reason)
            .setStartUserId(NumberUtils.parseLong(instance.getStartUserId()));
    }

    default BpmProcessInstanceBpmnModelViewRespVO buildProcessInstanceBpmnModelView(HistoricProcessInstance processInstance,
                                                                                    List<HistoricTaskInstance> taskInstances,
                                                                                    BpmnModel bpmnModel,
                                                                                    Set<String> unfinishedTaskActivityIds,
                                                                                    Set<String> finishedTaskActivityIds,
                                                                                    Set<String> finishedSequenceFlowActivityIds,
                                                                                    Set<String> rejectTaskActivityIds,
                                                                                    Map<Long, AdminUserRespDTO> userMap,
                                                                                    Map<Long, DeptRespDTO> deptMap) {
        BpmProcessInstanceBpmnModelViewRespVO respVO = new BpmProcessInstanceBpmnModelViewRespVO();
        // 基本信息
        respVO.setProcessInstance(BeanUtils.toBean(processInstance, BpmProcessInstanceRespVO.class, o -> o
                        .setStatus(FlowableUtils.getProcessInstanceStatus(processInstance)))
                        .setStartUser(buildUser(processInstance.getStartUserId(), userMap, deptMap)));
        respVO.setTasks(convertList(taskInstances, task -> BeanUtils.toBean(task, BpmTaskRespVO.class)
                .setStatus(FlowableUtils.getTaskStatus(task)).setReason(FlowableUtils.getTaskReason(task))
                .setAssigneeUser(buildUser(task.getAssignee(), userMap, deptMap))
                .setOwnerUser(buildUser(task.getOwner(), userMap, deptMap))));
        respVO.setBpmnXml(BpmnModelUtils.getBpmnXml(bpmnModel));
        // 进度信息
        respVO.setUnfinishedTaskActivityIds(unfinishedTaskActivityIds)
                .setFinishedTaskActivityIds(finishedTaskActivityIds)
                .setFinishedSequenceFlowActivityIds(finishedSequenceFlowActivityIds)
                .setRejectedTaskActivityIds(rejectTaskActivityIds);
        return respVO;
    }

    default BpmProcessInstanceRespVO.User buildUser(String userId,
                                                    Map<Long, AdminUserRespDTO> userMap,
                                                    Map<Long, DeptRespDTO> deptMap) {
        if (StrUtil.isBlank(userId)) {
            return null;
        }
        AdminUserRespDTO user = userMap.get(NumberUtils.parseLong(userId));
        if (user == null) {
            return null;
        }
        BpmProcessInstanceRespVO.User userVO = BeanUtils.toBean(user, BpmProcessInstanceRespVO.User.class);
        DeptRespDTO dept = user.getDeptId() != null ? deptMap.get(user.getDeptId()) : null;
        if (dept != null) {
            userVO.setDeptName(dept.getName());
        }
        return userVO;
    }

    default BpmApprovalDetailRespVO.ApprovalTaskInfo buildApprovalTaskInfo(HistoricTaskInstance task) {
        if (task == null) {
            return null;
        }
        return BeanUtils.toBean(task, BpmApprovalDetailRespVO.ApprovalTaskInfo.class)
                .setStatus(FlowableUtils.getTaskStatus(task)).setReason(FlowableUtils.getTaskReason(task));
    }

}
