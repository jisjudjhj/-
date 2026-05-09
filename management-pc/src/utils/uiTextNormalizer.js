const TEXT_REPLACEMENTS = [
  ['抱歉，您访问的页面不存在或已被移除', '页面不存在'],
  ['当前账号不是管理端可用账号', '账号不可用'],
  ['这里可以修改登录密码，也可以快速跳转到与你身份相关的管理设置。', '密码与设置'],
  ['修改密码后建议重新登录一次，确认新密码已经生效。', '修改后重新登录'],
  ['如果资料修改处于待审核状态，不影响这里的密码修改。', '不影响密码修改'],
  ['请填写完整密码信息', '请填写完整'],
  ['支持依赖校验、风险提示、批量编排与默认策略恢复', '依赖 · 风险 · 批量'],
  ['检测到核心链路风险：请优先恢复核心或关键风险模块，避免交易/通知等功能异常。', '核心链路风险'],
  ['当前接口会向所有正常状态用户发送站内消息。', '全员触达'],
  ['审核用户提交的昵称和头像修改申请', '昵称 / 头像审核'],
  ['当前按 ID 倒序显示，首条为最新商品', '最新在前'],
  ['暂无活动说明', '无说明'],
  ['暂无描述', '无描述'],
  ['暂无发送记录', '无记录'],
  ['暂无实时画像样本', '暂无样本'],
  ['暂无可安排的目标活动', '暂无场次'],
  ['等待管理员处理', '待处理'],
  ['无补充说明', '无说明'],
  ['请在管理端模块开关中启用秒杀功能。', '模块未开启'],
  ['网络或服务暂时异常，请稍后刷新重试。', '加载失败'],
  ['请求失败，请稍后重试。', '请求失败'],
  ['当前没有 AI 回复', '暂无回复'],
  ['AI 商家助手暂时繁忙，请稍后再试。你也可以直接点击“一键生成上架文案”。', '生成失败'],
  ['管理端需要先发布活动，并通过至少一个商品报名后才会生成场次。', '暂无场次'],
  ['切换顶部标签看看其他场次，或稍后再来。', '暂无场次'],
  ['用统一视图查看推荐实验的参数方案、核心指标和分层结果，方便在上线前判断哪组策略更稳、更适合当前流量。', '参数 · 指标 · 分层'],
  ['先看实验改了什么参数，再看对照结果和分层表现，最后确认验证与回滚路径。', '参数 · 结果 · 回滚'],
  ['当前实验只调整推荐权重，不改信号源口径；所有结果都基于真实曝光、点击、加购和购买行为做比较。', '只调整权重'],
  ['暂无可计算的对照差异', '暂无差异'],
  ['暂无可用于对比的实验组数据', '暂无实验组'],
  ['暂无漏斗数据，请先触发推荐曝光与行为事件', '暂无漏斗'],
  ['当前暂无分层数据', '暂无分层'],
  ['当前暂无客单分层数据', '暂无分层'],
  ['当前实验覆盖的总曝光量，用于判断结论是否具备样本基础。', '总曝光'],
  ['暂无足够样本，建议继续积累曝光和购买数据。', '样本不足'],
  ['建议补齐 ORDER 类型 recommendation_event 后查看高客单分层结果。', '样本不足'],
  ['建议至少验证 1 条快反馈路径和 1 条风险回滚路径。', '验证路径'],
  ['三组推荐权重、核心指标和当前实验结果。', '三组权重'],
  ['新老客、高低客单分层与总体平均值。', '分层结果'],
  ['保留关键验证路径和回滚检查，便于上线前核验。', '验证 / 回滚'],
  ['当前实验只调整推荐权重，不改信号链路', '只调权重'],
  ['统一用曝光、点击、转化和购买判断策略效果', '指标判断'],
  ['A 组不做个性化，只给热门榜；B 组使用标准混合推荐；C 组提高协同过滤权重。三组信号源口径保持一致，便于观察权重变化带来的实际效果差异。', '热门 / 混合 / 协同'],
  ['不依赖主观判断，统一比较 CTR、点击后转化率、总体转化率和购买量四个指标，从而判断策略是否真正提升了用户承接和购买效率。', '曝光 · 点击 · 购买'],
  ['暂无足够曝光、点击和购买数据，先触发推荐曝光后再看结论。', '样本不足'],
  ['当前样本不足，先补数据再下结论。', '样本不足'],
  ['给商家生成文案、卖点和经营建议。', '文案 / 卖点'],
  ['从评论里抽取优点、风险和适合人群。', '优点 / 风险'],
  ['先看 AI 服务状态、推荐联动和最近一轮输出摘要，帮助运营人员快速建立整体认知。', '服务 · 推荐 · 摘要'],
  ['用户对话、推荐商品、画像线索和下一步动作。', '对话 · 商品 · 画像'],
  ['统一查看 AI 模块启停状态、服务配置和当前推荐分群摘要，强调可控性。', '启停 · 配置 · 分群'],
  ['把连通检查、评价摘要和商品问答放到同一个验证视图里，方便查看能力验证链路。', '连通 · 摘要 · 问答'],
  ['让运营人员快速看清 AI 对话、推荐结果和用户分群如何联动', '智能联动'],
  ['这个页面把模型配置、推荐闭环、分群洞察和对话结果放在一张画布里，用于日常运营查看和业务验证。', '模型 · 推荐 · 分群'],
  ['按实例总览、AI 导购工作台、模块开关、工具验证切换。', '总览 · 工作台 · 开关'],
  ['现在可以按照“实例总览 → AI 导购工作台 → 模块开关 → 工具验证”的固定顺序查看，页面结构和其它运营页面保持一致。', '按顺序查看'],
  ['发起一轮 AI 对话后生成摘要', '暂无摘要'],
  ['直接观察意图识别、推荐商品、画像线索和下一步动作。', '意图 · 推荐 · 画像'],
  ['可以试试这些问题：', '示例问题：'],
  ['例如：帮我找适合送礼、预算 400 元以内的蓝牙耳机', '输入需求'],
  ['例如：这款更适合通勤还是送礼？', '输入问题'],
  ['先看用户画像和行为分布，再下钻到用户明细，是商家端最核心的经营分析视角。', '画像 · 行为 · 明细'],
  ['发券后的核销、支付订单和成交额变化。', '核销 · 订单 · 成交'],
  ['本店优惠券配置与投放。', '券投放'],
  ['这里只支持修改昵称和头像，提交后会进入审核流程，7 天内只能提交一次。', '昵称 / 头像审核'],
  ['首屏直接看主行为强弱、转化薄弱点和搜索信号。', '行为 · 转化 · 搜索'],
  ['当前主行为、最弱承接点和搜索信号要放在同一条阅读线上', '行为与转化'],
  ['当前最高频行为，用来解释推荐为什么要优先继承这类意图。', '最高频行为'],
  ['当前行为结构与成交承接距离。', '成交距离'],
  ['判断关键词意图有没有成为推荐与运营的前置信号。', '关键词信号'],
  ['用于判断当前用户意图和运营词池。', '意图词池'],
  ['按词频排序，便于运营人员判断“用户在搜什么”。', '词频排序'],
  ['把行为次数、参与用户数和行为占比放在一张表里，便于直接解释哪一步最强、哪一步最弱。', '行为明细'],
  ['把行为次数、参与用户和占比列出来，方便问题追踪。', '行为明细'],
  ['等待行为数据回流。', '等待数据'],
  ['等待加购与购买数据回流。', '等待数据'],
  ['当前未采集到明显搜索行为。', '暂无搜索'],
  ['等待行为总量生成经营结论。', '等待数据'],
  ['当前采样窗口内全部行为的合计次数。', '行为合计'],
  ['单一行为类型中参与用户数最高的规模。', '最高参与用户'],
  ['购买行为在全部行为中的占比，直接反映成交承接。', '购买占比'],
  ['看高意向动作最终能否走到下单成交。', '下单承接'],
  ['当前 count 最高的起始行为。', '主行为'],
  ['搜索行为可直接支撑关键词召回、类目偏好和运营投放判断。', '搜索支撑召回'],
  ['当前还没有采集到有效行为数据，可先检查行为采集与转化回流，再回到本页查看行为结构。', '暂无行为数据'],
  ['先看行为结构的主结论，适合 30 秒开场。', '主结论'],
  ['用图表解释各类行为的强弱与占比。', '行为占比'],
  ['看搜索趋势、关键词、纠错命中和搜索到点击/购买转化。', '搜索转化'],
  ['优先看搜索总量趋势、关键词热度与搜索后承接效率。', '搜索趋势'],
  ['当前主行为是', '主行为'],
  ['当前行为以', '行为主线'],
  ['这意味着前端推荐与运营动作应该优先承接高意向浏览和加购行为，而不是只看总流量。', '高意向承接'],
  ['统一查看限流命中、黑名单状态和高风险接口。支持在线调整规则、手动封禁与解封，帮助运营快速处理异常流量。', '限流 · 黑名单 · 风险接口'],
  ['把营收规模、趋势变化和品类结构整理到同一页，方便运营团队快速判断当前增长来自哪里，以及下一步应该盯什么。', '营收 · 趋势 · 品类'],
  ['当前营收主要拉动商品。', '营收拉动项'],
  ['这款商品是当前营收拉动项之一，适合和趋势图一起判断活动与推荐效果。', '营收拉动项'],
  ['把销量和占比展开，便于识别结构失衡和增长重心。', '销量与占比'],
  ['先看总体规模、热销商品和经营概览，适合快速理解当前销售盘面。', '规模 · 爆品'],
  ['品类分布、销售额支撑类目和集中度。', '品类分布'],
  ['用营收与订单双轴图解释近期销售变化和增长节奏。', '趋势复盘'],
  ['当前还没有可用销售数据，可先完成一轮下单流程，再回到本页查看营收、订单和品类结构。', '暂无销售数据'],
  ['活动开始后，涉及交易的核心时间与商品配置不建议再调整，保持场次配置稳定。', '开始后谨慎调整'],
  ['暂无账号准备信息', '暂无账号'],
  ['请选择完整活动时间', '请选择时间'],
  ['AI 与运营支撑', '智能运营'],
  ['降低决策与运营成本', '降本增效'],
  ['数据新鲜度与缓存说明', '数据新鲜度'],
  ['为什么这个用户会被分到当前分群', '分群原因'],
  ['系统先对订单、行为和注册时长等特征做 `StandardScaler` 标准化，再计算用户与每个簇中心的距离，距离最小的簇就是当前分群。该用户当前距离中心', '按特征距离归类'],
  ['浏览、加购、收藏较多，但订单金额偏低，转化空间明显。', '高意向待转化'],
  ['样本太少，不参与聚类中心计算，避免把噪声用户拉偏。', '样本少，不聚类'],
  ['分群画像卡片', '分群画像'],
  ['数据新鲜度与缓存说明', '数据新鲜度'],
  ['当前视图', '视图'],
  ['当前策略组', '策略组'],
  ['实时热榜', '热榜'],
  ['画像透视', '画像'],
  ['标签与链路', '标签与规则'],
  ['实时样本用户', '样本用户'],
  ['不同信号源分别保留推荐 token，方便回看曝光来源。', '曝光可追踪'],
  ['适合和推荐解释面板一起查看。', '可联动查看'],
  ['推荐效果与成交链路可追踪。', '成交可追踪'],
  ['库存小于并发用户数，用于验证防超卖和限购。', '防超卖验证'],
  ['对齐拼多多/抖音/淘宝的平台介入模式：客服接单、查看争议上下文、直接进入会话处理。', '工单 · 会话 · 介入'],
  ['官方客服已受理当前工单。', '已受理'],
  ['您好，我是官方客服，已接入当前会话并开始核实处理。', '已接入，正在核实。'],
  ['提示：秒杀相关消息可直接跳转到报名记录', '秒杀消息可跳转'],
  ['当前读取的是最新一次 KMeans 分群结果。没在列表里的编码，也可以手动输入。', '读取最新分群'],
  ['经营链路关键问题、行为数据、分层和实验结果。', '问题 · 分层 · 实验'],
  ['按经营诊断、转化链路和价值分层切换视角。', '诊断 · 漏斗 · 分层'],
  ['先看当前经营问题', '经营问题'],
  ['经营问题列表', '经营问题'],
  ['平台的问题不在于页面多寡，而在于是否能解释差异和流失点', '差异与流失'],
  ['这一屏只保留三层信息：当前做法、问题后果，以及我们怎样用行为链路和分层把问题拆开。', '做法 · 后果 · 拆解'],
  ['用同一套数据闭环把个性化、链路和验证接起来', '数据闭环'],
  ['漏斗、价值分层和实验对照形成经营闭环。', '漏斗 · 分层 · 实验'],
  ['经营问题、数据链路和处理动作。', '问题与闭环'],
  ['用户从浏览到购买的收缩过程。', '转化链路'],
  ['用户价值层次与差异化触达依据。', '价值分层'],
  ['业务主线不是报表堆叠，而是解决“一刀切、不可解释、不可验证”，并且把推荐、营销和转化串成闭环。', '推荐 · 营销 · 转化'],
  ['采集到的原始行为规模。', '行为规模'],
  ['漏斗、实验与真实成交承接。', '成交承接'],
  ['用于给后续漏斗与实验对照提供结果基线。', '结果基线'],
  ['把经营问题、策略动作和结果沉淀放在同一页里，帮助运营团队快速理解当前平台状态与下一步动作。', '问题 · 动作 · 结果'],
  ['当前经营问题', '经营问题'],
  ['再看可以落地的运营动作', '运营动作'],
  ['退款处理和平台介入进度。', '退款与介入'],
  ['运营人员最容易理解的地方是“不同信号源为什么要用不同策略”。这里直接对比信号源效果和业务含义。', '信号源策略'],
  ['关键证据、核心指标和当前结果。', '关键证据'],
  ['按人群与行为差异生成推荐策略。', '差异化推荐'],
  ['高价值人群、待转化人群和对应动作。', '重点人群'],
  ['这一块收敛项目名、仓库层级、AI 摘要和 OSS 产物，方便一次查看。', '项目产物'],
  ['优先查看贡献最高的价值层。', '价值层'],
  ['当前重点商品、成交贡献和运营动作。', '重点商品'],
  ['先看平台当前现状，再看策略联动和结果沉淀。', '现状 · 策略 · 结果'],
  ['推荐策略差异、效果图和经营结论。', '策略差异'],
  ['聚焦退款争议和退款原因分布，体现售后闭环能力。', '售后闭环'],
  ['画像、人群价值、热销商品和当前结果。', '画像 · 商品 · 结果'],
  ['建议结合当前分群特征制定动作。', '按分群触达'],
  ['建议结合用户分层结果进行差异化运营。', '差异化运营'],
  ['建议结合当前分群特征推进定向触达。', '定向触达'],
  ['仪表盘', '运营工作台'],
  ['智能决策中心', '推荐预览'],
  ['智能决策', '推荐预览'],
  ['AI 对话中心', '客服对话'],
  ['AI对话中心', '客服对话'],
  ['AI 助手中心', '客服助手'],
  ['AI助手中心', '客服助手'],
  ['推荐中心', '推荐预览'],
  ['画像中心', '用户分群'],
  ['用户画像中心', '用户分群'],
  ['营销活动中心', '营销活动'],
  ['商品管理中心', '商品管理'],
  ['订单管理中心', '订单管理'],
  ['售后服务中心', '售后处理'],
  ['秒杀活动中心', '秒杀场次'],
  ['数据分析中心', '经营洞察'],
  ['运营分析中心', '经营洞察'],
  ['管理中心', '管理'],
  ['数据看板', '数据概览'],
  ['运营看板', '运营工作台'],
  ['经营看板', '经营概览'],
  ['管理看板', '管理概览'],
  ['实时看板', '实时数据'],
  ['退款管理', '售后退款'],
  ['订单管理', '订单履约'],
  ['钱包管理', '资金流水'],
  ['评论管理', '评价治理'],
  ['轮播图管理', '首页展示'],
  ['商家管理', '商家入驻'],
  ['操作日志', '操作审计'],
  ['消息推送', '触达消息'],
  ['AI 助手', '智能助手'],
  ['AI助手', '智能助手'],
  ['AI 导购', '智能导购'],
  ['AI导购', '智能导购'],
  ['AI 商家助手', '智能商家助手'],
  ['AI商家助手', '智能商家助手'],
  ['AI 服务', '智能服务'],
  ['AI服务', '智能服务'],
  ['AI 工具', '智能工具'],
  ['AI工具', '智能工具'],
  ['AI 文案', '智能文案'],
  ['AI文案', '智能文案'],
  ['AI 结果', '智能结果'],
  ['AI结果', '智能结果'],
  ['AI 回复', '智能回复'],
  ['AI回复', '智能回复'],
  ['AI 模型', '智能模型'],
  ['AI模型', '智能模型'],
  ['AI API', '智能接口'],
  ['API 地址', '接口地址'],
  ['API地址', '接口地址'],
  ['GMV', '成交额'],
  ['AOV', '客单价'],
  ['CTR', '点击率'],
  ['KPI', '指标'],
  ['Top', 'Top'],
  ['Topic', '主题'],
  ['Kafka Lag', '队列积压'],
  ['Lag Topic', '积压主题'],
  ['Lag', '积压'],
  ['Dead Letter', '死信'],
  ['KMeans', '分群'],
  ['Hybrid', '混合推荐'],
  ['User-CF', '协同过滤'],
  ['Content-CB', '内容推荐'],
  ['CF', '协同过滤'],
  ['CB', '内容推荐'],
  ['OSS', '对象存储'],
  ['Operations Overview', '运营总览'],
  ['Experiment Operations', '实验运营'],
  ['Revenue Operations', '营收运营']
]

