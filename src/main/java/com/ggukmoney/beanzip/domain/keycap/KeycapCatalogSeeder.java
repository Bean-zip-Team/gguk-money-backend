package com.ggukmoney.beanzip.domain.keycap;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap.Grade;
import com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 시즌1 카탈로그는 현재 실제 이미지 자산이 확보된 12종만 시드한다 — Figma SPEC(548:13)은 "시즌1 24종"이라
 * 적혀 있지만 자산이 12종만 존재해 12종으로 축소 운영하기로 결정함(§11.12 참고).
 */
@Component
@RequiredArgsConstructor
public class KeycapCatalogSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KeycapCatalogSeeder.class);
    private static final String IMAGE_BASE_URL = "https://d1ial9ngbs7qry.cloudfront.net/keycaps/webp/";
    private static final int REQUIRED_SHARD_COUNT = 20;
    private static final int SEASON = 1;

    private static final List<CatalogEntry> CATALOG = List.of(
            new CatalogEntry("biscuit", "비스킷 키캡", Grade.RARE),
            new CatalogEntry("cheer", "치어 키캡", Grade.COMMON),
            new CatalogEntry("dolphin", "돌고래 키캡", Grade.COMMON),
            new CatalogEntry("earth", "지구 키캡", Grade.EPIC),
            new CatalogEntry("jellyfoot", "젤리발 키캡", Grade.RARE),
            new CatalogEntry("lucky", "럭키 키캡", Grade.COMMON),
            new CatalogEntry("main", "메인 키캡", Grade.COMMON),
            new CatalogEntry("moon", "달 키캡", Grade.EPIC),
            new CatalogEntry("pinkjelly", "핑크젤리 키캡", Grade.RARE),
            new CatalogEntry("pudding", "푸딩 키캡", Grade.LEGENDARY),
            new CatalogEntry("redlego", "레드레고 키캡", Grade.COMMON),
            new CatalogEntry("yellowlego", "옐로우레고 키캡", Grade.COMMON)
    );

    private final KeycapRepository keycapRepository;

    @Override
    public void run(String... args) {
        try {
            int sortOrder = 1;
            for (CatalogEntry entry : CATALOG) {
                seedIfMissing(entry, sortOrder);
                sortOrder++;
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to seed keycap catalog; existing rows will be used until this succeeds", exception);
        }
    }

    private void seedIfMissing(CatalogEntry entry, int sortOrder) {
        if (keycapRepository.existsByCode(entry.code())) {
            return;
        }
        keycapRepository.save(Keycap.createFor(
                entry.code(),
                entry.name(),
                entry.grade(),
                REQUIRED_SHARD_COUNT,
                SEASON,
                IMAGE_BASE_URL + entry.code() + "Keycap.webp",
                sortOrder
        ));
    }

    private record CatalogEntry(String code, String name, Grade grade) {
    }
}
