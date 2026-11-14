<script lang="ts" setup>
import type { DeptDemoApi } from '@tuoyu/shared/api/system/deptDemo';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { Page, useVbenDrawer } from '@vben/common-ui';

import {
  listDeptDemo,
  removeDeptDemo,
} from '@tuoyu/shared/api/system/deptDemo';
import { hasPermission } from '@tuoyu/shared/utils/permission';
import { ElButton, ElMessage, ElPopconfirm } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { $t } from '#/locales';

import { searchFieldMappingTime, useColumns, useSearchSchema } from './data';
import DeptDemoDrawer from './dept-demo-drawer.vue';

defineOptions({ name: 'DeptDemo' });

const [Drawer, drawerApi] = useVbenDrawer({
  connectedComponent: DeptDemoDrawer,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    fieldMappingTime: searchFieldMappingTime,
    schema: useSearchSchema(),
    submitOnChange: false,
    wrapperClass: 'grid-cols-1 md:grid-cols-2 lg:grid-cols-4',
  },
  gridOptions: {
    columns: useColumns(),
    height: 'auto',
    keepSource: true,
    pagerConfig: { enabled: false },
    proxyConfig: {
      ajax: {
        // 树表一次返回全部数据，由表格按上级字段组装成树
        query: async (_params, formValues) => ({
          items: await listDeptDemo(formValues),
        }),
      },
    },
    rowConfig: { isHover: true, keyField: 'deptId' },
    toolbarConfig: { custom: true, refresh: true, zoom: true },
    treeConfig: {
      expandAll: false,
      parentField: 'parentId',
      rowField: 'deptId',
      transform: true,
    },
  } as VxeTableGridOptions<DeptDemoApi.DeptDemo>,
});

function reload() {
  gridApi.query();
}

function expandAll(expand: boolean) {
  gridApi.grid.setAllTreeExpand(expand);
}

function onCreate(parent?: DeptDemoApi.DeptDemo) {
  drawerApi
    .setData({ values: parent ? { parentId: parent.deptId } : {} })
    .open();
}

function onEdit(row: DeptDemoApi.DeptDemo) {
  drawerApi.setData({ deptId: row.deptId }).open();
}

async function onDelete(row: DeptDemoApi.DeptDemo) {
  await removeDeptDemo([row.deptId as number | string]);
  ElMessage.success($t('crud.deleted'));
  reload();
}
</script>

<template>
  <Page auto-content-height>
    <Drawer @success="reload" />
    <Grid :table-title="$t('system.deptDemo.list')">
      <template #toolbar-actions>
        <div class="flex gap-2">
          <ElButton
            v-if="hasPermission('system:deptDemo:add')"
            type="primary"
            @click="onCreate()"
          >
            {{ $t('system.deptDemo.create') }}
          </ElButton>
          <ElButton @click="expandAll(true)">
            {{ $t('crud.expandAll') }}
          </ElButton>
          <ElButton @click="expandAll(false)">
            {{ $t('crud.collapseAll') }}
          </ElButton>
        </div>
      </template>

      <template #actions="{ row }">
        <ElButton
          v-if="hasPermission('system:deptDemo:add')"
          link
          size="small"
          type="primary"
          @click="onCreate(row)"
        >
          {{ $t('crud.addChild') }}
        </ElButton>
        <ElButton
          v-if="hasPermission('system:deptDemo:edit')"
          link
          size="small"
          type="primary"
          @click="onEdit(row)"
        >
          {{ $t('common.edit') }}
        </ElButton>
        <ElPopconfirm
          v-if="hasPermission('system:deptDemo:remove')"
          :cancel-button-text="$t('common.cancel')"
          :confirm-button-text="$t('system.deptDemo.delete')"
          :title="$t('crud.confirmDelete', { name: row.deptName })"
          confirm-button-type="danger"
          placement="left"
          width="240"
          @confirm="onDelete(row)"
        >
          <template #reference>
            <ElButton link size="small" type="danger">
              {{ $t('common.delete') }}
            </ElButton>
          </template>
        </ElPopconfirm>
      </template>
    </Grid>
  </Page>
</template>
