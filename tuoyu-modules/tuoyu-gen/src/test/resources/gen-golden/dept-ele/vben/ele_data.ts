import type { DeptDemoApi } from '@tuoyu/shared/api/system/deptDemo';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { listDeptDemo } from '@tuoyu/shared/api/system/deptDemo';
import { getDictOptions } from '@tuoyu/shared/api/system/dict';
import { listToTree } from '@tuoyu/shared/utils/tree';

import { $t } from '#/locales';

/** 查询条件：一行最多 4 个 */
export function useSearchSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      componentProps: {
        clearable: true,
        placeholder: $t('crud.inputPlaceholder'),
      },
      fieldName: 'deptName',
      label: $t('system.deptDemo.deptName'),
    },
    {
      component: 'ApiSelect',
      componentProps: {
        api: () => getDictOptions('sys_normal_disable'),
        clearable: true,
        placeholder: $t('crud.selectPlaceholder'),
      },
      fieldName: 'status',
      label: $t('system.deptDemo.status'),
    },
  ];
}

/** 范围查询的字段：开始、结束时间分别提交为 params[begin*]、params[end*] */
export const searchFieldMappingTime: [string, [string, string], string][] = [];

/** 表格列 */
export function useColumns(): VxeTableGridOptions<DeptDemoApi.DeptDemo>['columns'] {
  return [
    {
      field: 'deptId',
      treeNode: true,
      title: $t('system.deptDemo.deptId'),
      minWidth: 120,
    },
    {
      field: 'parentId',
      title: $t('system.deptDemo.parentId'),
      minWidth: 120,
    },
    {
      field: 'ancestors',
      title: $t('system.deptDemo.ancestors'),
      minWidth: 120,
    },
    {
      field: 'deptName',
      title: $t('system.deptDemo.deptName'),
      minWidth: 120,
    },
    {
      field: 'orderNum',
      title: $t('system.deptDemo.orderNum'),
      minWidth: 120,
    },
    {
      field: 'status',
      cellRender: { name: 'CellDict', props: { dictType: 'sys_normal_disable' } },
      title: $t('system.deptDemo.status'),
      minWidth: 120,
    },
    {
      align: 'right',
      field: 'actions',
      fixed: 'right',
      slots: { default: 'actions' },
      title: $t('crud.actions'),
      width: 200,
    },
  ];
}

/** 新增、编辑表单：两列，长文本占整行 */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'ApiTreeSelect',
      componentProps: {
        api: async () =>
          listToTree(await listDeptDemo(), 'deptId', 'parentId'),
        // 任意一级节点都可以作为上级，不只是末级
        checkStrictly: true,
        childrenField: 'children',
        defaultExpandAll: true,
        labelField: 'deptName',
        placeholder: $t('crud.selectPlaceholder'),
        valueField: 'deptId',
      },
      fieldName: 'parentId',
      label: $t('system.deptDemo.parentId'),
      rules: 'selectRequired',
    },
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'ancestors',
      label: $t('system.deptDemo.ancestors'),
    },
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'deptName',
      label: $t('system.deptDemo.deptName'),
      rules: 'required',
    },
    {
      component: 'InputNumber',
      componentProps: { class: 'w-full', controlsPosition: 'right' },
      fieldName: 'orderNum',
      label: $t('system.deptDemo.orderNum'),
      rules: 'required',
    },
    {
      component: 'RadioGroup',
      // 选项在抽屉打开时从字典加载（loadFormOptions）
      componentProps: { options: [] },
      fieldName: 'status',
      label: $t('system.deptDemo.status'),
    },
  ];
}

/** 以逗号分隔的字符串保存、表单中为多选的字段 */
export const multipleFields: string[] = [];

/** 加载表单中依赖字典的选项（单选、多选），只保留文字与值 */
export async function loadFormOptions(): Promise<
  Record<string, { label: string; value: string }[]>
> {
  const [status] = await Promise.all([
    getDictOptions('sys_normal_disable'),
  ]);
  const toOptions = (options: { label: string; value: string }[]) =>
    options.map(({ label, value }) => ({ label, value }));
  return {
    status: toOptions(status),
  };
}
