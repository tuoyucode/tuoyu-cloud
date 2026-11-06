<#-- Vben 风格：查询条件、表格列、表单字段。约定见前端仓库 CONTRIBUTING.md 的“页面模板”一节 -->
<#assign i18n = moduleName + "." + businessName>
<#assign pk = pkColumn.javaField>
<#assign hasDictColumn = false>
<#assign optionFields = []>
<#assign multipleFieldNames = []>
<#assign rangeFields = []>
<#list columns as column>
<#if column.dictType?has_content><#assign hasDictColumn = true></#if>
<#if (column.insert || column.edit) && !column.pk && column.dictType?has_content && (column.htmlType == 'radio' || column.htmlType == 'checkbox')><#assign optionFields = optionFields + [column]></#if>
<#if (column.insert || column.edit) && column.htmlType == 'checkbox'><#assign multipleFieldNames = multipleFieldNames + [column.javaField]></#if>
<#if column.query && column.htmlType == 'datetime' && column.queryType == 'BETWEEN'><#assign rangeFields = rangeFields + [column]></#if>
</#list>
import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { ${ClassName}Api } from '#/api/${moduleName}/${businessName}';

<#-- 两个接口导入按路径字母顺序排列（前端 lint 规则） -->
<#assign apiPath = "#/api/" + moduleName + "/" + businessName>
<#assign dictFirst = (["#/api/system/dict", apiPath]?sort[0] == "#/api/system/dict")>
<#if hasDictColumn && dictFirst>
import { getDictOptions } from '#/api/system/dict';
</#if>
<#if table.tree>
import { list${ClassName} } from '${apiPath}';
</#if>
<#if hasDictColumn && !dictFirst>
import { getDictOptions } from '#/api/system/dict';
</#if>
import { $t } from '#/locales';
<#if table.tree>
import { listToTree } from '#/utils/tree';
</#if>

/** 查询条件：一行最多 4 个 */
export function useSearchSchema(): VbenFormSchema[] {
  return [
<#list columns as column>
<#if column.query>
    {
<#if column.dictType?has_content && (column.htmlType == 'select' || column.htmlType == 'radio' || column.htmlType == 'checkbox' || column.htmlType == 'switch')>
      component: 'ApiSelect',
      componentProps: {
        allowClear: true,
        api: () => getDictOptions('${column.dictType}'),
        placeholder: $t('crud.selectPlaceholder'),
      },
<#elseif column.htmlType == 'datetime' && column.queryType == 'BETWEEN'>
      component: 'RangePicker',
      componentProps: { class: 'w-full', showTime: true },
<#elseif column.htmlType == 'datetime'>
      component: 'DatePicker',
      componentProps: {
        class: 'w-full',
        showTime: true,
        valueFormat: 'YYYY-MM-DD HH:mm:ss',
      },
<#elseif column.htmlType == 'inputNumber'>
      component: 'InputNumber',
      componentProps: { class: 'w-full' },
<#else>
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
</#if>
      fieldName: '${column.javaField}',
      label: $t('${i18n}.${column.javaField}'),
    },
</#if>
</#list>
  ];
}

/** 范围查询的字段：开始、结束时间分别提交为 params[begin*]、params[end*] */
<#if rangeFields?has_content>
export const searchFieldMappingTime: [string, [string, string], string][] = [
<#list rangeFields as column>
  [
    '${column.javaField}',
    ['params[begin${column.capJavaField}]', 'params[end${column.capJavaField}]'],
    'YYYY-MM-DD HH:mm:ss',
  ],
</#list>
];
<#else>
export const searchFieldMappingTime: [string, [string, string], string][] = [];
</#if>

/** 表格列 */
export function useColumns(): VxeTableGridOptions<${ClassName}Api.${ClassName}>['columns'] {
  return [
<#if table.crud>
    { type: 'checkbox', width: 48 },
</#if>
<#assign treeNodeSet = false>
<#list columns as column>
<#if column.list>
    {
      field: '${column.javaField}',
<#if enableStatus && column.javaField == statusField>
      slots: { default: 'status' },
<#elseif column.dictType?has_content>
      cellRender: { name: 'CellDict', props: { dictType: '${column.dictType}' } },
</#if>
<#if table.tree && !treeNodeSet>
      treeNode: true,
<#assign treeNodeSet = true>
</#if>
      title: $t('${i18n}.${column.javaField}'),
<#if column.htmlType == 'datetime'>
      width: 170,
<#else>
      minWidth: 120,
</#if>
    },
</#if>
</#list>
    {
      align: 'right',
      field: 'actions',
      fixed: 'right',
      slots: { default: 'actions' },
      title: $t('crud.actions'),
      width: <#if table.tree>200<#else>140</#if>,
    },
  ];
}