const TEXT_NODE = 3
let observer
let scheduled = false
let styleInjected = false

function normalizeText(value) {
  if (!value) return value
  return TEXT_REPLACEMENTS.reduce((text, [from, to]) => text.replaceAll(from, to), value)
}

function normalizeNode(node) {
  if (!node) return

  if (node.nodeType === TEXT_NODE) {
    const normalized = normalizeText(node.nodeValue)
    if (normalized !== node.nodeValue) {
      node.nodeValue = normalized
    }
    return
  }

  if (node.nodeType !== 1) return

  ;['title', 'aria-label', 'placeholder'].forEach((attr) => {
    const value = node.getAttribute?.(attr)
    const normalized = normalizeText(value)
    if (normalized && normalized !== value) {
      node.setAttribute(attr, normalized)
    }
  })

  node.childNodes?.forEach(normalizeNode)
}

function normalizeDocument() {
  scheduled = false
  normalizeNode(document.body)
  document.title = normalizeText(document.title)
}

function scheduleNormalize() {
  if (scheduled) return
  scheduled = true
  requestAnimationFrame(normalizeDocument)
}

export function setupUiTextNormalizer() {
  if (observer || typeof window === 'undefined') return

  injectCompactAdminStyle()
  scheduleNormalize()
  observer = new MutationObserver(scheduleNormalize)
  observer.observe(document.body, {
    childList: true,
    subtree: true,
    characterData: true,
    attributes: true,
    attributeFilter: ['title', 'aria-label', 'placeholder']
  })
}

