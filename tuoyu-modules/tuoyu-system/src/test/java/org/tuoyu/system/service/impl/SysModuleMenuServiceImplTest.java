package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.system.api.domain.bo.RemoteMenuBo;
import org.tuoyu.system.domain.SysMenu;
import org.tuoyu.system.mapper.SysMenuMapper;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("业务模块的菜单登记")
class SysModuleMenuServiceImplTest {

    static {
        SystemTestSupport.init();
    }

    private final SysMenuMapper menuMapper = SystemTestSupport.mapper(SysMenuMapper.class);

    private final SysModuleMenuServiceImpl service = new SysModuleMenuServiceImpl(menuMapper);

    private static RemoteMenuBo menu(long id, long parentId, String type, String perms) {
        RemoteMenuBo bo = new RemoteMenuBo();
        bo.setMenuId(id);
        bo.setParentId(parentId);
        bo.setMenuName("菜单" + id);
        bo.setMenuType(type);
        bo.setPerms(perms);
        return bo;
    }

    private static SysMenu existing(long id) {
        SysMenu menu = new SysMenu();
        menu.setMenuId(id);
        return menu;
    }

    @Test
    @DisplayName("只写入还不存在的菜单，已存在的保持原样，并补齐默认值")
    void insertsOnlyMissingMenus() {
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing(1L)));

        int inserted = service.registerModuleMenus("mes", List.of(
            menu(1L, 0L, "M", null),
            menu(2L, 1L, "C", "mes:workorder:list"),
            menu(3L, 2L, "F", "mes:workorder:add")));

        assertThat(inserted).isEqualTo(2);
        ArgumentCaptor<SysMenu> captor = ArgumentCaptor.forClass(SysMenu.class);
        verify(menuMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(SysMenu::getMenuId).containsExactly(2L, 3L);
        SysMenu page = captor.getAllValues().get(0);
        assertThat(page.getIsFrame()).isEqualTo("N");
        assertThat(page.getStatus()).isEqualTo("0");
        assertThat(page.getVisible()).isEqualTo("0");
        assertThat(page.getIcon()).isEqualTo("#");
    }

    @Test
    @DisplayName("权限码必须以模块标识开头，不能借登记写入其他模块的权限码")
    void rejectsForeignPermission() {
        assertThatThrownBy(() -> service.registerModuleMenus("mes", List.of(menu(2L, 0L, "C", "system:user:list"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("mes:");
        verify(menuMapper, never()).insert(any(SysMenu.class));
    }

    @Test
    @DisplayName("模块标识、重复 ID、按钮缺少权限码时整批拒绝")
    void rejectsInvalidDeclarations() {
        assertThatThrownBy(() -> service.registerModuleMenus("MES", List.of(menu(1L, 0L, "M", null))))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.registerModuleMenus("mes", List.of(menu(1L, 0L, "M", null), menu(1L, 0L, "M", null))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("重复");
        assertThatThrownBy(() -> service.registerModuleMenus("mes", List.of(menu(1L, 0L, "F", null))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("缺少权限码");
        assertThatThrownBy(() -> service.registerModuleMenus("mes", List.of(menu(1L, 1L, "M", null))))
            .isInstanceOf(ServiceException.class).hasMessageContaining("上级不能是自己");
        verify(menuMapper, never()).insert(any(SysMenu.class));
    }

    @Test
    @DisplayName("上级菜单既不在本批中、也不存在时拒绝")
    void rejectsUnknownParent() {
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.registerModuleMenus("mes", List.of(menu(2L, 99L, "C", "mes:workorder:list"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("99");
    }

    @Test
    @DisplayName("其他实例同时写入同一个菜单时跳过，不报错")
    void skipsConcurrentDuplicates() {
        when(menuMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(menuMapper.insert(any(SysMenu.class))).thenThrow(new DuplicateKeyException("duplicate")).thenReturn(1);

        int inserted = service.registerModuleMenus("mes", List.of(menu(1L, 0L, "M", null), menu(2L, 1L, "C", null)));

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    @DisplayName("没有菜单时不访问数据库")
    void emptyDeclaration() {
        assertThat(service.registerModuleMenus("mes", List.of())).isZero();
        verify(menuMapper, never()).selectList(any(Wrapper.class));
    }

}
