package org.tuoyu.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysClient;
import org.tuoyu.system.domain.bo.SysClientBo;
import org.tuoyu.system.domain.vo.SysClientVo;
import org.tuoyu.system.mapper.SysClientMapper;
import org.tuoyu.system.service.ISysClientService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 客户端管理Service业务层处理
 *
 * @author Michelle.Chung
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysClientServiceImpl implements ISysClientService {

    private static final String CLIENT_RULE_SEPARATOR_REGEX = "[,;\\r\\n]+";

    private final SysClientMapper clientMapper;

    /**
     * 查询客户端管理
     */
    @Override
    public SysClientVo queryById(Long id) {
        SysClientVo vo = BeanMapper.map(clientMapper.selectById(id), SysClientVo.class);
        fillClientRuleFields(vo);
        hideSecret(vo);
        return vo;
    }

    /**
     * 查询客户端管理
     */
    @Cacheable(cacheNames = CacheNames.SYS_CLIENT, key = "#clientId")
    @Override
    public SysClientVo queryByClientId(String clientId) {
        SysClientVo vo = BeanMapper.map(clientMapper.selectOne(QueryBuilder.lambda(SysClient.class).eq(SysClient::getClientId, clientId).build()), SysClientVo.class);
        fillClientRuleFields(vo);
        return vo;
    }

    /**
     * 查询客户端管理列表
     */
    @Override
    public PageResult<SysClientVo> queryPageList(SysClientBo bo, PageParam pageParam) {
        LambdaQueryWrapper<SysClient> lqw = buildQueryWrapper(bo);
        PageResult<SysClientVo> result = PageResults.of(clientMapper.selectPage(pageParam.toPage(SysClient.class), lqw), SysClientVo.class);
        result.getItems().forEach(vo -> {
            fillClientRuleFields(vo);
            hideSecret(vo);
        });
        return result;
    }

    /**
     * 查询客户端管理列表
     */
    @Override
    public List<SysClientVo> queryList(SysClientBo bo) {
        LambdaQueryWrapper<SysClient> lqw = buildQueryWrapper(bo);
        List<SysClientVo> list = BeanMapper.mapList(clientMapper.selectList(lqw), SysClientVo.class);
        list.forEach(vo -> {
            fillClientRuleFields(vo);
            hideSecret(vo);
        });
        return list;
    }

    /**
     * 统计符合条件的客户端数量（导出前先统计，查询条件与列表一致）
     */
    @Override
    public long countList(SysClientBo bo) {
        return clientMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo)).getTotal();
    }

    private LambdaQueryWrapper<SysClient> buildQueryWrapper(SysClientBo bo) {
        return QueryBuilder.lambda(SysClient.class)
            .eqIfText(SysClient::getClientId, bo.getClientId())
            .eqIfText(SysClient::getClientKey, bo.getClientKey())
            .eqIfText(SysClient::getClientSecret, bo.getClientSecret())
            .eqIfText(SysClient::getStatus, bo.getStatus())
            .orderByAsc(SysClient::getId)
            .build();
    }

    /**
     * 新增客户端管理
     */
    @Override
    public void insertByBo(SysClientBo bo) {
        SysClient add = BeanMapper.map(bo, SysClient.class);
        add.setGrantType(CollUtil.join(bo.getGrantTypeList(), StringUtils.SEPARATOR));
        add.setAccessPath(resolveRuleValue(bo.getAccessPath(), bo.getAccessPathList(), this::normalizeAccessPath));
        add.setIpWhitelist(resolveRuleValue(bo.getIpWhitelist(), bo.getIpWhitelistList(), UnaryOperator.identity()));
        // 生成clientId
        String clientKey = bo.getClientKey();
        String clientSecret = bo.getClientSecret();
        add.setClientId(SecureUtil.md5(clientKey + clientSecret));
        clientMapper.insert(add);
        bo.setId(add.getId());
    }

    /**
     * 修改客户端管理
     */
    @CacheEvict(cacheNames = CacheNames.SYS_CLIENT, key = "#bo.clientId")
    @Override
    public void updateByBo(SysClientBo bo) {
        SysClient update = BeanMapper.map(bo, SysClient.class);
        update.setGrantType(StringUtils.joinComma(bo.getGrantTypeList()));
        update.setAccessPath(resolveRuleValue(bo.getAccessPath(), bo.getAccessPathList(), this::normalizeAccessPath));
        update.setIpWhitelist(resolveRuleValue(bo.getIpWhitelist(), bo.getIpWhitelistList(), UnaryOperator.identity()));
        // 列表和详情接口不返回密钥，修改时没有传密钥表示不修改（全局更新策略为 NOT_NULL）
        if (StringUtils.isBlank(update.getClientSecret())) {
            update.setClientSecret(null);
        }
        if (clientMapper.updateById(update) == 0) {
            throw new ServiceException("修改客户端'{}'失败，客户端不存在", bo.getClientKey());
        }
    }

    /**
     * 修改状态
     */
    @CacheEvict(cacheNames = CacheNames.SYS_CLIENT, key = "#clientId")
    @Override
    public void updateClientStatus(String clientId, String status) {
        int rows = clientMapper.update(Wrappers.<SysClient>lambdaUpdate()
            .set(SysClient::getStatus, status)
            .eq(SysClient::getClientId, clientId));
        if (rows == 0) {
            throw new ServiceException("修改客户端状态失败，客户端不存在");
        }
    }

    /**
     * 批量删除客户端管理
     */
    @CacheEvict(cacheNames = CacheNames.SYS_CLIENT, allEntries = true)
    @Override
    public void deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (clientMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除客户端失败，客户端不存在");
        }
    }

    /**
     * 校验客户端key是否唯一
     *
     * @param client 客户端信息
     * @return 结果
     */
    @Override
    public boolean checkClickKeyUnique(SysClientBo client) {
        boolean exist = clientMapper.exists(QueryBuilder.lambda(SysClient.class)
            .eq(SysClient::getClientKey, client.getClientKey())
            .neIfPresent(SysClient::getId, client.getId())
            .build());
        return !exist;
    }

    /**
     * 去掉密钥：管理接口与导出都不下发客户端密钥
     */
    private void hideSecret(SysClientVo vo) {
        if (vo != null) {
            vo.setClientSecret(null);
        }
    }

    /**
     * 回填客户端扩展规则字段，便于前端直接展示和编辑。
     */
    private void fillClientRuleFields(SysClientVo vo) {
        if (ObjectUtil.isNull(vo)) {
            return;
        }
        vo.setGrantTypeList(StringUtils.splitList(vo.getGrantType()));
        vo.setAccessPathList(parseRuleList(vo.getAccessPath(), this::normalizeAccessPath));
        vo.setIpWhitelistList(parseRuleList(vo.getIpWhitelist(), UnaryOperator.identity()));
    }

    /**
     * 统一处理白名单与路径规则的入库格式。
     */
    private String resolveRuleValue(String rawValue, List<String> listValue, UnaryOperator<String> normalizer) {
        List<String> rules = rawValue != null
            ? StringUtils.str2List(rawValue, CLIENT_RULE_SEPARATOR_REGEX, true, true)
            : listValue;
        if (CollUtil.isEmpty(rules)) {
            return listValue != null || rawValue != null ? "" : null;
        }
        return CollUtil.join(rules.stream()
            .map(normalizer)
            .filter(StringUtils::isNotBlank)
            .toList(), StringUtils.SEPARATOR);
    }

    /**
     * 将规则串转换为列表。
     */
    private List<String> parseRuleList(String value, UnaryOperator<String> normalizer) {
        return StringUtils.str2List(value, CLIENT_RULE_SEPARATOR_REGEX, true, true).stream()
            .map(normalizer)
            .filter(StringUtils::isNotBlank)
            .toList();
    }

    /**
     * 统一补齐路径前导斜杠，避免配置成 app/** 时无法命中。
     */
    private String normalizeAccessPath(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        String accessPath = StringUtils.trim(path);
        if (StringUtils.isBlank(accessPath)) {
            return null;
        }
        if (StringUtils.equals(accessPath, "*") || StringUtils.equals(accessPath, "/**")) {
            return "/**";
        }
        return accessPath.startsWith(StringUtils.SLASH) ? accessPath : StringUtils.SLASH + accessPath;
    }

}
