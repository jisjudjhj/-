export function getAnalyticsChartTheme() {
  const isDark = document.documentElement.classList.contains('dark')

  return {
    isDark,
    textColor: isDark ? '#cbd5e1' : '#475569',
    axisColor: isDark ? '#475569' : '#cbd5e1',
    splitLineColor: isDark ? '#334155' : '#dbe3ee',
    surfaceColor: isDark ? '#0f172a' : '#ffffff',
    palette: {
      primary: '#3b82f6',
      secondary: '#06b6d4',
      accent: '#8b5cf6',
      success: '#10b981',
      warning: '#f59e0b',
      danger: '#f43f5e',
    },
  }
}
