from __future__ import annotations

import math
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP_YML = ROOT / "backend/src/main/resources/application.yml"
CONTENT_CB = ROOT / "backend/src/main/java/com/ecommerce/recommendation/ContentBasedFiltering.java"
SERVICE = ROOT / "backend/src/main/java/com/ecommerce/service/impl/RecommendationServiceImpl.java"
ADMIN = ROOT / "backend/src/main/java/com/ecommerce/controller/AdminController.java"
RECOMMEND_PREVIEW_VUE = ROOT / "management-pc/src/views/admin/RecommendPreview.vue"
USER_BEHAVIOR_MAPPER = ROOT / "backend/src/main/resources/mapper/UserBehaviorMapper.xml"
USER_PREFERENCE_BOOTSTRAP = ROOT / "backend/src/main/java/com/ecommerce/recommendation/UserPreferenceBootstrapService.java"
PY_BUILDER = ROOT / "backend/python_analytics/recommendation_builder.py"
FEATURE_BUILDER = ROOT / "backend/python_analytics/feature_builder.py"


def read(path: Path) -> str:
    if not path.exists():
        fail(f"missing file: {path}")
    return path.read_text(encoding="utf-8")


def fail(message: str) -> None:
    print(f"[FAIL] {message}")
    raise SystemExit(1)


def ok(message: str) -> None:
    print(f"[PASS] {message}")


def extract_yaml_number(text: str, key: str) -> float:
    match = re.search(rf"^\s*{re.escape(key)}:\s*([0-9.]+)\s*$", text, re.MULTILINE)
    if not match:
        fail(f"application.yml missing {key}")
    return float(match.group(1))


def check_online_weights() -> None:
    text = read(APP_YML)
    cf = extract_yaml_number(text, "collaborative-weight")
    cb = extract_yaml_number(text, "content-weight")
    hot = extract_yaml_number(text, "popularity-weight")
    if not math.isclose(cf + cb + hot, 1.0, abs_tol=0.0001):
        fail(f"online hybrid weights must sum to 1.0, got {cf + cb + hot:.4f}")
    if cb < 0.50:
        fail(f"content/category weight too low: {cb}")
    if hot > 0.20:
        fail(f"popularity weight too high for personalized recommendation: {hot}")
    ok(f"online hybrid weights prefer personal signal: CF={cf}, CB={cb}, HOT={hot}")


def check_content_based_scoring() -> None:
    text = read(CONTENT_CB)
    required_fragments = [
        "score += catWeight * 0.45",
        "InterestTagTaxonomy.weightedOverlap(profile.tags, productTags)",
        "score += priceMatch * 0.10",
        "score += salesNorm * 0.08",
        "score += ratingNorm * 0.07",
        "hasStrongCategorySignal(categoryWeights)",
        "contentScore * 0.84 + rankScore * 0.16",
    ]
    for fragment in required_fragments:
        if fragment not in text:
            fail(f"content based scoring rule missing: {fragment}")
    ok("content based recommendation uses category as the primary signal and keeps hot fallback secondary")


def check_cluster_rerank_simulation() -> None:
    text = read(SERVICE)
    required_fragments = [
        "rankScore * 55.0",
        "categoryWeights.get(categoryKey) * 30.0",
        "tagWeight * 8.0",
    ]
    for fragment in required_fragments:
        if fragment not in text:
            fail(f"cluster rerank scoring rule missing: {fragment}")

    def score(index: int, size: int, category_weight: int, tag_hits: int = 0) -> float:
        rank_score = (size - index) / max(size, 1)
        return rank_score * 55.0 + category_weight * 30.0 + tag_hits * 8.0

    wrong_top = score(index=0, size=10, category_weight=0)
    preferred_mid = score(index=5, size=10, category_weight=2)
    if preferred_mid <= wrong_top:
        fail(f"cluster rerank cannot lift preferred category: preferred={preferred_mid}, wrong={wrong_top}")
    ok("cluster-aware rerank can lift Top preference categories above unrelated early candidates")


