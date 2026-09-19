import { h } from 'vue'
import {
  HomeOutlined,
  ProfileOutlined,
  BellOutlined,
  AppstoreOutlined,
  TeamOutlined,
  CreditCardOutlined,
  BarChartOutlined,
  SettingOutlined,
} from '@antdv-next/icons'
import type { MenuProps } from 'antdv-next'

/** 导航是前端展示能力清单；后续业务页面未交付前不伪装为可操作入口。 */
export const futureRoutes = [
  { path: '/orders', title: '订单中心', admin: false },
  { path: '/notifications', title: '通知中心', admin: false },
  { path: '/catalog/dishes', title: '商品管理', admin: true },
  { path: '/customers', title: '顾客管理', admin: true },
  { path: '/finance/payments', title: '支付与退款', admin: true },
  { path: '/reports', title: '经营分析', admin: true },
  { path: '/settings/shop', title: '门店设置', admin: true },
  { path: '/settings/employees', title: '员工管理', admin: true },
  { path: '/settings/audit', title: '安全审计', admin: true },
  { path: '/settings/maintenance', title: '系统维护', admin: true },
]
export function navigationItems(administrator: boolean): MenuProps['items'] {
  const items: MenuProps['items'] = [
    {
      type: 'group',
      label: '日常作业',
      children: [
        { key: '/workspace', label: '工作台', icon: () => h(HomeOutlined) },
        {
          key: '/orders',
          label: '订单中心',
          icon: () => h(ProfileOutlined),
          disabled: true,
          title: '暂未开放',
        },
        {
          key: '/notifications',
          label: '通知中心',
          icon: () => h(BellOutlined),
          disabled: true,
          title: '暂未开放',
        },
      ],
    },
  ]
  if (administrator)
    items.push(
      { type: 'divider' },
      {
        type: 'group',
        label: '经营管理',
        children: [
          {
            key: '/catalog/dishes',
            label: '商品管理',
            icon: () => h(AppstoreOutlined),
            disabled: true,
            title: '暂未开放',
          },
          {
            key: '/customers',
            label: '顾客管理',
            icon: () => h(TeamOutlined),
            disabled: true,
            title: '暂未开放',
          },
          {
            key: '/finance/payments',
            label: '支付与退款',
            icon: () => h(CreditCardOutlined),
            disabled: true,
            title: '暂未开放',
          },
          {
            key: '/reports',
            label: '经营分析',
            icon: () => h(BarChartOutlined),
            disabled: true,
            title: '暂未开放',
          },
        ],
      },
      { type: 'divider' },
      {
        key: 'settings',
        label: '系统管理',
        icon: () => h(SettingOutlined),
        children: futureRoutes
          .filter((item) => item.path.startsWith('/settings/'))
          .map((item) => ({
            key: item.path,
            label: item.title,
            disabled: true,
            title: '暂未开放',
          })),
      },
    )
  return items
}
