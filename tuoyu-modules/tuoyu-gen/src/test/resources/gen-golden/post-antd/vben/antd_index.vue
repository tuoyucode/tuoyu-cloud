<script lang="ts" setup>
import type { PostDemoApi } from '@tuoyu/shared/api/system/postDemo';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { formatDate } from '@vben/utils';

import {
  changePostDemoStatus,
  exportPostDemo,
  listPostDemo,
  removePostDemo,
} from '@tuoyu/shared/api/system/postDemo';
import { toPageQuery } from '@tuoyu/shared/utils/page-query';
import { hasPermission } from '@tuoyu/shared/utils/permission';
import { Button, message, Modal, Popconfirm, Switch } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { $t } from '#/locales';

import { searchFieldMappingTime, useColumns, useSearchSchema } from './data';
import PostDemoDrawer from './post-demo-drawer.vue';

defineOptions({ name: 'PostDemo' });

const STATUS_ACTIVE = '0';
const STATUS_INACTIVE = '1';

const [Drawer, drawerApi] = useVbenDrawer({
  connectedComponent: PostDemoDrawer,
  destroyOnClose: true,
});

/** 已勾选的行数，用于启用“删除所选” */
const selectedCount = ref(0);

/** 正在切换状态的行，避免重复点击 */
const switching = ref(new Set<number | string | undefined>());

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    fieldMappingTime: searchFieldMappingTime,
    schema: useSearchSchema(),
    submitOnChange: false,
    wrapperClass: 'grid-cols-1 md:grid-cols-2 lg:grid-cols-4',
  },
  gridEvents: {
    checkboxAll: updateSelectedCount,
    checkboxChange: updateSelectedCount,
  },
  gridOptions: {
    checkboxConfig: { highlight: true },
    columns: useColumns(),
    height: 'auto',
    keepSource: true,
    pagerConfig: { pageSize: 10, pageSizes: [10, 20, 50, 100] },
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          selectedCount.value = 0;
          return await listPostDemo({
            ...toPageQuery(page),
            ...formValues,
          });
        },
      },
    },
    rowConfig: { isHover: true, keyField: 'postId' },
    toolbarConfig: { custom: true, refresh: true, zoom: true },
  } as VxeTableGridOptions<PostDemoApi.PostDemo>,
});

function updateSelectedCount() {
  selectedCount.value = gridApi.grid.getCheckboxRecords().length;
}

function reload() {
  gridApi.query();
}

function onCreate() {
  drawerApi.setData({}).open();
}

function onEdit(row: PostDemoApi.PostDemo) {
  drawerApi.setData({ postId: row.postId }).open();
}

async function onDelete(row: PostDemoApi.PostDemo) {
  await removePostDemo([row.postId as number | string]);
  message.success($t('crud.deleted'));
  reload();
}

function onBatchDelete() {
  const rows = gridApi.grid.getCheckboxRecords() as PostDemoApi.PostDemo[];
  if (rows.length === 0) {
    return;
  }
  Modal.confirm({
    content: $t('crud.confirmBatchDelete', { count: rows.length }),
    okButtonProps: { danger: true },
    okText: $t('crud.batchDelete'),
    async onOk() {
      await removePostDemo(rows.map((row) => row.postId as number | string));
      message.success($t('crud.deleted'));
      reload();
    },
  });
}

async function onStatusChange(row: PostDemoApi.PostDemo, checked: boolean) {
  const id = row.postId as number | string;
  const status = checked ? STATUS_ACTIVE : STATUS_INACTIVE;
  switching.value.add(id);
  try {
    await changePostDemoStatus(id, status);
    row.status = status;
    message.success(
      $t(checked ? 'crud.enabledName' : 'crud.disabledName', {
        name: row.postCode,
      }),
    );
  } finally {
    switching.value.delete(id);
  }
}

async function onExport() {
  const query = await gridApi.formApi.getValues();
  const fileName = `${$t('system.postDemo.exportFileName')}_${formatDate(new Date(), 'YYYYMMDD_HHmmss')}.xlsx`;
  await exportPostDemo(query, fileName);
}
</script>

<template>
  <Page auto-content-height>
    <Drawer @success="reload" />
    <Grid :table-title="$t('system.postDemo.list')">
      <template #toolbar-actions>
        <div class="flex gap-2">
          <Button
            v-if="hasPermission('system:postDemo:add')"
            type="primary"
            @click="onCreate"
          >
            {{ $t('system.postDemo.create') }}
          </Button>
          <Button
            v-if="hasPermission('system:postDemo:remove')"
            :disabled="selectedCount === 0"
            danger
            @click="onBatchDelete"
          >
            {{ $t('crud.batchDelete') }}
          </Button>
          <Button
            v-if="hasPermission('system:postDemo:export')"
            @click="onExport"
          >
            {{ $t('crud.export') }}
          </Button>
        </div>
      </template>

      <template #status="{ row }">
        <Switch
          :checked="row.status === STATUS_ACTIVE"
          :disabled="!hasPermission('system:postDemo:edit')"
          :loading="switching.has(row.postId)"
          @change="(checked) => onStatusChange(row, checked as boolean)"
        />
      </template>

      <template #actions="{ row }">
        <Button
          v-if="hasPermission('system:postDemo:edit')"
          size="small"
          type="link"
          @click="onEdit(row)"
        >
          {{ $t('common.edit') }}
        </Button>
        <Popconfirm
          v-if="hasPermission('system:postDemo:remove')"
          :ok-button-props="{ danger: true }"
          :ok-text="$t('system.postDemo.delete')"
          :title="$t('crud.confirmDelete', { name: row.postCode })"
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
