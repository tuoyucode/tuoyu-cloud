<#-- Vben 风格（Element Plus 版）：树表页。约定见前端仓库 CONTRIBUTING.md 的“页面模板”一节 -->
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
import { ElButton, ElMessage, ElPopconfirm } from 'element-plus';

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
  ElMessage.success($t('crud.deleted'));
  reload();
}
</script>

<template>
  <Page auto-content-height>
    <Drawer @success="reload" />
    <Grid :table-title="$t('${i18n}.list')">
      <template #toolbar-actions>
        <div class="flex gap-2">
          <ElButton
            v-if="hasPermission('${permissionPrefix}:add')"
            type="primary"
            @click="onCreate()"
          >
            {{ $t('${i18n}.create') }}
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
          v-if="hasPermission('${permissionPrefix}:add')"
          link
          size="small"
          type="primary"
          @click="onCreate(row)"
        >
          {{ $t('crud.addChild') }}
        </ElButton>
        <ElButton
          v-if="hasPermission('${permissionPrefix}:edit')"
          link
          size="small"
          type="primary"
          @click="onEdit(row)"
        >
          {{ $t('common.edit') }}
        </ElButton>
        <ElPopconfirm
          v-if="hasPermission('${permissionPrefix}:remove')"
          :cancel-button-text="$t('common.cancel')"
          :confirm-button-text="$t('${i18n}.delete')"
          :title="$t('crud.confirmDelete', { name: row.${treeName} })"
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