/** 新增、编辑表单：两列，长文本占整行 */
export function useFormSchema(): VbenFormSchema[] {
  return [
<#list columns as column>
<#if (column.insert || column.edit) && !column.pk && (!column.superColumn || column.usableColumn)>
<#assign selectLike = column.htmlType == 'select' || column.htmlType == 'radio' || column.htmlType == 'checkbox' || column.htmlType == 'datetime' || column.htmlType == 'switch'>
    {
<#if table.tree && column.javaField == treeParentCode>
      component: 'ApiTreeSelect',
      componentProps: {
        api: async () =>
          listToTree(await list${ClassName}(), '${treeCode}', '${treeParentCode}'),
        childrenField: 'children',
        labelField: '${treeName}',
        placeholder: $t('crud.selectPlaceholder'),
        treeDefaultExpandAll: true,
        valueField: '${treeCode}',
      },
<#assign selectLike = true>
<#elseif column.htmlType == 'textarea' || column.htmlType == 'editor'>
      component: 'Textarea',
      componentProps: { placeholder: $t('crud.inputPlaceholder'), rows: 3 },
      formItemClass: 'md:col-span-2',
<#elseif column.htmlType == 'select' && column.dictType?has_content>
      component: 'ApiSelect',
      componentProps: {
        api: () => getDictOptions('${column.dictType}'),
        placeholder: $t('crud.selectPlaceholder'),
      },
<#elseif column.htmlType == 'radio' && column.dictType?has_content>
      component: 'RadioGroup',
      // 选项在抽屉打开时从字典加载（loadFormOptions）
      componentProps: { options: [] },
<#elseif column.htmlType == 'checkbox' && column.dictType?has_content>
      component: 'CheckboxGroup',
      // 选项在抽屉打开时从字典加载（loadFormOptions）；后端以逗号分隔的字符串保存
      componentProps: { options: [] },
<#elseif column.htmlType == 'switch'>
      component: 'Switch',
      componentProps: {
        checkedValue: ${column.switchActiveValue},
        unCheckedValue: ${column.switchInactiveValue},
      },
      defaultValue: ${column.switchActiveValue},
<#elseif column.htmlType == 'datetime'>
      component: 'DatePicker',
      componentProps: {
        class: 'w-full',
        showTime: true,
        valueFormat: 'YYYY-MM-DD HH:mm:ss',
      },
<#elseif column.htmlType == 'inputNumber'>
      component: 'InputNumber',
      componentProps: { class: 'w-full' },
<#elseif column.htmlType == 'imageUpload' || column.htmlType == 'fileUpload'>
      // 上传组件在“文件存储”页面完成后接入，目前填写文件 ID
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
<#else>
      component: 'Input',
      componentProps: { placeholder: $t('crud.inputPlaceholder') },
</#if>
      fieldName: '${column.javaField}',
      label: $t('${i18n}.${column.javaField}'),
<#if column.required>
      rules: '<#if selectLike>selectRequired<#else>required</#if>',
</#if>
    },
</#if>
</#list>
  ];
}

/** 以逗号分隔的字符串保存、表单中为多选的字段 */
export const multipleFields: string[] = [<#list multipleFieldNames as field>'${field}'<#sep>, </#sep></#list>];

/** 加载表单中依赖字典的选项（单选、多选） */
export async function loadFormOptions(): Promise<
  Record<string, { label: string; value: string }[]>
> {
<#if optionFields?has_content>
  const [<#list optionFields as column>${column.javaField}<#sep>, </#sep></#list>] = await Promise.all([
<#list optionFields as column>
    getDictOptions('${column.dictType}'),
</#list>
  ]);
  return { <#list optionFields as column>${column.javaField}<#sep>, </#sep></#list> };
<#else>
  return {};
</#if>
}
