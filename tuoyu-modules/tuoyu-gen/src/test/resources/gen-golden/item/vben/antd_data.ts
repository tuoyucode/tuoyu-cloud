import type { BizItemApi } from '@tuoyu/shared/api/system/item';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { $t } from '#/locales';

/** 查询条件：一行最多 4 个 */
export function useSearchSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'itemName',
      label: $t('system.item.itemName'),
    },
    {
      component: 'InputNumber',
      componentProps: { class: 'w-full' },
      fieldName: 'price',
      label: $t('system.item.price'),
    },
    {
      component: 'InputNumber',
      componentProps: { class: 'w-full' },
      fieldName: 'weight',
      label: $t('system.item.weight'),
    },
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'itemType',
      label: $t('system.item.itemType'),
    },
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'enabled',
      label: $t('system.item.enabled'),
    },
    {
      component: 'RangePicker',
      componentProps: { class: 'w-full', showTime: true },
      fieldName: 'startDate',
      label: $t('system.item.startDate'),
    },
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'itemCodeX',
      label: $t('system.item.itemCodeX'),
    },
  ];
}

/** 范围查询的字段：开始、结束时间分别提交为 params[begin*]、params[end*] */
export const searchFieldMappingTime: [string, [string, string], string][] = [
  [
    'startDate',
    ['params[beginStartDate]', 'params[endStartDate]'],
    'YYYY-MM-DD HH:mm:ss',
  ],
];

/** 表格列 */
export function useColumns(): VxeTableGridOptions<BizItemApi.BizItem>['columns'] {
  return [
    { type: 'checkbox', width: 48 },
    {
      field: 'itemId',
      title: $t('system.item.itemId'),
      minWidth: 120,
    },
    {
      field: 'itemName',
      title: $t('system.item.itemName'),
      minWidth: 120,
    },
    {
      field: 'coverImage',
      title: $t('system.item.coverImage'),
      minWidth: 120,
    },
    {
      field: 'price',
      title: $t('system.item.price'),
      minWidth: 120,
    },
    {
      field: 'weight',
      title: $t('system.item.weight'),
      minWidth: 120,
    },
    {
      field: 'itemType',
      title: $t('system.item.itemType'),
      minWidth: 120,
    },
    {
      field: 'enabled',
      title: $t('system.item.enabled'),
      minWidth: 120,
    },
    {
      field: 'startDate',
      title: $t('system.item.startDate'),
      width: 170,
    },
    {
      field: 'itemCodeX',
      title: $t('system.item.itemCodeX'),
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
      fieldName: 'itemName',
      label: $t('system.item.itemName'),
      rules: 'required',
    },
    {
      // 上传组件在“文件存储”页面完成后接入，目前填写文件 ID
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'coverImage',
      label: $t('system.item.coverImage'),
    },
    {
      component: 'InputNumber',
      componentProps: { class: 'w-full' },
      fieldName: 'price',
      label: $t('system.item.price'),
      rules: 'required',
    },
    {
      component: 'InputNumber',
      componentProps: { class: 'w-full' },
      fieldName: 'weight',
      label: $t('system.item.weight'),
    },
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'itemType',
      label: $t('system.item.itemType'),
    },
    {
      component: 'Switch',
      componentProps: {
        checkedValue: true,
        unCheckedValue: false,
      },
      defaultValue: true,
      fieldName: 'enabled',
      label: $t('system.item.enabled'),
    },
    {
      component: 'DatePicker',
      componentProps: {
        class: 'w-full',
        showTime: true,
        valueFormat: 'YYYY-MM-DD HH:mm:ss',
      },
      fieldName: 'startDate',
      label: $t('system.item.startDate'),
    },
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'itemCodeX',
      label: $t('system.item.itemCodeX'),
    },
  ];
}

/** 以逗号分隔的字符串保存、表单中为多选的字段 */
export const multipleFields: string[] = [];

/** 加载表单中依赖字典的选项（单选、多选） */
export async function loadFormOptions(): Promise<
  Record<string, { label: string; value: string }[]>
> {
  return {};
}
