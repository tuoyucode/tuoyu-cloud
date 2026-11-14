<script lang="ts" setup>
import type { DeptDemoApi } from '@tuoyu/shared/api/system/deptDemo';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { Page, useVbenDrawer } from '@vben/common-ui';

import {
  listDeptDemo,
  removeDeptDemo,
} from '@tuoyu/shared/api/system/deptDemo';
import { hasPermission } from '@tuoyu/shared/utils/permission';
import { Button, message, Popconfirm } from 'ant-design-vue';

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
  message.success($t('crud.deleted'));
  reload();
}
</script>

<template>
  <Page auto-content-height>
    <Drawer @success="reload" />
    <Grid :table-title="$t('system.deptDemo.list')">
      <template #toolbar-actions>
        <div class="flex gap-2">
          <Button
            v-if="hasPermission('system:deptDemo:add')"
            type="primary"
            @click="onCreate()"
          >
            {{ $t('system.deptDemo.create') }}
          </Button>
          <Button @click="expandAll(true)">{{ $t('crud.expandAll') }}</Button>
          <Button @click="expandAll(false)">{{ $t('crud.collapseAll') }}</Button>
        </div>
      </template>

      <template #actions="{ row }">
        <Button
          v-if="hasPermission('system:deptDemo:add')"
          size="small"
          type="link"
          @click="onCreate(row)"
        >
          {{ $t('crud.addChild') }}
        </Button>
        <Button
          v-if="hasPermission('system:deptDemo:edit')"
          size="small"
          type="link"
          @click="onEdit(row)"
        >
          {{ $t('common.edit') }}
        </Button>
        <Popconfirm
          v-if="hasPermission('system:deptDemo:remove')"
          :ok-button-props="{ danger: true }"
          :ok-text="$t('system.deptDemo.delete')"
          :title="$t('crud.confirmDelete', { name: row.deptName })"
          placement="left"
          @confirm="onDelete(row)"
        >
          <Button danger size="small" type="link">
            {{ $t('common.delete') }}
          </Button>
        </Popconfirm>
      </template>
    </Grid>
  </Page>
</template>
