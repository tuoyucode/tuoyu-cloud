package org.tuoyu.system.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.api.domain.bo.RemoteMenuBo;
import org.tuoyu.system.domain.SysMenu;
import org.tuoyu.system.mapper.SysMenuMapper;
import org.tuoyu.system.service.ISysModuleMenuService;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 业务模块的菜单登记。
 * <p>
 * 只追加不覆盖：已存在的菜单（按 ID）保持原样，管理员调整过的名称、顺序、图标与显示状态不会在模块重启后被改回；
 * 模块删掉的菜单也不会被自动删除，需要时写迁移脚本处理。
 * 多个实例同时启动时可能同时写入同一个菜单，主键冲突的一方跳过即可。
 *
 * @author tuoyu
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysModuleMenuServiceImpl implements ISysModuleMenuService {

    /**
     * 模块标识：小写字母开头，只含小写字母与数字
     */
    private static final Pattern MODULE = Pattern.compile("[a-z][a-z0-9]{1,19}");

    /**
     * 权限码：三段，以冒号分隔，每段由字母与数字组成
     */
    private static final Pattern PERMISSION = Pattern.compile("[a-z][a-z0-9]*:[a-zA-Z][a-zA-Z0-9]*:[a-zA-Z][a-zA-Z0-9]*");

    private static final Long ROOT = 0L;

    private final SysMenuMapper menuMapper;

    @Override
    public int registerModuleMenus(String module, List<RemoteMenuBo> menus) {
        if (module == null || !MODULE.matcher(module).matches()) {
            throw new ServiceException("模块标识只能由小写字母开头的 2 ~ 20 位小写字母与数字组成：" + module);
        }
        if (menus == null || menus.isEmpty()) {
            return 0;
        }
        Set<Long> declared = validate(module, menus);
        checkParents(menus, declared);

        Set<Long> existing = menuMapper.selectList(QueryBuilder.lambda(SysMenu.class)
                .select(SysMenu::getMenuId)
                .in(SysMenu::getMenuId, declared)
                .build())
            .stream().map(SysMenu::getMenuId).collect(Collectors.toSet());
        int inserted = 0;
        for (RemoteMenuBo menu : menus) {
            if (existing.contains(menu.getMenuId())) {
                continue;
            }
            try {
                menuMapper.insert(toEntity(menu));
                inserted++;
            } catch (DuplicateKeyException e) {
                // 另一个实例刚写入了同一个菜单
                log.debug("模块 {} 的菜单 {} 已由其他实例写入", module, menu.getMenuId());
            }
        }
        if (inserted > 0) {
            log.info("模块 {} 登记菜单：新写入 {} 个，已存在 {} 个", module, inserted, menus.size() - inserted);
        }
        return inserted;
    }

    /**
     * 校验每个菜单的声明，返回本批的菜单 ID
     */
    private static Set<Long> validate(String module, List<RemoteMenuBo> menus) {
        Set<Long> ids = new LinkedHashSet<>();
        for (RemoteMenuBo menu : menus) {
            if (menu.getMenuId() == null || menu.getMenuId() <= 0) {
                throw new ServiceException("模块 " + module + " 的菜单“" + menu.getMenuName() + "”缺少有效的菜单 ID");
            }
            if (!ids.add(menu.getMenuId())) {
                throw new ServiceException("模块 " + module + " 的菜单 ID " + menu.getMenuId() + " 重复");
            }
            if (menu.getMenuId().equals(menu.getParentId())) {
                throw new ServiceException("模块 " + module + " 的菜单 " + menu.getMenuId() + " 的上级不能是自己");
            }
            String perms = menu.getPerms();
            if (StringUtils.isNotBlank(perms)) {
                if (!PERMISSION.matcher(perms).matches() || !perms.startsWith(module + ":")) {
                    throw new ServiceException("模块 " + module + " 的菜单 " + menu.getMenuId() + " 的权限码 " + perms
                        + " 不合法：权限码应为三段，并以 " + module + ": 开头");
                }
            } else if (SystemConstants.TYPE_BUTTON.equals(menu.getMenuType())) {
                throw new ServiceException("模块 " + module + " 的按钮 " + menu.getMenuId() + " 缺少权限码");
            }
        }
        return ids;
    }

    /**
     * 上级菜单必须是顶级（0）、本批中的菜单或已经存在的菜单
     */
    private void checkParents(List<RemoteMenuBo> menus, Set<Long> declared) {
        Set<Long> outside = new HashSet<>();
        for (RemoteMenuBo menu : menus) {
            Long parentId = menu.getParentId() == null ? ROOT : menu.getParentId();
            if (!ROOT.equals(parentId) && !declared.contains(parentId)) {
                outside.add(parentId);
            }
        }
        if (outside.isEmpty()) {
            return;
        }
        Set<Long> found = menuMapper.selectList(QueryBuilder.lambda(SysMenu.class)
                .select(SysMenu::getMenuId)
                .in(SysMenu::getMenuId, outside)
                .build())
            .stream().map(SysMenu::getMenuId).collect(Collectors.toSet());
        outside.removeAll(found);
        if (!outside.isEmpty()) {
            throw new ServiceException("菜单声明中的上级菜单不存在：" + outside);
        }
    }

    private static SysMenu toEntity(RemoteMenuBo bo) {
        SysMenu menu = new SysMenu();
        menu.setMenuId(bo.getMenuId());
        menu.setParentId(bo.getParentId() == null ? ROOT : bo.getParentId());
        menu.setMenuName(bo.getMenuName());
        menu.setOrderNum(bo.getOrderNum() == null ? 0 : bo.getOrderNum());
        menu.setPath(StringUtils.blankToDefault(bo.getPath(), ""));
        menu.setComponent(StringUtils.isBlank(bo.getComponent()) ? null : bo.getComponent());
        menu.setQueryParam("");
        menu.setIsFrame(SystemConstants.NO);
        menu.setIsCache(SystemConstants.YES);
        menu.setMenuType(bo.getMenuType());
        menu.setVisible(StringUtils.blankToDefault(bo.getVisible(), SystemConstants.NORMAL));
        menu.setStatus(SystemConstants.NORMAL);
        menu.setPerms(StringUtils.isBlank(bo.getPerms()) ? "" : bo.getPerms());
        menu.setIcon(StringUtils.blankToDefault(bo.getIcon(), "#"));
        menu.setActiveMenu("");
        menu.setExt("");
        menu.setRemark(StringUtils.blankToDefault(bo.getRemark(), ""));
        return menu;
    }

}
