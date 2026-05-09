function safeCall(apiName) {
  if (typeof wx !== 'object' || typeof wx[apiName] !== 'function') {
    return {}
  }

  try {
    return wx[apiName]() || {}
  } catch (err) {
    return {}
  }
}

function getSystemInfoCompat() {
  const windowInfo = safeCall('getWindowInfo')
  const deviceInfo = safeCall('getDeviceInfo')
  const appBaseInfo = safeCall('getAppBaseInfo')

  const merged = {
    ...appBaseInfo,
    ...deviceInfo,
    ...windowInfo
  }

  if (!merged.windowWidth || !merged.windowHeight || !merged.statusBarHeight) {
    if (typeof wx === 'object' && typeof wx.getSystemInfoSync === 'function') {
      try {
        const legacyInfo = wx.getSystemInfoSync() || {}
        return {
          ...legacyInfo,
          ...merged,
          windowWidth: merged.windowWidth || legacyInfo.windowWidth || 375,
          windowHeight: merged.windowHeight || legacyInfo.windowHeight || legacyInfo.screenHeight || 667,
          statusBarHeight: merged.statusBarHeight || legacyInfo.statusBarHeight || 20,
          platform: merged.platform || legacyInfo.platform || '',
          screenWidth: merged.screenWidth || legacyInfo.screenWidth || merged.windowWidth || 375,
          screenHeight: merged.screenHeight || legacyInfo.screenHeight || merged.windowHeight || 667,
          safeArea: merged.safeArea || legacyInfo.safeArea || null
        }
      } catch (err) {}
    }
  }

  return {
    ...merged,
    windowWidth: merged.windowWidth || merged.screenWidth || 375,
    windowHeight: merged.windowHeight || merged.screenHeight || 667,
    statusBarHeight: merged.statusBarHeight || 20,
    platform: merged.platform || '',
    screenWidth: merged.screenWidth || merged.windowWidth || 375,
    screenHeight: merged.screenHeight || merged.windowHeight || 667,
    safeArea: merged.safeArea || null
  }
}

module.exports = {
  getSystemInfoCompat
}
