import type { PageQuery, PageResult } from '../types';

import { exportExcel, requestClient } from '../request';

export namespace PostDemoApi {
  /** 岗位 */
  export interface PostDemo {
    /** 岗位ID */
    postId?: number | string;
    /** 岗位编码 */
    postCode?: string;
    /** 岗位名称 */
    postName?: string;
    /** 类别编码 */
    postCategory?: string;
    /** 显示顺序 */
    postSort?: number;
    /** 状态 */
    status?: string;
    /** 备注 */
    remark?: string;
    /** 创建时间 */
    createTime?: string;
  }

  /** 查询条件 */
  export interface Query extends PageQuery {
    postCode?: string;
    postName?: string;
    postCategory?: string;
    status?: string;
    /** 范围查询条件，例如 params[beginCreateTime] */
    params?: Record<string, unknown>;
  }

  /** 新增、修改的参数 */
  export type Form = Partial<PostDemo>;
}

const BASE = '/system/postDemo';

export function listPostDemo(query: PostDemoApi.Query) {
  return requestClient.get<PageResult<PostDemoApi.PostDemo>>(`${BASE}/list`, {
    params: query,
  });
}

export function getPostDemo(postId: number | string) {
  return requestClient.get<PostDemoApi.PostDemo>(`${BASE}/${postId}`);
}

export function addPostDemo(data: PostDemoApi.Form) {
  return requestClient.post<null>(BASE, data);
}

export function updatePostDemo(data: PostDemoApi.Form) {
  return requestClient.put<null>(BASE, data);
}

export function removePostDemo(postIds: (number | string)[]) {
  return requestClient.delete<null>(`${BASE}/${postIds.join(',')}`);
}

export function exportPostDemo(query: PostDemoApi.Query, fileName: string) {
  return exportExcel(`${BASE}/export`, { ...query }, fileName);
}

export function changePostDemoStatus(
  postId: number | string,
  status: string,
) {
  return requestClient.put<null>(`${BASE}/changeStatus`, { postId, status });
}