function injectCompactAdminStyle() {
  if (styleInjected || document.getElementById('admin-compact-page-style')) return

  const style = document.createElement('style')
  style.id = 'admin-compact-page-style'
  style.textContent = `
    .copy-compact .page-subtitle,
    .copy-compact .section-description,
    .copy-compact .section-shell__desc,
    .copy-compact .seckill-hero__desc,
    .copy-compact .stream-hero__desc,
    .copy-compact .decision-subline,
    .copy-compact .helper-text,
    .copy-compact .dialog-tip__desc,
    .copy-compact .defense-rail-list__text,
    .copy-compact .recommend-defense-row p,
    .copy-compact .recommend-formula-card p,
    .copy-compact .defense-surface__text,
    .copy-compact .behavior-overview-facts__text,
    .copy-compact .sales-overview-points__text,
    .copy-compact .ab-test-status-card__desc,
    .copy-compact .recommend-preview-status-card__desc,
    .copy-compact .intelligence-overview-board__text,
    .copy-compact .competition-overview-metrics__sub,
    .copy-compact .analysis-page-header__meta,
    .copy-compact .dashboard-cockpit__summary-label,
    .copy-compact .dashboard-cockpit__section-eyebrow,
    .copy-compact .competition-overview-metrics__desc,
    .copy-compact .competition-workbench p.mt-1.text-sm,
    .copy-compact .competition-workbench p.mt-2.text-sm,
    .copy-compact .competition-workbench p.mt-3.text-sm,
    .copy-compact .analysis-page p.mt-1.text-sm,
    .copy-compact .analysis-page p.mt-2.text-sm,
    .copy-compact .analysis-page p.mt-3.text-sm,
    .copy-compact .admin-page p.mt-1.text-sm,
    .copy-compact .admin-page p.mt-2.text-sm,
    .copy-compact .admin-page p.mt-3.text-sm,
    .copy-compact .dashboard-page p.mt-1.text-sm,
    .copy-compact .dashboard-page p.mt-2.text-sm,
    .copy-compact .dashboard-page p.mt-3.text-sm,
    .copy-compact .decision-page p.mt-1.text-sm,
    .copy-compact .decision-page p.mt-2.text-sm,
    .copy-compact .decision-page p.mt-3.text-sm,
    .copy-compact .el-alert__description,
    .copy-compact .text-slate-500.leading-6,
    .copy-compact .text-slate-600.leading-6,
    .copy-compact .text-gray-500.leading-6,
    .copy-compact .text-gray-600.leading-6 {
      display: none !important;
    }

    .copy-compact .el-empty__description {
      font-size: 13px;
    }

    .copy-compact .panel-card,
    .copy-compact .defense-surface,
    .copy-compact .tech-panel {
      border-radius: 18px !important;
    }

    .copy-compact .el-card__body,
    .copy-compact .panel-card,
    .copy-compact .section-shell,
    .copy-compact .defense-surface {
      padding-top: 14px;
      padding-bottom: 14px;
    }

    .el-main > .page-container,
    .el-main > .admin-page,
    .el-main > .dashboard-page,
    .el-main > .decision-page,
    .app-main > .page-container,
    .app-main > .admin-page,
    .app-main > .dashboard-page,
    .app-main > .decision-page {
      max-width: 1120px;
      margin-left: auto;
      margin-right: auto;
    }

    .el-main > .page-container,
    .el-main > .admin-page,
    .el-main > .dashboard-page {
      padding-top: 20px;
    }

    .el-card + .el-card,
    .el-row + .el-card,
    .el-card + .el-row {
      margin-top: 14px;
    }

    .el-card__body {
      padding: 16px;
    }

    .el-descriptions,
    .el-table {
      font-size: 13px;
    }

    .el-page-header__content,
    .page-title,
    .section-title {
      letter-spacing: 0;
    }

    .page-subtitle,
    .section-description,
    .helper-text {
      max-width: 720px;
      color: #697386;
      line-height: 1.7;
    }
  `
  document.head.appendChild(style)
  styleInjected = true
}
