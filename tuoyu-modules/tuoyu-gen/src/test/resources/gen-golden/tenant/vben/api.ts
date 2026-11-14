import type { PageQuery, PageResult } from '../types';

import { exportExcel, requestClient } from '../request';

export namespace VerifyDemoApi {
  /** 验证示例 */
  export interface VerifyDemo {
    /** 主键 */
    demoId?: number | string;
    /** 示例编码 */
    demoCode?: string;
    /** 示例名称 */
    demoName?: string;
    /** 金额 */
    amount?: number;
    /** 显示顺序 */
    demoSort?: number;
    /** 状态 */
    status?: string;
    /** 备注 */
    remark?: string;
    /** 创建时间 */
    createTime?: string;
  }

  /** 查询条件 */
  export interface Query extends PageQuery {
    demoCode?: string;
    demoName?: string;
    amount?: number;
    demoSort?: number;
    status?: string;
    /** 范围查询条件，例如 params[beginCreateTime] */
    params?: Record<string, unknown>;
  }

  /** 新增、修改的参数 */
  export type Form = Partial<VerifyDemo>;
}

const BASE = '/system/verifyDemo';

export function listVerifyDemo(query: VerifyDemoApi.Query) {
  return requestClient.get<PageResult<VerifyDemoApi.VerifyDemo>>(`${BASE}/list`, {
    params: query,
  });
}

export function getVerifyDemo(demoId: number | string) {
  return requestClient.get<VerifyDemoApi.VerifyDemo>(`${BASE}/${demoId}`);
}

export function addVerifyDemo(data: VerifyDemoApi.Form) {
  return requestClient.post<null>(BASE, data);
}

export function updateVerifyDemo(data: VerifyDemoApi.Form) {
  return requestClient.put<null>(BASE, data);
}

export function removeVerifyDemo(demoIds: (number | string)[]) {
  return requestClient.delete<null>(`${BASE}/${demoIds.join(',')}`);
}

export function exportVerifyDemo(query: VerifyDemoApi.Query, fileName: string) {
  return exportExcel(`${BASE}/export`, { ...query }, fileName);
}