def check_preference_coverage() -> None:
    service_text = read(SERVICE)
    python_text = read(PY_BUILDER)
    if "Math.ceil(inspectSize * 0.60D)" not in service_text:
        fail("online recommendation must require at least 60% Top category coverage in inspected head")
    if "math.ceil(inspect_size * 0.60)" not in python_text:
        fail("offline recommendation must require at least 60% Top category coverage in inspected head")
    ok("online and offline recommendation both enforce Top preference coverage")


def check_bigdata_features() -> None:
    text = read(FEATURE_BUILDER)
    required_columns = [
        "order_count_90d",
        "order_amount_90d",
        "distinct_category_count_90d",
        "behavior_count_30d",
        "cart_count_30d",
        "favorite_count_30d",
        "purchase_behavior_count_30d",
        "active_days_30d",
        "recency_order_days",
        "recency_behavior_days",
    ]
    for column in required_columns:
        if column not in text:
            fail(f"kmeans feature missing: {column}")
    ok("KMeans segmentation uses order, behavior, category breadth, activity and recency features")


def check_search_signal_enters_profile() -> None:
    mapper_text = read(USER_BEHAVIOR_MAPPER)
    content_text = read(CONTENT_CB)
    service_text = read(SERVICE)
    bootstrap_text = read(USER_PREFERENCE_BOOTSTRAP)
    required = [
        (mapper_text, "selectUserSearchCategoryPreferences"),
        (mapper_text, "FROM search_history sh"),
        (mapper_text, "FROM user_behavior ub"),
        (mapper_text, "DATE_SUB(NOW(), INTERVAL 7 DAY)"),
        (mapper_text, "remove_cart"),
        (content_text, "loadSearchCategoryPreferences(userId, 8)"),
        (service_text, "loadSearchCategoryPreferenceRows(userId)"),
        (service_text, "recommendation_exposure"),
        (bootstrap_text, "selectUserSearchCategoryPreferences(userId"),
    ]
    for text, fragment in required:
        if fragment not in text:
            fail(f"search signal is not wired into recommendation/profile: {fragment}")
    for fragment in [
        "SELECT DISTINCT",
        "sh.id AS source_id",
        "ub.id AS source_id",
        "keyword_match",
        "behavior_match",
    ]:
        if fragment not in mapper_text:
            fail(f"search-to-category mapping must deduplicate one keyword event per category: {fragment}")
    for fragment in [
        "mergePreferenceKeywordRows(scores",
        "SELECT keyword, SUM(search_count) AS countValue",
        "SELECT search_keyword AS keyword, COUNT(*) AS countValue",
    ]:
        if fragment in service_text:
            fail(f"raw search keywords must not be mixed into category preference scores: {fragment}")
    if "return new LinkedHashMap<>();" in bootstrap_text:
        fail("UserPreferenceBootstrapService.buildCategoryScores still looks empty")
    ok("search keywords are deduplicated and mapped into category preferences without raw-keyword pollution")


def check_admin_compare_uses_online_ranking() -> None:
    text = read(ADMIN)
    vue_text = read(RECOMMEND_PREVIEW_VUE)
    required = [
        "getPersonalRecommendationsWithExplanation(userId, safeLimit, true)",
        'data.put("online", onlineProducts)',
        'data.put("quality", optimizedQuality)',
        "topCategoryHitRate",
        "beforeAfterQuality",
        "negativeFeedback",
        "portraitLayers",
        "explainableFormula",
    ]
    for fragment in required:
        if fragment not in text:
            fail(f"admin compare missing final online ranking or quality report: {fragment}")
    for fragment in [
        "key: 'online'",
        "const compareTab = ref('online')",
        "const compareQuality = computed(() => compare.value.quality || null)",
        "Top 偏好命中率",
        "命中标签对比",
        "beforeAfterQuality",
        "负反馈降权",
    ]:
        if fragment not in vue_text:
            fail(f"recommend preview page does not show online ranking quality: {fragment}")
    ok("admin compare exposes final online ranking and Top category hit-rate quality report")


def main() -> None:
    print("[audit] recommendation / analytics / segmentation logic")
    check_online_weights()
    check_content_based_scoring()
    check_cluster_rerank_simulation()
    check_preference_coverage()
    check_bigdata_features()
    check_search_signal_enters_profile()
    check_admin_compare_uses_online_ranking()
    print("[audit] all recommendation logic checks passed")


if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as exc:
        fail(str(exc))
