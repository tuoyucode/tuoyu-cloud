package org.tuoyu.system.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.redis.utils.CacheUtils;
import org.tuoyu.system.domain.SysClient;
import org.tuoyu.system.domain.bo.SysClientBo;
import org.tuoyu.system.domain.vo.SysClientVo;
import org.tuoyu.system.mapper.SysClientMapper;
import org.tuoyu.system.service.ISysClientService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * 客户端服务实现。
 * <p>
 * 客户端所有租户共用（表中没有租户列）。认证服务按客户端 ID 读取客户端（带密钥，按客户端 ID 缓存在
 * {@link CacheNames#SYS_CLIENT}）；管理接口的详情、列表、导出都不下发密钥，列表也不能按密钥过滤，
 * 否则列表成了猜测密钥的判定器。客户端 ID 在创建时由 key 与密钥生成，之后不能修改。
 * <p>
 * 授权类型、访问路径、IP 白名单在库中都是逗号分隔的字符串，视图中另给列表形式。
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class SysClientServiceImpl implements ISysClientService {

    /**
     * 访问路径、IP 白名单的分隔符：逗号、分号或换行
     */
    private static final Pattern RULE_SEPARATOR = Pattern.compile("[,;\\r\\n]+");

    /**
     * 表示全部路径的写法
     */
    private static final String ALL_PATHS = "/**";

    private final SysClientMapper clientMapper;

    @Override
    public SysClientVo queryById(Long id) {
        return toVo(clientMapper.selectById(id), false);
    }

    @Override
    @Cacheable(cacheNames = CacheNames.SYS_CLIENT, key = "#clientId")
    public SysClientVo queryByClientId(String clientId) {
        return toVo(clientMapper.selectOne(QueryBuilder.lambda(SysClient.class)
            .eq(SysClient::getClientId, clientId)
            .build()), true);
    }

    @Override
    public PageResult<SysClientVo> queryPageList(SysClientBo bo, PageParam pageParam) {
        return PageResults.of(clientMapper.selectPage(pageParam.toPage(SysClient.class),
            buildQueryWrapper(bo, !pageParam.hasSort())), client -> toVo(client, false));
    }

    @Override
    public List<SysClientVo> queryList(SysClientBo bo) {
        List<SysClientVo> list = new ArrayList<>();
        for (SysClient client : clientMapper.selectList(buildQueryWrapper(bo, true))) {
            list.add(toVo(client, false));
        }
        return list;
    }

    @Override
    public long countList(SysClientBo bo) {
        return clientMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public void insertByBo(SysClientBo bo) {
        SysClient client = toEntity(bo);
        client.setClientId(SecureUtil.md5(bo.getClientKey() + bo.getClientSecret()));
        clientMapper.insert(client);
        bo.setId(client.getId());
    }

    @Override
    public void updateByBo(SysClientBo bo) {
        SysClient old = clientMapper.selectById(bo.getId());
        if (old == null) {
            throw new ServiceException("修改客户端'{}'失败，客户端不存在", bo.getClientKey());
        }
        SysClient client = toEntity(bo);
        // 客户端 ID 由创建时的 key 与密钥生成，认证服务按它查找客户端，不能修改
        client.setClientId(null);
        if (StringUtils.isBlank(bo.getClientSecret())) {
            client.setClientSecret(null);
        }
        if (clientMapper.updateById(client) == 0) {
            throw new ServiceException("修改客户端'{}'失败，客户端不存在", bo.getClientKey());
        }
        // 按库中的客户端 ID 清除缓存（请求中的客户端 ID 不可信，也可能没有提交）
        if (old.getClientId() != null) {
            CacheUtils.evict(CacheNames.SYS_CLIENT, old.getClientId());
        }
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.SYS_CLIENT, key = "#clientId")
    public void updateClientStatus(String clientId, String status) {
        int rows = clientMapper.update(Wrappers.<SysClient>lambdaUpdate()
            .set(SysClient::getStatus, status)
            .eq(SysClient::getClientId, clientId));
        if (rows == 0) {
            throw new ServiceException("修改客户端状态失败，客户端不存在");
        }
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.SYS_CLIENT, allEntries = true)
    public void deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (clientMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除客户端失败，客户端不存在");
        }
    }

    @Override
    public boolean checkClickKeyUnique(SysClientBo client) {
        return !clientMapper.exists(QueryBuilder.lambda(SysClient.class)
            .eq(SysClient::getClientKey, client.getClientKey())
            .neIfPresent(SysClient::getId, client.getId())
            .build());
    }

    /**
     * 请求转实体：授权类型取列表形式；访问路径、IP 白名单按字符串或列表形式规范化
     */
    private static SysClient toEntity(SysClientBo bo) {
        SysClient client = BeanMapper.map(bo, SysClient.class);
        client.setGrantType(bo.getGrantTypeList() == null ? null : String.join(StringUtils.SEPARATOR, bo.getGrantTypeList()));
        client.setAccessPath(joinRules(bo.getAccessPath(), bo.getAccessPathList(), SysClientServiceImpl::normalizePath));
        client.setIpWhitelist(joinRules(bo.getIpWhitelist(), bo.getIpWhitelistList(), UnaryOperator.identity()));
        return client;
    }

    /**
     * 实体转视图，填充三个列表形式
     *
     * @param client     实体
     * @param keepSecret 是否保留密钥（只有认证服务按客户端 ID 查询时保留）
     * @return 视图；实体为 null 时为 null
     */
    private static SysClientVo toVo(SysClient client, boolean keepSecret) {
        SysClientVo vo = BeanMapper.map(client, SysClientVo.class);
        if (vo == null) {
            return null;
        }
        vo.setGrantTypeList(StringUtils.str2List(vo.getGrantType(), StringUtils.SEPARATOR, true, true));
        vo.setAccessPathList(splitRules(vo.getAccessPath()));
        vo.setIpWhitelistList(splitRules(vo.getIpWhitelist()));
        if (!keepSecret) {
            vo.setClientSecret(null);
        }
        return vo;
    }

    /**
     * 按逗号、分号、换行拆分规则，去掉两端空白、丢弃空项
     */
    private static List<String> splitRules(String text) {
        List<String> items = new ArrayList<>();
        if (text == null) {
            return items;
        }
        for (String item : RULE_SEPARATOR.split(text)) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    /**
     * 写入的规则字段：字符串形式不为 null 时按它拆分，否则用列表形式；逐项去空白、规范化、丢弃空项后用逗号连接。
     * 两种形式都为 null 时为 null（不修改）；提交了但没有有效项时为空字符串（清空）
     */
    private static String joinRules(String text, List<String> list, UnaryOperator<String> normalizer) {
        List<String> items;
        if (text != null) {
            items = splitRules(text);
        } else if (list != null) {
            items = list;
        } else {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (String item : items) {
            String value = item == null ? "" : normalizer.apply(item.trim());
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return String.join(StringUtils.SEPARATOR, result);
    }

    /**
     * 访问路径规范化：{@code *} 与 {@code /**} 都表示全部路径；不以斜杠开头时补一个斜杠
     */
    private static String normalizePath(String path) {
        if (path.isEmpty()) {
            return path;
        }
        if ("*".equals(path) || ALL_PATHS.equals(path)) {
            return ALL_PATHS;
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * 列表条件：客户端 ID、key、状态相等（不含密钥）；默认按 ID 升序（分页时指定了排序则不加）
     */
    private LambdaQueryWrapper<SysClient> buildQueryWrapper(SysClientBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(SysClient.class)
            .eqIfText(SysClient::getClientId, bo.getClientId())
            .eqIfText(SysClient::getClientKey, bo.getClientKey())
            .eqIfText(SysClient::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, SysClient::getId)
            .build();
    }

}
