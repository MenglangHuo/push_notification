import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CustomTable, { type TableColumn } from './CustomTable.vue'

describe('CustomTable Component', () => {
  const sampleColumns: TableColumn[] = [
    { key: 'name', label: 'Name', width: 200, minWidth: 100 },
    { key: 'status', label: 'Status', width: 120, minWidth: 80 },
    { key: 'count', label: 'Count', width: 100, minWidth: 60, align: 'right' },
  ]

  const sampleItems = [
    { id: '1', name: 'Campaign Alpha', status: 'COMPLETED', count: 42 },
    { id: '2', name: 'Campaign Beta', status: 'PROCESSING', count: 18 },
  ]

  beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  function createWrapper(props = {}) {
    return mount(CustomTable, {
      props: {
        tableId: 'test-table',
        columns: sampleColumns,
        items: sampleItems,
        ...props,
      },
      slots: {
        'cell-name': '<template #cell-name="{ row }"><span class="custom-name">{{ row.name }}</span></template>',
      },
    })
  }

  it('renders table headers, items, and custom cell slots', () => {
    const wrapper = createWrapper()

    expect(wrapper.text()).toContain('Name')
    expect(wrapper.text()).toContain('Status')
    expect(wrapper.text()).toContain('Count')
    expect(wrapper.text()).toContain('Campaign Alpha')
    expect(wrapper.text()).toContain('Campaign Beta')
    expect(wrapper.find('.custom-name').text()).toBe('Campaign Alpha')
  })

  it('switches table density between Small, Normal, and Large', async () => {
    const wrapper = createWrapper()

    const smallBtn = wrapper.findAll('button').find((b) => b.text().includes('Small'))
    expect(smallBtn).toBeDefined()
    await smallBtn!.trigger('click')

    // Compact density applied
    const th = wrapper.find('th')
    expect(th.classes().some((c) => c.includes('py-2'))).toBe(true)

    const largeBtn = wrapper.findAll('button').find((b) => b.text().includes('Large'))
    expect(largeBtn).toBeDefined()
    await largeBtn!.trigger('click')

    expect(th.classes().some((c) => c.includes('py-4'))).toBe(true)
  })

  it('allows toggling column visibility on and off', async () => {
    const wrapper = createWrapper()

    // Find Columns dropdown trigger
    const colBtn = wrapper.findAll('button').find((b) => b.text().includes('Columns'))
    expect(colBtn).toBeDefined()
    await colBtn!.trigger('click')
    await flushPromises()

    // Find checkbox for Status column
    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    expect(checkboxes.length).toBe(3)

    // Toggle off the second column (Status)
    await checkboxes[1].trigger('change')
    await flushPromises()

    // Status header should no longer exist in the table header
    const headers = wrapper.findAll('th').map((h) => h.text())
    expect(headers.some((h) => h.includes('Status'))).toBe(false)
    expect(headers.some((h) => h.includes('Name'))).toBe(true)
  })

  it('handles column resizing on mousedown and mousemove', async () => {
    const wrapper = createWrapper()

    const resizer = wrapper.find('.cursor-col-resize')
    expect(resizer.exists()).toBe(true)

    // Trigger mousedown on resizer
    await resizer.trigger('mousedown', { clientX: 200 })

    // Dispatch global mousemove
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 250 }))
    await flushPromises()

    // Check col style width has grown
    const cols = wrapper.findAll('col')
    expect(cols[0].attributes('style')).toContain('250px')

    // Stop resize
    window.dispatchEvent(new MouseEvent('mouseup'))
  })
})
