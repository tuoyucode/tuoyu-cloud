<#-- Vben 风格（Ant Design Vue 版）：列表页（单表）。约定见前端仓库 CONTRIBUTING.md 的“页面模板”一节 -->
<#assign i18n = moduleName + "." + businessName>
<#assign pk = pkColumn.javaField>
<#assign kebab = businessName?replace("([A-Z])", "-$1", "r")?lower_case>
<#assign D = "$">
<#assign nameField = pk>
<#list columns as column><#if column.list && !column.pk && column.javaType == 'String'><#assign nameField = column.javaField><#break></#if></#list>
<script lang="ts" setup>
import type { ${ClassName}Api } from '@tuoyu/shared/api/${moduleName}/${businessName}';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
<#if enableExport>
import { formatDate } from '@vben/utils';
</#if>

import {
<#if enableStatus>
  change${ClassName}Status,
</#if>
<#if enableExport>
  export${ClassName},
</#if>
  list${ClassName},
  remove${ClassName},
} from '@tuoyu/shared/api/${moduleName}/${businessName}';
import { toPageQuery } from '@tuoyu/shared/utils/page-query';
import { hasPermission } from '@tuoyu/shared/utils/permission';
import { Button, message, Modal, Popconfirm<#if enableStatus>, Switch</#if> } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { $t } from '#/locales';

<#-- 两个本地导入按路径字母顺序排列（前端 lint 规则） -->
<#if (["./data", "./" + kebab + "-drawer.vue"]?sort[0] == "./data")>
import { searchFieldMappingTime, useColumns, useSearchSchema } from './data';
import ${BusinessName}Drawer from './${kebab}-drawer.vue';
<#else>
import ${BusinessName}Drawer from './${kebab}-drawer.vue';
import { searchFieldMappingTime, useColumns, useSearchSchema } from './data';
</#if>

defineOptions({ name: '${ClassName}' });

<#if enableStatus>
const STATUS_ACTIVE = ${statusActiveValue};
const STATUS_INACTIVE = ${statusInactiveValue};

</#if>
const [Drawer, drawerApi] = useVbenDrawer({
  connectedComponent: ${BusinessName}Drawer,
  destroyOnClose: true,
});

/** 已勾选的行数，用于启用“删除所选” */
const selectedCount = ref(0);
<#if enableStatus>

/** 正在切换状态的行，避免重复点击 */
const switching = ref(new Set<number | string | undefined>());
</#if>

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
          return await list${ClassName}({
            ...toPageQuery(page),
            ...formValues,
          });
        },
      },
    },
    rowConfig: { isHover: true, keyField: '${pk}' },
    toolbarConfig: { custom: true, refresh: true, zoom: true },
  } as VxeTableGridOptions<${ClassName}Api.${ClassName}>,
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

function onEdit(row: ${ClassName}Api.${ClassName}) {
  drawerApi.setData({ ${pk}: row.${pk} }).open();
}

async function onDelete(row: ${ClassName}Api.${ClassName}) {
  await remove${ClassName}([row.${pk} as number | string]);
  message.success($t('crud.deleted'));
  reload();
}

function onBatchDelete() {
  const rows = gridApi.grid.getCheckboxRecords() as ${ClassName}Api.${ClassName}[];
  if (rows.length === 0) {
    return;
  }
  Modal.confirm({
    content: $t('crud.confirmBatchDelete', { count: rows.length }),
    okButtonProps: { danger: true },
    okText: $t('crud.batchDelete'),
    async onOk() {
      await remove${ClassName}(rows.map((row) => row.${pk} as number | string));
      message.success($t('crud.deleted'));
      reload();
    },
  });
}
<#if enableStatus>

async function onStatusChange(row: ${ClassName}Api.${ClassName}, checked: boolean) {
  const id = row.${pk} as number | string;
  const status = checked ? STATUS_ACTIVE : STATUS_INACTIVE;
  switching.value.add(id);
  try {
    await change${ClassName}Status(id, status);
    row.${statusField} = status;
    message.success(
      $t(checked ? 'crud.enabledName' : 'crud.disabledName', {
        name: row.${nameField},
      }),
    );
  } finally {
    switching.value.delete(id);
  }
}
</#if>
<#if enableExport>

async function onExport() {
  const query = await gridApi.formApi.getValues();
  const fileName = `${D}{$t('${i18n}.exportFileName')}_${D}{formatDate(new Date(), 'YYYYMMDD_HHmmss')}.xlsx`;
  await export${ClassName}(query, fileName);
}
</#if>
</script>

<template>
  <Page auto-content-height>
    <Drawer @success="reload" />
    <Grid :table-title="$t('${i18n}.list')">
      <template #toolbar-actions>
        <div class="flex gap-2">
          <Button
            v-if="hasPermission('${permissionPrefix}:add')"
            type="primary"
            @click="onCreate"
          >
            {{ $t('${i18n}.create') }}
          </Button>
          <Button
            v-if="hasPermission('${permissionPrefix}:remove')"
            :disabled="selectedCount === 0"
            danger
            @click="onBatchDelete"
          >
            {{ $t('crud.batchDelete') }}
          </Button>
<#if enableExport>
          <Button
            v-if="hasPermission('${permissionPrefix}:export')"
            @click="onExport"
          >
            {{ $t('crud.export') }}
          </Button>
</#if>
        </div>
      </template>
<#if enableStatus>

      <template #status="{ row }">
        <Switch
          :checked="row.${statusField} === STATUS_ACTIVE"
          :disabled="!hasPermission('${permissionPrefix}:edit')"
          :loading="switching.has(row.${pk})"
          @change="(checked) => onStatusChange(row, checked as boolean)"
        />
      </template>
</#if>

      <template #actions="{ row }">
        <Button
          v-if="hasPermission('${permissionPrefix}:edit')"
          size="small"
          type="link"
          @click="onEdit(row)"
        >
          {{ $t('common.edit') }}
        </Button>
        <Popconfirm
          v-if="hasPermission('${permissionPrefix}:remove')"
          :ok-button-props="{ danger: true }"
          :ok-text="$t('${i18n}.delete')"
          :title="$t('crud.confirmDelete', { name: row.${nameField} })"
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
