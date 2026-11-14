import type { PostDemoApi } from '@tuoyu/shared/api/system/postDemo';

import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { getDictOptions } from '@tuoyu/shared/api/system/dict';

import { $t } from '#/locales';

/** 查询条件：一行最多 4 个 */
export function useSearchSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'postCode',
      label: $t('system.postDemo.postCode'),
    },
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'postName',
      label: $t('system.postDemo.postName'),
    },
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'postCategory',
      label: $t('system.postDemo.postCategory'),
    },
    {
      component: 'ApiSelect',
      componentProps: {
        allowClear: true,
        api: () => getDictOptions('sys_normal_disable'),
        placeholder: $t('crud.selectPlaceholder'),
      },
      fieldName: 'status',
      label: $t('system.postDemo.status'),
    },
    {
      component: 'RangePicker',
      componentProps: { class: 'w-full', showTime: true },
      fieldName: 'createTime',
      label: $t('system.postDemo.createTime'),
    },
  ];
}

/** 范围查询的字段：开始、结束时间分别提交为 params[begin*]、params[end*] */
export const searchFieldMappingTime: [string, [string, string], string][] = [
  [
    'createTime',
    ['params[beginCreateTime]', 'params[endCreateTime]'],
    'YYYY-MM-DD HH:mm:ss',
  ],
];

/** 表格列 */
export function useColumns(): VxeTableGridOptions<PostDemoApi.PostDemo>['columns'] {
  return [
    { type: 'checkbox', width: 48 },
    {
      field: 'postId',
      title: $t('system.postDemo.postId'),
      minWidth: 120,
    },
    {
      field: 'postCode',
      title: $t('system.postDemo.postCode'),
      minWidth: 120,
    },
    {
      field: 'postName',
      title: $t('system.postDemo.postName'),
      minWidth: 120,
    },
    {
      field: 'postCategory',
      title: $t('system.postDemo.postCategory'),
      minWidth: 120,
    },
    {
      field: 'postSort',
      title: $t('system.postDemo.postSort'),
      minWidth: 120,
    },
    {
      field: 'status',
      slots: { default: 'status' },
      title: $t('system.postDemo.status'),
      minWidth: 120,
    },
    {
      field: 'createTime',
      title: $t('system.postDemo.createTime'),
      width: 170,
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
      fieldName: 'postCode',
      label: $t('system.postDemo.postCode'),
      rules: 'required',
    },
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'postName',
      label: $t('system.postDemo.postName'),
      rules: 'required',
    },
    {
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
      fieldName: 'postCategory',
      label: $t('system.postDemo.postCategory'),
    },
    {
      component: 'InputNumber',
      componentProps: { class: 'w-full' },
      fieldName: 'postSort',
      label: $t('system.postDemo.postSort'),
      rules: 'required',
    },
    {
      component: 'RadioGroup',
      // 选项在抽屉打开时从字典加载（loadFormOptions）
      componentProps: { options: [] },
      // 新增时默认为启用状态
      defaultValue: '0',
      fieldName: 'status',
      label: $t('system.postDemo.status'),
    },
    {
      component: 'Textarea',
      componentProps: { placeholder: $t('crud.inputPlaceholder'), rows: 3 },
      formItemClass: 'md:col-span-2',
      fieldName: 'remark',
      label: $t('system.postDemo.remark'),
    },
  ];
}

/** 以逗号分隔的字符串保存、表单中为多选的字段 */
export const multipleFields: string[] = [];

/** 加载表单中依赖字典的选项（单选、多选） */
export async function loadFormOptions(): Promise<
  Record<string, { label: string; value: string }[]>
> {
  const [status] = await Promise.all([
    getDictOptions('sys_normal_disable'),
  ]);
  return { status };
}
