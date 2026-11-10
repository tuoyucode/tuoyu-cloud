package org.tuoyu.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.andon.domain.bo.MesAndonEventBo;
import org.tuoyu.mes.andon.enums.AndonLevel;
import org.tuoyu.mes.andon.enums.AndonType;
import org.tuoyu.mes.andon.service.IMesAndonEventService;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.common.StateGuard;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.equipment.domain.MesCheckItem;
import org.tuoyu.mes.equipment.domain.MesCheckRecord;
import org.tuoyu.mes.equipment.domain.MesCheckRecordDetail;
import org.tuoyu.mes.equipment.domain.bo.MesCheckAndonBo;
import org.tuoyu.mes.equipment.domain.bo.MesCheckRecordBo;
import org.tuoyu.mes.equipment.domain.bo.MesCheckRecordDetailBo;
import org.tuoyu.mes.equipment.domain.vo.MesCheckRecordDetailVo;
import org.tuoyu.mes.equipment.domain.vo.MesCheckRecordVo;
import org.tuoyu.mes.equipment.enums.CheckItemResult;
import org.tuoyu.mes.equipment.enums.CheckRecordStatus;
import org.tuoyu.mes.equipment.enums.CheckResult;
import org.tuoyu.mes.equipment.mapper.MesCheckRecordDetailMapper;
import org.tuoyu.mes.equipment.mapper.MesCheckRecordMapper;
import org.tuoyu.mes.equipment.service.IMesCheckItemService;
import org.tuoyu.mes.equipment.service.IMesCheckRecordService;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.material.support.SafeLookup;
import org.tuoyu.mes.material.support.UserNicknames;
import org.tuoyu.system.api.RemoteUserService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 点检记录
 * <p>
 * 列表、导出与按 ID 的查看、作废、发起安灯都限定在当前用户的数据权限内（按创建部门与创建者），不在其内的记录按不存在处理；
 * 读取设备与点检项目是内部校验，不加数据权限。状态变化都带原状态条件，并发操作时只有一个成功。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesCheckRecordServiceImpl implements IMesCheckRecordService {

    private static final String OBJECT = "点检记录";

    private static final String YES = "Y";

    private static final String NO = "N";

    private static final int MAX_TITLE = 128;

    private static final int MAX_CONTENT = 500;

    private final MesCheckRecordMapper recordMapper;
    private final MesCheckRecordDetailMapper detailMapper;
    private final MesDeviceMapper deviceMapper;
    private final IMesCheckItemService checkItemService;
    private final IMesAndonEventService andonService;
    private final IMesCodeService codeService;
    private final IMesStatusLogService statusLogService;
    private final RemoteUserService userService;

    private Clock clock = Clock.systemDefaultZone();

    /**
     * 测试中替换时钟
     */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public MesCheckRecordVo queryById(Long checkRecordId) {
        MesCheckRecordVo vo = BeanMapper.map(requireRecord(checkRecordId), MesCheckRecordVo.class);
        fill(List.of(vo));
        vo.setDetails(BeanMapper.mapList(details(checkRecordId), MesCheckRecordDetailVo.class));
        return vo;
    }

    @Override
    public PageResult<MesCheckRecordVo> queryPageList(MesCheckRecordBo bo, PageParam pageParam) {
        PageResult<MesCheckRecordVo> result = PageResults.of(recordMapper.selectPageInScope(
            pageParam.toPage(MesCheckRecord.class), buildQueryWrapper(bo, !pageParam.hasSort())), MesCheckRecordVo.class);
        fill(result.getItems());
        return result;
    }

    @Override
    public List<MesCheckRecordVo> queryList(MesCheckRecordBo bo) {
        List<MesCheckRecordVo> vos = BeanMapper.mapList(recordMapper.selectListInScope(buildQueryWrapper(bo, true)),
            MesCheckRecordVo.class);
        fill(vos);
        Map<Long, String> names = UserNicknames.of(userService, MesLookups.ids(vos, MesCheckRecordVo::getCheckerUserId));
        vos.forEach(vo -> vo.setCheckerName(SafeLookup.get(names, vo.getCheckerUserId())));
        return vos;
    }

    @Override
    public long countList(MesCheckRecordBo bo) {
        return recordMapper.countInScope(buildQueryWrapper(bo, false));
    }

    private LambdaQueryWrapper<MesCheckRecord> buildQueryWrapper(MesCheckRecordBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesCheckRecord.class)
            .likeIfText(MesCheckRecord::getRecordCode, bo.getRecordCode())
            .eqIfPresent(MesCheckRecord::getDeviceId, bo.getDeviceId())
            .eqIfText(MesCheckRecord::getCheckResult, bo.getCheckResult())
            .eqIfText(MesCheckRecord::getRecordStatus, bo.getRecordStatus())
            .geIfPresent(MesCheckRecord::getCheckTime, bo.getBeginCheckTime())
            .leIfPresent(MesCheckRecord::getCheckTime, bo.getEndCheckTime())
            .orderByDesc(defaultOrder, MesCheckRecord::getCheckTime)
            .orderByDesc(defaultOrder, MesCheckRecord::getCheckRecordId)
            .build();
    }

    /**
     * 设备编码与名称
     */
    private void fill(List<MesCheckRecordVo> vos) {
        if (vos.isEmpty()) {
            return;
        }
        Map<Long, MesDevice> devices = MesLookups.byIds(deviceMapper,
            MesLookups.ids(vos, MesCheckRecordVo::getDeviceId), MesDevice::getDeviceId);
        for (MesCheckRecordVo vo : vos) {
            MesDevice device = SafeLookup.get(devices, vo.getDeviceId());
            if (device != null) {
                vo.setDeviceCode(device.getDeviceCode());
                vo.setDeviceName(device.getDeviceName());
            }
        }
    }

    private List<MesCheckRecordDetail> details(Long checkRecordId) {
        return detailMapper.selectList(QueryBuilder.lambda(MesCheckRecordDetail.class)
            .eq(MesCheckRecordDetail::getCheckRecordId, checkRecordId)
            .orderByAsc(MesCheckRecordDetail::getSortNo, MesCheckRecordDetail::getDetailId)
            .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertByBo(MesCheckRecordBo bo) {
        MesDevice device = bo.getDeviceId() == null ? null : deviceMapper.selectById(bo.getDeviceId());
        if (device == null) {
            throw new ServiceException("设备不存在");
        }
        if (!SystemConstants.NORMAL.equals(device.getStatus())) {
            throw new ServiceException("设备“" + device.getDeviceName() + "”已停用，不能点检");
        }
        List<MesCheckItem> items = checkItemService.applicableItems(device);
        if (items.isEmpty()) {
            throw new ServiceException("设备“" + device.getDeviceName() + "”没有可用的点检项目，请先在“点检项目”中维护");
        }
        Map<Long, MesCheckRecordDetailBo> inputs = inputsByItem(bo.getDetails(), items);
        List<MesCheckRecordDetail> details = new ArrayList<>(items.size());
        for (MesCheckItem item : items) {
            details.add(CheckJudge.detail(item, inputs.get(item.getCheckItemId())));
        }
        int abnormal = (int) details.stream().filter(d -> CheckItemResult.NG.code().equals(d.getItemResult())).count();
        CheckResult result = abnormal > 0 ? CheckResult.ABNORMAL : CheckResult.NORMAL;

        MesCheckRecord record = new MesCheckRecord();
        record.setRecordCode(codeService.next(CodeRules.CHECK_RECORD));
        record.setDeviceId(device.getDeviceId());
        record.setCheckTime(LocalDateTime.now(clock));
        record.setCheckerUserId(UserContext.userId());
        record.setCheckResult(result.code());
        record.setAbnormalCount(abnormal);
        record.setRecordStatus(CheckRecordStatus.COMPLETED.code());
        record.setAndonRaised(NO);
        record.setRemark(StringUtils.trimToNull(bo.getRemark()));
        recordMapper.insert(record);
        details.forEach(detail -> detail.setCheckRecordId(record.getCheckRecordId()));
        detailMapper.insert(details);
        statusLogService.record(BizType.CHECK_RECORD, record.getCheckRecordId(), record.getRecordCode(), "create", null,
            CheckRecordStatus.COMPLETED.code(), abnormal > 0 ? "不合格 " + abnormal + " 项" : null);
    }

    /**
     * 填写的结果按点检项目归集：同一项目只能填写一次，只能填写该设备适用的启用项目
     */
    private static Map<Long, MesCheckRecordDetailBo> inputsByItem(List<MesCheckRecordDetailBo> inputs,
                                                                 List<MesCheckItem> items) {
        Map<Long, MesCheckItem> applicable = items.stream()
            .collect(Collectors.toMap(MesCheckItem::getCheckItemId, item -> item));
        Map<Long, MesCheckRecordDetailBo> result = new HashMap<>();
        for (MesCheckRecordDetailBo input : inputs == null ? List.<MesCheckRecordDetailBo>of() : inputs) {
            if (input == null || input.getCheckItemId() == null) {
                throw new ServiceException("点检项目不能为空");
            }
            if (!applicable.containsKey(input.getCheckItemId())) {
                throw new ServiceException("点检项目不适用于该设备或已停用，请刷新后重新填写");
            }
            if (result.put(input.getCheckItemId(), input) != null) {
                throw new ServiceException("点检项目“" + applicable.get(input.getCheckItemId()).getItemName() + "”重复填写");
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidRecord(Long checkRecordId, String reason) {
        if (StringUtils.isBlank(reason)) {
            throw new ServiceException("请填写作废原因");
        }
        MesCheckRecord record = requireRecord(checkRecordId);
        StateGuard.require(OBJECT, CheckRecordStatus.class, record.getRecordStatus(), "作废", CheckRecordStatus.COMPLETED);
        MesCheckRecord change = new MesCheckRecord();
        change.setRecordStatus(CheckRecordStatus.VOIDED.code());
        change.setVoidReason(reason.trim());
        int rows = recordMapper.update(change, Wrappers.<MesCheckRecord>lambdaUpdate()
            .eq(MesCheckRecord::getCheckRecordId, record.getCheckRecordId())
            .eq(MesCheckRecord::getRecordStatus, CheckRecordStatus.COMPLETED.code()));
        if (rows == 0) {
            throw new ServiceException("点检记录的状态已变化，请刷新后重试");
        }
        statusLogService.record(BizType.CHECK_RECORD, record.getCheckRecordId(), record.getRecordCode(), "void",
            CheckRecordStatus.COMPLETED.code(), CheckRecordStatus.VOIDED.code(), reason.trim());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void raiseAndon(Long checkRecordId, MesCheckAndonBo bo) {
        MesCheckRecord record = requireRecord(checkRecordId);
        StateGuard.require(OBJECT, CheckRecordStatus.class, record.getRecordStatus(), "发起安灯", CheckRecordStatus.COMPLETED);
        if (!CheckResult.ABNORMAL.code().equals(record.getCheckResult())) {
            throw new ServiceException("点检结论为正常，不需要发起安灯");
        }
        if (YES.equals(record.getAndonRaised())) {
            throw new ServiceException("该点检记录已经发起过安灯");
        }
        AndonLevel level = StringUtils.isBlank(bo.getEventLevel()) ? AndonLevel.URGENT
            : CodeEnum.of(AndonLevel.class, bo.getEventLevel(), "紧急程度");
        MesDevice device = deviceMapper.selectById(record.getDeviceId());
        if (device == null) {
            throw new ServiceException("设备不存在");
        }
        if (device.getWorkshopId() == null) {
            throw new ServiceException("设备“" + device.getDeviceName() + "”没有设置所属车间，不能发起安灯，请先在设备中设置车间");
        }
        // 先以“未发起”为条件占用：重复或并发的请求只有一个继续，安灯事件也只上报一次；上报失败时随事务一起回滚
        MesCheckRecord change = new MesCheckRecord();
        change.setAndonRaised(YES);
        change.setAndonTime(LocalDateTime.now(clock));
        int rows = recordMapper.update(change, Wrappers.<MesCheckRecord>lambdaUpdate()
            .eq(MesCheckRecord::getCheckRecordId, record.getCheckRecordId())
            .eq(MesCheckRecord::getRecordStatus, CheckRecordStatus.COMPLETED.code())
            .eq(MesCheckRecord::getAndonRaised, NO));
        if (rows == 0) {
            throw new ServiceException("该点检记录已经发起过安灯或状态已变化，请刷新后重试");
        }
        andonService.report(andonEvent(record, device, level, bo.getContent()));
        statusLogService.record(BizType.CHECK_RECORD, record.getCheckRecordId(), record.getRecordCode(), "andon",
            record.getRecordStatus(), record.getRecordStatus(), "发起安灯（" + level.label() + "）");
    }

    /**
     * 安灯事件：设备异常类，位置取设备的车间、产线与工位，描述中列出不合格项
     */
    private MesAndonEventBo andonEvent(MesCheckRecord record, MesDevice device, AndonLevel level, String extra) {
        List<String> failed = detailMapper.selectList(QueryBuilder.lambda(MesCheckRecordDetail.class)
                .eq(MesCheckRecordDetail::getCheckRecordId, record.getCheckRecordId())
                .eq(MesCheckRecordDetail::getItemResult, CheckItemResult.NG.code())
                .orderByAsc(MesCheckRecordDetail::getSortNo, MesCheckRecordDetail::getDetailId)
                .build())
            .stream().map(MesCheckRecordDetail::getItemName).toList();
        StringBuilder content = new StringBuilder("点检单 ").append(record.getRecordCode()).append(" 有 ")
            .append(failed.isEmpty() ? record.getAbnormalCount() : failed.size()).append(" 项不合格");
        if (!failed.isEmpty()) {
            content.append("：").append(String.join("、", failed));
        }
        if (StringUtils.isNotBlank(extra)) {
            content.append("。").append(extra.trim());
        }
        MesAndonEventBo event = new MesAndonEventBo();
        event.setEventType(AndonType.EQUIPMENT.code());
        event.setEventLevel(level.code());
        event.setTitle(StringUtils.substring("设备点检异常：" + device.getDeviceName(), 0, MAX_TITLE));
        event.setContent(StringUtils.substring(content.toString(), 0, MAX_CONTENT));
        event.setWorkshopId(device.getWorkshopId());
        event.setLineId(device.getLineId());
        event.setStationId(device.getStationId());
        event.setDeviceId(device.getDeviceId());
        return event;
    }

    @Override
    public List<MesStatusLogVo> logs(Long checkRecordId) {
        requireRecord(checkRecordId);
        return statusLogService.list(BizType.CHECK_RECORD, checkRecordId);
    }

    /**
     * 按 ID 取当前用户数据权限内的点检记录，不存在或不在数据权限内时抛出业务异常
     */
    private MesCheckRecord requireRecord(Long checkRecordId) {
        MesCheckRecord record = checkRecordId == null ? null : recordMapper.selectByIdInScope(checkRecordId);
        if (record == null) {
            throw new ServiceException("点检记录不存在");
        }
        return record;
    }

}
