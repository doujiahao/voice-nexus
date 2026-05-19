import type { AppRouteRecordRaw } from '/@/router/types';
import { LAYOUT } from '/@/router/constant';

export const AI_ROUTE: AppRouteRecordRaw = {
  path: '',
  name: 'ai-parent',
  component: LAYOUT,
  meta: {
    title: 'ai',
  },
  children: [
    {
      path: '/ai',
      name: 'ai',
      component: () => import('/@/views/dashboard/ai/index.vue'),
      meta: {
        title: 'AI助手',
      },
    },
  ],
};

export const CALL_ROUTE: AppRouteRecordRaw = {
  path: '/call',
  name: 'CallWorkspace',
  component: LAYOUT,
  meta: {
    title: '话务工作台',
    hideMenu: true,
  },
  children: [
    {
      path: 'workspace',
      name: 'CallWorkspaceIndex',
      component: () => import('/@/views/call/index.vue'),
      meta: {
        title: '坐席工作台',
        hideMenu: true,
        keepAlive: true,
        ignoreKeepAlive: false,
      },
    },
  ],
};

export const staticRoutesList = [AI_ROUTE, CALL_ROUTE];
