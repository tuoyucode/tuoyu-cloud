import type { VerifyDemoApi } from '@tuoyu/shared/api/system/verifyDemo';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

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
      fieldName: 'demoCode',
      label: $t('system.verifyDemo.demoCode'),
    },
    {
      component: 'Input',
      componentProps: {
        clearable: true,
        placeholder: $t('crud.inputPlaceholder'),
      },
      fieldName: 'demoName',
      label: $t('system.verifyDemo.demoName'),
    },
    {
      component: 'InputNumber',
      componentProps: { class: 'w-full', controlsPosition: 'right' },
      fieldName: 'amount',
      label: $t('system.verifyDemo.amount'),
    },
    {
      component: 'InputNumber',
      componentProps: { class: 'w-full', controlsPosition: 'right' },
      fieldName: 'demoSort',
      label: $t('system.verifyDemo.demoSort'),
    },
    {
      component: 'Input',
      componentProps: {
        clearable: true,
        placeholder: $t('crud.inputPlaceholder'),
      },
      fieldName: 'status',
      label: $t('system.verifyDemo.status'),
    },
  ];
}

/** 范围查询的字段：开始、结束时间分别提交为 params[begin*]、params[end*] */
export const searchFieldMappingTime: [string, [string, string], string][] = [];

/** 表格列 */
export function useColumns(): VxeTableGridOptions<VerifyDemoApi.VerifyDemo>['columns'] {
  return [
    { type: 'checkbox', width: 48 },
    {
      field: 'demoId',
      title: $t('system.verifyDemo.demoId'),
      minWidth: 120,
    },
    {
      field: 'demoCode',
      title: $t('system.verifyDemo.demoCode'),
      minWidth: 120,
    },
    {
      field: 'demoName',
      title: $t('system.verifyDemo.demoName'),
      minWidth: 120,
    },
    {
      field: 'amount',
      title: $t('system.verifyDemo.amount'),
      minWidth: 120,
    },
    {
      field: 'demoSort',
      title: $t('system.verifyDemo.demoSort'),
      minWidth: 120,
    },
    {
      field: 'status',
      title: $t('system.verifyDemo.status'),
      minWidth: 120,
    },
    {
      align: 'right',
      field: 'actions',
      fixed: 'right',
      slots: { default: 'actions' },
      title: $t('crud.actions'),
      width: 140,
    },
  ];
}

/** 新增、编辑表单：两列，长文本占整行 */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'demoCode',
      label: $t('system.verifyDemo.demoCode'),
    },
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'demoName',
      label: $t('system.verifyDemo.demoName'),
    },
    {
      component: 'InputNumber',
      componentProps: { class: 'w-full', controlsPosition: 'right' },
      fieldName: 'amount',
      label: $t('system.verifyDemo.amount'),
    },
    {
      component: 'InputNumber',
      componentProps: { class: 'w-full', controlsPosition: 'right' },
      fieldName: 'demoSort',
      label: $t('system.verifyDemo.demoSort'),
    },
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'status',
      label: $t('system.verifyDemo.status'),
    },
    {
      component: 'Input',
      componentProps: {
        placeholder: $t('crud.inputPlaceholder'),
        rows: 3,
        type: 'textarea',
      },
      formItemClass: 'md:col-span-2',
      fieldName: 'remark',
      label: $t('system.verifyDemo.remark'),
    },
  ];
}

/** 以逗号分隔的字符串保存、表单中为多选的字段 */
export const multipleFields: string[] = [];

/** 加载表单中依赖字典的选项（单选、多选），只保留文字与值 */
export async function loadFormOptions(): Promise<
  Record<string, { label: string; value: string }[]>
> {
  return {};
}
