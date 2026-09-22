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

/** 导航只展示当前角色已交付的页面；服务端继续校验实际权限。 */
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
        },
        {
          key: '/notifications',
          label: '通知中心',
          icon: () => h(BellOutlined),
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
            key: 'catalog',
            label: '商品管理',
            icon: () => h(AppstoreOutlined),
            children: [
              { key: '/catalog/dishes', label: '菜品管理' },
              { key: '/catalog/meals', label: '套餐管理' },
              { key: '/catalog/categories', label: '分类管理' },
            ],
          },
          {
            key: '/customers',
            label: '顾客管理',
            icon: () => h(TeamOutlined),
          },
          {
            key: 'finance',
            label: '支付与退款',
            icon: () => h(CreditCardOutlined),
            children: [
              { key: '/finance/payments', label: '支付流水' },
              { key: '/finance/refunds', label: '退款流水' },
              { key: '/finance/reconciliation', label: '资金对账' },
            ],
          },
          {
            key: '/reports',
            label: '经营分析',
            icon: () => h(BarChartOutlined),
          },
        ],
      },
      { type: 'divider' },
      {
        key: 'settings',
        label: '系统管理',
        icon: () => h(SettingOutlined),
        children: [
          { key: '/settings/shop', label: '门店设置' },
          { key: '/settings/employees', label: '员工管理' },
          { key: '/settings/audit', label: '安全审计' },
          { key: '/settings/maintenance', label: '系统维护' },
        ],
      },
    )
  return items
}
