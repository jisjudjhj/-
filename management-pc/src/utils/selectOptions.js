export function normalizeSelectOptions(options = [], config = {}) {
  const {
    labelKeys = ['label', 'name', 'title', 'productName', 'activityName', 'username'],
    valueKeys = ['value', 'id', 'productId', 'activityId', 'userId', 'status', 'type'],
    includeEmpty = false,
    emptyLabel = '全部',
    emptyValue = ''
  } = config

  const normalized = options
    .map((item) => {
      if (item === null || item === undefined) return null

      if (['string', 'number', 'boolean'].includes(typeof item)) {
        return {
          label: String(item),
          value: item
        }
      }

      const label = pickFirst(item, labelKeys)
      const value = pickFirst(item, valueKeys)

      if (value === undefined || value === null) return null

      return {
        ...item,
        label: label === undefined || label === null || label === '' ? String(value) : label,
        value
      }
    })
    .filter(Boolean)

  if (!includeEmpty) return normalized

  return [
    {
      label: emptyLabel,
      value: emptyValue
    },
    ...normalized
  ]
}

function pickFirst(item, keys) {
  for (const key of keys) {
    if (item[key] !== undefined && item[key] !== null) {
      return item[key]
    }
  }
  return undefined
}
