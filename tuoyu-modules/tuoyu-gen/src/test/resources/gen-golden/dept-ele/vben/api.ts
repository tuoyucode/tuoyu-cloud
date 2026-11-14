import { requestClient } from '../request';

export namespace DeptDemoApi {
  /** 部门 */
  export interface DeptDemo {
    /** 部门ID */
    deptId?: number | string;
    /** 上级部门 */
    parentId?: number | string;
    /** 祖级列表 */
    ancestors?: string;
    /** 部门名称 */
    deptName?: string;
    /** 显示顺序 */
    orderNum?: number;
    /** 状态 */
    status?: string;
    children?: DeptDemo[];
  }

  /** 查询条件 */
  export interface Query {
    deptName?: string;
    status?: string;
    /** 范围查询条件，例如 params[beginCreateTime] */
    params?: Record<string, unknown>;
  }

  /** 新增、修改的参数 */
  export type Form = Partial<DeptDemo>;
}

const BASE = '/system/deptDemo';

/** 树表一次返回全部数据（扁平列表） */
export function listDeptDemo(query: DeptDemoApi.Query = {}) {
  return requestClient.get<DeptDemoApi.DeptDemo[]>(`${BASE}/list`, {
    params: query,
  });
}

export function getDeptDemo(deptId: number | string) {
  return requestClient.get<DeptDemoApi.DeptDemo>(`${BASE}/${deptId}`);
}

export function addDeptDemo(data: DeptDemoApi.Form) {
  return requestClient.post<null>(BASE, data);
}

export function updateDeptDemo(data: DeptDemoApi.Form) {
  return requestClient.put<null>(BASE, data);
}

export function removeDeptDemo(deptIds: (number | string)[]) {
  return requestClient.delete<null>(`${BASE}/${deptIds.join(',')}`);
}
