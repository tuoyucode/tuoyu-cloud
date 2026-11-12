<#-- Vben 风格（Ant Design Vue 版）：树表页。约定见前端仓库 CONTRIBUTING.md 的“页面模板”一节 -->
<#assign i18n = moduleName + "." + businessName>
<#assign pk = pkColumn.javaField>
<#assign kebab = businessName?replace("([A-Z])", "-$1", "r")?lower_case>
<script lang="ts" setup>
import type { ${ClassName}Api } from '@tuoyu/shared/api/${moduleName}/${businessName}';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { Page, useVbenDrawer } from '@vben/common-ui';

import {
  list${ClassName},
  remove${ClassName},
} from '@tuoyu/shared/api/${moduleName}/${businessName}';
import { hasPermission } from '@tuoyu/shared/utils/permission';
import { Button, message, Popconfirm } from 'ant-design-vue';

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

const [Drawer, drawerApi] = useVbenDrawer({
  connectedComponent: ${BusinessName}Drawer,
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
          items: await list${ClassName}(formValues),
        }),
      },
    },
    rowConfig: { isHover: true, keyField: '${treeCode}' },
    toolbarConfig: { custom: true, refresh: true, zoom: true },
    treeConfig: {
      expandAll: false,
      parentField: '${treeParentCode}',
      rowField: '${treeCode}',
      transform: true,
    },
  } as VxeTableGridOptions<${ClassName}Api.${ClassName}>,
});

function reload() {
  gridApi.query();
}

function expandAll(expand: boolean) {
  gridApi.grid.setAllTreeExpand(expand);
}

function onCreate(parent?: ${ClassName}Api.${ClassName}) {
  drawerApi
    .setData({ values: parent ? { ${treeParentCode}: parent.${treeCode} } : {} })
    .open();
}

function onEdit(row: ${ClassName}Api.${ClassName}) {
  drawerApi.setData({ ${pk}: row.${pk} }).open();
}

async function onDelete(row: ${ClassName}Api.${ClassName}) {
  await remove${ClassName}([row.${pk} as number | string]);
  message.success($t('crud.deleted'));
  reload();
}
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
            @click="onCreate()"
          >
            {{ $t('${i18n}.create') }}
          </Button>
          <Button @click="expandAll(true)">{{ $t('crud.expandAll') }}</Button>
          <Button @click="expandAll(false)">{{ $t('crud.collapseAll') }}</Button>
        </div>
      </template>

      <template #actions="{ row }">
        <Button
          v-if="hasPermission('${permissionPrefix}:add')"
          size="small"
          type="link"
          @click="onCreate(row)"
        >
          {{ $t('crud.addChild') }}
        </Button>
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
          :title="$t('crud.confirmDelete', { name: row.${treeName} })"
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
