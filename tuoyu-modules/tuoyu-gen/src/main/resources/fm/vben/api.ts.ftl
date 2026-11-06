<#-- Vben 风格：接口与类型。约定见前端仓库 CONTRIBUTING.md 的“页面模板”一节 -->
<#assign pk = pkColumn.javaField>
<#-- 生成代码中的模板字符串：${D}{x} 输出为 ${x} -->
<#assign D = "$">
import type { <#if table.crud>PageQuery, PageResult<#else>PageQuery</#if> } from '#/api/types';

import { <#if enableExport>exportExcel, </#if>requestClient } from '#/api/request';

export namespace ${ClassName}Api {
  /** ${functionName} */
  export interface ${ClassName} {
<#list columns as column>
<#if !column.superColumn || column.usableColumn || column.javaField == 'createTime'>
    /** ${column.columnLabel} */
    ${column.javaField}?: ${column.tsType?replace("string | number", "number | string")};
</#if>
</#list>
<#if table.tree>
    children?: ${ClassName}[];
</#if>
  }

  /** 查询条件 */
  export interface Query extends PageQuery {
<#list columns as column>
<#if column.query && column.queryType != 'BETWEEN'>
    ${column.javaField}?: ${column.tsType?replace("string | number", "number | string")};
</#if>
</#list>
    /** 范围查询条件，例如 params[beginCreateTime] */
    params?: Record<string, unknown>;
  }

  /** 新增、修改的参数 */
  export type Form = Partial<${ClassName}>;
}

const BASE = '/${moduleName}/${businessName}';

<#if table.crud>
export function list${ClassName}(query: ${ClassName}Api.Query) {
  return requestClient.get<PageResult<${ClassName}Api.${ClassName}>>(`${D}{BASE}/list`, {
    params: query,
  });
}
<#else>
/** 树表一次返回全部数据（扁平列表） */
export function list${ClassName}(query: ${ClassName}Api.Query = {}) {
  return requestClient.get<${ClassName}Api.${ClassName}[]>(`${D}{BASE}/list`, {
    params: query,
  });
}
</#if>

export function get${ClassName}(${pk}: number | string) {
  return requestClient.get<${ClassName}Api.${ClassName}>(`${D}{BASE}/${D}{${pk}}`);
}

export function add${ClassName}(data: ${ClassName}Api.Form) {
  return requestClient.post<null>(BASE, data);
}

export function update${ClassName}(data: ${ClassName}Api.Form) {
  return requestClient.put<null>(BASE, data);
}

export function remove${ClassName}(${pk}s: (number | string)[]) {
  return requestClient.delete<null>(`${D}{BASE}/${D}{${pk}s.join(',')}`);
}
<#if enableExport>

export function export${ClassName}(query: ${ClassName}Api.Query, fileName: string) {
  return exportExcel(`${D}{BASE}/export`, { ...query }, fileName);
}
</#if>
<#if enableStatus>

export function change${ClassName}Status(
  ${pk}: number | string,
  ${statusField}: ${statusColumn.tsType?replace("string | number", "number | string")},
) {
  return requestClient.put<null>(`${D}{BASE}/changeStatus`, { ${pk}, ${statusField} });
}
</#if>
